package com.flowpilot.engine;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.model.ExecutionResult;
import com.flowpilot.domain.rule.model.RuleSnapshot;

public interface RuleEngine {

    ExecutionResult execute(RuleSnapshot snapshot, ExecutionContext context);
}
