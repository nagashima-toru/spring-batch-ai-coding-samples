---
id: P-20261017-naming-convention-unify
status: approved
---
# ゴール
GitHub Issue #60「命名規約（architecture §11）を案件ジョブにも適用できる形に改訂し、Skill・README と統一する」を解消する。

## 分割方針
- SSoT の順に進める。まず方式設計書 `docs/architecture.md` の §11（命名規約）を業務ジョブ（案件ジョブ）の規約を主とする形に改訂し、§10 の設定管理の記述を実態に合わせる（T-01）。命名を参照・重複定義している文書（implementation Skill・コーディング規約・batch-sample の解説）は T-01 の後に、§11 と矛盾しないように直す（T-02・T-03・T-07）
- `NotifyCandidateProcessor` の改名は、設計書 ANNOUNCE-001.md の改訂（T-04）を先に行い、その後にコード・テストを改名する（T-05）。設計書と異なる実装を作らないため
- 本数表記（README.md・CLAUDE.md・batch-sample/pom.xml のコメント）は §11 と独立しているので依存なし（T-06）。主な成果物は README.md で、同じ表記の付随ファイルとして CLAUDE.md と batch-sample/pom.xml を同じタスクで直す
- Spring Batch のジョブ名・Step 名・ItemStream の名前（メタデータに残る）と Flyway 適用済みファイルは変えない
- 案件ジョブの索引・位置付けの拡充は Issue #62 の範囲なので、この計画では命名に関する記述の整合だけを行う

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | review | 1 | - | architecture.md §11 を業務ジョブ主体の命名規約に改訂し §10 を実態に合わせる | |
| T-02 | todo | 0 | T-01 | implementation Skill の命名表を §11 と整合させ正本を §11 と明記する | |
| T-03 | todo | 0 | T-01 | coding-standards.md の Job 設定クラス名の記述を §11 と整合させる | |
| T-04 | todo | 0 | T-01 | 設計書 ANNOUNCE-001.md の Processor クラス名を NotifyCandidateItemProcessor に改訂する | |
| T-05 | todo | 0 | T-04 | NotifyCandidateProcessor を NotifyCandidateItemProcessor に改名する（コード・テスト） | |
| T-06 | review | 1 | - | README.md・CLAUDE.md・batch-sample/pom.xml の本数表記を実態に合わせる | |
| T-07 | todo | 0 | T-01 | batch-sample/docs/README.md の案件ジョブの命名の記述を §11 参照に直す | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
- 全タスク完了後、リポジトリ（`vault/` を除く）に `NotifyCandidateProcessor`・`設定管理部品`・`サンプルジョブ9本` が残っていない
- 全タスク完了後、`docs/architecture.md` §11 が業務ジョブの規約を主とし、リファレンスサンプル（番号付き）と骨格サンプル（job08・job09）を例外として明記している

## 環境の注意（planner の試走結果）
- planner の環境では `./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false` が `release version 25 not supported`（JDK 21 しか無い）で BUILD FAILURE になった。T-05 の受け入れ基準はこのビルドの成功を含むため、run の環境に JDK 25（Temurin）が無いと T-05 は満たせない
- 対応：オーケストレーターが apt で `openjdk-25-jdk-headless` を入れ、`~/.mavenrc` で JAVA_HOME を設定した（api.adoptium.net はネットワークポリシーで拒否されるため）。main の状態で同じビルドが成功することを確認済み
