package io.quarkiverse.desktop.showcase.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

/**
 * Lightweight AWT building blocks for pages (no {@code javax.swing}, no native peer) : text, images, painted areas,
 * rows and columns. Swing pages may use them too.
 * <p>
 * Colors and fonts are created when a component is built : an application class must never hold AWT objects in static
 * fields (Quarkus initializes application classes at build time, AWT classes at run time).
 */
public final class Ui {

    /** Font size of the body text. */
    public static final int TEXT_SIZE = 12;
    /** Text color. */
    public static final int TEXT_COLOR = 0x222222;
    /** Secondary text color. */
    public static final int MUTED_COLOR = 0x555555;
    /** Error color. */
    public static final int ERROR_COLOR = 0xC62828;

    private Ui() {
    }

    // ---------------------------------------------------------------------------------------------------------- text

    public static TextBlock text(String text) {
        return new TextBlock(text, new Font(Font.DIALOG, Font.PLAIN, TEXT_SIZE), new Color(TEXT_COLOR), 0);
    }

    /**
     * Text wrapped at {@code wrapWidth} pixels.
     */
    public static TextBlock text(String text, int wrapWidth) {
        return new TextBlock(text, new Font(Font.DIALOG, Font.PLAIN, TEXT_SIZE), new Color(TEXT_COLOR), wrapWidth);
    }

    public static TextBlock text(String text, Font font, int rgb, int wrapWidth) {
        return new TextBlock(text, font, new Color(rgb), wrapWidth);
    }

    /**
     * Page heading (bold, 22 px).
     */
    public static TextBlock heading(String text) {
        return new TextBlock(text, new Font(Font.DIALOG, Font.BOLD, 22), new Color(TEXT_COLOR), 0);
    }

    /**
     * Section title (bold, 14 px).
     */
    public static TextBlock title(String text) {
        return new TextBlock(text, new Font(Font.DIALOG, Font.BOLD, 14), new Color(TEXT_COLOR), 0);
    }

    /**
     * Small secondary text (11 px), e.g. the caption of a tile.
     */
    public static TextBlock caption(String text) {
        return new TextBlock(text, new Font(Font.DIALOG, Font.PLAIN, 11), new Color(MUTED_COLOR), 0);
    }

    // -------------------------------------------------------------------------------------------- images and paint

    /**
     * Shows {@code image} at its size.
     */
    public static Component image(BufferedImage image) {
        return new ImageView(image);
    }

    /**
     * A {@code width x height} area painted by {@code painter} every time it is painted (on screen and in snapshots).
     * Prefer {@link #image} of a {@link Snapshots#offscreen} image for Java2D content : it is rendered once, into a
     * {@code BufferedImage}, independently of the on-screen pipeline.
     */
    public static Component painted(int width, int height, Consumer<Graphics2D> painter) {
        return new PaintedView(width, height, painter);
    }

    // ------------------------------------------------------------------------------------------------------ layout

    /**
     * A lightweight container stacking {@code children} vertically at their preferred size, left aligned.
     */
    public static Container column(int gap, Component... children) {
        return container(new StackLayout(true, gap), children);
    }

    /**
     * A lightweight container placing {@code children} side by side at their preferred size, top aligned.
     */
    public static Container row(int gap, Component... children) {
        return container(new StackLayout(false, gap), children);
    }

    /**
     * {@code child} surrounded by {@code padding} pixels.
     */
    public static Container padded(int padding, Component child) {
        Container container = new Container() {
            @Override
            public Insets getInsets() {
                return new Insets(padding, padding, padding, padding);
            }
        };
        container.setLayout(new StackLayout(true, 0));
        container.add(child);
        return container;
    }

    private static Container container(LayoutManager layout, Component... children) {
        Container container = new Container();
        container.setLayout(layout);
        for (Component child : children) {
            container.add(child);
        }
        return container;
    }

    /**
     * The content shown instead of a page whose {@code build()} failed.
     */
    public static Container error(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        return column(8,
                text("This page failed to build: " + Checks.describe(error), new Font(Font.DIALOG, Font.BOLD, 13),
                        ERROR_COLOR, 1000),
                text(trace.toString().replace("\t", "    ").replace("\r", ""), new Font(Font.MONOSPACED, Font.PLAIN, 11),
                        TEXT_COLOR, 1000));
    }

    /**
     * Stacks the components of a container vertically (or horizontally) at their preferred size.
     */
    public static final class StackLayout implements LayoutManager {

        private final boolean vertical;
        private final int gap;

        public StackLayout(boolean vertical, int gap) {
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
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                c.setBounds(x, y, d.width, d.height);
                if (vertical) {
                    y += d.height + gap;
                } else {
                    x += d.width + gap;
                }
            }
        }
    }

    private static final class ImageView extends Component {

        private final BufferedImage image;

        ImageView(BufferedImage image) {
            this.image = image;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(image.getWidth(), image.getHeight());
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics g) {
            g.drawImage(image, 0, 0, null);
        }
    }

    private static final class PaintedView extends Component {

        private final int width;
        private final int height;
        private final Consumer<Graphics2D> painter;

        PaintedView(int width, int height, Consumer<Graphics2D> painter) {
            this.width = width;
            this.height = height;
            this.painter = painter;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(0, 0, width, height);
            try {
                painter.accept(g2);
            } finally {
                g2.dispose();
            }
        }
    }
}
