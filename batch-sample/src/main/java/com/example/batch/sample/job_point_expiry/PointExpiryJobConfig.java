package com.example.batch.sample.job_point_expiry;

import java.time.LocalDate;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;

/**
 * POINT-001: 会員ポイント失効バッチ。
 *
 * <p><b>デモする機能:</b> 期限切れポイント付与の失効更新と失効履歴登録を、
 * PostgreSQL のデータ変更 CTE（{@code WITH ... UPDATE ... RETURNING} を
 * {@code INSERT ... SELECT} する）により単一 SQL・単一トランザクションで原子的に行う
 * Tasklet 1 Step 構成（サンプル04 {@code SalesAggregateTasklet} と同型）。</p>
 *
 * <p>詳細設計書: docs/projects/member-point-expiry/detail-design/POINT-001.md</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=pointExpiryJob targetDate=2026-08-10,java.time.LocalDate
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class PointExpiryJobConfig {

    /** ジョブ名（spring.batch.job.name で指定する値）。 */
    public static final String JOB_NAME = "pointExpiryJob";

    @Bean
    public Job pointExpiryJob(JobRepository jobRepository, Step pointExpiryExpireStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("targetDate"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(pointExpiryExpireStep)
                .build();
    }

    /** Step1: 期限切れ付与の失効更新＋失効履歴登録（単一 SQL・単一トランザクション）。 */
    @Bean
    public Step pointExpiryExpireStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            PointExpiryTasklet pointExpiryTasklet, LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("pointExpiry.01ExpireStep", jobRepository)
                .tasklet(pointExpiryTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public PointExpiryTasklet pointExpiryTasklet(@Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            PointGrantMapper pointGrantMapper) {
        return new PointExpiryTasklet(targetDate, pointGrantMapper);
    }
}
