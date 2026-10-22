---
id: P-20261022-review-followups-95
status: approved
---
# ゴール

GitHub Issue #97, #98, #99 をまとめて対応する（PR #95 のレビュー指摘の後始末）。

- #97: `ErrorRecordSkipListener` が読込時の Skip の WARN ログに `FlatFileParseException.getMessage()`（`input=[<行>]` を含む）をそのまま出すため、行の内容がログに残る（方式設計 §8）。行番号などの位置情報だけを出す形にし、単体テストと `batch-sample/docs/` の文言も直す
- #98: `OrderItemProcessor` の Javadoc の「`@StepScope` で使うため final にしない」の古い記述を、新方針（docs/architecture.md §13・docs/decisions.md）に合わせて直す
- #99: PR #95 の `/code-review` の Should 相当の指摘 (a)〜(e) に対応する

## 分割方針

- Issue と指摘ごとに成果物を分け、ファイルが重ならないので T-01〜T-05 は互いに依存させず並行で進められるようにする
- T-01 は #97 と #99(d) をまとめる。`LineTokenizer#tokenize(String)` は行番号を受け取らないため、`ByteLengthLineTokenizer` のメッセージに行番号は足さず、行番号は `FlatFileParseException#getLineNumber()` から `ErrorRecordSkipListener` が WARN ログに出す（#97 の修正で #99(d) の「位置が分かるか」が満たされる）。トークナイザ側は、その分担を Javadoc に書くだけにする。同じ判断を2つのタスクに分けると記述が食い違うため1つにした
- T-02 は #98。ORDER-001 詳細設計 §1 Bean 一覧の注記（`@StepScope` で生成するクラスは final にしない。`ValidatingItemProcessorBase` 派生の Processor は `ItemProcessor<I, O>` 型の Bean にする）は新方針と食い違っていないので、設計書は改訂せず Javadoc だけを直す
- T-03 は #99(a)(b)。クラスレベルの `@StepScope` を付けた派生クラスを検出するルールを足し、`ValidatingItemProcessorBase` 以外の基底クラスは対象外として Javadoc に明記する。空マッチは `allowEmptyShould(false)` を明示し、失敗することをテストで示す
- T-04 は #99(c)。出力の書式は変えない（`docs/projects/order-daily-sales/system-test/scenarios.md` の実施記録やエラーファイルの既存の出力と食い違わせないため）。書式と「値を含めない」約束を Javadoc に明記し、書式を固定する単体テストを足す
- T-05 は #99(e)。リリースノートの置き場がリポジトリに無いため、`docs/decisions.md` に破壊的変更と移行手順を1行で足す
- T-01〜T-04 はコードを変更するタスク（`.java` を成果物に持つ）なので、最後に計画単位のレビュータスク T-06 を置く

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | todo | 0 | - | 読込時の Skip の WARN ログに行の内容を出さず行番号を出す（#97・#99(d)） | |
| T-02 | todo | 0 | - | OrderItemProcessor の Javadoc を ItemProcessor 型の Bean の方針に直す（#98） | |
| T-03 | todo | 0 | - | StepScopeBeanRuleTest の検出範囲と空マッチの扱いを明示する（#99(a)(b)） | |
| T-04 | todo | 0 | - | formatViolations の書式と値を含めない約束を Javadoc とテストで固定する（#99(c)） | |
| T-05 | todo | 0 | - | process() を final にした破壊的変更の移行手順を decisions.md に書く（#99(e)） | |
| T-06 | todo | 0 | T-01,T-02,T-03,T-04,T-05 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
