package com.flowpilot.interfaces.rest;

import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.ExecutionNotFoundException;
import com.flowpilot.exception.RuleExecutionException;
import com.flowpilot.exception.RuleLoadException;
import com.flowpilot.exception.RuleNotFoundException;
import com.flowpilot.exception.RuleValidationException;
import com.flowpilot.exception.RuleVersionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({RuleNotFoundException.class, RuleVersionNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", exception.getMessage(), null);
    }

    @ExceptionHandler(ExecutionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleExecutionNotFound(
            ExecutionNotFoundException exception
    ) {
        return response(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND", exception.getMessage(), null);
    }

    @ExceptionHandler(IllegalRuleStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalRuleStateException exception) {
        return response(HttpStatus.CONFLICT, "ILLEGAL_RULE_STATE", exception.getMessage(), null);
    }

    @ExceptionHandler(RuleValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(RuleValidationException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "RULE_VALIDATION_FAILED", exception.getMessage(), null);
    }

    @ExceptionHandler(RuleLoadException.class)
    public ResponseEntity<ApiErrorResponse> handleLoad(RuleLoadException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "RULE_LOAD_FAILED", exception.getMessage(), null);
    }

    @ExceptionHandler(RuleExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleExecution(RuleExecutionException exception) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "RULE_EXECUTION_FAILED",
                exception.getMessage(),
                exception.getExecutionId());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", null);
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            String executionId
    ) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, executionId));
    }
}
