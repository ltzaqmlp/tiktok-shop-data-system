package com.company.ops.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DbTest {
    @Test void jsonbTextIsReturnedAsJson() {
        var mapper=org.mockito.Mockito.mock(SqlMapper.class);
        org.mockito.Mockito.when(mapper.select(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(List.of(Map.of("delivery_results","{}","editor_task_results","{\"自定义任务\":{\"actualCount\":1,\"delivery\":\"已完成\"}}","director_task_results","{}")));
        var result=new Db(mapper,new ObjectMapper()).rows("select 1",Map.of()).getFirst();
        assertInstanceOf(Map.class,result.get("deliveryResults"));
        assertInstanceOf(Map.class,result.get("editorTaskResults"));
        assertInstanceOf(Map.class,result.get("directorTaskResults"));
    }
}
