package com.example.batch.core.item;

import org.springframework.batch.infrastructure.item.json.JacksonJsonObjectReader;
import org.springframework.batch.infrastructure.item.json.JsonItemReader;
import org.springframework.batch.infrastructure.item.json.builder.JsonItemReaderBuilder;
import org.springframework.core.io.Resource;

/**
 * JSON ファイル用 ItemReader のファクトリ。
 *
 * <p><b>用途:</b> JSON 配列形式のファイル（{@code [{...}, {...}]}）を1要素ずつ読み取る
 * Reader の生成を標準化する。全件をメモリに載せず逐次パースするため大量データにも使用できる。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * JsonItemReader<OrderJsonRecord> reader(@Value("#{jobParameters['inputFile']}") String file) {
 *     return JsonItemReaderFactory.create("orderJsonReader",
 *             new FileSystemResource(file), OrderJsonRecord.class);
 * }
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> 日付フォーマット等のデシリアライズ設定を変えたい場合は、
 * 設定済み {@code JsonMapper} を渡す {@code JacksonJsonObjectReader} を生成して
 * {@code JsonItemReaderBuilder} を直接使用する（Spring Batch 6 は Jackson 3 系
 * {@code tools.jackson} を使用する点に注意）。</p>
 */
public final class JsonItemReaderFactory {

    private JsonItemReaderFactory() {
    }

    /**
     * JSON 配列ファイルを読み取る ItemReader を生成する。
     *
     * @param name     Reader 名（ジョブ内で一意にする）
     * @param resource 入力ファイル（UTF-8）
     * @param itemType 配列要素をマッピングする型
     * @param <T>      要素型
     * @return リスタート対応の JsonItemReader
     */
    public static <T> JsonItemReader<T> create(String name, Resource resource, Class<T> itemType) {
        return new JsonItemReaderBuilder<T>()
                .name(name)
                .resource(resource)
                .jsonObjectReader(new JacksonJsonObjectReader<>(itemType))
                .saveState(true)
                .build();
    }
}
