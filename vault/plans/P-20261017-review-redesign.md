---
id: P-20261017-review-redesign
status: approved
---
# ゴール
GitHub Issue #78「レビュー工程の再設計：verifier・creator でのレビュー、規約レビューの改名、差分の外の指摘の扱い」を解消する。

前提：ハーネス本体（`.claude/agents/creator.md`・`.claude/agents/verifier.md`・`docs/vault-spec.md`）は ai-harness main（#214 を含む。コミット 0b5262e）に更新済みで、creator・verifier から組み込みの `/code-review` を呼べる（Issue の案2）。差分の外は「対象外（既存コード）」として FAIL にしない、設計変更が要る指摘は「人の判断が必要」で run が blocked にする、という規則はハーネス本体側にある。この計画ではハーネス本体のファイルを変えず、導入先側（スキル・docs・`vault/rules/`）だけを直す。

## 分割方針
- (a) T-01：規約レビューのスキル `review-code` を `review-standards` に改名し、参照箇所（CLAUDE.md・docs・PR テンプレート・各スキル・`vault/rules/`・checkstyle.xml のコメント）を追従させる。旧名の参照が残らないことはリポジトリ全体で確かめる（計画記録の `vault/plans`・`vault/tasks`・`vault/log`・`vault/verdicts` は除く）。checkstyle.xml を変えるのでこのタスクだけビルドを基準に入れる
- (b) T-02：改名後の `.claude/skills/review-standards/SKILL.md` の手順4（`/code-review` の実行）を、ハーネスの規則（差分の外は「対象外（既存コード）」、設計改訂が要るものは [Ask]）と整合させる。T-01 と同じファイルを変えるので after でつなぐ
- (c) T-03・T-04：verifier・creator のルールに、導入先固有の補足（`/code-review` の指摘と review-standards の Must/Should/Ask の対応、差分の外の扱い、規約チェックリストとの関係）だけを新しいファイルで書く。ハーネス本体の規則は写さず節名で参照する。どちらも T-02 の文言に合わせるので T-02 の後。互いに独立
- (d) T-05：planner ルールに、コードを変えるタスクの受け入れ基準へ `/code-review` の行を必ず入れるルールを新しいファイルで書く。行が verifier ルール（T-03 で作る）を参照するので T-03 の後
- T-06：開発ガイド（`docs/development-guide.md` §2.4）に、新しいルールファイルとレビューの流れを案内する。T-01 と同じファイルを変え、T-03〜T-05 で作るファイルを参照するので最後に置く
- Java コードの変更は無い。batch-sample の sample05 には触らない（別計画 #79 が進行中）
- この計画のタスクは文書と XML コメントの変更だけなので、`/code-review` の行は受け入れ基準に入れていない（T-05 のルールはこの計画の後の計画から効く）
- ビルドの前提：T-01 の `./mvnw` の基準は JDK 25 が要る。planner の試走の環境では JDK 21 しか無く `release version 25 not supported` で失敗したため、期待値 `1` は実行して確かめられていない。`docs/development-guide.md` §1.4 の準備（JDK 25 の用意と `~/.mavenrc`）を run の開始前に済ませておく

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | 規約レビューのスキルを review-standards に改名し参照を追従させる | |
| T-02 | done | 1 | T-01 | review-standards の手順4をハーネスの差分の扱いと整合させる | |
| T-03 | done | 1 | T-02 | verifier ルールに /code-review の指摘の区分の対応を書く | |
| T-04 | review | 1 | T-02 | creator ルールに /code-review の指摘の区分の対応を書く | |
| T-05 | todo | 0 | T-03 | planner ルールにコードを変えるタスクの /code-review の行を書く | |
| T-06 | todo | 0 | T-04,T-05 | 開発ガイドにレビューの流れと新しいルールを案内する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
- 旧名 `review-code` の参照が、計画記録（`vault/plans`・`vault/tasks`・`vault/log`・`vault/verdicts`）を除くリポジトリ全体に残っていない
- verifier・creator・planner のルールに、`/code-review` の扱い（区分の対応・差分の外を FAIL にしないこと・コードを変えるタスクへの基準の行）が書かれている
