package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.JWindow;
import javax.swing.LayoutStyle;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.table.JTableHeader;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Helpers shared by the look and feel pages : rendering a component tree offscreen under the current look and feel,
 * and the checks every look and feel page computes (look and feel properties, UI delegates, icons, bundle strings,
 * key bindings and action maps).
 * <p>
 * Everything here runs on the EDT and creates AWT/Swing objects at call time only (never in a static initializer).
 */
final class LafSupport {

    /** Width of a half-width checks table. */
    static final int HALF = 494;

    /** Icons of the UI defaults every look and feel may define (null ones are reported as "none"). */
    static final List<String> ICON_KEYS = List.of(
            "OptionPane.errorIcon", "OptionPane.informationIcon", "OptionPane.warningIcon", "OptionPane.questionIcon",
            "FileView.directoryIcon", "FileView.fileIcon", "FileView.computerIcon", "FileView.hardDriveIcon",
            "FileView.floppyDriveIcon", "FileChooser.newFolderIcon", "FileChooser.upFolderIcon",
            "FileChooser.homeFolderIcon", "FileChooser.detailsViewIcon", "FileChooser.listViewIcon", "Tree.openIcon",
            "Tree.closedIcon", "Tree.leafIcon", "Tree.expandedIcon", "Tree.collapsedIcon", "InternalFrame.icon",
            "InternalFrame.closeIcon", "InternalFrame.maximizeIcon", "InternalFrame.iconifyIcon",
            "InternalFrame.minimizeIcon", "Table.ascendingSortIcon", "Table.descendingSortIcon", "Menu.arrowIcon");

    /**
     * Strings of the look and feel resource bundles ({@code com.sun.swing.internal.plaf.basic.resources.basic} and the
     * bundle of each look and feel) : English values, the same on every operating system.
     */
    static final Map<String, String> BASIC_STRINGS = Map.of(
            "OptionPane.okButtonText", "OK",
            "OptionPane.cancelButtonText", "Cancel",
            "OptionPane.yesButtonText", "Yes",
            "OptionPane.noButtonText", "No",
            "FileChooser.openButtonText", "Open",
            "FileChooser.saveButtonText", "Save",
            "ColorChooser.swatchesNameText", "Swatches",
            "ProgressMonitor.progressText", "Progress...",
            "AbstractUndoableEdit.undoText", "Undo");

    /**
     * Delegates without their own {@code createUI} : the inherited one creates an instance of the superclass.
     */
    private static final Map<String, String> INHERITED_CREATE_UI = Map.of(
            "com.sun.java.swing.plaf.windows.WindowsSeparatorUI", "javax.swing.plaf.basic.BasicSeparatorUI");

    private LafSupport() {
    }

    // ------------------------------------------------------------------------------------------------------ render

    /**
     * {@code component} rendered at its preferred size, laid out in a packed (displayable, never shown) window : its
     * descendants got {@code addNotify} (e.g. a table in a scroll pane has its header) exactly as in the page frame.
     */
    static BufferedImage renderStaged(JComponent component) {
        JWindow stage = new JWindow();
        try {
            stage.setFocusableWindowState(false);
            stage.getContentPane().add(component);
            stage.pack();
            stage.validate();
            return Snapshots.render(component);
        } finally {
            stage.getContentPane().remove(component);
            stage.dispose();
        }
    }

