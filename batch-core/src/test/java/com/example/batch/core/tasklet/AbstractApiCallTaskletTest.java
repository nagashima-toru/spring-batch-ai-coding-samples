package com.example.batch.core.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.RetryableSystemException;
import com.example.batch.core.http.BatchHttpClient;

class AbstractApiCallTaskletTest {

    private final BatchHttpClient httpClient = mock(BatchHttpClient.class);

    @Test
    void APIを呼び出しレスポンスを処理して正常終了する() throws Exception {
        var handled = new AtomicReference<String>();
        var tasklet = new AbstractApiCallTasklet<String>(httpClient) {
            @Override
            protected String callApi(BatchHttpClient client) {
                assertThat(client).isSameAs(httpClient);
                return "response-body";
            }

            @Override
            protected void handleResponse(String response, StepContribution contribution) {
                handled.set(response);
            }
        };

        RepeatStatus status = tasklet.execute(mock(StepContribution.class), null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(handled.get()).isEqualTo("response-body");
    }

    @Test
    void API呼出の例外はそのまま伝播する() {
        var tasklet = new AbstractApiCallTasklet<String>(httpClient) {
            @Override
            protected String callApi(BatchHttpClient client) {
                throw new RetryableSystemException("接続失敗");
            }

            @Override
            protected void handleResponse(String response, StepContribution contribution) {
            }
        };

        assertThatThrownBy(() -> tasklet.execute(mock(StepContribution.class), null))
                .isInstanceOf(RetryableSystemException.class);
    }
}
