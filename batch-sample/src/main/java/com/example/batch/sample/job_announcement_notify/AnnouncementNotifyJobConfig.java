package com.example.batch.sample.job_announcement_notify;

import java.nio.file.Path;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.MyBatisCursorItemReader;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.mybatis.spring.batch.builder.MyBatisCursorItemReaderBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.batch.core.exception.BusinessSkipPolicy;
import com.example.batch.core.exception.ErrorRecordSkipListener;
import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.JobParametersValidators;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;
import com.example.batch.sample.common.SampleProperties;

import jakarta.validation.Validator;

/**
 * ANNOUNCE-001: お知らせ通知バッチ。
 *
 * <p><b>デモする機能:</b> 未処理お知らせの一括取込（Tasklet）→ 通知区域の郵便番号展開不能チェック
 * （Tasklet）→ 通知対象ユーザーの抽出・検証・メール送信依頼登録（Chunk）→ 処理済み更新（Tasklet）
 * という Chunk＋Tasklet 混在ジョブ。Job レベル {@code ExecutionContext} を使った
 * リスタート安全な追加警告判定（{@link RegionErrorWarningJobExecutionListener}）も実装する。</p>
 *
 * <p>詳細設計書: docs/projects/announcement-notification/detail-design/ANNOUNCE-001.md</p>
 *
 * <p><b>実行方法:</b></p>
 * <pre>{@code
 * --spring.batch.job.name=announcementNotifyJob \
 *     scheduledTime=2026-08-09T09:15:00,java.time.LocalDateTime \
 *     errorFile=/data/error/announce_error.txt
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class AnnouncementNotifyJobConfig {

    /** ジョブ名（spring.batch.job.name で指定する値）。 */
    public static final String JOB_NAME = "announcementNotifyJob";

    @Bean
    public Job announcementNotifyJob(JobRepository jobRepository,
            Step announcementNotifyPickupStep, Step announcementNotifyRegionCheckStep,
            Step announcementNotifyRegisterMailStep, Step announcementNotifyMarkProcessedStep,
            LoggingJobExecutionListener loggingJobExecutionListener,
            RegionErrorWarningJobExecutionListener regionErrorWarningJobExecutionListener,
            DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(JobParametersValidators.required("scheduledTime", "errorFile"))
                .listener(duplicateRunPreventingJobExecutionListener)
                .listener(loggingJobExecutionListener)
                .listener(regionErrorWarningJobExecutionListener)
                .start(announcementNotifyPickupStep)
                .next(announcementNotifyRegionCheckStep)
                .next(announcementNotifyRegisterMailStep)
                .next(announcementNotifyMarkProcessedStep)
                .build();
    }

    /** Step1: 未処理のお知らせを処理中へ一括更新する（対象確定）。 */
    @Bean
    public Step announcementNotifyPickupStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager, AnnouncementPickupTasklet announcementPickupTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("announcementNotify.01PickupStep", jobRepository)
                .tasklet(announcementPickupTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    public AnnouncementPickupTasklet announcementPickupTasklet(AnnouncementMapper announcementMapper) {
        return new AnnouncementPickupTasklet(announcementMapper);
    }

    /** Step2: 通知区域が郵便番号マスタに1件も展開できないお知らせをエラーへ一括更新する。 */
    @Bean
    public Step announcementNotifyRegionCheckStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            AnnouncementRegionCheckTasklet announcementRegionCheckTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("announcementNotify.02RegionCheckStep", jobRepository)
                .tasklet(announcementRegionCheckTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    public AnnouncementRegionCheckTasklet announcementRegionCheckTasklet(AnnouncementMapper announcementMapper) {
        return new AnnouncementRegionCheckTasklet(announcementMapper);
    }

    /** Step3: 通知対象ユーザーを抽出し、検証のうえメール送信依頼を登録する（Chunk）。 */
    @Bean
    public Step announcementNotifyRegisterMailStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MyBatisCursorItemReader<NotifyCandidate> notifyTargetReader,
            NotifyCandidateProcessor notifyCandidateProcessor,
            MyBatisBatchItemWriter<MailSend> announcementNotifyMailWriter,
            ErrorRecordSkipListener<NotifyCandidate, MailSend> announcementNotifyErrorRecordSkipListener,
            LoggingStepExecutionListener loggingStepExecutionListener,
            SampleProperties properties) {
        return new StepBuilder("announcementNotify.03RegisterMailStep", jobRepository)
                .<NotifyCandidate, MailSend>chunk(properties.chunkSize())
                .reader(notifyTargetReader)
                .processor(notifyCandidateProcessor)
                .writer(announcementNotifyMailWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skipPolicy(new BusinessSkipPolicy(properties.skipLimit()))
                .skipListener(announcementNotifyErrorRecordSkipListener)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    public MyBatisCursorItemReader<NotifyCandidate> notifyTargetReader(SqlSessionFactory sqlSessionFactory) {
        MyBatisCursorItemReader<NotifyCandidate> reader = new MyBatisCursorItemReaderBuilder<NotifyCandidate>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("com.example.batch.sample.job_announcement_notify.NotifyTargetMapper.selectNotifyTargets")
                .build();
        // ExecutionContext の状態保存キーを明示する（既定はクラス短縮名。Reader 追加時のキー衝突を予防）
        reader.setName("notifyTargetReader");
        return reader;
    }

    @Bean
    public NotifyCandidateProcessor notifyCandidateProcessor(Validator validator) {
        return new NotifyCandidateProcessor(validator);
    }

    @Bean
    public MyBatisBatchItemWriter<MailSend> announcementNotifyMailWriter(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<MailSend>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.sample.job_announcement_notify.MailSendMapper.insert")
                .assertUpdates(false) // ON CONFLICT DO NOTHING により更新件数0が正常に発生するため検証しない
                .build();
    }

    @Bean
    @StepScope
    public ErrorRecordSkipListener<NotifyCandidate, MailSend> announcementNotifyErrorRecordSkipListener(
            @Value("#{jobParameters['errorFile']}") String errorFile) {
        return new ErrorRecordSkipListener<>(Path.of(errorFile),
                candidate -> String.join(",", String.valueOf(candidate.announcementId()),
                        String.valueOf(candidate.userId()), candidate.email()),
                mailSend -> String.join(",", String.valueOf(mailSend.announcementId()),
                        String.valueOf(mailSend.userId()), mailSend.email()));
    }

    /** Step4: 処理中のお知らせを処理済みへ一括更新する。 */
    @Bean
    public Step announcementNotifyMarkProcessedStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            AnnouncementMarkProcessedTasklet announcementMarkProcessedTasklet,
            LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("announcementNotify.04MarkProcessedStep", jobRepository)
                .tasklet(announcementMarkProcessedTasklet, transactionManager)
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    public AnnouncementMarkProcessedTasklet announcementMarkProcessedTasklet(AnnouncementMapper announcementMapper) {
        return new AnnouncementMarkProcessedTasklet(announcementMapper);
    }

    @Bean
    public RegionErrorWarningJobExecutionListener regionErrorWarningJobExecutionListener() {
        return new RegionErrorWarningJobExecutionListener();
    }
}
