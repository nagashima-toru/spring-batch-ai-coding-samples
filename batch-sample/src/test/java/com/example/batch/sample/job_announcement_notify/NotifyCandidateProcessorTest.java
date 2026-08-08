package com.example.batch.sample.job_announcement_notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.example.batch.core.exception.BusinessException;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class NotifyCandidateProcessorTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final NotifyCandidateProcessor processor = new NotifyCandidateProcessor(VALIDATOR_FACTORY.getValidator());

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void 正常な候補はメール送信依頼へ未送信ステータスで変換する() throws Exception {
        NotifyCandidate candidate = new NotifyCandidate(1L, 100L, "taro@example.com", "件名", "本文");

        MailSend mailSend = processor.process(candidate);

        assertThat(mailSend).isEqualTo(new MailSend(1L, 100L, "taro@example.com", "件名", "本文", "0"));
    }

    @Test
    void メールアドレス形式が不正な場合はBusinessExceptionをスローする() {
        NotifyCandidate candidate = new NotifyCandidate(1L, 100L, "bad-mail", "件名", "本文");

        assertThatThrownBy(() -> processor.process(candidate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");
    }

    @Test
    void メールアドレスが空文字の場合はBusinessExceptionをスローする() {
        // @Email 単体は空文字を許容するため @NotBlank を併記している（コードレビューで指摘・追加）
        NotifyCandidate candidate = new NotifyCandidate(1L, 100L, "", "件名", "本文");

        assertThatThrownBy(() -> processor.process(candidate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");
    }
}
