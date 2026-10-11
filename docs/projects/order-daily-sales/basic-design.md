# 基本設計書: 受注CSV取込・日次売上集計バッチ（order-daily-sales）

| 項目 | 内容 |
|---|---|
| 案件名 | 受注CSV取込・日次売上集計バッチ（order-daily-sales） |
| 入力（要件定義書） | docs/projects/order-daily-sales/requirements.md（2026-10-11 版・確定） |
| ステータス | レビュー待ち |

## 1. ジョブ一覧

| ジョブID | ジョブ名（Bean名） | 概要 | 方式 | 実行タイミング | 先行ジョブ |
|---|---|---|---|---|---|
| ORDER-001 | orderDailySalesJob | 対象日の受注CSVを受注明細へ取り込み（対象日分は洗い替え）、受注日×店舗の日次売上集計を作成する | Chunk（ワークへ取込）＋ Tasklet（チェック・洗い替え・集計） | 日次 02:00 | なし |

**ジョブ分割の検討**: 要件の処理一覧（No.1〜3）は実行タイミング（日次 02:00）・リラン単位（対象日）・
依存関係（取込→集計の順序固定）がすべて同じであり、別ジョブにする理由がないため **1ジョブ**とする。
取込と集計を別ジョブにすると、取込だけ成功して集計が未実行の状態を Step Functions で制御する必要が生じ、
運用が複雑になる。

**実装配置**: `batch-sample` モジュールにパッケージ `job_order_daily_sales` として追加する。
案件ジョブのため、ジョブ名・パッケージ名にサンプル番号を付けない（announcement-notification・
member-point-expiry 案件と同じ判断。architecture.md §11 の `jobNN_{内容}`・`{サンプル番号}{内容}Job` は
サンプルジョブ向けの規約）。

| 対象 | 名前 |
|---|---|
| ジョブ名（Bean名） | `orderDailySalesJob` |
| パッケージ | `com.example.batch.sample.job_order_daily_sales` |
| Job 設定クラス | `OrderDailySalesJobConfig` |
| Step 名の接頭辞 | `orderDailySales.` |

## 2. ジョブ方式設計

### ORDER-001: orderDailySalesJob

- **方式**: Chunk（ワークテーブルへの取込）＋ Tasklet（前処理・ワーク初期化・洗い替え＋集計）
- **選定理由**:
  - 取込は入力が件数上限のないファイル（最大10万件/日）であり、architecture.md §3.1 の判断基準
    「入力データが件数無制限（ファイル）→ Chunk」に該当する。不正行を Skip してエラーファイルへ退避し
    処理を継続する要件（要件定義書 §3.5）はレコード単位の Skip 制御が必要で、Chunk でのみ実現できる。
    サンプル01の選定基準「件数上限のないファイル取込」「不正レコードを退避して処理を継続したい」に合致する
  - 集計は `INSERT ... SELECT ... GROUP BY` の SQL 1本で表現できるため Tasklet とする
    （§3.1「集計 SQL 1本で完結する処理 → Tasklet」、§9.2「集計は SQL で行い Java 側で集計ループを避ける」。
    サンプル01「選ばない場面: 取込後の集計がSQL1本で済む → サンプル04（Tasklet）を検討」、
    サンプル04「選ぶ: 集計・変換が SQL で表現できる」）
  - ファイル存在・ヘッダのチェックと、ワークテーブルの初期化は単発処理のため Tasklet（§3.1「前処理・後処理 → Tasklet」）
  - ヘッダ行の照合は業務に依存しない汎用処理のため、業務側で独自に作らず **batch-core に
    `CsvHeaderCheckTasklet`（`com.example.batch.core.tasklet`）を追加**して使う（architecture.md §2
    「基盤部品に不足があれば batch-core を拡張する」）。入力ファイル・文字コード・期待するヘッダ項目名の並びを
    受け取り、1行目が一致しなければ `BusinessException` を送出する（`FileExistenceCheckTasklet` と同じ扱い）
  - **洗い替えはワークテーブル経由で行う**。architecture.md §4 は洗い替えの削除と登録を同一 Step（同一Tx）で
    行うことを求める（途中失敗時に旧データを残すため）。取込はチャンク単位でコミットする Chunk Step のため、
    本テーブルへ直接取り込むと削除と登録を同一Txにできない。そこで CSV はいったんワークテーブル
    （`order_detail_work`）へ取り込み、本テーブルの「対象日分の削除 → ワークからの登録 → 集計」を
    1つの Tasklet（1Tx）で行う。これにより、途中で失敗しても本テーブル（受注明細・日次売上集計）は
    旧データのまま残り、照会側に取込途中の状態が見えない
  - 10万件はサンプル01の「数十万件超で処理時間が課題 → パーティショニング」の閾値を下回り、
    05:00 の期限（3時間）にも十分収まるためパーティショニングは採用しない
