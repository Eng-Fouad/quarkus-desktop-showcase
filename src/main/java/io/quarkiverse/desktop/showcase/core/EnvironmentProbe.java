package io.quarkiverse.desktop.showcase.core;

import java.util.Map;

/**
 * Adds environment keys that core cannot compute without {@code javax.swing} (e.g. the look and feel) : CDI beans
 * found by {@link Environment#describe()}.
 */
public interface EnvironmentProbe {

    void describe(Map<String, Object> environment);
}
