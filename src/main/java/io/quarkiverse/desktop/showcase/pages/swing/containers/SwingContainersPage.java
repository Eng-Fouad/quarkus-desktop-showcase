package io.quarkiverse.desktop.showcase.pages.swing.containers;

import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.block;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.captioned;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.column;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.icon;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.rect;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.row;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.section;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.size;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.sized;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.ImageIcon;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.metal.MetalToolBarUI;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Swing containers : {@link JScrollPane} (row and column headers, the four corners, viewport border and scroll modes,
 * scroll bar policies, right-to-left), {@link JSplitPane} (one-touch buttons, nested, proportional location),
 * {@link JTabbedPane} (four placements, wrapped runs, scroll tab layout, tab components, mnemonics, HTML titles),
 * {@link JLayeredPane} (layers and positions), a stand-alone {@link JRootPane} with a menu bar and a visible glass pane,
 * {@link JToolBar} (floatable, rollover, separators, vertical, floated into its own window and docked back) and
 * {@link Box} (struts, glue, rigid areas, fillers).
 * <p>
 * The UI delegates reach their key bindings through {@code LazyActionMap} (reflective {@code loadActionMap} of
 * {@code BasicScrollPaneUI}, {@code BasicSplitPaneUI}, {@code BasicTabbedPaneUI}, {@code BasicToolBarUI},
 * {@code BasicRootPaneUI}) and {@code KeyStroke.getKeyStroke(String)} (reflection on the {@code KeyEvent} fields) : the
 * checks perform some of those actions. Capture method A (printAll), plus the floating tool bar window as an extra.
 */
@Singleton
public class SwingContainersPage implements FeaturePage {

    private static final int BLUE = 0x42A5F5;
    private static final int ORANGE = 0xFFA726;
    private static final int GREEN = 0x66BB6A;
    private static final int RED = 0xEF5350;
    private static final int PURPLE = 0xAB47BC;
    private static final int TEAL = 0x26A69A;
    private static final int GREY = 0xB0BEC5;
    private static final int YELLOW = 0xFFEE58;
    private static final int INDIGO = 0x5C6BC0;
    private static final int PINK = 0xEC407A;

    /** Screen location of the floating tool bar window (outside the main window). */
    private static final int FLOAT_X = 1460;
    private static final int FLOAT_Y = 560;

    // per build state (one content at a time)
    private State state;

    private static final class State {
        final Map<String, SwingKit.CheckColumns> tables = new LinkedHashMap<>();
        JScrollPane gridScroll;
        JScrollPane rtlScroll;
        JScrollPane plainScroll;
        JSplitPane mainSplit;
        JSplitPane nestedSplit;
        JSplitPane collapsedSplit;
        JSplitPane proportionalSplit;
        final List<JTabbedPane> placements = new ArrayList<>();
        ImageIcon badge;
        JTabbedPane wrapTabs;
        JTabbedPane scrollTabs;
        JTabbedPane componentTabs;
        JTabbedPane mnemonicTabs;
        JLayeredPane layered;
        final List<JComponent> layeredBlocks = new ArrayList<>();
        JRootPane rootPane;
        JToolBar toolBar;
        JToolBar verticalToolBar;
        JToolBar plainToolBar;
        JToolBar floatingToolBar;
        JPanel floatingDock;
        Window floatingWindow;
        BufferedImage floatingImage;
        Box hbox;
        Box vbox;
        final List<Component> fillers = new ArrayList<>();
    }

    @Override
    public String id() {
        return "swing-containers";
    }

