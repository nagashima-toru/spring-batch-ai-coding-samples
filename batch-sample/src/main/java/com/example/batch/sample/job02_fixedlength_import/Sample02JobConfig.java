package com.example.batch.sample.job02_fixedlength_import;

import java.nio.charset.Charset;
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

import com.example.batch.core.exception.BusinessSkipPolicy;
import com.example.batch.core.exception.ErrorRecordSkipListener;
import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.item.FixedLengthItemReaderFactory;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.sample.common.Product;
import com.example.batch.sample.common.SampleProperties;

import jakarta.validation.Validator;

/**
 * サンプル02: 固定長ファイル → DB 取込ジョブ（Chunk）。
 *
 * <p><b>デモする機能:</b> バイト長ベースの固定長パース（Shift_JIS・全角混在のレガシー連携想定）、
 * パースエラー・チェックエラーの Skip とエラーレコード退避。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample02FixedLengthImportJob \
 *     inputFile=/data/in/products.dat errorFile=/data/error/products_error.txt
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample02JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample02FixedLengthImportJob";

    /** 入力ファイルの文字コード（レガシー連携想定で Shift_JIS）。 */
    private static final Charset FILE_CHARSET = Charset.forName("Shift_JIS");

    @Bean
    public Job sample02FixedLengthImportJob(JobRepository jobRepository, Step sample02ImportStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("inputFile", "errorFile"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(sample02ImportStep)
                .build();
    }

    @Bean
    public Step sample02ImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            FlatFileItemReader<ProductFixedRecord> sample02ProductReader,
            ProductItemProcessor sample02ProductProcessor,
            MyBatisBatchItemWriter<Product> sample02ProductWriter,
            ErrorRecordSkipListener<ProductFixedRecord, Product> sample02ErrorRecordSkipListener,
            LoggingStepExecutionListener loggingStepExecutionListener,
            SampleProperties properties) {
        return new StepBuilder("sample02.01ImportStep", jobRepository)
                .<ProductFixedRecord, Product>chunk(properties.chunkSize())
                .reader(sample02ProductReader)
                .processor(sample02ProductProcessor)
                .writer(sample02ProductWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skipPolicy(new BusinessSkipPolicy(properties.skipLimit()))
                .skipListener(sample02ErrorRecordSkipListener)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<ProductFixedRecord> sample02ProductReader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        return FixedLengthItemReaderFactory.create("sample02ProductReader", new FileSystemResource(inputFile),
                ProductFixedRecord.class, FILE_CHARSET,
                new String[] {"productCode", "productName", "price"},
                new int[] {8, 20, 8});
    }

    @Bean
    public ProductItemProcessor sample02ProductProcessor(Validator validator) {
        return new ProductItemProcessor(validator);
    }

    @Bean
    public MyBatisBatchItemWriter<Product> sample02ProductWriter(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<Product>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.sample.common.ProductMapper.upsert")
                .assertUpdates(false)
                .build();
    }

    @Bean
    @StepScope
    public ErrorRecordSkipListener<ProductFixedRecord, Product> sample02ErrorRecordSkipListener(
            @Value("#{jobParameters['errorFile']}") String errorFile) {
        return new ErrorRecordSkipListener<>(Path.of(errorFile),
                r -> String.join(",", r.productCode(), r.productName(), r.price()),
                p -> String.join(",", p.productCode(), p.productName(), String.valueOf(p.price())));
    }
}
