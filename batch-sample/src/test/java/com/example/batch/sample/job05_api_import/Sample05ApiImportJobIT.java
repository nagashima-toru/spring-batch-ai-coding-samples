package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertySource;

import com.example.batch.core.exception.BusinessException;
import com.example.batch.core.exception.RetryableSystemException;
import com.example.batch.core.execution.BatchExitCodes;
import com.example.batch.sample.AbstractJobIT;
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

    /** 件数APIが返す件数（ユーザーAPIが返すのは5件）。 */
    private static volatile int reportedCount;

    /** このページ番号の時だけ、ユーザーAPIが500を返す（-1なら失敗させない）。 */
    private static volatile int errorPage;

    /** 真の間、U003 のメールアドレスを入力チェックで Skip される値にする。 */
    private static volatile boolean invalidU003;

    /** 真の間、page=2 に U006 を足して6件（チャンクサイズ3のちょうど倍数）にする。 */
    private static volatile boolean sixUsers;

    @Autowired
    private JobOperator jobOperator;

    /** 取込 Step のライター。最後のチャンクを書く時に停止要求を出すため、実物を呼ぶ spy にする。 */
    @MockitoSpyBean
    private MyBatisBatchItemWriter<ApiUser> sample05UserWriter;

    /** 真の間、U005 を含むチャンクの書込みで実行中のジョブに停止要求を出す（1回出したら偽に戻す）。 */
    private boolean stopOnLastChunk;

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
            send(exchange, 200, "{\"count\":" + reportedCount + "}");
        });
        apiStub.createContext("/users", exchange -> {
            USERS_REQUESTS.incrementAndGet();
            if (serverError) {
                send(exchange, 500, "[]");
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            int page = pageParam(query);
            if (page == errorPage) {
                send(exchange, 500, "[]");
                return;
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String body;
            if (!"Bearer local-dummy-token".equals(auth)) {
                body = "[]";
                send(exchange, 401, body);
                return;
            }
            // page=0: 2件 / page=1: 2件（ページサイズと同数） / page=2: 1件（最終ページ）
            if (page == 0) {
                body = """
                        [{"userId":"U001","userName":"山田","email":"u001@example.com"},
                         {"userId":"U002","userName":"鈴木","email":"u002@example.com"}]""";
            } else if (page == 1) {
                String u003Email = invalidU003 ? "not-an-email" : "u003@example.com";
                body = """
                        [{"userId":"U003","userName":"佐藤","email":"%s"},
                         {"userId":"U004","userName":"高橋","email":"u004@example.com"}]""".formatted(u003Email);
            } else if (page == 2 && sixUsers) {
                // page=2 もページサイズと同数（2件）になり、page=3 の空ページで終わる
                body = """
                        [{"userId":"U005","userName":"田中","email":"u005@example.com"},
                         {"userId":"U006","userName":"伊藤","email":"u006@example.com"}]""";
            } else if (page == 2) {
                body = "[{\"userId\":\"U005\",\"userName\":\"田中\",\"email\":\"u005@example.com\"}]";
            } else {
                body = "[]";
            }
            send(exchange, 200, body);
        });
        apiStub.start();
    }

    /** クエリ文字列から page パラメータの値を得る。無ければ -1。 */
    private static int pageParam(String query) {
        if (query == null) {
            return -1;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "page".equals(pair.substring(0, eq))) {
                return Integer.parseInt(pair.substring(eq + 1));
            }
        }
        return -1;
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
        // チャンクの途中でページの取得が起きるよう、チャンクサイズ（3）をページサイズ（2）と異なる値にする
        registry.add("sample.chunk-size", () -> 3);
    }

    @BeforeEach
    void setUp() {
        truncateAppTable("api_user");
        serverError = false;
        reportedCount = 5;
        errorPage = -1;
        invalidU003 = false;
        sixUsers = false;
        stopOnLastChunk = false;
        COUNT_REQUESTS.set(0);
        USERS_REQUESTS.set(0);
    }

    @Test
    void ページングAPIから全件を取り込み正常終了する() throws Exception {
        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(5);
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
        assertThat(countStep.getExecutionContext().getLong("apiUserCount")).isEqualTo(5L);
        assertThat(countStep.getEndTime().isAfter(importStep.getStartTime())).isFalse();
        assertThat(findStep(execution, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void 取込Stepが途中で失敗した場合_リスタートで続きを取り込み件数の突き合わせも通る() throws Exception {
        // 1チャンク目（U001〜U003）をコミットした後、2チャンク目でU004を読んだ後のpage=2の取得で失敗する
        errorPage = 2;

        JobExecution failed = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(findStep(failed, "sample05.01ImportStep").getWriteCount()).isEqualTo(3);

        errorPage = -1;
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // リスタートでは続きの2件（U004・U005）だけを読む。取得件数は前回から引き継いだ読込位置（累計）から求めて上書きし、
        // 5件になって件数APIの5件と一致する
        assertThat(findStep(restarted, "sample05.01ImportStep").getReadCount()).isEqualTo(2);
        assertThat(restarted.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isEqualTo(5L);
        assertThat(findStep(restarted, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(5);
    }

    @Test
    void チャンクの途中で失敗した場合_リスタート後の取得件数が水増しされず件数の突き合わせが通る() throws Exception {
        // 1チャンク目（U001〜U003）の読込中、page=1の取得で失敗する。コミット済みの件数は0
        errorPage = 1;

        JobExecution failed = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        StepExecution failedImport = findStep(failed, "sample05.01ImportStep");
        assertThat(failedImport.getWriteCount()).isZero();
        assertThat(failed.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isZero();
        // Spring Batch 6.0.5 では、失敗した実行の readCount にロールバックしたチャンクの読込件数も入る（Issue #79 の再現。
        // 観測値は2）。Spring Batch の内部の挙動なので assert はしない。取得件数が readCount に依存しないことは上の0件で確かめる

        errorPage = -1;
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(findStep(restarted, "sample05.01ImportStep").getReadCount()).isEqualTo(5);
        assertThat(restarted.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isEqualTo(5L);
        assertThat(findStep(restarted, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(5);
    }

    @Test
    void 入力チェックでSkipした件がある場合_Skipした件も取得件数に数え件数の突き合わせが通り警告終了する() throws Exception {
        invalidU003 = true;

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        assertThat(findStep(execution, "sample05.01ImportStep").getProcessSkipCount()).isEqualTo(1);
        // Skip した U003 も API から取得した件なので、取得件数は5件のまま件数APIの5件と一致する
        assertThat(execution.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isEqualTo(5L);
        assertThat(findStep(execution, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(4);
    }

    @Test
    void 件数がチャンクサイズの倍数の場合_空の最終チャンクで終わっても取得件数が正しい() throws Exception {
        sixUsers = true;
        reportedCount = 6;

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // 2チャンク（3件＋3件）をコミットした後、終端の null だけを読む空のチャンクでも取得件数が保存される
        assertThat(execution.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isEqualTo(6L);
        assertThat(findStep(execution, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(6);
    }

    @Test
    void 最後のチャンクのコミット後に停止した場合_リスタートで取得件数がコミット済みの件数のまま件数の突き合わせが通る() throws Exception {
        // チャンク2（U004・U005と終端のnull）を読み終えた後、その書込みの中で停止要求を出す。
        // チャンク2のコミット後に停止が検知され、委譲先Readerの読込位置は終端のnullを含む6になる（Issue #81の条件）。
        // 停止要求を読込中（スタブの別スレッド）に出すと、JobOperator#stop が Step スコープの Reader を閉じて
        // 読込位置が0に戻る（同じスレッドから出しても同じ）。JobOperator#stop が行う実行中の Step への停止要求
        // （StepExecution#setTerminateOnly）だけを、Step と同じスレッドの書込みの中で出す
        stopOnLastChunk = true;
        doAnswer(invocation -> {
            Chunk<?> chunk = invocation.getArgument(0);
            if (stopOnLastChunk && chunk.size() == 2) {
                stopOnLastChunk = false;
                StepSynchronizationManager.getContext().getStepExecution().setTerminateOnly();
            }
            return invocation.callRealMethod();
        }).when(sample05UserWriter).write(any());

        JobExecution stopped = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(stopped.getStatus()).isEqualTo(BatchStatus.STOPPED);
        StepExecution stoppedImport = findStep(stopped, "sample05.01ImportStep");
        assertThat(stoppedImport.getStatus()).isEqualTo(BatchStatus.STOPPED);
        assertThat(stoppedImport.getWriteCount()).isEqualTo(5);
        assertThat(stoppedImport.getExecutionContext().getInt(Sample05JobConfig.USER_READER_NAME + ".read.count"))
                .isEqualTo(6);
        assertThat(stopped.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isEqualTo(5L);

        JobExecution restarted = jobOperator.restart(stopped);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isEqualTo(5L);
        assertThat(findStep(restarted, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(apiUserMapper.count()).isEqualTo(5);
    }

    @Test
    void 件数APIの件数と取得件数が一致しない場合_件数突き合わせStepで異常終了する() throws Exception {
        reportedCount = 6; // ユーザーAPIは5件しか返さない（取りこぼし）

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("syncDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        assertThat(findStep(execution, "sample05.01ImportStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(findStep(execution, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions()).anyMatch(e -> e instanceof BusinessException);
        // 取り込んだ分は UPSERT 済み（原因を取り除いて再実行すればよい）
        assertThat(apiUserMapper.count()).isEqualTo(5);
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
        // JSON用Reader（sample05UserJsonReader）の読込位置から取得件数を求めている
        assertThat(execution.getExecutionContext().getLong(ApiUserReadCountListener.READ_COUNT_KEY)).isEqualTo(2L);
        assertThat(USERS_REQUESTS.get()).isZero();
        assertThat(COUNT_REQUESTS.get()).isZero();
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .doesNotContain("sample05.00UserCountStep")
                .contains("sample05.01ImportStep");
        // 件数突き合わせ Step は通るが、件数APIを呼んでいないため突き合わせずに正常終了する
        assertThat(findStep(execution, "sample05.02CountCheckStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
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
