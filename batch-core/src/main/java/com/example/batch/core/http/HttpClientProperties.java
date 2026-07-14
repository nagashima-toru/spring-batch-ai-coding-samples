package com.example.batch.core.http;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * HTTP Client 基盤の設定値。
 *
 * <p><b>用途:</b> {@link BatchHttpClient} のタイムアウト・リトライ動作を
 * {@code application.yml} から外部設定する。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * batch:
 *   core:
 *     http:
 *       connect-timeout: 5s
 *       read-timeout: 30s
 *       retry:
 *         max-attempts: 3
 *         initial-delay: 500ms
 *         multiplier: 2.0
 *         max-delay: 10s
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> 接続先ごとに設定を分けたい場合は、この record を複数バインドして
 * {@link BatchHttpClient} を接続先ごとに Bean 定義する。</p>
 *
 * @param connectTimeout 接続タイムアウト
 * @param readTimeout    読取タイムアウト
 * @param retry          リトライ設定
 */
@ConfigurationProperties("batch.core.http")
public record HttpClientProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue Retry retry) {

    /**
     * 指数バックオフリトライの設定。
     *
     * @param maxAttempts  最大試行回数（初回を含む。3 なら最大2回リトライ）
     * @param initialDelay 初回リトライまでの待機時間
     * @param multiplier   バックオフ倍率
     * @param maxDelay     待機時間の上限
     */
    public record Retry(
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("500ms") Duration initialDelay,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("10s") Duration maxDelay) {
    }
}
