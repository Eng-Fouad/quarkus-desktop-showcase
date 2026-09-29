package io.quarkiverse.desktop.showcase.core;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.runtime.ImageMode;

/**
 * Global state of the showcase run.
 */
public final class ShowcaseMode {

    private static volatile boolean snapshot;
    private static volatile boolean realInput = true;
    private static volatile String ui = "none";
    private static volatile String mainThread = "unknown";

    private ShowcaseMode() {
    }

    /**
     * {@code true} when pages are rendered to be compared : pages must then be deterministic
     * (animations and timers stopped at a fixed state, no caret, no hover, no clock...).
     */
    public static boolean snapshot() {
        return snapshot;
    }

    static void enableSnapshot() {
        snapshot = true;
        realInput = false;
    }

    /**
     * {@code false} while real (operating system or Robot) mouse events are dropped : in snapshot mode, except while a
     * page with {@link FeaturePage#needsFocus()} runs. Events dispatched with {@code Component.dispatchEvent} are never
     * dropped.
     */
    public static boolean realInput() {
        return realInput;
    }

    static void realInput(boolean enabled) {
        realInput = enabled;
    }

    /**
     * {@code JVM} or {@code NATIVE}.
     */
    public static String runtime() {
        return ImageMode.current().isNativeImage() ? "NATIVE" : "JVM";
    }

    /**
     * The name of the thread that starts the application (the {@code StartupEvent} observers) : {@code main} with the JVM
     * and in native executables (on macOS, quarkus-desktop runs the application on a new thread named {@code main}, the
     * first thread of the process running the Cocoa event loop, as with the {@code java} launcher).
     */
    public static String mainThread() {
        return mainThread;
    }

    /**
     * Records the thread that starts the application (called by a {@code StartupEvent} observer).
     */
    public static void mainThread(Thread thread) {
        mainThread = thread.getName();
    }

    /**
     * The main window implementation : {@code swing} or {@code awt}.
     */
    public static String ui() {
        return ui;
    }

    static void ui(String kind) {
        ui = kind;
    }

    /**
     * The Java2D pipeline rendering on screen : the simple class name of the default {@link GraphicsConfiguration}
     * ({@code D3DGraphicsConfig}, {@code Win32GraphicsConfig}, {@code XRGraphicsConfig}, {@code X11GraphicsConfig},
     * {@code GLXGraphicsConfig}...) followed by the pipeline system properties that are set. The runs of a comparison
     * must use the same one, which a system property or a missing native library could change.
     */
    public static String pipeline() {
        if (GraphicsEnvironment.isHeadless()) {
            return "headless";
        }
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();
        List<String> flags = new ArrayList<>();
        for (String flag : List.of("d3d", "noddraw", "opengl", "xrender", "metal", "pmoffscreen")) {
            String value = System.getProperty("sun.java2d." + flag);
            if (value != null) {
                flags.add(flag + "=" + value);
            }
        }
        return gc.getClass().getSimpleName() + (flags.isEmpty() ? "" : " " + String.join(" ", flags));
    }
}
