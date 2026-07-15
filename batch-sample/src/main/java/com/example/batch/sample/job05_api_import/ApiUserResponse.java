package com.example.batch.sample.job05_api_import;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * サンプル05: ユーザーAPI のレスポンス要素（JSON → record マッピング）。
 *
 * @param userId   ユーザーID
 * @param userName ユーザー名
 * @param email    メールアドレス
 */
public record ApiUserResponse(
        @NotBlank @Size(max = 20)
        String userId,
        @NotBlank @Size(max = 100)
        String userName,
        @NotBlank @Email @Size(max = 255)
        String email) {
}
