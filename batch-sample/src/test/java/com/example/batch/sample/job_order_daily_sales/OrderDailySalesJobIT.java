package com.example.batch.sample.job_order_daily_sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.example.batch.core.execution.BatchExitCodes;
import com.example.batch.sample.AbstractJobIT;

/**
 * ORDER-001（受注CSV取込・日次売上集計）の結合テスト。
 *
 * <p>詳細設計書 docs/projects/order-daily-sales/detail-design/ORDER-001.md の §3 エラー処理設計・
 * §5 リラン設計・§7「結合テスト」の観点に基づく。ジョブパラメータの Validator が未知のキーを拒否するため、
 * {@code uniqueParameters()} は使わず、テストごとに異なる {@code scheduledTime} で JobInstance の重複を避ける。</p>
 */
class OrderDailySalesJobIT extends AbstractJobIT {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 11);
    private static final String HEADER = "order_no,line_no,order_date,store_code,item_code,quantity,unit_price";
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2026, 10, 11, 17, 0, 0, 0, ZoneOffset.UTC);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    @Qualifier("orderDailySalesJob")
    private Job job;

    @Autowired
    private ApplicationContext applicationContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        truncateAppTable("order_detail_work");
        truncateAppTable("order_detail");
        truncateAppTable("daily_store_sales");
    }

    // ---------------------------------------------------------------- ヘルパー

    /** テストごとに異なる scheduledTime（JobInstance を重複させない）。 */
    private static String nextScheduledTime() {
        return BASE_TIME.plusSeconds(SEQUENCE.incrementAndGet()).toString();
    }

    private JobParameters parameters(String scheduledTime, Path inputFile, Path errorFile) {
        return parameters(scheduledTime, TARGET_DATE, inputFile, errorFile);
    }

    private JobParameters parameters(String scheduledTime, LocalDate targetDate, Path inputFile, Path errorFile) {
        return new JobParametersBuilder()
                .addString("scheduledTime", scheduledTime)
                .addLocalDate("targetDate", targetDate)
                .addString("inputFile", inputFile.toString())
                .addString("errorFile", errorFile.toString())
                .toJobParameters();
    }

    private JobParameters parameters(Path inputFile, Path errorFile) {
        return parameters(nextScheduledTime(), inputFile, errorFile);
    }

    private Path writeCsv(String fileName, String header, List<String> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        lines.addAll(rows);
        Path file = tempDir.resolve(fileName);
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private Path writeCsv(String fileName, List<String> rows) throws IOException {
        return writeCsv(fileName, HEADER, rows);
    }

    private List<String> errorLines(Path errorFile) throws IOException {
        return Files.exists(errorFile) ? Files.readAllLines(errorFile, StandardCharsets.UTF_8) : List.of();
    }

    private List<String> detailRows() {
        return jdbcTemplate.queryForList(
                "SELECT order_no || '|' || line_no || '|' || order_date || '|' || store_code || '|' || item_code"
                        + " || '|' || quantity || '|' || unit_price || '|' || amount"
                        + " FROM app.order_detail ORDER BY order_no, line_no",
                String.class);
    }

    private List<String> salesRows() {
        return jdbcTemplate.queryForList(
                "SELECT sales_date || '|' || store_code || '|' || order_count || '|' || line_count"
                        + " || '|' || total_quantity || '|' || total_amount"
                        + " FROM app.daily_store_sales ORDER BY sales_date, store_code",
                String.class);
    }

    /**
     * 指定テーブルへの INSERT で条件に合う行だけ失敗させるトリガを一時的に足してジョブを起動する。
     * トリガ・関数は finally で必ず消す（他のテストに影響させない）。
     */
    private JobExecution startWithFailTrigger(String table, String condition, JobParameters params)
            throws Exception {
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION app.it_fail_on_line() RETURNS trigger AS $$ "
                + "BEGIN IF " + condition + " THEN RAISE EXCEPTION 'IT による意図的な失敗'; END IF; RETURN NEW; END; "
                + "$$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE TRIGGER it_fail_trigger BEFORE INSERT ON app." + table + " "
                + "FOR EACH ROW EXECUTE FUNCTION app.it_fail_on_line()");
        try {
            return jobOperator.start(job, params);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS it_fail_trigger ON app." + table);
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS app.it_fail_on_line()");
        }
    }

    /** 実行回の Step 4（取込 Step）の読込件数。 */
    private static long importStepReadCount(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(s -> s.getStepName().contains("04ImportStep"))
                .mapToLong(StepExecution::getReadCount)
                .sum();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app." + table, Long.class);
    }

    private void insertDetail(String orderNo, int lineNo, String orderDate, String storeCode, int quantity,
            int unitPrice) {
        jdbcTemplate.update(
                "INSERT INTO app.order_detail (order_no, line_no, order_date, store_code, item_code, quantity,"
                        + " unit_price, amount) VALUES (?, ?, ?::date, ?, 'OLD', ?, ?, ?)",
                orderNo, lineNo, orderDate, storeCode, quantity, unitPrice, (long) quantity * unitPrice);
    }

    private void insertSales(String salesDate, String storeCode, int orders, int lines, long qty, long amount) {
        jdbcTemplate.update(
                "INSERT INTO app.daily_store_sales (sales_date, store_code, order_count, line_count,"
                        + " total_quantity, total_amount) VALUES (?::date, ?, ?, ?, ?, ?)",
                salesDate, storeCode, orders, lines, qty, amount);
    }

    /** 対象日（2026-10-11）に旧データを入れ、他の日（2026-10-10）のデータも入れる。 */
    private void insertOldData() {
        insertDetail("OLD1", 1, "2026-10-11", "S09", 1, 100);
        insertSales("2026-10-11", "S09", 1, 1, 1, 100);
        insertDetail("PREV", 1, "2026-10-10", "S08", 2, 50);
        insertSales("2026-10-10", "S08", 1, 1, 2, 100);
    }

    private static final List<String> OLD_DETAIL = List.of(
            "OLD1|1|2026-10-11|S09|OLD|1|100|100", "PREV|1|2026-10-10|S08|OLD|2|50|100");
    private static final List<String> OLD_SALES = List.of(
            "2026-10-10|S08|1|1|2|100", "2026-10-11|S09|1|1|1|100");

    // ---------------------------------------------------------------- テスト

    @Test
    void 複数店舗複数受注のファイルを取り込むと明細と集計が登録され終了コードCOMPLETEDでエラーファイルは作られない()
            throws Exception {
        Path input = writeCsv("orders.csv", List.of(
                "A001,1,2026-10-11,S01,I01,2,100",
                "A001,2,2026-10-11,S01,I02,1,500",
                "A002,1,2026-10-11,S01,I01,3,100",
                "B001,1,2026-10-11,S02,I03,5,1000",
                "B001,2,2026-10-11,S02,I01,1,0"));
        Path error = tempDir.resolve("error.txt");

        JobExecution execution = jobOperator.start(job, parameters(input, error));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(detailRows()).containsExactly(
                "A001|1|2026-10-11|S01|I01|2|100|200",
                "A001|2|2026-10-11|S01|I02|1|500|500",
                "A002|1|2026-10-11|S01|I01|3|100|300",
                "B001|1|2026-10-11|S02|I03|5|1000|5000",
                "B001|2|2026-10-11|S02|I01|1|0|0");
        // 受注件数は受注番号の重複を除く（S01 は A001・A002 の2件、明細は3行）
        assertThat(salesRows()).containsExactly(
                "2026-10-11|S01|2|3|6|1000",
                "2026-10-11|S02|1|2|6|5000");
        assertThat(Files.exists(error)).isFalse();
    }

    @Test
    void 正常に実行した場合＿実行時パラメータ確定のログは1回だけ出力される() throws Exception {
        Path input = writeCsv("orders.csv", List.of(
                "A001,1,2026-10-11,S01,I01,2,100",
                "B001,1,2026-10-11,S02,I03,5,1000"));
        Path error = tempDir.resolve("error.txt");

        Logger logger = (Logger) LoggerFactory.getLogger(OrderDailySalesJobConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        JobExecution execution;
        try {
            execution = jobOperator.start(job, parameters(input, error));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // 入力ファイルのパスは @TempDir 配下でテストごとに一意なので、このテストの実行分だけを数える
        assertThat(appender.list.stream()
                .filter(e -> e.getMessage().startsWith("実行時パラメータを確定しました: "))
                .filter(e -> e.getFormattedMessage().contains(input.toString()))
                .toList()).hasSize(1);
    }

    @Test
    void 不正行を含むファイルでは正常行だけ取り込みエラーファイルに退避して終了コードCOMPLETED_WITH_WARNINGになる()
            throws Exception {
        Path input = writeCsv("orders.csv", List.of(
                "A001,1,2026-10-11,S01,I01,2,100",       // line 2 正常
                "A003,1,2026-10-10,S01,I01,1,100",       // line 3 対象日不一致
                "A004,1,2026-10-11,S01,I01,0,100",       // line 4 数量0
                "bad,line",                              // line 5 列数不一致
                "A001,1,2026-10-11,S01,I01,9,9",         // line 6 ファイル内の重複
                "A005,1,2026-10-11,S01,I02,1,300"));     // line 7 正常
        Path error = tempDir.resolve("error.txt");

        JobExecution execution = jobOperator.start(job, parameters(input, error));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        assertThat(detailRows()).containsExactly(
                "A001|1|2026-10-11|S01|I01|2|100|200",
                "A005|1|2026-10-11|S01|I02|1|300|300");
        assertThat(salesRows()).containsExactly("2026-10-11|S01|2|2|3|500");
        List<String> errors = errorLines(error);
        assertThat(errors).hasSize(4);
        // 読取時の Skip は即時、加工時の Skip はチャンク終了時に書かれるため、出力順ではなく内容で確かめる
        assertThat(errors).anySatisfy(l -> assertThat(l).startsWith("line=3,A003,1,2026-10-10,S01,I01,1,100"));
        assertThat(errors).anySatisfy(l -> assertThat(l).startsWith("line=4,A004,1,2026-10-11,S01,I01,0,100"));
        assertThat(errors).anySatisfy(l -> assertThat(l).startsWith("bad,line"));
        assertThat(errors).anySatisfy(l -> assertThat(l).startsWith("line=6,A001,1,2026-10-11,S01,I01,9,9"));
    }

    @Test
    void 同じtargetDateを別のscheduledTimeで2回実行すると1回目の内容が2回目に置き換わり他の日のデータは変わらない()
            throws Exception {
        insertDetail("PREV", 1, "2026-10-10", "S08", 2, 50);
        insertSales("2026-10-10", "S08", 1, 1, 2, 100);
        Path first = writeCsv("first.csv", List.of(
                "A001,1,2026-10-11,S01,I01,2,100",
                "A002,1,2026-10-11,S02,I01,3,100"));
        Path second = writeCsv("second.csv", List.of(
                "A001,1,2026-10-11,S01,I09,5,200",
                "C001,1,2026-10-11,S03,I01,1,1000"));

        JobExecution firstExecution = jobOperator.start(job, parameters(first, tempDir.resolve("e1.txt")));
        assertThat(firstExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(salesRows()).containsExactly(
                "2026-10-10|S08|1|1|2|100", "2026-10-11|S01|1|1|2|200", "2026-10-11|S02|1|1|3|300");

        JobExecution secondExecution = jobOperator.start(job, parameters(second, tempDir.resolve("e2.txt")));

        assertThat(secondExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(detailRows()).containsExactly(
                "A001|1|2026-10-11|S01|I09|5|200|1000",
                "C001|1|2026-10-11|S03|I01|1|1000|1000",
                "PREV|1|2026-10-10|S08|OLD|2|50|100");
        assertThat(salesRows()).containsExactly(
                "2026-10-10|S08|1|1|2|100",
                "2026-10-11|S01|1|1|5|1000",
                "2026-10-11|S03|1|1|1|1000");
    }

    @Test
    void 入力ファイルが存在しない場合は終了コードFAILEDで本テーブルは変わらない() throws Exception {
        insertOldData();
        Path missing = tempDir.resolve("not-found.csv");

        JobExecution execution = jobOperator.start(job, parameters(missing, tempDir.resolve("error.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("FAILED");
        assertThat(detailRows()).containsExactlyElementsOf(OLD_DETAIL);
        assertThat(salesRows()).containsExactlyElementsOf(OLD_SALES);
    }

    @Test
    void ヘッダ行が不正な場合は終了コードFAILEDで本テーブルは変わらない() throws Exception {
        insertOldData();
        Path input = writeCsv("orders.csv",
                "order_no,line_no,order_date,store_code,item_code,unit_price,quantity",
                List.of("A001,1,2026-10-11,S01,I01,2,100"));

        JobExecution execution = jobOperator.start(job, parameters(input, tempDir.resolve("error.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("FAILED");
        assertThat(detailRows()).containsExactlyElementsOf(OLD_DETAIL);
        assertThat(salesRows()).containsExactlyElementsOf(OLD_SALES);
        assertThat(count("order_detail_work")).isZero();
    }

    @Test
    void Skip件数がskipLimitを超過した場合は終了コードFAILEDで本テーブルは変わらず旧データが残る() throws Exception {
        insertOldData();
        List<String> rows = new ArrayList<>();
        rows.add("A001,1,2026-10-11,S01,I01,2,100");
        // 数量0の不正行を skipLimit（既定10）を超える11件並べる
        for (int i = 1; i <= 11; i++) {
            rows.add("X001," + i + ",2026-10-11,S01,I01,0,100");
        }
        Path input = writeCsv("orders.csv", rows);
        Path error = tempDir.resolve("error.txt");

        JobExecution execution = jobOperator.start(job, parameters(input, error));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("FAILED");
        assertThat(detailRows()).containsExactlyElementsOf(OLD_DETAIL);
        assertThat(salesRows()).containsExactlyElementsOf(OLD_SALES);
        // 超過までの Skip 行はエラーファイルに残る
        assertThat(errorLines(error)).isNotEmpty();
    }

    @Test
    void 別の日に同じ受注番号と明細番号が登録済みでStep5が一意制約違反になると終了コードFAILEDで本テーブルは全体ロールバックされる()
            throws Exception {
        insertOldData();
        // 別の日（2026-10-10）に同じ order_no＋line_no を登録済み
        insertDetail("A001", 1, "2026-10-10", "S08", 1, 10);
        List<String> detailBefore = detailRows();
        List<String> salesBefore = salesRows();
        Path input = writeCsv("orders.csv", List.of(
                "A000,1,2026-10-11,S01,I01,1,100",
                "A001,1,2026-10-11,S01,I01,2,100"));

        JobExecution execution = jobOperator.start(job, parameters(input, tempDir.resolve("error.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("FAILED");
        // 対象日の旧データの削除も含めて全体がロールバックされている
        assertThat(detailRows()).containsExactlyElementsOf(detailBefore);
        assertThat(salesRows()).containsExactlyElementsOf(salesBefore);
        assertThat(detailRows()).contains("OLD1|1|2026-10-11|S09|OLD|1|100|100");
        // ワークは残る（Step 5 をやり直せるように）
        assertThat(count("order_detail_work")).isEqualTo(2);
    }

    @Test
    void Step4の途中でDB例外により失敗しても同一パラメータでリスタートすると最初から取り込み直し1回で成功した場合と同じ結果になる()
            throws Exception {
        // 250行（チャンクサイズ100で3チャンク）＋ 5行目と重複する最終行（ファイル内の重複）
        List<String> rows = new ArrayList<>();
        for (int i = 1; i <= 250; i++) {
            rows.add("R001," + i + ",2026-10-11,S01,I01,1,10");
        }
        rows.add("R001,5,2026-10-11,S01,I01,1,10"); // ファイル252行目: ファイル内の重複
        Path input = writeCsv("orders.csv", rows);
        Path error = tempDir.resolve("error.txt");
        JobParameters params = parameters(input, error);

        // 2チャンク目の line_no=120 の INSERT だけ失敗させる
        JobExecution failed = startWithFailTrigger("order_detail_work", "NEW.line_no = 120", params);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failed.getExitStatus().getExitCode()).isEqualTo("FAILED");
        // コミット済みの1チャンク目（100行）だけがワークに残り、本テーブルは不変
        assertThat(count("order_detail_work")).isEqualTo(100);
        assertThat(count("order_detail")).isZero();
        assertThat(count("daily_store_sales")).isZero();

        // 前の実行回のエラーファイルが残っていても、Step 3 で削除されて重複しないことを確かめる
        Files.writeString(error, "stale,line" + System.lineSeparator(), StandardCharsets.UTF_8);

        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // 重複1件の Skip（1回で成功した場合と同じ）
        assertThat(restarted.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        // Step 4 はファイルの先頭から読み直した（入力の全データ行数）
        assertThat(importStepReadCount(restarted)).isEqualTo(rows.size());
        // 1回で成功した場合と同じ結果（250行・重複行は取り込まれない）
        assertThat(count("order_detail")).isEqualTo(250);
        assertThat(count("order_detail_work")).isEqualTo(250);
        assertThat(salesRows()).containsExactly("2026-10-11|S01|1|250|250|2500");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM app.order_detail WHERE order_no = 'R001' AND line_no = 5", Integer.class))
                .isEqualTo(1);
        List<String> errors = errorLines(error);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("line=252,R001,5,2026-10-11,S01,I01,1,10");
    }

    @Test
    void 実行AをStep4で失敗させ別の対象日の実行Bを完了させてからAをリスタートすると両日の明細と集計がそれぞれの入力どおりになる()
            throws Exception {
        List<String> rowsA = new ArrayList<>();
        for (int i = 1; i <= 250; i++) {
            rowsA.add("A001," + i + ",2026-10-11,S01,I01,1,10");
        }
        Path inputA = writeCsv("orders-a.csv", rowsA);
        Path inputB = writeCsv("orders-b.csv", List.of(
                "B001,1,2026-10-12,S02,I02,2,300",
                "B002,1,2026-10-12,S02,I02,1,50"));
        JobParameters paramsA = parameters(nextScheduledTime(), TARGET_DATE, inputA, tempDir.resolve("error-a.txt"));
        JobParameters paramsB = parameters(nextScheduledTime(), TARGET_DATE.plusDays(1), inputB,
                tempDir.resolve("error-b.txt"));

        // A の行（order_no = A001）の2チャンク目だけを失敗させる
        JobExecution failedA = startWithFailTrigger("order_detail_work",
                "NEW.order_no = 'A001' AND NEW.line_no = 120", paramsA);
        assertThat(failedA.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(count("order_detail_work")).isEqualTo(100);

        JobExecution executionB = jobOperator.start(job, paramsB);
        assertThat(executionB.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        List<String> detailAfterB = detailRows();
        List<String> salesAfterB = salesRows();
        assertThat(detailAfterB).containsExactly(
                "B001|1|2026-10-12|S02|I02|2|300|600",
                "B002|1|2026-10-12|S02|I02|1|50|50");
        assertThat(salesAfterB).containsExactly("2026-10-12|S02|2|2|3|650");

        JobExecution restartedA = jobOperator.restart(failedA);

        assertThat(restartedA.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restartedA.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(importStepReadCount(restartedA)).isEqualTo(rowsA.size());
        // 10/11 は A の入力どおり、10/12 は B の完了直後のまま
        List<String> detailAfterRestart = detailRows();
        assertThat(detailAfterRestart.stream().filter(r -> r.contains("|2026-10-11|")).toList()).hasSize(250);
        assertThat(detailAfterRestart.stream().filter(r -> r.contains("|2026-10-12|")).toList())
                .containsExactlyElementsOf(detailAfterB);
        assertThat(salesRows()).containsExactly(
                "2026-10-11|S01|1|250|250|2500",
                salesAfterB.get(0));
    }

    @Test
    void 実行AをStep5で失敗させ別の対象日の実行Bを完了させてからAをリスタートすると対象日の明細が0件にならない()
            throws Exception {
        Path inputA = writeCsv("orders-a.csv", List.of(
                "A001,1,2026-10-11,S01,I01,2,100",
                "A001,2,2026-10-11,S01,I02,1,500",
                "A002,1,2026-10-11,S03,I01,3,100"));
        Path inputB = writeCsv("orders-b.csv", List.of(
                "B001,1,2026-10-12,S02,I02,2,300",
                "B002,1,2026-10-12,S02,I02,1,50"));
        JobParameters paramsA = parameters(nextScheduledTime(), TARGET_DATE, inputA, tempDir.resolve("error-a.txt"));
        JobParameters paramsB = parameters(nextScheduledTime(), TARGET_DATE.plusDays(1), inputB,
                tempDir.resolve("error-b.txt"));

        // app.order_detail への INSERT（Step 5）を失敗させる
        JobExecution failedA = startWithFailTrigger("order_detail", "TRUE", paramsA);
        assertThat(failedA.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(count("order_detail")).isZero();
        assertThat(count("order_detail_work")).isEqualTo(3);

        JobExecution executionB = jobOperator.start(job, paramsB);
        assertThat(executionB.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        List<String> detailAfterB = detailRows();
        List<String> salesAfterB = salesRows();
        assertThat(detailAfterB).hasSize(2);

        JobExecution restartedA = jobOperator.restart(failedA);

        assertThat(restartedA.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restartedA.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        // 10/11 の明細は A の入力どおり（0件でない）、10/12 は B のまま
        assertThat(detailRows()).containsExactly(
                "A001|1|2026-10-11|S01|I01|2|100|200",
                "A001|2|2026-10-11|S01|I02|1|500|500",
                "A002|1|2026-10-11|S03|I01|3|100|300",
                detailAfterB.get(0),
                detailAfterB.get(1));
        assertThat(salesRows()).containsExactly(
                "2026-10-11|S01|1|2|3|700",
                "2026-10-11|S03|1|1|3|300",
                salesAfterB.get(0));
    }

    @Test
    void 同じジョブの実行中の実行がある状態で起動すると二重起動防止により終了コードFAILEDになり本テーブルは変わらない()
            throws Exception {
        insertOldData();
        Path input = writeCsv("orders.csv", List.of("A001,1,2026-10-11,S01,I01,2,100"));
        // STARTED のまま終了していない実行を作る（別の scheduledTime の別 JobInstance）
        JobParameters runningParams = parameters(nextScheduledTime(), input, tempDir.resolve("running-error.txt"));
        JobInstance runningInstance = jobRepository.createJobInstance(job.getName(), runningParams);
        JobExecution running = jobRepository.createJobExecution(runningInstance, runningParams,
                new ExecutionContext());
        running.setStatus(BatchStatus.STARTED);
        running.setStartTime(LocalDateTime.now());
        jobRepository.update(running);
        JobExecution execution;
        try {
            execution = jobOperator.start(job, parameters(input, tempDir.resolve("error.txt")));
        } finally {
            // 後続のテストに影響しないよう、仕込んだ実行を終了させる
            running.setStatus(BatchStatus.FAILED);
            running.setEndTime(LocalDateTime.now());
            jobRepository.update(running);
        }

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("FAILED");
        assertThat(execution.getExitStatus().getExitDescription()).contains("二重起動");
        assertThat(detailRows()).containsExactlyElementsOf(OLD_DETAIL);
        assertThat(salesRows()).containsExactlyElementsOf(OLD_SALES);
    }

    @Test
    void scheduledTimeを渡さない場合はジョブパラメータ検証エラーで起動失敗し本テーブルは変わらない() throws Exception {
        insertOldData();
        Path input = writeCsv("orders.csv", List.of("A001,1,2026-10-11,S01,I01,2,100"));
        JobParameters params = new JobParametersBuilder()
                .addLocalDate("targetDate", TARGET_DATE)
                .addString("inputFile", input.toString())
                .addString("errorFile", tempDir.resolve("error.txt").toString())
                .toJobParameters();

        assertThatThrownBy(() -> jobOperator.start(job, params))
                .isInstanceOf(InvalidJobParametersException.class);

        assertThat(detailRows()).containsExactlyElementsOf(OLD_DETAIL);
        assertThat(salesRows()).containsExactlyElementsOf(OLD_SALES);
    }

    @Test
    void inputFileがパスに変換できない場合＿ジョブパラメータ検証エラーで起動失敗し本テーブルは変わらない() throws Exception {
        insertOldData();
        JobParameters params = new JobParametersBuilder()
                .addString("scheduledTime", nextScheduledTime())
                .addLocalDate("targetDate", TARGET_DATE)
                .addString("inputFile", "in" + (char) 0 + "put.csv")
                .addString("errorFile", tempDir.resolve("error.txt").toString())
                .toJobParameters();

        assertThatThrownBy(() -> jobOperator.start(job, params))
                .isInstanceOf(InvalidJobParametersException.class);

        assertThat(detailRows()).containsExactlyElementsOf(OLD_DETAIL);
        assertThat(salesRows()).containsExactlyElementsOf(OLD_SALES);
    }

    @Test
    void 実行時パラメータのBeanを取得した場合＿CGLIBのクラスプロキシである() {
        Object bean = applicationContext.getBean("orderDailySalesRunParameters");

        assertThat(bean).isInstanceOf(OrderDailySalesRunParametersHolder.class);
        assertThat(AopUtils.isCglibProxy(bean)).isTrue();
    }
}
