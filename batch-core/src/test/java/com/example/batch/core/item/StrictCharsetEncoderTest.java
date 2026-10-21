package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import com.example.batch.core.exception.BusinessException;

class StrictCharsetEncoderTest {

    private static final Charset WINDOWS_31J = Charset.forName("Windows-31J");

    @Test
    void 表せる文字だけの場合＿文字コードのバイト列を返す() {
        byte[] bytes = StrictCharsetEncoder.encode("Aあ", WINDOWS_31J, "対象");

        assertThat(bytes).isEqualTo("Aあ".getBytes(WINDOWS_31J));
    }

    @Test
    void 表せない文字を含む場合＿位置とコードポイントを示し値を含まないBusinessExceptionをスローする() {
        assertThatThrownBy(() -> StrictCharsetEncoder.encode("A𠮷B", WINDOWS_31J, "入力の行"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入力の行")
                .hasMessageContaining("位置=2 文字=U+20BB7")
                .hasMessageNotContaining("𠮷")
                .hasMessageNotContaining("A𠮷B");
    }

    @Test
    void 置換文字を含む場合＿読込時の置換であることを示すBusinessExceptionをスローする() {
        String text = "A" + (char) 0xFFFD + "B";

        assertThatThrownBy(() -> StrictCharsetEncoder.encode(text, WINDOWS_31J, "入力の行"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("位置=2 文字=U+FFFD")
                .hasMessageContaining("置き換えた文字");
    }

    @Test
    void 対になっていないサロゲートを含む場合＿BusinessExceptionをスローする() {
        String text = "A" + (char) 0xD842 + "B";

        assertThatThrownBy(() -> StrictCharsetEncoder.encode(text, WINDOWS_31J, "入力の行"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("位置=2 文字=U+D842");
    }
}
