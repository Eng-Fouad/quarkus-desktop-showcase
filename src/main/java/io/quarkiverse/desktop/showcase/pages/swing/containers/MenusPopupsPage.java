package io.quarkiverse.desktop.showcase.pages.swing.containers;

import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.block;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.captioned;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.column;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.icon;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.row;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.section;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.size;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.sized;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Swing menus and popups : a {@link JMenuBar} (menus, items with icons, accelerators and mnemonics, check box and radio
 * button items, separators, cascading submenus, HTML and {@link Action} based items, a glue pushing Help to the right),
 * the menus opened through {@link MenuSelectionManager}, and popups of every weight : a {@link JPopupMenu} as a light
 * weight popup (a component of the layered pane), as a heavy weight popup (a {@code JWindow}) when light weight popups
 * are disabled or when it leaves the window, and {@link PopupFactory} popups (medium weight : an AWT panel, forced heavy
 * weight, and a light weight tool tip).
 * <p>
 * A shown {@code JPopupMenu} grabs the window : when the showcase window is not the focused window (the usual case in
 * snapshot mode), the toolkit ungrabs it at once and Swing cancels the menu a moment later. Each popup is therefore
 * shown, captured and hidden within one event dispatch, which makes the images independent of the focus. The popups
 * are extra images ; the page shows the menus rendered without a window (printAll of the popup menus).
 */
@Singleton
public class MenusPopupsPage implements FeaturePage {

    private State state;

    private static final class State {
        final List<String> actions = new ArrayList<>();
        final List<String> menuEvents = new ArrayList<>();
        final List<String> popupEvents = new ArrayList<>();
        final Map<String, BufferedImage> extras = new LinkedHashMap<>();
        final List<Check> popupChecks = new ArrayList<>();
        JMenuBar bar;
        JPanel barHolder;
        JMenu file;
        JMenu recent;
        JMenu edit;
        JMenu view;
        JMenu format;
        JMenu help;
        JMenuItem newItem;
        JMenuItem saveAs;
        JMenuItem print;
        JMenuItem recentSecond;
        JCheckBoxMenuItem toolbar;
        JCheckBoxMenuItem statusBar;
        JCheckBoxMenuItem wrap;
        JRadioButtonMenuItem small;
        JRadioButtonMenuItem large;
        JMenuItem html;
        Action copy;
        final List<JLabel> anchors = new ArrayList<>();
        SwingKit.CheckColumns menuTable;
        SwingKit.CheckColumns popupTable;
    }

    @Override
    public String id() {
        return "swing-menus-popups";
    }

