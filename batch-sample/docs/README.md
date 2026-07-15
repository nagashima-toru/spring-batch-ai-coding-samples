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
