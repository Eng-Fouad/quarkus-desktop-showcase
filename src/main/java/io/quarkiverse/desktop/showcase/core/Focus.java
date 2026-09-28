package io.quarkiverse.desktop.showcase.core;

import java.awt.AWTException;
import java.awt.Desktop;
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
 * the mouse pointer is moved back. When the windows of another process cover every title bar (the application in front
 * of a user's desktop), the window is first raised above them without being activated (always on top, then not). Bounded
 * : {@link #MAX_ATTEMPTS} attempts.
 * <p>
 * The title bar click is Windows only : {@link Foreground} knows the foreground there only. On Linux and macOS the Java
 * focus state is trusted and each attempt is {@code toFront} and {@code requestFocus}. On X11 they ask the window
 * manager to activate the window (_NET_ACTIVE_WINDOW) : the Docker window manager of the showcase has no mouse
 * bindings, a click would activate nothing there, and without a window manager a frame has no title bar at all. On
 * macOS, AWT never activates the application and rejects the focus requests of an inactive one : with
 * {@code -Dshowcase.activate=true} (unattended runs only, such as CI : on a user's desktop it would take the focus from
 * the active application), each attempt first activates the application ({@code Desktop.requestForeground}), and the
 * later attempts click the title bar on macOS too (a mouse click activates the application), without the check of the
 * window under the point that only Windows can make.
 * <p>
 * A window is also placed asynchronously : {@link #awaitPlaced} waits until its screen location, as AWT knows it, is the
 * requested one (X11 reports a location after the focus, sometimes) before Robot coordinates are computed from it.
 */
public final class Focus {

    /** Unattended runs (CI) : activate the application (macOS), click title bars there too (see the class comment). */
    static final boolean ACTIVATE = Boolean.getBoolean("showcase.activate");

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
     * Completes (on the EDT) with {@code true} once {@code window} is showing less than {@code tolerance} pixels away
     * from {@code expected} on screen, {@code false} after {@code timeoutMillis}. The tolerance covers the frame of a
     * window manager.
     * <p>
     * X11 : {@code getLocationOnScreen} never asks the X server, it returns the location that the peer keeps, which the
     * ConfigureNotify events update on the toolkit thread (X11 sends one for each configure request, a window manager
     * delays the requests of the windows it manages). A Frame or a Dialog reports its Java location until the window
     * manager has reparented it and sent an event, then the location of the events handled since : a move requested
     * while it is shown is seen once the window manager reported it. Under a window manager, the events of a decorated
     * one handled before the reparenting are dropped (an undecorated one applies those handled once it is shown :
     * openbox reports another location while it maps it) ; without window manager every event is applied. A
     * java.awt.Window (JWindow, heavy weight popups) reports the location it requested last and applies every event,
     * also one that reports an earlier request. Such an event, handled after this wait passed, takes the location back
     * until the next one : this wait cannot tell it apart. A window whose location must not change once placed is
     * located before its peer is created (setLocation before pack), so that no event reports another location.
     */
    public static CompletionStage<Boolean> awaitPlaced(Window window, Point expected, int tolerance, long timeoutMillis) {
        return Edt.until(() -> window.isShowing() && window.getLocationOnScreen().distance(expected) < tolerance,
                timeoutMillis, "window placed").handle((v, error) -> error == null);
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
                activate();
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
            if (attempt < MAX_ATTEMPTS
                    && (Boolean.FALSE.equals(Foreground.thisProcess()) || ACTIVATE && Platforms.isMac())) {
                clickTitleBar(window);
            }
        }
        LOG.infof("Focus: %s not focused after %d attempts (foreground : %s)", title(window), MAX_ATTEMPTS,
                Foreground.describe());
        return 0;
    }

    /**
     * macOS with {@code -Dshowcase.activate=true} : activates the application, which AWT never does. Best effort.
     */
    private static void activate() {
        if (!ACTIVATE || !Platforms.isMac()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                Desktop.getDesktop().requestForeground(true);
            }
        } catch (RuntimeException | LinkageError e) {
            // e.g. a registration missing from a native executable : the title bar click remains
            LOG.infof("Focus: requestForeground failed : %s", e);
        }
    }

    /**
     * Clicks the middle of the title bar of {@code window} (or of a decorated owner, or of another decorated showcase
     * frame) : only where the window under the point belongs to this process. On Windows, when the windows of another
     * process cover every title bar (the application in front of a user's desktop), {@code window} is raised above them
     * first ({@link #raise}).
     */
    private static boolean clickTitleBar(Window window) throws Exception {
        if (clickUncoveredTitleBar(window) || Platforms.isWindows() && raise(window) && clickUncoveredTitleBar(window)) {
            return true;
        }
        LOG.infof("Focus: no title bar of a showcase window is uncovered, no click");
        return false;
    }

    /**
     * Windows : raises {@code window} (or its first decorated owner) above the windows of the other processes, without
     * activating it. A background process may not bring its windows in front of the foreground window ({@code toFront}
     * is refused with the activation), but it may make a window always on top, and a window that is no longer always on
     * top stays above the other windows : its title bar can then be clicked. Only a window that is not always on top
     * already ; {@code false} when there is none.
     */
    private static boolean raise(Window window) throws Exception {
        String raised = onEdt(() -> {
            for (Window w = window; w != null; w = w.getOwner()) {
                if (w.isShowing() && decorated(w) && !w.isAlwaysOnTop() && w.isAlwaysOnTopSupported()) {
                    w.setAlwaysOnTop(true);
                    w.setAlwaysOnTop(false);
                    return title(w);
                }
            }
            return null;
        });
        if (raised == null) {
            return false;
        }
        LOG.infof("Focus: every title bar covered by another process, %s raised above it", raised);
        // the new stacking order is applied by the toolkit thread : WindowFromPoint sees it a moment later
        sleep(150);
        return true;
    }

    /**
     * {@link #clickTitleBar} without the raise : {@code false} when no title bar of a showcase window is uncovered.
     */
    private static boolean clickUncoveredTitleBar(Window window) throws Exception {
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
                    if (insets.top <= 0) {
                        // no title bar drawn around the window (no window manager) : the point would be in the page
                        continue;
                    }
                    // the caption : below the top border, above the client area. The middle first (no button there),
                    // then right and left of it, away from the caption buttons and the icon : the window of another
                    // process may cover the middle (the agent terminal of a CI runner)
                    int y = b.y + Math.max(4, insets.top * 2 / 3);
                    int middle = b.x + b.width / 2;
                    list.add(new Point(middle, y));
                    int right = Math.min(b.x + b.width * 3 / 4, b.x + b.width - 6 * insets.top);
                    if (right > middle) {
                        list.add(new Point(right, y));
                    }
                    int left = b.x + Math.max(b.width / 4, 3 * insets.top);
                    if (left < middle) {
                        list.add(new Point(left, y));
                    }
                }
            }
            return list;
        });
        for (Point p : points) {
            Boolean ours = Foreground.thisProcessAt(p);
            // macOS (unattended runs only) : the window under the point is unknown, the first point is clicked
            if (Boolean.TRUE.equals(ours) || ours == null && ACTIVATE && Platforms.isMac()) {
                click(p);
                LOG.infof("Focus: clicked the title bar of a showcase window at %d,%d to get the foreground for %s",
                        p.x, p.y, title(window));
                return true;
            }
        }
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
