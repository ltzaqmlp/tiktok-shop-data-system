ALTER TABLE daily_metric_report
  ADD COLUMN notes text NOT NULL DEFAULT '';

CREATE TABLE daily_report_summary (
  id bigserial PRIMARY KEY,
  report_date date NOT NULL UNIQUE,
  today_important_result text NOT NULL DEFAULT '',
  need_boss_support text NOT NULL DEFAULT '',
  tomorrow_focus text NOT NULL DEFAULT '',
  submission_status varchar(24) NOT NULL DEFAULT 'DRAFT' CHECK(submission_status IN ('DRAFT','REJECTED','APPROVED')),
  rejection_reason text NOT NULL DEFAULT '',
  reviewer_id bigint REFERENCES sys_user(id),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
