# 詳細設計書: ORDER-001 orderDailySalesJob

| 項目 | 内容 |
|---|---|
| ジョブID / ジョブ名（Bean名） | ORDER-001 / `orderDailySalesJob` |
| 入力（基本設計書） | docs/projects/order-daily-sales/basic-design.md（1.4 版・承認済み） |
| ステータス | レビュー済み |

## 1. ジョブ構成

| 項目 | 内容 |
|---|---|
| ジョブパッケージ | `com.example.batch.sample.job_order_daily_sales`（batch-sample モジュール。基本設計書 §1） |
| Config クラス | `OrderDailySalesJobConfig`（`JOB_NAME = "orderDailySalesJob"` を定数で保持） |
| ジョブパラメータ検証 | `OrderDailySalesJobParametersValidator`（新規。§1.1） |
| ジョブリスナー | `DuplicateRunPreventingJobExecutionListener`（二重起動防止）／`LoggingJobExecutionListener`（標準ログ・Skip 発生時の警告終了）。いずれも `BatchCoreAutoConfiguration` 登録済みの共通 Bean を注入する |
| チャンクサイズ・Skip 上限 | `SampleProperties#chunkSize()`（既定100）・`SampleProperties#skipLimit()`（既定10）。既存の共通設定をそのまま使う |
| 案件固有の設定 | `SampleProperties` にネストした record `OrderDailySales(String inputDir, String errorDir)` を追加する（既存の `Job05` と同じ形。§1.2） |
| batch-core の拡張 | `CsvHeaderCheckTasklet`（新規）・`CsvItemReaderFactory#createWithLineNumber`（追加）・`LineNumberedFieldSetMapper`（新規）。§6 |
| 追加依存 | なし |

### 1.1 ジョブパラメータと検証

| パラメータ名 | 型（起動引数の書き方） | 必須 | 検証内容 | 検証エラー時 |
|---|---|---|---|---|
| scheduledTime | String（`scheduledTime=2026-10-11T17:00:00Z`） | ○ | 存在すること。`OffsetDateTime.parse`（ISO-8601、オフセット必須）で解釈できること | `JobParametersInvalidException`（Step 実行前に異常終了＝1） |
| targetDate | java.time.LocalDate（`targetDate=2026-10-11,java.time.LocalDate`） | - | 指定された場合、パラメータの型が `LocalDate` であること（型サフィックスを付け忘れると String になるため検出する） | 同上 |
| inputFile | String | - | 指定された場合、`Path.of` で変換できること（存在確認は Step 1） | `JobParametersInvalidException`（Step 実行前に異常終了＝1） |
| errorFile | String | - | 指定された場合、`Path.of` で変換できること | 同上 |

- `OrderDailySalesJobParametersValidator`（`JobParametersValidator` を実装）は、まず
  `JobParametersValidators.requiredAndOptional(new String[]{"scheduledTime"}, new String[]{"targetDate", "inputFile", "errorFile"})`
  に委譲し、基盤部品が拒否しない未知のキーを許可キー以外として `InvalidJobParametersException` で拒否してから（方式設計 §3.4）、上表の書式・型を検証する
- `targetDate` を型サフィックスなしの不正な日付文字列で渡した場合も「型が LocalDate でない」として検出する。
  `,java.time.LocalDate` 付きで日付に変換できない値を渡した場合は、起動時の JobParameters 変換で失敗する（基本設計書 §2 エラー処理）

**実行時パラメータの確定（省略時の補完）**: `OrderDailySalesRunParameters`（新規 record。`targetDate: LocalDate`・
`inputFile: Path`・`errorFile: Path`）の static メソッド
`resolve(String scheduledTime, LocalDate targetDate, String inputFile, String errorFile, OrderDailySales settings)`
に補完規則を実装する（単体テスト可能にするため Config に書かない）。
- record は final クラスのため、そのままでは `@JobScope`・`@StepScope` のスコーププロキシ
  （クラスを継承して作る）を作れない。そこで record を包む `OrderDailySalesRunParametersHolder`（final にしないクラス。
  `get()` で record を返す）を `@JobScope` の Bean `orderDailySalesRunParameters` にする
- スコーププロキシは CGLIB でこのクラスを継承して作り、Objenesis でコンストラクタを呼ばずに生成されるため、
  引数なしコンストラクタは不要
- resolve の入力はすべて起動前に Validator が検証するので、Bean の生成（Step の実行中の遅延解決）で
  不正なパラメータの例外は起きない
- この Bean はジョブパラメータ4つ（`#{jobParameters['scheduledTime']}` String・`['targetDate']` LocalDate・
  `['inputFile']` String・`['errorFile']` String。省略時は null）と `SampleProperties` を受け取り、
  `OrderDailySalesRunParameters.resolve(...)` を1回呼び、補完した3つの値を INFO ログに1回出す。
  値が必要な `@StepScope` の Bean メソッド（Bean 一覧の備考に「RunParameters の」とあるもの）はこの Bean を受け取って使う。
  補完規則は resolve の1か所にだけある。解決とログは JobExecution ごとに1回（リスタートでは新しい JobExecution で
  Bean が作り直されるが、日付は `scheduledTime` から導くので値は変わらない）

