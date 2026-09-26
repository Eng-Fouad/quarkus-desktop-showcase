package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.desktop.showcase.core.Edt;

/**
 * Robot input for the pages that need the focus ({@code needsFocus()}), used from a background thread only (never the
 * EDT : {@link Robot#waitForIdle()} is illegal there).
 * <p>
 * Safety : every key press is preceded by a check that one of the showcase windows is focused ({@link Edt#ownsFocus()}),
 * otherwise the key is skipped ({@link #key} returns {@code false}); keys pressed are always released; the mouse pointer
 * is restored when closed.
 */
final class RobotSupport implements AutoCloseable {

    private final Robot robot;
    private final Point savedPointer;
    private final List<String> skipped = new ArrayList<>();

    private RobotSupport(Robot robot, Point savedPointer) {
        this.robot = robot;
        this.savedPointer = savedPointer;
    }

    static RobotSupport create() throws AWTException {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("RobotSupport is used from a background thread");
        }
        Robot robot = new Robot();
        robot.setAutoDelay(20);
        PointerInfo pointer = MouseInfo.getPointerInfo();
        return new RobotSupport(robot, pointer == null ? null : pointer.getLocation());
    }

    Robot robot() {
        return robot;
    }

    /**
     * Presses {@code keys} in order (e.g. SHIFT then TAB), then releases them in reverse order. Skipped (and
     * {@code false}) when no showcase window is focused right before a press.
     */
    boolean key(int... keys) {
        int pressed = 0;
        try {
            for (int key : keys) {
                if (!Edt.ownsFocus()) {
                    skipped.add("key " + java.awt.event.KeyEvent.getKeyText(key));
                    return false;
                }
                robot.keyPress(key);
                pressed++;
            }
            return true;
        } finally {
            for (int i = pressed - 1; i >= 0; i--) {
                robot.keyRelease(keys[i]);
            }
            robot.waitForIdle();
        }
    }

    /**
     * {@code true} when the screen pixel at {@code screen} has the color {@code rgb} : checked right before a click, so
     * that a click never lands on another window that happens to cover the target.
     */
    boolean pixelIs(Point screen, int rgb) {
        boolean same = (robot.getPixelColor(screen.x, screen.y).getRGB() & 0xFFFFFF) == (rgb & 0xFFFFFF);
        if (!same) {
            skipped.add("click at a covered point");
        }
        return same;
    }

    void move(Point screen) {
        robot.mouseMove(screen.x, screen.y);
        robot.waitForIdle();
    }

    void press(int buttonMask) {
        robot.mousePress(buttonMask);
        robot.waitForIdle();
    }

    void release(int buttonMask) {
        robot.mouseRelease(buttonMask);
        robot.waitForIdle();
    }

    void wheel(int notches) {
        robot.mouseWheel(notches);
        robot.waitForIdle();
    }

    void idle() {
        robot.waitForIdle();
    }

    void delay(int millis) {
        robot.delay(millis);
    }

    BufferedImage capture(Rectangle screen) {
        return robot.createScreenCapture(screen);
    }

    /**
     * The inputs skipped because the showcase was not focused.
     */
    List<String> skipped() {
        return skipped;
    }

    @Override
    public void close() {
        if (savedPointer != null) {
            robot.mouseMove(savedPointer.x, savedPointer.y);
        }
    }
}
