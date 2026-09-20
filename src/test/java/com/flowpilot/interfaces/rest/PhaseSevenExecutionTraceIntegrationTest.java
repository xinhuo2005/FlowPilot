package com.flowpilot.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.application.CreateRuleCommand;
import com.flowpilot.application.CreateRuleVersionCommand;
import com.flowpilot.application.PublishApplicationService;
import com.flowpilot.application.RuleApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhaseSevenExecutionTraceIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RuleApplicationService ruleApplicationService;

    @Autowired
    private PublishApplicationService publishApplicationService;

    @Test
    void shouldPersistSuccessfulExecutionAndEveryNode() throws Exception {
        String ruleCode = publishedRule(
                "TRACE_SUCCESS",
                "THEN(userCheck, WHEN(riskCheck, stockCheck), createOrder)");

        String responseBody = mockMvc.perform(post("/api/flows/{ruleCode}/execute", ruleCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "routingKey", "trace-success",
                                "variables", Map.of(
                                        "userValid", true,
                                        "riskPassed", true,
                                        "stockAvailable", true)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String executionId = objectMapper.readTree(responseBody).path("executionId").asText();

        mockMvc.perform(get("/api/executions/{executionId}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(executionId))
                .andExpect(jsonPath("$.ruleCode").value(ruleCode))
                .andExpect(jsonPath("$.ruleVersion").value(1))
                .andExpect(jsonPath("$.routingKey").value("trace-success"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.nodes.length()").value(4))
                .andExpect(jsonPath("$.nodes[?(@.nodeId == 'userCheck')].status").value("SUCCESS"))
                .andExpect(jsonPath("$.nodes[?(@.nodeId == 'riskCheck')].status").value("SUCCESS"))
                .andExpect(jsonPath("$.nodes[?(@.nodeId == 'stockCheck')].status").value("SUCCESS"))
                .andExpect(jsonPath("$.nodes[?(@.nodeId == 'createOrder')].status").value("SUCCESS"));
    }

    @Test
    void shouldPersistFailedExecutionAndFailedNode() throws Exception {
        String ruleCode = publishedRule("TRACE_FAILED", "THEN(userCheck, createOrder)");

        String responseBody = mockMvc.perform(post("/api/flows/{ruleCode}/execute", ruleCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "routingKey", "trace-failed",
                                "variables", Map.of("userValid", false)))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("RULE_EXECUTION_FAILED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode error = objectMapper.readTree(responseBody);
        String executionId = error.path("executionId").asText();

        mockMvc.perform(get("/api/executions/{executionId}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("RULE_EXECUTION_FAILED"))
                .andExpect(jsonPath("$.errorMessage").value(
                        org.hamcrest.Matchers.containsString("user check failed")))
                .andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.nodes[0].nodeId").value("userCheck"))
                .andExpect(jsonPath("$.nodes[0].status").value("FAILED"))
                .andExpect(jsonPath("$.nodes[0].errorMessage").value("user check failed"));
    }

    @Test
    void shouldReturnNotFoundForUnknownExecution() throws Exception {
        mockMvc.perform(get("/api/executions/{executionId}", "missing-execution"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXECUTION_NOT_FOUND"));
    }

    private String publishedRule(String prefix, String content) {
        String ruleCode = prefix + "_" + SEQUENCE.incrementAndGet();
        ruleApplicationService.createRule(
                new CreateRuleCommand(ruleCode, "Execution trace", "phase seven"));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand(content, "phase-seven"));
        publishApplicationService.publish(ruleCode, 1);
        return ruleCode;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