| 値 | 指定時 | 省略時 |
|---|---|---|
| targetDate | 指定値 | `OffsetDateTime.parse(scheduledTime).atZoneSameInstant(ZoneId.of("Asia/Tokyo")).toLocalDate().minusDays(1)` |
| inputFile | 指定値 | `{inputDir}/orders_{targetDate:yyyyMMdd}.csv` |
| errorFile | 指定値 | `{errorDir}/orders_{targetDate:yyyyMMdd}_{scheduledTime を Asia/Tokyo に変換した日時:yyyyMMddHHmmss}_error.txt` |

- 例: `scheduledTime=2026-10-11T17:00:00Z`（省略可の3つは省略）→ targetDate=2026-10-11、
  inputFile=`{inputDir}/orders_20261011.csv`、errorFile=`{errorDir}/orders_20261011_20261012020000_error.txt`
- 補完した3つの値は INFO ログに1回出力する（運用者がどのファイルを処理したか追えるようにする）
- `LocalDate.now()`・`Clock` は使わない（日付はすべて `scheduledTime` から導出し、リスタートで値が変わらないようにする）

### 1.2 設定（application*.yml）

| キー | 既定（application.yml） | local（application-local.yml） | 説明 |
|---|---|---|---|
| `sample.order-daily-sales.input-dir` | `/data/in` | `batch-sample/data/in` | 受注CSVの既定ディレクトリ |
| `sample.order-daily-sales.error-dir` | `/data/error` | `batch-sample/data/error` | エラーファイルの既定ディレクトリ |

- dev/prod の実際の配置先は要件定義書 未確定 No.4（S3 等）の確定後に見直す。それまでは既定値（コンテナ内パス）を使う

### Step 一覧・フロー

| # | Step名 | Step Bean名 | 型 | 概要 | 失敗時 |
|---|---|---|---|---|---|
| 1 | `orderDailySales.01FileCheckStep` | `orderDailySalesFileCheckStep` | Tasklet | 入力ファイルの存在・読取可能性の確認 | 異常終了（1）。本テーブル不変 |
| 2 | `orderDailySales.02HeaderCheckStep` | `orderDailySalesHeaderCheckStep` | Tasklet | ヘッダ行の照合 | 異常終了（1）。本テーブル不変 |
| 3 | `orderDailySales.03ClearWorkStep` | `orderDailySalesClearWorkStep` | Tasklet | エラーファイルを削除し、ワークテーブルを空にする | 異常終了（1）。本テーブル不変 |
| 4 | `orderDailySales.04ImportStep` | `orderDailySalesImportStep` | Chunk | CSV → ワークテーブル（不正行は Skip） | 異常終了（1）。ワークにコミット済みチャンクが残る（調査用）。リスタートでは Step 3 から作り直す |
| 5 | `orderDailySales.05ReplaceStep` | `orderDailySalesReplaceStep` | Tasklet | 本テーブルの対象日分を1Txで入れ替え＋集計 | 異常終了（1）。Tx 全体ロールバックで本テーブル不変 |

- 分岐なしの直列フロー（`start(01).next(02).next(03).next(04).next(05)`）
- **Step 1〜4 に `allowStartIfComplete(true)` を設定する**。リスタート時、完了済みの Step 1〜4 も再実行し、取込を最初から
  やり直す（基本設計書 §2 リラン設計。ワークは全 JobInstance で共有のため、リスタート前のワークを使わない）。
  Step 5 には設定しない（Step 5 が完了していればジョブは COMPLETED で、同一パラメータのリスタートはできない）
- 全 Step に `LoggingStepExecutionListener`（共通 Bean）を登録する

### Bean 一覧（実装工程への確定指示）

| Bean名 | 型 | スコープ | 備考 |
|---|---|---|---|
| `orderDailySalesJob` | `Job` | singleton | Validator・2リスナー・5 Step |
| `orderDailySalesRunParameters` | `OrderDailySalesRunParametersHolder` | `@JobScope` | §1.1 実行時パラメータの確定。ジョブパラメータ4つ・`SampleProperties`。resolve を1回呼びログを1回出す |
| `orderDailySalesFileCheckTasklet` | `FileExistenceCheckTasklet` | `@StepScope` | RunParameters の `inputFile()` |
| `orderDailySalesHeaderCheckTasklet` | `CsvHeaderCheckTasklet` | `@StepScope` | RunParameters の `inputFile()`・UTF-8・`OrderCsvRecord.COLUMN_NAMES` |
| `orderDailySalesClearWorkTasklet` | `OrderDetailWorkClearTasklet` | `@StepScope` | §2 Step 3。RunParameters の `errorFile()`・`OrderDetailWorkMapper` |
| `orderDailySalesReader` | `FlatFileItemReader<OrderCsvRecord>` | `@StepScope` | §2 Step 4。RunParameters の `inputFile()`。読取位置を保存しない（`saveState=false`） |
| `orderDailySalesProcessor` | `ItemProcessor<OrderCsvRecord, OrderDetailWork>`（実体は `OrderItemProcessor`） | `@StepScope` | §2 Step 4。Step 実行ごとに既出キーの状態を新しく（空から）持つため Step スコープ。RunParameters の `targetDate()` |
| `orderDailySalesWriter` | `MyBatisBatchItemWriter<OrderDetailWork>` | singleton | statementId `...OrderDetailWorkMapper.insert` |
| `orderDailySalesErrorRecordSkipListener` | `ErrorRecordSkipListener<OrderCsvRecord, OrderDetailWork>` | `@StepScope` | RunParameters の `errorFile()` |
| `orderDailySalesReplaceTasklet` | `OrderDailySalesReplaceTasklet` | `@StepScope` | RunParameters の `targetDate()`・`OrderDetailMapper`・`DailyStoreSalesMapper` |

