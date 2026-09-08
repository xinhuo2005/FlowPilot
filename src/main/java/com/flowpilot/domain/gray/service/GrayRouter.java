package com.flowpilot.domain.gray.service;

public interface GrayRouter {

    /**
     * Routes a key using the complete algorithm boundary of 0..100. Persisted active gray policies
     * remain restricted to 1..99; stop and promote represent the two endpoint lifecycle operations.
     */
    int route(String routingKey, int baseVersion, int grayVersion, int percentage);
}
