# ジョブ1本を工程タスクに分けて after でつなぐ

## 目的
詳細設計がレビュー済みのジョブ1本を受け取った時に、implementation 以降の工程タスクに分けて `after` で順につなぎ、全タスクの受け入れ基準にビルドの成功を入れられるようにする。

## ルール
- 計画の前提は、詳細設計書がレビュー済みであること。`/review-design` を通った `docs/projects/{案件}/detail-design/{ジョブID}.md` を指す
- ゴール文や設計書からレビュー済みと確かめられない時は、タスクを起こさず計画票末尾の `## 人への質問` に書く
- 1計画で扱うジョブは1本にする。複数ジョブは計画を分ける（5タスクで、1計画7タスク以下の上限に収まる）
- ジョブ1本は implementation → unit-test → integration-test → review-code → system-test の順の5タスクに分ける
- 各タスクは `after` に直前の工程のタスク ID を書く。直列につなぎ、並行させない（先頭の implementation だけ `after` は空）
- 各タスクの「成果物」のパスは、対応する工程の `.claude/skills/{工程}/SKILL.md` の「入力と出力」節と `docs/architecture.md` §2.1 に従って宣言する。パスの一覧はここに書き写さない
- 全タスクの受け入れ基準に `./mvnw verify -DskipITs` の成功を入れる
- 成果物に `*IT.java` を含むタスクだけ `./mvnw verify` の成功も入れる（結合テストは Docker が要るため）
- 要件定義〜設計レビュー（`requirements`・`basic-design`・`detail-design`・`review-design`）はハーネスのタスクにしない
- 設計書と異なる実装が必要になりそうな点はタスクにせず `## 人への質問` に書く（`docs/development-guide.md` §5.5）

## 確認方法
- 計画票のタスク表が5行で、`after` が上記の順に1本の鎖になっている（先頭以外の `after` が直前の ID だけ）
- 各タスク票の受け入れ基準に `./mvnw verify -DskipITs` があり、`*IT.java` を成果物に持つタスクにだけ `./mvnw verify` もある
- レビュー済みと確かめられない場合、タスクが無く `## 人への質問` に質問がある
- 参照した節番号が `grep -n '^#' docs/development-guide.md docs/architecture.md` に見出しとして実在する
