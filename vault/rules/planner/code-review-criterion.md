# planner ルール：計画単位のレビュータスクへの導入先の補足

## 目的
計画単位のレビュータスクを、verifier ルール（`vault/rules/verifier/code-review.md`）の区分（Must/Should/Ask）で判定させるための、この導入先固有の補足を書く。

## ルール
- 計画単位のレビュータスクを足す条件・コードを変更するタスクの判定・title・成果物・決定済みは、`.claude/agents/planner.md` の「計画単位のレビュータスク」節に従い、ここには写さない
- 計画単位のレビュータスクの受け入れ基準の1行目は「`/code-review` で計画の差分に致命的な指摘が残っていない（`vault/rules/verifier/code-review.md` に従って判定する）」にする（verifier ルールは、受け入れ基準の行がそのファイルを参照している時だけ判定に使うため）
- この行には確認コマンドを併記せず、`/code-review` の引数（`effort`・`--max-findings` など）も付けない
- ジョブ1本の計画のタスク数は `vault/rules/planner/job-task-split.md` に従う
- 差分の外（既存コード）への指摘を直すタスクは起こさない（verifier が対象外として reasons に残し、PR 作成時の取り出しで扱う）

## 確認方法
- 計画単位のレビュータスクの受け入れ基準の1行目が、上の1行目の文言である
- 通常のタスクの受け入れ基準には `/code-review` の行が無い
