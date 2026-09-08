package com.flowpilot.domain.rule.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record RuleVersion(
        Long id,
        Long ruleId,
        Integer version,
        String ruleContent,
        RuleVersionStatus status,
        String checksum,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime publishedAt
) {
    public RuleVersion {
        requirePositive(ruleId, "ruleId");
        requirePositive(version, "version");
        ruleContent = requireText(ruleContent, "ruleContent");
        Objects.requireNonNull(status, "status must not be null");
    }

    private static void requirePositive(Number value, String fieldName) {
        if (value == null || value.longValue() < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
