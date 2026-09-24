package com.flowpilot.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhaseElevenObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPropagateTraceIdAndExposeHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/rules/MISSING_TRACE_RULE")
                        .header("X-Trace-Id", "trace-phase-eleven"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", "trace-phase-eleven"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

    }
}
