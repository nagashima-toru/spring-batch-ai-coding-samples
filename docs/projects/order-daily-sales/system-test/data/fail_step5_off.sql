-- No.7 用: fail_step5_on.sql で足したトリガと関数を外す
DROP TRIGGER IF EXISTS st_fail_detail_insert ON app.order_detail;
DROP FUNCTION IF EXISTS app.st_fail_detail_insert();
