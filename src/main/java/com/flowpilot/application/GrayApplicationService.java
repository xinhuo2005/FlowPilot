package com.flowpilot.application;

import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.service.GrayManager;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class GrayApplicationService {

    private final GrayManager grayManager;

    public GrayApplicationService(GrayManager grayManager) {
        this.grayManager = grayManager;
    }

    public void startGray(String ruleCode, StartGrayCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        grayManager.start(ruleCode, command.grayVersion(), command.percentage());
    }

    public void updatePercentage(String ruleCode, int percentage) {
        grayManager.updatePercentage(ruleCode, percentage);
    }

    public void stopGray(String ruleCode) {
        grayManager.stop(ruleCode);
    }

    public void promote(String ruleCode) {
        grayManager.promote(ruleCode);
    }

    public GrayPolicy getPolicy(String ruleCode) {
        return grayManager.getActivePolicy(ruleCode);
    }
}
