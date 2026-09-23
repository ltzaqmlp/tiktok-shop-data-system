package com.company.ops.dashboard;

import org.junit.jupiter.api.Test;
import com.company.ops.common.Db;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static com.company.ops.common.Db.p;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EffectiveOrderDashboardTest {
    @Test void skuOrdersUseOneOrderAmountAndMissingDaysStayUnknown(){
        var rows=List.of(
            p("gmv",new BigDecimal("27.00"),"orderCount",2,"soldQty",2,"skuOrderCount",2,"refundAmount",0,"refundedQty",0,"refundOrderCount",0,"visitorCount",100),
            p("gmv",new BigDecimal("59.97"),"orderCount",3,"soldQty",3,"skuOrderCount",3,"refundAmount",0,"refundedQty",0,"refundOrderCount",0,"visitorCount",200)
        );
        var totals=DashboardService.shopSums(rows);
        assertEquals(new BigDecimal("86.97"),totals.get("gmv"));
        assertEquals(new BigDecimal("5"),totals.get("orderCount"));
        assertEquals(new BigDecimal("17.39400000"),totals.get("aov"));
        assertEquals(new BigDecimal("0.01666667"),totals.get("conversionRate"));
        assertEquals(BigDecimal.ZERO,DashboardService.shopSums(List.of(p("gmv",BigDecimal.ZERO,"orderCount",0))).get("aov"));
        assertNull(DashboardService.shopSums(List.of()).get("aov"));
        var missing=new java.util.ArrayList<>(rows);
        missing.add(p("gmv",null,"orderCount",null,"soldQty",null,"skuOrderCount",null,"refundAmount",null,"refundedQty",null,"refundOrderCount",null,"visitorCount",10));
        assertNull(DashboardService.shopSums(missing).get("gmv"));
    }

    @Test void campaignAdsShowReportedAttributionWithoutOrderReconciliation(){
        var db=mock(Db.class);
        when(db.rows(startsWith("select distinct currency_code"),anyMap())).thenReturn(List.of(p("currencyCode","USD")));
        when(db.one(startsWith("select sum(spend)"),anyMap())).thenReturn(
                p("spend",new BigDecimal("42.81"),"attributedRevenue",new BigDecimal("56.50"),"attributedOrderCount",6),
                p("spend",BigDecimal.ZERO,"attributedRevenue",BigDecimal.ZERO,"attributedOrderCount",0));
        when(db.count(startsWith("select count(distinct biz_date)"),anyMap())).thenReturn(1L);
        when(db.rows(startsWith("select biz_date date,sum(spend)"),anyMap())).thenReturn(List.of());

        var scope=new DashboardService.Scope("UK",LocalDate.of(2026,9,17),LocalDate.of(2026,9,17),null,"custom");
        var ads=new DashboardService(db).ads(scope);
        assertEquals(new BigDecimal("56.50"),((java.util.Map<?,?>)ads.get("attributedRevenue")).get("value"));
        assertEquals(new BigDecimal("1.31978510"),((java.util.Map<?,?>)ads.get("roi")).get("value"));
        assertEquals(new BigDecimal("7.13500000"),((java.util.Map<?,?>)ads.get("cpo")).get("value"));
    }

    @Test void campaignCpoShowsZeroWhenSpendExistsWithoutOrders(){
        var db=mock(Db.class);
        when(db.rows(startsWith("select distinct currency_code"),anyMap())).thenReturn(List.of(p("currencyCode","USD")));
        when(db.one(startsWith("select sum(spend)"),anyMap())).thenReturn(
                p("spend",new BigDecimal("19.62"),"attributedRevenue",BigDecimal.ZERO,"attributedOrderCount",0),
                p("spend",BigDecimal.ZERO,"attributedRevenue",BigDecimal.ZERO,"attributedOrderCount",0));
        when(db.count(startsWith("select count(distinct biz_date)"),anyMap())).thenReturn(1L);
        when(db.rows(startsWith("select biz_date date,sum(spend)"),anyMap())).thenReturn(List.of());

        var scope=new DashboardService.Scope("MY",LocalDate.of(2026,9,22),LocalDate.of(2026,9,22),null,"custom");
        var ads=new DashboardService(db).ads(scope);
        assertEquals(BigDecimal.ZERO,((java.util.Map<?,?>)ads.get("cpo")).get("value"));
    }

    @Test void campaignRoiShowsZeroWhenRevenueAndSpendAreZero(){
        var db=mock(Db.class);
        when(db.rows(startsWith("select distinct currency_code"),anyMap())).thenReturn(List.of(p("currencyCode","USD")));
        when(db.one(startsWith("select sum(spend)"),anyMap())).thenReturn(
                p("spend",BigDecimal.ZERO,"attributedRevenue",BigDecimal.ZERO,"attributedOrderCount",0),
                p("spend",BigDecimal.ZERO,"attributedRevenue",BigDecimal.ZERO,"attributedOrderCount",0));
        when(db.count(startsWith("select count(distinct biz_date)"),anyMap())).thenReturn(8L);
        when(db.rows(startsWith("select biz_date date,sum(spend)"),anyMap())).thenReturn(List.of());

        var scope=new DashboardService.Scope("UK",LocalDate.of(2026,9,9),LocalDate.of(2026,9,16),null,"custom");
        var roi=(java.util.Map<?,?>)new DashboardService(db).ads(scope).get("roi");
        assertEquals(BigDecimal.ZERO,roi.get("value"));
        assertEquals("ZERO_BASELINE",roi.get("comparePreviousStatus"));
    }

    @Test void productAdsDoNotDependOnCampaignAttribution(){
        var db=mock(Db.class);
        var scope=new DashboardService.Scope("MY",LocalDate.of(2026,9,22),LocalDate.of(2026,9,22),null,"custom");
        when(db.rows(startsWith("with ads as"),anyMap())).thenReturn(List.of(p("adRevenue",new BigDecimal("18.50"),"roi",new BigDecimal("1.25"),"cpo",new BigDecimal("8.00"),"orders",2)));

        var rows=new ProductAdMetricsService(db).metrics(scope);
        assertEquals(new BigDecimal("18.50"),rows.getFirst().get("adRevenue"));
        verify(db,times(1)).rows(anyString(),anyMap());
    }
}
