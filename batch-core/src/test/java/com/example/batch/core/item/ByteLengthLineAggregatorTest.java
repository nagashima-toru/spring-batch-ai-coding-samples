package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.transform.RecordFieldExtractor;

import com.example.batch.core.exception.BusinessException;

class ByteLengthLineAggregatorTest {

    private static final Charset SJIS = Charset.forName("Shift_JIS");

    // RecordFieldExtractor がリフレクションでアクセスするため public にする
    public record Product(String code, String name, String amount) {
    }

    private ByteLengthLineAggregator<Product> aggregator(int... lengths) {
        var extractor = new RecordFieldExtractor<>(Product.class);
        extractor.setNames("code", "name", "amount");
        return new ByteLengthLineAggregator<>(extractor, SJIS, lengths);
    }

    @Test
    void 各フィールドをバイト長で半角スペース右埋めする() {
        var line = aggregator(5, 10, 6).aggregate(new Product("A01", "山田", "100"));

        assertThat(line).isEqualTo("A01  山田      100   ");
        assertThat(line.getBytes(SJIS)).hasSize(21);
    }

    @Test
    void null値は空文字としてパディングする() {
        var line = aggregator(3, 4, 3).aggregate(new Product("A", null, "1"));

        assertThat(line).isEqualTo("A      1  ");
    }

    @Test
    void バイト長を超えるフィールド値はBusinessExceptionをスローする() {
        assertThatThrownBy(() -> aggregator(2, 4, 3).aggregate(new Product("TOOLONG", "x", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超えています");
    }
}
