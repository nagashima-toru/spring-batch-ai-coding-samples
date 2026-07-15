---
name: implementation
description: 詳細設計書を入力にジョブを実装する。「SALES-001 を実装して」「詳細設計書どおりにコードを書いて」「このジョブを実装して」のような発話や、設計レビュー完了後の次工程として発動する。バッチのジョブ・Step・Reader/Processor/Writer・Mapper の新規実装や修正の依頼全般で積極的に使用する。
---

# 実装 Skill

詳細設計書を入力に、基盤部品（batch-core）を利用してジョブを実装する。

## 入力と出力（工程連鎖）

| | 内容 |
|---|---|
| 入力 | `docs/projects/{案件名}/detail-design/{ジョブID}.md`（レビュー済みであること） |
| 参照 | `docs/coding-standards.md`（規約）、`docs/architecture.md`、類似サンプル（`batch-sample/`） |
| 出力 | ジョブ実装コード一式（下表）＋ Flyway マイグレーション |
| 次工程 | `/unit-test` → `/integration-test` → `/review-code` |

### 出力ファイルの格納先・命名規則

| 成果物 | 場所・命名 |
|---|---|
| Job設定クラス | `{module}/src/main/java/.../job_{内容}/{Xxx}JobConfig.java` |
| 入力レコード | 同パッケージ `{Xxx}Record.java`（record 型＋Bean Validation） |
| Processor | 同パッケージ `{Xxx}ItemProcessor.java`（ValidatingItemProcessorBase 継承） |
| Mapper IF / XML | `common/` または同パッケージ / `src/main/resources/mapper/{Xxx}Mapper.xml` |
| Flyway | `src/main/resources/db/migration/V{次番}__{内容}.sql`（既存ファイル変更禁止） |

## 進め方

1. 詳細設計書を読み、レビュー済みか確認する（未レビューならユーザーへ確認）
2. 設計書のStep構成表から使用する基盤部品を確認し、該当サンプルの実装を参照する
3. 設計書どおりに実装する。**設計との差異が必要になったら、コードを書く前に設計書の改訂を提案する**（SSoT）
4. **検証ループ（コンパイル）**: `./mvnw compile` を実行する。失敗したらエラー出力
   （コンパイルエラー / Checkstyle 違反）を読んで修正し再実行する（最大3ラウンド）
5. **検証ループ（デグレ確認）**: コンパイルが通ったら `./mvnw test -pl {module}` で
   既存テストが壊れていないことを確認する。失敗したら
   `{module}/target/surefire-reports/` を読んで修正し再実行する（最大3ラウンド）

ループの共通ルール（上限・早期エスカレーション・検証を弱める反則の禁止）は
`docs/development-guide.md` §5 に従う。

## 最重要ルール: 基盤部品を使わず独自実装してはならない

以下は**必ず batch-core の部品を使う**。同等機能の独自実装はレビューで差し戻しになる。

| やりたいこと | 使う部品 |
|---|---|
| HTTP 呼出 | `BatchHttpClient`（RestClient 直接使用禁止） |
| CSV / 固定長 / JSON の読み書き | `CsvItemReaderFactory` / `FixedLengthItemReaderFactory` / `JsonItemReaderFactory` / `CsvItemWriterFactory` / `FixedLengthItemWriterFactory` |
| API のページング取得 | `RestApiPagingItemReader` |
| 入力チェック | `ValidatingItemProcessorBase` 継承（Bean Validation 併用） |
| Skip 制御 | `BusinessSkipPolicy`（skipLimit 必須） |
| Skip レコードの退避 | `ErrorRecordSkipListener` |
| ファイル存在チェック / 移動退避 | `FileExistenceCheckTasklet` / `FileMoveTasklet` |
| 単発 API コール Step | `AbstractApiCallTasklet` 継承 |
| DB 件数チェック | `TableCountCheckTasklet` |
| パラメータ検証 | `JobParametersValidators` |
| 開始終了・件数ログ | `LoggingJobExecutionListener` / `LoggingStepExecutionListener`（個別ログ実装禁止） |
| 二重起動防止 | `DuplicateRunPreventingJobExecutionListener` |
| 例外 | `BusinessException` / `SystemException` / `RetryableSystemException`（RuntimeException 直接送出禁止） |
| ヘッダ・トレーラ出力 | `SummaryFooterCallback`（Writer の footer と Step の listener 両方に登録） |

基盤部品で実現できない場合は、業務側に書かず **batch-core への機能追加を提案**する。

## Spring Batch 6 実装上の注意（5系コードの流用禁止）

- 部品パッケージは `org.springframework.batch.infrastructure.item.*`（5系の `batch.item.*` ではない）
- Chunk Step は `new StepBuilder(name, jobRepository).<I,O>chunk(size).reader(...).processor(...)
  .writer(...).transactionManager(tx).faultTolerant().skipPolicy(...).skipListener(...)` の新モデル
- リスナー登録: インターフェース実装リスナーを `listener(Object)` オーバーロードに渡さない
  （`(Object)` キャスト禁止。アノテーション走査のみでインターフェースが無視される）
- `@EnableBatchProcessing` を付けない。リスタートが必要なアプリは
  `spring-boot-starter-batch-jdbc` が依存にあることを確認する（Boot 4 の既定はインメモリ）
- ジョブパラメータ受け取りは `@StepScope` ＋ `#{jobParameters['xxx']}`

## 全ジョブ共通の必須実装

- `JobParametersValidators.required(...)` によるパラメータ検証
- `DuplicateRunPreventingJobExecutionListener`・`LoggingJobExecutionListener` をジョブに、
  `LoggingStepExecutionListener` を各 Step に登録
- 取込の冪等性（UPSERT または洗い替え。coding-standards.md §4.3）
- SQL は Mapper XML・スキーマ修飾（`app.xxx`）・アノテーションSQL禁止
