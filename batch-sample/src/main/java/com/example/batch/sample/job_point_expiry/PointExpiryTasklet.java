package com.example.batch.sample.job_point_expiry;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * POINT-001 Step1: 期限切れポイント付与の失効＋失効履歴登録 Tasklet。
 *
 * <p>{@link PointGrantMapper#expireAndRecordHistory(LocalDate)} を1回呼ぶだけで、
 * 失効更新と履歴登録がデータ変更 CTE により単一 SQL・単一トランザクションで原子的に実行される
 * （詳細設計書 §2・§4。サンプル04 {@code SalesAggregateTasklet}・ANNOUNCE-001
 * {@code AnnouncementPickupTasklet} と同型）。対象0件でも異常とはしない。</p>
 *
 * <p>Mapper が送出する例外（{@code DataAccessException} 系）は握りつぶさずそのまま送出する
 * （詳細設計書 §3。Skip/Retry を使わない本ジョブでは包み直しても挙動が変わらないため）。</p>
 */
public class PointExpiryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PointExpiryTasklet.class);

    private final LocalDate targetDate;
    private final PointGrantMapper pointGrantMapper;

    public PointExpiryTasklet(LocalDate targetDate, PointGrantMapper pointGrantMapper) {
        this.targetDate = targetDate;
        this.pointGrantMapper = pointGrantMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int expiredCount = pointGrantMapper.expireAndRecordHistory(targetDate);
        contribution.incrementWriteCount(expiredCount);
        log.info("ポイントを失効しました: 対象日={} 失効件数={}", targetDate, expiredCount);
        return RepeatStatus.FINISHED;
    }
}
