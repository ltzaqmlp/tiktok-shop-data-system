DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'TEAM_LEAD');
DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'TEAM_LEAD');
DELETE FROM sys_role WHERE role_code = 'TEAM_LEAD';
