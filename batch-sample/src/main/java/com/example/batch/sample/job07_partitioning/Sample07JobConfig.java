package com.example.batch.sample.job07_partitioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;

/**
 * サンプル07【骨格】: パーティショニングによる大量データ並列処理ジョブ。
 *
 * <p><b>デモする機能:</b> Partitioner による処理範囲分割と Worker Step の並列実行。
 * この骨格では Worker は担当範囲をログ出力するのみ。実務では Worker Step を
 * Chunk Step（範囲条件付きカーソル読取 → 加工 → 書込）に置き換える。</p>
 *
 * <p>性能設計の考え方・パーティション数の決め方は docs/sample07-partitioning.md を参照。</p>
 */
@Configuration(proxyBeanMethods = false)
public class Sample07JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample07PartitioningJob";

    private static final Logger log = LoggerFactory.getLogger(Sample07JobConfig.class);

    /** 並列度（実務では CPU コア数・DB 接続数から性能測定して決定する）。 */
    private static final int GRID_SIZE = 4;

    @Bean
    public Job sample07PartitioningJob(JobRepository jobRepository, Step sample07ManagerStep,
            LoggingJobExecutionListener loggingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("processDate"))
                .listener(loggingJobExecutionListener)
                .start(sample07ManagerStep)
                .build();
    }

    /** Manager Step: 範囲を分割し Worker Step を並列起動する。 */
    @Bean
    public Step sample07ManagerStep(JobRepository jobRepository, Step sample07WorkerStep,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample07.01ManagerStep", jobRepository)
                .partitioner(sample07WorkerStep.getName(), new SalesIdRangePartitioner(1, 1000))
                .step(sample07WorkerStep)
                .gridSize(GRID_SIZE)
                .taskExecutor(new SimpleAsyncTaskExecutor("sample07-worker-"))
                .listener(loggingStepExecutionListener)
                .build();
    }

    /** Worker Step: 骨格ではログ出力のみ。実務では Chunk Step に置き換える。 */
    @Bean
    public Step sample07WorkerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            Tasklet sample07WorkerTasklet) {
        return new StepBuilder("sample07.02WorkerStep", jobRepository)
                .tasklet(sample07WorkerTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet sample07WorkerTasklet(
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return (contribution, chunkContext) -> {
            log.info("Worker実行（骨格）: 担当範囲 id={}〜{}", minId, maxId);
            return RepeatStatus.FINISHED;
        };
    }
}
