package com.example.batch.sample.job_point_expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link PointExpiryTasklet} の単体テスト。
 *
 * <p>詳細設計書 docs/projects/member-point-expiry/detail-design/POINT-001.md §6
 * 「単体テスト（PointExpiryTaskletTest）」の観点に基づく。</p>
 */
class PointExpiryTaskletTest {

    private final PointGrantMapper pointGrantMapper = mock(PointGrantMapper.class);

    private StepContribution stepContribution() {
        JobExecution jobExecution = new JobExecution(1L, new JobInstance(1L, "pointExpiryJob"), new JobParameters());
        StepExecution stepExecution = new StepExecution("pointExpiry.01ExpireStep", jobExecution);
        return new StepContribution(stepExecution);
    }

    @Test
    void 失効対象がある場合はMapperを1回だけ呼びFINISHEDで終了しwriteCountへ失効件数を反映する() throws Exception {
        LocalDate targetDate = LocalDate.of(2026, 8, 10);
        PointExpiryTasklet tasklet = new PointExpiryTasklet(targetDate, pointGrantMapper);
        when(pointGrantMapper.expireAndRecordHistory(targetDate)).thenReturn(3);
        StepContribution contribution = stepContribution();

        RepeatStatus result = tasklet.execute(contribution, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        assertThat(contribution.getWriteCount()).isEqualTo(3);
        verify(pointGrantMapper, times(1)).expireAndRecordHistory(targetDate);
        verifyNoMoreInteractions(pointGrantMapper);
    }

    @Test
    void 失効対象が0件でも異常にせずFINISHEDで終了しwriteCountは0のままとなる() throws Exception {
        LocalDate targetDate = LocalDate.of(2026, 8, 10);
        PointExpiryTasklet tasklet = new PointExpiryTasklet(targetDate, pointGrantMapper);
        when(pointGrantMapper.expireAndRecordHistory(targetDate)).thenReturn(0);
        StepContribution contribution = stepContribution();

        RepeatStatus result = tasklet.execute(contribution, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        assertThat(contribution.getWriteCount()).isZero();
    }

    @Test
    void コンストラクタで注入されたtargetDateが実行時刻から導出されずそのままMapperへ渡る() throws Exception {
        LocalDate injectedTargetDate = LocalDate.of(2020, 1, 1);
        PointExpiryTasklet tasklet = new PointExpiryTasklet(injectedTargetDate, pointGrantMapper);
        when(pointGrantMapper.expireAndRecordHistory(any())).thenReturn(0);

        tasklet.execute(stepContribution(), null);

        ArgumentCaptor<LocalDate> targetDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(pointGrantMapper).expireAndRecordHistory(targetDateCaptor.capture());
        assertThat(targetDateCaptor.getValue()).isEqualTo(injectedTargetDate).isNotEqualTo(LocalDate.now());
    }

    @Test
    void Mapperがシステム例外を送出した場合は握りつぶさずそのまま送出する() {
        LocalDate targetDate = LocalDate.of(2026, 8, 10);
        PointExpiryTasklet tasklet = new PointExpiryTasklet(targetDate, pointGrantMapper);
        DataAccessResourceFailureException thrown = new DataAccessResourceFailureException("DB接続断");
        when(pointGrantMapper.expireAndRecordHistory(targetDate)).thenThrow(thrown);

        assertThatThrownBy(() -> tasklet.execute(stepContribution(), null)).isSameAs(thrown);
    }

    @Test
    void Mapperがデッドロック例外を送出した場合も握りつぶさずそのまま送出する() {
        LocalDate targetDate = LocalDate.of(2026, 8, 10);
        PointExpiryTasklet tasklet = new PointExpiryTasklet(targetDate, pointGrantMapper);
        DeadlockLoserDataAccessException thrown = new DeadlockLoserDataAccessException("デッドロック検出", null);
        when(pointGrantMapper.expireAndRecordHistory(targetDate)).thenThrow(thrown);

        assertThatThrownBy(() -> tasklet.execute(stepContribution(), null)).isSameAs(thrown);
    }

    @Test
    void Mapperが一意制約違反を送出した場合もSystemExceptionへ包み直さずそのまま送出する() {
        LocalDate targetDate = LocalDate.of(2026, 8, 10);
        PointExpiryTasklet tasklet = new PointExpiryTasklet(targetDate, pointGrantMapper);
        DuplicateKeyException thrown = new DuplicateKeyException("duplicate key value violates unique constraint");
        when(pointGrantMapper.expireAndRecordHistory(targetDate)).thenThrow(thrown);

        assertThatThrownBy(() -> tasklet.execute(stepContribution(), null))
                .isSameAs(thrown)
                .hasMessageContaining("duplicate key");
    }
}
