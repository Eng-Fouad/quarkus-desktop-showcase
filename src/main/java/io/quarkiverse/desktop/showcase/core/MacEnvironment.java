package io.quarkiverse.desktop.showcase.core;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.awt.SystemColor;
import java.awt.Window;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The macOS part of the environment of a run ({@link Environment#describe()} keys {@code macos.*} and
 * {@code property.*}) : the macOS version, whether AWT owns the application (the first thread of the process is then the
 * {@code AppKit Thread}), the system properties that the {@code java} launcher sets or that change the rendering, the
 * system colors, and the privacy permissions (TCC) that Robot needs.
 */
public final class MacEnvironment {

    /**
     * The system properties read by the macOS AWT that matter for a comparison (pinned by tools/Snapshot.java, or set by
     * the java launcher only : {@code sun.java.launcher}).
     */
    static final List<String> PROPERTIES = List.of("apple.awt.application.name", "apple.awt.application.appearance",
            "apple.laf.useScreenMenuBar", "apple.awt.UIElement", "sun.java.launcher", "sun.java2d.metal",
            "sun.java2d.opengl", "javafx.embed.singleThread");

    /** The value of the permission keys when they are not probed. */
    static final String NOT_PROBED = "not probed (-Dshowcase.robot=true)";

    private static volatile String screenCapture = NOT_PROBED;
    private static volatile String input = NOT_PROBED;

    private MacEnvironment() {
    }

    /**
     * Adds the macOS keys (call it on the EDT, on macOS only).
     */
    static void describe(Map<String, Object> env) {
        env.put("macos.version", System.getProperty("os.version"));
        env.put("macos.arch", System.getProperty("os.arch"));
        // AWT owns the application (NSApplicationAWT) : it renamed the first thread of the process "AppKit Thread"
        // (LWCToolkit.installToolkitThreadInJava) ; not when AWT runs embedded in JavaFX
        env.put("macos.appKitThread", Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> "AppKit Thread".equals(t.getName())));
        for (String key : PROPERTIES) {
            env.put("property." + key, String.valueOf(System.getProperty(key)));
        }
        env.put("macos.systemColors", List.of(Checks.argb(SystemColor.textHighlight.getRGB()),
                Checks.argb(SystemColor.control.getRGB()), Checks.argb(SystemColor.controlText.getRGB()),
                Checks.argb(SystemColor.window.getRGB())).toString());
        env.put("macos.tcc.screenCapture", screenCapture);
        env.put("macos.tcc.input", input);
    }

    /**
     * Probes the privacy permissions that Robot needs on macOS (Screen Recording for screen captures, Accessibility for
     * input events), granted to the application that started the showcase (the terminal) : with
     * {@code -Dshowcase.robot=true} only (it shows a small window and moves the mouse pointer by a few pixels, then
     * back). Call it before the user interface starts, off the EDT.
     */
    public static void probePermissions() {
        if (!Platforms.isMac() || !Boolean.getBoolean("showcase.robot")) {
            return;
        }
        try {
            Robot robot = new Robot();
            AtomicReference<Window> window = new AtomicReference<>();
            EventQueue.invokeAndWait(() -> {
                Window w = new Window(null);
                w.setBackground(new Color(0x3A7B5C));
                w.setBounds(60, 60, 40, 40);
                w.setVisible(true);
                window.set(w);
            });
            try {
                robot.waitForIdle();
                robot.delay(300);
                Color pixel = robot.getPixelColor(80, 80);
                // without the permission, macOS captures the desktop without the windows of the other applications [I]
                screenCapture = String.valueOf((pixel.getRGB() & 0xFFFFFF) == 0x3A7B5C);
            } finally {
                EventQueue.invokeAndWait(() -> window.get().dispose());
            }
            PointerInfo pointer = MouseInfo.getPointerInfo();
            if (pointer == null) {
                input = "no pointer";
                return;
            }
            Point start = pointer.getLocation();
            try {
                robot.mouseMove(start.x + 7, start.y + 5);
                robot.waitForIdle();
                robot.delay(100);
                Point moved = MouseInfo.getPointerInfo().getLocation();
                // without the permission, macOS ignores the events that Robot posts
                input = String.valueOf(moved.x == start.x + 7 && moved.y == start.y + 5);
            } finally {
                robot.mouseMove(start.x, start.y);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            screenCapture = "error: " + Checks.describe(e);
        }
    }
}
