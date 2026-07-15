package com.example.batch.sample.job06_flow_job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.example.batch.core.exception.SystemException;
import com.example.batch.sample.common.WorkRecord;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class WorkItemProcessorTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final WorkItemProcessor processor = new WorkItemProcessor(VALIDATOR_FACTORY.getValidator());

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void 正常レコードはワークレコードへ変換する() throws Exception {
        WorkRecord record = processor.process(new WorkCsvRecord("W001", "100"));

        assertThat(record.code()).isEqualTo("W001");
        assertThat(record.amount()).isEqualByComparingTo("100");
    }

    @Test
    void 負数レコードはSystemExceptionをスローしジョブを失敗させる() {
        // リスタートデモ用の意図的失敗（Skip 対象外の SystemException であることが重要）
        assertThatThrownBy(() -> processor.process(new WorkCsvRecord("W005", "-1")))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("負数");
    }
}
