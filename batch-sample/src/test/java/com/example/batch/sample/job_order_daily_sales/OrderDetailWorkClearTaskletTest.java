package com.example.batch.sample.job_order_daily_sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * {@link OrderDetailWorkClearTasklet} の単体テスト。
 *
 * <p>詳細設計書 ORDER-001 §7「単体テスト」の業務 Tasklet の観点に基づく。</p>
 */
class OrderDetailWorkClearTaskletTest {

    @TempDir
    Path tempDir;

    private final OrderDetailWorkMapper mapper = mock(OrderDetailWorkMapper.class);

    @Test
    void 実行した場合＿truncateを1回だけ呼びFINISHEDで終了する() throws Exception {
        OrderDetailWorkClearTasklet tasklet = new OrderDetailWorkClearTasklet(tempDir.resolve("error.txt"), mapper);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(mapper, times(1)).truncate();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void Mapperが例外をスローした場合＿包み直さずそのまま送出する() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("接続断");
        doThrow(failure).when(mapper).truncate();
        OrderDetailWorkClearTasklet tasklet = new OrderDetailWorkClearTasklet(tempDir.resolve("error.txt"), mapper);

        assertThatThrownBy(() -> tasklet.execute(null, null)).isSameAs(failure);
    }

    @Test
    void 実行した場合＿初期化したことをINFOログに出力する() throws Exception {
        Path errorFile = tempDir.resolve("error.txt");
        Logger logger = (Logger) LoggerFactory.getLogger(OrderDetailWorkClearTasklet.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new OrderDetailWorkClearTasklet(errorFile, mapper).execute(null, null);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("受注明細ワークを初期化しました: errorFile=" + errorFile + " 削除=false");
    }
}
