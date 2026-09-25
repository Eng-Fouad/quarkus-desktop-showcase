package io.quarkiverse.desktop.showcase.core;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A page of the showcase, exercising one AWT, Java2D or Swing feature area.
 * <p>
 * Implementations are CDI beans ({@code @Singleton}) discovered through {@code Instance<FeaturePage>}.
 * Every method is invoked on the AWT event dispatch thread (EDT).
 * <p>
 * In snapshot mode ({@link ShowcaseMode#snapshot()}), the rendered page is saved as an image and compared between
 * JVM and native mode : a page must render the same pixels on every run (no running animation or timer captured at a
 * random frame, no clock, no random values, no caret, no hover, no focus decoration).
 * <p>
 * Packaging rule : a page referencing {@code javax.swing} lives under {@code ...showcase.pages.swing.**} or
 * {@code ...showcase.pages.laf.**} (excluded from the awt-only variant), every other page must only use AWT.
 */
public interface FeaturePage {

    /**
     * Unique, stable and file-name safe identifier ({@code group-name}, lower case, dashes).
     */
    String id();

    String title();

    /**
     * One of {@link Categories#ORDER}.
     */
    String category();

    /**
     * Order within the category.
     */
    default int order() {
        return 100;
    }

    /**
     * Builds a fresh content component for this page : a lightweight AWT {@link java.awt.Container} or AWT
     * components for AWT pages, a {@code JPanel} for Swing pages.
     * Checks can be attached to any component of the content with {@link Checks#attach(Component, java.util.List)} or
     * shown with {@link ChecksView}.
     */
    Component build() throws Exception;

    /**
     * Completes once the content is fully rendered (e.g. images decoded, background work done, secondary window shown).
     */
    default CompletionStage<?> ready(Component content) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Additional images to compare, for content outside the page component (dialogs, other windows, popups, rendered
     * print pages, ...). Invoked after {@link #ready(Component)} completed. Keys must be file-name safe.
     */
    default CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(Map.of());
    }

    /**
     * {@code true} for a page that shows where the runtime legitimately makes a difference (JVM or native image) :
     * its images and check values are expected to differ between the runs, and are reported as {@code EXPECTED} by
     * tools/Compare.java. Failed checks and errors are still reported.
     */
    default boolean runtimeDependent() {
        return false;
    }

    /**
     * {@code true} for a page that needs its window to be the focused window (keyboard input, Robot, focus
     * traversal...). In snapshot mode, such a page runs while the showcase holds a machine-wide lock (so that concurrent
     * showcase processes never fight over the focus), after its window was brought to the front, and receives real
     * mouse and keyboard events. The page must still check that one of its own windows is focused right before each
     * Robot key press ({@link Edt#ownsFocus()}), and record {@code "skipped: not focused"} otherwise.
     */
    default boolean needsFocus() {
        return false;
    }

    /**
     * Releases resources (stops timers, disposes windows, restores the clipboard, ...) when the page is left.
     */
    default void dispose(Component content) {
    }
}
