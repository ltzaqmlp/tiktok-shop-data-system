package com.company.ops.admin;

import com.company.ops.auth.AuthController;
import com.company.ops.common.*;
import static com.company.ops.common.Db.p;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
public class ModuleRegistrar implements ApplicationRunner {
    private final Db db;private final PasswordEncoder passwords;
    @Value("${app.admin-username}")String username;@Value("${app.admin-password}")String password;@Value("${app.shop-name}")String shopName;@Value("${app.shop-key}")String shopKey;
    public ModuleRegistrar(Db db,PasswordEncoder passwords){this.db=db;this.passwords=passwords;}
    @Override @Transactional public void run(ApplicationArguments ignored){
        String[][] roles={{"ADMIN","系统管理员"},{"BOSS","老板"},{"DEPT_HEAD","部门负责人"},{"TEAM_LEAD","小组负责人"},{"OPS","运营"},{"ADS_BUYER","投流投手"},{"MARKET_LEAD","市场负责人"},{"MARKET_MEMBER","市场成员"},{"DIRECTOR","编导"},{"SHOOTER","拍摄"}};
        for(var r:roles)db.exec("insert into sys_role(role_code,role_name,system_role) values(#{p.code},#{p.name},true) on conflict(role_code) do nothing",p("code",r[0],"name",r[1]));
        db.exec("update sys_module set status='OFFLINE'",Map.of());
        String[][] menus={{"dashboard","dashboard.overview","经营驾驶舱","/dashboard","dashboard/index","DataBoard"},{"daily","daily.report","日报","/daily-reports","daily/reports","Document"},{"shooting","shooting.ticket","拍摄工单","/shooting-tickets","shooting/tickets","Document"},{"import","import.upload","数据导入","/data-import","import/index","Upload"},{"admin","admin.group","系统管理",null,null,"Menu"},{"admin","admin.users","用户管理","/admin/users","admin/users","User"},{"admin","admin.roles","角色管理","/admin/roles","admin/roles","Key"},{"admin","admin.menus","菜单管理","/admin/menus","admin/menus","Menu"},{"admin","admin.audit","审计日志","/admin/audit","admin/audit","Document"},{"admin","admin.login-logs","登录日志","/admin/login-logs","admin/login-logs","Connection"},{"export","export.details","明细导出",null,null,"Document"}};
        int order=100;
        for(var m:menus){
            db.exec("insert into sys_module(module_code,name,version,status) values(#{p.code},#{p.name},'1.0.0','ONLINE') on conflict(module_code) do update set version='1.0.0',status='ONLINE',registered_at=now()",p("code",m[0],"name",m[0]));
            db.exec("insert into sys_menu(module_id,menu_code,name,route_path,component_key,icon,sort_order) values((select id from sys_module where module_code=#{p.module}),#{p.code},#{p.name},#{p.route},#{p.component},#{p.icon},#{p.order}) on conflict(menu_code) do update set route_path=excluded.route_path,component_key=excluded.component_key,module_id=excluded.module_id",p("module",m[0],"code",m[1],"name",m[2],"route",m[3],"component",m[4],"icon",m[5],"order",order));order+=100;
        }
        db.exec("update sys_menu set parent_id=(select id from sys_menu where menu_code='admin.group') where menu_code like 'admin.%' and menu_code<>'admin.group'",Map.of());
        db.exec("insert into sys_role_menu(role_id,menu_id) select r.id,m.id from sys_role r join sys_menu m on m.menu_code='dashboard.overview' on conflict do nothing",Map.of());
        db.exec("insert into sys_role_menu(role_id,menu_id) select r.id,m.id from sys_role r cross join sys_menu m where (r.role_code='ADMIN' and m.menu_code in ('dashboard.overview','daily.report','shooting.ticket','import.upload','export.details','admin.users','admin.roles','admin.menus','admin.audit','admin.login-logs')) or (r.role_code in ('BOSS','DEPT_HEAD','TEAM_LEAD','OPS','ADS_BUYER','MARKET_LEAD','MARKET_MEMBER','SHOOTER') and m.menu_code in ('dashboard.overview','daily.report')) or (r.role_code in ('DEPT_HEAD','TEAM_LEAD','OPS','ADS_BUYER','MARKET_LEAD','MARKET_MEMBER','SHOOTER') and m.menu_code='import.upload') or (r.role_code in ('DEPT_HEAD','DIRECTOR','SHOOTER') and m.menu_code='shooting.ticket') or (r.role_code='BOSS' and m.menu_code='export.details') on conflict do nothing",Map.of());
        db.exec("delete from sys_role_menu where role_id in (select id from sys_role where role_code in ('TEAM_LEAD','OPS','SHOOTER')) and menu_id=(select id from sys_menu where menu_code='daily.report')",Map.of());
        if(db.count("select count(*) from sys_user",Map.of())==0){
            AuthController.validatePassword(password);
            String id=db.insert("insert into sys_user(username,display_name,password_hash) values(#{p.username},'系统管理员',#{p.hash})",p("username",username,"hash",passwords.encode(password)));
            db.exec("insert into sys_user_role(user_id,role_id) select #{p.id},id from sys_role where role_code='ADMIN'",p("id",Long.valueOf(id)));
            db.exec("insert into sys_role_menu(role_id,menu_id) select r.id,m.id from sys_role r cross join sys_menu m where r.role_code='ADMIN' or (r.role_code in ('BOSS','DEPT_HEAD','TEAM_LEAD','OPS','ADS_BUYER','MARKET_LEAD') and m.menu_code='dashboard.overview') or (r.role_code='BOSS' and m.menu_code='export.details') on conflict do nothing",Map.of());
        }
        db.exec("insert into dim_shop(market_code,shop_key,shop_name) values('MY',#{p.key},#{p.name}) on conflict(market_code,shop_key) do nothing",p("key",shopKey,"name",shopName));
        // Interrupted facts transaction rolls back; aggregate-only failures remain explicitly retryable.
        db.exec("update sys_import_task set status=case when status='AGGREGATING' then 'AGGREGATE_FAILED' else 'FAILED' end,error_message='服务重启中断任务，请重新导入或重算',finished_at=now() where status in ('CREATED','VALIDATING','IMPORTING','AGGREGATING')",Map.of());
    }
}
