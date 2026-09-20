package com.flowpilot.application;

public interface RuleApplicationService {

    Long createRule(CreateRuleCommand command);

    Integer createVersion(String ruleCode, CreateRuleVersionCommand command);
}
