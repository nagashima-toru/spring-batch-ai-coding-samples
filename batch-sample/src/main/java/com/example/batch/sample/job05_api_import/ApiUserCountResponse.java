package com.example.batch.sample.job05_api_import;

/**
 * 件数取得 API（{@code GET /users/count}）のレスポンス。
 *
 * @param count 取込対象のユーザー件数
 */
public record ApiUserCountResponse(long count) {
}
