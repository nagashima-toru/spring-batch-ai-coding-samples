package com.example.batch.sample.job09_notification;

import java.util.Map;

import com.example.batch.core.http.BatchHttpClient;

/**
 * サンプル09【骨格】: Slack Incoming Webhook への失敗通知。
 *
 * <p>HTTP Client 基盤（リトライ・ログ内包）を利用して Webhook へ POST する。
 * Webhook URL は機密情報のため環境変数（Secrets Manager）から注入する。</p>
 */
public class SlackWebhookNotificationSender implements NotificationSender {

    private final BatchHttpClient httpClient;
    private final String webhookUrl;

    public SlackWebhookNotificationSender(BatchHttpClient httpClient, String webhookUrl) {
        this.httpClient = httpClient;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void sendJobFailure(String jobName, long executionId, String message) {
        String text = ":rotating_light: バッチ異常終了: %s (executionId=%d)%n%s".formatted(jobName, executionId, message);
        httpClient.post(webhookUrl, Map.of("text", text), String.class, Map.of());
    }
}
