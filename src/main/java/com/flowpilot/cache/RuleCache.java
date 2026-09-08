package com.flowpilot.cache;

import com.flowpilot.domain.rule.model.RuleSnapshot;

public interface RuleCache {

    /**
     * Returns the cached immutable snapshot, or {@code null} when the key is absent.
     */
    RuleSnapshot get(String ruleCode, int version);

    void put(RuleSnapshot snapshot);

    void evict(String ruleCode, int version);

    void evictAll(String ruleCode);
}
