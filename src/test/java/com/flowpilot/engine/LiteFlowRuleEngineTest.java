package com.flowpilot.engine;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.model.ExecutionResult;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.model.ValidationResult;
import com.flowpilot.domain.rule.service.RuleValidator;
import com.flowpilot.exception.RuleExecutionException;
import com.flowpilot.exception.RuleLoadException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LiteFlowRuleEngineTest {

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private LiteFlowRuleLoader ruleLoader;

    @Autowired
    private RuleValidator ruleValidator;

    @Test
    void shouldExecuteTheMinimumThenChainInOrder() {
        RuleSnapshot snapshot = snapshot(101L, "ORDER_MINIMUM", 1,
                "THEN(userCheck, stockCheck, createOrder)", "minimum-v1");
        ExecutionContext context = context("exec-minimum", Map.of());

        ExecutionResult result = ruleEngine.execute(snapshot, context);

        assertThat(result.success()).isTrue();
        assertThat(result.ruleCode()).isEqualTo("ORDER_MINIMUM");
        assertThat(context.executedNodes())
                .containsExactly("userCheck", "stockCheck", "createOrder");
        assertThat(context.variables()).containsEntry("orderCreated", true);
    }

    @Test
    void shouldExecuteTheCompleteDemoChain() {
        RuleSnapshot snapshot = snapshot(102L, "ORDER_COMPLETE", 1,
                "THEN(userCheck, WHEN(riskCheck, stockCheck), priceCalculate, createOrder, notify)",
                "complete-v1");
        ExecutionContext context = context("exec-complete", Map.of("amount", 88));

        ruleEngine.execute(snapshot, context);

        assertThat(context.executedNodes()).first().isEqualTo("userCheck");
        assertThat(context.executedNodes().subList(1, 3))
                .containsExactlyInAnyOrder("riskCheck", "stockCheck");
        assertThat(context.executedNodes()).endsWith(
                "priceCalculate", "createOrder", "notify");
        assertThat(context.variables())
                .containsEntry("finalPrice", 88)
                .containsEntry("orderCreated", true)
                .containsEntry("notified", true);
    }

    @Test
    void shouldKeepDifferentRuleVersionsOnDifferentChains() {
        RuleSnapshot versionOne = snapshot(103L, "VERSIONED_ORDER", 1,
                "THEN(userCheck, createOrder)", "version-1");
        RuleSnapshot versionTwo = snapshot(103L, "VERSIONED_ORDER", 2,
                "THEN(userCheck, notify)", "version-2");
        ExecutionContext firstContext = context("exec-v1", Map.of());
        ExecutionContext secondContext = context("exec-v2", Map.of());

        ruleEngine.execute(versionOne, firstContext);
        ruleEngine.execute(versionTwo, secondContext);

        assertThat(firstContext.executedNodes()).containsExactly("userCheck", "createOrder");
        assertThat(secondContext.executedNodes()).containsExactly("userCheck", "notify");
    }

    @Test
    void shouldRejectConflictingContentForAnAlreadyLoadedVersion() {
        RuleSnapshot original = snapshot(104L, "IMMUTABLE_ORDER", 1,
                "THEN(userCheck)", "checksum-a");
        RuleSnapshot conflicting = snapshot(104L, "IMMUTABLE_ORDER", 1,
                "THEN(notify)", "checksum-b");

        ruleLoader.load(original);

        assertThatThrownBy(() -> ruleLoader.load(conflicting))
                .isInstanceOf(RuleLoadException.class)
                .hasMessageContaining("different checksum");
    }

    @Test
    void shouldValidateSyntaxAndReferencedComponentsBeforePublishing() {
        ValidationResult valid = ruleValidator.validate(
                "VALID_ORDER", "THEN(userCheck, createOrder)");
        ValidationResult unknownComponent = ruleValidator.validate(
                "INVALID_ORDER", "THEN(userCheck, missingComponent)");
        ValidationResult blank = ruleValidator.validate("BLANK_ORDER", " ");

        assertThat(valid.valid()).isTrue();
        assertThat(unknownComponent.valid()).isFalse();
        assertThat(unknownComponent.errors()).isNotEmpty();
        assertThat(blank.valid()).isFalse();
    }

    @Test
    void shouldExposeTheExecutionIdWhenAComponentFails() {
        RuleSnapshot snapshot = snapshot(105L, "REJECTED_ORDER", 1,
                "THEN(userCheck, createOrder)", "rejected-v1");
        ExecutionContext context = context("exec-rejected", Map.of("userValid", false));

        assertThatThrownBy(() -> ruleEngine.execute(snapshot, context))
                .isInstanceOf(RuleExecutionException.class)
                .extracting("executionId")
                .isEqualTo("exec-rejected");
        assertThat(context.executedNodes()).containsExactly("userCheck");
    }

    private RuleSnapshot snapshot(
            long ruleId, String ruleCode, int version, String content, String checksum) {
        return new RuleSnapshot(ruleId, ruleCode, version, content, checksum);
    }

    private ExecutionContext context(String executionId, Map<String, Object> variables) {
        return new ExecutionContext(executionId, "routing-key", variables);
    }
}
