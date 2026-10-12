---
id: P-20261012-order-daily-sales
status: approved
---
# ゴール
docs/projects/order-daily-sales/detail-design/ORDER-001.md（レビュー済み）の ORDER-001 を implementation〜system-test のタスクに分けて実装・検証する

## 分割方針
- `vault/rules/planner/job-task-split.md` に従い、ジョブ1本（ORDER-001 `orderDailySalesJob`）を implementation → unit-test → integration-test → review-code → system-test の5タスクに分け、`after` で直列につなぐ（並行させない）
- 前提の確認：詳細設計書 `docs/projects/order-daily-sales/detail-design/ORDER-001.md` の冒頭表の「ステータス」が「レビュー済み」（1.1 版。変更履歴に設計レビュー指摘の反映あり）、入力の基本設計書は 1.3 版・承認済み
- 詳細設計書 §6 の batch-core の拡張（`CsvHeaderCheckTasklet`・`CsvItemReaderFactory#createWithLineNumber`・`LineNumberedFieldSetMapper`）はジョブが使う部品なので、別タスクにせず T-01（implementation）に含める。単体テストは T-02 に含める
- 全タスクの受け入れ基準に `./mvnw verify -DskipITs` を入れる。`*IT.java` を成果物に持つ T-03・T-04 だけ `./mvnw verify` も入れる
- T-05（system-test）はコードを変えない。不具合を見つけたら blocked にして人に返す（system-test Skill の「修正は implementation 以降の工程を再度通す」に合わせる）
- 要件定義〜設計レビューはハーネスのタスクにしない（設計は main にマージ済み）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | implementation: ORDER-001 のジョブ実装・batch-core 拡張・Flyway V5 | |
| T-02 | done | 1 | T-01 | unit-test: ORDER-001 と batch-core 拡張の単体テスト | |
| T-03 | done | 1 | T-02 | integration-test: OrderDailySalesJobIT の結合テスト | |
| T-04 | done | 1 | T-03 | review-code: ORDER-001 のコードレビューと指摘の反映 | |
| T-05 | done | 1 | T-04 | system-test: order-daily-sales のシステムテストのシナリオと実施記録 | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
- 全タスク done の時点で `./mvnw verify` が成功し、`docs/projects/order-daily-sales/system-test/scenarios.md` の全シナリオが OK である

## 人への質問（回答済み）
1. 計画作成時のこの環境には JDK 25 が無く（`java -version` は 21.0.12）、`./mvnw verify -DskipITs` が `release version 25 not supported` でコンパイル前に失敗しました。また Docker デーモンが動いておらず（`docker info` が失敗）、Testcontainers の結合テスト（T-03・T-04 の `./mvnw verify`）と system-test（T-05。PostgreSQL 16 にジョブを実行する）を実施できません。`/run` を実行する環境に JDK 25（Temurin）と Docker を用意できますか。用意できない場合、T-01 から verifier が FAIL を返し続け、attempt の上限で blocked になります（検証できないものを PASS にしないルール：`vault/rules/verifier/build-verification.md`）
   - 回答：用意する。JDK 25 は `eclipse-temurin:25-jdk` イメージから `/opt/jdk-25` に展開し、`~/.mavenrc` で Maven の `JAVA_HOME` に設定した（人が許可）。Docker デーモンは起動済みで `postgres:16` イメージも取得済み。`./mvnw -v` が Java 25.0.4.1 を表示することを確認した
