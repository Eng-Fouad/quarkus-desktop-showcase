package io.quarkiverse.desktop.showcase.pages.laf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;

/**
 * The global look and feel state a look and feel page changes, saved in {@code build()} and restored in
 * {@code dispose()} : a page must not leak its look and feel (nor its theme, developer defaults, auxiliary look and
 * feels, installed look and feel list or default decoration flags) into the other pages.
 * <p>
 * The previous look and feel <em>instance</em> is installed again (no reflection needed to restore it), after its Metal
 * theme, so that it recomputes the same defaults as before.
 */
final class LafState {

    private final LookAndFeel lookAndFeel;
    private final MetalTheme metalTheme;
    private final List<LookAndFeel> auxiliary;
    private final LookAndFeelInfo[] installed;
    private final boolean frameDecorated;
    private final boolean dialogDecorated;
    /** Developer defaults ({@code UIManager.put}) changed by the page, with their previous values. */
    private final Map<Object, Object> developerDefaults = new LinkedHashMap<>();

    private LafState() {
        lookAndFeel = UIManager.getLookAndFeel();
        metalTheme = MetalLookAndFeel.getCurrentTheme();
        LookAndFeel[] aux = UIManager.getAuxiliaryLookAndFeels();
        auxiliary = aux == null ? List.of() : List.of(aux);
        installed = UIManager.getInstalledLookAndFeels();
        frameDecorated = JFrame.isDefaultLookAndFeelDecorated();
        dialogDecorated = JDialog.isDefaultLookAndFeelDecorated();
    }

    static LafState save() {
        return new LafState();
    }

    /**
     * {@code UIManager.put(key, value)}, the previous developer value being restored by {@link #restore()}.
     */
    void put(Object key, Object value) {
        if (!developerDefaults.containsKey(key)) {
            developerDefaults.put(key, UIManager.get(key) == UIManager.getLookAndFeelDefaults().get(key)
                    ? null
                    : UIManager.get(key));
        }
        UIManager.put(key, value);
    }

    LookAndFeel previousLookAndFeel() {
        return lookAndFeel;
    }

    /**
     * Restores everything saved, the look and feel last. Never throws : a failure is returned as text (the page
     * logs it, the next page must still run).
     */
    String restore() {
        List<String> errors = new ArrayList<>();
        try {
            LookAndFeel[] current = UIManager.getAuxiliaryLookAndFeels();
            if (current != null) {
                for (LookAndFeel aux : current) {
                    if (!auxiliary.contains(aux)) {
                        UIManager.removeAuxiliaryLookAndFeel(aux);
                    }
                }
            }
        } catch (RuntimeException e) {
            errors.add("auxiliary: " + e);
        }
        try {
            if (!Arrays.equals(UIManager.getInstalledLookAndFeels(), installed)) {
                UIManager.setInstalledLookAndFeels(installed);
            }
        } catch (RuntimeException e) {
            errors.add("installed: " + e);
        }
        JFrame.setDefaultLookAndFeelDecorated(frameDecorated);
        JDialog.setDefaultLookAndFeelDecorated(dialogDecorated);
        developerDefaults.forEach(UIManager::put);
        developerDefaults.clear();
        try {
            if (metalTheme != null) {
                MetalLookAndFeel.setCurrentTheme(metalTheme);
            }
            if (lookAndFeel != null && UIManager.getLookAndFeel() != lookAndFeel) {
                UIManager.setLookAndFeel(lookAndFeel);
            } else if (lookAndFeel instanceof MetalLookAndFeel) {
                // same instance but maybe another theme : recompute the defaults
                UIManager.setLookAndFeel(lookAndFeel);
            }
        } catch (UnsupportedLookAndFeelException | RuntimeException e) {
            errors.add("look and feel: " + e);
        }
        return errors.isEmpty() ? null : String.join("; ", errors);
    }
}
