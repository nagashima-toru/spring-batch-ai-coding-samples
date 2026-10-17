package com.example.batch.sample.job03_file_export;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisCursorItemReader;
import org.mybatis.spring.batch.builder.MyBatisCursorItemReaderBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.exception.SystemException;
import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.item.CsvItemWriterFactory;
import com.example.batch.core.item.FixedLengthItemWriterFactory;
import com.example.batch.core.item.SummaryFooterCallback;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.core.tasklet.FileMoveTasklet;
import com.example.batch.sample.common.SalesTransaction;
import com.example.batch.sample.common.SampleProperties;

/**
 * サンプル03: DB → CSV 抽出ジョブ（Chunk）。
 *
 * <p><b>デモする機能:</b> MyBatisCursorItemReader によるカーソルフェッチ（全件メモリロードなし）、
 * ヘッダ行＋トレーラ行（件数・金額合計）付きファイル生成、出力ファイル上書きによる冪等な再実行。</p>
 *
 * <p><b>一時出力→配置:</b> 1つ目の Step は配置先の親ディレクトリ配下の {@code tmp/} に一時ファイルとして出力し、
 * 2つ目の Step が {@link FileMoveTasklet} で outputFile へ移動する（途中失敗時に不完全なファイルを配置先に残さない）。</p>
 *
 * <p><b>固定長出力:</b> 任意パラメータ {@code format=fixed} で {@link FixedLengthItemWriterFactory} による
 * Shift_JIS 固定長（ヘッダ・トレーラ無し）を出力する。未指定・空・{@code csv} は CSV、それ以外は起動を拒否する。</p>
 *
 * <p><b>注意:</b> トレーラの集計値はメモリ上で保持するため、本ジョブは途中失敗時に
 * リスタートせず最初から再実行する運用とする（出力は上書きされるため安全）。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample03FileExportJob outputFile=/data/out/sales.csv
 * --spring.batch.job.name=sample03FileExportJob outputFile=/data/out/sales.dat format=fixed
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample03JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample03FileExportJob";

    @Bean
    public Job sample03FileExportJob(JobRepository jobRepository, Step sample03ExportStep, Step sample03FileMoveStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(parameters -> {
                    JobParametersValidators.requiredAndOptional(new String[] {"outputFile"}, new String[] {"format"})
                            .validate(parameters);
                    validateFormat(parameters.getString("format"));
                })
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(sample03ExportStep)
                .next(sample03FileMoveStep)
                .build();
    }

    private static void validateFormat(String format) throws InvalidJobParametersException {
        if (format != null && !format.isEmpty() && !"csv".equals(format) && !"fixed".equals(format)) {
            throw new InvalidJobParametersException("format は csv または fixed を指定してください: " + format);
        }
    }

    /**
     * 一時ファイルのパスを返す（配置先の親ディレクトリ配下の tmp/ に同名で置く）。
     *
     * @param outputFile 配置先ファイル
     * @return 一時ファイル
     */
    static Path tempFileOf(String outputFile) {
        return outputDirOf(outputFile).resolve("tmp").resolve(requirePath(Path.of(outputFile).getFileName(),
                "outputFile にファイル名がありません: " + outputFile));
    }

    private static Path outputDirOf(String outputFile) {
        return requirePath(Path.of(outputFile).toAbsolutePath().getParent(),
                "outputFile に親ディレクトリがありません: " + outputFile);
    }

    // outputFile がルートパスの時だけ null になる。例外の3階層に合わせて SystemException にする
    private static Path requirePath(Path path, String message) {
        if (path == null) {
            throw new SystemException(message);
        }
        return path;
    }

    @Bean
    public Step sample03FileMoveStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            Tasklet sample03FileMoveTasklet, LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample03.02FileMoveStep", jobRepository)
                .tasklet(sample03FileMoveTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet sample03FileMoveTasklet(@Value("#{jobParameters['outputFile']}") String outputFile) {
        return new FileMoveTasklet(tempFileOf(outputFile), outputDirOf(outputFile), true);
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
            @Value("#{jobParameters['format']}") String format,
            SummaryFooterCallback<SalesTransaction> sample03FooterCallback) {
        Path tempFile = tempFileOf(outputFile);
        Path tempDir = outputDirOf(outputFile).resolve("tmp");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new SystemException("一時ディレクトリを作成できません: " + tempDir, e);
        }
        if ("fixed".equals(format)) {
            return FixedLengthItemWriterFactory.create("sample03SalesFixedWriter", new FileSystemResource(tempFile),
                    SalesTransaction.class, Charset.forName("Shift_JIS"), null, null,
                    new String[] {"id", "customerCode", "amount", "soldAt"}, new int[] {10, 10, 12, 10});
        }
        return CsvItemWriterFactory.create("sample03SalesWriter", new FileSystemResource(tempFile),
                SalesTransaction.class, StandardCharsets.UTF_8,
                "id,customer_code,amount,sold_at", sample03FooterCallback,
                "id", "customerCode", "amount", "soldAt");
    }
}
