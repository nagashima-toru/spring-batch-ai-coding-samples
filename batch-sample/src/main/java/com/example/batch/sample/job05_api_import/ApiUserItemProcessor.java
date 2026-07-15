package com.example.batch.sample.job05_api_import;

import com.example.batch.core.item.ValidatingItemProcessorBase;
import com.example.batch.sample.common.ApiUser;

import jakarta.validation.Validator;

/**
 * サンプル05: APIレスポンスの入力チェック＋エンティティ変換 Processor。
 *
 * <p>外部システムのレスポンスも取込前に必ず検証する（信頼しない）。</p>
 */
public class ApiUserItemProcessor extends ValidatingItemProcessorBase<ApiUserResponse, ApiUser> {

    public ApiUserItemProcessor(Validator validator) {
        super(validator);
    }

    @Override
    protected ApiUser doProcess(ApiUserResponse item) {
        return new ApiUser(item.userId(), item.userName(), item.email());
    }
}
