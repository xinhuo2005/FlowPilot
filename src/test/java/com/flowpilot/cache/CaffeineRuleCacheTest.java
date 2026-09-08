package com.flowpilot.cache;

import com.flowpilot.domain.rule.model.RuleSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineRuleCacheTest {

    private final CaffeineRuleCache cache = new CaffeineRuleCache(10, Duration.ofMinutes(1));

    @Test
    void cachesSnapshotsByRuleCodeAndVersion() {
        RuleSnapshot versionOne = snapshot("ORDER_FLOW", 1);
        RuleSnapshot versionTwo = snapshot("ORDER_FLOW", 2);
        cache.put(versionOne);
        cache.put(versionTwo);

        assertThat(cache.get("ORDER_FLOW", 1)).isSameAs(versionOne);
        assertThat(cache.get("ORDER_FLOW", 2)).isSameAs(versionTwo);
        assertThat(cache.get("OTHER_FLOW", 1)).isNull();
    }

    @Test
    void evictsOneVersionWithoutAffectingOtherVersions() {
        cache.put(snapshot("ORDER_FLOW", 1));
        cache.put(snapshot("ORDER_FLOW", 2));

        cache.evict("ORDER_FLOW", 1);

        assertThat(cache.get("ORDER_FLOW", 1)).isNull();
        assertThat(cache.get("ORDER_FLOW", 2)).isNotNull();
    }

    @Test
    void evictsAllVersionsOfOnlyTheSelectedRule() {
        cache.put(snapshot("ORDER_FLOW", 1));
        cache.put(snapshot("ORDER_FLOW", 2));
        cache.put(snapshot("PAYMENT_FLOW", 1));

        cache.evictAll("ORDER_FLOW");

        assertThat(cache.get("ORDER_FLOW", 1)).isNull();
        assertThat(cache.get("ORDER_FLOW", 2)).isNull();
        assertThat(cache.get("PAYMENT_FLOW", 1)).isNotNull();
    }

    private static RuleSnapshot snapshot(String ruleCode, int version) {
        return new RuleSnapshot(
                1L,
                ruleCode,
                version,
                "THEN(userCheck)",
                String.valueOf(version).repeat(64)
        );
    }
}
