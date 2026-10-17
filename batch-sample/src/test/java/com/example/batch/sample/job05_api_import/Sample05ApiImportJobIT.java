package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.batch.core.exception.RetryableSystemException;
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

    /** 件数APIへのリクエスト数。 */
    private static final AtomicInteger COUNT_REQUESTS = new AtomicInteger();

    /** ユーザーAPIへのリクエスト数。 */
    private static final AtomicInteger USERS_REQUESTS = new AtomicInteger();

    /** 真の間、スタブは500を返す。 */
    private static volatile boolean serverError;

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
        apiStub.createContext("/users/count", exchange -> {
            COUNT_REQUESTS.incrementAndGet();
            if (serverError) {
                send(exchange, 500, "{}");
                return;
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!"Bearer local-dummy-token".equals(auth)) {
                send(exchange, 401, "{}");
                return;
            }
            send(exchange, 200, "{\"count\":3}");
        });
        apiStub.createContext("/users", exchange -> {
            USERS_REQUESTS.incrementAndGet();
            if (serverError) {
                send(exchange, 500, "[]");
                return;
            }
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
        serverError = false;
        COUNT_REQUESTS.set(0);
        USERS_REQUESTS.set(0);
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

    @Test
    void 件数取得Stepが成功した場合_取込Stepより先に実行され件数をExecutionContextに保存する() throws Exception {
        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution countStep = findStep(execution, "sample05.00UserCountStep");
        StepExecution importStep = findStep(execution, "sample05.01ImportStep");
        assertThat(countStep.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(importStep.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(countStep.getExecutionContext().getLong("apiUserCount")).isEqualTo(3L);
        assertThat(countStep.getEndTime().isAfter(importStep.getStartTime())).isFalse();
    }

    @Test
    void APIが5xxを返し続ける場合_BatchHttpClientのリトライ後にRetryableSystemExceptionで異常終了する()
            throws Exception {
        serverError = true;

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        assertThat(execution.getAllFailureExceptions()).anyMatch(Sample05ApiImportJobIT::hasRetryableCause);
        assertThat(COUNT_REQUESTS.get()).isEqualTo(3);
        assertThat(apiUserMapper.count()).isZero();
    }

    @Test
    void inputFileを指定した場合_APIを呼ばずJSONファイルから取り込む(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("users.json");
        Files.writeString(file, """
                [{"userId":"R001","userName":"再生一","email":"r001@example.com"},
                 {"userId":"R002","userName":"再生二","email":"r002@example.com"}]""",
                StandardCharsets.UTF_8);

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .addString("inputFile", file.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(2);
        assertThat(USERS_REQUESTS.get()).isZero();
        assertThat(COUNT_REQUESTS.get()).isZero();
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .doesNotContain("sample05.00UserCountStep")
                .contains("sample05.01ImportStep");
    }

    @Test
    void inputFileに存在しないファイルを指定した場合_異常終了する(@TempDir Path tempDir) throws Exception {
        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .addString("inputFile", tempDir.resolve("missing.json").toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    private static StepExecution findStep(JobExecution execution, String name) {
        return execution.getStepExecutions().stream()
                .filter(s -> name.equals(s.getStepName()))
                .findFirst()
                .orElseThrow();
    }

    private static boolean hasRetryableCause(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof RetryableSystemException) {
                return true;
            }
        }
        return false;
    }
}
