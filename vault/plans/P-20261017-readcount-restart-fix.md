---
id: P-20261017-readcount-restart-fix
status: approved
---
# ゴール
GitHub Issue #79「sample05: チャンクの途中で失敗した後のリスタートで、取得件数が水増しされ件数の突き合わせが失敗するおそれ」を解決する。

取込 Step（`sample05.01ImportStep`）の取得件数 `apiUserReadCount` を、StepExecution の readCount ではなく Reader が確定させた読込位置（Step の ExecutionContext の `<reader名>.read.count`）から求めるように改め、チャンクの途中で失敗した後のリスタートでも件数突き合わせ（`sample05.02CountCheckStep`）が通るようにする。SSoT に従い、解説 `batch-sample/docs/sample05-api-import.md` を先に改訂してからコードとテストを直す。`./mvnw clean verify` が通ること。

## 分割方針
- 改訂の順は SSoT に従う：解説（T-01）→ リスナーと単体テスト（T-02）→ 結合テスト（T-03）。直列につなぐ
- 再現の確認（planner が計画の作成時に実施。リポジトリは変えていない）：Spring Batch 6.0.5（ローカルの Maven リポジトリにある版）の ChunkOrientedStep を ResourcelessJobRepository で動かし、`RestApiPagingItemReader`（ページサイズ2）・チャンクサイズ3・2ページ目（page=1）の取得で例外、の条件で実行した。失敗した実行は commit=0・write=0 なのに readCount=2 で、Step の ExecutionContext に `r.read.count` は無かった（Reader の保存位置は0）。今のリスナーでは 2 を足し込み、リスタートで全5件を読み直すので合計が 7 になり、水増しは再現した。チャンクサイズ2（今の結合テストの設定）ではページ境界とチャンク境界がそろうため readCount とコミット済みの件数が一致し、今の結合テストでは表に出ない
- 同じ確認で分かった読込位置の性質：`AbstractItemCountingItemStreamItemReader.read()` は `doRead()` の前に件数を1増やすため、読込位置には読込時の例外の件（読込 Skip）と、データの終端を確かめる最後の読込（null が返る読込）も1件として入る。COMPLETED の Step の読込位置は「取得件数＋1」（5件で6、件数がチャンクサイズの倍数でも同じ、0件で1）。FAILED の Step はコミット済みの読込位置（無ければキー自体が無い）。リスタートでは前回の Step の ExecutionContext が引き継がれるので、読込位置は前回からの累計になる。T-02 の計算式はこれに基づく
- T-03 の結合テストは、チャンクサイズを3に変え、スタブのユーザーを5件（3ページ）にして、1チャンク目の途中で失敗する新しいテストと、1チャンク目をコミットした後の2チャンク目の途中で失敗する既存テストの両方で水増しが起きないことを確かめる
- 受け入れ基準のビルド確認は `vault/rules/planner/job-task-split.md` の形に従うが、grep のパターンはシェルの特殊文字を避けて `'^.INFO. BUILD SUCCESS'` にした（`$` と `[` `\` を使わない）。T-01 は文書だけの変更のためビルドの基準を入れない
- 新規ジョブではないため、ジョブ1本を5工程に分けるルール（job-task-split.md）の工程分けは適用しない（バグ修正で、設計書は解説の改訂で足りる）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | review | 1 | - | sample05 解説の取得件数の数え方を Reader の読込位置基準に改訂する | |
| T-02 | todo | 0 | T-01 | ApiUserReadCountListener を Reader の読込位置から取得件数を求める実装に直し単体テストを書き直す | |
| T-03 | todo | 0 | T-02 | Sample05ApiImportJobIT にチャンク途中失敗のリスタートテストを追加しスタブのページ判定を正確にする | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
