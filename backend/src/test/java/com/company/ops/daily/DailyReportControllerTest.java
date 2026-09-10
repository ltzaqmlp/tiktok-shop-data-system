package com.company.ops.daily;

import com.company.ops.common.Api;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyReportControllerTest {
    @Test void datesAndReviewRolesAreStrict(){
        assertEquals(LocalDate.of(2026,9,7),DailyReportController.day("2026-09-07"));
        assertThrows(Api.Problem.class,()->DailyReportController.day("07/09/2026"));
        assertTrue(DailyReportController.leader(Map.of("roles",java.util.List.of(Map.of("roleCode","DEPT_HEAD")))));
        assertFalse(DailyReportController.leader(Map.of("roles",java.util.List.of(Map.of("roleCode","MARKET_MEMBER")))));
    }
    @Test void directorUsesTheContentDailyReportFlow(){
        var actor=Map.<String,Object>of("marketCode","MY","roles",java.util.List.of(Map.of("roleCode","DIRECTOR")));
        assertEquals("DIRECTOR",DailyReportController.dailyRole(actor));
        assertEquals("DIRECTOR",DailyReportController.contentRole(actor));
        assertTrue(DailyReportController.marketReviewer(actor,"MY"));
        assertFalse(DailyReportController.marketReviewer(actor,"UK"));
    }
    @Test void operationsAndTechnicalReportsRequireNotesAndBlockers(){
        assertEquals("OPS",DailyReportController.dailyRole(Map.of("roles",java.util.List.of(Map.of("roleCode","OPS")))));
        assertEquals("TECH",DailyReportController.dailyRole(Map.of("roles",java.util.List.of(Map.of("roleCode","TECH")))));
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of(),"OPS",true));
        assertEquals("已处理接口故障",DailyReportController.metrics(Map.of("notes","已处理接口故障","blockers","无"),"TECH",true).get("notes"));
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of("notes","已处理接口故障"),"TECH",true));
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
        var values=DailyReportController.deliveryResults(Map.of("deliveryResults",Map.of("actualNewPublish","https://example.test/1")),"EDITOR");
        assertEquals("https://example.test/1",values.get("actualNewPublish"));
        assertThrows(Api.Problem.class,()->DailyReportController.deliveryResults(Map.of("deliveryResults",Map.of("actualNewPublish","x".repeat(2001))),"EDITOR"));
    }
    @Test void editorPlanOnlyAcceptsUniqueNamedTasksWithWholeNumberPlans(){
        var tasks=DailyReportController.editorPlanTasks(Map.of("tasks",java.util.List.of(Map.of("taskName","新增发布","plannedCount",5),Map.of("taskName","自定义任务","plannedCount",4))));
        assertEquals(2,tasks.size());
        assertThrows(Api.Problem.class,()->DailyReportController.editorPlanTasks(Map.of("tasks",java.util.List.of(Map.of("taskName","新增发布","plannedCount",5),Map.of("taskName","新增发布","plannedCount",4)))));
        assertThrows(Api.Problem.class,()->DailyReportController.editorPlanTasks(Map.of("tasks",java.util.List.of(Map.of("taskName","","plannedCount",1)))));
    }
}
