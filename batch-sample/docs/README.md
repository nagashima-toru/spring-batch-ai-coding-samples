# サンプルジョブ一覧

基盤部品（batch-core）の使い方を示すリファレンス実装。
各サンプルの README に「このパターンを選ぶべき場面 / 選ばない場面」を記載しているので、
案件での方式選定の判断材料にすること。

## コアサンプル（フル実装・テスト付き）

| # | ドキュメント | パターン | 型 |
|---|---|---|---|
| 01 | [sample01-csv-import.md](sample01-csv-import.md) | CSV → DB 取込（Skip・エラー退避・警告終了） | Chunk |
| 02 | [sample02-fixedlength-import.md](sample02-fixedlength-import.md) | 固定長(Shift_JIS) → DB 取込 | Chunk |
| 03 | [sample03-file-export.md](sample03-file-export.md) | DB → CSV 抽出（ヘッダ/トレーラ・カーソル） | Chunk |
| 04 | [sample04-db-aggregate.md](sample04-db-aggregate.md) | DB → DB 集計・洗い替え（truncate-insert） | Tasklet |
| 05 | [sample05-api-import.md](sample05-api-import.md) | REST API → DB（ページング・認証） | Chunk |
| 06 | [sample06-flow-job.md](sample06-flow-job.md) | 複合Flow・条件分岐・**リスタートデモ** | Flow |

## 拡張サンプル（骨格＋解説）

| # | ドキュメント | パターン |
|---|---|---|
| 07 | [sample07-partitioning.md](sample07-partitioning.md) | パーティショニング並列処理・性能設計 |
| 08 | [sample08-s3-io.md](sample08-s3-io.md) | S3入出力のプロファイル切替（AWS非依存開発） |
| 09 | [sample09-notification.md](sample09-notification.md) | 異常終了時の Slack/SNS 通知 |

## 基盤部品別の実例ジョブ索引

implementation Skill の「最重要ルール」表にある基盤部品ごとに、batch-sample のどのジョブが実例かを引ける索引。
部品の使い方に迷ったら、該当行のジョブを参照すること。

| 部品 | 実例（ジョブのパッケージ / クラス） |
|---|---|
| `BatchHttpClient` | `job05_api_import` / `Sample05JobConfig`、`job09_notification` / `SlackWebhookNotificationSender` |
| `CsvItemReaderFactory` | `job01_csv_import` / `Sample01JobConfig`、`job06_flow_job` / `Sample06JobConfig`、`job_order_daily_sales` / `OrderDailySalesJobConfig` |
| `FixedLengthItemReaderFactory` | `job02_fixedlength_import` / `Sample02JobConfig` |
| `JsonItemReaderFactory` | `job05_api_import` / `Sample05JobConfig`（`inputFile` 指定時のリプレイ取込） |
| `CsvItemWriterFactory` | `job03_file_export` / `Sample03JobConfig` |
| `FixedLengthItemWriterFactory` | `job03_file_export` / `Sample03JobConfig`（`format=fixed` の固定長出力） |
| `RestApiPagingItemReader` | `job05_api_import` / `Sample05JobConfig` |
| `CountingItemStreamReader` | `job05_api_import` / `Sample05JobConfig`（件数 API と突き合わせる取得件数） |
| `ValidatingItemProcessorBase` | `job01_csv_import` / `CustomerItemProcessor`、`job02_fixedlength_import` / `ProductItemProcessor`、`job_order_daily_sales` / `OrderItemProcessor` |
| `BusinessSkipPolicy` | `job01_csv_import` / `Sample01JobConfig`、`job02_fixedlength_import` / `Sample02JobConfig`、`job_announcement_notify` / `AnnouncementNotifyJobConfig` |
| `ErrorRecordSkipListener` | `job01_csv_import` / `Sample01JobConfig`、`job02_fixedlength_import` / `Sample02JobConfig`、`job_announcement_notify` / `AnnouncementNotifyJobConfig` |
| `FileExistenceCheckTasklet` | `job06_flow_job` / `Sample06JobConfig`、`job_order_daily_sales` / `OrderDailySalesJobConfig` |
| `FileMoveTasklet` | `job03_file_export` / `Sample03JobConfig`（一時出力→配置） |
| `CsvHeaderCheckTasklet` | `job_order_daily_sales` / `OrderDailySalesJobConfig` |
| `AbstractApiCallTasklet` | `job05_api_import` / `ApiUserCountTasklet` |
| `TableCountCheckTasklet` | `job04_db_aggregate` / `Sample04JobConfig`、`job05_api_import` / `Sample05JobConfig`（件数 API との突き合わせ） |
| `JobParametersValidators` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_order_daily_sales` / `OrderDailySalesJobParametersValidator`（独自 Validator への拡張例） |
| `LoggingJobExecutionListener` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_point_expiry` / `PointExpiryJobConfig` |
| `LoggingStepExecutionListener` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_point_expiry` / `PointExpiryJobConfig` |
| `DuplicateRunPreventingJobExecutionListener` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_point_expiry` / `PointExpiryJobConfig` |
| `BusinessException` | `job01_csv_import` / `CustomerItemProcessor`、`job_order_daily_sales` / `OrderItemProcessor` |
| `SystemException` | `job06_flow_job` / `WorkItemProcessor` |
| `RetryableSystemException` | `job05_api_import` / `Sample05ApiImportJobIT`（`BatchHttpClient` が 5xx・接続エラーで送出。Batch の `retry()` は設定しない） |
| `SummaryFooterCallback` | `job03_file_export` / `Sample03JobConfig` |

