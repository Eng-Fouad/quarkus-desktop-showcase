package io.quarkiverse.desktop.showcase.ui.swing;

import java.util.List;

import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.UiSetup;

/**
 * Swing settings of the showcase (default variant, whatever the main window) : the Metal look and feel with the Ocean
 * theme (whatever {@code swing.defaultlaf} says), and in snapshot mode no tooltips and no blinking carets.
 */
@Singleton
public class SwingSetup implements UiSetup {

    /** Text components whose caret must not blink in snapshot mode. */
    public static final List<String> TEXT_COMPONENTS = List.of("TextField", "FormattedTextField", "PasswordField",
            "TextArea", "TextPane", "EditorPane");

    @Override
    public void apply() {
        installLookAndFeel();
        if (ShowcaseMode.snapshot()) {
            ToolTipManager.sharedInstance().setEnabled(false);
            for (String key : TEXT_COMPONENTS) {
                UIManager.put(key + ".caretBlinkRate", 0);
            }
        }
    }

    /**
     * The look and feel of the showcase (and of the pages that do not test look and feels) : Metal, Ocean theme. A page
     * changing the look and feel calls it in {@code dispose()}, then updates the component trees of its windows.
     */
    public static void installLookAndFeel() {
        try {
            MetalLookAndFeel.setCurrentTheme(new OceanTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException(e);
        }
    }
}
