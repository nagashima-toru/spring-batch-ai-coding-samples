---
id: P-20261012-build-cmd-clean
status: approved
---
# ゴール
GitHub Issue #45「工程 Skill と PR テンプレートのビルドコマンドも clean 付き・カバレッジ判定の分離にそろえる（#44 の残り）」を解消する。

P-20261012-harness-build-criteria（#42・#44）でハーネスの受け入れ基準と verifier・creator ルールは `clean` 付き・batch-sample のカバレッジを `-DskipITs` で判定しない形に直した（正本: `vault/rules/verifier/build-verification.md`・`docs/decisions.md`）。同じ問題（batch-sample のカバレッジはモジュール全体で判定するため `-DskipITs` では必ず落ちる／JaCoCo の append が既定 true のため `target/` に残った実行データで判定がぶれる）が残る工程 Skill・PR テンプレート・それと対になる `docs/development-guide.md` の §3・§4 のビルドコマンドを、次の3コマンドの使い分けにそろえる。

- `./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false`（全モジュールの Checkstyle・SpotBugs・単体テスト。カバレッジ不足では止めない）
- `./mvnw clean verify -DskipITs -pl batch-core`（batch-core のカバレッジ80%）
- `./mvnw clean verify`（結合テストを含む全体。batch-sample のカバレッジ80%はここで判定する）

## 分割方針
- 成果物のファイル（Skill は1ディレクトリ）ごとに1タスクにする。T-01〜T-05 は互いに依存せず並行できる
- T-06 は `docs/development-guide.md` の §3・§4 を直し、あわせてゴールの範囲（工程 Skill・PR テンプレート全体）に `./mvnw verify` が残っていないことを確かめる。全体の確認のため T-01〜T-05 に依存させる
- 新しいファイルは作らないので、refcheck（`(ref-1)`）の対象文書に未作成のパスを先行して書く問題は起きない。`(ref-1)` の対象（各 `SKILL.md`）を成果物に持つ T-01〜T-04 には `(ref-1)` の基準を入れる
- 対象外：`.github/workflows/ci.yml`（CI は毎回クリーンなチェックアウトで `target/` が無く、結合テストを含む `verify` なので batch-sample のカバレッジも正しく判定される）、`.github/workflows/cd.yml`（`package` のみ）、`docs/decisions.md`（過去の決定の記録）、`docs/development-guide.md` の §1・`README.md`・`CLAUDE.md`（人向けのよく使うコマンド一覧で Issue #45 の範囲外。次フェーズ候補に回す）、`./mvnw test`・`./mvnw compile`（JaCoCo の閾値チェックは verify フェーズなので問題が起きない）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | unit-test Skill のビルドコマンドを clean 付き・カバレッジ判定の分離にそろえる | |
| T-02 | done | 1 | - | integration-test Skill のビルドコマンドを clean 付き・カバレッジ判定の分離にそろえる | |
| T-03 | done | 1 | - | review-code Skill とチェックリストのビルドコマンドを clean 付きにそろえる | |
| T-04 | done | 1 | - | sdd-pipeline Skill のビルドコマンドを clean 付きにそろえる | |
| T-05 | done | 1 | - | PR テンプレート（既定・実装 PR 用）のビルドコマンドを clean 付きにそろえる | |
| T-06 | review | 1 | T-01,T-02,T-03,T-04,T-05 | development-guide の §3・§4 のビルドコマンドを clean 付きにそろえ、ゴール範囲の残存を確かめる | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## 次フェーズの候補
- `docs/development-guide.md` §1.2・§1.3、`README.md`、`CLAUDE.md` の「よく使うコマンド」の `./mvnw verify -DskipITs`（まっさらな環境では batch-sample のカバレッジで必ず失敗する）を、上の3コマンドの使い分けに直すか検討する（Issue #45 の範囲外のため本計画では扱わない）
