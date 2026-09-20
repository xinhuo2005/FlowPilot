package com.flowpilot.domain.rule.service;

public interface RulePublisher {

    void publish(String ruleCode, int version);

    void rollback(String ruleCode, int targetVersion);
}
