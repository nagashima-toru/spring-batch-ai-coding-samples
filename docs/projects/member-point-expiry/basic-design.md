# 基本設計書: 会員ポイント失効バッチ（member-point-expiry）

| 項目 | 内容 |
|---|---|
| 案件名 | 会員ポイント失効バッチ（member-point-expiry） |
| 入力（要件定義書） | docs/projects/member-point-expiry/requirements.md（2026-08-09 版・ステータス「確定」） |
| ステータス | 確定（1.2）。[Ask] 2件は §6 のとおりユーザー確認済み・現状維持で確定 |

## 1. ジョブ一覧

| ジョブID | ジョブ名（Bean名） | 概要 | 方式 | 実行タイミング | 先行ジョブ |
|---|---|---|---|---|---|
| POINT-001 | pointExpiryJob | 有効期限が対象日より前の「有効」なポイント付与を「失効」へ更新し、同時に失効履歴を記録する | Tasklet | 日次 02:00 | なし |

**ジョブ分割の検討**: 要件定義書 §3.1 の処理は No.1（付与の失効）と No.2（失効履歴の記録）の2つだが、
以下の理由から **1ジョブ・1 Step** とする。

- 実行タイミング（日次深夜）・リラン単位（対象日）・依存関係（なし）がまったく同一であり、
  別ジョブに分ける理由がない（分割基準: 「実行タイミング・リラン単位・依存関係が異なるものは別ジョブ」）
- 要件定義書 §3.4 の**整合性要件**（失効させた付与と失効履歴が必ず1対1）を満たすには、
  両処理が**同一トランザクション内で完結**する必要がある。Step を分けると Step ごとに別トランザクションに
  なり（architecture.md §4）、Step 間で異常終了した場合に「失効済みだが履歴なし」が発生しうる。
  よって「1 Step 1 責務」（§3.2）における本 Step の責務は「**期限切れ付与の失効（履歴記録を含む不可分な業務単位）**」と定義する

**実装配置**: `batch-sample` モジュールにパッケージ `job_point_expiry` として追加する
（architecture.md §2.1 パッケージ・バイ・フィーチャー）。ジョブ名は案件ジョブのため
サンプル番号プレフィックスを付けない（announcement-notification 案件の先例に合わせる）。
**パッケージ名も同様にサンプル番号を付けない**（architecture.md §11 の `jobNN_{内容}` はサンプルジョブ向けの規約であり、
案件ジョブでパッケージだけ連番を残すとジョブ名・Step 名・Config クラス名（いずれも無番号）と内部不整合になるため。
announcement-notification 案件で同じ判断がなされ `job_announcement_notify` に統一済み）。

| 対象 | 名称 |
|---|---|
| ジョブ名（Bean名） | `pointExpiryJob` |
| Step 名 | `pointExpiry.01ExpireStep` |
| パッケージ | `com.example.batch.sample.job_point_expiry` |
| Job 設定クラス | `PointExpiryJobConfig` |
| Mapper XML | `PointGrantMapper.xml` |

## 2. ジョブ方式設計

### POINT-001: pointExpiryJob

- **方式**: Tasklet（1 Step）
- **選定理由**:
  - 処理は「期限切れ付与の一括 UPDATE」と「失効履歴の一括 INSERT」であり、**DB 内の SQL 1本で完結**する。
    architecture.md §3.1 の判断基準「集計 SQL 1本で完結する処理（`INSERT ... SELECT`）→ **Tasklet**
    （DB 内で完結させ、データを Java 側に持ち出さない）」に該当する
  - サンプル04（DB→DB 集計・洗い替え）の選定基準「**選ぶ: 集計・変換が SQL で表現できる（DB内で完結）**」に合致し、
    「選ばない: 1件ごとに外部API呼出や複雑なJavaロジックが必要」には該当しない
    （失効判定は `status` と `expiry_date` の比較のみで、レコード単位の Java ロジックが不要）
  - Chunk も検討したが不採用。理由は3点:
    1. 要件定義書 §3.5 のとおり**業務エラー（Skip 対象）が存在しない**ため、Chunk の主要な利点である
       レコード単位の Skip 制御（§3.1 の表）が不要
    2. 入力件数は数千〜数万件で**上限がある**ため、§3.1 の「入力データが件数無制限（ファイル・テーブル・API ページング）→ Chunk」に当たらない
    3. Chunk はチャンク単位コミットのため、途中失敗時に「一部だけ失効済み」の状態になる。
       これ自体は冪等性により次回実行で回復するが、整合性要件（§3.4）を SQL の原子性で
       単純に担保できる Tasklet の方が設計・検証コストが低い
  - なお architecture.md §4 は「長大な更新は Tasklet にしない（undo ログ肥大・ロック長期化）」と定めるが、
    本件は最大でも数万行の UPDATE ＋ 同数の INSERT であり「長大」に当たらない（後述の閾値を参照）
