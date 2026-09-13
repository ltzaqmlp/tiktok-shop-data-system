DELETE FROM sys_role_menu
WHERE role_id = (SELECT id FROM sys_role WHERE role_code = 'BOSS');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT boss.id, dept_menu.menu_id
FROM sys_role boss
JOIN sys_role dept ON dept.role_code = 'DEPT_HEAD'
JOIN sys_role_menu dept_menu ON dept_menu.role_id = dept.id
WHERE boss.role_code = 'BOSS'
ON CONFLICT DO NOTHING;
