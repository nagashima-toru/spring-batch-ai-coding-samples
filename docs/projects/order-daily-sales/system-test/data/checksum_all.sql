-- 本テーブル全体の件数とチェックサム（created_at を含む。「本テーブル不変」の確認用）。psql -At -f checksum_all.sql
SELECT 'order_detail', count(*), md5(string_agg(concat_ws(',', order_no, line_no, order_date, amount, created_at), ';' ORDER BY order_no, line_no))
FROM app.order_detail
UNION ALL
SELECT 'daily_store_sales', count(*), md5(string_agg(concat_ws(',', sales_date, store_code, total_amount, created_at), ';' ORDER BY sales_date, store_code))
FROM app.daily_store_sales;
