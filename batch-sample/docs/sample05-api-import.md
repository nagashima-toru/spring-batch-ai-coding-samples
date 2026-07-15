# サンプル05: REST API → DB 連携（Chunk + HTTP Client 基盤）

外部のユーザーAPI（ページング形式）から全件を取得し `app.api_user` へ取り込む。

## デモする機能

- HTTP Client 基盤 `BatchHttpClient` の利用（RestClient 直接使用は規約違反）
  - タイムアウト・指数バックオフリトライ（5xx/接続断のみ）・共通ログ・例外変換を内包
- `RestApiPagingItemReader` によるページング取得（全件メモリロードなし、リスタート対応）
- 認証ヘッダ（Bearer トークン）の外部設定（環境変数 `SAMPLE05_API_TOKEN` から注入）
- 外部レスポンスの入力チェック（外部システムを信頼しない）

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

※ ローカルで動かす場合は `base-url` にモックAPI（結合テストでは JDK 組込み HttpServer を使用）を立てる。

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| ページングAPIからの定期的な全件・差分同期 | API呼出が1回だけ（トークン取得・通知など）→ `AbstractApiCallTasklet` |
| 取得件数が多くメモリに載せたくない | 相手システムがファイル連携を提供している → ファイル取込（サンプル01/02）の方が安定 |

## 実装ポイント

- リトライは HTTP Client 基盤に一元化し、Batch の Retry と**二重にしない**（architecture.md §5.2）
- 4xx は設定・認証の誤りであり、リトライせず即異常終了する（`SystemException`）
- カーソル（トークン）方式のページングは `RestApiPagingItemReader` の対象外
  （Javadoc の拡張ポイント参照）
