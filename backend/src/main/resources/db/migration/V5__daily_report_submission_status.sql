ALTER TABLE daily_report
  ADD COLUMN submission_status varchar(20) NOT NULL DEFAULT 'SUBMITTED' CHECK(submission_status IN ('DRAFT','SUBMITTED')),
  ADD COLUMN submitted_at timestamptz;

CREATE INDEX idx_daily_report_submitted ON daily_report(submission_status, report_date DESC, created_by);
