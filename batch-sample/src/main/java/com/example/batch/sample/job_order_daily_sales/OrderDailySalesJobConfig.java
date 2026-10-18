package com.example.batch.sample.job_order_daily_sales;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.example.batch.core.exception.BusinessSkipPolicy;
import com.example.batch.core.exception.ErrorRecordSkipListener;
import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.item.CsvItemReaderFactory;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.core.tasklet.CsvHeaderCheckTasklet;
import com.example.batch.core.tasklet.FileExistenceCheckTasklet;
import com.example.batch.sample.common.SampleProperties;

import jakarta.validation.Validator;

/**
 * ORDER-001: 受注CSV取込・日次売上集計ジョブ（Chunk ＋ Tasklet）。
 *
 * <p><b>デモする機能:</b> ワークテーブル経由の洗い替え（削除と登録を1トランザクションで行い、失敗時に旧データを残す）、
 * 行番号付き CSV 読取によるファイル内の重複検出（リスタートは Step 1〜4 を最初からやり直す）、ヘッダ行の照合、
 * {@code scheduledTime}（UTC）から JST の前日を導出する省略時パラメータの補完。</p>
 *
 * <p>詳細設計書: docs/projects/order-daily-sales/detail-design/ORDER-001.md</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=orderDailySalesJob scheduledTime=2026-10-11T17:00:00Z
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class OrderDailySalesJobConfig {

    /** ジョブ名（spring.batch.job.name で指定する値）。 */
    public static final String JOB_NAME = "orderDailySalesJob";

    private static final Logger log = LoggerFactory.getLogger(OrderDailySalesJobConfig.class);

    @Bean
    public Job orderDailySalesJob(JobRepository jobRepository,
            Step orderDailySalesFileCheckStep, Step orderDailySalesHeaderCheckStep,
            Step orderDailySalesClearWorkStep, Step orderDailySalesImportStep, Step orderDailySalesReplaceStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new OrderDailySalesJobParametersValidator())
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(orderDailySalesFileCheckStep)
                .next(orderDailySalesHeaderCheckStep)
                .next(orderDailySalesClearWorkStep)
                .next(orderDailySalesImportStep)
                .next(orderDailySalesReplaceStep)
                .build();
    }

    /** Step1: 入力ファイルの存在・読取可能性の確認。 */
    @Bean
    public Step orderDailySalesFileCheckStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager, FileExistenceCheckTasklet orderDailySalesFileCheckTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("orderDailySales.01FileCheckStep", jobRepository)
                .allowStartIfComplete(true)
                .tasklet(orderDailySalesFileCheckTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    /** Step2: ヘッダ行の照合。 */
    @Bean
    public Step orderDailySalesHeaderCheckStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager, CsvHeaderCheckTasklet orderDailySalesHeaderCheckTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("orderDailySales.02HeaderCheckStep", jobRepository)
                .allowStartIfComplete(true)
                .tasklet(orderDailySalesHeaderCheckTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    /** Step3: エラーファイルを削除し、ワークテーブルを空にする。 */
    @Bean
    public Step orderDailySalesClearWorkStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager, OrderDetailWorkClearTasklet orderDailySalesClearWorkTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("orderDailySales.03ClearWorkStep", jobRepository)
                .allowStartIfComplete(true)
                .tasklet(orderDailySalesClearWorkTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    /** Step4: CSV → ワークテーブル（不正行は Skip してエラーファイルへ退避）。 */
    @Bean
    public Step orderDailySalesImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            FlatFileItemReader<OrderCsvRecord> orderDailySalesReader,
            OrderItemProcessor orderDailySalesProcessor,
            MyBatisBatchItemWriter<OrderDetailWork> orderDailySalesWriter,
            ErrorRecordSkipListener<OrderCsvRecord, OrderDetailWork> orderDailySalesErrorRecordSkipListener,
            LoggingStepExecutionListener loggingStepExecutionListener,
            SampleProperties properties) {
        return new StepBuilder("orderDailySales.04ImportStep", jobRepository)
                .allowStartIfComplete(true)
                .<OrderCsvRecord, OrderDetailWork>chunk(properties.chunkSize())
                .reader(orderDailySalesReader)
                .processor(orderDailySalesProcessor)
                .writer(orderDailySalesWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skipPolicy(new BusinessSkipPolicy(properties.skipLimit()))
                .skipListener(orderDailySalesErrorRecordSkipListener)
                .listener(loggingStepExecutionListener)
                .build();
    }

    /** Step5: 対象日の受注明細・日次売上集計の入れ替え（1トランザクション）。 */
    @Bean
    public Step orderDailySalesReplaceStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager, OrderDailySalesReplaceTasklet orderDailySalesReplaceTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("orderDailySales.05ReplaceStep", jobRepository)
                .tasklet(orderDailySalesReplaceTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public FileExistenceCheckTasklet orderDailySalesFileCheckTasklet(
            @Value("#{jobParameters['scheduledTime']}") String scheduledTime,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['errorFile']}") String errorFile,
            SampleProperties properties) {
        return new FileExistenceCheckTasklet(
                runParameters(scheduledTime, targetDate, inputFile, errorFile, properties).inputFile());
    }

    @Bean
    @StepScope
    public CsvHeaderCheckTasklet orderDailySalesHeaderCheckTasklet(
            @Value("#{jobParameters['scheduledTime']}") String scheduledTime,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['errorFile']}") String errorFile,
            SampleProperties properties) {
        return new CsvHeaderCheckTasklet(
                runParameters(scheduledTime, targetDate, inputFile, errorFile, properties).inputFile(),
                StandardCharsets.UTF_8, OrderCsvRecord.COLUMN_NAMES);
    }

    @Bean
    @StepScope
    public OrderDetailWorkClearTasklet orderDailySalesClearWorkTasklet(
            @Value("#{jobParameters['scheduledTime']}") String scheduledTime,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['errorFile']}") String errorFile,
            SampleProperties properties, OrderDetailWorkMapper orderDetailWorkMapper) {
        return new OrderDetailWorkClearTasklet(
                runParameters(scheduledTime, targetDate, inputFile, errorFile, properties).errorFile(),
                orderDetailWorkMapper);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<OrderCsvRecord> orderDailySalesReader(
            @Value("#{jobParameters['scheduledTime']}") String scheduledTime,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['errorFile']}") String errorFile,
            SampleProperties properties) {
        OrderDailySalesRunParameters runParameters =
                runParameters(scheduledTime, targetDate, inputFile, errorFile, properties);
        FlatFileItemReader<OrderCsvRecord> reader = CsvItemReaderFactory.createWithLineNumber(
                "orderDailySalesReader", new FileSystemResource(runParameters.inputFile()), StandardCharsets.UTF_8, 1,
                OrderCsvRecord.COLUMN_NAMES, OrderCsvRecord::of);
        // リスタート時もファイルの先頭から読み直す（Step 4 は最初からやり直すため読取位置を保存しない）
        reader.setSaveState(false);
        return reader;
    }

    @Bean
    @StepScope
    public OrderItemProcessor orderDailySalesProcessor(Validator validator,
            @Value("#{jobParameters['scheduledTime']}") String scheduledTime,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['errorFile']}") String errorFile,
            SampleProperties properties) {
        return new OrderItemProcessor(validator,
                runParameters(scheduledTime, targetDate, inputFile, errorFile, properties).targetDate());
    }

    @Bean
    public MyBatisBatchItemWriter<OrderDetailWork> orderDailySalesWriter(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<OrderDetailWork>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.sample.job_order_daily_sales.OrderDetailWorkMapper.insert")
                .build();
    }

    @Bean
    @StepScope
    public ErrorRecordSkipListener<OrderCsvRecord, OrderDetailWork> orderDailySalesErrorRecordSkipListener(
            @Value("#{jobParameters['scheduledTime']}") String scheduledTime,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['errorFile']}") String errorFile,
            SampleProperties properties) {
        return new ErrorRecordSkipListener<>(
                runParameters(scheduledTime, targetDate, inputFile, errorFile, properties).errorFile(),
                r -> "line=" + r.lineSeq() + "," + String.join(",", r.orderNo(), r.lineNo(), r.orderDate(),
                        r.storeCode(), r.itemCode(), r.quantity(), r.unitPrice()),
                w -> "line=" + w.lineSeq() + "," + w.orderNo() + "," + w.lineNo());
    }

    @Bean
    @StepScope
    public OrderDailySalesReplaceTasklet orderDailySalesReplaceTasklet(
            @Value("#{jobParameters['scheduledTime']}") String scheduledTime,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate,
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("#{jobParameters['errorFile']}") String errorFile,
            SampleProperties properties, OrderDetailMapper orderDetailMapper,
            DailyStoreSalesMapper dailyStoreSalesMapper) {
        return new OrderDailySalesReplaceTasklet(
                runParameters(scheduledTime, targetDate, inputFile, errorFile, properties).targetDate(),
                orderDetailMapper, dailyStoreSalesMapper);
    }

    /** 省略時の補完を済ませた実行時パラメータを確定し、補完後の値をログに出す。 */
    private static OrderDailySalesRunParameters runParameters(String scheduledTime, LocalDate targetDate,
            String inputFile, String errorFile, SampleProperties properties) {
        OrderDailySalesRunParameters resolved = OrderDailySalesRunParameters.resolve(scheduledTime, targetDate,
                inputFile, errorFile, properties.orderDailySales());
        log.info("実行時パラメータを確定しました: targetDate={} inputFile={} errorFile={}",
                resolved.targetDate(), resolved.inputFile(), resolved.errorFile());
        return resolved;
    }
}