- **Step構成（概要）**: 1 Step 1 責務（§3.2）。Step 間の受け渡しは DB（ワークテーブル `order_detail_work`）を介す

| # | Step名 | 方式 | 処理内容 |
|---|---|---|---|
| 1 | orderDailySales.01FileCheckStep | Tasklet（基盤部品 `FileExistenceCheckTasklet`） | 入力ファイルの存在・読取可能性を確認する。無ければ異常終了 |
| 2 | orderDailySales.02HeaderCheckStep | Tasklet（基盤部品 `CsvHeaderCheckTasklet`。本案件で batch-core に追加） | 入力ファイルの1行目（ヘッダ行）が IF-ORDER-I01 の項目名の並びと一致することを確認する。不一致なら異常終了 |
| 3 | orderDailySales.03ClearWorkStep | Tasklet | ワークテーブル `order_detail_work` を空にする（`TRUNCATE`） |
| 4 | orderDailySales.04ImportStep | Chunk | 受注CSVを読取（Reader）→ 検証・変換（Processor）→ ワークテーブルへ登録（Writer）。不正行は Skip しエラーファイルへ退避 |
| 5 | orderDailySales.05ReplaceStep | Tasklet | 1Tx で次の3つの SQL を順に実行する: ① 対象日の受注明細（`order_date = targetDate`）と日次売上集計（`sales_date = targetDate`）を削除 ② ワークの全行を受注明細へ `INSERT ... SELECT` ③ 対象日の受注明細を店舗ごとに集計し日次売上集計へ `INSERT ... SELECT ... GROUP BY` |

  - Step 5 は「洗い替え」と「集計」の2つの処理を持つが、両者を同一Txにすることが §4 の要件
    （失敗時に旧データを残す・明細と集計の不整合を見せない）であるため1 Step とする（§3.2 の
    「1 Step 1 責務」の例外。責務は「対象日の本テーブルを入れ替える」の1つとみなす）

- **Processor の検証内容**（`ValidatingItemProcessorBase` を継承。Bean Validation ＋ 業務チェック）:
  - 項目定義（IF-ORDER-I01）の必須・型・桁・値域
  - 受注日がジョブの対象日（`targetDate`）と一致すること
  - 同一ファイル内で受注番号＋明細番号が重複しないこと（2件目以降を業務エラーにする）。
    既出キーは Processor がメモリ上で **「キー → 最初に現れた行番号」の対応**として保持する（最大10万キー、数MB）。
    Skip やロールバックでチャンクが再処理されると、同じ行が Processor に再度渡される。このとき行番号が
    保持している行番号と同じなら同一行の再処理とみなし、重複として扱わない（行番号が異なる場合だけ重複）。
    このため入力レコードは行番号を持つ（Reader で付与する。付与方法は詳細設計で定める）
  - リスタート時は Step 開始時に、ワークテーブル（＝リスタート前にコミット済みの行）からキーと行番号を
    読み込み、対応を復元する。このためワークテーブルは行番号（`line_seq`）を持つ
  - 明細金額（単価×数量）を算出し、ワークの `amount` に設定する
