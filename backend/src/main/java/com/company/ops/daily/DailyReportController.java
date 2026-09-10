package com.company.ops.daily;

import com.company.ops.auth.Identity;
import com.company.ops.common.*;
import static com.company.ops.common.Db.p;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Role-based daily reports. The legacy daily_report and daily_report_task tables are
 * intentionally retained by V6 for schema compatibility and are excluded from this API.
 */
@RestController
@RequestMapping("/api/v1/daily-reports")
public class DailyReportController {
    private static final List<String> MARKETS=List.of("MY","UK","US","DE","FR");
    private static final List<String> EDITOR=List.of("plannedNewPublish","actualNewPublish","plannedFirstReview","actualFirstReview","plannedReworkAcceptance","actualReworkAcceptance");
    private static final List<String> LEAD=List.of("plannedReviewVideos","actualReviewVideos","plannedValidBenchmark","actualValidBenchmark","plannedDeconstruction","actualDeconstruction","plannedCompleteScript","actualCompleteScript","plannedReadyScript","actualReadyScript");
    private static final List<String> ADS=List.of("plannedTest","actualTest","newAdjustPlan","adSpend","adGmv","impressions","clicks","orders","expandedMaterial","stoppedMaterial");
    private static final List<String> SIMPLE=List.of();
    private final Db db; private final TransactionTemplate tx;
    public DailyReportController(Db db,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.tx=new TransactionTemplate(manager);}

    @GetMapping("/context")
    public Object context(HttpServletRequest req){
        var actor=Identity.actor(req);var data=p("role",dailyRole(actor),"marketCode",Objects.toString(actor.get("marketCode"),""),"markets",db.rows("select market_code,market_name,currency_code from dim_market where market_code in ('MY','UK','US','DE','FR') and enabled order by case market_code when 'MY' then 1 when 'UK' then 2 when 'US' then 3 when 'DE' then 4 else 5 end"));
        return Api.ok(req,data);
    }

    @GetMapping("/summary")
    public Object summary(@RequestParam String date,HttpServletRequest req){
        requireViewer(Identity.actor(req));var args=p("date",day(date));
        String sql="select m.market_code,m.market_name,"+
            "coalesce(sum(r.planned_review_videos) filter(where r.report_type='DIRECTOR'),0) planned_review_videos,coalesce(sum(r.actual_review_videos) filter(where r.report_type='DIRECTOR'),0) actual_review_videos,"+
            "coalesce(sum(r.planned_valid_benchmark) filter(where r.report_type='DIRECTOR'),0) planned_valid_benchmark,coalesce(sum(r.actual_valid_benchmark) filter(where r.report_type='DIRECTOR'),0) actual_valid_benchmark,"+
            "coalesce(sum(r.planned_deconstruction) filter(where r.report_type='DIRECTOR'),0) planned_deconstruction,coalesce(sum(r.actual_deconstruction) filter(where r.report_type='DIRECTOR'),0) actual_deconstruction,"+
            "coalesce(sum(r.planned_complete_script) filter(where r.report_type='DIRECTOR'),0) planned_complete_script,coalesce(sum(r.actual_complete_script) filter(where r.report_type='DIRECTOR'),0) actual_complete_script,"+
            "coalesce(sum(r.planned_ready_script) filter(where r.report_type='DIRECTOR'),0) planned_ready_script,coalesce(sum(r.actual_ready_script) filter(where r.report_type='DIRECTOR'),0) actual_ready_script,"+
            "coalesce(sum(r.planned_new_publish) filter(where r.report_type='EDITOR'),0) planned_new_publish,coalesce(sum(r.actual_new_publish) filter(where r.report_type='EDITOR'),0) actual_new_publish,"+
            "coalesce(sum(r.planned_first_review) filter(where r.report_type='EDITOR'),0) planned_first_review,coalesce(sum(r.actual_first_review) filter(where r.report_type='EDITOR'),0) actual_first_review,"+
            "coalesce(sum(r.planned_rework_acceptance) filter(where r.report_type='EDITOR'),0) planned_rework_acceptance,coalesce(sum(r.actual_rework_acceptance) filter(where r.report_type='EDITOR'),0) actual_rework_acceptance " +
            "from dim_market m left join daily_metric_report r on r.market_code=m.market_code and r.report_date=#{p.date} and r.submission_status='APPROVED' where m.market_code in ('MY','UK','US','DE','FR') group by m.market_code,m.market_name order by case m.market_code when 'MY' then 1 when 'UK' then 2 when 'US' then 3 when 'DE' then 4 else 5 end";
        return Api.ok(req,db.rows(sql,args));
    }

