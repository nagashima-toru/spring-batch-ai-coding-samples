package com.example.batch.sample.job01_csv_import;

import com.example.batch.core.item.ValidatingItemProcessorBase;

import jakarta.validation.Validator;

/**
 * サンプル01: 顧客CSVレコードの入力チェック＋エンティティ変換 Processor。
 *
 * <p>形式チェック（Bean Validation）は基盤部品 {@link ValidatingItemProcessorBase} が実行する。
 * 本クラスでは業務チェックと変換のみ実装する。チェックエラーは Skip 対象となり、
 * エラーレコードファイルへ退避される。</p>
 */
public class CustomerItemProcessor extends ValidatingItemProcessorBase<CustomerCsvRecord, Customer> {

    public CustomerItemProcessor(Validator validator) {
        super(validator);
    }

    @Override
    protected Customer doProcess(CustomerCsvRecord item) {
        // 業務チェックが必要な場合はここで BusinessException を送出する
        return new Customer(item.customerCode().toUpperCase(), item.name(), item.email());
    }
}
