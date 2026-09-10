CREATE TABLE editor_daily_task_plan (
  id bigserial PRIMARY KEY,
  report_date date NOT NULL,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  task_code varchar(32) NOT NULL CHECK (task_code IN ('NEW_PUBLISH','FIRST_REVIEW','REWORK_ACCEPTANCE')),
  planned_count bigint NOT NULL CHECK (planned_count >= 0),
  sort_order int NOT NULL,
  director_id bigint NOT NULL REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(report_date, market_code, task_code)
);

CREATE INDEX idx_editor_daily_task_plan_date_market ON editor_daily_task_plan(report_date, market_code, sort_order);
