# コードレビューチェックリスト

## A. 基盤部品の利用（coding-standards.md §4.2 / implementation Skill 対応表）

- [ ] RestClient を直接使用していない（BatchHttpClient 経由）
- [ ] ファイル読み書きに基盤ファクトリを使用（自前の FlatFileItemReaderBuilder 組み立ては拡張理由がある場合のみ）
- [ ] 入力チェックは ValidatingItemProcessorBase 継承＋Bean Validation
- [ ] Skip は BusinessSkipPolicy（skipLimit 設定済み）、退避は ErrorRecordSkipListener
- [ ] 開始終了・件数ログを個別実装していない（共通リスナー登録のみ）
- [ ] 例外は Business/System/RetryableSystem の3階層（RuntimeException 直接送出なし）
- [ ] JobParametersValidator が全ジョブに設定されている
- [ ] 二重起動防止リスナーが全ジョブに登録されている

## B. Spring Batch 6 準拠（architecture.md §13）

- [ ] `org.springframework.batch.item.*`（5系パッケージ）の import が混入していない
- [ ] `@EnableBatchProcessing` を付けていない
- [ ] リスナー登録で `(Object)` キャストをしていない
- [ ] `@StepScope` ＋ SpEL でジョブパラメータを受けている（引数直渡しでない）
- [ ] リスタートが必要なアプリに spring-boot-starter-batch-jdbc がある

## C. トランザクション・リスタート安全性

- [ ] Writer 内で手動コミット/ロールバックしていない
- [ ] 洗い替え（truncate+insert）が同一 Step（同一Tx）内
- [ ] Processor に DB 更新等の副作用がない
- [ ] 取込が冪等（UPSERT / 洗い替え）
- [ ] Reader は状態保存対応の実装で name が設定されている（ジョブ内一意）

## D. 性能

- [ ] 大量読取はカーソル（MyBatisCursorItemReader）で全件メモリロードしていない
- [ ] ループ内で 1 件ずつ Mapper を呼んでいない（MyBatisBatchItemWriter 等でバッチ化）
- [ ] チャンクサイズ・Skip上限がハードコードされていない（設定外部化）
- [ ] 集計を Java 側のループでやっていない（SQL化を検討したか）

## E. SQL・MyBatis（coding-standards.md §5）

- [ ] SQL は Mapper XML（アノテーションSQLなし）
- [ ] テーブルはスキーマ修飾（app. / batch.）
- [ ] SELECT * を使っていない
- [ ] Flyway: 適用済みファイルを変更していない（新規 V 番号で追加）

## F. コード品質（coding-standards.md §1〜3）

- [ ] コンストラクタインジェクション（フィールドインジェクションなし）
- [ ] 機密情報・環境依存値のハードコードなし
- [ ] 例外の握りつぶし・スタックトレース喪失（cause 未指定）なし
- [ ] System.out / printStackTrace なし
- [ ] Javadoc（公開クラス）・コメントが日本語で「なぜ」を説明している

## G. テスト

- [ ] 単体テストが観点表（unit-test Skill references）に基づいている
- [ ] 結合テストに 正常 / 警告(Skip) / 異常 / リスタート / 冪等性 の観点がある
- [ ] テストメソッド名が日本語の「条件＿期待結果」形式
- [ ] `./mvnw verify` が通る（カバレッジ80%・Checkstyle・SpotBugs 含む）