    @Override
    public String title() {
        return "Containers";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public Component build() {
        State s = new State();
        state = s;
        JPanel content = column(18,
                Ui.heading("Swing containers"),
                Ui.text("Scroll panes, split panes, tabbed panes, layered pane, root pane with glass pane, tool bars and "
                        + "boxes of the current look and feel (Metal, Ocean theme). The checks read the layout results, "
                        + "the key bindings and the actions the UI delegates load by reflection.", SwingKit.WIDTH),
                scrollPanes(s), table(s, "Scroll panes"),
                splitPanes(s), table(s, "Split panes"),
                tabbedPanes(s), table(s, "Tabbed panes"),
                layeredAndRoot(s), table(s, "Layered pane and root pane"),
                toolBars(s), table(s, "Tool bars and boxes"));
        content.setBorder(BorderFactory.createEmptyBorder());
        return content;
    }

    private static SwingKit.CheckColumns table(State s, String title) {
        SwingKit.CheckColumns view = SwingKit.pending(title);
        s.tables.put(title, view);
        return view;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        return Edt.rounds(2)
                .thenApply(v -> {
                    // layout dependent operations : the content is showing and laid out
                    s.proportionalSplit.setDividerLocation(0.25);
                    collapse(s.collapsedSplit);
                    return null;
                })
                .thenCompose(v -> Edt.rounds(2))
                .thenAccept(v -> {
                    s.tables.get("Scroll panes").setChecks(scrollChecks(s));
                    s.tables.get("Split panes").setChecks(splitChecks(s));
                    s.tables.get("Tabbed panes").setChecks(tabChecks(s));
                    s.tables.get("Layered pane and root pane").setChecks(layeredChecks(s));
                    s.tables.get("Tool bars and boxes").setChecks(toolBarChecks(s));
                })
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        State s = state;
        Map<String, BufferedImage> extras = new LinkedHashMap<>();
        if (s.floatingImage != null) {
            extras.put("floating-toolbar", s.floatingImage);
        }
        return CompletableFuture.completedFuture(extras);
    }

    @Override
    public void dispose(Component content) {
        State s = state;
        state = null;
        if (s != null && s.floatingWindow != null) {
            s.floatingWindow.dispose();
        }
    }

    // ------------------------------------------------------------------------------------------------ scroll panes

    private static Component scrollPanes(State s) {
        GridView grid = new GridView(900, 640, BLUE, "grid");
        JScrollPane scroll = new JScrollPane(grid, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setColumnHeaderView(new Ruler(true, 900));
        scroll.setRowHeaderView(new Ruler(false, 640));
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, block("px", GREY, 30, 20));
        scroll.setCorner(JScrollPane.UPPER_RIGHT_CORNER, block("", ORANGE, 16, 20));
        scroll.setCorner(JScrollPane.LOWER_LEFT_CORNER, block("", GREEN, 30, 16));
        scroll.setCorner(JScrollPane.LOWER_RIGHT_CORNER, block("", RED, 16, 16));
        scroll.setViewportBorder(BorderFactory.createLineBorder(new Color(INDIGO), 2));
        scroll.getViewport().setViewPosition(new Point(160, 120));
        s.gridScroll = sized(scroll, 340, 230);

        GridView rtlGrid = new GridView(600, 400, PURPLE, "rtl");
        JScrollPane rtl = new JScrollPane(rtlGrid);
        rtl.setCorner(JScrollPane.LOWER_LEADING_CORNER, block("", TEAL, 16, 16));
        rtl.setCorner(JScrollPane.UPPER_TRAILING_CORNER, block("", YELLOW, 16, 16));
        rtl.setColumnHeaderView(new Ruler(true, 600));
        rtl.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        rtl.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        rtl.getViewport().setViewPosition(new Point(300, 40));
        s.rtlScroll = sized(rtl, 300, 230);

        JPanel small = new JPanel(new GridLayout(3, 2, 6, 6));
        small.setBackground(new Color(0xFAFAFA));
        for (int i = 1; i <= 6; i++) {
            small.add(block("Cell " + i, i % 2 == 0 ? GREY : 0xE3F2FD, 90, 40));
        }
        JScrollPane plain = new JScrollPane(small, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        plain.setWheelScrollingEnabled(false);
        plain.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        s.plainScroll = sized(plain, 240, 230);

        return section("JScrollPane",
                "Row and column header rulers, the four corners and a viewport border (left) ; right-to-left "
                        + "orientation puts the vertical scroll bar on the left, with LOWER_LEADING and UPPER_TRAILING "
                        + "corners (middle) ; AS_NEEDED/NEVER policies with a view that fits (right).",
                row(24, captioned("headers, corners, ALWAYS policies, view at 160,120", s.gridScroll),
                        captioned("RIGHT_TO_LEFT, BACKINGSTORE scroll mode", s.rtlScroll),
                        captioned("AS_NEEDED / NEVER, SIMPLE scroll mode", s.plainScroll)));
    }

    private static List<Check> scrollChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JScrollPane sp = s.gridScroll;
        JViewport vp = sp.getViewport();
        checks.add(Checks.expect("grid: view position", "160,120", () -> SwingKit.point(vp.getViewPosition())));
        checks.add(Checks.expect("grid: view size", "900x640", () -> size(vp.getViewSize())));
        checks.add(Checks.info("grid: extent size", () -> size(vp.getExtentSize())));
        checks.add(Checks.info("grid: viewport bounds", () -> rect(sp.getViewportBorderBounds())));
        checks.add(Checks.info("grid: column header bounds", () -> rect(sp.getColumnHeader().getBounds())));
        checks.add(Checks.info("grid: row header bounds", () -> rect(sp.getRowHeader().getBounds())));
        checks.add(Checks.expect("grid: corners present (UL, UR, LL, LR)", "true true true true",
                () -> (sp.getCorner(JScrollPane.UPPER_LEFT_CORNER) != null) + " "
                        + (sp.getCorner(JScrollPane.UPPER_RIGHT_CORNER) != null) + " "
                        + (sp.getCorner(JScrollPane.LOWER_LEFT_CORNER) != null) + " "
                        + (sp.getCorner(JScrollPane.LOWER_RIGHT_CORNER) != null)));
        checks.add(Checks.info("grid: corner bounds UL / LR", () -> rect(sp.getCorner(JScrollPane.UPPER_LEFT_CORNER)
                .getBounds()) + " / " + rect(sp.getCorner(JScrollPane.LOWER_RIGHT_CORNER).getBounds())));
        checks.add(Checks.expect("grid: scroll bars visible (V, H)", "true true",
                () -> sp.getVerticalScrollBar().isVisible() + " " + sp.getHorizontalScrollBar().isVisible()));
        checks.add(Checks.expect("grid: vertical unit / block increment (Scrollable)", "20 / 100",
                () -> sp.getVerticalScrollBar().getUnitIncrement(1) + " / "
                        + sp.getVerticalScrollBar().getBlockIncrement(1)));
        checks.add(Checks.expect("grid: horizontal scroll bar value", 160, () -> sp.getHorizontalScrollBar().getValue()));
        checks.add(Checks.expect("grid: action unitScrollDown then unitScrollUp", "160,140 -> 160,120", () -> {
            SwingKit.perform(sp, "unitScrollDown", null);
            String down = SwingKit.point(vp.getViewPosition());
            SwingKit.perform(sp, "unitScrollUp", null);
            return down + " -> " + SwingKit.point(vp.getViewPosition());
        }));
        checks.add(Checks.expect("grid: binding ctrl END (ancestor of focused)", "scrollEnd",
                () -> sp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("ctrl END"))));
        checks.add(Checks.expect("grid: binding PAGE_DOWN (ancestor of focused)", "scrollDown",
                () -> sp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("PAGE_DOWN"))));
        checks.add(Checks.expect("grid: UI delegates", "javax.swing.plaf.metal.MetalScrollPaneUI "
                + "javax.swing.plaf.basic.BasicViewportUI javax.swing.plaf.metal.MetalScrollBarUI",
                () -> ui(sp) + " " + ui(vp) + " " + ui(sp.getVerticalScrollBar())));
        checks.add(Checks.expect("grid: layout", "javax.swing.ScrollPaneLayout$UIResource",
                () -> sp.getLayout().getClass().getName()));
        checks.add(Checks.expect("grid: viewport scroll mode", "BLIT", () -> scrollMode(vp)));

        JScrollPane rtl = s.rtlScroll;
        checks.add(Checks.expect("rtl: vertical scroll bar left of the viewport", true,
                () -> rtl.getVerticalScrollBar().getX() < rtl.getViewport().getX()));
        checks.add(Checks.expect("rtl: LOWER_LEADING is LOWER_RIGHT", true,
                () -> rtl.getCorner(JScrollPane.LOWER_LEADING_CORNER) == rtl.getCorner(JScrollPane.LOWER_RIGHT_CORNER)));
        checks.add(Checks.expect("rtl: UPPER_TRAILING is UPPER_LEFT", true,
                () -> rtl.getCorner(JScrollPane.UPPER_TRAILING_CORNER) == rtl.getCorner(JScrollPane.UPPER_LEFT_CORNER)));
        checks.add(Checks.info("rtl: view position", () -> SwingKit.point(rtl.getViewport().getViewPosition())));
        checks.add(Checks.info("rtl: horizontal scroll bar value", () -> rtl.getHorizontalScrollBar().getValue()));
        checks.add(Checks.expect("rtl: viewport scroll mode", "BACKINGSTORE", () -> scrollMode(rtl.getViewport())));

