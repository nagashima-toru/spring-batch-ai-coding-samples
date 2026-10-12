-- No.3 用: fail_trigger_on.sql で足したトリガと関数を外す
DROP TRIGGER IF EXISTS st_fail_work_insert ON app.order_detail_work;
DROP FUNCTION IF EXISTS app.st_fail_work_insert();
