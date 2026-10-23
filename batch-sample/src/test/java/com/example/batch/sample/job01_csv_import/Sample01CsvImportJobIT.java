package com.example.batch.sample.job01_csv_import;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.batch.core.execution.BatchExitCodes;
import com.example.batch.sample.AbstractJobIT;

/**
 * サンプル01（CSV取込）の結合テスト。
 */
class Sample01CsvImportJobIT extends AbstractJobIT {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("sample01CsvImportJob")
    private Job job;

    @Autowired
    private CustomerMapper customerMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        truncateAppTable("customer");
    }

    private JobParameters parameters(Path input, Path error) {
        return uniqueParameters()
                .addString("inputFile", input.toString())
                .addString("errorFile", error.toString())
                .toJobParameters();
    }

    @Test
    void 正常なCSVを全件取り込み正常終了する() throws Exception {
        Path input = Files.writeString(tempDir.resolve("customers.csv"), """
                customer_code,name,email
                c001,山田太郎,taro@example.com
                C002,鈴木花子,hanako@example.com
                """);
        Path error = tempDir.resolve("customers_error.txt");

        JobExecution execution = jobOperator.start(job, parameters(input, error));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(customerMapper.count()).isEqualTo(2);
        // 顧客コードは大文字へ正規化される（Processor の変換）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.customer WHERE customer_code = 'C001'", Long.class)).isEqualTo(1);
        assertThat(error).doesNotExist();
    }

    @Test
    void 不正レコードはSkipしてエラーファイルへ退避し警告終了する() throws Exception {
        Path input = Files.writeString(tempDir.resolve("customers.csv"), """
                customer_code,name,email
                C001,山田太郎,taro@example.com
                ,名無し,invalid-mail
                C003,佐藤次郎,jiro@example.com
                """);
        Path error = tempDir.resolve("customers_error.txt");

        JobExecution execution = jobOperator.start(job, parameters(input, error));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // Skip 発生時は警告終了（終了コード2に対応する ExitStatus）
        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        assertThat(customerMapper.count()).isEqualTo(2);
        assertThat(Files.readString(error)).contains("名無し");
    }

    @Test
    void 入力ファイルが存在しない場合は異常終了する() throws Exception {
        JobParameters params = parameters(tempDir.resolve("missing.csv"), tempDir.resolve("e.txt"));

        JobExecution execution = jobOperator.start(job, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }
}
