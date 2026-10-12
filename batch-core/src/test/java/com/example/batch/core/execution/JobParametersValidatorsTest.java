package com.example.batch.core.execution;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

class JobParametersValidatorsTest {

    @Test
    void 必須パラメータが揃っていれば検証を通過する() {
        JobParametersValidator validator = JobParametersValidators.required("importDate");
        JobParameters params = new JobParametersBuilder().addString("importDate", "2026-07-14").toJobParameters();

        assertThatCode(() -> validator.validate(params)).doesNotThrowAnyException();
    }

    @Test
    void 必須パラメータが欠けている場合は例外をスローする() {
        JobParametersValidator validator = JobParametersValidators.required("importDate");

        assertThatThrownBy(() -> validator.validate(new JobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void 必須キーのみ指定で未知のキーがある場合＿検証を通過する() {
        JobParametersValidator validator = JobParametersValidators.required("importDate");
        JobParameters params = new JobParametersBuilder()
                .addString("importDate", "2026-07-14")
                .addString("unknownKey", "x")
                .toJobParameters();

        assertThatCode(() -> validator.validate(params)).doesNotThrowAnyException();
    }

    @Test
    void 必須と任意を指定し未知のキーがある場合＿検証を通過する() {
        JobParametersValidator validator = JobParametersValidators.requiredAndOptional(
                new String[] {"importDate"}, new String[] {"dryRun"});
        JobParameters params = new JobParametersBuilder()
                .addString("importDate", "2026-07-14")
                .addString("unknownKey", "x")
                .toJobParameters();

        assertThatCode(() -> validator.validate(params)).doesNotThrowAnyException();
    }

    @Test
    void 任意パラメータは無くても検証を通過する() {
        JobParametersValidator validator = JobParametersValidators.requiredAndOptional(
                new String[] {"importDate"}, new String[] {"dryRun"});
        JobParameters params = new JobParametersBuilder().addString("importDate", "2026-07-14").toJobParameters();

        assertThatCode(() -> validator.validate(params)).doesNotThrowAnyException();
    }
}
