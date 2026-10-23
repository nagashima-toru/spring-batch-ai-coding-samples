package com.example.batch.sample.job03_file_export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
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
        Path result = Sample03JobConfig.tempOutputOf(dir.resolve("sales.csv").toString(), null).tempFile();

        assertThat(result).isEqualTo(dir.toAbsolutePath().resolve("tmp").resolve("sales.csv"));
    }

    @Test
    void 一時ディレクトリを指定した場合＿指定のディレクトリ配下に同名の一時ファイルパスを返す(@TempDir Path dir) {
        Path work = dir.resolve("work");

        Path result = Sample03JobConfig.tempOutputOf(dir.resolve("out").resolve("sales.csv").toString(),
                work.toString()).tempFile();

        assertThat(result).isEqualTo(work.toAbsolutePath().resolve("sales.csv"));
    }

    @Test
    void 一時ディレクトリが空文字の場合＿親ディレクトリ配下のtmpに同名の一時ファイルパスを返す(@TempDir Path dir) {
        Path result = Sample03JobConfig.tempOutputOf(dir.resolve("sales.csv").toString(), "").tempFile();

        assertThat(result).isEqualTo(dir.toAbsolutePath().resolve("tmp").resolve("sales.csv"));
    }

    @Test
    void 一時ディレクトリが空白だけの場合＿親ディレクトリ配下のtmpに同名の一時ファイルパスを返す(@TempDir Path dir) {
        Path result = Sample03JobConfig.tempOutputOf(dir.resolve("sales.csv").toString(), "   ").tempFile();

        assertThat(result).isEqualTo(dir.toAbsolutePath().resolve("tmp").resolve("sales.csv"));
    }

    @Test
    void tempDirが空白だけの場合＿起動を拒否しない(@TempDir Path dir) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("outputFile", dir.resolve("sales.csv").toString())
                .addString("tempDir", "   ")
                .toJobParameters();

        assertThatCode(() -> Sample03JobConfig.validateParameters(parameters)).doesNotThrowAnyException();
    }

    @Test
    void formatが空白だけの場合＿起動を拒否しない(@TempDir Path dir) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("outputFile", dir.resolve("sales.csv").toString())
                .addString("format", "   ")
                .toJobParameters();

        assertThatCode(() -> Sample03JobConfig.validateParameters(parameters)).doesNotThrowAnyException();
    }

    @Test
    void tempDirにNUL文字を含む場合＿InvalidJobParametersExceptionで起動を拒否する(@TempDir Path dir) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("outputFile", dir.resolve("sales.csv").toString())
                .addString("tempDir", "work\u0000dir")
                .toJobParameters();

        assertThatThrownBy(() -> Sample03JobConfig.validateParameters(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("パスとして解釈できません");
    }

    @Test
    void outputFileにNUL文字を含む場合＿InvalidJobParametersExceptionで起動を拒否する(@TempDir Path dir) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("outputFile", dir.toString() + "/sales\u0000.csv")
                .toJobParameters();

        assertThatThrownBy(() -> Sample03JobConfig.validateParameters(parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("パスとして解釈できません")
                .message().doesNotContain("\u0000");
    }
}
