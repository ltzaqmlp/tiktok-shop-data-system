package com.company.ops;

import com.company.ops.importer.ImportMapping;
import com.company.ops.dashboard.DashboardService;
import com.company.ops.auth.AuthController;
import com.company.ops.admin.AdminController;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RulesTest {
    @Test void createLegacyWorkbookFixture()throws Exception{
        java.nio.file.Path dir=java.nio.file.Path.of("target","fixtures");java.nio.file.Files.createDirectories(dir);
        try(var book=new org.apache.poi.hssf.usermodel.HSSFWorkbook()){
            var sheet=book.createSheet("data");String[][] rows={{"Date","GMV","Orders"},{"2026-09-10","200","4"}};
            for(int i=0;i<rows.length;i++){var row=sheet.createRow(i);for(int j=0;j<rows[i].length;j++)row.createCell(j).setCellValue(rows[i][j]);}
            try(var out=java.nio.file.Files.newOutputStream(dir.resolve("legacy.xls"))){book.write(out);}
        }
    }
    @Test void emptySqlAggregateRemainsReadable(){
        var mapper=org.mockito.Mockito.mock(com.company.ops.common.SqlMapper.class);
        org.mockito.Mockito.when(mapper.select(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.Collections.singletonList(null));
        var db=new com.company.ops.common.Db(mapper,new com.fasterxml.jackson.databind.ObjectMapper());
        assertTrue(db.one("select sum(visitor_count) from empty_table",java.util.Map.of()).isEmpty());
    }
    @Test void importAndMetricBoundaries(){
        assertEquals(new BigDecimal("1234.50"),ImportMapping.amount("RM 1,234.50"));
        assertEquals(new BigDecimal("-20.00"),ImportMapping.amount("(20.00)"));
        assertEquals(new BigDecimal("0.123"),ImportMapping.ratio("12.3%"));
        assertThrows(RuntimeException.class,()->ImportMapping.ratio("101%"));
        assertEquals(new BigDecimal("2.0000"),ImportMapping.ratio("200.00%",true));
        assertThrows(RuntimeException.class,()->ImportMapping.ratio("-1%",true));
        assertEquals(LocalDate.of(2026,9,5),ImportMapping.date("2026/9/5"));
        assertEquals(LocalDate.of(2026,9,5),ImportMapping.date("05/09/2026"));
        assertEquals(LocalDate.of(2026,10,9),ImportMapping.date("09/10/2026"));
        assertEquals(LocalDate.of(2026,9,5),ImportMapping.dateTime("05/09/2026 14:08:11").toLocalDate());
        var productAdFile="Product data 2026-09-13 - 2026-09-13 - Campaign 1871234567890123456.xlsx";
        assertEquals(LocalDate.of(2026,9,13),ImportMapping.productAdDate(productAdFile,null));
        assertEquals("1871234567890123456",ImportMapping.productAdCampaignId(productAdFile));
        var productRange=ImportMapping.dateRange("数据分析日期: 30/08/2026~05/09/2026").orElseThrow();
        assertEquals(LocalDate.of(2026,8,30),productRange.from());
        assertEquals(LocalDate.of(2026,9,5),productRange.to());
        assertEquals("COMPLETED",ImportMapping.status("已完成"));
        assertEquals("AFFILIATE_PENDING",ImportMapping.status("待确认"));
        assertEquals("AFFILIATE_SETTLED",ImportMapping.status("已结算"));
        assertEquals("fact_affiliate_order",ImportMapping.TABLES.get("AFFILIATE_ORDER"));
        assertTrue(ImportMapping.REQUIRED.get("AFFILIATE_ORDER").contains("quantity"));
        assertEquals("UNKNOWN",ImportMapping.status("new source status"));
        assertEquals(new BigDecimal("40.00000000"),DashboardService.divide(120,3));
        assertNull(DashboardService.divide(10,0));
        assertNull(DashboardService.divide(null,10));
        assertNull(DashboardService.change(10,0));
        assertEquals(new BigDecimal("-0.25000000"),DashboardService.change(75,100));
        assertEquals("OK",DashboardService.comparisonStatus(120,100,1,1,1,1));
        assertEquals("ZERO_BASELINE",DashboardService.comparisonStatus(120,0,1,1,1,1));
        assertEquals("NO_HISTORY",DashboardService.comparisonStatus(120,0,1,1,0,1));
        assertEquals("HISTORY_PARTIAL",DashboardService.comparisonStatus(120,100,1,1,5,7));
        assertEquals("CURRENT_MISSING",DashboardService.comparisonStatus(120,100,0,1,1,1));
        var week=new DashboardService.Scope("MY",LocalDate.of(2026,9,7),LocalDate.of(2026,9,9),null,"week");
        assertEquals(LocalDate.of(2026,8,31),week.previous().dateFrom());
        assertEquals(LocalDate.of(2026,9,2),week.previous().dateTo());
        var month=new DashboardService.Scope("MY",LocalDate.of(2026,3,1),LocalDate.of(2026,3,31),null,"month");
        assertEquals(LocalDate.of(2026,2,1),month.previous().dateFrom());
        assertEquals(LocalDate.of(2026,2,28),month.previous().dateTo());
        assertEquals("较上周同期",week.comparisonLabel());
        assertEquals("skuorders",ImportMapping.normalize("SKU Orders:"));
        assertEquals("attributed_order_count",ImportMapping.fieldForSource("GMV_MAX_PRODUCT","sku_order_count"));
        assertEquals("sku_order_count",ImportMapping.fieldForSource("PRODUCT_DAILY","sku_order_count"));
        assertThrows(RuntimeException.class,()->AuthController.validatePassword("1234567890"));
        assertDoesNotThrow(()->AuthController.validatePassword("StrongPass12!"));
    }
    @Test void sessionStatusDoesNotRequireProtectedIdentity(){
        var encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);when(encoder.encode(anyString())).thenReturn("dummy");
        var controller=new AuthController(mock(com.company.ops.common.Db.class),mock(com.company.ops.auth.Identity.class),encoder,mock(org.springframework.transaction.PlatformTransactionManager.class));
        var req=mock(jakarta.servlet.http.HttpServletRequest.class);var session=mock(jakarta.servlet.http.HttpSession.class);
        assertEquals(Map.of("authenticated",false),((com.company.ops.common.Api.Envelope)controller.session(req)).data());
        when(req.getSession(false)).thenReturn(session);when(session.getAttribute("uid")).thenReturn(1L);
        assertEquals(Map.of("authenticated",true),((com.company.ops.common.Api.Envelope)controller.session(req)).data());
    }

    @Test void newUserPasswordIsFixed(){
        assertEquals("admin100",AdminController.temporary());
    }

    @Test void failedLoginCanBeFilteredByRecordedUsername(){
        var db=mock(com.company.ops.common.Db.class);
        when(db.rows(anyString(),anyMap())).thenReturn(java.util.List.of());
        when(db.count(anyString(),anyMap())).thenReturn(0L);
        var controller=new AdminController(db,mock(com.company.ops.auth.Identity.class),mock(org.springframework.security.crypto.password.PasswordEncoder.class),mock(org.springframework.transaction.PlatformTransactionManager.class));
        var req=mock(jakarta.servlet.http.HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/admin/login-logs");

        controller.logs(Map.of("username","missing-user"),req);

        verify(db).rows(contains("l.username ilike"),anyMap());
    }

    @Test void deletingSkuConfigAlsoDeletesItsProductMappings(){
        var db=mock(com.company.ops.common.Db.class);
        when(db.one(contains("from sku_config"),anyMap())).thenReturn(new java.util.LinkedHashMap<>(Map.of("shopId","7","sellerSku","SKU-1")));
        when(db.exec(startsWith("delete from sku_config"),anyMap())).thenReturn(1);
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status=mock(org.springframework.transaction.TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(status);
        var controller=new AdminController(db,mock(com.company.ops.auth.Identity.class),mock(org.springframework.security.crypto.password.PasswordEncoder.class),manager);

        controller.deleteSkuConfig(9,mock(jakarta.servlet.http.HttpServletRequest.class));

        verify(db).exec(startsWith("delete from product_sku_mapping"),argThat(args->"7".equals(args.get("shop"))&&"SKU-1".equals(args.get("sku"))));
        verify(manager).commit(status);
    }

    @Test void usersCanHaveMultipleMarketPermissions(){
        var user=Map.<String,Object>of("marketCode","MY","marketCodes",java.util.List.of("MY","UK","US"),"roles",java.util.List.of());
        assertEquals(java.util.List.of("MY","UK","US"),com.company.ops.auth.Identity.marketCodes(user));
        assertTrue(com.company.ops.auth.Identity.hasMarket(user,"UK"));
        assertFalse(com.company.ops.auth.Identity.hasMarket(user,"DE"));
    }
}
