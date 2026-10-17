package com.example.batch.sample.job05_api_import;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * サンプル05: 取込 Step の取得件数を Job の ExecutionContext に保存するリスナー。
 *
 * <p>取得件数は「読込件数＋読込 Skip 件数」とする（入力チェックで Skip した件も API から取得した件に数える）。
 * 後続の件数突き合わせ Step（{@code sample05.02CountCheckStep}）が、件数 API の件数と比べる。
 * Job の ExecutionContext に置くのは、リスタートで取込 Step が飛ばされても値を引き継ぐため。</p>
 *
 * <p>取込 Step の途中失敗からリスタートすると、Reader は保存した位置から続きを読み、新しい StepExecution の件数は
 * 0 から数え直す。そのため上書きせず、前回までの実行で保存した件数に今回の件数を足し込む
 * （途中失敗した実行の件数はコミット済みの分だけで、Reader の保存位置と一致する）。</p>
 */
public class ApiUserReadCountListener implements StepExecutionListener {

    /** Job の ExecutionContext に取得件数を保存するキー。 */
    public static final String READ_COUNT_KEY = "apiUserReadCount";

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();
        long previous = jobContext.containsKey(READ_COUNT_KEY) ? jobContext.getLong(READ_COUNT_KEY) : 0L;
        long readCount = stepExecution.getReadCount() + stepExecution.getReadSkipCount();
        jobContext.putLong(READ_COUNT_KEY, previous + readCount);
        return null;
    }
}
