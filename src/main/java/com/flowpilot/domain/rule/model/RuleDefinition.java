package com.flowpilot.domain.rule.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record RuleDefinition(
        Long id,
        String ruleCode,
        String ruleName,
        Integer currentVersion,
        RuleStatus status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public RuleDefinition {
        ruleCode = requireText(ruleCode, "ruleCode");
        ruleName = requireText(ruleName, "ruleName");
        Objects.requireNonNull(status, "status must not be null");
        if (currentVersion != null && currentVersion < 1) {
            throw new IllegalArgumentException("currentVersion must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
