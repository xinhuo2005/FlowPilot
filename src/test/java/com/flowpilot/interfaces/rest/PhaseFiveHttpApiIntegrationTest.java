package com.flowpilot.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhaseFiveHttpApiIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldManageAndExecuteAPublishedRuleThroughHttp() throws Exception {
        String ruleCode = newRuleCode("HTTP_EXECUTE");

        createRule(ruleCode);
        createVersion(ruleCode, "THEN(userCheck, stockCheck, createOrder)")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/rules/{ruleCode}/versions/{version}/publish", ruleCode, 1))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/flows/{ruleCode}/execute", ruleCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "routingKey", "customer-1001",
                                "variables", Map.of("userValid", true, "stockAvailable", true)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").isNotEmpty())
                .andExpect(jsonPath("$.ruleCode").value(ruleCode))
                .andExpect(jsonPath("$.ruleVersion").value(1))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.orderCreated").value(true));

        mockMvc.perform(get("/api/rules/{ruleCode}", ruleCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(1));

        mockMvc.perform(get("/api/rules/{ruleCode}/versions", ruleCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void shouldExposeGrayLifecycleThroughHttp() throws Exception {
        String ruleCode = newRuleCode("HTTP_GRAY");
        createRule(ruleCode);
        createVersion(ruleCode, "THEN(userCheck, createOrder)");
        createVersion(ruleCode, "THEN(userCheck, notify)");
        mockMvc.perform(post("/api/rules/{ruleCode}/versions/{version}/publish", ruleCode, 1))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/rules/{ruleCode}/gray", ruleCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("grayVersion", 2, "percentage", 10))))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/rules/{ruleCode}/gray", ruleCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("percentage", 30))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/rules/{ruleCode}/gray", ruleCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseVersion").value(1))
                .andExpect(jsonPath("$.grayVersion").value(2))
                .andExpect(jsonPath("$.percentage").value(30));
        mockMvc.perform(delete("/api/rules/{ruleCode}/gray", ruleCode))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnStableErrorContractAndExecutionId() throws Exception {
        String ruleCode = newRuleCode("HTTP_ERROR");
        createRule(ruleCode);
        createVersion(ruleCode, "THEN(userCheck)");
        mockMvc.perform(post("/api/rules/{ruleCode}/versions/{version}/publish", ruleCode, 1))
                .andExpect(status().isNoContent());

        String body = mockMvc.perform(post("/api/flows/{ruleCode}/execute", ruleCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "routingKey", "rejected-customer",
                                "variables", Map.of("userValid", false)))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("RULE_EXECUTION_FAILED"))
                .andExpect(jsonPath("$.executionId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode error = objectMapper.readTree(body);
        assertThat(error.path("executionId").asText()).isNotBlank();
    }

    @Test
    void shouldRejectInvalidRequestAndReportMissingRule() throws Exception {
        mockMvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ruleCode", "", "ruleName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/rules/{ruleCode}", "MISSING_RULE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RULE_NOT_FOUND"));
    }

    private void createRule(String ruleCode) throws Exception {
        mockMvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "ruleCode", ruleCode,
                                "ruleName", "HTTP integration rule",
                                "description", "phase five"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
    }

    private org.springframework.test.web.servlet.ResultActions createVersion(
            String ruleCode,
            String ruleContent
    ) throws Exception {
        return mockMvc.perform(post("/api/rules/{ruleCode}/versions", ruleCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("ruleContent", ruleContent, "createdBy", "phase-five-test"))));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String newRuleCode(String prefix) {
        return prefix + "_" + SEQUENCE.incrementAndGet();
    }
}