        JScrollPane plain = s.plainScroll;
        checks.add(Checks.expect("fits: scroll bars visible (V, H)", "false false",
                () -> plain.getVerticalScrollBar().isVisible() + " " + plain.getHorizontalScrollBar().isVisible()));
        checks.add(Checks.expect("fits: wheel scrolling enabled", false, plain::isWheelScrollingEnabled));
        checks.add(Checks.expect("fits: viewport scroll mode", "SIMPLE", () -> scrollMode(plain.getViewport())));
        checks.add(Checks.info("fits: view size", () -> size(plain.getViewport().getViewSize())));
        return checks;
    }

    private static String scrollMode(JViewport viewport) {
        return switch (viewport.getScrollMode()) {
            case JViewport.BLIT_SCROLL_MODE -> "BLIT";
            case JViewport.BACKINGSTORE_SCROLL_MODE -> "BACKINGSTORE";
            case JViewport.SIMPLE_SCROLL_MODE -> "SIMPLE";
            default -> String.valueOf(viewport.getScrollMode());
        };
    }

    // ------------------------------------------------------------------------------------------------- split panes

    private static Component splitPanes(State s) {
        JSplitPane nested = new JSplitPane(JSplitPane.VERTICAL_SPLIT, minimal(block("top", ORANGE, 100, 60)),
                minimal(block("bottom", GREEN, 100, 60)));
        nested.setDividerLocation(90);
        nested.setDividerSize(8);
        s.nestedSplit = nested;
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, minimal(block("left", BLUE, 100, 60)),
                nested);
        main.setOneTouchExpandable(true);
        main.setDividerSize(12);
        main.setDividerLocation(140);
        main.setResizeWeight(0.3);
        s.mainSplit = sized(main, 330, 210);

        JSplitPane collapsed = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, minimal(block("collapsed", PINK, 100, 60)),
                minimal(block("right", TEAL, 100, 60)));
        collapsed.setOneTouchExpandable(true);
        collapsed.setDividerLocation(120);
        s.collapsedSplit = sized(collapsed, 300, 210);

        JSplitPane proportional = new JSplitPane(JSplitPane.VERTICAL_SPLIT, minimal(block("25 %", PURPLE, 100, 30)),
                minimal(block("75 %", YELLOW, 100, 30)));
        proportional.setResizeWeight(0.5);
        proportional.setContinuousLayout(true);
        s.proportionalSplit = sized(proportional, 300, 210);

        return section("JSplitPane",
                "Continuous horizontal split with one-touch buttons, containing a vertical split (left) ; the left "
                        + "one-touch button clicked with doClick (middle) ; proportional location 0.25 (right).",
                row(24, captioned("one-touch, continuous, nested", s.mainSplit),
                        captioned("one-touch left button clicked", s.collapsedSplit),
                        captioned("setDividerLocation(0.25), resize weight 0.5", s.proportionalSplit)));
    }

    private static <C extends JComponent> C minimal(C component) {
        component.setMinimumSize(new Dimension(20, 20));
        return component;
    }

    private static void collapse(JSplitPane split) {
        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
        List<JButton> buttons = SwingKit.findAll(divider, JButton.class);
        // the first one-touch button moves the divider to the minimum (left) side
        buttons.getFirst().doClick(0);
    }

    private static List<Check> splitChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JSplitPane main = s.mainSplit;
        checks.add(Checks.expect("main: divider location", 140, main::getDividerLocation));
        checks.add(Checks.expect("main: nested divider location", 90, s.nestedSplit::getDividerLocation));
        checks.add(Checks.expect("main: one-touch, continuous, resize weight", "true true 0.3",
                () -> main.isOneTouchExpandable() + " " + main.isContinuousLayout() + " " + main.getResizeWeight()));
        checks.add(Checks.expect("main: divider size", 12, main::getDividerSize));
        checks.add(Checks.expect("main: minimum / maximum divider location", "21 / 295",
                () -> main.getMinimumDividerLocation() + " / " + main.getMaximumDividerLocation()));
        checks.add(Checks.expect("main: divider class", "javax.swing.plaf.metal.MetalSplitPaneDivider",
                () -> ((BasicSplitPaneUI) main.getUI()).getDivider().getClass().getName()));
        checks.add(Checks.expect("main: one-touch buttons", 2,
                () -> SwingKit.findAll(((BasicSplitPaneUI) main.getUI()).getDivider(), JButton.class).size()));
        checks.add(Checks.expect("main: UI delegate", "javax.swing.plaf.metal.MetalSplitPaneUI", () -> ui(main)));
        checks.add(Checks.expect("main: actions (loadActionMap)", "true true true true",
                () -> (main.getActionMap().get("negativeIncrement") != null) + " "
                        + (main.getActionMap().get("positiveIncrement") != null) + " "
                        + (main.getActionMap().get("selectMin") != null) + " "
                        + (main.getActionMap().get("startResize") != null)));
        checks.add(Checks.expect("main: binding F8 (ancestor of focused)", "startResize",
                () -> main.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("F8"))));
        checks.add(Checks.expect("collapsed: divider location after the one-touch click (left inset)", 1,
                s.collapsedSplit::getDividerLocation));
        checks.add(Checks.expect("collapsed: last divider location", 120, s.collapsedSplit::getLastDividerLocation));
        checks.add(Checks.expect("proportional: divider location (0.25 of 210 - 10)", 50,
                s.proportionalSplit::getDividerLocation));
        checks.add(Checks.expect("proportional: divider size (Metal default)", 10, s.proportionalSplit::getDividerSize));
        return checks;
    }

    // ------------------------------------------------------------------------------------------------ tabbed panes

    private static Component tabbedPanes(State s) {
        // a PNG classpath resource through ImageIcon(URL) : resource: URL in a native executable, Toolkit PNG decoder
        s.badge = new ImageIcon(Edt.resource("/showcase/swing-containers/badge.png"));
        List<Component> placementViews = new ArrayList<>();
        String[] names = { "TOP", "BOTTOM", "LEFT", "RIGHT" };
        int[] placements = { SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT };
        for (int i = 0; i < placements.length; i++) {
            JTabbedPane tabs = new JTabbedPane(placements[i]);
            tabs.addTab("Alpha", icon(BLUE, SwingKit.CIRCLE, 12), block("Alpha", 0xE3F2FD, 120, 80), "first tab");
            tabs.addTab("Beta", s.badge, block("Beta", 0xFFF3E0, 120, 80));
            tabs.addTab("Gamma", icon(GREEN, SwingKit.TRIANGLE, 12), block("Gamma", 0xE8F5E9, 120, 80));
            tabs.setEnabledAt(2, false);
            tabs.setBackgroundAt(1, new Color(0xFFE0B2));
            tabs.setSelectedIndex(1);
            s.placements.add(sized(tabs, 240, 150));
            placementViews.add(captioned(names[i], tabs));
        }

        JTabbedPane wrap = new JTabbedPane(SwingConstants.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
        JTabbedPane scroll = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        String[] planets = { "Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto" };
        for (int i = 0; i < planets.length; i++) {
            wrap.addTab(planets[i], block(planets[i], i % 2 == 0 ? 0xE8EAF6 : 0xF3E5F5, 100, 40));
            scroll.addTab(planets[i], block(planets[i], i % 2 == 0 ? 0xE0F2F1 : 0xFFFDE7, 100, 40));
        }
        wrap.setSelectedIndex(6);
        scroll.setSelectedIndex(7);
        s.wrapTabs = sized(wrap, 500, 150);
        s.scrollTabs = sized(scroll, 500, 150);

        JTabbedPane components = new JTabbedPane();
        for (String name : List.of("Report.txt", "Notes.md", "Build.log", "Draft.html")) {
            components.addTab(name, block(name, 0xFAFAFA, 100, 60));
            components.setTabComponentAt(components.getTabCount() - 1, new CloseableTab(components, name));
        }
        components.addTab("<html><b>HTML</b> <i>title</i></html>", block("HTML title", 0xFFF8E1, 100, 60));
        components.setSelectedIndex(1);
        s.componentTabs = sized(components, 500, 130);

        JTabbedPane mnemonics = new JTabbedPane(SwingConstants.BOTTOM);
        String[] mnemonicNames = { "Alpha", "Bravo", "Charlie", "Delta", "Echo" };
        for (int i = 0; i < mnemonicNames.length; i++) {
            mnemonics.addTab(mnemonicNames[i], block(mnemonicNames[i], 0xECEFF1, 100, 60));
            mnemonics.setMnemonicAt(i, mnemonicNames[i].charAt(0));
        }
        mnemonics.setDisplayedMnemonicIndexAt(3, 2);
        mnemonics.setEnabledAt(2, false);
        s.mnemonicTabs = sized(mnemonics, 500, 130);

        return section("JTabbedPane",
                "The four tab placements (the disabled Gamma tab is skipped by navigation) ; wrapped runs and the "
                        + "scroll tab layout with arrow buttons ; tab components with a close button (Build.log closed "
                        + "with doClick) and an HTML title ; mnemonics (Delta underlines its third letter).",
                column(12, row(12, placementViews.toArray(Component[]::new)),
                        row(24, captioned("WRAP_TAB_LAYOUT, Uranus selected", s.wrapTabs),
                                captioned("SCROLL_TAB_LAYOUT, Neptune selected", s.scrollTabs)),
                        row(24, captioned("tab components (JLabel + close button)", s.componentTabs),
                                captioned("mnemonics, selected by actions and a synthetic alt+E", s.mnemonicTabs))));
    }

    private static List<Check> tabChecks(State s) {
        List<Check> checks = new ArrayList<>();
        String[] names = { "TOP", "BOTTOM", "LEFT", "RIGHT" };
        for (int i = 0; i < names.length; i++) {
            JTabbedPane tabs = s.placements.get(i);
            checks.add(Checks.info(names[i] + ": tab 0 bounds / selected component bounds",
                    () -> rect(tabs.getBoundsAt(0)) + " / " + rect(tabs.getSelectedComponent().getBounds())));
        }
        JTabbedPane top = s.placements.getFirst();
        checks.add(Checks.expect("badge.png through ImageIcon(URL) : load status, size", "8 16x16",
                () -> s.badge.getImageLoadStatus() + " " + s.badge.getIconWidth() + "x" + s.badge.getIconHeight()));
        checks.add(Checks.expect("TOP: tab count, selected, enabled(2)", "3 1 false",
                () -> top.getTabCount() + " " + top.getSelectedIndex() + " " + top.isEnabledAt(2)));
        checks.add(Checks.expect("TOP: indexAtLocation(center of tab 0)", 0, () -> {
            Rectangle r = top.getBoundsAt(0);
            return top.indexAtLocation(r.x + r.width / 2, r.y + r.height / 2);
        }));
        checks.add(Checks.expect("TOP: tool tip of tab 0, indexOfTab(Beta)", "first tab 1",
                () -> top.getToolTipTextAt(0) + " " + top.indexOfTab("Beta")));
        checks.add(Checks.expect("TOP: action navigateNext skips the disabled tab", "1 -> 0", () -> {
            JTabbedPane tabs = s.placements.get(1);
            int before = tabs.getSelectedIndex();
            SwingKit.perform(tabs, "navigateNext", null);
            int after = tabs.getSelectedIndex();
            SwingKit.perform(tabs, "navigatePrevious", null);
            return before + " -> " + after;
        }));
        checks.add(Checks.expect("BOTTOM: selected after navigateNext and navigatePrevious", 1,
                () -> s.placements.get(1).getSelectedIndex()));
        checks.add(Checks.expect("TOP: binding ctrl PAGE_DOWN (ancestor of focused)", "navigatePageDown",
                () -> top.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                        .get(KeyStroke.getKeyStroke("ctrl PAGE_DOWN"))));
        checks.add(Checks.expect("TOP: UI delegate", "javax.swing.plaf.metal.MetalTabbedPaneUI", () -> ui(top)));
        checks.add(Checks.info("WRAP: tab run count", s.wrapTabs::getTabRunCount));
        checks.add(Checks.expect("WRAP: layout policy", JTabbedPane.WRAP_TAB_LAYOUT, s.wrapTabs::getTabLayoutPolicy));
        checks.add(Checks.expect("SCROLL: tab run count", 1, s.scrollTabs::getTabRunCount));
        checks.add(Checks.info("SCROLL: bounds of the selected tab", () -> rect(s.scrollTabs.getBoundsAt(7))));
        checks.add(Checks.expect("SCROLL: scroll buttons", 2, () -> SwingKit.findAll(s.scrollTabs, JButton.class)
                .stream().filter(b -> b instanceof javax.swing.plaf.UIResource).count()));
        checks.add(Checks.expect("SCROLL: actions scrollTabsForward/Backward", "true true",
                () -> (s.scrollTabs.getActionMap().get("scrollTabsForwardAction") != null) + " "
                        + (s.scrollTabs.getActionMap().get("scrollTabsBackwardAction") != null)));
        JTabbedPane comps = s.componentTabs;
        checks.add(Checks.expect("components: close Build.log with doClick", "5 -> 4", () -> {
            int before = comps.getTabCount();
            CloseableTab tab = (CloseableTab) comps.getTabComponentAt(2);
            tab.close.doClick(0);
            return before + " -> " + comps.getTabCount();
        }));
        checks.add(Checks.expect("components: titles", "Report.txt Notes.md Draft.html <html><b>HTML</b> <i>title</i></html>",
                () -> {
                    List<String> titles = new ArrayList<>();
                    for (int i = 0; i < comps.getTabCount(); i++) {
                        titles.add(comps.getTitleAt(i));
                    }
                    return String.join(" ", titles);
                }));
        checks.add(Checks.expect("components: tab component of tab 0", CloseableTab.class.getSimpleName(),
                () -> comps.getTabComponentAt(0).getClass().getSimpleName()));
        checks.add(Checks.expect("components: indexOfTabComponent", 1,
                () -> comps.indexOfTabComponent(comps.getTabComponentAt(1))));
        JTabbedPane m = s.mnemonicTabs;
        checks.add(Checks.expect("mnemonics: mnemonic / displayed index of Delta", "68 / 2",
                () -> m.getMnemonicAt(3) + " / " + m.getDisplayedMnemonicIndexAt(3)));
        checks.add(Checks.expect("mnemonics: binding alt B (in focused window)", "setSelectedIndex",
                () -> m.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("alt B"))));
        checks.add(Checks.expect("mnemonics: action setSelectedIndex with command d", 3, () -> {
            SwingKit.perform(m, "setSelectedIndex", "d");
            return m.getSelectedIndex();
        }));
        checks.add(Checks.expect("mnemonics: action setSelectedIndex with command c (disabled)", 3, () -> {
            SwingKit.perform(m, "setSelectedIndex", "c");
            return m.getSelectedIndex();
        }));
        checks.add(Checks.expect("mnemonics: synthetic alt+E key press", 4, () -> {
            SwingKit.key(m, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_E, 'e');
            return m.getSelectedIndex();
        }));
        return checks;
    }

    /**
     * A tab component : a label and a small close button removing its tab.
     */
    private static final class CloseableTab extends JPanel {

        final JButton close;

        CloseableTab(JTabbedPane tabs, String title) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            JLabel label = new JLabel(title, icon(ORANGE, SwingKit.SQUARE, 10), SwingConstants.LEFT);
            close = new JButton("x");
            close.setMargin(new java.awt.Insets(0, 3, 0, 3));
            close.setFont(close.getFont().deriveFont(10f));
            close.setFocusable(false);
            close.addActionListener(e -> {
                int index = tabs.indexOfTabComponent(this);
                if (index >= 0) {
                    tabs.remove(index);
                }
            });
            add(label);
            add(close);
        }
    }

    // ------------------------------------------------------------------------------- layered pane and root pane

    private static Component layeredAndRoot(State s) {
        JLayeredPane layered = new JLayeredPane();
        layered.setOpaque(true);
        layered.setBackground(new Color(0xFAFAFA));
        layered.setBorder(BorderFactory.createLineBorder(new Color(GREY)));
        Object[][] specs = {
                { "DEFAULT 0, pos 0", JLayeredPane.DEFAULT_LAYER, 0, BLUE, 10, 10 },
                { "DEFAULT 0, pos 1", JLayeredPane.DEFAULT_LAYER, 1, 0x90CAF9, 40, 40 },
                { "PALETTE 100", JLayeredPane.PALETTE_LAYER, -1, GREEN, 120, 60 },
                { "MODAL 200", JLayeredPane.MODAL_LAYER, -1, ORANGE, 200, 30 },
                { "POPUP 300", JLayeredPane.POPUP_LAYER, -1, PURPLE, 260, 90 },
                { "DRAG 400", JLayeredPane.DRAG_LAYER, -1, RED, 320, 120 },
                { "layer -10", -10, -1, GREY, 250, 5 },
        };
        for (Object[] spec : specs) {
            JLabel label = block((String) spec[0], (int) spec[3], 170, 70);
            label.setVerticalAlignment(SwingConstants.TOP);
            label.setBorder(BorderFactory.createLineBorder(new Color(0x263238)));
            label.setBounds((int) spec[4], (int) spec[5], 170, 70);
            layered.add(label, spec[1], (int) spec[2]);
            s.layeredBlocks.add(label);
        }
        // the second block of the default layer is brought to the front of its layer
        layered.moveToFront(s.layeredBlocks.get(1));
        s.layered = sized(layered, 500, 220);

        JRootPane root = new JRootPane();
        JMenuBar bar = new JMenuBar();
        bar.add(new JMenu("File"));
        bar.add(new JMenu("Edit"));
        bar.add(new JMenu("Help"));
        root.setJMenuBar(bar);
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        form.add(new JLabel("Name"));
        form.add(new JTextField("Duke"));
        form.add(new JLabel("Role"));
        form.add(new JComboBox<>(new String[] { "Mascot", "Developer" }));
        form.add(new JButton("Cancel"));
        form.add(new JButton("Save"));
        root.getContentPane().add(form, BorderLayout.CENTER);
        root.setDefaultButton((JButton) form.getComponent(5));
        root.setGlassPane(new Veil());
        root.getGlassPane().setVisible(true);
        SwingKit.unfocusable(root);
        s.rootPane = sized(root, 380, 220);

        return section("JLayeredPane and JRootPane",
                "Components in the standard layers and at positions within a layer (the 'DEFAULT pos 1' block was moved "
                        + "to the front of its layer) ; a stand-alone JRootPane with a menu bar, a default button and "
                        + "its glass pane visible (a translucent veil painted over the content).",
                row(24, captioned("JLayeredPane", s.layered), captioned("JRootPane glass pane", s.rootPane)));
    }

    private static List<Check> layeredChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JLayeredPane lp = s.layered;
        List<JComponent> blocks = s.layeredBlocks;
        checks.add(Checks.expect("layers of the blocks", "0 0 100 200 300 400 -10", () -> {
            List<String> layers = new ArrayList<>();
            blocks.forEach(b -> layers.add(String.valueOf(lp.getLayer(b))));
            return String.join(" ", layers);
        }));
        checks.add(Checks.expect("positions of the blocks in their layer", "1 0 0 0 0 0 0", () -> {
            List<String> positions = new ArrayList<>();
            blocks.forEach(b -> positions.add(String.valueOf(lp.getPosition(b))));
            return String.join(" ", positions);
        }));
        checks.add(Checks.expect("child indexes (0 = front)", "5 4 3 2 1 0 6", () -> {
            List<String> indexes = new ArrayList<>();
            blocks.forEach(b -> indexes.add(String.valueOf(lp.getIndexOf(b))));
            return String.join(" ", indexes);
        }));
        checks.add(Checks.expect("highest / lowest layer", "400 / -10", () -> lp.highestLayer() + " / " + lp.lowestLayer()));
        checks.add(Checks.expect("components in DEFAULT layer", 2, () -> lp.getComponentCountInLayer(0)));
        checks.add(Checks.expect("optimized drawing enabled (overlapping children)", false, lp::isOptimizedDrawingEnabled));
        checks.add(Checks.expect("setLayer(DRAG block, PALETTE) then back", "100 -> 400", () -> {
            JComponent drag = blocks.get(5);
            lp.setLayer(drag, JLayeredPane.PALETTE_LAYER);
            int moved = lp.getLayer(drag);
            lp.setLayer(drag, JLayeredPane.DRAG_LAYER);
            return moved + " -> " + lp.getLayer(drag);
        }));
        JRootPane root = s.rootPane;
        checks.add(Checks.expect("root pane: glass pane visible, first child", "true true",
                () -> root.getGlassPane().isVisible() + " " + (root.getComponent(0) == root.getGlassPane())));
        checks.add(Checks.expect("root pane: layout", "javax.swing.JRootPane$RootLayout",
                () -> root.getLayout().getClass().getName()));
        checks.add(Checks.expect("root pane: menu bar layer", JLayeredPane.FRAME_CONTENT_LAYER,
                () -> root.getLayeredPane().getLayer(root.getJMenuBar())));
        checks.add(Checks.info("root pane: menu bar / content pane bounds",
                () -> rect(root.getJMenuBar().getBounds()) + " / " + rect(root.getContentPane().getBounds())));
        checks.add(Checks.expect("root pane: glass pane bounds", "0,0 380x220", () -> rect(root.getGlassPane().getBounds())));
        checks.add(Checks.expect("root pane: default button", "Save", () -> root.getDefaultButton().getText()));
        checks.add(Checks.expect("root pane: UI delegate, decoration style", "javax.swing.plaf.metal.MetalRootPaneUI 0",
                () -> ui(root) + " " + root.getWindowDecorationStyle()));
        checks.add(Checks.expect("root pane: actions press / release (loadActionMap)", "true true",
                () -> (root.getActionMap().get("press") != null) + " " + (root.getActionMap().get("release") != null)));
        checks.add(Checks.expect("root pane: binding ENTER (in focused window)", "press",
                () -> root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ENTER"))));
        return checks;
    }

    /**
     * The glass pane : a translucent veil with a rounded badge, painted over the whole root pane.
     */
    private static final class Veil extends JComponent {

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                g.setColor(new Color(0x263238));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                RoundRectangle2D badge = new RoundRectangle2D.Float(getWidth() / 2f - 80, getHeight() / 2f - 22, 160, 44,
                        22, 22);
                g.setColor(new Color(0xFFFFFF));
                g.fill(badge);
                g.setColor(new Color(0x1E88E5));
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
                String text = "Glass pane";
                int w = g.getFontMetrics().stringWidth(text);
                g.drawString(text, (getWidth() - w) / 2, getHeight() / 2 + 5);
            } finally {
                g.dispose();
            }
        }
    }

    // --------------------------------------------------------------------------------------------- tool bars, box

    private static Component toolBars(State s) {
        JToolBar bar = new JToolBar("Editing");
        bar.setRollover(true);
        bar.add(new JButton("New", icon(BLUE, SwingKit.SQUARE, 16)));
        bar.add(new JButton(icon(ORANGE, SwingKit.CIRCLE, 16)));
        bar.addSeparator();
        JToggleButton bold = new JToggleButton("Bold", icon(RED, SwingKit.DIAMOND, 16), true);
        bar.add(bold);
        bar.add(new JToggleButton("Italic"));
        bar.addSeparator(new Dimension(30, 10));
        bar.add(new JComboBox<>(new String[] { "Dialog 12", "Serif 14", "Monospaced 11" }));
        JButton disabled = new JButton("Disabled", icon(GREEN, SwingKit.TRIANGLE, 16));
        disabled.setEnabled(false);
        bar.add(disabled);
        s.toolBar = bar;

        JToolBar vertical = new JToolBar("Tools", JToolBar.VERTICAL);
        vertical.add(new JButton(icon(PURPLE, SwingKit.SQUARE, 16)));
        vertical.add(new JButton(icon(TEAL, SwingKit.CIRCLE, 16)));
        vertical.addSeparator();
        vertical.add(new JButton(icon(PINK, SwingKit.TRIANGLE, 16)));
        s.verticalToolBar = vertical;

        JPanel dock = new JPanel(new BorderLayout());
        dock.setBorder(BorderFactory.createLineBorder(new Color(GREY)));
        dock.add(bar, BorderLayout.NORTH);
        dock.add(vertical, BorderLayout.WEST);
        dock.add(block("docking area (BorderLayout)", 0xF5F5F5, 200, 100), BorderLayout.CENTER);
        sized(dock, 560, 160);

        JToolBar plain = new JToolBar();
        plain.setFloatable(false);
        plain.setBorderPainted(false);
        plain.setMargin(new java.awt.Insets(4, 8, 4, 8));
        plain.add(new JButton("Cut"));
        plain.add(new JButton("Copy"));
        plain.add(new JButton("Paste"));
        s.plainToolBar = plain;

        JToolBar floating = new JToolBar("Floating palette");
        floating.setUI(new PassiveFloatingToolBarUI());
        floating.add(new JButton(icon(BLUE, SwingKit.DIAMOND, 16)));
        floating.add(new JButton(icon(ORANGE, SwingKit.SQUARE, 16)));
        floating.add(new JButton(icon(GREEN, SwingKit.CIRCLE, 16)));
        s.floatingToolBar = floating;
        JPanel floatingDock = new JPanel(new BorderLayout());
        floatingDock.setBorder(BorderFactory.createLineBorder(new Color(GREY)));
        floatingDock.add(floating, BorderLayout.NORTH);
        floatingDock.add(block("floated, then docked back", 0xF5F5F5, 200, 60), BorderLayout.CENTER);
        s.floatingDock = sized(floatingDock, 400, 100);

        Box hbox = Box.createHorizontalBox();
        hbox.add(new JButton("One"));
        hbox.add(visible(Box.createHorizontalStrut(20), YELLOW, s));
        hbox.add(new JButton("Two"));
        hbox.add(visible(Box.createRigidArea(new Dimension(40, 30)), ORANGE, s));
        hbox.add(new JButton("Three"));
        hbox.add(visible(Box.createHorizontalGlue(), GREEN, s));
        hbox.add(visible(new Box.Filler(new Dimension(10, 10), new Dimension(60, 20), new Dimension(200, 20)), PINK, s));
        hbox.add(new JButton("Four"));
        hbox.setBorder(BorderFactory.createLineBorder(new Color(GREY)));
        s.hbox = sized(hbox, 700, 50);

        Box vbox = Box.createVerticalBox();
        vbox.add(new JLabel("Top"));
        vbox.add(visible(Box.createVerticalStrut(10), YELLOW, s));
        vbox.add(new JLabel("Middle"));
        vbox.add(visible(Box.createVerticalGlue(), GREEN, s));
        vbox.add(new JLabel("Bottom"));
        vbox.setBorder(BorderFactory.createLineBorder(new Color(GREY)));
        s.vbox = sized(vbox, 120, 140);

        return section("JToolBar and Box",
                "A floatable rollover tool bar with separators (default and 30x10), a toggled button, a combo box and a "
                        + "disabled button, docked NORTH, with a vertical tool bar docked WEST ; a non floatable tool bar "
                        + "without border and with margins ; a tool bar floated into its own window (see the extra "
                        + "image) and docked back. Boxes : fillers are painted (strut yellow, rigid area orange, glue "
                        + "green, custom filler pink).",
                column(12, row(24, captioned("floatable, rollover, NORTH + vertical WEST", dock),
                        column(12, captioned("not floatable, no border, margins", s.plainToolBar),
                                captioned("floated and docked back", s.floatingDock))),
                        row(24, captioned("horizontal Box", s.hbox), captioned("vertical Box", s.vbox))));
    }

    private static Component visible(Component filler, int rgb, State s) {
        JComponent c = (JComponent) filler;
        c.setOpaque(true);
        c.setBackground(new Color(rgb));
        s.fillers.add(c);
        return c;
    }

    private static void floatAndDock(State s) {
        JToolBar bar = s.floatingToolBar;
        MetalToolBarUI ui = (MetalToolBarUI) bar.getUI();
        ui.setFloatingLocation(FLOAT_X, FLOAT_Y);
        ui.setFloating(true, null);
        Window window = SwingUtilities.getWindowAncestor(bar);
        s.floatingWindow = window;
        if (window instanceof RootPaneContainer container) {
            s.floatingImage = Snapshots.render(container.getRootPane());
        }
    }

    private static List<Check> toolBarChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JToolBar bar = s.toolBar;
        checks.add(Checks.expect("tool bar: component count, orientation, floatable, rollover", "8 0 true true",
                () -> bar.getComponentCount() + " " + bar.getOrientation() + " " + bar.isFloatable() + " "
                        + bar.isRollover()));
        checks.add(Checks.expect("tool bar: separator sizes (default, custom)", "10x10 / 30x10", () -> {
            List<JToolBar.Separator> separators = SwingKit.findAll(bar, JToolBar.Separator.class);
            return size(separators.get(0).getSeparatorSize()) + " / " + size(separators.get(1).getSeparatorSize());
        }));
        checks.add(Checks.info("tool bar: separator bounds", () -> {
            List<String> bounds = new ArrayList<>();
            SwingKit.findAll(bar, JToolBar.Separator.class).forEach(sep -> bounds.add(rect(sep.getBounds())));
            return String.join(" ", bounds);
        }));
        checks.add(Checks.expect("tool bar: index of the combo box", 6,
                () -> bar.getComponentIndex(SwingKit.find(bar, JComboBox.class))));
        checks.add(Checks.expect("tool bar: UI delegates", "javax.swing.plaf.metal.MetalToolBarUI "
                + "javax.swing.plaf.basic.BasicToolBarSeparatorUI",
                () -> ui(bar) + " " + ui(SwingKit.find(bar, JToolBar.Separator.class))));
        checks.add(Checks.expect("tool bar: actions navigateRight / navigateLeft (loadActionMap)", "true true",
                () -> (bar.getActionMap().get("navigateRight") != null) + " "
                        + (bar.getActionMap().get("navigateLeft") != null)));
        checks.add(Checks.expect("tool bar: layout", "javax.swing.JToolBar$DefaultToolBarLayout",
                () -> bar.getLayout().getClass().getName()));
        checks.add(Checks.expect("vertical: orientation, parent constraint", "1 West",
                () -> s.verticalToolBar.getOrientation() + " "
                        + ((BorderLayout) s.verticalToolBar.getParent().getLayout()).getConstraints(s.verticalToolBar)));
        checks.add(Checks.expect("plain: floatable, border painted, margin", "false false 4,8,4,8",
                () -> s.plainToolBar.isFloatable() + " " + s.plainToolBar.isBorderPainted() + " "
                        + s.plainToolBar.getMargin().top + "," + s.plainToolBar.getMargin().left + ","
                        + s.plainToolBar.getMargin().bottom + "," + s.plainToolBar.getMargin().right));

        JToolBar floating = s.floatingToolBar;
        checks.add(Checks.expect("floating: floated into its own window",
                "true javax.swing.plaf.basic.BasicToolBarUI$1ToolBarDialog",
                () -> {
                    floatAndDock(s);
                    return ((MetalToolBarUI) floating.getUI()).isFloating() + " "
                            + (s.floatingWindow == null ? "none" : s.floatingWindow.getClass().getName());
                }));
        checks.add(Checks.expect("floating: window title, location, showing", "Floating palette " + FLOAT_X + ","
                + FLOAT_Y + " true", () -> {
                    java.awt.Dialog dialog = (java.awt.Dialog) s.floatingWindow;
                    return dialog.getTitle() + " " + SwingKit.point(dialog.getLocation()) + " " + dialog.isShowing();
                }));
        checks.add(Checks.expect("floating: docked back NORTH", "false true North", () -> {
            ((MetalToolBarUI) floating.getUI()).setFloating(false, null);
            return ((MetalToolBarUI) floating.getUI()).isFloating() + " " + (floating.getParent() == s.floatingDock)
                    + " " + ((BorderLayout) s.floatingDock.getLayout()).getConstraints(floating);
        }));
        checks.add(Checks.expect("floating: window hidden after docking", false,
                () -> s.floatingWindow != null && s.floatingWindow.isVisible()));

        checks.add(Checks.info("hbox: filler bounds (strut, rigid, glue, filler)", () -> {
            List<String> bounds = new ArrayList<>();
            for (Component c : s.fillers.subList(0, 4)) {
                bounds.add(rect(c.getBounds()));
            }
            return String.join(" ", bounds);
        }));
        checks.add(Checks.expect("hbox: strut and rigid area widths", "20 40",
                () -> s.fillers.get(0).getWidth() + " " + s.fillers.get(1).getWidth()));
        checks.add(Checks.expect("hbox: layout, axis", "javax.swing.BoxLayout 0",
                () -> s.hbox.getLayout().getClass().getName() + " " + ((javax.swing.BoxLayout) s.hbox.getLayout()).getAxis()));
        checks.add(Checks.expect("hbox: filler minimum / preferred / maximum", "10x10 / 60x20 / 200x20", () -> {
            Component f = s.fillers.get(3);
            return size(f.getMinimumSize()) + " / " + size(f.getPreferredSize()) + " / " + size(f.getMaximumSize());
        }));
        checks.add(Checks.info("vbox: strut and glue bounds",
                () -> rect(s.fillers.get(4).getBounds()) + " " + rect(s.fillers.get(5).getBounds())));
        return checks;
    }

    /**
     * The Metal tool bar UI whose floating window never takes the focus (the showcase never steals the focus of the
     * user's application). Set with {@code setUI} : no reflection involved.
     */
    private static final class PassiveFloatingToolBarUI extends MetalToolBarUI {

        @Override
        protected RootPaneContainer createFloatingWindow(JToolBar toolbar) {
            RootPaneContainer container = super.createFloatingWindow(toolbar);
            if (container instanceof Window window) {
                window.setAutoRequestFocus(false);
                window.setFocusableWindowState(false);
            }
            return container;
        }
    }

    // ------------------------------------------------------------------------------------------------ components

    /**
     * A scrollable checkerboard with cell coordinates (unit increment 20, block increment 100).
     */
    private static final class GridView extends JComponent implements Scrollable {

        private static final int CELL = 40;
        private final int width;
        private final int height;
        private final int rgb;
        private final String name;

        GridView(int width, int height, int rgb, String name) {
            this.width = width;
            this.height = height;
            this.rgb = rgb;
            this.name = name;
            setOpaque(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                g.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
                Color light = new Color(0xFFFFFF);
                Color tint = new Color(rgb);
                Color faded = new Color((tint.getRed() + 3 * 255) / 4, (tint.getGreen() + 3 * 255) / 4,
                        (tint.getBlue() + 3 * 255) / 4);
                Rectangle clip = g.getClipBounds() == null ? new Rectangle(0, 0, width, height) : g.getClipBounds();
                for (int y = clip.y / CELL * CELL; y < Math.min(height, clip.y + clip.height); y += CELL) {
                    for (int x = clip.x / CELL * CELL; x < Math.min(width, clip.x + clip.width); x += CELL) {
                        g.setColor(((x + y) / CELL) % 2 == 0 ? faded : light);
                        g.fillRect(x, y, CELL, CELL);
                        g.setColor(new Color(0x546E7A));
                        g.drawString(x / CELL + "," + y / CELL, x + 4, y + 14);
                    }
                }
                g.setColor(tint);
                g.drawRect(0, 0, width - 1, height - 1);
                g.drawString(name, 6, height - 6);
            } finally {
                g.dispose();
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(200, 160);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * A ruler used as row or column header : a tick every 10 pixels, a number every 50.
     */
    private static final class Ruler extends JComponent {

        private final boolean horizontal;
        private final int length;

        Ruler(boolean horizontal, int length) {
            this.horizontal = horizontal;
            this.length = length;
            setOpaque(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return horizontal ? new Dimension(length, 20) : new Dimension(30, length);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0xFFF8E1));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0x5D4037));
                g.setFont(new Font(Font.DIALOG, Font.PLAIN, 9));
                for (int i = 0; i < length; i += 10) {
                    int tick = i % 50 == 0 ? 8 : 4;
                    if (horizontal) {
                        g.drawLine(i, 20 - tick, i, 19);
                        if (i % 50 == 0) {
                            g.drawString(String.valueOf(i), i + 2, 10);
                        }
                    } else {
                        g.drawLine(30 - tick, i, 29, i);
                        if (i % 50 == 0) {
                            g.drawString(String.valueOf(i), 2, i + 10);
                        }
                    }
                }
            } finally {
                g.dispose();
            }
        }
    }
}
