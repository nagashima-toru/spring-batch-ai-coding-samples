package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.core.io.FileSystemResource;

class CsvItemReaderFactoryTest {

    @TempDir
    Path tempDir;

    record Customer(String customerCode, String name, int age) {
    }

    @Test
    void ヘッダ付きCSVをレコード型へマッピングして読み取る() throws Exception {
        Path csv = Files.writeString(tempDir.resolve("customer.csv"), """
                customer_code,name,age
                C001,山田太郎,30
                C002,鈴木花子,25
                """);
        FlatFileItemReader<Customer> reader = CsvItemReaderFactory.create("testReader",
                new FileSystemResource(csv.toFile()), Customer.class,
                StandardCharsets.UTF_8, 1, "customerCode", "name", "age");

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo(new Customer("C001", "山田太郎", 30));
            assertThat(reader.read()).isEqualTo(new Customer("C002", "鈴木花子", 25));
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }

    @Test
    void カラム数が不正な行はFlatFileParseExceptionとなる() throws Exception {
        Path csv = Files.writeString(tempDir.resolve("bad.csv"), "C001,山田\n");
        FlatFileItemReader<Customer> reader = CsvItemReaderFactory.create("testReader",
                new FileSystemResource(csv.toFile()), Customer.class,
                StandardCharsets.UTF_8, 0, "customerCode", "name", "age");

        reader.open(new ExecutionContext());
        try {
            assertThatThrownBy(reader::read).isInstanceOf(FlatFileParseException.class);
        } finally {
            reader.close();
        }
    }
}
