package com.flowpilot.domain.rule.service;

import com.flowpilot.domain.rule.model.RuleSnapshot;

public interface RuleResolver {

    RuleSnapshot resolve(String ruleCode, String routingKey);
}
