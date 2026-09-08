package com.flowpilot.domain.rule.model;

public record RuleSnapshot(
        Long ruleId,
        String ruleCode,
        Integer version,
        String ruleContent,
        String checksum
) {
    public RuleSnapshot {
        requirePositive(ruleId, "ruleId");
        ruleCode = requireText(ruleCode, "ruleCode");
        requirePositive(version, "version");
        ruleContent = requireText(ruleContent, "ruleContent");
        checksum = requireText(checksum, "checksum");
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
