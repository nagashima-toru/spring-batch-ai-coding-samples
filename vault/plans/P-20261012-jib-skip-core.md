---
id: P-20261012-jib-skip-core
status: approved
---
# ゴール

GitHub Issue #55「CD の jib:build が batch-core で「Main class was not found」になり失敗する」を解消する。

`.github/workflows/cd.yml` のイメージビルドは `-pl batch-sample -am ... package jib:build` で実行しており、`-am` により依存先の `batch-core`（main クラスを持たないライブラリ）でも jib のゴールが走って失敗する。`batch-core/pom.xml` の `<properties>` に `jib.skip=true` を設定し、batch-core ではイメージを作らないようにする。

## 分割方針

- 修正は `batch-core/pom.xml` へのプロパティ1行（とコメント）の追加だけなので、1タスクにする。
- `.github/workflows/cd.yml`・親 `pom.xml`・`batch-sample/pom.xml` は変えない（batch-sample は親 POM の子で batch-core の子ではないため、batch-core のプロパティは継承されない）。
- docs（`docs/architecture.md`・`docs/aws-setup.md` など）には jib のモジュール別の挙動の記述が無い（`grep -rn -i jib docs/` で該当なし）ため、docs の改訂タスクは起こさない。
- 再現の確認は、レジストリへ push しない `jib:buildTar` で行う。JDK・ネットワークに依存しない確認（`-pl batch-core jib:buildTar`、`package` を付けない reactor 実行でのスキップのログ）と、JDK 25 とベースイメージ取得が要る確認（Issue の再現コマンドの BUILD SUCCESS・`./mvnw clean verify`）を分けて基準にする。

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | batch-core の pom.xml に jib.skip=true を設定し CD の jib を batch-core でスキップさせる | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## 人への質問（回答済み）

1. 計画作成時の環境（このリポジトリのクラウド環境）には JDK 21 しか無く（`java -version` が 21.0.12）、`./mvnw clean verify -DskipITs -pl batch-core` は `release version 25 not supported` でコンパイルに失敗します。T-01 の受け入れ基準のうち JDK 25 が要るもの（Issue の再現コマンドの BUILD SUCCESS とイメージ tar の生成、`./mvnw clean verify -DskipITs -pl batch-core` の成功）は、run を JDK 25（Temurin）がある環境で実行する前提でよいですか。JDK 25 を用意できない場合、それらの基準を外して JDK に依存しない基準（`-pl batch-core jib:buildTar` のスキップ、`package` を付けない reactor 実行でのスキップのログ）だけで完了としてよいか、指示をください。
   - 回答：基準は変えない（B）。この環境に apt で `openjdk-25-jdk-headless` を入れ、`~/.mavenrc` で `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64` にしたので、`./mvnw` は JDK 25 で動く。
