package com.example.batch.core.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.BusinessException;

/**
 * {@link CsvHeaderCheckTasklet} の単体テスト。
 *
 * <p>詳細設計書 ORDER-001 §7「単体テスト」の CsvHeaderCheckTasklet の観点に基づく。</p>
 */
class CsvHeaderCheckTaskletTest {

    private static final String[] EXPECTED = {"order_no", "line_no", "quantity"};

    @TempDir
    Path tempDir;

    private Path csv(String content) throws Exception {
        return Files.writeString(tempDir.resolve("input.csv"), content, StandardCharsets.UTF_8);
    }

    private CsvHeaderCheckTasklet tasklet(Path file) {
        return new CsvHeaderCheckTasklet(file, StandardCharsets.UTF_8, EXPECTED);
    }

    @Test
    void ヘッダ行が期待値と一致する場合＿FINISHEDで終了する() throws Exception {
        Path file = csv("order_no,line_no,quantity\nA001,1,5\n");

        RepeatStatus status = tasklet(file).execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void ヘッダ行のみでデータ行が無い場合＿FINISHEDで終了する() throws Exception {
        Path file = csv("order_no,line_no,quantity");

        RepeatStatus status = tasklet(file).execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void 項目名が異なる場合＿期待値と実際の1行目を含むBusinessExceptionをスローする() throws Exception {
        Path file = csv("order_no,line_no,qty\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("期待=order_no,line_no,quantity")
                .hasMessageContaining("実際=order_no,line_no,qty");
    }

    @Test
    void 項目の順序が異なる場合＿BusinessExceptionをスローする() throws Exception {
        Path file = csv("line_no,order_no,quantity\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ヘッダ行が想定と異なります");
    }

    @Test
    void 列数が少ない場合＿BusinessExceptionをスローする() throws Exception {
        Path file = csv("order_no,line_no\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("実際=order_no,line_no");
    }

    @Test
    void 列数が多い場合＿BusinessExceptionをスローする() throws Exception {
        Path file = csv("order_no,line_no,quantity,extra\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ヘッダ行が想定と異なります");
    }

    @Test
    void 末尾にカンマが余分にある場合＿空の項目が増えるためBusinessExceptionをスローする() throws Exception {
        Path file = csv("order_no,line_no,quantity,\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 空ファイルの場合＿空ファイルを示すBusinessExceptionをスローする() throws Exception {
        Path file = csv("");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("空ファイル");
    }

    @Test
    void BOM付きファイルの場合＿BOMを除いて比較し一致すればFINISHEDを返す() throws Exception {
        Path file = csv("﻿order_no,line_no,quantity\n");

        RepeatStatus status = tasklet(file).execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void BOM付きで項目名が異なる場合＿BusinessExceptionをスローする() throws Exception {
        Path file = csv("﻿order_no,line_no,qty\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ヘッダ行が想定と異なります");
    }

    @Test
    void 項目の前後に空白がある場合＿トリムせずBusinessExceptionをスローする() throws Exception {
        Path file = csv("order_no, line_no,quantity\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ヘッダ行が想定と異なります");
    }

    @Test
    void 大文字小文字が異なる場合＿BusinessExceptionをスローする() throws Exception {
        Path file = csv("ORDER_NO,line_no,quantity\n");

        assertThatThrownBy(() -> tasklet(file).execute(null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ファイルが存在しない場合＿読取不可のBusinessExceptionをスローする() {
        Path missing = tempDir.resolve("missing.csv");

        assertThatThrownBy(() -> tasklet(missing).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("読み取れません");
    }
}
