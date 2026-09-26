package io.quarkiverse.desktop.showcase.pages.swing.containers;

import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.captioned;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.column;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.row;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.section;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.sized;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Heavyweight / lightweight mixing : AWT heavyweight components (native windows) under Swing lightweight components.
 * The toolkit cuts the shape of every heavyweight component ({@code applyShape} of its peer) so that the lightweight
 * components above it in the z-order stay visible, and clips it to its lightweight ancestors. Cases : an AWT
 * {@link Button} partly covered by a {@link JInternalFrame}, a light weight {@link JPopupMenu} over an AWT
 * {@link Canvas}, {@code Component.setMixingCutoutShape} of round lightweight components over canvases (default :
 * the bounds, an ellipse, an empty shape), and a heavyweight button clipped by its lightweight parent.
 * <p>
 * The page image (printAll) paints every heavyweight component completely (the native WM_PRINT ignores the window
 * shape) : only the screen shows mixing. This page therefore needs the focus (its window in front) : Robot reads
 * pixels of solid areas (checks) and captures the area ({@code screen} extra image, reported as SCREEN by Compare),
 * or records {@code skipped: not focused}.
 */
@Singleton
public class AwtMixingPage implements FeaturePage {

    private static final int FRAME_COLOR = 0x3949AB;
    private static final int CANVAS_RED = 0xE53935;
    private static final int CANVAS_GREEN = 0x43A047;
    private static final int DISC_BLUE = 0x1E88E5;
    private static final int POPUP_PURPLE = 0x8E24AA;
    private static final int GREY = 0xECEFF1;
    private static final int PANEL_YELLOW = 0xFFF9C4;
    private static final int REFERENCE_TEAL = 0x00897B;

    private State state;

    private static final class State {
        JPanel area;
        JDesktopPane desktop;
        Button coveredButton;
        JInternalFrame frame;
        Canvas popupCanvas;
        JPopupMenu popup;
        JComponent popupItem;
        final List<JLayeredPane> cutouts = new ArrayList<>();
        final List<Canvas> cutoutCanvases = new ArrayList<>();
        final List<JComponent> discs = new ArrayList<>();
        JPanel clipPanel;
        Button clippedButton;
        JLabel reference;
        BufferedImage screen;
        BufferedImage printed;
        SwingKit.CheckColumns table;
    }

    @Override
    public String id() {
        return "swing-awt-mixing";
    }

    @Override
    public String title() {
        return "Heavyweight and lightweight mixing";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 150;
    }

    @Override
    public boolean needsFocus() {
        return true;
    }

    @Override
    public Component build() {
        State s = new State();
        state = s;

        // --- an AWT button partly covered by an internal frame
        JDesktopPane desktop = new JDesktopPane();
        desktop.setBackground(new Color(GREY));
        s.coveredButton = new Button("AWT Button (heavyweight)");
        s.coveredButton.setBounds(30, 60, 200, 60);
        desktop.add(s.coveredButton, JLayeredPane.DEFAULT_LAYER);
        s.frame = new JInternalFrame("JInternalFrame", false, false, false, false);
        JLabel content = SwingKit.block("lightweight", FRAME_COLOR, 100, 60);
        s.frame.getContentPane().add(content);
        s.frame.setBounds(140, 20, 220, 140);
        desktop.add(s.frame, JLayeredPane.PALETTE_LAYER);
        s.frame.setVisible(true);
        SwingKit.unfocusable(s.frame);
        s.desktop = sized(desktop, 460, 190);

        // --- a heavyweight button clipped by its lightweight parent
        JPanel clipHolder = new JPanel(null);
        clipHolder.setOpaque(false);
        s.clipPanel = new JPanel(null);
        s.clipPanel.setBackground(new Color(PANEL_YELLOW));
        s.clipPanel.setBounds(0, 0, 200, 60);
        s.clippedButton = new Button("clipped by the JPanel at x = 200");
        s.clippedButton.setBounds(100, 10, 220, 40);
        s.clipPanel.add(s.clippedButton);
        clipHolder.add(s.clipPanel);
        // a lightweight reference area : when its pixels are not on screen, the window is covered by another one
        s.reference = SwingKit.block("reference (lightweight)", REFERENCE_TEAL, 160, 24);
        s.reference.setBounds(0, 80, 160, 24);
        clipHolder.add(s.reference);
        sized(clipHolder, 400, 104);

        // --- a light weight popup menu over a canvas (shown in ready)
        s.popupCanvas = new SolidCanvas(CANVAS_RED);
        s.popupCanvas.setPreferredSize(new Dimension(360, 150));
        s.popup = new JPopupMenu();
        s.popupItem = new Swatch(POPUP_PURPLE, "JPopupMenu (light weight)");
        s.popup.add(s.popupItem);

        // --- mixing cutout shapes
        String[] names = { "default (bounds)", "ellipse, window coordinates", "ellipse, own coordinates",
                "empty shape" };
        List<Component> cutoutViews = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            JLayeredPane pane = new JLayeredPane();
            pane.setOpaque(true);
            pane.setBackground(new Color(GREY));
            Canvas canvas = new SolidCanvas(CANVAS_GREEN);
            canvas.setBounds(0, 0, 140, 120);
            pane.add(canvas, JLayeredPane.DEFAULT_LAYER);
            Disc disc = new Disc();
            disc.setBounds(20, 10, 100, 100);
            if (i == 2) {
                // the javadoc suggests the coordinates of the component : the region is used as is, in the
                // coordinates of the window (see ready() for the ellipse that works)
                disc.setMixingCutoutShape(new Ellipse2D.Float(0, 0, 100, 100));
            } else if (i == 3) {
                disc.setMixingCutoutShape(new Rectangle());
            }
            pane.add(disc, JLayeredPane.PALETTE_LAYER);
            s.cutouts.add(sized(pane, 140, 120));
            s.cutoutCanvases.add(canvas);
            s.discs.add(disc);
            cutoutViews.add(captioned(names[i], pane));
        }

