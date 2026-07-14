package com.example.batch.core.http;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * HTTP リクエスト/レスポンスの共通ログ出力インターセプタ。
 *
 * <p><b>用途:</b> {@link BatchHttpClient} 経由の全 HTTP 呼出について、
 * メソッド・URI・ステータス・所要時間を統一フォーマットでログ出力する。
 * 障害調査時に「どの外部呼出が・いつ・どれだけかかったか」を追跡可能にする。</p>
 *
 * <p><b>使い方:</b> {@link BatchHttpClient} が自動で組み込むため、通常は直接使用しない。</p>
 *
 * <p><b>拡張ポイント:</b> リクエスト/レスポンスボディのログが必要な場合は本クラスを継承して
 * {@link #intercept} を拡張する（機密情報・個人情報を出力しないこと）。</p>
 */
public class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Instant start = Instant.now();
        log.info("HTTP呼出開始: {} {}", request.getMethod(), request.getURI());
        try {
            ClientHttpResponse response = execution.execute(request, body);
            log.info("HTTP呼出終了: {} {} status={} elapsedMs={}", request.getMethod(), request.getURI(),
                    response.getStatusCode().value(), Duration.between(start, Instant.now()).toMillis());
            return response;
        } catch (IOException e) {
            log.warn("HTTP呼出失敗: {} {} elapsedMs={} cause={}", request.getMethod(), request.getURI(),
                    Duration.between(start, Instant.now()).toMillis(), e.toString());
            throw e;
        }
    }
}