    /**
     * {@code image} scaled by {@code scale} (bilinear, into a TYPE_INT_ARGB image) : thumbnails for the page, the
     * full size images being extra snapshots.
     */
    static BufferedImage thumbnail(BufferedImage image, double scale) {
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return Snapshots.offscreen(w, h, g -> {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, w, h, null);
            g.setColor(new Color(0xB0BEC5));
            g.drawRect(0, 0, w - 1, h - 1);
        });
    }

    /**
     * Thumbnails with their captions, {@code columns} per row.
     */
    static Container thumbnails(Map<String, BufferedImage> images, double scale, int columns) {
        List<Component> rows = new ArrayList<>();
        List<Component> row = new ArrayList<>();
        for (Map.Entry<String, BufferedImage> e : images.entrySet()) {
            row.add(Ui.column(4, Ui.caption(e.getKey()), Ui.image(thumbnail(e.getValue(), scale))));
            if (row.size() == columns) {
                rows.add(Ui.row(12, row.toArray(Component[]::new)));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            rows.add(Ui.row(12, row.toArray(Component[]::new)));
        }
        return Ui.column(12, rows.toArray(Component[]::new));
    }

    /**
     * The page root : a transparent Swing panel stacking {@code children} (AWT lightweight or Swing components).
     */
    static JPanel page(Component... children) {
        JPanel page = new JPanel(new Ui.StackLayout(true, 14));
        page.setOpaque(false);
        for (Component child : children) {
            page.add(child);
        }
        return page;
    }

    /**
     * {@code checks} in two tables side by side (the second one untitled).
     */
    static Container twoColumns(String title, List<Check> checks, int nameWidth) {
        int half = (checks.size() + 1) / 2;
        return Ui.row(12, ChecksView.table(title, checks.subList(0, half), nameWidth, HALF),
                ChecksView.table(checks.size() > half ? " " : "", checks.subList(half, checks.size()), nameWidth, HALF));
    }

    /**
     * Disposes the windows of a page (never throws).
     */
    static void dispose(List<? extends Window> windows) {
        for (Window w : windows) {
            try {
                w.dispose();
            } catch (RuntimeException e) {
                // best effort
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------ checks

    /**
     * Properties of the current look and feel.
     *
     * @param nativeLaf expected {@link LookAndFeel#isNativeLookAndFeel()} (null : informational)
     * @param decorations expected {@link LookAndFeel#getSupportsWindowDecorations()} (null : informational)
     */
    static List<Check> lookAndFeelChecks(String expectedClass, Boolean nativeLaf, Boolean decorations) {
        LookAndFeel laf = UIManager.getLookAndFeel();
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("UIManager.getLookAndFeel()", expectedClass, () -> laf.getClass().getName()));
        checks.add(Check.info("getName()", laf.getName()));
        checks.add(Check.info("getID()", laf.getID()));
        checks.add(Check.info("getDescription()", laf.getDescription()));
        checks.add(Checks.expect("isSupportedLookAndFeel()", true, laf::isSupportedLookAndFeel));
        checks.add(nativeLaf == null ? Check.info("isNativeLookAndFeel()", laf.isNativeLookAndFeel())
                : Checks.expect("isNativeLookAndFeel()", nativeLaf, laf::isNativeLookAndFeel));
        checks.add(decorations == null ? Check.info("getSupportsWindowDecorations()", laf.getSupportsWindowDecorations())
                : Checks.expect("getSupportsWindowDecorations()", decorations, laf::getSupportsWindowDecorations));
        checks.add(Checks.info("getLookAndFeelDefaults() size", () -> UIManager.getLookAndFeelDefaults().size()));
        checks.add(Checks.info("Button.font", () -> font(UIManager.getFont("Button.font"))));
        checks.add(Checks.info("Label.font", () -> font(UIManager.getFont("Label.font"))));
        checks.add(Checks.info("Panel.background", () -> color(UIManager.getColor("Panel.background"))));
        checks.add(Checks.info("Button.background", () -> color(UIManager.getColor("Button.background"))));
        checks.add(Checks.info("textHighlight", () -> color(UIManager.getColor("textHighlight"))));
        checks.add(Checks.info("LayoutStyle gaps (related, unrelated, indent)", LafSupport::layoutGaps));
        return checks;
    }

    /**
     * Preferred gaps of the look and feel's {@link LayoutStyle} (the Windows one uses dialog units : font metrics).
     */
    static String layoutGaps() {
        JPanel parent = new JPanel();
        JButton a = new JButton("One");
        JButton b = new JButton("Two");
        JLabel l = new JLabel("Label");
        parent.add(a);
        parent.add(b);
        parent.add(l);
        LayoutStyle style = LayoutStyle.getInstance();
        return style.getPreferredGap(a, b, LayoutStyle.ComponentPlacement.RELATED, SwingConstants.EAST, parent) + ", "
                + style.getPreferredGap(a, b, LayoutStyle.ComponentPlacement.UNRELATED, SwingConstants.EAST, parent)
                + ", " + style.getPreferredGap(l, a, LayoutStyle.ComponentPlacement.INDENT, SwingConstants.EAST, parent)
                + " ; container " + style.getContainerGap(a, SwingConstants.WEST, parent);
    }

    /**
     * The UI delegate of every distinct UI class id among {@code components}, compared to the class name the look and
     * feel defaults map it to (reflective {@code UIDefaults.getUIClass} + {@code createUI}). Synth based look and feels
     * map every id to the {@code SynthLookAndFeel.createUI} dispatcher : a {@code javax.swing.plaf.synth.Synth*} delegate
     * is expected then.
     */
    static List<Check> uiChecks(List<JComponent> components) {
        Map<String, JComponent> byId = new LinkedHashMap<>();
        for (JComponent c : components) {
            // "ComponentUI" : the id of JComponent subclasses without a delegate (JLayeredPane, Box.Filler...)
            if (!c.getUIClassID().equals("ComponentUI")) {
                byId.putIfAbsent(c.getUIClassID(), c);
            }
        }
        List<Check> checks = new ArrayList<>();
        UIDefaults defaults = UIManager.getDefaults();
        byId.forEach((id, c) -> {
            ComponentUI ui = c.getUI();
            String actual = ui == null ? "null (no UI delegate)" : ui.getClass().getName();
            Object mapped = defaults.get(id);
            String expected = mapped instanceof Class<?> type ? type.getName() : String.valueOf(mapped);
            if (mapped == null || (ui == null && id.equals("InternalFrameTitlePaneUI"))) {
                // not created through the UI manager (e.g. Synth's internal frame title pane paints itself)
                checks.add(Check.info(id, "not mapped, " + (ui == null ? "no UI delegate" : simple(actual))));
            } else if (expected.equals("javax.swing.plaf.synth.SynthLookAndFeel")
                    || expected.equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
                // SynthLookAndFeel.createUI dispatches by id (javax.swing.plaf.synth.Synth*UI, sun.swing.plaf.synth...)
                checks.add(Check.of(id, ui != null && (simple(actual).startsWith("Synth") || simple(actual)
                        .startsWith("GTK")), simple(actual)));
            } else if (ui != null && (expected.equals(actual) || actual.equals(INHERITED_CREATE_UI.get(expected)))) {
                checks.add(Check.pass(id, simple(actual)));
            } else {
                checks.add(Check.fail(id, "expected " + expected + " but got " + actual));
            }
        });
        return checks;
    }

    /**
     * Size and pixel hash of the icons of {@link #ICON_KEYS}, painted with a component of the type they expect.
     */
    static List<Check> iconChecks(Map<String, BufferedImage> painted) {
        List<Check> checks = new ArrayList<>();
        for (String key : ICON_KEYS) {
            checks.add(Checks.info(key, () -> {
                Icon icon = UIManager.getIcon(key);
                if (icon == null) {
                    return "none";
                }
                BufferedImage image = paintIcon(key, icon);
                if (painted != null) {
                    painted.put(key, image);
                }
                return icon.getIconWidth() + "x" + icon.getIconHeight() + " " + Checks.sha256(image);
            }));
        }
        return checks;
    }

    /**
     * {@code icon} painted into a transparent image, with a component of the type the icon expects (Metal internal
     * frame icons cast it to a button, menu arrow icons to a menu item...).
     */
    static BufferedImage paintIcon(String key, Icon icon) {
        Component c;
        if (key.startsWith("InternalFrame.") && !key.equals("InternalFrame.icon")) {
            c = new JButton();
        } else if (key.startsWith("Menu.")) {
            c = new JMenu("Menu");
        } else if (key.startsWith("Tree.")) {
            c = new JTree();
        } else if (key.startsWith("Table.")) {
            c = new JTableHeader();
        } else {
            c = new JLabel();
        }
        int w = Math.max(1, icon.getIconWidth());
        int h = Math.max(1, icon.getIconHeight());
        return Snapshots.offscreen(w, h, g -> icon.paintIcon(c, g, 0, 0));
    }

    /**
     * An image of the painted icons with the last part of their key as caption.
     */
    static BufferedImage iconStrip(Map<String, BufferedImage> icons) {
        int cell = 76;
        int columns = 13;
        int maxHeight = Math.max(16, icons.values().stream().mapToInt(i -> Math.min(48, i.getHeight())).max().orElse(16));
        int rowHeight = maxHeight + 22;
        int rows = Math.max(1, (icons.size() + columns - 1) / columns);
        int height = rows * rowHeight;
        List<Map.Entry<String, BufferedImage>> entries = new ArrayList<>(icons.entrySet());
        return Snapshots.offscreen(columns * cell, height, g -> {
            prepareText(g);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
            for (int i = 0; i < entries.size(); i++) {
                int x = (i % columns) * cell;
                int y = (i / columns) * rowHeight;
                BufferedImage image = entries.get(i).getValue();
                int w = Math.min(48, image.getWidth());
                int h = Math.min(48, image.getHeight());
                g.drawImage(image, x + (cell - w) / 2, y + (maxHeight - h) / 2, w, h, null);
                String key = entries.get(i).getKey();
                String caption = key.substring(key.indexOf('.') + 1).replace("Icon", "");
                g.setColor(new Color(Ui.MUTED_COLOR));
                int tw = g.getFontMetrics().stringWidth(caption);
                g.drawString(caption, x + Math.max(0, (cell - tw) / 2), y + maxHeight + 14);
            }
        });
    }

    static void prepareText(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * Resource bundle strings : {@link #BASIC_STRINGS} and {@code specific} (expected English values), the German
     * Cancel text (locale bundles, informational), and {@code infoKeys} (informational).
     */
    static List<Check> stringChecks(Map<String, String> specific, List<String> infoKeys) {
        List<Check> checks = new ArrayList<>();
        Map<String, String> expected = new java.util.TreeMap<>(BASIC_STRINGS);
        // the look and feel bundle may override a basic string (e.g. Motif : FileChooser.openButtonText = OK)
        expected.putAll(specific);
        expected.forEach((key, value) -> checks.add(Checks.expect(key, value, () -> UIManager.getString(key))));
        checks.add(Checks.info("OptionPane.cancelButtonText (de)",
                () -> UIManager.getString("OptionPane.cancelButtonText", Locale.GERMAN)));
        for (String key : infoKeys) {
            checks.add(Checks.info(key, () -> UIManager.getString(key)));
        }
        return checks;
    }

    /**
     * Key bindings and action maps : the size of the (lazy, reflectively loaded : {@code loadActionMap}) action map and
     * of the input maps (key strokes parsed from strings : reflective {@code KeyEvent.VK_*} fields) of each component.
     *
     * @param required whether a component with actions must have some (false for look and feels that define no key
     *        bindings, such as a Synth look and feel loaded from XML)
     */
    static List<Check> keyChecks(Map<String, JComponent> components, boolean required) {
        List<Check> checks = new ArrayList<>();
        components.forEach((name, c) -> {
            try {
                int actions = c.getActionMap().allKeys() == null ? 0 : c.getActionMap().allKeys().length;
                String value = "actions " + actions + ", keys " + keys(c, JComponent.WHEN_FOCUSED) + " / "
                        + keys(c, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) + " / "
                        + keys(c, JComponent.WHEN_IN_FOCUSED_WINDOW);
                checks.add(required ? Check.of(name, actions > 0, value) : Check.info(name, value));
            } catch (RuntimeException e) {
                checks.add(Check.fail(name, Checks.describe(e)));
            }
        });
        return checks;
    }

    private static int keys(JComponent c, int condition) {
        Object[] keys = c.getInputMap(condition).allKeys();
        return keys == null ? 0 : keys.length;
    }

    // -------------------------------------------------------------------------------------------------- formatting

    static String font(Font font) {
        if (font == null) {
            return "none";
        }
        String style = switch (font.getStyle()) {
            case Font.BOLD -> "bold";
            case Font.ITALIC -> "italic";
            case Font.BOLD | Font.ITALIC -> "bold italic";
            default -> "plain";
        };
        return font.getFamily(Locale.ROOT) + " " + style + " " + Checks.num(font.getSize2D(), 1);
    }

    static String color(Color color) {
        return color == null ? "none" : Checks.argb(color.getRGB());
    }

    static String simple(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * Every {@link JComponent} of the tree of {@code root} (depth first), the layered pane children included.
     */
    static List<JComponent> components(Component root) {
        List<JComponent> list = new ArrayList<>();
        collect(root, list);
        return list;
    }

    private static void collect(Component c, List<JComponent> list) {
        if (c instanceof JComponent jc) {
            list.add(jc);
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, list);
            }
        }
    }
}
