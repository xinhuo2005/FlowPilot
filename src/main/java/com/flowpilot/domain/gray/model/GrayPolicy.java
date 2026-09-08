package com.flowpilot.domain.gray.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record GrayPolicy(
        Long id,
        Long ruleId,
        Integer baseVersion,
        Integer grayVersion,
        Integer percentage,
        GrayPolicyStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public GrayPolicy {
        requirePositive(ruleId, "ruleId");
        requirePositive(baseVersion, "baseVersion");
        requirePositive(grayVersion, "grayVersion");
        if (baseVersion.equals(grayVersion)) {
            throw new IllegalArgumentException("baseVersion and grayVersion must differ");
        }
        if (percentage == null || percentage < 1 || percentage > 99) {
            throw new IllegalArgumentException("percentage must be between 1 and 99");
        }
        Objects.requireNonNull(status, "status must not be null");
    }

    private static void requirePositive(Number value, String fieldName) {
        if (value == null || value.longValue() < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
