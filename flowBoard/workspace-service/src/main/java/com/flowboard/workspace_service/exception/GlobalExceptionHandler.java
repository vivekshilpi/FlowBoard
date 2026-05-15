package com.flowboard.workspace_service.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED = "Validation failed";
    private static final String GENERIC_ERROR_MESSAGE =
            "Something went wrong while processing your request. Please try again.";

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException ex){
        return new ResponseEntity<>(new ErrorResponse(LocalDateTime.now(), ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(), ex.getMessage()), ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex){
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(fe->fe.getField()+": "+fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return new ResponseEntity<>(new ErrorResponse(
                LocalDateTime.now(), 400, VALIDATION_FAILED, message
        ), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex){
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        return new ResponseEntity<>(new ErrorResponse(
                LocalDateTime.now(), 400, VALIDATION_FAILED, message
        ), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex){
        String message = ex instanceof MissingServletRequestParameterException missing
                ? missing.getParameterName() + " parameter is required"
                : "Request body is missing or malformed";

        return new ResponseEntity<>(new ErrorResponse(
                LocalDateTime.now(), 400, VALIDATION_FAILED, message
        ), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex){
        log.error("Unhandled exception in workspace-service", ex);
        return new ResponseEntity<>(new ErrorResponse(
                LocalDateTime.now(), 500, "Internal Server Error", GENERIC_ERROR_MESSAGE
        ), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
