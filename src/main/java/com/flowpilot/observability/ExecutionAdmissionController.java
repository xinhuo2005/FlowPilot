package com.flowpilot.observability;

import com.flowpilot.exception.IllegalRuleStateException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExecutionAdmissionController {

    private final Semaphore semaphore;
    private final long timeoutMs;

    public ExecutionAdmissionController(
            @Value("${flowpilot.execution.max-concurrency:256}") int maxConcurrency,
            @Value("${flowpilot.execution.admission-timeout-ms:1000}") long timeoutMs
    ) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be positive");
        }
        this.semaphore = new Semaphore(maxConcurrency, true);
        this.timeoutMs = Math.max(1, timeoutMs);
    }

    public void acquire() {
        try {
            if (!semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IllegalRuleStateException("Execution admission limit reached");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalRuleStateException("Execution admission was interrupted");
        }
    }

    public void release() {
        semaphore.release();
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
