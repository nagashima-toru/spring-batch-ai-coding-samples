---
id: P-20261012-harness-update
status: approved
---
# ゴール

ai-harness（https://github.com/nagashima-toru/ai-harness 、public）で Issue が改修されたので、その改修をこのリポジトリのハーネスに取り込む。取り込み先の版は ai-harness の main の 6c1063a2c1a38a838ade55ebc500e2c5a6dffa5d（前回導入の `.claude/harness-manifest.json` の version は "2026-10-11 19:42"。その後の #185・#186・#188・#192・#194・#196・#197 を含む）。

## 分割方針

- ハーネス本体の更新は、ai-harness の `scripts/install.sh --update` を1回実行すれば一式がそろう。変わるファイルどうしが互いに依存している（manifest は配ったファイルのハッシュを持ち、smoke.sh は transition.py・stop_gate.py などの新しい動作を検査する）ので、分けると途中の状態で smoke が通らない。よって1タスク（T-01）にする。主な成果物は `.claude/harness-manifest.json`（ハーネス一式の版の記録）で、install が書き換えるファイルをすべて成果物に列挙する。
- 事前にこのリポジトリの clone で `install.sh --update` を試し、変わるのは T-01 の「成果物」に挙げた15パスだけであること、`.claude/settings.json` と `CLAUDE.md` は skip（既存を維持）、利用者が編集済みでスキップされたファイルは無いことを確かめた。
- そのまま `install.sh --update` を実行すると、manifest に `"gitignore": "appended"` が記録され、このリポジトリで `bash scripts/smoke.sh` を実行した時に `(install-gi) 03` が NG になる（smoke はこのリポジトリ自身を導入元として一時ディレクトリへ install し、その時に `.claude/harness-manifest.json` もコピーされ、その `gitignore` キーが引き継がれるため。ai-harness 本体には manifest が無いので起きない）。smoke の NG が残ると、以後の計画で smoke 全体を基準に持つタスクが無関係な失敗で止まるので、T-01 では `.gitignore` に `.claude/worktrees/` を先に追記してから `install.sh --update` を実行する（追記済みなら install は manifest に `gitignore` キーを書かない）。この手順で smoke が `fail=0` になることを clone で確かめた。

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | review | 1 | - | ai-harness 6c1063a の install.sh --update でハーネス一式を更新する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## 人への質問（回答済み）

1. smoke の `(install-gi) 03` の扱い（B：`.gitignore` に `.claude/worktrees/` を手で先に追記してから `install.sh --update` を実行する／A：そのまま実行して NG 1件を受け入れる）。ai-harness へ Issue を起票するか → 回答：B で進める。ai-harness への Issue も登録する（Issue の起票はこの計画のタスクに含めず、オーケストレーターが計画の外で行う）
