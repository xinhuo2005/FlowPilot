package com.flowpilot.application;

import java.util.Objects;
import java.util.UUID;

public record OperationRequest(
        String operationId,
        String ruleCode,
        String operationType,
        Integer targetVersion,
        String operator,
        String roles,
        String requestHash
) {

    public OperationRequest {
        operationId = requireText(operationId, "operationId");
        ruleCode = requireText(ruleCode, "ruleCode");
        operationType = requireText(operationType, "operationType");
        operator = requireText(operator, "operator");
        roles = roles == null ? "" : roles;
        requestHash = requireText(requestHash, "requestHash");
    }

    public static OperationRequest of(
            String operationId,
            String ruleCode,
            String operationType,
            Integer targetVersion,
            String operator,
            String roles
    ) {
        return of(operationId, ruleCode, operationType, targetVersion, operator, roles, "");
    }

    public static OperationRequest of(
            String operationId,
            String ruleCode,
            String operationType,
            Integer targetVersion,
            String operator,
            String roles,
            String requestDetails
    ) {
        String actualOperationId = operationId == null || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId;
        String hashSource = operationType + "|" + ruleCode + "|"
                + Objects.toString(targetVersion, "") + "|" + Objects.toString(requestDetails, "");
        return new OperationRequest(
                actualOperationId,
                ruleCode,
                operationType,
                targetVersion,
                operator == null || operator.isBlank() ? "local-demo" : operator,
                roles,
                com.flowpilot.infrastructure.util.ChecksumUtils.sha256(hashSource));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
