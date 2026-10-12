package com.example.batch.sample.job_order_daily_sales;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/**
 * {@link OrderDailySalesJobParametersValidator} の単体テスト。
 *
 * <p>詳細設計書 ORDER-001 §7「単体テスト」の Validator の観点に基づく。</p>
 */
class OrderDailySalesJobParametersValidatorTest {

    private final OrderDailySalesJobParametersValidator validator = new OrderDailySalesJobParametersValidator();

    private JobParametersBuilder base() {
        return new JobParametersBuilder().addString("scheduledTime", "2026-10-11T17:00:00Z");
    }

    @Test
    void scheduledTimeのみ指定した場合＿検証を通過する() {
        JobParameters parameters = base().toJobParameters();

        assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
    }

    @Test
    void 任意パラメータをすべて正しい型で指定した場合＿検証を通過する() {
        JobParameters parameters = base()
                .addLocalDate("targetDate", LocalDate.of(2026, 10, 11))
                .addString("inputFile", "/data/in/orders.csv")
                .addString("errorFile", "/data/error/e.txt")
                .toJobParameters();

        assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
    }

    @Test
    void scheduledTimeにプラス9時間のオフセットを指定した場合＿検証を通過する() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("scheduledTime", "2026-10-12T02:00:00+09:00").toJobParameters();

        assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
    }

    @Test
    void scheduledTimeが欠落している場合＿InvalidJobParametersExceptionをスローする() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("inputFile", "/data/in/orders.csv").toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("scheduledTime");
    }

    @Test
    void パラメータが空の場合＿InvalidJobParametersExceptionをスローする() {
        assertThatThrownBy(() -> validator.validate(new JobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void scheduledTimeにオフセットが無い場合＿InvalidJobParametersExceptionをスローする() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("scheduledTime", "2026-10-12T02:00:00").toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("scheduledTime")
                .hasMessageContaining("2026-10-12T02:00:00");
    }

    @Test
    void scheduledTimeが不正な文字列の場合＿InvalidJobParametersExceptionをスローする() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("scheduledTime", "not-a-time").toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("not-a-time");
    }

    @Test
    void scheduledTimeが文字列型でない場合＿InvalidJobParametersExceptionをスローする() {
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("scheduledTime", LocalDate.of(2026, 10, 12)).toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("文字列");
    }

    @Test
    void targetDateが文字列型の場合＿InvalidJobParametersExceptionをスローする() {
        JobParameters parameters = base().addString("targetDate", "2026-10-11").toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("targetDate")
                .hasMessageContaining("LocalDate");
    }

    @Test
    void targetDateが不正な日付文字列の文字列型の場合＿型がLocalDateでないとしてInvalidJobParametersExceptionをスローする() {
        JobParameters parameters = base().addString("targetDate", "2026-02-30").toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("targetDate");
    }

    @Test
    void 未知のキーを指定した場合＿InvalidJobParametersExceptionをスローする() {
        JobParameters parameters = base().addString("unknownKey", "x").toJobParameters();

        assertThatThrownBy(() -> validator.validate(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("unknownKey");
    }
}
