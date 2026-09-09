INSERT INTO fact_order_sku (
  shop_id, market_code, biz_date, order_id, sku_id, seller_sku,
  source_status, normalized_status, quantity, return_quantity,
  paid_time, created_time, cancel_type, order_refund_amount, raw_extra
)
SELECT
  o.shop_id,
  o.market_code,
  o.biz_date,
  o.order_id,
  coalesce(o.raw_extra->>'5', ''),
  coalesce(o.raw_extra->>'6', ''),
  o.source_status,
  o.normalized_status,
  o.item_qty,
  coalesce(o.returned_qty, 0),
  case when nullif(o.raw_extra->>'25', '') is null then null
       else to_timestamp(o.raw_extra->>'25', 'DD/MM/YYYY HH24:MI:SS') end,
  o.order_created_at,
  nullif(o.raw_extra->>'3', ''),
  o.order_refund_amount,
  o.raw_extra
FROM fact_order o
ON CONFLICT (shop_id, order_id, sku_id) DO NOTHING;
