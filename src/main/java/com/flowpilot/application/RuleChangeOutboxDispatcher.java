package com.flowpilot.application;

import com.flowpilot.domain.rule.repository.RuleChangeOutboxRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RuleChangeOutboxDispatcher {

    private final RuleChangeOutboxRepository repository;
    private final RuleChangeEventHandler handler;

    public RuleChangeOutboxDispatcher(
            RuleChangeOutboxRepository repository,
            RuleChangeEventHandler handler
    ) {
        this.repository = repository;
        this.handler = handler;
    }

    public int dispatchOnce(int batchSize) {
        int processed = 0;
        for (RuleChangeOutboxRepository.OutboxRecord record : repository.findPending(batchSize)) {
            if (!repository.claim(record.id())) {
                continue;
            }
            try {
                handler.handle(record.event());
                repository.markProcessed(record.id());
                processed++;
            } catch (RuntimeException exception) {
                repository.markRetry(
                        record.id(), exception.getMessage(), LocalDateTime.now().plusSeconds(5));
            }
        }
        return processed;
    }
}