    @GetMapping("/summary/review")
    public Object summaryReview(@RequestParam String date,HttpServletRequest req){
        requireViewer(Identity.actor(req));
        var result=db.one(summarySelect()+" where s.report_date=#{p.date}",p("date",day(date)));
        if(Identity.role(Identity.actor(req),"BOSS")&&!result.isEmpty()&&!"APPROVED".equals(result.get("submissionStatus")))result=summaryBlank(day(date));
        return Api.ok(req,result.isEmpty()?summaryBlank(day(date)):result);
    }

    @PutMapping("/summary/{date}")
    public Object saveSummary(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){
        requireRole(Identity.actor(req),"DEPT_HEAD");
        LocalDate reportDate=day(date); Map<String,Object> result=tx.execute(s->{
            var values=p("date",reportDate,"today",Api.text(body,"todayImportantResult",4000,false),"support",Api.text(body,"needBossSupport",4000,false),"tomorrow",Api.text(body,"tomorrowFocus",4000,false));
            var old=db.one("select id from daily_report_summary where report_date=#{p.date} for update",p("date",reportDate));
            if(old.isEmpty()) db.insert(summaryInsertSql(),values);
            else db.exec("update daily_report_summary set today_important_result=#{p.today},need_boss_support=#{p.support},tomorrow_focus=#{p.tomorrow},submission_status=case when submission_status='APPROVED' then 'APPROVED' else 'DRAFT' end,rejection_reason='',updated_at=now() where id=#{p.id}",p("today",values.get("today"),"support",values.get("support"),"tomorrow",values.get("tomorrow"),"id",old.get("id")));
            return db.one(summarySelect()+" where s.report_date=#{p.date}",p("date",reportDate));
        });
        req.setAttribute("auditAction","DAILY_REPORT_SUMMARY_SAVE");req.setAttribute("targetId",result.get("id"));req.setAttribute("auditAfter",result);return Api.ok(req,result);
    }

    @PostMapping("/summary/{date}/approve")
    public Object approveSummary(@PathVariable String date,HttpServletRequest req){return reviewSummary(day(date),null,req);}

    @PostMapping("/summary/{date}/reject")
    public Object rejectSummary(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){return reviewSummary(day(date),Api.text(body,"reason",2000,true),req);}

    @GetMapping("/summary/details")
    public Object summaryDetails(@RequestParam String date,HttpServletRequest req){
        requireViewer(Identity.actor(req));
        return Api.ok(req,rows("r.report_date=#{p.date} and r.report_type in ('EDITOR','DIRECTOR') and r.submission_status='APPROVED'",p("date",day(date))));
    }

    @GetMapping("/market/{market}")
    public Object market(@PathVariable String market,@RequestParam String date,HttpServletRequest req){
        market=market(market);var actor=Identity.actor(req);var args=p("date",day(date),"market",market,"user",Identity.uid(req));String where="r.report_date=#{p.date} and r.market_code=#{p.market} and r.report_type in ('EDITOR','DIRECTOR')";
        if(Identity.role(actor,"BOSS"))where+=" and r.submission_status='APPROVED'";
        else if(Identity.role(actor,"DEPT_HEAD")||Identity.role(actor,"ADMIN"))where+=" and r.submission_status<>'DRAFT'";
        else if(marketReviewer(actor,market))where+=" and (r.report_type='EDITOR' or r.reporter_id=#{p.user}) and r.submission_status<>'DRAFT'";
        else if(Identity.role(actor,"DIRECTOR")&&market.equals(actor.get("marketCode")))where+=" and r.reporter_id=#{p.user}";
        else if(Identity.role(actor,"MARKET_MEMBER")&&market.equals(actor.get("marketCode")))where+=" and r.reporter_id=#{p.user}";
        else throw forbidden();
        return Api.ok(req,rows(where,args));
    }