- Mapper は `@Mapper` インタフェースとし MyBatis Starter の自動登録に任せる（Config で `@Bean` 定義しない）
- **`@StepScope` で生成するクラスは final にしない**（record も不可）。スコーププロキシがクラスを継承して作られるため。
  本案件で新規作成する対象は `CsvHeaderCheckTasklet`（batch-core）・`OrderDetailWorkClearTasklet`・`OrderItemProcessor`・`OrderDailySalesReplaceTasklet`。
  `@JobScope` の `OrderDailySalesRunParametersHolder` も同じ理由で final にしない（`get()` も final にしない）。
  Bean メソッドの戻り値の型は具象クラスにする（プロキシが `StepExecutionListener` 等のインタフェースも実装するように）
  ただし `ValidatingItemProcessorBase` を継承した Processor（`orderDailySalesProcessor`）は、戻り値と注入先の型を `ItemProcessor<OrderCsvRecord, OrderDetailWork>` にする（docs/architecture.md §13）
  継承元の public メソッドも final にしない（final だとプロキシが上書きできず、
  プロキシ経由の呼び出しで target の状態が使われない）。`ValidatingItemProcessorBase#process` は例外で final にし、上のとおりインタフェース型の Bean にする（Issue #76）

## 2. Step 詳細設計

### Step 1: orderDailySales.01FileCheckStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `FileExistenceCheckTasklet(runParameters.inputFile())` | `FileExistenceCheckTasklet` |

#### 処理フロー

1. 入力ファイルが存在し、通常ファイルで、読取可能であることを確認する（基盤部品の処理）
2. 満たさなければ `BusinessException` が送出され Step は失敗する

### Step 2: orderDailySales.02HeaderCheckStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `CsvHeaderCheckTasklet(runParameters.inputFile(), UTF_8, OrderCsvRecord.COLUMN_NAMES)` | `CsvHeaderCheckTasklet`（本案件で batch-core に追加。§6.1） |

#### 処理フロー

1. 入力ファイルの1行目を UTF-8 で読み、カンマで分割する。1行目の先頭の BOM（U+FEFF）は取り除いてから分割する
2. 分割結果が期待するヘッダ名の並び（`order_no,line_no,order_date,store_code,item_code,quantity,unit_price`）と
   **完全一致**（順序・件数・大文字小文字・前後空白を含む）することを確認する
3. 一致しない（空ファイル・項目名違い・列数違いを含む）場合は `BusinessException` を送出し Step は失敗する

### Step 3: orderDailySales.03ClearWorkStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `OrderDetailWorkClearTasklet`（新規。`Tasklet` を直接実装。コンストラクタで `errorFile`（`Path`）と `OrderDetailWorkMapper` を受け取る） | なし（基盤部品に任意 SQL を実行する汎用 Tasklet・ファイルを削除する Tasklet は無い。POINT-001 と同じ判断） |

#### 処理フロー

1. エラーファイル（`runParameters.errorFile()`）があれば削除する（`Files.deleteIfExists`）。リスタートで取込を
   やり直す時に、前の実行回の Skip 行が残って重複しないようにするため（基盤部品 `ErrorRecordSkipListener` は追記で書く）。
   削除に失敗した場合（`IOException`）は `SystemException`（メッセージにパスを含める）に包んで送出し、Step は失敗する
2. `TRUNCATE TABLE app.order_detail_work` を実行する（Tasklet の Tx 内。PostgreSQL の TRUNCATE はトランザクショナル）
3. INFO ログ「受注明細ワークを初期化しました: errorFile={} 削除={}」を出力する（削除＝ファイルがあって削除したか）

- 1 はトランザクションの外の操作のため、2 が失敗してロールバックされてもエラーファイルの削除は戻らない。
  リスタートで Step 3 から取込をやり直し、エラーファイルも作り直されるため問題ない

### Step 4: orderDailySales.04ImportStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Reader | `CsvItemReaderFactory.createWithLineNumber("orderDailySalesReader", new FileSystemResource(inputFile), UTF_8, 1, OrderCsvRecord.COLUMN_NAMES, OrderCsvRecord::of)` で生成し、`setSaveState(false)` で読取位置の保存を無効にする（リスタートでもファイルの先頭から読む。`Sample03JobConfig` の Reader・Writer と同じ扱い） | `CsvItemReaderFactory`（拡張。§6.2）・`LineNumberedFieldSetMapper` |
| Processor | `OrderItemProcessor`（新規。`ValidatingItemProcessorBase<OrderCsvRecord, OrderDetailWork>` を継承する。`StepExecutionListener` は実装しない） | `ValidatingItemProcessorBase` |
| Writer | `MyBatisBatchItemWriter<OrderDetailWork>`（statementId `com.example.batch.sample.job_order_daily_sales.OrderDetailWorkMapper.insert`。`assertUpdates` は既定（true）） | なし（MyBatis 標準） |
| Skip/Retry | `.faultTolerant().skipPolicy(new BusinessSkipPolicy(properties.skipLimit())).skipListener(orderDailySalesErrorRecordSkipListener)`。Retry は設定しない | `BusinessSkipPolicy`・`ErrorRecordSkipListener` |
| リスナー | `loggingStepExecutionListener` | `LoggingStepExecutionListener` |
| チャンクサイズ | `properties.chunkSize()`（既定100） | `SampleProperties` |

