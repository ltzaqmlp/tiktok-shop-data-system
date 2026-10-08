\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(1);
CREATE TEMP TABLE campaign_before AS SELECT * FROM fact_ad_campaign_daily;
CREATE TEMP TABLE product_before AS SELECT * FROM fact_ad_product_daily;
CREATE TEMP TABLE dashboard_before AS SELECT * FROM agg_dashboard_daily;

DO $$ BEGIN
  ASSERT NOT EXISTS (SELECT 1 FROM sys_import_task WHERE status IN ('CREATED','VALIDATING','IMPORTING','AGGREGATING')), 'An import is running';
  ASSERT (SELECT count(*)=6 AND sum(spend)=219.24 AND sum(attributed_revenue)=329.72 AND sum(attributed_order_count)=20
    FROM fact_ad_campaign_daily WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06'), 'Campaign baseline changed';
  ASSERT (SELECT count(*)=18 AND sum(spend)=218.09 AND sum(attributed_revenue)=329.72 AND sum(attributed_order_count)=20
    FROM fact_ad_product_daily WHERE shop_id=1 AND campaign_id='1871565679015985' AND biz_date BETWEEN '2026-10-01' AND '2026-10-06'), 'Product baseline changed';
  ASSERT (SELECT count(*)=6 FROM agg_dashboard_daily WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06');
  ASSERT EXISTS (SELECT 1 FROM fact_ad_product_daily WHERE id=430 AND shop_id=1 AND campaign_id='1871565679015985'
    AND biz_date='2026-10-06' AND product_id='1736479479514957476' AND spend=36.57 AND attributed_revenue=43.34);
END $$;

-- The screenshot gives period totals only. Record the rounding residual on the
-- final day as an explicit test calibration, not a verified platform daily value.
UPDATE fact_ad_product_daily SET spend=spend+0.02, attributed_revenue=attributed_revenue+0.01,
  raw_extra=coalesce(raw_extra,'{}'::jsonb)||jsonb_build_object('_test_ad_reconciliation',jsonb_build_object(
    'period','2026-10-01~2026-10-06','reference','user supplied campaign screenshot 1871565679015985',
    'target_spend',218.11,'target_revenue',329.73,'target_orders',20,
    'spend_adjustment',0.02,'revenue_adjustment',0.01,
    'note','Period residual allocated to 2026-10-06 for test review; daily amount not verified',
    'previous_spend',spend,'previous_revenue',attributed_revenue)), updated_at=now()
WHERE id=430;

UPDATE fact_ad_campaign_daily c SET spend=p.spend, attributed_revenue=p.revenue, attributed_order_count=p.orders,
  raw_extra=coalesce(c.raw_extra,'{}'::jsonb)||jsonb_build_object('_test_ad_reconciliation',jsonb_build_object(
    'campaign_id','1871565679015985','period','2026-10-01~2026-10-06',
    'note','Test overview aligned to the user specified campaign; final-day residual is a period calibration',
    'previous_spend',c.spend,'previous_revenue',c.attributed_revenue)), updated_at=now()
FROM (SELECT biz_date,sum(spend) spend,sum(attributed_revenue) revenue,sum(attributed_order_count) orders
  FROM fact_ad_product_daily WHERE shop_id=1 AND campaign_id='1871565679015985'
  AND biz_date BETWEEN '2026-10-01' AND '2026-10-06' GROUP BY biz_date) p
WHERE c.shop_id=1 AND c.market_code='MY' AND c.campaign_id='overview' AND c.ad_account_key='default' AND c.biz_date=p.biz_date;

UPDATE agg_dashboard_daily a SET ad_spend=c.spend,ad_revenue=c.attributed_revenue,
  ad_order_count=c.attributed_order_count,updated_at=now()
FROM fact_ad_campaign_daily c WHERE a.shop_id=1 AND a.market_code='MY'
AND c.shop_id=a.shop_id AND c.biz_date=a.biz_date AND c.campaign_id='overview'
AND a.biz_date BETWEEN '2026-10-01' AND '2026-10-06';

DO $$ BEGIN
  ASSERT (SELECT sum(spend)=218.11 AND sum(attributed_revenue)=329.73 AND sum(attributed_order_count)=20
    AND round(sum(attributed_revenue)/sum(spend),2)=1.51 AND round(sum(spend)/sum(attributed_order_count),2)=10.91
    FROM fact_ad_campaign_daily WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06');
  ASSERT (SELECT sum(spend)=218.11 AND sum(attributed_revenue)=329.73 AND sum(attributed_order_count)=20
    FROM fact_ad_product_daily WHERE shop_id=1 AND campaign_id='1871565679015985' AND biz_date BETWEEN '2026-10-01' AND '2026-10-06');
  ASSERT (SELECT sum(ad_spend)=218.11 AND sum(ad_revenue)=329.73 AND sum(ad_order_count)=20
    FROM agg_dashboard_daily WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06');
  ASSERT NOT EXISTS (SELECT 1 FROM campaign_before b JOIN fact_ad_campaign_daily a USING(id)
    WHERE (to_jsonb(b)-ARRAY['spend','attributed_revenue','updated_at','raw_extra'])
       IS DISTINCT FROM (to_jsonb(a)-ARRAY['spend','attributed_revenue','updated_at','raw_extra']));
  ASSERT NOT EXISTS (SELECT 1 FROM product_before b JOIN fact_ad_product_daily a USING(id)
    WHERE (to_jsonb(b)-ARRAY['spend','attributed_revenue','updated_at','raw_extra'])
       IS DISTINCT FROM (to_jsonb(a)-ARRAY['spend','attributed_revenue','updated_at','raw_extra']));
  ASSERT NOT EXISTS (SELECT 1 FROM dashboard_before b JOIN agg_dashboard_daily a USING(shop_id,market_code,biz_date)
    WHERE (to_jsonb(b)-ARRAY['ad_spend','ad_revenue','updated_at'])
       IS DISTINCT FROM (to_jsonb(a)-ARRAY['ad_spend','ad_revenue','updated_at']));
  ASSERT NOT EXISTS (SELECT * FROM campaign_before WHERE NOT (shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06')
    EXCEPT SELECT * FROM fact_ad_campaign_daily);
  ASSERT NOT EXISTS (SELECT * FROM product_before WHERE id<>430 EXCEPT SELECT * FROM fact_ad_product_daily);
  ASSERT NOT EXISTS (SELECT * FROM dashboard_before WHERE NOT (shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06')
    EXCEPT SELECT * FROM agg_dashboard_daily);
END $$;

INSERT INTO sys_audit_log(user_id,module,action,target_type,target_id,request_id,success,request_summary,before_data,after_data)
SELECT 2,'import','MANUAL_AD_RECONCILE','test_campaign_period','MY:1:2026-10-01~2026-10-06',
  'test-ad-reconcile-20261007',true,
  jsonb_build_object('authorization','User requested direct test database correction','reference_campaign','1871565679015985',
    'backup','deploy/backup-test/ad-reconcile-20261007.dump',
    'note','Final-day spend +0.02 and revenue +0.01 are period-total calibrations, not verified daily platform data'),
  jsonb_build_object('campaign',(SELECT jsonb_agg(to_jsonb(c)) FROM campaign_before c WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06'),
    'product',(SELECT to_jsonb(p) FROM product_before p WHERE id=430),
    'dashboard',(SELECT jsonb_agg(to_jsonb(a)) FROM dashboard_before a WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06')),
  jsonb_build_object('campaign',(SELECT jsonb_agg(to_jsonb(c)) FROM fact_ad_campaign_daily c WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06'),
    'product',(SELECT to_jsonb(p) FROM fact_ad_product_daily p WHERE id=430));
COMMIT;

SELECT sum(spend) spend_usd,sum(attributed_order_count) sku_orders,sum(attributed_revenue) revenue_usd,
  round(sum(attributed_revenue)/sum(spend),2) roi,round(sum(spend)/sum(attributed_order_count),2) cpo_usd
FROM fact_ad_campaign_daily WHERE shop_id=1 AND market_code='MY' AND biz_date BETWEEN '2026-10-01' AND '2026-10-06';
