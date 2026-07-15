package com.example.batch.sample.job01_csv_import;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * サンプル01: 顧客CSVの入力レコード。
 *
 * <p>形式チェックは Bean Validation アノテーションで宣言し、
 * {@code ValidatingItemProcessorBase} が自動実行する。</p>
 *
 * @param customerCode 顧客コード（英数字10桁以内）
 * @param name         氏名
 * @param email        メールアドレス
 */
public record CustomerCsvRecord(
        @NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9]+", message = "は英数字で入力してください")
        String customerCode,
        @NotBlank @Size(max = 100)
        String name,
        @NotBlank @Email @Size(max = 255)
        String email) {
}
