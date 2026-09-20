package com.flowpilot.domain.rule.service;

import com.flowpilot.domain.rule.model.RuleSnapshot;

public interface RuleLoader {

    String load(RuleSnapshot snapshot);
}
