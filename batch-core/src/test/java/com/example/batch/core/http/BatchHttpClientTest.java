package com.example.batch.core.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.batch.core.exception.RetryableSystemException;
import com.example.batch.core.exception.SystemException;

class BatchHttpClientTest {

    private static final HttpClientProperties FAST_RETRY = new HttpClientProperties(
            Duration.ofSeconds(1), Duration.ofSeconds(1),
            new HttpClientProperties.Retry(3, Duration.ofMillis(1), 2.0, Duration.ofMillis(5)));

    public record User(String name) {
    }

    private BatchHttpClient client(RestClient.Builder builder) {
        // MockRestServiceServer のリクエストファクトリを上書きしないテスト用コンストラクタを使用
        return new BatchHttpClient(builder, FAST_RETRY, false);
    }

    @Test
    void 正常応答はボディを型変換して返す() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/users/1")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"name\":\"山田\"}", MediaType.APPLICATION_JSON));
        var client = client(builder);

        User user = client.get("/users/{id}", User.class, Map.of("Authorization", "Bearer token"), 1);

        assertThat(user.name()).isEqualTo("山田");
        server.verify();
    }

    @Test
    void クライアントエラーはリトライせずSystemExceptionへ変換する() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/users")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        var client = client(builder);

        assertThatThrownBy(() -> client.get("/users", User.class, Map.of()))
                .isInstanceOf(SystemException.class)
                .isNotInstanceOf(RetryableSystemException.class)
                .hasMessageContaining("404");
        server.verify();
    }

    @Test
    void サーバエラーは指数バックオフでリトライし枯渇後に例外をスローする() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // maxAttempts=3 のため 3 回呼ばれるはず
        server.expect(org.springframework.test.web.client.ExpectedCount.times(3), requestTo("/unstable"))
                .andRespond(withServerError());
        var client = client(builder);

        assertThatThrownBy(() -> client.get("/unstable", User.class, Map.of()))
                .isInstanceOf(RetryableSystemException.class)
                .hasMessageContaining("500");
        server.verify();
    }

    @Test
    void リトライ途中で成功すれば結果を返す() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/flaky")).andRespond(withServerError());
        server.expect(requestTo("/flaky"))
                .andRespond(withSuccess("{\"name\":\"回復\"}", MediaType.APPLICATION_JSON));
        var client = client(builder);

        User user = client.get("/flaky", User.class, Map.of());

        assertThat(user.name()).isEqualTo("回復");
        server.verify();
    }

    @Test
    void POSTはボディを送信できる() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/users")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"name\":\"登録済\"}", MediaType.APPLICATION_JSON));
        var client = client(builder);

        User created = client.post("/users", new User("新規"), User.class, Map.of());

        assertThat(created.name()).isEqualTo("登録済");
        server.verify();
    }

    @Test
    void バックオフ合計時間を見積もれる() {
        var retry = new HttpClientProperties.Retry(3, Duration.ofMillis(100), 2.0, Duration.ofMillis(150));
        // 1回目: 100ms, 2回目: min(200,150)=150ms
        assertThat(BatchHttpClient.estimateTotalBackoff(retry)).isEqualTo(Duration.ofMillis(250));
    }
}
