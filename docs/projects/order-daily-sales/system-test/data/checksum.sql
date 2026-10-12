-- 対象日の本テーブル内容のチェックサム（created_at を除く）。psql -At -v d=2026-10-13 -f checksum.sql
SELECT 'order_detail', count(*), md5(string_agg(concat_ws(',', order_no, line_no, order_date, store_code, item_code, quantity, unit_price, amount), E'\n' ORDER BY order_no, line_no))
FROM app.order_detail WHERE order_date = :'d'
UNION ALL
SELECT 'daily_store_sales', count(*), md5(string_agg(concat_ws(',', sales_date, store_code, order_count, line_count, total_quantity, total_amount), E'\n' ORDER BY store_code))
FROM app.daily_store_sales WHERE sales_date = :'d';
