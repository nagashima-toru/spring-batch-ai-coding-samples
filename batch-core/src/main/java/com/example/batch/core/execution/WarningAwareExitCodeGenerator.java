package com.example.batch.core.execution;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.batch.autoconfigure.JobExecutionEvent;
import org.springframework.context.ApplicationListener;

/**
 * 終了コード規約（0=正常 / 2=警告 / 1=異常）に基づく ExitCodeGenerator。
 *
 * <p><b>用途:</b> ジョブの実行結果をプロセス終了コードへ変換する。
 * Spring Boot 標準の {@code JobExecutionExitCodeGenerator}（BatchStatus の序数を返す）を
 * 置き換え、方式設計 §7 の規約どおりのコードを返す。</p>
 *
 * <p><b>使い方:</b> auto-configuration により自動登録される。アプリの main では
 * {@code System.exit(SpringApplication.exit(...))} で終了コードを伝播させること。</p>
 *
 * <p>判定の優先順位: 1件でも失敗があれば異常（1）、失敗が無く警告があれば警告（2）、
 * すべて正常なら 0。</p>
 *
 * <p><b>拡張ポイント:</b> 終了コード体系を案件独自に変える場合は、本クラスと同型の
 * {@link ExitCodeGenerator} Bean を業務側で定義する（本 Bean は
 * {@code @ConditionalOnMissingBean} のため自動で置き換わる）。</p>
 */
public class WarningAwareExitCodeGenerator
        implements ExitCodeGenerator, ApplicationListener<JobExecutionEvent> {

    private final List<JobExecution> executions = new CopyOnWriteArrayList<>();

    @Override
    public void onApplicationEvent(JobExecutionEvent event) {
        executions.add(event.getJobExecution());
    }

    @Override
    public int getExitCode() {
        if (executions.isEmpty()) {
            // ジョブが1つも起動していない（ジョブ名指定誤り等）場合は異常とする
            return BatchExitCodes.ERROR;
        }
        int exitCode = BatchExitCodes.NORMAL;
        for (JobExecution execution : executions) {
            int code = toExitCode(execution);
            if (code == BatchExitCodes.ERROR) {
                return BatchExitCodes.ERROR;
            }
            if (code == BatchExitCodes.WARNING) {
                exitCode = BatchExitCodes.WARNING;
            }
        }
        return exitCode;
    }

    private int toExitCode(JobExecution execution) {
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            return BatchExitCodes.ERROR;
        }
        String exitCode = execution.getExitStatus().getExitCode();
        if (ExitStatus.COMPLETED.getExitCode().equals(exitCode)) {
            return BatchExitCodes.NORMAL;
        }
        // COMPLETED_WITH_WARNING および規約外のカスタム ExitStatus は「完走したが要確認」として警告扱い
        return BatchExitCodes.WARNING;
    }
}
