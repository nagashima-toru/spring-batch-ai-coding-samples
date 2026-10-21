package com.example.batch.sample.job_order_daily_sales;

/**
 * ORDER-001: 実行時パラメータ（{@link OrderDailySalesRunParameters}）を包む {@code @JobScope} Bean の本体。
 *
 * <p>{@link OrderDailySalesRunParameters} は record（final クラス）のため、スコーププロキシ
 * （クラスを継承して作る）を作れず、そのままでは {@code @JobScope} の Bean にできない。
 * そこで final にしないこのクラスで包み、{@code @JobScope} の Bean にする。
 * 1回の JobExecution につき解決とログ出力を1回だけ行い、各 {@code @StepScope} の Bean はこの Bean から値を受け取る。</p>
 *
 * <p>スコーププロキシが継承して上書きできるよう、クラスも {@link #get()} も final にしない。</p>
 *
 * <p>スコーププロキシは CGLIB でこのクラスを継承して作り、Objenesis でコンストラクタを呼ばずに生成されるため、
 * 引数なしコンストラクタは不要。</p>
 *
 * <p>resolve の入力はすべて起動前に Validator が検証するので、Bean の生成（Step の実行中の遅延解決）で
 * 不正なパラメータの例外は起きない。</p>
 */
public class OrderDailySalesRunParametersHolder {

    private final OrderDailySalesRunParameters value;

    /**
     * @param value 省略時の補完を済ませた実行時パラメータ
     */
    public OrderDailySalesRunParametersHolder(OrderDailySalesRunParameters value) {
        this.value = value;
    }

    /**
     * @return 省略時の補完を済ませた実行時パラメータ
     */
    public OrderDailySalesRunParameters get() {
        return value;
    }
}
