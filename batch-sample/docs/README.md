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
| `JsonItemReaderFactory` | 実例なし（#61 で追加予定） |
| `CsvItemWriterFactory` | `job03_file_export` / `Sample03JobConfig` |
| `FixedLengthItemWriterFactory` | 実例なし（#61 で追加予定） |
| `RestApiPagingItemReader` | `job05_api_import` / `Sample05JobConfig` |
| `ValidatingItemProcessorBase` | `job01_csv_import` / `CustomerItemProcessor`、`job02_fixedlength_import` / `ProductItemProcessor`、`job_order_daily_sales` / `OrderItemProcessor` |
| `BusinessSkipPolicy` | `job01_csv_import` / `Sample01JobConfig`、`job02_fixedlength_import` / `Sample02JobConfig`、`job_announcement_notify` / `AnnouncementNotifyJobConfig` |
| `ErrorRecordSkipListener` | `job01_csv_import` / `Sample01JobConfig`、`job02_fixedlength_import` / `Sample02JobConfig`、`job_announcement_notify` / `AnnouncementNotifyJobConfig` |
| `FileExistenceCheckTasklet` | `job06_flow_job` / `Sample06JobConfig`、`job_order_daily_sales` / `OrderDailySalesJobConfig` |
| `FileMoveTasklet` | 実例なし（#61 で追加予定） |
| `CsvHeaderCheckTasklet` | `job_order_daily_sales` / `OrderDailySalesJobConfig` |
| `AbstractApiCallTasklet` | 実例なし（#61 で追加予定） |
| `TableCountCheckTasklet` | `job04_db_aggregate` / `Sample04JobConfig` |
| `JobParametersValidators` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_order_daily_sales` / `OrderDailySalesJobParametersValidator`（独自 Validator への拡張例） |
| `LoggingJobExecutionListener` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_point_expiry` / `PointExpiryJobConfig` |
| `LoggingStepExecutionListener` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_point_expiry` / `PointExpiryJobConfig` |
| `DuplicateRunPreventingJobExecutionListener` | `job01_csv_import` / `Sample01JobConfig`、`job04_db_aggregate` / `Sample04JobConfig`、`job_point_expiry` / `PointExpiryJobConfig` |
| `BusinessException` | `job01_csv_import` / `CustomerItemProcessor`、`job_order_daily_sales` / `OrderItemProcessor` |
| `SystemException` | `job06_flow_job` / `WorkItemProcessor` |
| `RetryableSystemException` | 実例なし（#61 で追加予定） |
| `SummaryFooterCallback` | `job03_file_export` / `Sample03JobConfig` |

## 案件ジョブについて

`batch-sample` モジュールには、上記のリファレンスサンプル（`job01_`〜`job09_`）とは別に、
実際の案件で作成したジョブ（例: `job_announcement_notify`）も同梱される。
案件ジョブはサンプルのカタログには含めない。パッケージ名・ジョブ名・Step 名・設定クラス名などの命名は、方式設計書の業務ジョブの規約（[docs/architecture.md §11](../../docs/architecture.md#11-命名規約)）に従う。
サンプル番号付きの命名（`jobNN_` など）はリファレンスサンプルだけが使う例外である。設計書は `docs/projects/{案件名}/` 配下に格納する
（`docs/development-guide.md` §2 参照）。
