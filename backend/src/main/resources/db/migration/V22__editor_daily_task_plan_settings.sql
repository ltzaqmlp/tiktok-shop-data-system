CREATE TABLE editor_daily_task_plan_setting (
  report_date date NOT NULL,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  director_id bigint NOT NULL REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (report_date, market_code)
);
