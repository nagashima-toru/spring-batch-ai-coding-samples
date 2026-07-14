package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.core.io.FileSystemResource;

class FixedLengthItemWriterFactoryTest {

    private static final Charset SJIS = Charset.forName("Shift_JIS");

    @TempDir
    Path tempDir;

    // RecordFieldExtractor がリフレクションでアクセスするため public にする
    public record Product(String code, String name, String price) {
    }

    @Test
    void シフトJISのバイト長固定長ファイルを生成する() throws Exception {
        Path out = tempDir.resolve("products.dat");
        FlatFileItemWriter<Product> writer = FixedLengthItemWriterFactory.create("testWriter",
                new FileSystemResource(out.toFile()), Product.class, SJIS, null, null,
                new String[] {"code", "name", "price"}, new int[] {5, 10, 6});

        writer.open(new ExecutionContext());
        writer.write(Chunk.of(new Product("P0001", "りんご", "1000")));
        writer.close();

        String line = Files.readAllLines(out, SJIS).getFirst();
        assertThat(line).isEqualTo("P0001りんご    1000  ");
        assertThat(line.getBytes(SJIS)).hasSize(21);
    }
}
