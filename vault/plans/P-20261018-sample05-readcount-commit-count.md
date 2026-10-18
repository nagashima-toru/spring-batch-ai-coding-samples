---
id: P-20261018-sample05-readcount-commit-count
status: approved
---
# ゴール
GitHub Issue #81「sample05: 最後のチャンクのコミット後に取込Stepが COMPLETED 以外で終わると、取得件数が1件多くなり件数の突き合わせが失敗する」を解消する（コメントで統合された #82 のうち「件数の突き合わせに使う件数をコミット済み件数にする」部分を含む）。

- 現状：`ApiUserReadCountListener` が取得件数 `apiUserReadCount` を Reader の読込位置（`<Reader名>.read.count`）から求め、Step が COMPLETED の時だけ終端の読込1件を引いている。最後のチャンク（終端の null を含む）のコミット後に STOPPED/FAILED で終わると N+1 が保存され、リスタートしても N+1 のままで `sample05.02CountCheckStep` が失敗する（終了コード1）
- 方針：「COMPLETED なら1を引く」推定をやめ、batch-core に「Reader が返した null 以外の件数（コミット済み）を自分で Step の ExecutionContext に保存する」数え上げ部品（Reader デコレータ `CountingItemStreamReader`）を足し、sample05 はその件数を取得件数にする
- PR では `Closes #81` とする

## 分割方針
- SSoT の順に、設計（サンプル解説）→ batch-core の部品＋単体テスト → sample05 の Listener/JobConfig＋単体テスト → 結合テスト → 計画単位のレビュー、の直列につなぐ（後のタスクほど前のタスクの成果物を前提にする）
- `docs/architecture.md` には基盤部品の一覧が無い（§1〜§13 に部品の表が無いことを確認済み）ため変更しない。基盤部品の一覧は `.claude/skills/implementation/SKILL.md` の「最重要ルール」表と `batch-sample/docs/README.md` の「基盤部品別の実例ジョブ索引」にあるので、T-01 でこの2か所に部品を追記する（CLAUDE.md の「基盤部品で実現できる処理の独自実装禁止（implementation Skill の対応表参照）」を新しい部品にも効かせるため）
- 範囲外：#82 の「書込 Skip 後のリスタートで未書込の件を取りこぼす」問題そのものはタスクにしない（Spring Batch のリスタート挙動に由来し、別途判断が要るため）
- 範囲外：maxItemCount を使う経路（Issue に書かれた2つ目の再現経路）は sample05 が maxItemCount を使わないため結合テストにしない。数え方が maxItemCount・スキャンモードに依存しないこと（Reader が返した null 以外だけを数える）は T-02 の単体テストで確かめる
- ジョブ1本の工程分割（`vault/rules/planner/job-task-split.md`）は新規ジョブの実装向けのため、この不具合修正にはそのまま当てはめない。ビルドの基準（`-DskipITs` 系2本、`*IT.java` を含むタスクだけ `./mvnw clean verify`）はコードを変更するタスクに入れる
- コードを変更するタスク（T-02〜T-04）があるため、最後に計画単位のレビュータスク T-05 を置く
- ビルドには JDK 25 と Docker が要る（`docs/development-guide.md` §1.4）。planner の環境は JDK 21 のため、`./mvnw` を使う確認コマンドは planner が試走できていない（grep・smoke の確認コマンドは試走済み）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | sample05 解説の取得件数の数え方をコミット済み件数に改訂する | |
| T-02 | done | 1 | T-01 | batch-core に件数を数えて保存する Reader デコレータ CountingItemStreamReader を足す | |
| T-03 | done | 1 | T-02 | sample05 の取得件数を CountingItemStreamReader の件数から求める | |
| T-04 | done | 1 | T-03 | 最後のチャンクのコミット後の停止とリスタートを Sample05ApiImportJobIT で確かめる | |
| T-05 | review | 1 | T-01,T-02,T-03,T-04 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
