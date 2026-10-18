package com.example.batch.sample.job05_api_import;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import com.example.batch.core.item.CountingItemStreamReader;

/**
 * サンプル05: 取込 Step の取得件数を Job の ExecutionContext に保存するリスナー。
 *
 * <p>取得件数は、{@link CountingItemStreamReader} が Step の ExecutionContext に保存したコミット済みの件数
 * （Reader が返した null 以外の件数＝書込件数＋Skip 件数）をそのまま使う。入力チェックで Skip した件も
 * API から取得した件なので含まれる（Skip 件数は足さない）。終端の {@code null} を数えないため、
 * Step が COMPLETED・STOPPED・FAILED のどれで終わっても件数がずれない（Issue #81）。</p>
 *
 * <p>StepExecution の readCount は使わない（Issue #79）。readCount はチャンクの途中で失敗すると、ロールバックされた
 * チャンクの分まで数えており、コミット済みの件数と一致しない。リスタート時は件数が前回から引き継がれて
 * 累計になっているため、Job の ExecutionContext の値には足し込まず上書きする。</p>
 *
 * <p>後続の件数突き合わせ Step（{@code sample05.02CountCheckStep}）が、この件数を件数 API の件数と比べる。
 * Job の ExecutionContext に置くのは、リスタートで取込 Step が飛ばされても値を引き継ぐため。</p>
 */
public class ApiUserReadCountListener implements StepExecutionListener {

    /** Job の ExecutionContext に取得件数を保存するキー。 */
    public static final String READ_COUNT_KEY = "apiUserReadCount";

    private static final Logger log = LoggerFactory.getLogger(ApiUserReadCountListener.class);

    private final String counterName;

    /**
     * リスナーを作る。
     *
     * @param counterName 取得件数を数える {@link CountingItemStreamReader} の名前
     */
    public ApiUserReadCountListener(String counterName) {
        this.counterName = counterName;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExecutionContext stepContext = stepExecution.getExecutionContext();
        Object value = stepContext.get(CountingItemStreamReader.countKey(counterName));
        long count = 0L;
        if (value != null) {
            count = ((Number) value).longValue();
        } else if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
            // 正常終了なら件数は必ず保存される。無いのは名前の不一致などの設定の誤りで、
            // 件数突き合わせの不一致だけでは原因が分からないため知らせる（afterStep の例外は Spring Batch がログに留める）
            log.warn("取込Stepの取得件数が見つかりません。取得件数を0件とします: counterName={}", counterName);
        }
        stepExecution.getJobExecution().getExecutionContext().putLong(READ_COUNT_KEY, count);
        return null;
    }
}
