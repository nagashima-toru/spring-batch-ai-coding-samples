package com.example.batch.sample.job06_flow_job;

import java.math.BigDecimal;

/**
 * ワークテーブルエンティティ（app.work_import）。サンプル06で使用する。
 *
 * @param code   コード
 * @param amount 金額
 */
public record WorkRecord(String code, BigDecimal amount) {
}
