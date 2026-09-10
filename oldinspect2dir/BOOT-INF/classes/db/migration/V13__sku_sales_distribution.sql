CREATE TABLE sku_config (
  id bigserial PRIMARY KEY,
  shop_id bigint NOT NULL REFERENCES dim_shop(id) ON DELETE CASCADE,
  display_name varchar(100) NOT NULL,
  seller_sku varchar(100) NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  sort_order int NOT NULL DEFAULT 0,
  UNIQUE(shop_id, seller_sku)
);

CREATE TABLE fact_order_sku (
  id bigserial PRIMARY KEY,
  shop_id bigint NOT NULL REFERENCES dim_shop(id) ON DELETE CASCADE,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  biz_date date NOT NULL,
  order_id varchar(100) NOT NULL,
  sku_id varchar(100) NOT NULL DEFAULT '',
  seller_sku varchar(100) NOT NULL DEFAULT '',
  source_status varchar(100) NOT NULL DEFAULT '',
  normalized_status varchar(32) NOT NULL,
  quantity bigint NOT NULL DEFAULT 0 CHECK(quantity >= 0),
  return_quantity bigint NOT NULL DEFAULT 0 CHECK(return_quantity >= 0),
  paid_time timestamptz,
  created_time timestamptz,
  cancel_type varchar(255),
  order_refund_amount numeric(18,2),
  raw_extra jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(shop_id, order_id, sku_id)
);

CREATE INDEX idx_order_sku_sales ON fact_order_sku(shop_id, market_code, biz_date, normalized_status);
CREATE INDEX idx_order_sku_mapping ON fact_order_sku(shop_id, seller_sku);
