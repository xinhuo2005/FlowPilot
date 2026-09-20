package com.flowpilot.application;

import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleStatus;

import java.time.LocalDateTime;

public record RuleDetailResponse(
        Long id,
        String ruleCode,
        String ruleName,
        Integer currentVersion,
        RuleStatus status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RuleDetailResponse from(RuleDefinition definition) {
        return new RuleDetailResponse(
                definition.id(), definition.ruleCode(), definition.ruleName(),
                definition.currentVersion(), definition.status(), definition.description(),
                definition.createdAt(), definition.updatedAt());
    }
}
