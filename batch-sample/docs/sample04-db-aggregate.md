# サンプル04: DB → DB 集計・洗い替え（Tasklet）

売上明細を顧客別に集計し、`app.sales_summary` を truncate-insert 方式で洗い替える。

## デモする機能

- **Chunk ではなく Tasklet を選ぶ判断**（集計SQL 1本で完結 → データをJavaへ持ち出さない）
- truncate ＋ `INSERT ... SELECT ... GROUP BY` を**同一トランザクション**で実行
  （途中失敗時は全体ロールバック、旧データ消失なし）
- 後続 Step での結果検証（`TableCountCheckTasklet`。集計0件なら異常終了）

## 実行方法

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample04DbAggregateJob \
  aggregateDate=2026-07-14,java.time.LocalDate"
```

※ `aggregateDate` は JobInstance を識別するパラメータ（同一日付での再実行は成功済みならエラーになる）。

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| 集計・変換が SQL で表現できる（DB内で完結） | 1件ごとに外部API呼出や複雑なJavaロジックが必要 → Chunk |
| 全件作り直しでよい集計テーブル（洗い替え） | 差分更新が必要（洗い替えコストが大きい）→ MERGE/UPSERT 方式を設計 |
| 参照側が一時的な全件ロック・空状態を許容できる | 洗い替え中も参照無停止が必須 → 一時テーブル＋RENAME 方式（architecture.md §4） |

## 実装ポイント

- PostgreSQL の `TRUNCATE` はトランザクショナル（ロールバック可能）である特性を利用している。
  DB製品によっては TRUNCATE が暗黙コミットされるため、方式設計時に必ず確認すること
- 集計行数はログ出力し、後続の件数チェック Step で下限を検証する
