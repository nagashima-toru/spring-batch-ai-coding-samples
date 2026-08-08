package com.example.batch.sample.job_announcement_notify;

import org.apache.ibatis.annotations.Mapper;

/**
 * メール送信依頼（app.mail_send）の Mapper。SQL は mapper/MailSendMapper.xml に定義する。
 */
@Mapper
public interface MailSendMapper {

    /** メール送信依頼を登録する（一意制約 (announcement_id, user_id) 違反時は何もしない＝再実行で冪等）。 */
    void insert(MailSend mailSend);
}
