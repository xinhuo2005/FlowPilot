package com.flowpilot.observability;

import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.model.GrayPolicyStatus;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import com.flowpilot.domain.gray.service.GrayManager;
import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleStatus;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrayProtectionServiceTest {

    @Test
    void shouldStopGrayAfterFailureThresholdIsReached() {
        GrayPolicyRepository policyRepository = mock(GrayPolicyRepository.class);
        GrayManager grayManager = mock(GrayManager.class);
        RuleDefinitionRepository definitionRepository = mock(RuleDefinitionRepository.class);
        RuleDefinition definition = new RuleDefinition(
                7L, "ORDER_FLOW", "Order", 1, RuleStatus.ENABLED, null, null, null);
        when(definitionRepository.findByCode("ORDER_FLOW")).thenReturn(Optional.of(definition));
        when(policyRepository.findActiveByRuleId(7L)).thenReturn(Optional.of(
                new GrayPolicy(1L, 7L, 1, 2, 50, GrayPolicyStatus.ACTIVE, null, null)));
        FlowPilotMetrics metrics = new FlowPilotMetrics(new SimpleMeterRegistry());
        GrayProtectionService service = new GrayProtectionService(
                policyRepository, grayManager, definitionRepository, metrics, 2);

        service.recordFailure("ORDER_FLOW", 2);
        service.recordFailure("ORDER_FLOW", 2);

        verify(grayManager).stop("ORDER_FLOW");
    }

    @Test
    void shouldIgnoreFailuresFromStableVersion() {
        GrayPolicyRepository policyRepository = mock(GrayPolicyRepository.class);
        GrayManager grayManager = mock(GrayManager.class);
        RuleDefinitionRepository definitionRepository = mock(RuleDefinitionRepository.class);
        RuleDefinition definition = new RuleDefinition(
                7L, "ORDER_FLOW", "Order", 1, RuleStatus.ENABLED, null, null, null);
        when(definitionRepository.findByCode("ORDER_FLOW")).thenReturn(Optional.of(definition));
        when(policyRepository.findActiveByRuleId(7L)).thenReturn(Optional.of(
                new GrayPolicy(1L, 7L, 1, 2, 50, GrayPolicyStatus.ACTIVE, null, null)));
        GrayProtectionService service = new GrayProtectionService(
                policyRepository, grayManager, definitionRepository,
                new FlowPilotMetrics(new SimpleMeterRegistry()), 1);

        service.recordFailure("ORDER_FLOW", 1);

        org.mockito.Mockito.verifyNoInteractions(grayManager);
    }
}
