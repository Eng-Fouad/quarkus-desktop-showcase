package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;

/**
 * Helpers shared by the pages of the "Data Transfer &amp; Desktop" category (AWT only : used by the AWT pages and by the
 * Swing pages of {@code pages.swing.desktop}).
 * <p>
 * Nothing here holds AWT objects in static fields : Quarkus initializes application classes at build time, AWT classes
 * at run time.
 */
public final class DesktopSupport {

    /** Value of the checks whose Robot keyboard input was not sent because no showcase window was focused. */
    public static final String NOT_FOCUSED = "skipped: not focused";

    private DesktopSupport() {
    }

    /**
     * {@code true} when side effects outside the showcase windows are allowed ({@code -Dshowcase.sideEffects=true}) :
     * Desktop browse/open/mail/edit, tray balloons, taskbar attention requests.
     */
    public static boolean sideEffects() {
        return Boolean.getBoolean("showcase.sideEffects");
    }

    /**
     * The window containing {@code component} (without {@code SwingUtilities} : AWT only).
     */
    public static Window windowOf(Component component) {
        Component c = component;
        while (c != null && !(c instanceof Window)) {
            c = c.getParent();
        }
        return (Window) c;
    }

    /**
     * The screen location of the point {@code (x, y)} of {@code component} (call it on the EDT, the component showing).
     */
    public static Point onScreen(Component component, int x, int y) {
        Point p = component.getLocationOnScreen();
        return new Point(p.x + x, p.y + y);
    }

    /**
     * The screen location of the center of {@code component}.
     */
    public static Point center(Component component) {
        return onScreen(component, component.getWidth() / 2, component.getHeight() / 2);
    }

    /**
     * A sequence of event names with consecutive repetitions collapsed ({@code a, b, b, b, c} becomes {@code a, b*, c}) :
     * the number of repeated events (drag over, mouse moved...) depends on the timing, their order does not.
     */
    public static String collapse(List<String> events) {
        List<String> out = new ArrayList<>();
        String previous = null;
        boolean repeated = false;
        for (String e : events) {
            if (e.equals(previous)) {
                repeated = true;
                continue;
            }
            if (previous != null) {
                out.add(repeated ? previous + "*" : previous);
            }
            previous = e;
            repeated = false;
        }
        if (previous != null) {
            out.add(repeated ? previous + "*" : previous);
        }
        return out.isEmpty() ? "(none)" : String.join(", ", out);
    }

