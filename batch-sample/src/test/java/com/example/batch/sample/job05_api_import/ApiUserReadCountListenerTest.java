package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

import com.example.batch.core.item.CountingItemStreamReader;

/**
 * {@link ApiUserReadCountListener} の単体テスト。
 */
class ApiUserReadCountListenerTest {

    private static final String COUNT_KEY = CountingItemStreamReader.countKey(Sample05JobConfig.USER_READ_COUNTER_NAME);

    private final ApiUserReadCountListener listener = new ApiUserReadCountListener(
            Sample05JobConfig.USER_READ_COUNTER_NAME);

    private JobExecution newJobExecution(long id) {
        return new JobExecution(id, new JobInstance(1L, "sample05ApiImportJob"), new JobParameters());
    }

    private StepExecution newStepExecution(JobExecution jobExecution, BatchStatus status) {
        StepExecution stepExecution = new StepExecution(1L, "sample05.01ImportStep", jobExecution);
        stepExecution.setStatus(status);
        return stepExecution;
    }

    private long savedCount(JobExecution jobExecution) {
        return jobExecution.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY);
    }

    @Test
    void 取込Stepが完了した場合_件数のキーの値をそのまま保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = newStepExecution(jobExecution, BatchStatus.COMPLETED);
        stepExecution.getExecutionContext().putLong(COUNT_KEY, 3L);
        stepExecution.setReadCount(99);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(3L);
    }

    @Test
    void 取込Stepがチャンクの途中で失敗した場合_readCountではなくコミット済みの件数を保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = newStepExecution(jobExecution, BatchStatus.FAILED);
        stepExecution.getExecutionContext().putLong(COUNT_KEY, 2L);
        stepExecution.setReadCount(3);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(2L);
    }

    @Test
    void 取込Stepが最後のチャンクのコミット後に停止した場合_コミット済みの件数をそのまま保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = newStepExecution(jobExecution, BatchStatus.STOPPED);
        stepExecution.getExecutionContext().putLong(COUNT_KEY, 5L);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(5L);
    }

    @Test
    void 取込Stepが最後のチャンクのコミット後に失敗した場合_コミット済みの件数をそのまま保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = newStepExecution(jobExecution, BatchStatus.FAILED);
        stepExecution.getExecutionContext().putLong(COUNT_KEY, 5L);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(5L);
    }

    @Test
    void 取込Stepが最初のチャンクで失敗した場合_件数のキーが無いため0件を保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = newStepExecution(jobExecution, BatchStatus.FAILED);
        stepExecution.setReadCount(2);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isZero();
    }

    @Test
    void 取込Stepが完了したのに件数のキーが無い場合_0件を保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = newStepExecution(jobExecution, BatchStatus.COMPLETED);
        stepExecution.getExecutionContext().putLong("otherCounter.item.count", 4L);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isZero();
    }

    @Test
    void リスタートで取込Stepが完了した場合_前回の件数に足し込まず引き継いだ件数で上書きする() {
        JobExecution jobExecution = newJobExecution(2L);
        jobExecution.getExecutionContext().putLong(ApiUserReadCountListener.READ_COUNT_KEY, 2L);
        StepExecution stepExecution = newStepExecution(jobExecution, BatchStatus.COMPLETED);
        stepExecution.getExecutionContext().putLong(COUNT_KEY, 5L);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(5L);
    }
}
