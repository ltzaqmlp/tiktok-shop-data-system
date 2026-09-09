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
        assertEquals("MARKET_LEAD",DailyReportController.contentRole(actor));
        assertTrue(DailyReportController.marketReviewer(actor,"MY"));
        assertFalse(DailyReportController.marketReviewer(actor,"UK"));
    }
    @Test void editorSubmissionNeedsOnlyItsSixNonNegativeMetrics(){
        var values=DailyReportController.metrics(Map.of("plannedNewPublish",3,"actualNewPublish",2,"plannedFirstReview",4,"actualFirstReview",3,"plannedReworkAcceptance",2,"actualReworkAcceptance",2),"EDITOR",true);
        assertEquals(3L,values.get("planned_new_publish"));
        assertEquals(0,values.get("planned_review_videos"));
        assertEquals("0",values.get("ad_spend").toString());
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of("plannedNewPublish",-1,"actualNewPublish",2,"plannedFirstReview",4,"actualFirstReview",3,"plannedReworkAcceptance",2,"actualReworkAcceptance",2),"EDITOR",true));
    }
    @Test void adsAllowMoneyButCountsMustBeWholeNumbers(){
        var values=DailyReportController.metrics(Map.of("plannedTest",5,"actualTest",3,"newAdjustPlan",2,"adSpend","12.50","adGmv","42.10","impressions",100,"clicks",8,"orders",2,"expandedMaterial",1,"stoppedMaterial",0),"ADS_BUYER",true);
        assertEquals("12.50",values.get("ad_spend").toString());
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of("plannedTest","1.5","actualTest",3,"newAdjustPlan",2,"adSpend","12.50","adGmv","42.10","impressions",100,"clicks",8,"orders",2,"expandedMaterial",1,"stoppedMaterial",0),"ADS_BUYER",true));
    }
    @Test void deliveryResultsAreBoundedAndKeptByMetric(){
        var values=DailyReportController.deliveryResults(Map.of("deliveryResults",Map.of("actualNewPublish","https://example.test/1")),"EDITOR");
        assertEquals("https://example.test/1",values.get("actualNewPublish"));
        assertThrows(Api.Problem.class,()->DailyReportController.deliveryResults(Map.of("deliveryResults",Map.of("actualNewPublish","x".repeat(2001))),"EDITOR"));
    }
}
