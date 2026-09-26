package io.quarkiverse.desktop.showcase.pages.swing.controls;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.border.TitledBorder;

import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;

/**
 * Helpers shared by the pages of the swing-controls group (Swing code : lives under {@code pages.swing}).
 * <p>
 * Nothing here creates AWT or Swing objects in a static initializer (application classes are initialized at build time
 * in a native executable) : colors, fonts, borders and icons are created by the methods, when a page is built.
 */
final class ControlsSupport {

    /** Resources of the group. */
    static final String RESOURCES = "/showcase/swing-controls/";

    /** Width of the content of a page (the page frame content area is at least 1028 pixels wide). */
    static final int WIDTH = 1000;

    private ControlsSupport() {
    }

    // ------------------------------------------------------------------------------------------------------ layout

    /**
     * A transparent panel stacking {@code children} vertically, each one stretched to the width of the panel.
     */
    static JPanel column(int gap, Component... children) {
        JPanel panel = new JPanel(new FillLayout(true, gap));
        panel.setOpaque(false);
        for (Component child : children) {
            panel.add(child);
        }
        return panel;
    }

    /**
     * A transparent panel placing {@code children} side by side at their preferred width, stretched to its height.
     */
    static JPanel row(int gap, Component... children) {
        JPanel panel = new JPanel(new FillLayout(false, gap));
        panel.setOpaque(false);
        for (Component child : children) {
            panel.add(child);
        }
        return panel;
    }

