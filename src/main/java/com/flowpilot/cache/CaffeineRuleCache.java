package com.flowpilot.cache;

import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class CaffeineRuleCache implements RuleCache {

    private static final long DEFAULT_MAXIMUM_SIZE = 1_000;
    private static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(30);

    private final Cache<RuleCacheKey, RuleSnapshot> cache;

    public CaffeineRuleCache() {
        this(DEFAULT_MAXIMUM_SIZE, DEFAULT_EXPIRE_AFTER_ACCESS);
    }

    public CaffeineRuleCache(long maximumSize, Duration expireAfterAccess) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        Objects.requireNonNull(expireAfterAccess, "expireAfterAccess must not be null");
        if (expireAfterAccess.isZero() || expireAfterAccess.isNegative()) {
            throw new IllegalArgumentException("expireAfterAccess must be positive");
        }
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(expireAfterAccess)
                .build();
    }

    @Override
    public RuleSnapshot get(String ruleCode, int version) {
        return cache.getIfPresent(new RuleCacheKey(ruleCode, version));
    }

    @Override
    public void put(RuleSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        cache.put(new RuleCacheKey(snapshot.ruleCode(), snapshot.version()), snapshot);
    }

    @Override
    public void evict(String ruleCode, int version) {
        cache.invalidate(new RuleCacheKey(ruleCode, version));
    }

    @Override
    public void evictAll(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode must not be blank");
        }
        cache.asMap().keySet().removeIf(key -> key.ruleCode().equals(ruleCode));
    }
}
