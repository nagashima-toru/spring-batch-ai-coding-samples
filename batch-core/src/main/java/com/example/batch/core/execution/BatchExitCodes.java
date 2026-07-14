package com.example.batch.core.execution;

import org.springframework.batch.core.ExitStatus;

/**
 * 終了コード規約の定義。
 *
 * <p><b>用途:</b> 方式設計（docs/architecture.md §7）の終了コード規約を一元定義する。
 * ジョブの終了コードは ECS タスクの終了コードへ伝播し、EventBridge / Step Functions の
 * 失敗検知・リラン制御に使用される。</p>
 *
 * <table border="1">
 *   <caption>終了コード規約</caption>
 *   <tr><th>コード</th><th>意味</th><th>ExitStatus</th></tr>
 *   <tr><td>0</td><td>正常終了</td><td>COMPLETED</td></tr>
 *   <tr><td>2</td><td>警告終了（Skip 発生等）</td><td>COMPLETED_WITH_WARNING</td></tr>
 *   <tr><td>1</td><td>異常終了</td><td>FAILED ほか</td></tr>
 * </table>
 *
 * <p><b>使い方:</b> 警告終了は {@code LoggingJobExecutionListener} が Skip 件数から自動判定する。
 * ジョブ固有の警告条件がある場合は afterJob で
 * {@code jobExecution.setExitStatus(BatchExitCodes.COMPLETED_WITH_WARNING)} を設定する。</p>
 *
 * <p><b>拡張ポイント:</b> ExitStatus のカスタム値は本クラスの
 * {@code COMPLETED_WITH_WARNING} のみ許可（方式設計 §11。乱立防止）。
 * 追加が必要な場合は方式設計を改訂してから本クラスへ定義する。</p>
 */
public final class BatchExitCodes {

    /** 正常終了。 */
    public static final int NORMAL = 0;

    /** 警告終了（Skip 発生等、完走したが要確認）。 */
    public static final int WARNING = 2;

    /** 異常終了。 */
    public static final int ERROR = 1;

    /** 警告終了を表す ExitStatus。 */
    public static final ExitStatus COMPLETED_WITH_WARNING =
            new ExitStatus("COMPLETED_WITH_WARNING", "Skip等が発生したが完走した");

    private BatchExitCodes() {
    }
}
