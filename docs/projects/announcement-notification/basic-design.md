# 基本設計書: お知らせ通知バッチ（announcement-notification）

| 項目 | 内容 |
|---|---|
| 案件名 | お知らせ通知バッチ（announcement-notification） |
| 入力（要件定義書） | docs/projects/announcement-notification/requirements.md（2026-07-15 版） |
| ステータス | レビュー待ち |

## 1. ジョブ一覧

| ジョブID | ジョブ名（Bean名） | 概要 | 方式 | 実行タイミング | 先行ジョブ |
|---|---|---|---|---|---|
| ANNOUNCE-001 | announcementNotifyJob | 未処理のお知らせから通知対象ユーザーを抽出し、メール送信テーブルへ送信依頼を登録する | Chunk（前後処理は Tasklet） | 随時（EventBridge Scheduler 15分間隔ポーリング） | なし |

ジョブ分割の検討: 要件の処理一覧（No.1〜5）は同一の実行タイミング・同一のリラン単位（お知らせの処理済み管理）で
完結するため **1ジョブ**とする。郵便番号マスタの整備（取込）は提供元が未確定のため本案件のスコープ外とし、
確定後に別ジョブ（別案件）として設計する。

実装配置: `batch-sample` モジュールにパッケージ `job_announcement_notify` として追加する（§2.1 パッケージ・バイ・フィーチャー）。
ジョブ名は案件ジョブのため基本設計テンプレートの形式（`{内容}Job`）に従い、サンプル番号プレフィックスは付けない
（詳細設計レビューにて、パッケージ名にサンプル番号由来の連番規約（`jobNN_`）を残すとジョブ名・Step名・
Configクラス名（いずれも無番号）と内部不整合になると指摘されたため、パッケージ名も番号を外して統一した）。

## 2. ジョブ方式設計

### ANNOUNCE-001: announcementNotifyJob

- **方式**: Chunk（本処理）＋ Tasklet（前後処理）
- **選定理由**:
  - 本処理（通知対象の抽出→検証→メール送信依頼登録）は、入力が「お知らせ×対象ユーザー」の直積で
    件数上限がない（最大数十万件）ため、architecture.md §3.1 の判断基準
    「入力データが件数無制限（テーブル）→ Chunk」に該当する
  - 不正レコード（メールアドレス未設定等）を Skip して残りを通知する要件（要件定義書 §3.5）があり、
    レコード単位の Skip 制御は Chunk でのみ可能（§3.1 の表）。
    サンプル01の選定基準「不正レコードを退避して処理を継続したい → Chunk」に合致する
  - `INSERT ... SELECT` 1本の Tasklet（サンプル04方式）も検討したが、レコード単位の検証・Skip・
    エラーレコード退避ができないため不採用（サンプル04「選ばない場面: 1件ごとに複雑なJavaロジックが必要 → Chunk」）
  - 前後処理（対象確定・展開不能チェック・処理済み更新）は SQL 一括実行の単発処理のため Tasklet（§3.1）
- **Step構成（概要）**: 1 Step 1 責務（§3.2）。Step 間の受け渡しはお知らせのステータス（DB）を介す

| # | Step名 | 方式 | 処理内容 |
|---|---|---|---|
| 1 | announcementNotify.01PickupStep | Tasklet | `status=未処理(0)` のお知らせを `処理中(1)` に一括更新し、今回実行の対象を確定する（前回異常終了の `処理中` 残骸も後続 Step の対象に含まれるため自己回復する） |
| 2 | announcementNotify.02RegionCheckStep | Tasklet | `処理中` のお知らせのうち、通知区域が郵便番号マスタで1件も郵便番号に展開できないものを `エラー(9)` に一括更新する。1件以上エラー化した場合は警告終了の条件とする |
| 3 | announcementNotify.03RegisterMailStep | Chunk | `処理中` のお知らせについて通知対象ユーザーを抽出（Reader）→ 検証（Processor）→ メール送信テーブルへ登録（Writer） |
| 4 | announcementNotify.04MarkProcessedStep | Tasklet | `処理中` のお知らせを `処理済み(2)` に一括更新し、処理日時を記録する |

