package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The layout managers of {@code java.awt} : FlowLayout (leading, centered on the baseline, trailing), BorderLayout
 * (relative and absolute constraints), GridLayout, GridBagLayout (weights, anchors, fill, insets, padding, spans,
 * baseline anchors) and CardLayout, each laid out left-to-right and right-to-left ({@link ComponentOrientation}).
 * <p>
 * The boxes are lightweight components with fixed preferred sizes and baselines : the layouts are pure Java, so the
 * checks compare the exact bounds of every box (the same on every platform and runtime), and verify that each
 * right-to-left layout mirrors its left-to-right twin. The Swing layouts (BoxLayout, SpringLayout, GroupLayout,
 * OverlayLayout) are on the Swing pages.
 */
@Singleton
public class AwtLayoutsPage implements FeaturePage {

    private static final int TILE_WIDTH = 236;
    private static final int TILE_HEIGHT = 112;
    private static final int[] COLORS = { 0xFF8A65, 0x4FC3F7, 0xAED581, 0xFFD54F, 0xBA68C8, 0x4DB6AC, 0xF06292,
            0x90A4AE };

    @Override
    public String id() {
        return "awt-layouts";
    }

    @Override
    public String title() {
        return "AWT layouts";
    }

    @Override
    public String category() {
        return Categories.AWT;
    }

    @Override
    public int order() {
        return 30;
    }

    /**
     * A demo : a name, a factory of its container (laid out in both orientations), and whether its right-to-left layout
     * is the mirror of the left-to-right one (relative constraints) or not (absolute constraints).
     */
    private record Demo(String name, boolean mirrored, int tolerance, Function<Boolean, Container> factory) {

        Demo(String name, Function<Boolean, Container> factory) {
            this(name, true, 0, factory);
        }

        Demo(String name, boolean mirrored, Function<Boolean, Container> factory) {
            this(name, mirrored, 0, factory);
        }
    }

