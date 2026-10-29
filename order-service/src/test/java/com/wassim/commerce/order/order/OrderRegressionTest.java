package com.wassim.commerce.order.order;

import com.wassim.commerce.order.exaptions.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

class OrderRegressionTest {
    @Test
    void databaseErrorsReturnGenericConflictWithoutInternalDetails() {
        String detail = "INSERT INTO users violates constraint uk_users_email_lower";
        var response = new GlobalExceptionHandler().handleDataIntegrityViolation(
                new DataIntegrityViolationException(detail, new RuntimeException("private database details")));
        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("The request conflicts with existing data.", response.getBody().get("message"));
        assertFalse(response.getBody().toString().contains(detail));
        assertFalse(response.getBody().toString().contains("private database details"));
    }
}
