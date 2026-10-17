package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.SystemException;
import com.example.batch.core.http.BatchHttpClient;

/**
 * {@link ApiUserCountTasklet} の単体テスト。
 */
class ApiUserCountTaskletTest {

    private static final String BASE_URL = "http://localhost:8080";

    private final BatchHttpClient httpClient = mock(BatchHttpClient.class);

    private final StepExecution stepExecution = new StepExecution(1L, "sample05.00UserCountStep",
            new JobExecution(1L, new JobInstance(1L, "sample05ApiImportJob"), new JobParameters()));

    @Test
    void 件数APIが件数を返した場合_ステップのExecutionContextにapiUserCountを保存する() throws Exception {
        when(httpClient.get(eq(BASE_URL + "/users/count"), eq(ApiUserCountResponse.class), any(Map.class)))
                .thenReturn(new ApiUserCountResponse(3));
        ApiUserCountTasklet tasklet = new ApiUserCountTasklet(httpClient, BASE_URL, "tok");

        RepeatStatus result = tasklet.execute(new StepContribution(stepExecution), null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        assertThat(stepExecution.getExecutionContext().getLong("apiUserCount")).isEqualTo(3L);
    }

    @Test
    void 件数APIのレスポンスが空の場合_SystemExceptionをスローする() {
        when(httpClient.get(eq(BASE_URL + "/users/count"), eq(ApiUserCountResponse.class), any(Map.class)))
                .thenReturn(null);
        ApiUserCountTasklet tasklet = new ApiUserCountTasklet(httpClient, BASE_URL, "tok");

        assertThatThrownBy(() -> tasklet.execute(new StepContribution(stepExecution), null))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("件数APIのレスポンスが空です");
    }
}
