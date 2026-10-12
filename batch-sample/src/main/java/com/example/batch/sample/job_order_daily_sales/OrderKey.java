package com.example.batch.sample.job_order_daily_sales;

/**
 * ORDER-001: ワークのキー取得結果（既出キーの復元用）。
 *
 * @param orderNo 受注番号
 * @param lineNo  明細番号
 * @param lineSeq 入力ファイル上の行番号
 */
public record OrderKey(String orderNo, int lineNo, int lineSeq) {
}
