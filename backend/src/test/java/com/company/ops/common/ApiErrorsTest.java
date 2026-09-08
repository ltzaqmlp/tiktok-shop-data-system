package com.company.ops.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ApiErrorsTest {
    @Test void malformedClientInputReturns400(){
        var req=mock(HttpServletRequest.class);when(req.getAttribute("requestId")).thenReturn("test");
        var errors=new Api.Errors();
        assertEquals(400,errors.handle(new MissingServletRequestParameterException("marketCode","String"),req).getStatusCode().value());
        assertEquals(400,errors.handle(new DateTimeParseException("bad date","bad",0),req).getStatusCode().value());
    }
}
