---
id: P-20261012-readme-build-cmd-clean
status: approved
---
# ゴール

GitHub Issue #52「README・CLAUDE.md・development-guide §1 のよく使うコマンドも clean 付き・カバレッジ判定の分離にそろえる（#45 の残り）」を解消する。人向けの「よく使うコマンド」に残る clean の無い `./mvnw verify` を、`vault/rules/verifier/build-verification.md` と同じ3コマンドの使い分け（`./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false`・`./mvnw clean verify -DskipITs -pl batch-core`・`./mvnw clean verify`）にそろえる。

## 分割方針

- 変える文書ごとに1タスクにする（README.md・CLAUDE.md・docs/development-guide.md）。T-01 と T-02 は互いに独立で並行できる
- リポジトリ全体に clean の無い `./mvnw verify` が残らないことの確認は、最後の T-03 に入れる。T-03 は `after` で T-01・T-02 に依存させ、T-03 の worktree に前2タスクの変更が入った状態で全体を検査する
- 新しく作るファイルは無いので、`(ref-1)` の対象文書（README.md）への新規ファイル参照の先行は起きない。README.md を変える T-01 には `(ref-1)` の基準を入れる
- 対象外：`docs/decisions.md`（過去の決定の記録で、当時のコマンドをそのまま残す）、`.github/workflows/`（CI はクリーンなチェックアウトで動くので clean は不要。また Markdown ではない）、`vault/`（計画・ルール・ログの記録）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | README.md のクイックスタートのビルドコマンドを clean 付きにする | |
| T-02 | done | 1 | - | CLAUDE.md の「ビルド・実行コマンド」を3コマンドの使い分けにそろえる | |
| T-03 | done | 1 | T-01, T-02 | docs/development-guide.md §1.2・§1.3 のコマンドをそろえ、全体に残りが無いことを確かめる | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
