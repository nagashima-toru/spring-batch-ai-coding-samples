# コーディング規約

本規約は `implementation` / `review-code` Skill から参照される。
機械チェック可能なルールは Checkstyle（`config/checkstyle/checkstyle.xml`）・SpotBugs で強制され、本書はその背景と、機械チェックできない設計判断の規約を定める。

## 1. 一般規約（Java）

- **Java 25 の標準機能を使う**: レコードは `record`、不変コレクションは `List.of()`、文字列結合はテキストブロック、`switch` 式・パターンマッチを積極的に使用する
- インデントはスペース4、1行120文字以内、ファイルは UTF-8 / LF
- `var` はローカル変数で右辺から型が自明な場合のみ使用可
- null を返さない。「無い」は `Optional` / 空コレクションで表現する。引数の null 許容は `@Nullable` を明示
- フィールドインジェクション禁止。**コンストラクタインジェクション**のみ（`@Autowired` 省略）
- ユーティリティクラスは `final` ＋ private コンストラクタ
- マジックナンバー・リテラル直書き禁止。定数または `@ConfigurationProperties` へ外部化する

## 2. コメント・Javadoc

- ドキュメント・コメントは**日本語**で書く
- 公開クラスには Javadoc を必須とし、基盤部品（batch-core）は「**用途・使い方・拡張ポイント**」の3点を必ず記載する
- コメントは「なぜ」を書く。「何をしているか」はコードで表現する（コードの直訳コメント禁止）

## 3. 例外処理

- 例外は batch-core の階層（`BusinessException` / `SystemException` / `RetryableSystemException`）を使用する。`RuntimeException` を直接 throw しない
- 例外の握りつぶし禁止。catch したら「回復する・変換して再送出する・ログして方針どおり終了させる」のいずれかを行う
- 入力データ起因のエラーは `BusinessException`（Skip 対象）、環境起因は `SystemException` 系に分類する（方式設計 §5）
- スタックトレースを失う `throw new XxxException(e.getMessage())` 禁止。必ず cause を渡す

## 4. Spring Batch 実装規約

### 4.1 基本

- **Spring Batch 6 の API で実装する。5系以前のサンプルコード（Web記事等）の流用禁止**（方式設計 §13）
- `@EnableBatchProcessing` を付けない（Boot の自動構成を使う）
- Job/Step/Reader/Processor/Writer は `@Configuration` クラス（`SampleNNJobConfig`）に Bean 定義でまとめる
- Step スコープ Bean（`@StepScope`）でジョブパラメータを受け取る場合は SpEL `#{jobParameters['xxx']}` を使用する
- ジョブには必ず `JobParametersValidator` を設定する（基盤部品の Validator を利用）
- Chunk/Tasklet の選択・Step 分割・トランザクション設計は方式設計 §3〜4 に従う

### 4.2 部品の利用（最重要）

- **batch-core にある機能の独自実装禁止**。以下は必ず基盤部品を使う:
  - HTTP 呼出（RestClient 直接使用禁止 → HTTP Client 基盤）
  - ファイル存在チェック・移動・退避（Files API 直接使用禁止 → 共通 Tasklet）
  - CSV/固定長/JSON の読み書き（→ 共通 ItemReader/Writer ファクトリ）
  - 入力チェック（→ バリデーション付き Processor 基底）
  - 開始終了・件数ログ（→ 共通 Listener。個別ジョブでログ実装しない）
- 基盤部品で実現できない要件が出た場合は、業務側にコピペせず batch-core への機能追加を検討する

### 4.3 リスタート安全性

- Reader/Writer は状態保存に対応した Spring Batch 標準実装（またはその派生）を使う。自作 Reader で `ExecutionContext` 保存を実装しないまま大量データを扱わない
- Processor は副作用を持たない（DB 更新は Writer に集約する）
- 取込は UPSERT または洗い替えで冪等にする（方式設計 §6.2）

## 5. MyBatis 規約

- SQL は Mapper XML に書く。`@Select` 等のアノテーション SQL 禁止
- テーブルは必ずスキーマ修飾する（`app.customer`）
- 動的 SQL（`<if>` 等）は最小限にし、条件分岐が複雑な場合は Mapper メソッドを分ける
- 大量読取は `MyBatisCursorItemReader`、大量書込は `MyBatisBatchItemWriter`。ループ内での1件ずつの Mapper 呼出禁止
- `SELECT *` 禁止。カラムを明示する

## 6. 設定・リソース

- 環境差分は `application-{profile}.yml` へ。コード内の環境分岐（`if (env.equals("prod"))`）禁止
- 機密情報（パスワード・APIキー・アカウントID）のハードコード禁止。環境変数参照とする
- 設定値は `@ConfigurationProperties`（record 型）で受け、`@Value` の散在を避ける
- ファイルパスはジョブパラメータまたは設定で受け取る。ハードコード禁止

## 7. テストコード規約

- テストクラス名: 単体 `{対象}Test`、結合 `{ジョブ名}IT`
- テストメソッド名は日本語で「条件＿期待結果」を表現する（例: `不正な日付フォーマットの場合_BusinessExceptionをスローする`）
- AAA（Arrange-Act-Assert）構造を空行で区切る。1テスト1検証観点
- アサーションは AssertJ（`assertThat`）を使用する
- 単体テストで Spring コンテキストを起動しない（Mockito で依存を差し替える）
- 結合テストは `@SpringBatchTest` + Testcontainers を使用し、`JobOperator` でジョブを起動して `ExitStatus`・DB/ファイルの結果を検証する
- スリープによる同期待ち禁止（Awaitility 等の条件待ちを使う）

## 8. コミット・PR

- コミットメッセージは日本語。1行目に要約（50字程度）、本文に「なぜ」を書く
- 1 コミット 1 論理変更。フォーマット変更と機能変更を混ぜない
- PR には設計書リンクと `review-code` Skill の実施記録を含める（テンプレート準拠）

## 9. 禁止事項まとめ

| 禁止 | 理由 / 代替 |
|---|---|
| Spring Batch 5 系 API・サンプルの流用 | 6系で変更あり。公式6系リファレンス準拠 |
| 基盤部品と同等機能の独自実装 | batch-core を利用・拡張する |
| フィールドインジェクション | コンストラクタインジェクション |
| アノテーション SQL | Mapper XML |
| 全件メモリロード（`selectList` での大量読取） | カーソルフェッチ |
| 機密情報・環境依存値のハードコード | 環境変数・設定ファイル |
| `System.out.println` / `printStackTrace` | SLF4J ロガー |
| Flyway 適用済みマイグレーションファイルの変更 | 新しいバージョン番号のファイルを追加 |
