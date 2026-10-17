# サンプル03: DB → CSV 抽出（Chunk）

売上明細を全件抽出し、ヘッダ行＋トレーラ行（件数・金額合計）付きCSVを生成する。
任意パラメータ `format=fixed` を付けると、Windows-31J（MS932）の固定長ファイル（明細行のみ）を出力する。
ジョブは「一時出力→配置」の2 Step 構成で、途中で失敗しても配置先に不完全なファイルを出さない。

## デモする機能

- `MyBatisCursorItemReader` による**カーソルフェッチ**（全件メモリロードなし）
- ヘッダ/トレーラ付きファイル生成（`CsvItemWriterFactory` + `SummaryFooterCallback`）
- 出力ファイル上書きによる冪等な再実行
- `FileMoveTasklet` による**一時出力→配置**（一時ファイルへ出力してから配置先へ移す）
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

- 目的は「途中で失敗しても配置先に不完全なファイルを出さない」こと。失敗時の一時ファイルは一時ディレクトリに残り、次の実行で上書きされる
- 一時ファイルの場所は `<outputFile の親ディレクトリ>/tmp/<outputFile のファイル名>`。`outputFile` が相対パスなら絶対パスにしてから親ディレクトリを取る。一時ディレクトリが無ければ作る
- `outputFile` のファイル名が `tmp` の時は、一時ディレクトリと配置先が同じパスになり移動できないため、起動時のパラメータ検証で拒否する（`InvalidJobParametersException`）
- 一時ディレクトリは配置先と同じファイルシステムなので、移動は名前の付け替えで済む
- 移動は上書きあり（`FileMoveTasklet` の `overwrite=true`）。出力ファイルを上書き生成する冪等性（docs/architecture.md §6.2）を保つため

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
- 各項目の幅は、DB の型が取りうる値がすべて収まるようにしている（1件の値で出力全体が失敗しないため）。値が幅を超えると `BusinessException` で失敗する
- 文字コードは Windows-31J。Java の `Shift_JIS` は `髙`・`①`・`～` などの Windows 拡張文字を表せず、黙って `?` に置き換えるため使わない
- ヘッダ行・トレーラ行は出さない（明細行のみ）。CSV はこれまでどおりヘッダ＋トレーラ付き

## 実行方法

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample03FileExportJob \
  outputFile=batch-sample/data/out/sales.csv"
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
