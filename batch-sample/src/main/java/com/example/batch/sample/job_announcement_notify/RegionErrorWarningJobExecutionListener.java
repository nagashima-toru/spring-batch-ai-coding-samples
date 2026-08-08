package com.example.batch.sample.job_announcement_notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

import com.example.batch.core.execution.BatchExitCodes;

/**
 * ANNOUNCE-001 専用: 区域展開不能（Step2）による警告終了判定リスナー。
 *
 * <p><b>用途:</b> 共通の {@code loggingJobExecutionListener}（Skip 発生時の警告判定）に加えて、
 * 「通知区域が郵便番号マスタに1件も展開できないお知らせが発生した」場合も警告終了（終了コード2）
 * とする（詳細設計書 §3）。共通 Bean {@code LoggingJobExecutionListener} を継承する実装は、
 * Uber-jar 構成で全ジョブ Config が単一 Spring コンテキストに同居するため
 * {@code @ConditionalOnMissingBean} の型衝突を起こし他ジョブの Bean 解決を壊すおそれがある
 * （設計レビューで検出）。そのため本クラスは {@link JobExecutionListener} を直接実装し、
 * 共通リスナーと**併用**する。</p>
 *
 * <p><b>使い方:</b> {@code loggingJobExecutionListener} とあわせて Job の {@code .listener()} に
 * 追加する。区域展開不能件数は {@link AnnouncementRegionCheckTasklet} が Job レベルの
 * {@code ExecutionContext} へ記録する（Step レベルではリスタート時に値が失われるため。
 * 詳細設計書 §5）。</p>
 */
public class RegionErrorWarningJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(RegionErrorWarningJobExecutionListener.class);

    @Override
    public void afterJob(JobExecution jobExecution) {
        int regionErrorCount = jobExecution.getExecutionContext()
                .getInt(AnnouncementRegionCheckTasklet.JOB_CONTEXT_KEY_REGION_ERROR_COUNT, 0);
        if (regionErrorCount <= 0) {
            return;
        }
        if (jobExecution.getStatus() == BatchStatus.COMPLETED
                && ExitStatus.COMPLETED.getExitCode().equals(jobExecution.getExitStatus().getExitCode())) {
            jobExecution.setExitStatus(BatchExitCodes.COMPLETED_WITH_WARNING);
            log.warn("区域展開不能のお知らせが発生したため警告終了とします: regionErrorCount={}", regionErrorCount);
        }
    }
}