- **リラン設計**:
  - **前提: JDBC ジョブリポジトリ**（`spring-boot-starter-batch-jdbc`。architecture.md §9.1）
  - **異常終了からの復旧は同一パラメータでリスタート**（§6.1）。失敗した Step から再開し、
    Step 4 は処理済みチャンクの続きから再開する（Reader は基盤部品 `CsvItemReaderFactory` の
    リスタート対応 `FlatFileItemReader`）。完了済みの Step 3（ワーク初期化）はリスタートで再実行されないため、
    ワークへのコミット済みの取込結果は消えない
  - **ECシステムから訂正ファイルを受け取った場合の再実行（洗い替え）**は、新しい `scheduledTime` と
    同じ `targetDate` を指定して新しい JobInstance として実行する。Step 5 が対象日の受注明細・集計を
    削除してから入れ直すため、二重計上は起きない（§6.2 冪等性。要件定義書 §5 リラン要件）
  - 成功済みジョブを同一パラメータで再実行するとエラーになるのは正しい挙動（§6.1）
  - ワーク・受注明細の主キー（受注番号＋明細番号）は二重登録の最終防御とする。Processor の重複検出を
    すり抜けて一意制約違反になった場合はシステムエラーとして異常終了する（設計上発生しない想定）。
    Step 5 で違反した場合（別の日に同じ受注番号＋明細番号が登録済みの場合）は Tx 全体がロールバックされ、
    本テーブルは旧データのまま残る
  - ワークテーブルは次回実行の Step 3 で空にするまで残す（異常時の調査に使えるようにする）。
    二重起動防止によりジョブは同時に1つしか動かないため、ワークを対象日で分ける必要はない
  - 二重起動防止: 基盤部品 `DuplicateRunPreventingJobExecutionListener` ＋ EventBridge 側の
    同時実行抑止（§6.3 多層防御）
- **エラー処理**（事象ごとの分類と動作）:

| エラー事象 | 分類 | 例外 | 動作 | 終了コード |
|---|---|---|---|---|
| 入力ファイルが存在しない・読めない | 前処理エラー | `BusinessException`（`FileExistenceCheckTasklet`） | Step 1 で失敗 | 1（異常） |
| ヘッダ行が想定と異なる | 前処理エラー | `BusinessException` | Step 2 で失敗（取込を始めない） | 1（異常） |
| CSV としてパースできない行（列数不一致等） | 業務エラー | `FlatFileParseException`（`BusinessSkipPolicy` が Skip 対象として扱う） | Skip しエラーファイルへ退避 | 完走すれば 2（警告） |
| 必須欠落・型・桁・値域の不正 | 業務エラー | `BusinessException` | Skip しエラーファイルへ退避 | 完走すれば 2（警告） |
| 受注日が対象日と不一致 | 業務エラー | `BusinessException` | Skip しエラーファイルへ退避 | 完走すれば 2（警告） |
| 同一ファイル内の受注番号＋明細番号の重複 | 業務エラー | `BusinessException` | 2件目以降を Skip しエラーファイルへ退避 | 完走すれば 2（警告） |
| Skip 件数が `skipLimit` を超過 | 業務エラーの閾値超過 | `SkipLimitExceededException` | Step 4 で失敗 | 1（異常） |
| DB 接続断・一意制約違反（想定外）・設定不備 | システムエラー | `SystemException` 等 | 即時異常終了（Skip しない） | 1（異常） |

  - `skipLimit` は既定 **10**（§5.2）とし、設定で外部化する。超過時は入力ファイル自体の品質問題とみなして
    異常終了させ、ECシステムへ訂正ファイルを依頼する
  - Skip した行は基盤部品 `ErrorRecordSkipListener` でエラーファイルへ退避する（元レコード＋エラー理由）
  - 警告終了（終了コード2）の条件: Skip が1件以上発生して完走（§5.3）
  - **Skip 件数と警告判定は実行回（JobExecution）ごと**とする（基盤部品 `LoggingJobExecutionListener`・
    `BusinessSkipPolicy` の標準動作のまま。前回の実行回から引き継がない）。このため次の2点に注意する
    - 1回目の実行で Skip が発生して異常終了し、リスタートで Skip が発生しなかった場合、終了コードは 0 になる。
      1回目の Skip 行はエラーファイルに残っている（リスタートは同じファイルへ追記する）ため、
      **運用者は異常終了からのリスタート後、終了コードにかかわらずエラーファイルを確認する**（§5 運用設計）
    - `skipLimit` も実行回ごとに数えるため、リスタートをまたいだ合計は `skipLimit` を超えうる
  - Step 1〜5 のどこで失敗しても、本テーブル（受注明細・日次売上集計）は旧データのまま変わらない
    （本テーブルを変更するのは Step 5 だけで、Step 5 は1Txのため）。Step 4 の失敗ではワークに
    コミット済みチャンクまでが残り、リスタートでその続きから取り込む