- **通知対象の抽出ルール（Step 3 Reader の SELECT。DISTINCT で重複排除）**:
  1. 本人: 住所の郵便番号が「通知区域を郵便番号マスタで展開した郵便番号リスト」に完全一致するユーザー
  2. 家族: 郵便番号未登録のユーザーのうち、家族関係テーブルで 1. のユーザーと紐づく者（区域内世帯と同居とみなす）。
     郵便番号を持つユーザーは自身の住所のみで判定する（家族関係では対象にならない）
  - 同一お知らせ×同一ユーザーは 1 件に重複排除（UNION ＋ 主キー制約）
  - **メールアドレス未設定のユーザーは抽出条件で除外する**（通知不能＝業務上の対象外であり、データ不正（Skip）として扱わない。
    skipLimit 到達による異常終了を防ぐ）
  - 抽出 SQL は **`ORDER BY announcement_id, user_id` を必須**とする（リスタート時にカーソル再開位置が
    ずれないよう順序を安定させる。§6.1）
- **リラン設計**:
  - **前提: JDBC ジョブリポジトリ**。Boot 4 の既定 JobRepository はインメモリ（Resourceless）でリスタート不能のため、
    `spring-boot-starter-batch-jdbc` を依存に追加しメタデータを `batch` スキーマへ永続化する（§9.1）
  - 失敗時は**同一パラメータでリスタート**（失敗 Step から再開。Chunk Step は処理済みチャンクの続きから。§6.1）
  - 冪等性: メール送信テーブルは `(announcement_id, user_id)` の一意制約＋ `ON CONFLICT DO NOTHING` で
    登録済みレコードを読み飛ばす（§6.2 の UPSERT 方式）。リスタートせず次回の定期実行に任せても、
    Step 1 が `処理中` 残骸を対象に含めて再処理するため二重通知は発生しない
  - Reader は `MyBatisCursorItemReader`（リスタート対応。§6.1）
  - 二重起動防止: 基盤部品のジョブ実行制御＋ EventBridge 側の同時実行抑止（§6.3 多層防御）
- **エラー処理**（事象ごとの分類と動作）:

| エラー事象 | 分類 | 例外 | 動作 | 終了コード |
|---|---|---|---|---|
| メールアドレス形式不正 | 業務エラー | `BusinessException` | Skip（`skipLimit=10`）し、エラーレコードファイルへ退避（元レコードキー＋理由。§5.2） | 完走すれば 2（警告） |
| メールアドレス未設定 | 業務上の対象外（エラーではない） | - | Reader の抽出条件で除外（Skip・エラー記録の対象にしない） | 影響なし |
| 通知区域が郵便番号に展開不能 | 業務エラー（お知らせ単位） | - | Step 2 でお知らせを `エラー(9)` に更新し、他のお知らせは処理継続 | 1件以上で 2（警告） |
| DB 接続断・マスタテーブル不存在・設定不備 | システムエラー | `SystemException` | 即時異常終了（Skip しない） | 1（異常） |

  - 警告終了（終了コード2）の条件: Skip が1件以上発生、**または** Step 2 で展開不能お知らせを1件以上エラー化
    （`COMPLETED_WITH_WARNING`。§5.3・§7。エラー化したお知らせは業務側で区域を修正のうえ `未処理` へ戻して再実行）
- **ジョブパラメータ**:

| パラメータ名 | 型 | 必須 | 例 | 説明 |
|---|---|---|---|---|
| scheduledTime | java.time.LocalDateTime | ○ | 2026-07-15T09:15:00 | 起動時刻（EventBridge Scheduler の実行予定時刻を渡す）。JobInstance の識別に使用。リスタート時は同一値を指定する |
| errorFile | String | ○ | /data/error/announce_error.txt | Skip レコードの退避先ファイルパス |

- **処理量とチャンクサイズ**: 通知対象は区域規模に依存し最大で数十万件／回（要件定義書 §5）。
  チャンクサイズ初期値 **100**（§3.3。設定で外部化）。`MyBatisBatchItemWriter`（バッチ実行モード）による
  一括 INSERT で 30万件でも十数分以内を見込む。性能測定の結果、15分間隔に収まらない場合は
  チャンクサイズ調整→パーティショニング（サンプル07）の順で検討する

