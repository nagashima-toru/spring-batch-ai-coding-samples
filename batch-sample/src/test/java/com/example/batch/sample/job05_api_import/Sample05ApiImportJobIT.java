package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.batch.sample.AbstractJobIT;
import com.example.batch.sample.common.ApiUserMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * サンプル05（API連携）の結合テスト。
 *
 * <p>JDK 組込みの {@link HttpServer} でページングAPIをスタブし、
 * HTTP Client 基盤経由の実 HTTP 通信でジョブを検証する。</p>
 */
class Sample05ApiImportJobIT extends AbstractJobIT {

    private static HttpServer apiStub;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("sample05ApiImportJob")
    private Job job;

    @Autowired
    private ApiUserMapper apiUserMapper;

    @BeforeAll
    static void startApiStub() throws IOException {
        apiStub = HttpServer.create(new InetSocketAddress(0), 0);
        apiStub.createContext("/users", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String body;
            if (!"Bearer local-dummy-token".equals(auth)) {
                body = "[]";
                send(exchange, 401, body);
                return;
            }
            // page=0: 2件（ページサイズと同数） / page=1: 1件（最終ページ）
            if (query.contains("page=0")) {
                body = """
                        [{"userId":"U001","userName":"山田","email":"u001@example.com"},
                         {"userId":"U002","userName":"鈴木","email":"u002@example.com"}]""";
            } else if (query.contains("page=1")) {
                body = "[{\"userId\":\"U003\",\"userName\":\"佐藤\",\"email\":\"u003@example.com\"}]";
            } else {
                body = "[]";
            }
            send(exchange, 200, body);
        });
        apiStub.start();
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @AfterAll
    static void stopApiStub() {
        apiStub.stop(0);
    }

    @DynamicPropertySource
    static void apiProperties(DynamicPropertyRegistry registry) {
        registry.add("sample.job05.api.base-url",
                () -> "http://localhost:" + apiStub.getAddress().getPort());
        registry.add("sample.job05.api.page-size", () -> 2);
    }

    @BeforeEach
    void setUp() {
        truncateAppTable("api_user");
    }

    @Test
    void ページングAPIから全件を取り込み正常終了する() throws Exception {
        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_name FROM app.api_user WHERE user_id = 'U003'", String.class)).isEqualTo("佐藤");
    }
}
