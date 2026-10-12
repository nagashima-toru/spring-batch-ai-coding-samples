package com.example.batch.sample.job_order_daily_sales;

import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ORDER-001: 受注CSVの入力レコード。
 *
 * <p>全項目を String で受け、形式は Bean Validation で検証する（型変換の失敗をパースエラーにせず、
 * 項目名付きのエラー理由をエラーファイルへ出すため）。値はトリムしない。</p>
 *
 * @param orderNo   受注番号（英数字20文字以内）
 * @param lineNo    明細番号（1〜999999999 の整数）
 * @param orderDate 受注日（yyyy-MM-dd 形式）
 * @param storeCode 店舗コード（英数字10文字以内）
 * @param itemCode  商品コード（英数字20文字以内）
 * @param quantity  数量（1〜999999 の整数）
 * @param unitPrice 単価（0〜999999999 の整数）
 * @param lineSeq   入力ファイル上の行番号（Reader が付与。ヘッダ行が1、最初のデータ行が2）
 */
public record OrderCsvRecord(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9]+")
        String orderNo,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,8}")
        String lineNo,
        @NotBlank @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")
        String orderDate,
        @NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9]+")
        String storeCode,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9]+")
        String itemCode,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,5}")
        String quantity,
        @NotBlank @Pattern(regexp = "0|[1-9][0-9]{0,8}")
        String unitPrice,
        int lineSeq) {

    /** CSV の列名（ヘッダ行と同じ snake_case。並びも同じ）。 */
    static final String[] COLUMN_NAMES = {
        "order_no", "line_no", "order_date", "store_code", "item_code", "quantity", "unit_price"
    };

    /**
     * 1行分の項目群と行番号からレコードを生成する（トリムしない）。
     *
     * @param fieldSet   1行をトークン化した項目群
     * @param lineNumber 入力ファイル上の行番号
     * @return 入力レコード
     */
    public static OrderCsvRecord of(FieldSet fieldSet, int lineNumber) {
        return new OrderCsvRecord(
                fieldSet.readRawString(COLUMN_NAMES[0]),
                fieldSet.readRawString(COLUMN_NAMES[1]),
                fieldSet.readRawString(COLUMN_NAMES[2]),
                fieldSet.readRawString(COLUMN_NAMES[3]),
                fieldSet.readRawString(COLUMN_NAMES[4]),
                fieldSet.readRawString(COLUMN_NAMES[5]),
                fieldSet.readRawString(COLUMN_NAMES[6]),
                lineNumber);
    }
}
