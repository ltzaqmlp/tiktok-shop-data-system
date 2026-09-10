package com.company.ops.auth;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AuthControllerTest {
    @Test void rememberTokenHashIsDeterministicAndDoesNotExposeTheToken(){
        String token="remember-token";
        assertEquals(AuthController.tokenHash(token),AuthController.tokenHash(token));
        assertNotEquals(token,AuthController.tokenHash(token));
    }
}
