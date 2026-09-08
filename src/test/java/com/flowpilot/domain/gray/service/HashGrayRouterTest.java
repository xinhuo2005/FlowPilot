package com.flowpilot.domain.gray.service;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashGrayRouterTest {

    private final HashGrayRouter router = new HashGrayRouter();

    @Test
    void routesSameKeyToSameVersionOneHundredTimes() {
        int firstResult = router.route("user-10001", 1, 2, 10);

        assertThat(IntStream.range(0, 100)
                .map(ignored -> router.route("user-10001", 1, 2, 10)))
                .allMatch(version -> version == firstResult);
    }

    @Test
    void supportsZeroAndOneHundredPercentAlgorithmBoundaries() {
        assertThat(router.route("any-key", 1, 2, 0)).isEqualTo(1);
        assertThat(router.route("any-key", 1, 2, 100)).isEqualTo(2);
    }

    @Test
    void grayRatioIsCloseToConfiguredPercentage() {
        long grayCount = IntStream.range(0, 10_000)
                .mapToObj(index -> "routing-key-" + index)
                .filter(key -> router.route(key, 1, 2, 10) == 2)
                .count();

        assertThat(grayCount).isBetween(850L, 1_150L);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> router.route(" ", 1, 2, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> router.route("key", 1, 1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> router.route("key", 1, 2, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> router.route("key", 1, 2, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
