---
id: P-20261013-amend-criterion-pipe
status: approved
---
# ゴール
GitHub Issue #57「amend_task.py の --replace-criterion が受け入れ基準の | を ／ に置き換え、確認コマンドのパイプが壊れる」を解消する。

## 分割方針
- 修正は `scripts/amend_task.py` の `--replace-criterion` の1か所（基準文の整形）で、確認は smoke の amend-task の節への追記、仕様の記述は `docs/vault-spec.md` 2節の amend_task.py の1行への補足だけなので、1タスクにまとめる。主な成果物は `scripts/amend_task.py`、smoke と vault-spec は付随して変えるファイル
- `scripts/transition.py` の `clean_text` は計画票のタスク表のセル（question）用なので変えない。`--note`（log 行）と `--add-deliverable` の扱いも変えない（既存の挙動を広げて変えない）
- このリポジトリはハーネスの導入先（`.claude/harness-manifest.json` がある）。マニフェストは「install で配った内容」の記録なので書き換えない。変えた3ファイルは次の `install.sh --update` で「利用者が編集済み」としてスキップされ、修正は上書きされずに残る

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | todo | 0 | - | amend_task.py の --replace-criterion で基準文のパイプ記号を置き換えないようにし smoke で確かめる | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## マージ後に人が行う作業
- 導入元のハーネス（ai-harness）にも同じ不具合があるはずなので、導入元へ Issue として報告する（導入元で直った後に `install.sh --update` を使う時は、この3ファイル `scripts/amend_task.py`・`scripts/smoke.sh`・`docs/vault-spec.md` が編集済みとしてスキップされる点に注意する）
