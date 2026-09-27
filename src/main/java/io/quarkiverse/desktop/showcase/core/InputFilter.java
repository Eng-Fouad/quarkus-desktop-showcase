package io.quarkiverse.desktop.showcase.core;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Drops the real mouse events (operating system and Robot) while {@link ShowcaseMode#realInput()} is false, i.e. in
 * snapshot mode except during pages that need the focus : no hover or rollover effect, whatever the mouse pointer
 * position, and no accidental click of the user. Synthetic events dispatched with {@code Component.dispatchEvent} do
 * not go through the event queue and are never dropped.
 * <p>
 * Native controls (AWT heavyweight components) still draw their own hover state when the pointer is over them.
 */
final class InputFilter extends EventQueue {

    private static volatile boolean installed;

    private InputFilter() {
    }

    /**
     * {@code true} once the filter is installed (snapshot mode) : it also reports the dispatched key events to
     * {@link RobotSession}.
     */
    static boolean installed() {
        return installed;
    }

    /**
     * Pushes the filter on top of the system event queue (once).
     */
    static void install() {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        if (!(queue instanceof InputFilter)) {
            queue.push(new InputFilter());
        }
        installed = true;
    }

    @Override
    protected void dispatchEvent(AWTEvent event) {
        if (event instanceof MouseEvent && !ShowcaseMode.realInput()) {
            return;
        }
        super.dispatchEvent(event);
        if (event instanceof KeyEvent) {
            // after the dispatch : the key was processed (Robot typing waits for it)
            RobotSession.dispatched(event);
        }
    }
}
