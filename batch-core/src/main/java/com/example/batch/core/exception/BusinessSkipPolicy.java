package com.example.batch.core.exception;

import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

/**
 * 業務エラーのみを Skip する共通 Skip ポリシー。
 *
 * <p><b>用途:</b> 方式設計（docs/architecture.md §5.2）の
 * 「Skip は業務エラーのみ・上限件数必須」を実現する標準ポリシー。
 * 以下の例外を Skip 対象とし、上限を超えた時点でジョブを異常終了させる。</p>
 * <ul>
 *   <li>{@link BusinessException}（業務チェックエラー）</li>
 *   <li>{@link FlatFileParseException}（ファイルのパースエラー＝入力データ不正）</li>
 * </ul>
 * <p>{@link SystemException} などの環境起因エラーは Skip せず即時異常終了させる。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * new StepBuilder("step", jobRepository).<In, Out>chunk(100)
 *     .reader(reader).processor(processor).writer(writer)
 *     .transactionManager(txManager)
 *     .faultTolerant()
 *     .skipPolicy(new BusinessSkipPolicy(10))
 *     .build();
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> Skip 対象例外を追加したい場合は {@link #isSkippable(Throwable)} を
 * オーバーライドする。</p>
 */
public class BusinessSkipPolicy implements SkipPolicy {

    private final int skipLimit;

    /**
     * @param skipLimit Skip 上限件数（この件数を超えて Skip 対象エラーが発生するとジョブは異常終了する）
     */
    public BusinessSkipPolicy(int skipLimit) {
        if (skipLimit < 0) {
            throw new IllegalArgumentException("skipLimit は 0 以上を指定してください: " + skipLimit);
        }
        this.skipLimit = skipLimit;
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        if (!isSkippable(t)) {
            return false;
        }
        if (skipCount >= skipLimit) {
            throw new SkipLimitExceededException(skipLimit, t);
        }
        return true;
    }

    /**
     * Skip 対象の例外かどうかを判定する。
     *
     * @param t 発生した例外
     * @return Skip 対象であれば true
     */
    protected boolean isSkippable(Throwable t) {
        return t instanceof BusinessException || t instanceof FlatFileParseException;
    }
}
