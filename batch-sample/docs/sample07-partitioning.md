# サンプル07【骨格】: パーティショニングによる大量データ並列処理

`Partitioner` で処理範囲を分割し、Worker Step を並列実行するパターンの骨格実装。
Worker はログ出力のみのため、実務では Chunk Step に置き換えて使用する。

## 構成

```
sample07.01ManagerStep（SalesIdRangePartitioner で ID範囲を GRID_SIZE 分割）
  └─ sample07.02WorkerStep × N並列（各Workerは stepExecutionContext の minId/maxId を処理）
```

## 実務への展開手順

1. `SalesIdRangePartitioner` の固定範囲（1〜1000）を、対象テーブルの `MIN(id)/MAX(id)` を
   起動時に取得する実装へ変更する
2. Worker の Tasklet を Chunk Step へ置き換える
   - Reader: `MyBatisCursorItemReader` ＋ 範囲条件付きSQL（`WHERE id BETWEEN #{minId} AND #{maxId}`）
   - `@StepScope` で `#{stepExecutionContext['minId']}` を注入
3. `SimpleAsyncTaskExecutor` を `ThreadPoolTaskExecutor`（プール上限あり）へ変更する

## 性能設計の考え方

- **並列度（gridSize）** は「DB 最大接続数 ÷ 同時実行ジョブ数」と CPU コア数の小さい方を上限に、
  実測して決める（並列度を上げても DB がボトルネックなら効果はない）
- 分割キーは**偏りが出ないもの**を選ぶ（連番IDなら範囲分割、偏るなら剰余分割）
- 各パーティションは独立したトランザクション・独立したリスタート単位になる
  （失敗したパーティションのみ再実行される）
- ECS でのスケールアウト（複数タスクによるリモートパーティショニング）が必要な規模なら、
  ジョブ自体の分割（日付範囲でジョブパラメータを分ける）も比較検討する

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| 単一スレッドで処理時間が要件を満たさない大量データ | まず SQL 化・チャンクサイズ調整で足りる場合（並列化は最後の手段） |
| データを独立した範囲に分割できる | レコード間に処理順序の依存がある |
