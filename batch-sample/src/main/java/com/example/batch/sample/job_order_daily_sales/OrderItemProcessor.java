package com.example.batch.sample.job_order_daily_sales;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import com.example.batch.core.exception.BusinessException;
import com.example.batch.core.item.ValidatingItemProcessorBase;

import jakarta.validation.Validator;

/**
 * ORDER-001 Step 4: 受注CSVレコードの入力チェック＋ワーク行への変換 Processor。
 *
 * <p>形式チェック（Bean Validation）は基盤部品 {@link ValidatingItemProcessorBase} が実行する。
 * 本クラスは業務チェック（実在する日付・対象日との一致・ファイル内の重複）と変換を行う。</p>
 *
 * <p>重複検出は「キー → 最初に現れた行番号」の対応をメモリに持つ。同じキーでも行番号が同じなら
 * チャンク再処理による同一行の再入力とみなし、重複にしない。リスタートは Step 1〜4 を最初からやり直すため、
 * 対応を復元する処理は持たない。{@code @StepScope} のため Step 実行ごとに空の対応から作る。</p>
 *
 * <p>{@code @StepScope} で使うため final にしない。</p>
 */
public class OrderItemProcessor extends ValidatingItemProcessorBase<OrderCsvRecord, OrderDetailWork> {

    private final LocalDate targetDate;
    private final Map<String, Integer> seenKeys = new HashMap<>();

    public OrderItemProcessor(Validator validator, LocalDate targetDate) {
        super(validator);
        this.targetDate = targetDate;
    }

    @Override
    protected OrderDetailWork doProcess(OrderCsvRecord item) {
        LocalDate orderDate = parseOrderDate(item.orderDate());
        if (!orderDate.equals(targetDate)) {
            throw new BusinessException("受注日が対象日と一致しません: orderDate=" + item.orderDate()
                    + " targetDate=" + targetDate);
        }
        int lineNo = Integer.parseInt(item.lineNo());
        String key = keyOf(item.orderNo(), lineNo);
        Integer firstLineSeq = seenKeys.get(key);
        if (firstLineSeq != null && firstLineSeq != item.lineSeq()) {
            throw new BusinessException("受注番号＋明細番号が重複しています: orderNo=" + item.orderNo()
                    + " lineNo=" + lineNo + " 先頭の行=" + firstLineSeq);
        }
        seenKeys.put(key, item.lineSeq());

        int quantity = Integer.parseInt(item.quantity());
        int unitPrice = Integer.parseInt(item.unitPrice());
        return new OrderDetailWork(item.orderNo(), lineNo, orderDate, item.storeCode(), item.itemCode(),
                quantity, unitPrice, (long) quantity * unitPrice, item.lineSeq());
    }

    private static LocalDate parseOrderDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new BusinessException("orderDate が実在する日付ではありません: " + text, e);
        }
    }

    private static String keyOf(String orderNo, int lineNo) {
        return orderNo + "\t" + lineNo;
    }
}
