package com.example.batch.sample.job09_notification;

/**
 * サンプル09【骨格】: ジョブ異常終了通知の送信先を抽象化するインターフェース。
 */
public interface NotificationSender {

    /**
     * ジョブ失敗を通知する。
     *
     * @param jobName     失敗したジョブ名
     * @param executionId JobExecution ID（リスタート時に使用）
     * @param message     失敗内容
     */
    void sendJobFailure(String jobName, long executionId, String message);
}
