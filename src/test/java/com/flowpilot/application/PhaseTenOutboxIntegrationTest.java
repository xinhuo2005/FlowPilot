package com.flowpilot.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PhaseTenOutboxIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private RuleApplicationService ruleApplicationService;

    @Autowired
    private PublishApplicationService publishApplicationService;

    @Autowired
    private GrayApplicationService grayApplicationService;

    @Autowired
    private RuleChangeOutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistAndDispatchPublishEventExactlyOnce() {
        String ruleCode = "PHASE10_OUTBOX_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(new CreateRuleCommand(ruleCode, "outbox", null));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(userCheck)", "phase-ten"));

        publishApplicationService.publish(ruleCode, 1, "phase10-op-1", "release-bot", "RELEASE_MANAGER");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_change_outbox WHERE rule_code = ? AND event_type = ?",
                Integer.class, ruleCode, "RULE_PUBLISHED")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM rule_change_outbox WHERE rule_code = ? AND event_type = ?",
                String.class, ruleCode, "RULE_PUBLISHED")).isEqualTo("PENDING");

        assertThat(dispatcher.dispatchOnce(100)).isGreaterThanOrEqualTo(1);
        assertThat(dispatcher.dispatchOnce(10)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM rule_change_outbox WHERE rule_code = ? AND event_type = ?",
                String.class, ruleCode, "RULE_PUBLISHED")).isEqualTo("PROCESSED");
    }

    @Test
    void shouldCreateGrayChangeEventInSameStateTransition() {
        String ruleCode = "PHASE10_GRAY_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(new CreateRuleCommand(ruleCode, "outbox", null));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(userCheck)", "phase-ten"));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(notify)", "phase-ten"));
        publishApplicationService.publish(ruleCode, 1, "phase10-op-2", "release-bot", "RELEASE_MANAGER");

        grayApplicationService.startGray(
                ruleCode, new StartGrayCommand(2, 10),
                "phase10-op-3", "release-bot", "RELEASE_MANAGER");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_change_outbox WHERE rule_code = ? AND event_type = ?",
                Integer.class, ruleCode, "GRAY_STARTED")).isEqualTo(1);
    }
}
