package com.company.ops.dashboard;

import com.company.ops.common.Db;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProductAdMetricsService {
    private final Db db;

    public ProductAdMetricsService(Db db) { this.db = db; }

    public List<Map<String, Object>> metrics(DashboardService.Scope scope) {
        return db.rows("""
            with ads as (
              select product_id,
                     sum(spend) ad_spend,
                     sum(attributed_revenue) ad_revenue,
                     sum(attributed_order_count) orders
              from fact_ad_product_daily
              where market_code=#{p.market}
                and biz_date between #{p.from} and #{p.to}
                and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})
              group by product_id
            ), mappings as (
              select product_sku_mapping.product_id,
                     string_agg(product_sku_mapping.display_name || '(' || product_sku_mapping.seller_sku || ')', ' / ' order by product_sku_mapping.sort_order, product_sku_mapping.id) display_name,
                     min(product_sku_mapping.sort_order) sort_order,
                     min(product_sku_mapping.id) mapping_id
              from product_sku_mapping
              join dim_shop s on s.id=product_sku_mapping.shop_id
              where s.market_code=#{p.market}
                and (cast(#{p.shop} as bigint) is null or product_sku_mapping.shop_id=#{p.shop})
                and product_sku_mapping.enabled
              group by product_id
            )
            select m.product_id,
                   m.display_name,
                   coalesce(a.ad_spend,0) ad_spend,
                   coalesce(a.ad_revenue,0) ad_revenue,
                   case when coalesce(a.ad_spend,0)=0 then 0 else a.ad_revenue/a.ad_spend end roi,
                   case when coalesce(a.orders,0)=0 then 0 else a.ad_spend/a.orders end cpo,
                   coalesce(a.orders,0) orders
            from mappings m
            join ads a on a.product_id=m.product_id
            order by m.sort_order,m.mapping_id
            """, scope.params());
    }
}
