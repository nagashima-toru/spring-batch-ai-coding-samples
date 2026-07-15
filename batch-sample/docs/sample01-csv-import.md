# サンプル01: CSV → DB 取込（Chunk）

顧客CSVファイルを検証しながら `app.customer` へ取り込む、**取込ジョブの基本形**。

## デモする機能

- 基盤部品によるCSV読取（`CsvItemReaderFactory`、リスタート対応）
- Bean Validation ＋ 業務チェック（`ValidatingItemProcessorBase` 継承）
- 業務エラーのみ Skip（`BusinessSkipPolicy`）とエラーレコード退避（`ErrorRecordSkipListener`）
- Skip 発生時の警告終了（終了コード2）
- UPSERT による冪等な取込（同一ファイルの再実行で結果が変わらない）

## 実行方法

```bash
docker compose -f docker/compose.yaml up -d
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample01CsvImportJob \
  inputFile=batch-sample/data/in/customers.csv \
  errorFile=batch-sample/data/error/customers_error.txt"
echo "exit=$?"   # 0=正常 / 2=警告(Skipあり) / 1=異常
```

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| 件数上限のないファイル取込（チャンクコミットでメモリ一定） | 取込後の集計がSQL1本で済む → サンプル04（Tasklet）を検討 |
| 不正レコードを退避して処理を継続したい | 1件でも不正があれば全件ロールバックしたい（Skipを使わず即失敗させる設計にする） |
| 取込前後にチェック・退避処理が必要（Step追加で対応） | 数十万件超で処理時間が課題 → サンプル07（パーティショニング）を検討 |

## 実装ポイント

- Reader/Listener は `@StepScope` とし、ジョブパラメータ（ファイルパス）を SpEL で受け取る
- `JobParametersValidators.required(...)` でパラメータ不足を起動直後に検出する
- Writer は `MyBatisBatchItemWriter` ＋ Mapper XML の UPSERT（`ON CONFLICT DO UPDATE`）
