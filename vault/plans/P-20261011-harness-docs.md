---
id: P-20261011-harness-docs
status: approved
---
# ゴール
spring-batch-ai-coding-samples の利用者が、`sdd-pipeline` とハーネス（`/plan`・`/run`）のどちらを使えばよいか docs から分かるように、`docs/development-guide.md` にハーネスを使う工程の節を足し、`CLAUDE.md` の Skills の説明から辿れるようにする。最後に、決定事項を `docs/decisions.md` に残して設計文書 `vault/designs/D-20261011-spring-batch-adapt.md` を削除する（設計文書 D-20261011-spring-batch-adapt のフェーズ2）。

## 分割方針
- 変えるファイルごとに1タスクにする（`docs/development-guide.md`・`CLAUDE.md`・`docs/decisions.md`・設計文書の削除の4タスク）。同じファイルを2タスクで変えると、並行時に計画ブランチへのマージで衝突するため、`docs/development-guide.md` の §2.4 の追加と §5.3 の追記は T-01 にまとめる
- T-02（CLAUDE.md からのリンク）は、リンク先の見出しを T-01 が作るので `after` に T-01 を置く
- T-03（`docs/decisions.md`）は、載せる決定事項を決定済みで固定したので T-01 と並行できる（`after` は空）
- T-04（設計文書の削除）は、設計文書を読む T-01〜T-03 のすべての後に置く。ゴール全体の確認（smoke・設計文書への参照の残り）も T-04 の受け入れ基準に入れる
- `vault/rules/planner/job-task-split.md`（ジョブ1本を5工程に分け、全タスクに `./mvnw verify -DskipITs` を入れる）は適用しない。この計画は人向けの docs だけを変え、Java・ビルド設定を変えないため（ビルド結果が変わらない）。代わりにハーネスの整合を `bash scripts/smoke.sh` で確かめる
- smoke の `(ref-1)` の対象文書（`docs/vault-spec.md`・`README.md` など）はどのタスクも変えない。`docs/development-guide.md`・`CLAUDE.md`・`docs/decisions.md` は refcheck の対象外なので、`(ref-1)` の基準は最後の T-04 で smoke 全体の `fail=0` として確かめる

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | development-guide.md にハーネスで進める節（§2.4）と §5.3 の追記を足す | |
| T-02 | todo | 0 | T-01 | CLAUDE.md の Skills の節から development-guide.md §2.4 へリンクする | |
| T-03 | review | 1 | - | docs/decisions.md を新規作成し、docs に無い決定事項を残す | |
| T-04 | todo | 0 | T-01,T-02,T-03 | 設計文書 D-20261011-spring-batch-adapt を削除する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
