---
id: P-20261011-harness-rules
status: done
---
# ゴール
spring-batch-ai-coding-samples で、詳細設計がレビュー済みのジョブを `/plan` に渡せば implementation〜system-test の工程をタスクに分けて実行・検証できるように、`vault/rules/` の各役割（common・planner・creator・verifier）に docs を参照する薄いルールを置き、`.claude/settings.json` の `permissions.allow` にビルドと Docker の許可を足す。（設計文書 `vault/designs/D-20261011-spring-batch-adapt.md` のフェーズ1）

## 分割方針
- ルールは役割ディレクトリごとに1ファイル（common・planner・creator・verifier の4本）とし、1タスク1ファイルにする。どれも互いの内容を参照しないので `after` の依存を張らず、並行で進められる
- `.claude/settings.json` の変更は独立した1タスク（T-05）にし、ルールのタスクと成果物を混ぜない（前回 auto mode の分類器に拒否されたため。確認が出たら人が承認する）
- ルールは docs を正本にした薄いものにし、本文を書き写さず、パスと節番号で参照する（設計文書の決定事項）
- このフェーズは Java のコードを変えないので、各タスクの受け入れ基準に `./mvnw verify -DskipITs` は入れない（決定事項の「全タスクに `./mvnw verify -DskipITs`」は、このフェーズで作るルールに従って起こすジョブの計画が対象）
- `bash scripts/smoke.sh` の `fail=0` は、smoke が `.claude/settings.json` を読む検査（merge_settings_json）を持つため T-05 の基準に入れる。ルールのファイルは smoke の refcheck の対象外（`vault/rules/` 配下は除外。さらに導入先の印 `.claude/harness-manifest.json` があるので refcheck は検査しない）
- `docs/`・`CLAUDE.md`・`.claude/skills/` は変更しない（フェーズ1の決定済み）。フェーズ2（docs への使い方の追記と設計文書の削除）は範囲外

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | 共通ルール：正本の docs を役割ごとに案内する | |
| T-02 | done | 1 | - | planner ルール：ジョブ1本を工程タスクに分けて after でつなぐ | |
| T-03 | done | 1 | - | creator ルール：工程 Skill に従い確認手順を読み替える | |
| T-04 | done | 1 | - | verifier ルール：ビルドを実行して判定する | |
| T-05 | done | 1 | - | settings.json の permissions.allow にビルドと Docker の許可を足す | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
- `bash scripts/rules.sh creator`・`bash scripts/rules.sh planner`・`bash scripts/rules.sh verifier` の出力に、それぞれ common と自分の役割のルールが1本ずつ出る
- `bash scripts/smoke.sh 2>&1 | tail -1` の出力が `fail=0` で終わる
- 各ルールファイルが100行以内で、docs の本文を書き写していない（参照先のパスと節番号で示している）
