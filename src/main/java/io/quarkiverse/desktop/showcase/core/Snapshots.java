package io.quarkiverse.desktop.showcase.core;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.function.Consumer;

/**
 * Renders components to images.
 * <ul>
 * <li>{@link #render} (capture method A, the default) : {@code printAll} into a {@code TYPE_INT_ARGB} image. It
 * bypasses Swing double buffering and the on-screen pipeline, renders AWT heavyweight components through their peer
 * (Windows: native {@code WM_PRINT}, reliable at {@code -Dsun.java2d.uiScale=1} only, which tools/Snapshot.java sets
 * by default), and does not depend on what is on screen.</li>
 * <li>{@link #screen} (method B, opt-in) : Robot screen capture of the on-screen bounds (what the user sees, including
 * overlapping windows : only for dedicated checks).</li>
 * <li>{@link #offscreen} (method C) : Java2D drawing into a {@code BufferedImage}, independent of any window.</li>
 * </ul>
 * Every method must be called on the EDT.
 */
public final class Snapshots {

    private Snapshots() {
    }

    /**
     * {@code component} rendered at its current size (laid out first) on a white background, scaled by {@code scale}.
     * A component that is not showing (e.g. not added to a window yet) is rendered without its native (heavyweight)
     * controls, which need a peer.
     */
    public static BufferedImage render(Component component, double scale) {
        layout(component);
        int width = Math.max(1, (int) Math.ceil(component.getWidth() * scale));
        int height = Math.max(1, (int) Math.ceil(component.getHeight() * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            if (scale != 1) {
                g.scale(scale, scale);
            }
            g.setClip(0, 0, component.getWidth(), component.getHeight());
            if (component.isShowing()) {
                component.printAll(g);
            } else {
                printDetached(component, g);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * {@link #render(Component, double)} at scale 1.
     */
    public static BufferedImage render(Component component) {
        return render(component, 1);
    }

    /**
     * {@code component} rendered at its preferred size (for a component that is not in a window yet).
     */
    public static BufferedImage renderAtPreferredSize(Component component) {
        component.setSize(component.getPreferredSize());
        return render(component, 1);
    }

    /**
     * Robot capture of the on-screen bounds of {@code component} (at the logical resolution). What the user sees :
     * overlapping windows, the mouse pointer position and window activation matter. Opt-in only.
     */
    public static BufferedImage screen(Component component) throws AWTException {
        GraphicsConfiguration gc = component.getGraphicsConfiguration();
        Robot robot = gc == null ? new Robot() : new Robot(gc.getDevice());
        Rectangle bounds = new Rectangle(component.getLocationOnScreen(), component.getSize());
        MultiResolutionImage capture = robot.createMultiResolutionScreenCapture(bounds);
        Image base = capture.getResolutionVariant(bounds.width, bounds.height);
        BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(base, 0, 0, bounds.width, bounds.height, null);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * A transparent {@code TYPE_INT_ARGB} image painted by {@code painter} (capture method C for Java2D pages).
     */
    public static BufferedImage offscreen(int width, int height, Consumer<Graphics2D> painter) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            painter.accept(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Lays out {@code component} : validates its window when it is displayable, otherwise lays out its descendants
     * explicitly ({@code validate()} does nothing without a peer).
     */
    public static void layout(Component component) {
        if (component.isDisplayable()) {
            Component root = component;
            while (root.getParent() != null) {
                root = root.getParent();
            }
            root.validate();
        } else {
            layoutDetached(component);
        }
    }

    private static void layoutDetached(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutDetached(child);
            }
        }
    }

    private static void printDetached(Component component, Graphics g) {
        if (isSwingComponent(component)) {
            // JComponent.print paints its Swing descendants, showing or not, but skips AWT components without a peer
            component.print(g);
            printAwtDescendants((Container) component, g);
            return;
        }
        component.print(g);
        if (component instanceof Container container) {
            Component[] children = container.getComponents();
            for (int i = children.length - 1; i >= 0; i--) {
                Component child = children[i];
                // not displayable : no peer, isLightweight() is false for every component. Native controls without a peer
                // paint nothing, the others (lightweight components, containers) paint as usual
                if (!child.isVisible()) {
                    continue;
                }
                Graphics cg = g.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());
                try {
                    printDetached(child, cg);
                } finally {
                    cg.dispose();
                }
            }
        }
    }

    private static void printAwtDescendants(Container swing, Graphics g) {
        Component[] children = swing.getComponents();
        for (int i = children.length - 1; i >= 0; i--) {
            Component child = children[i];
            if (!child.isVisible()) {
                continue;
            }
            Graphics cg = g.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());
            try {
                if (isSwingComponent(child)) {
                    printAwtDescendants((Container) child, cg);
                } else {
                    printDetached(child, cg);
                }
            } finally {
                cg.dispose();
            }
        }
    }

    private static boolean isSwingComponent(Component component) {
        for (Class<?> c = component.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("javax.swing.JComponent")) {
                return true;
            }
        }
        return false;
    }
}
