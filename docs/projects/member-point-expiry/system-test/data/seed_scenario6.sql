-- システムテスト No.6（失効履歴一意制約違反によるリラン手順）用テストデータ・運用手順SQL
-- 会員ポイント失効バッチ（POINT-001 pointExpiryJob）
-- 個人情報は使用しない。member_id は 92001〜 のダミー連番。
-- 実行手順は scenarios.md No.6 参照。以下の順に実行する（ジョブ実行を挟む）。

-- ステップA: 初回分の投入（この後 targetDate=2026-09-27 でジョブを実行し、920001 を正常に失効させる）
INSERT INTO app.point_grant (point_grant_id, member_id, points, granted_date, expiry_date, status, expired_date, created_at, updated_at) VALUES
 (920001, 92001, 1000, '2026-07-01', '2026-09-05', '1', NULL, now(), now());

-- ステップB: データ破損の模擬（本来行うべきでない誤った手動更新。920001 は履歴を持ったまま status='1' に戻る）
--   ＋対照用の正当な新規データ（同一トランザクションでの全ロールバックを確認するため）
--   ※ この後 targetDate=2026-09-28 でジョブを実行すると、
--     app.point_expiry_history.point_grant_id の一意制約違反で異常終了する（920001 の履歴が既存のため）
UPDATE app.point_grant SET status='1', expired_date=NULL, updated_at=now() WHERE point_grant_id=920001;
INSERT INTO app.point_grant (point_grant_id, member_id, points, granted_date, expiry_date, status, expired_date, created_at, updated_at) VALUES
 (920002, 92002, 2000, '2026-07-02', '2026-09-06', '1', NULL, now(), now());

-- ステップE: 運用手順「データ調査」（リラン前に必ず実行する。基本設計書§5・詳細設計書§5）
--   履歴を持つ付与が status='1' に戻っていないかを確認する
-- select g.point_grant_id, g.status, h.expired_date as history_expired_date
--   from app.point_grant g join app.point_expiry_history h on h.point_grant_id = g.point_grant_id
--  where g.status = '1';

-- ステップE': 運用手順「是正」（調査で判明した破損行を、既存履歴の内容に合わせて修復する）
--   このあと同一 targetDate=2026-09-28 でリスタートすると 920002 のみが失効し、正常終了する
UPDATE app.point_grant SET status='2', expired_date='2026-09-27', updated_at=now() WHERE point_grant_id=920001;
