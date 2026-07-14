package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.json.JsonItemReader;
import org.springframework.core.io.FileSystemResource;

class JsonItemReaderFactoryTest {

    @TempDir
    Path tempDir;

    record Order(String orderId, int amount) {
    }

    @Test
    void JSON配列ファイルを1要素ずつ読み取る() throws Exception {
        Path json = Files.writeString(tempDir.resolve("orders.json"), """
                [
                  {"orderId": "O-1", "amount": 1000},
                  {"orderId": "O-2", "amount": 2000}
                ]
                """);
        JsonItemReader<Order> reader = JsonItemReaderFactory.create("testReader",
                new FileSystemResource(json.toFile()), Order.class);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo(new Order("O-1", 1000));
            assertThat(reader.read()).isEqualTo(new Order("O-2", 2000));
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }
}
