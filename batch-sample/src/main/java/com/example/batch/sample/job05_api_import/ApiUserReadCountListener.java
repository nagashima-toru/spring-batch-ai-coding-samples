package com.example.batch.sample.job05_api_import;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * サンプル05: 取込 Step の取得件数を Job の ExecutionContext に保存するリスナー。
 *
 * <p>取得件数は「読込件数＋読込 Skip 件数」とする（入力チェックで Skip した件も API から取得した件に数える）。
 * 後続の件数突き合わせ Step（{@code sample05.02CountCheckStep}）が、件数 API の件数と比べる。
 * Job の ExecutionContext に置くのは、リスタートで取込 Step が飛ばされても値を引き継ぐため。</p>
 */
public class ApiUserReadCountListener implements StepExecutionListener {

    /** Job の ExecutionContext に取得件数を保存するキー。 */
    public static final String READ_COUNT_KEY = "apiUserReadCount";

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long readCount = stepExecution.getReadCount() + stepExecution.getReadSkipCount();
        stepExecution.getJobExecution().getExecutionContext().putLong(READ_COUNT_KEY, readCount);
        return null;
    }
}
