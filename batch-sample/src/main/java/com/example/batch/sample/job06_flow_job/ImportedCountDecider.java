package com.example.batch.sample.job06_flow_job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;

/**
 * サンプル06: 取込件数による条件分岐 Decider。
 *
 * <p>取込 Step の書込件数が 1 件以上なら {@code PROCEED}（集計 Step へ進む）、
 * 0 件なら {@code SKIP_SUMMARY}（集計をスキップして正常終了）を返す。</p>
 */
public class ImportedCountDecider implements JobExecutionDecider {

    /** 集計Stepへ進む。 */
    public static final String PROCEED = "PROCEED";

    /** 集計Stepをスキップする。 */
    public static final String SKIP_SUMMARY = "SKIP_SUMMARY";

    private static final Logger log = LoggerFactory.getLogger(ImportedCountDecider.class);

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        long writeCount = stepExecution != null ? stepExecution.getWriteCount() : 0;
        String status = writeCount > 0 ? PROCEED : SKIP_SUMMARY;
        log.info("条件分岐: 取込件数={} → {}", writeCount, status);
        return new FlowExecutionStatus(status);
    }
}
