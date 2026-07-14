package com.example.batch.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.boot.batch.autoconfigure.JobExecutionEvent;

class WarningAwareExitCodeGeneratorTest {

    private final WarningAwareExitCodeGenerator generator = new WarningAwareExitCodeGenerator();

    private void publish(BatchStatus status, ExitStatus exitStatus) {
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(status);
        when(execution.getExitStatus()).thenReturn(exitStatus);
        generator.onApplicationEvent(new JobExecutionEvent(execution));
    }

    @Test
    void 正常完了は終了コード0を返す() {
        publish(BatchStatus.COMPLETED, ExitStatus.COMPLETED);

        assertThat(generator.getExitCode()).isEqualTo(BatchExitCodes.NORMAL);
    }

    @Test
    void 警告付き完了は終了コード2を返す() {
        publish(BatchStatus.COMPLETED, BatchExitCodes.COMPLETED_WITH_WARNING);

        assertThat(generator.getExitCode()).isEqualTo(BatchExitCodes.WARNING);
    }

    @Test
    void 失敗は終了コード1を返す() {
        publish(BatchStatus.FAILED, ExitStatus.FAILED);

        assertThat(generator.getExitCode()).isEqualTo(BatchExitCodes.ERROR);
    }

    @Test
    void 警告と失敗が混在する場合は失敗を優先する() {
        publish(BatchStatus.COMPLETED, BatchExitCodes.COMPLETED_WITH_WARNING);
        publish(BatchStatus.FAILED, ExitStatus.FAILED);

        assertThat(generator.getExitCode()).isEqualTo(BatchExitCodes.ERROR);
    }

    @Test
    void ジョブが1つも実行されていない場合は異常とする() {
        assertThat(generator.getExitCode()).isEqualTo(BatchExitCodes.ERROR);
    }
}