    @GetMapping("/ads")
    public Object ads(@RequestParam String date,HttpServletRequest req){
        var actor=Identity.actor(req);var args=p("date",day(date),"user",Identity.uid(req));String where="r.report_date=#{p.date} and r.report_type='ADS_BUYER'";
        if(Identity.role(actor,"BOSS")||Identity.role(actor,"ADS_BUYER")){}
        else if(Identity.role(actor,"DEPT_HEAD")||Identity.role(actor,"ADMIN"))where+=" and r.submission_status<>'DRAFT'";
        else throw forbidden();
        return Api.ok(req,rows(where,args));
    }

    @GetMapping("/simple/{type}")
    public Object simple(@PathVariable String type,@RequestParam String date,HttpServletRequest req){
        type=simpleType(type);var actor=Identity.actor(req);var args=p("date",day(date),"user",Identity.uid(req),"type",type);
        String where="r.report_date=#{p.date} and r.report_type=#{p.type}";
        if(Identity.role(actor,"BOSS"))where+=" and r.submission_status='APPROVED'";
        else if(Identity.role(actor,"DEPT_HEAD")||Identity.role(actor,"ADMIN")){where+=" and r.submission_status<>'DRAFT'";}
        else if(simpleRole(actor).equals(type))where+=" and r.reporter_id=#{p.user}";
        else throw forbidden();
        return Api.ok(req,rows(where,args));
    }

    @GetMapping("/mine/content")
    public Object mineContent(@RequestParam String date,HttpServletRequest req){
        String type=contentRole(Identity.actor(req));var args=p("date",day(date),"user",Identity.uid(req),"type",type);
        var report=db.one(select()+" where r.report_date=#{p.date} and r.reporter_id=#{p.user} and r.report_type=#{p.type}",args);
        if(report.isEmpty())report.putAll(blank(day(date),type,ownMarket(Identity.actor(req))));return Api.ok(req,report);
    }
    @PutMapping("/mine/content/{date}") public Object saveContent(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){return storeContent(date,body,req,false);}
    @PostMapping("/mine/content/{date}/submit") public Object submitContent(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){return storeContent(date,body,req,true);}

    @GetMapping("/mine/ads")
    public Object mineAds(@RequestParam String date,HttpServletRequest req){
        requireRole(Identity.actor(req),"ADS_BUYER");return Api.ok(req,rows("r.report_date=#{p.date} and r.reporter_id=#{p.user} and r.report_type='ADS_BUYER'",p("date",day(date),"user",Identity.uid(req))));
    }
    @PutMapping("/mine/ads/{date}/{market}") public Object saveAds(@PathVariable String date,@PathVariable String market,@RequestBody Map<String,Object> body,HttpServletRequest req){return storeAds(date,market,body,req,false);}
    @PostMapping("/mine/ads/{date}/{market}/submit") public Object submitAds(@PathVariable String date,@PathVariable String market,@RequestBody Map<String,Object> body,HttpServletRequest req){return storeAds(date,market,body,req,true);}

    @GetMapping("/mine/simple")
    public Object mineSimple(@RequestParam String date,HttpServletRequest req){
        var actor=Identity.actor(req);String type=simpleRole(actor);var report=db.one(select()+" where r.report_date=#{p.date} and r.reporter_id=#{p.user} and r.report_type=#{p.type}",p("date",day(date),"user",Identity.uid(req),"type",type));
        if(report.isEmpty())report.putAll(blank(day(date),type,"MY"));return Api.ok(req,report);
    }
    @PutMapping("/mine/simple/{date}") public Object saveSimple(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){return storeSimple(date,body,req,false);}
    @PostMapping("/mine/simple/{date}/submit") public Object submitSimple(@PathVariable String date,@RequestBody Map<String,Object> body,HttpServletRequest req){return storeSimple(date,body,req,true);}

