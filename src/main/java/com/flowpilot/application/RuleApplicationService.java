package com.flowpilot.application;

import java.util.List;

public interface RuleApplicationService {

    Long createRule(CreateRuleCommand command);

    Integer createVersion(String ruleCode, CreateRuleVersionCommand command);

    RuleDetailResponse getRule(String ruleCode);

    List<RuleVersionResponse> listVersions(String ruleCode);
}
