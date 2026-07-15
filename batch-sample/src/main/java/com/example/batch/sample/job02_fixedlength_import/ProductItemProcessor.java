package com.example.batch.sample.job02_fixedlength_import;

import java.math.BigDecimal;

import com.example.batch.core.item.ValidatingItemProcessorBase;
import com.example.batch.sample.common.Product;

import jakarta.validation.Validator;

/**
 * サンプル02: 商品固定長レコードの入力チェック＋エンティティ変換 Processor。
 */
public class ProductItemProcessor extends ValidatingItemProcessorBase<ProductFixedRecord, Product> {

    public ProductItemProcessor(Validator validator) {
        super(validator);
    }

    @Override
    protected Product doProcess(ProductFixedRecord item) {
        // ゼロ埋め数字文字列（例: 00001000）を数値へ変換
        return new Product(item.productCode(), item.productName(), new BigDecimal(item.price()));
    }
}
