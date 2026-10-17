package com.example.batch.sample.job03_file_export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.batch.sample.AbstractJobIT;
import com.example.batch.sample.common.SalesMapper;
import com.example.batch.sample.common.SalesTransaction;

/**
 * サンプル03（ファイル抽出）の結合テスト。
 */
class Sample03FileExportJobIT extends AbstractJobIT {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("sample03FileExportJob")
    private Job job;

    @Autowired
    private SalesMapper salesMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        truncateAppTable("sales_transaction");
    }

    @Test
    void ヘッダとトレーラ付きのCSVを出力する() throws Exception {
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C001", new BigDecimal("1000"), LocalDate.of(2026, 7, 1)));
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C002", new BigDecimal("2500"), LocalDate.of(2026, 7, 2)));
        Path output = tempDir.resolve("sales.csv");

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addString("outputFile", output.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<String> lines = Files.readAllLines(output);
        assertThat(lines.getFirst()).isEqualTo("id,customer_code,amount,sold_at");
        assertThat(lines).hasSize(4); // ヘッダ + 明細2 + トレーラ
        assertThat(lines.getLast()).isEqualTo("T,2,3500"); // 件数・金額合計
        assertThat(lines.get(1)).contains("C001").contains("1000");
    }

    @Test
    void 対象0件でもヘッダとトレーラのみのファイルを出力する() throws Exception {
        Path output = tempDir.resolve("sales_empty.csv");

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addString("outputFile", output.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.readAllLines(output)).containsExactly(
                "id,customer_code,amount,sold_at",
                "T,0,0");
    }

    @Test
    void CSV出力の場合_一時ファイルを配置先へ移動し一時ディレクトリに残さない() throws Exception {
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C001", new BigDecimal("1000"), LocalDate.of(2026, 7, 1)));
        Path output = tempDir.resolve("out").resolve("sales_moved.csv");

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addString("outputFile", output.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(output).exists();
        assertThat(output.getParent().resolve("tmp").resolve("sales_moved.csv")).doesNotExist();
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .contains("sample03.01ExportStep", "sample03.02FileMoveStep");
    }

    @Test
    void formatにfixedを指定した場合_Shift_JISの固定長ファイルを配置先へ出力する() throws Exception {
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C001", new BigDecimal("1000"), LocalDate.of(2026, 7, 1)));
        salesMapper.insertTransaction(
                new SalesTransaction(null, "顧客A", new BigDecimal("2500"), LocalDate.of(2026, 7, 2)));
        Path output = tempDir.resolve("sales_fixed.dat");
        Charset sjis = Charset.forName("Shift_JIS");

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addString("outputFile", output.toString())
                .addString("format", "fixed")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<String> lines = Files.readAllLines(output, sjis);
        assertThat(lines).hasSize(2); // ヘッダ・トレーラ無し
        assertThat(lines).allSatisfy(line -> assertThat(line.getBytes(sjis)).hasSize(42));
        byte[] customerLine = lines.stream()
                .filter(line -> line.contains("顧客A"))
                .findFirst().orElseThrow().getBytes(sjis);
        assertThat(new String(customerLine, 10, 10, sjis)).isEqualTo("顧客A" + " ".repeat(5));
    }

    @Test
    void 配置Stepが失敗した場合_リスタートで出力Stepを飛ばし配置だけをやり直す() throws Exception {
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C001", new BigDecimal("1000"), LocalDate.of(2026, 7, 1)));
        Path output = tempDir.resolve("sales_restart.csv");
        // 配置先に同名の空でないディレクトリを置き、移動（上書き）を失敗させる
        Files.createDirectories(output);
        Files.writeString(output.resolve("block.txt"), "x");

        JobExecution failed = jobOperator.start(job, uniqueParameters()
                .addString("outputFile", output.toString())
                .toJobParameters());

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(stepStatus(failed, "sample03.01ExportStep")).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepStatus(failed, "sample03.02FileMoveStep")).isEqualTo(BatchStatus.FAILED);
        Path tempFile = tempDir.resolve("tmp").resolve("sales_restart.csv");
        assertThat(tempFile).exists();

        // 原因を取り除いてリスタート（同一パラメータ）
        Files.delete(output.resolve("block.txt"));
        Files.delete(output);
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly("sample03.02FileMoveStep");
        assertThat(tempFile).doesNotExist();
        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSize(3); // ヘッダ + 明細1 + トレーラ
        assertThat(lines.getFirst()).isEqualTo("id,customer_code,amount,sold_at");
        assertThat(lines.getLast()).isEqualTo("T,1,1000");
    }

    @Test
    void formatに未定義の値を指定した場合_InvalidJobParametersExceptionで起動を拒否する() {
        Path output = tempDir.resolve("sales_invalid.csv");

        assertThatThrownBy(() -> jobOperator.start(job, uniqueParameters()
                .addString("outputFile", output.toString())
                .addString("format", "xml")
                .toJobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    private static BatchStatus stepStatus(JobExecution execution, String stepName) {
        return execution.getStepExecutions().stream()
                .filter(s -> stepName.equals(s.getStepName()))
                .findFirst()
                .orElseThrow()
                .getStatus();
    }
}
