package com.example.batch.sample.job01_csv_import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.example.batch.core.exception.BusinessException;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class CustomerItemProcessorTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final CustomerItemProcessor processor = new CustomerItemProcessor(VALIDATOR_FACTORY.getValidator());

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void 正常レコードは顧客コードを大文字化してエンティティへ変換する() throws Exception {
        Customer customer = processor.process(new CustomerCsvRecord("c001", "山田太郎", "taro@example.com"));

        assertThat(customer).isEqualTo(new Customer("C001", "山田太郎", "taro@example.com"));
    }

    @Test
    void 顧客コードが空の場合はBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(new CustomerCsvRecord("", "山田太郎", "taro@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("customerCode");
    }

    @Test
    void メールアドレス形式が不正な場合はBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(new CustomerCsvRecord("C001", "山田太郎", "bad-mail")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");
    }

    @Test
    void 顧客コードに記号を含む場合はBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(new CustomerCsvRecord("C-01!", "山田太郎", "a@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("英数字");
    }
}
