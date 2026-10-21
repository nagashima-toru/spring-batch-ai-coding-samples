package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

import com.example.batch.core.exception.BusinessException;

class ByteLengthLineTokenizerTest {

    private static final Charset SJIS = Charset.forName("Shift_JIS");

    @Test
    void シフトJISの全角混在レコードをバイト長で分割する() {
        // code=5バイト, name=10バイト(全角5文字), amount=6バイト
        var tokenizer = new ByteLengthLineTokenizer(SJIS,
                new String[] {"code", "name", "amount"}, new int[] {5, 10, 6});

        FieldSet fields = tokenizer.tokenize("A0001山田太郎　000100");

        assertThat(fields.readString("code")).isEqualTo("A0001");
        assertThat(fields.readString("name")).isEqualTo("山田太郎");
        assertThat(fields.readString("amount")).isEqualTo("000100");
    }

    @Test
    void 末尾の半角スペースパディングを除去する() {
        var tokenizer = new ByteLengthLineTokenizer(StandardCharsets.UTF_8,
                new String[] {"code", "name"}, new int[] {5, 10});

        FieldSet fields = tokenizer.tokenize("A1   abc       ");

        assertThat(fields.readString("code")).isEqualTo("A1");
        assertThat(fields.readString("name")).isEqualTo("abc");
    }

    @Test
    void バイト長が不一致の行はBusinessExceptionをスローする() {
        var tokenizer = new ByteLengthLineTokenizer(SJIS, new String[] {"code"}, new int[] {5});

        assertThatThrownBy(() -> tokenizer.tokenize("TOOLONGLINE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("バイト長が不正");
    }

    @Test
    void フィールド境界がマルチバイト文字を分断する場合はBusinessExceptionをスローする() {
        // 「あああ」=6バイト。先頭フィールド3バイトの境界が2文字目「あ」の中間に来る
        var tokenizer = new ByteLengthLineTokenizer(SJIS, new String[] {"a", "b"}, new int[] {3, 3});

        assertThatThrownBy(() -> tokenizer.tokenize("あああ"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分断");
    }

    @Test
    void フィールド定義数の不一致はコンストラクタで検出する() {
        assertThatThrownBy(() -> new ByteLengthLineTokenizer(SJIS, new String[] {"a"}, new int[] {1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 表せない文字を含む行の場合＿BusinessExceptionをスローする() {
        var tokenizer = new ByteLengthLineTokenizer(
                Charset.forName("Windows-31J"), new String[] {"a"}, new int[] {4});

        assertThatThrownBy(() -> tokenizer.tokenize("A𠮷B"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("表せない文字");
    }
}
