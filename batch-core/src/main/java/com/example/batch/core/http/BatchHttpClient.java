package com.example.batch.core.http;

import java.time.Duration;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.batch.core.exception.RetryableSystemException;
import com.example.batch.core.exception.SystemException;

/**
 * バッチ用 HTTP Client 基盤（{@link RestClient} ラッパー）。
 *
 * <p><b>用途:</b> バッチからの外部 API 呼出を標準化する。以下を内包しており、
 * 業務コードで {@link RestClient} を直接使用してはならない（docs/coding-standards.md §4.2）。</p>
 * <ul>
 *   <li>接続・読取タイムアウト（{@link HttpClientProperties} で外部設定）</li>
 *   <li>指数バックオフによる自動リトライ（5xx・接続エラーのみ。4xx はリトライしない）</li>
 *   <li>リクエスト/レスポンスの共通ログ（{@link HttpLoggingInterceptor}）</li>
 *   <li>例外変換: 4xx → {@link SystemException}（呼出側の不備）、
 *       5xx・接続エラー → {@link RetryableSystemException}（リトライ枯渇時にそのまま送出）</li>
 * </ul>
 *
 * <p><b>使い方:</b> auto-configuration により Bean 登録されるため、コンストラクタインジェクションで受け取る。</p>
 * <pre>{@code
 * List<User> users = httpClient.get("https://api.example.com/users?page={p}",
 *         new ParameterizedTypeReference<List<User>>() {},
 *         Map.of("Authorization", "Bearer " + token), 1);
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> 接続先ごとに認証・ベースURLを固定したクライアントが必要な場合は、
 * {@code RestClient.Builder} に {@code baseUrl}・{@code defaultHeader} を設定して
 * 本クラスの Bean を接続先ごとに定義する。</p>
 */
public class BatchHttpClient {

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    /**
     * @param builder    ベースとなる RestClient.Builder（baseUrl・共通ヘッダを設定済みでもよい）
     * @param properties タイムアウト・リトライ設定
     */
    public BatchHttpClient(RestClient.Builder builder, HttpClientProperties properties) {
        this(builder, properties, true);
    }

    /**
     * テスト用コンストラクタ。{@code applyTimeouts=false} の場合は builder に設定済みの
     * リクエストファクトリ（MockRestServiceServer 等）を上書きしない。
     */
    BatchHttpClient(RestClient.Builder builder, HttpClientProperties properties, boolean applyTimeouts) {
        if (applyTimeouts) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(properties.connectTimeout());
            requestFactory.setReadTimeout(properties.readTimeout());
            builder = builder.requestFactory(requestFactory);
        }
        this.restClient = builder
                .requestInterceptor(new HttpLoggingInterceptor())
                .build();
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .includes(RetryableSystemException.class)
                .maxRetries(Math.max(0, properties.retry().maxAttempts() - 1L))
                .delay(properties.retry().initialDelay())
                .multiplier(properties.retry().multiplier())
                .maxDelay(properties.retry().maxDelay())
                .build());
    }

    /**
     * GET リクエストを送信しレスポンスボディを取得する。
     *
     * @param uri          リクエストURI（URIテンプレート可）
     * @param responseType レスポンスボディの型
     * @param headers      追加ヘッダ（不要なら空Map）
     * @param uriVariables URIテンプレート変数
     */
    public <T> T get(String uri, Class<T> responseType, Map<String, String> headers, Object... uriVariables) {
        return executeWithRetry("GET " + uri, () -> restClient.get()
                .uri(uri, uriVariables)
                .headers(h -> headers.forEach(h::add))
                .retrieve()
                .body(responseType));
    }

    /**
     * GET リクエストを送信しレスポンスボディを取得する（ジェネリクス型向け）。
     */
    public <T> T get(String uri, ParameterizedTypeReference<T> responseType, Map<String, String> headers,
            Object... uriVariables) {
        return executeWithRetry("GET " + uri, () -> restClient.get()
                .uri(uri, uriVariables)
                .headers(h -> headers.forEach(h::add))
                .retrieve()
                .body(responseType));
    }

    /**
     * POST リクエストを送信しレスポンスボディを取得する。
     *
     * @param body リクエストボディ（JSON へシリアライズされる）
     */
    public <T> T post(String uri, Object body, Class<T> responseType, Map<String, String> headers,
            Object... uriVariables) {
        return executeWithRetry("POST " + uri, () -> restClient.post()
                .uri(uri, uriVariables)
                .headers(h -> headers.forEach(h::add))
                .body(body)
                .retrieve()
                .body(responseType));
    }

    private <T> T executeWithRetry(String description, Retryable<T> call) {
        try {
            return retryTemplate.execute(new Retryable<>() {
                @Override
                public T execute() {
                    return callAndTranslate(call);
                }

                @Override
                public String getName() {
                    return description;
                }
            });
        } catch (RetryException e) {
            // リトライ枯渇（または非リトライ対象で即中断）。変換済みの原因例外を保ったまま送出する
            if (e.getCause() instanceof SystemException cause) {
                throw cause;
            }
            throw new SystemException("HTTP呼出がリトライ上限まで失敗しました: " + description, e);
        }
    }

    private <T> T callAndTranslate(Retryable<T> call) {
        try {
            return call.execute();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new RetryableSystemException(
                        "外部APIがサーバエラーを返却しました: status=" + e.getStatusCode().value(), e);
            }
            throw new SystemException(
                    "外部APIがクライアントエラーを返却しました（リクエスト内容・認証情報を確認）: status="
                            + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new RetryableSystemException("外部APIへの接続に失敗しました（タイムアウト・接続断）", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new SystemException("HTTP呼出で予期しないエラーが発生しました", e);
        }
    }

    /**
     * リトライ間隔の合計見積り（テスト・運用設計用のユーティリティ）。
     *
     * @param retry リトライ設定
     * @return 全リトライが待機した場合の合計待機時間
     */
    public static Duration estimateTotalBackoff(HttpClientProperties.Retry retry) {
        Duration total = Duration.ZERO;
        Duration delay = retry.initialDelay();
        for (int i = 1; i < retry.maxAttempts(); i++) {
            total = total.plus(delay);
            long next = (long) (delay.toMillis() * retry.multiplier());
            delay = Duration.ofMillis(Math.min(next, retry.maxDelay().toMillis()));
        }
        return total;
    }
}
