package com.example.batch.core.item;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.batch.infrastructure.item.ItemProcessor;

import com.example.batch.core.exception.BusinessException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * バリデーション付き ItemProcessor の抽象基底クラス。
 *
 * <p><b>用途:</b> 取込ジョブの入力チェックを標準化する。Bean Validation
 * （{@code @NotBlank}・{@code @Pattern} 等のアノテーション）による形式チェックを
 * 自動実行した後、業務チェック・変換処理（{@link #doProcess}）を呼び出す。</p>
 *
 * <p>チェックエラーは {@link BusinessException}（Skip 対象）として送出されるため、
 * {@code BusinessSkipPolicy}・{@code ErrorRecordSkipListener} と組み合わせることで
 * 「エラーレコードは退避して処理継続」が実現できる。</p>
 *
 * <p><b>使い方:</b></p>
 * <pre>{@code
 * public class CustomerItemProcessor extends ValidatingItemProcessorBase<CustomerCsvRecord, Customer> {
 *     public CustomerItemProcessor(Validator validator) { super(validator); }
 *
 *     @Override
 *     protected Customer doProcess(CustomerCsvRecord item) {
 *         // 業務チェック（形式チェックはBean Validationで実施済み）
 *         if (customerMapper.exists(item.customerCode())) { ... }
 *         return new Customer(...);  // エンティティへ変換
 *     }
 * }
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> エラーメッセージの形式を変えたい場合は
 * {@link #formatViolations(Object, Set)} をオーバーライドする。</p>
 *
 * @param <I> 入力レコード型（Bean Validation アノテーションを付与する）
 * @param <O> 出力（変換後）型
 */
public abstract class ValidatingItemProcessorBase<I, O> implements ItemProcessor<I, O> {

    private final Validator validator;

    protected ValidatingItemProcessorBase(Validator validator) {
        this.validator = validator;
    }

    /**
     * 形式チェック（Bean Validation）の後に {@link #doProcess} を呼ぶ。
     *
     * <p>入力チェックを必ず {@code doProcess} の前に実行するため final にする。
     * {@code @StepScope} の Bean にする時は、Bean メソッドの戻り値と注入先の型を
     * {@code ItemProcessor<I, O>} にする（スコーププロキシがインタフェースの JDK 動的プロキシになる。
     * docs/architecture.md §13）。</p>
     */
    @Override
    public final O process(I item) throws Exception {
        Set<ConstraintViolation<I>> violations = validator.validate(item);
        if (!violations.isEmpty()) {
            throw new BusinessException(formatViolations(item, violations));
        }
        return doProcess(item);
    }

    /**
     * 形式チェック通過後の業務チェック・変換処理。
     *
     * <p>業務チェックエラーは {@link BusinessException} を送出すること（Skip 対象になる）。
     * {@code null} を返すとそのレコードはフィルタされる（Skip とは異なり正常扱い）。</p>
     *
     * @param item 形式チェック済みの入力レコード
     * @return 変換後のオブジェクト（フィルタする場合は null）
     * @throws Exception 業務チェックエラー・変換エラー
     */
    protected abstract O doProcess(I item) throws Exception;

    /**
     * バリデーション違反をエラーメッセージへ整形する。
     *
     * @param item       入力レコード
     * @param violations 違反一覧
     * @return エラーメッセージ
     */
    protected String formatViolations(I item, Set<ConstraintViolation<I>> violations) {
        String detail = violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return "入力チェックエラー: " + detail;
    }
}
