package com.example.batch.core.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

class BusinessSkipPolicyTest {

    private final BusinessSkipPolicy policy = new BusinessSkipPolicy(2);

    @Test
    void 業務例外は上限までSkipする() {
        assertThat(policy.shouldSkip(new BusinessException("入力不正"), 0)).isTrue();
        assertThat(policy.shouldSkip(new BusinessException("入力不正"), 1)).isTrue();
    }

    @Test
    void パースエラーもSkip対象とする() {
        assertThat(policy.shouldSkip(new FlatFileParseException("パース失敗", "bad,line"), 0)).isTrue();
    }

    @Test
    void システム例外はSkipしない() {
        assertThat(policy.shouldSkip(new SystemException("DB断"), 0)).isFalse();
        assertThat(policy.shouldSkip(new RuntimeException("不明"), 0)).isFalse();
    }

    @Test
    void 上限到達後はSkipLimitExceededExceptionをスローする() {
        assertThatThrownBy(() -> policy.shouldSkip(new BusinessException("入力不正"), 2))
                .isInstanceOf(SkipLimitExceededException.class);
    }

    @Test
    void 負のskipLimitは指定できない() {
        assertThatThrownBy(() -> new BusinessSkipPolicy(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
