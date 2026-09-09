ALTER TABLE daily_metric_report DROP CONSTRAINT daily_metric_report_report_type_check;
UPDATE daily_metric_report SET report_type = 'DIRECTOR' WHERE report_type = 'MARKET_LEAD';
ALTER TABLE daily_metric_report ADD CONSTRAINT daily_metric_report_report_type_check CHECK (report_type IN ('EDITOR','DIRECTOR','ADS_BUYER'));

INSERT INTO sys_user_role(user_id, role_id)
SELECT ur.user_id, director.id
FROM sys_user_role ur
JOIN sys_role old_role ON old_role.id = ur.role_id AND old_role.role_code = 'MARKET_LEAD'
CROSS JOIN sys_role director
WHERE director.role_code = 'DIRECTOR'
ON CONFLICT DO NOTHING;
DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'MARKET_LEAD');
DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'MARKET_LEAD');
DELETE FROM sys_role WHERE role_code = 'MARKET_LEAD';