- **Step構成（概要）**:

| # | Step名 | 方式 | 処理内容 |
|---|---|---|---|
| 1 | pointExpiry.01ExpireStep | Tasklet | 対象日を条件に、期限切れの「有効」な付与を「失効」へ更新し、更新された行をそのまま失効履歴へ登録する（後述の単一 SQL）。Tasklet 全体で1トランザクション |

- **整合性要件（要件定義書 §3.4）の担保方式**: PostgreSQL の**データ変更 CTE（`UPDATE ... RETURNING` を
  `WITH` 句に置き、その結果を `INSERT ... SELECT` する）**を用い、失効更新と履歴登録を**1つの SQL 文**で実行する。

```sql
WITH expired AS (
    UPDATE app.point_grant
       SET status       = '2',
           expired_date = #{targetDate},
           updated_at   = now()
     WHERE status = '1'
       AND expiry_date < #{targetDate}
    RETURNING point_grant_id, member_id, points, granted_date, expiry_date
)
INSERT INTO app.point_expiry_history (
    point_grant_id, member_id, points, granted_date, expiry_date, expired_date, created_at
)
SELECT point_grant_id, member_id, points, granted_date, expiry_date, #{targetDate}, now()
  FROM expired;
```

  - **単一 SQL 文である**ため、失効更新と履歴登録は原子的に成立し、両者は常に1対1になる（片方だけ成立しない）
  - `RETURNING` が返すのは「実際にこの文が更新した行」そのものであるため、
    「UPDATE 対象を SELECT で取り直す」方式で起こりうる取りこぼし・二重取得が原理的に発生しない。
    分離レベル READ COMMITTED で、履歴 INSERT → 付与 UPDATE の2文に分けた場合、
    2文の間にポイント付与処理が新たな期限切れ行をコミットすると UPDATE 側だけがそれを拾い、
    「失効済みだが履歴なし」が生じうる。単一文にすることでこの競合も排除する
  - 二重防御として、失効履歴テーブルの `point_grant_id` に**一意制約**を付与し、
    1つの付与に対して履歴が2件以上作られないことを DB 制約で保証する（§4.2）
  - 上記 SQL・§4.2 の DDL は PostgreSQL 16（`docker/compose.yaml` の環境）で動作確認済み。
    「失効2件＝履歴2件で1対1」「同一対象日での再実行は0件（冪等）」「翌日実行で期限当日だった付与が失効（自己回復）」
    「`(status, expiry_date)` インデックスが抽出に使われる（Bitmap Index Scan）」ことを確認した（設計レビューでも再検証済み）
  - 更新件数は SQL の戻り値（affected rows。データ変更 CTE では外側の INSERT 行数＝失効させた付与件数）として取得し、
    `contribution.incrementWriteCount(件数)` で `StepContribution` へ反映するとともに、
    **Tasklet 内で INFO ログにも出力する**（サンプル04 `SalesAggregateTasklet` と異なり、コードレビューでの
    ユーザー確認により `writeCount` 反映を追加した）。処理件数は `BATCH_STEP_EXECUTION.WRITE_COUNT`・
    共通リスナー（`LoggingStepExecutionListener`）のログ・業務ログのいずれからも確認できる。
    0件でも正常終了とする（対象なしの日が普通に発生するため）
  - **同時実行との競合**: ポイント付与処理は常時稼働しうるが（要件定義書 §3.2）、本ジョブは日次深夜の実行であり
    同一行を更新し合う可能性は低い。仮に行ロック競合・デッドロックが起きた場合はシステムエラーとして
    即時異常終了し（後述のエラー処理表）、トランザクションは全ロールバックされる。
    未失効分は次回実行（`expiry_date < targetDate`）でまとめて回収されるため取りこぼしは生じない
