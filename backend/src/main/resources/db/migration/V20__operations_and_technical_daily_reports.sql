ALTER TABLE daily_metric_report DROP CONSTRAINT daily_metric_report_report_type_check;
ALTER TABLE daily_metric_report ADD CONSTRAINT daily_metric_report_report_type_check
  CHECK (report_type IN ('EDITOR','DIRECTOR','ADS_BUYER','OPS','TECH'));
