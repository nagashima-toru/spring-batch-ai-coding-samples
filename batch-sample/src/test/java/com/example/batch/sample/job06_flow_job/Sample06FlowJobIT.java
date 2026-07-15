package com.example.batch.sample.job06_flow_job;

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
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.batch.sample.AbstractJobIT;
import com.example.batch.sample.common.WorkMapper;

/**
 * サンプル06（複合Flowジョブ）の結合テスト。
 *
 * <p><b>本テストの主眼はリスタート機構の実機検証</b>:
 * 途中失敗 → データ修正 → 同一パラメータで再実行、で処理済みチャンクの続きから
 * 再開されることを、メタデータ（readCount）と業務データの両面で確認する。</p>
 */
class Sample06FlowJobIT extends AbstractJobIT {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("sample06FlowJob")
    private Job job;

    @Autowired
    private WorkMapper workMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        truncateAppTable("work_import");
        truncateAppTable("work_summary");
    }

    @Test
    void 正常データは取込と集計まで完走する() throws Exception {
        Path input = Files.writeString(tempDir.resolve("work.csv"), """
                code,amount
                W001,100
                W002,200
                W003,300
                """);
        JobParameters params = uniqueParameters().addString("inputFile", input.toString()).toJobParameters();

        JobExecution execution = jobOperator.start(job, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(workMapper.countWork()).isEqualTo(3);
        assertThat(workMapper.countSummary()).isEqualTo(1); // 集計Stepまで実行された
    }

    @Test
    void 取込0件の場合は条件分岐により集計Stepをスキップして正常終了する() throws Exception {
        Path input = Files.writeString(tempDir.resolve("work_empty.csv"), "code,amount\n");
        JobParameters params = uniqueParameters().addString("inputFile", input.toString()).toJobParameters();

        JobExecution execution = jobOperator.start(job, params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(workMapper.countSummary()).isZero(); // Decider により集計はスキップ
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .doesNotContain("sample06.03SummaryStep");
    }

    @Test
    void 途中失敗後にデータを修正して同一パラメータで再実行すると続きから再開する() throws Exception {
        // チャンクサイズ2: 5行目（3チャンク目）の負数レコードで失敗させる
        // → 1・2チャンク目（4行）はコミット済みの状態を作る
        Path input = Files.writeString(tempDir.resolve("work.csv"), """
                code,amount
                W001,100
                W002,200
                W003,300
                W004,400
                W005,-1
                W006,600
                """);
        JobParameters params = uniqueParameters().addString("inputFile", input.toString()).toJobParameters();

        // 1回目: 意図的失敗
        JobExecution failed = jobOperator.start(job, params);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        // メタデータがDBへ永続化されていること（JDBCジョブリポジトリが有効であること）も検証する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch.batch_step_execution_context", Long.class)).isPositive();
        assertThat(workMapper.countWork()).isEqualTo(4); // コミット済みチャンク分のみ取込済み
        assertThat(workMapper.countSummary()).isZero();  // 集計Stepには到達していない

        // データ修正（負数を正数へ）
        Files.writeString(input, """
                code,amount
                W001,100
                W002,200
                W003,300
                W004,400
                W005,500
                W006,600
                """);

        // 2回目: リスタート（同一パラメータ）
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(workMapper.countWork()).isEqualTo(6);
        assertThat(workMapper.countSummary()).isEqualTo(1);
        // リスタートでは処理済みの4件を読み直さず、続き（2件）のみ読んでいること
        StepExecution importStep = restarted.getStepExecutions().stream()
                .filter(s -> s.getStepName().equals("sample06.02ImportStep"))
                .findFirst().orElseThrow();
        assertThat(importStep.getReadCount()).isEqualTo(2);
    }
}
