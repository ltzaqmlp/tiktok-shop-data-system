CREATE TABLE director_daily_task_plan_setting (
  report_date date NOT NULL,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  director_id bigint NOT NULL REFERENCES sys_user(id),
  dept_head_id bigint NOT NULL REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (report_date, market_code, director_id)
);

CREATE TABLE director_daily_task_plan (
  id bigserial PRIMARY KEY,
  report_date date NOT NULL,
  market_code varchar(16) NOT NULL REFERENCES dim_market(market_code),
  director_id bigint NOT NULL REFERENCES sys_user(id),
  task_code varchar(100) NOT NULL,
  task_name varchar(100) NOT NULL,
  planned_count bigint NOT NULL CHECK (planned_count >= 0),
  sort_order int NOT NULL,
  dept_head_id bigint NOT NULL REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(report_date, market_code, director_id, task_code)
);

CREATE INDEX idx_director_daily_task_plan_editor
  ON director_daily_task_plan(report_date, market_code, director_id, sort_order);

ALTER TABLE daily_metric_report
  ADD COLUMN director_task_results jsonb NOT NULL DEFAULT '{}'::jsonb;
