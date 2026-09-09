package com.company.ops.shooting;

import com.company.ops.common.Api;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShootingTicketControllerTest {
    @Test void validatesTicketInputsAndRoles(){
        assertEquals("MY",ShootingTicketController.market("my"));
        assertEquals("SCRIPT_SHOOT",ShootingTicketController.taskType("SCRIPT_SHOOT"));
        assertEquals("2026-09-10T10:00:00Z",ShootingTicketController.deadline("2026-09-10T10:00:00Z"));
        assertThrows(Api.Problem.class,()->ShootingTicketController.market("TH"));
        assertThrows(Api.Problem.class,()->ShootingTicketController.deadline("2026-09-10 18:00:00"));
        assertEquals(3L,ShootingTicketController.number(Map.of("plannedValidShotCount",3),"plannedValidShotCount",true));
        assertThrows(Api.Problem.class,()->ShootingTicketController.number(Map.of("actualValidShotCount",-1),"actualValidShotCount",true));
    }
}
