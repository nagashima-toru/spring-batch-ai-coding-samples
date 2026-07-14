package com.example.batch.core.item;

import java.nio.charset.Charset;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.RecordFieldSetMapper;
import org.springframework.core.io.Resource;

/**
 * 固定長ファイル用 ItemReader のファクトリ（マルチバイト対応）。
 *
 * <p><b>用途:</b> レガシーシステム連携の固定長ファイル取込ジョブの Reader 生成を標準化する。
 * フィールド幅は<b>バイト数</b>で定義する（{@link ByteLengthLineTokenizer} を使用。
 * Shift_JIS の全角文字を含むレイアウトに対応）。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * FlatFileItemReader<LegacyProductRecord> reader(@Value("#{jobParameters['inputFile']}") String file) {
 *     return FixedLengthItemReaderFactory.create("sample02ProductReader",
 *             new FileSystemResource(file), LegacyProductRecord.class,
 *             Charset.forName("Shift_JIS"),
 *             new String[] {"productCode", "productName", "price"},
 *             new int[] {8, 40, 10});
 * }
 * }</pre>
 *
 * <p>バイト長不正の行は {@code FlatFileParseException} となり Skip 対象として扱える。</p>
 *
 * <p><b>拡張ポイント:</b> パディング規則を変えたい場合は {@link ByteLengthLineTokenizer} を
 * 継承したトークナイザを作成し、{@link FlatFileItemReaderBuilder} で直接組み立てる。</p>
 */
public final class FixedLengthItemReaderFactory {

    private FixedLengthItemReaderFactory() {
    }

    /**
     * 固定長ファイルを読み取る ItemReader を生成する。
     *
     * @param name        Reader 名（ジョブ内で一意にする）
     * @param resource    入力ファイル
     * @param recordType  1行をマッピングするレコード型
     * @param charset     ファイルの文字コード（例: Shift_JIS）
     * @param fieldNames  フィールド名（recordType のコンポーネント名と対応させる）
     * @param byteLengths 各フィールドのバイト長
     * @param <T>         レコード型
     * @return リスタート対応の FlatFileItemReader
     */
    public static <T> FlatFileItemReader<T> create(String name, Resource resource, Class<T> recordType,
            Charset charset, String[] fieldNames, int[] byteLengths) {
        return new FlatFileItemReaderBuilder<T>()
                .name(name)
                .resource(resource)
                .encoding(charset.name())
                .saveState(true)
                .strict(true)
                .lineTokenizer(new ByteLengthLineTokenizer(charset, fieldNames, byteLengths))
                .fieldSetMapper(new RecordFieldSetMapper<>(recordType))
                .build();
    }
}