## 3. IF設計

### 3.1 ファイルIF

| IF-ID | ファイル名 | 形式 | 文字コード | 方向 | レイアウト |
|---|---|---|---|---|---|
| IF-ANNOUNCE-E01 | announce_error.txt（errorFile パラメータで指定） | テキスト（1行1レコード、改行コード LF） | UTF-8 | 出力 | 基盤部品 `ErrorRecordSkipListener` の標準形式（元レコード＋エラー理由） |

エラーレコードの元レコード部: `お知らせID, ユーザーID, メールアドレス`（個人情報のため氏名・住所は出力しない）

### 3.2 API IF

なし（入出力はすべて DB）

## 4. テーブル設計（app スキーマ）

### 4.1 テーブル一覧

| テーブル名 | 用途 | 新規/変更 | 想定件数 |
|---|---|---|---|
| announcement | お知らせ（業務システムが登録、本バッチがステータス管理） | 新規 | 数十件/日 |
| announcement_region | 通知区域（お知らせに紐づく都道府県・市町村） | 新規 | お知らせあたり数件 |
| app_user | ユーザー（住所・メールアドレス） | 新規 | 数万〜数十万件 |
| family_relation | 家族関係（ユーザー間の紐づけ） | 新規 | ユーザー数に比例 |
| zip_code_master | 郵便番号マスタ（都道府県・市町村→郵便番号） | 新規 | 約12万件 |
| mail_send | メール送信依頼（本バッチの出力。送信基盤が読み取る） | 新規 | 最大数十万件/回（送信基盤の保持期間運用に従い削除） |

※ 全テーブル新規設計は要件定義書 未確定 No.5 の前提による（業務システム側テーブルとの分担が確定したら見直す）。
※ `user` は PostgreSQL の予約語のため `app_user` とする。

### 4.2 テーブル定義

#### announcement（お知らせ）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| announcement_id | bigint | NOT NULL | PK | お知らせID（業務システムが採番） |
| title | varchar(200) | NOT NULL | | 件名（メール件名に使用） |
| body | text | NOT NULL | | 本文（メール本文に使用） |
| status | varchar(1) | NOT NULL | | 0=未処理 / 1=処理中 / 2=処理済み / 9=エラー（区域展開不能） |
| created_at | timestamptz | NOT NULL | | 登録日時（業務システムが設定） |
| processed_at | timestamptz | NULL | | 処理済み更新日時（本バッチが設定） |

- インデックス: `(status)`（未処理・処理中の抽出用）

#### announcement_region（通知区域）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| announcement_id | bigint | NOT NULL | PK(1), FK→announcement | お知らせID |
| region_seq | integer | NOT NULL | PK(2) | 区域連番 |
| prefecture_code | varchar(2) | NOT NULL | | 都道府県コード（JIS X 0401） |
| city_code | varchar(5) | NULL | | 市区町村コード（JIS X 0402）。NULL は都道府県全域を示す |

#### app_user（ユーザー）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| user_id | bigint | NOT NULL | PK | ユーザーID |
| user_name | varchar(100) | NOT NULL | | 氏名 |
| email | varchar(254) | NULL | | メールアドレス（未設定ユーザーは Skip 対象） |
| zip_code | varchar(7) | NULL | | 住所の郵便番号（ハイフンなし7桁）。NULL は住所未登録（家族ルールの対象） |

- インデックス: `(zip_code)`（区域突合用）

#### family_relation（家族関係）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| user_id | bigint | NOT NULL | PK(1), FK→app_user | ユーザーID |
| family_user_id | bigint | NOT NULL | PK(2), FK→app_user | 家族のユーザーID |

- 双方向の関係は 2 行で表現する（業務システムが両方向を登録する前提）
- インデックス: `(family_user_id)`（逆引き用）

