package com.example.batch.sample.job04_db_aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.sample.common.SalesMapper;

/**
 * {@link SalesAggregateTasklet} の単体テスト。
 */
class SalesAggregateTaskletTest {

    private final SalesMapper salesMapper = mock(SalesMapper.class);
    private final SalesAggregateTasklet tasklet = new SalesAggregateTasklet(salesMapper);

    @Test
    void 実行した場合＿truncateの後にinsertを呼びFINISHEDで終了する() throws Exception {
        when(salesMapper.insertSummaryFromTransactions()).thenReturn(3);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        InOrder inOrder = inOrder(salesMapper);
        inOrder.verify(salesMapper).truncateSummary();
        inOrder.verify(salesMapper).insertSummaryFromTransactions();
    }

    @Test
    void truncateで例外が起きた場合＿そのまま送出しinsertは呼ばない() {
        IllegalStateException failure = new IllegalStateException("truncate失敗");
        doThrow(failure).when(salesMapper).truncateSummary();

        assertThatThrownBy(() -> tasklet.execute(null, null)).isSameAs(failure);

        verify(salesMapper, never()).insertSummaryFromTransactions();
    }
}
