package com.example.batch.core.item;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

import org.springframework.batch.infrastructure.item.file.transform.FieldExtractor;
import org.springframework.batch.infrastructure.item.file.transform.LineAggregator;

import com.example.batch.core.exception.BusinessException;

/**
 * バイト長ベースの固定長行アグリゲータ（マルチバイト対応）。
 *
 * <p><b>用途:</b> {@link ByteLengthLineTokenizer} の書込側。レコードの各フィールドを
 * 指定バイト数に半角スペースでパディングして固定長行を生成する。
 * レガシーシステムへ連携する固定長ファイルの出力に使用する。</p>
 *
 * <p><b>使い方:</b> 通常は {@code FixedLengthItemWriterFactory} 経由で利用する。</p>
 *
 * <p>フィールド値が指定バイト長を超える場合は {@link BusinessException} を送出する
 * （切り捨てによるデータ破損を防ぐため）。</p>
 *
 * <p>文字コードで表せない文字（対になっていないサロゲートを含む）を含む場合は
 * {@link BusinessException} を送出する。例外のメッセージには値を含めず、1始まりの文字位置と
 * コードポイント（U+XXXX）を示す。読込時に文字コードとして解釈できなかったバイト列は
 * U+FFFD に置き換わって渡ってくるので、U+FFFD を表せない文字コード（Shift_JIS・Windows-31J など）
 * では、その旨を添えた例外になる。U+FFFD を表せる文字コード（UTF-8 など）では、この置換は
 * 検出しない。</p>
 *
 * <p><b>拡張ポイント:</b> 数値の左詰めゼロ埋め等が必要な場合は
 * {@link #pad(byte[], int, String)} をオーバーライドする。</p>
 *
 * @param <T> 出力するレコードの型
 */
public class ByteLengthLineAggregator<T> implements LineAggregator<T> {

    private static final byte PADDING_BYTE = (byte) ' ';

    private final FieldExtractor<T> fieldExtractor;
    private final Charset charset;
    private final int[] byteLengths;

    /**
     * @param fieldExtractor レコードからフィールド値を抽出する（RecordFieldExtractor 等）
     * @param charset        出力ファイルの文字コード（例: Shift_JIS）
     * @param byteLengths    各フィールドのバイト長
     */
    public ByteLengthLineAggregator(FieldExtractor<T> fieldExtractor, Charset charset, int[] byteLengths) {
        this.fieldExtractor = fieldExtractor;
        this.charset = charset;
        this.byteLengths = byteLengths.clone();
    }

    @Override
    public String aggregate(T item) {
        Object[] fields = fieldExtractor.extract(item);
        if (fields.length != byteLengths.length) {
            throw new IllegalStateException(
                    "抽出フィールド数とバイト長定義数が一致しません: %d != %d".formatted(fields.length, byteLengths.length));
        }
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int i = 0; i < fields.length; i++) {
            String value = Objects.toString(fields[i], "");
            byte[] bytes = StrictCharsetEncoder.encode(value, charset, "出力のフィールド index=" + i);
            if (bytes.length > byteLengths[i]) {
                throw new BusinessException(
                        "フィールド値が固定長 %dバイト を超えています: value=%s".formatted(byteLengths[i], value));
            }
            line.writeBytes(pad(bytes, byteLengths[i], value));
        }
        return new String(line.toByteArray(), charset);
    }

    /**
     * フィールド値を指定バイト長にパディングする。既定では右側を半角スペース埋めする。
     *
     * @param bytes  フィールド値のバイト列（指定バイト長以下であることは検証済み）
     * @param length パディング後のバイト長
     * @param value  元のフィールド値（拡張時の判定用）
     * @return パディング済みバイト列
     */
    protected byte[] pad(byte[] bytes, int length, String value) {
        byte[] padded = new byte[length];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        for (int i = bytes.length; i < length; i++) {
            padded[i] = PADDING_BYTE;
        }
        return padded;
    }
}
