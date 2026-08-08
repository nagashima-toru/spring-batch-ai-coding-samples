package com.example.batch.sample.job_announcement_notify;

/**
 * ANNOUNCE-001: メール送信依頼エンティティ（app.mail_send）。
 *
 * <p>{@code mail_send_id}・{@code created_at} は DB 側（IDENTITY / DEFAULT）で採番・設定するため
 * 本レコードには含めない。</p>
 *
 * @param announcementId お知らせID
 * @param userId         通知対象ユーザーID
 * @param email          宛先メールアドレス（登録時点のスナップショット）
 * @param subject        件名（お知らせ title のスナップショット）
 * @param body           本文（お知らせ body のスナップショット）
 * @param sendStatus     送信ステータス（"0"=未送信で登録する）
 */
public record MailSend(Long announcementId, Long userId, String email, String subject, String body,
        String sendStatus) {
}
