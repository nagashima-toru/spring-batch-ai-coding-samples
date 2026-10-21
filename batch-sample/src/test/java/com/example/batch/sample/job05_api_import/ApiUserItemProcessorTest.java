package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.example.batch.core.exception.BusinessException;
import com.example.batch.sample.common.ApiUser;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/**
 * {@link ApiUserItemProcessor} の単体テスト（入力チェックと変換）。
 */
class ApiUserItemProcessorTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final ApiUserItemProcessor processor = new ApiUserItemProcessor(VALIDATOR_FACTORY.getValidator());

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void 正常なレスポンスの場合＿同じ値のApiUserへ変換する() throws Exception {
        ApiUser result = processor.process(new ApiUserResponse("U001", "山田太郎", "taro@example.com"));

        assertThat(result).isEqualTo(new ApiUser("U001", "山田太郎", "taro@example.com"));
    }

    @Test
    void userIdが空白の場合＿userIdを含むメッセージのBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(new ApiUserResponse(" ", "山田太郎", "taro@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void userIdが20文字の場合＿検証を通り変換する() throws Exception {
        String userId = "U".repeat(20);

        ApiUser result = processor.process(new ApiUserResponse(userId, "山田太郎", "taro@example.com"));

        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    void userIdが21文字の場合＿userIdを含むメッセージのBusinessExceptionをスローする() {
        ApiUserResponse item = new ApiUserResponse("U".repeat(21), "山田太郎", "taro@example.com");

        assertThatThrownBy(() -> processor.process(item))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void emailがメールアドレスの形でない場合＿emailを含むメッセージのBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(new ApiUserResponse("U001", "山田太郎", "not-an-email")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");
    }

    @Test
    void userNameがnullの場合＿userNameを含むメッセージのBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(new ApiUserResponse("U001", null, "taro@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userName");
    }
}
