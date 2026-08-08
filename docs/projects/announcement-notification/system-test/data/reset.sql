-- ANNOUNCE-001 システムテスト: 業務テーブルの初期化
-- 各シナリオ実施前に実行し、テスト間の依存をなくす（FK順に TRUNCATE ... CASCADE）
TRUNCATE TABLE app.mail_send CASCADE;
TRUNCATE TABLE app.family_relation CASCADE;
TRUNCATE TABLE app.announcement_region CASCADE;
TRUNCATE TABLE app.announcement CASCADE;
TRUNCATE TABLE app.app_user CASCADE;
TRUNCATE TABLE app.zip_code_master CASCADE;
