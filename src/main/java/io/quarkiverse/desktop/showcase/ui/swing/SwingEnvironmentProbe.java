package io.quarkiverse.desktop.showcase.ui.swing;

import java.util.Map;

import javax.swing.UIManager;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.EnvironmentProbe;

/**
 * The look and feel of the run (Swing variant only).
 */
@Singleton
public class SwingEnvironmentProbe implements EnvironmentProbe {

    @Override
    public void describe(Map<String, Object> environment) {
        environment.put("lookAndFeel", UIManager.getLookAndFeel() == null ? "none"
                : UIManager.getLookAndFeel().getClass().getName());
    }
}
