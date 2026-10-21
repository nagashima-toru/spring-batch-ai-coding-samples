package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.example.batch.core.exception.BusinessException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

class ValidatingItemProcessorBaseTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    record CustomerInput(@NotBlank String code, @Max(150) int age) {
    }

    record Customer(String code, int age) {
    }

    private ValidatingItemProcessorBase<CustomerInput, Customer> processor() {
        return new ValidatingItemProcessorBase<>(validator) {
            @Override
            protected Customer doProcess(CustomerInput item) {
                if ("SKIP".equals(item.code())) {
                    return null;
                }
                return new Customer(item.code(), item.age());
            }
        };
    }

    @Test
    void 形式チェック通過後にdoProcessで変換する() throws Exception {
        Customer result = processor().process(new CustomerInput("C001", 30));

        assertThat(result).isEqualTo(new Customer("C001", 30));
    }

    @Test
    void 形式チェック違反はBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor().process(new CustomerInput("", 200)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入力チェックエラー")
                .hasMessageContaining("code")
                .hasMessageContaining("age");
    }

    @Test
    void doProcessがnullを返すとレコードはフィルタされる() throws Exception {
        assertThat(processor().process(new CustomerInput("SKIP", 1))).isNull();
    }

    @Test
    void processメソッドの修飾子＿finalである() throws NoSuchMethodException {
        assertThat(Modifier.isFinal(ValidatingItemProcessorBase.class.getMethod("process", Object.class)
                .getModifiers())).isTrue();
    }
}