    @PostMapping("/{id}/approve") public Object approve(@PathVariable long id,HttpServletRequest req){return review(id,null,req);}
    @PostMapping("/{id}/reject") public Object reject(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){return review(id,Api.text(body,"reason",2000,true),req);}

    private Object storeContent(String value,Map<String,Object> body,HttpServletRequest req,boolean submit){var actor=Identity.actor(req);return store(day(value),contentRole(actor),ownMarket(actor),body,req,submit);}
    private Object storeAds(String value,String valueMarket,Map<String,Object> body,HttpServletRequest req,boolean submit){requireRole(Identity.actor(req),"ADS_BUYER");return store(day(value),"ADS_BUYER",market(valueMarket),body,req,submit);}
    private Object storeSimple(String value,Map<String,Object> body,HttpServletRequest req,boolean submit){var actor=Identity.actor(req);return store(day(value),simpleRole(actor),"MY",body,req,submit);}
    private Object store(LocalDate date,String type,String market,Map<String,Object> body,HttpServletRequest req,boolean submit){
        long user=Identity.uid(req);Map<String,Object> result=tx.execute(s->{
            if("ADS_BUYER".equals(type)){
                String lockKey=date+"|"+market;
                db.rows("select pg_advisory_xact_lock(hashtextextended(#{p.lockKey},0))",p("lockKey",lockKey));
                var occupied=db.one("select u.display_name reporter_name from daily_metric_report r join sys_user u on u.id=r.reporter_id where r.report_date=#{p.date} and r.market_code=#{p.market} and r.report_type='ADS_BUYER' and r.reporter_id<>#{p.user} limit 1",p("date",date,"market",market,"user",user));
                if(!occupied.isEmpty())throw new Api.Problem(409,"DAILY_ADS_MARKET_TAKEN","该国家当天已由"+occupied.get("reporterName")+"填报，不能重复填写");
            }
            var old=db.one("select id,submission_status from daily_metric_report where report_date=#{p.date} and reporter_id=#{p.user} and report_type=#{p.type} and market_code=#{p.market} for update",p("date",date,"user",user,"type",type,"market",market));
            Api.require(old.isEmpty()||!"APPROVED".equals(old.get("submissionStatus")),"已审核通过的日报不能修改，请由审核人退回后再填写");
            var values=metrics(body,type,submit);values.put("deliveryResults",db.json(deliveryResults(body,type)));values.put("submitted",submit);values.put("date",date);values.put("user",user);values.put("type",type);values.put("market",market);values.put("status",submit?("EDITOR".equals(type)?"PENDING_MARKET":"PENDING_DEPT"):(old.isEmpty()?"DRAFT":old.get("submissionStatus").toString()));
            long id;if(old.isEmpty())id=Long.parseLong(db.insert(insertSql(),values));else{id=Long.parseLong(old.get("id").toString());values.put("id",id);db.exec(updateSql(),values);}return db.one(select()+" where r.id=#{p.id}",p("id",id));
        });
        req.setAttribute("auditAction",submit?"DAILY_REPORT_SUBMIT":"DAILY_REPORT_SAVE");req.setAttribute("targetId",result.get("id"));req.setAttribute("auditAfter",result);return Api.ok(req,result);
    }