**入力レコード `OrderCsvRecord`**（record。全項目を String で受け、形式は Bean Validation で検証する。
型変換の失敗をパースエラーにせず、項目名付きのエラー理由をエラーファイルへ出すため）:

| コンポーネント | 型 | 内容 |
|---|---|---|
| orderNo, lineNo, orderDate, storeCode, itemCode, quantity, unitPrice | String | CSV の各列（列名は `COLUMN_NAMES` 定数。ヘッダ名と同じ snake_case 文字列の配列） |
| lineSeq | int | Reader が付与する行番号（§6.2。`FlatFileItemReader` が `LineMapper` に渡す行番号。ヘッダ行が1、最初のデータ行が2） |

- `static OrderCsvRecord of(FieldSet fieldSet, int lineNumber)` を持ち、`LineNumberedFieldSetMapper` として Reader に渡す
  （`fieldSet.readRawString(name)` で値をそのまま取り出す。トリムしない）

**出力レコード `OrderDetailWork`**（record。ワークテーブル1行）: `orderNo: String`・`lineNo: int`・`orderDate: LocalDate`・
`storeCode: String`・`itemCode: String`・`quantity: int`・`unitPrice: int`・`amount: long`・`lineSeq: int`

#### 処理フロー

1. **既出キーの対応**: Processor は既出キーの対応（`Map<String, Integer>`。キーは `order_no + "\t" + line_no`、
   値は `line_seq`）をフィールドに持ち、空の状態で生成される（`@StepScope` のため Step 実行ごとに新しいインスタンス）。
   Step 4 は常に Step 3 で空にしたワークへ最初から取り込むため、ワークから復元しない（基本設計書 §2）
2. **読取**: Reader が1行を読み、`OrderCsvRecord` に行番号を付けて返す。列数不一致などのパース不能行は
   `FlatFileParseException` となり、`BusinessSkipPolicy` により Skip される（エラーファイルには元の行文字列）
3. **形式チェック**: `ValidatingItemProcessorBase` が Bean Validation（下表の「Bean Validation」）を実行する。
   違反は `BusinessException`（Skip）
4. **業務チェック**（`doProcess`。下表の順に評価し、最初に該当したもので `BusinessException` を送出）:
   1. `orderDate` を `LocalDate.parse`（ISO 形式）で日付に変換する。存在しない日付（例 2026-02-30）は業務エラー
   2. 変換した日付が `runParameters.targetDate()` と一致すること
   3. 重複検出: キーが既出の対応にあり、**その行番号が今回の `lineSeq` と異なる**場合は重複として業務エラー。
      同じ行番号なら同一行の再処理（チャンクの再処理）とみなしエラーにしない
5. **キーの登録**: 1〜4 をすべて通った行だけ、キー → `lineSeq` を対応に登録する（既に同じ値で登録済みなら何もしない）
6. **変換**: `OrderDetailWork` を生成する。`lineNo`・`quantity`・`unitPrice` は `Integer.parseInt`、
   `amount = (long) quantity * unitPrice`
7. **書込**: Writer がチャンク単位で `app.order_detail_work` へ INSERT する（`created_at` は SQL の `now()`）

#### 入力チェック仕様

| 項目 | チェック内容 | 実装 | エラー時 |
|---|---|---|---|
| 列数 | 7列であること | Reader（strict な区切りトークナイザ。`IncorrectTokenCountException`） | Skip（`FlatFileParseException`） |
| orderNo | 必須・英数字・20文字以内（前後空白不可） | `@NotBlank` `@Size(max = 20)` `@Pattern(regexp = "[A-Za-z0-9]+")` | Skip |
| lineNo | 必須・1〜999999999 の整数（先頭ゼロ不可） | `@NotBlank` `@Pattern(regexp = "[1-9][0-9]{0,8}")` | Skip |
| orderDate | 必須・`yyyy-MM-dd` 形式 | `@NotBlank` `@Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")` | Skip |
| orderDate | 実在する日付 | 業務チェック（`LocalDate.parse`。`DateTimeParseException` を `BusinessException` に包む） | Skip |
| orderDate | 対象日と一致 | 業務チェック | Skip |
| storeCode | 必須・英数字・10文字以内 | `@NotBlank` `@Size(max = 10)` `@Pattern(regexp = "[A-Za-z0-9]+")` | Skip |
| itemCode | 必須・英数字・20文字以内 | `@NotBlank` `@Size(max = 20)` `@Pattern(regexp = "[A-Za-z0-9]+")` | Skip |
| quantity | 必須・1〜999999 の整数（先頭ゼロ不可） | `@NotBlank` `@Pattern(regexp = "[1-9][0-9]{0,5}")` | Skip |
| unitPrice | 必須・0〜999999999 の整数（先頭ゼロ不可） | `@NotBlank` `@Pattern(regexp = "0\|[1-9][0-9]{0,8}")` | Skip |
| orderNo＋lineNo | 同一ファイル内で一意（他のチェックを通った行の中で） | 業務チェック（既出キーの対応） | 2件目以降を Skip |

