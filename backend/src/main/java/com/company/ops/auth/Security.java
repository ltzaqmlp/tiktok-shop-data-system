package com.company.ops.auth;

import com.company.ops.common.*;
import static com.company.ops.common.Db.p;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class Security {
    @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder(12);}
    @Bean SecurityFilterChain chain(HttpSecurity http, Gate gate)throws Exception{
        return http.csrf(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .authorizeHttpRequests(c->c.anyRequest().permitAll()).addFilterBefore(gate,UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean Gate gate(Identity identity,Db db,ObjectMapper json){return new Gate(identity,db,json);}
    @Bean FilterRegistrationBean<Gate> noDoubleFilter(Gate gate){var bean=new FilterRegistrationBean<>(gate);bean.setEnabled(false);return bean;}
    public static class Gate extends OncePerRequestFilter {
        private final Identity identity;private final Db db;private final ObjectMapper json;
        Gate(Identity identity,Db db,ObjectMapper json){this.identity=identity;this.db=db;this.json=json;}
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
            req.setAttribute("requestId",UUID.randomUUID().toString());res.setHeader("X-Request-ID",Api.id(req));res.setHeader("Cache-Control","no-store");
            String path=req.getRequestURI();boolean write=!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod());long start=System.currentTimeMillis();
            try{
                if("/api/v1/auth/session".equals(path))restoreRemember(req,res);
                if(write){
                    if(!"XMLHttpRequest".equals(req.getHeader("X-Requested-With")))throw new Api.Problem(403,"CSRF_REJECTED","请求来源校验失败");
                    String origin=req.getHeader("Origin");
                    if(origin!=null){URI uri=URI.create(origin);if(!Objects.equals(uri.getAuthority(),req.getHeader("Host"))||!Objects.equals(uri.getScheme(),req.getScheme()))throw new Api.Problem(403,"CSRF_REJECTED","不允许跨站请求");}
                }
                if(!Set.of("/api/v1/auth/login","/api/v1/auth/session","/api/v1/system/health","/error").contains(path)){
                    var session=req.getSession(false);if(session==null||session.getAttribute("uid")==null)session=restoreRemember(req,res);
                    if(session==null||session.getAttribute("uid")==null)throw new Api.Problem(401,"AUTH_REQUIRED","请先登录");
                    var user=identity.user((long)session.getAttribute("uid"));
                    if(user.isEmpty()||!"ACTIVE".equals(user.get("status"))||!Objects.equals(session.getAttribute("version"),user.get("sessionVersion"))){session.invalidate();throw new Api.Problem(401,"AUTH_REQUIRED","会话已失效，请重新登录");}
                    req.setAttribute("actor",user);
                    if(Boolean.TRUE.equals(user.get("mustChangePassword"))&&!Set.of("/api/v1/auth/me","/api/v1/auth/change-password","/api/v1/auth/logout").contains(path))throw new Api.Problem(403,"AUTH_PASSWORD_CHANGE_REQUIRED","必须先修改初始密码");
                    boolean allowed=true;
                    if(path.startsWith("/api/v1/admin/")||path.endsWith("/reaggregate"))allowed=Identity.role(user,"ADMIN");
                    else if(path.startsWith("/api/v1/dashboard/"))allowed=identity.hasMenu(user,"dashboard.overview");
                    else if(path.startsWith("/api/v1/daily-reports"))allowed=identity.hasMenu(user,"daily.report");
                    else if(path.startsWith("/api/v1/shooting-tickets"))allowed=identity.hasMenu(user,"shooting.ticket");
                    else if(path.startsWith("/api/v1/imports"))allowed=identity.hasMenu(user,"import.upload");
                    else if(path.startsWith("/api/v1/exports/"))allowed=identity.canExport(user);
                    if(!allowed)throw new Api.Problem(403,"AUTH_FORBIDDEN","没有此操作的权限");
                }
                chain.doFilter(req,res);
            }catch(Api.Problem e){res.setStatus(e.status);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),new Api.Envelope(false,Api.id(req),null,Map.of("code",e.code,"message",e.getMessage())));}
            finally{
                if(Identity.actor(req)!=null&&(write||path.startsWith("/api/v1/exports/"))){
                    var parts=path.split("/");String module=parts.length>3?parts[3]:"system";
                    db.exec("insert into sys_audit_log(user_id,module,action,target_type,target_id,request_id,request_summary,before_data,after_data,success,duration_ms,ip) values(#{p.uid},#{p.module},#{p.action},#{p.target},#{p.targetId},#{p.requestId},cast(#{p.summary} as jsonb),cast(#{p.before} as jsonb),cast(#{p.after} as jsonb),#{p.success},#{p.duration},#{p.ip})",
                        p("uid",Identity.uid(req),"module",module,"action",Objects.toString(req.getAttribute("auditAction"),req.getMethod()+" "+path),"target",path,"targetId",Objects.toString(req.getAttribute("targetId"),null),"requestId",Api.id(req),"summary",db.json(req.getAttribute("auditSummary")),"before",db.json(req.getAttribute("auditBefore")),"after",db.json(req.getAttribute("auditAfter")),"success",res.getStatus()<400,"duration",(int)(System.currentTimeMillis()-start),"ip",req.getRemoteAddr()));
                }
            }
        }
        private HttpSession restoreRemember(HttpServletRequest req,HttpServletResponse res){
            String[] parts=AuthController.rememberValue(req).split("\\.",2);if(parts.length!=2){return null;}var token=db.one("select u.id,u.session_version from sys_remember_token t join sys_user u on u.id=t.user_id where t.selector=#{p.selector} and t.token_hash=#{p.hash} and t.expires_at>now() and t.session_version=u.session_version and u.status='ACTIVE' and u.deleted_at is null",p("selector",parts[0],"hash",AuthController.tokenHash(parts[1])));if(token.isEmpty()){AuthController.expireRemember(req,res);return null;}var session=req.getSession(true);session.setMaxInactiveInterval(30*24*60*60);session.setAttribute("uid",Long.parseLong(token.get("id").toString()));session.setAttribute("version",Integer.parseInt(token.get("sessionVersion").toString()));return session;
        }
    }
}
