package com.flowpilot.domain.gray.service;

import com.flowpilot.domain.gray.model.GrayPolicy;

public interface GrayManager {

    void start(String ruleCode, int grayVersion, int percentage);

    void updatePercentage(String ruleCode, int percentage);

    void stop(String ruleCode);

    void promote(String ruleCode);

    GrayPolicy getActivePolicy(String ruleCode);
}
