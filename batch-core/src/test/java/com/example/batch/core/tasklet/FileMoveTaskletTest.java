package com.example.batch.core.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.batch.core.exception.BusinessException;

class FileMoveTaskletTest {

    @TempDir
    Path tempDir;

    @Test
    void ファイルを移動先ディレクトリへ移動する() throws Exception {
        Path source = Files.writeString(tempDir.resolve("done.csv"), "data");
        Path destDir = tempDir.resolve("archive");
        var tasklet = new FileMoveTasklet(source, destDir, false);

        tasklet.execute(null, null);

        assertThat(source).doesNotExist();
        assertThat(destDir.resolve("done.csv")).hasContent("data");
    }

    @Test
    void 上書き許可の場合は同名ファイルを置き換える() throws Exception {
        Path source = Files.writeString(tempDir.resolve("done.csv"), "new");
        Path destDir = Files.createDirectories(tempDir.resolve("archive"));
        Files.writeString(destDir.resolve("done.csv"), "old");
        var tasklet = new FileMoveTasklet(source, destDir, true);

        tasklet.execute(null, null);

        assertThat(destDir.resolve("done.csv")).hasContent("new");
    }

    @Test
    void 上書き不可で同名ファイルがある場合はBusinessExceptionをスローする() throws Exception {
        Path source = Files.writeString(tempDir.resolve("done.csv"), "new");
        Path destDir = Files.createDirectories(tempDir.resolve("archive"));
        Files.writeString(destDir.resolve("done.csv"), "old");
        var tasklet = new FileMoveTasklet(source, destDir, false);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("既に存在します");
    }

    @Test
    void 移動元が存在しない場合はBusinessExceptionをスローする() {
        var tasklet = new FileMoveTasklet(tempDir.resolve("missing.csv"), tempDir.resolve("archive"), false);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在しません");
    }
}
