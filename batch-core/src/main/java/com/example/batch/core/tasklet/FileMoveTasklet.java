package com.example.batch.core.tasklet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.BusinessException;

/**
 * ファイル移動・退避 Tasklet。
 *
 * <p><b>用途:</b> 取込完了後の入力ファイルを処理済みディレクトリへ退避する、
 * 生成したファイルを連携ディレクトリへ移動する、といった後処理 Step を標準化する。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * FileMoveTasklet archiveTasklet(@Value("#{jobParameters['inputFile']}") String inputFile) {
 *     return new FileMoveTasklet(Path.of(inputFile), Path.of("/data/archive"), true);
 * }
 * }</pre>
 *
 * <p>移動先ディレクトリが存在しない場合は作成する。{@code overwrite=false} で移動先に
 * 同名ファイルが存在する場合は {@link BusinessException} で失敗する。</p>
 *
 * <p><b>拡張ポイント:</b> 退避ファイル名にタイムスタンプを付与したい場合は
 * {@link #resolveDestination(Path)} をオーバーライドする。</p>
 */
public class FileMoveTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FileMoveTasklet.class);

    private final Path sourceFile;
    private final Path destinationDir;
    private final boolean overwrite;

    /**
     * @param sourceFile     移動元ファイル
     * @param destinationDir 移動先ディレクトリ（無ければ作成される）
     * @param overwrite      移動先に同名ファイルがある場合に上書きするか
     */
    public FileMoveTasklet(Path sourceFile, Path destinationDir, boolean overwrite) {
        this.sourceFile = sourceFile;
        this.destinationDir = destinationDir;
        this.overwrite = overwrite;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (!Files.exists(sourceFile)) {
            throw new BusinessException("移動元ファイルが存在しません: " + sourceFile);
        }
        Files.createDirectories(destinationDir);
        Path destination = resolveDestination(destinationDir);
        if (overwrite) {
            Files.move(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            if (Files.exists(destination)) {
                throw new BusinessException("移動先に同名ファイルが既に存在します: " + destination);
            }
            Files.move(sourceFile, destination);
        }
        log.info("ファイルを移動しました: from={} to={}", sourceFile, destination);
        return RepeatStatus.FINISHED;
    }

    /**
     * 移動先のファイルパスを決定する。既定では移動元と同名。
     *
     * @param destinationDir 移動先ディレクトリ
     * @return 移動先ファイルパス
     */
    protected Path resolveDestination(Path destinationDir) {
        return destinationDir.resolve(sourceFile.getFileName());
    }
}
