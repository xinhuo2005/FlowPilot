package com.flowpilot.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StartGrayCommand(
        @NotNull @Min(1) Integer grayVersion,
        @NotNull @Min(1) @Max(99) Integer percentage
) {

    public StartGrayCommand {
        if (grayVersion == null || grayVersion < 1) {
            throw new IllegalArgumentException("grayVersion must be positive");
        }
        if (percentage == null || percentage < 1 || percentage > 99) {
            throw new IllegalArgumentException("percentage must be between 1 and 99");
        }
    }
}
