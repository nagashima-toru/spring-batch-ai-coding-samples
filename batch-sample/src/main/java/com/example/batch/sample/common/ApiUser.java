package com.example.batch.sample.common;

/**
 * APIユーザーエンティティ（app.api_user）。
 *
 * @param userId   ユーザーID
 * @param userName ユーザー名
 * @param email    メールアドレス
 */
public record ApiUser(String userId, String userName, String email) {
}
