package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.core.io.FileSystemResource;

class CsvItemWriterFactoryTest {

    @TempDir
    Path tempDir;

    // RecordFieldExtractor がリフレクションでアクセスするため public にする
    public record Sales(String customerCode, BigDecimal amount) {
    }

    @Test
    void ヘッダとトレーラ付きのCSVを生成する() throws Exception {
        Path out = tempDir.resolve("sales.csv");
        var footer = new SummaryFooterCallback<Sales>(Sales::amount,
                (count, sum) -> "T,%d,%s".formatted(count, sum));
        FlatFileItemWriter<Sales> writer = CsvItemWriterFactory.create("testWriter",
                new FileSystemResource(out.toFile()), Sales.class, StandardCharsets.UTF_8,
                "customer_code,amount", footer, "customerCode", "amount");

        writer.open(new ExecutionContext());
        Chunk<Sales> chunk = Chunk.of(
                new Sales("C001", new BigDecimal("1000")),
                new Sales("C002", new BigDecimal("2500")));
        writer.write(chunk);
        footer.afterWrite(chunk);
        writer.close();

        assertThat(Files.readAllLines(out)).containsExactly(
                "customer_code,amount",
                "C001,1000",
                "C002,2500",
                "T,2,3500");
    }

    @Test
    void 既存ファイルは上書きされる() throws Exception {
        Path out = Files.writeString(tempDir.resolve("sales.csv"), "古い内容\n");
        FlatFileItemWriter<Sales> writer = CsvItemWriterFactory.create("testWriter",
                new FileSystemResource(out.toFile()), Sales.class, StandardCharsets.UTF_8,
                null, null, "customerCode", "amount");

        writer.open(new ExecutionContext());
        writer.write(Chunk.of(new Sales("C001", BigDecimal.ONE)));
        writer.close();

        assertThat(Files.readAllLines(out)).containsExactly("C001,1");
    }
}
