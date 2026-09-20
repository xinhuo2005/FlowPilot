package com.flowpilot.application;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.model.ExecutionResult;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.service.RuleResolver;
import com.flowpilot.engine.RuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PhaseSixSmoothSwitchIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private RuleApplicationService ruleApplicationService;

    @Autowired
    private PublishApplicationService publishApplicationService;

    @Autowired
    private RuleResolver ruleResolver;

    @Autowired
    private RuleEngine actualRuleEngine;

    @Test
    void inFlightRequestMustKeepVersionOneWhileNewRequestUsesVersionTwo() throws Exception {
        String ruleCode = "SMOOTH_SWITCH_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(
                new CreateRuleCommand(ruleCode, "Smooth switch", "phase six"));
        ruleApplicationService.createVersion(
                ruleCode,
                new CreateRuleVersionCommand("THEN(userCheck, createOrder)", "phase-six"));
        ruleApplicationService.createVersion(
                ruleCode,
                new CreateRuleVersionCommand("THEN(userCheck, notify)", "phase-six"));
        publishApplicationService.publish(ruleCode, 1);

        BlockingRuleEngine blockingEngine = new BlockingRuleEngine(actualRuleEngine, "request-v1");
        FlowExecutionApplicationService executionService =
                new DefaultFlowExecutionApplicationService(ruleResolver, blockingEngine);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<FlowExecuteResponse> t1 = executor.submit(() -> executionService.execute(
                    ruleCode,
                    new FlowExecuteCommand("request-v1", Map.of("userValid", true))));

            assertThat(blockingEngine.awaitBlocked(Duration.ofSeconds(5))).isTrue();
            assertThat(blockingEngine.blockedSnapshot().version()).isEqualTo(1);

            publishApplicationService.publish(ruleCode, 2);

            FlowExecuteResponse t2 = executionService.execute(
                    ruleCode,
                    new FlowExecuteCommand("request-v2", Map.of("userValid", true)));
            assertThat(t2.ruleVersion()).isEqualTo(2);
            assertThat(resultMap(t2)).containsEntry("notified", true)
                    .doesNotContainKey("orderCreated");

            blockingEngine.release();
            FlowExecuteResponse t1Result = t1.get(5, TimeUnit.SECONDS);
            assertThat(t1Result.ruleVersion()).isEqualTo(1);
            assertThat(resultMap(t1Result)).containsEntry("orderCreated", true)
                    .doesNotContainKey("notified");
        } finally {
            blockingEngine.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultMap(FlowExecuteResponse response) {
        return (Map<String, Object>) response.result();
    }

    private static final class BlockingRuleEngine implements RuleEngine {

        private final RuleEngine delegate;
        private final String blockedRoutingKey;
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicReference<RuleSnapshot> blockedSnapshot = new AtomicReference<>();

        private BlockingRuleEngine(RuleEngine delegate, String blockedRoutingKey) {
            this.delegate = delegate;
            this.blockedRoutingKey = blockedRoutingKey;
        }

        @Override
        public ExecutionResult execute(RuleSnapshot snapshot, ExecutionContext context) {
            if (blockedRoutingKey.equals(context.routingKey())) {
                blockedSnapshot.set(snapshot);
                blocked.countDown();
                try {
                    if (!released.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release blocked execution");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Blocked execution was interrupted", exception);
                }
            }
            return delegate.execute(snapshot, context);
        }

        private boolean awaitBlocked(Duration timeout) throws InterruptedException {
            return blocked.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private RuleSnapshot blockedSnapshot() {
            return blockedSnapshot.get();
        }

        private void release() {
            released.countDown();
        }
    }
}
