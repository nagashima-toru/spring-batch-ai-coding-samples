package com.example.batch.sample.common;

import java.math.BigDecimal;

/**
 * 商品エンティティ（app.product）。
 *
 * @param productCode 商品コード
 * @param productName 商品名
 * @param price       単価
 */
public record Product(String productCode, String productName, BigDecimal price) {
}
