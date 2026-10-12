package com.example.batch.sample.job_order_daily_sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.batch.core.exception.BusinessException;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/**
 * {@link OrderItemProcessor} の単体テスト。
 *
 * <p>詳細設計書 ORDER-001 §2「入力チェック仕様」・§7「単体テスト」の OrderItemProcessor の観点に基づく。
 * Mapper はモックにし、Bean Validation は実際の Validator で検証する。</p>
 */
class OrderItemProcessorTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final LocalDate TARGET = LocalDate.of(2026, 10, 11);

    private final OrderDetailWorkMapper mapper = mock(OrderDetailWorkMapper.class);
    private final OrderItemProcessor processor =
            new OrderItemProcessor(VALIDATOR_FACTORY.getValidator(), TARGET, mapper);

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    private static OrderCsvRecord record(String orderNo, String lineNo, String orderDate, String storeCode,
            String itemCode, String quantity, String unitPrice, int lineSeq) {
        return new OrderCsvRecord(orderNo, lineNo, orderDate, storeCode, itemCode, quantity, unitPrice, lineSeq);
    }

    private static OrderCsvRecord valid() {
        return record("A001", "1", "2026-10-11", "S01", "I01", "2", "300", 2);
    }

    private static OrderCsvRecord withOrderNo(String v) {
        return record(v, "1", "2026-10-11", "S01", "I01", "2", "300", 2);
    }

    private static OrderCsvRecord withLineNo(String v) {
        return record("A001", v, "2026-10-11", "S01", "I01", "2", "300", 2);
    }

    private static OrderCsvRecord withOrderDate(String v) {
        return record("A001", "1", v, "S01", "I01", "2", "300", 2);
    }

    private static OrderCsvRecord withStoreCode(String v) {
        return record("A001", "1", "2026-10-11", v, "I01", "2", "300", 2);
    }

    private static OrderCsvRecord withItemCode(String v) {
        return record("A001", "1", "2026-10-11", "S01", v, "2", "300", 2);
    }

    private static OrderCsvRecord withQuantity(String v) {
        return record("A001", "1", "2026-10-11", "S01", "I01", v, "300", 2);
    }

    private static OrderCsvRecord withUnitPrice(String v) {
        return record("A001", "1", "2026-10-11", "S01", "I01", "2", v, 2);
    }

    private static OrderCsvRecord withKey(String orderNo, String lineNo, int lineSeq) {
        return record(orderNo, lineNo, "2026-10-11", "S01", "I01", "2", "300", lineSeq);
    }

    // ---- 正常・変換 ----

    @Test
    void 正常レコードの場合＿ワーク行へ変換し金額は数量と単価の積になる() throws Exception {
        OrderDetailWork work = processor.process(valid());

        assertThat(work).isEqualTo(new OrderDetailWork(
                "A001", 1, TARGET, "S01", "I01", 2, 300, 600L, 2));
    }

    @Test
    void 数量と単価が最大値の場合＿金額がintを超えても桁あふれしない() throws Exception {
        OrderDetailWork work = processor.process(
                record("A001", "1", "2026-10-11", "S01", "I01", "999999", "999999999", 2));

        assertThat(work.amount()).isEqualTo(999_999L * 999_999_999L);
        assertThat(work.amount()).isGreaterThan(Integer.MAX_VALUE);
    }

    @Test
    void 単価が0の場合＿金額0で変換される() throws Exception {
        OrderDetailWork work = processor.process(withUnitPrice("0"));

        assertThat(work.unitPrice()).isZero();
        assertThat(work.amount()).isZero();
    }

    // ---- orderNo ----

    @Test
    void 受注番号が20文字の場合＿通る() {
        assertThatCode(() -> processor.process(withOrderNo("A".repeat(20)))).doesNotThrowAnyException();
    }

    @Test
    void 受注番号が21文字の場合＿桁超過でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withOrderNo("A".repeat(21))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("orderNo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " A001", "A001 ", "A-001", "あ001"})
    void 受注番号が空_空白_前後空白_記号_全角の場合＿BusinessExceptionをスローする(String value) {
        assertThatThrownBy(() -> processor.process(withOrderNo(value)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("orderNo");
    }

    // ---- lineNo ----

    @Test
    void 明細番号が0の場合＿値域外でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withLineNo("0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lineNo");
    }

    @Test
    void 明細番号が1の場合＿下限値として通る() throws Exception {
        assertThat(processor.process(withLineNo("1")).lineNo()).isEqualTo(1);
    }

    @Test
    void 明細番号が999999999の場合＿上限値として通る() throws Exception {
        assertThat(processor.process(withLineNo("999999999")).lineNo()).isEqualTo(999_999_999);
    }

    @Test
    void 明細番号が1000000000の場合＿桁超過でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withLineNo("1000000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lineNo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "01", "-1", "1 ", " 1", "1.0", "abc"})
    void 明細番号が空_先頭ゼロ_負数_空白_小数_非数値の場合＿BusinessExceptionをスローする(String value) {
        assertThatThrownBy(() -> processor.process(withLineNo(value)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lineNo");
    }

    // ---- orderDate ----

    @ParameterizedTest
    @ValueSource(strings = {"", "2026/10/11", "20261011", "2026-1-11", "2026-10-11 ", " 2026-10-11"})
    void 受注日が空_書式違い_前後空白の場合＿BusinessExceptionをスローする(String value) {
        assertThatThrownBy(() -> processor.process(withOrderDate(value)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("orderDate");
    }

    @Test
    void 受注日が実在しない日付2026_02_30の場合＿実在する日付でないBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withOrderDate("2026-02-30")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("実在する日付ではありません")
                .hasMessageContaining("2026-02-30");
    }

    @Test
    void 受注日が対象日と異なる場合＿対象日不一致のBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withOrderDate("2026-10-10")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("対象日と一致しません")
                .hasMessageContaining("2026-10-10");
    }

    // ---- storeCode ----

    @Test
    void 店舗コードが10文字の場合＿通る() {
        assertThatCode(() -> processor.process(withStoreCode("S".repeat(10)))).doesNotThrowAnyException();
    }

    @Test
    void 店舗コードが11文字の場合＿桁超過でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withStoreCode("S".repeat(11))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("storeCode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " S01", "S01 ", "S_01"})
    void 店舗コードが空_空白_前後空白_記号の場合＿BusinessExceptionをスローする(String value) {
        assertThatThrownBy(() -> processor.process(withStoreCode(value)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("storeCode");
    }

    // ---- itemCode ----

    @Test
    void 商品コードが20文字の場合＿通る() {
        assertThatCode(() -> processor.process(withItemCode("I".repeat(20)))).doesNotThrowAnyException();
    }

    @Test
    void 商品コードが21文字の場合＿桁超過でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withItemCode("I".repeat(21))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("itemCode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " I01", "I01 ", "I.01"})
    void 商品コードが空_空白_前後空白_記号の場合＿BusinessExceptionをスローする(String value) {
        assertThatThrownBy(() -> processor.process(withItemCode(value)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("itemCode");
    }

    // ---- quantity ----

    @Test
    void 数量が0の場合＿値域外でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withQuantity("0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void 数量が1の場合＿下限値として通る() throws Exception {
        assertThat(processor.process(withQuantity("1")).quantity()).isEqualTo(1);
    }

    @Test
    void 数量が999999の場合＿上限値として通る() throws Exception {
        assertThat(processor.process(withQuantity("999999")).quantity()).isEqualTo(999_999);
    }

    @Test
    void 数量が1000000の場合＿桁超過でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withQuantity("1000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quantity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "01", "-1", " 1", "1 ", "1.5", "x"})
    void 数量が空_先頭ゼロ_負数_空白_小数_非数値の場合＿BusinessExceptionをスローする(String value) {
        assertThatThrownBy(() -> processor.process(withQuantity(value)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quantity");
    }

    // ---- unitPrice ----

    @Test
    void 単価が999999999の場合＿上限値として通る() throws Exception {
        assertThat(processor.process(withUnitPrice("999999999")).unitPrice()).isEqualTo(999_999_999);
    }

    @Test
    void 単価が1000000000の場合＿桁超過でBusinessExceptionをスローする() {
        assertThatThrownBy(() -> processor.process(withUnitPrice("1000000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unitPrice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "00", "01", "-1", " 0", "0 ", "1.5", "x"})
    void 単価が空_先頭ゼロ_負数_空白_小数_非数値の場合＿BusinessExceptionをスローする(String value) {
        assertThatThrownBy(() -> processor.process(withUnitPrice(value)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unitPrice");
    }

    // ---- 重複検出 ----

    @Test
    void 同じキーが別の行番号で再度現れた場合＿重複でBusinessExceptionをスローする() throws Exception {
        processor.process(withKey("A001", "1", 2));

        assertThatThrownBy(() -> processor.process(withKey("A001", "1", 5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重複")
                .hasMessageContaining("先頭の行=2");
    }

    @Test
    void 同じキーを同じ行番号で再処理した場合＿チャンク再処理とみなしエラーにしない() throws Exception {
        processor.process(withKey("A001", "1", 2));

        OrderDetailWork again = processor.process(withKey("A001", "1", 2));

        assertThat(again.lineSeq()).isEqualTo(2);
    }

    @Test
    void 受注番号か明細番号のどちらかが異なる場合＿別キーとして重複にしない() throws Exception {
        processor.process(withKey("A001", "1", 2));

        assertThatCode(() -> {
            processor.process(withKey("A001", "2", 3));
            processor.process(withKey("A002", "1", 4));
        }).doesNotThrowAnyException();
    }

    @Test
    void 受注番号と明細番号の連結が同じでも区切りが違う場合＿別キーとして重複にしない() throws Exception {
        processor.process(withKey("A1", "11", 2));

        assertThatCode(() -> processor.process(withKey("A11", "1", 3))).doesNotThrowAnyException();
    }

    @Test
    void 先頭レコードが不正で2件目が正常な場合＿2件目は重複扱いにならず通る() {
        assertThatThrownBy(() -> processor.process(withKey("A001", "0", 2)))
                .isInstanceOf(BusinessException.class);

        assertThatCode(() -> processor.process(withKey("A001", "1", 3))).doesNotThrowAnyException();
    }

    @Test
    void 先頭レコードが対象日不一致で2件目が同じキーの正常行の場合＿2件目は通る() {
        assertThatThrownBy(() -> processor.process(
                record("A001", "1", "2026-10-10", "S01", "I01", "2", "300", 2)))
                .isInstanceOf(BusinessException.class);

        assertThatCode(() -> processor.process(withKey("A001", "1", 3))).doesNotThrowAnyException();
    }

    // ---- beforeStep ----

    @SuppressWarnings("unchecked")
    private void stubKeys(List<OrderKey> keys) {
        doAnswer(invocation -> {
            ResultHandler<OrderKey> handler = invocation.getArgument(0);
            for (OrderKey key : keys) {
                ResultContext<OrderKey> context = mock(ResultContext.class);
                when(context.getResultObject()).thenReturn(key);
                handler.handleResult(context);
            }
            return null;
        }).when(mapper).selectKeys(any());
    }

    @Test
    void beforeStepでワークのキーを復元した場合＿リスタートをまたいだ重複も検出する() throws Exception {
        stubKeys(List.of(new OrderKey("A001", 1, 2), new OrderKey("A001", 2, 3)));
        processor.beforeStep(null);

        assertThatThrownBy(() -> processor.process(withKey("A001", "2", 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("先頭の行=3");
        assertThatCode(() -> processor.process(withKey("A001", "3", 11))).doesNotThrowAnyException();
    }

    @Test
    void beforeStepで復元したキーと同じ行番号の場合＿再処理とみなしエラーにしない() {
        stubKeys(List.of(new OrderKey("A001", 1, 2)));
        processor.beforeStep(null);

        assertThatCode(() -> processor.process(withKey("A001", "1", 2))).doesNotThrowAnyException();
    }

    @Test
    void beforeStepを2回呼んだ場合＿結果が同じで既出キーは1回目の内容に増えない() throws Exception {
        stubKeys(List.of(new OrderKey("A001", 1, 2)));
        processor.beforeStep(null);
        processor.beforeStep(null);

        assertThatThrownBy(() -> processor.process(withKey("A001", "1", 9)))
                .isInstanceOf(BusinessException.class);
        verify(mapper, times(2)).selectKeys(any());
    }

    @Test
    void beforeStepを再度呼んだ場合＿それまでに登録した既出キーは空にしてから読み直す() throws Exception {
        stubKeys(List.of());
        processor.process(withKey("A001", "1", 2));

        processor.beforeStep(null);

        assertThatCode(() -> processor.process(withKey("A001", "1", 9))).doesNotThrowAnyException();
    }

    @Test
    void ワークが空でbeforeStepを呼んだ場合＿重複は検出されず通る() {
        stubKeys(List.of());
        processor.beforeStep(null);

        assertThatCode(() -> processor.process(valid())).doesNotThrowAnyException();
    }
}
