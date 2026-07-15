package com.example.batch.sample.job06_flow_job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * サンプル06: ワークCSVの入力レコード。
 *
 * @param code   コード
 * @param amount 金額（数字文字列。負数はシステム異常のデモ用に許容してパースする）
 */
public record WorkCsvRecord(
        @NotBlank @Size(max = 10)
        String code,
        @NotBlank @Pattern(regexp = "-?\\d+", message = "は数値で入力してください")
        String amount) {
}
