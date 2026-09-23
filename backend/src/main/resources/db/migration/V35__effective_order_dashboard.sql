-- An order amount is stored once per platform order, while the dashboard's
-- "orders" count follows TikTok's SKU-order (order ID + SKU ID) grain.
-- ponytail: a mixed order is excluded in full because its order-level amount
-- cannot be split reliably; add SKU-level paid amounts if mixed orders appear.
CREATE VIEW v_effective_order AS
SELECT o.shop_id, o.market_code, o.biz_date, o.currency_code, o.order_id,
       o.normalized_status, o.order_amount,
       coalesce(o.order_refund_amount, 0) refund_amount,
       count(*) FILTER (WHERE s.quantity > 0) sku_order_count,
       sum(greatest(s.quantity - least(s.return_quantity, s.quantity), 0)) sold_qty,
       sum(least(s.return_quantity, s.quantity)) refunded_qty
FROM fact_order o
JOIN fact_order_sku s ON s.shop_id = o.shop_id AND s.order_id = o.order_id
WHERE o.normalized_status IN ('PAID', 'SHIPPED', 'COMPLETED')
GROUP BY o.id
HAVING bool_and(btrim(s.seller_sku) <> '')
   AND bool_and(s.normalized_status IN ('PAID', 'SHIPPED', 'COMPLETED'))
   AND sum(s.quantity) > 0;

CREATE VIEW v_effective_order_daily AS
SELECT shop_id, market_code, biz_date, currency_code,
       sum(order_amount) gmv,
       sum(sku_order_count) order_count,
       sum(sold_qty) sold_qty,
       sum(sku_order_count) sku_order_count,
       sum(refund_amount) refund_amount,
       sum(refunded_qty) refunded_qty,
       count(*) FILTER (WHERE refund_amount > 0 OR refunded_qty > 0) refund_order_count
FROM v_effective_order
GROUP BY shop_id, market_code, biz_date, currency_code;
