---
id: P-20261017-devguide-sample01-params
status: approved
---
# ゴール
GitHub Issue #59「development-guide §1 のサンプル01実行例が必須パラメータ不足で失敗する」を解消する。`docs/development-guide.md` §1.3「よく使うコマンド」のサンプルジョブ実行例が `importDate=2026-07-14,java.time.LocalDate` を渡しており、sample01 の必須パラメータ `inputFile` / `errorFile`（`Sample01JobConfig.java` の `JobParametersValidators.required("inputFile", "errorFile")`）を満たさずパラメータ検証で失敗する。これを `batch-sample/docs/sample01-csv-import.md` の実行方法と同じパラメータに直す。同一原因の `docs/architecture.md` §1.2 の実行例も、オーケストレーターの判断で範囲に含めて直す。

## 分割方針
- 誤った実行例は `docs/development-guide.md` §1.3 と `docs/architecture.md` §1.2 の2箇所にある。成果物を1ファイルずつに特定するため、ファイルごとに1タスク（T-01・T-02）に分ける
- 2タスクは別ファイルを変え、互いの成果物を参照しないので `after` の依存は無く、並行して進められる
- `docs/architecture.md` §11 命名規約表の `importDate`（ジョブパラメータ命名の例）は誤りではないので変えない
- README.md・CLAUDE.md・`batch-sample/docs/` 配下・コードは既に正しい例を使っているので変えない

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | review | 1 | - | development-guide §1.3 のサンプル01実行例を inputFile/errorFile に直す | |
| T-02 | doing | 1 | - | architecture.md §1.2 のサンプル01実行例を inputFile/errorFile に直す | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
