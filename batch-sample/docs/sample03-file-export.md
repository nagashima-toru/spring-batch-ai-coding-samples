# サンプル03: DB → CSV 抽出（Chunk）

売上明細を全件抽出し、ヘッダ行＋トレーラ行（件数・金額合計）付きCSVを生成する。

## デモする機能

- `MyBatisCursorItemReader` による**カーソルフェッチ**（全件メモリロードなし）
- ヘッダ/トレーラ付きファイル生成（`CsvItemWriterFactory` + `SummaryFooterCallback`）
- 出力ファイル上書きによる冪等な再実行

## 出力例

```
id,customer_code,amount,sold_at
1,C001,1000,2026-07-01
2,C002,2500,2026-07-02
T,2,3500
```

## 実行方法

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample03FileExportJob \
  outputFile=batch-sample/data/out/sales.csv"
```

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| 件数上限のないテーブル抽出（カーソルでメモリ一定） | 抽出条件・整形が複雑でSQLで完結する → ビューや `INSERT SELECT`＋抽出の2段構成を検討 |
| 件数・合計値のトレーラが必要な対外ファイル連携 | リアルタイム連携が必要 → バッチではなく API/イベント連携を検討 |

## 実装ポイント・注意

- `SummaryFooterCallback` は **Writer の footerCallback と Step の listener の両方**に登録する
  （listener 登録を忘れると件数が集計されずトレーラが `T,0,0` になる）
- トレーラの集計値はメモリ保持のため、本ジョブは**リスタートせず最初から再実行**する運用とする
  （出力は上書きされるため安全。docs/architecture.md §6.2）
- 抽出順序は SQL の `ORDER BY` で保証する（ファイル比較テストを安定させる）
