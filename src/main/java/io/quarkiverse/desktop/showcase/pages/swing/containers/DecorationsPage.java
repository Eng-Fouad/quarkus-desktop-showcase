package io.quarkiverse.desktop.showcase.pages.swing.containers;

import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.captioned;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.column;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.icon;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.row;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.section;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.sized;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.ui;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JToolTip;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.LayerUI;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Borders, separators, tool tips and {@link JLayer} : every {@link BorderFactory} border (line, rounded, etched,
 * bevel, soft bevel, matte with a color and with an icon tiled from a GIF resource loaded through
 * {@code ImageIcon(URL)}, empty, dashed, stroke, compound, titled in the 6 positions and 5 justifications), a custom
 * {@link AbstractBorder} and look and feel borders ; {@link JSeparator}s ; {@link JToolTip}s shown as components (plain,
 * HTML, colors, Metal accelerator text) and real tool tips posted with the Ctrl+F1 key binding of the
 * {@link ToolTipManager} (light weight, medium weight, and heavy weight when the tip leaves the window) ; {@link JLayer}
 * with paint-over, a blur made with {@link ConvolveOp}, a spotlight and event interception.
 * <p>
 * Tool tips are disabled in snapshot mode and Metal only shows them when the application has the focus : the page
 * enables them and sets {@code ToolTipManager.enableToolTipMode} to {@code allWindows} while it posts them, then
 * restores both.
 */
@Singleton
public class DecorationsPage implements FeaturePage {

    private static final int INK = 0x1565C0;
    private static final int ACCENT = 0xEF6C00;
    private static final String TOOLTIP_MODE = "ToolTipManager.enableToolTipMode";

    private State state;

    private static final class State {
        final Map<String, Border> borders = new LinkedHashMap<>();
        final Map<String, BufferedImage> extras = new LinkedHashMap<>();
        final List<String> layerEvents = new ArrayList<>();
        final List<TipButton> tipButtons = new ArrayList<>();
        ImageIcon tile;
        JLayer<JComponent> eventLayer;
        JButton eventButton;
        JLayer<JComponent> blurLayer;
        BlurUI blur;
        SwingKit.CheckColumns borderTable;
        SwingKit.CheckColumns tipTable;
        Boolean tipsEnabled;
    }

    @Override
    public String id() {
        return "swing-decorations";
    }

    @Override
    public String title() {
        return "Borders, separators, tool tips, JLayer";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 140;
    }

    @Override
    public Component build() {
        State s = new State();
        state = s;
        // a GIF classpath resource through ImageIcon(URL) : resource: URL in a native executable, Toolkit GIF decoder
        s.tile = new ImageIcon(Edt.resource("/showcase/swing-containers/tile.gif"));

        s.borderTable = SwingKit.pending("Borders and separators");
        s.tipTable = SwingKit.pending("Tool tips and layers");
        return column(18,
                Ui.heading("Borders, separators, tool tips, JLayer"),
                Ui.text("BorderFactory borders on labels, titled borders in every position and justification, look "
                        + "and feel borders, separators, tool tips as components and posted with Ctrl+F1 (extra images "
                        + "tooltip-*), and JLayer decorations.", SwingKit.WIDTH),
                // near the top of the page : the posted light and medium weight tips must fit in the window
                section("Posted tool tips", "Once the page is ready, each button posts its tool tip with Ctrl+F1 (the "
                        + "tool tip manager key binding), the tip is captured (extra images tooltip-*) and hidden with "
                        + "Ctrl+F1 again.", tipButtons(s)),
                section("BorderFactory", null, SwingKit.wrap(SwingKit.WIDTH, 12, borderTiles(s))),
                section("TitledBorder : positions (rows) x justifications (columns)", null, titledGrid(s)),
                section("Look and feel borders (Metal) and separators", null, row(24,
                        SwingKit.wrap(640, 12, lafTiles(s)), separators())),
                s.borderTable,
                section("JToolTip", "Tool tips as components (the Metal UI shows the mnemonic of the button of the "
                        + "third one).", SwingKit.wrap(SwingKit.WIDTH, 16, tips())),
                section("JLayer", "Paint over the view, a blur of the view (ConvolveOp), a spotlight, and a layer "
                        + "receiving the mouse events of its view (synthetic press and release on the button).",
                        layers(s)),
                s.tipTable);
    }