- **ジョブパラメータ**:

| パラメータ名 | 型 | 必須 | 例 | 説明 |
|---|---|---|---|---|
| scheduledTime | String（ISO-8601 のオフセット付き日時） | ○ | 2026-10-11T17:00:00Z | 起動時刻。EventBridge Scheduler の実行予定時刻（`<aws.scheduler.scheduled-time>`。**UTC**）をそのまま渡す。手動実行では `2026-10-12T09:30:00+09:00` のようにオフセット付きで指定する。JobInstance の識別に使用する。リスタート時は同一値、訂正ファイルでの再実行時は実行時の時刻を指定する |
| targetDate | java.time.LocalDate | - | 2026-10-11 | 対象日（受注日）。省略時は `scheduledTime` を **Asia/Tokyo に変換した日付の前日**とする（要件定義書 §4。例: `2026-10-11T17:00:00Z` → JST 2026-10-12 02:00 → 対象日 2026-10-11） |
| inputFile | String | - | /data/in/orders_20261011.csv | 受注CSVのパス。省略時は設定値の入力ディレクトリ配下の `orders_{targetDate:yyyyMMdd}.csv` |
| errorFile | String | - | /data/error/orders_20261011_20261012020000_error.txt | エラーファイルのパス。省略時は設定値のエラーディレクトリ配下の `orders_{targetDate:yyyyMMdd}_{scheduledTime(JST):yyyyMMddHHmmss}_error.txt`（実行ごとに別ファイル。§3.1 IF-ORDER-E01） |

  - `scheduledTime` は UTC で渡ってくるため、日付の導出に使う前に必ず Asia/Tokyo へ変換する
    （UTC のまま日付を取ると JST 02:00 が前日 17:00 になり、2日前を処理してしまう）。
    Spring Batch の標準変換で扱える型ではないため String で受け取り、ジョブ内で `OffsetDateTime` として解釈する
  - 検証: `scheduledTime` の必須に加え、オフセット付き ISO-8601 として解釈できることを起動直後に検証する
    （`JobParametersValidators.requiredAndOptional` ＋ 書式チェックの Validator。不正なら Step 実行前に異常終了＝1）
  - `targetDate`・`inputFile`・`errorFile` の省略時の補完は詳細設計で定める（EventBridge Scheduler は
    日付演算・パス組み立てができないため、ジョブ側で補完する）
- **処理量とチャンクサイズ**:
  - 想定件数: 1日最大 **10万件**（要件定義書 §3.2・§5）
  - チャンクサイズ初期値 **100**（§3.3。設定で外部化）。コミット約1,000回、`MyBatisBatchItemWriter`
    （バッチ実行モード）による一括 INSERT で Step 4（ワークへの取込）は数分を見込む
  - Step 5 は1Txで最大10万件の削除＋10万件の `INSERT ... SELECT`＋集計を行う。すべて DB 内で完結する
    SQL 3本で、`(order_date, store_code)` インデックスにより対象日の行を特定するため、**数十秒以内**を見込む。
    architecture.md §4 は長大な更新を Tasklet にしないことを求めるが、この規模ならロック時間・undo 量とも
    許容範囲と判断する（対象日の行だけをロックし、他の日の照会・更新を妨げない）
  - 全体で十数分以内を見込み、05:00 の期限（3時間）に対し十分な余裕がある
  - 方式見直しの閾値: 1日の件数が **10万件**を超えるようになった場合、Step 5 の1Tx の大きさを再評価する
    （一時テーブル＋`RENAME` 方式など。architecture.md §4）。数十万件を超え Step 4 の処理時間が課題になった場合は、
    チャンクサイズ調整 → パーティショニング（サンプル07）の順で検討する（要件定義書 未確定 No.2）

