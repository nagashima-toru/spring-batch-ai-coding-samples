package com.example.batch.sample.job05_api_import;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/**
 * {@link InputSourceDecider} の単体テスト。
 */
class InputSourceDeciderTest {

    private final InputSourceDecider decider = new InputSourceDecider();

    private String decide(JobParameters parameters) {
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, "sample05ApiImportJob"), parameters);
        return decider.decide(execution, null).getName();
    }

    @Test
    void inputFileが指定された場合_FILEを返す() {
        JobParameters parameters = new JobParametersBuilder().addString("inputFile", "/tmp/users.json")
                .toJobParameters();

        assertThat(decide(parameters)).isEqualTo(InputSourceDecider.FILE);
    }

    @Test
    void inputFileが未指定の場合_APIを返す() {
        assertThat(decide(new JobParameters())).isEqualTo(InputSourceDecider.API);
    }

    @Test
    void inputFileが空文字の場合_APIを返す() {
        JobParameters parameters = new JobParametersBuilder().addString("inputFile", "").toJobParameters();

        assertThat(decide(parameters)).isEqualTo(InputSourceDecider.API);
    }

    @Test
    void inputFileが空白のみの場合_APIを返す() {
        JobParameters parameters = new JobParametersBuilder().addString("inputFile", " ").toJobParameters();

        assertThat(decide(parameters)).isEqualTo(InputSourceDecider.API);
    }
}
