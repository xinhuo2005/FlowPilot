package com.flowpilot.application;

import com.flowpilot.domain.execution.model.ExecutionMode;
import com.flowpilot.domain.execution.model.ExecutionStatus;
import com.flowpilot.domain.gray.service.GrayManager;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PhaseTwelveExecutionModeIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private RuleApplicationService ruleApplicationService;

    @Autowired
    private PublishApplicationService publishApplicationService;

    @Autowired
    private GrayApplicationService grayApplicationService;

    @Autowired
    private FlowExecutionApplicationService executionService;

    @Autowired
    private RuleDefinitionRepository definitionRepository;

    @Test
    void shouldRunDryRunWithoutApplyingSideEffectVariables() {
        String ruleCode = "PHASE12_DRY_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(new CreateRuleCommand(ruleCode, "dry run", null));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand(
                        "THEN(userCheck, createOrder, notify)", "phase-twelve"));
        publishApplicationService.publish(ruleCode, 1, "phase12-dry-1", "release-bot", "RELEASE_MANAGER");

        FlowExecuteResponse response = executionService.execute(
                ruleCode,
                new FlowExecuteCommand(
                        "dry-run-key", Map.of("userValid", true), ExecutionMode.DRY_RUN, null));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result).containsEntry("orderWouldBeCreated", true)
                .containsEntry("notificationWouldBeSent", true)
                .doesNotContainKey("orderCreated")
                .doesNotContainKey("notified");
        assertThat(executionService.queryExecution(response.executionId()).status())
                .isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void shouldCompareStableAndShadowVersionsWithoutFailingPrimaryExecution() {
        String ruleCode = "PHASE12_SHADOW_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(new CreateRuleCommand(ruleCode, "shadow", null));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(userCheck, createOrder)", "phase-twelve"));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(userCheck, notify)", "phase-twelve"));
        publishApplicationService.publish(ruleCode, 1, "phase12-shadow-1", "release-bot", "RELEASE_MANAGER");
        grayApplicationService.startGray(
                ruleCode, new StartGrayCommand(2, 1),
                "phase12-shadow-2", "release-bot", "RELEASE_MANAGER");

        String stableKey = findStableKey();
        FlowExecuteResponse response = executionService.execute(
                ruleCode,
                new FlowExecuteCommand(
                        stableKey, Map.of("userValid", true), ExecutionMode.SHADOW, 2));

        @SuppressWarnings("unchecked")
        Map<String, Object> comparison = (Map<String, Object>) response.result();
        assertThat(response.success()).isTrue();
        assertThat(response.ruleVersion()).isEqualTo(1);
        assertThat(comparison).containsEntry("shadowVersion", 2)
                .containsEntry("shadowSuccess", true)
                .containsEntry("matched", false);
        assertThat(definitionRepository.findByCode(ruleCode).orElseThrow().currentVersion())
                .isEqualTo(1);
    }

    private static String findStableKey() {
        for (int index = 0; index < 1_000; index++) {
            String candidate = "stable-shadow-" + index;
            if (Math.floorMod(candidate.hashCode(), 100) >= 1) {
                return candidate;
            }
        }
        throw new IllegalStateException("unable to find stable routing key");
    }
}
