package io.quarkiverse.desktop.showcase.pages.swing.infra;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.swing.BorderFactory;
import javax.swing.CellRendererPane;
import javax.swing.DebugGraphics;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.RepaintManager;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Swing painting : custom {@code paintComponent}, {@code paintBorder} and {@code paintChildren}, opaque, non-opaque
 * and translucent components, {@link JLayeredPane} order, the {@link RepaintManager} (dirty regions, invalid
 * components, double buffering, offscreen and volatile buffers, the per-window {@link BufferStrategy}),
 * {@code paintImmediately} (painting origin and clip), {@link DebugGraphics} (log option), {@link CellRendererPane},
 * {@code paint} vs {@code print} paths ({@code isPaintingForPrint}), {@code getVisibleRect} and
 * {@code scrollRectToVisible}.
 * <p>
 * The tiles are captured with {@code printAll} (method A). The checks of the painting machinery run once the page is
 * shown (a component must be showing for the {@link RepaintManager}) and are exact : the components have fixed sizes.
 */
@Singleton
public class SwingPaintingPage implements FeaturePage {

    private static final int TILE_W = 116;
    private static final int TILE_H = 96;
    private static final int CHECKER_A = 0xCFD8DC;
    private static final int CHECKER_B = 0xFFFFFF;

    // per build state
    private Demo demo;
    private ChecksView machinery;
    private ChecksView paths;
    private JTextArea immediateLog;
    private JTextArea dirtyLog;
    private JTextArea debugLog;

    private static final class Demo {
        final List<String> paintLog = Collections.synchronizedList(new ArrayList<>());
        JPanel content;
        LoggingPanel opaque;
        LoggingPanel checker;
        LoggingPanel nonOpaque;
        JLayeredPane layered;
        JPanel viewportView;
        JViewport viewport;
        JPanel sample;
    }

    @Override
    public String id() {
        return "swing-painting";
    }

    @Override
    public String title() {
        return "Painting and RepaintManager";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 930;
    }

