package io.quarkiverse.desktop.showcase.core;

/**
 * Toolkit wide settings applied on the EDT before the main window opens, whatever the main window (CDI beans) : e.g.
 * {@code ui.swing.SwingSetup} installs the look and feel and the Swing snapshot mode settings.
 */
public interface UiSetup {

    void apply();
}
