package com.example.batch.core.item;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import com.example.batch.core.exception.BusinessException;

/**
 * 文字コードで表せない文字を例外にする strict エンコードの共通ヘルパー。
 *
 * <p>{@link ByteLengthLineAggregator} と {@link ByteLengthLineTokenizer} が利用する。
 * 例外のメッセージには値を含めず、1始まりの文字位置（コードポイント単位）とコードポイントだけを示す
 * （メッセージはWARN/ERRORログに出るため、個人情報・機密情報を含めない）。</p>
 */
final class StrictCharsetEncoder {

    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private StrictCharsetEncoder() {
    }

    /**
     * 文字列を strict にエンコードする。
     *
     * @param text    エンコード対象
     * @param charset 文字コード
     * @param target  例外メッセージの先頭に付ける対象の説明（値は含めない）
     * @return エンコード後のバイト列
     * @throws BusinessException 文字コードで表せない文字を含む場合
     */
    static byte[] encode(String text, Charset charset, String target) {
        try {
            // エンコーダは状態を持つため、呼び出しのたびに新しく作る
            ByteBuffer buffer = charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new BusinessException(message(text, charset, target), e);
        }
    }

    private static String message(String text, Charset charset, String target) {
        int position = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            position++;
            if (!charset.newEncoder().canEncode(new String(Character.toChars(codePoint)))) {
                String message = "%s: 文字コード %s で表せない文字があります（位置=%d 文字=U+%04X）"
                        .formatted(target, charset.name(), position, codePoint);
                if (codePoint == REPLACEMENT_CHARACTER) {
                    message += "。U+FFFD は読込時に文字コードで解釈できなかったバイト列を置き換えた文字です";
                }
                return message;
            }
            index += Character.charCount(codePoint);
        }
        return "%s: 文字コード %s で表せない文字があります".formatted(target, charset.name());
    }
}
