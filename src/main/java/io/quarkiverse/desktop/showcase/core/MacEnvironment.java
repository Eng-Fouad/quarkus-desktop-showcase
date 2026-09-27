package io.quarkiverse.desktop.showcase.core;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.SystemColor;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /** The colors of the two windows of the Screen Recording probe (a desktop never shows both, side by side). */
    private static final int[] PROBE_COLORS = { 0x3A7B5C, 0xC4586E };
    /** The difference allowed per channel : the color management of macOS changes a captured color by a few levels. */
    private static final int PROBE_TOLERANCE = 16;

    private MacEnvironment() {
    }

    /**
     * {@code true} when the Screen Recording permission was probed ({@code -Dshowcase.robot=true}) and is denied : the
     * screen pixels never show the windows of the showcase, waiting for them is pointless.
     */
    static boolean screenCaptureDenied() {
        return "false".equals(screenCapture);
    }

    /**
     * {@code true} when the Accessibility permission was probed ({@code -Dshowcase.robot=true}) and is denied : macOS
     * drops the events that Robot posts, sending them is pointless.
     */
    static boolean inputDenied() {
        return "false".equals(input);
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
     * {@code -Dshowcase.robot=true} only (it shows two small windows and moves the mouse pointer by a few pixels, then
     * back). Call it before the user interface starts, off the EDT.
     * <p>
     * A denial switches off work for the whole run ({@link RobotSession} stops its pixel waits at the first mismatch and
     * sends no input), so a denial is only recorded when the probe is conclusive : the probe windows are always on top,
     * inside the usable screen area, and their pixels are polled for 1.5 s (a slow first paint) with a tolerance ; the
     * pointer move is tried three times, away from the screen edges.
     */
    public static void probePermissions() {
        if (!Platforms.isMac() || !Boolean.getBoolean("showcase.robot")) {
            return;
        }
        try {
            Robot robot = new Robot();
            screenCapture = String.valueOf(probeScreenCapture(robot));
            input = probeInput(robot);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            screenCapture = "error: " + Checks.describe(e);
        }
    }

    /**
     * Shows two windows of different colors side by side and reads the pixel in the middle of each : without the
     * permission, macOS captures the desktop without the windows of the other applications [I].
     */
    private static boolean probeScreenCapture(Robot robot) throws Exception {
        List<Window> windows = new ArrayList<>();
        List<Point> centers = new ArrayList<>();
        EventQueue.invokeAndWait(() -> {
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            for (int i = 0; i < PROBE_COLORS.length; i++) {
                Color color = new Color(PROBE_COLORS[i]);
                Window w = new Window(null) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(color);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                w.setBackground(color);
                w.setFocusableWindowState(false);
                w.setAutoRequestFocus(false);
                w.setAlwaysOnTop(true);
                w.setBounds(usable.x + 60 + 40 * i, usable.y + 60, 40, 40);
                w.setVisible(true);
                windows.add(w);
                centers.add(new Point(usable.x + 80 + 40 * i, usable.y + 80));
            }
        });
        try {
            robot.waitForIdle();
            long deadline = System.nanoTime() + 1_500_000_000L;
            while (true) {
                boolean shown = true;
                for (int i = 0; i < PROBE_COLORS.length && shown; i++) {
                    Point p = centers.get(i);
                    shown = close(robot.getPixelColor(p.x, p.y).getRGB(), PROBE_COLORS[i]);
                }
                if (shown) {
                    return true;
                }
                if (System.nanoTime() - deadline > 0) {
                    return false;
                }
                robot.delay(100);
            }
        } finally {
            EventQueue.invokeAndWait(() -> windows.forEach(Window::dispose));
        }
    }

    private static boolean close(int argb, int rgb) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((argb >> shift) & 0xFF) - ((rgb >> shift) & 0xFF)) > PROBE_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves the pointer by a few pixels (away from the edges of its screen) and checks that it moved : without the
     * permission, macOS ignores the events that Robot posts. Three tries (the user may move the mouse meanwhile), the
     * pointer moved back each time.
     */
    private static String probeInput(Robot robot) {
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer == null) {
            return "no pointer";
        }
        Point start = pointer.getLocation();
        Rectangle screen = pointer.getDevice().getDefaultConfiguration().getBounds();
        int dx = start.x + 8 < screen.x + screen.width ? 7 : -7;
        int dy = start.y + 6 < screen.y + screen.height ? 5 : -5;
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                robot.mouseMove(start.x + dx, start.y + dy);
                robot.waitForIdle();
                robot.delay(100);
                PointerInfo moved = MouseInfo.getPointerInfo();
                if (moved != null && moved.getLocation().equals(new Point(start.x + dx, start.y + dy))) {
                    return "true";
                }
                robot.mouseMove(start.x, start.y);
                robot.delay(100);
            }
            return "false";
        } finally {
            robot.mouseMove(start.x, start.y);
        }
    }
}
