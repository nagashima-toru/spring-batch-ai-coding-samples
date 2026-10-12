package com.example.batch.sample.job_order_daily_sales;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.example.batch.sample.common.SampleProperties.OrderDailySales;

/**
 * ORDER-001: 実行時パラメータ（省略時の補完を済ませた値）。
 *
 * <p>record は final クラスでスコーププロキシを作れないため Bean にしない。
 * 値が必要な Bean メソッドは {@link #resolve} を呼んで使う（補完規則はここだけに置く）。
 * 日付はすべて {@code scheduledTime} から導出し、実行時刻には依存しない（リスタートで値が変わらない）。</p>
 *
 * @param targetDate 対象日（受注日）
 * @param inputFile  受注CSVのパス
 * @param errorFile  エラーファイルのパス
 */
public record OrderDailySalesRunParameters(LocalDate targetDate, Path inputFile, Path errorFile) {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * ジョブパラメータの省略時の値を補完して実行時パラメータを確定する。
     *
     * @param scheduledTime オフセット付き ISO-8601 の起動時刻（必須）
     * @param targetDate    対象日（省略時は null。省略時は JST に変換した日付の前日）
     * @param inputFile     受注CSVのパス（省略時は null。省略時は入力ディレクトリ配下の既定名）
     * @param errorFile     エラーファイルのパス（省略時は null。省略時はエラーディレクトリ配下の既定名）
     * @param settings      入力・エラーディレクトリの設定
     * @return 補完済みの実行時パラメータ
     */
    public static OrderDailySalesRunParameters resolve(String scheduledTime, LocalDate targetDate, String inputFile,
            String errorFile, OrderDailySales settings) {
        ZonedDateTime scheduledJst = OffsetDateTime.parse(scheduledTime).atZoneSameInstant(JST);
        LocalDate resolvedDate = targetDate != null ? targetDate : scheduledJst.toLocalDate().minusDays(1);
        String dateText = resolvedDate.format(DATE_FORMAT);
        Path resolvedInput = inputFile != null ? Path.of(inputFile)
                : Path.of(settings.inputDir(), "orders_" + dateText + ".csv");
        Path resolvedError = errorFile != null ? Path.of(errorFile)
                : Path.of(settings.errorDir(),
                        "orders_" + dateText + "_" + scheduledJst.format(DATE_TIME_FORMAT) + "_error.txt");
        return new OrderDailySalesRunParameters(resolvedDate, resolvedInput, resolvedError);
    }
}