- **リラン設計**:
  - **前提: JDBC ジョブリポジトリ**。Boot 4 の既定 JobRepository はインメモリ（Resourceless）で
    メタデータが永続化されないため、`spring-boot-starter-batch-jdbc` を依存に含める
    （architecture.md §9.1。`batch-sample` は既に依存済み）
  - 失敗時は**同一 `targetDate` でリスタート**する。Tasklet Step は Step 単位で最初から再実行される（§3.1）が、
    トランザクションが全ロールバックされているため中途半端な状態は残らない
  - **冪等性**: 抽出条件が `status='1'`（有効）であるため、既に失効済みの付与は再実行時の対象に入らない
    （architecture.md §6.2 の「同一入力の再実行で結果が変わらない」を条件による絞り込みで担保）。
    失効履歴の `expired_date` にも実行時刻ではなく**ジョブパラメータの対象日**を格納するため、
    リラン時刻がずれても結果が変わらない。
    なお監査列（`point_grant.updated_at` / `point_expiry_history.created_at`）には実処理時刻（`now()`）を
    記録するため実行のたびに値が変わるが、これは監査目的の記録であり業務的な結果（失効状態・履歴の内容）は
    不変であるという意味での冪等性である
  - **成功済みジョブの同一パラメータ再実行はエラーになる**のが正しい挙動（§6.1）。
    意図的な再処理は `targetDate` を変えて実行する（例: 数日実行できなかった場合は最新日を1回実行すれば
    比較演算子が不等号（`<`）のため取りこぼしなく回収できる。要件定義書 §3.4）
  - 二重起動防止: 基盤部品 `DuplicateRunPreventingJobExecutionListener` ＋ EventBridge 側の
    同時実行抑止（§6.3 多層防御）
- **エラー処理**（事象ごとの分類と動作）:

| エラー事象 | 分類 | 例外 | 動作 | 終了コード |
|---|---|---|---|---|
| DB 接続断・デッドロック・テーブル不存在・SQL エラー | システムエラー | `SystemException`（MyBatis / DataSource の例外を通じて Step が失敗） | 即時異常終了（トランザクション全ロールバック） | 1（異常） |
| ジョブパラメータ `targetDate` **未指定** | システムエラー（設定・起動不備） | `JobParametersInvalidException`（`JobParametersValidators.required("targetDate")` が検出） | Step 実行前に失敗 | 1（異常） |
| ジョブパラメータ `targetDate` の**型・書式不正**（`java.time.LocalDate` へ変換できない） | システムエラー（設定・起動不備） | パラメータ変換時の例外（Validator ではなく起動時の JobParameters 変換で失敗する） | ジョブ起動時に失敗 | 1（異常） |
| 失効履歴の一意制約違反（`point_grant_id` 重複） | システムエラー（設計上発生しない想定の防御） | `SystemException` | 即時異常終了（全ロールバック） | 1（異常） |

  - **Skip は使用しない**（`skipLimit` の設定対象なし）。要件定義書 §3.5 のとおり外部入力を伴わず、
    業務データ起因のエラー（Skip 対象）が存在しないため（architecture.md §5.2 の Skip は
    `BusinessException` 系のみ許可、という原則に照らしても Skip すべき事象がない）
  - エラーレコードファイルへの退避は行わない（退避対象が存在しないため。`errorFile` パラメータも設けない）
  - **警告終了（終了コード2）は発生しない**。§5.3 の警告終了条件は「Skip が1件以上発生して完走」であり、
    本ジョブは Skip を行わないためこの条件を満たすことがない。終了コード規約（0/2/1）自体は変更しない（§7）
- **ジョブパラメータ**:

| パラメータ名 | 型 | 必須 | 例 | 説明 |
|---|---|---|---|---|
| targetDate | java.time.LocalDate | ○ | 2026-08-10 | 失効判定の対象日。`expiry_date < targetDate` の付与を失効させる（対象日と同日期限の付与は失効させない）。JobInstance の識別にも使用する。通常運用ではスケジューラが実行当日の日付を渡し、リラン時は同一値、任意日での再処理時は別の値を指定する |

  - `JobParametersValidators.required("targetDate")` で必須検証する（要件定義書 §4。実行時刻から自動導出しない）
- **処理量とチャンクサイズ**:
  - 想定件数: ポイント付与テーブル全体で数千〜数万件（要件定義書 §3.2・§5）。1回の実行で失効対象になるのは
    そのうちの一部（日次実行のため、通常は1日分の期限到来分＝数十〜数百件規模）
  - **チャンクサイズは適用外**（Tasklet のため）。想定処理時間は、`(status, expiry_date)` の複合インデックスで
    対象行を特定したうえでの UPDATE ＋ INSERT であり、**最大でも数秒**を見込む
  - **方式見直しの閾値**: 1回の失効対象が概ね **10万件**を超えるようになった場合、単一トランザクションが
    長大化（undo ログ肥大・ロック長期化。architecture.md §4）するため、対象日レンジや会員IDレンジで
    分割コミットする方式（複数 Step / パーティショニング＝サンプル07）へ見直す。
    小規模データ前提（要件定義書 §5）である限り本設計を維持する

