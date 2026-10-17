---
id: P-20261018-review-plan-level
status: approved
---
# ゴール
ai-harness#218（`/code-review` をタスク単位から計画単位に移す）に、このリポジトリの導入先側の文書・ルールを追従させる（GitHub Issue #78 の続き）。ハーネス本体（`.claude/agents/`・`.claude/skills/plan/`・`.claude/skills/run/`・`.claude/skills/design/`・`.claude/hooks/`・`scripts/`・`docs/vault-spec.md`）は変えない。

## 分割方針
- 直前の計画 P-20261017-review-redesign で「タスクごとに `/code-review` の行を入れる」「範囲はタスクの差分」の前提で書いた導入先のファイルを、1ファイル1タスクで計画単位のレビュータスクの前提に直す
- T-01・T-02：planner ルール（`vault/rules/planner/code-review-criterion.md` を導入先固有の補足だけに縮める、`vault/rules/planner/job-task-split.md` に計画単位のレビュータスクを足す）
- T-03：`.claude/skills/review-standards/SKILL.md` の手順4（ハーネスのタスクでは手順4を行わず計画単位のレビュータスクに任せる。範囲の誤った箇条を直す）
- T-04・T-05：creator ルール（`vault/rules/creator/code-review.md`・`vault/rules/creator/skill-workflow.md`）
- T-06：verifier ルール（`vault/rules/verifier/code-review.md`）
- T-07：`docs/development-guide.md` §2.4 末尾の段落を書き直し、あわせて古い前提の記述がゴールの範囲に残っていないことを確かめる（T-01〜T-06 の後）
- T-01〜T-06 は互いに別のファイルを変えるので依存を付けない。同じファイルを2タスクで変えることはない
- CLAUDE.md・`docs/development-guide.md` §2.2 の表・`.github/PULL_REQUEST_TEMPLATE/implementation.md`・`.claude/skills/sdd-pipeline/SKILL.md` の `/code-review` の記述は、対話や sdd-pipeline で `/review-standards` を使う時（手順4を従来どおり実行する）の説明なので変えない。`.claude/skills/implementation/SKILL.md`・`.claude/skills/integration-test/SKILL.md`・`docs/coding-standards.md` にはタスクごとの `/code-review` を前提にした記述が無い（`git grep -n -e 'merge-base work/' -e 'code-review-criterion'` で確認済み）ので触らない。batch-sample にも触らない
- 計画単位のレビュータスクの判定：全タスクの成果物が `.md`（`vault/rules/` 配下・`.claude/skills/review-standards/SKILL.md`・`docs/development-guide.md`）で、「`vault/` 配下でなく、拡張子が `.md`・`.txt` でもないパス」を持つタスクが0件のため、計画単位のレビュータスクは足さない

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | planner ルール code-review-criterion.md を計画単位のレビュータスクへの導入先の補足にする | |
| T-02 | done | 1 | - | planner ルール job-task-split.md に計画単位のレビュータスクを足す | |
| T-03 | doing | 1 | - | review-standards SKILL.md の手順4をハーネスでは計画単位のレビュータスクに任せる形にする | |
| T-04 | todo | 0 | - | creator ルール code-review.md を計画単位のレビュータスクの前提に直す | |
| T-05 | todo | 0 | - | creator ルール skill-workflow.md の review-standards タスクから手順4を外す | |
| T-06 | todo | 0 | - | verifier ルール code-review.md を計画単位のレビュータスクの前提に直す | |
| T-07 | todo | 0 | T-01,T-02,T-03,T-04,T-05,T-06 | development-guide.md 2.4 末尾の段落を計画単位のレビュータスク前提に書き直す | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
