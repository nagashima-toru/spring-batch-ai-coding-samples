package com.example.batch.sample.job06_flow_job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;


/**
 * サンプル06: 後処理のサマリ作成 Tasklet（洗い替え）。
 */
public class WorkSummaryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(WorkSummaryTasklet.class);

    private final WorkMapper workMapper;

    public WorkSummaryTasklet(WorkMapper workMapper) {
        this.workMapper = workMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        workMapper.truncateSummary();
        int inserted = workMapper.insertSummaryFromWork();
        log.info("ワークサマリを作成しました: 行数={}", inserted);
        return RepeatStatus.FINISHED;
    }
}