- 数量・単価の上限（基本設計書 §3.1 で詳細設計に委ねた事項）: 数量 999,999・単価 999,999,999 とする。
  明細金額の最大は約 1.0×10^15 で `bigint` に収まり、`integer` 列（数量・単価）にも収まる
- 前後空白は `@Pattern` で不正になる（トリムしない。基本設計書 §3.1）。Reader のトークナイザが値をトリムしないことを
  単体テストで確認する
- `@Pattern` は `null` を通すため、必須は `@NotBlank` で検出する（空文字の列は `@NotBlank` 違反になる）

**エラーファイルの元レコード部**（`ErrorRecordSkipListener` のフォーマッタ）:
- 読取・加工時（入力レコード）: `line={lineSeq},` に続けて7項目をカンマ区切りで連結（例 `line=15,A001,1,2026-10-11,S01,I01,0,100`）
- 書込時（出力レコード）: `line={lineSeq},{orderNo},{lineNo}`（書込時の Skip は本設計では発生しない想定。Writer の例外は
  `BusinessException` ではないため Skip されず異常終了する）
- パース不能行: 基盤部品の標準どおり元の行文字列（行番号は例外メッセージに含まれる）

### Step 5: orderDailySales.05ReplaceStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `OrderDailySalesReplaceTasklet`（新規。`Tasklet` を直接実装） | なし（Step 3 と同じ理由） |
| トランザクション | Tasklet 全体で1Tx（`StepBuilder#tasklet(tasklet, transactionManager)`。architecture.md §4） | - |

#### 処理フロー（すべて同一 Tx。途中の例外で全体ロールバック）

1. `OrderDetailMapper#deleteByOrderDate(targetDate)`: 対象日の受注明細を削除（件数を控える）
2. `DailyStoreSalesMapper#deleteBySalesDate(targetDate)`: 対象日の日次売上集計を削除（件数を控える）
3. `OrderDetailMapper#insertFromWork()`: ワークの全行を受注明細へ登録（件数を控える）
4. `DailyStoreSalesMapper#insertAggregate(targetDate)`: 対象日の受注明細を店舗ごとに集計して登録（件数を控える）
5. `contribution.incrementWriteCount(3 の件数)` を呼び、INFO ログ
   「日次売上を入れ替えました: 対象日={} 明細削除={} 集計削除={} 明細登録={} 集計登録={}」を出力する

- ワークが0件（ヘッダ行のみのファイル、または全行 Skip）の場合も正常とする。対象日の旧データは削除され、
  受注明細・集計とも0件になる（受注0件の日を正しく表す）。ただし全行 Skip は Step 4 の `skipLimit` 超過で
  通常は先に異常終了する
- 3 はワークの行を対象日で絞らない。ワークにはこの実行回の Step 3〜4 で取り込んだ行（Step 4 で受注日＝対象日を
  検証済み）だけがあるため（§5）。条件を足すと、想定外の行を黙って捨てることになる
- 3 の一意制約違反（別の日に同じ受注番号＋明細番号が登録済み）は `DuplicateKeyException` として送出され、
  Tx 全体がロールバックされて異常終了する（握りつぶさない）

## 3. エラー処理設計

| # | エラー事象 | 分類 | 例外 | 動作 | 退避・出力先 |
|---|---|---|---|---|---|
| 1 | `scheduledTime` 未指定・書式不正、未知のパラメータ、`targetDate` の型が LocalDate でない、`inputFile`・`errorFile` がパスに変換できない | システム（起動不備） | `JobParametersInvalidException` | Step 実行前に異常終了（1） | ログ |
| 2 | `targetDate` を `,java.time.LocalDate` 付きで渡したが日付に変換できない | システム（起動不備） | JobParameters 変換時の例外 | ジョブ起動時に異常終了（1） | ログ |
| 3 | 二重起動 | システム | `SystemException`（`DuplicateRunPreventingJobExecutionListener`） | 異常終了（1） | ログ |
| 4 | 入力ファイルなし・読取不可 | 前処理 | `BusinessException`（`FileExistenceCheckTasklet`） | Step 1 失敗→異常終了（1） | ログ |
| 5 | ヘッダ行不一致（空ファイル含む。BOM 付きは BOM を除いて照合する） | 前処理 | `BusinessException`（`CsvHeaderCheckTasklet`） | Step 2 失敗→異常終了（1） | ログ |
| 5a | エラーファイルを削除できない（権限不足等） | システム | `SystemException`（`OrderDetailWorkClearTasklet`） | Step 3 失敗→異常終了（1）→原因除去後リスタート | ログ |
| 6 | パース不能行（列数不一致等） | 業務 | `FlatFileParseException` | Skip（上限 `skipLimit`） | エラーファイル（元の行） |
| 7 | 形式不正（必須・桁・文字種・値域） | 業務 | `BusinessException`（`ValidatingItemProcessorBase`） | Skip | エラーファイル |
| 8 | 実在しない日付・対象日と不一致 | 業務 | `BusinessException`（`OrderItemProcessor`） | Skip | エラーファイル |
| 9 | ファイル内の重複（2件目以降） | 業務 | `BusinessException`（`OrderItemProcessor`） | Skip | エラーファイル |
| 10 | Skip 件数が `skipLimit` を超過 | 業務（閾値超過） | `SkipLimitExceededException` | Step 4 失敗→異常終了（1） | ログ（超過までの Skip 行はエラーファイル） |
| 11 | ワーク・受注明細の一意制約違反 | システム（設計上発生しない想定） | `DuplicateKeyException` | 即時異常終了（1）。Step 5 なら全体ロールバック | ログ |
| 12 | DB 接続断・SQL エラー | システム | `DataAccessException` 系 | 即時異常終了（1）→原因除去後リスタート | ログ |

