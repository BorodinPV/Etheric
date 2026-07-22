package com.etheric.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    void toResponse_returns500() {
        Exception ex = new RuntimeException("Something went wrong");
        Response response = mapper.toResponse(ex);
        assertEquals(500, response.getStatus());
    }

    @Test
    void toResponse_entityIsNotNull() {
        Exception ex = new RuntimeException("test");
        Response response = mapper.toResponse(ex);
        assertNotNull(response.getEntity());
    }

    @Test
    void toResponse_withDifferentExceptionTypes() {
        Response r1 = mapper.toResponse(new IllegalArgumentException("bad arg"));
        assertEquals(500, r1.getStatus());

        Response r2 = mapper.toResponse(new NullPointerException("null"));
        assertEquals(500, r2.getStatus());

        Response r3 = mapper.toResponse(new IllegalStateException("illegal"));
        assertEquals(500, r3.getStatus());
    }

    @Test
    void toResponse_entityContainsServerError() {
        Exception ex = new RuntimeException("test");
        Response response = mapper.toResponse(ex);
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity().toString().contains("server_error"));
    }
}