## 3. IF設計

### 3.1 ファイルIF

| IF-ID | ファイル名 | 形式 | 文字コード | 方向 | レイアウト |
|---|---|---|---|---|---|
| IF-ORDER-I01 | orders_{yyyyMMdd}.csv（`inputFile` で指定可） | CSV（カンマ区切り・ヘッダ行あり・改行コード LF/CRLF） | UTF-8（BOMなし） | 入力 | 下表 |
| IF-ORDER-E01 | orders_{targetDate:yyyyMMdd}_{scheduledTime(JST):yyyyMMddHHmmss}_error.txt（`errorFile` で指定可） | テキスト（1行1レコード、改行コード LF） | UTF-8 | 出力 | 基盤部品 `ErrorRecordSkipListener` の標準形式（`元レコード<TAB>エラー理由`）。**追記**で書く（同部品の動作）。既定のファイル名は実行ごとに異なるため、訂正ファイルでの再実行では新しいファイルになり、リスタートでは同じファイルに追記される。Skip が0件ならファイルは作られない |

**IF-ORDER-I01 レイアウト**（要件定義書 §3.2 の案。ヘッダ行の項目名は下表の「ヘッダ名」で固定）:

| No | 項目名 | ヘッダ名 | 型 | 桁数 | 必須 | 備考 |
|---|---|---|---|---|---|---|
| 1 | 受注番号 | order_no | 英数字 | 20以内 | ○ | |
| 2 | 明細番号 | line_no | 整数 | 1以上 | ○ | 受注番号＋明細番号で一意 |
| 3 | 受注日 | order_date | 日付（yyyy-MM-dd） | 10 | ○ | 対象日と一致すること |
| 4 | 店舗コード | store_code | 英数字 | 10以内 | ○ | |
| 5 | 商品コード | item_code | 英数字 | 20以内 | ○ | |
| 6 | 数量 | quantity | 整数 | 1以上 | ○ | |
| 7 | 単価 | unit_price | 整数 | 0以上 | ○ | 円・税抜 |

- 値の前後の空白は不正とする（トリムしない）。クォートは CSV 標準（ダブルクォート）に従う
- 数量・単価の上限は、明細金額（単価×数量）が `bigint` に収まる範囲で詳細設計で定める

### 3.2 API IF

なし

## 4. テーブル設計（app スキーマ）

### 4.1 テーブル一覧

| テーブル名 | 用途 | 新規/変更 | 想定件数 |
|---|---|---|---|
| order_detail_work | 受注明細ワーク（取込中の一時置き場。Step 3 で毎回空にする） | 新規 | 最大10万件（1日分） |
| order_detail | 受注明細（受注CSVの1行=1レコード） | 新規 | 最大10万件/日（保持期間は未確定。要件定義書 未確定 No.3） |
| daily_store_sales | 日次売上集計（受注日×店舗） | 新規 | 店舗数/日 |

※ `order` は SQL の予約語のため、テーブル名は `order_detail` とする。

### 4.2 テーブル定義

#### order_detail_work（受注明細ワーク）

`order_detail` と同じカラム構成・同じ主キー（`order_no`, `line_no`）に、次の1列を加える（他の列は下記
order_detail の定義を参照）。インデックスは主キーのみ（Step 5 は全件を読むため）。

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| line_seq | integer | NOT NULL | | 入力ファイル上の行番号（ヘッダ行を1行目とする物理行番号）。リスタート時の重複検出の復元に使う（§2）。order_detail へは引き継がない |

`created_at` には取込時刻を入れ、Step 5 で
order_detail へそのまま引き継ぐ。

#### order_detail（受注明細）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| order_no | varchar(20) | NOT NULL | PK(1) | 受注番号 |
| line_no | integer | NOT NULL | PK(2) | 明細番号 |
| order_date | date | NOT NULL | | 受注日 |
| store_code | varchar(10) | NOT NULL | | 店舗コード |
| item_code | varchar(20) | NOT NULL | | 商品コード |
| quantity | integer | NOT NULL | | 数量 |
| unit_price | integer | NOT NULL | | 単価（円・税抜） |
| amount | bigint | NOT NULL | | 明細金額（単価×数量。取込時に算出） |
| created_at | timestamptz | NOT NULL | | 登録日時（実処理時刻。監査用） |

