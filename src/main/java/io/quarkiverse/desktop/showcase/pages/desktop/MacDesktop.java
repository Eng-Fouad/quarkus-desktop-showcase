package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Helpers of the macOS pages (AWT only : used by the AWT pages and by the Swing pages of the macOS desktop
 * integration). The macOS pages exist on every operating system (the catalogue is the same everywhere) : elsewhere they
 * only state that they are not available, with deterministic checks.
 */
public final class MacDesktop {

    /** The value of the {@code availability} check of a macOS page on another operating system. */
    public static final String NOT_AVAILABLE = "not available on this OS";

    private MacDesktop() {
    }

    /**
     * The content of a macOS page on another operating system : a heading, a text and the checks (an
     * {@code availability} check first).
     */
    public static Component notAvailable(String title, String text, List<Check> extra) {
        List<Check> checks = new ArrayList<>();
        checks.add(Check.info("availability", NOT_AVAILABLE));
        checks.addAll(extra);
        return Ui.column(14, Ui.heading(title), Ui.text(text, 1000), ChecksView.table(title, checks));
    }

    /**
     * {@code true} when native dialogs and windows that need the user may be shown ({@code -Dshowcase.interactive=true}) :
     * the macOS native dialogs (open and save panels, print panels) cannot be closed programmatically.
     */
    public static boolean interactive() {
        return Boolean.getBoolean("showcase.interactive");
    }
}
