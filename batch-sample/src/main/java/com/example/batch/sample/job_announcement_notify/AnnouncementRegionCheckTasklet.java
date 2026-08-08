package com.example.batch.sample.job_announcement_notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * ANNOUNCE-001 Step2: 通知区域が郵便番号マスタに1件も展開できないお知らせをエラーへ更新する Tasklet。
 *
 * <p>更新件数は本 Step（Tasklet）の {@code StepExecution} ではなく
 * <b>Job レベルの {@code ExecutionContext}</b> へ記録する。Tasklet Step は一度 {@code COMPLETED}
 * すると既定でリスタート時に再実行されないため、Step レベルの {@code ExecutionContext} に記録すると
 * 後続 Step の失敗によるリスタート実行では値が失われ、{@link RegionErrorWarningJobExecutionListener}
 * が警告判定できなくなる。Job レベルの {@code ExecutionContext} はリスタート時に前回実行分から
 * 引き継がれるため、この問題を回避できる（詳細設計書 §2 Step2・§5。設計レビューで検出・反映）。</p>
 */
public class AnnouncementRegionCheckTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementRegionCheckTasklet.class);

    /** Job レベル ExecutionContext に区域展開不能件数を記録するキー。 */
    static final String JOB_CONTEXT_KEY_REGION_ERROR_COUNT = "regionErrorCount";

    private final AnnouncementMapper announcementMapper;

    public AnnouncementRegionCheckTasklet(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int regionErrorCount = announcementMapper.markRegionUnresolved();

        ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();
        int accumulated = jobContext.getInt(JOB_CONTEXT_KEY_REGION_ERROR_COUNT, 0) + regionErrorCount;
        jobContext.putInt(JOB_CONTEXT_KEY_REGION_ERROR_COUNT, accumulated);

        if (regionErrorCount > 0) {
            log.warn("通知区域が郵便番号に展開できないお知らせを検出しました: 件数={}", regionErrorCount);
        } else {
            log.info("区域展開エラーのお知らせはありませんでした");
        }
        return RepeatStatus.FINISHED;
    }
}
