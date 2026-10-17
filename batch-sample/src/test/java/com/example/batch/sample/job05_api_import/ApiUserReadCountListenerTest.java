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
}
