package com.example.batch.sample.job01_csv_import;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.item.CsvItemReaderFactory;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.sample.common.SampleProperties;

import jakarta.validation.Validator;

/**
 * サンプル01: CSV → DB 取込ジョブ（Chunk）。
 *
 * <p><b>デモする機能:</b> Bean Validation による入力チェック、業務エラーの Skip、
 * エラーレコードのファイル退避、警告終了（Skip 発生時に終了コード2）、UPSERT による冪等な取込。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample01CsvImportJob \
 *     inputFile=/data/in/customers.csv errorFile=/data/error/customers_error.txt
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample01JobConfig {

    /** ジョブ名（spring.batch.job.name で指定する値）。 */
    public static final String JOB_NAME = "sample01CsvImportJob";

    @Bean
    public Job sample01CsvImportJob(JobRepository jobRepository, Step sample01ImportStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("inputFile", "errorFile"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(sample01ImportStep)
                .build();
    }

    @Bean
    public Step sample01ImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            FlatFileItemReader<CustomerCsvRecord> sample01CustomerReader,
            CustomerItemProcessor sample01CustomerProcessor,
            MyBatisBatchItemWriter<Customer> sample01CustomerWriter,
            ErrorRecordSkipListener<CustomerCsvRecord, Customer> sample01ErrorRecordSkipListener,
            LoggingStepExecutionListener loggingStepExecutionListener,
            SampleProperties properties) {
        return new StepBuilder("sample01.01ImportStep", jobRepository)
                .<CustomerCsvRecord, Customer>chunk(properties.chunkSize())
                .reader(sample01CustomerReader)
                .processor(sample01CustomerProcessor)
                .writer(sample01CustomerWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skipPolicy(new BusinessSkipPolicy(properties.skipLimit()))
                .skipListener(sample01ErrorRecordSkipListener)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<CustomerCsvRecord> sample01CustomerReader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        return CsvItemReaderFactory.create("sample01CustomerReader", new FileSystemResource(inputFile),
                CustomerCsvRecord.class, StandardCharsets.UTF_8, 1, "customerCode", "name", "email");
    }

    @Bean
    public CustomerItemProcessor sample01CustomerProcessor(Validator validator) {
        return new CustomerItemProcessor(validator);
    }

    @Bean
    public MyBatisBatchItemWriter<Customer> sample01CustomerWriter(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<Customer>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.sample.job01_csv_import.CustomerMapper.upsert")
                .assertUpdates(false) // UPSERT はバッチ実行時に更新件数が取得できないため検証しない
                .build();
    }

    @Bean
    @StepScope
    public ErrorRecordSkipListener<CustomerCsvRecord, Customer> sample01ErrorRecordSkipListener(
            @Value("#{jobParameters['errorFile']}") String errorFile) {
        return new ErrorRecordSkipListener<>(Path.of(errorFile),
                r -> String.join(",", r.customerCode(), r.name(), r.email()),
                c -> String.join(",", c.customerCode(), c.name(), c.email()));
    }
}
