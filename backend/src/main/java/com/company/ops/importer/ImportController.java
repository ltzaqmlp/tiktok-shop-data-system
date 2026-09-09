package com.company.ops.importer;

import com.company.ops.common.*;
import com.company.ops.auth.Identity;
import com.company.ops.admin.AdminController;
import static com.company.ops.common.Db.p;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/v1/imports")
public class ImportController {
    private final Db db;private final ImportService service;
    public ImportController(Db db,ImportService service){this.db=db;this.service=service;}
    @PostMapping public Object upload(@RequestParam String sourceType,@RequestParam String marketCode,@RequestParam long shopId,@RequestParam(required=false)String bizDate,@RequestParam("file")List<MultipartFile> files,@RequestParam(defaultValue="false")boolean force,HttpServletRequest req)throws Exception{
        Api.require(!files.isEmpty()&&("PRODUCT_DAILY".equals(sourceType)||files.size()<=10),"除商品数据外每批最多导入 10 个文件");Api.require(files.stream().mapToLong(MultipartFile::getSize).sum()<=500L*1024*1024,"批量文件总大小不能超过 500 MB");
        req.setAttribute("auditAction","IMPORT_START");req.setAttribute("auditSummary",p("sourceType",sourceType,"marketCode",marketCode,"shopId",shopId,"fileCount",files.size(),"filenames",files.stream().map(MultipartFile::getOriginalFilename).toList()));
        if(force&&!Identity.role(Identity.actor(req),"ADMIN"))throw new Api.Problem(403,"AUTH_FORBIDDEN","仅管理员可以强制重跑");
        if(Identity.role(Identity.actor(req),"ADS_BUYER")&&!Identity.role(Identity.actor(req),"ADMIN")&&!sourceType.equals("GMV_MAX_CAMPAIGN"))throw new Api.Problem(403,"AUTH_FORBIDDEN","投流角色仅能导入广告数据");
        var ids=new ArrayList<String>();for(var file:files)ids.add(service.submit(sourceType,marketCode,shopId,bizDate,file,Identity.uid(req),force,files.size()>1));req.setAttribute("targetId",ids.size()==1?ids.getFirst():"batch:"+ids.size());return Api.ok(req,p("ids",ids,"id",ids.getFirst()));
    }
    @GetMapping public Object list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(defaultValue="")String sourceType,@RequestParam(defaultValue="")String status,HttpServletRequest req){var args=AdminController.paging(page,pageSize);args.put("source",sourceType);args.put("status",status);String where=" where (#{p.source}='' or t.source_type=#{p.source}) and (#{p.status}='' or t.status=#{p.status})";return Api.ok(req,p("items",db.rows("select t.*,s.shop_name from sys_import_task t join dim_shop s on s.id=t.shop_id"+where+" order by t.id desc limit #{p.limit} offset #{p.offset}",args),"total",db.count("select count(*) from sys_import_task t"+where,args)));}
    @GetMapping("/{id}")public Object detail(@PathVariable long id,HttpServletRequest req){var row=db.one("select t.*,s.shop_name from sys_import_task t join dim_shop s on s.id=t.shop_id where t.id=#{p.id}",p("id",id));if(row.isEmpty())throw new Api.Problem(404,"NOT_FOUND","导入任务不存在");row.remove("storedPath");return Api.ok(req,row);}
    @GetMapping("/{id}/errors")public Object errors(@PathVariable long id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,HttpServletRequest req){var args=AdminController.paging(page,pageSize);args.put("id",id);return Api.ok(req,p("items",db.rows("select * from sys_import_error where task_id=#{p.id} order by id limit #{p.limit} offset #{p.offset}",args),"total",db.count("select count(*) from sys_import_error where task_id=#{p.id}",args)));}
    @PostMapping("/{id}/reaggregate")public Object reaggregate(@PathVariable long id,HttpServletRequest req){service.reaggregate(id);req.setAttribute("auditAction","IMPORT_REAGGREGATE");return Api.ok(req,p("id",id));}
}