## 案件ジョブについて

`batch-sample` モジュールには、上記のリファレンスサンプル（`job01_`〜`job09_`）とは別に、
実際の案件で作成したジョブも同梱される。
案件ジョブは SDD パイプライン（requirements→system-test）の出力例として位置付け、リファレンスサンプルとは別表で載せる。
パッケージ名・ジョブ名・Step 名・設定クラス名などの命名は、方式設計書の業務ジョブの規約（[docs/architecture.md §11](../../docs/architecture.md#11-命名規約)）に従う。
サンプル番号付きの命名（`jobNN_` など）はリファレンスサンプルだけが使う例外である。設計書は `docs/projects/{案件名}/` 配下に格納する
（`docs/development-guide.md` §2 参照）。

| ジョブ | 設計書 | 近いリファレンスサンプル | 違い |
|---|---|---|---|
| `job_order_daily_sales`（ORDER-001） | [ORDER-001.md](../../docs/projects/order-daily-sales/detail-design/ORDER-001.md) | [sample06](sample06-flow-job.md)（ファイルチェック→取込→集計） | ヘッダチェック（`CsvHeaderCheckTasklet`）とワークテーブル経由の取込を加え、集計を対象日分の洗い替え（`OrderDailySalesReplaceTasklet`）にする。パラメータ検証を `OrderDailySalesJobParametersValidator` で拡張する |
| `job_point_expiry`（POINT-001） | [POINT-001.md](../../docs/projects/member-point-expiry/detail-design/POINT-001.md) | [sample04](sample04-db-aggregate.md)（Tasklet＋SQL） | 単一 Step・単一 SQL で失効更新と履歴登録を1トランザクションで行う。失効対象0件が正常のため `TableCountCheckTasklet` を使わない |
| `job_announcement_notify`（ANNOUNCE-001） | [ANNOUNCE-001.md](../../docs/projects/announcement-notification/detail-design/ANNOUNCE-001.md) | [sample03](sample03-file-export.md)（カーソル読み）・[sample01](sample01-csv-import.md)（Skip・エラー退避） | ステータス更新の Tasklet で対象を確定してから Chunk で DB → DB 登録する4 Step の線形フロー。区域展開不能の件数で警告終了にする `RegionErrorWarningJobExecutionListener` を持つ |
