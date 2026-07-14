package com.example.batch.core.item;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.file.FlatFileFooterCallback;

/**
 * 件数・合計値トレーラ行を出力するフッタコールバック。
 *
 * <p><b>用途:</b> ファイル抽出ジョブで「トレーラ行に総件数・金額合計を出力する」
 * 定番要件を標準化する。書込件数と任意項目の合計値を集計し、Step 終了時に
 * ファイル末尾へトレーラ行を書き込む。</p>
 *
 * <p><b>使い方:</b> Writer の footerCallback と Step の listener の<b>両方</b>に登録する。</p>
 * <pre>{@code
 * var footer = new SummaryFooterCallback<SalesRecord>(
 *         r -> r.amount(),                              // 合計対象（不要なら null）
 *         (count, sum) -> "T,%d,%s".formatted(count, sum)); // トレーラ行フォーマット
 *
 * var writer = CsvItemWriterFactory.create(..., footer, ...);
 *
 * new StepBuilder("exportStep", jobRepository).<SalesRecord, SalesRecord>chunk(100)
 *     .reader(reader).writer(writer)
 *     .listener(footer)   // ← 書込件数を集計するために必須
 *     .build();
 * }</pre>
 *
 * <p><b>注意:</b> 集計値はメモリ上に保持するため、途中失敗からのリスタートでは件数が
 * 巻き戻らない。トレーラ付きファイル抽出は「最初からやり直す」運用（出力ファイル上書き）
 * を前提とする（docs/architecture.md §6.2）。</p>
 *
 * <p><b>拡張ポイント:</b> 複数項目の合計が必要な場合は本クラスを参考に
 * {@link FlatFileFooterCallback} と {@link ItemWriteListener} を実装した専用クラスを作成する。</p>
 *
 * @param <T> 書き込むレコードの型
 */
public class SummaryFooterCallback<T> implements FlatFileFooterCallback, ItemWriteListener<T> {

    private final Function<T, BigDecimal> amountExtractor;
    private final BiFunction<Long, BigDecimal, String> footerFormatter;

    private final AtomicLong count = new AtomicLong();
    private final AtomicReference<BigDecimal> sum = new AtomicReference<>(BigDecimal.ZERO);

    /**
     * @param amountExtractor 合計対象の値を取り出す関数（合計不要の場合は null）
     * @param footerFormatter （件数, 合計値）からトレーラ行文字列を生成する関数
     */
    public SummaryFooterCallback(Function<T, BigDecimal> amountExtractor,
            BiFunction<Long, BigDecimal, String> footerFormatter) {
        this.amountExtractor = amountExtractor;
        this.footerFormatter = footerFormatter;
    }

    @Override
    public void afterWrite(Chunk<? extends T> items) {
        count.addAndGet(items.size());
        if (amountExtractor != null) {
            for (T item : items) {
                BigDecimal amount = amountExtractor.apply(item);
                if (amount != null) {
                    sum.accumulateAndGet(amount, BigDecimal::add);
                }
            }
        }
    }

    @Override
    public void writeFooter(Writer writer) throws IOException {
        writer.write(footerFormatter.apply(count.get(), sum.get()));
    }

    /** @return これまでの書込件数（テスト・ログ用） */
    public long getCount() {
        return count.get();
    }

    /** @return これまでの合計値（テスト・ログ用） */
    public BigDecimal getSum() {
        return sum.get();
    }
}
