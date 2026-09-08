package com.company.ops.dashboard;

import com.company.ops.common.Api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service){this.service=service;}
    @GetMapping("/{section}") public Object get(@PathVariable String section,@RequestParam String marketCode,@RequestParam String dateFrom,@RequestParam String dateTo,@RequestParam(required=false)Long shopId,@RequestParam(defaultValue="custom")String comparisonPeriod,HttpServletRequest req){
        var scope=service.scope(marketCode,dateFrom,dateTo,shopId,comparisonPeriod);
        Object data=switch(section){case "overview"->service.overview(scope);case "sales-trend"->service.daily(scope);case "product-funnel"->service.funnel(scope);case "ads"->service.ads(scope);case "order-status"->service.statuses(scope);case "after-sales"->service.afterSales(scope);default->throw new Api.Problem(404,"NOT_FOUND","接口不存在");};return Api.ok(req,data);
    }
}
