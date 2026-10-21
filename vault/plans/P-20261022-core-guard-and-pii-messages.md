---
id: P-20261022-core-guard-and-pii-messages
status: approved
---
# ゴール
GitHub Issue #76 と #93（nagashima-toru/spring-batch-ai-coding-samples）を1つの計画で解決する。

- #76 batch-core: `ValidatingItemProcessorBase.process()` の final が外れ、入力チェックの実行が保証されない。`final` を外した理由（`@StepScope` のクラスプロキシ）を確かめ、`final` を保ったまま同じことができる形にする
- #93 batch-core: 固定長部品（`ByteLengthLineTokenizer`・`ByteLengthLineAggregator`）の例外メッセージに行・値がそのまま入る（方式設計 §8 の個人情報のログ出力禁止）。メッセージを位置・バイト数などの情報にし、単体テストの検証と `batch-sample/docs/sample03-file-export.md` の文言を直す

## 分割方針
- #76 と #93 は独立なので並行させる。#76 は T-01（設計の改訂）→ T-02（実装）の直列、#93 は T-03（読込側）と T-04（書込側）を並行させる。着手可能集合は最初に T-01・T-03・T-04 の3件になる
- #76 の方式：`process()` を final に戻し、`@StepScope` の Bean にする Processor は Bean メソッドの戻り値と注入先の型を `ItemProcessor<I, O>`（インタフェース）にする。スコーププロキシの対象型がインタフェースになると JDK 動的プロキシで作られ、final の `process()` を継承で上書きする必要が無くなる（`docs/decisions.md` の「final を外した」理由を解消する）。リポジトリ内で `@StepScope` の `ValidatingItemProcessorBase` 継承 Processor は `orderDailySalesProcessor`（`OrderItemProcessor`）1つだけ
- CLAUDE.md の「設計書と異なる実装（先に設計書を改訂してから実装する）」に従い、方式設計 §13・`docs/decisions.md`・ORDER-001 詳細設計の改訂（T-01）を実装（T-02）より先にする
- T-02 は `@StepScope` のプロキシの不具合が結合テストでしか出ない（`docs/decisions.md`）ため、`./mvnw clean verify`（Docker 必須）を基準に入れる
- #93 は Issue に挙がった2つのメッセージに加え、同じ `ByteLengthLineTokenizer` の境界分断のメッセージ（`field=%s line=%s`）も行をそのまま出しているので、T-03 で同じ扱いにする（Issue のタイトル「固定長部品の例外メッセージに行・値がそのまま入る」の範囲）
- T-01 は Markdown だけの変更で、ビルドの結果に影響しないため、ビルドの基準を入れない
- コードを変更するタスク（T-02・T-03・T-04）があるので、計画単位のレビュータスク T-05 を最後に足す

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | doing | 1 | - | `@StepScope` の Processor をインタフェース型の Bean にする方針を設計書に反映する | |
| T-02 | todo | 0 | T-01 | `ValidatingItemProcessorBase.process()` を final に戻し、`orderDailySalesProcessor` をインタフェース型の Bean にする | |
| T-03 | doing | 1 | - | `ByteLengthLineTokenizer` の例外メッセージから行の内容を除く | |
| T-04 | doing | 1 | - | `ByteLengthLineAggregator` の例外メッセージから値を除き、sample03 の解説を直す | |
| T-05 | todo | 0 | T-01,T-02,T-03,T-04 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## 次フェーズの候補（票は起こさない）
- 読込時の Skip では、`FlatFileItemReader` が例外を `FlatFileParseException`（メッセージに `input=[<行>]` を含む）で包み、`ErrorRecordSkipListener` がその `getMessage()` を WARN ログに出す。T-03 で `ByteLengthLineTokenizer` のメッセージを直しても、読込時の Skip の WARN ログには行がそのまま残る。#93 の範囲外のため、別の Issue として扱うかを人が判断する（`batch-core/src/main/java/com/example/batch/core/exception/ErrorRecordSkipListener.java`）