    @Override
    public Component build() {
        Demo d = new Demo();
        demo = d;

        JPanel tiles = InfraSupport.row(10,
                tile("paintComponent", gradientTile()),
                tile("opaque child", opaqueTile(d)),
                tile("non-opaque child", nonOpaqueTile(d)),
                tile("translucent, opaque false", translucentTile()),
                tile("paintBorder", borderTile()),
                tile("paintChildren overlay", childrenTile()),
                tile("JLayeredPane layers", layeredTile(d)),
                tile("content area off", contentAreaTile()));

        d.sample = sample();
        BufferedImage painted = paintToImage(d.sample, false);
        BufferedImage printed = paintToImage(d.sample, true);
        BufferedImage scaled = Snapshots2.print(d.sample, 1.5);
        List<Check> pathChecks = new ArrayList<>();
        pathChecks.add(Checks.expect("paint() and printAll() into images : identical pixels", "true",
                () -> Checks.sha256(painted).equals(Checks.sha256(printed))));
        pathChecks.add(Checks.expect("print at scale 1.5 : size", "270x120",
                () -> scaled.getWidth() + "x" + scaled.getHeight()));
        pathChecks.add(Checks.expect("print at scale 1.5 : pixel inside the blue fill", Checks.argb(0xFF1E88E5),
                () -> Checks.argb(scaled.getRGB(30, 90))));
        List<String> debugLines = new ArrayList<>();
        BufferedImage debugged = debugGraphics(d.sample, painted, pathChecks, debugLines);
        paths = ChecksView.table("Painting paths (offscreen) and DebugGraphics", pathChecks);

        immediateLog = InfraSupport.log(List.of("pending"), 60, 9);
        dirtyLog = InfraSupport.log(List.of("pending"), 60, 9);
        debugLog = InfraSupport.log(debugLines, 140, debugLines.size());
        machinery = ChecksView.table("RepaintManager, paintImmediately, on-screen painting",
                List.of(Check.info("state", "pending")));

        d.content = InfraSupport.column(14,
                Ui.text("Custom painting and the Swing painting machinery. Tiles (captured with printAll) : a "
                        + "paintComponent override, opaque and non-opaque children over a checkered parent, a "
                        + "translucent background, custom borders, an overlay painted after the children, layered "
                        + "components and a button without content area. Below : one component rendered through "
                        + "the different paint paths, and the logs of DebugGraphics, paintImmediately and "
                        + "RepaintManager.", 1000),
                tiles,
                InfraSupport.row(14,
                        caption("paint(g)", Ui.image(painted)),
                        caption("printAll(g)", Ui.image(printed)),
                        caption("paint(g) with DebugGraphics", Ui.image(debugged)),
                        caption("print at scale 1.5", Ui.image(scaled)),
                        scrollDemo(d)),
                InfraSupport.titled("DebugGraphics.LOG_OPTION (normalized)", 1028, debugLog),
                paths,
                InfraSupport.row(14,
                        InfraSupport.titled("paintImmediately", 507, immediateLog),
                        InfraSupport.titled("RepaintManager", 507, dirtyLog)),
                machinery);
        return d.content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Demo d = demo;
        ChecksView view = machinery;
        JTextArea immediate = immediateLog;
        JTextArea dirty = dirtyLog;
        return Edt.rounds(3).thenAccept(v -> {
            List<Check> checks = new ArrayList<>();
            List<String> immediateLines = new ArrayList<>();
            List<String> dirtyLines = new ArrayList<>();
            repaintManager(d, checks, dirtyLines);
            paintImmediately(d, checks, immediateLines);
            visibleRect(d, checks);
            onScreen(d, checks);
            view.setChecks(checks);
            immediate.setText(String.join("\n", immediateLines));
            dirty.setText(String.join("\n", dirtyLines));
            d.content.revalidate();
        });
    }

    @Override
    public void dispose(Component content) {
        demo = null;
        machinery = null;
        paths = null;
        immediateLog = null;
        dirtyLog = null;
        debugLog = null;
    }

    // ------------------------------------------------------------------------------------------------ tiles

    private static JComponent caption(String text, Component component) {
        return InfraSupport.column(4, component, Ui.caption(text));
    }

    private static JComponent tile(String caption, JComponent body) {
        body.setPreferredSize(new Dimension(TILE_W, TILE_H));
        return caption(caption, body);
    }

