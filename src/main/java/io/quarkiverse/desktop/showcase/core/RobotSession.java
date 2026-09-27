package io.quarkiverse.desktop.showcase.core;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

import org.jboss.logging.Logger;

/**
 * Robot input for the pages that need the focus ({@link FeaturePage#needsFocus()}), from a background thread only
 * (never the EDT : {@link Robot#waitForIdle()} is illegal there, and the waits below would block the events they wait
 * for).
 * <p>
 * Safety : every key press and mouse button press is preceded by a check that a showcase window is focused and that no
 * other process owns the foreground ({@link Edt#ownsFocus()}), waiting up to {@link #FOCUS_WAIT_MILLIS} for the focus
 * to come back ({@link Edt#awaitFocus} : X11 moves the focus with FocusOut then FocusIn, Windows has no foreground
 * window during an activation change), otherwise it is skipped (the method returns {@code false} and
 * {@link #skipped()} lists it); everything pressed is released and the mouse pointer is moved back when the session is
 * closed. On macOS, Robot input needs the Accessibility permission (TCC) of the application that started the showcase :
 * when it is known to be denied ({@link #inputDenied()}) no key or button is pressed either (skipped the same way).
 * <p>
 * Keyboard input waits for each key to be processed : after a press (a release), the next input is only sent once the
 * {@code KEY_PRESSED} ({@code KEY_RELEASED}) event of that key was dispatched on the EDT (at most
 * {@link #KEY_WAIT_MILLIS}). AWT translates a key into a character with the keyboard state of the moment it handles
 * the key : a Shift pressed too early turns "a" into "A", a Ctrl released too early turns a copy drag into a move. Keys
 * that Java never sees (a native menu or drag loop consumes them) are sent with {@link #nativeKeys(boolean)} : a fixed
 * delay instead.
 * <p>
 * Screen pixels tell whether a window is on screen : {@link #pixelIs} right before a click, {@link #waitForPixel} until
 * a new window is painted (X11 shows the unpainted native background until the first paint), {@link #raiseUntil} to
 * bring a window above the always-on-top windows that cover it (without a window manager, X11 keeps them in the order
 * they were mapped ; a window manager restacking its windows may cover an override-redirect POPUP), and
 * {@link #awaitVisible} for the probes of a page covered by other applications. On macOS they need the Screen Recording
 * permission (TCC) : when it is known to be denied ({@link #screenCaptureDenied()}) {@link #waitForPixel},
 * {@link #raiseUntil} and {@link #awaitVisible} end at the first mismatch, without raising any window.
 */
public final class RobotSession implements AutoCloseable {

    /**
     * See {@link #waitForIdle(Robot)}.
     */
    private static final Object IDLE_LOCK = new Object();

    private static final Logger LOG = Logger.getLogger(RobotSession.class);

    /** The longest wait for the event of a key press or release. */
    public static final long KEY_WAIT_MILLIS = 1000;

    /** How long a key or mouse button press waits for the focus to come back to a showcase window. */
    public static final long FOCUS_WAIT_MILLIS = 500;

    /** Dispatched key events : (id, key code) to count. Updated on the EDT, read by the Robot threads. */
    private static final Map<Long, AtomicInteger> DISPATCHED = new ConcurrentHashMap<>();
    private static volatile boolean listening;

    private final Robot robot;
    private final Point pointer;
    private final Set<Integer> buttons = new LinkedHashSet<>();
    private final Set<Integer> keys = new LinkedHashSet<>();
    private final List<String> skipped = new ArrayList<>();
    private boolean nativeKeys;
    private boolean idleAfterInput = true;
    private int unobservedKeys;
    private String lastMismatch = "";

    private RobotSession(Robot robot, Point pointer) {
        this.robot = robot;
        this.pointer = pointer;
    }

    /**
     * Opens a session (from a background thread).
     */
    public static RobotSession open() throws AWTException {
        if (Edt.isEdt()) {
            throw new IllegalStateException("Robot input must not run on the EDT");
        }
        listen();
        Robot robot = new Robot();
        robot.setAutoDelay(0);
        robot.setAutoWaitForIdle(false);
        PointerInfo info = MouseInfo.getPointerInfo();
        return new RobotSession(robot, info == null ? null : info.getLocation());
    }

