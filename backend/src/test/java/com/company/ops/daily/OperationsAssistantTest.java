package com.company.ops.daily;

import com.company.ops.auth.Identity;
import com.company.ops.common.Api;
import com.company.ops.common.Db;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OperationsAssistantTest {
    private static Map<String,Object> actor(String id,String role){return Map.of("id",id,"roles",List.of(Map.of("roleCode",role)));}

    @Test void assistantAlwaysStartsWithItsBoundSupervisor(){
        assertEquals("OPS_ASSISTANT",DailyReportController.dailyRole(actor("2","OPS_ASSISTANT")));
        assertEquals("PENDING_OPS",DailyReportController.nextContentStatus(true,"OPS_ASSISTANT","REJECTED"));
        assertEquals("PENDING_OPS",DailyReportController.nextContentStatus(true,"OPS_ASSISTANT","PENDING_DEPT"));
        assertEquals("DRAFT",DailyReportController.nextContentStatus(false,"OPS_ASSISTANT","PENDING_DEPT"));
        assertEquals("PENDING_DEPT",DailyReportController.nextContentStatus(true,"OPS",null));
        assertEquals("PENDING_MARKET",DailyReportController.nextContentStatus(true,"EDITOR",null));
        var report=Map.<String,Object>of("reportType","OPS_ASSISTANT","submissionStatus","PENDING_OPS","operationsSupervisorId","1","reporterId","2");
        assertTrue(DailyReportController.operationsReviewer(actor("1","OPS"),report));
        assertFalse(DailyReportController.operationsReviewer(actor("3","OPS"),report));
        assertFalse(DailyReportController.operationsReviewer(actor("1","DEPT_HEAD"),report));
        assertFalse(DailyReportController.operationsReviewer(actor("1","BOSS"),report));
        assertTrue(DailyReportController.operationsReviewer(actor("3","ADMIN"),report));
        assertThrows(Api.Problem.class,()->DailyReportController.metrics(Map.of("notes","工作记录"),"OPS_ASSISTANT",true));
    }

    @Test void wrongSupervisorAndDepartmentCannotSkipSupervisorReview(){
        var db=mock(Db.class);var controller=new DailyReportController(db,mock(PlatformTransactionManager.class));
        when(db.one(startsWith("select reporter_id"),anyMap())).thenReturn(Map.of("reporterId","2","reportType","OPS_ASSISTANT"));
        when(db.one(startsWith("select operations_supervisor_id"),anyMap())).thenReturn(Map.of("operationsSupervisorId","1"));
        when(db.one(startsWith("select * from daily_metric_report"),anyMap())).thenAnswer(call->new LinkedHashMap<>(Map.of("reportType","OPS_ASSISTANT","submissionStatus","PENDING_OPS","marketCode","MY","reporterId","2")));
        var req=new MockHttpServletRequest();
        for(String role:List.of("OPS","DEPT_HEAD","OPS_ASSISTANT","BOSS")){
            req.setAttribute("actor",actor("3",role));
            assertThrows(Api.Problem.class,()->controller.approve(4,req));
        }
        verify(db,never()).exec(anyString(),anyMap());
        req.setAttribute("actor",actor("1","OPS"));controller.approve(4,req);
        verify(db).exec(startsWith("update daily_metric_report"),argThat(args->Boolean.TRUE.equals(args.get("ops"))&&"PENDING_DEPT".equals(args.get("status"))));
    }

    @Test void supervisorListsOnlyBoundAssistantReports(){
        var db=mock(Db.class);var controller=new DailyReportController(db,mock(PlatformTransactionManager.class));var req=new MockHttpServletRequest();req.setAttribute("actor",actor("1","OPS"));
        controller.simple("OPS_ASSISTANT","2026-10-07",req);
        verify(db).rows(contains("u.operations_supervisor_id=#{p.user} and r.submission_status<>'DRAFT'"),argThat(args->Long.valueOf(1).equals(args.get("user"))));
        assertTrue(Identity.exactRole(actor("1","OPS"),"OPS"));
    }

    @Test void assistantSubmissionUsesDatabaseBindingAndNumericIds(){
        var db=mock(Db.class);var controller=new DailyReportController(db,mock(PlatformTransactionManager.class));var req=new MockHttpServletRequest();req.setAttribute("actor",actor("2","OPS_ASSISTANT"));
        when(db.one(anyString(),anyMap())).thenReturn(new LinkedHashMap<>());
        when(db.one(startsWith("select operations_supervisor_id"),anyMap())).thenReturn(Map.of("operationsSupervisorId","1"));
        when(db.count(anyString(),anyMap())).thenAnswer(call->{assertEquals(1L,((Map<?,?>)call.getArgument(1)).get("id"));return 1L;});
        when(db.insert(anyString(),anyMap())).thenReturn("4");
        controller.submitSimple("2026-10-07",Map.of("notes","工作记录","blockers","无"),req);
        verify(db).insert(anyString(),argThat(args->"PENDING_OPS".equals(args.get("status"))&&"OPS_ASSISTANT".equals(args.get("type"))));
    }
}
