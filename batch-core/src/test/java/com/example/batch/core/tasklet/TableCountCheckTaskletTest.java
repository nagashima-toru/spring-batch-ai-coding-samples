package com.example.batch.core.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.BusinessException;

class TableCountCheckTaskletTest {

    @Test
    void 件数が範囲内なら正常終了する() throws Exception {
        var tasklet = new TableCountCheckTasklet("app.customer", () -> 100, 1, 1000L);

        assertThat(tasklet.execute(null, null)).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void 上限なしの場合は最小件数のみチェックする() throws Exception {
        var tasklet = new TableCountCheckTasklet("app.customer", () -> Long.MAX_VALUE, 1, null);

        assertThat(tasklet.execute(null, null)).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void 最小件数未満の場合はBusinessExceptionをスローする() {
        var tasklet = new TableCountCheckTasklet("app.customer", () -> 0, 1, null);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下回っています");
    }

    @Test
    void 最大件数超過の場合はBusinessExceptionをスローする() {
        var tasklet = new TableCountCheckTasklet("app.customer", () -> 11, 0, 10L);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超えています");
    }
}
