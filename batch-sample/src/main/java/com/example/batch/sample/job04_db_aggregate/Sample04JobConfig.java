package com.example.batch.sample.job04_db_aggregate;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.core.tasklet.TableCountCheckTasklet;
import com.example.batch.sample.common.SalesMapper;

/**
 * サンプル04: DB → DB 集計・洗い替えジョブ（Tasklet）。
 *
 * <p><b>デモする機能:</b> truncate-insert 方式の洗い替え（単一トランザクション）、
 * 集計 SQL（INSERT ... SELECT ... GROUP BY）による DB 内完結処理、
 * 後続 Step での結果件数チェック。</p>
 *
 * <p>集計 SQL 1本で完結する処理のため Chunk ではなく Tasklet を採用している
 * （docs/architecture.md §3.1 の使い分け基準を参照）。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample04DbAggregateJob aggregateDate=2026-07-14,java.time.LocalDate
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample04JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample04DbAggregateJob";

    @Bean
    public Job sample04DbAggregateJob(JobRepository jobRepository,
            Step sample04AggregateStep, Step sample04CountCheckStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("aggregateDate"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(sample04AggregateStep)
                .next(sample04CountCheckStep)
                .build();
    }

    /** 洗い替え本体（truncate + INSERT SELECT を1トランザクションで実行）。 */
    @Bean
    public Step sample04AggregateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            SalesMapper salesMapper, LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample04.01AggregateStep", jobRepository)
                .tasklet(new SalesAggregateTasklet(salesMapper), transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    /** 後処理: 集計結果が1件以上あることを検証する（基盤部品の件数チェック Tasklet）。 */
    @Bean
    public Step sample04CountCheckStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            SalesMapper salesMapper, LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample04.02CountCheckStep", jobRepository)
                .tasklet(new TableCountCheckTasklet("app.sales_summary", salesMapper::countSummary, 1, null),
                        transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }
}