## 3. IF設計

### 3.1 ファイルIF

なし（入出力はすべて DB。要件定義書 §3.2・§3.3。エラーレコードファイルも出力しない＝Skip なしのため）

### 3.2 API IF

なし（入出力はすべて DB）

## 4. テーブル設計（app スキーマ）

### 4.1 テーブル一覧

| テーブル名 | 用途 | 新規/変更 | 想定件数 |
|---|---|---|---|
| point_grant | ポイント付与（付与ごとに1レコード。本バッチが `status` を「失効」へ更新する） | 新規 | 数千〜数万件 |
| point_expiry_history | 失効履歴（本バッチの出力。CS対応・監査で参照） | 新規 | 累積で数千〜数万件（削除せず保持） |

※ 要件定義書 §6 未確定 No.1（テーブルのカラム設計）は、本節をもって確定とする（基本設計で確定する前提の項目）。
※ 会員マスタは本案件のスコープ外のため作成せず、`member_id` は FK 制約なしの論理参照とする（§6 の確認事項参照）。
※ ポイント付与データの投入手段は本案件スコープ外（要件定義書 §2）。テスト・ローカルでは SQL で直接投入する。

### 4.2 テーブル定義

#### point_grant（ポイント付与）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| point_grant_id | bigint | NOT NULL | PK | 付与ID（ポイント付与処理が採番。本バッチは採番しない） |
| member_id | bigint | NOT NULL | | 会員ID（会員マスタは本案件スコープ外のため FK なし） |
| points | integer | NOT NULL | | 付与ポイント数（正の整数） |
| granted_date | date | NOT NULL | | 付与日 |
| expiry_date | date | NOT NULL | | 有効期限（この日までは利用可能。翌日以降に失効対象となる） |
| status | varchar(1) | NOT NULL | | 1=有効 / 2=失効（要件定義書 §3.4 の「有効」「失効」に対応するコード値） |
| expired_date | date | NULL | | 失効日（本バッチが `targetDate` を設定。`status='1'` の間は NULL） |
| created_at | timestamptz | NOT NULL | | 登録日時（ポイント付与処理が設定） |
| updated_at | timestamptz | NOT NULL | | 更新日時（本バッチが失効時に更新） |

- インデックス: `(status, expiry_date)`（失効対象の抽出用。**等値条件の `status` を先頭、範囲条件の `expiry_date` を後続**に置く。
  複合インデックスは先頭から順に絞り込むため、範囲条件を先頭に置くと後続列で絞り込めなくなる）、
  `(member_id)`（会員向け残高照会・CS対応での参照用。本バッチは使用しない）
- ステータス値は announcement-notification 案件の先例に合わせ `varchar(1)` のコード値で保持する
  （日本語文字列を直接格納しない）

#### point_expiry_history（失効履歴）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| point_expiry_history_id | bigint（`GENERATED ALWAYS AS IDENTITY`） | NOT NULL | PK | 失効履歴ID（DB 採番。先例 `app.mail_send` に合わせ `bigserial` ではなく標準SQLの IDENTITY 列を使う） |
| point_grant_id | bigint | NOT NULL | UQ, FK→point_grant | 失効させた付与のキー（**一意制約により1付与＝1履歴を DB で保証**） |
| member_id | bigint | NOT NULL | | 会員ID（失効時点のスナップショット） |
| points | integer | NOT NULL | | 失効したポイント数（付与時点の値） |
| granted_date | date | NOT NULL | | 付与日 |
| expiry_date | date | NOT NULL | | 有効期限 |
| expired_date | date | NOT NULL | | 失効日（ジョブパラメータ `targetDate`。実行時刻ではないためリランで値が変わらない） |
| created_at | timestamptz | NOT NULL | | 履歴登録日時（実処理時刻。監査用に実行時刻も残す） |

- 一意制約: `(point_grant_id)`（整合性要件の二重防御。§2 の整合性担保方式を参照）
- インデックス: `(member_id, expired_date)`（CS対応での会員別・期間別の照会用）
- 履歴は削除せず保持する（要件定義書 §5。保持期間の運用ルールは本案件では定めない＝未確定 No.2）

