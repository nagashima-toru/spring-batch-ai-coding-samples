package com.example.batch.core.tasklet;

import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.exception.BusinessException;

/**
 * DB 件数チェック Tasklet。
 *
 * <p><b>用途:</b> 前処理での取込前提条件チェック（例: マスタが空でないこと）や、
 * 後処理での処理結果検証（例: 取込件数が想定範囲内であること）を標準化する。</p>
 *
 * <p><b>使い方:</b> 件数取得は MyBatis Mapper のメソッド参照で渡す（SQL は Mapper XML で管理）。</p>
 * <pre>{@code
 * @Bean
 * TableCountCheckTasklet customerCountCheckTasklet(CustomerMapper mapper) {
 *     return new TableCountCheckTasklet("app.customer", mapper::count, 1, null);
 * }
 * }</pre>
 *
 * <p>件数が {@code minCount} 未満、または {@code maxCount}（null なら上限なし）超過の場合は
 * {@link BusinessException} を送出し Step は失敗する。</p>
 *
 * <p><b>拡張ポイント:</b> 件数を後続 Step へ引き渡したい場合は、本クラスを継承し
 * {@code chunkContext} 経由で Job の ExecutionContext へ格納する処理を追加する。</p>
 */
public class TableCountCheckTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(TableCountCheckTasklet.class);

    private final String targetName;
    private final LongSupplier countSupplier;
    private final long minCount;
    private final Long maxCount;

    /**
     * @param targetName    チェック対象の名称（ログ・エラーメッセージ用。例: テーブル名）
     * @param countSupplier 件数を取得する処理（MyBatis Mapper のメソッド参照を想定）
     * @param minCount      許容する最小件数（この値未満で異常）
     * @param maxCount      許容する最大件数（null の場合は上限チェックなし）
     */
    public TableCountCheckTasklet(String targetName, LongSupplier countSupplier, long minCount, Long maxCount) {
        this.targetName = targetName;
        this.countSupplier = countSupplier;
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        long count = countSupplier.getAsLong();
        log.info("件数チェック: target={} count={} min={} max={}", targetName, count, minCount, maxCount);
        if (count < minCount) {
            throw new BusinessException(
                    "件数チェックエラー: %s の件数 %d が最小件数 %d を下回っています".formatted(targetName, count, minCount));
        }
        if (maxCount != null && count > maxCount) {
            throw new BusinessException(
                    "件数チェックエラー: %s の件数 %d が最大件数 %d を超えています".formatted(targetName, count, maxCount));
        }
        return RepeatStatus.FINISHED;
    }
}
