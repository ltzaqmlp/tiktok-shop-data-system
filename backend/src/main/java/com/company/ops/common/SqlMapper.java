package com.company.ops.common;

import java.util.*;
import org.apache.ibatis.annotations.*;

// SQL text is authored exclusively by server services; request values are always bound under p.
@Mapper
public interface SqlMapper {
    @Select("${sql}") List<Map<String,Object>> select(@Param("sql") String sql, @Param("p") Map<String,Object> p);
    @Update("${sql}") int update(@Param("sql") String sql, @Param("p") Map<String,Object> p);
    @Select(value="${sql}", affectData=true)
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    List<Map<String,Object>> returning(@Param("sql") String sql, @Param("p") Map<String,Object> p);
}
