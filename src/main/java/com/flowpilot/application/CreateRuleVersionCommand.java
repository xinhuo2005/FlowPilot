package com.flowpilot.application;

public record CreateRuleVersionCommand(String ruleContent, String createdBy) {

    public CreateRuleVersionCommand(String ruleContent) {
        this(ruleContent, null);
    }

    public CreateRuleVersionCommand {
        if (ruleContent == null || ruleContent.isBlank()) {
            throw new IllegalArgumentException("ruleContent must not be blank");
        }
    }
}
