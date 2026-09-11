package com.company.ops.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class Db {
    private static final Set<String> JSON_FIELDS=Set.of("rawExtra","requestSummary","beforeData","afterData","deliveryResults","editorTaskResults","directorTaskResults");
    private final SqlMapper mapper;
    private final ObjectMapper json;
    public Db(SqlMapper mapper, ObjectMapper json) { this.mapper=mapper; this.json=json; }
    public static Map<String,Object> p(Object... entries) {
        Map<String,Object> map=new LinkedHashMap<>();
        for(int i=0;i<entries.length;i+=2) map.put(entries[i].toString(),entries[i+1]);
        return map;
    }
    public List<Map<String,Object>> rows(String sql, Map<String,Object> p) { return mapper.select(sql,p).stream().map(this::camel).toList(); }
    public List<Map<String,Object>> rows(String sql) { return rows(sql,Map.of()); }
    public Map<String,Object> one(String sql, Map<String,Object> p) { var list=rows(sql,p); return list.isEmpty()?new LinkedHashMap<>():list.getFirst(); }
    public int exec(String sql, Map<String,Object> p) { return mapper.update(sql,p); }
    public String insert(String sql, Map<String,Object> p) { return mapper.returning(sql+" returning id",p).getFirst().get("id").toString(); }
    public long count(String sql, Map<String,Object> p) { return ((Number)one(sql,p).get("count")).longValue(); }
    public String json(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);} }
    private Map<String,Object> camel(Map<String,Object> source) {
        var out=new LinkedHashMap<String,Object>();
        if(source==null)return out;
        source.forEach((key,value)->{
            StringBuilder name=new StringBuilder(); boolean cap=false;
            for(char c:key.toCharArray()) { if(c=='_'){cap=true;continue;}name.append(cap?Character.toUpperCase(c):c);cap=false; }
            String k=name.toString(); Object v=value;
            if(value!=null && (k.equals("id")||k.endsWith("Id")||k.equals("createdBy"))) v=value.toString();
            else if(value instanceof java.sql.Timestamp t) v=t.toInstant().toString();
            else if(value instanceof java.sql.Date d) v=d.toLocalDate().toString();
            else if(value!=null && (value.getClass().getName().equals("org.postgresql.util.PGobject") || JSON_FIELDS.contains(k))) {
                try{v=json.readValue(value.toString(),Object.class);}catch(Exception ignored){v=value.toString();}
            }
            out.put(k,v);
        });return out;
    }
    public static BigDecimal decimal(Object value) { return value==null?BigDecimal.ZERO:new BigDecimal(value.toString()); }
}
