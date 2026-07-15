package com.example.batch.sample.job04_db_aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.batch.sample.AbstractJobIT;
import com.example.batch.sample.common.SalesMapper;
import com.example.batch.sample.common.SalesTransaction;

/**
 * サンプル04（DB集計・洗い替え）の結合テスト。
 */
class Sample04DbAggregateJobIT extends AbstractJobIT {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("sample04DbAggregateJob")
    private Job job;

    @Autowired
    private SalesMapper salesMapper;

    @BeforeEach
    void setUp() {
        truncateAppTable("sales_transaction");
        truncateAppTable("sales_summary");
    }

    @Test
    void 顧客別集計で洗い替えられる() throws Exception {
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C001", new BigDecimal("1000"), LocalDate.of(2026, 7, 1)));
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C001", new BigDecimal("500"), LocalDate.of(2026, 7, 2)));
        salesMapper.insertTransaction(
                new SalesTransaction(null, "C002", new BigDecimal("2000"), LocalDate.of(2026, 7, 3)));
        // 洗い替え対象の旧データ
        jdbcTemplate.update(
                "INSERT INTO app.sales_summary (customer_code, total_amount, sales_count) VALUES ('OLD', 1, 1)");

        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("aggregateDate", LocalDate.of(2026, 7, 14))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(salesMapper.countSummary()).isEqualTo(2); // 旧データは消え C001/C002 のみ
        BigDecimal c001Total = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM app.sales_summary WHERE customer_code = 'C001'", BigDecimal.class);
        assertThat(c001Total).isEqualByComparingTo("1500");
    }

    @Test
    void 集計結果が0件の場合は件数チェックで異常終了する() throws Exception {
        JobExecution execution = jobOperator.start(job, uniqueParameters()
                .addLocalDate("aggregateDate", LocalDate.of(2026, 7, 15))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }
}
