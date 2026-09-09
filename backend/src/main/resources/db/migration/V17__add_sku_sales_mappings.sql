INSERT INTO sku_config (shop_id, display_name, seller_sku, sort_order)
SELECT s.id, v.display_name, v.seller_sku, v.sort_order
FROM dim_shop s
CROSS JOIN (VALUES
  ('2瓶精华液', 'SKUSHUADAN', 250),
  ('2瓶精华液+刮痧板', '12320JNZH', 500)
) v(display_name, seller_sku, sort_order)
WHERE s.shop_key = 'MY_MAIN'
ON CONFLICT (shop_id, seller_sku) DO UPDATE SET
  display_name = excluded.display_name,
  enabled = true,
  sort_order = excluded.sort_order,
  updated_at = now();
