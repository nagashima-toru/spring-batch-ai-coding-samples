package com.example.batch.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.core.io.FileSystemResource;

import com.example.batch.core.exception.BusinessException;

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

    @Test
    void 解釈できないバイト列を含む行からリスタートした場合＿置換文字を示すBusinessExceptionを原因とする例外になる()
            throws Exception {
        Charset windows31j = Charset.forName("Windows-31J");
        // 1行目は正常。2行目は「0x81 0x20」(Windows-31J として解釈できない)を含む21バイト
        byte[] line1 = ("P0001りんご　　001000\r\n").getBytes(windows31j);
        byte[] line2Head = "P0002".getBytes(windows31j);
        byte[] invalid = {(byte) 0x81, (byte) 0x20};
        byte[] line2Tail = "              \r\n".getBytes(windows31j);
        var out = new ByteArrayOutputStream();
        out.writeBytes(line1);
        out.writeBytes(line2Head);
        out.writeBytes(invalid);
        out.writeBytes(line2Tail);
        Path file = tempDir.resolve("invalid.dat");
        Files.write(file, out.toByteArray());

        FlatFileItemReader<Product> reader = FixedLengthItemReaderFactory.create("testReader",
                new FileSystemResource(file.toFile()), Product.class, windows31j,
                new String[] {"productCode", "productName", "price"}, new int[] {5, 10, 6});
        var context = new ExecutionContext();
        context.putInt("testReader.read.count", 1);

        reader.open(context);
        try {
            assertThatThrownBy(reader::read)
                    .isInstanceOf(FlatFileParseException.class)
                    .cause()
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("U+FFFD");
        } finally {
            reader.close();
        }
    }
}
