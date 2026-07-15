package com.example.batch.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

class ErrorRecordSkipListenerTest {

    @TempDir
    Path tempDir;

    @Test
    void 加工時Skipのレコードをフォーマットして退避する() throws Exception {
        Path errorFile = tempDir.resolve("error/skipped.txt");
        var listener = new ErrorRecordSkipListener<String, String>(errorFile, r -> "record=" + r, r -> "out=" + r);

        listener.onSkipInProcess("A001", new BusinessException("金額が不正"));

        assertThat(Files.readAllLines(errorFile))
                .containsExactly("record=A001\t金額が不正");
    }

    @Test
    void 読取時Skipは元の行を退避する() throws Exception {
        Path errorFile = tempDir.resolve("skipped.txt");
        var listener = new ErrorRecordSkipListener<String, String>(errorFile, r -> r, r -> r);

        listener.onSkipInRead(new FlatFileParseException("パース失敗", "bad,csv,line"));

        assertThat(Files.readAllLines(errorFile))
                .containsExactly("bad,csv,line\tパース失敗");
    }

    @Test
    void 書込時Skipも退避され複数件は追記される() throws Exception {
        Path errorFile = tempDir.resolve("skipped.txt");
        var listener = new ErrorRecordSkipListener<String, String>(errorFile, r -> r, r -> r);

        listener.onSkipInWrite("R1", new BusinessException("重複"));
        listener.onSkipInWrite("R2", new BusinessException("重複"));

        assertThat(Files.readAllLines(errorFile)).hasSize(2);
    }
}
