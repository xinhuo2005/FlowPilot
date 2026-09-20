package com.flowpilot.application;

public record StartGrayCommand(Integer grayVersion, Integer percentage) {

    public StartGrayCommand {
        if (grayVersion == null || grayVersion < 1) {
            throw new IllegalArgumentException("grayVersion must be positive");
        }
        if (percentage == null || percentage < 1 || percentage > 99) {
            throw new IllegalArgumentException("percentage must be between 1 and 99");
        }
    }
}
