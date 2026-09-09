package com.company.ops.auth;

import com.company.ops.common.*;
import static com.company.ops.common.Db.p;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.*;

@RestController @RequestMapping("/api/v1")
public class AuthController {
    private final Db db;private final Identity identity;private final PasswordEncoder passwords;private final TransactionTemplate tx;
    @Value("${app.login-max-failures}") int threshold;@Value("${app.lock-minutes}") int lockMinutes;
    private final String dummy;
    public AuthController(Db db,Identity identity,PasswordEncoder passwords,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.identity=identity;this.passwords=passwords;this.tx=new TransactionTemplate(manager);dummy=passwords.encode(UUID.randomUUID().toString());}
    public static void validatePassword(String value){Api.require(value.length()>=10&&value.length()<=72,"密码须为10–72位");int kinds=0;for(String regex:List.of(".*[a-zA-Z].*",".*[0-9].*",".*[^a-zA-Z0-9].*"))if(value.matches(regex))kinds++;Api.require(kinds>=2,"密码须包含字母、数字、符号中的两类");}
    @PostMapping("/auth/login") public Object login(@RequestBody Map<String,Object> body,HttpServletRequest req,HttpServletResponse res){
        String username=Api.text(body,"username",64,true),password=Objects.toString(body.get("password"),"");Api.require(password.length()<=72,"密码过长");
        Map<String,Object> result=tx.execute(s->{
            var u=db.one("select * from sys_user where username=#{p.username} and deleted_at is null for update",p("username",username));String reason=null;
            boolean valid=passwords.matches(password,u.isEmpty()?dummy:u.get("passwordHash").toString());
            if(!u.isEmpty()&&"DISABLED".equals(u.get("status")))reason="账号已停用";
            else if(!u.isEmpty()&&u.get("lockedUntil")!=null&&Instant.parse(u.get("lockedUntil").toString()).isAfter(Instant.now()))reason="账号暂时锁定";
            else if(!valid){reason="账号或密码错误";if(!u.isEmpty())db.exec("update sys_user set failed_login_count=failed_login_count+1,status=case when failed_login_count+1>=#{p.max} then 'LOCKED' else status end,locked_until=case when failed_login_count+1>=#{p.max} then now()+(#{p.minutes} * interval '1 minute') else locked_until end where id=#{p.id}",p("max",threshold,"minutes",lockMinutes,"id",Long.valueOf(u.get("id").toString())));}
            if(reason==null)db.exec("update sys_user set status='ACTIVE',failed_login_count=0,locked_until=null,last_login_at=now() where id=#{p.id}",p("id",Long.valueOf(u.get("id").toString())));
            db.exec("insert into sys_login_log(user_id,username,success,reason,ip,user_agent) values(#{p.id},#{p.username},#{p.success},#{p.reason},#{p.ip},#{p.ua})",p("id",u.isEmpty()?null:Long.valueOf(u.get("id").toString()),"username",username,"success",reason==null,"reason",reason,"ip",req.getRemoteAddr(),"ua",Objects.toString(req.getHeader("User-Agent"),"").substring(0,Math.min(500,Objects.toString(req.getHeader("User-Agent"),"").length()))));
            return p("user",u,"reason",reason);
        });
        if(result.get("reason")!=null)throw new Api.Problem(401,result.get("reason").equals("账号暂时锁定")?"AUTH_ACCOUNT_LOCKED":"AUTH_INVALID_CREDENTIALS",result.get("reason").toString());
        @SuppressWarnings("unchecked")var u=(Map<String,Object>)result.get("user");
        if(req.getSession(false)!=null)req.getSession(false).invalidate();var session=req.getSession(true);boolean remember=Boolean.TRUE.equals(body.get("rememberMe"));session.setMaxInactiveInterval(remember?30*24*60*60:4*60*60);session.setAttribute("uid",Long.parseLong(u.get("id").toString()));session.setAttribute("version",u.get("sessionVersion"));var cookie=new Cookie("JSESSIONID",session.getId());cookie.setMaxAge(remember?30*24*60*60:-1);cookie.setHttpOnly(true);cookie.setSecure(req.isSecure());cookie.setPath(req.getContextPath().isBlank()?"/":req.getContextPath());cookie.setAttribute("SameSite","Lax");res.addCookie(cookie);
        return Api.ok(req,Map.of("mustChangePassword",u.get("mustChangePassword")));
    }
    @GetMapping("/auth/session") public Object session(HttpServletRequest req){var session=req.getSession(false);return Api.ok(req,Map.of("authenticated",session!=null&&session.getAttribute("uid")!=null));}
    @GetMapping("/auth/me") public Object me(HttpServletRequest req){var user=new LinkedHashMap<>(Identity.actor(req));user.remove("sessionVersion");return Api.ok(req,user);}
    @GetMapping("/me/menus") public Object menus(HttpServletRequest req){return Api.ok(req,identity.menus(Identity.actor(req)));}
    @PostMapping("/auth/logout") public Object logout(HttpServletRequest req){req.getSession().invalidate();return Api.ok(req,Map.of());}
    @PostMapping("/auth/change-password") public Object change(@RequestBody Map<String,Object> body,HttpServletRequest req){
        String old=Objects.toString(body.get("oldPassword"),""),next=Objects.toString(body.get("newPassword"),"");validatePassword(next);Api.require(!next.equals(old),"新密码不能与原密码相同");
        tx.executeWithoutResult(s->{var u=db.one("select password_hash from sys_user where id=#{p.id} for update",p("id",Identity.uid(req)));if(!passwords.matches(old,u.get("passwordHash").toString()))throw new Api.Problem(400,"AUTH_INVALID_CREDENTIALS","原密码不正确");db.exec("update sys_user set password_hash=#{p.hash},must_change_password=false,session_version=session_version+1,updated_at=now() where id=#{p.id}",p("id",Identity.uid(req),"hash",passwords.encode(next)));});
        req.setAttribute("auditAction","PASSWORD_CHANGE");req.getSession().invalidate();return Api.ok(req,Map.of());
    }
}
