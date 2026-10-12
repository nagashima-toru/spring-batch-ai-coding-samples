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
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
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

    record Row(String orderNo, String name, int lineNumber) {
    }

    private static Row toRow(FieldSet fieldSet, int lineNumber) {
        return new Row(fieldSet.readRawString("order_no"), fieldSet.readRawString("name"), lineNumber);
    }

    private FlatFileItemReader<Row> lineNumberReader(Path csv, int linesToSkip) {
        return CsvItemReaderFactory.createWithLineNumber("lineReader", new FileSystemResource(csv.toFile()),
                StandardCharsets.UTF_8, linesToSkip, new String[] {"order_no", "name"},
                CsvItemReaderFactoryTest::toRow);
    }

    @Test
    void 行番号付きReaderでヘッダ1行をスキップする場合＿最初のデータ行の行番号が2になる() throws Exception {
        Path csv = Files.writeString(tempDir.resolve("line.csv"), "order_no,name\nA001,x\nA002,y\n");
        FlatFileItemReader<Row> reader = lineNumberReader(csv, 1);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo(new Row("A001", "x", 2));
            assertThat(reader.read()).isEqualTo(new Row("A002", "y", 3));
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }

    @Test
    void 行番号付きReaderでスキップ行数が0の場合＿最初の行の行番号が1になる() throws Exception {
        Path csv = Files.writeString(tempDir.resolve("noheader.csv"), "A001,x\n");
        FlatFileItemReader<Row> reader = lineNumberReader(csv, 0);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo(new Row("A001", "x", 1));
        } finally {
            reader.close();
        }
    }

    @Test
    void 行番号付きReaderで値の前後に空白がある場合＿トリムせずそのまま渡す() throws Exception {
        Path csv = Files.writeString(tempDir.resolve("space.csv"), "order_no,name\n A001 , x \n");
        FlatFileItemReader<Row> reader = lineNumberReader(csv, 1);

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isEqualTo(new Row(" A001 ", " x ", 2));
        } finally {
            reader.close();
        }
    }

    @Test
    void 行番号付きReaderで列数が不一致の行＿FlatFileParseExceptionとなり次の行は読める() throws Exception {
        Path csv = Files.writeString(tempDir.resolve("badcount.csv"), "order_no,name\nA001\nA002,y\nA003,a,b\n");
        FlatFileItemReader<Row> reader = lineNumberReader(csv, 1);

        reader.open(new ExecutionContext());
        try {
            assertThatThrownBy(reader::read).isInstanceOf(FlatFileParseException.class);
            assertThat(reader.read()).isEqualTo(new Row("A002", "y", 3));
            assertThatThrownBy(reader::read).isInstanceOf(FlatFileParseException.class);
        } finally {
            reader.close();
        }
    }

    @Test
    void 行番号付きReaderをリスタートする場合＿保存した読取位置の続きから読み行番号も続きから振られる() throws Exception {
        Path csv = Files.writeString(tempDir.resolve("restart.csv"),
                "order_no,name\nA001,x\nA002,y\nA003,z\nA004,w\n");
        ExecutionContext context = new ExecutionContext();
        FlatFileItemReader<Row> first = lineNumberReader(csv, 1);
        first.open(context);
        try {
            assertThat(first.read()).isEqualTo(new Row("A001", "x", 2));
            assertThat(first.read()).isEqualTo(new Row("A002", "y", 3));
            first.update(context);
        } finally {
            first.close();
        }
        FlatFileItemReader<Row> second = lineNumberReader(csv, 1);

        second.open(context);
        try {
            assertThat(second.read()).isEqualTo(new Row("A003", "z", 4));
            assertThat(second.read()).isEqualTo(new Row("A004", "w", 5));
            assertThat(second.read()).isNull();
        } finally {
            second.close();
        }
    }
}
