package com.example.batch.sample.job_order_daily_sales;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final String[] REQUIRED_KEYS = {SCHEDULED_TIME};
    private static final String[] OPTIONAL_KEYS = {TARGET_DATE, "inputFile", "errorFile"};

    /** 許可するキー（必須＋任意）。未知のキーの拒否に使う。 */
    private static final Set<String> ALLOWED_KEYS = Stream.concat(Arrays.stream(REQUIRED_KEYS),
            Arrays.stream(OPTIONAL_KEYS)).collect(Collectors.toUnmodifiableSet());

    private final JobParametersValidator delegate =
            JobParametersValidators.requiredAndOptional(REQUIRED_KEYS, OPTIONAL_KEYS);

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        delegate.validate(parameters);

        // Spring Batch 6 の DefaultJobParametersValidator は未知のキーを拒否しないため、ここで明示的に拒否する
        for (String key : parameters.parameters().stream().map(JobParameter::name).toList()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new InvalidJobParametersException("未知のジョブパラメータです: " + key);
            }
        }

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
