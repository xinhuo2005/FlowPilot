package com.flowpilot.domain.rule.model;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        if (valid && !errors.isEmpty()) {
            throw new IllegalArgumentException("a valid result cannot contain errors");
        }
    }

    public static ValidationResult validResult() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(String error) {
        return new ValidationResult(false, List.of(error));
    }
}
