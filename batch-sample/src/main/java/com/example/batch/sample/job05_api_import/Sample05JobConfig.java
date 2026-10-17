package com.example.batch.sample.job05_api_import;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.exception.BusinessSkipPolicy;
import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.http.BatchHttpClient;
import com.example.batch.core.item.JsonItemReaderFactory;
import com.example.batch.core.item.RestApiPagingItemReader;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.core.tasklet.TableCountCheckTasklet;
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
 * <p><b>Step 構成:</b> 任意パラメータ {@code inputFile} が未指定なら、件数取得 Step
 * （{@code sample05.00UserCountStep}、{@code AbstractApiCallTasklet} 継承の {@link ApiUserCountTasklet}）の後に、
 * 取込 Step（{@code sample05.01ImportStep}）で API から取り込む。{@code inputFile} を指定すると
 * 件数取得 Step を飛ばし、API の代わりに保存済み JSON ファイルから取り込む（リプレイ用途。
 * 分岐は {@link InputSourceDecider}）。取込の後、件数突き合わせ Step（{@code sample05.02CountCheckStep}、
 * {@code TableCountCheckTasklet}）で取得件数が件数 API の件数と一致することを確かめる（inputFile 指定時は行わない）。
 * HTTP のリトライは {@code BatchHttpClient} が行い、
 * Batch の Retry は使わない（方式設計 §5.2）。</p>
 *
 * <p>API の接続先・ページサイズ・トークンは {@code application.yml} の
 * {@code sample.job05.api.*} で設定する（トークンは環境変数 {@code SAMPLE05_API_TOKEN} から注入）。</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=sample05ApiImportJob syncDate=2026-07-14,java.time.LocalDate
 *
 * # 保存済み JSON から取り込む（リプレイ）
 * --spring.batch.job.name=sample05ApiImportJob syncDate=2026-07-14,java.time.LocalDate
 *   inputFile=batch-sample/data/in/api-users.json
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class Sample05JobConfig {

    /** ジョブ名。 */
    public static final String JOB_NAME = "sample05ApiImportJob";

    /** API 取得用 Reader の名前。ExecutionContext の読込位置のキー（{@code <名前>.read.count}）に使われる。 */
    public static final String USER_READER_NAME = "sample05UserReader";

    /** inputFile 指定時に使う JSON 用 Reader の名前。ExecutionContext の読込位置のキーに使われる。 */
    public static final String USER_JSON_READER_NAME = "sample05UserJsonReader";

    private static final Logger log = LoggerFactory.getLogger(Sample05JobConfig.class);

    @Bean
    public Job sample05ApiImportJob(JobRepository jobRepository, Step sample05UserCountStep,
            Step sample05ImportStep, Step sample05CountCheckStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        InputSourceDecider decider = new InputSourceDecider();
        // 取込 Step は両方の分岐で同じ Step のため、後続の件数突き合わせ Step も両方で通る（inputFile 時は何もしない）
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.requiredAndOptional(
                        new String[] {"syncDate"}, new String[] {"inputFile"}))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .start(decider)
                .on(InputSourceDecider.FILE).to(sample05ImportStep)
                .from(decider).on(InputSourceDecider.API).to(sample05UserCountStep)
                .next(sample05ImportStep)
                .next(sample05CountCheckStep)
                .end()
                .build();
    }

    @Bean
    public Step sample05UserCountStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            ApiUserCountTasklet sample05UserCountTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        // 件数突き合わせ Step で使うため、件数を Job の ExecutionContext にも載せる（リスタートでも引き継がれる）
        ExecutionContextPromotionListener promotionListener = new ExecutionContextPromotionListener();
        promotionListener.setKeys(new String[] {ApiUserCountTasklet.COUNT_KEY});
        return new StepBuilder("sample05.00UserCountStep", jobRepository)
                .tasklet(sample05UserCountTasklet, transactionManager)
                .listener(promotionListener)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    public Step sample05CountCheckStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            Tasklet sample05CountCheckTasklet, LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("sample05.02CountCheckStep", jobRepository)
                .tasklet(sample05CountCheckTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet sample05CountCheckTasklet(
            @Value("#{jobExecutionContext['" + ApiUserCountTasklet.COUNT_KEY + "']}") Long apiUserCount,
            @Value("#{jobExecutionContext['" + ApiUserReadCountListener.READ_COUNT_KEY + "']}") Long readCount) {
        if (apiUserCount == null) {
            // inputFile 指定時は件数 API を呼ばないため、突き合わせる相手が無い
            return (contribution, chunkContext) -> {
                log.info("件数APIを呼んでいないため、件数の突き合わせを行いません");
                return RepeatStatus.FINISHED;
            };
        }
        // 取得件数が件数 API の件数と一致すること（最小＝最大＝API の件数）。取りこぼしを正常終了で見逃さない
        return new TableCountCheckTasklet("APIユーザーの取得件数", () -> readCount == null ? 0L : readCount,
                apiUserCount, apiUserCount);
    }

    @Bean
    public ApiUserCountTasklet sample05UserCountTasklet(BatchHttpClient batchHttpClient,
            SampleProperties properties) {
        SampleProperties.Job05.Api api = properties.job05().api();
        return new ApiUserCountTasklet(batchHttpClient, api.baseUrl(), api.token());
    }

    @Bean
    public Step sample05ImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            ItemStreamReader<ApiUserResponse> sample05UserReader,
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
                .listener(new ApiUserReadCountListener(USER_READER_NAME, USER_JSON_READER_NAME))
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public ItemStreamReader<ApiUserResponse> sample05UserReader(BatchHttpClient batchHttpClient,
            SampleProperties properties, @Value("#{jobParameters['inputFile']}") String inputFile) {
        if (InputSourceDecider.isFileInput(inputFile)) {
            return JsonItemReaderFactory.create(USER_JSON_READER_NAME, new FileSystemResource(inputFile),
                    ApiUserResponse.class);
        }
        SampleProperties.Job05.Api api = properties.job05().api();
        Map<String, String> authHeader = Map.of("Authorization", "Bearer " + api.token());
        return new RestApiPagingItemReader<>(USER_READER_NAME,
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
