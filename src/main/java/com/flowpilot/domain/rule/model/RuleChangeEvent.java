package com.flowpilot.domain.rule.model;

import java.util.Objects;
import java.util.UUID;

public record RuleChangeEvent(
        String eventId,
        String ruleCode,
        Long ruleId,
        String eventType,
        Integer aggregateVersion,
        String payload
) {

    public RuleChangeEvent {
        eventId = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
        ruleCode = requireText(ruleCode, "ruleCode");
        ruleId = Objects.requireNonNull(ruleId, "ruleId must not be null");
        eventType = requireText(eventType, "eventType");
        payload = requireText(payload, "payload");
    }

    public static RuleChangeEvent of(
            String ruleCode,
            Long ruleId,
            String eventType,
            Integer aggregateVersion,
            String payload
    ) {
        return new RuleChangeEvent(null, ruleCode, ruleId, eventType, aggregateVersion, payload);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
