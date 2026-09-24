package com.flowpilot.application;

import com.flowpilot.domain.execution.repository.FlowExecutionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionRetentionServiceTest {

    @Test
    void shouldPurgeUsingConfiguredRetentionWindowAndBatch() {
        FlowExecutionRepository repository = mock(FlowExecutionRepository.class);
        when(repository.deleteOlderThan(any(LocalDateTime.class), eq(25))).thenReturn(4);
        ExecutionRetentionService service = new ExecutionRetentionService(repository, 7, 25);

        assertThat(service.purgeOnce()).isEqualTo(4);
        verify(repository).deleteOlderThan(any(LocalDateTime.class), eq(25));
    }
}
