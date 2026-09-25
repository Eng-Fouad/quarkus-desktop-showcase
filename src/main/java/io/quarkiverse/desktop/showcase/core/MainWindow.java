package io.quarkiverse.desktop.showcase.core;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.List;

/**
 * The main window : page navigation and the selected page. Two CDI implementations exist, {@code ui.swing.SwingMainWindow}
 * (JFrame, JTree, JSplitPane : the default) and {@code ui.awt.AwtMainWindow} (Frame, java.awt.List, CardLayout : used
 * when Swing is absent, i.e. in the awt-only variant, or with {@code -Dshowcase.ui=awt}).
 * <p>
 * Every method is invoked on the EDT.
 */
public interface MainWindow {

    /** Width of the page frame (at least). */
    int PAGE_WIDTH = 1060;
    /** Height of the page frame (at least). */
    int PAGE_HEIGHT = 760;
    /** Padding between the page frame and the page content. */
    int PAGE_PADDING = 16;
    /** Size and position of the window in snapshot mode. */
    int SNAPSHOT_X = 40;
    int SNAPSHOT_Y = 40;
    int SNAPSHOT_WIDTH = 1400;
    int SNAPSHOT_HEIGHT = 900;

    /**
     * {@code swing} or {@code awt}.
     */
    String kind();

    /**
     * The implementation with the highest priority is used (unless {@code showcase.ui} names one).
     */
    int priority();

    /**
     * Creates and shows the window.
     */
    void open(List<FeaturePage> pages);

    Window window();

    /**
     * The whole content of the window, without the native decorations : rendered as {@code _main-window.png}.
     */
    Component rootContent();

    /**
     * The container of the page content (white background, {@link #PAGE_PADDING} around the content, at least
     * {@link #PAGE_WIDTH} x {@link #PAGE_HEIGHT}) : rendered as {@code <page-id>.png}.
     */
    Container pageFrame();

    /**
     * Selects {@code page} in the navigation and shows it (builds it) if it is not the current page.
     */
    void select(FeaturePage page);

    Component currentContent();

    /**
     * The exception thrown by {@link FeaturePage#build()} of the current page, if any.
     */
    Throwable currentError();

    /**
     * Moves the focus out of the page, to a component that paints no focus decoration.
     */
    void releaseFocus();

    /**
     * {@code true} once no page component owns the focus (the focus sink owns it, or the window is not focused).
     */
    boolean focusReleased();

    /**
     * Disposes the current page, if any.
     */
    void clear();
}
