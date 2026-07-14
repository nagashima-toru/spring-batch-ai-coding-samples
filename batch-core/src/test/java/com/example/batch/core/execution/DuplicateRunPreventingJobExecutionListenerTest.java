package com.example.batch.core.execution;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;

import com.example.batch.core.exception.SystemException;

class DuplicateRunPreventingJobExecutionListenerTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final DuplicateRunPreventingJobExecutionListener listener =
            new DuplicateRunPreventingJobExecutionListener(jobRepository);

    private JobExecution execution(long id, String jobName) {
        JobExecution execution = mock(JobExecution.class);
        JobInstance instance = mock(JobInstance.class);
        when(instance.getJobName()).thenReturn(jobName);
        when(execution.getJobInstance()).thenReturn(instance);
        when(execution.getId()).thenReturn(id);
        return execution;
    }

    @Test
    void 自分自身のみ実行中の場合は起動を許可する() {
        JobExecution self = execution(1L, "importJob");
        when(jobRepository.findRunningJobExecutions("importJob")).thenReturn(Set.of(self));

        assertThatCode(() -> listener.beforeJob(self)).doesNotThrowAnyException();
    }

    @Test
    void 同名ジョブが実行中の場合はSystemExceptionをスローする() {
        JobExecution self = execution(2L, "importJob");
        JobExecution running = execution(1L, "importJob");
        when(jobRepository.findRunningJobExecutions("importJob")).thenReturn(Set.of(self, running));

        assertThatThrownBy(() -> listener.beforeJob(self))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("二重起動");
    }

    @Test
    void 実行中ジョブが無ければ起動を許可する() {
        JobExecution self = execution(1L, "importJob");
        when(jobRepository.findRunningJobExecutions("importJob")).thenReturn(Set.of());

        assertThatCode(() -> listener.beforeJob(self)).doesNotThrowAnyException();
    }
}
