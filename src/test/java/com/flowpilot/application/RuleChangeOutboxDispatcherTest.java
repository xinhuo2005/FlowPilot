package com.flowpilot.application;

import com.flowpilot.domain.rule.model.RuleChangeEvent;
import com.flowpilot.domain.rule.repository.RuleChangeOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleChangeOutboxDispatcherTest {

    @Test
    void shouldScheduleRetryWhenHandlerFails() {
        RuleChangeOutboxRepository repository = mock(RuleChangeOutboxRepository.class);
        RuleChangeEventHandler handler = mock(RuleChangeEventHandler.class);
        RuleChangeEvent event = RuleChangeEvent.of("RULE", 1L, "RULE_PUBLISHED", 1, "currentVersion=1");
        RuleChangeOutboxRepository.OutboxRecord record = new RuleChangeOutboxRepository.OutboxRecord(
                1L, event, "PENDING", 0, LocalDateTime.now());
        when(repository.findPending(10)).thenReturn(List.of(record));
        when(repository.claim(1L)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("downstream unavailable"))
                .when(handler).handle(event);

        RuleChangeOutboxDispatcher dispatcher = new RuleChangeOutboxDispatcher(repository, handler);

        assertThat(dispatcher.dispatchOnce(10)).isZero();
        verify(repository).markRetry(eq(1L), eq("downstream unavailable"), any(LocalDateTime.class));
    }
}
