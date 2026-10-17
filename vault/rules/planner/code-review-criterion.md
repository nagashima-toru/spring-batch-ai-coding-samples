# planner ルール：コードを変えるタスクに /code-review の行を入れる

## 目的
コードを変えるタスクで、規約の確認だけでは拾えない不具合（PR #70 の例）が残ったまま done にならないようにする。

## ルール
- 「成果物」が `docs/` 配下のパスと `.md` のファイルだけのタスクは対象外とし、それ以外のパス（Java・SQL・XML・YAML・properties・CSV などのファイルや、`docs/` 以外のディレクトリの宣言）を1つでも持つタスクを「コードを変えるタスク」とする
- コードを変えるタスクの受け入れ基準には、次の1行を必ず入れる：「`/code-review` で差分に致命的な指摘が残っていない（`vault/rules/verifier/code-review.md` に従って判定する）」
- この行には確認コマンドを併記しない（verifier が Skill ツールで組み込みの `/code-review` を呼んで判定するため）。`effort`・`--max-findings` などの引数も付けない
- この行を入れると受け入れ基準が7行を超える時は、ほかの行をまとめて7行以内に収め、この行は削らない
- ジョブ1本の工程タスク（`vault/rules/planner/job-task-split.md`）では、implementation・unit-test・integration-test・review-standards の各タスクが通常これに当たる。system-test のタスクは成果物で決める
- 差分の外（既存コード）への指摘を直すタスクは起こさない（verifier が対象外として reasons に残し、PR 作成時の取り出しで扱う）

## 確認方法
- コードを変えるタスクの受け入れ基準に上の1行がある。docs だけのタスクには無い
