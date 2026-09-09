package com.company.ops.importer;

import com.company.ops.common.Api;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ImportMapping {
    private ImportMapping(){}
    public record DateRange(LocalDate from,LocalDate to){}
    public static final Map<String,String> TABLES=Map.of("SHOP_ANALYTICS","fact_shop_daily","PRODUCT_DAILY","fact_product_daily","ORDER_DETAIL","fact_order","GMV_MAX_CAMPAIGN","fact_ad_campaign_daily");
    public static final Map<String,List<String>> REQUIRED=Map.of("SHOP_ANALYTICS",List.of("biz_date","gmv","order_count"),"PRODUCT_DAILY",List.of("product_id","gmv","order_count","sold_qty","sku_order_count","impressions","clicks","add_to_cart_count"),"ORDER_DETAIL",List.of("order_id","sku_id","source_status","quantity"),"GMV_MAX_CAMPAIGN",List.of("biz_date","spend","attributed_revenue","attributed_order_count"));
    public static final Map<String,String> KEYS=Map.of("SHOP_ANALYTICS","shop_id,biz_date","PRODUCT_DAILY","shop_id,biz_date,product_id","ORDER_DETAIL","shop_id,order_id","GMV_MAX_CAMPAIGN","shop_id,biz_date,ad_account_key,campaign_id");
    public static String normalize(String value){return Objects.toString(value,"").toLowerCase(Locale.ROOT).replaceAll("[\\s_（）()\\-:：/]+","").replaceAll("[.，,]+$","").trim();}
    public static BigDecimal amount(String value){
        if(value==null||value.isBlank()||value.trim().equals("-"))return BigDecimal.ZERO;
        String v=value.trim().replace(",","").replaceAll("(?i)^(MYR|RM|USD|THB|VND|PHP|SGD|IDR|GBP|EUR|[$€£¥])\\s*","").replace(" ","");
        if(v.startsWith("(")&&v.endsWith(")"))v="-"+v.substring(1,v.length()-1);
        return new BigDecimal(v);
    }
    public static BigDecimal ratio(String value){BigDecimal n=amount(value.replace("%",""));if(value.contains("%"))n=n.movePointLeft(2);Api.require(n.signum()>=0&&n.compareTo(BigDecimal.ONE)<=0,"百分比必须在 0–100% 范围内");return n;}
    public static LocalDate date(String value){
        if(value==null||value.isBlank())throw new IllegalArgumentException("缺少业务日期");String v=value.trim();
        for(String pattern:List.of("yyyy-MM-dd","yyyy/M/d","yyyy年M月d日","yyyyMMdd","dd/MM/yyyy"))try{return LocalDate.parse(v,DateTimeFormatter.ofPattern(pattern));}catch(Exception ignored){}
        try{return OffsetDateTime.parse(v).toLocalDate();}catch(Exception ignored){}
        if(v.length()>10)try{return date(v.substring(0,10));}catch(Exception ignored){}
        try{double serial=Double.parseDouble(v);if(serial>20000&&serial<100000)return org.apache.poi.ss.usermodel.DateUtil.getLocalDateTime(serial).toLocalDate();}catch(Exception ignored){}
        throw new IllegalArgumentException("日期格式无效");
    }
    public static Optional<DateRange> dateRange(String value){
        String v=Objects.toString(value,"");
        var matcher=java.util.regex.Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})\\s*[~～-]\\s*(\\d{1,2}/\\d{1,2}/\\d{4})").matcher(v);
        if(!matcher.find())return Optional.empty();
        LocalDate from=date(matcher.group(1)),to=date(matcher.group(2));
        Api.require(!from.isAfter(to),"业务日期范围无效");
        return Optional.of(new DateRange(from,to));
    }
    public static OffsetDateTime dateTime(String value){
        String v=Objects.toString(value,"").trim();
        try{return OffsetDateTime.parse(v);}catch(Exception ignored){}
        for(String pattern:List.of("dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss"))try{return LocalDateTime.parse(v,DateTimeFormatter.ofPattern(pattern)).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();}catch(Exception ignored){}
        throw new IllegalArgumentException("日期时间格式无效");
    }
    public static String status(String raw){
        return switch(normalize(raw)){
            case "unpaid","awaitingpayment","未付款","待付款","未支付" -> "UNPAID";
            case "paid","awaitingshipment","toship","待发货","已付款" -> "PAID";
            case "shipped","intransit","已发货","运输中" -> "SHIPPED";
            case "completed","delivered","已完成","已送达" -> "COMPLETED";
            case "cancelled","canceled","已取消" -> "CANCELLED";
            case "refunded","已退款" -> "REFUNDED";
            default -> "UNKNOWN";
        };
    }
}
