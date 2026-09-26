package io.quarkiverse.desktop.showcase.pages.swing.containers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Helpers shared by the Swing container pages : Swing panels stacking their children at their preferred size, painted
 * icons, component tree searches, deterministic formatting of bounds and popup classification.
 * <p>
 * Nothing here holds an AWT or Swing object in a static field (Quarkus initializes application classes at build time).
 */
final class SwingKit {

    /** Width available to the page content. */
    static final int WIDTH = 1028;

    /** Icon shapes of {@link #icon(int, int, int)}. */
    static final int SQUARE = 0;
    static final int CIRCLE = 1;
    static final int TRIANGLE = 2;
    static final int DIAMOND = 3;

    private SwingKit() {
    }

    // ------------------------------------------------------------------------------------------------------ panels

    /**
     * A transparent {@link JPanel} stacking {@code children} vertically at their preferred size.
     */
    static JPanel column(int gap, Component... children) {
        return panel(new Ui.StackLayout(true, gap), children);
    }

    /**
     * A transparent {@link JPanel} placing {@code children} side by side at their preferred size.
     */
    static JPanel row(int gap, Component... children) {
        return panel(new Ui.StackLayout(false, gap), children);
    }

    private static JPanel panel(java.awt.LayoutManager layout, Component... children) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        for (Component child : children) {
            panel.add(child);
        }
        return panel;
    }

    /**
     * {@code children} wrapped into rows of at most {@code width} pixels.
     */
    static JPanel wrap(int width, int gap, List<? extends Component> children) {
        List<Component> rows = new ArrayList<>();
        List<Component> current = new ArrayList<>();
        int used = 0;
        for (Component child : children) {
            int w = child.getPreferredSize().width;
            if (!current.isEmpty() && used + gap + w > width) {
                rows.add(row(gap, current.toArray(Component[]::new)));
                current.clear();
                used = 0;
            }
            used += (current.isEmpty() ? 0 : gap) + w;
            current.add(child);
        }
        if (!current.isEmpty()) {
            rows.add(row(gap, current.toArray(Component[]::new)));
        }
        return column(gap, rows.toArray(Component[]::new));
    }

    /**
     * A section : a bold title, an optional explanation and the demonstration.
     */
    static JPanel section(String title, String text, Component body) {
        return text == null ? column(6, Ui.title(title), body)
                : column(6, Ui.title(title), Ui.text(text, WIDTH), body);
    }

    /**
     * {@code component} with a fixed preferred size.
     */
    static <C extends JComponent> C sized(C component, int width, int height) {
        component.setPreferredSize(new Dimension(width, height));
        return component;
    }

    /**
     * A caption above {@code component}.
     */
    static JPanel captioned(String caption, Component component) {
        return column(4, Ui.caption(caption), component);
    }

    /**
     * An opaque label with a background color, centered text.
     */
    static JLabel block(String text, int rgb, int width, int height) {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setOpaque(true);
        label.setBackground(new Color(rgb));
        label.setForeground(new Color(luminance(rgb) > 140 ? 0x202020 : 0xFFFFFF));
        label.setPreferredSize(new Dimension(width, height));
        return label;
    }

    static int luminance(int rgb) {
        return (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
    }

    /**
     * A titled table of checks laid out in two columns (half the height of a {@link ChecksView} of the same checks).
     */
    static final class CheckColumns extends JPanel {

        private static final int GAP = 16;
        private final ChecksView left;
        private final ChecksView right;

        CheckColumns(String title, List<Check> checks) {
            super(new Ui.StackLayout(true, 6));
            setOpaque(false);
            int width = (SwingKit.WIDTH - GAP) / 2;
            left = ChecksView.table("", List.of(), 220, width);
            right = ChecksView.table("", List.of(), 220, width);
            add(Ui.title(title));
            add(row(GAP, left, right));
            setChecks(checks);
        }

        /**
         * Replaces the checks (on the EDT) : the first half in the left column, the rest in the right one.
         */
        void setChecks(List<Check> checks) {
            int half = (checks.size() + 1) / 2;
            left.setChecks(checks.subList(0, half));
            right.setChecks(checks.subList(half, checks.size()));
            revalidate();
        }

        List<Check> getChecks() {
            List<Check> all = new ArrayList<>(left.getChecks());
            all.addAll(right.getChecks());
            return all;
        }
    }

    static CheckColumns checks(String title, List<Check> checks) {
        return new CheckColumns(title, checks);
    }

    static CheckColumns pending(String title) {
        return new CheckColumns(title, List.of(Check.info("state", "pending")));
    }

    // ------------------------------------------------------------------------------------------------------- icons

    /**
     * A painted icon (no image resource) : a filled shape of {@code rgb} with a dark outline.
     */
    static Icon icon(int rgb, int shape, int size) {
        return new ShapeIcon(rgb, shape, size);
    }

    private record ShapeIcon(int rgb, int shape, int size) implements Icon {

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(x, y);
                float s = size;
                java.awt.Shape shapeToPaint = switch (shape) {
                    case CIRCLE -> new Ellipse2D.Float(1, 1, s - 2, s - 2);
                    case TRIANGLE -> {
                        Path2D.Float p = new Path2D.Float();
                        p.moveTo(s / 2, 1);
                        p.lineTo(s - 1, s - 2);
                        p.lineTo(1, s - 2);
                        p.closePath();
                        yield p;
                    }
                    case DIAMOND -> {
                        Path2D.Float p = new Path2D.Float();
                        p.moveTo(s / 2, 1);
                        p.lineTo(s - 1, s / 2);
                        p.lineTo(s / 2, s - 1);
                        p.lineTo(1, s / 2);
                        p.closePath();
                        yield p;
                    }
                    default -> new RoundRectangle2D.Float(1, 1, s - 2, s - 2, s / 4, s / 4);
                };
                boolean enabled = c == null || c.isEnabled();
                g.setColor(enabled ? new Color(rgb) : new Color(0xD0D0D0));
                g.fill(shapeToPaint);
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(enabled ? 0x37474F : 0x9E9E9E));
                g.draw(shapeToPaint);
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    // --------------------------------------------------------------------------------------------- tree searches

    static <T> List<T> findAll(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T> void collect(Component c, Class<T> type, List<T> found) {
        if (type.isInstance(c)) {
            found.add(type.cast(c));
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
            }
        }
    }

    static <T> T find(Component root, Class<T> type, Predicate<T> filter) {
        for (T candidate : findAll(root, type)) {
            if (filter.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static <T> T find(Component root, Class<T> type) {
        return find(root, type, c -> true);
    }

    /**
     * Makes {@code root} and all its descendants non focusable : selecting an internal frame, showing an option pane or
     * a dialog then never requests the focus (pages must not steal the focus of the user's application).
     */
    static void unfocusable(Component root) {
        root.setFocusable(false);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                unfocusable(child);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ formatting

    static String rect(Rectangle r) {
        return r == null ? "null" : r.x + "," + r.y + " " + r.width + "x" + r.height;
    }

    static String size(Dimension d) {
        return d == null ? "null" : d.width + "x" + d.height;
    }

    static String point(Point p) {
        return p == null ? "null" : p.x + "," + p.y;
    }

    /**
     * The class name of the UI delegate of {@code c} (a JDK class name : deterministic).
     */
    static String ui(JComponent c) {
        Object ui = c.getUI();
        return ui == null ? "null" : ui.getClass().getName();
    }

    static String font(Font f) {
        return f == null ? "null" : f.getFamily(java.util.Locale.ROOT) + " " + f.getStyle() + " " + f.getSize();
    }

    // -------------------------------------------------------------------------------------------------- popups

    /**
     * {@code light} (the popup is a lightweight component of the layered pane of {@code owner}'s window),
     * {@code medium} (inside an AWT heavyweight panel of that window) or {@code heavy} (in a window of its own).
     */
    static String popupKind(Component contents, Window ownerWindow) {
        Window window = SwingUtilities.getWindowAncestor(contents);
        if (window == null) {
            return "not showing";
        }
        if (window != ownerWindow) {
            return "heavy";
        }
        for (Component c = contents.getParent(); c != null && c != window; c = c.getParent()) {
            if (!c.isLightweight()) {
                return "medium";
            }
        }
        return "light";
    }

    /**
     * The popup weight Swing uses in {@code window} for a popup that would be {@code kind} in a Swing window : light and
     * medium weight popups need a {@code JRootPane} (a {@code JFrame}, {@code JDialog} or {@code JWindow}), every popup
     * of a plain AWT window (the AWT main window, {@code -Dshowcase.ui=awt}) is heavy weight.
     */
    static String expectedKind(String kind, Window window) {
        return window instanceof javax.swing.RootPaneContainer ? kind : "heavy";
    }

    /**
     * The name of the window class holding {@code contents} (e.g. {@code javax.swing.Popup$HeavyWeightWindow}).
     */
    static String windowClass(Component contents) {
        Window window = SwingUtilities.getWindowAncestor(contents);
        return window == null ? "none" : window.getClass().getName();
    }

    /**
     * {@code base} rendered with each of {@code overlays} (showing components, e.g. popups over it) rendered at its
     * on-screen offset from {@code base} : what the user sees, without the rest of the screen.
     */
    static BufferedImage composite(Component base, List<? extends Component> overlays) {
        Rectangle area = new Rectangle(base.getLocationOnScreen(), base.getSize());
        List<Rectangle> bounds = new ArrayList<>();
        for (Component overlay : overlays) {
            Rectangle r = new Rectangle(overlay.getLocationOnScreen(), overlay.getSize());
            bounds.add(r);
            area.add(r);
        }
        BufferedImage image = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, area.width, area.height);
            Point origin = base.getLocationOnScreen();
            g.drawImage(Snapshots.render(base), origin.x - area.x, origin.y - area.y, null);
            for (int i = 0; i < overlays.size(); i++) {
                Rectangle r = bounds.get(i);
                g.drawImage(Snapshots.render(overlays.get(i)), r.x - area.x, r.y - area.y, null);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    // ------------------------------------------------------------------------------------------ events and actions

    /**
     * Performs the action {@code key} of the action map of {@code c} (as a key binding would), with {@code command}.
     */
    static void perform(JComponent c, String key, String command) {
        Action action = c.getActionMap().get(key);
        if (action == null) {
            throw new IllegalStateException("No action " + key + " in the action map of " + c.getClass().getName());
        }
        action.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, command));
    }

    /**
     * Dispatches a synthetic key press (and release) to {@code target} : never dropped, no focus needed.
     */
    static void key(Component target, int modifiers, int keyCode, char keyChar) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, keyChar));
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, 0, modifiers, keyCode, keyChar));
    }

    /**
     * A tiny {@code #AARRGGBB} of the pixel at {@code x, y} of {@code image}.
     */
    static String pixel(BufferedImage image, int x, int y) {
        return Checks.argb(image.getRGB(x, y));
    }
}
