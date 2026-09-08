package com.flowpilot.domain.gray.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrayPolicyTest {

    @Test
    void acceptsActivePercentageBoundaries() {
        new GrayPolicy(null, 1L, 1, 2, 1, GrayPolicyStatus.ACTIVE, null, null);
        new GrayPolicy(null, 1L, 1, 2, 99, GrayPolicyStatus.ACTIVE, null, null);
    }

    @Test
    void rejectsZeroAndOneHundredPercentPolicies() {
        assertThatThrownBy(() ->
                new GrayPolicy(null, 1L, 1, 2, 0, GrayPolicyStatus.ACTIVE, null, null)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new GrayPolicy(null, 1L, 1, 2, 100, GrayPolicyStatus.ACTIVE, null, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSameBaseAndGrayVersion() {
        assertThatThrownBy(() ->
                new GrayPolicy(null, 1L, 2, 2, 10, GrayPolicyStatus.ACTIVE, null, null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseVersion and grayVersion must differ");
    }
}
