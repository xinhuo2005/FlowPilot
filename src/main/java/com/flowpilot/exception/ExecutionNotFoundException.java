package com.flowpilot.exception;

public class ExecutionNotFoundException extends RuntimeException {

    public ExecutionNotFoundException(String executionId) {
        super("Execution not found: " + executionId);
    }
}
