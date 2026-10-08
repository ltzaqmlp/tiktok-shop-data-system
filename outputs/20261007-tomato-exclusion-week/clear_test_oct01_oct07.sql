\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';
LOCK TABLE sys_import_task,sys_import_error,fact_shop_daily,fact_product_daily,
  fact_order,fact_order_sku,fact_affiliate_order,fact_ad_campaign_daily,
  fact_ad_product_daily,fact_refund_detail,fact_product_period,agg_dashboard_daily
  IN SHARE ROW EXCLUSIVE MODE;
DO $$ BEGIN
  ASSERT NOT EXISTS (SELECT 1 FROM sys_import_task WHERE status IN ('CREATED','VALIDATING','IMPORTING','AGGREGATING')), 'Import is running; no data deleted';
END $$;

CREATE TEMP TABLE remove_tasks AS
SELECT t.* FROM sys_import_task t WHERE
  (t.biz_date_from<='2026-10-07' AND t.biz_date_to>='2026-10-01')
  OR (t.status='FAILED' AND t.biz_date_from IS NULL AND t.biz_date_to IS NULL AND EXISTS (
    SELECT 1 FROM sys_import_task s WHERE s.shop_id=t.shop_id AND s.source_type=t.source_type
    AND s.original_filename=t.original_filename
    AND s.biz_date_from<='2026-10-07' AND s.biz_date_to>='2026-10-01'));
CREATE TEMP TABLE clear_counts(table_name text,deleted_rows bigint);

DO $$
DECLARE tab text; predicate text; before_hash text; after_hash text; deleted_count bigint; remaining_count bigint;
BEGIN
  ASSERT (SELECT count(*)=44 FROM remove_tasks), 'Import history changed since preview';
  FOREACH tab IN ARRAY ARRAY['fact_shop_daily','fact_product_daily','fact_order_sku','fact_order',
    'fact_affiliate_order','fact_ad_campaign_daily','fact_ad_product_daily','fact_refund_detail',
    'agg_dashboard_daily','fact_product_period','sys_import_error','sys_import_task'] LOOP
    predicate:=CASE tab
      WHEN 'fact_product_period' THEN 'date_from<=DATE ''2026-10-07'' AND date_to>=DATE ''2026-10-01'''
      WHEN 'sys_import_error' THEN 'task_id IN (SELECT id FROM remove_tasks)'
      WHEN 'sys_import_task' THEN 'id IN (SELECT id FROM remove_tasks)'
      ELSE 'biz_date BETWEEN DATE ''2026-10-01'' AND DATE ''2026-10-07''' END;
    EXECUTE format('SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'''' ORDER BY to_jsonb(t)::text),'''')) FROM %I t WHERE NOT (%s)',tab,predicate) INTO before_hash;
    EXECUTE format('DELETE FROM %I WHERE %s',tab,predicate);
    GET DIAGNOSTICS deleted_count=ROW_COUNT;
    INSERT INTO clear_counts VALUES(tab,deleted_count);
    EXECUTE format('SELECT count(*) FROM %I WHERE %s',tab,predicate) INTO remaining_count;
    ASSERT remaining_count=0, 'Target data remains in '||tab;
    EXECUTE format('SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'''' ORDER BY to_jsonb(t)::text),'''')) FROM %I t WHERE NOT (%s)',tab,predicate) INTO after_hash;
    ASSERT before_hash=after_hash, 'Unrelated data changed in '||tab;
  END LOOP;
  ASSERT (SELECT sum(deleted_rows)=185 FROM clear_counts WHERE table_name NOT IN ('sys_import_task','sys_import_error')), 'Fact data changed since preview';
  ASSERT NOT EXISTS (SELECT 1 FROM sys_import_task WHERE biz_date_from<='2026-10-07' AND biz_date_to>='2026-10-01');
END $$;

INSERT INTO sys_audit_log(user_id,module,action,target_type,target_id,request_id,success,request_summary)
VALUES(2,'import','TEST_DATE_DATA_CLEAR','test_business_dates','2026-10-01~2026-10-07',
  'test-date-clear-20261008',true,jsonb_build_object(
    'authorization','User requested clearing Oct 1 through Oct 7 test data and matching import history for reimport',
    'counts',(SELECT jsonb_object_agg(table_name,deleted_rows) FROM clear_counts),
    'import_task_ids',(SELECT jsonb_agg(id ORDER BY id) FROM remove_tasks),
    'period_reports','Overlapping product-period reports removed; daily data outside the date range preserved'));
COMMIT;
SELECT * FROM clear_counts ORDER BY table_name;
