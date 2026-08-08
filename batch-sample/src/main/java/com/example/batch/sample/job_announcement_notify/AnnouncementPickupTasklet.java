package com.example.batch.sample.job_announcement_notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * ANNOUNCE-001 Step1: 未処理のお知らせを処理中へ一括更新する Tasklet。
 *
 * <p>{@code WHERE status='0'} の一括 UPDATE 1本で完結する単発処理のため Tasklet を採用する
 * （詳細設計書 §2 Step1）。前回異常終了で「処理中」のまま残ったお知らせも Step3 以降の対象に
 * 含まれるため、対象0件でも異常とはしない。</p>
 */
public class AnnouncementPickupTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementPickupTasklet.class);

    private final AnnouncementMapper announcementMapper;

    public AnnouncementPickupTasklet(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int pickedUpCount = announcementMapper.pickupUnprocessed();
        log.info("未処理のお知らせを処理中へ更新しました: 件数={}", pickedUpCount);
        return RepeatStatus.FINISHED;
    }
}
