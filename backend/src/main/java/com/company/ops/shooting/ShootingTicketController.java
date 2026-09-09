package com.company.ops.shooting;

import com.company.ops.auth.Identity;
import com.company.ops.common.*;
import static com.company.ops.common.Db.p;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shooting-tickets")
public class ShootingTicketController {
    private static final List<String> MARKETS=List.of("MY","UK","US","DE","FR");
    private static final List<Map<String,Object>> TASK_TYPES=List.of(
        p("code","SCRIPT_SHOOT","name","脚本拍摄"),
        p("code","LIBRARY","name","素材库补充"),
        p("code","RESHOOT","name","补拍")
    );
    private final Db db; private final TransactionTemplate tx;
    public ShootingTicketController(Db db,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.tx=new TransactionTemplate(manager);}

    @GetMapping("/context")
    public Object context(HttpServletRequest req){
        var actor=Identity.actor(req);
        return Api.ok(req,p("role",role(actor),"markets",db.rows("select market_code,market_name,currency_code from dim_market where enabled and market_code in ('MY','UK','US','DE','FR') order by case market_code when 'MY' then 1 when 'UK' then 2 when 'US' then 3 when 'DE' then 4 else 5 end"),"taskTypes",TASK_TYPES));
    }

    @GetMapping
    public Object list(HttpServletRequest req){
        var actor=Identity.actor(req);var args=p();String where="1=1";
        if(Identity.role(actor,"DIRECTOR")&&!Identity.role(actor,"ADMIN")){where="t.created_by=#{p.user}";args.put("user",Identity.uid(req));}
        else if(!Identity.role(actor,"SHOOTER")&&!Identity.role(actor,"DEPT_HEAD")&&!Identity.role(actor,"ADMIN"))throw forbidden();
        return Api.ok(req,db.rows(select()+" where "+where+" order by t.created_at desc",args));
    }

    @PostMapping
    public Object create(@RequestBody Map<String,Object> body,HttpServletRequest req){
        requireRole(Identity.actor(req),"DIRECTOR");
        String market=market(Api.text(body,"marketCode",16,true));
        String type=taskType(Api.text(body,"taskType",32,true));
        String requirement=Api.text(body,"shotRequirement",4000,true);
        long planned=number(body,"plannedValidShotCount",true);
        String deadline=deadline(Api.text(body,"deadline",64,true));
        long id=Long.parseLong(tx.execute(s->db.insert("insert into shooting_ticket(created_by,market_code,task_type,shot_requirement,planned_valid_shot_count,deadline) values(#{p.user},#{p.market},#{p.type},#{p.requirement},#{p.planned},cast(#{p.deadline} as timestamptz))",p("user",Identity.uid(req),"market",market,"type",type,"requirement",requirement,"planned",planned,"deadline",deadline))));
        var result=db.one(select()+" where t.id=#{p.id}",p("id",id));
        req.setAttribute("auditAction","SHOOTING_TICKET_CREATE");req.setAttribute("targetId",id);req.setAttribute("auditAfter",result);return Api.ok(req,result);
    }

    @PostMapping("/{id}/complete")
    public Object complete(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){
        requireRole(Identity.actor(req),"SHOOTER");String sku=Api.text(body,"sku",500,true);long actual=number(body,"actualValidShotCount",true);String notes=Api.text(body,"materialNotes",4000,false);
        var result=tx.execute(s->{var old=db.one("select id,status from shooting_ticket where id=#{p.id} for update",p("id",id));Api.require(!old.isEmpty(),"拍摄工单不存在");Api.require("PENDING_SHOOT".equals(old.get("status")),"当前工单不在待拍摄状态");db.exec("update shooting_ticket set shooter_id=#{p.user},sku=#{p.sku},actual_valid_shot_count=#{p.actual},material_notes=#{p.notes},actual_delivered_at=now(),status='PENDING_EDITOR_REVIEW',rejection_stage=null,rejection_reason='',updated_at=now() where id=#{p.id}",p("user",Identity.uid(req),"sku",sku,"actual",actual,"notes",notes,"id",id));return db.one(select()+" where t.id=#{p.id}",p("id",id));});
        req.setAttribute("auditAction","SHOOTING_TICKET_COMPLETE");req.setAttribute("targetId",id);req.setAttribute("auditAfter",result);return Api.ok(req,result);
    }

    @PostMapping("/{id}/editor-review")
    public Object editorReview(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){return review(id,body,req,"EDITOR");}
    @PostMapping("/{id}/dept-review")
    public Object deptReview(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){return review(id,body,req,"DEPT");}

