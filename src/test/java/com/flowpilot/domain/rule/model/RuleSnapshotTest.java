package com.flowpilot.domain.rule.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleSnapshotTest {

    @Test
    void retainsResolvedVersionAndContent() {
        RuleSnapshot snapshot = new RuleSnapshot(
                1L,
                "ORDER_FLOW",
                2,
                "THEN(userCheck,stockCheck,createOrder)",
                "a".repeat(64)
        );

        assertThat(snapshot.ruleId()).isEqualTo(1L);
        assertThat(snapshot.ruleCode()).isEqualTo("ORDER_FLOW");
        assertThat(snapshot.version()).isEqualTo(2);
        assertThat(snapshot.ruleContent()).contains("stockCheck");
        assertThat(snapshot.checksum()).hasSize(64);
    }

    @Test
    void rejectsInvalidPublicArguments() {
        assertThatThrownBy(() -> new RuleSnapshot(
                1L,
                " ",
                1,
                "THEN(userCheck)",
                "a".repeat(64)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ruleCode must not be blank");

        assertThatThrownBy(() -> new RuleSnapshot(
                1L,
                "ORDER_FLOW",
                0,
                "THEN(userCheck)",
                "a".repeat(64)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("version must be positive");
    }
}
