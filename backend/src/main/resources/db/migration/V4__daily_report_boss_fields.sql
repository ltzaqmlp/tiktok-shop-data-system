ALTER TABLE daily_report
  ADD COLUMN today_focus text NOT NULL DEFAULT '',
  ADD COLUMN key_result text NOT NULL DEFAULT '',
  ADD COLUMN need_boss_support text NOT NULL DEFAULT '',
  ADD COLUMN tomorrow_focus text NOT NULL DEFAULT '';

ALTER TABLE daily_report_task ADD COLUMN work_module varchar(100) NOT NULL DEFAULT '';