    private Object review(long id,Map<String,Object> body,HttpServletRequest req,String stage){
        var actor=Identity.actor(req);boolean admin=Identity.role(actor,"ADMIN");if("EDITOR".equals(stage)){requireRole(actor,"DIRECTOR");}else requireRole(actor,"DEPT_HEAD");
        boolean approved=Boolean.TRUE.equals(body.get("approved"))||"true".equalsIgnoreCase(Objects.toString(body.get("approved"),""));String reason=Api.text(body,"reason",4000,!approved);
        var result=tx.execute(s->{var old=db.one("select id,created_by,status from shooting_ticket where id=#{p.id} for update",p("id",id));Api.require(!old.isEmpty(),"拍摄工单不存在");Api.require(("EDITOR".equals(stage)?"PENDING_EDITOR_REVIEW":"PENDING_DEPT_REVIEW").equals(old.get("status")),"当前工单不在审核状态");if("EDITOR".equals(stage)&&!admin&&!Objects.toString(old.get("createdBy"),"").equals(Long.toString(Identity.uid(req))))throw forbidden();String next=approved?("EDITOR".equals(stage)?"PENDING_DEPT_REVIEW":"APPROVED"):"PENDING_SHOOT";String update="update shooting_ticket set status=#{p.status},rejection_stage=#{p.stage},rejection_reason=#{p.reason},editor_reviewer_id=case when #{p.editor} then #{p.user} else editor_reviewer_id end,editor_reviewed_at=case when #{p.editor} then now() else editor_reviewed_at end,dept_reviewer_id=case when #{p.dept} then #{p.user} else dept_reviewer_id end,dept_reviewed_at=case when #{p.dept} then now() else dept_reviewed_at end,updated_at=now() where id=#{p.id}";db.exec(update,p("status",next,"stage",approved?null:stage,"reason",approved?"":reason,"editor","EDITOR".equals(stage),"dept","DEPT".equals(stage),"user",Identity.uid(req),"id",id));return db.one(select()+" where t.id=#{p.id}",p("id",id));});
        req.setAttribute("auditAction",approved?"SHOOTING_TICKET_APPROVE":"SHOOTING_TICKET_REJECT");req.setAttribute("targetId",id);req.setAttribute("auditAfter",result);return Api.ok(req,result);
    }

    private static String select(){return "select t.*,m.market_name region_name,creator.display_name director_name,shooter.display_name shooter_name,editor.display_name editor_reviewer_name,dept.display_name dept_reviewer_name from shooting_ticket t join dim_market m on m.market_code=t.market_code join sys_user creator on creator.id=t.created_by left join sys_user shooter on shooter.id=t.shooter_id left join sys_user editor on editor.id=t.editor_reviewer_id left join sys_user dept on dept.id=t.dept_reviewer_id";}
    static String role(Map<String,Object> actor){if(Identity.role(actor,"ADMIN"))return "ADMIN";if(Identity.role(actor,"DIRECTOR"))return "DIRECTOR";if(Identity.role(actor,"SHOOTER"))return "SHOOTER";if(Identity.role(actor,"DEPT_HEAD"))return "DEPT_HEAD";return "VIEWER";}
    static String market(String value){String code=Objects.toString(value,"").toUpperCase(Locale.ROOT);Api.require(MARKETS.contains(code),"地区无效");return code;}
    static String taskType(String value){Api.require(TASK_TYPES.stream().anyMatch(t->t.get("code").equals(value)),"任务类型无效");return value;}
    static String deadline(String value){try{Instant.parse(value);return value;}catch(Exception e){throw new Api.Problem(400,"VALIDATION_ERROR","截止时间格式无效");}}
    static long number(Map<String,Object> body,String key,boolean required){Object raw=body.get(key);if(raw==null||raw.toString().isBlank()){Api.require(!required,key+" 不能为空");return 0;}try{long result=Long.parseLong(raw.toString());Api.require(result>=0,key+" 不能小于 0");return result;}catch(NumberFormatException e){throw new Api.Problem(400,"VALIDATION_ERROR",key+" 必须是非负整数");}}
    private static Api.Problem forbidden(){return new Api.Problem(403,"AUTH_FORBIDDEN","没有此操作的权限");}
    private static void requireRole(Map<String,Object> actor,String role){if(!Identity.role(actor,role)&&!Identity.role(actor,"ADMIN"))throw forbidden();}
}
