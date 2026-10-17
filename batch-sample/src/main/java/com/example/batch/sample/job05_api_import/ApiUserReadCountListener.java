package com.example.batch.sample.job05_api_import;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.util.ExecutionContextUserSupport;

/**
 * サンプル05: 取込 Step の取得件数を Job の ExecutionContext に保存するリスナー。
 *
 * <p>取得件数は、Reader が Step の ExecutionContext に保存する読込位置（{@code <Reader名>.read.count}）から求める。
 * 入力チェックで Skip した件も API から取得した件なので、読込位置に含まれる（Skip 件数は足さない）。
 * Step が正常終了（COMPLETED）した場合は、終端の {@code null} が返る読込が1件数えられているため、読込位置から1を引く。
 * それ以外（FAILED・STOPPED など）は読込位置をそのまま取得件数とする。</p>
 *
 * <p>StepExecution の readCount は使わない（Issue #79）。readCount はチャンクの途中で失敗すると、ロールバックされた
 * チャンクの分まで数えており、Reader が保存したコミット済みの読込位置と一致しない。リスタート時に読込位置は前回から
 * 引き継がれて累計になっているため、Job の ExecutionContext の値には足し込まず上書きする。</p>
 *
 * <p>後続の件数突き合わせ Step（{@code sample05.02CountCheckStep}）が、この件数を件数 API の件数と比べる。
 * Job の ExecutionContext に置くのは、リスタートで取込 Step が飛ばされても値を引き継ぐため。</p>
 */
public class ApiUserReadCountListener implements StepExecutionListener {

    /** Job の ExecutionContext に取得件数を保存するキー。 */
    public static final String READ_COUNT_KEY = "apiUserReadCount";

    /** Reader（{@code AbstractItemCountingItemStreamItemReader}）が読込位置を保存するキーの、Reader 名より後ろの部分。 */
    private static final String READER_READ_COUNT = "read.count";

    private static final Logger log = LoggerFactory.getLogger(ApiUserReadCountListener.class);

    private final String[] readerNames;

    /**
     * リスナーを作る。
     *
     * @param readerNames 読込位置を探す Reader 名（先に見つかったものを使う）
     */
    public ApiUserReadCountListener(String... readerNames) {
        this.readerNames = readerNames.clone();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExecutionContext stepContext = stepExecution.getExecutionContext();
        Long position = null;
        for (String readerName : readerNames) {
            // Reader と同じ方法でキー（<Reader名>.read.count）を組み立てる
            String key = new ExecutionContextUserSupport(readerName).getKey(READER_READ_COUNT);
            Object value = stepContext.get(key);
            if (value != null) {
                position = ((Number) value).longValue();
                break;
            }
        }
        boolean completed = stepExecution.getStatus() == BatchStatus.COMPLETED;
        if (position == null) {
            if (completed) {
                // 正常終了なら読込位置は必ず保存される。無いのは Reader 名の不一致などの設定の誤りで、
                // 件数突き合わせの不一致だけでは原因が分からないため知らせる（afterStep の例外は Spring Batch がログに留める）
                log.warn("取込Stepの読込位置が見つかりません。取得件数を0件とします: readerNames={}",
                        Arrays.toString(readerNames));
            }
            position = 0L;
        }
        long count = completed ? position - 1 : position;
        stepExecution.getJobExecution().getExecutionContext().putLong(READ_COUNT_KEY, Math.max(0L, count));
        return null;
    }
}
