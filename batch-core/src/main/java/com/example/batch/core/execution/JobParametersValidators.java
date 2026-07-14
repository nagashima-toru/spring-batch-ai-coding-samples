package com.example.batch.core.execution;

import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * ジョブパラメータ検証のユーティリティ。
 *
 * <p><b>用途:</b> 全ジョブに必須の {@code JobParametersValidator}（方式設計 §11、
 * コーディング規約 §4.1）の定義を簡潔にする。パラメータ不備を起動直後に検出し、
 * 中途半端な実行を防ぐ。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * @Bean
 * Job importJob(JobRepository jobRepository, Step step) {
 *     return new JobBuilder("importJob", jobRepository)
 *             .validator(JobParametersValidators.required("importDate", "inputFile"))
 *             .start(step)
 *             .build();
 * }
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> 形式チェック（日付範囲等）が必要な場合は
 * {@link JobParametersValidator} をラムダで実装し、必須チェックと組み合わせて
 * ジョブ側で順に呼び出す。</p>
 */
public final class JobParametersValidators {

    private JobParametersValidators() {
    }

    /**
     * 指定キーを必須とする Validator を生成する（未知のキーは許容しない）。
     *
     * @param requiredKeys 必須パラメータ名
     * @return Validator
     */
    public static JobParametersValidator required(String... requiredKeys) {
        return new DefaultJobParametersValidator(requiredKeys, new String[0]);
    }

    /**
     * 必須キーと任意キーを指定する Validator を生成する。
     *
     * @param requiredKeys 必須パラメータ名
     * @param optionalKeys 任意パラメータ名
     * @return Validator
     */
    public static JobParametersValidator requiredAndOptional(String[] requiredKeys, String[] optionalKeys) {
        return new DefaultJobParametersValidator(requiredKeys, optionalKeys);
    }
}
