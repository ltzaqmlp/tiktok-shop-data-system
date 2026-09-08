package com.company.ops.daily;

import com.company.ops.common.Api;
import java.time.LocalDate;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyReportControllerTest {
    @Test void dailyReportAcceptsOnlyWorkbookStatusesAndDates(){
        assertEquals(LocalDate.of(2026,9,7),DailyReportController.day("2026-09-07"));
        assertEquals("已完成",DailyReportController.status(Map.of("completionStatus","已完成")));
        assertThrows(Api.Problem.class,()->DailyReportController.day("07/09/2026"));
        assertThrows(Api.Problem.class,()->DailyReportController.status(Map.of("completionStatus","已归档")));
    }
    @Test void dailyReportAcceptsOneCombinedEntry(){
        assertEquals(1,DailyReportController.tasks(java.util.List.of(Map.of("workDetail","日报汇总"))).size());
        assertThrows(Api.Problem.class,()->DailyReportController.tasks(java.util.List.of(Map.of(),Map.of())));
    }
    @Test void onlyBossOrAdminCanReadEveryone(){
        assertTrue(DailyReportController.leader(Map.of("roles",java.util.List.of(Map.of("roleCode","BOSS")))));
        assertFalse(DailyReportController.leader(Map.of("roles",java.util.List.of(Map.of("roleCode","MARKET_MEMBER")))));
    }
    @Test @SuppressWarnings("unchecked") void memberListAlwaysFiltersByTheirAccount(){
        var db=mock(com.company.ops.common.Db.class);when(db.rows(anyString(),anyMap())).thenReturn(java.util.List.of());
        var request=mock(HttpServletRequest.class);when(request.getAttribute("actor")).thenReturn(Map.of("id","7","roles",java.util.List.of(Map.of("roleCode","MARKET_MEMBER"))));
        new DailyReportController(db,mock(PlatformTransactionManager.class)).list("2026-09-07",request);
        var sql=ArgumentCaptor.forClass(String.class);var params=ArgumentCaptor.forClass(Map.class);verify(db).rows(sql.capture(),params.capture());
        assertTrue(sql.getValue().contains("r.created_by=#{p.user}"));assertEquals(7L,params.getValue().get("user"));
    }
    @Test void bossListExcludesUnsubmittedReports(){
        var db=mock(com.company.ops.common.Db.class);when(db.rows(anyString(),anyMap())).thenReturn(java.util.List.of());
        var request=mock(HttpServletRequest.class);when(request.getAttribute("actor")).thenReturn(Map.of("id","8","roles",java.util.List.of(Map.of("roleCode","BOSS"))));
        new DailyReportController(db,mock(PlatformTransactionManager.class)).list("2026-09-07",request);
        var sql=ArgumentCaptor.forClass(String.class);verify(db).rows(sql.capture(),anyMap());
        assertTrue(sql.getValue().contains("r.submission_status='SUBMITTED'"));
    }
}
