package com.company.ops.importer;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.company.ops.common.*;
import com.company.ops.dashboard.DashboardService;
import static com.company.ops.common.Db.p;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ImportService {
    private final Db db;
    private final DashboardService dashboard;
    private final TransactionTemplate tx;
    private final Map<String,String> aliases=new HashMap<>();
    // V1 uses a single import worker, matching daily manual batches. Use a DB-backed
    // queue before running multiple application instances.
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Value("${app.file-root}") String fileRoot;

    public ImportService(Db db,DashboardService dashboard,org.springframework.transaction.PlatformTransactionManager manager,ObjectMapper json)throws IOException{
        this.db=db;this.dashboard=dashboard;tx=new TransactionTemplate(manager);
        var map=json.readValue(getClass().getResourceAsStream("/import-aliases.json"),new TypeReference<Map<String,List<String>>>(){});
        map.forEach((key,values)->{aliases.put(ImportMapping.normalize(key),key);values.forEach(v->aliases.put(ImportMapping.normalize(v),key));});
    }
    @PreDestroy void stop(){worker.shutdown();}

    public String submit(String source,String market,long shop,String bizDate,MultipartFile file,long user,boolean force)throws Exception{
        return submit(source,market,shop,bizDate,file,user,force,false);
    }
    public String submit(String source,String market,long shop,String bizDate,MultipartFile file,long user,boolean force,boolean batch)throws Exception{
        Api.require(ImportMapping.TABLES.containsKey(source),"数据源不支持，退款明细尚未启用");
        String filename=Objects.toString(file.getOriginalFilename(),"");
        Api.require(filename.length()<=255&&filename.toLowerCase().matches(".*\\.(xlsx|xls)$"),"只接受 XLSX / XLS 文件");
        Api.require(file.getSize()>0&&file.getSize()<=100L*1024*1024,"文件为空或超过 100 MB");
        String mime=Objects.toString(file.getContentType(),"");
        Api.require(Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","application/octet-stream","").contains(mime),"文件类型无效");
        var context=db.one("select s.id,s.shop_key,m.currency_code from dim_shop s join dim_market m on m.market_code=s.market_code where s.id=#{p.shop} and s.market_code=#{p.market} and s.enabled and m.enabled",p("shop",shop,"market",market));
        Api.require(!context.isEmpty(),"店铺与市场不匹配或已停用");
        LocalDate date=bizDate==null||bizDate.isBlank()?null:ImportMapping.date(bizDate);
        Path folder=Path.of(fileRoot,"raw",source,LocalDate.now().toString().substring(0,7)).toAbsolutePath().normalize();
        Files.createDirectories(folder);
        Path stored=folder.resolve(UUID.randomUUID()+(filename.toLowerCase().endsWith(".xlsx")?".xlsx":".xls"));
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var in=new DigestInputStream(file.getInputStream(),digest)){Files.copy(in,stored);}
        String hash=HexFormat.of().formatHex(digest.digest());
        try{
            try(var in=Files.newInputStream(stored)){
                byte[] head=in.readNBytes(8);boolean zip=head.length>=4&&head[0]=='P'&&head[1]=='K';boolean ole=head.length==8&&head[0]==(byte)0xD0&&head[1]==(byte)0xCF;
                Api.require(zip||ole,"文件内容不是 Excel 工作簿");
            }
            if(stored.toString().endsWith("xlsx"))try(var zip=new java.util.zip.ZipFile(stored.toFile())){
                Api.require(zip.stream().noneMatch(e->e.getName().toLowerCase().contains("vbaproject")),"拒绝包含宏的工作簿");
            }
            String id=tx.execute(s->{
                db.rows("select pg_advisory_xact_lock(#{p.shop})",p("shop",shop));
                if(!batch&&db.count("select count(*) from sys_import_task where shop_id=#{p.shop} and source_type=#{p.source} and status in ('CREATED','VALIDATING','IMPORTING','AGGREGATING')",p("shop",shop,"source",source))>0)
                    throw new Api.Problem(409,"IMPORT_CONCURRENT_TASK","此店铺数据源已有导入任务，请等待完成");
                if(!force&&db.count("select count(*) from sys_import_task where shop_id=#{p.shop} and source_type=#{p.source} and file_hash=#{p.hash} and status in ('SUCCESS','AGGREGATE_FAILED')",p("shop",shop,"source",source,"hash",hash))>0)
                    throw new Api.Problem(409,"IMPORT_FILE_DUPLICATE","文件已导入，请查看历史或由管理员重跑");
                return db.insert("insert into sys_import_task(source_type,market_code,shop_id,biz_date_from,biz_date_to,original_filename,stored_path,file_hash,status,created_by) values(#{p.source},#{p.market},#{p.shop},#{p.date},#{p.date},#{p.filename},#{p.path},#{p.hash},'CREATED',#{p.user})",p("source",source,"market",market,"shop",shop,"date",date,"filename",filename,"path",stored.toString(),"hash",hash,"user",user));
            });
            worker.submit(()->run(Long.parseLong(id),source,market,shop,date,stored,context,user));
            return id;
        }catch(Exception e){Files.deleteIfExists(stored);throw e;}
    }

    private void run(long id,String source,String market,long shop,LocalDate chosen,Path file,Map<String,Object> context,long user){
        var listener=new Reader(id,source,market,shop,chosen,context);String finalStatus="FAILED",finalMessage=null;
        try{
            db.exec("update sys_import_task set status='VALIDATING',started_at=now() where id=#{p.id}",p("id",id));
            db.exec("update sys_import_task set status='IMPORTING' where id=#{p.id}",p("id",id));
            tx.executeWithoutResult(s->{
                if(source.equals("ORDER_DETAIL"))readOrder(file,listener);
                else if(source.equals("PRODUCT_DAILY"))readProduct(file,listener);
                else EasyExcel.read(file.toFile(),listener).headRowNumber(0).sheet().doRead();
                Api.require(listener.header!=null,"缺少关键表头："+String.join(", ",ImportMapping.REQUIRED.get(source)));
                Api.require(listener.count>0,"工作簿没有数据行");
                if(listener.failed>0)throw new Api.Problem(400,"IMPORT_ROW_INVALID","行数据校验失败，整批数据未写入");
                listener.flush();
            });
            Api.require(!listener.dates.isEmpty(),"无法确定工作簿业务日期范围");
            db.exec("update sys_import_task set status='AGGREGATING',total_rows=#{p.rows},success_rows=#{p.rows},biz_date_from=#{p.from},biz_date_to=#{p.to},header_signature=#{p.signature} where id=#{p.id}",p("id",id,"rows",listener.count,"from",listener.dates.first(),"to",listener.dates.last(),"signature",listener.signature));
            if(listener.affectsDailyAggregation()){
                try{dashboard.reaggregate(shop,market,listener.dates);finalStatus="SUCCESS";}
                catch(Exception e){finalStatus="AGGREGATE_FAILED";finalMessage="事实数据已保存，聚合失败，请执行重算";}
            }else finalStatus="SUCCESS";
        }catch(Exception e){
            String message=e instanceof Api.Problem?e.getMessage():"工作簿解析失败，请检查表头、日期与文件格式";
            db.exec("update sys_import_task set total_rows=#{p.rows},failed_rows=#{p.failed},success_rows=0 where id=#{p.id}",p("id",id,"rows",listener.count,"failed",listener.failed));
            finalMessage=message;
        }
        if(source.equals("ORDER_DETAIL")&&!"FAILED".equals(finalStatus)){
            for(var unmapped:db.rows("select o.seller_sku,sum(greatest(o.quantity-least(o.return_quantity,o.quantity),0)) effective_qty from fact_order_sku o left join sku_config c on c.shop_id=o.shop_id and c.seller_sku=o.seller_sku and c.enabled where o.shop_id=#{p.shop} and c.id is null and o.normalized_status in ('PAID','SHIPPED','COMPLETED') group by o.seller_sku having sum(greatest(o.quantity-least(o.return_quantity,o.quantity),0))>0 order by o.seller_sku",p("shop",shop))){
                String seller=Objects.toString(unmapped.get("sellerSku"),"");listener.errors.add(p("row",0,"field","Seller SKU","raw",seller,"code","SKU_UNMAPPED","message",(seller.isBlank()?"Seller SKU为空":"Seller SKU未配置")+"，有效销量 "+unmapped.get("effectiveQty")+"，请在 SKU 配置中补充映射"));
            }
        }
        for(var error:listener.errors){
            error.put("task",id);
            db.exec("insert into sys_import_error(task_id,row_no,field_name,raw_value,error_code,message) values(#{p.task},#{p.row},#{p.field},#{p.raw},#{p.code},#{p.message})",error);
        }
        finish(id,finalStatus,finalMessage);
        var task=db.one("select status,error_message from sys_import_task where id=#{p.id}",p("id",id));
        db.exec("insert into sys_audit_log(user_id,module,action,target_id,request_id,success,request_summary) values(#{p.user},'import',#{p.action},#{p.target},#{p.request},#{p.success},cast(#{p.summary} as jsonb))",p("user",user,"action","SUCCESS".equals(task.get("status"))?"IMPORT_FINISH":"IMPORT_FAIL","target",String.valueOf(id),"request",UUID.randomUUID().toString(),"success","SUCCESS".equals(task.get("status")),"summary",db.json(task)));
    }
    private void finish(long id,String status,String message){db.exec("update sys_import_task set status=#{p.status},error_message=#{p.message},finished_at=now() where id=#{p.id}",p("id",id,"status",status,"message",message));}

    // TikTok's order export may repeat XML row nodes; the streaming fallback merges
    // fragments without changing the source file.
    private void readOrder(Path file,Reader listener){
        if(file.toString().endsWith(".xlsx")&&hasSplitRows(file)){readBrokenOrderXml(file,listener);return;}
        readPoi(file,listener,"订单工作簿无法读取");
    }
    private void readProduct(Path file,Reader listener){
        // TikTok product exports can declare dimension=A1 even when rows 3+ contain the report.
        readPoi(file,listener,"商品工作簿无法读取");
    }
    private void readPoi(Path file,Reader listener,String errorMessage){
        try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(file.toFile())){
            var formatter=new org.apache.poi.ss.usermodel.DataFormatter(Locale.ROOT);var row=new LinkedHashMap<Integer,String>();int rowNo=-1;
            for(var sourceRow:book.getSheetAt(0)){
                if(rowNo!=-1&&rowNo!=sourceRow.getRowNum()){listener.process(row,rowNo+1);row=new LinkedHashMap<>();}
                rowNo=sourceRow.getRowNum();for(var cell:sourceRow)row.put(cell.getColumnIndex(),formatter.formatCellValue(cell));
            }
            if(rowNo!=-1)listener.process(row,rowNo+1);
        }catch(IOException e){throw new Api.Problem(400,"IMPORT_FILE_INVALID",errorMessage);}
    }
    private boolean hasSplitRows(Path file){
        try(var zip=new java.util.zip.ZipFile(file.toFile())){
            var entry=zip.stream().filter(e->e.getName().startsWith("xl/worksheets/")&&e.getName().endsWith(".xml")).findFirst().orElseThrow();
            String head=new String(zip.getInputStream(entry).readNBytes(65536),java.nio.charset.StandardCharsets.UTF_8);int first=head.indexOf("<row r=\"1\"");return first>=0&&head.indexOf("<row r=\"1\"",first+1)>=0;
        }catch(Exception e){return false;}
    }
    private void readBrokenOrderXml(Path file,Reader listener){
        try(var zip=new java.util.zip.ZipFile(file.toFile())){
            var entry=zip.stream().filter(e->e.getName().startsWith("xl/worksheets/")&&e.getName().endsWith(".xml")).findFirst().orElseThrow();
            var factory=javax.xml.stream.XMLInputFactory.newFactory();factory.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
            var xml=factory.createXMLStreamReader(zip.getInputStream(entry));var row=new LinkedHashMap<Integer,String>();int rowNo=-1,column=-1;StringBuilder value=null;boolean text=false;
            while(xml.hasNext()){
                int event=xml.next();String name=xml.hasName()?xml.getLocalName():"";
                if(event==javax.xml.stream.XMLStreamConstants.START_ELEMENT&&name.equals("row")){
                    int next=Integer.parseInt(xml.getAttributeValue(null,"r"));if(rowNo!=-1&&rowNo!=next){listener.process(row,rowNo);row=new LinkedHashMap<>();}rowNo=next;
                }else if(event==javax.xml.stream.XMLStreamConstants.START_ELEMENT&&name.equals("c")){
                    String ref=xml.getAttributeValue(null,"r");column=0;for(int i=0;i<ref.length()&&Character.isLetter(ref.charAt(i));i++)column=column*26+Character.toUpperCase(ref.charAt(i))-'A'+1;column--;value=new StringBuilder();
                }else if(event==javax.xml.stream.XMLStreamConstants.START_ELEMENT&&(name.equals("v")||name.equals("t")))text=true;
                else if(event==javax.xml.stream.XMLStreamConstants.CHARACTERS&&text&&value!=null)value.append(xml.getText());
                else if(event==javax.xml.stream.XMLStreamConstants.END_ELEMENT&&(name.equals("v")||name.equals("t")))text=false;
                else if(event==javax.xml.stream.XMLStreamConstants.END_ELEMENT&&name.equals("c")){row.put(column,value==null?"":value.toString());column=-1;value=null;}
            }
            if(rowNo!=-1)listener.process(row,rowNo);
        }catch(Api.Problem e){throw e;}catch(Exception e){throw new Api.Problem(400,"IMPORT_FILE_INVALID","订单工作簿结构无效");}
    }

    public void reaggregate(long id){
        var task=db.one("select * from sys_import_task where id=#{p.id}",p("id",id));Api.require("AGGREGATE_FAILED".equals(task.get("status")),"仅聚合失败任务可以重算");
        var dates=new TreeSet<LocalDate>();LocalDate from=LocalDate.parse(task.get("bizDateFrom").toString()),to=LocalDate.parse(task.get("bizDateTo").toString());
        for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1))dates.add(d);
        dashboard.reaggregate(Long.parseLong(task.get("shopId").toString()),task.get("marketCode").toString(),dates);finish(id,"SUCCESS",null);
    }

    private class Reader extends AnalysisEventListener<Map<Integer,String>> {
        final long task,shop;final String source,market;final LocalDate chosen;final Map<String,Object> context;
        Map<Integer,String> header;String signature;int count,failed;String currentField="";String currentRaw="";
        ImportMapping.DateRange productRange;
        final LinkedHashMap<String,Map<String,Object>> batch=new LinkedHashMap<>();
        final LinkedHashMap<String,Map<String,Object>> skuBatch=new LinkedHashMap<>();
        final List<Map<String,Object>> errors=new ArrayList<>();final TreeSet<LocalDate> dates=new TreeSet<>();Set<String> bestFields=Set.of();
        boolean snapshotReset;
        Reader(long task,String source,String market,long shop,LocalDate chosen,Map<String,Object> context){this.task=task;this.source=source;this.market=market;this.shop=shop;this.chosen=chosen;this.context=context;}
        @Override public void invoke(Map<Integer,String> row,AnalysisContext ctx){process(row,ctx.readRowHolder().getRowIndex()+1);}

        boolean isProductPeriod(){return source.equals("PRODUCT_DAILY")&&productRange!=null&&!productRange.from().equals(productRange.to());}
        boolean affectsDailyAggregation(){return !isProductPeriod();}
        String targetTable(){return isProductPeriod()?"fact_product_period":ImportMapping.TABLES.get(source);}
        String keyColumns(){return isProductPeriod()?"shop_id,date_from,date_to,product_id":ImportMapping.KEYS.get(source);}

        void captureProductRange(Map<Integer,String> row){
            if(!source.equals("PRODUCT_DAILY"))return;
            String text=row.values().stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(" "));
            var found=ImportMapping.dateRange(text);
            if(found.isEmpty())return;
            var range=found.get();
            if(productRange!=null&&!productRange.equals(range))throw new Api.Problem(400,"IMPORT_DATE_RANGE_AMBIGUOUS","工作簿中存在多个不同的商品数据日期范围");
            productRange=range;
            // Product exports may carry their own multi-day range. The range in the file is authoritative.
            // A manually selected date is only validated for true single-day exports; for historical
            // multi-day files it is ignored so old exports can be bulk-imported without re-exporting.
            if(chosen!=null&&range.from().equals(range.to())&&!chosen.equals(range.from()))
                throw new Api.Problem(400,"IMPORT_DATE_MISMATCH","文件业务日期与选择日期不一致");
            for(LocalDate d=range.from();!d.isAfter(range.to());d=d.plusDays(1))dates.add(d);
        }

        void process(Map<Integer,String> row,int rowNo){
            if(header==null){
                captureProductRange(row);
                var candidate=new LinkedHashMap<Integer,String>();
                row.forEach((i,v)->{String key=aliases.get(ImportMapping.normalize(v));if(key!=null)candidate.put(i,key);});
                if(candidate.size()>bestFields.size())bestFields=new HashSet<>(candidate.values());
                if(candidate.values().containsAll(ImportMapping.REQUIRED.get(source))){
                    header=candidate;
                    try{signature=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("|",new TreeSet<>(candidate.values())).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
                    catch(Exception e){throw new IllegalStateException(e);}
                    return;
                }
                if(rowNo>=30)throw new Api.Problem(400,"IMPORT_HEADER_MISSING","缺少关键表头："+ImportMapping.REQUIRED.get(source).stream().filter(k->!bestFields.contains(k)).collect(java.util.stream.Collectors.joining(", ")));
                return;
            }
            if(row.values().stream().allMatch(v->v==null||v.isBlank()))return;
            count++;
            var raw=new LinkedHashMap<String,String>();header.forEach((i,k)->raw.putIfAbsent(k,Objects.toString(row.get(i),"")));
            if(source.equals("GMV_MAX_CAMPAIGN")&&raw.getOrDefault("biz_date","").trim().equals("-")){count--;return;}
            if(source.equals("ORDER_DETAIL")&&raw.getOrDefault("order_id","").startsWith("Platform unique order ID")){count--;return;}
            try{
                var values=new LinkedHashMap<String,Object>();
                LocalDate date=null;
                currentField="biz_date";currentRaw=raw.getOrDefault("biz_date","");if(source.equals("ORDER_DETAIL")){currentRaw=raw.getOrDefault("paid_time","").trim();if(currentRaw.isBlank())currentRaw=raw.getOrDefault("order_created_at","");}
                if(source.equals("PRODUCT_DAILY")&&productRange!=null){
                    if(!isProductPeriod())date=productRange.from();
                }else{
                    date=currentRaw.isBlank()?chosen:ImportMapping.date(currentRaw);
                    Api.require(date!=null,"缺少业务日期，请选择业务日期或提供日期列");
                    if(chosen!=null&&!chosen.equals(date))throw new Api.Problem(400,"IMPORT_DATE_MISMATCH","文件业务日期与选择日期不一致");
                }
                if(raw.containsKey("shop_id")&&!raw.get("shop_id").isBlank()&&!raw.get("shop_id").equals(context.get("shopKey"))&&!raw.get("shop_id").equals(String.valueOf(shop)))throw new Api.Problem(400,"IMPORT_SHOP_MISMATCH","文件店铺与选择店铺不一致");
                if(raw.containsKey("market_code")&&!raw.get("market_code").isBlank()&&!raw.get("market_code").equalsIgnoreCase(market))throw new Api.Problem(400,"IMPORT_SHOP_MISMATCH","文件市场与选择市场不一致");
                String currency=raw.getOrDefault("currency_code","").trim().toUpperCase(Locale.ROOT);if(currency.isBlank())currency=context.get("currencyCode").toString();if(currency.equals("RM"))currency="MYR";
                Api.require(currency.matches("[A-Z]{3}"),"币种代码无效");if(!source.equals("GMV_MAX_CAMPAIGN"))Api.require(currency.equals(context.get("currencyCode")),"文件币种与市场币种不一致");
                values.putAll(p("shop_id",shop,"market_code",market,"currency_code",currency));
                if(isProductPeriod()){values.put("date_from",productRange.from());values.put("date_to",productRange.to());}
                else values.put("biz_date",date);

                String columns=switch(source){
                    case "SHOP_ANALYTICS" -> "gmv,order_count,sold_qty,sku_order_count,refund_amount,customer_count,page_view_count,visitor_count,conversion_rate_src,impressions,clicks,unique_impressions,unique_clicks";
                    case "PRODUCT_DAILY" -> "product_id,gmv,order_count,sku_order_count,sold_qty,estimated_customer_count,impressions,clicks,add_to_cart_count,refund_amount,refunded_qty,refund_customer_count,unique_impressions,unique_clicks,added_user_count";
                    case "ORDER_DETAIL" -> "order_id,source_status,order_amount,item_qty,returned_qty,order_refund_amount";
                    default -> "ad_account_key,campaign_id,campaign_name,spend,attributed_revenue,attributed_order_count,impressions,clicks";
                };
                Set<String> strings=Set.of("product_id","order_id","source_status","ad_account_key","campaign_id","campaign_name");
                Set<String> money=Set.of("gmv","refund_amount","order_amount","order_refund_amount","spend","attributed_revenue");
                Set<String> nullableNumbers=Set.of("visitor_count","customer_count","page_view_count","sku_order_count","returned_qty","order_refund_amount","impressions","clicks","unique_impressions","unique_clicks","added_user_count");
                for(String field:columns.split(",")){
                    currentField=field;currentRaw=raw.getOrDefault(field,"");
                    if(ImportMapping.REQUIRED.get(source).contains(field))Api.require(!currentRaw.isBlank(),field+" 不能为空");
                    if(strings.contains(field)){
                        String value=currentRaw.trim();if(field.equals("ad_account_key")&&value.isBlank())value="default";if(field.equals("campaign_id")&&value.isBlank())value="overview";
                        if(ImportMapping.REQUIRED.get(source).contains(field))Api.require(!value.isEmpty(),field+" 不能为空");
                        Api.require(value.length()<=(field.equals("product_id")?64:field.equals("campaign_name")?255:100),field+" 超过长度限制");
                        if(field.endsWith("_id"))Api.require(!value.matches("(?i)^[+-]?[0-9]+([.][0-9]+)?E[+-]?[0-9]+$"),"业务 ID 不能为科学计数法，请将源列保存为文本");
                        values.put(field,value);
                    }else if(field.endsWith("_src"))values.put(field,currentRaw.isBlank()?null:ImportMapping.ratio(currentRaw));
                    else if(currentRaw.isBlank()&&(nullableNumbers.contains(field)||(source.equals("SHOP_ANALYTICS")&&field.equals("refund_amount"))||(isProductPeriod()&&Set.of("refund_amount","refunded_qty","refund_customer_count").contains(field))))values.put(field,null);
                    else{
                        var num=ImportMapping.amount(currentRaw);
                        if(money.contains(field)){
                            num=num.setScale(2,java.math.RoundingMode.HALF_UP);Api.require(num.precision()<=18,"金额超出允许范围");values.put(field,num);
                        }else{Api.require(num.signum()>=0,"数量不能为负");values.put(field,num.longValueExact());}
                        }
                }
                if(source.equals("PRODUCT_DAILY"))validateProductRatio(raw,values,"ctr_src","clicks","impressions");
                if(source.equals("PRODUCT_DAILY"))validateProductRatio(raw,values,"add_to_cart_rate_src","add_to_cart_count","clicks");
                if(source.equals("PRODUCT_DAILY"))validateProductRatio(raw,values,"ctor_src","sku_order_count","clicks");
                if(source.equals("ORDER_DETAIL")){
                    long quantity=nonNegativeInt(raw.getOrDefault("quantity",raw.getOrDefault("item_qty","")));
                    long returned=returnQuantity(raw.getOrDefault("return_quantity",raw.getOrDefault("returned_qty","")),rowNo);
                    if(returned>quantity&&errors.size()<1000)errors.add(p("row",rowNo,"field","return_quantity","raw",String.valueOf(returned),"code","RETURN_QTY_GT_QUANTITY","message","退货数大于 Quantity，统计时按 Quantity 上限扣减"));
                    values.put("item_qty",quantity);values.put("returned_qty",returned);
                    String created=raw.getOrDefault("order_created_at","");if(!created.isBlank())values.put("order_created_at",ImportMapping.dateTime(created));
                    values.put("normalized_status",ImportMapping.status(raw.getOrDefault("source_status","")));
                    if("UNKNOWN".equals(values.get("normalized_status"))&&errors.size()<1000)errors.add(p("row",rowNo,"field","source_status","raw",raw.getOrDefault("source_status",""),"code","ORDER_STATUS_UNKNOWN","message","未知源状态，已映射为 UNKNOWN"));
                    Map<String,Object> sku=new LinkedHashMap<>(p("shop_id",shop,"market_code",market,"biz_date",date,"order_id",raw.getOrDefault("order_id","").trim(),"sku_id",raw.getOrDefault("sku_id","").trim(),"seller_sku",raw.getOrDefault("seller_sku","").trim(),"source_status",raw.getOrDefault("source_status","").trim(),"normalized_status",values.get("normalized_status"),"quantity",quantity,"return_quantity",returned,"raw_extra",db.json(row)));
                    String paid=raw.getOrDefault("paid_time","").trim(),createdTime=raw.getOrDefault("order_created_at","").trim();
                    sku.put("paid_time",paid.isBlank()?null:ImportMapping.dateTime(paid));
                    sku.put("created_time",createdTime.isBlank()?null:ImportMapping.dateTime(createdTime));
                    String cancel=raw.getOrDefault("cancel_type","").trim();sku.put("cancel_type",cancel.isBlank()?null:cancel);
                    sku.put("order_refund_amount",values.get("order_refund_amount"));
                    Api.require(((String)sku.get("sku_id")).length()<=100,"sku_id 超过长度限制");Api.require(((String)sku.get("seller_sku")).length()<=100,"seller_sku 超过长度限制");
                    String skuKey=sku.get("order_id")+"\u001f"+sku.get("sku_id");
                    if(skuBatch.put(skuKey,sku)!=null&&errors.size()<1000)errors.add(p("row",rowNo,"field","Order ID + SKU ID","raw",skuKey,"code","ORDER_SKU_DUPLICATE","message","同一 Order ID + SKU ID 重复，已保留最后一条"));
                }
                values.put("raw_extra",db.json(row));
                String keys=keyColumns();String key=Arrays.stream(keys.split(",")).map(k->Objects.toString(values.get(k),"")).collect(java.util.stream.Collectors.joining("\u001f"));
                if(!batch.containsKey(key)&&batch.size()>=500)flush();
                var previous=batch.put(key,values);
                if(source.equals("ORDER_DETAIL")&&previous!=null){
                    values.put("item_qty",asLong(previous.get("item_qty"))+asLong(values.get("item_qty")));
                    values.put("returned_qty",sumNullableLong(previous.get("returned_qty"),values.get("returned_qty")));
                    if(values.get("order_refund_amount")==null)values.put("order_refund_amount",previous.get("order_refund_amount"));
                }
                if(date!=null)dates.add(date);
            }catch(Exception e){
                failed++;if(errors.size()<1000)errors.add(p("row",rowNo,"field",currentField,"raw",currentRaw.length()>2000?currentRaw.substring(0,2000):currentRaw,"code",e instanceof Api.Problem problem?problem.code:"IMPORT_ROW_INVALID","message",e instanceof Api.Problem?e.getMessage():"字段数值、日期或格式无效"));
            }
        }
        long asLong(Object value){return value==null?0:((Number)value).longValue();}
        long nonNegativeInt(String raw){var n=ImportMapping.amount(raw);Api.require(n.signum()>=0&&n.scale()<=0,"数量必须是非负整数");return n.longValueExact();}
        long returnQuantity(String raw,int rowNo){var n=ImportMapping.amount(raw);Api.require(n.scale()<=0,"数量必须是整数");if(n.signum()<0){if(errors.size()<1000)errors.add(p("row",rowNo,"field","return_quantity","raw",raw,"code","RETURN_QTY_NEGATIVE","message","退货数为负，已按 0 处理"));return 0;}return n.longValueExact();}
        Long sumNullableLong(Object a,Object b){return a==null&&b==null?null:asLong(a)+asLong(b);}
        private void validateProductRatio(Map<String,String> raw,Map<String,Object> values,String field,String numerator,String denominator){
            String text=raw.getOrDefault(field,"").trim();if(text.isEmpty()||text.equals("-"))return;
            currentField=field;currentRaw=text;BigDecimal calculated=DashboardService.divide(values.get(numerator),values.get(denominator));if(calculated==null)return;
            Api.require(calculated.subtract(ImportMapping.ratio(text)).abs().compareTo(new BigDecimal("0.005"))<=0,field+" 与分子/分母计算值偏差超过 0.5 个百分点");
        }

        void flush(){
            if(batch.isEmpty()&&skuBatch.isEmpty())return;
            if(source.equals("ORDER_DETAIL")&&!snapshotReset){
                for(var old:db.rows("select distinct biz_date from fact_order where shop_id=#{p.shop}",p("shop",shop)))dates.add(LocalDate.parse(old.get("bizDate").toString()));
                db.exec("delete from fact_order_sku where shop_id=#{p.shop}",p("shop",shop));
                db.exec("delete from fact_order where shop_id=#{p.shop}",p("shop",shop));
                snapshotReset=true;
            }
            if(!batch.isEmpty()){
                List<String> names=new ArrayList<>(batch.values().iterator().next().keySet());var params=new LinkedHashMap<String,Object>();var tuples=new ArrayList<String>();int index=0;
                for(var row:batch.values()){
                    var binds=new ArrayList<String>();for(String col:names){String key="v"+index++;params.put(key,row.get(col));binds.add(col.equals("raw_extra")?"cast(#{p."+key+"} as jsonb)":"#{p."+key+"}");}tuples.add("("+String.join(",",binds)+")");
                }
                String keys=keyColumns();Set<String> keySet=new HashSet<>(Arrays.asList(keys.split(",")));
                String update=names.stream().filter(k->!keySet.contains(k)).map(k->k+"=excluded."+k).collect(java.util.stream.Collectors.joining(","));
                db.exec("insert into "+targetTable()+"("+String.join(",",names)+") values "+String.join(",",tuples)+" on conflict("+keys+") do update set "+update+",updated_at=now()",params);
            }
            if(!skuBatch.isEmpty()){
                var skuNames=new ArrayList<>(skuBatch.values().iterator().next().keySet());var skuParams=new LinkedHashMap<String,Object>();var skuTuples=new ArrayList<String>();int skuIndex=0;
                for(var row:skuBatch.values()){
                    var binds=new ArrayList<String>();for(String col:skuNames){String key="s"+skuIndex++;skuParams.put(key,row.get(col));binds.add(col.equals("raw_extra")?"cast(#{p."+key+"} as jsonb)":"#{p."+key+"}");}skuTuples.add("("+String.join(",",binds)+")");
                }
                var skuKeys="shop_id,order_id,sku_id";var skuUpdate=skuNames.stream().filter(k->!Set.of("shop_id","order_id","sku_id").contains(k)).map(k->k+"=excluded."+k).collect(java.util.stream.Collectors.joining(","));
                db.exec("insert into fact_order_sku("+String.join(",",skuNames)+") values "+String.join(",",skuTuples)+" on conflict("+skuKeys+") do update set "+skuUpdate+",updated_at=now()",skuParams);
            }
            batch.clear();
            skuBatch.clear();
        }
        @Override public void doAfterAllAnalysed(AnalysisContext context){}
    }
}
