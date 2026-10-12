package com.example.batch.sample.job_order_daily_sales;

import java.time.LocalDate;

/**
 * ORDER-001: 受注明細ワーク（app.order_detail_work）の1行。
 *
 * @param orderNo   受注番号
 * @param lineNo    明細番号
 * @param orderDate 受注日
 * @param storeCode 店舗コード
 * @param itemCode  商品コード
 * @param quantity  数量
 * @param unitPrice 単価（円・税抜）
 * @param amount    明細金額（数量×単価）
 * @param lineSeq   入力ファイル上の行番号
 */
public record OrderDetailWork(
        String orderNo,
        int lineNo,
        LocalDate orderDate,
        String storeCode,
        String itemCode,
        int quantity,
        int unitPrice,
        long amount,
        int lineSeq) {
}
