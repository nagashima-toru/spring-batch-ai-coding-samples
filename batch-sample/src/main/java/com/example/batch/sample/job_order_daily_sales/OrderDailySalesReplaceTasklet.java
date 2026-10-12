package com.example.batch.sample.job_order_daily_sales;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * ORDER-001 Step 5: 対象日の受注明細・日次売上集計の入れ替え Tasklet。
 *
 * <p>同一トランザクション内で「対象日の受注明細削除 → 対象日の集計削除 → ワークからの受注明細登録 →
 * 対象日の集計登録」を順に行う。途中で例外が出れば全体がロールバックされ、本テーブルは旧データのまま残る。
 * Mapper の例外は包み直さずそのまま送出する。</p>
 *
 * <p>{@code @StepScope} で使うため final にしない。</p>
 */
public class OrderDailySalesReplaceTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(OrderDailySalesReplaceTasklet.class);

    private final LocalDate targetDate;
    private final OrderDetailMapper orderDetailMapper;
    private final DailyStoreSalesMapper dailyStoreSalesMapper;

    public OrderDailySalesReplaceTasklet(LocalDate targetDate, OrderDetailMapper orderDetailMapper,
            DailyStoreSalesMapper dailyStoreSalesMapper) {
        this.targetDate = targetDate;
        this.orderDetailMapper = orderDetailMapper;
        this.dailyStoreSalesMapper = dailyStoreSalesMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int deletedDetails = orderDetailMapper.deleteByOrderDate(targetDate);
        int deletedSales = dailyStoreSalesMapper.deleteBySalesDate(targetDate);
        int insertedDetails = orderDetailMapper.insertFromWork();
        int insertedSales = dailyStoreSalesMapper.insertAggregate(targetDate);
        contribution.incrementWriteCount(insertedDetails);
        log.info("日次売上を入れ替えました: 対象日={} 明細削除={} 集計削除={} 明細登録={} 集計登録={}",
                targetDate, deletedDetails, deletedSales, insertedDetails, insertedSales);
        return RepeatStatus.FINISHED;
    }
}
