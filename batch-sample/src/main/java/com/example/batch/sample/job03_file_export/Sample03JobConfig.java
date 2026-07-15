package com.example.batch.sample.job03_file_export;

import java.nio.charset.StandardCharsets;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisCursorItemReader;
import org.mybatis.spring.batch.builder.MyBatisCursorItemReaderBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.item.CsvItemWriterFactory;
import com.example.batch.core.item.SummaryFooterCallback;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.sample.common.SalesTransaction;
import com.example.batch.sample.common.SampleProperties;

/**
 * サンプル03: DB → CSV 抽出ジョブ（Chunk）。
 *
 * <p><b>デモする機能:</b> MyBatisCursorItemReader によるカーソルフェッチ（全件メモリロードなし）、
 * ヘッダ行＋トレーラ行（件数・金額合計）付きファイル生成、出力ファイル上書きによる冪等な再実行。</p>
 *
 * <p><b>注意:</b> トレーラの集計値はメモリ上で保持するため、本ジョブは途中失敗時に
 * リスタートせず最初から再実行する運用とする（出力は上書きされるため安全）。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample03FileExportJob outputFile=/data/out/sales.csv
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample03JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample03FileExportJob";

    @Bean
    public Job sample03FileExportJob(JobRepository jobRepository, Step sample03ExportStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("outputFile"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(sample03ExportStep)
                .build();
    }

    @Bean
    public Step sample03ExportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            MyBatisCursorItemReader<SalesTransaction> sample03SalesReader,
            FlatFileItemWriter<SalesTransaction> sample03SalesWriter,
            SummaryFooterCallback<SalesTransaction> sample03FooterCallback,
            LoggingStepExecutionListener loggingStepExecutionListener,
            SampleProperties properties) {
        return new StepBuilder("sample03.01ExportStep", jobRepository)
                .<SalesTransaction, SalesTransaction>chunk(properties.chunkSize())
                .reader(sample03SalesReader)
                .writer(sample03SalesWriter)
                .transactionManager(transactionManager)
                // トレーラの件数・合計を集計するため ItemWriteListener として登録（Writer 側の footer と同一 Bean）
                .listener(sample03FooterCallback)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    public MyBatisCursorItemReader<SalesTransaction> sample03SalesReader(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisCursorItemReaderBuilder<SalesTransaction>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("com.example.batch.sample.common.SalesMapper.selectAllTransactions")
                .build();
    }

    @Bean
    @StepScope
    public SummaryFooterCallback<SalesTransaction> sample03FooterCallback() {
        return new SummaryFooterCallback<>(SalesTransaction::amount,
                (count, sum) -> "T,%d,%s".formatted(count, sum));
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<SalesTransaction> sample03SalesWriter(
            @Value("#{jobParameters['outputFile']}") String outputFile,
            SummaryFooterCallback<SalesTransaction> sample03FooterCallback) {
        return CsvItemWriterFactory.create("sample03SalesWriter", new FileSystemResource(outputFile),
                SalesTransaction.class, StandardCharsets.UTF_8,
                "id,customer_code,amount,sold_at", sample03FooterCallback,
                "id", "customerCode", "amount", "soldAt");
    }
}
