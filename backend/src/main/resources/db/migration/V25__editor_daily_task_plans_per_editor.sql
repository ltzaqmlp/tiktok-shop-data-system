ALTER TABLE editor_daily_task_plan_setting
  ADD COLUMN editor_id bigint REFERENCES sys_user(id);

ALTER TABLE editor_daily_task_plan
  ADD COLUMN editor_id bigint REFERENCES sys_user(id);

ALTER TABLE editor_daily_task_plan_setting
  DROP CONSTRAINT editor_daily_task_plan_setting_pkey;

ALTER TABLE editor_daily_task_plan
  DROP CONSTRAINT editor_daily_task_plan_report_date_market_code_task_code_key;

CREATE UNIQUE INDEX uq_editor_daily_task_plan_setting_editor
  ON editor_daily_task_plan_setting(report_date, market_code, editor_id);

CREATE UNIQUE INDEX uq_editor_daily_task_plan_editor_task
  ON editor_daily_task_plan(report_date, market_code, editor_id, task_code);

CREATE INDEX idx_editor_daily_task_plan_editor
  ON editor_daily_task_plan(report_date, market_code, editor_id, sort_order);

INSERT INTO editor_daily_task_plan_setting(report_date, market_code, editor_id, director_id)
SELECT s.report_date, s.market_code, u.id, s.director_id
FROM editor_daily_task_plan_setting s
JOIN sys_user u ON u.market_code = s.market_code
  AND u.status = 'ACTIVE'
  AND u.deleted_at IS NULL
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id AND r.role_code = 'MARKET_MEMBER'
WHERE s.editor_id IS NULL
ON CONFLICT (report_date, market_code, editor_id) DO NOTHING;

INSERT INTO editor_daily_task_plan(report_date, market_code, editor_id, task_code, task_name, planned_count, sort_order, director_id)
SELECT p.report_date, p.market_code, u.id, p.task_code, p.task_name, p.planned_count, p.sort_order, p.director_id
FROM editor_daily_task_plan p
JOIN sys_user u ON u.market_code = p.market_code
  AND u.status = 'ACTIVE'
  AND u.deleted_at IS NULL
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id AND r.role_code = 'MARKET_MEMBER'
WHERE p.editor_id IS NULL
ON CONFLICT (report_date, market_code, editor_id, task_code) DO NOTHING;