    private Object review(long id,String reason,HttpServletRequest req){
        var actor=Identity.actor(req);Map<String,Object> result=tx.execute(s->{
            var report=db.one("select * from daily_metric_report where id=#{p.id} for update",p("id",id));Api.require(!report.isEmpty(),"日报不存在");String state=report.get("submissionStatus").toString();String type=report.get("reportType").toString();String reportMarket=report.get("marketCode").toString();long user=Identity.uid(req);boolean marketReview="PENDING_MARKET".equals(state)&&"EDITOR".equals(type)&&(marketReviewer(actor,reportMarket)||Identity.role(actor,"ADMIN"));boolean deptReview="PENDING_DEPT".equals(state)&&(Identity.role(actor,"DEPT_HEAD")||Identity.role(actor,"ADMIN"));if(!marketReview&&!deptReview)throw forbidden();
            String next=reason==null?(marketReview?"PENDING_DEPT":"APPROVED"):"REJECTED";String stage=marketReview?"MARKET":"DEPT";
            db.exec("update daily_metric_report set submission_status=#{p.status},rejection_stage=#{p.stage},rejection_reason=#{p.reason},market_reviewer_id=case when #{p.market} then #{p.user} else market_reviewer_id end,market_reviewed_at=case when #{p.market} then now() else market_reviewed_at end,dept_reviewer_id=case when #{p.dept} then #{p.user} else dept_reviewer_id end,dept_reviewed_at=case when #{p.dept} then now() else dept_reviewed_at end,updated_at=now() where id=#{p.id}",p("status",next,"stage",reason==null?null:stage,"reason",Objects.toString(reason,""),"market",marketReview,"dept",deptReview,"user",user,"id",id));return db.one(select()+" where r.id=#{p.id}",p("id",id));
        });
        req.setAttribute("auditAction",reason==null?"DAILY_REPORT_APPROVE":"DAILY_REPORT_REJECT");req.setAttribute("targetId",id);req.setAttribute("auditAfter",result);return Api.ok(req,result);
    }

    private Object reviewSummary(LocalDate date,String reason,HttpServletRequest req){
        requireRole(Identity.actor(req),"DEPT_HEAD");
        Map<String,Object> result=tx.execute(s->{
            var summary=db.one("select id from daily_report_summary where report_date=#{p.date} for update",p("date",date));
            Api.require(!summary.isEmpty(),"请先保存汇总日报");
            String status=reason==null?"APPROVED":"REJECTED";
            db.exec("update daily_report_summary set submission_status=#{p.status},rejection_reason=#{p.reason},reviewer_id=#{p.user},reviewed_at=now(),updated_at=now() where id=#{p.id}",p("status",status,"reason",Objects.toString(reason,""),"user",Identity.uid(req),"id",Long.parseLong(summary.get("id").toString())));
            if(reason!=null) db.exec("update daily_metric_report set submission_status='REJECTED',rejection_stage='DEPT',rejection_reason=#{p.reason},updated_at=now() where report_date=#{p.date} and report_type in ('EDITOR','DIRECTOR') and submission_status='APPROVED'",p("date",date,"reason",reason));
            return db.one(summarySelect()+" where s.report_date=#{p.date}",p("date",date));
        });
        req.setAttribute("auditAction",reason==null?"DAILY_REPORT_SUMMARY_APPROVE":"DAILY_REPORT_SUMMARY_REJECT");req.setAttribute("targetId",result.get("id"));req.setAttribute("auditAfter",result);return Api.ok(req,result);
    }

