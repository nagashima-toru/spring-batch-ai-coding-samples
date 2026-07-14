package com.example.batch.core.item;

import java.nio.charset.Charset;
import java.util.Arrays;

import org.springframework.batch.infrastructure.item.file.transform.DefaultFieldSet;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.batch.infrastructure.item.file.transform.LineTokenizer;

import com.example.batch.core.exception.BusinessException;

/**
 * バイト長ベースの固定長トークナイザ（マルチバイト対応）。
 *
 * <p><b>用途:</b> レガシーシステム連携で使われる固定長ファイルは「文字数」ではなく
 * 「バイト数」でフィールド幅が定義されることが多い（Shift_JIS で全角=2バイト等）。
 * Spring Batch 標準の {@code FixedLengthTokenizer} は文字数ベースのため、
 * マルチバイト文字を含む固定長レイアウトには本クラスを使用する。</p>
 *
 * <p><b>使い方:</b> 通常は {@code FixedLengthItemReaderFactory} 経由で利用する。
 * 直接使う場合:</p>
 * <pre>{@code
 * var tokenizer = new ByteLengthLineTokenizer(Charset.forName("Shift_JIS"),
 *         new String[] {"code", "name", "amount"}, new int[] {5, 20, 10});
 * }</pre>
 *
 * <p>各フィールドは指定バイト数で切り出し、末尾の空白パディング（半角・全角スペースとも）を
 * 除去して返す。
 * 行のバイト長が定義合計と一致しない場合・フィールド境界がマルチバイト文字の途中に
 * かかる場合は {@link BusinessException} を送出する（Skip 対象として扱える）。</p>
 *
 * <p><b>拡張ポイント:</b> パディング除去の挙動を変えたい場合（全角スペース・ゼロ埋め等）は
 * {@link #stripPadding(String)} をオーバーライドする。</p>
 */
public class ByteLengthLineTokenizer implements LineTokenizer {

    private final Charset charset;
    private final String[] names;
    private final int[] byteLengths;
    private final int totalBytes;

    /**
     * @param charset     ファイルの文字コード（例: Shift_JIS）
     * @param names       フィールド名（レコード型のコンポーネント名と対応させる）
     * @param byteLengths 各フィールドのバイト長
     */
    public ByteLengthLineTokenizer(Charset charset, String[] names, int[] byteLengths) {
        if (names.length != byteLengths.length) {
            throw new IllegalArgumentException(
                    "names と byteLengths の要素数が一致しません: %d != %d".formatted(names.length, byteLengths.length));
        }
        this.charset = charset;
        this.names = names.clone();
        this.byteLengths = byteLengths.clone();
        this.totalBytes = Arrays.stream(byteLengths).sum();
    }

    @Override
    public FieldSet tokenize(String line) {
        if (line == null) {
            line = "";
        }
        byte[] bytes = line.getBytes(charset);
        if (bytes.length != totalBytes) {
            throw new BusinessException(
                    "固定長レコードのバイト長が不正です: expected=%d actual=%d line=%s"
                            .formatted(totalBytes, bytes.length, line));
        }
        String[] values = new String[byteLengths.length];
        int offset = 0;
        for (int i = 0; i < byteLengths.length; i++) {
            byte[] slice = Arrays.copyOfRange(bytes, offset, offset + byteLengths[i]);
            String value = new String(slice, charset);
            // デコード→再エンコードで元のバイト列に戻らない場合、境界がマルチバイト文字を分断している
            if (!Arrays.equals(value.getBytes(charset), slice)) {
                throw new BusinessException(
                        "フィールド境界がマルチバイト文字を分断しています: field=%s line=%s".formatted(names[i], line));
            }
            values[i] = stripPadding(value);
            offset += byteLengths[i];
        }
        return new DefaultFieldSet(values, names);
    }

    /**
     * フィールド値からパディングを除去する。既定では末尾の空白（半角・全角スペースとも）を除去する。
     *
     * @param value 切り出したフィールド値
     * @return パディング除去後の値
     */
    protected String stripPadding(String value) {
        return value.stripTrailing();
    }
}
