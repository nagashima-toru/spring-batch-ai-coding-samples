package com.example.batch.core.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.step.StepExecution;

class LoggingStepExecutionListenerTest {

    private final LoggingStepExecutionListener listener = new LoggingStepExecutionListener();

    @Test
    void afterStepはExitStatusを変更しない() {
        StepExecution step = mock(StepExecution.class);
        when(step.getStepName()).thenReturn("testStep");
        when(step.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(step.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(step.getEndTime()).thenReturn(LocalDateTime.now());

        assertThat(listener.afterStep(step)).isNull();
    }

    @Test
    void 開始時刻未設定でも例外にならない() {
        StepExecution step = mock(StepExecution.class);
        when(step.getStepName()).thenReturn("testStep");
        when(step.getStatus()).thenReturn(BatchStatus.STARTING);

        assertThatCode(() -> {
            listener.beforeStep(step);
            listener.afterStep(step);
        }).doesNotThrowAnyException();
    }
}
