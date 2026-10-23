# サンプル03: DB → CSV 抽出（Chunk）

売上明細を全件抽出し、ヘッダ行＋トレーラ行（件数・金額合計）付きCSVを生成する。
任意パラメータ `format=fixed` を付けると、Windows-31J（MS932）の固定長ファイル（明細行のみ）を出力する。
ジョブは「一時出力→配置」の2 Step 構成で、途中で失敗しても配置先に不完全なファイルを出さない。

## デモする機能

- `MyBatisCursorItemReader` による**カーソルフェッチ**（全件メモリロードなし）
- ヘッダ/トレーラ付きファイル生成（`CsvItemWriterFactory` + `SummaryFooterCallback`）
- 出力ファイル上書きによる冪等な再実行
- `FileMoveTasklet` による**一時出力→配置**（一時ファイルへ出力してから配置先へ移す。一時ファイルのパスは `TempOutputFile`）
- `FixedLengthItemWriterFactory` による**固定長出力**（`format=fixed`）

## 出力例

```
id,customer_code,amount,sold_at
1,C001,1000,2026-07-01
2,C002,2500,2026-07-02
T,2,3500
```

## 一時出力→配置

ジョブは2 Step で構成する。

| Step | 役割 |
|---|---|
| `sample03.01ExportStep` | DBから抽出し、一時ファイルへ出力する（Chunk） |
| `sample03.02FileMoveStep` | `FileMoveTasklet` で一時ファイルを配置先（`outputFile`）へ移す（Tasklet） |

- 目的は「途中で失敗しても配置先に不完全なファイルを出さない」こと。失敗時の一時ファイルは一時ディレクトリに残る。前回の失敗で残った同名の一時ファイルは消さない（`TempOutputFile` は消さず、Writer が最初から書き直す。Writer は `shouldDeleteIfExists(true)`、sample03 は `saveState=false` なので前回の中身は残らない）
- 一時ファイルの場所は `<outputFile の親ディレクトリ>/tmp/<outputFile のファイル名>`。`outputFile` が相対パスなら絶対パスにしてから親ディレクトリを取る。一時ディレクトリが無ければ作る。パスの決定と一時ディレクトリの作成は基盤部品の `TempOutputFile`（`inSubdirectory`）で行う
- `outputFile` のファイル名が `tmp` の時は、一時ディレクトリと配置先が同じパスになり移動できないため、起動時のパラメータ検証で拒否する（`InvalidJobParametersException`）
- 一時ディレクトリは配置先と同じファイルシステムなので、移動は名前の付け替えで済む
- 移動は上書きあり（`FileMoveTasklet` の `overwrite=true`）。出力ファイルを上書き生成する冪等性（docs/architecture.md §6.2）を保つため
- 配置先のディレクトリを再帰的に監視する仕組みがある場合は、`tmp/` の書きかけのファイルを拾わないよう、任意パラメータ `tempDir` を指定する。`TempOutputFile.inDirectory` で一時ディレクトリを配置先の外に置く（未指定・空文字・空白だけなら上の `tmp/`）
- `tempDir` の空白だけの値は未指定として扱う（上の `tmp/` を使う。sample05 の `inputFile` と同じ扱い）
- NUL 文字を含むなど、パスとして解釈できない値（`outputFile`・`tempDir`）は、起動時のパラメータ検証で拒否する（`InvalidJobParametersException`）
- `tempDir` は識別パラメータのままにする。配置 Step だけが失敗した後のリスタートは、前回の一時ディレクトリにある一時ファイルを配置先へ移す。識別しないパラメータにして `tempDir` を変えたままリスタートできると、配置 Step が新しい一時ディレクトリを見て一時ファイルが見つからず失敗する。識別パラメータなら `tempDir` を変えた起動は新しい JobInstance になり、出力 Step から出力し直す（出力ファイルは上書き生成なので冪等。docs/architecture.md §6.2）。リスタートは `tempDir` を含めて同じパラメータで起動する（docs/architecture.md §6.1）。識別しないパラメータとして渡す指定（`tempDir=<パス>,java.lang.String,false`）はしない
- 一時ディレクトリは配置先と同じファイルシステムに置く前提。別のファイルシステムだと移動がアトミックにならず、コピーと削除になって配置先に書きかけのファイルが見える。同じファイルシステムかどうかはコードで検査しない（一時ディレクトリが起動時にまだ無いことがあるため）
- 一時ディレクトリが配置先と重なる指定（配置先の親ディレクトリと同じ、など）は、起動時のパラメータ検証で拒否する（`InvalidJobParametersException`）。重なりの検査は正規化したパスで行い、シンボリックリンクは解決しないので、実体が同じでも検出できない場合がある
- 別のジョブや別の実行が同じ一時ディレクトリに同じ出力ファイル名で書くと同じ一時ファイルを使う。同じ出力ファイル名で並行実行しない（同じジョブの並行実行は二重起動防止で止まる。方式設計 §6.3）。出力ファイル名か一時ディレクトリを分ける

