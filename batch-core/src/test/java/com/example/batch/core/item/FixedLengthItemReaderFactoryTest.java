package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.core.io.FileSystemResource;

class FixedLengthItemReaderFactoryTest {

    private static final Charset SJIS = Charset.forName("Shift_JIS");

    @TempDir
    Path tempDir;

    record Product(String productCode, String productName, String price) {
    }

    @Test
    void シフトJIS固定長ファイルをバイト長で読み取る() throws Exception {
        // code=5バイト, name=10バイト(全角5文字ぶん・全角スペース埋め), price=6バイト
        Path file = tempDir.resolve("products.dat");
        Files.write(file, ("P0001りんご　　001000\r\nP0002みかん　　000500\r\n").getBytes(SJIS));

        FlatFileItemReader<Product> reader = FixedLengthItemReaderFactory.create("testReader",
                new FileSystemResource(file.toFile()), Product.class, SJIS,
                new String[] {"productCode", "productName", "price"}, new int[] {5, 10, 6});

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo(new Product("P0001", "りんご", "001000"));
            assertThat(reader.read()).isEqualTo(new Product("P0002", "みかん", "000500"));
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }
}
