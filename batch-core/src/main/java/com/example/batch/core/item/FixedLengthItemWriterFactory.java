package com.example.batch.core.item;

import java.nio.charset.Charset;

import org.springframework.batch.infrastructure.item.file.FlatFileFooterCallback;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.transform.RecordFieldExtractor;
import org.springframework.core.io.WritableResource;
import org.springframework.lang.Nullable;

/**
 * 固定長ファイル用 ItemWriter のファクトリ（マルチバイト対応）。
 *
 * <p><b>用途:</b> レガシーシステムへ連携する固定長ファイルの生成を標準化する。
 * フィールド幅は<b>バイト数</b>で定義し、半角スペースで右埋めする
 * （{@link ByteLengthLineAggregator} を使用）。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * FlatFileItemWriter<LegacyRecord> writer(@Value("#{jobParameters['outputFile']}") String file) {
 *     return FixedLengthItemWriterFactory.create("legacyWriter",
 *             new FileSystemResource(file), LegacyRecord.class, Charset.forName("Shift_JIS"),
 *             null, null,
 *             new String[] {"code", "name", "amount"}, new int[] {8, 40, 10});
 * }
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> ゼロ埋め等のパディング規則は {@link ByteLengthLineAggregator} を
 * 継承して差し替え、{@link FlatFileItemWriterBuilder} で直接組み立てる。</p>
 */
public final class FixedLengthItemWriterFactory {

    private FixedLengthItemWriterFactory() {
    }

    /**
     * 固定長ファイルへ書き込む ItemWriter を生成する。
     *
     * @param name           Writer 名（ジョブ内で一意にする）
     * @param resource       出力ファイル（既存の場合は削除して上書き）
     * @param sourceType     書き込むレコード型
     * @param charset        出力文字コード（例: Shift_JIS）
     * @param header         ヘッダ行（不要なら null）
     * @param footerCallback トレーラ出力（不要なら null）
     * @param fieldNames     出力するフィールド名（出力順）
     * @param byteLengths    各フィールドのバイト長
     * @param <T>            レコード型
     * @return FlatFileItemWriter
     */
    public static <T> FlatFileItemWriter<T> create(String name, WritableResource resource, Class<T> sourceType,
            Charset charset, @Nullable String header, @Nullable FlatFileFooterCallback footerCallback,
            String[] fieldNames, int[] byteLengths) {
        RecordFieldExtractor<T> extractor = new RecordFieldExtractor<>(sourceType);
        extractor.setNames(fieldNames);
        FlatFileItemWriterBuilder<T> builder = new FlatFileItemWriterBuilder<T>()
                .name(name)
                .resource(resource)
                .encoding(charset.name())
                .shouldDeleteIfExists(true)
                .saveState(true)
                .lineAggregator(new ByteLengthLineAggregator<>(extractor, charset, byteLengths));
        if (header != null) {
            builder.headerCallback(writer -> writer.write(header));
        }
        if (footerCallback != null) {
            builder.footerCallback(footerCallback);
        }
        return builder.build();
    }
}
