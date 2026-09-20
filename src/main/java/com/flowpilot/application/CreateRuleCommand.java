package com.flowpilot.application;

import jakarta.validation.constraints.NotBlank;

public record CreateRuleCommand(
        @NotBlank String ruleCode,
        @NotBlank String ruleName,
        String description
) {

    public CreateRuleCommand {
        ruleCode = requireText(ruleCode, "ruleCode");
        ruleName = requireText(ruleName, "ruleName");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
