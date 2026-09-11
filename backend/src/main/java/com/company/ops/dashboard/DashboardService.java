package com.company.ops.dashboard;

import com.company.ops.common.*;
import static com.company.ops.common.Db.p;
import static com.company.ops.common.Db.decimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DashboardService {
    private final Db db;
    public DashboardService(Db db){this.db=db;}

    public record Scope(String marketCode,LocalDate dateFrom,LocalDate dateTo,Long shopId,String comparisonPeriod){
        public Map<String,Object> params(){return p("market",marketCode,"from",dateFrom,"to",dateTo,"shop",shopId);}
        public Scope between(LocalDate from,LocalDate to){return new Scope(marketCode,from,to,shopId,comparisonPeriod);}
        public long days(){return ChronoUnit.DAYS.between(dateFrom,dateTo)+1;}
        public Scope previous(){return switch(comparisonPeriod){case "week"->between(dateFrom.minusWeeks(1),dateTo.minusWeeks(1));case "month"->between(dateFrom.minusMonths(1),dateTo.minusMonths(1));default->between(dateFrom.minusDays(days()),dateFrom.minusDays(1));};}
        public String comparisonLabel(){return switch(comparisonPeriod){case "today"->"较昨日";case "yesterday"->"较前日";case "week"->"较上周同期";case "month"->"较上月同期";default->"较上一周期";};}
    }

    public Scope scope(String market,String from,String to,Long shop,String comparisonPeriod){
        Api.require(market!=null&&!market.isBlank(),"必须选择单一市场");
        LocalDate a=LocalDate.parse(from),b=LocalDate.parse(to);Api.require(!a.isAfter(b)&&ChronoUnit.DAYS.between(a,b)<366,"日期范围必须为1–366天");
        Api.require(Set.of("today","yesterday","week","month","custom").contains(comparisonPeriod),"比较周期无效");
        Api.require(db.count("select count(*) from dim_market where market_code=#{p.market} and enabled",p("market",market))==1,"市场不存在或已停用");
        if(shop!=null)Api.require(db.count("select count(*) from dim_shop where id=#{p.shop} and market_code=#{p.market}",p("shop",shop,"market",market))==1,"店铺与市场不匹配");
        return new Scope(market,a,b,shop,comparisonPeriod);
    }

    public static final String WHERE=" where market_code=#{p.market} and biz_date between #{p.from} and #{p.to} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})";
    private static final String MARKET_SHOP=" where market_code=#{p.market} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})";
    private static final String PERIOD_EXACT=" where market_code=#{p.market} and date_from=#{p.from} and date_to=#{p.to} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})";

    public static BigDecimal divide(Object top,Object bottom){
        if(top==null||bottom==null)return null;BigDecimal b=decimal(bottom);return b.signum()==0?null:decimal(top).divide(b,8,RoundingMode.HALF_UP);
    }
    public static BigDecimal change(Object now,Object previous){
        if(now==null||previous==null)return null;BigDecimal baseline=decimal(previous);return baseline.signum()==0?null:decimal(now).subtract(baseline).divide(baseline.abs(),8,RoundingMode.HALF_UP);
    }
    private static BigDecimal subtract(Object left,Object right){return left==null||right==null?null:decimal(left).subtract(decimal(right));}
    public static String comparisonStatus(Object now,Object previous,long currentDataDays,long expectedCurrentDays,long historyDataDays,long expectedHistoryDays){
        if(currentDataDays<=0)return "CURRENT_MISSING";
        if(currentDataDays<expectedCurrentDays)return "CURRENT_PARTIAL";
        if(now==null)return "VALUE_UNAVAILABLE";
        if(historyDataDays<=0)return "NO_HISTORY";
        if(historyDataDays<expectedHistoryDays)return "HISTORY_PARTIAL";
        if(previous==null)return "VALUE_UNAVAILABLE";
        if(decimal(previous).signum()==0)return "ZERO_BASELINE";
        return "OK";
    }
    private static Object comparisonValue(String status,Object now,Object previous){return "OK".equals(status)?change(now,previous):null;}

    @Transactional public void reaggregate(long shop,String market,Collection<LocalDate> dates){
        db.rows("select pg_advisory_xact_lock(#{p.shop})",p("shop",shop));
        for(LocalDate date:dates){
            var a=p("shop",shop,"market",market,"date",date);
            db.exec("delete from agg_dashboard_daily where shop_id=#{p.shop} and biz_date=#{p.date}",a);
            db.exec("""
                insert into agg_dashboard_daily(shop_id,market_code,biz_date,currency_code,gmv,order_count,sold_qty,sku_order_count,refund_amount,refunded_qty,refund_customer_count,cancel_order_count,ad_spend,ad_revenue,ad_order_count)
                select #{p.shop},#{p.market},#{p.date},m.currency_code,
                       coalesce(s.gmv,0),coalesce(s.order_count,0),
                       coalesce(s.sold_qty,pr.sold_qty,0),
                       coalesce(s.sku_order_count,pr.sku_order_count,0),
                       coalesce(s.refund_amount,pr.refund_amount,0),
                       coalesce(pr.refunded_qty,0),coalesce(pr.refund_customer_count,0),
                       o.cancelled,coalesce(ad.spend,0),coalesce(ad.revenue,0),coalesce(ad.orders,0)
                from dim_market m
                left join fact_shop_daily s on s.shop_id=#{p.shop} and s.biz_date=#{p.date}
                cross join (select sum(sold_qty) sold_qty,sum(sku_order_count) sku_order_count,sum(refund_amount) refund_amount,sum(refunded_qty) refunded_qty,sum(refund_customer_count) refund_customer_count from fact_product_daily where shop_id=#{p.shop} and biz_date=#{p.date}) pr
                cross join (select count(*) filter(where normalized_status='CANCELLED') cancelled from fact_order where shop_id=#{p.shop} and biz_date=#{p.date}) o
                cross join (select case when count(distinct currency_code)<=1 then sum(spend) else 0 end spend,case when count(distinct currency_code)<=1 then sum(attributed_revenue) else 0 end revenue,case when count(distinct currency_code)<=1 then sum(attributed_order_count) else 0 end orders from fact_ad_campaign_daily where shop_id=#{p.shop} and biz_date=#{p.date}) ad
                where m.market_code=#{p.market}
                """,a);
        }
    }

    /** Shop Analytics is the authoritative daily source for dashboard KPI values. */
    private Map<String,Object> shopSums(Scope scope){
        var row=db.one("""
            select
              case when count(*)=0 then null else sum(gmv) end gmv,
              case when count(*)=0 then null else sum(order_count) end order_count,
              case when count(*)=0 then null else sum(sold_qty) end sold_qty,
              case when count(*)=0 or count(*) filter(where sku_order_count is null)>0 then null else sum(sku_order_count) end sku_order_count,
              case when count(*)=0 or count(*) filter(where refund_amount is null)>0 then null else sum(refund_amount) end refund_amount,
              case when count(*)=0 or count(*) filter(where visitor_count is null)>0 then null else sum(visitor_count) end visitor_count,
              case when count(*)=0 or count(*) filter(where visitor_count is null or conversion_rate_src is null)>0 then null else sum(visitor_count*conversion_rate_src)/nullif(sum(visitor_count),0) end conversion_rate
            from fact_shop_daily"""+WHERE,scope.params());
        row.put("aov",divide(row.get("gmv"),row.get("orderCount")));
        return row;
    }

    private Object affiliateSales(Scope scope){
        return db.one("select coalesce(sum(item_qty),0) affiliate_sales from fact_affiliate_order where market_code=#{p.market} and biz_date between #{p.from} and #{p.to} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop}) and normalized_status in ('AFFILIATE_PENDING','AFFILIATE_SETTLED')",scope.params()).get("affiliateSales");
    }

    public List<Map<String,Object>> daily(Scope scope){
        return db.rows("""
            select biz_date date,
                   case when count(*)=0 then null else sum(gmv) end gmv,
                   case when count(*)=0 then null else sum(order_count) end order_count,
                   case when count(*)=0 then null else sum(sold_qty) end sold_qty,
                   case when count(*) filter(where sku_order_count is null)=0 then sum(sku_order_count) end sku_order_count,
                   case when count(*) filter(where refund_amount is null)=0 then sum(refund_amount) end refund_amount,
                   sum(gmv)/nullif(sum(order_count),0) aov
            from fact_shop_daily"""+WHERE+" group by biz_date order by biz_date",scope.params());
    }

    private String currency(Scope scope){return db.one("select currency_code from dim_market where market_code=#{p.market}",scope.params()).get("currencyCode").toString();}
    private long dataDays(Scope scope,String table){return db.count("select count(distinct biz_date) from "+table+WHERE,scope.params());}
    private long shopMetricDays(Scope scope,String key){
        String predicate=switch(key){
            case "skuOrderCount" -> "sku_order_count is not null";
            case "refundAmount" -> "refund_amount is not null";
            case "visitorCount" -> "visitor_count is not null";
            case "conversionRate" -> "visitor_count is not null and conversion_rate_src is not null";
            default -> "true";
        };
        return db.count("select count(distinct biz_date) from fact_shop_daily"+WHERE+" and "+predicate,scope.params());
    }

    private Map<String,Object> coverage(Scope scope,String table){
        var row=db.one("select count(*) rows_in_range,count(distinct biz_date) days_in_range,min(biz_date) first_biz_date,max(biz_date) last_biz_date from "+table+WHERE,scope.params());
        var latest=db.one("select max(biz_date) latest_biz_date from "+table+MARKET_SHOP,scope.params());row.put("latestBizDate",latest.get("latestBizDate"));return row;
    }
    private long productPeriodRows(Scope scope){return db.count("select count(*) from fact_product_period"+PERIOD_EXACT,scope.params());}
    private Map<String,Object> productPeriodCoverage(Scope scope){
        long rows=productPeriodRows(scope);var latest=db.one("select max(date_to) latest_biz_date from fact_product_period"+MARKET_SHOP,scope.params());
        return p("rowsInRange",rows,"daysInRange",rows>0?scope.days():0,"firstBizDate",rows>0?scope.dateFrom:null,"lastBizDate",rows>0?scope.dateTo:null,"latestBizDate",latest.get("latestBizDate"));
    }
    private String fullProductSource(Scope scope){
        if(productPeriodRows(scope)>0)return "PRODUCT_PERIOD";
        if(dataDays(scope,"fact_product_daily")>=scope.days())return "PRODUCT_DAILY";
        return null;
    }
    private long productCoverageDays(Scope scope){return productPeriodRows(scope)>0?scope.days():dataDays(scope,"fact_product_daily");}

    private Map<String,Object> productAggregate(Scope scope){
        String source=fullProductSource(scope);if(source==null)return new LinkedHashMap<>();
        String table=source.equals("PRODUCT_PERIOD")?"fact_product_period":"fact_product_daily";
        String where=source.equals("PRODUCT_PERIOD")?PERIOD_EXACT:WHERE;
        var row=db.one("""
            select sum(gmv) gmv,sum(order_count) order_count,sum(sku_order_count) sku_order_count,sum(sold_qty) sold_qty,
                   sum(estimated_customer_count) estimated_customer_count,sum(impressions) impressions,sum(clicks) clicks,
                   sum(add_to_cart_count) add_to_cart_count,sum(refund_amount) refund_amount,sum(refunded_qty) refunded_qty,
                   sum(refund_customer_count) refund_customer_count,sum(unique_impressions) unique_impressions,
                   sum(unique_clicks) unique_clicks,sum(added_user_count) added_user_count,count(*) product_rows,
                   count(*) filter(where sold_qty>0) sold_qty_non_zero_rows,count(*) filter(where sku_order_count>0) sku_order_count_non_zero_rows
            from """+" "+table+where,scope.params());
        row.put("source",source);return row;
    }

    private Map<String,Object> refundProductAggregate(Scope scope){
        var period=db.one("select date_from,date_to,sum(refunded_qty) refunded_qty,sum(refund_customer_count) refund_customer_count from fact_product_period where market_code=#{p.market} and date_from<=#{p.from} and date_to>=#{p.to} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop}) group by date_from,date_to order by date_to-date_from limit 1",scope.params());
        if(!period.isEmpty()){period.put("source","PRODUCT_PERIOD");return period;}
        return productAggregate(scope);
    }

    private Map<String,Object> productQuality(Scope scope){
        var row=productAggregate(scope);if(row.isEmpty())return p("source","NONE","productRows",0,"soldQty",null,"skuOrderCount",null,"soldQtyNonZeroRows",0,"skuOrderCountNonZeroRows",0);
        return p("source",row.get("source"),"productRows",row.get("productRows"),"soldQty",row.get("soldQty"),"skuOrderCount",row.get("skuOrderCount"),"soldQtyNonZeroRows",row.get("soldQtyNonZeroRows"),"skuOrderCountNonZeroRows",row.get("skuOrderCountNonZeroRows"));
    }

    public Map<String,Object> overview(Scope scope){
        long days=scope.days();var previousScope=scope.previous();var avgScope=scope.between(scope.dateFrom.minusDays(7),scope.dateFrom.minusDays(1));
        var now=shopSums(scope);var before=shopSums(previousScope);var avg=shopSums(avgScope);
        Object influencerSales=affiliateSales(scope);
        var trendScope=days==1?scope.between(scope.dateTo.minusDays(13),scope.dateTo):scope;var trend=daily(trendScope);
        var shopTrend=db.rows("select biz_date date,case when count(*) filter(where visitor_count is null)=0 then sum(visitor_count) end visitor_count,case when count(*) filter(where visitor_count is null or conversion_rate_src is null)=0 then sum(visitor_count*conversion_rate_src)/nullif(sum(visitor_count),0) end conversion_rate from fact_shop_daily"+WHERE+" group by biz_date order by biz_date",trendScope.params());
        boolean productDataAvailable=fullProductSource(scope)!=null;
        var result=p("currencyCode",currency(scope),"comparisonLabel",scope.comparisonLabel(),"productDataAvailable",productDataAvailable,"productDataSource",Objects.toString(fullProductSource(scope),"NONE"));
        result.put("affiliateSales",p("value",influencerSales,"trend",List.of()));
        result.put("selfSales",p("value",subtract(now.get("orderCount"),influencerSales),"trend",List.of()));
        var coverage=p("shopAnalytics",coverage(scope,"fact_shop_daily"),"productDaily",coverage(scope,"fact_product_daily"),"productPeriod",productPeriodCoverage(scope),"orderDetail",coverage(scope,"fact_order"),"ads",coverage(scope,"fact_ad_campaign_daily"));
        result.put("dataCoverage",coverage);result.put("productDataQuality",productQuality(scope));
        for(String key:List.of("gmv","orderCount","soldQty","skuOrderCount","aov","refundAmount","visitorCount","conversionRate")){
            long currentDataDays=shopMetricDays(scope,key),previousDataDays=shopMetricDays(previousScope,key),avgDataDays=shopMetricDays(avgScope,key);
            Object average=avg.get(key);if(!Set.of("aov","conversionRate").contains(key)&&average!=null)average=decimal(average).divide(BigDecimal.valueOf(7),8,RoundingMode.HALF_UP);
            String previousStatus=comparisonStatus(now.get(key),before.get(key),currentDataDays,days,previousDataDays,days);
            String avgStatus=days==1?comparisonStatus(now.get(key),average,currentDataDays,1,avgDataDays,7):"NOT_APPLICABLE";
            var points=(Set.of("visitorCount","conversionRate").contains(key)?shopTrend:trend).stream().map(row->p("date",row.get("date"),"value",row.get(key))).toList();
            result.put(key,p("value",now.get(key),"comparePrevious",comparisonValue(previousStatus,now.get(key),before.get(key)),"comparePreviousStatus",previousStatus,"compare7dAvg",days==1?comparisonValue(avgStatus,now.get(key),average):null,"compare7dAvgStatus",avgStatus,"trend",points));
        }
        result.put("dataFreshness",db.one("select max(biz_date_to) latest_biz_date,max(finished_at) latest_import_at from sys_import_task where source_type='SHOP_ANALYTICS' and market_code=#{p.market} and status='SUCCESS' and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})",scope.params()));
        return result;
    }

    public Map<String,Object> funnel(Scope scope){
        var row=db.one("""
            select case when count(*)=0 or count(*) filter(where impressions is null)>0 then null else sum(impressions) end impressions,
                   case when count(*)=0 or count(*) filter(where clicks is null)>0 then null else sum(clicks) end clicks,
                   null::bigint add_to_cart_count,
                   case when count(*)=0 then null else sum(order_count) end order_count,
                   case when count(*)=0 or count(*) filter(where sku_order_count is null)>0 then null else sum(sku_order_count) end sku_order_count,
                   case when count(*)=0 or count(*) filter(where unique_impressions is null)>0 then null else sum(unique_impressions) end unique_impressions,
                   case when count(*)=0 or count(*) filter(where unique_clicks is null)>0 then null else sum(unique_clicks) end unique_clicks,
                   null::bigint added_user_count,
                   case when count(*)=0 or count(*) filter(where customer_count is null)>0 then null else sum(customer_count) end estimated_customer_count,
                   count(*) shop_rows
            from fact_shop_daily"""+WHERE,scope.params());
        boolean hasShop=((Number)row.getOrDefault("shopRows",0)).longValue()>0;row.put("source",hasShop?"SHOP_ANALYTICS_PARTIAL":"NONE");row.put("complete",false);
        var product=productAggregate(scope);
        if(hasShop&&!product.isEmpty()){
            row.put("addToCartCount",product.get("addToCartCount"));
            row.put("addedUserCount",product.get("addedUserCount"));
            row.put("addToCartRate",divide(product.get("addToCartCount"),product.get("clicks")));
        }
        row.put("ctr",divide(row.get("clicks"),row.get("impressions")));
        if(product.isEmpty())row.put("addToCartRate",null);
        row.put("ctor",divide(row.get("skuOrderCount"),row.get("clicks")));
        return row;
    }

    private Map<String,Object> adTotals(Scope scope){return db.one("select sum(spend) spend,sum(attributed_revenue) attributed_revenue,sum(attributed_order_count) attributed_order_count from fact_ad_campaign_daily"+WHERE,scope.params());}
    public Map<String,Object> ads(Scope scope){
        long days=scope.days();var previous=scope.previous();
        var currencies=db.rows("select distinct currency_code from fact_ad_campaign_daily"+WHERE,scope.between(previous.dateFrom,scope.dateTo).params());if(currencies.size()>1)throw new Api.Problem(400,"DASHBOARD_CURRENCY_MIXED","广告数据包含不同币种，不能汇总或比较");
        var now=adTotals(scope);var before=adTotals(previous);now.put("roi",divide(now.get("attributedRevenue"),now.get("spend")));now.put("cpo",divide(now.get("spend"),now.get("attributedOrderCount")));before.put("roi",divide(before.get("attributedRevenue"),before.get("spend")));before.put("cpo",divide(before.get("spend"),before.get("attributedOrderCount")));
        long currentDays=dataDays(scope,"fact_ad_campaign_daily"),historyDays=dataDays(previous,"fact_ad_campaign_daily");var out=p("currencyCode",currencies.isEmpty()?currency(scope):currencies.getFirst().get("currencyCode"));
        for(String key:List.of("spend","attributedRevenue","roi","cpo")){String status=comparisonStatus(now.get(key),before.get(key),currentDays,days,historyDays,days);out.put(key,p("value",now.get(key),"comparePrevious",comparisonValue(status,now.get(key),before.get(key)),"comparePreviousStatus",status));}
        out.put("trend",db.rows("select biz_date date,sum(spend) spend,sum(attributed_revenue) attributed_revenue,sum(attributed_order_count) attributed_order_count,case when sum(spend)=0 then null else sum(attributed_revenue)/sum(spend) end roi,case when sum(attributed_order_count)=0 then null else sum(spend)/sum(attributed_order_count) end cpo from fact_ad_campaign_daily"+WHERE+" group by biz_date order by biz_date",scope.params()));return out;
    }

    public List<Map<String,Object>> statuses(Scope scope){return db.rows("select normalized_status status,count(*) count,count(*)::numeric/nullif(sum(count(*)) over(),0) ratio from fact_order"+WHERE+" group by normalized_status order by count(*) desc",scope.params());}
    public List<Map<String,Object>> skuSales(Scope scope){return db.rows("select c.display_name,string_agg(distinct c.seller_sku,',' order by c.seller_sku) seller_sku,sum(greatest(o.quantity-least(o.return_quantity,o.quantity),0)) sales,sum(greatest(o.quantity-least(o.return_quantity,o.quantity),0))::numeric/nullif(sum(sum(greatest(o.quantity-least(o.return_quantity,o.quantity),0))) over(),0) ratio from fact_order_sku o join sku_config c on c.shop_id=o.shop_id and c.seller_sku=o.seller_sku and c.enabled where o.market_code=#{p.market} and o.biz_date between #{p.from} and #{p.to} and (cast(#{p.shop} as bigint) is null or o.shop_id=#{p.shop}) and o.normalized_status in ('PAID','SHIPPED','COMPLETED') group by c.display_name having sum(greatest(o.quantity-least(o.return_quantity,o.quantity),0))>0 order by min(c.sort_order),min(c.id)",scope.params());}
    private long cancelledOrders(Scope scope){return db.count("select count(*) from fact_order"+WHERE+" and normalized_status='CANCELLED'",scope.params());}
    private long orderCoverageDays(Scope scope){
        long full=db.count("select count(*) from sys_import_task where source_type='ORDER_DETAIL' and status='SUCCESS' and market_code=#{p.market} and biz_date_from<=#{p.from} and biz_date_to>=#{p.to} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})",scope.params());
        return full>0?scope.days():dataDays(scope,"fact_order");
    }

    public Map<String,Object> afterSales(Scope scope){
        long days=scope.days();var previous=scope.previous();var nowShop=shopSums(scope);var beforeShop=shopSums(previous);var nowProduct=refundProductAggregate(scope);var beforeProduct=refundProductAggregate(previous);var out=new LinkedHashMap<String,Object>();
        String refundStatus=comparisonStatus(nowShop.get("refundAmount"),beforeShop.get("refundAmount"),shopMetricDays(scope,"refundAmount"),days,shopMetricDays(previous,"refundAmount"),days);
        out.put("refundAmount",p("value",nowShop.get("refundAmount"),"comparePrevious",comparisonValue(refundStatus,nowShop.get("refundAmount"),beforeShop.get("refundAmount")),"comparePreviousStatus",refundStatus));
        boolean periodTotal="PRODUCT_PERIOD".equals(nowProduct.get("source"));
        for(String key:List.of("refundedQty","refundCustomerCount")){
            Object now=nowProduct.get(key),before=beforeProduct.get(key);String status=periodTotal?"PERIOD_TOTAL":comparisonStatus(now,before,productCoverageDays(scope),days,productCoverageDays(previous),days);
            out.put(key,p("value",now,"comparePrevious",comparisonValue(status,now,before),"comparePreviousStatus",status));
        }
        if(periodTotal)out.put("productDataRange",p("dateFrom",nowProduct.get("dateFrom"),"dateTo",nowProduct.get("dateTo")));
        long nowCancel=cancelledOrders(scope),beforeCancel=cancelledOrders(previous);String cancelStatus=comparisonStatus(nowCancel,beforeCancel,orderCoverageDays(scope),days,orderCoverageDays(previous),days);
        out.put("cancelOrderCount",p("value",nowCancel,"comparePrevious",comparisonValue(cancelStatus,nowCancel,beforeCancel),"comparePreviousStatus",cancelStatus));
        return out;
    }
}
