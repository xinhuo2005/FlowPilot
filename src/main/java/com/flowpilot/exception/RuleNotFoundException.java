package com.flowpilot.exception;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String ruleCode) {
        super("Rule not found: " + ruleCode);
    }
}