    private static JComponent gradientTile() {
        return InfraSupport.painted(TILE_W, TILE_H, g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setPaint(new GradientPaint(0, 0, new Color(0x4695EB), TILE_W, TILE_H, new Color(0x1B3A6B)));
            g.fillRect(0, 0, TILE_W, TILE_H);
            g.setColor(new Color(0xFFB74D));
            g.fill(new Ellipse2D.Double(12, 14, 50, 50));
            g.setColor(new Color(255, 255, 255, 160));
            g.setStroke(new BasicStroke(3f));
            g.draw(new RoundRectangle2D.Double(46, 30, 58, 50, 14, 14));
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
            g.drawString("Swing", 50, 88);
        });
    }

    /** A checkered, opaque parent (null layout, children at fixed bounds). */
    private static LoggingPanel checker(Demo d, String name) {
        LoggingPanel panel = new LoggingPanel(d, name) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                for (int y = 0; y < getHeight(); y += 8) {
                    for (int x = 0; x < getWidth(); x += 8) {
                        g.setColor(new Color(((x + y) / 8) % 2 == 0 ? CHECKER_A : CHECKER_B));
                        g.fillRect(x, y, 8, 8);
                    }
                }
            }
        };
        panel.setLayout(null);
        panel.setOpaque(true);
        return panel;
    }

    private static JComponent opaqueTile(Demo d) {
        LoggingPanel parent = checker(d, "checker-opaque");
        d.opaque = new LoggingPanel(d, "opaque");
        d.opaque.setOpaque(true);
        d.opaque.setBackground(new Color(0x81C784));
        d.opaque.setBounds(18, 16, 80, 64);
        d.opaque.setBorder(BorderFactory.createLineBorder(new Color(0x2E7D32)));
        parent.add(d.opaque);
        return parent;
    }

    private static JComponent nonOpaqueTile(Demo d) {
        d.checker = checker(d, "checker");
        d.nonOpaque = new LoggingPanel(d, "non-opaque");
        d.nonOpaque.setOpaque(false);
        d.nonOpaque.setBounds(18, 16, 80, 64);
        d.nonOpaque.setBorder(BorderFactory.createLineBorder(new Color(0x2E7D32), 2));
        d.checker.add(d.nonOpaque);
        return d.checker;
    }

    private static JComponent translucentTile() {
        JPanel parent = checker(new Demo(), "checker-translucent");
        JPanel child = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                // not opaque : the parent is painted first, the translucent background is blended over it
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        child.setOpaque(false);
        child.setBackground(new Color(0x80E53935, true));
        child.setBounds(18, 16, 80, 64);
        parent.add(child);
        return parent;
    }

    private static JComponent borderTile() {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setBackground(new Color(0xFFF8E1));
        Icon dot = new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(new Color(0xFFB74D));
                g.fillRect(x, y, 6, 6);
                g.setColor(new Color(0x8D6E63));
                g.fillRect(x + 6, y + 6, 6, 6);
            }

            @Override
            public int getIconWidth() {
                return 12;
            }

            @Override
            public int getIconHeight() {
                return 12;
            }
        };
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(12, 12, 12, 12, dot),
                new DashedRoundBorder()));
        JLabel label = new JLabel("border", SwingConstants.CENTER);
        panel.add(label);
        return panel;
    }

    /** A custom border : a dashed rounded rectangle. */
    private static final class DashedRoundBorder extends AbstractBorder {

        @Override
        public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0x5E35B1));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                        new float[] { 5f, 3f }, 0f));
                g.draw(new RoundRectangle2D.Double(x + 2, y + 2, width - 4, height - 4, 12, 12));
            } finally {
                g.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(5, 5, 5, 5);
        }
    }

    private static JComponent childrenTile() {
        JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 4, 12)) {
            @Override
            protected void paintChildren(Graphics graphics) {
                super.paintChildren(graphics);
                // painted over the children
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(0xE53935));
                    g.fill(new Ellipse2D.Double(getWidth() - 34, 4, 26, 26));
                    g.setColor(Color.WHITE);
                    g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.drawString("3", getWidth() - 25, 22);
                } finally {
                    g.dispose();
                }
            }
        };
        panel.setBackground(new Color(0xE8EAF6));
        panel.add(new JButton("One"));
        panel.add(new JButton("Two"));
        return panel;
    }

    private static JComponent layeredTile(Demo d) {
        JLayeredPane layered = new JLayeredPane();
        layered.setOpaque(true);
        layered.setBackground(new Color(0xECEFF1));
        int[][] specs = { { 10, 10, 0x4FC3F7 }, { 34, 30, 0xFFB74D }, { 58, 50, 0x81C784 } };
        Integer[] layers = { JLayeredPane.POPUP_LAYER, JLayeredPane.PALETTE_LAYER, JLayeredPane.DEFAULT_LAYER };
        for (int i = 0; i < specs.length; i++) {
            JLabel box = new JLabel(String.valueOf(layers[i]), SwingConstants.CENTER);
            box.setOpaque(true);
            box.setBackground(new Color(specs[i][2]));
            box.setBorder(BorderFactory.createLineBorder(new Color(specs[i][2]).darker()));
            box.setBounds(specs[i][0], specs[i][1], 50, 36);
            // the first box is in the highest layer : painted on top of the others
            layered.add(box, layers[i]);
        }
        d.layered = layered;
        return layered;
    }

    private static JComponent contentAreaTile() {
        JPanel parent = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                g.setPaint(new GradientPaint(0, 0, new Color(0xFFE0B2), 0, getHeight(), new Color(0xFF8A65)));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        JButton button = new JButton("flat");
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setFocusPainted(false);
        parent.add(button);
        return parent;
    }

    // ------------------------------------------------------------------------------------------------ paint paths

    /** A component tree with solid fills and a label (no dependency on the print state). */
    private static JPanel sample() {
        JPanel panel = new JPanel(null);
        panel.setBackground(new Color(0xFAFAFA));
        panel.setBorder(BorderFactory.createLineBorder(new Color(0x90A4AE)));
        panel.setSize(180, 80);
        JPanel blue = new JPanel();
        blue.setBackground(new Color(0x1E88E5));
        blue.setBounds(10, 10, 60, 60);
        JLabel label = new JLabel("Swing paint");
        label.setBounds(80, 10, 90, 24);
        JButton button = new JButton("OK");
        button.setBounds(80, 40, 70, 26);
        panel.add(blue);
        panel.add(label);
        panel.add(button);
        Snapshots2.layout(panel);
        return panel;
    }

    private static BufferedImage paintToImage(JComponent c, boolean print) {
        BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            if (print) {
                c.printAll(g);
            } else {
                c.paint(g);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Offscreen helpers for detached components (the core Snapshots needs a showing component for printAll). */
    private static final class Snapshots2 {

        static void layout(Component c) {
            if (c instanceof java.awt.Container container) {
                container.doLayout();
                for (Component child : container.getComponents()) {
                    layout(child);
                }
            }
        }

        static BufferedImage print(JComponent c, double scale) {
            int w = (int) Math.ceil(c.getWidth() * scale);
            int h = (int) Math.ceil(c.getHeight() * scale);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.scale(scale, scale);
                c.print(g);
            } finally {
                g.dispose();
            }
            return image;
        }
    }

    private static JComponent scrollDemo(Demo d) {
        JPanel view = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(0xB0BEC5));
                for (int x = 0; x < getWidth(); x += 20) {
                    g.drawLine(x, 0, x, getHeight());
                }
                for (int y = 0; y < getHeight(); y += 20) {
                    g.drawLine(0, y, getWidth(), y);
                }
                g.setColor(new Color(0xE53935));
                g.fillRect(250, 150, 20, 20);
            }
        };
        view.setBackground(Color.WHITE);
        view.setPreferredSize(new Dimension(300, 200));
        JViewport viewport = new JViewport();
        viewport.setView(view);
        viewport.setPreferredSize(new Dimension(100, 80));
        viewport.setViewPosition(new java.awt.Point(30, 20));
        d.viewport = viewport;
        d.viewportView = view;
        JPanel holder = new JPanel(new java.awt.BorderLayout());
        holder.setBorder(BorderFactory.createLineBorder(new Color(0x90A4AE)));
        holder.add(viewport);
        return caption("JViewport : scrollRectToVisible", holder);
    }

    // ------------------------------------------------------------------------------------------------ checks

    /** A panel recording paintComponent calls (name, clip, print state). */
    private static class LoggingPanel extends JPanel {

        private final Demo demo;

        LoggingPanel(Demo demo, String name) {
            this.demo = demo;
            setName(name);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Rectangle clip = g.getClipBounds();
            demo.paintLog.add(getName() + " clip " + (clip == null ? "null" : InfraSupport.rect(clip))
                    + (isPaintingForPrint() ? " (print)" : ""));
        }
    }

    private static void repaintManager(Demo d, List<Check> checks, List<String> lines) {
        RepaintManager rm = RepaintManager.currentManager(d.opaque);
        checks.add(Checks.expect("RepaintManager.currentManager()", "javax.swing.RepaintManager",
                () -> rm.getClass().getName()));
        checks.add(Checks.expect("isDoubleBufferingEnabled()", "true", rm::isDoubleBufferingEnabled));
        checks.add(Checks.info("getDoubleBufferMaximumSize()",
                () -> InfraSupport.size(rm.getDoubleBufferMaximumSize())));
        checks.add(Checks.info("getOffscreenBuffer(c, 50, 50)",
                () -> className(rm.getOffscreenBuffer(d.opaque, 50, 50))));
        checks.add(Checks.info("getVolatileOffscreenBuffer(c, 50, 50)", () -> {
            java.awt.Image image = rm.getVolatileOffscreenBuffer(d.opaque, 50, 50);
            return className(image) + (image instanceof VolatileImage v ? ", accelerated "
                    + v.getCapabilities().isAccelerated() : "");
        }));
        checks.add(Checks.info("window BufferStrategy (per window back buffer)", () -> {
            Window w = SwingUtilities.getWindowAncestor(d.opaque);
            BufferStrategy bs = w == null ? null : w.getBufferStrategy();
            return bs == null ? "none" : className(bs);
        }));
        checks.add(Checks.info("swing.bufferPerWindow / swing.volatileImageBufferEnabled",
                () -> System.getProperty("swing.bufferPerWindow") + " / "
                        + System.getProperty("swing.volatileImageBufferEnabled")));
        checks.add(Checks.expect("JPanel / JLabel isDoubleBuffered()", "true / false",
                () -> new JPanel().isDoubleBuffered() + " / " + new JLabel().isDoubleBuffered()));

        // dirty regions : the component is showing, repaint() is recorded synchronously on the EDT
        LoggingPanel c = d.opaque;
        checks.add(Checks.expect("repaint(10,10,20,20) + repaint(40,10,20,20) : dirty region union", "10,10 50x20",
                () -> {
                    rm.markCompletelyClean(c);
                    c.repaint(10, 10, 20, 20);
                    c.repaint(40, 10, 20, 20);
                    String region = InfraSupport.rect(rm.getDirtyRegion(c));
                    lines.add("repaint(10,10,20,20), repaint(40,10,20,20)");
                    lines.add("  getDirtyRegion = " + region);
                    return region;
                }));
        checks.add(Checks.expect("repaint() : dirty region = bounds", "0,0 80x64", () -> {
            rm.markCompletelyClean(c);
            c.repaint();
            String region = InfraSupport.rect(rm.getDirtyRegion(c));
            lines.add("repaint() : getDirtyRegion = " + region);
            return region;
        }));
        checks.add(Checks.expect("markCompletelyDirty / isCompletelyDirty / getDirtyRegion",
                "true 0,0 2147483647x2147483647", () -> {
                    rm.markCompletelyDirty(c);
                    return rm.isCompletelyDirty(c) + " " + InfraSupport.rect(rm.getDirtyRegion(c));
                }));
        checks.add(Checks.expect("markCompletelyClean / isCompletelyDirty / getDirtyRegion", "false 0,0 0x0", () -> {
            rm.markCompletelyClean(c);
            return rm.isCompletelyDirty(c) + " " + InfraSupport.rect(rm.getDirtyRegion(c));
        }));

        // a custom RepaintManager recording the calls (it does not queue anything : the default one is restored)
        List<String> recorded = new ArrayList<>();
        RepaintManager recorder = new RepaintManager() {
            @Override
            public void addDirtyRegion(JComponent component, int x, int y, int w, int h) {
                recorded.add("addDirtyRegion " + InfraSupport.name(component) + " " + x + "," + y + " " + w + "x" + h);
            }

            @Override
            public void addInvalidComponent(JComponent component) {
                recorded.add("addInvalidComponent " + InfraSupport.name(component));
            }
        };
        checks.add(Checks.expect("custom RepaintManager : recorded calls",
                "addDirtyRegion non-opaque 5,5 10x10 ; addDirtyRegion checker 0,0 116x96 ; "
                        + "addInvalidComponent non-opaque",
                () -> {
                    RepaintManager.setCurrentManager(recorder);
                    try {
                        d.nonOpaque.repaint(5, 5, 10, 10);
                        d.checker.repaint(new Rectangle(0, 0, 116, 96));
                        d.nonOpaque.revalidate();
                    } finally {
                        RepaintManager.setCurrentManager(rm);
                    }
                    lines.add("custom RepaintManager :");
                    recorded.forEach(r -> lines.add("  " + r));
                    return String.join(" ; ", recorded);
                }));
        checks.add(Checks.expect("RepaintManager restored", "true", () -> RepaintManager.currentManager(c) == rm));

        // revalidate + validateInvalidComponents : synchronous layout
        checks.add(Checks.expect("revalidate() + validateInvalidComponents() : new size", "96x70", () -> {
            JPanel holder = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)) {
                @Override
                public boolean isValidateRoot() {
                    return true;
                }
            };
            JPanel child = new JPanel();
            child.setPreferredSize(new Dimension(40, 30));
            holder.add(child);
            holder.setBounds(0, 0, 100, 80);
            d.checker.add(holder, 0);
            try {
                holder.validate();
                child.setPreferredSize(new Dimension(96, 70));
                child.revalidate();
                rm.validateInvalidComponents();
                String size = InfraSupport.size(child.getSize());
                lines.add("revalidate + validateInvalidComponents : " + size);
                return size;
            } finally {
                d.checker.remove(holder);
                d.checker.repaint();
            }
        }));
    }

    /**
     * {@code check} as is at an integral UI scale ; informational at a fractional scale (e.g. 150 %), where the
     * RepaintManager enlarges the clip of a paintImmediately to whole device pixels.
     */
    private static Check atIntegralScale(Component c, Check check) {
        double scale = c.getGraphicsConfiguration() == null ? 1
                : c.getGraphicsConfiguration().getDefaultTransform().getScaleY();
        return scale == Math.rint(scale) || !Boolean.FALSE.equals(check.ok()) ? check
                : Check.info(check.name(), check.value() + " (UI scale " + Checks.num(scale, 2) + ")");
    }

    private static void paintImmediately(Demo d, List<Check> checks, List<String> lines) {
        checks.add(atIntegralScale(d.opaque, Checks.expect("opaque.paintImmediately(10,10,30,30)",
                "opaque clip 10,10 30x30", () -> {
            d.paintLog.clear();
            d.opaque.paintImmediately(10, 10, 30, 30);
            List<String> log = new ArrayList<>(d.paintLog);
            lines.add("opaque.paintImmediately(10,10,30,30)");
            log.forEach(l -> lines.add("  " + l));
            return log.isEmpty() ? "nothing painted, visible rect " + InfraSupport.rect(d.opaque.getVisibleRect())
                    : String.join(" ; ", log);
        })));
        checks.add(atIntegralScale(d.opaque, Checks.expect(
                "non-opaque.paintImmediately(10,10,30,30) : painted from its opaque parent",
                "checker clip 28,26 30x30 ; non-opaque clip 10,10 30x30", () -> {
                    d.paintLog.clear();
                    d.nonOpaque.paintImmediately(10, 10, 30, 30);
                    List<String> log = new ArrayList<>(d.paintLog);
                    lines.add("non-opaque.paintImmediately(10,10,30,30)");
                    log.forEach(l -> lines.add("  " + l));
                    return String.join(" ; ", log);
                })));
        checks.add(Checks.expect("paintImmediately with double buffering disabled", "opaque clip 0,0 80x64", () -> {
            RepaintManager rm = RepaintManager.currentManager(d.opaque);
            d.paintLog.clear();
            rm.setDoubleBufferingEnabled(false);
            try {
                d.opaque.paintImmediately(d.opaque.getVisibleRect());
            } finally {
                rm.setDoubleBufferingEnabled(true);
            }
            List<String> log = new ArrayList<>(d.paintLog);
            lines.add("double buffering off : paintImmediately(visibleRect)");
            log.forEach(l -> lines.add("  " + l));
            return String.join(" ; ", log);
        }));
        checks.add(Checks.expect("printAll of the tile : print state seen by paintComponent",
                "checker clip 0,0 116x96 (print) ; non-opaque clip 0,0 80x64 (print)", () -> {
                    d.paintLog.clear();
                    BufferedImage image = new BufferedImage(TILE_W, TILE_H, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    try {
                        d.checker.printAll(g);
                    } finally {
                        g.dispose();
                    }
                    return String.join(" ; ", d.paintLog);
                }));
        checks.add(Checks.expect("isOptimizedDrawingEnabled : JPanel, JLayeredPane (overlapping)", "true, false",
                () -> d.opaque.isOptimizedDrawingEnabled() + ", " + d.layered.isOptimizedDrawingEnabled()));
        checks.add(Checks.expect("JLayeredPane : layers of the 3 boxes, top first", "300 100 0", () -> {
            StringBuilder sb = new StringBuilder();
            for (Component c : d.layered.getComponents()) {
                sb.append(sb.isEmpty() ? "" : " ").append(JLayeredPane.getLayer((JComponent) c));
            }
            return sb.toString();
        }));
    }

    private static BufferedImage debugGraphics(JPanel c, BufferedImage plain, List<Check> checks,
            List<String> lines) {
        checks.add(Checks.expect("DebugGraphics defaults : flash color, time, count", "#FFFF0000 100 2",
                () -> Checks.argb(DebugGraphics.flashColor().getRGB()) + " " + DebugGraphics.flashTime() + " "
                        + DebugGraphics.flashCount()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previous = DebugGraphics.logStream();
        String[] log = new String[1];
        BufferedImage[] debugged = new BufferedImage[1];
        checks.add(Checks.run("setDebugGraphicsOptions(LOG_OPTION) + paint(g)", () -> {
            DebugGraphics.setLogStream(new PrintStream(out, true, StandardCharsets.UTF_8));
            c.setDebugGraphicsOptions(DebugGraphics.LOG_OPTION);
            try {
                debugged[0] = paintToImage(c, false);
            } finally {
                c.setDebugGraphicsOptions(DebugGraphics.NONE_OPTION);
                DebugGraphics.setLogStream(previous);
            }
            log[0] = out.toString(StandardCharsets.UTF_8).replace("\r", "")
                    .replaceAll("Graphics(<B>)?\\(\\d+-\\d+\\)", "Graphics(#)")
                    .replaceAll("@[0-9a-f]+", "@#");
            return "ok";
        }));
        List<String> logLines = log[0] == null ? List.of() : List.of(log[0].split("\n"));
        checks.add(Check.info("DebugGraphics log lines", logLines.size()));
        checks.add(Check.info("DebugGraphics log hash (normalized)", log[0] == null ? "none" : Checks.sha256(log[0])));
        // DebugGraphics is a plain Graphics (not a Graphics2D) : text hints, Ocean gradients and line borders are
        // skipped
        checks.add(Checks.expect("DebugGraphics : blue fill pixel as with a plain paint", "true",
                () -> plain.getRGB(20, 40) == debugged[0].getRGB(20, 40) && plain.getRGB(20, 40) == 0xFF1E88E5));
        checks.add(Checks.info("DebugGraphics : pixels differing (not a Graphics2D : no text hints, gradients, "
                + "line borders)", () -> {
            int count = 0;
            for (int y = 0; y < plain.getHeight(); y++) {
                for (int x = 0; x < plain.getWidth(); x++) {
                    if (plain.getRGB(x, y) != debugged[0].getRGB(x, y)) {
                        count++;
                    }
                }
            }
            return count;
        }));
        checks.add(Checks.expect("getDebugGraphicsOptions() after NONE_OPTION", 0, c::getDebugGraphicsOptions));
        logLines.stream().limit(12).forEach(l -> lines.add(l.length() > 140 ? l.substring(0, 140) + "…" : l));
        if (logLines.size() > 12) {
            lines.add("… " + (logLines.size() - 12) + " more lines");
        }

        // CellRendererPane : how JTable, JList and JTree paint their renderers
        checks.add(Checks.expect("SwingUtilities.paintComponent (CellRendererPane) : renderer pixel",
                Checks.argb(0xFFFFB74D), () -> {
                    JLabel renderer = new JLabel("cell");
                    renderer.setOpaque(true);
                    renderer.setBackground(new Color(0xFFB74D));
                    BufferedImage image = new BufferedImage(60, 20, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    try {
                        SwingUtilities.paintComponent(g, renderer, new CellRendererPane(), 0, 0, 60, 20);
                    } finally {
                        g.dispose();
                    }
                    return Checks.argb(image.getRGB(55, 2));
                }));
        return debugged[0];
    }

    private static void visibleRect(Demo d, List<Check> checks) {
        checks.add(Checks.expect("viewport view getVisibleRect()", "30,20 100x80",
                () -> InfraSupport.rect(d.viewportView.getVisibleRect())));
        checks.add(Checks.expect("scrollRectToVisible(250,150 20x20) : view position", "170,90", () -> {
            d.viewportView.scrollRectToVisible(new Rectangle(250, 150, 20, 20));
            java.awt.Point p = d.viewport.getViewPosition();
            return p.x + "," + p.y;
        }));
    }

    private static void onScreen(Demo d, List<Check> checks) {
        checks.add(Checks.info("getGraphics() of a showing component", () -> {
            Graphics g = d.opaque.getGraphics();
            try {
                return g == null ? "null" : className(g) + " / "
                        + className(((Graphics2D) g).getDeviceConfiguration());
            } finally {
                if (g != null) {
                    g.dispose();
                }
            }
        }));
        checks.add(Checks.info("createImage(50, 50)", () -> className(d.opaque.createImage(50, 50))));
        checks.add(Checks.expect("VolatileImage paint == BufferedImage paint (solid fills)", "true", () -> {
            VolatileImage volatileImage = d.opaque.createVolatileImage(80, 64);
            if (volatileImage == null) {
                return "no volatile image";
            }
            for (int attempt = 0; attempt < 5; attempt++) {
                volatileImage.validate(d.opaque.getGraphicsConfiguration());
                Graphics2D g = volatileImage.createGraphics();
                try {
                    paintBlocks(g);
                } finally {
                    g.dispose();
                }
                if (!volatileImage.contentsLost()) {
                    break;
                }
            }
            BufferedImage snapshot = volatileImage.getSnapshot();
            BufferedImage reference = new BufferedImage(80, 64, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = reference.createGraphics();
            try {
                paintBlocks(g);
            } finally {
                g.dispose();
            }
            volatileImage.flush();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 80; x++) {
                    if ((snapshot.getRGB(x, y) & 0xFFFFFF) != (reference.getRGB(x, y) & 0xFFFFFF)) {
                        return "pixel " + x + "," + y + " " + Checks.argb(snapshot.getRGB(x, y)) + " vs "
                                + Checks.argb(reference.getRGB(x, y));
                    }
                }
            }
            return true;
        }));
    }

    private static void paintBlocks(Graphics2D g) {
        g.setColor(new Color(0xECEFF1));
        g.fillRect(0, 0, 80, 64);
        g.setColor(new Color(0x1E88E5));
        g.fillRect(8, 8, 30, 20);
        g.setColor(new Color(0xE53935));
        g.fillRect(40, 30, 32, 26);
    }

    private static String className(Object o) {
        if (o == null) {
            return "null";
        }
        String name = o.getClass().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
