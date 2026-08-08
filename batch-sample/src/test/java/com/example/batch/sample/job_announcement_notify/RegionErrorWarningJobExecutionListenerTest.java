package com.example.batch.sample.job_announcement_notify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;

import com.example.batch.core.execution.BatchExitCodes;

class RegionErrorWarningJobExecutionListenerTest {

    private final RegionErrorWarningJobExecutionListener listener = new RegionErrorWarningJobExecutionListener();

    private JobExecution jobExecution(BatchStatus status, ExitStatus exitStatus, int regionErrorCount) {
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, "announcementNotifyJob"),
                new JobParameters());
        execution.setStatus(status);
        execution.setExitStatus(exitStatus);
        execution.getExecutionContext()
                .putInt(AnnouncementRegionCheckTasklet.JOB_CONTEXT_KEY_REGION_ERROR_COUNT, regionErrorCount);
        return execution;
    }

    @Test
    void 区域展開不能が0件の場合はExitStatusを変更しない() {
        JobExecution execution = jobExecution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, 0);

        listener.afterJob(execution);

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }

    @Test
    void 区域展開不能が1件以上の完走ジョブは警告ExitStatusへ変更する() {
        JobExecution execution = jobExecution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, 1);

        listener.afterJob(execution);

        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
    }

    @Test
    void 区域展開不能があっても失敗ジョブのExitStatusは変更しない() {
        JobExecution execution = jobExecution(BatchStatus.FAILED, ExitStatus.FAILED, 1);

        listener.afterJob(execution);

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }
}
