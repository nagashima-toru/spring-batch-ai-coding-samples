-- No.3 用: ワークへの INSERT を後半の行（line_seq >= 350）で失敗させるトリガを一時的に足す
CREATE OR REPLACE FUNCTION app.st_fail_work_insert() RETURNS trigger AS $$
BEGIN
  IF NEW.line_seq >= 350 THEN
    RAISE EXCEPTION 'system-test: forced failure at line_seq=%', NEW.line_seq;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER st_fail_work_insert BEFORE INSERT ON app.order_detail_work
  FOR EACH ROW EXECUTE FUNCTION app.st_fail_work_insert();
