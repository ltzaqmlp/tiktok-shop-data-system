ALTER TABLE editor_daily_task_plan DROP CONSTRAINT editor_daily_task_plan_task_code_check;
ALTER TABLE editor_daily_task_plan ALTER COLUMN task_code TYPE varchar(100);
ALTER TABLE editor_daily_task_plan ADD COLUMN task_name varchar(100) NOT NULL DEFAULT '';
UPDATE editor_daily_task_plan SET task_name = CASE task_code WHEN 'NEW_PUBLISH' THEN '新增发布' WHEN 'FIRST_REVIEW' THEN '首次交审' WHEN 'REWORK_ACCEPTANCE' THEN '返工验收' ELSE task_code END;
ALTER TABLE editor_daily_task_plan ALTER COLUMN task_name DROP DEFAULT;
ALTER TABLE daily_metric_report ADD COLUMN editor_task_results jsonb NOT NULL DEFAULT '{}'::jsonb;
