package com.example.batch.core.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

import com.example.batch.core.execution.BatchExitCodes;

class LoggingJobExecutionListenerTest {

    private final LoggingJobExecutionListener listener = new LoggingJobExecutionListener();

    private JobExecution jobExecution(BatchStatus status, ExitStatus exitStatus, long skipCount) {
        JobExecution execution = mock(JobExecution.class);
        JobInstance instance = mock(JobInstance.class);
        StepExecution step = mock(StepExecution.class);
        when(instance.getJobName()).thenReturn("testJob");
        when(execution.getJobInstance()).thenReturn(instance);
        when(execution.getId()).thenReturn(1L);
        when(execution.getJobParameters()).thenReturn(new JobParameters());
        when(execution.getStatus()).thenReturn(status);
        when(execution.getExitStatus()).thenReturn(exitStatus);
        when(execution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(execution.getEndTime()).thenReturn(LocalDateTime.now());
        when(step.getSkipCount()).thenReturn(skipCount);
        when(execution.getStepExecutions()).thenReturn(List.of(step));
        when(execution.getAllFailureExceptions()).thenReturn(List.of());
        return execution;
    }

    @Test
    void Skipが発生した完走ジョブは警告ExitStatusへ変更する() {
        JobExecution execution = jobExecution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, 3);

        listener.afterJob(execution);

        ArgumentCaptor<ExitStatus> captor = ArgumentCaptor.forClass(ExitStatus.class);
        verify(execution).setExitStatus(captor.capture());
        assertThat(captor.getValue().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
    }

    @Test
    void Skipなしの完走ジョブはExitStatusを変更しない() {
        JobExecution execution = jobExecution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, 0);

        listener.afterJob(execution);

        verify(execution, never()).setExitStatus(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 失敗ジョブはExitStatusを変更しない() {
        JobExecution execution = jobExecution(BatchStatus.FAILED, ExitStatus.FAILED, 5);

        listener.afterJob(execution);

        verify(execution, never()).setExitStatus(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void beforeJobでMDCを設定しafterJobで解除する() {
        JobExecution execution = jobExecution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, 0);

        listener.beforeJob(execution);
        assertThat(MDC.get("jobName")).isEqualTo("testJob");
        assertThat(MDC.get("jobExecutionId")).isEqualTo("1");

        listener.afterJob(execution);
        assertThat(MDC.get("jobName")).isNull();
        assertThat(MDC.get("jobExecutionId")).isNull();
    }
}
