-- ANNOUNCE-001 システムテスト: 機能シナリオ（ST-1〜ST-3）用の少量データ
-- 個人情報は使用しない（すべて架空の生成データ）
-- 実行前に reset.sql で初期化しておくこと

-- 郵便番号マスタ（東京都13: 千代田区13101・中央区13102 / 大阪府27: 大阪市北区27100）
INSERT INTO app.zip_code_master (zip_code, prefecture_code, city_code) VALUES
  ('1000001', '13', '13101'),
  ('1000002', '13', '13101'),
  ('1040031', '13', '13102'),
  ('5300001', '27', '27100');

-- お知らせ1: 正常（千代田区13101を指定）
INSERT INTO app.announcement (announcement_id, title, body, status, created_at) VALUES
  (1, '防災訓練のお知らせ', '9月1日に実施します', '0', CURRENT_TIMESTAMP);
INSERT INTO app.announcement_region (announcement_id, region_seq, prefecture_code, city_code) VALUES
  (1, 1, '13', '13101');

-- お知らせ2: 区域展開不能（マスタに存在しない区域99999を指定）→ ST-3 で status=9 になる想定
INSERT INTO app.announcement (announcement_id, title, body, status, created_at) VALUES
  (2, '区域不正なお知らせ', '本文B', '0', CURRENT_TIMESTAMP);
INSERT INTO app.announcement_region (announcement_id, region_seq, prefecture_code, city_code) VALUES
  (2, 1, '99', '99999');

-- ユーザー
INSERT INTO app.app_user (user_id, user_name, email, zip_code) VALUES
  (101, '区域内本人',       'taro@example.com',   '1000001'),  -- 区域内(本人) → 対象
  (102, '同居家族_住所なし', 'hanako@example.com', NULL),       -- 住所なし・区域内家族 → 対象
  (103, '別居家族_区域外',   'jiro@example.com',   '5300001'),  -- 区域外に自宅 → 対象外
  (104, '区域内_メール未設定', NULL,               '1000002'),  -- メール未設定 → 対象外(エラーでない)
  (105, '形式不正メール',    'invalid-mail',       '1000002');  -- メール形式不正 → Skip(警告)

-- 家族関係（双方向2行。102は101の家族=同居扱い、103も101の家族だが自宅が区域外なので対象外）
INSERT INTO app.family_relation (user_id, family_user_id) VALUES
  (102, 101), (101, 102),
  (103, 101), (101, 103);
