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
 * <p><b>使い方:</b> Chunk Step の skipListener として登録する。読取・加工時の Skip は
 * 入力型 {@code T}、書込時の Skip は変換後の出力型 {@code S} で通知されるため、
 * それぞれのフォーマッタを渡す。</p>
 * <pre>{@code
 * .faultTolerant()
 * .skipPolicy(new BusinessSkipPolicy(10))
 * .skipListener(new ErrorRecordSkipListener<>(
 *         Path.of("/data/error/customer_error.txt"),
 *         csv -> String.join(",", csv.code(), csv.name()),   // 読取・加工時（入力レコード）
 *         entity -> entity.toString()))                       // 書込時（変換後エンティティ）
 * }</pre>
 *
 * <p>読取時 Skip（パースエラー）は例外から元の行文字列を取得して出力する。
 * WARN ログにはレコードの内容を出さず、原因（{@code BusinessException} ならメッセージ、それ以外は例外のクラス名）だけを出す。
 * 読込時のパースエラーなら行番号も出す（レコードの内容はエラーレコードファイルにだけ残る）。</p>
 *
 * <p><b>拡張ポイント:</b> 出力形式はフォーマッタ関数で調整する。出力先をファイル以外
 * （DB等）にしたい場合は {@link SkipListener} を直接実装した別クラスを作成する。</p>
 *
 * @param <T> 読取・加工時の入力レコード型
 * @param <S> 書込時の出力（変換後）型
 */
public class ErrorRecordSkipListener<T, S> implements SkipListener<T, S> {

    private static final Logger log = LoggerFactory.getLogger(ErrorRecordSkipListener.class);

    private final Path errorFile;
    private final Function<T, String> inputFormatter;
    private final Function<S, String> outputFormatter;
    private final Charset charset;

    /**
     * UTF-8 でエラーレコードファイルを出力するリスナーを作成する。
     *
     * @param errorFile       退避先ファイルパス（親ディレクトリが無ければ作成する）
     * @param inputFormatter  入力レコードを1行の文字列へ変換する関数（読取・加工時 Skip 用）
     * @param outputFormatter 出力レコードを1行の文字列へ変換する関数（書込時 Skip 用）
     */
    public ErrorRecordSkipListener(Path errorFile, Function<T, String> inputFormatter,
            Function<S, String> outputFormatter) {
        this(errorFile, inputFormatter, outputFormatter, StandardCharsets.UTF_8);
    }

    public ErrorRecordSkipListener(Path errorFile, Function<T, String> inputFormatter,
            Function<S, String> outputFormatter, Charset charset) {
        this.errorFile = errorFile;
        this.inputFormatter = inputFormatter;
        this.outputFormatter = outputFormatter;
        this.charset = charset;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        String record = t instanceof FlatFileParseException parseError ? parseError.getInput() : "(読取失敗)";
        String reason = t instanceof FlatFileParseException parseError
                ? readReason(parseError) : "cause=" + describe(t);
        writeErrorRecord(record, t, reason);
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        writeErrorRecord(inputFormatter.apply(item), t, "cause=" + describe(t));
    }

    @Override
    public void onSkipInWrite(S item, Throwable t) {
        writeErrorRecord(outputFormatter.apply(item), t, "cause=" + describe(t));
    }

    /** ログ用の理由（行の内容を含まない）。 */
    private static String readReason(FlatFileParseException e) {
        Throwable cause = e.getCause();
        String detail = cause == null
                ? FlatFileParseException.class.getSimpleName() : describe(cause);
        return "line=" + e.getLineNumber() + " cause=" + detail;
    }

    /** 原因の表記。BusinessException ならメッセージ、それ以外ならクラスの単純名だけを返す。 */
    private static String describe(Throwable t) {
        return t instanceof BusinessException ? t.getMessage() : t.getClass().getSimpleName();
    }

    private synchronized void writeErrorRecord(String record, Throwable t, String logReason) {
        log.warn("レコードをSkipしました。エラーレコードファイルへ退避します: file={} reason={}", errorFile, logReason);
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
