package com.example.batch.core.item;

import java.util.List;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.support.AbstractItemCountingItemStreamItemReader;

/**
 * REST API ページング取得用 ItemReader。
 *
 * <p><b>用途:</b> ページング形式の REST API（{@code ?page=N&size=M}）から全件を取得する
 * Chunk Step の Reader。1ページずつ取得するため、全件をメモリに載せない。
 * 読取位置（item count）を ExecutionContext に保存するためリスタートに対応する。</p>
 *
 * <p><b>使い方:</b> ページ取得処理は {@code BatchHttpClient} を使った関数として渡す。</p>
 * <pre>{@code
 * @Bean
 * @StepScope
 * RestApiPagingItemReader<User> reader(BatchHttpClient httpClient) {
 *     return new RestApiPagingItemReader<>("userApiReader",
 *             page -> httpClient.get("/users?page={p}&size={s}",
 *                     new ParameterizedTypeReference<List<User>>() {}, Map.of(), page, 100),
 *             100);
 * }
 * }</pre>
 *
 * <p>取得ページの要素数がページサイズ未満、または空になった時点で読取終了と判定する。</p>
 *
 * <p><b>拡張ポイント:</b> カーソル（トークン）方式のページングには対応しない。
 * その場合は {@link AbstractItemCountingItemStreamItemReader} を継承した専用 Reader を作成する
 * （トークンはリスタートのため ExecutionContext に保存すること）。</p>
 *
 * @param <T> 取得する要素の型
 */
public class RestApiPagingItemReader<T> extends AbstractItemCountingItemStreamItemReader<T> {

    private static final Logger log = LoggerFactory.getLogger(RestApiPagingItemReader.class);

    private final IntFunction<List<T>> pageFetcher;
    private final int pageSize;
    private final int firstPageNumber;

    private List<T> currentPage = List.of();
    private int indexInPage;
    private int nextPageNumber;
    private boolean lastPageReached;

    /**
     * ページ番号 0 始まりの Reader を生成する。
     *
     * @param name        Reader 名（ジョブ内で一意にする）
     * @param pageFetcher ページ番号を受け取り1ページ分の要素を返す関数
     * @param pageSize    APIに要求するページサイズ（終端判定に使用する）
     */
    public RestApiPagingItemReader(String name, IntFunction<List<T>> pageFetcher, int pageSize) {
        this(name, pageFetcher, pageSize, 0);
    }

    /**
     * @param firstPageNumber 先頭ページ番号（0 始まり API なら 0、1 始まりなら 1）
     */
    public RestApiPagingItemReader(String name, IntFunction<List<T>> pageFetcher, int pageSize,
            int firstPageNumber) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize は 1 以上を指定してください: " + pageSize);
        }
        setName(name);
        this.pageFetcher = pageFetcher;
        this.pageSize = pageSize;
        this.firstPageNumber = firstPageNumber;
    }

    @Override
    protected void doOpen() {
        currentPage = List.of();
        indexInPage = 0;
        nextPageNumber = firstPageNumber;
        lastPageReached = false;
    }

    @Override
    protected T doRead() {
        if (indexInPage >= currentPage.size()) {
            if (lastPageReached) {
                return null;
            }
            fetchNextPage();
            if (currentPage.isEmpty()) {
                return null;
            }
        }
        return currentPage.get(indexInPage++);
    }

    private void fetchNextPage() {
        int pageNumber = nextPageNumber++;
        List<T> page = pageFetcher.apply(pageNumber);
        currentPage = page == null ? List.of() : page;
        indexInPage = 0;
        if (currentPage.size() < pageSize) {
            lastPageReached = true;
        }
        log.info("APIページ取得: page={} 件数={}", pageNumber, currentPage.size());
    }

    @Override
    protected void doClose() {
        currentPage = List.of();
    }
}
