package com.flowpilot.engine;

import com.flowpilot.domain.rule.model.ValidationResult;
import com.flowpilot.domain.rule.service.RuleValidator;
import com.flowpilot.exception.RuleLoadException;
import org.springframework.stereotype.Component;

@Component
public class LiteFlowRuleValidator implements RuleValidator {

    private final LiteFlowRuleLoader ruleLoader;

    public LiteFlowRuleValidator(LiteFlowRuleLoader ruleLoader) {
        this.ruleLoader = ruleLoader;
    }

    @Override
    public ValidationResult validate(String ruleCode, String ruleContent) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return ValidationResult.invalid("ruleCode must not be blank");
        }
        if (ruleContent == null || ruleContent.isBlank()) {
            return ValidationResult.invalid("ruleContent must not be blank");
        }

        try {
            ruleLoader.validateLoad(ruleCode, ruleContent);
            return ValidationResult.validResult();
        } catch (RuleLoadException exception) {
            Throwable cause = exception.getCause();
            String detail = cause == null || cause.getMessage() == null
                    ? exception.getMessage()
                    : cause.getMessage();
            return ValidationResult.invalid(detail);
        }
    }
}
