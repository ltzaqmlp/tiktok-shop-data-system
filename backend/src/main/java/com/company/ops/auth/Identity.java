package com.company.ops.auth;

import com.company.ops.common.*;
import com.company.ops.admin.AdminController;
import static com.company.ops.common.Db.p;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class Identity {
    private final Db db;
    public Identity(Db db){this.db=db;}
    public Map<String,Object> user(long id){
        var user=db.one("select id,username,display_name,org_unit_id,market_code,status,must_change_password,session_version,last_login_at from sys_user where id=#{p.id} and deleted_at is null",p("id",id));
        if(user.isEmpty())return user;
        user.put("roles",db.rows("select r.id,r.role_code,r.role_name from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=#{p.id} and r.enabled",p("id",id)));
        var markets=db.rows("select m.market_code,m.market_name,m.currency_code from sys_user_market um join dim_market m on m.market_code=um.market_code where um.user_id=#{p.id} and m.enabled order by m.market_code",p("id",id));
        user.put("markets",markets);user.put("marketCodes",markets.stream().map(m->m.get("marketCode").toString()).toList());
        user.put("permissions",canExport(user)?List.of("export"):List.of());return user;
    }
    @SuppressWarnings("unchecked")
    public static boolean role(Map<String,Object> user,String code){return ((List<Map<String,Object>>)user.getOrDefault("roles",List.of())).stream().anyMatch(r->code.equals(r.get("roleCode"))||("ADMIN".equals(code)&&"BOSS".equals(r.get("roleCode"))));}
    @SuppressWarnings("unchecked")
    public static boolean exactRole(Map<String,Object> user,String code){return ((List<Map<String,Object>>)user.getOrDefault("roles",List.of())).stream().anyMatch(r->code.equals(r.get("roleCode")));}
    public boolean canExport(Map<String,Object> user){return role(user,"ADMIN")||hasMenu(user,"export.details");}
    public boolean hasMenu(Map<String,Object> user,String code){return role(user,"ADMIN")||db.count("select count(*) from sys_role_menu rm join sys_user_role ur on ur.role_id=rm.role_id join sys_role r on r.id=rm.role_id join sys_menu m on m.id=rm.menu_id join sys_module md on md.id=m.module_id where ur.user_id=#{p.id} and m.menu_code=#{p.code} and r.enabled and m.enabled and md.status='ONLINE'",p("id",Long.valueOf(user.get("id").toString()),"code",code))>0;}
    public static List<String> marketCodes(Map<String,Object> user){
        Object raw=user.get("marketCodes");if(raw instanceof Collection<?> values)return values.stream().map(Objects::toString).filter(v->!v.isBlank()).toList();
        String legacy=Objects.toString(user.get("marketCode"),"");return legacy.isBlank()?List.of():List.of(legacy);
    }
    public static boolean hasMarket(Map<String,Object> user,String market){return role(user,"ADMIN")||role(user,"DEPT_HEAD")||marketCodes(user).contains(market);}
    public List<Map<String,Object>> menus(Map<String,Object> user){
        String scope=role(user,"ADMIN")?"":" and m.id in (with recursive visible(id) as (select rm.menu_id from sys_role_menu rm join sys_user_role ur on ur.role_id=rm.role_id join sys_role r on r.id=rm.role_id where ur.user_id=#{p.id} and r.enabled union select parent.parent_id from sys_menu parent join visible v on parent.id=v.id where parent.parent_id is not null) select id from visible)";
        return AdminController.tree(db.rows("select m.*,md.module_code from sys_menu m join sys_module md on md.id=m.module_id where m.enabled and md.status='ONLINE'"+scope+" order by m.sort_order,m.id",p("id",Long.valueOf(user.get("id").toString()))));
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> actor(HttpServletRequest req){return (Map<String,Object>)req.getAttribute("actor");}
    public static long uid(HttpServletRequest req){return Long.parseLong(actor(req).get("id").toString());}
}
