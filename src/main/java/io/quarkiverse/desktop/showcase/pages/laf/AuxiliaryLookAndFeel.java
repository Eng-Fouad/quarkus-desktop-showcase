package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.plaf.ComponentUI;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * An auxiliary look and feel ({@code UIManager.addAuxiliaryLookAndFeel}) : once installed, the UI manager creates the
 * delegates through the multiplexing look and feel ({@code javax.swing.plaf.multi.MultiLookAndFeel}, instantiated by
 * class name), whose {@code Multi*UI} delegates (created by class name + reflective {@code createUI}) combine the
 * delegate of the default look and feel with the ones of the auxiliary look and feels. This one marks buttons, labels,
 * sliders and progress bars with a corner triangle and a client property.
 * <p>
 * Installed by instance ; its {@link MarkerUI} is loaded by class name : {@code @RegisterForReflection}.
 */
public class AuxiliaryLookAndFeel extends LookAndFeel {

    /** Client property set by {@link MarkerUI#installUI}. */
    public static final String INSTALLED = "showcase.auxiliary.installed";

    @Override
    public String getName() {
        return "Showcase auxiliary";
    }

    @Override
    public String getID() {
        return "ShowcaseAuxiliary";
    }

    @Override
    public String getDescription() {
        return "Auxiliary look and feel of the quarkus-desktop showcase";
    }

    @Override
    public boolean isNativeLookAndFeel() {
        return false;
    }

    @Override
    public boolean isSupportedLookAndFeel() {
        return true;
    }

    @Override
    public UIDefaults getDefaults() {
        // an auxiliary look and feel supports a few ids only : no error report for the others
        UIDefaults defaults = new UIDefaults() {
            @Override
            protected void getUIError(String msg) {
            }
        };
        String marker = MarkerUI.class.getName();
        for (String id : new String[] { "ButtonUI", "ToggleButtonUI", "CheckBoxUI", "LabelUI", "SliderUI",
                "ProgressBarUI" }) {
            defaults.put(id, marker);
        }
        return defaults;
    }

    /**
     * Paints a small corner triangle over the component (after the default delegate) and records its installation.
     */
    @RegisterForReflection
    public static class MarkerUI extends ComponentUI {

        public static final int MARKER = 0xFFFB8C00;

        public MarkerUI() {
        }

        public static ComponentUI createUI(JComponent c) {
            return new MarkerUI();
        }

        @Override
        public void installUI(JComponent c) {
            c.putClientProperty(INSTALLED, Boolean.TRUE);
        }

        @Override
        public void uninstallUI(JComponent c) {
            c.putClientProperty(INSTALLED, null);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            // never fill the background : the default delegate painted the component
            paint(g, c);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            int w = c.getWidth();
            g.setColor(new Color(MARKER, true));
            g.fillPolygon(new int[] { w - 9, w, w }, new int[] { 0, 0, 9 }, 3);
        }
    }
}
