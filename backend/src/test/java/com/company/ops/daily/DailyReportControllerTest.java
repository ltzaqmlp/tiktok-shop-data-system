package com.company.ops.daily;

import com.company.ops.common.Api;
import com.company.ops.auth.Identity;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyReportControllerTest {
    @Test void plansMatchAllDirectorMarketsAndSaveToTheSelectedMarket(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);
        var manager=org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        var controller=new DailyReportController(db,manager);
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","1","marketCodes",java.util.List.of("DE","FR","US"),"roles",java.util.List.of(Map.of("roleCode","DIRECTOR"))));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from dim_market m"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("marketCode","DE"),Map.of("marketCode","FR"),Map.of("marketCode","US")));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from sys_user u"),org.mockito.ArgumentMatchers.anyMap())).thenAnswer(call->"US".equals(((Map<?,?>)call.getArgument(1)).get("market"))?java.util.List.of(Map.of("editorId","2","editorName","美国剪辑")):java.util.List.of());
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of());
        var result=(Api.Envelope)controller.editorPlan("2026-09-16",null,req);
        var plans=(java.util.List<?>)result.data();
        assertEquals(1,plans.size());assertEquals("US",((Map<?,?>)plans.getFirst()).get("marketCode"));
        controller.saveEditorPlan("2026-09-16",2L,Map.of("marketCode","US","tasks",java.util.List.of(Map.of("taskName","新增发布","plannedCount",3))),req);
        org.mockito.Mockito.verify(db).exec(org.mockito.ArgumentMatchers.startsWith("insert into editor_daily_task_plan_setting"),org.mockito.ArgumentMatchers.argThat(args->"US".equals(args.get("market"))&&Long.valueOf(2).equals(args.get("editor"))));
        assertThrows(Api.Problem.class,()->controller.saveEditorPlan("2026-09-16",2L,Map.of("marketCode","UK"),req));
        assertThrows(Api.Problem.class,()->controller.saveEditorPlan("2026-09-16",2L,Map.of("marketCode","FR"),req));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from sys_user u"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("editorId","2","editorName","多市场剪辑")));
        assertEquals(3,((java.util.List<?>)((Api.Envelope)controller.editorPlan("2026-09-16",null,req)).data()).size());
    }
    @Test void plansKeepTheLatestConfigurationForFollowingDates(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);
        var controller=new DailyReportController(db,org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","2","marketCodes",java.util.List.of("US"),"roles",java.util.List.of(Map.of("roleCode","MARKET_MEMBER"))));
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.contains("from editor_daily_task_plan_setting"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of("reportDate","2026-09-15"));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from editor_daily_task_plan where"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("taskName","新增发布","plannedCount",3,"sortOrder",0)));

        var result=(Api.Envelope)controller.editorPlan("2026-09-16","US",req);
        assertTrue((Boolean)((Map<?,?>)result.data()).get("configured"));
        assertEquals(3,((Map<?,?>)((java.util.List<?>)((Map<?,?>)result.data()).get("tasks")).getFirst()).get("plannedCount"));
        org.mockito.Mockito.verify(db).rows(org.mockito.ArgumentMatchers.contains("select max(report_date)"),org.mockito.ArgumentMatchers.argThat(args->LocalDate.of(2026,9,16).equals(args.get("date"))));
    }
    @Test void marketReportKeepsApprovedDirectorPlanSnapshotAfterPlanChanges(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);
        var controller=new DailyReportController(db,org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","9","marketCodes",java.util.List.of("MY"),"roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD"))));
        var report=new java.util.LinkedHashMap<String,Object>(Map.of("reporterId","42","reportType","DIRECTOR","marketCode","MY","plannedReviewVideos",5,"actualReviewVideos",3));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from daily_metric_report"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(report));
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.contains("from director_daily_task_plan_setting"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of("reportDate","2026-09-21"));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from director_daily_task_plan where"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("taskCode","REVIEW_VIDEOS","taskName","复盘视频","plannedCount",20L,"sortOrder",0)));

        var result=(Api.Envelope)controller.market("MY","2026-09-21",req);
        var row=(Map<?,?>)((java.util.List<?>)result.data()).getFirst();
        assertEquals(5,row.get("plannedReviewVideos"));
        assertEquals(3,row.get("actualReviewVideos"));
        assertEquals(5,((Map<?,?>)((java.util.List<?>)row.get("directorTasks")).getFirst()).get("plannedCount"));
    }
    @Test void marketReportKeepsApprovedEditorPlanSnapshotAfterPlanChanges(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);
        var controller=new DailyReportController(db,org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","9","marketCodes",java.util.List.of("MY"),"roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD"))));
        var report=new java.util.LinkedHashMap<String,Object>(Map.of("reporterId","42","reportType","EDITOR","marketCode","MY","plannedNewPublish",5,"actualNewPublish",5));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from daily_metric_report"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(report));
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.contains("from editor_daily_task_plan_setting"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of("reportDate","2026-09-21"));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from editor_daily_task_plan where"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("taskName","新增发布","plannedCount",12L,"sortOrder",0)));

        var result=(Api.Envelope)controller.market("MY","2026-09-21",req);
        var row=(Map<?,?>)((java.util.List<?>)result.data()).getFirst();
        assertEquals(5,row.get("plannedNewPublish"));
        assertEquals(5,row.get("actualNewPublish"));
    }
    @Test void marketReportShowsSavedCustomDirectorTasksAfterPlanChanges(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);var controller=new DailyReportController(db,org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","9","marketCodes",java.util.List.of("MY"),"roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD"))));
        var report=new java.util.LinkedHashMap<String,Object>(Map.of("reporterId","42","reportType","DIRECTOR","marketCode","MY","plannedReviewVideos",0,"actualReviewVideos",0,"directorTaskResults",Map.of("old-code",Map.of("taskName","历史自定义任务","plannedCount",5,"sortOrder",0,"actualCount",3,"delivery","交付"))));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from daily_metric_report"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(report));
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.contains("from director_daily_task_plan_setting"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of("reportDate","2026-09-21"));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from director_daily_task_plan where"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("taskCode","new-code","taskName","新配置任务","plannedCount",20L,"sortOrder",0)));
        var result=(Api.Envelope)controller.market("MY","2026-09-21",req);var row=(Map<?,?>)((java.util.List<?>)result.data()).getFirst();var task=(Map<?,?>)((java.util.List<?>)row.get("directorTasks")).getFirst();
        assertEquals("历史自定义任务",task.get("taskName"));assertEquals(5,task.get("plannedCount"));assertEquals(3,task.get("actualCount"));
    }
    @Test void editingPendingReportsRequiresResubmission(){
        assertEquals("DRAFT",DailyReportController.nextContentStatus(false,"EDITOR","PENDING_DEPT"));
        assertEquals("PENDING_MARKET",DailyReportController.nextContentStatus(true,"EDITOR","PENDING_DEPT"));
        assertEquals("DRAFT",DailyReportController.nextContentStatus(false,"DIRECTOR","PENDING_DEPT"));
        assertEquals("PENDING_DEPT",DailyReportController.nextContentStatus(true,"DIRECTOR","DRAFT"));
        assertEquals("APPROVED",DailyReportController.nextContentStatus(false,"EDITOR","APPROVED"));
    }
    @Test void directorPlanCutoffUsesBeijingTimeAndMovesAtSeventeen(){
        var before=java.time.ZonedDateTime.of(2026,9,22,16,59,59,0,java.time.ZoneId.of("Asia/Shanghai"));
        var cutoff=java.time.ZonedDateTime.of(2026,9,22,17,0,0,0,java.time.ZoneId.of("Asia/Shanghai"));
        assertEquals(LocalDate.of(2026,9,22),DailyReportController.directorPlanEffectiveDate(before));
        assertEquals(LocalDate.of(2026,9,23),DailyReportController.directorPlanEffectiveDate(cutoff));
        assertEquals(LocalDate.of(2026,9,23),DailyReportController.directorPlanEffectiveDate(LocalDate.of(2026,9,23),before));
        assertEquals(LocalDate.of(2026,9,22),DailyReportController.directorPlanEffectiveDate(LocalDate.of(2026,9,22),before));
        assertEquals(LocalDate.of(2026,9,23),DailyReportController.directorPlanEffectiveDate(LocalDate.of(2026,9,22),cutoff));
        assertEquals(LocalDate.of(2026,9,23),DailyReportController.directorPlanEffectiveDate(cutoff.withZoneSameInstant(java.time.ZoneId.of("UTC"))));
    }
    @Test void departmentHeadReadsAndSavesTheSelectedFuturePlanDate(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);
        var manager=org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.mockito.Mockito.when(manager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        var controller=new DailyReportController(db,manager);
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","9","roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD"))));
        var tomorrow=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1);
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenAnswer(call->{
            String sql=call.getArgument(0);
            return sql.contains("from sys_user u join sys_user_market um")?java.util.List.of(Map.of("directorId","42","directorName","编导甲","marketCode","MY","marketName","马来西亚")):java.util.List.of();
        });
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of());
        org.mockito.Mockito.when(db.insert(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenReturn("1");

        controller.directorPlan(tomorrow.toString(),null,req);
        org.mockito.Mockito.verify(db).one(org.mockito.ArgumentMatchers.contains("from director_daily_task_plan_setting"),org.mockito.ArgumentMatchers.argThat(args->tomorrow.equals(args.get("date"))));
        controller.saveDirectorPlan(tomorrow.toString(),42L,Map.of("marketCode","MY","tasks",java.util.List.of(Map.of("taskName","明日任务","plannedCount",7))),req);
        org.mockito.Mockito.verify(db).exec(org.mockito.ArgumentMatchers.startsWith("insert into director_daily_task_plan_setting"),org.mockito.ArgumentMatchers.argThat(args->tomorrow.equals(args.get("date"))&&"MY".equals(args.get("market"))));
        org.mockito.Mockito.verify(db).insert(org.mockito.ArgumentMatchers.startsWith("insert into director_daily_task_plan("),org.mockito.ArgumentMatchers.argThat(args->tomorrow.equals(args.get("date"))&&"MY".equals(args.get("market"))));
    }
    @Test void multiMarketDirectorPlanSavesToTheSelectedMarket(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);var manager=org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.mockito.Mockito.when(manager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        var controller=new DailyReportController(db,manager);var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","9","roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD"))));var tomorrow=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1);
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from sys_user u join sys_user_market um"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("directorId","42","directorName","编导甲","marketCode","MY","marketName","马来西亚"),Map.of("directorId","42","directorName","编导甲","marketCode","UK","marketName","英国")));
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of());org.mockito.Mockito.when(db.insert(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenReturn("1");
        controller.saveDirectorPlan(tomorrow.toString(),42L,Map.of("marketCode","UK","tasks",java.util.List.of(Map.of("taskName","英国任务","plannedCount",7))),req);
        org.mockito.Mockito.verify(db).exec(org.mockito.ArgumentMatchers.startsWith("insert into director_daily_task_plan_setting"),org.mockito.ArgumentMatchers.argThat(args->"UK".equals(args.get("market"))));
        org.mockito.Mockito.verify(db).exec(org.mockito.ArgumentMatchers.startsWith("delete from director_daily_task_plan"),org.mockito.ArgumentMatchers.argThat(args->"UK".equals(args.get("market"))));
        org.mockito.Mockito.verify(db).insert(org.mockito.ArgumentMatchers.startsWith("insert into director_daily_task_plan("),org.mockito.ArgumentMatchers.argThat(args->"UK".equals(args.get("market"))));
        assertThrows(Api.Problem.class,()->controller.saveDirectorPlan(tomorrow.toString(),42L,Map.of("marketCode","US","tasks",java.util.List.of()),req));
    }
    @Test void summaryUsesDynamicDirectorTaskNamesAndCurrentPlansWithoutChangingActuals(){
        var db=org.mockito.Mockito.mock(com.company.ops.common.Db.class);
        var controller=new DailyReportController(db,org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        req.setAttribute("actor",Map.of("id","9","marketCodes",java.util.List.of("MY"),"roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD"))));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from dim_market m left join daily_metric_report"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("marketCode","MY","marketName","马来西亚")));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from sys_user u join sys_user_market um"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("directorId","42","directorName","编导甲","marketCode","MY","marketName","马来西亚"),Map.of("directorId","43","directorName","编导乙","marketCode","MY","marketName","马来西亚")));
        org.mockito.Mockito.when(db.one(org.mockito.ArgumentMatchers.contains("from director_daily_task_plan_setting"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of("reportDate","2026-09-22"));
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from director_daily_task_plan where"),org.mockito.ArgumentMatchers.anyMap())).thenAnswer(call->{String director=((Map<?,?>)call.getArgument(1)).get("director").toString();String code="42".equals(director)?"task-42":"task-43";long plan="42".equals(director)?12:8;return java.util.List.of(Map.of("taskCode",code,"taskName","新任务名","plannedCount",plan,"sortOrder",0));});
        org.mockito.Mockito.when(db.rows(org.mockito.ArgumentMatchers.contains("from daily_metric_report where"),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.List.of(Map.of("reporterId","42","marketCode","MY","directorTaskResults",Map.of("task-42",Map.of("taskName","新任务名","plannedCount",5L,"sortOrder",0,"actualCount",5L,"delivery","完成说明"))),Map.of("reporterId","43","marketCode","MY","directorTaskResults",Map.of("task-43",Map.of("taskName","新任务名","plannedCount",4L,"sortOrder",0,"actualCount",4L,"delivery","完成说明")))));

        var result=(Api.Envelope)controller.summary("2026-09-22",req);
        var market=(Map<?,?>)((java.util.List<?>)result.data()).getFirst();
        var tasks=(java.util.List<?>)market.get("directorTasks");var task=(Map<?,?>)tasks.getFirst();
        assertEquals(1,tasks.size());
        assertEquals("新任务名",task.get("taskName"));
        assertEquals(9L,task.get("plannedCount"));
        assertEquals(9L,task.get("actualCount"));
    }
    @Test void datesAndReviewRolesAreStrict(){
        assertEquals(LocalDate.of(2026,9,7),DailyReportController.day("2026-09-07"));
        assertThrows(Api.Problem.class,()->DailyReportController.day("07/09/2026"));
        assertTrue(DailyReportController.leader(Map.of("roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD")))));
        assertTrue(Identity.role(Map.of("roles",java.util.List.of(Map.of("roleCode","BOSS"))),"ADMIN"));
        assertFalse(Identity.exactRole(Map.of("roles",java.util.List.of(Map.of("roleCode","BOSS"))),"ADMIN"));
        assertTrue(DailyReportController.leader(Map.of("roles",java.util.List.of(Map.of("roleCode","BOSS")))));
        assertFalse(DailyReportController.leader(Map.of("roles",java.util.List.of(Map.of("roleCode","MARKET_MEMBER")))));
    }
    @Test void directorUsesTheContentDailyReportFlow(){
        var actor=Map.<String,Object>of("marketCode","MY","marketCodes",java.util.List.of("MY","UK"),"roles",java.util.List.of(Map.of("roleCode","DIRECTOR")));
        assertEquals("DIRECTOR",DailyReportController.dailyRole(actor));
        assertEquals("DIRECTOR",DailyReportController.contentRole(actor));
        assertTrue(DailyReportController.marketReviewer(actor,"MY"));
        assertTrue(DailyReportController.marketReviewer(actor,"UK"));
        assertFalse(DailyReportController.marketReviewer(actor,"US"));
    }
    @Test void operationsAndTechnicalReportsRequireNotesAndBlockers(){
        assertEquals("OPS",DailyReportController.dailyRole(Map.of("roles",java.util.List.of(Map.of("roleCode","OPS")))));
        assertEquals("TECH",DailyReportController.dailyRole(Map.of("roles",java.util.List.of(Map.of("roleCode","TECH")))));
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of(),"OPS",true));
        assertEquals(" 已处理接口故障\n第二行 ",DailyReportController.metrics(Map.of("notes"," 已处理接口故障\n第二行 ","blockers","无"),"TECH",true).get("notes"));
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of("notes","已处理接口故障"),"TECH",true));
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of("notes","已处理接口故障","blockers",""),"OPS",false));
    }
    @Test void editorSubmissionNeedsOnlyItsSixNonNegativeMetrics(){
        var values=DailyReportController.metrics(Map.of("plannedNewPublish",3,"actualNewPublish",2,"plannedFirstReview",4,"actualFirstReview",3,"plannedReworkAcceptance",2,"actualReworkAcceptance",2,"notes","今日按计划完成","blockers","无"),"EDITOR",true);
        assertEquals(3L,values.get("planned_new_publish"));
        assertEquals(0,values.get("planned_review_videos"));
        assertEquals("0",values.get("ad_spend").toString());
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of("plannedNewPublish",-1,"actualNewPublish",2,"plannedFirstReview",4,"actualFirstReview",3,"plannedReworkAcceptance",2,"actualReworkAcceptance",2),"EDITOR",true));
    }
    @Test void adsAllowMoneyButCountsMustBeWholeNumbers(){
        var values=DailyReportController.metrics(Map.ofEntries(Map.entry("plannedTest",5),Map.entry("actualTest",3),Map.entry("newAdjustPlan",2),Map.entry("adSpend","12.50"),Map.entry("adGmv","42.10"),Map.entry("impressions",100),Map.entry("clicks",8),Map.entry("orders",2),Map.entry("expandedMaterial",1),Map.entry("stoppedMaterial",0),Map.entry("notes","完成测试"),Map.entry("blockers","无")),"ADS_BUYER",true);
        assertEquals("12.50",values.get("ad_spend").toString());
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.ofEntries(Map.entry("plannedTest","1.5"),Map.entry("actualTest",3),Map.entry("newAdjustPlan",2),Map.entry("adSpend","12.50"),Map.entry("adGmv","42.10"),Map.entry("impressions",100),Map.entry("clicks",8),Map.entry("orders",2),Map.entry("expandedMaterial",1),Map.entry("stoppedMaterial",0),Map.entry("notes","完成测试"),Map.entry("blockers","无")),"ADS_BUYER",true));
    }
    @Test void deliveryResultsAreBoundedAndKeptByMetric(){
        var values=DailyReportController.deliveryResults(Map.of("deliveryResults",Map.of("actualNewPublish","1、测试1\n2、测试2 \n3、测试3 ")),"EDITOR");
        assertEquals("1、测试1\n2、测试2 \n3、测试3 ",values.get("actualNewPublish"));
        assertThrows(Api.Problem.class,()->DailyReportController.deliveryResults(Map.of("deliveryResults",Map.of("actualNewPublish","x".repeat(2001))),"EDITOR"));
    }
    @Test void editorPlanOnlyAcceptsUniqueNamedTasksWithWholeNumberPlans(){
        var tasks=DailyReportController.editorPlanTasks(Map.of("tasks",java.util.List.of(Map.of("taskName","新增发布","plannedCount",5),Map.of("taskName","自定义任务","plannedCount",4))));
        assertEquals(2,tasks.size());
        assertThrows(Api.Problem.class,()->DailyReportController.editorPlanTasks(Map.of("tasks",java.util.List.of(Map.of("taskName","新增发布","plannedCount",5),Map.of("taskName","新增发布","plannedCount",4)))));
        assertThrows(Api.Problem.class,()->DailyReportController.editorPlanTasks(Map.of("tasks",java.util.List.of(Map.of("taskName","","plannedCount",1)))));
    }
}
