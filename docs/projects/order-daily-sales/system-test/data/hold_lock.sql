-- No.4 用: ワークテーブルを排他ロックして Step 3（TRUNCATE）を待たせ、ジョブを「実行中（STARTED）」のまま保つ。
-- psql -v sec=40 -f hold_lock.sql（sec 秒後にコミットしてロックを解放する）
BEGIN;
LOCK TABLE app.order_detail_work IN ACCESS EXCLUSIVE MODE;
SELECT pg_sleep(:sec);
COMMIT;