    private List<Map<String,Object>> rows(String where,Map<String,Object> args){return db.rows(select()+" where "+where+" order by r.market_code,u.display_name,r.id",args);}
    private static String select(){return "select r.*,u.display_name reporter_name,m.market_name,greatest(r.planned_test-r.actual_test,0) test_gap,case when r.ad_spend=0 then 0 else round(r.ad_gmv/r.ad_spend,4) end roi,case when r.impressions=0 then 0 else round(r.clicks::numeric/r.impressions,4) end ctr from daily_metric_report r join sys_user u on u.id=r.reporter_id join dim_market m on m.market_code=r.market_code";}
    private static Map<String,Object> blank(LocalDate date,String type,String market){var value=p("reportDate",date.toString(),"reportType",type,"marketCode",market,"submissionStatus","DRAFT","rejectionReason","","deliveryResults",new LinkedHashMap<>(),"submittedAt",null);for(String key:EDITOR)value.put(key,0);for(String key:LEAD)value.put(key,0);for(String key:ADS)value.put(key,key.equals("adSpend")||key.equals("adGmv")?BigDecimal.ZERO:0);value.put("testGap",0);value.put("roi",BigDecimal.ZERO);value.put("ctr",BigDecimal.ZERO);return value;}
    static Map<String,String> deliveryResults(Map<String,Object> body,String type){
        Object raw=body.get("deliveryResults");if(raw==null)return new LinkedHashMap<>();Api.require(raw instanceof Map,"交付成果格式无效");var source=(Map<?,?>)raw;var result=new LinkedHashMap<String,String>();List<String> fields="EDITOR".equals(type)?EDITOR:"DIRECTOR".equals(type)?LEAD:ADS;for(String key:fields){Object value=source.get(key);if(value==null)continue;String text=value.toString().trim();Api.require(text.length()<=2000,key+" 交付成果不能超过 2000 个字符");if(!text.isBlank())result.put(key,text);}return result;
    }
    static Map<String,Object> metrics(Map<String,Object> body,String type,boolean required){
        var values=p();for(String key:EDITOR)values.put(camelToSnake(key),0);for(String key:LEAD)values.put(camelToSnake(key),0);for(String key:ADS)values.put(camelToSnake(key),key.equals("adSpend")||key.equals("adGmv")?BigDecimal.ZERO:0);
        List<String> fields="EDITOR".equals(type)?EDITOR:"DIRECTOR".equals(type)?LEAD:"ADS_BUYER".equals(type)?ADS:SIMPLE;
        for(String key:fields)values.put(camelToSnake(key),(key.equals("adSpend")||key.equals("adGmv"))?money(body,key,required):number(body,key,required));values.put("notes",Api.text(body,"notes",4000,required&&Set.of("EDITOR","DIRECTOR","OPS","TECH").contains(type)));return values;
    }
    private static long number(Map<String,Object> body,String key,boolean required){Object raw=body.get(key);if(raw==null||raw.toString().isBlank()){Api.require(!required,key+" 不能为空");return 0;}try{long result=Long.parseLong(raw.toString());Api.require(result>=0,key+" 不能小于 0");return result;}catch(NumberFormatException e){throw new Api.Problem(400,"VALIDATION_ERROR",key+" 必须是非负整数");}}
    private static BigDecimal money(Map<String,Object> body,String key,boolean required){Object raw=body.get(key);if(raw==null||raw.toString().isBlank()){Api.require(!required,key+" 不能为空");return BigDecimal.ZERO;}try{var result=new BigDecimal(raw.toString());Api.require(result.signum()>=0&&result.scale()<=2,key+" 必须是最多两位小数的非负数");return result;}catch(NumberFormatException e){throw new Api.Problem(400,"VALIDATION_ERROR",key+" 格式无效");}}
    private static String insertSql(){return "insert into daily_metric_report(report_date,reporter_id,report_type,market_code,submission_status,delivery_results,submitted_at,notes,planned_review_videos,actual_review_videos,planned_valid_benchmark,actual_valid_benchmark,planned_deconstruction,actual_deconstruction,planned_complete_script,actual_complete_script,planned_ready_script,actual_ready_script,planned_new_publish,actual_new_publish,planned_first_review,actual_first_review,planned_rework_acceptance,actual_rework_acceptance,planned_test,actual_test,new_adjust_plan,ad_spend,ad_gmv,impressions,clicks,orders,expanded_material,stopped_material) values(#{p.date},#{p.user},#{p.type},#{p.market},#{p.status},cast(#{p.deliveryResults} as jsonb),case when #{p.submitted} then now() else null end,#{p.notes},#{p.planned_review_videos},#{p.actual_review_videos},#{p.planned_valid_benchmark},#{p.actual_valid_benchmark},#{p.planned_deconstruction},#{p.actual_deconstruction},#{p.planned_complete_script},#{p.actual_complete_script},#{p.planned_ready_script},#{p.actual_ready_script},#{p.planned_new_publish},#{p.actual_new_publish},#{p.planned_first_review},#{p.actual_first_review},#{p.planned_rework_acceptance},#{p.actual_rework_acceptance},#{p.planned_test},#{p.actual_test},#{p.new_adjust_plan},#{p.ad_spend},#{p.ad_gmv},#{p.impressions},#{p.clicks},#{p.orders},#{p.expanded_material},#{p.stopped_material})";}
    private static String updateSql(){return "update daily_metric_report set submission_status=#{p.status},rejection_stage=null,rejection_reason='',delivery_results=cast(#{p.deliveryResults} as jsonb),submitted_at=case when #{p.submitted} then now() else submitted_at end,notes=#{p.notes},planned_review_videos=#{p.planned_review_videos},actual_review_videos=#{p.actual_review_videos},planned_valid_benchmark=#{p.planned_valid_benchmark},actual_valid_benchmark=#{p.actual_valid_benchmark},planned_deconstruction=#{p.planned_deconstruction},actual_deconstruction=#{p.actual_deconstruction},planned_complete_script=#{p.planned_complete_script},actual_complete_script=#{p.actual_complete_script},planned_ready_script=#{p.planned_ready_script},actual_ready_script=#{p.actual_ready_script},planned_new_publish=#{p.planned_new_publish},actual_new_publish=#{p.actual_new_publish},planned_first_review=#{p.planned_first_review},actual_first_review=#{p.actual_first_review},planned_rework_acceptance=#{p.planned_rework_acceptance},actual_rework_acceptance=#{p.actual_rework_acceptance},planned_test=#{p.planned_test},actual_test=#{p.actual_test},new_adjust_plan=#{p.new_adjust_plan},ad_spend=#{p.ad_spend},ad_gmv=#{p.ad_gmv},impressions=#{p.impressions},clicks=#{p.clicks},orders=#{p.orders},expanded_material=#{p.expanded_material},stopped_material=#{p.stopped_material},updated_at=now() where id=#{p.id}";}
    private static String summarySelect(){return "select s.*,u.display_name reviewer_name from daily_report_summary s left join sys_user u on u.id=s.reviewer_id";}
    private static String summaryInsertSql(){return "insert into daily_report_summary(report_date,today_important_result,need_boss_support,tomorrow_focus) values(#{p.date},#{p.today},#{p.support},#{p.tomorrow})";}
    private static Map<String,Object> summaryBlank(LocalDate date){return p("reportDate",date.toString(),"todayImportantResult","","needBossSupport","","tomorrowFocus","","submissionStatus","DRAFT","rejectionReason","");}
    static LocalDate day(String value){try{return LocalDate.parse(value);}catch(Exception e){throw new Api.Problem(400,"VALIDATION_ERROR","日期格式无效");}}
    static boolean leader(Map<String,Object> user){return Identity.role(user,"ADMIN")||Identity.role(user,"BOSS")||Identity.role(user,"DEPT_HEAD");}
    private static String camelToSnake(String value){return value.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
    private static String market(String value){String code=Objects.toString(value,"").toUpperCase(Locale.ROOT);Api.require(MARKETS.contains(code),"市场无效");return code;}
    private static Api.Problem forbidden(){return new Api.Problem(403,"AUTH_FORBIDDEN","没有此操作的权限");}
    private static void requireRole(Map<String,Object> actor,String role){if(!Identity.role(actor,role)&&!Identity.role(actor,"ADMIN"))throw forbidden();}
    private static void requireViewer(Map<String,Object> actor){if(!leader(actor))throw forbidden();}
    private static String ownMarket(Map<String,Object> actor){String code=Objects.toString(actor.get("marketCode"),"");Api.require(MARKETS.contains(code),"账号尚未配置负责市场");return code;}
    static String dailyRole(Map<String,Object> actor){if(Identity.role(actor,"MARKET_MEMBER"))return "EDITOR";if(Identity.role(actor,"DIRECTOR"))return "DIRECTOR";if(Identity.role(actor,"ADS_BUYER"))return "ADS_BUYER";if(Identity.role(actor,"OPS"))return "OPS";if(Identity.role(actor,"TECH"))return "TECH";return "VIEWER";}
    static boolean marketReviewer(Map<String,Object> actor,String market){return market.equals(actor.get("marketCode"))&&Identity.role(actor,"DIRECTOR");}
    static String contentRole(Map<String,Object> actor){String role=dailyRole(actor);if(!Set.of("EDITOR","DIRECTOR").contains(role))throw forbidden();return role;}
    private static String simpleType(String value){String type=Objects.toString(value,"").toUpperCase(Locale.ROOT);Api.require(Set.of("OPS","TECH").contains(type),"日报类型无效");return type;}
    private static String simpleRole(Map<String,Object> actor){String role=dailyRole(actor);if(!Set.of("OPS","TECH").contains(role))throw forbidden();return role;}
}
