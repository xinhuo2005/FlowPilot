package com.flowpilot.application;

import com.flowpilot.domain.rule.model.RuleChangeEvent;

@FunctionalInterface
public interface RuleChangeEventHandler {

    void handle(RuleChangeEvent event);
}