- 業務 Tasklet（`OrderDetailWorkClearTasklet`・`OrderDailySalesReplaceTasklet`）は Mapper の例外を包み直さずそのまま送出する
  （Skip/Retry を使わない Step のため、包み直しても挙動が変わらない。POINT-001 と同じ）
- 警告終了（2）は `LoggingJobExecutionListener` が実行回の Skip 件数で判定する（基盤部品の標準動作。基本設計書 §2）

## 4. SQL設計（Mapper XML 方針）

Mapper XML は `batch-sample/src/main/resources/mapper/` に置き、SQL はすべて `app.` でスキーマ修飾する。

| Mapper#メソッド | 種別 | SQL方針 |
|---|---|---|
| `OrderDetailWorkMapper#truncate()` | UPDATE（`<update>`） | `TRUNCATE TABLE app.order_detail_work` |
| `OrderDetailWorkMapper#insert(OrderDetailWork)` | INSERT | 9列（`line_seq` 含む）＋ `created_at = now()` の単純 INSERT。`ON CONFLICT` は付けない（重複は設計上起きず、起きたら異常終了させる） |
| `OrderDetailMapper#deleteByOrderDate(LocalDate)` | DELETE | `DELETE FROM app.order_detail WHERE order_date = #{targetDate}` |
| `OrderDetailMapper#insertFromWork()` | INSERT | `INSERT INTO app.order_detail (order_no, line_no, order_date, store_code, item_code, quantity, unit_price, amount, created_at) SELECT 同列 FROM app.order_detail_work`（`line_seq` は引き継がない。`created_at` はワークの値をそのまま） |
| `DailyStoreSalesMapper#deleteBySalesDate(LocalDate)` | DELETE | `DELETE FROM app.daily_store_sales WHERE sales_date = #{targetDate}` |
| `DailyStoreSalesMapper#insertAggregate(LocalDate)` | INSERT | `INSERT INTO app.daily_store_sales (...) SELECT order_date, store_code, COUNT(DISTINCT order_no), COUNT(*), SUM(quantity), SUM(amount), now() FROM app.order_detail WHERE order_date = #{targetDate} GROUP BY order_date, store_code` |

- 1.4 版で `OrderDetailWorkMapper#selectKeys` と結果用 record `OrderKey` を廃止した（既出キーをワークから復元しないため）
- 戻り値: INSERT/DELETE は件数（`int`）を返す。TRUNCATE は `void`

**Flyway `V5__create_order_daily_sales_tables.sql`**（基本設計書 §4 準拠）:
- `app.order_detail_work`: 基本設計書の列＋`line_seq INTEGER NOT NULL`、`PRIMARY KEY (order_no, line_no)`、`created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `app.order_detail`: `PRIMARY KEY (order_no, line_no)`、インデックス `idx_order_detail_order_date_store_code (order_date, store_code)`
- `app.daily_store_sales`: `PRIMARY KEY (sales_date, store_code)`
- FK は付けない（店舗・商品マスタは本案件スコープ外。ワーク→明細の間も参照関係ではない）

## 5. リラン設計

- **途中失敗時**: 同一パラメータでリスタートする。どの Step で失敗しても、リスタートは Step 1 から全 Step を実行する
  （Step 1〜4 は `allowStartIfComplete(true)`、Step 4 の Reader は `saveState=false`。§1 Step 一覧・§2 Step 4）
  - Step 3 でエラーファイルを削除し、ワークを空にしてから、Step 4 で入力ファイルの先頭から取り込み直す。
    既出キーの対応も空から作る。Skip 件数・`skipLimit`・警告判定は1回で完走した場合と同じになる
  - Step 5 で失敗: Tx 全体がロールバック済み。リスタートではワークも作り直してから Step 5 を実行する
  - **リスタートまでの間に別の JobInstance が実行された場合**（別の対象日の定時実行、同じ対象日の訂正ファイルでの
    再実行）: その実行がワークを入れ替えていても、リスタートは自分の入力ファイルからワークを作り直すため、
    別の実行回の行は混ざらない（Issue #74）。同じ対象日の訂正実行の後に古い JobInstance をリスタートすると、
    対象日の明細はリスタートした側の入力ファイルの内容で洗い替えられる（基本設計書 §5 の運用で、この操作はしない）
  - 本テーブルはリスタートまで変わらない（Step 1〜4 で失敗した場合は Step 5 に達していないため、Step 5 で
    失敗した場合はロールバック済みのため）
- **冪等性**: Step 5 が対象日の受注明細・集計を削除してから入れ直す（洗い替え）。同じ入力で何度実行しても結果は同じ
  （`created_at` の監査列だけは実行ごとに変わる）
- **成功後の再処理（訂正ファイル）**: 新しい `scheduledTime`（実行時の時刻）と同じ `targetDate` を指定して実行する。
  エラーファイルの既定名は `scheduledTime` を含むため、前回のエラーファイルとは別のファイルになる

## 6. batch-core の拡張（本案件で追加する基盤部品）

基本設計書 §2 のとおり、業務に依存しない処理は batch-core に追加する。既存部品と同じく Javadoc に
用途・使い方・拡張ポイントを書き、batch-core の単体テストを追加する（カバレッジ 80% の対象）。

### 6.1 `com.example.batch.core.tasklet.CsvHeaderCheckTasklet`（新規）

| 項目 | 内容 |
|---|---|
| コンストラクタ | `CsvHeaderCheckTasklet(Path file, Charset charset, String... expectedColumns)` |
| 処理 | ファイルの1行目を読み、カンマで分割した値の並びが `expectedColumns` と完全一致するか検証する。値はトリムしない |
| 失敗時 | 不一致・空ファイル・読取不可は `BusinessException`（メッセージに期待値と実際の1行目を含める） |
| ログ | 一致時に INFO「ヘッダ行を確認しました: file={}」 |
| 戻り値 | `RepeatStatus.FINISHED` |
| クラス修飾子 | `public class`（final にしない。`@StepScope` で使うため。§1 Bean 一覧の注記） |
| 制約 | ヘッダ行がクォートされている形式には対応しない（拡張ポイントとして Javadoc に記載） |

### 6.2 `CsvItemReaderFactory#createWithLineNumber`（追加）と `LineNumberedFieldSetMapper`（新規）

