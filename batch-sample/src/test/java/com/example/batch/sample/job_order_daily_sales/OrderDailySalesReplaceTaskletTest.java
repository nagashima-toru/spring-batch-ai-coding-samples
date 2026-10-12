package com.example.batch.sample.job_order_daily_sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.mockito.InOrder;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link OrderDailySalesReplaceTasklet} の単体テスト。
 *
 * <p>詳細設計書 ORDER-001 §7「単体テスト」の業務 Tasklet の観点に基づく。</p>
 */
class OrderDailySalesReplaceTaskletTest {

    private static final LocalDate TARGET = LocalDate.of(2026, 10, 11);

    private final OrderDetailMapper orderDetailMapper = mock(OrderDetailMapper.class);
    private final DailyStoreSalesMapper dailyStoreSalesMapper = mock(DailyStoreSalesMapper.class);
    private final OrderDailySalesReplaceTasklet tasklet =
            new OrderDailySalesReplaceTasklet(TARGET, orderDetailMapper, dailyStoreSalesMapper);

    private StepContribution stepContribution() {
        JobExecution jobExecution = new JobExecution(1L, new JobInstance(1L, "orderDailySalesJob"),
                new JobParameters());
        return new StepContribution(new StepExecution(1L, "orderDailySales.05ReplaceStep", jobExecution));
    }

    @Test
    void 実行した場合＿明細削除_集計削除_明細登録_集計登録の順にMapperを1回ずつ呼びFINISHEDで終了する() throws Exception {
        when(orderDetailMapper.deleteByOrderDate(TARGET)).thenReturn(4);
        when(dailyStoreSalesMapper.deleteBySalesDate(TARGET)).thenReturn(2);
        when(orderDetailMapper.insertFromWork()).thenReturn(7);
        when(dailyStoreSalesMapper.insertAggregate(TARGET)).thenReturn(3);

        RepeatStatus result = tasklet.execute(stepContribution(), null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        InOrder inOrder = inOrder(orderDetailMapper, dailyStoreSalesMapper);
        inOrder.verify(orderDetailMapper).deleteByOrderDate(TARGET);
        inOrder.verify(dailyStoreSalesMapper).deleteBySalesDate(TARGET);
        inOrder.verify(orderDetailMapper).insertFromWork();
        inOrder.verify(dailyStoreSalesMapper).insertAggregate(TARGET);
        verifyNoMoreInteractions(orderDetailMapper, dailyStoreSalesMapper);
    }

    @Test
    void 実行した場合＿writeCountへ明細登録件数が反映される() throws Exception {
        when(orderDetailMapper.deleteByOrderDate(TARGET)).thenReturn(4);
        when(dailyStoreSalesMapper.deleteBySalesDate(TARGET)).thenReturn(2);
        when(orderDetailMapper.insertFromWork()).thenReturn(7);
        when(dailyStoreSalesMapper.insertAggregate(TARGET)).thenReturn(3);
        StepContribution contribution = stepContribution();

        tasklet.execute(contribution, null);

        assertThat(contribution.getWriteCount()).isEqualTo(7);
    }

    @Test
    void ワークが0件の場合＿異常にせずwriteCount0でFINISHEDとなり旧データの削除は行う() throws Exception {
        when(orderDetailMapper.deleteByOrderDate(TARGET)).thenReturn(5);
        when(dailyStoreSalesMapper.deleteBySalesDate(TARGET)).thenReturn(2);
        when(orderDetailMapper.insertFromWork()).thenReturn(0);
        when(dailyStoreSalesMapper.insertAggregate(TARGET)).thenReturn(0);
        StepContribution contribution = stepContribution();

        RepeatStatus result = tasklet.execute(contribution, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        assertThat(contribution.getWriteCount()).isZero();
        verify(orderDetailMapper).deleteByOrderDate(TARGET);
        verify(dailyStoreSalesMapper).deleteBySalesDate(TARGET);
    }

    @Test
    void 明細登録で一意制約違反が起きた場合＿包み直さず送出し集計登録とwriteCount反映は行わない() {
        DuplicateKeyException failure = new DuplicateKeyException("pk 重複");
        when(orderDetailMapper.insertFromWork()).thenThrow(failure);
        StepContribution contribution = stepContribution();

        assertThatThrownBy(() -> tasklet.execute(contribution, null)).isSameAs(failure);

        verify(dailyStoreSalesMapper, never()).insertAggregate(TARGET);
        assertThat(contribution.getWriteCount()).isZero();
    }

    @Test
    void 明細削除で例外が起きた場合＿そのまま送出し後続のMapperは呼ばない() {
        IllegalStateException failure = new IllegalStateException("削除失敗");
        doThrow(failure).when(orderDetailMapper).deleteByOrderDate(TARGET);

        assertThatThrownBy(() -> tasklet.execute(stepContribution(), null)).isSameAs(failure);

        verifyNoMoreInteractions(dailyStoreSalesMapper);
        verify(orderDetailMapper, never()).insertFromWork();
    }

    @Test
    void 実行した場合＿削除件数と登録件数を含むINFOログを出力する() throws Exception {
        when(orderDetailMapper.deleteByOrderDate(TARGET)).thenReturn(4);
        when(dailyStoreSalesMapper.deleteBySalesDate(TARGET)).thenReturn(2);
        when(orderDetailMapper.insertFromWork()).thenReturn(7);
        when(dailyStoreSalesMapper.insertAggregate(TARGET)).thenReturn(3);
        Logger logger = (Logger) LoggerFactory.getLogger(OrderDailySalesReplaceTasklet.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            tasklet.execute(stepContribution(), null);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("日次売上を入れ替えました: 対象日=2026-10-11 明細削除=4 集計削除=2 明細登録=7 集計登録=3");
    }
}
