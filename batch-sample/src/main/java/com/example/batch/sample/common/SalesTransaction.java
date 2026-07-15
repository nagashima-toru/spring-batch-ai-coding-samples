package com.example.batch.sample.common;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 売上明細エンティティ（app.sales_transaction）。
 *
 * @param id           明細ID
 * @param customerCode 顧客コード
 * @param amount       売上金額
 * @param soldAt       売上日
 */
public record SalesTransaction(Long id, String customerCode, BigDecimal amount, LocalDate soldAt) {
}