| 項目 | 内容 |
|---|---|
| 関数型インタフェース | `com.example.batch.core.item.LineNumberedFieldSetMapper<T>`: `T mapFieldSet(FieldSet fieldSet, int lineNumber) throws BindException` |
| 追加メソッド | `static <T> FlatFileItemReader<T> createWithLineNumber(String name, Resource resource, Charset charset, int linesToSkip, String[] columnNames, LineNumberedFieldSetMapper<T> mapper)` |
| 実装方針 | 既存 `create` と同じ設定（`name`・`saveState(true)`・`strict(true)`・区切り文字カンマ・`linesToSkip`）で `FlatFileItemReaderBuilder` を使い、`lineMapper` に「`DelimitedLineTokenizer`（`names` に `columnNames`、`strict=true`）でトークン化し、`mapper.mapFieldSet(fieldSet, lineNumber)` を呼ぶ `LineMapper`」を設定する |
| 行番号 | `FlatFileItemReader` が `LineMapper#mapLine(line, lineNumber)` に渡す値をそのまま使う（ヘッダ行を含むファイル先頭からの行番号。改行を含むクォート値がある場合はそのレコードの最終行の番号） |
| 例外 | トークン化・マッピングの例外は `FlatFileItemReader` が `FlatFileParseException` に包む（既存 `create` と同じく `BusinessSkipPolicy` で Skip 対象） |

## 7. テスト観点メモ（テスト工程への引き継ぎ）

**単体テスト**
- `OrderDailySalesRunParameters.resolve`: `2026-10-11T17:00:00Z` → 対象日 2026-10-11（UTC の日付をそのまま使わないこと）、
  JST 00:00〜08:59 の境界（UTC で前日になる時刻）、`+09:00` 指定、targetDate・inputFile・errorFile 指定時は指定値優先、既定パスの組み立て
- `OrderDailySalesJobParametersValidator`: scheduledTime 欠落・オフセットなし（`2026-10-12T02:00:00`）・不正文字列、targetDate が String 型、inputFile・errorFile がパスに変換できない（ヌル文字を含む）、未知のキー
- `OrderItemProcessor`: 入力チェック仕様の各行の境界値（lineNo 0/1/999999999/1000000000、quantity 0/1/999999/1000000、unitPrice 0/999999999、
  桁超過、先頭ゼロ、前後空白、2026-02-30）、対象日不一致、重複（行番号が違う→エラー／同じ行番号の再処理→エラーにしない）、
  1件目が不正で2件目が正常→2件目は通る、amount の計算（最大値で桁あふれしない）
- `OrderDetailWorkClearTasklet`: エラーファイルがある→削除して TRUNCATE、無い→TRUNCATE のみ（例外にならない）、
  削除できない（例: パスがディレクトリで中身がある）→ `SystemException` で TRUNCATE を呼ばない
- `CsvHeaderCheckTasklet`: 一致、項目名違い、順序違い、列数違い、空ファイル、BOM 付き（BOM を除いて一致・不一致）、前後空白
- `CsvItemReaderFactory#createWithLineNumber`: 行番号（ヘッダ1行スキップで最初のデータ行が2）、値をトリムしない、列数不一致で `FlatFileParseException`、
  リスタート（`ExecutionContext` から読取位置を復元して続きを読み、行番号も続きから振られる）
- 業務 Tasklet 2つ: Mapper の呼び出し順と件数のログ・`incrementWriteCount`

