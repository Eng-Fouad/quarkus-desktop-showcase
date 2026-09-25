package io.quarkiverse.desktop.showcase.core;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Chooses the main window among the CDI beans : the one named by {@code showcase.ui}, or the one with the highest
 * priority (Swing when present, AWT in the awt-only variant).
 */
public final class UiSelection {

    private UiSelection() {
    }

    public static MainWindow select(List<MainWindow> windows, String requested) {
        if (windows.isEmpty()) {
            throw new IllegalStateException("No MainWindow bean");
        }
        MainWindow window = null;
        if (requested != null && !requested.isBlank()) {
            String kind = requested.trim().toLowerCase(Locale.ROOT);
            window = windows.stream().filter(w -> w.kind().equals(kind)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("showcase.ui=" + requested + " : no such main window, available: "
                            + windows.stream().map(MainWindow::kind).sorted().toList()));
        }
        if (window == null) {
            window = windows.stream().max(Comparator.comparingInt(MainWindow::priority)).orElseThrow();
        }
        ShowcaseMode.ui(window.kind());
        return window;
    }
}
