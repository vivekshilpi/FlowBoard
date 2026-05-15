package com.flowBoard.auth_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHideUnexpectedExceptionDetailsFromUsers() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneralException(new RuntimeException("SMTP credentials missing"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().getError());
        assertEquals(
                "Something went wrong while processing your request. Please try again.",
                response.getBody().getMessage()
        );
    }

    @Test
    void shouldExplainMissingParametersClearly() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new MissingServletRequestParameterException("email", "String"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().getError());
        assertEquals("email parameter is required", response.getBody().getMessage());
    }
}
