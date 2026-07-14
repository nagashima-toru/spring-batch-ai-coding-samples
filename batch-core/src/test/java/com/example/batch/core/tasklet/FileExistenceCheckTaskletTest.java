package com.example.batch.core.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.BusinessException;

class FileExistenceCheckTaskletTest {

    @TempDir
    Path tempDir;

    @Test
    void ファイルが存在すれば正常終了する() throws Exception {
        Path file = Files.writeString(tempDir.resolve("input.csv"), "data");
        var tasklet = new FileExistenceCheckTasklet(file);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void ファイルが存在しない場合はBusinessExceptionをスローする() {
        var tasklet = new FileExistenceCheckTasklet(tempDir.resolve("missing.csv"));

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在しません");
    }

    @Test
    void パスがディレクトリの場合はBusinessExceptionをスローする() {
        var tasklet = new FileExistenceCheckTasklet(tempDir);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ファイルではありません");
    }
}
