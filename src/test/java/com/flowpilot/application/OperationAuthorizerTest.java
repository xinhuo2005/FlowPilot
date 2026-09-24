package com.flowpilot.application;

import com.flowpilot.exception.IllegalRuleStateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationAuthorizerTest {

    @Test
    void shouldRequireReleaseRoleWhenEnabled() {
        OperationAuthorizer authorizer = new OperationAuthorizer(true);
        OperationRequest request = OperationRequest.of(
                "op-1", "RULE", "PUBLISH", 1, "operator", "RULE_EDITOR");

        assertThatThrownBy(() -> authorizer.authorize(request))
                .isInstanceOf(IllegalRuleStateException.class);
    }

    @Test
    void shouldAcceptReleaseManagerRoleWhenEnabled() {
        OperationAuthorizer authorizer = new OperationAuthorizer(true);
        OperationRequest request = OperationRequest.of(
                "op-2", "RULE", "PUBLISH", 1, "operator", "release_manager");

        authorizer.authorize(request);
    }
}
