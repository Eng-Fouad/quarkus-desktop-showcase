package io.quarkiverse.desktop.showcase.core;

import java.awt.AWTException;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.jboss.logging.Logger;

/**
 * Brings a showcase window to the front and makes sure it really is the focused window of the desktop, for the pages
 * that need the focus ({@link FeaturePage#needsFocus()}).
 * <p>
 * Each attempt asks for the focus ({@code toFront}, {@code requestFocus}) and waits for the window to be focused while
 * this process owns the foreground ({@link Foreground}). Windows refuses the foreground to a background process when
 * another process received the last input (for instance another showcase process that just released the focus lock) :
 * the later attempts then click the title bar of a decorated window of the showcase, as a user would (a click
 * activates the window it lands on), only after checking that the window under the point belongs to this process, and
 * the mouse pointer is moved back. Bounded : {@link #MAX_ATTEMPTS} attempts.
 */
public final class Focus {

    /** Attempts of {@link #acquire}. */
    public static final int MAX_ATTEMPTS = 4;

    private static final Logger LOG = Logger.getLogger(Focus.class);

    private static long lastClickNanos;

    private Focus() {
    }

    /**
     * {@code true} when {@code window} is the focused window and no other process owns the foreground.
     */
    public static boolean has(Window window) {
        return window != null && window.isFocused() && !Boolean.FALSE.equals(Foreground.thisProcess());
    }

    /**
     * Brings {@code window} to the front and waits until {@link #has} it (at most {@link #MAX_ATTEMPTS} attempts, a
     * few seconds). The stage completes on the EDT with the number of the attempt that succeeded, {@code 0} when the
     * window could not get the focus.
     */
    public static CompletionStage<Integer> acquire(Window window) {
        return Edt.background(() -> acquireBlocking(window));
    }

    /**
     * {@link #acquire} from a background thread (never the EDT) : the number of the attempt that succeeded, {@code 0}
     * when the window could not get the focus.
     */
    public static int acquireBlocking(Window window) throws Exception {
        if (Edt.isEdt()) {
            throw new IllegalStateException("Focus.acquireBlocking must not run on the EDT");
        }
        if (window == null) {
            return 0;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (has(window)) {
                return attempt;
            }
            boolean showing = onEdt(() -> {
                if (!window.isShowing()) {
                    return false;
                }
                window.toFront();
                window.requestFocus();
                return true;
            });
            if (!showing) {
                return 0;
            }
            if (await(() -> has(window), attempt == 1 ? 1500 : 1000)) {
                return attempt;
            }
            if (attempt < MAX_ATTEMPTS && Boolean.FALSE.equals(Foreground.thisProcess())) {
                clickTitleBar(window);
            }
        }
        LOG.infof("Focus: %s not focused after %d attempts (foreground : %s)", title(window), MAX_ATTEMPTS,
                Foreground.describe());
        return 0;
    }

    /**
     * Clicks the middle of the title bar of {@code window} (or of a decorated owner, or of another decorated showcase
     * frame) : only where the window under the point belongs to this process.
     */
    private static boolean clickTitleBar(Window window) throws Exception {
        List<Point> points = onEdt(() -> {
            List<Window> candidates = new ArrayList<>();
            for (Window w = window; w != null; w = w.getOwner()) {
                candidates.add(w);
            }
            for (Frame frame : Frame.getFrames()) {
                if (!candidates.contains(frame)) {
                    candidates.add(frame);
                }
            }
            List<Point> list = new ArrayList<>();
            for (Window w : candidates) {
                if (w.isShowing() && decorated(w)) {
                    Rectangle b = w.getBounds();
                    Insets insets = w.getInsets();
                    // the caption : below the top border, above the client area ; the middle has no button
                    list.add(new Point(b.x + b.width / 2, b.y + Math.max(4, insets.top * 2 / 3)));
                }
            }
            return list;
        });
        for (Point p : points) {
            if (Boolean.TRUE.equals(Foreground.thisProcessAt(p))) {
                click(p);
                LOG.infof("Focus: clicked the title bar of a showcase window at %d,%d to get the foreground for %s",
                        p.x, p.y, title(window));
                return true;
            }
        }
        LOG.infof("Focus: no title bar of a showcase window is uncovered, no click");
        return false;
    }

    private static synchronized void click(Point p) throws AWTException {
        // two clicks on a title bar within the double click interval would maximize the window
        Object interval = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        long spacing = TimeUnit.MILLISECONDS.toNanos((interval instanceof Integer i ? Math.min(i, 5000) : 500) + 200);
        long wait = lastClickNanos + spacing - System.nanoTime();
        if (lastClickNanos != 0 && wait > 0) {
            sleep(TimeUnit.NANOSECONDS.toMillis(wait) + 1);
        }
        Robot robot = new Robot();
        PointerInfo info = MouseInfo.getPointerInfo();
        Point saved = info == null ? null : info.getLocation();
        try {
            robot.mouseMove(p.x, p.y);
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(30);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
        } finally {
            lastClickNanos = System.nanoTime();
            if (saved != null) {
                robot.mouseMove(saved.x, saved.y);
            }
        }
    }

    private static boolean decorated(Window w) {
        return w instanceof Frame f ? !f.isUndecorated() : w instanceof Dialog d && !d.isUndecorated();
    }

    private static String title(Window w) {
        String title = w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : null;
        return title == null || title.isEmpty() ? w.getClass().getSimpleName() : "'" + title + "'";
    }

    static <T> T onEdt(Callable<T> action) throws Exception {
        if (Edt.isEdt()) {
            return action.call();
        }
        try {
            return Edt.supply(action).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    /**
     * Waits (on a background thread) until {@code condition} is true, polling every 20 ms : {@code false} on timeout.
     */
    static boolean await(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) {
                return false;
            }
            sleep(20);
        }
        return true;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
