-- Core daily KPI fields that are present in TikTok Shop Analytics exports.
-- Nullable is intentional: rows imported before this migration remain distinguishable
-- from a genuine zero until the Shop Analytics file is re-imported.
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS sku_order_count bigint;
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS refund_amount numeric(18,2);
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS customer_count bigint;
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS page_view_count bigint;
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS impressions bigint;
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS clicks bigint;
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS unique_impressions bigint;
ALTER TABLE fact_shop_daily ADD COLUMN IF NOT EXISTS unique_clicks bigint;

-- TikTok product-list exports may represent a whole date range. Those values must
-- never be assigned to one arbitrary business day, otherwise daily KPI and trend
-- calculations are corrupted. Store range aggregates separately.
CREATE TABLE IF NOT EXISTS fact_product_period (
    id bigserial PRIMARY KEY,
    shop_id bigint NOT NULL REFERENCES dim_shop(id),
    market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
    date_from date NOT NULL,
    date_to date NOT NULL,
    currency_code varchar(8) NOT NULL,
    product_id varchar(64) NOT NULL,
    gmv numeric(18,2) NOT NULL DEFAULT 0,
    order_count bigint NOT NULL DEFAULT 0,
    sku_order_count bigint NOT NULL DEFAULT 0,
    sold_qty bigint NOT NULL DEFAULT 0,
    estimated_customer_count bigint NOT NULL DEFAULT 0,
    impressions bigint NOT NULL DEFAULT 0,
    clicks bigint NOT NULL DEFAULT 0,
    add_to_cart_count bigint NOT NULL DEFAULT 0,
    refund_amount numeric(18,2),
    refunded_qty bigint,
    refund_customer_count bigint,
    unique_impressions bigint,
    unique_clicks bigint,
    added_user_count bigint,
    raw_extra jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK(date_from <= date_to),
    UNIQUE(shop_id,date_from,date_to,product_id)
);
CREATE INDEX IF NOT EXISTS idx_product_period_market_range ON fact_product_period(market_code,date_from,date_to);
CREATE INDEX IF NOT EXISTS idx_product_period_shop_range ON fact_product_period(shop_id,date_from,date_to);

-- Keep useful return/refund fields from order exports for drill-down/reconciliation.
-- Dashboard refund-by-day remains sourced from Shop Analytics because the order file
-- does not provide an authoritative refund-occurrence date.
ALTER TABLE fact_order ADD COLUMN IF NOT EXISTS returned_qty bigint;
ALTER TABLE fact_order ADD COLUMN IF NOT EXISTS order_refund_amount numeric(18,2);
