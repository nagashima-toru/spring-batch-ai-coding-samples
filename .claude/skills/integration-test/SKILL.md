---
name: integration-test
description: ジョブ単位の結合テスト（Testcontainers + Spring Batch Test）を作成する。「結合テストを書いて」「ジョブのITを作って」「Testcontainersでジョブを検証して」のような発話や、単体テスト完了後の次工程として発動する。
---

# 結合テスト Skill

実DB（Testcontainers の PostgreSQL 16、Flyway 適用済み）に対してジョブを実際に起動し、
終了ステータス・DB・ファイルの結果を検証する結合テストを作成する。

## 入力と出力（工程連鎖）

| | 内容 |
|---|---|
| 入力 | 実装コード＋詳細設計書（エラー処理設計・リラン設計の節が検証仕様になる） |
| 参照 | `batch-sample/src/test/java/.../AbstractJobIT.java`（基底クラス）、既存 IT（Sample01〜06） |
| 出力 | `src/test/java/...{ジョブパッケージ}/{ジョブ名}IT.java`（Failsafe が `*IT` を実行） |
| 次工程 | `/review-code` |

## テストの構成（必ず既存の型に従う）

- `AbstractJobIT` を継承する（PostgreSQL コンテナ共有・`spring.batch.job.enabled=false`・
  `truncateAppTable()`・`uniqueParameters()` を提供）
- ジョブは `JobOperator#start(job, params)` で起動し、`JobExecution` を検証する
- テストデータは各テストが自前で投入する（`@BeforeEach` で対象テーブルを truncate）
- ファイルIFは `@TempDir` を使う
- 外部APIは JDK 組込み `HttpServer` でスタブし、`@DynamicPropertySource` で base-url を差し替える
  （Sample05ApiImportJobIT が実例）

## 必須の検証観点（ジョブごとに全て検討する）

| 観点 | 検証内容 |
|---|---|
| 正常系 | `BatchStatus.COMPLETED`・ExitStatus `COMPLETED`・DB/ファイルの結果値 |
| 警告系 | Skip 発生時に ExitStatus が `COMPLETED_WITH_WARNING`、エラーレコードファイルの内容 |
| 異常系 | 前提NG（ファイルなし・0件等）で `BatchStatus.FAILED` |
| リスタート | 途中失敗 → `JobOperator#restart(execution)` → 続きから再開
  （復帰後の readCount と業務データ件数の両面で検証。Sample06FlowJobIT が実例） |
| 冪等性 | 同一入力の再実行（別パラメータ）で結果が変わらない（UPSERT/洗い替え） |
| 分岐 | Flow の各分岐経路（実行された Step 名を `getStepExecutions()` で検証） |

## 注意

- Docker が必要（`./mvnw verify`。急ぎのビルドは `-DskipITs`）
- アサーションは「件数」だけでなく代表レコードの**値**まで検証する（変換ロジックの通し確認）
- 検証で `Thread.sleep` を使わない（ジョブ起動は同期実行なので不要）
- メタデータの永続化検証が必要な場合は `batch.batch_*` テーブルを直接参照してよい