## 出力形式（format パラメータ）

任意のジョブパラメータ `format` で出力形式を選ぶ。必須パラメータは `outputFile` のまま増やさない。

| 値 | 内容 |
|---|---|
| `csv`（既定。省略時も csv） | ヘッダ＋トレーラ付きCSV（上の出力例） |
| `fixed` | `FixedLengthItemWriterFactory` による Windows-31J（MS932）の固定長ファイル |

上記以外の値は、起動時のパラメータ検証で拒否する（`InvalidJobParametersException`）。

### 固定長のレイアウト（1行63バイト）

| 項目 | バイト数 | 備考 |
|---|---|---|
| `id` | 20 | DB は `BIGINT`。符号付き19桁＝20バイトまで収まる幅にする |
| `customerCode` | 20 | DB は `VARCHAR(10)`（文字数）。全角10文字＝20バイトまで収まる幅にする |
| `amount` | 13 | DB は `NUMERIC(12)`（小数なし）。符号付き12桁＝13バイトまで収まる幅にする |
| `soldAt` | 10 | `yyyy-MM-dd` |

- 各項目は左詰めで、右側を半角スペースで埋める（`ByteLengthLineAggregator` の既定）。合計1行63バイト
- 幅はバイト数。全角1文字は2バイトとして数える（例：`顧客A` は5バイト＋空白15バイト）
- 各項目の幅は、DB の型が取りうる値がすべて収まるようにしている（1件の値で出力全体が失敗しないため）。値が幅を超えると `BusinessException` で失敗する。例外のメッセージには値を含めず、フィールドの添字・幅・値のバイト数を示す
- 文字コードは Windows-31J。Java の `Shift_JIS` は `髙`・`①`・`～` などの Windows 拡張文字を表せないため使わない。出力文字コードで表せない文字を含む値は `BusinessException` で失敗する（`ByteLengthLineAggregator`）。例外のメッセージには値を含めず、フィールドの添字・文字位置・コードポイント（例 U+20BB7）を示す
- ヘッダ行・トレーラ行は出さない（明細行のみ）。CSV はこれまでどおりヘッダ＋トレーラ付き

## 実行方法

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample03FileExportJob \
  outputFile=batch-sample/data/out/sales.csv"
```

一時ディレクトリを配置先の外に置く例：

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample03FileExportJob \
  outputFile=batch-sample/data/out/sales.csv tempDir=batch-sample/data/work"
```

固定長で出力する例：

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample03FileExportJob \
  outputFile=batch-sample/data/out/sales.dat format=fixed"
```

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| 件数上限のないテーブル抽出（カーソルでメモリ一定） | 抽出条件・整形が複雑でSQLで完結する → ビューや `INSERT SELECT`＋抽出の2段構成を検討 |
| 件数・合計値のトレーラが必要な対外ファイル連携 | リアルタイム連携が必要 → バッチではなく API/イベント連携を検討 |

## 実装ポイント・注意

- `SummaryFooterCallback` は **Writer の footerCallback と Step の listener の両方**に登録する
  （listener 登録を忘れると件数が集計されずトレーラが `T,0,0` になる）
- トレーラの集計値はメモリ保持のため、出力 Step は途中の状態を保存しない（Reader・Writer とも `saveState=false`）。
  出力 Step が途中で失敗した後にリスタート（同一パラメータで再起動）しても、一時ファイルを最初から出力し直す。
  件数の途中から追記して、トレーラと明細が食い違ったファイルを配置することはない（docs/architecture.md §6.2）
- 配置 Step だけが失敗した場合は、リスタートで出力 Step（COMPLETED）を飛ばし、配置だけをやり直す
- 抽出順序は SQL の `ORDER BY` で保証する（ファイル比較テストを安定させる）
