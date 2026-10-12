# 決定事項

このリポジトリで決めたことのうち、docs の本文に無いものを1件1行で残す。新しい決定は表の末尾に追記する。

| 決めたこと | なぜ | 出典 |
|---|---|---|
| 全タスクの受け入れ基準に `./mvnw verify -DskipITs` の成功を入れ、結合テスト（`*IT.java`）を成果物に含むタスクだけ `./mvnw verify` の成功も入れる | Docker が無い環境でも大半のタスクを判定でき、結合テストは要るタスクでだけ確かめれば足りるため | D-20261011-spring-batch-adapt |
| `vault/rules/` は docs を正本にした薄いルールにし、どの docs を読むかと要点だけを書く。docs の本文は書き写さない | このリポジトリは `docs/` を SSoT にしており、ルールとの二重管理を避けるため | D-20261011-spring-batch-adapt |
| `.claude/settings.json` の `permissions.allow` に `Bash(./mvnw *)`・`Bash(docker compose *)`・`Bash(docker info*)` を足す | 無人実行（`claude -p`）でビルドと DB 起動が拒否されないようにするため | D-20261011-spring-batch-adapt |
| creator が Skill のユーザー確認の手順に当たった時は `sdd-pipeline` の読み替え表に従い、`[Ask]` に当たるものは独断せず blocked にする | creator はユーザーに質問できず、パイプライン用の読み替えが定義済みのため | D-20261011-spring-batch-adapt |
| `./mvnw verify -DskipITs` の受け入れ基準では、batch-sample のカバレッジ（JaCoCo 80%）を判定しない（全モジュールの Checkstyle・SpotBugs・単体テストと batch-core のカバレッジまで）。batch-sample のカバレッジは結合テストを含む `./mvnw verify` で判定する | batch-sample のカバレッジはモジュール全体で判定し、既存ジョブの多くは結合テストでしか網羅されないため、まっさらな worktree の `-DskipITs` では main の時点で 0.22 しかなく必ず失敗する（`target/` に結合テストの実行データが残っていると通ってしまい、判定がぶれる） | P-20261012-order-daily-sales（/plan unblock の回答） |
| `@StepScope` で生成する Bean のクラスは final にせず、継承元の public メソッドも final にしない（`ValidatingItemProcessorBase#process` も final を外した） | スコーププロキシはクラスを継承して作られ、final メソッドは横取りできずに未初期化のプロキシ本体で実行されて NullPointerException になる。単体テストでは見つからず結合テストで初めて出る | P-20261012-order-daily-sales（T-03 の blocked） |
