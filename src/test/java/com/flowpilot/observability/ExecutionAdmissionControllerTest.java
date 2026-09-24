package com.flowpilot.observability;

import com.flowpilot.exception.IllegalRuleStateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionAdmissionControllerTest {

    @Test
    void shouldRejectWhenConcurrencyPermitIsNotReleasedBeforeTimeout() {
        ExecutionAdmissionController controller = new ExecutionAdmissionController(1, 5);
        controller.acquire();

        assertThatThrownBy(controller::acquire)
                .isInstanceOf(IllegalRuleStateException.class)
                .hasMessageContaining("admission limit");

        controller.release();
    }
}
