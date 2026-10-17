package com.example.batch.sample.job_announcement_notify;

import com.example.batch.core.item.ValidatingItemProcessorBase;

import jakarta.validation.Validator;

/**
 * ANNOUNCE-001 Step3: 通知対象候補の入力チェック＋メール送信依頼エンティティへの変換 Processor。
 *
 * <p>メールアドレスの形式チェック（{@code @Email}）は基盤部品 {@link ValidatingItemProcessorBase}
 * が実行する。チェックエラーは Skip 対象となり、エラーレコードファイルへ退避される
 * （詳細設計書 §2 Step3・§3）。メールアドレス未設定のユーザーは Reader の SQL で抽出時に
 * 除外済みのため、本クラスでは null チェックを行わない。</p>
 */
public class NotifyCandidateItemProcessor extends ValidatingItemProcessorBase<NotifyCandidate, MailSend> {

    /** メール送信ステータス: 未送信（本バッチはこの状態で登録するのみ。以降は送信基盤が更新する）。 */
    private static final String SEND_STATUS_UNSENT = "0";

    public NotifyCandidateItemProcessor(Validator validator) {
        super(validator);
    }

    @Override
    protected MailSend doProcess(NotifyCandidate item) {
        return new MailSend(item.announcementId(), item.userId(), item.email(), item.subject(), item.body(),
                SEND_STATUS_UNSENT);
    }
}