**Flyway マイグレーション**: `V4__create_member_point_expiry_tables.sql`
（上記2テーブルを app スキーマへ作成。SQL はスキーマ修飾必須。既存の V1〜V3 は変更しない）

## 5. 運用設計

| 項目 | 内容 |
|---|---|
| スケジュール（EventBridge） | EventBridge Scheduler で **日次 02:00** に起動し、`targetDate` に実行当日の日付を渡す（要件定義書 §4）。失効対象0件の日は正常終了（0件をエラーにしない） |
| 依存フロー（Step Functions 要否） | 不要（単独ジョブ。先行・後続ジョブなし。要件定義書 §4） |
| 失敗時の運用（通知先・リラン手順） | 終了コード1 を CloudWatch（終了コード・ログ）で検知する。原因（DB 接続断等）を除去後、**同一 `targetDate` でリスタート**する。トランザクションは全ロールバック済みのため部分適用の後始末は不要。数日リランできなかった場合も、次回実行（`expiry_date < targetDate`）で未失効分をまとめて回収できる（自己回復）。ただし**失効履歴の一意制約違反で失敗した場合はそのままリスタートしても再発する**ため、`point_expiry_history` に既存履歴を持つ付与が `status='1'`（有効）に戻っていないか等のデータ調査を先に行う。試走用途のため個別の通知先設計は行わない（要件定義書 §5・未確定 No.2） |
| 終了コードと運用アクション | 0=正常（対象0件を含む） / 2=警告: **本ジョブでは発生しない**（Skip なし） / 1=異常: 原因除去後リスタート（architecture.md §7 の規約から変更しない） |
| 監視 | 終了コードによる監視のみ（要件定義書 §5）。ジョブ/Step の開始・終了・所要時間は共通リスナー（`LoggingJobExecutionListener` / `LoggingStepExecutionListener`）の構造化ログで確認する。**失効件数**は `contribution.incrementWriteCount(件数)` により `BATCH_STEP_EXECUTION.WRITE_COUNT`・共通リスナーのログ・Tasklet の業務ログのいずれからも確認できる（§2 参照） |

## 6. 確認事項（設計中に判明した要確認点）

要件定義書 §6 の未確定 No.1（テーブルのカラム設計）は §4 で確定した。No.2（保持期間・監視通知）は
試走用途のため対象外（要件定義書の記載どおり）。加えて、本設計を進める中で以下2件が判明し、
ユーザーに確認のうえいずれも「現状維持」で確定した（2026-08-09）。

| # | 区分 | 事項 | 決定内容 |
|---|---|---|---|
| 1 | 確定 | 会員マスタテーブルの要否 | 要件定義書は会員IDのみを扱うと定めており（§5 セキュリティ）、会員マスタは本案件スコープ外と判断。`point_grant.member_id` に FK 制約を付けず論理参照とする（現状維持で確定。実案件化する場合は会員マスタの有無に応じてFK追加を再検討） |
| 2 | 確定 | `status` の取りうる値（ポイント利用・取消の扱い） | 要件定義書は「有効」「失効」の2値しか定めていないため `1=有効 / 2=失効` とする（現状維持で確定。実案件化する場合は業務側のステータス設計との突き合わせを再検討） |

## 7. 変更履歴

| 日付 | 版 | 変更内容 |
|---|---|---|
| 2026-08-09 | 1.0 | 初版作成 |
| 2026-08-09 | 1.1 | 設計レビュー指摘反映: 失効件数の確認手段を運用設計と統一（Tasklet の業務ログ。共通リスナーの `writeCount` は Tasklet では 0）、ジョブパラメータ不正の例外・検出タイミングを「未指定」と「型・書式不正」に分離、`(status, expiry_date)` インデックスの根拠を等値／範囲条件の順序で記述、失効履歴PKを `bigserial` → `GENERATED ALWAYS AS IDENTITY`（先例準拠）、パッケージ名の無番号化の根拠を明記、監査列と冪等性の関係を補足、一意制約違反時のリラン前調査を運用に追記、ポイント付与処理との同時実行時の扱いを追記 |
| 2026-08-18 | 1.2 | コードレビュー指摘へのユーザー回答を反映: 失効件数を `contribution.incrementWriteCount(件数)` で `StepContribution` へも反映する方針に変更（従来の「Taskletの業務ログのみ・writeCountは0のまま」から更新。§2・§5 を修正） |
