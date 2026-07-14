package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;

class SummaryFooterCallbackTest {

    record Sales(BigDecimal amount) {
    }

    @Test
    void 書込件数と合計値を集計してトレーラ行を出力する() throws Exception {
        var footer = new SummaryFooterCallback<Sales>(Sales::amount,
                (count, sum) -> "T,%d,%s".formatted(count, sum));

        footer.afterWrite(Chunk.of(new Sales(new BigDecimal("100")), new Sales(new BigDecimal("200"))));
        footer.afterWrite(Chunk.of(new Sales(new BigDecimal("50"))));

        var writer = new StringWriter();
        footer.writeFooter(writer);

        assertThat(writer.toString()).isEqualTo("T,3,350");
        assertThat(footer.getCount()).isEqualTo(3);
        assertThat(footer.getSum()).isEqualByComparingTo("350");
    }

    @Test
    void 合計対象なしの場合は件数のみ集計する() throws Exception {
        var footer = new SummaryFooterCallback<Sales>(null, (count, sum) -> "T," + count);

        footer.afterWrite(Chunk.of(new Sales(BigDecimal.ONE)));

        var writer = new StringWriter();
        footer.writeFooter(writer);

        assertThat(writer.toString()).isEqualTo("T,1");
    }

    @Test
    void null金額は合計から除外する() throws Exception {
        var footer = new SummaryFooterCallback<Sales>(Sales::amount,
                (count, sum) -> count + "," + sum);

        footer.afterWrite(Chunk.of(new Sales(null), new Sales(BigDecimal.TEN)));

        var writer = new StringWriter();
        footer.writeFooter(writer);

        assertThat(writer.toString()).isEqualTo("2,10");
    }
}
