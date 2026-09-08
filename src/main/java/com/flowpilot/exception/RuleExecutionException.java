package com.flowpilot.exception;

public class RuleExecutionException extends RuntimeException {

    private final String executionId;

    public RuleExecutionException(String executionId, String message, Throwable cause) {
        super(message, cause);
        this.executionId = executionId;
    }

    public String getExecutionId() {
        return executionId;
    }
}
