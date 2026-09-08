package com.flowpilot.engine;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.model.ExecutionResult;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.exception.RuleExecutionException;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class LiteFlowRuleEngine implements RuleEngine {

    private final FlowExecutor flowExecutor;
    private final LiteFlowRuleLoader ruleLoader;

    public LiteFlowRuleEngine(FlowExecutor flowExecutor, LiteFlowRuleLoader ruleLoader) {
        this.flowExecutor = flowExecutor;
        this.ruleLoader = ruleLoader;
    }

    @Override
    public ExecutionResult execute(RuleSnapshot snapshot, ExecutionContext context) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String chainId = ruleLoader.load(snapshot);
        try {
            LiteflowResponse response = flowExecutor.execute2Resp(chainId, null, context);
            if (!response.isSuccess()) {
                throw new RuleExecutionException(
                        context.executionId(),
                        "rule execution failed for " + chainId,
                        response.getCause());
            }
            return new ExecutionResult(
                    context.executionId(),
                    snapshot.ruleCode(),
                    snapshot.version(),
                    true,
                    context.variables());
        } catch (RuleExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RuleExecutionException(
                    context.executionId(),
                    "rule execution failed for " + chainId,
                    exception);
        }
    }
}
