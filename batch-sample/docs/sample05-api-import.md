# サンプル05: REST API → DB 連携（Chunk + HTTP Client 基盤）

外部のユーザーAPI（ページング形式）から全件を取得し `app.api_user` へ取り込む。
任意パラメータ `inputFile` を指定すると、API の代わりに保存済みの JSON ファイルから取り込む（リプレイ）。

## 処理の流れ

ジョブの先頭で Decider（`InputSourceDecider`）が `inputFile` の有無を見て、実行する Step を分ける。

| `inputFile` | 実行する Step |
|---|---|
| 未指定（または空文字） | `sample05.00UserCountStep` → `sample05.01ImportStep` → `sample05.02CountCheckStep` |
| 指定あり | `sample05.01ImportStep` → `sample05.02CountCheckStep`（`sample05.00UserCountStep` は飛ばす。突き合わせは行わない） |

- 既存の `sample05.01ImportStep` は改名できないため、前に入る件数取得 Step を `00` とした
- `sample05.00UserCountStep`：`AbstractApiCallTasklet` を継承した `ApiUserCountTasklet` で、件数 API
  `GET {base-url}/users/count`（Bearer トークン付き、レスポンス例 `{"count":3}`）を1回呼ぶ。
  件数をログに出し、Step の ExecutionContext に `apiUserCount` として保存する（取込前の疎通確認を兼ねる）。
  `ExecutionContextPromotionListener` で Job の ExecutionContext にも載せ、後続の件数突き合わせで使う
- ページングで大量データを取るのは Chunk＋`RestApiPagingItemReader` の役割で、1回だけの呼出は Tasklet にする
  （architecture.md §3.1）
- `sample05.01ImportStep`：API 取込（`RestApiPagingItemReader`）または `inputFile` の JSON 読込
  （`JsonItemReaderFactory`）から取り込む。入力チェックと UPSERT は両方で共通。
  Step の終了時に、取得件数（読込件数＋読込 Skip 件数）を Job の ExecutionContext に `apiUserReadCount` として保存する
- `sample05.02CountCheckStep`：batch-core の `TableCountCheckTasklet` で、取得件数 `apiUserReadCount` が
  件数 API の `apiUserCount` と一致することを確かめる（最小件数＝最大件数＝`apiUserCount`）。
  一致しない時は `BusinessException` で異常終了（終了コード1）する。API がページングの途中でデータを返さなかった等の
  取りこぼしを、正常終了として見逃さないため。入力チェックで Skip した件も「取得した件」として数える
  （Skip は終了コード2で別途知らせる）。取り込んだデータは UPSERT なので、原因を取り除いて再実行すればよい
- `inputFile` 指定時は件数 API を呼ばないため、件数の突き合わせは行わない（取込 Step は両方の分岐で同じ Step なので
  `sample05.02CountCheckStep` は通るが、件数 `apiUserCount` が無い時はログを出すだけで正常に終わる）

## デモする機能

- HTTP Client 基盤 `BatchHttpClient` の利用（RestClient 直接使用は規約違反）
  - タイムアウト・指数バックオフリトライ（5xx/接続断のみ）・共通ログ・例外変換を内包
- `RestApiPagingItemReader` によるページング取得（全件メモリロードなし、リスタート対応）
- 認証ヘッダ（Bearer トークン）の外部設定（環境変数 `SAMPLE05_API_TOKEN` から注入）
- 外部レスポンスの入力チェック（外部システムを信頼しない）
- `AbstractApiCallTasklet`：件数取得 Step（`sample05.00UserCountStep`）で単発の API コールを行う
- `JsonItemReaderFactory`：`inputFile` 指定時に保存済み JSON から取り込む（リプレイ）
- `RetryableSystemException`：リトライを使い切った 5xx・接続エラーで異常終了する
- `TableCountCheckTasklet`：件数 API の件数と取得件数の突き合わせ（`sample05.02CountCheckStep`）

## 設定（application.yml）

```yaml
sample:
  job05:
    api:
      base-url: http://localhost:8081   # 接続先
      page-size: 100
      token: ${SAMPLE05_API_TOKEN:local-dummy-token}
```

## 実行方法

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample05ApiImportJob \
  syncDate=2026-07-14,java.time.LocalDate"
```

`inputFile` を指定して、保存済みの JSON 配列ファイルから取り込む例（API 障害時の再取込や検証用）。

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample05ApiImportJob \
  syncDate=2026-07-14,java.time.LocalDate \
  inputFile=batch-sample/data/in/api-users.json"
```

- 必須パラメータは `syncDate` のまま。`inputFile` は任意
- サンプル入力は `batch-sample/data/in/api-users.json`
- ファイルが無い時は異常終了（終了コード1）する

※ ローカルで動かす場合は `base-url` にモックAPI（結合テストでは JDK 組込み HttpServer を使用）を立てる。

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| ページングAPIからの定期的な全件・差分同期 | API呼出が1回だけ（トークン取得・通知など）→ `AbstractApiCallTasklet`（本サンプルの件数取得 Step が実例） |
| 取得件数が多くメモリに載せたくない | 相手システムがファイル連携を提供している → ファイル取込（サンプル01/02）の方が安定 |

## 実装ポイント

- リトライは HTTP Client 基盤に一元化し、Batch の Retry と**二重にしない**（architecture.md §5.2）
- 4xx は設定・認証の誤りであり、リトライせず即異常終了する（`SystemException`）
- カーソル（トークン）方式のページングは `RestApiPagingItemReader` の対象外
  （Javadoc の拡張ポイント参照）

## リトライ方針

- HTTP 呼出のリトライは `BatchHttpClient` が `batch.core.http.retry.*`
  （既定 `max-attempts: 3`、指数バックオフ）で行う
- architecture.md §5.2 に従い、Batch の `retry()` は設定しない。二重にすると試行回数が掛け算になるため
- 5xx・接続エラーが続くと、`BatchHttpClient` が試行を使い切った後に `RetryableSystemException` で異常終了（終了コード1）する
- 4xx は `SystemException` で即異常終了する（リトライしない）
- この動作は結合テスト `Sample05ApiImportJobIT` で確かめている
