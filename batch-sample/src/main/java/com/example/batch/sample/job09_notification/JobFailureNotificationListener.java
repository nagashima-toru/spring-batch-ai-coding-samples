package com.example.batch.sample.job09_notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

/**
 * サンプル09【骨格】: ジョブ異常終了時の通知リスナー（JobExecutionListener 応用）。
 *
 * <p>afterJob で失敗を検知し {@link NotificationSender} へ通知する。
 * <b>通知の失敗でジョブの終了コードを変えない</b>よう、通知エラーは握りつぶさず
 * ログ出力に留める（通知は運用補助であり、ジョブ本体の成否とは分離する）。</p>
 *
 * <p>使い方: 通知したいジョブの {@code listener(...)} に追加する。全ジョブ共通で
 * 通知する場合の構成は docs/sample09-notification.md を参照。</p>
 */
public class JobFailureNotificationListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobFailureNotificationListener.class);

    private final NotificationSender sender;

    public JobFailureNotificationListener(NotificationSender sender) {
        this.sender = sender;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            return;
        }
        String jobName = jobExecution.getJobInstance().getJobName();
        String message = jobExecution.getAllFailureExceptions().stream()
                .map(Throwable::getMessage)
                .findFirst()
                .orElse("(失敗原因不明)");
        try {
            sender.sendJobFailure(jobName, jobExecution.getId(), message);
        } catch (RuntimeException e) {
            // 通知失敗でジョブの終了コードを変えない（原因はログに残し、監視はCloudWatch側でも検知する）
            log.error("異常終了通知の送信に失敗しました: jobName={}", jobName, e);
        }
    }
}
