package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class RestApiPagingItemReaderTest {

    @Test
    void 全ページを順に読み取り最終ページで終了する() throws Exception {
        // page0: 2件(満杯), page1: 1件(ページサイズ未満=最終)
        var reader = new RestApiPagingItemReader<String>("testReader",
                page -> switch (page) {
                    case 0 -> List.of("a", "b");
                    case 1 -> List.of("c");
                    default -> throw new IllegalStateException("余分なページ取得: " + page);
                }, 2);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo("a");
            assertThat(reader.read()).isEqualTo("b");
            assertThat(reader.read()).isEqualTo("c");
            assertThat(reader.read()).isNull();
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }

    @Test
    void 最初のページが空の場合はnullを返す() throws Exception {
        var fetchCount = new AtomicInteger();
        var reader = new RestApiPagingItemReader<String>("testReader", page -> {
            fetchCount.incrementAndGet();
            return List.of();
        }, 10);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isNull();
            assertThat(fetchCount.get()).isEqualTo(1);
        } finally {
            reader.close();
        }
    }

    @Test
    void ちょうどページサイズで終わる場合は次の空ページで終了と判定する() throws Exception {
        var reader = new RestApiPagingItemReader<String>("testReader",
                page -> page == 0 ? List.of("a", "b") : List.of(), 2);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo("a");
            assertThat(reader.read()).isEqualTo("b");
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }

    @Test
    void 先頭ページ番号を1始まりにできる() throws Exception {
        var reader = new RestApiPagingItemReader<String>("testReader",
                page -> page == 1 ? List.of("x") : List.of(), 10, 1);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo("x");
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }

    @Test
    void 不正なページサイズは指定できない() {
        assertThatThrownBy(() -> new RestApiPagingItemReader<String>("r", p -> List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
