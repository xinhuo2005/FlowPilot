package com.flowpilot.application;

import jakarta.validation.constraints.NotBlank;

public record CreateRuleVersionCommand(@NotBlank String ruleContent, String createdBy) {

    public CreateRuleVersionCommand(String ruleContent) {
        this(ruleContent, null);
    }

    public CreateRuleVersionCommand {
        if (ruleContent == null || ruleContent.isBlank()) {
            throw new IllegalArgumentException("ruleContent must not be blank");
        }
    }
}