    @Override
    public String title() {
        return "Menus and popups";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Component build() {
        State s = new State();
        state = s;
        s.bar = menuBar(s);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBorder(BorderFactory.createLineBorder(new Color(0xB0BEC5)));
        holder.add(s.bar, BorderLayout.NORTH);
        JLabel area = block("the File menu and its Open Recent submenu are opened here (extra image menubar-open)",
                0xF5F7FA, 600, 250);
        area.setForeground(new Color(0x78909C));
        holder.add(area, BorderLayout.CENTER);
        s.barHolder = sized(holder, 1000, 280);

        String[] anchorNames = { "light : JPopupMenu", "heavy : lightweight disabled", "medium : PopupFactory",
                "heavy : PopupFactory forced", "light : JToolTip" };
        List<Component> anchorViews = new ArrayList<>();
        for (String name : anchorNames) {
            JLabel anchor = block(name, 0xE3F2FD, 186, 36);
            anchor.setBorder(BorderFactory.createLineBorder(new Color(0x90CAF9)));
            s.anchors.add(anchor);
            anchorViews.add(anchor);
        }
        JLabel leaving = block("heavy : leaves the window", 0xFFF3E0, 1000, 36);
        leaving.setHorizontalAlignment(JLabel.RIGHT);
        leaving.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xFFCC80)),
                BorderFactory.createEmptyBorder(0, 0, 0, 12)));
        s.anchors.add(leaving);

        List<Component> renders = new ArrayList<>();
        for (JMenu menu : List.of(s.file, s.edit, s.view, s.format, s.recent)) {
            renders.add(captioned(menu.getText() + " (printAll of its popup menu)",
                    Ui.image(Snapshots.renderAtPreferredSize(menu.getPopupMenu()))));
        }

        s.menuTable = SwingKit.pending("Menu bar, items and actions");
        s.popupTable = SwingKit.pending("Popups");
        return column(18,
                Ui.heading("Menus and popups"),
                Ui.text("A JMenuBar in the page (Metal) with accelerators, mnemonics, icons, check box and radio button "
                        + "items, submenus, HTML text and Action based items ; the Help menu is pushed right by a glue. "
                        + "The extra images show the menus opened through the MenuSelectionManager and the popups "
                        + "shown under the anchors below, with their weight.", SwingKit.WIDTH),
                s.barHolder,
                section("Popup anchors", "Each popup is shown under its anchor, captured and hidden again (extra "
                        + "images popup-*). The last one starts 60 pixels before the right edge of the window.",
                        column(8, row(17, anchorViews.toArray(Component[]::new)), leaving)),
                section("Menus rendered without a window", null, SwingKit.wrap(SwingKit.WIDTH, 16, renders)),
                s.menuTable, s.popupTable);
    }

    private static JMenuBar menuBar(State s) {
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        JMenuBar bar = new JMenuBar();

        s.file = menu("File", KeyEvent.VK_F, s);
        s.newItem = item("New", KeyEvent.VK_N, KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrl),
                icon(0x42A5F5, SwingKit.SQUARE, 16), s);
        s.file.add(s.newItem);
        s.file.add(item("Open...", KeyEvent.VK_O, KeyStroke.getKeyStroke(KeyEvent.VK_O, ctrl),
                icon(0xFFA726, SwingKit.DIAMOND, 16), s));
        s.recent = menu("Open Recent", KeyEvent.VK_R, s);
        s.recent.setIcon(icon(0x66BB6A, SwingKit.CIRCLE, 16));
        s.recent.add(item("report.txt", KeyEvent.VK_1, null, null, s));
        s.recentSecond = item("notes.md", KeyEvent.VK_2, null, null, s);
        s.recent.add(s.recentSecond);
        s.recent.add(item("build.log", KeyEvent.VK_3, null, null, s));
        s.recent.addSeparator();
        s.recent.add(item("Clear list", 0, null, null, s));
        s.file.add(s.recent);
        s.file.addSeparator();
        JMenuItem save = item("Save", KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrl), null, s);
        s.file.add(save);
        s.saveAs = item("Save As...", KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrl | InputEvent.SHIFT_DOWN_MASK),
                null, s);
        s.saveAs.setDisplayedMnemonicIndex(5);
        s.file.add(s.saveAs);
        s.file.addSeparator();
        s.print = item("Print...", KeyEvent.VK_P, KeyStroke.getKeyStroke(KeyEvent.VK_P, ctrl), null, s);
        s.print.setEnabled(false);
        s.file.add(s.print);
        s.file.addSeparator();
        s.file.add(item("Close", KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_W, ctrl), null, s));
        bar.add(s.file);

        s.edit = menu("Edit", KeyEvent.VK_E, s);
        s.edit.add(item("Undo", KeyEvent.VK_U, KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrl), null, s));
        s.edit.add(item("Redo", KeyEvent.VK_R, KeyStroke.getKeyStroke(KeyEvent.VK_Y, ctrl), null, s));
        s.edit.addSeparator();
        s.edit.add(new JMenuItem(action("Cut", KeyEvent.VK_T, KeyEvent.VK_X, 0xEF5350, s)));
        s.copy = action("Copy", KeyEvent.VK_C, KeyEvent.VK_C, 0xAB47BC, s);
        s.edit.add(new JMenuItem(s.copy));
        s.edit.add(new JMenuItem(action("Paste", KeyEvent.VK_P, KeyEvent.VK_V, 0x26A69A, s)));
        s.edit.addSeparator();
        s.edit.add(item("Find Next", KeyEvent.VK_N, KeyStroke.getKeyStroke("F3"), null, s));
        bar.add(s.edit);

        s.view = menu("View", KeyEvent.VK_V, s);
        s.toolbar = checkItem("Toolbar", true, s);
        s.statusBar = checkItem("Status Bar", false, s);
        s.view.add(s.toolbar);
        s.view.add(s.statusBar);
        s.view.addSeparator();
        ButtonGroup sizes = new ButtonGroup();
        s.small = radioItem("Small icons", false, sizes, s);
        JRadioButtonMenuItem medium = radioItem("Medium icons", true, sizes, s);
        s.large = radioItem("Large icons", false, sizes, s);
        s.view.add(s.small);
        s.view.add(medium);
        s.view.add(s.large);
        s.view.addSeparator();
        JMenu zoom = menu("Zoom", KeyEvent.VK_Z, s);
        for (String level : List.of("50 %", "100 %", "200 %")) {
            zoom.add(item(level, 0, null, null, s));
        }
        s.view.add(zoom);
        bar.add(s.view);

        s.format = menu("Format", KeyEvent.VK_O, s);
        s.html = item("<html><b>Bold</b>, <i>italic</i> and <font color=#C62828>red</font></html>", 0, null, null, s);
        s.format.add(s.html);
        s.wrap = checkItem("Word wrap", true, s);
        s.wrap.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrl | InputEvent.ALT_DOWN_MASK));
        s.format.add(s.wrap);
        JMenuItem disabledIcon = item("Disabled with icon", 0, null, icon(0x5C6BC0, SwingKit.TRIANGLE, 16), s);
        disabledIcon.setEnabled(false);
        s.format.add(disabledIcon);
        bar.add(s.format);

        bar.add(Box.createHorizontalGlue());
        s.help = menu("Help", KeyEvent.VK_H, s);
        s.help.add(item("About", KeyEvent.VK_A, null, null, s));
        bar.add(s.help);
        return bar;
    }

    private static JMenu menu(String text, int mnemonic, State s) {
        JMenu menu = new JMenu(text);
        menu.setMnemonic(mnemonic);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                s.menuEvents.add(text + " selected");
            }

            @Override
            public void menuDeselected(MenuEvent e) {
                s.menuEvents.add(text + " deselected");
            }

            @Override
            public void menuCanceled(MenuEvent e) {
                s.menuEvents.add(text + " canceled");
            }
        });
        return menu;
    }

    private static JMenuItem item(String text, int mnemonic, KeyStroke accelerator, javax.swing.Icon icon, State s) {
        JMenuItem item = new JMenuItem(text, icon);
        if (mnemonic != 0) {
            item.setMnemonic(mnemonic);
        }
        item.setAccelerator(accelerator);
        item.addActionListener(e -> s.actions.add(e.getActionCommand()));
        return item;
    }

    private static JCheckBoxMenuItem checkItem(String text, boolean selected, State s) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, selected);
        item.addActionListener(e -> s.actions.add(text + "=" + item.isSelected()));
        return item;
    }

    private static JRadioButtonMenuItem radioItem(String text, boolean selected, ButtonGroup group, State s) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text, selected);
        group.add(item);
        item.addActionListener(e -> s.actions.add(text + "=" + item.isSelected()));
        return item;
    }

    private static Action action(String name, int mnemonic, int key, int rgb, State s) {
        Action action = new AbstractAction(name, icon(rgb, SwingKit.CIRCLE, 16)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                s.actions.add("action " + name);
            }
        };
        action.putValue(Action.MNEMONIC_KEY, mnemonic);
        action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK));
        action.putValue(Action.SHORT_DESCRIPTION, name + " the selection");
        return action;
    }

    private static JPopupMenu contextMenu(String label, State s) {
        JPopupMenu popup = new JPopupMenu(label);
        popup.add(new JMenuItem("Cut", icon(0xEF5350, SwingKit.CIRCLE, 16)));
        popup.add(new JMenuItem("Copy", icon(0xAB47BC, SwingKit.CIRCLE, 16)));
        popup.add(new JMenuItem("Paste", icon(0x26A69A, SwingKit.CIRCLE, 16)));
        popup.addSeparator();
        popup.add(new JCheckBoxMenuItem("Selected check box", true));
        JMenu more = new JMenu("More");
        more.add(new JMenuItem("Properties"));
        popup.add(more);
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                s.popupEvents.add(label + " visible");
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                s.popupEvents.add(label + " invisible");
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                s.popupEvents.add(label + " canceled");
            }
        });
        return popup;
    }

    private static JPanel factoryContents(String text, int rgb) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(new Color(rgb));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x37474F)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        panel.add(new JLabel(text), BorderLayout.NORTH);
        panel.add(new JLabel("PopupFactory contents", icon(0x37474F, SwingKit.DIAMOND, 14), JLabel.LEFT),
                BorderLayout.CENTER);
        return panel;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        return Edt.rounds(2)
                .thenAccept(v -> popups(s, SwingUtilities.getWindowAncestor(content)))
                .thenCompose(v -> Edt.rounds(2))
                .thenAccept(v -> {
                    s.menuTable.setChecks(menuChecks(s));
                    s.popupTable.setChecks(s.popupChecks);
                })
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(state.extras));
    }

    @Override
    public void dispose(Component content) {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        state = null;
    }

    /**
     * Shows, captures and hides every popup within this single event dispatch (see the class comment).
     */
    private static void popups(State s, Window window) {
        List<Check> checks = s.popupChecks;
        MenuSelectionManager msm = MenuSelectionManager.defaultManager();

        // --- the menu bar path File > Open Recent > notes.md
        s.menuEvents.add("-- open");
        MenuElement[] path = { s.bar, s.file, s.file.getPopupMenu(), s.recent, s.recent.getPopupMenu(), s.recentSecond };
        msm.setSelectedPath(path);
        checks.add(Checks.expect("menu bar path: selected path", "JMenuBar File JPopupMenu Open Recent JPopupMenu notes.md",
                () -> pathNames(msm.getSelectedPath())));
        String menuKind = SwingKit.expectedKind("light", window);
        checks.add(Checks.expect("menu bar path: popups visible, weights", "true true " + menuKind + " " + menuKind,
                () -> s.file.isPopupMenuVisible() + " " + s.recent.isPopupMenuVisible() + " "
                        + SwingKit.popupKind(s.file.getPopupMenu(), window) + " "
                        + SwingKit.popupKind(s.recent.getPopupMenu(), window)));
        checks.add(Checks.expect("menu bar path: File selected, notes.md armed", "true true",
                () -> s.file.isSelected() + " " + s.recentSecond.isArmed()));
        checks.add(Checks.info("menu bar path: popup locations in the menu bar holder (File, Open Recent)",
                () -> SwingKit.point(SwingUtilities.convertPoint(s.file.getPopupMenu(), 0, 0, s.barHolder)) + " "
                        + SwingKit.point(SwingUtilities.convertPoint(s.recent.getPopupMenu(), 0, 0, s.barHolder))));
        capture(s, "menubar-open", () -> SwingKit.composite(s.barHolder,
                List.of(s.file.getPopupMenu(), s.recent.getPopupMenu())));
        msm.clearSelectedPath();
        checks.add(Checks.expect("menu bar path: cleared", "0 false", () -> msm.getSelectedPath().length + " "
                + s.file.isPopupMenuVisible()));
        s.menuEvents.add("-- closed");

        // --- a JPopupMenu : light weight inside the window
        JPopupMenu light = contextMenu("light", s);
        popupMenu(s, window, "light", light, s.anchors.get(0), 0, "light");
        // --- the same with light weight popups disabled : a heavy weight window in JDK 25 (JPopupMenu.getPopup)
        JPopupMenu disabled = contextMenu("disabled", s);
        disabled.setLightWeightPopupEnabled(false);
        popupMenu(s, window, "heavy-disabled", disabled, s.anchors.get(1), 0, "heavy");
        // --- leaving the window : heavy weight window
        JPopupMenu leaving = contextMenu("leaving", s);
        JLabel leavingAnchor = s.anchors.get(5);
        int windowRight = window.getX() + window.getWidth();
        int x = windowRight - leavingAnchor.getLocationOnScreen().x - 60;
        GraphicsConfiguration gc = window.getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        boolean fitsOnScreen = screen.x + screen.width >= windowRight + leaving.getPreferredSize().width;
        popupMenu(s, window, "leaving-window", leaving, leavingAnchor, x, fitsOnScreen ? "heavy" : null);

        // --- PopupFactory : medium weight (an AWT Panel in the layered pane) for contents other than JPopupMenu and
        // JToolTip, heavy weight when forced (protected getPopup(..., true)), light weight for a JToolTip
        factoryPopup(s, window, "medium", s.anchors.get(2), factoryContents("medium weight", 0xE8F5E9), false, "medium");
        factoryPopup(s, window, "heavy-forced", s.anchors.get(3), factoryContents("heavy weight (forced)", 0xFFEBEE),
                true, "heavy");
        JToolTip tip = new JToolTip();
        tip.setTipText("A JToolTip as PopupFactory contents");
        factoryPopup(s, window, "tooltip", s.anchors.get(4), tip, false, "light");

        checks.add(Checks.info("popup menu events", () -> String.join(", ", s.popupEvents)));
        checks.add(Checks.expect("PopupFactory shared instance class", "javax.swing.PopupFactory",
                () -> PopupFactory.getSharedInstance().getClass().getName()));
        checks.add(Checks.expect("JPopupMenu default light weight enabled", true,
                JPopupMenu::getDefaultLightWeightPopupEnabled));
    }

    private static void popupMenu(State s, Window window, String name, JPopupMenu popup, JComponent anchor, int x,
            String expectedKind) {
        List<Check> checks = s.popupChecks;
        popup.show(anchor, x, anchor.getHeight());
        Check kind = Check.info(name + ": weight, window", SwingKit.popupKind(popup, window) + " "
                + SwingKit.windowClass(popup));
        if (expectedKind != null) {
            expectedKind = SwingKit.expectedKind(expectedKind, window);
            String expectedWindow = expectedKind.equals("heavy") ? "javax.swing.Popup$HeavyWeightWindow"
                    : window.getClass().getName();
            kind = Checks.expect(kind.name(), expectedKind + " " + expectedWindow, kind::value);
        }
        checks.add(kind);
        checks.add(Checks.expect(name + ": visible, invoker, light weight enabled", "true true "
                + popup.isLightWeightPopupEnabled(), () -> popup.isVisible() + " " + (popup.getInvoker() == anchor) + " "
                        + popup.isLightWeightPopupEnabled()));
        checks.add(Checks.expect(name + ": selected path", "JPopupMenu",
                () -> pathNames(MenuSelectionManager.defaultManager().getSelectedPath())));
        checks.add(Checks.info(name + ": size", () -> size(popup.getSize())));
        capture(s, "popup-" + name, () -> Snapshots.render(popup));
        popup.setVisible(false);
        checks.add(Checks.expect(name + ": hidden", "false 0", () -> popup.isVisible() + " "
                + MenuSelectionManager.defaultManager().getSelectedPath().length));
    }

    private static void factoryPopup(State s, Window window, String name, JComponent anchor, JComponent contents,
            boolean forceHeavy, String expectedKind) {
        List<Check> checks = s.popupChecks;
        Point p = anchor.getLocationOnScreen();
        Popup popup = new ForcingPopupFactory().popup(anchor, contents, p.x, p.y + anchor.getHeight(), forceHeavy);
        popup.show();
        checks.add(Checks.expect("PopupFactory " + name + ": weight", SwingKit.expectedKind(expectedKind, window),
                () -> SwingKit.popupKind(contents, window)));
        checks.add(Checks.info("PopupFactory " + name + ": container", () -> {
            Component parent = contents.getParent();
            return parent == null ? "none" : parent.getClass().getName();
        }));
        capture(s, "popup-factory-" + name, () -> Snapshots.render(contents));
        popup.hide();
        checks.add(Checks.expect("PopupFactory " + name + ": hidden", false, contents::isShowing));
    }

    private static void capture(State s, String name, java.util.function.Supplier<BufferedImage> renderer) {
        try {
            s.extras.put(name, renderer.get());
        } catch (RuntimeException e) {
            s.popupChecks.add(Check.fail("capture " + name, Checks.describe(e)));
        }
    }

    private static String pathNames(MenuElement[] path) {
        List<String> names = new ArrayList<>();
        for (MenuElement element : path) {
            Component c = element.getComponent();
            names.add(c instanceof JMenuItem item ? item.getText() : c.getClass().getSimpleName());
        }
        return String.join(" ", names);
    }

    /**
     * Exposes the protected {@code getPopup(owner, contents, x, y, isHeavyWeightPopup)} of {@link PopupFactory}.
     */
    private static final class ForcingPopupFactory extends PopupFactory {

        Popup popup(Component owner, Component contents, int x, int y, boolean heavy) {
            return getPopup(owner, contents, x, y, heavy);
        }
    }

    private static List<Check> menuChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JMenuBar bar = s.bar;
        checks.add(Checks.expect("menu bar: component count, menu count (glue included)", "6 6",
                () -> bar.getComponentCount() + " "
                + bar.getMenuCount()));
        checks.add(Checks.expect("menu bar: getMenu(4) is the glue", "null", () -> String.valueOf(bar.getMenu(4))));
        checks.add(Checks.info("menu bar: Help menu bounds", () -> SwingKit.rect(s.help.getBounds())));
        checks.add(Checks.expect("File: item count, menu components", "10 10",
                () -> s.file.getItemCount() + " " + s.file.getMenuComponentCount()));
        checks.add(Checks.expect("File: accelerators", "ctrl pressed N, ctrl pressed O, ctrl pressed S, "
                + "shift ctrl pressed S, ctrl pressed P, ctrl pressed W", () -> {
                    List<String> strokes = new ArrayList<>();
                    for (int i = 0; i < s.file.getItemCount(); i++) {
                        JMenuItem item = s.file.getItem(i);
                        if (item != null && item.getAccelerator() != null) {
                            strokes.add(item.getAccelerator().toString());
                        }
                    }
                    return String.join(", ", strokes);
                }));
        checks.add(Checks.expect("Edit: action based items (text, mnemonic, accelerator, tool tip)",
                "Copy 67 ctrl pressed C Copy the selection", () -> {
                    JMenuItem item = s.edit.getItem(4);
                    return item.getText() + " " + item.getMnemonic() + " " + item.getAccelerator() + " "
                            + item.getToolTipText();
                }));
        checks.add(Checks.expect("Save As: mnemonic, displayed mnemonic index", "65 5",
                () -> s.saveAs.getMnemonic() + " " + s.saveAs.getDisplayedMnemonicIndex()));
        checks.add(Checks.expect("accelerator texts (modifiers, keys)", "Ctrl+Shift F3 N -", () -> InputEvent
                .getModifiersExText(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK) + " "
                + KeyEvent.getKeyText(KeyEvent.VK_F3) + " " + KeyEvent.getKeyText(KeyEvent.VK_N) + " "
                + UIManager.getString("MenuItem.acceleratorDelimiter")));
        checks.add(Checks.expect("KeyStroke.getKeyStroke(\"shift ctrl S\") equals the Save As accelerator", true,
                () -> KeyStroke.getKeyStroke("shift ctrl S").equals(s.saveAs.getAccelerator())));
        checks.add(Checks.expect("UI delegates (bar, menu, item, check, radio, popup)",
                "MetalMenuBarUI BasicMenuUI BasicMenuItemUI BasicCheckBoxMenuItemUI BasicRadioButtonMenuItemUI "
                        + "BasicPopupMenuUI",
                () -> String.join(" ", simple(ui(bar)), simple(ui(s.file)), simple(ui(s.newItem)),
                        simple(ui(s.toolbar)), simple(ui(s.small)), simple(ui(s.file.getPopupMenu())))));
        checks.add(Checks.expect("popup separator UI", "javax.swing.plaf.metal.MetalPopupMenuSeparatorUI",
                () -> ui((JComponent) s.file.getMenuComponent(3))));
        checks.add(Checks.expect("actions (loadActionMap) : item doClick, menu selectMenu, bar takeFocus",
                "true true true", () -> (s.newItem.getActionMap().get("doClick") != null) + " "
                        + (s.file.getActionMap().get("selectMenu") != null) + " "
                        + (bar.getActionMap().get("takeFocus") != null)));
        checks.add(Checks.expect("bindings : bar F10, menu alt F (in focused window)", "takeFocus selectMenu",
                () -> bar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("F10")) + " "
                        + s.file.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("alt F"))));
        checks.add(Checks.expect("HTML item: html property set", true,
                () -> s.html.getClientProperty("html") != null));

        s.actions.clear();
        checks.add(Checks.expect("doClick New, disabled Print, Copy action", "New, action Copy", () -> {
            s.newItem.doClick(0);
            s.print.doClick(0);
            s.edit.getItem(4).doClick(0);
            return String.join(", ", s.actions);
        }));
        s.actions.clear();
        checks.add(Checks.expect("check box items toggled by doClick", "Toolbar=false, Status Bar=true", () -> {
            s.toolbar.doClick(0);
            s.statusBar.doClick(0);
            return String.join(", ", s.actions);
        }));
        s.actions.clear();
        checks.add(Checks.expect("radio items : doClick Large", "Large icons=true, selected: false true", () -> {
            s.large.doClick(0);
            return String.join(", ", s.actions) + ", selected: " + s.small.isSelected() + " " + s.large.isSelected();
        }));
        s.actions.clear();
        checks.add(Checks.expect("synthetic ctrl+N, ctrl+shift+S, F3 and ctrl+C key presses", "New, Save As..., "
                + "Find Next, action Copy", () -> {
                    SwingKit.key(s.barHolder, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_N, KeyEvent.CHAR_UNDEFINED);
                    SwingKit.key(s.barHolder, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_S,
                            KeyEvent.CHAR_UNDEFINED);
                    SwingKit.key(s.barHolder, 0, KeyEvent.VK_F3, KeyEvent.CHAR_UNDEFINED);
                    SwingKit.key(s.barHolder, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_C, KeyEvent.CHAR_UNDEFINED);
                    return String.join(", ", s.actions);
                }));
        s.actions.clear();
        checks.add(Checks.expect("disabled Copy action disables its item", "false false", () -> {
            s.copy.setEnabled(false);
            String result = s.copy.isEnabled() + " " + s.edit.getItem(4).isEnabled();
            s.copy.setEnabled(true);
            return result;
        }));
        checks.add(Checks.info("menu events", () -> String.join(", ", s.menuEvents)));
        checks.add(Checks.info("File popup preferred size", () -> size(s.file.getPopupMenu().getPreferredSize())));
        return checks;
    }

    private static String simple(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
