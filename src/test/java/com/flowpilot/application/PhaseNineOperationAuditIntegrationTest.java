package com.flowpilot.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PhaseNineOperationAuditIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private RuleApplicationService ruleApplicationService;

    @Autowired
    private PublishApplicationService publishApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldMakeRepeatedPublishWithSameOperationIdIdempotent() {
        String ruleCode = "PHASE9_IDEMPOTENT_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(new CreateRuleCommand(ruleCode, "audit", null));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(userCheck)", "phase-nine"));

        publishApplicationService.publish(ruleCode, 1, "phase9-op-1", "release-bot", "RELEASE_MANAGER");
        publishApplicationService.publish(ruleCode, 1, "phase9-op-1", "release-bot", "RELEASE_MANAGER");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flow_operation_audit WHERE operation_id = ?", Integer.class,
                "phase9-op-1");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM flow_operation_audit WHERE operation_id = ?", String.class,
                "phase9-op-1");

        assertThat(count).isEqualTo(1);
        assertThat(status).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldRejectReusingOperationIdForDifferentRequest() {
        String ruleCode = "PHASE9_HASH_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(new CreateRuleCommand(ruleCode, "audit", null));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(userCheck)", "phase-nine"));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(notify)", "phase-nine"));

        publishApplicationService.publish(ruleCode, 1, "phase9-op-2", "release-bot", "RELEASE_MANAGER");

        assertThatThrownBy(() -> publishApplicationService.publish(
                ruleCode, 2, "phase9-op-2", "release-bot", "RELEASE_MANAGER"))
                .isInstanceOf(com.flowpilot.exception.IllegalRuleStateException.class)
                .hasMessageContaining("different request");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flow_operation_audit WHERE operation_id = ?", Integer.class,
                "phase9-op-2")).isEqualTo(1);
    }

    @Test
    void shouldRecordFailedMutationForOperationalDiagnosis() {
        String operationId = "phase9-op-failed-" + SEQUENCE.incrementAndGet();

        assertThatThrownBy(() -> publishApplicationService.publish(
                "MISSING_PHASE9_RULE", 1, operationId, "release-bot", "RELEASE_MANAGER"))
                .isInstanceOf(com.flowpilot.exception.RuleNotFoundException.class);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, operator, operation_type FROM flow_operation_audit WHERE operation_id = ?",
                operationId);
        assertThat(row).containsEntry("STATUS", "FAILED")
                .containsEntry("OPERATOR", "release-bot")
                .containsEntry("OPERATION_TYPE", "PUBLISH");
    }
}
