-- Legacy daily_report tables remain untouched for schema compatibility and are excluded from the new workflow.
INSERT INTO dim_market(market_code,market_name,currency_code) VALUES
  ('UK','英国','GBP'),('US','美国','USD'),('DE','德国','EUR'),('FR','法国','EUR')
ON CONFLICT(market_code) DO UPDATE SET market_name=excluded.market_name,currency_code=excluded.currency_code;

ALTER TABLE sys_user ADD COLUMN market_code varchar(16) REFERENCES dim_market(market_code);
CREATE INDEX idx_sys_user_market ON sys_user(market_code);

CREATE TABLE daily_metric_report (
  id bigserial PRIMARY KEY,
  report_date date NOT NULL,
  reporter_id bigint NOT NULL REFERENCES sys_user(id),
  report_type varchar(20) NOT NULL CHECK(report_type IN ('EDITOR','MARKET_LEAD','ADS_BUYER')),
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  submission_status varchar(24) NOT NULL DEFAULT 'DRAFT' CHECK(submission_status IN ('DRAFT','PENDING_MARKET','PENDING_DEPT','APPROVED','REJECTED')),
  rejection_stage varchar(20) CHECK(rejection_stage IN ('MARKET','DEPT')),
  rejection_reason text NOT NULL DEFAULT '',
  market_reviewer_id bigint REFERENCES sys_user(id), market_reviewed_at timestamptz,
  dept_reviewer_id bigint REFERENCES sys_user(id), dept_reviewed_at timestamptz,
  planned_review_videos bigint NOT NULL DEFAULT 0, actual_review_videos bigint NOT NULL DEFAULT 0,
  planned_valid_benchmark bigint NOT NULL DEFAULT 0, actual_valid_benchmark bigint NOT NULL DEFAULT 0,
  planned_deconstruction bigint NOT NULL DEFAULT 0, actual_deconstruction bigint NOT NULL DEFAULT 0,
  planned_complete_script bigint NOT NULL DEFAULT 0, actual_complete_script bigint NOT NULL DEFAULT 0,
  planned_ready_script bigint NOT NULL DEFAULT 0, actual_ready_script bigint NOT NULL DEFAULT 0,
  planned_new_publish bigint NOT NULL DEFAULT 0, actual_new_publish bigint NOT NULL DEFAULT 0,
  planned_first_review bigint NOT NULL DEFAULT 0, actual_first_review bigint NOT NULL DEFAULT 0,
  planned_rework_acceptance bigint NOT NULL DEFAULT 0, actual_rework_acceptance bigint NOT NULL DEFAULT 0,
  planned_test bigint NOT NULL DEFAULT 0, actual_test bigint NOT NULL DEFAULT 0,
  new_adjust_plan bigint NOT NULL DEFAULT 0, ad_spend numeric(18,2) NOT NULL DEFAULT 0,
  ad_gmv numeric(18,2) NOT NULL DEFAULT 0, impressions bigint NOT NULL DEFAULT 0, clicks bigint NOT NULL DEFAULT 0,
  orders bigint NOT NULL DEFAULT 0, expanded_material bigint NOT NULL DEFAULT 0, stopped_material bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(report_date,reporter_id,report_type,market_code),
  CHECK(planned_review_videos>=0 AND actual_review_videos>=0 AND planned_valid_benchmark>=0 AND actual_valid_benchmark>=0 AND planned_deconstruction>=0 AND actual_deconstruction>=0 AND planned_complete_script>=0 AND actual_complete_script>=0 AND planned_ready_script>=0 AND actual_ready_script>=0 AND planned_new_publish>=0 AND actual_new_publish>=0 AND planned_first_review>=0 AND actual_first_review>=0 AND planned_rework_acceptance>=0 AND actual_rework_acceptance>=0 AND planned_test>=0 AND actual_test>=0 AND new_adjust_plan>=0 AND ad_spend>=0 AND ad_gmv>=0 AND impressions>=0 AND clicks>=0 AND orders>=0 AND expanded_material>=0 AND stopped_material>=0)
);
CREATE INDEX idx_daily_metric_date_market_status ON daily_metric_report(report_date,market_code,submission_status);
