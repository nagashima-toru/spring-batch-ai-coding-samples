-- お知らせ通知バッチ（ANNOUNCE-001）業務テーブル
-- docs/projects/announcement-notification/basic-design.md §4 準拠

-- お知らせ（業務システムが登録、本バッチがステータス管理）
-- status: 0=未処理 / 1=処理中 / 2=処理済み / 9=エラー（区域展開不能）
CREATE TABLE app.announcement (
    announcement_id BIGINT       NOT NULL PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    status          VARCHAR(1)   NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    processed_at    TIMESTAMPTZ
);

CREATE INDEX idx_announcement_status ON app.announcement (status);

-- 通知区域（お知らせに紐づく都道府県・市区町村。city_code が NULL の場合は都道府県全域）
CREATE TABLE app.announcement_region (
    announcement_id BIGINT     NOT NULL,
    region_seq      INTEGER    NOT NULL,
    prefecture_code VARCHAR(2) NOT NULL,
    city_code       VARCHAR(5),
    PRIMARY KEY (announcement_id, region_seq),
    FOREIGN KEY (announcement_id) REFERENCES app.announcement (announcement_id)
);

-- ユーザー（住所・メールアドレス）
-- email が NULL のユーザーは通知不能のため対象外、zip_code が NULL のユーザーは住所未登録（家族ルールの対象）
CREATE TABLE app.app_user (
    user_id   BIGINT       NOT NULL PRIMARY KEY,
    user_name VARCHAR(100) NOT NULL,
    email     VARCHAR(254),
    zip_code  VARCHAR(7)
);

CREATE INDEX idx_app_user_zip_code ON app.app_user (zip_code);

-- 家族関係（双方向2行で表現する前提。業務システムが両方向を登録する）
CREATE TABLE app.family_relation (
    user_id        BIGINT NOT NULL,
    family_user_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, family_user_id),
    FOREIGN KEY (user_id) REFERENCES app.app_user (user_id),
    FOREIGN KEY (family_user_id) REFERENCES app.app_user (user_id)
);

CREATE INDEX idx_family_relation_family_user_id ON app.family_relation (family_user_id);

-- 郵便番号マスタ（都道府県・市区町村→郵便番号）。データ整備は本案件スコープ外（要件定義書 未確定No.1）
CREATE TABLE app.zip_code_master (
    zip_code        VARCHAR(7) NOT NULL,
    prefecture_code VARCHAR(2) NOT NULL,
    city_code       VARCHAR(5) NOT NULL,
    PRIMARY KEY (zip_code, city_code)
);

CREATE INDEX idx_zip_code_master_prefecture_code ON app.zip_code_master (prefecture_code);
CREATE INDEX idx_zip_code_master_city_code ON app.zip_code_master (city_code);

-- メール送信依頼（本バッチの出力。send_status: 0=未送信。以降は送信基盤が更新する）
CREATE TABLE app.mail_send (
    mail_send_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    announcement_id BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    email           VARCHAR(254) NOT NULL,
    subject         VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    send_status     VARCHAR(1)   NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (announcement_id, user_id),
    FOREIGN KEY (announcement_id) REFERENCES app.announcement (announcement_id),
    FOREIGN KEY (user_id) REFERENCES app.app_user (user_id)
);

CREATE INDEX idx_mail_send_send_status ON app.mail_send (send_status);
