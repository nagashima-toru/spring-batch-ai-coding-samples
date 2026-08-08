-- ANNOUNCE-001 システムテスト: 性能シナリオ（ST-6）用の本番相当データ
-- ユーザー10万件・通知対象約3万件（本人27,000 ＋ 同居家族3,000）
-- 個人情報は使用しない（generate_series による架空データ）
-- 実行前に reset.sql で初期化しておくこと

-- 郵便番号マスタ: 東京都(13) の300郵便番号（30市区町村 × 10郵便番号）
INSERT INTO app.zip_code_master (zip_code, prefecture_code, city_code)
SELECT lpad((1000000 + g)::text, 7, '0'),
       '13',
       '131' || lpad((g % 30 + 1)::text, 2, '0')
FROM generate_series(1, 300) AS g;

-- お知らせ1件: 東京都全域（city_code NULL）を通知区域に指定
INSERT INTO app.announcement (announcement_id, title, body, status, created_at)
VALUES (1, '大規模通知テスト', '本番相当データの性能確認用', '0', CURRENT_TIMESTAMP);
INSERT INTO app.announcement_region (announcement_id, region_seq, prefecture_code, city_code)
VALUES (1, 1, '13', NULL);

-- 本人 27,000件: 東京都内の郵便番号を保持（区域内 → 対象）
INSERT INTO app.app_user (user_id, user_name, email, zip_code)
SELECT g, 'user' || g, 'user' || g || '@example.com',
       lpad((1000001 + (g % 300))::text, 7, '0')
FROM generate_series(1, 27000) AS g;

-- 区域外 70,000件: 大阪府(27)相当の郵便番号（マスタの区域展開に一致しない → 対象外）
INSERT INTO app.app_user (user_id, user_name, email, zip_code)
SELECT g, 'user' || g, 'user' || g || '@example.com',
       '270' || lpad((g % 1000)::text, 4, '0')
FROM generate_series(27001, 97000) AS g;

-- 同居家族 3,000件: 住所なし・区域内ユーザー(1..27000)の家族（双方向2行）→ 対象
INSERT INTO app.app_user (user_id, user_name, email, zip_code)
SELECT g, 'user' || g, 'user' || g || '@example.com', NULL
FROM generate_series(97001, 100000) AS g;

INSERT INTO app.family_relation (user_id, family_user_id)
SELECT g, ((g - 97001) % 27000) + 1
FROM generate_series(97001, 100000) AS g;

INSERT INTO app.family_relation (user_id, family_user_id)
SELECT ((g - 97001) % 27000) + 1, g
FROM generate_series(97001, 100000) AS g;

ANALYZE app.zip_code_master;
ANALYZE app.app_user;
ANALYZE app.family_relation;
ANALYZE app.announcement;
ANALYZE app.announcement_region;
