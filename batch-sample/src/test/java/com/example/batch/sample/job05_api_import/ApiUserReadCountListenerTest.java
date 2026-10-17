package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * {@link ApiUserReadCountListener} の単体テスト。
 */
class ApiUserReadCountListenerTest {

    @Test
    void 取込Step終了時_読込件数と読込Skip件数の合計をJobのExecutionContextに保存する() {
        JobExecution jobExecution = new JobExecution(1L, new JobInstance(1L, "sample05ApiImportJob"),
                new JobParameters());
        StepExecution stepExecution = new StepExecution(1L, "sample05.01ImportStep", jobExecution);
        stepExecution.setReadCount(3);
        stepExecution.setReadSkipCount(1);

        new ApiUserReadCountListener().afterStep(stepExecution);

        assertThat(jobExecution.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY))
                .isEqualTo(4L);
    }

    @Test
    void リスタートで取込Stepをやり直した場合_前回の実行の件数に足し込む() {
        JobExecution jobExecution = new JobExecution(2L, new JobInstance(1L, "sample05ApiImportJob"),
                new JobParameters());
        // 前回の実行（途中失敗）でコミット済みの件数が Job の ExecutionContext に残っている
        jobExecution.getExecutionContext().putLong(ApiUserReadCountListener.READ_COUNT_KEY, 600L);
        StepExecution stepExecution = new StepExecution(2L, "sample05.01ImportStep", jobExecution);
        stepExecution.setReadCount(400);

        new ApiUserReadCountListener().afterStep(stepExecution);

        assertThat(jobExecution.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY))
                .isEqualTo(1000L);
    }
}