        s.area = column(14,
                row(24, section("AWT Button under a JInternalFrame", null, s.desktop),
                        section("Heavyweight clipped by its lightweight parent", null, clipHolder)),
                row(24, section("JPopupMenu over an AWT Canvas", null, s.popupCanvas),
                        section("setMixingCutoutShape of a round lightweight over canvases", null,
                                row(12, cutoutViews.toArray(Component[]::new)))));
        s.table = SwingKit.pending("Mixing");
        return column(14,
                Ui.heading("Heavyweight and lightweight mixing"),
                Ui.text("AWT heavyweight components under Swing lightweight ones. This image is printed with "
                        + "JComponent.printAll, which skips heavyweight components : they are missing here. The extra "
                        + "image 'printed-heavyweights' adds them with their own printAll (the peer prints the native "
                        + "window, WM_PRINT on Windows, ignoring the cut shapes). The screen capture (extra image "
                        + "'screen', taken when the window has the focus) and the pixel checks show the cut shapes. "
                        + "The pixel checks (Robot) are only in report.json : they depend on the focus and on the "
                        + "windows of other applications, this image does not.", SwingKit.WIDTH),
                s.area, s.table);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        // deterministic checks in the table ; the screen checks are attached to the (checks free) mixing area
        s.table.setChecks(staticChecks(s));
        List<Check> checks = new ArrayList<>();
        // Component.getLocationOnWindow (package private) of the second disc : its cutout ellipse in window coordinates
        JComponent disc = s.discs.get(1);
        Point onWindow = SwingUtilities.convertPoint(disc, 0, 0, SwingUtilities.getWindowAncestor(disc));
        disc.setMixingCutoutShape(new Ellipse2D.Float(onWindow.x, onWindow.y, 100, 100));
        return Edt.until(Edt::ownsFocus, 2_000, "focus")
                .handle((v, error) -> Edt.ownsFocus())
                .thenCompose(focused -> {
                    if (!focused) {
                        checks.add(Check.info("screen checks", "skipped: not focused"));
                        s.screen = placeholder();
                        return CompletableFuture.completedFuture(null);
                    }
                    s.popup.show(s.popupCanvas, 40, 30);
                    java.awt.Window window = SwingUtilities.getWindowAncestor(s.popupCanvas);
                    checks.add(Checks.expect("popup: weight over the canvas", SwingKit.expectedKind("light", window),
                            () -> SwingKit.popupKind(s.popupItem, window)));
                    return Edt.rounds(3).thenCompose(v -> Edt.delay(500)).thenCompose(v -> screenChecks(s, checks));
                })
                .whenComplete((v, error) -> {
                    s.popup.setVisible(false);
                })
                .thenAccept(v -> s.printed = printedWithHeavyweights(s))
                .thenAccept(v -> Checks.attach(s.area, checks))
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        Map<String, BufferedImage> extras = new LinkedHashMap<>();
        if (state.printed != null) {
            extras.put("printed-heavyweights", state.printed);
        }
        if (state.screen != null) {
            // "screen" : reported as SCREEN (not a mismatch) by tools/Compare.java
            extras.put("screen", state.screen);
        }
        return CompletableFuture.completedFuture(extras);
    }

    @Override
    public void dispose(Component content) {
        State s = state;
        state = null;
        if (s != null) {
            s.popup.setVisible(false);
        }
    }

    private static List<Check> staticChecks(State s) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("lightweight: Button, Canvas, JInternalFrame, disc", "false false true true",
                () -> s.coveredButton.isLightweight() + " " + s.popupCanvas.isLightweight() + " "
                        + s.frame.isLightweight() + " " + s.discs.getFirst().isLightweight()));
        checks.add(Checks.expect("z-order in the desktop (frame above the button)", "0 1",
                () -> s.desktop.getComponentZOrder(s.frame) + " " + s.desktop.getComponentZOrder(s.coveredButton)));
        checks.add(Checks.expect("z-order in the cutout panes (disc above the canvas)", "0 1",
                () -> s.cutouts.getFirst().getComponentZOrder(s.discs.getFirst()) + " "
                        + s.cutouts.getFirst().getComponentZOrder(s.cutoutCanvases.getFirst())));
        checks.add(Checks.expect("sun.awt.disableMixing", "null",
                () -> String.valueOf(System.getProperty("sun.awt.disableMixing"))));
        checks.add(Checks.info("clipped button: bounds in its parent / parent size",
                () -> SwingKit.rect(s.clippedButton.getBounds()) + " / " + SwingKit.size(s.clipPanel.getSize())));
        return checks;
    }

    private static CompletionStage<Void> screenChecks(State s, List<Check> checks) {
        // screen points of solid areas, computed on the event dispatch thread
        Map<String, Point> points = new LinkedHashMap<>();
        Map<String, Integer> expected = new LinkedHashMap<>();
        points.put("internal frame over the button", screen(s.desktop, 180, 95));
        expected.put("internal frame over the button", FRAME_COLOR);
        points.put("button outside the frame", screen(s.desktop, 50, 70));
        expected.put("button outside the frame", null);
        points.put("popup over the canvas", screen(s.popupItem, 10, s.popupItem.getHeight() / 2));
        expected.put("popup over the canvas", POPUP_PURPLE);
        points.put("canvas beside the popup", screen(s.popupCanvas, 340, 130));
        expected.put("canvas beside the popup", CANVAS_RED);
        points.put("reference", screen(s.reference, 150, 12));
        expected.put("reference", REFERENCE_TEAL);
        String[] names = { "default", "ellipse window", "ellipse own", "empty" };
        int[] corners = { GREY, CANVAS_GREEN, CANVAS_GREEN, CANVAS_GREEN };
        int[] centers = { DISC_BLUE, DISC_BLUE, CANVAS_GREEN, CANVAS_GREEN };
        for (int i = 0; i < names.length; i++) {
            points.put("cutout " + names[i] + ": disc corner", screen(s.cutouts.get(i), 23, 13));
            expected.put("cutout " + names[i] + ": disc corner", corners[i]);
            points.put("cutout " + names[i] + ": disc center", screen(s.cutouts.get(i), 70, 60));
            expected.put("cutout " + names[i] + ": disc center", centers[i]);
        }
        points.put("clipped button: outside its parent", screen(s.clipPanel, 260, 30));
        expected.put("clipped button: outside its parent", 0xFFFFFF);
        points.put("clipped button: inside its parent", screen(s.clipPanel, 106, 16));
        expected.put("clipped button: inside its parent", null);
        Rectangle area = new Rectangle(s.area.getLocationOnScreen(), s.area.getSize());
        GraphicsConfiguration gc = s.area.getGraphicsConfiguration();
        Point reference = points.get("reference");
        Point canvas = points.get("canvas beside the popup");
        Point popup = points.get("popup over the canvas");
        java.awt.Window window = SwingUtilities.getWindowAncestor(s.area);
        // wait until the window is painted on screen : the lightweight reference area, a heavyweight canvas and the
        // popup show their colors (slow with -Xint, while the window comes to the front, or for a heavy weight popup
        // window). Another application (or showcase process) may still cover the window : bring it to the front once
        // more and wait again.
        return Edt.background(() -> painted(gc, reference, canvas, popup, 4_000))
                .thenCompose(painted -> {
                    if (painted) {
                        return CompletableFuture.completedFuture(true);
                    }
                    window.toFront();
                    return Edt.background(() -> painted(gc, reference, canvas, popup, 5_000));
                })
                .thenCompose(painted -> Edt.background(() -> {
                    Robot robot = new Robot(gc.getDevice());
                    Thread.sleep(300);
                    Map<String, Integer> colors = new LinkedHashMap<>();
                    for (Map.Entry<String, Point> e : points.entrySet()) {
                        colors.put(e.getKey(), robot.getPixelColor(e.getValue().x, e.getValue().y).getRGB() & 0xFFFFFF);
                    }
                    BufferedImage capture = robot.createScreenCapture(area);
                    return Map.entry(colors, capture);
                }))
                .thenAccept(result -> {
                    // the window may have lost the focus meanwhile, or be covered by another window : the pixels are
                    // then those of another window
                    boolean stillFocused = Edt.ownsFocus();
                    boolean covered = result.getKey().get("reference") != REFERENCE_TEAL;
                    result.getKey().forEach((name, rgb) -> {
                        Integer want = expected.get(name);
                        String value = String.format(java.util.Locale.ROOT, "#%06X", rgb);
                        if (!stillFocused || covered) {
                            checks.add(Check.info("pixel " + name, !stillFocused ? "skipped: focus lost"
                                    : "skipped: window covered"));
                        } else if (want == null) {
                            checks.add(Check.info("pixel " + name, value));
                        } else {
                            String wanted = String.format(java.util.Locale.ROOT, "#%06X", want);
                            checks.add(Check.of("pixel " + name, wanted.equals(value), wanted.equals(value) ? value
                                    : "expected " + wanted + " but got " + value));
                        }
                    });
                    s.screen = result.getValue();
                })
                .exceptionally(error -> {
                    checks.add(Check.fail("robot", Checks.describe(error)));
                    s.screen = placeholder();
                    return null;
                });
    }

    /**
     * The mixing area printed by Swing, with every heavyweight component printed over it by its own printAll (the
     * peer prints the native control : WM_PRINT on Windows).
     */
    private static BufferedImage printedWithHeavyweights(State s) {
        BufferedImage image = Snapshots.render(s.area);
        Graphics2D g = image.createGraphics();
        try {
            List<Component> heavyweights = new ArrayList<>(List.of(s.coveredButton, s.clippedButton, s.popupCanvas));
            heavyweights.addAll(s.cutoutCanvases);
            for (Component hw : heavyweights) {
                Point p = SwingUtilities.convertPoint(hw.getParent(), hw.getLocation(), s.area);
                Graphics cg = g.create(p.x, p.y, hw.getWidth(), hw.getHeight());
                try {
                    hw.printAll(cg);
                } finally {
                    cg.dispose();
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Polls (off the event dispatch thread) until the three points show their colors, at most {@code millis}.
     */
    private static boolean painted(GraphicsConfiguration gc, Point reference, Point canvas, Point popup, long millis)
            throws Exception {
        Robot robot = new Robot(gc.getDevice());
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (true) {
            boolean ok = (robot.getPixelColor(reference.x, reference.y).getRGB() & 0xFFFFFF) == REFERENCE_TEAL
                    && (robot.getPixelColor(canvas.x, canvas.y).getRGB() & 0xFFFFFF) == CANVAS_RED
                    && (robot.getPixelColor(popup.x, popup.y).getRGB() & 0xFFFFFF) == POPUP_PURPLE;
            if (ok || System.nanoTime() > deadline) {
                return ok;
            }
            Thread.sleep(100);
        }
    }

    private static Point screen(Component c, int x, int y) {
        Point p = c.getLocationOnScreen();
        return new Point(p.x + x, p.y + y);
    }

    private static BufferedImage placeholder() {
        return Snapshots.offscreen(300, 40, g -> {
            g.setColor(new Color(0xFFFFFF));
            g.fillRect(0, 0, 300, 40);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x555555));
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            g.drawString("not focused : no screen capture", 10, 24);
        });
    }

    /**
     * A heavyweight canvas painting a solid color.
     */
    private static final class SolidCanvas extends Canvas {

        private final int rgb;

        SolidCanvas(int rgb) {
            this.rgb = rgb;
            setBackground(new Color(rgb));
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(new Color(rgb));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * A lightweight, non opaque component painting a blue disc (the rest of its bounds is transparent).
     */
    private static final class Disc extends JComponent {

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(DISC_BLUE));
                Shape disc = new Ellipse2D.Float(0, 0, getWidth(), getHeight());
                g.fill(disc);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * A popup menu item painting a solid color with a label.
     */
    private static final class Swatch extends JComponent {

        private final int rgb;
        private final String text;

        Swatch(int rgb, String text) {
            this.rgb = rgb;
            this.text = text;
            setPreferredSize(new Dimension(200, 44));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(rgb));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0xFFFFFF));
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
                g.drawString(text, 30, 27);
            } finally {
                g.dispose();
            }
        }
    }
}
