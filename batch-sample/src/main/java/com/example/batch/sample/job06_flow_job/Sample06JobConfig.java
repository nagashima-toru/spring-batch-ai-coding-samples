package com.example.batch.sample.job06_flow_job;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.item.CsvItemReaderFactory;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.core.tasklet.FileExistenceCheckTasklet;

import jakarta.validation.Validator;

/**
 * サンプル06: 複合ジョブ（Flow・条件分岐・リスタートデモ）。
 *
 * <p><b>デモする機能:</b> 前処理チェック → 本処理 → 後処理の Step 分割、
 * Decider による条件分岐（取込0件なら後処理スキップ）、
 * <b>途中失敗 → 同一パラメータ再実行での処理済みチャンクの続きからの再開</b>。</p>
 *
 * <p>リスタートの動作確認手順は docs/sample06-flow-job.md を参照
 * （実務で最重要のリスタート機構を体験するためのサンプル）。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample06FlowJob inputFile=/data/in/work.csv
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample06JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample06FlowJob";

    /**
     * リスタートデモ用に小さいチャンクサイズを使用する
     * （少件数データでも「処理済みチャンク＋失敗チャンク」の状況を作れるようにするため）。
     */
    static final int DEMO_CHUNK_SIZE = 2;

    @Bean
    public Job sample06FlowJob(JobRepository jobRepository,
            Step sample06FileCheckStep, Step sample06ImportStep, Step sample06SummaryStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        ImportedCountDecider decider = new ImportedCountDecider();
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("inputFile"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(sample06FileCheckStep)
                .next(sample06ImportStep)
                .next(decider)
                .on(ImportedCountDecider.PROCEED).to(sample06SummaryStep)
                .from(decider).on(ImportedCountDecider.SKIP_SUMMARY).end()
                .end()
                .build();
    }

    /** 前処理: 入力ファイルの存在チェック（基盤部品）。 */
    @Bean
    public Step sample06FileCheckStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            FileExistenceCheckTasklet sample06FileCheckTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample06.01FileCheckStep", jobRepository)
                .tasklet(sample06FileCheckTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public FileExistenceCheckTasklet sample06FileCheckTasklet(
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        return new FileExistenceCheckTasklet(Path.of(inputFile));
    }

    /** 本処理: CSV → ワークテーブル取込（負数レコードで意図的に失敗するリスタートデモ）。 */
    @Bean
    public Step sample06ImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            FlatFileItemReader<WorkCsvRecord> sample06WorkReader,
            WorkItemProcessor sample06WorkProcessor,
            MyBatisBatchItemWriter<WorkRecord> sample06WorkWriter,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample06.02ImportStep", jobRepository)
                .<WorkCsvRecord, WorkRecord>chunk(DEMO_CHUNK_SIZE)
                .reader(sample06WorkReader)
                .processor(sample06WorkProcessor)
                .writer(sample06WorkWriter)
                .transactionManager(transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<WorkCsvRecord> sample06WorkReader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        return CsvItemReaderFactory.create("sample06WorkReader", new FileSystemResource(inputFile),
                WorkCsvRecord.class, StandardCharsets.UTF_8, 1, "code", "amount");
    }

    @Bean
    public WorkItemProcessor sample06WorkProcessor(Validator validator) {
        return new WorkItemProcessor(validator);
    }

    @Bean
    public MyBatisBatchItemWriter<WorkRecord> sample06WorkWriter(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<WorkRecord>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.sample.job06_flow_job.WorkMapper.upsert")
                .assertUpdates(false)
                .build();
    }

    /** 後処理: サマリ作成（取込0件時は Decider によりスキップされる）。 */
    @Bean
    public Step sample06SummaryStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            WorkMapper workMapper, LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample06.03SummaryStep", jobRepository)
                .tasklet(new WorkSummaryTasklet(workMapper), transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }
}
