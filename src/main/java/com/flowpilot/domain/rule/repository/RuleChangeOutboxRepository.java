package com.flowpilot.domain.rule.repository;

import com.flowpilot.domain.rule.model.RuleChangeEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface RuleChangeOutboxRepository {

    void append(RuleChangeEvent event);

    List<OutboxRecord> findPending(int limit);

    boolean claim(Long id);

    void markProcessed(Long id);

    void markRetry(Long id, String error, LocalDateTime availableAt);

    record OutboxRecord(
            Long id,
            RuleChangeEvent event,
            String status,
            int attempts,
            LocalDateTime availableAt
    ) {
    }
}
