package com.flowpilot.exception;

public class RuleVersionNotFoundException extends RuntimeException {

    public RuleVersionNotFoundException(String ruleCode, int version) {
        super("Rule version not found: " + ruleCode + "@" + version);
    }
}
