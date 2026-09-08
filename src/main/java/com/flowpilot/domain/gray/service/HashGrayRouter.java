package com.flowpilot.domain.gray.service;

public final class HashGrayRouter implements GrayRouter {

    private static final int BUCKET_COUNT = 100;

    @Override
    public int route(String routingKey, int baseVersion, int grayVersion, int percentage) {
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("routingKey must not be blank");
        }
        if (baseVersion < 1 || grayVersion < 1) {
            throw new IllegalArgumentException("rule versions must be positive");
        }
        if (baseVersion == grayVersion) {
            throw new IllegalArgumentException("baseVersion and grayVersion must differ");
        }
        if (percentage < 0 || percentage > BUCKET_COUNT) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }

        int bucket = Math.floorMod(routingKey.hashCode(), BUCKET_COUNT);
        return bucket < percentage ? grayVersion : baseVersion;
    }
}
