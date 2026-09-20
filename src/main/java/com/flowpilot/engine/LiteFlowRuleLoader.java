package com.flowpilot.engine;

import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.service.RuleLoader;
import com.flowpilot.exception.RuleLoadException;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.flow.FlowBus;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class LiteFlowRuleLoader implements RuleLoader {

    private final ConcurrentMap<String, String> loadedChecksums = new ConcurrentHashMap<>();

    @Override
    public String load(RuleSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        String chainId = RuleChainId.from(snapshot);

        loadedChecksums.compute(chainId, (ignored, loadedChecksum) -> {
            if (loadedChecksum != null && !loadedChecksum.equals(snapshot.checksum())) {
                throw new RuleLoadException(
                        "rule version " + chainId + " was already loaded with a different checksum",
                        null);
            }
            if (loadedChecksum == null) {
                buildChain(chainId, snapshot.ruleContent());
                return snapshot.checksum();
            }
            return loadedChecksum;
        });
        return chainId;
    }

    public void validateLoad(String ruleCode, String ruleContent) {
        String validationChainId = "__validation__" + normalize(ruleCode) + "__" + UUID.randomUUID();
        try {
            buildChain(validationChainId, ruleContent);
        } finally {
            if (FlowBus.containChain(validationChainId)) {
                FlowBus.removeChain(validationChainId);
            }
        }
    }

    private void buildChain(String chainId, String ruleContent) {
        try {
            LiteFlowChainELBuilder.createChain()
                    .setChainId(chainId)
                    .setEL(ruleContent)
                    .build();
        } catch (RuntimeException exception) {
            throw new RuleLoadException("failed to load LiteFlow chain " + chainId, exception);
        }
    }

    private String normalize(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return "anonymous";
        }
        return ruleCode.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
