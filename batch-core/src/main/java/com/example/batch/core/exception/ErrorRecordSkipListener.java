package com.example.batch.core.exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

/**
 * Skip されたレコードをエラーレコードファイルへ退避する共通リスナー。
 *
 * <p><b>用途:</b> 方式設計（docs/architecture.md §5.2）の「Skip したレコードは
 * 元レコード＋エラー理由をファイルへ退避し、手動リカバリを可能にする」を実現する。
 * 出力形式は「{@code 元レコード<TAB>エラー理由}」の1行1レコード。</p>
 *
 * <p><b>使い方:</b> Chunk Step のリスナーとして登録する。</p>
 * <pre>{@code
 * .faultTolerant()
 * .skipPolicy(new BusinessSkipPolicy(10))
 * .listener(new ErrorRecordSkipListener<>(
 *         Path.of("/data/error/customer_error.txt"), CustomerCsvRecord::toString))
 * }</pre>
 *
 * <p>読取時 Skip（パースエラー）は例外から元の行文字列を取得して出力する。
 * 加工・書込時 Skip はコンストラクタで渡したフォーマッタでレコードを文字列化して出力する。</p>
 *
 * <p><b>拡張ポイント:</b> 出力形式を変えたい場合はフォーマッタ関数で調整する。
 * 出力先をファイル以外（DB等）にしたい場合は {@link SkipListener} を直接実装した
 * 別クラスを作成する。</p>
 *
 * @param <T> Skip 対象のレコード型
 */
public class ErrorRecordSkipListener<T> implements SkipListener<T, T> {

    private static final Logger log = LoggerFactory.getLogger(ErrorRecordSkipListener.class);

    private final Path errorFile;
    private final Function<T, String> formatter;
    private final Charset charset;

    /**
     * UTF-8 でエラーレコードファイルを出力するリスナーを作成する。
     *
     * @param errorFile 退避先ファイルパス（親ディレクトリが無ければ作成する）
     * @param formatter レコードを1行の文字列へ変換する関数
     */
    public ErrorRecordSkipListener(Path errorFile, Function<T, String> formatter) {
        this(errorFile, formatter, StandardCharsets.UTF_8);
    }

    public ErrorRecordSkipListener(Path errorFile, Function<T, String> formatter, Charset charset) {
        this.errorFile = errorFile;
        this.formatter = formatter;
        this.charset = charset;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        String record = t instanceof FlatFileParseException parseError ? parseError.getInput() : "(読取失敗)";
        writeErrorRecord(record, t);
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        writeErrorRecord(formatter.apply(item), t);
    }

    @Override
    public void onSkipInWrite(T item, Throwable t) {
        writeErrorRecord(formatter.apply(item), t);
    }

    private synchronized void writeErrorRecord(String record, Throwable t) {
        log.warn("レコードをSkipしました。エラーレコードファイルへ退避します: file={} reason={}", errorFile, t.getMessage());
        try {
            Path parentDir = errorFile.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(errorFile, record + "\t" + t.getMessage() + System.lineSeparator(), charset,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("エラーレコードファイルの書込に失敗しました: " + errorFile, e);
        }
    }
}
