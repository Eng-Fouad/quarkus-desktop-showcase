package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.RobotSession;

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

    /** Side effect : {@code Desktop.browse} and {@code Desktop.mail} (opens the browser and the mail client). */
    public static final String BROWSE = "browse";
    /** Side effect : {@code TrayIcon.displayMessage} (a notification of the desktop). */
    public static final String TRAY_BALLOON = "tray-balloon";
    /** Side effect : {@code Taskbar.requestWindowUserAttention} (flashes the taskbar button). */
    public static final String ATTENTION = "attention";
    /** Side effect : the application features of {@code Taskbar} (badge, progress, attention of the dock icon). */
    public static final String TASKBAR = "taskbar";
    /** Side effect : macOS Dock icon, badge and menu, default menu bar, {@code Desktop.requestForeground}. */
    public static final String DOCK = "dock";

    private DesktopSupport() {
    }

    /**
     * {@code true} when every side effect outside the showcase windows is allowed ({@code -Dshowcase.sideEffects=true}) :
     * Desktop browse/mail, tray balloons, taskbar attention requests, dock changes.
     */
    public static boolean sideEffects() {
        return "true".equalsIgnoreCase(System.getProperty("showcase.sideEffects", "").trim());
    }

    /**
     * {@code true} when the side effect {@code name} ({@link #BROWSE}, {@link #TRAY_BALLOON}, {@link #ATTENTION},
     * {@link #TASKBAR}, {@link #DOCK}) is allowed : {@code -Dshowcase.sideEffects=true} allows all of them, a comma
     * separated list of names only these (e.g. {@code -Dshowcase.sideEffects=tray-balloon} for one notification).
     */
    public static boolean sideEffect(String name) {
        if (sideEffects()) {
            return true;
        }
        for (String allowed : System.getProperty("showcase.sideEffects", "").split(",")) {
            if (allowed.trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The window containing {@code component} (without {@code SwingUtilities} : AWT only).
     */
    public static Window windowOf(Component component) {
        return RobotSession.windowOf(component);
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
}
