package com.flowpilot.domain.rule.service;

import com.flowpilot.domain.rule.model.ValidationResult;

public interface RuleValidator {

    ValidationResult validate(String ruleCode, String ruleContent);
}
