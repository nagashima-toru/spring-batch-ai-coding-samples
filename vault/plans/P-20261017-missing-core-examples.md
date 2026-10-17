---
id: P-20261017-missing-core-examples
status: approved
---
# ゴール
GitHub Issue #61「サンプルで使われていない基盤部品の実例を追加する（Retry・JSON読込・固定長出力・ファイル退避・単発API）」を、既存のリファレンスサンプル（sample03・sample05）への組み込みで解消する。サンプルの本数は増やさない。

対象部品（batch-core `com.example.batch.core` 配下）: `item/JsonItemReaderFactory`, `item/FixedLengthItemWriterFactory`（＋`ByteLengthLineAggregator`）, `tasklet/FileMoveTasklet`, `tasklet/AbstractApiCallTasklet`, `exception/RetryableSystemException`。

## 分割方針
- SSoT（解説が正、コードは解説に従う）に合わせ、サンプルごとに「解説の改訂 → コード＋テスト」の順に分ける。解説タスク（T-01・T-03）は互いに独立で並行できる
- sample03 は変更が1ファイル（`Sample03JobConfig`）と結合テストに収まるので、コードを1タスク（T-02）にまとめる
- sample05 は変更が大きいので2タスクに分ける。T-04 は単発 API コール Step（`AbstractApiCallTasklet`）と 5xx の結合テスト（`RetryableSystemException`）、T-05 は `inputFile` によるリプレイ（`JsonItemReaderFactory`・Decider）。同じファイル（`Sample05JobConfig`・`Sample05ApiImportJobIT`）を変えるので T-05 は T-04 の後に直列にする
- T-02 と T-04 は変えるファイルが重ならないので並行できる
- 最後に索引（`batch-sample/docs/README.md`）の「実例なし（#61 で追加予定）」5行を実例に置き換える（T-06。コードが全部入った後）
- Flyway の新規ファイルは不要（既存テーブル `app.sales_transaction`・`app.api_user` だけを使う）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | sample03 解説に一時出力→配置と固定長出力（format）を書く | |
| T-02 | doing | 1 | T-01 | sample03 に一時出力→配置の Step と format=fixed の固定長出力を実装する | |
| T-03 | done | 1 | - | sample05 解説に件数取得 Step・inputFile リプレイ・リトライ方針を書く | |
| T-04 | review | 1 | T-03 | sample05 に AbstractApiCallTasklet の件数取得 Step と 5xx 異常終了の結合テストを追加する | |
| T-05 | todo | 0 | T-04 | sample05 に inputFile 指定時の JSON ファイル取込（リプレイ）を実装する | |
| T-06 | todo | 0 | T-02,T-05 | 基盤部品別の実例ジョブ索引の「実例なし」5行を実例に置き換える | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
- コードを変えるタスク（T-02・T-04・T-05）の受け入れ基準に `./mvnw clean verify`（結合テスト込み）の成功がある
- 計画の完了時、リポジトリ（`vault/`・`target/`・`.git/`・worktree を除く）に「#61 で追加予定」が残っていない
