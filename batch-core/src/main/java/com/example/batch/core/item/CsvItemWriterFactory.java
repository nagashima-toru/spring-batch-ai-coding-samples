package com.example.batch.core.item;

import java.nio.charset.Charset;

import org.springframework.batch.infrastructure.item.file.FlatFileFooterCallback;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.core.io.WritableResource;
import org.springframework.lang.Nullable;

/**
 * CSV ファイル用 ItemWriter のファクトリ。
 *
 * <p><b>用途:</b> ファイル抽出ジョブの Writer 生成を標準化する。ヘッダ行・トレーラ行
 * （{@link SummaryFooterCallback}）付きの CSV 生成に対応する。
 * 既存ファイルは削除して上書きする（再実行で同じ結果になる冪等設計。
 * docs/architecture.md §6.2）。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * FlatFileItemWriter<SalesRecord> writer(@Value("#{jobParameters['outputFile']}") String file) {
 *     return CsvItemWriterFactory.create("sample03SalesWriter",
 *             new FileSystemResource(file), SalesRecord.class, StandardCharsets.UTF_8,
 *             "customer_code,total_amount",   // ヘッダ行（不要なら null）
 *             footerCallback,                  // トレーラ（不要なら null）
 *             "customerCode", "totalAmount");
 * }
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> 区切り文字・クォート処理の変更が必要な場合は
 * {@link FlatFileItemWriterBuilder} を直接使用してよい（{@code name()} 設定を忘れないこと）。</p>
 */
public final class CsvItemWriterFactory {

    private CsvItemWriterFactory() {
    }

    /**
     * CSV ファイルへ書き込む ItemWriter を生成する。
     *
     * @param name           Writer 名（ジョブ内で一意にする）
     * @param resource       出力ファイル（既存の場合は削除して上書き）
     * @param sourceType     書き込むレコード型
     * @param charset        出力文字コード
     * @param header         ヘッダ行（不要なら null）
     * @param footerCallback トレーラ出力（不要なら null。{@link SummaryFooterCallback} を想定）
     * @param columnNames    出力するフィールド名（出力順）
     * @param <T>            レコード型
     * @return FlatFileItemWriter
     */
    public static <T> FlatFileItemWriter<T> create(String name, WritableResource resource, Class<T> sourceType,
            Charset charset, @Nullable String header, @Nullable FlatFileFooterCallback footerCallback,
            String... columnNames) {
        FlatFileItemWriterBuilder<T> builder = new FlatFileItemWriterBuilder<T>()
                .name(name)
                .resource(resource)
                .encoding(charset.name())
                .shouldDeleteIfExists(true)
                .saveState(true);
        if (header != null) {
            builder.headerCallback(writer -> writer.write(header));
        }
        if (footerCallback != null) {
            builder.footerCallback(footerCallback);
        }
        return builder
                .delimited()
                .sourceType(sourceType)
                .names(columnNames)
                .build();
    }
}
