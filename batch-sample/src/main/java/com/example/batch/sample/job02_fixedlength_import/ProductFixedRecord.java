package com.example.batch.sample.job02_fixedlength_import;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * サンプル02: 商品固定長ファイルの入力レコード。
 *
 * <p>レイアウト（Shift_JIS・バイト長）: 商品コード8 / 商品名20（全角可） / 単価8（数字右詰めゼロ埋め）。
 * パディングは基盤部品のトークナイザが除去済み。</p>
 *
 * @param productCode 商品コード
 * @param productName 商品名
 * @param price       単価（数字文字列。Processor で BigDecimal へ変換）
 */
public record ProductFixedRecord(
        @NotBlank @Size(max = 8)
        String productCode,
        @NotBlank @Size(max = 10)
        String productName,
        @NotBlank @Pattern(regexp = "\\d+", message = "は数字で入力してください")
        String price) {
}
