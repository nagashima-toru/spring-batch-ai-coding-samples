package com.example.batch.core.item;

import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

/**
 * 行番号付きで {@link FieldSet} をレコードへ変換する関数型インタフェース。
 *
 * <p><b>用途:</b> {@link CsvItemReaderFactory#createWithLineNumber} で、読み取った1行の項目群に
 * 入力ファイル上の行番号を加えてレコードを生成する（重複検出など、行番号を使う処理のため）。</p>
 *
 * <p><b>使い方:</b> レコード型の static メソッドをメソッド参照で渡す。</p>
 * <pre>{@code
 * LineNumberedFieldSetMapper<OrderCsvRecord> mapper = OrderCsvRecord::of;
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> 変換中の例外は {@code FlatFileItemReader} が {@code FlatFileParseException} に包む。</p>
 *
 * @param <T> 変換後のレコード型
 */
@FunctionalInterface
public interface LineNumberedFieldSetMapper<T> {

    /**
     * 1行分の項目群と行番号からレコードを生成する。
     *
     * @param fieldSet   1行をトークン化した項目群
     * @param lineNumber 入力ファイル先頭からの行番号（ヘッダ行を含めて1始まり）
     * @return 変換後のレコード
     * @throws BindException 変換に失敗した場合
     */
    T mapFieldSet(FieldSet fieldSet, int lineNumber) throws BindException;
}
