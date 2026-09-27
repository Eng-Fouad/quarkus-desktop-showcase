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
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Robot input for the pages that need the focus ({@link FeaturePage#needsFocus()}), from a background thread only
 * (never the EDT : {@link Robot#waitForIdle()} is illegal there, and the waits below would block the events they wait
 * for).
 * <p>
 * Safety : every key press and mouse button press is preceded by a check that a showcase window is focused and that no
 * other process owns the foreground ({@link Edt#ownsFocus()}), otherwise it is skipped (the method returns
 * {@code false} and {@link #skipped()} lists it); everything pressed is released and the mouse pointer is moved back
 * when the session is closed.
 * <p>
 * Keyboard input waits for each key to be processed : after a press (a release), the next input is only sent once the
 * {@code KEY_PRESSED} ({@code KEY_RELEASED}) event of that key was dispatched on the EDT (at most
 * {@link #KEY_WAIT_MILLIS}). AWT translates a key into a character with the keyboard state of the moment it handles
 * the key : a Shift pressed too early turns "a" into "A", a Ctrl released too early turns a copy drag into a move. Keys
 * that Java never sees (a native menu or drag loop consumes them) are sent with {@link #nativeKeys(boolean)} : a fixed
 * delay instead.
 */
public final class RobotSession implements AutoCloseable {

    /** The longest wait for the event of a key press or release. */
    public static final long KEY_WAIT_MILLIS = 1000;

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
     * @return {@code false} (nothing pressed) when no showcase window is focused
     */
    public boolean keyPress(int keyCode) {
        if (!Focus.await(Edt::ownsFocus, 500)) {
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
     * window is focused.
     *
     * @return {@code false} (nothing pressed) when no showcase window is focused
     */
    public boolean press(int mask) {
        if (!Focus.await(Edt::ownsFocus, 500)) {
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

    public void wheel(int notches) {
        robot.mouseWheel(notches);
        if (idleAfterInput) {
            idle();
        }
    }

    // ---------------------------------------------------------------------------------------------------- screen

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
        boolean same = pixel(p) == (rgb & 0xFFFFFF);
        if (!same) {
            skipped.add("click at a covered point");
        }
        return same;
    }

    public BufferedImage capture(Rectangle screen) {
        return robot.createScreenCapture(screen);
    }

    /**
     * Waits until the screen pixels at {@code probes} have their expected colors ({@code probes} maps a point to an
     * RGB color), i.e. until the page is visible on screen : windows of other applications (or other showcase
     * processes) may cover it. The window of {@code component} is brought to the front between the attempts.
     *
     * @return {@code false} if the page stayed covered
     */
    public boolean awaitVisible(Component component, Map<Point, Integer> probes) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean visible = true;
            for (var probe : probes.entrySet()) {
                int found = pixel(probe.getKey());
                if (found != (probe.getValue() & 0xFFFFFF)) {
                    visible = false;
                    lastMismatch = rgb(found) + " instead of " + rgb(probe.getValue()) + " at " + probe.getKey().x
                            + "," + probe.getKey().y;
                    break;
                }
            }
            if (visible) {
                return true;
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
     * The last pixel that did not have its expected color in {@link #awaitVisible} (for diagnostics : it depends on the
     * windows of the desktop, never put it in a check value).
     */
    public String lastMismatch() {
        return lastMismatch;
    }

    // ----------------------------------------------------------------------------------------------------- misc

    public void idle() {
        robot.waitForIdle();
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
