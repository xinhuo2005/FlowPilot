package com.flowpilot.application;

import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;

import java.time.LocalDateTime;

public record RuleVersionResponse(
        Integer version,
        String ruleContent,
        RuleVersionStatus status,
        String checksum,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime publishedAt
) {
    public static RuleVersionResponse from(RuleVersion version) {
        return new RuleVersionResponse(
                version.version(), version.ruleContent(), version.status(), version.checksum(),
                version.createdBy(), version.createdAt(), version.publishedAt());
    }
}
