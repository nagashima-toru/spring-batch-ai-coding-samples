package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * {@link ApiUserReadCountListener} の単体テスト。
 */
class ApiUserReadCountListenerTest {

    private static final String API_KEY = Sample05JobConfig.USER_READER_NAME + ".read.count";
    private static final String JSON_KEY = Sample05JobConfig.USER_JSON_READER_NAME + ".read.count";

    private final ApiUserReadCountListener listener = new ApiUserReadCountListener(
            Sample05JobConfig.USER_READER_NAME, Sample05JobConfig.USER_JSON_READER_NAME);

    private JobExecution newJobExecution(long id) {
        return new JobExecution(id, new JobInstance(1L, "sample05ApiImportJob"), new JobParameters());
    }

    private long savedCount(JobExecution jobExecution) {
        return jobExecution.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY);
    }

    @Test
    void 取込Stepが完了した場合_Readerの読込位置から終端の読込1件を引いた件数を保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = new StepExecution(1L, "sample05.01ImportStep", jobExecution);
        stepExecution.setStatus(BatchStatus.COMPLETED);
        stepExecution.getExecutionContext().putInt(API_KEY, 4);
        stepExecution.setReadCount(99);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(3L);
    }

    @Test
    void inputFile指定で取込Stepが完了した場合_JSON用Readerの読込位置から件数を求める() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = new StepExecution(1L, "sample05.01ImportStep", jobExecution);
        stepExecution.setStatus(BatchStatus.COMPLETED);
        stepExecution.getExecutionContext().putInt(JSON_KEY, 3);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(2L);
    }

    @Test
    void 取込Stepがチャンクの途中で失敗した場合_readCountではなくコミット済みの読込位置を保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = new StepExecution(1L, "sample05.01ImportStep", jobExecution);
        stepExecution.setStatus(BatchStatus.FAILED);
        stepExecution.getExecutionContext().putInt(API_KEY, 2);
        stepExecution.setReadCount(3);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(2L);
    }

    @Test
    void 取込Stepが最初のチャンクで失敗した場合_読込位置が無いため0件を保存する() {
        JobExecution jobExecution = newJobExecution(1L);
        StepExecution stepExecution = new StepExecution(1L, "sample05.01ImportStep", jobExecution);
        stepExecution.setStatus(BatchStatus.FAILED);
        stepExecution.setReadCount(2);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isZero();
    }

    @Test
    void リスタートで取込Stepが完了した場合_前回の件数に足し込まず読込位置から求めた件数で上書きする() {
        JobExecution jobExecution = newJobExecution(2L);
        jobExecution.getExecutionContext().putLong(ApiUserReadCountListener.READ_COUNT_KEY, 2L);
        StepExecution stepExecution = new StepExecution(2L, "sample05.01ImportStep", jobExecution);
        stepExecution.setStatus(BatchStatus.COMPLETED);
        stepExecution.getExecutionContext().putInt(API_KEY, 6);

        listener.afterStep(stepExecution);

        assertThat(savedCount(jobExecution)).isEqualTo(5L);
    }
}