- インデックス: `(order_date, store_code)`（対象日の削除と集計用）
- 主キーは受注日を含まないため、別の日のファイルに同じ受注番号＋明細番号があると一意制約違反になる。
  受注番号は日をまたいで一意である前提とする（要件定義書 未確定 No.1 の IF 仕様で確認する）

#### daily_store_sales（日次売上集計）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| sales_date | date | NOT NULL | PK(1) | 受注日 |
| store_code | varchar(10) | NOT NULL | PK(2) | 店舗コード |
| order_count | integer | NOT NULL | | 受注件数（受注番号の重複を除いた件数） |
| line_count | integer | NOT NULL | | 明細件数 |
| total_quantity | bigint | NOT NULL | | 売上数量（数量の合計） |
| total_amount | bigint | NOT NULL | | 売上金額（明細金額の合計。円・税抜） |
| created_at | timestamptz | NOT NULL | | 登録日時（実処理時刻。監査用） |

- 受注が0件の店舗はレコードを作らない（要件定義書 §3.4）

**Flyway マイグレーション**: `V5__create_order_daily_sales_tables.sql`（上記3テーブルを app スキーマへ作成。SQL はスキーマ修飾必須）

## 5. 運用設計

| 項目 | 内容 |
|---|---|
| スケジュール（EventBridge） | EventBridge Scheduler で毎日 **02:00（JST。スケジュールのタイムゾーンを Asia/Tokyo に設定）** に起動し、`scheduledTime` に `<aws.scheduler.scheduled-time>`（UTC）をそのまま渡す。`targetDate`・`inputFile`・`errorFile` は省略する（前日分を既定パスから取り込む） |
| 依存フロー（Step Functions 要否） | 不要（単独ジョブ。ECシステムとはファイル配置で疎結合。売上照会・帳票とは集計テーブル経由） |
| 失敗時の運用（通知先・リラン手順） | 終了コード1: CloudWatch（終了コード・ログ）で検知し運用者へ通知。ファイル未着・ヘッダ不正・Skip 上限超過は ECシステムへ訂正ファイルを依頼し、新しい `scheduledTime`＋同じ `targetDate` で再実行（洗い替え）。システムエラーは原因除去後に同一パラメータでリスタート（リスタート後は終了コードにかかわらずエラーファイルを確認する。§2 エラー処理）。終了コード2: エラーファイルを確認し、必要なら訂正ファイルで再実行 |
| 終了コードと運用アクション | 0=正常 / 2=警告: エラーファイル確認 / 1=異常: 原因除去後リスタートまたは訂正ファイルで再実行 |
| 処理時間の監視 | 05:00 までに完了しない場合は運用者へ通知する（EventBridge / CloudWatch の設定。本バッチ側の実装は不要） |

## 6. 変更履歴

| 日付 | 版 | 変更内容 |
|---|---|---|
| 2026-10-11 | 1.0 | 初版 |
| 2026-10-11 | 1.1 | 洗い替えをワークテーブル経由に変更（architecture.md §4 の「削除と登録を同一Tx」に合わせる）。Step 3 を ClearWorkStep に、Step 5 を ReplaceStep（削除＋登録＋集計を1Tx）に変更し、order_detail_work を追加 |
| 2026-10-11 | 1.2 | 設計レビュー指摘の反映: `scheduledTime` を UTC のオフセット付き文字列で受け JST に変換して対象日を導出／エラーファイルを実行ごとに別名・追記と明記／重複検出をチャンク再処理に耐える方式（キー→行番号）に変更しワークに `line_seq` を追加／Skip・警告判定が実行回ごとである旨と運用を明記／Step 5 の Tx 規模の根拠と見直し閾値を追記／ヘッダ照合を batch-core の `CsvHeaderCheckTasklet` に変更／Step 間受け渡しの記述を修正／`inputFile`・`errorFile` を任意とし要件定義書を改訂 |