    @Override
    public Component build() {
        List<Demo> demos = demos();
        List<Check> checks = new ArrayList<>();
        List<Component> tiles = new ArrayList<>();
        for (Demo demo : demos) {
            Container ltr = demo.factory().apply(true);
            Container rtl = demo.factory().apply(false);
            Snapshots.layout(ltr);
            Snapshots.layout(rtl);
            tiles.add(tile(demo.name() + " · LTR", ltr));
            tiles.add(tile(demo.name() + " · RTL", rtl));
            checks.add(Check.info(demo.name() + " : LTR bounds", dump(ltr)));
            if (demo.mirrored() && demo.tolerance() == 0) {
                checks.add(Checks.expect(demo.name() + " : RTL mirrors LTR", true, () -> mirrors(ltr, rtl, 0)));
            } else if (demo.mirrored()) {
                // the extra space is split between the columns by integer division : the remainder goes to the last
                // column in both orientations, so a mirrored cell may move by a pixel or two
                checks.add(Checks.expect(demo.name() + " : RTL mirrors LTR (+-" + demo.tolerance() + " px)", true,
                        () -> mirrors(ltr, rtl, demo.tolerance())));
                checks.add(Checks.expect(demo.name() + " : RTL mirrors LTR exactly", false, () -> mirrors(ltr, rtl, 0)));
            } else {
                checks.add(Check.info(demo.name() + " : RTL bounds", dump(rtl)));
                checks.add(Checks.expect(demo.name() + " : RTL mirrors LTR", false, () -> mirrors(ltr, rtl, 0)));
            }
        }
        checks.addAll(apiChecks());

        List<Component> rows = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i += 4) {
            rows.add(Ui.row(14, tiles.subList(i, Math.min(tiles.size(), i + 4)).toArray(Component[]::new)));
        }
        List<Component> column = new ArrayList<>();
        column.add(Ui.text("Lightweight boxes with fixed preferred sizes and baselines, laid out by the AWT layout managers "
                + "in a " + TILE_WIDTH + " x " + TILE_HEIGHT + " container, left-to-right and right-to-left. The dashed "
                + "line of a box is its baseline.", 1000));
        column.addAll(rows);
        column.add(ChecksView.table("Layout checks", withExpectedBounds(checks)));
        return Ui.column(12, column.toArray(Component[]::new));
    }

    // ------------------------------------------------------------------------------------------------------ demos

    private static List<Demo> demos() {
        List<Demo> demos = new ArrayList<>();
        demos.add(new Demo("FlowLayout LEADING", ltr -> flow(new FlowLayout(FlowLayout.LEADING, 6, 6), ltr)));
        demos.add(new Demo("FlowLayout CENTER baseline", ltr -> {
            FlowLayout layout = new FlowLayout(FlowLayout.CENTER, 4, 6);
            layout.setAlignOnBaseline(true);
            return flow(layout, ltr);
        }));
        demos.add(new Demo("FlowLayout TRAILING", ltr -> flow(new FlowLayout(FlowLayout.TRAILING, 6, 6), ltr)));
        demos.add(new Demo("FlowLayout LEFT (absolute)", false, ltr -> flowAbsolute(ltr)));
        demos.add(new Demo("BorderLayout relative", ltr -> {
            Container c = container(new BorderLayout(4, 4), ltr);
            c.add(box("PAGE_START", 60, 20, 0), BorderLayout.PAGE_START);
            c.add(box("PAGE_END", 60, 20, 1), BorderLayout.PAGE_END);
            c.add(box("LINE_START", 72, 20, 2), BorderLayout.LINE_START);
            c.add(box("LINE_END", 60, 20, 3), BorderLayout.LINE_END);
            c.add(box("CENTER", 30, 20, 4), BorderLayout.CENTER);
            return oriented(c, ltr);
        }));
        demos.add(new Demo("BorderLayout absolute", false, ltr -> {
            Container c = container(new BorderLayout(2, 2), ltr);
            c.add(box("NORTH", 60, 24, 0), BorderLayout.NORTH);
            c.add(box("SOUTH", 60, 16, 1), BorderLayout.SOUTH);
            c.add(box("WEST", 50, 20, 2), BorderLayout.WEST);
            c.add(box("EAST", 44, 20, 3), BorderLayout.EAST);
            c.add(box("CENTER", 30, 20, 4), BorderLayout.CENTER);
            return oriented(c, ltr);
        }));
        demos.add(new Demo("GridLayout 2x3", ltr -> {
            Container c = container(new GridLayout(2, 3, 4, 4), ltr);
            for (int i = 0; i < 5; i++) {
                c.add(box(String.valueOf((char) ('A' + i)), 20, 20, i));
            }
            return oriented(c, ltr);
        }));
        demos.add(new Demo("GridLayout 0x4 (rows computed)", ltr -> {
            Container c = container(new GridLayout(0, 4, 2, 2), ltr);
            for (int i = 0; i < 7; i++) {
                c.add(box(String.valueOf(i + 1), 20, 20, i));
            }
            return oriented(c, ltr);
        }));
        demos.add(new Demo("GridBagLayout form", ltr -> gridBagForm(ltr)));
        demos.add(new Demo("GridBagLayout anchors", true, 2, ltr -> gridBagAnchors(ltr)));
        demos.add(new Demo("GridBagLayout baseline", ltr -> gridBagBaseline(ltr)));
        demos.add(new Demo("GridBagLayout weights, spans", ltr -> gridBagWeights(ltr)));
        demos.add(new Demo("CardLayout show(Two)", ltr -> {
            Container c = container(new CardLayout(4, 4), ltr);
            c.add(box("One", 20, 20, 0), "One");
            c.add(box("Two", 20, 20, 1), "Two");
            c.add(box("Three", 20, 20, 2), "Three");
            ((CardLayout) c.getLayout()).show(c, "Two");
            return oriented(c, ltr);
        }));
        return demos;
    }

    private static Container flow(FlowLayout layout, boolean ltr) {
        Container c = container(layout, ltr);
        c.add(box("A", 60, 24, 0, 16));
        c.add(box("B", 40, 36, 1, 30));
        c.add(box("C", 70, 20, 2, 12));
        c.add(box("D", 50, 30, 3, 20));
        c.add(box("E", 80, 24, 4, 18));
        return oriented(c, ltr);
    }

    private static Container flowAbsolute(boolean ltr) {
        // LEFT is absolute : right-to-left orientation reverses the order in each row, not the alignment
        Container c = container(new FlowLayout(FlowLayout.LEFT, 6, 6), ltr);
        c.add(box("1", 50, 24, 0));
        c.add(box("2", 50, 24, 1));
        c.add(box("3", 50, 24, 2));
        return oriented(c, ltr);
    }

    private static Container gridBagForm(boolean ltr) {
        Container c = container(new GridBagLayout(), ltr);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        for (int row = 0; row < 3; row++) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.LINE_END;
            c.add(box("L" + (row + 1), 30 + row * 10, 20, row), gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.LINE_START;
            c.add(box("field " + (row + 1), 40, 20, 4 + row), gbc);
        }
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LAST_LINE_END;
        gbc.ipadx = 10;
        c.add(box("OK", 30, 16, 7), gbc);
        return oriented(c, ltr);
    }

    private static Container gridBagAnchors(boolean ltr) {
        Container c = container(new GridBagLayout(), ltr);
        int[][] anchors = {
                { GridBagConstraints.FIRST_LINE_START, GridBagConstraints.PAGE_START, GridBagConstraints.FIRST_LINE_END },
                { GridBagConstraints.LINE_START, GridBagConstraints.CENTER, GridBagConstraints.LINE_END },
                { GridBagConstraints.LAST_LINE_START, GridBagConstraints.PAGE_END, GridBagConstraints.LAST_LINE_END } };
        String[][] names = { { "FLS", "PS", "FLE" }, { "LS", "C", "LE" }, { "LLS", "PE", "LLE" } };
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1;
        gbc.weighty = 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                gbc.gridx = col;
                gbc.gridy = row;
                gbc.anchor = anchors[row][col];
                c.add(box(names[row][col], 30, 18, row * 3 + col), gbc);
            }
        }
        return oriented(c, ltr);
    }

    private static Container gridBagBaseline(boolean ltr) {
        Container c = container(new GridBagLayout(), ltr);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weighty = 1;
        gbc.insets = new Insets(0, 2, 0, 2);
        int[] anchors = { GridBagConstraints.BASELINE, GridBagConstraints.BASELINE_LEADING,
                GridBagConstraints.BASELINE_TRAILING, GridBagConstraints.ABOVE_BASELINE, GridBagConstraints.BELOW_BASELINE };
        String[] names = { "BL", "BLL", "BLT", "ABL", "BBL" };
        int[] heights = { 30, 24, 40, 20, 26 };
        int[] baselines = { 22, 10, 30, 14, 8 };
        for (int i = 0; i < anchors.length; i++) {
            gbc.gridx = i;
            gbc.anchor = anchors[i];
            gbc.weightx = i == 1 || i == 2 ? 1 : 0;
            c.add(box(names[i], 36, heights[i], i, baselines[i]), gbc);
        }
        return oriented(c, ltr);
    }

    private static Container gridBagWeights(boolean ltr) {
        Container c = container(new GridBagLayout(), ltr);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0.25;
        gbc.weighty = 1;
        c.add(box("2 rows", 30, 20, 0), gbc);
        gbc.gridx = 1;
        gbc.gridheight = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0.75;
        c.add(box("2 columns", 30, 20, 1), gbc);
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        gbc.ipady = 8;
        c.add(box("w .5", 30, 20, 2), gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.25;
        gbc.ipady = 0;
        c.add(box("w .25", 30, 20, 3), gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weighty = 0;
        c.add(box("REMAINDER", 30, 14, 4), gbc);
        return oriented(c, ltr);
    }

    // ------------------------------------------------------------------------------------------------ containers

    private static Container container(LayoutManager layout, boolean ltr) {
        DemoContainer c = new DemoContainer();
        c.setLayout(layout);
        c.setSize(TILE_WIDTH, TILE_HEIGHT);
        return c;
    }

    private static Container oriented(Container c, boolean ltr) {
        c.applyComponentOrientation(ltr ? ComponentOrientation.LEFT_TO_RIGHT : ComponentOrientation.RIGHT_TO_LEFT);
        return c;
    }

    private static Component tile(String caption, Container demo) {
        return Ui.column(4, Ui.caption(caption), demo);
    }

    /**
     * The container of a demo : a fixed size, a light background and a 1 pixel border.
     */
    static final class DemoContainer extends Container {

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(TILE_WIDTH, TILE_HEIGHT);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Insets getInsets() {
            return new Insets(2, 2, 2, 2);
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(new Color(0xF5F7FA));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(0xB0BEC5));
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            super.paint(g);
        }
    }

    private static LayoutBox box(String name, int width, int height, int color) {
        return new LayoutBox(name, width, height, -1, COLORS[color % COLORS.length]);
    }

    private static LayoutBox box(String name, int width, int height, int color, int baseline) {
        return new LayoutBox(name, width, height, baseline, COLORS[color % COLORS.length]);
    }

    /**
     * A lightweight box : fixed preferred size, optional fixed baseline (drawn as a dashed line), its name centered.
     */
    static final class LayoutBox extends Component {

        private final int width;
        private final int height;
        private final int baseline;
        private final int rgb;

        LayoutBox(String name, int width, int height, int baseline, int rgb) {
            this.width = width;
            this.height = height;
            this.baseline = baseline;
            this.rgb = rgb;
            setName(name);
            setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
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
        public int getBaseline(int w, int h) {
            return baseline;
        }

        @Override
        public BaselineResizeBehavior getBaselineResizeBehavior() {
            return baseline < 0 ? BaselineResizeBehavior.OTHER : BaselineResizeBehavior.CONSTANT_ASCENT;
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                AwtSupport.textHints(g);
                int w = getWidth();
                int h = getHeight();
                g.setColor(new Color(rgb));
                g.fillRect(0, 0, w, h);
                g.setColor(new Color(rgb).darker());
                g.drawRect(0, 0, w - 1, h - 1);
                if (baseline >= 0 && baseline < h) {
                    g.setColor(new Color(0x37474F));
                    for (int x = 1; x < w - 1; x += 4) {
                        g.drawLine(x, baseline, Math.min(x + 1, w - 2), baseline);
                    }
                }
                g.setColor(new Color(0x212121));
                g.setFont(getFont());
                FontMetrics fm = g.getFontMetrics();
                String name = getName();
                g.drawString(name, (w - fm.stringWidth(name)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g.dispose();
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------- checks

    /**
     * {@code name:x,y wxh} of every child of {@code c}.
     */
    private static String dump(Container c) {
        List<String> parts = new ArrayList<>();
        for (Component child : c.getComponents()) {
            parts.add(child.getName() + ":" + AwtSupport.bounds(child.getBounds()));
        }
        return String.join(" ", parts);
    }

    /**
     * {@code true} when every child of {@code rtl} is the horizontal mirror of the same child of {@code ltr}.
     */
    private static boolean mirrors(Container ltr, Container rtl, int tolerance) {
        int width = ltr.getWidth();
        Component[] a = ltr.getComponents();
        Component[] b = rtl.getComponents();
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(b[i].getX() - (width - a[i].getX() - a[i].getWidth())) > tolerance || b[i].getY() != a[i].getY()
                    || b[i].getWidth() != a[i].getWidth() || b[i].getHeight() != a[i].getHeight()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The LTR bounds are the same everywhere (pure Java layout of fixed size boxes) : they become expectations.
     */
    private static List<Check> withExpectedBounds(List<Check> checks) {
        List<Check> result = new ArrayList<>();
        for (Check check : checks) {
            String expected = EXPECTED_BOUNDS.get(check.name());
            if (expected != null) {
                result.add(Check.of(check.name(), expected.equals(check.value()),
                        expected.equals(check.value()) ? check.value() : "expected " + expected + " but got " + check.value()));
            } else {
                result.add(check);
            }
        }
        return result;
    }

    /**
     * The left-to-right bounds (and the right-to-left bounds of the absolute layouts) : pure Java computations of fixed
     * size boxes, the same on every platform and runtime.
     */
    private static final Map<String, String> EXPECTED_BOUNDS = Map.ofEntries(
            Map.entry("FlowLayout LEADING : LTR bounds",
                    "A:8,14 60x24 B:74,8 40x36 C:120,16 70x20 D:8,50 50x30 E:64,53 80x24"),
            Map.entry("FlowLayout CENTER baseline : LTR bounds",
                    "A:29,22 60x24 B:93,8 40x36 C:137,26 70x20 D:51,52 50x30 E:105,54 80x24"),
            Map.entry("FlowLayout TRAILING : LTR bounds",
                    "A:46,14 60x24 B:112,8 40x36 C:158,16 70x20 D:92,50 50x30 E:148,53 80x24"),
            Map.entry("FlowLayout LEFT (absolute) : LTR bounds",
                    "1:8,8 50x24 2:64,8 50x24 3:120,8 50x24"),
            Map.entry("FlowLayout LEFT (absolute) : RTL bounds",
                    "1:120,8 50x24 2:64,8 50x24 3:8,8 50x24"),
            Map.entry("BorderLayout relative : LTR bounds",
                    "PAGE_START:2,2 232x20 PAGE_END:2,90 232x20 LINE_START:2,26 72x60 LINE_END:174,26 60x60 CENTER:78,26 92x60"),
            Map.entry("BorderLayout absolute : LTR bounds",
                    "NORTH:2,2 232x24 SOUTH:2,94 232x16 WEST:2,28 50x64 EAST:190,28 44x64 CENTER:54,28 134x64"),
            Map.entry("BorderLayout absolute : RTL bounds",
                    "NORTH:2,2 232x24 SOUTH:2,94 232x16 WEST:2,28 50x64 EAST:190,28 44x64 CENTER:54,28 134x64"),
            Map.entry("GridLayout 2x3 : LTR bounds",
                    "A:3,2 74x52 B:81,2 74x52 C:159,2 74x52 D:3,58 74x52 E:81,58 74x52"),
            Map.entry("GridLayout 0x4 (rows computed) : LTR bounds",
                    "1:3,2 56x53 2:61,2 56x53 3:119,2 56x53 4:177,2 56x53 5:3,57 56x53 6:61,57 56x53 7:119,57 56x53"),
            Map.entry("GridBagLayout form : LTR bounds",
                    "L1:26,5 30x20 field 1:64,5 166x20 L2:16,31 40x20 field 2:64,31 166x20 L3:6,57 50x20 field 3:64,57 166x20 OK:190,91 40x16"),
            Map.entry("GridBagLayout anchors : LTR bounds",
                    "FLS:2,2 30x18 PS:102,2 30x18 FLE:203,2 30x18 LS:2,47 30x18 C:102,47 30x18 LE:203,47 30x18 LLS:2,92 30x18 PE:102,92 30x18 LLE:203,92 30x18"),
            Map.entry("GridBagLayout baseline : LTR bounds",
                    "BL:4,10 36x30 BLL:44,22 36x24 BLT:116,2 36x40 ABL:156,12 36x20 BBL:196,32 36x26"),
            Map.entry("GridBagLayout weights, spans : LTR bounds",
                    "2 rows:4,4 62x86 2 columns:70,4 161x37 w .5:70,45 95x45 w .25:169,45 62x45 REMAINDER:4,94 227x14"),
            Map.entry("CardLayout show(Two) : LTR bounds",
                    "One:6,6 224x100 Two:6,6 224x100 Three:6,6 224x100"));

    private static List<Check> apiChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("FlowLayout preferredLayoutSize (LEADING, 5 boxes, one row)", "340x52", () -> {
            Container c = flow(new FlowLayout(FlowLayout.LEADING, 6, 6), true);
            return AwtSupport.size(c.getLayout().preferredLayoutSize(c));
        }));
        checks.add(Checks.expect("FlowLayout alignOnBaseline / getAlignment", "true / 1", () -> {
            FlowLayout layout = new FlowLayout(FlowLayout.CENTER);
            layout.setAlignOnBaseline(true);
            return layout.getAlignOnBaseline() + " / " + layout.getAlignment();
        }));
        checks.add(Checks.expect("BorderLayout.getLayoutComponent(LINE_START) / (target LTR, WEST)",
                "LINE_START / LINE_START", () -> {
                    Container c = demos().get(4).factory().apply(true);
                    BorderLayout layout = (BorderLayout) c.getLayout();
                    return layout.getLayoutComponent(BorderLayout.LINE_START).getName() + " / "
                            + layout.getLayoutComponent(c, BorderLayout.WEST).getName();
                }));
        checks.add(Checks.expect("BorderLayout.getLayoutComponent(LINE_START) / (target RTL, WEST)",
                "LINE_START / LINE_END", () -> {
                    Container c = demos().get(4).factory().apply(false);
                    BorderLayout layout = (BorderLayout) c.getLayout();
                    return layout.getLayoutComponent(BorderLayout.LINE_START).getName() + " / "
                            + layout.getLayoutComponent(c, BorderLayout.WEST).getName();
                }));
        checks.add(Checks.expect("BorderLayout.getConstraints (PAGE_START, PAGE_END)", "First Last", () -> {
            Container c = demos().get(4).factory().apply(true);
            BorderLayout layout = (BorderLayout) c.getLayout();
            return layout.getConstraints(c.getComponent(0)) + " " + layout.getConstraints(c.getComponent(1));
        }));
        checks.add(Checks.expect("GridLayout(0, 4) with 7 boxes : rows x columns", "2x4", () -> {
            Container c = demos().get(7).factory().apply(true);
            GridLayout layout = (GridLayout) c.getLayout();
            int rows = (c.getComponentCount() + layout.getColumns() - 1) / layout.getColumns();
            return rows + "x" + layout.getColumns();
        }));
        checks.add(Checks.info("GridBagLayout form : column widths / row heights", () -> {
            Container c = gridBagForm(true);
            Snapshots.layout(c);
            int[][] dims = ((GridBagLayout) c.getLayout()).getLayoutDimensions();
            return Arrays.toString(dims[0]) + " / " + Arrays.toString(dims[1]);
        }));
        checks.add(Checks.expect("GridBagLayout form : weights", "[0.0, 1.0] / [0.0, 0.0, 0.0, 1.0]", () -> {
            Container c = gridBagForm(true);
            Snapshots.layout(c);
            double[][] weights = ((GridBagLayout) c.getLayout()).getLayoutWeights();
            return Arrays.toString(weights[0]) + " / " + Arrays.toString(weights[1]);
        }));
        checks.add(Checks.expect("GridBagLayout form : location(x, y) of a point of the OK box / origin", "1,3 / 2,2", () -> {
            Container c = gridBagForm(true);
            Snapshots.layout(c);
            GridBagLayout layout = (GridBagLayout) c.getLayout();
            Component ok = c.getComponent(c.getComponentCount() - 1);
            Point cell = layout.location(ok.getX() + 1, ok.getY() + 1);
            Point origin = layout.getLayoutOrigin();
            return cell.x + "," + cell.y + " / " + origin.x + "," + origin.y;
        }));
        checks.add(Checks.expect("GridBagLayout.getConstraints of the OK box", "gridx=0 gridy=3 gridwidth=0 anchor=LAST_LINE_END",
                () -> {
                    Container c = gridBagForm(true);
                    GridBagConstraints gbc = ((GridBagLayout) c.getLayout()).getConstraints(c.getComponent(6));
                    return "gridx=" + gbc.gridx + " gridy=" + gbc.gridy + " gridwidth=" + gbc.gridwidth + " anchor="
                            + (gbc.anchor == GridBagConstraints.LAST_LINE_END ? "LAST_LINE_END" : gbc.anchor);
                }));
        checks.addAll(cardChecks());
        checks.add(Checks.expect("ComponentOrientation.getOrientation(ar, he, fa, ur, en, fr)",
                "RTL RTL RTL RTL LTR LTR", () -> String.join(" ", List.of("ar", "he", "fa", "ur", "en", "fr").stream()
                        .map(language -> ComponentOrientation.getOrientation(Locale.of(language)).isLeftToRight() ? "LTR"
                                : "RTL")
                        .toList())));
        checks.add(Checks.expect("applyComponentOrientation reaches the children", "false false", () -> {
            Container c = flow(new FlowLayout(), false);
            return c.getComponentOrientation().isLeftToRight() + " "
                    + c.getComponent(4).getComponentOrientation().isLeftToRight();
        }));
        return checks;
    }

    private static List<Check> cardChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("CardLayout first, next, next, next (wraps), previous, last, show(Two)",
                "One Two Three One Three Three Two", () -> {
                    Container c = new DemoContainer();
                    CardLayout cards = new CardLayout(4, 4);
                    c.setLayout(cards);
                    c.add(box("One", 20, 20, 0), "One");
                    c.add(box("Two", 20, 20, 1), "Two");
                    c.add(box("Three", 20, 20, 2), "Three");
                    List<String> visible = new ArrayList<>();
                    cards.first(c);
                    visible.add(visibleCard(c));
                    cards.next(c);
                    visible.add(visibleCard(c));
                    cards.next(c);
                    visible.add(visibleCard(c));
                    cards.next(c);
                    visible.add(visibleCard(c));
                    cards.previous(c);
                    visible.add(visibleCard(c));
                    cards.last(c);
                    visible.add(visibleCard(c));
                    cards.show(c, "Two");
                    visible.add(visibleCard(c));
                    return String.join(" ", visible);
                }));
        checks.add(Checks.expect("CardLayout bounds of the visible card (hgap 4, vgap 4)", "Two:6,6 224x100", () -> {
            Container c = new DemoContainer();
            c.setSize(TILE_WIDTH, TILE_HEIGHT);
            CardLayout cards = new CardLayout(4, 4);
            c.setLayout(cards);
            c.add(box("One", 20, 20, 0), "One");
            c.add(box("Two", 20, 20, 1), "Two");
            cards.show(c, "Two");
            c.doLayout();
            Component two = c.getComponent(1);
            return two.getName() + ":" + AwtSupport.bounds(two.getBounds());
        }));
        return checks;
    }

    private static String visibleCard(Container c) {
        for (Component child : c.getComponents()) {
            if (child.isVisible()) {
                return child.getName();
            }
        }
        return "none";
    }
}
