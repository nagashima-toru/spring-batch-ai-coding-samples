package com.example.batch.sample.job06_flow_job;

import java.math.BigDecimal;

import com.example.batch.core.exception.SystemException;
import com.example.batch.core.item.ValidatingItemProcessorBase;

import jakarta.validation.Validator;

/**
 * サンプル06: ワークレコードの入力チェック＋変換 Processor。
 *
 * <p><b>リスタートデモ用の意図的な失敗:</b> 金額が負数のレコードを検出すると
 * {@link SystemException}（Skip 対象外）を送出し、ジョブを途中失敗させる。
 * データを修正して同一パラメータで再実行すると、処理済みチャンクの続きから再開される。
 * 手順は docs/sample06-flow-job.md を参照。</p>
 */
public class WorkItemProcessor extends ValidatingItemProcessorBase<WorkCsvRecord, WorkRecord> {

    public WorkItemProcessor(Validator validator) {
        super(validator);
    }

    @Override
    protected WorkRecord doProcess(WorkCsvRecord item) {
        BigDecimal amount = new BigDecimal(item.amount());
        if (amount.signum() < 0) {
            throw new SystemException(
                    "金額が負数のレコードを検出しました（リスタートデモ用の意図的失敗）: code=" + item.code());
        }
        return new WorkRecord(item.code(), amount);
    }
}
