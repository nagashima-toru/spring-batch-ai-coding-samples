package com.example.batch.core.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.batch.core.exception.SystemException;

class TempOutputFileTest {

    @TempDir
    Path dir;

    @Test
    void 配下のディレクトリ名を指定した場合＿配置先の親ディレクトリ配下に同名の一時ファイルパスを返す() {
        var temp = TempOutputFile.inSubdirectory(dir.resolve("sales.csv"), "tmp");

        assertThat(temp.tempFile()).isEqualTo(dir.toAbsolutePath().resolve("tmp").resolve("sales.csv"));
        assertThat(temp.outputDir()).isEqualTo(dir.toAbsolutePath());
        assertThat(temp.outputFile()).isEqualTo(dir.toAbsolutePath().resolve("sales.csv"));
        assertThat(temp.tempDir()).isEqualTo(dir.toAbsolutePath().resolve("tmp"));
    }

    @Test
    void 一時ディレクトリを指定した場合＿そのディレクトリ配下に同名の一時ファイルパスを返す() {
        var temp = TempOutputFile.inDirectory(dir.resolve("out").resolve("sales.csv"), dir.resolve("work"));

        assertThat(temp.tempFile()).isEqualTo(dir.toAbsolutePath().resolve("work").resolve("sales.csv"));
        assertThat(temp.outputDir()).isEqualTo(dir.toAbsolutePath().resolve("out"));
    }

    @Test
    void 一時ディレクトリ名がカレントを指す場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("sales.csv");

        assertThatThrownBy(() -> TempOutputFile.inSubdirectory(outputFile, "."))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリ名は単一のディレクトリ名にしてください");
    }

    @Test
    void 一時ディレクトリが配置先と同じパスの場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("tmp");

        assertThatThrownBy(() -> TempOutputFile.inSubdirectory(outputFile, "tmp"))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリが配置先と重なります");
    }

    @Test
    void 一時ディレクトリが配置先の親ディレクトリと同じ場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("sales.csv");

        assertThatThrownBy(() -> TempOutputFile.inDirectory(outputFile, dir))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリが配置先と重なります");
    }

    @Test
    void dirNameに区切り文字を含む場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("sales.csv");

        assertThatThrownBy(() -> TempOutputFile.inSubdirectory(outputFile, "a/b"))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリ名は単一のディレクトリ名にしてください: dirName=a/b");
    }

    @Test
    void dirNameが親ディレクトリを指す場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("sales.csv");

        assertThatThrownBy(() -> TempOutputFile.inSubdirectory(outputFile, ".."))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリ名は単一のディレクトリ名にしてください");
    }

    @Test
    void dirNameが絶対パスの場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("sales.csv");
        String absoluteDirName = dir.resolve("other").toAbsolutePath().toString();

        assertThatThrownBy(() -> TempOutputFile.inSubdirectory(outputFile, absoluteDirName))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリ名は単一のディレクトリ名にしてください");
    }

    @Test
    void dirNameが空文字の場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("sales.csv");

        assertThatThrownBy(() -> TempOutputFile.inSubdirectory(outputFile, ""))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリ名は単一のディレクトリ名にしてください");
    }

    @Test
    void 重なり検査で一時ディレクトリが配置先の祖先の場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("a").resolve("b").resolve("sales.csv");
        Path tempDir = dir.resolve("a");

        assertThatThrownBy(() -> TempOutputFile.inDirectory(outputFile, tempDir))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリが配置先と重なります");
    }

    @Test
    void 重なり検査で一時ディレクトリが配置先ファイルの配下の場合＿SystemExceptionを送出する() {
        Path outputFile = dir.resolve("sales.csv");
        Path tempDir = dir.resolve("sales.csv").resolve("work");

        assertThatThrownBy(() -> TempOutputFile.inDirectory(outputFile, tempDir))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリが配置先と重なります");
    }

    @Test
    void ファイル名の無いパスの場合＿SystemExceptionを送出する() {
        assertThatThrownBy(() -> TempOutputFile.inSubdirectory(Path.of("/"), "tmp"))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("配置先にファイル名がありません");
    }

    @Test
    void prepareを呼んだ場合＿一時ディレクトリを作成し一時ファイルのパスを返す() {
        var temp = TempOutputFile.inSubdirectory(dir.resolve("sales.csv"), "tmp");

        Path result = temp.prepare();

        assertThat(result).isEqualTo(temp.tempFile());
        assertThat(temp.tempDir()).isDirectory();
    }

    @Test
    void 一時ディレクトリを作成できない場合＿SystemExceptionを送出する() throws Exception {
        Files.writeString(dir.resolve("tmp"), "file");
        var temp = TempOutputFile.inSubdirectory(dir.resolve("sales.csv"), "tmp");

        assertThatThrownBy(temp::prepare)
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("一時ディレクトリを作成できません")
                .hasCauseInstanceOf(java.io.IOException.class);
    }
}
