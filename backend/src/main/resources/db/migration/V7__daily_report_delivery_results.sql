ALTER TABLE daily_metric_report
  ADD COLUMN delivery_results jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN submitted_at timestamptz;
