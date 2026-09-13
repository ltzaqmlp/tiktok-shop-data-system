DELETE FROM sys_role_menu
WHERE role_id = (SELECT id FROM sys_role WHERE role_code = 'BOSS');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT boss.id, admin_menu.menu_id
FROM sys_role boss
JOIN sys_role admin ON admin.role_code = 'ADMIN'
JOIN sys_role_menu admin_menu ON admin_menu.role_id = admin.id
WHERE boss.role_code = 'BOSS'
ON CONFLICT DO NOTHING;
