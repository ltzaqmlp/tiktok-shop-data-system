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
            .thenReturn(List.of(Map.of("delivery_results","{}")));
        var result=new Db(mapper,new ObjectMapper()).rows("select 1",Map.of()).getFirst();
        assertInstanceOf(Map.class,result.get("deliveryResults"));
    }
}
