package com.flowpilot.engine;

import com.flowpilot.domain.rule.model.RuleSnapshot;

final class RuleChainId {

    private RuleChainId() {
    }

    static String from(RuleSnapshot snapshot) {
        return snapshot.ruleCode() + "__v" + snapshot.version();
    }
}
