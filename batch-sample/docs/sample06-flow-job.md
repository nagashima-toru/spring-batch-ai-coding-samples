# サンプル06: 複合ジョブ（Flow・条件分岐・リスタート）

前処理チェック → 本処理（取込） → 後処理（集計）の複数 Step 構成。
**Spring Batch のリスタート機構を実際に体験するためのサンプル**（実務で最重要）。

## デモする機能

- Step 分割（1 Step 1 責務）と Flow 定義
- `JobExecutionDecider` による条件分岐（取込0件なら集計をスキップして正常終了）
- **途中失敗 → 同一パラメータ再実行で処理済みチャンクの続きから再開**

## ジョブ構成

```
sample06.01FileCheckStep（ファイル存在チェック）
  → sample06.02ImportStep（CSV→work_import 取込。チャンクサイズ2）
  → [Decider] 取込件数 > 0 ？
       PROCEED      → sample06.03SummaryStep（サマリ洗い替え）
       SKIP_SUMMARY → 正常終了
```

## リスタート動作確認手順（必ず一度試すこと）

付属の `data/in/work.csv` は5行目に負数レコード（意図的失敗データ）を含む。

```bash
# 1. ローカルDB起動
docker compose -f docker/compose.yaml up -d

# 2. 1回目実行 → 5行目で異常終了（exit=1）
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample06FlowJob \
  inputFile=batch-sample/data/in/work.csv"; echo "exit=$?"

# 3. この時点の状態を確認
#    - app.work_import には W001〜W004 の4件のみ（チャンクサイズ2×2回コミット済み）
#    - batch.batch_step_execution_context に読取位置（read.count=4）が保存されている
docker exec batch-postgres psql -U batch_admin -d batchdb \
  -c "SELECT code FROM app.work_import ORDER BY code" \
  -c "SELECT short_context FROM batch.batch_step_execution_context WHERE short_context LIKE '%read.count%'"

# 4. データ修正（5行目の -1 を 500 に直す）
sed -i '' 's/W005,-1/W005,500/' batch-sample/data/in/work.csv

# 5. 2回目実行（同一パラメータ）→ 続き（5行目）から再開し完走（exit=0）
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample06FlowJob \
  inputFile=batch-sample/data/in/work.csv"; echo "exit=$?"

# 6. ログの readCount=2（6件中、続きの2件だけ読んだ）と work_import 6件を確認
git checkout -- batch-sample/data/in/work.csv   # デモデータを元に戻す
```

同じ検証は結合テスト
`Sample06FlowJobIT#途中失敗後にデータを修正して同一パラメータで再実行すると続きから再開する`
で自動化されている。

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| チェック→本処理→後処理のような複数工程を1ジョブで管理したい | 工程ごとに実行スケジュール・リランを分けたい → ジョブ分割し Step Functions で制御 |
| 結果に応じた分岐（0件スキップ等）が必要 | 分岐がない直列2Step程度 → シンプルに `.start().next()` で十分 |

## 実装ポイント

- 失敗デモは `WorkItemProcessor` が負数で `SystemException`（**Skip対象外**）を送出する仕組み。
  Skip 対象の `BusinessException` にすると失敗せず警告終了になる点が対比ポイント
- リスタートには **JDBC ジョブリポジトリが必須**（Boot 4 の既定はインメモリ。
  `spring-boot-starter-batch-jdbc` を依存へ追加。architecture.md §9.1）
- 「成功済みジョブの同一パラメータ再実行はエラー」が正しい挙動（誤操作防止）
