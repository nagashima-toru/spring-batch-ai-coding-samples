package com.example.batch.sample.job_order_daily_sales;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

import com.example.batch.core.execution.JobParametersValidators;

/**
 * ORDER-001: ジョブパラメータの検証。
 *
 * <p>必須・任意キー（未知のキーは拒否）を基盤部品 {@link JobParametersValidators} に委譲したうえで、
 * {@code scheduledTime} がオフセット付き ISO-8601 として解釈できること、
 * {@code targetDate} が指定された場合に型が {@link LocalDate} であることを検証する。</p>
 */
public class OrderDailySalesJobParametersValidator implements JobParametersValidator {

    private static final String SCHEDULED_TIME = "scheduledTime";
    private static final String TARGET_DATE = "targetDate";

    private final JobParametersValidator delegate = JobParametersValidators.requiredAndOptional(
            new String[] {SCHEDULED_TIME}, new String[] {TARGET_DATE, "inputFile", "errorFile"});

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        delegate.validate(parameters);

        JobParameter<?> scheduledTime = parameters.getParameter(SCHEDULED_TIME);
        if (scheduledTime == null || !(scheduledTime.value() instanceof String text)) {
            throw new InvalidJobParametersException("scheduledTime は文字列で指定してください");
        }
        try {
            OffsetDateTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidJobParametersException(
                    "scheduledTime はオフセット付き ISO-8601 で指定してください（例 2026-10-11T17:00:00Z）: " + text);
        }

        JobParameter<?> targetDate = parameters.getParameter(TARGET_DATE);
        if (targetDate != null && targetDate.type() != LocalDate.class) {
            throw new InvalidJobParametersException(
                    "targetDate の型は java.time.LocalDate で指定してください（例 targetDate=2026-10-11,java.time.LocalDate）: "
                            + targetDate.value());
        }
    }
}
