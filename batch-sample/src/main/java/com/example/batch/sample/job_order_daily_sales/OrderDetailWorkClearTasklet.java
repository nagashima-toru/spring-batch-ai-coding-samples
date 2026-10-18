package com.example.batch.sample.job_order_daily_sales;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.SystemException;

/**
 * ORDER-001 Step 3: エラーファイルを削除し、受注明細ワークを空にする Tasklet。
 *
 * <p>エラーファイルを削除してから {@link OrderDetailWorkMapper#truncate()} を1回呼ぶ。
 * ファイルの削除はトランザクションの外の操作で戻らないが、リスタートでは Step 1〜4 を最初からやり直し
 * エラーファイルを作り直すため問題ない。削除に失敗した時は TRUNCATE を呼ばない。
 * Mapper の例外は包み直さずそのまま送出する（Skip/Retry を使わない Step のため挙動が変わらない）。</p>
 */
public class OrderDetailWorkClearTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(OrderDetailWorkClearTasklet.class);

    private final Path errorFile;
    private final OrderDetailWorkMapper orderDetailWorkMapper;

    public OrderDetailWorkClearTasklet(Path errorFile, OrderDetailWorkMapper orderDetailWorkMapper) {
        this.errorFile = errorFile;
        this.orderDetailWorkMapper = orderDetailWorkMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        boolean deleted;
        try {
            deleted = Files.deleteIfExists(errorFile);
        } catch (IOException e) {
            throw new SystemException("エラーファイルを削除できません: " + errorFile, e);
        }
        orderDetailWorkMapper.truncate();
        log.info("受注明細ワークを初期化しました: errorFile={} 削除={}", errorFile, deleted);
        return RepeatStatus.FINISHED;
    }
}
