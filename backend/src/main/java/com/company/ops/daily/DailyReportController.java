package com.company.ops.daily;

import com.company.ops.auth.Identity;
import com.company.ops.common.*;
import static com.company.ops.common.Db.p;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/daily-reports")
public class DailyReportController {
    private final Db db; private final TransactionTemplate tx;
    public DailyReportController(Db db,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.tx=new TransactionTemplate(manager);}

    @GetMapping
    public Object list(@RequestParam(required=false)String date,HttpServletRequest req){
        var args=p(); String where="";
        if(date!=null&&!date.isBlank()){args.put("date",day(date));where=" where r.report_date=#{p.date}";}
        if(leader(Identity.actor(req)))where+=(where.isEmpty()?" where ":" and ")+"r.submission_status='SUBMITTED'";
        else {where+=(where.isEmpty()?" where ":" and ")+"r.created_by=#{p.user}";args.put("user",Identity.uid(req));}
        return Api.ok(req,db.rows("select r.report_date,r.group_name,r.role_market,r.today_focus,r.key_result,r.need_boss_support,r.tomorrow_focus,r.submission_status,r.created_by,u.display_name reporter_name,t.id task_id,t.sort_order,t.work_module,t.work_detail,t.plan_delivery,t.actual_result,t.completion_status,t.issue_next_step,t.result_link from daily_report r join sys_user u on u.id=r.created_by join daily_report_task t on t.report_id=r.id"+where+" order by r.report_date desc,u.display_name,t.sort_order",args));
    }

    @GetMapping("/mine")
    public Object mine(@RequestParam String date,HttpServletRequest req){
        LocalDate reportDate=day(date); long user=Identity.uid(req);
        var report=db.one("select r.id,r.report_date,r.group_name,r.role_market,r.today_focus,r.key_result,r.need_boss_support,r.tomorrow_focus,r.submission_status,u.display_name reporter_name from daily_report r join sys_user u on u.id=r.created_by where r.report_date=#{p.date} and r.created_by=#{p.user}",p("date",reportDate,"user",user));
        if(report.isEmpty()){
            var profile=db.one("select u.display_name,coalesce(o.name,'') group_name from sys_user u left join sys_org_unit o on o.id=u.org_unit_id where u.id=#{p.user}",p("user",user));
            report=p("reportDate",reportDate.toString(),"reporterName",profile.get("displayName"),"groupName",profile.get("groupName"),"roleMarket","","todayFocus","","keyResult","","needBossSupport","","tomorrowFocus","","submissionStatus","DRAFT","tasks",List.of());
        }else report.put("tasks",db.rows("select id,sort_order,work_module,work_detail,plan_delivery,actual_result,completion_status,issue_next_step,result_link from daily_report_task where report_id=#{p.id} order by sort_order",p("id",Long.parseLong(report.get("id").toString()))));
        return Api.ok(req,report);
    }

    @PutMapping("/mine/{date}")
    public Object save(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){
        return store(date,body,req,false);
    }
    @PostMapping("/mine/{date}/submit")
    public Object submit(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){
        return store(date,body,req,true);
    }
    private Object store(String date,Map<String,Object> body,HttpServletRequest req,boolean submit){
        LocalDate reportDate=day(date); long user=Identity.uid(req); String group=Api.text(body,"groupName",100,submit),roleMarket=Api.text(body,"roleMarket",100,submit),todayFocus=Api.text(body,"todayFocus",2000,false),keyResult=Api.text(body,"keyResult",2000,false),needBossSupport=Api.text(body,"needBossSupport",2000,false),tomorrowFocus=Api.text(body,"tomorrowFocus",2000,false);
        List<Map<String,Object>> tasks=tasks(body.get("tasks"));
        tx.executeWithoutResult(s->{
            var old=db.one("select id,submission_status from daily_report where report_date=#{p.date} and created_by=#{p.user} for update",p("date",reportDate,"user",user));
            long reportId;
            if(old.isEmpty()) reportId=Long.parseLong(db.insert("insert into daily_report(report_date,group_name,role_market,today_focus,key_result,need_boss_support,tomorrow_focus,submission_status,submitted_at,created_by) values(#{p.date},#{p.group},#{p.role},#{p.today},#{p.result},#{p.support},#{p.tomorrow},#{p.status},case when #{p.submit} then now() else null end,#{p.user})",p("date",reportDate,"group",group,"role",roleMarket,"today",todayFocus,"result",keyResult,"support",needBossSupport,"tomorrow",tomorrowFocus,"status",submit?"SUBMITTED":"DRAFT","submit",submit,"user",user)));
            else {reportId=Long.parseLong(old.get("id").toString());db.exec("update daily_report set group_name=#{p.group},role_market=#{p.role},today_focus=#{p.today},key_result=#{p.result},need_boss_support=#{p.support},tomorrow_focus=#{p.tomorrow},submission_status=case when #{p.submit} then 'SUBMITTED' else submission_status end,submitted_at=case when #{p.submit} then now() else submitted_at end,updated_at=now() where id=#{p.id}",p("group",group,"role",roleMarket,"today",todayFocus,"result",keyResult,"support",needBossSupport,"tomorrow",tomorrowFocus,"submit",submit,"id",reportId));db.exec("delete from daily_report_task where report_id=#{p.id}",p("id",reportId));}
            for(int i=0;i<tasks.size();i++){var task=tasks.get(i);db.exec("insert into daily_report_task(report_id,sort_order,work_module,work_detail,plan_delivery,actual_result,completion_status,issue_next_step,result_link) values(#{p.report},#{p.order},#{p.module},#{p.work},#{p.plan},#{p.actual},#{p.status},#{p.issue},#{p.link})",p("report",reportId,"order",i,"module",Api.text(task,"workModule",100,submit),"work",Api.text(task,"workDetail",2000,submit),"plan",Api.text(task,"planDelivery",2000,submit),"actual",Api.text(task,"actualResult",2000,false),"status",status(task),"issue",Api.text(task,"issueNextStep",2000,false),"link",Api.text(task,"resultLink",2000,false)));}
        });
        req.setAttribute("auditAction",submit?"DAILY_REPORT_SUBMIT":"DAILY_REPORT_DRAFT_SAVE");req.setAttribute("targetId",date);req.setAttribute("auditSummary",p("reportDate",date,"taskCount",tasks.size(),"submissionStatus",submit?"SUBMITTED":"DRAFT"));
        return mine(date,req);
    }

    static LocalDate day(String value){try{return LocalDate.parse(value);}catch(Exception e){throw new Api.Problem(400,"VALIDATION_ERROR","日期格式无效");}}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> tasks(Object raw){
        Api.require(raw instanceof List<?>,"tasks 无效"); var list=(List<?>)raw;Api.require(list.size()==1,"日报请一次性填写完整内容");
        var result=new ArrayList<Map<String,Object>>();for(Object task:list){Api.require(task instanceof Map<?,?>,"日报任务无效");result.add((Map<String,Object>)task);}return result;
    }
    static String status(Map<String,Object> task){String value=Api.text(task,"completionStatus",20,true);Api.require(Set.of("未开始","进行中","已完成","已阻塞").contains(value),"完成状态无效");return value;}
    static boolean leader(Map<String,Object> user){return Identity.role(user,"ADMIN")||Identity.role(user,"BOSS");}
}
