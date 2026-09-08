package com.flowpilot.exception;

import java.util.List;

public class RuleValidationException extends RuntimeException {

    private final List<String> errors;

    public RuleValidationException(List<String> errors) {
        super("Rule validation failed: " + String.join("; ", List.copyOf(errors)));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
