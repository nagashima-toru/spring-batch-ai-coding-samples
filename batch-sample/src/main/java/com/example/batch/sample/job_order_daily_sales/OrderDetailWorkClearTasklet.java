package com.example.batch.sample.job_order_daily_sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * ORDER-001 Step 3: 受注明細ワークを空にする Tasklet。
 *
 * <p>{@link OrderDetailWorkMapper#truncate()} を1回呼ぶ。Mapper の例外は包み直さずそのまま送出する
 * （Skip/Retry を使わない Step のため挙動が変わらない）。</p>
 */
public class OrderDetailWorkClearTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(OrderDetailWorkClearTasklet.class);

    private final OrderDetailWorkMapper orderDetailWorkMapper;

    public OrderDetailWorkClearTasklet(OrderDetailWorkMapper orderDetailWorkMapper) {
        this.orderDetailWorkMapper = orderDetailWorkMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        orderDetailWorkMapper.truncate();
        log.info("受注明細ワークを初期化しました");
        return RepeatStatus.FINISHED;
    }
}
