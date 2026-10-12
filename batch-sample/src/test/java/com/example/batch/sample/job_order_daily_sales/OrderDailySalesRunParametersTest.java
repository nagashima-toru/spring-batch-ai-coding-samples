package com.example.batch.sample.job_order_daily_sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.batch.sample.common.SampleProperties.OrderDailySales;

/**
 * {@link OrderDailySalesRunParameters#resolve} の単体テスト。
 *
 * <p>詳細設計書 ORDER-001 §7「単体テスト」の resolve の観点に基づく。</p>
 */
class OrderDailySalesRunParametersTest {

    private static final OrderDailySales SETTINGS = new OrderDailySales("/data/in", "/data/error");

    @Test
    void UTCの前日15時以降を指定した場合＿JSTの前日がUTCの日付ではなく対象日になる() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T17:00:00Z", null, null, null, SETTINGS);

        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(result.inputFile()).isEqualTo(Path.of("/data/in", "orders_20261011.csv"));
        assertThat(result.errorFile())
                .isEqualTo(Path.of("/data/error", "orders_20261011_20261012020000_error.txt"));
    }

    @Test
    void JSTの0時ちょうどに相当するUTC前日15時の場合＿JSTの日付で前日が対象日になる() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T15:00:00Z", null, null, null, SETTINGS);

        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(result.errorFile())
                .isEqualTo(Path.of("/data/error", "orders_20261011_20261012000000_error.txt"));
    }

    @Test
    void JSTの0時の1秒前に相当するUTC前日14時59分59秒の場合＿JSTでは前日扱いとなり対象日がさらに1日前になる() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T14:59:59Z", null, null, null, SETTINGS);

        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(result.errorFile())
                .isEqualTo(Path.of("/data/error", "orders_20261010_20261011235959_error.txt"));
    }

    @Test
    void JSTの8時59分に相当するUTCの場合＿UTCでは前日でもJSTの日付から前日を求める() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T23:59:00Z", null, null, null, SETTINGS);

        // JST では 2026-10-12 08:59 のため、対象日は 2026-10-11
        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void プラス9時間のオフセットで指定した場合＿そのままJSTとして対象日と既定ファイル名を決める() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-12T02:00:00+09:00", null, null, null, SETTINGS);

        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(result.inputFile()).isEqualTo(Path.of("/data/in", "orders_20261011.csv"));
        assertThat(result.errorFile())
                .isEqualTo(Path.of("/data/error", "orders_20261011_20261012020000_error.txt"));
    }

    @Test
    void JSTと異なるオフセットで指定した場合＿JSTへ変換した日付から対象日を決める() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T12:00:00-05:00", null, null, null, SETTINGS);

        // 2026-10-12T02:00:00+09:00 と同じ時刻
        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(result.errorFile())
                .isEqualTo(Path.of("/data/error", "orders_20261011_20261012020000_error.txt"));
    }

    @Test
    void 月初に実行した場合＿前月末日が対象日になる() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-31T16:00:00Z", null, null, null, SETTINGS);

        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(OrderDailySalesRunParameters.resolve("2026-11-01T02:00:00+09:00", null, null, null, SETTINGS)
                .targetDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void targetDateを指定した場合＿指定値が優先され既定ファイル名にも指定値の日付が使われる() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T17:00:00Z", LocalDate.of(2026, 9, 1), null, null, SETTINGS);

        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.inputFile()).isEqualTo(Path.of("/data/in", "orders_20260901.csv"));
        assertThat(result.errorFile())
                .isEqualTo(Path.of("/data/error", "orders_20260901_20261012020000_error.txt"));
    }

    @Test
    void inputFileを指定した場合＿指定値が優先される() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T17:00:00Z", null, "/tmp/custom.csv", null, SETTINGS);

        assertThat(result.inputFile()).isEqualTo(Path.of("/tmp/custom.csv"));
        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(result.errorFile())
                .isEqualTo(Path.of("/data/error", "orders_20261011_20261012020000_error.txt"));
    }

    @Test
    void errorFileを指定した場合＿指定値が優先される() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T17:00:00Z", null, null, "/tmp/custom_error.txt", SETTINGS);

        assertThat(result.errorFile()).isEqualTo(Path.of("/tmp/custom_error.txt"));
        assertThat(result.inputFile()).isEqualTo(Path.of("/data/in", "orders_20261011.csv"));
    }

    @Test
    void 全項目を指定した場合＿すべて指定値が使われる() {
        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T17:00:00Z", LocalDate.of(2026, 1, 2), "/a/in.csv", "/b/err.txt", SETTINGS);

        assertThat(result).isEqualTo(new OrderDailySalesRunParameters(
                LocalDate.of(2026, 1, 2), Path.of("/a/in.csv"), Path.of("/b/err.txt")));
    }

    @Test
    void ローカル設定のディレクトリを使う場合＿既定パスがその配下に組み立てられる() {
        OrderDailySales local = new OrderDailySales("batch-sample/data/in", "batch-sample/data/error");

        OrderDailySalesRunParameters result = OrderDailySalesRunParameters.resolve(
                "2026-10-11T17:00:00Z", null, null, null, local);

        assertThat(result.inputFile()).isEqualTo(Path.of("batch-sample/data/in/orders_20261011.csv"));
        assertThat(result.errorFile()).isEqualTo(
                Path.of("batch-sample/data/error/orders_20261011_20261012020000_error.txt"));
    }
}
