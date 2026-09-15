CREATE TABLE product_sku_mapping (
  id bigserial PRIMARY KEY,
  shop_id bigint NOT NULL REFERENCES dim_shop(id) ON DELETE CASCADE,
  product_id varchar(64) NOT NULL,
  display_name varchar(100) NOT NULL,
  seller_sku varchar(100) NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  sort_order int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(shop_id, product_id, seller_sku)
);

CREATE INDEX idx_product_sku_mapping_shop_product ON product_sku_mapping(shop_id, product_id, enabled);

CREATE TABLE fact_ad_product_daily (
  id bigserial PRIMARY KEY,
  shop_id bigint NOT NULL REFERENCES dim_shop(id) ON DELETE CASCADE,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  biz_date date NOT NULL,
  currency_code varchar(8) NOT NULL,
  campaign_id varchar(100) NOT NULL,
  product_id varchar(64) NOT NULL,
  spend numeric(18,2) NOT NULL DEFAULT 0,
  attributed_revenue numeric(18,2) NOT NULL DEFAULT 0,
  attributed_order_count bigint NOT NULL DEFAULT 0,
  raw_extra jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(shop_id, biz_date, campaign_id, product_id)
);

CREATE INDEX idx_ad_product_market_date ON fact_ad_product_daily(market_code, biz_date, shop_id, product_id);
