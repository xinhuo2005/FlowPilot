package com.flowpilot.application;

import com.flowpilot.domain.execution.repository.FlowExecutionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ExecutionRetentionService {

    private final FlowExecutionRepository repository;
    private final int retentionDays;
    private final int batchSize;

    public ExecutionRetentionService(
            FlowExecutionRepository repository,
            @Value("${flowpilot.execution.retention-days:30}") int retentionDays,
            @Value("${flowpilot.execution.retention-batch-size:1000}") int batchSize
    ) {
        this.repository = repository;
        this.retentionDays = Math.max(1, retentionDays);
        this.batchSize = Math.max(1, batchSize);
    }

    public int purgeOnce() {
        return repository.deleteOlderThan(LocalDateTime.now().minusDays(retentionDays), batchSize);
    }
}
