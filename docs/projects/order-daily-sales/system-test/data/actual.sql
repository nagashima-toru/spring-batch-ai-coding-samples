-- 対象日の集計結果を expected.sh と同じ書式で出す。psql -At -v d=2026-10-11 -f actual.sql
SELECT store_code || '|' || order_count || '|' || line_count || '|' || total_quantity || '|' || total_amount
FROM app.daily_store_sales WHERE sales_date = :'d' ORDER BY store_code COLLATE "C";
