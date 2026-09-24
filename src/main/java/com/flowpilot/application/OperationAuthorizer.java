package com.flowpilot.application;

import com.flowpilot.exception.IllegalRuleStateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OperationAuthorizer {

    private final boolean roleCheckEnabled;

    public OperationAuthorizer(
            @Value("${flowpilot.security.require-role:false}") boolean roleCheckEnabled
    ) {
        this.roleCheckEnabled = roleCheckEnabled;
    }

    public void authorize(OperationRequest request) {
        if (!roleCheckEnabled) {
            return;
        }
        Set<String> roles = Arrays.stream(request.roles().split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        if (!roles.contains("RELEASE_MANAGER") && !roles.contains("RULE_ADMIN")) {
            throw new IllegalRuleStateException(
                    "Operator is not allowed to perform " + request.operationType());
        }
    }
}
