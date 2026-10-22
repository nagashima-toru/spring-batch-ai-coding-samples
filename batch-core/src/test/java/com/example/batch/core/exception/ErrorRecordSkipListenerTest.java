package com.example.batch.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
    void 読取時Skipの場合＿WARNログに行の内容を含めず行番号を出す() {
        var listener = new ErrorRecordSkipListener<String, String>(tempDir.resolve("skipped.txt"), r -> r, r -> r);
        var e = new FlatFileParseException("Parsing error at line: 3 in resource=[x], input=[秘密,行]",
                new BusinessException("固定長レコードのバイト長が不正です: expected=5 actual=11"), "秘密,行", 3);

        List<String> logs = captureLogs(() -> listener.onSkipInRead(e));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("line=3").contains("固定長レコードのバイト長が不正です: expected=5 actual=11")
                .doesNotContain("秘密");
    }

    @Test
    void 読取時Skipで原因がBusinessExceptionでない場合＿原因のクラス名だけを出す() {
        var listener = new ErrorRecordSkipListener<String, String>(tempDir.resolve("skipped.txt"), r -> r, r -> r);
        var e = new FlatFileParseException("Parsing error at line: 3 in resource=[x], input=[秘密,行]",
                new IllegalStateException("値=秘密"), "秘密,行", 3);

        List<String> logs = captureLogs(() -> listener.onSkipInRead(e));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("line=3").contains("IllegalStateException").doesNotContain("秘密");
    }

    @Test
    void 加工時Skipで原因がBusinessExceptionの場合＿WARNログにメッセージを出しレコードの内容を出さない() {
        var listener = new ErrorRecordSkipListener<String, String>(tempDir.resolve("skipped.txt"),
                r -> "秘密:" + r, r -> r);

        List<String> logs = captureLogs(
                () -> listener.onSkipInProcess("A001", new BusinessException("入力チェックエラー: email 形式が不正です")));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("cause=入力チェックエラー: email 形式が不正です").doesNotContain("秘密");
    }

    @Test
    void 加工時Skipで原因がBusinessExceptionでない場合＿原因のクラス名だけを出す() {
        var listener = new ErrorRecordSkipListener<String, String>(tempDir.resolve("skipped.txt"), r -> r, r -> r);

        List<String> logs = captureLogs(
                () -> listener.onSkipInProcess("A001", new IllegalStateException("値=秘密")));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("cause=IllegalStateException").doesNotContain("秘密");
    }

    @Test
    void 書込時Skipで原因がBusinessExceptionでない場合＿原因のクラス名だけを出す() {
        var listener = new ErrorRecordSkipListener<String, String>(tempDir.resolve("skipped.txt"), r -> r, r -> r);

        List<String> logs = captureLogs(() -> listener
                .onSkipInWrite("R1", new IllegalArgumentException("Key (code)=(秘密) already exists")));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("cause=IllegalArgumentException").doesNotContain("秘密");
    }

    @Test
    void 読取時SkipでFlatFileParseException以外の場合＿原因のクラス名だけを出す() {
        var listener = new ErrorRecordSkipListener<String, String>(tempDir.resolve("skipped.txt"), r -> r, r -> r);

        List<String> logs = captureLogs(() -> listener.onSkipInRead(new IllegalStateException("値=秘密")));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("cause=IllegalStateException").doesNotContain("秘密");
    }

    private static List<String> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(ErrorRecordSkipListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
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
