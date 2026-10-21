package com.example.batch.sample.job03_file_export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;

import com.example.batch.core.item.SummaryFooterCallback;
import com.example.batch.sample.common.SalesTransaction;

/**
 * {@link Sample03JobConfig} の単体テスト（トレーラ行の書式と一時ファイルのパス）。
 */
class Sample03JobConfigTest {

    private final SummaryFooterCallback<SalesTransaction> footer = new Sample03JobConfig().sample03FooterCallback();

    private static SalesTransaction transaction(long id, String amount) {
        return new SalesTransaction(id, "C001", new BigDecimal(amount), LocalDate.of(2026, 10, 1));
    }

    @Test
    void 売上2件を書き込んだ場合＿トレーラが件数と金額合計の形式で出力される() throws IOException {
        footer.afterWrite(Chunk.of(transaction(1, "100.50"), transaction(2, "200")));
        StringWriter writer = new StringWriter();

        footer.writeFooter(writer);

        assertThat(writer).hasToString("T,2,300.50");
    }

    @Test
    void 書き込みが0件の場合＿トレーラが件数0金額0で出力される() throws IOException {
        StringWriter writer = new StringWriter();

        footer.writeFooter(writer);

        assertThat(writer).hasToString("T,0,0");
    }

    @Test
    void 配置先ファイルを指定した場合＿親ディレクトリ配下のtmpに同名の一時ファイルパスを返す(@TempDir Path dir) {
        Path result = Sample03JobConfig.tempFileOf(dir.resolve("sales.csv").toString());

        assertThat(result).isEqualTo(dir.toAbsolutePath().resolve("tmp").resolve("sales.csv"));
    }
}
