package com.example.batch.core.item;

import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.util.ExecutionContextUserSupport;

/**
 * 読込件数をコミットごとに ExecutionContext へ保存する ItemStreamReader デコレータ。
 *
 * <p><b>用途:</b> 委譲先 Reader が返した null 以外の件数を数え、チャンクのコミットごとに
 * Step の ExecutionContext へ保存する。件数の突き合わせに使うコミット済みの件数
 * （書込件数＋Skip 件数）が得られる。保存は {@code ItemStream.update} で行うため、
 * Reader の読込位置（{@code <Reader名>.read.count}）と同じくチャンクのトランザクションの中で
 * 確定し、ロールバックしたチャンクの分は残らない。リスタート時は {@code open} で引き継ぐ。</p>
 *
 * <p>終端の null は数えないので、件数は Step の終了状態（COMPLETED・STOPPED・FAILED）に
 * 左右されない。</p>
 *
 * <p><b>使い方:</b> {@code @StepScope} の Reader Bean の戻り値で包む。
 * 件数のキーは {@link #countKey(String)} で得る。</p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * CountingItemStreamReader<User> reader(BatchHttpClient httpClient) {
 *     var delegate = new RestApiPagingItemReader<User>("userApiReader", fetcher, 100);
 *     return new CountingItemStreamReader<>("userApiCount", delegate);
 * }
 * }</pre>
 *
 * <p><b>注意:</b></p>
 * <ul>
 *   <li>名前は委譲先の Reader 名と別の名前にする（キーの衝突を避ける）</li>
 *   <li>スレッドセーフではない。マルチスレッド Step では使わない</li>
 *   <li>委譲先が読込位置を保存しない（{@code saveState=false} でリスタート時に先頭から読み直す）
 *       場合は、引き継いだ件数に同じ件を足し直してしまうため使わない</li>
 * </ul>
 *
 * @param <T> 読み込む要素の型
 */
public class CountingItemStreamReader<T> implements ItemStreamReader<T> {

    /** 件数キーの後ろの部分（{@code <名前>.item.count}）。 */
    public static final String COUNT_KEY_SUFFIX = "item.count";

    private final String name;
    private final ItemStreamReader<T> delegate;
    private long count;

    /**
     * @param name     件数のキーに使う名前（委譲先の Reader 名とは別の名前にする）
     * @param delegate 包む Reader
     */
    public CountingItemStreamReader(String name, ItemStreamReader<T> delegate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name は必須です（空白のみ不可）");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate は必須です");
        }
        this.name = name;
        this.delegate = delegate;
    }

    /**
     * 件数の保存キーを返す。
     *
     * @param name コンストラクタに渡した名前
     * @return {@code <名前>.item.count}
     */
    public static String countKey(String name) {
        return new ExecutionContextUserSupport(name).getKey(COUNT_KEY_SUFFIX);
    }

    @Override
    public T read() throws Exception {
        T item = delegate.read();
        if (item != null) {
            count++;
        }
        return item;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        delegate.open(executionContext);
        String key = countKey(name);
        Object saved = executionContext.get(key);
        count = saved instanceof Number number ? number.longValue() : 0L;
    }

    @Override
    public void update(ExecutionContext executionContext) {
        delegate.update(executionContext);
        executionContext.putLong(countKey(name), count);
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * @return これまでに数えた件数（リスタート時は引き継いだ分を含む）
     */
    public long getCount() {
        return count;
    }
}
