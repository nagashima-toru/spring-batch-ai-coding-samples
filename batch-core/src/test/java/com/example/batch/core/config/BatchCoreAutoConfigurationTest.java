package com.example.batch.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.batch.core.execution.DuplicateRunPreventingJobExecutionListener;
import com.example.batch.core.execution.WarningAwareExitCodeGenerator;
import com.example.batch.core.http.BatchHttpClient;
import com.example.batch.core.listener.LoggingJobExecutionListener;
import com.example.batch.core.listener.LoggingStepExecutionListener;

class BatchCoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BatchCoreAutoConfiguration.class));

    @Test
    void 基盤部品のBeanが自動登録される() {
        runner.withUserConfiguration(JobRepositoryMockConfig.class).run(context -> {
            assertThat(context).hasSingleBean(LoggingJobExecutionListener.class);
            assertThat(context).hasSingleBean(LoggingStepExecutionListener.class);
            assertThat(context).hasSingleBean(WarningAwareExitCodeGenerator.class);
            assertThat(context).hasSingleBean(DuplicateRunPreventingJobExecutionListener.class);
            assertThat(context).hasSingleBean(BatchHttpClient.class);
        });
    }

    @Test
    void JobRepositoryが無い場合は二重起動防止リスナーを登録しない() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DuplicateRunPreventingJobExecutionListener.class);
            assertThat(context).hasSingleBean(BatchHttpClient.class);
        });
    }

    @Test
    void 業務側でExitCodeGeneratorを定義した場合は置き換えられる() {
        runner.withUserConfiguration(CustomExitCodeConfig.class).run(context -> {
            assertThat(context).doesNotHaveBean(WarningAwareExitCodeGenerator.class);
            assertThat(context).hasSingleBean(ExitCodeGenerator.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class JobRepositoryMockConfig {
        @Bean
        JobRepository jobRepository() {
            return mock(JobRepository.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomExitCodeConfig {
        @Bean
        ExitCodeGenerator customExitCodeGenerator() {
            return () -> 99;
        }
    }
}
