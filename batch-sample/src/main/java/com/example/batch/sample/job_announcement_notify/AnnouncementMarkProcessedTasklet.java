package com.example.batch.sample.job_announcement_notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * ANNOUNCE-001 Step4: 処理中のお知らせを処理済みへ一括更新する Tasklet（詳細設計書 §2 Step4）。
 * 処理日時（{@code processed_at}）は SQL 側で {@code CURRENT_TIMESTAMP} を設定する。
 */
public class AnnouncementMarkProcessedTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementMarkProcessedTasklet.class);

    private final AnnouncementMapper announcementMapper;

    public AnnouncementMarkProcessedTasklet(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int markedCount = announcementMapper.markProcessed();
        log.info("お知らせを処理済みへ更新しました: 件数={}", markedCount);
        return RepeatStatus.FINISHED;
    }
}
