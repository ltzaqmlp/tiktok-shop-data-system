CREATE TABLE daily_report (
  id bigserial PRIMARY KEY,
  report_date date NOT NULL,
  group_name varchar(100) NOT NULL,
  role_market varchar(100) NOT NULL,
  created_by bigint NOT NULL REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(report_date, created_by)
);

CREATE TABLE daily_report_task (
  id bigserial PRIMARY KEY,
  report_id bigint NOT NULL REFERENCES daily_report(id) ON DELETE CASCADE,
  sort_order int NOT NULL,
  work_detail text NOT NULL,
  plan_delivery text NOT NULL,
  actual_result text NOT NULL DEFAULT '',
  completion_status varchar(20) NOT NULL CHECK(completion_status IN ('未开始','进行中','已完成','已阻塞')),
  issue_next_step text NOT NULL DEFAULT '',
  result_link text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(report_id, sort_order)
);

CREATE INDEX idx_daily_report_date ON daily_report(report_date DESC, created_by);
