package com.flowpilot.application;

import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.infrastructure.persistence.repository.JdbcOperationAuditRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class OperationAuditService {

    private final JdbcOperationAuditRepository repository;
    private final OperationAuthorizer authorizer;

    public OperationAuditService(
            JdbcOperationAuditRepository repository,
            OperationAuthorizer authorizer
    ) {
        this.repository = repository;
        this.authorizer = authorizer;
    }

    public void execute(OperationRequest request, Runnable action) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(action, "action must not be null");
        authorizer.authorize(request);

        var existing = repository.findByOperationId(request.operationId());
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(request.requestHash())) {
                throw new IllegalRuleStateException(
                        "Operation id has already been used for a different request: "
                                + request.operationId());
            }
            switch (existing.get().status()) {
                case SUCCEEDED -> { return; }
                case IN_PROGRESS -> throw new IllegalRuleStateException(
                        "Operation is already in progress: " + request.operationId());
                case FAILED -> throw new IllegalRuleStateException(
                        "Operation has already failed; use a new operation id: "
                                + request.operationId());
                default -> throw new IllegalRuleStateException(
                        "Unsupported operation status: " + existing.get().status());
            }
        }

        if (!repository.insertInProgress(request)) {
            throw new IllegalRuleStateException(
                    "Operation was concurrently submitted: " + request.operationId());
        }
        try {
            action.run();
            repository.markSucceeded(request.operationId(), "completed");
        } catch (RuntimeException exception) {
            repository.markFailed(request.operationId(), exception.getMessage());
            throw exception;
        }
    }
}
