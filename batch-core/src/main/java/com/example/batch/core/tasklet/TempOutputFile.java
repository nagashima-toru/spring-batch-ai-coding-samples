package com.example.batch.core.tasklet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.example.batch.core.exception.SystemException;

/**
 * 一時出力→配置の、一時ファイルのパスを決めて一時ディレクトリを作るヘルパー。
 *
 * <p><b>用途:</b> 一時ファイルへ出力してから {@link FileMoveTasklet} で配置先へ移す方式（一時出力→配置）で、
 * 一時ファイルのパスの決定と一時ディレクトリの作成を担う。途中で失敗しても配置先に不完全なファイルを出さないため。</p>
 *
 * <p><b>使い方:</b> Writer の Bean で {@link #prepare()} の戻り値を出力先にし、
 * 配置用の Tasklet に {@link #tempFile()} と {@link #outputDir()} を渡す。</p>
 * <pre>{@code
 * TempOutputFile temp = TempOutputFile.inSubdirectory(Path.of(outputFile), ".tmp");
 *
 * // Writer の Bean（一時ファイルへ出力する）
 * new FlatFileItemWriterBuilder<Sale>().resource(new FileSystemResource(temp.prepare())) ... ;
 *
 * // 配置用の Step（一時ファイルを配置先へ移す）
 * new FileMoveTasklet(temp.tempFile(), temp.outputDir(), true);
 * }</pre>
 *
 * <p>一時ディレクトリは配置先と同じファイルシステムに置く（別のファイルシステムだと移動がコピーになり、配置先に書きかけのファイルが見える）。
 * 配置先のディレクトリを再帰的に監視する仕組みがある時は、{@link #inDirectory(Path, Path)} で一時ディレクトリを配置先の外に置く。</p>
 *
 * <p>一時ディレクトリと配置先の重なり検査は、正規化した絶対パスの上で行う。配置先ファイルが一時ディレクトリの配下にある場合
 * （一時ディレクトリが配置先ファイル・配置先ディレクトリ・その祖先のいずれかと同じ場合）と、一時ディレクトリが配置先ファイルの配下にある場合を拒否する。
 * 祖先を拒否するのは、一時ディレクトリの配下に配置先が入ると、一時ディレクトリを掃除した時に配置済みのファイルまで消えるおそれがあるため。
 * 重なり検査は正規化したパスの上で行い、シンボリックリンクは解決しないので、実体が同じディレクトリでも検出できない場合がある。</p>
 *
 * <p><b>拡張ポイント:</b> final で継承しない。一時ディレクトリの場所は {@link #inSubdirectory(Path, String)} と
 * {@link #inDirectory(Path, Path)} のどちらで生成するかで選ぶ。</p>
 */
public final class TempOutputFile {

    private final Path outputFile;
    private final Path outputDir;
    private final Path tempDir;
    private final Path tempFile;

    private TempOutputFile(Path outputFile, Path tempDirOrNull, String dirName) {
        Path absolute = outputFile.toAbsolutePath().normalize();
        Path fileName = absolute.getFileName();
        Path parent = absolute.getParent();
        if (fileName == null || parent == null) {
            throw new SystemException("配置先にファイル名がありません: " + outputFile);
        }
        Path resolvedTempDir = tempDirOrNull != null
                ? tempDirOrNull.toAbsolutePath().normalize()
                : parent.resolve(dirName).normalize();
        if (absolute.startsWith(resolvedTempDir) || resolvedTempDir.startsWith(absolute)) {
            throw new SystemException(
                    "一時ディレクトリが配置先と重なります: tempDir=" + resolvedTempDir + " outputFile=" + absolute);
        }
        this.outputFile = absolute;
        this.outputDir = parent;
        this.tempDir = resolvedTempDir;
        this.tempFile = resolvedTempDir.resolve(fileName);
    }

    /**
     * 一時ディレクトリを配置先の親ディレクトリ配下に置く。
     *
     * @param outputFile 配置先ファイル
     * @param dirName    配置先の親ディレクトリ配下の一時ディレクトリ名（単一のディレクトリ名。
     *                   null・空文字・「.」・「..」・「/」か「\」を含む文字列は不可）
     * @return 一時出力ファイル
     * @throws SystemException 一時ディレクトリ名が単一のディレクトリ名でない場合、
     *                         配置先にファイル名が無い場合、または一時ディレクトリが配置先と重なる場合
     */
    public static TempOutputFile inSubdirectory(Path outputFile, String dirName) {
        if (dirName == null || dirName.isEmpty() || ".".equals(dirName) || "..".equals(dirName)
                || dirName.indexOf('/') >= 0 || dirName.indexOf('\\') >= 0) {
            throw new SystemException("一時ディレクトリ名は単一のディレクトリ名にしてください: dirName=" + dirName);
        }
        return new TempOutputFile(outputFile, null, dirName);
    }

    /**
     * 一時ディレクトリを指定のディレクトリにする（配置先の外にも置ける）。
     *
     * @param outputFile 配置先ファイル
     * @param tempDir    一時ディレクトリ（配置先と同じファイルシステムに置く）
     * @return 一時出力ファイル
     * @throws SystemException 配置先にファイル名が無い、または一時ディレクトリが配置先と重なる場合
     */
    public static TempOutputFile inDirectory(Path outputFile, Path tempDir) {
        return new TempOutputFile(outputFile, tempDir, null);
    }

    /**
     * @return 配置先ファイル（絶対パス・正規化済み）
     */
    public Path outputFile() {
        return outputFile;
    }

    /**
     * @return 配置先ディレクトリ（配置先ファイルの親）
     */
    public Path outputDir() {
        return outputDir;
    }

    /**
     * @return 一時ディレクトリ
     */
    public Path tempDir() {
        return tempDir;
    }

    /**
     * @return 一時ファイル（一時ディレクトリ配下の、配置先と同名のファイル）
     */
    public Path tempFile() {
        return tempFile;
    }

    /**
     * 一時ディレクトリを作成し、一時ファイルのパスを返す。
     *
     * @return 一時ファイルのパス
     * @throws SystemException 一時ディレクトリを作成できない場合
     */
    public Path prepare() {
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new SystemException("一時ディレクトリを作成できません: " + tempDir, e);
        }
        return tempFile;
    }
}
