---
id: P-20261021-code-review-minor-fixes
status: approved
---
# ゴール
GitHub Issue #92「PR #91 の /code-review で出た軽微な指摘」の7項目をすべて解決する。

- batch-core（#77・#73 の変更）
  1. `CsvHeaderCheckTasklet` の BOM 定数がリテラルの U+FEFF 文字で書かれている → Unicode エスケープ（`\uFEFF`）表記にする
  2. `ByteLengthLineAggregator` と `ByteLengthLineTokenizer` の strict エンコードの重複 → 共通ヘルパーにまとめる
  3. 表せない文字の `BusinessException` の文言（U+FFFD の扱い、値そのものを含めてよいか）→ ログへの値の出力方針（方式設計 §8「個人情報・機密情報のログ出力は禁止」）と合わせて見直す
  4. 共有部品の契約の変更（表せない文字は例外）を Javadoc に明記し、リスタート時などのテストを足す
- batch-sample ORDER-001（#75 の変更）
  5. `OrderDailySalesRunParametersHolder` の遅延解決で、不正なパラメータの例外が入れ子になって出る
  6. `OrderDailySalesRunParametersHolder` に引数なしコンストラクタが無い（`@JobScope` のクラスプロキシ前提の設計を確認する）
  7. `OrderDailySalesJobIT` の「ログが1回だけ出る」検証が、ログ件数の取り方に依存して壊れやすい
- 備考：ORDER-001 の詳細設計書（`docs/projects/order-daily-sales/detail-design/ORDER-001.md`）も合わせて更新する

## 分割方針
- batch-core 側を2タスクに分ける。T-01 は項目1（BOM の表記。`CsvHeaderCheckTasklet` とそのテスト）、T-02 は項目2〜4（strict エンコードの共通ヘルパー・例外の文言・Javadoc の契約・テスト）。触るファイルが重ならないので並行できる
- ORDER-001 側を2タスクに分ける。T-03 は項目5・6（Validator でパスも起動前に検証し、Holder の Javadoc と詳細設計書を直す。設計書の改訂は SDD に従いこのタスクにまとめる）、T-04 は項目7と、T-03 の変更を結合テストで確かめるテスト（`OrderDailySalesJobIT` の1ファイル）。T-04 は T-03 の Validator の変更と設計書の §7 を前提にするので `after` に T-03 を持つ
- コードを変更するタスク（T-01〜T-04。いずれも `.java` を成果物に持つ）があるので、最後に計画単位のレビュータスク T-05 を足し、T-01〜T-04 を `after` に持たせる
- 全タスクの受け入れ基準に `./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false` と `./mvnw clean verify -DskipITs -pl batch-core` の成功を入れ、`*IT.java` を成果物に持つ T-04 と、計画全体を確かめる T-05 にだけ `./mvnw clean verify` を入れる（`vault/rules/planner/job-task-split.md` のビルドの基準に合わせる）。ビルドは Java 25 が要るので、確認コマンドの先頭に `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64` を付ける（この環境の既定の `JAVA_HOME` は Java 21）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | CsvHeaderCheckTasklet の BOM 定数とテストの BOM を Unicode エスケープ表記にする | |
| T-02 | done | 1 | - | 固定長部品の strict エンコードを StrictCharsetEncoder にまとめ、例外の文言と Javadoc の契約を直す | |
| T-03 | done | 1 | - | ORDER-001 のパスを Validator で起動前に検証し、Holder の Javadoc と詳細設計書を直す | |
| T-04 | done | 1 | T-03 | OrderDailySalesJobIT のログ回数の検証を ListAppender にし、パラメータ検証とプロキシのテストを足す | |
| T-05 | review | 1 | T-01,T-02,T-03,T-04 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## 次フェーズの候補
- `ByteLengthLineTokenizer`・`ByteLengthLineAggregator` の、表せない文字以外の `BusinessException` の文言（バイト長の超過の `value=`、バイト長の不一致・境界の分断の `line=`）も値そのものを含む。Issue #92 の項目3は「表せない文字の文言」なので本計画では変えない。ログへの値の出力方針（方式設計 §8）に合わせるなら別の Issue にする
- Spring Batch 標準の `FlatFileParseException` は入力行をメッセージに含む（`ErrorRecordSkipListener` の WARN ログに出る）。基盤部品の外の挙動なので本計画では扱わない
