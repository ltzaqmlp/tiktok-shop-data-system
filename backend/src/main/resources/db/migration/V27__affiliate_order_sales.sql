CREATE TABLE fact_affiliate_order (
  id bigserial PRIMARY KEY,
  shop_id bigint NOT NULL REFERENCES dim_shop(id) ON DELETE CASCADE,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  biz_date date NOT NULL,
  currency_code varchar(8) NOT NULL,
  order_id varchar(100) NOT NULL,
  sku_id varchar(100) NOT NULL DEFAULT '',
  source_status varchar(100) NOT NULL DEFAULT '',
  normalized_status varchar(32) NOT NULL,
  order_amount numeric(18,2) NOT NULL DEFAULT 0,
  item_qty bigint NOT NULL DEFAULT 0 CHECK(item_qty >= 0),
  returned_qty bigint NOT NULL DEFAULT 0 CHECK(returned_qty >= 0),
  order_created_at timestamptz,
  affiliate_username varchar(255) NOT NULL DEFAULT '',
  order_refund_amount numeric(18,2),
  raw_extra jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(shop_id, order_id, sku_id)
);

CREATE INDEX idx_affiliate_order_sales ON fact_affiliate_order(shop_id, market_code, biz_date, normalized_status);
