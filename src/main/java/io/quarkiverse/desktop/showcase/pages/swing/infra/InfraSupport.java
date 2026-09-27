package io.quarkiverse.desktop.showcase.pages.swing.infra;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import javax.swing.text.DefaultCaret;

import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Helpers shared by the pages of the swing-infra group (layouts, key bindings, painting, concurrency, RTL and
 * localization, printing) : containers, fixed size blocks, deterministic formatting of Swing values.
 * <p>
 * No AWT or Swing object is kept in a static field : Quarkus initializes application classes at build time.
 */
final class InfraSupport {

    /** Width of a half-width checks table (two tables side by side in the 1028 px content area). */
    static final int HALF = 494;

    private InfraSupport() {
    }

    // ------------------------------------------------------------------------------------------------ containers

    /**
     * A transparent panel stacking {@code children} vertically at their preferred size.
     */
    static JPanel column(int gap, Component... children) {
        return panel(new Ui.StackLayout(true, gap), children);
    }

    /**
     * A transparent panel placing {@code children} side by side at their preferred size.
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
     * {@code body} in a titled, fixed width box (the body is laid out at the given width, its preferred height).
     */
    static JPanel titled(String title, int width, JComponent body) {
        JPanel box = new JPanel(new java.awt.BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(width, d.height);
            }
        };
        Border line = BorderFactory.createLineBorder(new Color(0xB0BEC5));
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(line, title),
                BorderFactory.createEmptyBorder(2, 6, 6, 6)));
        box.setBackground(new Color(0xF7F9FC));
        box.add(body);
        return box;
    }

    /**
     * A read-only text area showing {@code lines} (monospaced, no caret, no focus), for logs.
     */
    static JTextArea log(List<String> lines, int columns, int rows) {
        JTextArea area = new JTextArea(String.join("\n", lines), rows, columns) {
            @Override
            public void scrollRectToVisible(Rectangle r) {
                // caret moves (setText) must never scroll the page
            }
        };
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        area.setEditable(false);
        area.setFocusable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        area.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        area.setBackground(new Color(0xFBFBF7));
        return area;
    }

    // ------------------------------------------------------------------------------------------------ formatting

    /**
     * {@code x,y wxh} of the bounds of {@code c} (integers).
     */
    static String bounds(Component c) {
        return rect(c.getBounds());
    }

    static String rect(Rectangle r) {
        return r.x + "," + r.y + " " + r.width + "x" + r.height;
    }

    static String rect(Rectangle2D r) {
        return Checks.bounds(r);
    }

    static String size(Dimension d) {
        return d == null ? "null" : d.width + "x" + d.height;
    }

    /**
     * The name of {@code c}, or its simple class name.
     */
    static String name(Component c) {
        return c.getName() != null ? c.getName() : c.getClass().getSimpleName();
    }

    /**
     * Numbers after a dash replaced by {@code #} (thread names : counters that depend on what ran before).
     */
    static String digitsNormalized(String s) {
        return s.replaceAll("-[0-9]+", "-#");
    }

    // ------------------------------------------------------------------------------------------------ blocks

    /**
     * A fixed size block (minimum = preferred = maximum size unless changed) painting a solid color and a short
     * label : layout results with blocks do not depend on fonts, so their bounds are exact expectations.
     */
    static class Block extends JComponent {

        private final int rgb;

        Block(String name, int rgb, int width, int height) {
            this.rgb = rgb;
            setName(name);
            Dimension size = new Dimension(width, height);
            setMinimumSize(size);
            setPreferredSize(size);
            setMaximumSize(size);
            setOpaque(true);
        }

        Block align(float x, float y) {
            setAlignmentX(x);
            setAlignmentY(y);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(rgb));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(rgb).darker());
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
                g.setColor(new Color(0x263238));
                FontMetrics fm = g.getFontMetrics();
                String text = getName();
                if (fm.stringWidth(text) < getWidth() - 2 && fm.getAscent() < getHeight()) {
                    g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                            (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                }
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * A component painted by {@code painter} at a fixed size.
     */
    static JComponent painted(int width, int height, Consumer<Graphics2D> painter) {
        JComponent c = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    painter.accept(g);
                } finally {
                    g.dispose();
                }
            }
        };
        Dimension size = new Dimension(width, height);
        c.setPreferredSize(size);
        c.setMinimumSize(size);
        c.setMaximumSize(size);
        return c;
    }

    /**
     * The descendants of {@code root} (depth first), {@code root} included.
     */
    static void walk(Component root, Consumer<Component> visitor) {
        visitor.accept(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                walk(child, visitor);
            }
        }
    }
}
