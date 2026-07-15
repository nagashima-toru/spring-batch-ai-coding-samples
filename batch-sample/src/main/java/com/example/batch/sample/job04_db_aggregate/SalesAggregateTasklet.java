package com.example.batch.sample.job04_db_aggregate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.sample.common.SalesMapper;

/**
 * サンプル04: 売上集計の洗い替え Tasklet。
 *
 * <p>truncate と INSERT ... SELECT を<b>同一トランザクション</b>（同一 Tasklet 内）で実行する。
 * 途中失敗時は全体がロールバックされ、旧データが消えたままにならない
 * （docs/architecture.md §4）。集計は SQL で完結させ、Java 側でループしない。</p>
 */
public class SalesAggregateTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(SalesAggregateTasklet.class);

    private final SalesMapper salesMapper;

    public SalesAggregateTasklet(SalesMapper salesMapper) {
        this.salesMapper = salesMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        salesMapper.truncateSummary();
        int inserted = salesMapper.insertSummaryFromTransactions();
        log.info("売上集計を洗い替えました: 集計行数={}", inserted);
        return RepeatStatus.FINISHED;
    }
}
