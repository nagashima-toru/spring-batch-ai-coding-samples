package com.example.batch.core.item;

import java.nio.charset.Charset;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.core.io.Resource;

/**
 * CSV ファイル用 ItemReader のファクトリ。
 *
 * <p><b>用途:</b> CSV 取込ジョブの Reader 生成を標準化する。リスタート対応
 * （読取位置の ExecutionContext 保存）が有効な {@link FlatFileItemReader} を生成する。</p>
 *
 * <p><b>使い方:</b> レコード型（record クラス）とカラム名の対応で型安全にマッピングする。</p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * FlatFileItemReader<CustomerCsvRecord> reader(@Value("#{jobParameters['inputFile']}") String file) {
 *     return CsvItemReaderFactory.create("sample01CustomerReader",
 *             new FileSystemResource(file), CustomerCsvRecord.class,
 *             StandardCharsets.UTF_8, 1, "customerCode", "name", "email");
 * }
 * }</pre>
 *
 * <p>パース不能行は {@code FlatFileParseException} となり、{@code BusinessSkipPolicy} で
 * Skip 対象として扱える。</p>
 *
 * <p><b>拡張ポイント:</b> 区切り文字・クォート文字の変更や一部カラムのみの取込が必要な場合は、
 * 本ファクトリを使わず {@link FlatFileItemReaderBuilder} を直接使用してよい
 * （その場合も {@code name()} と {@code saveState(true)} を必ず設定しリスタート対応を維持する）。</p>
 */
public final class CsvItemReaderFactory {

    private CsvItemReaderFactory() {
    }

    /**
     * CSV ファイルを読み取る ItemReader を生成する。
     *
     * @param name        Reader 名（ExecutionContext のキーになるためジョブ内で一意にする）
     * @param resource    入力ファイル
     * @param recordType  1行をマッピングするレコード型
     * @param charset     ファイルの文字コード
     * @param linesToSkip 先頭スキップ行数（ヘッダ行がある場合は 1）
     * @param columnNames CSV カラム名（recordType のコンポーネント名と対応させる）
     * @param <T>         レコード型
     * @return リスタート対応の FlatFileItemReader
     */
    public static <T> FlatFileItemReader<T> create(String name, Resource resource, Class<T> recordType,
            Charset charset, int linesToSkip, String... columnNames) {
        return new FlatFileItemReaderBuilder<T>()
                .name(name)
                .resource(resource)
                .encoding(charset.name())
                .linesToSkip(linesToSkip)
                .saveState(true)
                .strict(true)
                .delimited()
                .names(columnNames)
                .targetType(recordType)
                .build();
    }
}
