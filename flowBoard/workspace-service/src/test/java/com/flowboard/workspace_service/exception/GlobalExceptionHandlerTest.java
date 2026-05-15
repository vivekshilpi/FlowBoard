package com.flowboard.workspace_service.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleCustom returns embedded status and message")
    void handleCustom_returnsCustomStatus() {
        CustomException exception = new CustomException("No access", HttpStatus.FORBIDDEN);

        var response = handler.handleCustom(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("No access");
    }

    @Test
    @DisplayName("handleValidation aggregates field errors")
    void handleValidation_aggregatesFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        bindingResult.addError(new FieldError("request", "email", "must be valid"));
        Method method = SampleController.class.getDeclaredMethod("sample", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("name: must not be blank", "email: must be valid");
    }

    @Test
    @DisplayName("handleConstraintViolation aggregates constraint messages")
    void handleConstraintViolation_aggregatesMessages() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("email");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be valid");

        var response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("email: must be valid");
    }

    @Test
    @DisplayName("handleBadRequest distinguishes missing param and malformed body")
    void handleBadRequest_distinguishesCases() {
        var missing = handler.handleBadRequest(new MissingServletRequestParameterException("id", "Long"));
        var malformed = handler.handleBadRequest(new HttpMessageNotReadableException("bad body"));

        assertThat(missing.getBody().getMessage()).isEqualTo("id parameter is required");
        assertThat(malformed.getBody().getMessage()).isEqualTo("Request body is missing or malformed");
    }

    @Test
    @DisplayName("handleGeneral hides internal details")
    void handleGeneral_hidesDetails() {
        var response = handler.handleGeneral(new RuntimeException("db password missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo(
                "Something went wrong while processing your request. Please try again.");
    }

    private static final class SampleController {
        @SuppressWarnings("unused")
        void sample(String body) {
        }
    }
}
