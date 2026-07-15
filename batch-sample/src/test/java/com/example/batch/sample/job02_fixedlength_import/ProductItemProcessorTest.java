package com.example.batch.sample.job02_fixedlength_import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.example.batch.core.exception.BusinessException;
import com.example.batch.sample.common.Product;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class ProductItemProcessorTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final ProductItemProcessor processor = new ProductItemProcessor(VALIDATOR_FACTORY.getValidator());

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void ゼロ埋め数字文字列の単価をBigDecimalへ変換する() throws Exception {
        Product product = processor.process(new ProductFixedRecord("P0000001", "りんご", "00001000"));

        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    void 単価が数字以外の場合はBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(new ProductFixedRecord("P0000001", "りんご", "12A4")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数字");
    }
}
