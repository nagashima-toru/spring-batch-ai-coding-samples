package com.example.batch.core.config;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.batch.autoconfigure.BatchJobLauncherAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.WarningAwareExitCodeGenerator;
import com.example.batch.core.http.BatchHttpClient;
import com.example.batch.core.http.HttpClientProperties;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;

/**
 * batch-core の auto-configuration。
 *
 * <p><b>用途:</b> 基盤部品のうち Bean として共通利用するもの（リスナー・終了コード変換・
 * HTTP Client）を自動登録する。業務アプリは依存を追加するだけで基盤部品を利用できる。</p>
 *
 * <p><b>登録される Bean:</b></p>
 * <ul>
 *   <li>{@link LoggingJobExecutionListener} / {@link LoggingStepExecutionListener} — 標準ログ出力</li>
 *   <li>{@link WarningAwareExitCodeGenerator} — 終了コード規約（0/2/1）への変換。
 *       Boot 標準の ExitCodeGenerator を置き換えるため {@code BatchJobLauncherAutoConfiguration}
 *       より先に評価される</li>
 *   <li>{@link DuplicateRunPreventingJobExecutionListener} — 二重起動防止</li>
 *   <li>{@link BatchHttpClient} — HTTP Client 基盤（設定は {@link HttpClientProperties}）</li>
 * </ul>
 *
 * <p><b>拡張ポイント:</b> いずれの Bean も {@code @ConditionalOnMissingBean} のため、
 * 業務側で同型の Bean を定義すれば置き換えられる。</p>
 */
@AutoConfiguration(before = BatchJobLauncherAutoConfiguration.class)
@EnableConfigurationProperties(HttpClientProperties.class)
public class BatchCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoggingJobExecutionListener loggingJobExecutionListener() {
        return new LoggingJobExecutionListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingStepExecutionListener loggingStepExecutionListener() {
        return new LoggingStepExecutionListener();
    }

    @Bean
    @ConditionalOnMissingBean(ExitCodeGenerator.class)
    public WarningAwareExitCodeGenerator warningAwareExitCodeGenerator() {
        return new WarningAwareExitCodeGenerator();
    }

    /**
     * 二重起動防止リスナー。JobRepository は Boot の Batch 自動構成が後から定義するため
     * {@code @ConditionalOnBean} は使えず（自動構成の評価順序で必ず不成立になる）、
     * クラス存在条件＋実行時注入とする。
     */
    @Bean
    @ConditionalOnClass(JobRepository.class)
    @ConditionalOnMissingBean
    public DuplicateRunPreventingJobExecutionListener duplicateRunPreventingJobExecutionListener(
            JobRepository jobRepository) {
        return new DuplicateRunPreventingJobExecutionListener(jobRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public BatchHttpClient batchHttpClient(HttpClientProperties properties) {
        return new BatchHttpClient(RestClient.builder(), properties);
    }
}