#### zip_code_master（郵便番号マスタ）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| zip_code | varchar(7) | NOT NULL | PK(1) | 郵便番号（ハイフンなし7桁） |
| prefecture_code | varchar(2) | NOT NULL | | 都道府県コード（JIS X 0401） |
| city_code | varchar(5) | NOT NULL | PK(2) | 市区町村コード（JIS X 0402） |

- インデックス: `(prefecture_code)`、`(city_code)`（都道府県指定・市区町村指定のいずれも等値結合で突合する。前方一致は使わない）
- データ整備（取込・更新）は本案件スコープ外（要件定義書 未確定 No.1）。ローカル・テストではシードデータを使用する

#### mail_send（メール送信依頼）

| カラム名 | 型 | NULL | キー | 説明 |
|---|---|---|---|---|
| mail_send_id | bigserial | NOT NULL | PK | 送信依頼ID |
| announcement_id | bigint | NOT NULL | UQ(1), FK→announcement | お知らせID |
| user_id | bigint | NOT NULL | UQ(2), FK→app_user | 通知対象ユーザーID |
| email | varchar(254) | NOT NULL | | 宛先メールアドレス（登録時点のスナップショット） |
| subject | varchar(200) | NOT NULL | | 件名（お知らせ title のスナップショット） |
| body | text | NOT NULL | | 本文（お知らせ body のスナップショット） |
| send_status | varchar(1) | NOT NULL | | 0=未送信（送信基盤が更新。本バッチは 0 で登録するのみ） |
| created_at | timestamptz | NOT NULL | | 登録日時 |

- 一意制約: `(announcement_id, user_id)`（重複排除・リラン冪等性の担保。Writer は `ON CONFLICT DO NOTHING`）
- インデックス: `(send_status)`（送信基盤の未送信抽出用）

**Flyway マイグレーション**: `V3__create_announcement_notification_tables.sql`（上記6テーブルを app スキーマへ作成。SQL はスキーマ修飾必須）

## 5. 運用設計

| 項目 | 内容 |
|---|---|
| スケジュール（EventBridge） | EventBridge Scheduler で **15分間隔**に起動（`scheduledTime` に実行予定時刻を渡す）。処理済み管理により対象0件の空振り実行は正常終了（0件はエラーにしない） |
| 実行が起動間隔を跨ぐ場合 | 前回実行中に次回起動時刻が到来した場合、二重起動防止（基盤部品の実行中チェック＋EventBridge の同時実行抑止。§6.3）により**次回分は起動せずスキップ**され、未処理分は次々回実行が処理する（処理済み管理により取りこぼしなし）。跨ぎが常態化する場合は性能改善（チャンクサイズ調整→パーティショニング）を実施する |
| 依存フロー（Step Functions 要否) | 不要（単独ジョブ。先行・後続ジョブなし。メール送信基盤とは mail_send テーブル経由の疎結合） |
| 失敗時の運用（通知先・リラン手順） | 終了コード1: CloudWatch（終了コード・ログ）で検知し、原因除去後に同一 `scheduledTime` でリスタート。リスタートしない場合も次回定期実行が `処理中` 残骸を自己回復する。終了コード2: エラーレコードファイルと `status=9` のお知らせを確認し、データ修正後に `status=0` へ戻して次回実行に委ねる |
| 終了コードと運用アクション | 0=正常 / 2=警告: エラーレコード・エラーお知らせ確認 / 1=異常: 原因除去後リスタート（architecture.md §7） |

## 6. 変更履歴

| 日付 | 版 | 変更内容 |
|---|---|---|
| 2026-07-15 | 1.0 | 初版作成 |
| 2026-07-15 | 1.1 | 設計レビュー指摘反映（JDBC ジョブリポジトリ前提の明記、エラー処理の表形式化、メールアドレス未設定を Skip から抽出時除外へ変更、改行コード LF 明記、zip_code_master へ prefecture_code 列追加、Reader の ORDER BY 必須化、起動間隔を跨ぐ場合の運用追記、実装配置の明記） |
| 2026-08-08 | 1.2 | コードレビュー指摘反映: 実装パッケージ名を `job10_announcement_notify` → `job_announcement_notify` へ修正（詳細設計書・実装コードとの不一致を解消。基本設計書側の更新漏れだった） |