    /**
     * {@code #RRGGBB} of an RGB value (alpha ignored).
     */
    public static String rgb(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /**
     * Runs {@code action}, returning its result or {@code fallback} when it throws.
     */
    public static <T> T orElse(Callable<T> action, T fallback) {
        try {
            return action.call();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * A check expecting {@code action} to throw an exception of type {@code expected} (its simple name is the value).
     */
    public static Check expectThrows(String name, Class<? extends Throwable> expected, Callable<?> action) {
        try {
            Object value = action.call();
            return Check.fail(name, "expected " + expected.getSimpleName() + " but got " + value);
        } catch (Throwable t) {
            return expected.isInstance(t) ? Check.pass(name, expected.getSimpleName())
                    : Check.fail(name, "expected " + expected.getSimpleName() + " but got " + Checks.describe(t));
        }
    }

    /**
     * Blocks the calling (background) thread : never call it on the EDT.
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    /**
     * Waits (on a background thread) until {@code condition} is true, polling every 20 ms.
     *
     * @return {@code false} on timeout
     */
    public static boolean await(java.util.function.BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) {
                return false;
            }
            sleep(20);
        }
        return true;
    }

    /**
     * Runs {@code action} on the EDT and waits for its result (from a background thread only).
     */
    public static <T> T onEdt(Callable<T> action) throws Exception {
        if (Edt.isEdt()) {
            return action.call();
        }
        try {
            return Edt.supply(action).toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    /**
     * A small lightweight component painting a label in a colored box (for cursor swatches, drop bins...).
     */
    public static Component swatch(String text, int width, int height, int background, int foreground) {
        return new Swatch(text, width, height, background, foreground);
    }

    private static final class Swatch extends Component {

        private final String text;
        private final int width;
        private final int height;
        private final int background;
        private final int foreground;

        Swatch(String text, int width, int height, int background, int foreground) {
            this.text = text;
            this.width = width;
            this.height = height;
            this.background = background;
            this.foreground = foreground;
            setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                g.setColor(new Color(background));
                g.fillRect(0, 0, width, height);
                g.setColor(new Color(0x90A4AE));
                g.drawRect(0, 0, width - 1, height - 1);
                g.setColor(new Color(foreground));
                g.setFont(getFont());
                java.awt.FontMetrics fm = g.getFontMetrics();
                int x = Math.max(3, (width - fm.stringWidth(text)) / 2);
                g.drawString(text, x, (height - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Robot input from a background thread, following the safety rules of the showcase : the mouse pointer is moved back
     * where it was and every pressed button or key is released when the session is closed, and keys are only pressed
     * while a showcase window is focused ({@link Edt#ownsFocus()}).
     */
    public static final class RobotSession implements AutoCloseable {

        /** How long a press waits for the focus to come back to a showcase window (see {@link Edt#awaitFocus}). */
        private static final long FOCUS_WAIT_MILLIS = 500;

        private final Robot robot;
        private final Point pointer;
        private final Set<Integer> buttons = new LinkedHashSet<>();
        private final Set<Integer> keys = new LinkedHashSet<>();
        private String lastMismatch = "";

        public RobotSession() throws AWTException {
            if (Edt.isEdt()) {
                throw new IllegalStateException("Robot input must not run on the EDT");
            }
            robot = new Robot();
            robot.setAutoDelay(0);
            robot.setAutoWaitForIdle(false);
            PointerInfo info = MouseInfo.getPointerInfo();
            pointer = info == null ? null : info.getLocation();
        }

        public Robot robot() {
            return robot;
        }

        /** Where the pointer was when the session started ({@code null} if unknown). */
        public Point savedPointer() {
            return pointer;
        }

        public void move(Point p) {
            robot.mouseMove(p.x, p.y);
        }

        /**
         * Moves from {@code from} to {@code to} in {@code steps} steps, {@code stepMillis} apart.
         */
        public void glide(Point from, Point to, int steps, int stepMillis) {
            for (int i = 1; i <= steps; i++) {
                robot.mouseMove(from.x + (to.x - from.x) * i / steps, from.y + (to.y - from.y) * i / steps);
                sleep(stepMillis);
            }
        }

        /**
         * Presses the mouse buttons {@code mask} ({@link InputEvent#BUTTON1_DOWN_MASK}...) if a showcase window is
         * focused.
         *
         * @return {@code false} (nothing pressed) when no showcase window is focused
         */
        public boolean press(int mask) {
            if (!Edt.awaitFocus(FOCUS_WAIT_MILLIS)) {
                return false;
            }
            robot.mousePress(mask);
            buttons.add(mask);
            return true;
        }

        public void release(int mask) {
            if (buttons.remove(mask)) {
                robot.mouseRelease(mask);
            }
        }

        public void wheel(int notches) {
            robot.mouseWheel(notches);
        }

        /**
         * Presses (and keeps pressed) {@code keyCode} if a showcase window is focused.
         *
         * @return {@code false} (nothing pressed) when no showcase window is focused
         */
        public boolean keyPress(int keyCode) {
            // the previous key events are handled first : AWT translates a key into a character with the keyboard state
            // of the moment it handles the key, so a Shift pressed right after a key could turn "a" into "A"
            robot.delay(20);
            if (!java.awt.EventQueue.isDispatchThread()) {
                robot.waitForIdle();
            }
            if (!Edt.awaitFocus(FOCUS_WAIT_MILLIS)) {
                return false;
            }
            robot.keyPress(keyCode);
            keys.add(keyCode);
            return true;
        }

        public void keyRelease(int keyCode) {
            if (keys.remove(keyCode)) {
                robot.keyRelease(keyCode);
            }
        }

        /**
         * Types {@code keyCode} (press and release) if a showcase window is focused.
         *
         * @return {@code false} (nothing typed) when no showcase window is focused
         */
        public boolean type(int keyCode) {
            if (!keyPress(keyCode)) {
                return false;
            }
            keyRelease(keyCode);
            return true;
        }

        /**
         * The color of the screen pixel at {@code p} (RGB, no alpha).
         */
        public int pixel(Point p) {
            return robot.getPixelColor(p.x, p.y).getRGB() & 0xFFFFFF;
        }

        /**
         * Waits until the screen pixels at {@code probes} have their expected colors ({@code probes} maps a point to an
         * RGB color), i.e. until the page is visible on screen : windows of other applications (or other showcase
         * processes) may cover it. The window of {@code component} is brought to the front between the attempts.
         *
         * @return {@code false} if the page stayed covered
         */
        public boolean awaitVisible(Component component, java.util.Map<Point, Integer> probes) throws Exception {
            for (int attempt = 0; attempt < 8; attempt++) {
                boolean visible = true;
                for (var probe : probes.entrySet()) {
                    int found = pixel(probe.getKey());
                    if (found != (probe.getValue() & 0xFFFFFF)) {
                        visible = false;
                        lastMismatch = rgb(found) + " instead of " + rgb(probe.getValue()) + " at "
                                + probe.getKey().x + "," + probe.getKey().y;
                        break;
                    }
                }
                if (visible) {
                    return true;
                }
                onEdt(() -> {
                    Window window = windowOf(component);
                    if (window != null) {
                        window.toFront();
                    }
                    return null;
                });
                sleep(400);
            }
            return false;
        }

        /**
         * Makes sure that a showcase window is focused, asking again for the focus of the window of {@code component}
         * if another application took it (the page keeps the machine-wide focus lock of the showcase processes, but
         * other applications may still activate a window). Windows may refuse : the caller then skips its input.
         *
         * @return {@code true} if a showcase window is focused
         */
        public boolean ensureFocus(Component component) throws Exception {
            if (Edt.ownsFocus()) {
                return true;
            }
            onEdt(() -> {
                Window window = windowOf(component);
                if (window != null) {
                    window.toFront();
                    window.requestFocus();
                }
                return null;
            });
            return await(Edt::ownsFocus, 2000);
        }

        /**
         * The last pixel that did not have its expected color in {@link #awaitVisible} (for diagnostics : it depends on
         * the windows of the desktop, never put it in a check value).
         */
        public String lastMismatch() {
            return lastMismatch;
        }

        /** Releases everything still pressed. */
        public void releaseAll() {
            for (Integer key : List.copyOf(keys)) {
                keyRelease(key);
            }
            for (Integer mask : List.copyOf(buttons)) {
                release(mask);
            }
        }

        /**
         * Releases everything still pressed and moves the pointer back.
         */
        @Override
        public void close() {
            releaseAll();
            if (pointer != null) {
                robot.mouseMove(pointer.x, pointer.y);
            }
        }
    }
}
