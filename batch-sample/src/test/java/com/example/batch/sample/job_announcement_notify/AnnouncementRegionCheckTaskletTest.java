package com.example.batch.sample.job_announcement_notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class AnnouncementRegionCheckTaskletTest {

    private final AnnouncementMapper announcementMapper = mock(AnnouncementMapper.class);

    private final AnnouncementRegionCheckTasklet tasklet = new AnnouncementRegionCheckTasklet(announcementMapper);

    private ChunkContext chunkContext() {
        JobExecution jobExecution = new JobExecution(1L, new JobInstance(1L, "announcementNotifyJob"),
                new JobParameters());
        StepExecution stepExecution = new StepExecution("announcementNotify.02RegionCheckStep", jobExecution);
        return new ChunkContext(new StepContext(stepExecution));
    }

    @Test
    void 展開不能なお知らせが0件ならJobレベルExecutionContextは0のまま正常終了する() throws Exception {
        when(announcementMapper.markRegionUnresolved()).thenReturn(0);
        ChunkContext chunkContext = chunkContext();

        RepeatStatus result = tasklet.execute(null, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        assertThat(chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .getInt(AnnouncementRegionCheckTasklet.JOB_CONTEXT_KEY_REGION_ERROR_COUNT, 0)).isZero();
    }

    @Test
    void 展開不能なお知らせがある場合はJobレベルExecutionContextへ件数を記録する() throws Exception {
        when(announcementMapper.markRegionUnresolved()).thenReturn(3);
        ChunkContext chunkContext = chunkContext();

        tasklet.execute(null, chunkContext);

        assertThat(chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .getInt(AnnouncementRegionCheckTasklet.JOB_CONTEXT_KEY_REGION_ERROR_COUNT, 0)).isEqualTo(3);
    }

    @Test
    void リスタートで前回実行分の件数が引き継がれている場合は今回の件数を加算する() throws Exception {
        // Job レベル ExecutionContext はリスタート時に前回実行分から引き継がれる想定（詳細設計書 §5）。
        // Step2 自体が再実行されない restart でも警告判定が失われないことの回帰確認。
        when(announcementMapper.markRegionUnresolved()).thenReturn(2);
        ChunkContext chunkContext = chunkContext();
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .putInt(AnnouncementRegionCheckTasklet.JOB_CONTEXT_KEY_REGION_ERROR_COUNT, 1);

        tasklet.execute(null, chunkContext);

        assertThat(chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                .getInt(AnnouncementRegionCheckTasklet.JOB_CONTEXT_KEY_REGION_ERROR_COUNT, 0)).isEqualTo(3);
    }
}
