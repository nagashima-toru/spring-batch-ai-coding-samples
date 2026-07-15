package com.example.batch.sample.job05_api_import;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.exception.BusinessSkipPolicy;
import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.http.BatchHttpClient;
import com.example.batch.core.item.RestApiPagingItemReader;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.sample.common.ApiUser;
import com.example.batch.sample.common.SampleProperties;

import jakarta.validation.Validator;

/**
 * サンプル05: REST API → DB 連携ジョブ（Chunk）。
 *
 * <p><b>デモする機能:</b> HTTP Client 基盤（タイムアウト・指数バックオフリトライ・ログ内包）経由の
 * API 呼出、ページング取得（全件メモリロードなし）、認証ヘッダの外部設定、
 * レスポンスの入力チェック。</p>
 *
 * <p>API の接続先・ページサイズ・トークンは {@code application.yml} の
 * {@code sample.job05.api.*} で設定する（トークンは環境変数 {@code SAMPLE05_API_TOKEN} から注入）。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample05ApiImportJob syncDate=2026-07-14,java.time.LocalDate
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample05JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample05ApiImportJob";

    @Bean
    public Job sample05ApiImportJob(JobRepository jobRepository, Step sample05ImportStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("syncDate"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(sample05ImportStep)
                .build();
    }

    @Bean
    public Step sample05ImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            RestApiPagingItemReader<ApiUserResponse> sample05UserReader,
            ApiUserItemProcessor sample05UserProcessor,
            MyBatisBatchItemWriter<ApiUser> sample05UserWriter,
            LoggingStepExecutionListener loggingStepExecutionListener,
            SampleProperties properties) {
        return new StepBuilder("sample05.01ImportStep", jobRepository)
                .<ApiUserResponse, ApiUser>chunk(properties.chunkSize())
                .reader(sample05UserReader)
                .processor(sample05UserProcessor)
                .writer(sample05UserWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skipPolicy(new BusinessSkipPolicy(properties.skipLimit()))
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    public RestApiPagingItemReader<ApiUserResponse> sample05UserReader(BatchHttpClient batchHttpClient,
            SampleProperties properties) {
        SampleProperties.Job05.Api api = properties.job05().api();
        Map<String, String> authHeader = Map.of("Authorization", "Bearer " + api.token());
        return new RestApiPagingItemReader<>("sample05UserReader",
                page -> batchHttpClient.get(api.baseUrl() + "/users?page={page}&size={size}",
                        new ParameterizedTypeReference<List<ApiUserResponse>>() {
                        }, authHeader, page, api.pageSize()),
                api.pageSize());
    }

    @Bean
    public ApiUserItemProcessor sample05UserProcessor(Validator validator) {
        return new ApiUserItemProcessor(validator);
    }

    @Bean
    public MyBatisBatchItemWriter<ApiUser> sample05UserWriter(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<ApiUser>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.sample.common.ApiUserMapper.upsert")
                .assertUpdates(false)
                .build();
    }
}
