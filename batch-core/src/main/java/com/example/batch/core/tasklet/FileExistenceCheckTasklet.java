package com.example.batch.core.tasklet;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.BusinessException;

/**
 * ファイル存在チェック Tasklet。
 *
 * <p><b>用途:</b> 取込ジョブの前処理 Step として、入力ファイルの存在・読取可能性を検証する。
 * 本処理の Step を開始する前に入力不備を検出し、早期に異常終了させる。</p>
 *
 * <p><b>使い方:</b> ジョブパラメータからファイルパスを受け取り {@code @StepScope} で Bean 定義する。</p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * FileExistenceCheckTasklet checkTasklet(@Value("#{jobParameters['inputFile']}") String inputFile) {
 *     return new FileExistenceCheckTasklet(Path.of(inputFile));
 * }
 * }</pre>
 *
 * <p>ファイルが存在しない・通常ファイルでない・読取不可の場合は {@link BusinessException} を送出し、
 * Step は失敗（ジョブは異常終了）する。</p>
 *
 * <p><b>拡張ポイント:</b> 「ファイルが無ければ正常スキップ（後続Stepへ進まない）」としたい場合は、
 * 本クラスを使わずジョブの Flow 分岐（ExitStatus）と組み合わせた派生 Tasklet を作成する。</p>
 */
public class FileExistenceCheckTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FileExistenceCheckTasklet.class);

    private final Path targetFile;

    /**
     * @param targetFile 存在チェック対象のファイルパス
     */
    public FileExistenceCheckTasklet(Path targetFile) {
        this.targetFile = targetFile;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (!Files.exists(targetFile)) {
            throw new BusinessException("入力ファイルが存在しません: " + targetFile);
        }
        if (!Files.isRegularFile(targetFile)) {
            throw new BusinessException("入力パスがファイルではありません: " + targetFile);
        }
        if (!Files.isReadable(targetFile)) {
            throw new BusinessException("入力ファイルを読み取れません（権限を確認）: " + targetFile);
        }
        log.info("入力ファイルの存在を確認しました: file={} size={}bytes", targetFile, Files.size(targetFile));
        return RepeatStatus.FINISHED;
    }
}
