package com.company.ops.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.DateTimeException;
import java.util.*;

public class Api {
    public record Envelope(boolean success,String requestId,Object data,Object error) {}
    public static Envelope ok(HttpServletRequest req,Object data) { return new Envelope(true,id(req),data,null); }
    public static String id(HttpServletRequest req) { Object id=req.getAttribute("requestId");return id==null?UUID.randomUUID().toString():id.toString(); }
    public static class Problem extends RuntimeException {
        public final int status; public final String code;
        public Problem(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
    public static void require(boolean pass,String message){if(!pass)throw new Problem(400,"VALIDATION_ERROR",message);}
    public static String text(Map<String,Object> map,String key,int max,boolean required){
        String s=Objects.toString(map.get(key),"").trim();require(!required||!s.isEmpty(),key+" 不能为空");require(s.length()<=max,key+" 超过长度限制");return s;
    }
    public static long idValue(Object raw) { try{long v=Long.parseLong(Objects.toString(raw,""));require(v>0,"ID 无效");return v;}catch(NumberFormatException e){throw new Problem(400,"VALIDATION_ERROR","ID 无效");} }
    @RestControllerAdvice
    public static class Errors {
        @ExceptionHandler(Exception.class)
        ResponseEntity<Envelope> handle(Exception error,HttpServletRequest req){
            int status=500;String code="SYSTEM_INTERNAL_ERROR",message="服务处理失败，请联系管理员并提供请求编号";
            if(error instanceof Problem p){status=p.status;code=p.code;message=p.getMessage();}
            else if(error instanceof MaxUploadSizeExceededException){status=413;code="IMPORT_FILE_TOO_LARGE";message="文件不能超过 100 MB";}
            else if(error instanceof DataIntegrityViolationException){status=409;code="DATA_CONFLICT";message="数据已存在或仍被使用，请检查后重试";}
            else if(error instanceof IllegalArgumentException || error instanceof DateTimeException || error instanceof ServletRequestBindingException || error instanceof MethodArgumentTypeMismatchException || error instanceof MethodArgumentNotValidException || error instanceof org.springframework.http.converter.HttpMessageNotReadableException){status=400;code="VALIDATION_ERROR";message="请求字段或格式无效";}
            if(status==500) org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request {} failed: {} at {}",id(req),error.getClass().getName(),Arrays.toString(error.getStackTrace()));
            return ResponseEntity.status(status).body(new Envelope(false,id(req),null,Map.of("code",code,"message",message)));
        }
    }
}
