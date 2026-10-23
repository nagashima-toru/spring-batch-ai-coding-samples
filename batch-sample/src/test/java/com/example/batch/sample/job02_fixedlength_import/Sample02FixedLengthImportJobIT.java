package com.example.batch.sample.job02_fixedlength_import;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.batch.core.execution.BatchExitCodes;
import com.example.batch.sample.AbstractJobIT;

/**
 * サンプル02（固定長取込）の結合テスト。
 */
class Sample02FixedLengthImportJobIT extends AbstractJobIT {

    private static final Charset SJIS = Charset.forName("Shift_JIS");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("sample02FixedLengthImportJob")
    private Job job;

    @Autowired
    private ProductMapper productMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        truncateAppTable("product");
    }

    @Test
    void シフトJIS固定長ファイルを取り込み正常終了する() throws Exception {
        // レイアウト: code=8 / name=20(全角10文字) / price=8
        Path input = tempDir.resolve("products.dat");
        Files.write(input, (
                "P0000001りんご　　　　　　　00001000\r\n"
                + "P0000002みかんジュース　　　00000500\r\n").getBytes(SJIS));

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addString("inputFile", input.toString())
                .addString("errorFile", tempDir.resolve("error.txt").toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(productMapper.count()).isEqualTo(2);
        BigDecimal price = jdbcTemplate.queryForObject(
                "SELECT price FROM app.product WHERE product_code = 'P0000001'", BigDecimal.class);
        assertThat(price).isEqualByComparingTo("1000");
    }

    @Test
    void バイト長不正の行はSkipして警告終了する() throws Exception {
        Path input = tempDir.resolve("products.dat");
        Files.write(input, (
                "P0000001りんご　　　　　　　00001000\r\n"
                + "SHORT\r\n"
                + "P0000003ぶどう　　　　　　　00002000\r\n").getBytes(SJIS));
        Path error = tempDir.resolve("error.txt");

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addString("inputFile", input.toString())
                .addString("errorFile", error.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        assertThat(productMapper.count()).isEqualTo(2);
        assertThat(Files.readString(error)).contains("SHORT");
    }
}
