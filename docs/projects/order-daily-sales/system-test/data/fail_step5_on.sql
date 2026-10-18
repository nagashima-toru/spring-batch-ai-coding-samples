-- No.7 用: 受注明細（本テーブル）への INSERT を例外で失敗させるトリガを一時的に足す（Step 5 の失敗の注入）
CREATE OR REPLACE FUNCTION app.st_fail_detail_insert() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'system-test: forced failure on app.order_detail insert (order_no=%)', NEW.order_no;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER st_fail_detail_insert BEFORE INSERT ON app.order_detail
  FOR EACH ROW EXECUTE FUNCTION app.st_fail_detail_insert();
