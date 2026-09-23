package com.company.ops.shooting;

import com.company.ops.common.Api;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShootingTicketControllerTest {
    @Test void euRegionRespectsCountryPermissionsAndBossCannotCreate(){
        var director=Map.<String,Object>of("marketCodes",java.util.List.of("FR","US"),"roles",java.util.List.of(Map.of("roleCode","DIRECTOR")));
        assertEquals("EU",ShootingTicketController.allowedMarket(director,"EU"));
        assertEquals("FR",ShootingTicketController.allowedMarket(director,"FR"));
        assertThrows(Api.Problem.class,()->ShootingTicketController.allowedMarket(director,"DE"));
        var usOnly=Map.<String,Object>of("marketCode","US");
        assertThrows(Api.Problem.class,()->ShootingTicketController.allowedMarket(usOnly,"EU"));
        var boss=Map.<String,Object>of("roles",java.util.List.of(Map.of("roleCode","BOSS")));
        assertEquals("BOSS",ShootingTicketController.role(boss));
        var req=new org.springframework.mock.web.MockHttpServletRequest();req.setAttribute("actor",boss);
        var controller=new ShootingTicketController(null,org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        assertEquals(403,assertThrows(Api.Problem.class,()->controller.create(Map.of(),req)).status);
    }
    @Test void validatesTicketInputsAndRoles(){
        assertEquals("MY",ShootingTicketController.market("my"));
        assertEquals("SCRIPT_SHOOT",ShootingTicketController.taskType("SCRIPT_SHOOT"));
        assertEquals("2026-09-10T10:00:00Z",ShootingTicketController.plannedTime("2026-09-10T10:00:00Z"));
        assertThrows(Api.Problem.class,()->ShootingTicketController.market("TH"));
        assertThrows(Api.Problem.class,()->ShootingTicketController.plannedTime("2026-09-10 18:00:00"));
        assertEquals(3L,ShootingTicketController.number(Map.of("plannedValidShotCount",3),"plannedValidShotCount",true));
        assertThrows(Api.Problem.class,()->ShootingTicketController.number(Map.of("actualValidShotCount",-1),"actualValidShotCount",true));
    }
    @Test void formattedTextKeepsLineBreaksAndSpaces(){
        var value="1、测试1\n2、测试2 \n3、测试3 ";
        assertEquals(value,ShootingTicketController.formattedText(Map.of("text",value),"text",4000,true));
    }
    @Test void newTicketRequiresEndAfterStart(){
        var director=Map.<String,Object>of("id","42","marketCode","MY","roles",java.util.List.of(Map.of("roleCode","DIRECTOR")));
        var req=new org.springframework.mock.web.MockHttpServletRequest();req.setAttribute("actor",director);
        var controller=new ShootingTicketController(null,org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        var body=Map.<String,Object>of("marketCode","MY","taskType","SCRIPT_SHOOT","shotRequirement","测试","plannedValidShotCount",1,"plannedStart","2026-09-10T12:00:00Z","plannedEnd","2026-09-10T10:00:00Z","shooterId","9");
        assertEquals("结束时间必须晚于开始时间",assertThrows(Api.Problem.class,()->controller.create(body,req)).getMessage());
    }
}
