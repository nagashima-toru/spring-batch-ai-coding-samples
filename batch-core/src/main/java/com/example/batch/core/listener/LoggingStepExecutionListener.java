package com.example.batch.core.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Step 開始・終了の標準ログ出力リスナー。
 *
 * <p><b>用途:</b> 全 Step 共通の運用ログ（方式設計 §8）を出力する。
 * 読取・フィルタ・書込・Skip・コミット・ロールバックの各件数と所要時間を記録し、
 * 性能分析・障害調査の基礎データとする。</p>
 *
 * <p><b>使い方:</b> auto-configuration で Bean 登録される。各 Step の listener に追加する。</p>
 * <pre>{@code
 * new StepBuilder("importStep", jobRepository).<In, Out>chunk(100)
 *         .reader(reader).writer(writer)
 *         .listener(loggingStepExecutionListener)
 *         .build();
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> スループット（件/秒）等の追加メトリクスが必要な場合は
 * 本クラスを継承して {@link #afterStep} を拡張する。</p>
 */
public class LoggingStepExecutionListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingStepExecutionListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Step開始: stepName={}", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("Step終了: stepName={} status={} readCount={} filterCount={} writeCount={} "
                        + "skipCount={} (read={} process={} write={}) commitCount={} rollbackCount={} elapsedMs={}",
                stepExecution.getStepName(), stepExecution.getStatus(),
                stepExecution.getReadCount(), stepExecution.getFilterCount(), stepExecution.getWriteCount(),
                stepExecution.getSkipCount(), stepExecution.getReadSkipCount(),
                stepExecution.getProcessSkipCount(), stepExecution.getWriteSkipCount(),
                stepExecution.getCommitCount(), stepExecution.getRollbackCount(), elapsedMillis(stepExecution));
        // ExitStatus は変更しない（警告判定はジョブレベルで実施）
        return null;
    }

    private long elapsedMillis(StepExecution stepExecution) {
        LocalDateTime start = stepExecution.getStartTime();
        if (start == null) {
            return 0;
        }
        LocalDateTime end = stepExecution.getEndTime() != null ? stepExecution.getEndTime() : LocalDateTime.now();
        return Duration.between(start, end).toMillis();
    }
}
