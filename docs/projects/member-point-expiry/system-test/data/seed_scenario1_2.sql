-- システムテスト No.1（日次サイクル正常系）/ No.2（自己回復）用テストデータ
-- 会員ポイント失効バッチ（POINT-001 pointExpiryJob）
-- 個人情報は使用しない。member_id は 91001〜 のダミー連番。
-- 実行前提: V4__create_member_point_expiry_tables.sql 適用済み（app.point_grant / app.point_expiry_history が存在）

INSERT INTO app.point_grant (point_grant_id, member_id, points, granted_date, expiry_date, status, expired_date, created_at, updated_at) VALUES
 (910001, 91001, 100, '2026-08-01', '2026-09-18', '1', NULL, now(), now()), -- No.1 targetDate=2026-09-20 で失効する（< target）
 (910002, 91002, 200, '2026-08-05', '2026-09-19', '1', NULL, now(), now()), -- No.1 targetDate=2026-09-20 で失効する（< target）
 (910003, 91003, 300, '2026-08-10', '2026-09-20', '1', NULL, now(), now()), -- 境界値: No.1 targetDate=2026-09-20 と同日→失効しない
 (910004, 91004, 400, '2026-08-15', '2026-09-21', '1', NULL, now(), now()), -- No.1では対象外。No.2 targetDate=2026-09-23 で失効する
 (910005, 91005, 500, '2026-08-20', '2026-09-22', '1', NULL, now(), now()), -- No.1では対象外。No.2 targetDate=2026-09-23 で失効する
 (910006, 91006, 600, '2026-07-01', '2026-08-01', '2', '2026-08-01', now(), now()), -- 既に失効済み（再対象化されないことの確認用）
 (910007, 91007, 700, '2026-08-25', '2026-09-23', '1', NULL, now(), now()); -- 境界値: No.2 targetDate=2026-09-23 と同日→失効しない