    /**
     * Counts the key events dispatched on the EDT : by {@link InputFilter} after the dispatch (snapshot mode), or by an
     * AWT event listener (interactive mode).
     */
    private static synchronized void listen() {
        if (listening || InputFilter.installed()) {
            return;
        }
        AWTEventListener listener = event -> dispatched(event);
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK);
        listening = true;
    }

    /**
     * Records a dispatched key event (called on the EDT).
     */
    static void dispatched(AWTEvent event) {
        if (event instanceof KeyEvent key && key.getID() != KeyEvent.KEY_TYPED) {
            DISPATCHED.computeIfAbsent(eventKey(key.getID(), key.getKeyCode()), k -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static long eventKey(int id, int keyCode) {
        return ((long) id << 32) | (keyCode & 0xFFFFFFFFL);
    }

    private static int count(int id, int keyCode) {
        AtomicInteger counter = DISPATCHED.get(eventKey(id, keyCode));
        return counter == null ? 0 : counter.get();
    }

    public Robot robot() {
        return robot;
    }

    /**
     * {@code true} when the macOS Screen Recording permission was probed ({@code -Dshowcase.robot=true},
     * {@code macos.tcc.screenCapture = false}) and is denied : the screen pixels never show the showcase windows.
     */
    public static boolean screenCaptureDenied() {
        return MacEnvironment.screenCaptureDenied();
    }

    /**
     * {@code true} when the macOS Accessibility permission was probed ({@code -Dshowcase.robot=true},
     * {@code macos.tcc.input = false}) and is denied : macOS drops the events that Robot posts.
     */
    public static boolean inputDenied() {
        return MacEnvironment.inputDenied();
    }

    /** Where the pointer was when the session started ({@code null} if unknown). */
    public Point savedPointer() {
        return pointer;
    }

    /**
     * {@code true} while the keys go to a native loop that Java does not see (a native menu, a popup menu tracked by
     * the operating system) : each key is followed by a fixed delay instead of the wait for its event.
     */
    public RobotSession nativeKeys(boolean enabled) {
        nativeKeys = enabled;
        return this;
    }

    /**
     * {@code false} during a drag and drop (the operating system runs its own loop) : no {@link Robot#waitForIdle()}
     * after the mouse input. Default {@code true}.
     */
    public RobotSession idleAfterInput(boolean enabled) {
        idleAfterInput = enabled;
        return this;
    }

    // ------------------------------------------------------------------------------------------------------ keys

    /**
     * Presses {@code keyCodes} in order (e.g. SHIFT then TAB), then releases them in reverse order, each one once the
     * previous one was processed.
     *
     * @return {@code false} (and the keys pressed so far released) when a key was skipped : no showcase window focused
     */
    public boolean key(int... keyCodes) {
        int pressed = 0;
        try {
            for (int keyCode : keyCodes) {
                if (!keyPress(keyCode)) {
                    return false;
                }
                pressed++;
            }
            return true;
        } finally {
            for (int i = pressed - 1; i >= 0; i--) {
                keyRelease(keyCodes[i]);
            }
        }
    }

    /**
     * Types {@code keyCode} (press and release).
     *
     * @return {@code false} (nothing typed) when no showcase window is focused
     */
    public boolean type(int keyCode) {
        return key(keyCode);
    }

    /**
     * Presses (and keeps pressed) {@code keyCode} once a showcase window is focused (waiting a moment for the focus to
     * come back, e.g. during an activation change), then waits until its KEY_PRESSED event was dispatched.
     *
     * @return {@code false} (nothing pressed) when no showcase window is focused, or the input is known to be dropped
     *         ({@link #inputDenied()})
     */
    public boolean keyPress(int keyCode) {
        if (inputDenied()) {
            skipped.add("key " + KeyEvent.getKeyText(keyCode) + " (input permission denied)");
            return false;
        }
        if (!Edt.awaitFocus(FOCUS_WAIT_MILLIS)) {
            skipped.add("key " + KeyEvent.getKeyText(keyCode));
            return false;
        }
        int before = count(KeyEvent.KEY_PRESSED, keyCode);
        robot.keyPress(keyCode);
        keys.add(keyCode);
        processed(KeyEvent.KEY_PRESSED, keyCode, before);
        return true;
    }

    /**
     * Releases {@code keyCode} if this session pressed it, then waits until its KEY_RELEASED event was dispatched.
     */
    public void keyRelease(int keyCode) {
        if (keys.remove(keyCode)) {
            int before = count(KeyEvent.KEY_RELEASED, keyCode);
            robot.keyRelease(keyCode);
            processed(KeyEvent.KEY_RELEASED, keyCode, before);
        }
    }

    private void processed(int id, int keyCode, int before) {
        if (nativeKeys || !(listening || InputFilter.installed())) {
            robot.delay(60);
            idle();
            return;
        }
        if (!Focus.await(() -> count(id, keyCode) > before, KEY_WAIT_MILLIS)) {
            // consumed before the dispatch (a native loop) or input lost : the checks of the page tell
            unobservedKeys++;
        }
    }

    /**
     * The number of key presses and releases whose event was not dispatched in time (diagnostics).
     */
    public int unobservedKeys() {
        return unobservedKeys;
    }

    // ----------------------------------------------------------------------------------------------------- mouse

    public void move(Point p) {
        robot.mouseMove(p.x, p.y);
        if (idleAfterInput) {
            idle();
        }
    }

    /**
     * Moves from {@code from} to {@code to} in {@code steps} steps, {@code stepMillis} apart.
     */
    public void glide(Point from, Point to, int steps, int stepMillis) {
        for (int i = 1; i <= steps; i++) {
            robot.mouseMove(from.x + (to.x - from.x) * i / steps, from.y + (to.y - from.y) * i / steps);
            robot.delay(stepMillis);
        }
        if (idleAfterInput) {
            idle();
        }
    }

    /**
     * Presses the mouse buttons {@code mask} ({@link java.awt.event.InputEvent#BUTTON1_DOWN_MASK}...) if a showcase
     * window is focused (waiting a moment for the focus to come back).
     *
     * @return {@code false} (nothing pressed) when no showcase window is focused, or the input is known to be dropped
     *         ({@link #inputDenied()})
     */
    public boolean press(int mask) {
        if (inputDenied()) {
            skipped.add("mouse press (input permission denied)");
            return false;
        }
        if (!Edt.awaitFocus(FOCUS_WAIT_MILLIS)) {
            skipped.add("mouse press");
            return false;
        }
        robot.mousePress(mask);
        buttons.add(mask);
        if (idleAfterInput) {
            idle();
        }
        return true;
    }

    public void release(int mask) {
        if (buttons.remove(mask)) {
            robot.mouseRelease(mask);
            if (idleAfterInput) {
                idle();
            }
        }
    }

    /**
     * Press and release.
     *
     * @return {@code false} (nothing pressed) when no showcase window is focused
     */
    public boolean click(int mask) {
        if (!press(mask)) {
            return false;
        }
        release(mask);
        return true;
    }

    /**
     * Waits until a drag and drop ended after the release of the button ({@code ended}, at most 4 s : slow drops were
     * seen at 150 %). The drag loop of the operating system may miss the release until the next input (Windows, JVM
     * runs : the drag ended when the next drag pressed the button). When the drag started ({@code started} : no input
     * into the page otherwise), a small move over the drop target, then a click on it, end the loop (the drop then
     * fails or succeeds, the caller checks). The click is made only while a showcase window is focused ({@link #press})
     * and, on Windows, only where the window under the target belongs to this process
     * ({@link Foreground#thisProcessAt}), except in unattended runs ({@code -Dshowcase.activate=true} : the drag of the
     * first run of a CI runner stayed pending, the click would have been skipped). Call it before {@link #releaseAll()} :
     * the modifier keys of the drag stay
     * pressed until the drop is done (the drag loop reads them when it handles the release of the button). At most
     * 11 s.
     *
     * @return {@code true} when the drag ended
     */
    public boolean finishDrop(Point target, BooleanSupplier started, BooleanSupplier ended) {
        if (Focus.await(ended, 4000)) {
            return true;
        }
        if (!started.getAsBoolean()) {
            return false;
        }
        move(new Point(target.x + 1, target.y + 1));
        if (Focus.await(ended, 3000)) {
            return true;
        }
        if (Focus.ACTIVATE) {
            // unattended runs (CI) : no user application to click into, and the drag loop may leave the foreground
            // or the window under the target to another process (the drag image of the shell) : a click whatever the
            // focus and that window
            LOG.infof("drop at %d,%d not ended : click (-Dshowcase.activate=true)", target.x, target.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            return Focus.await(ended, 4000);
        }
        if (Boolean.FALSE.equals(Foreground.thisProcessAt(target))) {
            LOG.infof("drop at %d,%d not ended : no click, the window under the target belongs to another process",
                    target.x, target.y);
            return false;
        }
        click(InputEvent.BUTTON1_DOWN_MASK);
        return Focus.await(ended, 4000);
    }

    public void wheel(int notches) {
        robot.mouseWheel(notches);
        if (idleAfterInput) {
            idle();
        }
    }

    // ---------------------------------------------------------------------------------------------------- screen

    /** The images of {@link #capture} (identity, weak) : see {@link #isCapture}. */
    private static final Set<BufferedImage> CAPTURES = Collections.synchronizedSet(Collections.newSetFromMap(
            new WeakHashMap<>()));

    /**
     * The difference per channel that {@link #sameColor} accepts between a screen pixel read by Robot and the color the
     * application painted there : 0, except on macOS, where Robot reads the screen through the color profile of the
     * display (a pixel painted #37474F reads #36474F on the 3840x1080 display of the first macOS cycle).
     */
    public static final int COLOR_TOLERANCE = Platforms.isMac() ? 6 : 0;

    /**
     * {@code true} when the screen color {@code found} is the painted color {@code expected} (RGB), within
     * {@link #COLOR_TOLERANCE}.
     */
    public static boolean sameColor(int found, int expected) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((found >> shift) & 0xFF) - ((expected >> shift) & 0xFF)) > COLOR_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code expected} when {@link #sameColor} accepts {@code found}, {@code found} otherwise (RGB) : the value of a check
     * of a screen color, the same on every platform when the color is the expected one.
     */
    public static int snap(int found, int expected) {
        return sameColor(found, expected) ? expected & 0xFFFFFF : found & 0xFFFFFF;
    }

    /**
     * {@code true} when the two images have the same size and every pixel of {@code screen} is the one of
     * {@code painted} within {@link #COLOR_TOLERANCE}.
     */
    public static boolean sameImage(BufferedImage screen, BufferedImage painted) {
        if (screen.getWidth() != painted.getWidth() || screen.getHeight() != painted.getHeight()) {
            return false;
        }
        for (int y = 0; y < screen.getHeight(); y++) {
            for (int x = 0; x < screen.getWidth(); x++) {
                if (!sameColor(screen.getRGB(x, y), painted.getRGB(x, y))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The color of the screen pixel at {@code p} (RGB, no alpha).
     */
    public int pixel(Point p) {
        return robot.getPixelColor(p.x, p.y).getRGB() & 0xFFFFFF;
    }

    /**
     * {@code true} when the screen pixel at {@code p} has the color {@code rgb} : checked right before a click, so that
     * a click never lands on another window that happens to cover the target.
     */
    public boolean pixelIs(Point p, int rgb) {
        boolean same = sameColor(pixel(p), rgb);
        if (!same) {
            skipped.add("click at a covered point");
        }
        return same;
    }

    public BufferedImage capture(Rectangle screen) {
        BufferedImage image = robot.createScreenCapture(screen);
        CAPTURES.add(image);
        return image;
    }

    /**
     * Whether {@code image} is a screen capture of {@link #capture}, as captured (not a processed copy) : its colors come
     * from the screen, through the color profile of the display on macOS, one or two levels apart from one run to the
     * next. {@link SnapshotRunner} lists such snapshots in the report ({@code captures}), and the comparison tool
     * tolerates {@link #COLOR_TOLERANCE} for them on macOS.
     */
    public static boolean isCapture(BufferedImage image) {
        return CAPTURES.contains(image);
    }

    /**
     * Waits up to {@code timeoutMillis} until the screen pixel at {@code p} has the color {@code rgb} : a new window is
     * mapped and painted (X11 shows its unpainted native background until the first paint, slower on a busy machine).
     * Raises no window and records nothing in {@link #skipped()}.
     *
     * @return {@code true} once the pixel has the color
     */
    public boolean waitForPixel(Point p, int rgb, long timeoutMillis) {
        return waitForPixel(p, found -> sameColor(found, rgb), timeoutMillis);
    }

    /**
     * Waits up to {@code timeoutMillis} until {@code expected} accepts the screen pixel at {@code p} (RGB, no alpha),
     * polling every 50 ms ({@link Robot#waitForIdle()} in between unless {@link #idleAfterInput(boolean) idleAfterInput
     * (false)}).
     *
     * @return {@code true} once the pixel is accepted
     */
    public boolean waitForPixel(Point p, IntPredicate expected, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            int found = pixel(p);
            if (expected.test(found)) {
                return true;
            }
            lastMismatch = rgb(found) + " at " + p.x + "," + p.y;
            if (System.nanoTime() - deadline > 0 || screenCaptureDenied()) {
                return false;
            }
            robot.delay(50);
            if (idleAfterInput) {
                idle();
            }
        }
    }

    /**
     * Brings {@code window} to the front ({@code toFront} only) while the screen pixel at {@code p} shows it covered,
     * until {@code visible} accepts that pixel : each check waits up to {@code waitMillis} ({@link #waitForPixel}), at
     * most {@code maxRaises} raises. Unlike {@link #awaitVisible} a negative probe works (e.g. "not the color of the
     * backdrop" when the content varies). A focused window may still be covered : always-on-top windows keep the order
     * they were mapped in without a window manager (X11), and a window manager restacking the windows it manages may
     * cover an override-redirect window (Window.Type.POPUP) it does not manage. Each raise is logged
     * ({@link #logRetry}, {@code action} and the pixel found). No raise when the screen pixels are known not to show the
     * showcase windows ({@link #screenCaptureDenied()}).
     *
     * @return the number of raises it took ({@code 0} : visible at once), {@code -1} if still covered
     */
    public int raiseUntil(String action, Window window, Point p, IntPredicate visible, int maxRaises, long waitMillis)
            throws Exception {
        for (int raises = 0;; raises++) {
            if (waitForPixel(p, visible, waitMillis)) {
                return raises;
            }
            if (raises >= maxRaises || screenCaptureDenied()) {
                return -1;
            }
            logRetry(action, raises + 1, "covered : " + lastMismatch);
            Focus.onEdt(() -> {
                window.toFront();
                return null;
            });
        }
    }

    /**
     * Waits until the screen pixels at {@code probes} have their expected colors ({@code probes} maps a point to an
     * RGB color), i.e. until the page is visible on screen : windows of other applications (or other showcase
     * processes) may cover it. The window of {@code component} is brought to the front between the attempts (8 attempts,
     * 400 ms apart), except when the screen pixels are known not to show the showcase windows
     * ({@link #screenCaptureDenied()} : one attempt).
     *
     * @return {@code false} if the page stayed covered
     */
    public boolean awaitVisible(Component component, Map<Point, Integer> probes) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean visible = true;
            for (var probe : probes.entrySet()) {
                int found = pixel(probe.getKey());
                if (!sameColor(found, probe.getValue())) {
                    visible = false;
                    lastMismatch = rgb(found) + " instead of " + rgb(probe.getValue()) + " at " + probe.getKey().x
                            + "," + probe.getKey().y;
                    break;
                }
            }
            if (visible) {
                return true;
            }
            if (screenCaptureDenied()) {
                return false;
            }
            Focus.onEdt(() -> {
                Window window = windowOf(component);
                if (window != null) {
                    window.toFront();
                }
                return null;
            });
            robot.delay(400);
        }
        return false;
    }

    /**
     * Makes sure that the window of {@code component} is focused and that this process owns the foreground, asking
     * again for the focus if another application took it ({@link Focus#acquireBlocking}).
     *
     * @return {@code true} if a showcase window is focused
     */
    public boolean ensureFocus(Component component) throws Exception {
        Window window = Focus.onEdt(() -> windowOf(component));
        if (window != null && Focus.has(window)) {
            return true;
        }
        return Focus.acquireBlocking(window) > 0;
    }

    /**
     * The last pixel that did not have its expected color in {@link #awaitVisible}, {@link #waitForPixel} or
     * {@link #raiseUntil} (for diagnostics : it depends on the windows of the desktop, never put it in a check value).
     */
    public String lastMismatch() {
        return lastMismatch;
    }

    // ----------------------------------------------------------------------------------------------------- misc

    public void idle() {
        waitForIdle(robot);
    }

    /**
     * {@link Robot#waitForIdle()}, one thread at a time in the whole application. On macOS it is not thread safe:
     * {@code LWCToolkit.nativeSyncQueue} posts a dummy event and waits on one {@code NSConditionLock} of the application,
     * which each call allocates and releases: two threads waiting at the same time release it under each other, and the
     * process crashes (EXC_BAD_ACCESS in {@code -[NSConditionLock lockWhenCondition:beforeDate:]}, seen in a native
     * snapshot run when a background thread of awt-menus was still running on awt-events). Off the EDT only.
     */
    public static void waitForIdle(Robot robot) {
        synchronized (IDLE_LOCK) {
            robot.waitForIdle();
        }
    }

    public void delay(int millis) {
        robot.delay(millis);
    }

    /**
     * The inputs skipped because no showcase window was focused (or the target was covered).
     */
    public List<String> skipped() {
        return skipped;
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

    /**
     * Logs that an attempt of an action on the live desktop was incomplete (the attempts are recorded with
     * {@link Check#attempts}) : what was missing, for the analysis of the runs (never in a check value : it depends on
     * the desktop).
     */
    public static void logRetry(String action, int attempt, Object missing) {
        LOG.infof("%s : attempt %d incomplete (%s ; foreground : %s)", action, attempt, missing, Foreground.describe());
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

    private static String rgb(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }
}