    // --------------------------------------------------------------------------------------------------- borders

    private static List<Component> borderTiles(State s) {
        Color ink = new Color(INK);
        Color accent = new Color(ACCENT);
        Map<String, Border> b = s.borders;
        b.put("line", BorderFactory.createLineBorder(ink));
        b.put("line 3", BorderFactory.createLineBorder(ink, 3));
        b.put("line 4 rounded", BorderFactory.createLineBorder(ink, 4, true));
        b.put("etched lowered", BorderFactory.createEtchedBorder());
        b.put("etched raised", BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
        b.put("etched colors", BorderFactory.createEtchedBorder(new Color(0xFFCC80), accent));
        b.put("bevel raised", BorderFactory.createBevelBorder(BevelBorder.RAISED));
        b.put("bevel lowered", BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        b.put("bevel 4 colors", BorderFactory.createBevelBorder(BevelBorder.RAISED, new Color(0xBBDEFB),
                new Color(0x64B5F6), new Color(0x0D47A1), new Color(0x1976D2)));
        b.put("soft bevel raised", BorderFactory.createRaisedSoftBevelBorder());
        b.put("soft bevel lowered", BorderFactory.createLoweredSoftBevelBorder());
        b.put("soft bevel colors", BorderFactory.createSoftBevelBorder(BevelBorder.RAISED, new Color(0xFFE0B2), accent));
        b.put("matte color", BorderFactory.createMatteBorder(4, 12, 4, 12, accent));
        b.put("matte icon (GIF)", BorderFactory.createMatteBorder(12, 12, 12, 12, s.tile));
        b.put("empty 10", BorderFactory.createEmptyBorder(10, 10, 10, 10));
        b.put("dashed", BorderFactory.createDashedBorder(ink));
        b.put("dashed 2/6/3 rounded", BorderFactory.createDashedBorder(accent, 2, 6, 3, true));
        b.put("stroke dotted", BorderFactory.createStrokeBorder(new BasicStroke(3, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 1, new float[] { 0.1f, 6 }, 0), ink));
        b.put("stroke gradient", BorderFactory.createStrokeBorder(new BasicStroke(5),
                new GradientPaint(0, 0, ink, 150, 0, accent)));
        b.put("compound", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ink, 2),
                BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3),
                        BorderFactory.createLineBorder(accent, 2))));
        b.put("titled", BorderFactory.createTitledBorder("Title"));
        b.put("titled custom", BorderFactory.createTitledBorder(BorderFactory.createLineBorder(accent, 2), "Custom",
                TitledBorder.CENTER, TitledBorder.BELOW_TOP, new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 13), accent));
        b.put("custom AbstractBorder", new ShadowBorder());
        List<Component> tiles = new ArrayList<>();
        s.borders.forEach((name, border) -> {
            JLabel label = new JLabel(name, SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(new Color(name.startsWith("empty") ? 0xE3F2FD : 0xFAFAFA));
            label.setBorder(border);
            tiles.add(sized(label, 158, 62));
        });
        return tiles;
    }

    private static Component titledGrid(State s) {
        String[] positions = { "ABOVE_TOP", "TOP", "BELOW_TOP", "ABOVE_BOTTOM", "BOTTOM", "BELOW_BOTTOM" };
        int[] positionValues = { TitledBorder.ABOVE_TOP, TitledBorder.TOP, TitledBorder.BELOW_TOP,
                TitledBorder.ABOVE_BOTTOM, TitledBorder.BOTTOM, TitledBorder.BELOW_BOTTOM };
        String[] justifications = { "LEFT", "CENTER", "RIGHT", "LEADING", "TRAILING" };
        int[] justificationValues = { TitledBorder.LEFT, TitledBorder.CENTER, TitledBorder.RIGHT, TitledBorder.LEADING,
                TitledBorder.TRAILING };
        JPanel grid = new JPanel(new GridLayout(positions.length, justifications.length, 8, 6));
        grid.setOpaque(false);
        Border base = BorderFactory.createLineBorder(new Color(0x90A4AE));
        for (int p = 0; p < positions.length; p++) {
            for (int j = 0; j < justifications.length; j++) {
                TitledBorder border = BorderFactory.createTitledBorder(base, justifications[j],
                        justificationValues[j], positionValues[p]);
                JLabel label = new JLabel(positions[p], SwingConstants.CENTER);
                label.setForeground(new Color(0x78909C));
                label.setBorder(border);
                grid.add(label);
                if (p == 0 && j == 0) {
                    s.borders.put("titled ABOVE_TOP LEFT", border);
                }
            }
        }
        return sized(grid, SwingKit.WIDTH, 6 * 58);
    }

    private static List<Component> lafTiles(State s) {
        String[] keys = { "TextField.border", "TitledBorder.border", "Table.focusCellHighlightBorder", "ToolTip.border",
                "PopupMenu.border", "InternalFrame.border", "InternalFrame.paletteBorder", "Button.border" };
        List<Component> tiles = new ArrayList<>();
        for (String key : keys) {
            Border border = UIManager.getBorder(key);
            s.borders.put(key, border);
            // Button.border only paints on buttons
            JComponent c = key.startsWith("Button") ? new JButton(key) : new JLabel(key, SwingConstants.CENTER);
            c.setFont(c.getFont().deriveFont(11f));
            c.setBorder(border);
            tiles.add(sized(c, 196, 46));
        }
        return tiles;
    }

    private static Component separators() {
        JPanel panel = new JPanel(null);
        panel.setOpaque(true);
        panel.setBackground(new Color(0xFAFAFA));
        panel.setBorder(BorderFactory.createLineBorder(new Color(0xCFD8DC)));
        JSeparator horizontal = new JSeparator();
        horizontal.setBounds(10, 20, 300, 2);
        JSeparator colored = new JSeparator();
        colored.setForeground(new Color(ACCENT));
        colored.setBackground(new Color(0xFFE0B2));
        colored.setBounds(10, 40, 300, 2);
        JSeparator vertical = new JSeparator(SwingConstants.VERTICAL);
        vertical.setBounds(330, 10, 2, 150);
        JPopupMenu.Separator popup = new JPopupMenu.Separator();
        popup.setBounds(10, 60, 300, popup.getPreferredSize().height);
        JToolBar.Separator toolBar = new JToolBar.Separator(new Dimension(12, 40));
        toolBar.setOrientation(SwingConstants.VERTICAL);
        toolBar.setBounds(350, 10, 12, 40);
        panel.add(horizontal);
        panel.add(colored);
        panel.add(vertical);
        panel.add(popup);
        panel.add(toolBar);
        for (Component c : panel.getComponents()) {
            if (c == vertical || c == toolBar) {
                continue;
            }
            JLabel label = new JLabel(((JComponent) c).getUIClassID());
            label.setFont(label.getFont().deriveFont(10f));
            Rectangle r = c.getBounds();
            label.setBounds(r.x, r.y + 3, 300, 14);
            panel.add(label);
        }
        return captioned("JSeparator (H, colored, V), JPopupMenu.Separator, JToolBar.Separator (V)", sized(panel, 370, 200));
    }

    private static List<Check> borderChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JLabel probe = new JLabel("probe");
        probe.setSize(158, 62);
        StringBuilder insets = new StringBuilder();
        s.borders.forEach((name, border) -> {
            if (!name.startsWith("titled") && !name.contains(".")) {
                Insets in = border.getBorderInsets(probe);
                insets.append(name).append('=').append(in.top).append(',').append(in.left).append(',')
                        .append(in.bottom).append(',').append(in.right).append(border.isBorderOpaque() ? " opaque" : "")
                        .append("; ");
            }
        });
        checks.add(Check.info("border insets (top,left,bottom,right)", insets.toString().trim()));
        checks.add(Checks.expect("line 3 / matte color / empty insets", "3,3,3,3 4,12,4,12 10,10,10,10", () -> {
            Insets a = s.borders.get("line 3").getBorderInsets(probe);
            Insets b = s.borders.get("matte color").getBorderInsets(probe);
            Insets c = s.borders.get("empty 10").getBorderInsets(probe);
            return a.top + "," + a.left + "," + a.bottom + "," + a.right + " " + b.top + "," + b.left + "," + b.bottom
                    + "," + b.right + " " + c.top + "," + c.left + "," + c.bottom + "," + c.right;
        }));
        checks.add(Checks.expect("tile.gif through ImageIcon(URL) : load status, size", "8 12x12",
                () -> s.tile.getImageLoadStatus() + " " + s.tile.getIconWidth() + "x" + s.tile.getIconHeight()));
        checks.add(Checks.expect("border classes", "LineBorder EtchedBorder BevelBorder SoftBevelBorder MatteBorder "
                + "EmptyBorder StrokeBorder CompoundBorder TitledBorder", () -> String.join(" ",
                        simple(s.borders.get("line")), simple(s.borders.get("etched raised")),
                        simple(s.borders.get("bevel raised")), simple(s.borders.get("soft bevel raised")),
                        simple(s.borders.get("matte icon (GIF)")), simple(s.borders.get("empty 10")),
                        simple(s.borders.get("dashed")), simple(s.borders.get("compound")),
                        simple(s.borders.get("titled")))));
        checks.add(Checks.info("titled insets (ABOVE_TOP LEFT, titled custom)", () -> {
            Insets a = s.borders.get("titled ABOVE_TOP LEFT").getBorderInsets(probe);
            Insets b = s.borders.get("titled custom").getBorderInsets(probe);
            return a.top + "," + a.left + "," + a.bottom + "," + a.right + " " + b.top + "," + b.left + "," + b.bottom
                    + "," + b.right;
        }));
        checks.add(Checks.info("titled : minimum size, baseline", () -> {
            TitledBorder t = (TitledBorder) s.borders.get("titled");
            return SwingKit.size(t.getMinimumSize(probe)) + " " + t.getBaseline(probe, 158, 62);
        }));
        checks.add(Checks.expect("titled : default border (Ocean theme)",
                "javax.swing.plaf.BorderUIResource$LineBorderUIResource",
                () -> ((TitledBorder) s.borders.get("titled")).getBorder().getClass().getName()));
        checks.add(Checks.expect("look and feel border classes (TextField, TitledBorder, Button)",
                "BorderUIResource$CompoundBorderUIResource BorderUIResource$LineBorderUIResource MetalBorders$ButtonBorder",
                () -> String.join(" ", simple(s.borders.get("TextField.border")),
                        simple(s.borders.get("TitledBorder.border")), simple(button(s.borders.get("Button.border"))))));
        checks.add(Checks.expect("separator UI delegates", "MetalSeparatorUI MetalPopupMenuSeparatorUI "
                + "BasicToolBarSeparatorUI", () -> {
                    JSeparator separator = new JSeparator();
                    JPopupMenu.Separator popup = new JPopupMenu.Separator();
                    JToolBar.Separator toolBar = new JToolBar.Separator();
                    return String.join(" ", simpleName(ui(separator)), simpleName(ui(popup)), simpleName(ui(toolBar)));
                }));
        checks.add(Checks.expect("separator preferred sizes (horizontal, vertical)", "0x2 2x0",
                () -> SwingKit.size(new JSeparator().getPreferredSize()) + " "
                        + SwingKit.size(new JSeparator(SwingConstants.VERTICAL).getPreferredSize())));
        return checks;
    }

    private static Border button(Border border) {
        // Button.border is a compound border (MetalBorders.ButtonBorder inside a margin border)
        return border instanceof javax.swing.border.CompoundBorder c ? c.getOutsideBorder() : border;
    }

    private static String simple(Object o) {
        return o == null ? "null" : simpleName(o.getClass().getName());
    }

    private static String simpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    // ----------------------------------------------------------------------------------------------------- tips

    private static List<Component> tips() {
        List<Component> tips = new ArrayList<>();
        JToolTip plain = new JToolTip();
        plain.setTipText("A plain tool tip");
        tips.add(captioned("plain", plain));
        JToolTip html = new JToolTip();
        html.setTipText("<html><b>HTML</b> tool tip<br>on <i>two</i> lines with <font color=#C62828>color</font></html>");
        tips.add(captioned("HTML", html));
        JButton save = new JButton("Save");
        save.setMnemonic(KeyEvent.VK_S);
        JToolTip accelerator = save.createToolTip();
        accelerator.setTipText("Save the document");
        tips.add(captioned("Metal accelerator text (mnemonic of the button)", accelerator));
        JToolTip colors = new JToolTip();
        colors.setTipText("Custom colors and font");
        colors.setBackground(new Color(0x263238));
        colors.setForeground(new Color(0xFFD54F));
        colors.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        tips.add(captioned("colors, font", colors));
        return tips;
    }

    private static Component tipButtons(State s) {
        TipButton light = new TipButton("light weight tip", "A light weight tool tip (inside the window)", true);
        TipButton medium = new TipButton("medium weight tip", "Light weight popups disabled : medium weight", false);
        TipButton heavy = new TipButton("heavy weight tip", "This tool tip is too wide for the window : it gets a "
                + "heavy weight window of its own, outside the main window", true);
        s.tipButtons.addAll(List.of(light, medium, heavy));
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(SwingKit.WIDTH - 3 * 170 - 3 * 16, 30));
        return row(16, light, medium, spacer, heavy);
    }

    /**
     * A button remembering the tool tip it created (the tip the tool tip manager shows).
     */
    private static final class TipButton extends JButton {

        final boolean lightWeight;
        JToolTip lastTip;

        TipButton(String text, String tip, boolean lightWeight) {
            super(text, icon(0x7E57C2, SwingKit.CIRCLE, 14));
            this.lightWeight = lightWeight;
            setToolTipText(tip);
            setPreferredSize(new Dimension(170, 30));
            setFocusable(true);
        }

        @Override
        public JToolTip createToolTip() {
            lastTip = super.createToolTip();
            return lastTip;
        }
    }

    /**
     * Posts the tool tip of each button with the Ctrl+F1 key binding of the tool tip manager, captures it and hides it
     * (Ctrl+F1 again), within one event dispatch.
     */
    private static void postTips(State s, Window window, List<Check> checks) {
        ToolTipManager manager = ToolTipManager.sharedInstance();
        Object developerMode = UIManager.getDefaults().get(TOOLTIP_MODE);
        boolean overridden = developerMode != null
                && !developerMode.equals(UIManager.getLookAndFeelDefaults().get(TOOLTIP_MODE));
        s.tipsEnabled = manager.isEnabled();
        checks.add(Check.info("tool tip mode of the look and feel", UIManager.getLookAndFeelDefaults().get(TOOLTIP_MODE)));
        try {
            UIManager.put(TOOLTIP_MODE, "allWindows");
            manager.setEnabled(true);
            String[] expected = { "light", "medium", null };
            for (int i = 0; i < s.tipButtons.size(); i++) {
                TipButton button = s.tipButtons.get(i);
                String name = new String[] { "light", "medium", "heavy" }[i];
                manager.setLightWeightPopupEnabled(button.lightWeight);
                button.lastTip = null;
                SwingKit.key(button, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED);
                JToolTip tip = button.lastTip;
                if (tip == null || !tip.isShowing()) {
                    checks.add(Check.fail("tip " + name + ": shown", tip == null ? "not created" : "not showing"));
                    continue;
                }
                String expectedKind = expected[i];
                if (expectedKind == null) {
                    // heavy weight only if the screen extends beyond the window by the width of the tip
                    Rectangle screen = window.getGraphicsConfiguration().getBounds();
                    Point p = button.getLocationOnScreen();
                    boolean room = screen.x + screen.width > window.getX() + window.getWidth() + 20
                            && p.x + 10 + tip.getWidth() > window.getX() + window.getWidth();
                    expectedKind = room ? "heavy" : null;
                }
                if (expectedKind != null) {
                    expectedKind = SwingKit.expectedKind(expectedKind, window);
                }
                String kind = SwingKit.popupKind(tip, window);
                String expectedWeight = expectedKind;
                checks.add(expectedWeight == null ? Check.info("tip " + name + ": weight", kind)
                        : Checks.expect("tip " + name + ": weight", expectedWeight, () -> kind));
                checks.add(Checks.expect("tip " + name + ": text, component", button.getToolTipText() + " true",
                        () -> tip.getTipText() + " " + (tip.getComponent() == button)));
                checks.add(Checks.info("tip " + name + ": size, offset from the button", () -> {
                    Point b = button.getLocationOnScreen();
                    Point t = tip.getLocationOnScreen();
                    return SwingKit.size(tip.getSize()) + " " + (t.x - b.x) + "," + (t.y - b.y);
                }));
                s.extras.put("tooltip-" + name, Snapshots.render(tip));
                // Ctrl+F1 again hides the tip
                SwingKit.key(button, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED);
                checks.add(Checks.expect("tip " + name + ": hidden by a second Ctrl+F1", false, tip::isShowing));
            }
        } finally {
            manager.setLightWeightPopupEnabled(true);
            manager.setEnabled(s.tipsEnabled);
            UIManager.put(TOOLTIP_MODE, overridden ? developerMode : null);
        }
        checks.add(Checks.expect("tool tip manager restored (enabled, light weight, mode)",
                s.tipsEnabled + " true " + UIManager.getLookAndFeelDefaults().get(TOOLTIP_MODE),
                () -> manager.isEnabled() + " " + manager.isLightWeightPopupEnabled() + " " + UIManager.get(TOOLTIP_MODE)));
        checks.add(Checks.expect("tool tip UI, border", "javax.swing.plaf.metal.MetalToolTipUI "
                + "javax.swing.plaf.BorderUIResource$LineBorderUIResource", () -> {
                    JToolTip tip = new JToolTip();
                    return ui(tip) + " " + tip.getBorder().getClass().getName();
                }));
        checks.add(Checks.expect("tool tip delays (initial, dismiss, reshow)", "750 4000 500",
                () -> manager.getInitialDelay() + " " + manager.getDismissDelay() + " " + manager.getReshowDelay()));
    }

    // --------------------------------------------------------------------------------------------------- layers

    private static JPanel form(int rgb) {
        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.setBackground(new Color(rgb));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel fields = new JPanel(new GridLayout(3, 1, 4, 4));
        fields.setOpaque(false);
        fields.add(new JTextField("Swing JLayer"));
        fields.add(new JCheckBox("Decorated", true));
        JSlider slider = new JSlider(0, 100, 65);
        slider.setOpaque(false);
        fields.add(slider);
        form.add(fields, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(new JButton("Apply"));
        buttons.add(new JButton("Close"));
        form.add(buttons, BorderLayout.SOUTH);
        SwingKit.unfocusable(form);
        return form;
    }

    private static Component layers(State s) {
        JLayer<JComponent> wash = new JLayer<>(form(0xE3F2FD), new WashUI());
        s.blur = new BlurUI();
        s.blurLayer = new JLayer<>(form(0xFFF3E0), s.blur);
        JLayer<JComponent> spot = new JLayer<>(form(0xE8F5E9), new SpotlightUI());
        JPanel eventView = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        eventView.setBackground(new Color(0xF3E5F5));
        s.eventButton = new JButton("Button in a layer");
        s.eventButton.setFocusable(false);
        eventView.add(s.eventButton);
        eventView.add(new JLabel("events are logged by the LayerUI"));
        s.eventLayer = new JLayer<>(eventView, new EventLogUI(s.layerEvents));
        return column(12, row(14, captioned("paint over (wash)", sized(wash, 324, 140)),
                captioned("blur (ConvolveOp 5x5)", sized(s.blurLayer, 324, 140)),
                captioned("spotlight (Area)", sized(spot, 324, 140))),
                captioned("event interception (layer event mask)", sized(s.eventLayer, 660, 50)));
    }

    private static final class WashUI extends LayerUI<JComponent> {

        @Override
        public void paint(Graphics graphics, JComponent c) {
            super.paint(graphics, c);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                g.setPaint(new GradientPaint(0, 0, new Color(0x1E88E5), c.getWidth(), 0, new Color(0xAB47BC)));
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(new Color(0xFFFFFF));
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 26));
                g.rotate(-0.25, c.getWidth() / 2.0, c.getHeight() / 2.0);
                g.drawString("PREVIEW", c.getWidth() / 2 - 60, c.getHeight() / 2 + 10);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Paints the view into an image, blurs it with a 5x5 box {@link ConvolveOp} and paints the result.
     */
    private static final class BlurUI extends LayerUI<JComponent> {

        BufferedImage last;

        @Override
        public void paint(Graphics graphics, JComponent c) {
            int w = c.getWidth();
            int h = c.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            BufferedImage view = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D vg = view.createGraphics();
            try {
                vg.setClip(0, 0, w, h);
                super.paint(vg, c);
            } finally {
                vg.dispose();
            }
            float[] data = new float[25];
            java.util.Arrays.fill(data, 1f / 25);
            BufferedImage blurred = new ConvolveOp(new Kernel(5, 5, data), ConvolveOp.EDGE_NO_OP, null).filter(view, null);
            last = blurred;
            graphics.drawImage(blurred, 0, 0, null);
        }
    }

    private static final class SpotlightUI extends LayerUI<JComponent> {

        @Override
        public void paint(Graphics graphics, JComponent c) {
            super.paint(graphics, c);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Area shade = new Area(new Rectangle(0, 0, c.getWidth(), c.getHeight()));
                shade.subtract(new Area(new Ellipse2D.Float(20, 10, 170, 90)));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
                g.setColor(new Color(0x000000));
                g.fill(shade);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Receives the mouse events of the view (layer event mask) and logs them.
     */
    private static final class EventLogUI extends LayerUI<JComponent> {

        private final List<String> log;

        EventLogUI(List<String> log) {
            this.log = log;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            ((JLayer<?>) c).setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK);
            log.add("installUI");
        }

        @Override
        public void uninstallUI(JComponent c) {
            ((JLayer<?>) c).setLayerEventMask(0);
            super.uninstallUI(c);
        }

        @Override
        protected void processMouseEvent(MouseEvent e, JLayer<? extends JComponent> l) {
            String name = switch (e.getID()) {
                case MouseEvent.MOUSE_PRESSED -> "pressed";
                case MouseEvent.MOUSE_RELEASED -> "released";
                case MouseEvent.MOUSE_CLICKED -> "clicked";
                case MouseEvent.MOUSE_ENTERED -> "entered";
                case MouseEvent.MOUSE_EXITED -> "exited";
                default -> String.valueOf(e.getID());
            };
            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), l);
            log.add(name + " " + e.getComponent().getClass().getSimpleName() + " at " + p.x + "," + p.y);
        }
    }

    private static List<Check> layerChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JButton button = s.eventButton;
        List<String> actions = new ArrayList<>();
        button.addActionListener(e -> actions.add(e.getActionCommand()));
        checks.add(Checks.expect("layer: synthetic press, release, click on the button",
                "installUI, pressed JButton at 20,20, released JButton at 20,20, clicked JButton at 20,20 / Button in a layer",
                () -> {
                    int x = 20 - button.getX();
                    int y = 20 - button.getY();
                    for (int id : new int[] { MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                            MouseEvent.MOUSE_CLICKED }) {
                        button.dispatchEvent(new MouseEvent(button, id, 0, id == MouseEvent.MOUSE_CLICKED ? 0
                                : InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                    }
                    return String.join(", ", s.layerEvents) + " / " + String.join(", ", actions);
                }));
        checks.add(Checks.expect("layer: event mask, view, glass pane", AWTEvent.MOUSE_EVENT_MASK + " JPanel true",
                () -> s.eventLayer.getLayerEventMask() + " " + s.eventLayer.getView().getClass().getSimpleName() + " "
                        + (s.eventLayer.getGlassPane() != null)));
        checks.add(Checks.expect("layer: UI class", "EventLogUI", () -> s.eventLayer.getUI().getClass().getSimpleName()));
        checks.add(Checks.expect("blur: uniform background after the box blur (1/25 float weights)", "#FFFEF2DF",
                () -> s.blur.last == null ? "not painted" : Checks.argb(s.blur.last.getRGB(4, 70))));
        checks.add(Checks.info("blur: result (sha256)", () -> s.blur.last == null ? "not painted"
                : Checks.sha256(s.blur.last)));
        return checks;
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        Window window = SwingUtilities.getWindowAncestor(content);
        return Edt.rounds(2)
                .thenCompose(v -> Edt.stable(content, 2_000))
                .thenAccept(v -> {
                    List<Check> checks = new ArrayList<>();
                    postTips(s, window, checks);
                    checks.addAll(layerChecks(s));
                    s.tipTable.setChecks(checks);
                    s.borderTable.setChecks(borderChecks(s));
                })
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(state.extras));
    }

    @Override
    public void dispose(Component content) {
        State s = state;
        state = null;
        if (s != null && s.tipsEnabled != null) {
            ToolTipManager.sharedInstance().setEnabled(s.tipsEnabled);
        }
    }

    /**
     * A custom border : a rounded outline with a soft shadow on the right and bottom.
     */
    private static final class ShadowBorder extends AbstractBorder {

        @Override
        public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 0; i < 4; i++) {
                    g.setColor(new Color(0, 0, 0, 40 - i * 9));
                    g.draw(new RoundRectangle2D.Float(x + 2 + i, y + 2 + i, width - 6, height - 6, 12, 12));
                }
                g.setColor(new Color(INK));
                g.draw(new RoundRectangle2D.Float(x + 1, y + 1, width - 6, height - 6, 12, 12));
            } finally {
                g.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(4, 4, 8, 8);
            return insets;
        }
    }
}
