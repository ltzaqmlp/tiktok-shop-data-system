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
