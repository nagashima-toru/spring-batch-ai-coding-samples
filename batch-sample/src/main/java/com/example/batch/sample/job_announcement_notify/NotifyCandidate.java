package com.example.batch.sample.job_announcement_notify;

import jakarta.validation.constraints.Email;

/**
 * ANNOUNCE-001: 通知対象候補（Step3 Reader の抽出結果）。
 *
 * <p>{@code announcement_id} / {@code user_id} / {@code subject} / {@code body} は
 * 自テーブル起因の値であり入力チェック対象としない（詳細設計書 §2 Step3）。
 * メールアドレス未設定のユーザーは抽出 SQL で除外済みのため、ここでは形式チェックのみ行う。</p>
 *
 * @param announcementId お知らせID
 * @param userId         通知対象ユーザーID
 * @param email           宛先メールアドレス
 * @param subject        件名（お知らせの title）
 * @param body            本文（お知らせの body）
 */
public record NotifyCandidate(
        Long announcementId,
        Long userId,
        @Email String email,
        String subject,
        String body) {
}
