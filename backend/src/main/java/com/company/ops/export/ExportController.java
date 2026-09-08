package com.company.ops.export;

import com.company.ops.common.*;
import com.company.ops.dashboard.DashboardService;
import static com.company.ops.common.Db.p;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/v1/exports")
public class ExportController {
    private final Db db;private final DashboardService dashboard;private final TransactionTemplate tx;
    @Value("${app.export-max-rows}")int maxRows;@Value("${app.file-root}")String root;
    public ExportController(Db db,DashboardService dashboard,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.dashboard=dashboard;tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);}
    @GetMapping("/{type}")public void export(@PathVariable String type,@RequestParam String marketCode,@RequestParam String dateFrom,@RequestParam String dateTo,@RequestParam(required=false)Long shopId,HttpServletRequest req,HttpServletResponse res)throws Exception{
        req.setAttribute("auditAction","EXPORT");req.setAttribute("auditSummary",p("type",type,"marketCode",marketCode,"dateFrom",dateFrom,"dateTo",dateTo,"shopId",shopId));
        String table=switch(type){case "orders"->"fact_order";case "products"->"fact_product_daily";case "ads"->"fact_ad_campaign_daily";default->throw new Api.Problem(404,"NOT_FOUND","导出类型不存在");};
        var scope=dashboard.scope(marketCode,dateFrom,dateTo,shopId,"custom");
        String where=DashboardService.WHERE;
        if(type.equals("products")&&db.count("select count(*) from fact_product_period where market_code=#{p.market} and date_from=#{p.from} and date_to=#{p.to} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})",scope.params())>0){
            table="fact_product_period";where=" where market_code=#{p.market} and date_from=#{p.from} and date_to=#{p.to} and (cast(#{p.shop} as bigint) is null or shop_id=#{p.shop})";
        }
        String filename=type+"_"+marketCode+"_"+dateFrom+"_"+dateTo+"_"+System.currentTimeMillis()+".xlsx";Path folder=Path.of(root,"export",LocalDate.now().toString().substring(0,7));Files.createDirectories(folder);Path path=folder.resolve(UUID.randomUUID()+".xlsx");
        try(var book=new SXSSFWorkbook(100)){
            book.setCompressTempFiles(true);var sheet=book.createSheet("明细");
            final String exportTable=table,exportWhere=where;
            tx.executeWithoutResult(s->{var args=scope.params();long count=db.count("select count(*) from "+exportTable+exportWhere,args);if(count>maxRows)throw new Api.Problem(400,"EXPORT_TOO_LARGE","数据超过同步导出上限，请缩小日期范围");int rowIndex=0;long last=0;List<String> columns=null;
                while(true){args.put("last",last);var rows=db.rows("select * from "+exportTable+exportWhere+" and id>#{p.last} order by id limit 1000",args);if(rows.isEmpty())break;
                    if(columns==null){columns=new ArrayList<>(rows.getFirst().keySet());columns.removeAll(List.of("rawExtra","createdAt","updatedAt"));var header=sheet.createRow(rowIndex++);for(int i=0;i<columns.size();i++){header.createCell(i).setCellValue(columns.get(i));sheet.setColumnWidth(i,22*256);}}
                    for(var row:rows){var line=sheet.createRow(rowIndex++);for(int i=0;i<columns.size();i++){Object value=row.get(columns.get(i));var cell=line.createCell(i);if(value instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(Objects.toString(value,""));}last=Long.parseLong(row.get("id").toString());}
                }
                sheet.createFreezePane(0,1);req.setAttribute("auditSummary",p("filters",scope.params(),"rows",count,"filename",filename));
            });
            try(var out=Files.newOutputStream(path)){book.write(out);}finally{book.dispose();}
        }catch(Exception e){Files.deleteIfExists(path);throw e;}
        res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");res.setHeader("Content-Disposition","attachment; filename*=UTF-8''"+URLEncoder.encode(filename,StandardCharsets.UTF_8).replace("+","%20"));res.setContentLengthLong(Files.size(path));Files.copy(path,res.getOutputStream());
    }
}