**結合テスト（Testcontainers）**
- 正常: 複数店舗・複数受注のファイル → 明細・集計の値（受注件数は受注番号の重複を除く）、終了コード0、エラーファイルが作られない
- 警告: 不正行を含むファイル → 正常行だけ取込・集計、エラーファイルの内容（`line=` 付き）、終了コード2
- 洗い替え: 同じ targetDate で別の scheduledTime により2回実行 → 1回目の明細・集計が2回目の内容に置き換わる。他の日のデータは変わらない
- 異常: ファイルなし／ヘッダ不正 → 終了コード1、本テーブル不変。skipLimit 超過 → 終了コード1、本テーブル不変（旧データが残る）
- Step 5 の一意制約違反（別の日に同じキーを事前登録）→ 終了コード1、本テーブル不変
- リスタート: Step 4 の途中で失敗させ（例: 後半の行で DB 例外を起こす）、同一パラメータで再実行 → 最初から取り込み直し、
  最終結果（明細・集計・エラーファイルの内容・終了コード）が1回で成功した場合と同じ（エラーファイルに同じ行が重複しない）
- 別インスタンスをまたぐリスタート（Issue #74 の再現）:
  - 実行 A（targetDate=10/11）を Step 4 で失敗させ、実行 B（targetDate=10/12）を完了させてから A をリスタート →
    A が完了し、10/11 の明細・集計が A の入力どおり、10/12 の明細・集計は B のまま変わらない
  - 実行 A を Step 5 で失敗させ、実行 B（targetDate=10/12）を完了させてから A をリスタート →
    10/11 の明細が A の入力どおりに登録される（0件にならない）
- 二重起動防止・パラメータ不正（scheduledTime なし）→ 終了コード1
- パラメータ不正（inputFile がパスに変換できない）→ 起動時に `InvalidJobParametersException`（入れ子にならない）、本テーブル不変
- 実行時パラメータの Bean が CGLIB のクラスプロキシであること
- 実行時パラメータ確定のログが1回だけ出ること。ロガーに付けた ListAppender で数える（標準出力の取り込みに依存しない）

**システムテスト**
- 10万件のファイルで所要時間（基本設計書 §2 の見込み: 全体で十数分以内）と Step 5 の所要時間（数十秒以内）
- EventBridge 想定の起動（`scheduledTime` に UTC を渡し、targetDate 等を省略）で前日分が既定パスから取り込まれること
- 1.4 版の改訂に伴い、`system-test/scenarios.md` を次のとおり改訂する
  - シナリオ3（Step 4 の途中で失敗→リスタート）の期待結果を「Step 1〜4 も再実行され、Step 4 はファイルの先頭から取り込み直す。
    エラーファイルに同じ行が重複しない。最終結果が1回で成功した場合と同じ」に直す（「Step 1〜3 は再実行しない」「続きから」
    「既出キー復元」の記述を削除する）
  - 別の実行回をまたぐリスタートのシナリオを追加する（実行 A を Step 4・Step 5 で失敗させ、別の対象日の実行 B を完了させてから
    A をリスタート→A・B の対象日の明細・集計がそれぞれの入力どおり）

## 8. 変更履歴

| 日付 | 版 | 変更内容 |
|---|---|---|
| 2026-10-11 | 1.0 | 初版 |
| 2026-10-11 | 1.1 | 設計レビュー指摘の反映: `OrderDailySalesRunParameters` を `@JobScope` Bean にせず各 `@StepScope` Bean で resolve を呼ぶ方式に変更（record はスコーププロキシ不可）／`@StepScope` のクラスを final にしない制約を明記／`beforeStep` を冪等にする（対応を空にしてから読み込む）と明記 |
| 2026-10-12 | 1.2 | 結合テスト工程の指摘反映: §1 の `@StepScope` 注記を「継承元の public メソッドも final にしない」に直す（`ValidatingItemProcessorBase#process` から final を外した） |
| 2026-10-12 | 1.3 | 基盤部品の挙動の訂正（Issue #42）: §1.1 の Validator の説明を「未知のキーは基盤部品ではなくこのクラスで拒否する」に直す（実装は変えない） |
| 2026-10-18 | 1.4 | 基本設計書 1.4 版への追従（Issue #74）: リスタートは Step 1〜4 を最初からやり直す（Step 1〜4 に `allowStartIfComplete(true)`、Reader は `saveState=false`）／Step 3 でエラーファイルも削除し、Tasklet を `@StepScope` に変更／`OrderItemProcessor` の `beforeStep`（ワークからの既出キーの復元）と `StepExecutionListener` の登録を廃止し、`selectKeys`・`OrderKey` を削除／リラン設計とテスト観点を更新（設計レビューの指摘で、システムテストのシナリオの改訂の引き継ぎを追加） |
| 2026-10-21 | 1.5 | Issue #77: `CsvHeaderCheckTasklet` は1行目の先頭の BOM を取り除いてからヘッダを照合する（BOM 付きを不一致とする記述を削除） |
| 2026-10-21 | 1.6 | Issue #75: 実行時パラメータを `@JobScope` の `OrderDailySalesRunParametersHolder` で1回だけ解決し、各 `@StepScope` Bean に注入する（1.1 版の方式を見直し） |
| 2026-10-21 | 1.7 | Issue #92: inputFile・errorFile がパスに変換できることを起動前に検証する（遅延解決で例外が入れ子にならないようにする）／Holder に引数なしコンストラクタが要らない理由（Objenesis）を明記／結合テストのログ回数の検証方法を明記 |
| 2026-10-22 | 1.8 | Issue #76: `ValidatingItemProcessorBase#process` を final に戻した。§1 Bean 一覧の `orderDailySalesProcessor` の型を `ItemProcessor<OrderCsvRecord, OrderDetailWork>` にし、`@StepScope` の注記を直す |
