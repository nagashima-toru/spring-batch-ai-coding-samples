package com.example.batch.sample.job09_notification;

/**
 * サンプル09【骨格】: Amazon SNS への失敗通知（未実装）。
 *
 * <p>実装する場合は {@code software.amazon.awssdk:sns} を依存に追加し、
 * {@code SnsClient#publish} でトピックへ発行する。設計方針は docs/sample09-notification.md を参照。</p>
 */
public class SnsNotificationSender implements NotificationSender {

    @Override
    public void sendJobFailure(String jobName, long executionId, String message) {
        throw new UnsupportedOperationException(
                "SnsNotificationSender は骨格のみ。AWS SDK (sns) を追加して実装すること"
                        + "（docs/sample09-notification.md 参照）");
    }
}
