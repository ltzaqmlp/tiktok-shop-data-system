ALTER TABLE sys_user ADD COLUMN operations_supervisor_id bigint REFERENCES sys_user(id);
ALTER TABLE sys_user ADD CONSTRAINT sys_user_operations_supervisor_not_self CHECK (operations_supervisor_id IS NULL OR operations_supervisor_id <> id);
CREATE INDEX idx_sys_user_operations_supervisor ON sys_user(operations_supervisor_id) WHERE deleted_at IS NULL;

UPDATE sys_role SET role_name='运营主管',updated_at=now() WHERE role_code='OPS';
INSERT INTO sys_role(role_code,role_name,system_role) VALUES ('OPS_ASSISTANT','运营助理',true) ON CONFLICT(role_code) DO NOTHING;

ALTER TABLE daily_metric_report DROP CONSTRAINT daily_metric_report_report_type_check;
ALTER TABLE daily_metric_report ADD CONSTRAINT daily_metric_report_report_type_check CHECK (report_type IN ('EDITOR','DIRECTOR','ADS_BUYER','OPS','OPS_ASSISTANT','TECH'));
ALTER TABLE daily_metric_report DROP CONSTRAINT daily_metric_report_submission_status_check;
ALTER TABLE daily_metric_report ADD CONSTRAINT daily_metric_report_submission_status_check CHECK (submission_status IN ('DRAFT','PENDING_MARKET','PENDING_OPS','PENDING_DEPT','APPROVED','REJECTED'));
ALTER TABLE daily_metric_report DROP CONSTRAINT daily_metric_report_rejection_stage_check;
ALTER TABLE daily_metric_report ADD CONSTRAINT daily_metric_report_rejection_stage_check CHECK (rejection_stage IN ('MARKET','OPS','DEPT'));
ALTER TABLE daily_metric_report ADD COLUMN operations_reviewer_id bigint REFERENCES sys_user(id), ADD COLUMN operations_reviewed_at timestamptz;
