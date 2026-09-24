package com.flowpilot.observability;

import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import com.flowpilot.domain.gray.service.GrayManager;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GrayProtectionService {

    private static final Logger log = LoggerFactory.getLogger(GrayProtectionService.class);

    private final GrayPolicyRepository grayPolicyRepository;
    private final GrayManager grayManager;
    private final RuleDefinitionRepository definitionRepository;
    private final FlowPilotMetrics metrics;
    private final int failureThreshold;
    private final ConcurrentHashMap<String, FailureCounter> failures = new ConcurrentHashMap<>();

    public GrayProtectionService(
            GrayPolicyRepository grayPolicyRepository,
            GrayManager grayManager,
            RuleDefinitionRepository definitionRepository,
            FlowPilotMetrics metrics,
            @Value("${flowpilot.gray.protection.failure-threshold:5}") int failureThreshold
    ) {
        this.grayPolicyRepository = grayPolicyRepository;
        this.grayManager = grayManager;
        this.definitionRepository = definitionRepository;
        this.metrics = metrics;
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    public void recordFailure(String ruleCode, int version) {
        Long ruleId = findRuleId(ruleCode);
        if (ruleId == null) {
            return;
        }
        GrayPolicy policy = grayPolicyRepository.findActiveByRuleId(ruleId).orElse(null);
        if (policy == null || policy.grayVersion() != version) {
            return;
        }
        FailureCounter counter = failures.computeIfAbsent(
                ruleCode + "@" + version, ignored -> new FailureCounter());
        int count = counter.increment();
        if (count < failureThreshold || !counter.tryTrip()) {
            return;
        }
        try {
            grayManager.stop(ruleCode);
            metrics.recordGrayProtection(ruleCode, "STOP_GRAY");
            log.warn("gray_protection_triggered ruleCode={} grayVersion={} failures={}",
                    ruleCode, version, count);
        } catch (RuntimeException exception) {
            counter.resetTrip();
            log.warn("gray_protection_stop_failed ruleCode={} grayVersion={} reason={}",
                    ruleCode, version, exception.getMessage());
        } finally {
            failures.remove(ruleCode + "@" + version, counter);
        }
    }

    private Long findRuleId(String ruleCode) {
        return definitionRepository.findByCode(ruleCode)
                .map(definition -> definition.id())
                .orElse(null);
    }

    private static final class FailureCounter {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicInteger tripped = new AtomicInteger();

        int increment() {
            return count.incrementAndGet();
        }

        boolean tryTrip() {
            return tripped.compareAndSet(0, 1);
        }

        void resetTrip() {
            tripped.set(0);
        }
    }
}