    /**
     * {@code content} in a titled, padded group box.
     */
    static JPanel section(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        TitledBorder border = BorderFactory.createTitledBorder(title);
        panel.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(2, 8, 8, 8)));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    /**
     * A transparent panel with {@code layout}.
     */
    static JPanel panel(LayoutManager layout, Component... children) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        for (Component child : children) {
            panel.add(child);
        }
        return panel;
    }

    /**
     * A small caption label (gray, plain).
     */
    static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        label.setForeground(new Color(0x555555));
        return label;
    }

    /**
     * Sets the preferred size of a text component (or any component whose height depends on its width) to
     * {@code width} and the height it needs at that width.
     */
    static <C extends JComponent> C fitHeight(C component, int width) {
        component.setPreferredSize(null);
        component.setSize(width, 10);
        int height = component.getPreferredSize().height;
        component.setSize(width, height);
        height = component.getPreferredSize().height;
        component.setPreferredSize(new Dimension(width, height));
        return component;
    }

    /**
     * Stacks the children vertically (stretched to the container width) or horizontally (stretched to its height).
     */
    static final class FillLayout implements LayoutManager {

        private final boolean vertical;
        private final int gap;

        FillLayout(boolean vertical, int gap) {
            this.vertical = vertical;
            this.gap = gap;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int main = 0;
            int cross = 0;
            int count = 0;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                main += vertical ? d.height : d.width;
                cross = Math.max(cross, vertical ? d.width : d.height);
                count++;
            }
            main += Math.max(0, count - 1) * gap;
            Insets in = parent.getInsets();
            return vertical ? new Dimension(cross + in.left + in.right, main + in.top + in.bottom)
                    : new Dimension(main + in.left + in.right, cross + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int x = in.left;
            int y = in.top;
            int width = parent.getWidth() - in.left - in.right;
            int height = parent.getHeight() - in.top - in.bottom;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                if (vertical) {
                    c.setBounds(x, y, Math.max(width, 0), d.height);
                    y += d.height + gap;
                } else {
                    c.setBounds(x, y, d.width, Math.max(height, 0));
                    x += d.width + gap;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------- icons

    /**
     * A colored rounded square icon, painted (no image).
     */
    static Icon swatch(int rgb, int width, int height) {
        return new SwatchIcon(rgb, width, height);
    }

    /**
     * An {@link ImageIcon} of a classpath resource of the group ({@code ImageIcon(URL)} : {@code Toolkit.getImage} of a
     * {@code jar:} or {@code resource:} URL). The description is set : the default one is the URL, which must never be
     * shown.
     */
    static ImageIcon resourceIcon(String name, String description) {
        return new ImageIcon(Edt.resource(RESOURCES + name), description);
    }

    /**
     * The pixels of {@code icon} painted into a transparent ARGB image.
     */
    static BufferedImage paintIcon(Icon icon) {
        BufferedImage image = new BufferedImage(Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    private record SwatchIcon(int rgb, int width, int height) implements Icon {

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(rgb));
                g.fillRoundRect(x + 1, y + 1, width - 2, height - 2, 5, 5);
                g.setColor(new Color(rgb).darker());
                g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 5, 5);
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }

    /**
     * {@code component} painted like on screen ({@code paint}, not {@code printAll}) into a white ARGB image, with the
     * double buffering of the RepaintManager disabled meanwhile (no back buffer, no accelerated surface). Unlike
     * {@code printAll}, it shows what components hide when painting for print ({@code isPaintingForPrint()} : the
     * table selection, the focused cell, the header sort icons).
     */
    static BufferedImage paintedSnapshot(JComponent component) {
        RepaintManager manager = RepaintManager.currentManager(component);
        boolean doubleBuffered = manager.isDoubleBufferingEnabled();
        BufferedImage image = new BufferedImage(Math.max(1, component.getWidth()), Math.max(1, component.getHeight()),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        manager.setDoubleBufferingEnabled(false);
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            component.paint(g);
        } finally {
            manager.setDoubleBufferingEnabled(doubleBuffered);
            g.dispose();
        }
        return image;
    }

    // ----------------------------------------------------------------------------------------------------- ready

    /**
     * Interactive mode : only the snapshot runner calls {@code ready()} ; a page calls it itself once its content is
     * shown (its checks and late layout depend on it). Pages make {@code ready()} idempotent with {@link Readiness}.
     */
    static void readyWhenShown(FeaturePage page, Component content) {
        if (!ShowcaseMode.snapshot()) {
            EventQueue.invokeLater(() -> {
                if (content.isShowing()) {
                    page.ready(content);
                }
            });
        }
    }

    /**
     * The {@code ready()} stage of the current content, started once.
     */
    static final class Readiness {

        private Component content;
        private CompletionStage<?> stage;

        CompletionStage<?> get(Component content, Function<Component, CompletionStage<?>> prepare) {
            if (this.content != content) {
                this.content = content;
                stage = prepare.apply(content);
            }
            return stage;
        }

        void reset() {
            content = null;
            stage = null;
        }
    }

    // ----------------------------------------------------------------------------------------------------- actions

    /**
     * An action event from {@code source}.
     */
    static ActionEvent event(Object source, String command) {
        return new ActionEvent(source, ActionEvent.ACTION_PERFORMED, command);
    }

    /**
     * Runs the action {@code name} of the action map of {@code component} (look and feel actions are loaded lazily,
     * by reflection, on first use : {@code LazyActionMap}), with {@code component} as source.
     */
    static void runAction(JComponent component, String name) {
        runAction(component, name, name);
    }

    /**
     * Runs the action {@code name} of the action map of {@code component} with the action command {@code command}.
     */
    static void runAction(JComponent component, String name, String command) {
        Action action = component.getActionMap().get(name);
        if (action == null) {
            throw new IllegalStateException("no action " + name + " in the action map");
        }
        action.actionPerformed(event(component, command));
    }

    // ------------------------------------------------------------------------------------------------ deterministic

    /**
     * A date in the default time zone (it is formatted in the default time zone, so it shows the same values whatever
     * the zone of the machine).
     */
    static Date date(int year, int month, int day, int hour, int minute) {
        Calendar calendar = new GregorianCalendar(year, month - 1, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * {@code yyyy-MM-dd HH:mm} of {@code date} in the default time zone.
     */
    static String format(Date date) {
        Calendar c = new GregorianCalendar();
        c.setTime(date);
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /**
     * The simple class name of {@code value} and its value : {@code Integer 42} (dates : {@code Date yyyy-MM-dd HH:mm}).
     */
    static String typed(Object value) {
        if (value instanceof Date date) {
            // Date.toString() shows the time zone : formatted in the default zone instead
            return "Date " + format(date);
        }
        return value == null ? "null" : value.getClass().getSimpleName() + " " + value;
    }

    static String ints(int... values) {
        return IntStream.of(values).mapToObj(Integer::toString).collect(Collectors.joining(","));
    }

    /**
     * An ordered event log (a list of short texts).
     */
    static final class EventLog {

        private final List<String> events = new ArrayList<>();

        void add(String event) {
            events.add(event);
        }

        List<String> events() {
            return List.copyOf(events);
        }

        int size() {
            return events.size();
        }

        void clear() {
            events.clear();
        }

        /**
         * The events joined with {@code " | "}, then cleared.
         */
        String drain() {
            String text = String.join(" | ", events);
            events.clear();
            return text.isEmpty() ? "(none)" : text;
        }

        @Override
        public String toString() {
            return String.join(" | ", events);
        }
    }
}
