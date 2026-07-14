package com.example.batch.core.execution;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;

import com.example.batch.core.exception.SystemException;

/**
 * 二重起動防止リスナー。
 *
 * <p><b>用途:</b> 同一ジョブ（ジョブ名単位）の並行実行を防止する（方式設計 §6.3）。
 * スケジューラ側の同時実行抑止と併せた多層防御であり、手動リラン等での誤起動から
 * データ破損を防ぐ。</p>
 *
 * <p><b>使い方:</b> auto-configuration で Bean 登録されるため、ジョブ定義に listener として追加する。</p>
 * <pre>{@code
 * @Bean
 * Job importJob(JobRepository jobRepository, Step step,
 *         DuplicateRunPreventingJobExecutionListener duplicateRunListener) {
 *     return new JobBuilder("importJob", jobRepository)
 *             .listener(duplicateRunListener)
 *             .start(step)
 *             .build();
 * }
 * }</pre>
 *
 * <p>起動時に同名ジョブの実行中 JobExecution がジョブリポジトリに存在する場合、
 * {@link SystemException} を送出して当該実行を失敗させる。</p>
 *
 * <p><b>拡張ポイント:</b> 「パラメータが異なれば並行実行を許可する」等の要件がある場合は、
 * 本クラスを参考に判定条件を変えたリスナーを業務側で作成する。</p>
 */
public class DuplicateRunPreventingJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(DuplicateRunPreventingJobExecutionListener.class);

    private final JobRepository jobRepository;

    public DuplicateRunPreventingJobExecutionListener(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        Set<JobExecution> running = jobRepository.findRunningJobExecutions(jobName);
        boolean duplicated = running.stream().anyMatch(e -> e.getId() != jobExecution.getId());
        if (duplicated) {
            log.error("二重起動を検知しました: jobName={} 実行中executionIds={}", jobName,
                    running.stream().map(JobExecution::getId).toList());
            throw new SystemException(
                    "ジョブ %s は既に実行中のため起動を中止します（二重起動防止）".formatted(jobName));
        }
    }
}
