package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

class CountingItemStreamReaderTest {

    /** 渡された要素を順に返し、尽きたら null を返す小さな Reader。 */
    private static ItemStreamReader<String> readerOf(String... items) {
        Deque<String> queue = new ArrayDeque<>(List.of(items));
        return new ItemStreamReader<>() {
            @Override
            public String read() {
                return queue.poll();
            }
        };
    }

    @Test
    void nullでない件だけを数え終端のnullは数えない() throws Exception {
        var reader = new CountingItemStreamReader<>("cnt", readerOf("a", "b"));
        var context = new ExecutionContext();
        reader.open(context);

        assertThat(reader.read()).isEqualTo("a");
        assertThat(reader.read()).isEqualTo("b");
        assertThat(reader.read()).isNull();
        reader.update(context);

        assertThat(reader.getCount()).isEqualTo(2L);
        assertThat(context.getLong(CountingItemStreamReader.countKey("cnt"))).isEqualTo(2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateで件数を名前とitem_countをつないだキーに保存し委譲先のupdateも呼ぶ() throws Exception {
        ItemStreamReader<String> delegate = mock(ItemStreamReader.class);
        when(delegate.read()).thenReturn("a", (String) null);
        var reader = new CountingItemStreamReader<>("cnt", delegate);
        var context = new ExecutionContext();
        reader.open(context);
        reader.read();

        reader.update(context);

        assertThat(CountingItemStreamReader.countKey("cnt")).isEqualTo("cnt.item.count");
        assertThat(context.getLong("cnt.item.count")).isEqualTo(1L);
        verify(delegate).update(context);
    }

    @Test
    @SuppressWarnings("unchecked")
    void openで保存済みの件数を引き継ぎ続きから数える() throws Exception {
        ItemStreamReader<String> delegate = mock(ItemStreamReader.class);
        when(delegate.read()).thenReturn("x");
        var reader = new CountingItemStreamReader<>("cnt", delegate);
        var context = new ExecutionContext();
        context.putLong("cnt.item.count", 3L);

        reader.open(context);
        reader.read();

        assertThat(reader.getCount()).isEqualTo(4L);
        verify(delegate).open(context);
    }

    @Test
    void openでIntegerで復元された件数も引き継げる() throws Exception {
        var reader = new CountingItemStreamReader<>("cnt", readerOf("a"));
        var context = new ExecutionContext();
        context.putInt("cnt.item.count", 5);

        reader.open(context);
        reader.read();

        assertThat(reader.getCount()).isEqualTo(6L);
    }

    @Test
    void openでキーが無い時は0から数える() throws Exception {
        var reader = new CountingItemStreamReader<>("cnt", readerOf("a"));

        reader.open(new ExecutionContext());
        assertThat(reader.getCount()).isZero();
        reader.read();

        assertThat(reader.getCount()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeを委譲先に伝える() throws Exception {
        ItemStreamReader<String> delegate = mock(ItemStreamReader.class);
        var reader = new CountingItemStreamReader<>("cnt", delegate);

        reader.close();

        verify(delegate).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 委譲先の例外はそのまま投げて数えない() throws Exception {
        ItemStreamReader<String> delegate = mock(ItemStreamReader.class);
        when(delegate.read()).thenThrow(new IllegalStateException("読込失敗"));
        var reader = new CountingItemStreamReader<>("cnt", delegate);
        reader.open(new ExecutionContext());

        assertThatThrownBy(reader::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("読込失敗");
        assertThat(reader.getCount()).isZero();
    }

    @Test
    void nameがnullか空白またはdelegateがnullなら例外() {
        var delegate = readerOf("a");

        assertThatThrownBy(() -> new CountingItemStreamReader<>(null, delegate))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountingItemStreamReader<>("  ", delegate))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountingItemStreamReader<String>("cnt", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
