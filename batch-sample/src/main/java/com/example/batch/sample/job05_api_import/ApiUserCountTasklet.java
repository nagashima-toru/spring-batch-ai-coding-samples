package com.example.batch.sample.job05_api_import;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.StepContribution;

import com.example.batch.core.exception.SystemException;
import com.example.batch.core.http.BatchHttpClient;
import com.example.batch.core.tasklet.AbstractApiCallTasklet;

/**
 * 取込前にユーザー件数を取得する Tasklet。
 *
 * <p>件数 API を1回呼び出し、件数を Step の ExecutionContext に {@code apiUserCount} として保存する。
 * HTTP の例外変換・リトライは {@link BatchHttpClient} が行うため、ここでは捕捉しない。</p>
 */
public class ApiUserCountTasklet extends AbstractApiCallTasklet<ApiUserCountResponse> {

    /** ExecutionContext に件数を保存するキー。 */
    public static final String COUNT_KEY = "apiUserCount";

    private static final Logger log = LoggerFactory.getLogger(ApiUserCountTasklet.class);

    private final String baseUrl;

    private final String token;

    /**
     * コンストラクタ。
     *
     * @param httpClient HTTP Client 基盤
     * @param baseUrl    API のベース URL
     * @param token      認証トークン
     */
    public ApiUserCountTasklet(BatchHttpClient httpClient, String baseUrl, String token) {
        super(httpClient);
        this.baseUrl = baseUrl;
        this.token = token;
    }

    @Override
    protected ApiUserCountResponse callApi(BatchHttpClient client) {
        return client.get(baseUrl + "/users/count", ApiUserCountResponse.class,
                Map.of("Authorization", "Bearer " + token));
    }

    @Override
    protected void handleResponse(ApiUserCountResponse response, StepContribution contribution) {
        if (response == null) {
            throw new SystemException("件数APIのレスポンスが空です");
        }
        log.info("API側のユーザー件数を取得しました: count={}", response.count());
        contribution.getStepExecution().getExecutionContext().putLong(COUNT_KEY, response.count());
    }
}
