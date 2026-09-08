package com.flowpilot.cache;

public record RuleCacheKey(String ruleCode, int version) {

    public RuleCacheKey {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
