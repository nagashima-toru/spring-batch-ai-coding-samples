-- 会員ポイント失効バッチ（POINT-001）業務テーブル
-- docs/projects/member-point-expiry/basic-design.md §4 準拠

-- ポイント付与（付与ごとに1レコード。本バッチが status を「失効」へ更新する）
-- status: 1=有効 / 2=失効
-- member_id は会員マスタが本案件スコープ外のため FK なし（論理参照）
CREATE TABLE app.point_grant (
    point_grant_id BIGINT      NOT NULL PRIMARY KEY,
    member_id      BIGINT      NOT NULL,
    points         INTEGER     NOT NULL,
    granted_date   DATE        NOT NULL,
    expiry_date    DATE        NOT NULL,
    status         VARCHAR(1)  NOT NULL,
    expired_date   DATE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 失効対象抽出用（等値条件の status を先頭、範囲条件の expiry_date を後続に置く）
CREATE INDEX idx_point_grant_status_expiry_date ON app.point_grant (status, expiry_date);
-- 会員向け残高照会・CS対応での参照用（本バッチは使用しない）
CREATE INDEX idx_point_grant_member_id ON app.point_grant (member_id);

-- 失効履歴（本バッチの出力。CS対応・監査で参照。削除せず保持する）
CREATE TABLE app.point_expiry_history (
    point_expiry_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    point_grant_id          BIGINT      NOT NULL UNIQUE REFERENCES app.point_grant (point_grant_id),
    member_id               BIGINT      NOT NULL,
    points                  INTEGER     NOT NULL,
    granted_date            DATE        NOT NULL,
    expiry_date             DATE        NOT NULL,
    expired_date            DATE        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CS対応での会員別・期間別の照会用
CREATE INDEX idx_point_expiry_history_member_id_expired_date ON app.point_expiry_history (member_id, expired_date);
