INSERT INTO dim_shop(market_code,shop_key,shop_name)
VALUES ('UK','UK_MAIN','英国主店')
ON CONFLICT(market_code,shop_key) DO NOTHING;

INSERT INTO sku_config(shop_id,display_name,seller_sku,sort_order)
SELECT s.id,v.display_name,v.seller_sku,v.sort_order
FROM dim_shop s CROSS JOIN (VALUES
  ('1瓶精华+刮痧板','12320JN-GS',100),
  ('1瓶精华','12320JN-1',200),
  ('2瓶精华','12320JN-11',300),
  ('刮痧板','12320FJGSS-1',400)
) v(display_name,seller_sku,sort_order)
WHERE s.market_code='UK' AND s.shop_key='UK_MAIN'
ON CONFLICT(shop_id,seller_sku) DO NOTHING;

INSERT INTO product_sku_mapping(shop_id,product_id,display_name,seller_sku,sort_order)
SELECT s.id,v.product_id,v.display_name,v.seller_sku,v.sort_order
FROM dim_shop s CROSS JOIN (VALUES
  ('1729910233073424914','1瓶精华+刮痧板','12320JN-GS',100),
  ('1729911165136771603','1瓶精华','12320JN-1',200),
  ('1729912706303105554','2瓶精华','12320JN-11',300),
  ('1729909490139568658','刮痧板','12320FJGSS-1',400)
) v(product_id,display_name,seller_sku,sort_order)
WHERE s.market_code='UK' AND s.shop_key='UK_MAIN'
ON CONFLICT(shop_id,product_id,seller_sku) DO NOTHING;
