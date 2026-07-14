package com.example.batch.core.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;

import com.example.batch.core.execution.BatchExitCodes;

/**
 * ジョブ開始・終了の標準ログ出力＋警告終了判定リスナー。
 *
 * <p><b>用途:</b> 全ジョブ共通の運用ログ（方式設計 §8）を出力する。個別ジョブで
 * 開始終了ログを実装してはならない（コーディング規約 §4.2）。</p>
 * <ul>
 *   <li>開始時: ジョブ名・パラメータを INFO 出力し、MDC に {@code jobName}・
 *       {@code jobExecutionId} を設定（以降の全ログに付与される）</li>
 *   <li>終了時: ステータス・所要時間・読取/書込/Skip 件数の合計を INFO 出力</li>
 *   <li>Skip が1件以上あり完走した場合、ExitStatus を
 *       {@code COMPLETED_WITH_WARNING} へ変更（警告終了＝終了コード2。方式設計 §5.3）</li>
 *   <li>失敗時は失敗例外を ERROR 出力</li>
 * </ul>
 *
 * <p><b>使い方:</b> auto-configuration で Bean 登録される。全ジョブの listener に追加する。</p>
 * <pre>{@code
 * new JobBuilder("importJob", jobRepository)
 *         .listener(loggingJobExecutionListener)
 *         .start(step).build();
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> 警告終了の判定条件を変えたい場合は
 * {@link #isWarning(JobExecution)} をオーバーライドした Bean を業務側で定義する。</p>
 */
public class LoggingJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingJobExecutionListener.class);

    private static final String MDC_JOB_NAME = "jobName";
    private static final String MDC_JOB_EXECUTION_ID = "jobExecutionId";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        MDC.put(MDC_JOB_NAME, jobExecution.getJobInstance().getJobName());
        MDC.put(MDC_JOB_EXECUTION_ID, String.valueOf(jobExecution.getId()));
        log.info("ジョブ開始: jobName={} executionId={} parameters={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(), jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            long readCount = 0;
            long writeCount = 0;
            long skipCount = 0;
            for (StepExecution step : jobExecution.getStepExecutions()) {
                readCount += step.getReadCount();
                writeCount += step.getWriteCount();
                skipCount += step.getSkipCount();
            }

            if (jobExecution.getStatus() == BatchStatus.COMPLETED && isWarning(jobExecution)
                    && ExitStatus.COMPLETED.getExitCode().equals(jobExecution.getExitStatus().getExitCode())) {
                jobExecution.setExitStatus(BatchExitCodes.COMPLETED_WITH_WARNING);
                log.warn("Skipが発生したため警告終了とします: skipCount={}", skipCount);
            }

            log.info("ジョブ終了: jobName={} executionId={} status={} exitCode={} "
                            + "readCount={} writeCount={} skipCount={} elapsedMs={}",
                    jobExecution.getJobInstance().getJobName(), jobExecution.getId(),
                    jobExecution.getStatus(), jobExecution.getExitStatus().getExitCode(),
                    readCount, writeCount, skipCount, elapsedMillis(jobExecution));

            if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
                jobExecution.getAllFailureExceptions()
                        .forEach(t -> log.error("ジョブ失敗原因: {}", t.getMessage(), t));
            }
        } finally {
            MDC.remove(MDC_JOB_NAME);
            MDC.remove(MDC_JOB_EXECUTION_ID);
        }
    }

    /**
     * 警告終了とすべきかを判定する。既定では Skip 件数が1件以上の場合に警告とする。
     *
     * @param jobExecution 完走したジョブの実行情報
     * @return 警告終了とする場合 true
     */
    protected boolean isWarning(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount)
                .sum() > 0;
    }

    private long elapsedMillis(JobExecution jobExecution) {
        LocalDateTime start = jobExecution.getStartTime();
        if (start == null) {
            return 0;
        }
        LocalDateTime end = jobExecution.getEndTime() != null ? jobExecution.getEndTime() : LocalDateTime.now();
        return Duration.between(start, end).toMillis();
    }
}
