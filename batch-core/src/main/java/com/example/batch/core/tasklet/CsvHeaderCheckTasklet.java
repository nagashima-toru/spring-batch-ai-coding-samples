package com.example.batch.core.tasklet;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.BusinessException;

/**
 * CSV ヘッダ行チェック Tasklet。
 *
 * <p><b>用途:</b> 取込ジョブの前処理 Step として、CSV の1行目（ヘッダ行）が期待する項目名の並びと
 * 完全一致することを検証する。本処理の Step を開始する前に入力ファイルの不備を検出し、早期に異常終了させる。</p>
 *
 * <p><b>使い方:</b> ジョブパラメータからファイルパスを受け取り {@code @StepScope} で Bean 定義する。</p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * CsvHeaderCheckTasklet headerCheckTasklet(@Value("#{jobParameters['inputFile']}") String inputFile) {
 *     return new CsvHeaderCheckTasklet(Path.of(inputFile), StandardCharsets.UTF_8, "order_no", "line_no");
 * }
 * }</pre>
 *
 * <p>1行目をカンマで分割した値の並びを期待値と比較する（順序・件数・大文字小文字・前後空白を含む完全一致。
 * 値はトリムしない）。不一致・空ファイル・読取不可の場合は {@link BusinessException} を送出し、
 * Step は失敗（ジョブは異常終了）する。BOM 付きファイルは先頭項目が一致しないため不一致になる。</p>
 *
 * <p>{@code @StepScope} で使うため final にしない。</p>
 *
 * <p><b>拡張ポイント:</b> ヘッダ行がダブルクォートで囲まれている形式には対応しない。
 * 必要な場合は本クラスを継承して分割処理を差し替えるか、派生 Tasklet を作成する。</p>
 */
public class CsvHeaderCheckTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CsvHeaderCheckTasklet.class);

    private final Path file;
    private final Charset charset;
    private final List<String> expectedColumns;

    /**
     * @param file            検査対象の CSV ファイル
     * @param charset         ファイルの文字コード
     * @param expectedColumns 期待するヘッダ項目名の並び
     */
    public CsvHeaderCheckTasklet(Path file, Charset charset, String... expectedColumns) {
        this.file = file;
        this.charset = charset;
        this.expectedColumns = List.of(expectedColumns);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String firstLine = readFirstLine();
        if (firstLine == null) {
            throw new BusinessException("ヘッダ行がありません（空ファイル）: file=" + file
                    + " 期待=" + String.join(",", expectedColumns));
        }
        List<String> actual = Arrays.asList(firstLine.split(",", -1));
        if (!expectedColumns.equals(actual)) {
            throw new BusinessException("ヘッダ行が想定と異なります: file=" + file
                    + " 期待=" + String.join(",", expectedColumns) + " 実際=" + firstLine);
        }
        log.info("ヘッダ行を確認しました: file={}", file);
        return RepeatStatus.FINISHED;
    }

    private String readFirstLine() {
        try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
            return reader.readLine();
        } catch (IOException e) {
            throw new BusinessException("入力ファイルを読み取れません: file=" + file + " 原因=" + e.getMessage(), e);
        }
    }
}
