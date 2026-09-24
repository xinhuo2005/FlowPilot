package com.flowpilot.application;

import com.flowpilot.cache.RuleCache;
import com.flowpilot.domain.rule.model.RuleChangeEvent;
import org.springframework.stereotype.Component;

@Component
public class LocalRuleChangeEventHandler implements RuleChangeEventHandler {

    private final RuleCache ruleCache;

    public LocalRuleChangeEventHandler(RuleCache ruleCache) {
        this.ruleCache = ruleCache;
    }

    @Override
    public void handle(RuleChangeEvent event) {
        ruleCache.evictAll(event.ruleCode());
    }
}
