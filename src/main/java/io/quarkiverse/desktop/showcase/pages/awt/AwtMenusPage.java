package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuComponent;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.accessibility.Accessible;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Focus;
import io.quarkiverse.desktop.showcase.core.RobotSession;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * AWT menus : a {@link MenuBar} (with a help menu) of {@link Menu}s, {@link MenuItem}s with {@link MenuShortcut}s,
 * {@link CheckboxMenuItem}s, separators, disabled items, a submenu, fonts inherited along the menu tree, and a
 * {@link PopupMenu} attached to a component.
 * <p>
 * The menu model is drawn with Java2D (deterministic, in the page). Checks without input : the menu tree, shortcut texts
 * (resource bundle {@code sun.awt.resources.awt}), shortcut handling by the frame for a synthetic key event, events
 * dispatched to the items, accessible roles. With the focus (a {@code needsFocus} page, snapshot mode only), the native
 * menus are driven with the keyboard (F10, arrows, Enter : the items fire their events from the native peers) and
 * captured with Robot (extras), and the popup menu is shown and closed with Escape.
 */
@Singleton
public class AwtMenusPage implements FeaturePage {

    private static final int FRAME_WIDTH = 560;
    private static final int FRAME_HEIGHT = 380;

    // per build state
    private MenuFrame menuFrame;
    private java.awt.Window backdrop;
    private ChecksView modelView;
    private ChecksView inputView;
    private final Map<String, BufferedImage> captures = new LinkedHashMap<>();

    /**
     * The frame with the menu bar, and the events of its items.
     */
    private static final class MenuFrame {
        Frame frame;
        MenuBar bar;
        Menu file;
        Menu edit;
        Menu view;
        Menu help;
        Menu recent;
        MenuItem newItem;
        MenuItem save;
        MenuItem selectAll;
        CheckboxMenuItem toolbar;
        CheckboxMenuItem statusBar;
        CheckboxMenuItem wordWrap;
        PopupMenu popup;
        CheckboxMenuItem bold;
        Canvas canvas;
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public String id() {
        return "awt-menus";
    }

    @Override
    public String title() {
        return "AWT menus";
    }

    @Override
    public String category() {
        return Categories.AWT;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean needsFocus() {
        // the native menus are driven with the keyboard, the popup menu grabs the input until it is closed
        return true;
    }

    @Override
    public Component build() {
        captures.clear();
        menuFrame = createMenuFrame();
        List<Menu> menus = new ArrayList<>();
        for (int i = 0; i < menuFrame.bar.getMenuCount(); i++) {
            menus.add(menuFrame.bar.getMenu(i));
        }
        BufferedImage model = Snapshots.offscreen(1000, 250, g -> paintModel(g, menus, menuFrame.popup));
        modelView = ChecksView.table("Menu model", modelChecks(menuFrame));
        inputView = ChecksView.table("Shortcuts, events, native menus", List.of(Check.info("state", "pending")));
        return Ui.column(12,
                Ui.text("An AWT MenuBar of a Frame (native menus : owner drawn Win32 menus on Windows, Java painted on "
                        + "Linux) and a PopupMenu. Below, the menu model drawn with Java2D from the menu components "
                        + "(labels, MenuShortcut.toString(), states). The native menus are captured with Robot while they "
                        + "are driven with the keyboard (extra images).", 1000),
                Ui.image(model), modelView, inputView);
    }

    // ------------------------------------------------------------------------------------------------- menu frame

    private static MenuFrame createMenuFrame() {
        MenuFrame m = new MenuFrame();
        m.frame = new Frame("AWT menus");
        m.frame.setName("menuFrame");
        m.bar = new MenuBar();
        m.bar.setName("menuBar");

        m.file = menu("File");
        m.newItem = item(m, "New", new MenuShortcut(KeyEvent.VK_N));
        m.file.add(m.newItem);
        m.file.add(item(m, "Open...", new MenuShortcut(KeyEvent.VK_O)));
        m.save = item(m, "Save", new MenuShortcut(KeyEvent.VK_S));
        m.save.setEnabled(false);
        m.file.add(m.save);
        m.file.addSeparator();
        m.recent = menu("Recent files");
        m.recent.add(item(m, "notes.txt", null));
        m.recent.add(item(m, "report.pdf", null));
        m.file.add(m.recent);
        m.file.addSeparator();
        MenuItem exit = item(m, "Exit", null);
        exit.setActionCommand("exit-command");
        m.file.add(exit);

        m.edit = menu("Edit");
        m.edit.add(item(m, "Undo", new MenuShortcut(KeyEvent.VK_Z)));
        m.edit.addSeparator();
        m.edit.add(item(m, "Cut", new MenuShortcut(KeyEvent.VK_X)));
        m.edit.add(item(m, "Copy", new MenuShortcut(KeyEvent.VK_C)));
        m.edit.add(item(m, "Paste", new MenuShortcut(KeyEvent.VK_V)));
        m.edit.addSeparator();
        m.selectAll = item(m, "Select all", new MenuShortcut(KeyEvent.VK_A, true));
        m.edit.add(m.selectAll);
        m.edit.add(item(m, "Delete", new MenuShortcut(KeyEvent.VK_DELETE)));

        m.view = menu("View");
        m.toolbar = checkItem(m, "Toolbar", true);
        m.statusBar = checkItem(m, "Status bar", false);
        m.wordWrap = checkItem(m, "Word wrap", true);
        m.wordWrap.setEnabled(false);
        m.view.add(m.toolbar);
        m.view.add(m.statusBar);
        m.view.add(m.wordWrap);

        m.help = menu("Help");
        // a font set on a menu is inherited by its items
        m.help.setFont(new Font(Font.SERIF, Font.ITALIC, 14));
        m.help.add(item(m, "About", null));

        m.bar.add(m.file);
        m.bar.add(m.edit);
        m.bar.add(m.view);
        m.bar.setHelpMenu(m.help);
        m.frame.setMenuBar(m.bar);

        m.canvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                g.setColor(new Color(0xECEFF1));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0x546E7A));
                g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
                g.drawString("Canvas with a PopupMenu", 12, getHeight() - 12);
            }
        };
        m.canvas.setName("menuCanvas");
        m.canvas.setPreferredSize(new Dimension(FRAME_WIDTH, FRAME_HEIGHT));
        m.popup = new PopupMenu("Popup");
        m.popup.setName("popup");
        m.popup.add(item(m, "Cut", null));
        m.popup.add(item(m, "Copy", null));
        m.popup.add(item(m, "Paste", null));
        m.popup.addSeparator();
        m.bold = checkItem(m, "Bold", true);
        m.popup.add(m.bold);
        Menu color = menu("Color");
        color.add(item(m, "Red", null));
        color.add(item(m, "Green", null));
        m.popup.add(color);
        m.canvas.add(m.popup);
        m.frame.add(m.canvas);
        return m;
    }

    private static Menu menu(String label) {
        Menu menu = new Menu(label);
        menu.setName("menu " + label);
        return menu;
    }

    private static MenuItem item(MenuFrame m, String label, MenuShortcut shortcut) {
        MenuItem item = shortcut == null ? new MenuItem(label) : new MenuItem(label, shortcut);
        item.setName("item " + label);
        item.addActionListener(e -> m.events.add("action " + e.getActionCommand()));
        return item;
    }

    private static CheckboxMenuItem checkItem(MenuFrame m, String label, boolean state) {
        CheckboxMenuItem item = new CheckboxMenuItem(label, state);
        item.setName("item " + label);
        item.addItemListener(e -> m.events.add("item " + e.getItem() + " "
                + (e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED")));
        return item;
    }

    // ------------------------------------------------------------------------------------------------ model image

    private static void paintModel(Graphics2D g, List<Menu> menus, PopupMenu popup) {
        AwtSupport.textHints(g);
        g.setColor(new Color(0xFAFAFA));
        g.fillRect(0, 0, 1000, 250);
        List<Menu> columns = new ArrayList<>(menus);
        columns.add(popup);
        int x = 0;
        for (Menu menu : columns) {
            paintMenu(g, menu, x, 0, menu instanceof PopupMenu ? "PopupMenu" : "Menu");
            x += 200;
        }
    }

    private static void paintMenu(Graphics2D g, Menu menu, int x, int y, String kind) {
        Font plain = new Font(Font.DIALOG, Font.PLAIN, 12);
        Font bold = plain.deriveFont(Font.BOLD);
        g.setColor(new Color(0x37474F));
        g.fillRect(x + 4, y + 4, 188, 24);
        g.setColor(Color.WHITE);
        g.setFont(bold);
        g.drawString(kind + " \"" + menu.getLabel() + "\"", x + 10, y + 21);
        int rowY = y + 30;
        g.setColor(Color.WHITE);
        g.fillRect(x + 4, rowY, 188, 22 * menu.getItemCount() + 6);
        g.setColor(new Color(0xB0BEC5));
        g.drawRect(x + 4, rowY, 187, 22 * menu.getItemCount() + 5);
        rowY += 3;
        for (int i = 0; i < menu.getItemCount(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getLabel().equals("-")) {
                g.setColor(new Color(0xCFD8DC));
                g.drawLine(x + 10, rowY + 11, x + 186, rowY + 11);
                rowY += 22;
                continue;
            }
            Font font = item.getFont() != null ? item.getFont() : plain;
            g.setFont(font.deriveFont((float) Math.min(font.getSize(), 13)));
            g.setColor(new Color(item.isEnabled() ? 0x212121 : 0x9E9E9E));
            if (item instanceof CheckboxMenuItem check) {
                g.drawRect(x + 10, rowY + 5, 11, 11);
                if (check.getState()) {
                    Path2D.Float tick = new Path2D.Float();
                    tick.moveTo(x + 12, rowY + 11);
                    tick.lineTo(x + 15, rowY + 14);
                    tick.lineTo(x + 20, rowY + 7);
                    g.setStroke(new BasicStroke(1.6f));
                    g.draw(tick);
                    g.setStroke(new BasicStroke(1f));
                }
            }
            g.drawString(item.getLabel(), x + 28, rowY + 15);
            String right = item instanceof Menu ? ">" : item.getShortcut() == null ? "" : item.getShortcut().toString();
            FontMetrics fm = g.getFontMetrics(plain);
            g.setFont(plain);
            g.drawString(right, x + 184 - fm.stringWidth(right), rowY + 15);
            rowY += 22;
        }
    }

    // ------------------------------------------------------------------------------------------------ model checks

    private static List<Check> modelChecks(MenuFrame m) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("MenuBar menus / help menu", "File Edit View Help / Help", () -> {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < m.bar.getMenuCount(); i++) {
                labels.add(m.bar.getMenu(i).getLabel());
            }
            return String.join(" ", labels) + " / " + m.bar.getHelpMenu().getLabel();
        }));
        checks.add(Checks.expect("File items (separators are \"-\")", "New Open... Save - Recent files - Exit",
                () -> labels(m.file)));
        checks.add(Checks.expect("MenuShortcut.toString (New, Select all, Delete)", "Ctrl+N Ctrl+Shift+A Ctrl+Delete",
                () -> m.newItem.getShortcut() + " " + m.selectAll.getShortcut() + " "
                        + m.edit.getItem(m.edit.getItemCount() - 1).getShortcut()));
        checks.add(Checks.expect("MenuShortcut key / usesShiftModifier / equals", "78 false / 65 true / true",
                () -> m.newItem.getShortcut().getKey() + " " + m.newItem.getShortcut().usesShiftModifier() + " / "
                        + m.selectAll.getShortcut().getKey() + " " + m.selectAll.getShortcut().usesShiftModifier() + " / "
                        + m.newItem.getShortcut().equals(new MenuShortcut(KeyEvent.VK_N, false))));
        checks.add(Checks.expect("MenuBar.shortcuts() count / getShortcutMenuItem(Ctrl+S)", "9 / Save", () -> {
            int count = Collections.list(m.bar.shortcuts()).size();
            return count + " / " + m.bar.getShortcutMenuItem(new MenuShortcut(KeyEvent.VK_S)).getLabel();
        }));
        checks.add(Checks.expect("action commands (default = label, explicit)", "New / exit-command",
                () -> m.newItem.getActionCommand() + " / " + m.file.getItem(m.file.getItemCount() - 1).getActionCommand()));
        checks.add(Checks.expect("CheckboxMenuItem states (Toolbar, Status bar, Word wrap)", "true false true, enabled false",
                () -> m.toolbar.getState() + " " + m.statusBar.getState() + " " + m.wordWrap.getState() + ", enabled "
                        + m.wordWrap.isEnabled()));
        checks.add(Checks.expect("CheckboxMenuItem.getSelectedObjects", "[Toolbar] / null",
                () -> List.of(m.toolbar.getSelectedObjects()) + " / "
                        + (m.statusBar.getSelectedObjects() == null ? "null" : "not null")));
        checks.add(Checks.expect("font inherited from the Help menu by its item", "Serif italic 14",
                () -> {
                    Font font = m.help.getItem(0).getFont();
                    return font.getFamily(Locale.ROOT) + (font.isItalic() ? " italic " : " ") + font.getSize();
                }));
        checks.add(Checks.expect("MenuBar font (none set)", "null", () -> String.valueOf(m.bar.getFont())));
        checks.add(Checks.expect("submenu parent / isTearOff", "File / false",
                () -> ((Menu) m.recent.getParent()).getLabel() + " / " + m.recent.isTearOff()));
        checks.add(Checks.expect("PopupMenu parent / items", "menuCanvas / Cut Copy Paste - Bold Color",
                () -> ((Component) m.popup.getParent()).getName() + " / " + labels(m.popup)));
        checks.add(Checks.expect("Menu insert, insertSeparator, remove", "A - New Open... / New Open...", () -> {
            Menu menu = new Menu("scratch");
            menu.add(new MenuItem("New"));
            menu.add(new MenuItem("Open..."));
            menu.insert(new MenuItem("A"), 0);
            menu.insertSeparator(1);
            String inserted = labels(menu);
            menu.remove(0);
            menu.remove(0);
            return inserted + " / " + labels(menu);
        }));
        checks.add(Checks.expect("accessible roles (bar, menu, item, check item, popup)",
                "menu bar, menu, menu item, check box, popup menu",
                () -> String.join(", ", List.<MenuComponent> of(m.bar, m.file, m.newItem, m.toolbar, m.popup).stream()
                        .map(c -> ((Accessible) c).getAccessibleContext().getAccessibleRole().toDisplayString(Locale.US))
                        .toList())));
        checks.add(Checks.expect("ActionEvent dispatched to a MenuItem", "action New", () -> {
            m.newItem.dispatchEvent(new ActionEvent(m.newItem, ActionEvent.ACTION_PERFORMED, "New"));
            return m.events.removeLast();
        }));
        checks.add(Checks.expect("ItemEvent dispatched to a CheckboxMenuItem", "item Status bar SELECTED", () -> {
            m.statusBar.dispatchEvent(new ItemEvent(m.statusBar, ItemEvent.ITEM_STATE_CHANGED, "Status bar",
                    ItemEvent.SELECTED));
            return m.events.removeLast();
        }));
        checks.add(Checks.expect("Toolkit shortcut texts (sun.awt.resources.awt bundle)", "Ctrl Shift Alt",
                () -> Toolkit.getProperty("AWT.control", "?") + " " + Toolkit.getProperty("AWT.shift", "?") + " "
                        + Toolkit.getProperty("AWT.alt", "?")));
        checks.add(Checks.expect("InputEvent.getModifiersExText(CTRL | SHIFT) / KeyEvent.getKeyText(F10, DELETE)",
                "Ctrl+Shift / F10 Delete",
                () -> InputEvent.getModifiersExText(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK) + " / "
                        + KeyEvent.getKeyText(KeyEvent.VK_F10) + " " + KeyEvent.getKeyText(KeyEvent.VK_DELETE)));
        return checks;
    }

    private static String labels(Menu menu) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            labels.add(menu.getItem(i).getLabel());
        }
        return String.join(" ", labels);
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        MenuFrame m = menuFrame;
        ChecksView view = inputView;
        List<Check> checks = Collections.synchronizedList(new ArrayList<>());
        Frame frame = m.frame;
        frame.setAutoRequestFocus(false);
        frame.pack();
        Rectangle area = AwtSupport.secondaryArea(frame.getWidth(), frame.getHeight());
        frame.setLocation(area.x, area.y);
        frame.setVisible(true);

        return Edt.rounds(3)
                .thenCompose(v -> shortcutChecks(m, checks))
                .thenCompose(v -> {
                    Frame bare = new Frame();
                    bare.setBounds(frame.getBounds());
                    bare.addNotify();
                    Insets without = bare.getInsets();
                    bare.dispose();
                    Insets with = frame.getInsets();
                    checks.add(Checks.expect("Frame insets : the menu bar adds to the top inset", "true, same left/right",
                            () -> (with.top > without.top) + ", " + (with.left == without.left && with.right == without.right
                                    ? "same left/right" : "different left/right")));
                    if (!ShowcaseMode.snapshot() || !ShowcaseMode.realInput()) {
                        checks.add(Check.info("native menus with the keyboard", "skipped: snapshot mode only"));
                        return CompletableFuture.completedFuture(null);
                    }
                    return nativeMenus(m, checks);
                })
                .whenComplete((v, error) -> {
                    if (error != null) {
                        checks.add(Check.fail("native menus", Checks.describe(error)));
                    }
                    view.setChecks(checks);
                });
    }

    /**
     * Synthetic key events dispatched to the canvas of the menu frame : the frame passes them to its MenuBar
     * ({@code Frame.postProcessKeyEvent} and {@code MenuBar.handleShortcut}), which posts the ActionEvent / ItemEvent.
     */
    private static CompletionStage<Void> shortcutChecks(MenuFrame m, List<Check> checks) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        m.events.clear();
        pressShortcut(m.canvas, mask, KeyEvent.VK_N);
        pressShortcut(m.canvas, mask, KeyEvent.VK_S);
        pressShortcut(m.canvas, mask | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_A);
        pressShortcut(m.canvas, mask, KeyEvent.VK_DELETE);
        // posted events : processed later
        return Edt.until(() -> m.events.size() >= 3, 3000, "shortcut events").handle((v, error) -> {
            List<String> events = new ArrayList<>(m.events);
            checks.add(Checks.expect("shortcuts Ctrl+N, Ctrl+S (disabled item), Ctrl+Shift+A, Ctrl+Delete",
                    "action New, action Select all, action Delete", () -> String.join(", ", events)));
            m.events.clear();
            return null;
        });
    }

    private static void pressShortcut(Component target, int modifiersEx, int keyCode) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, 0, modifiersEx, keyCode, KeyEvent.CHAR_UNDEFINED));
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, 0, modifiersEx, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    /**
     * The native menus driven with the keyboard (F10 opens the menu bar, arrows move, Enter activates, Escape closes)
     * and captured with Robot, then the popup menu : only once the menu frame is focused ({@link Focus#acquire}).
     */
    private CompletionStage<Void> nativeMenus(MenuFrame m, List<Check> checks) {
        java.awt.Window previouslyFocused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusedWindow();
        Frame frame = m.frame;
        // an opaque window behind the frame : the captures never show the desktop (rounded corners, shadow)
        Rectangle behind = frame.getBounds();
        behind.grow(40, 40);
        backdrop = new AwtSupport.SolidWindow(behind, 0xCFD8DC);
        backdrop.setVisible(true);
        frame.setAlwaysOnTop(true);
        frame.setAutoRequestFocus(true);
        m.canvas.requestFocusInWindow();
        return Focus.acquire(frame)
                .thenCompose(attempts -> {
                    checks.add(Check.attempts("menu frame focused", attempts));
                    if (attempts == 0) {
                        checks.add(Check.info("native menus with the keyboard", "skipped: not focused"));
                        return CompletableFuture.completedFuture(null);
                    }
                    Rectangle bounds = frame.getBounds();
                    Point canvas = m.canvas.getLocationOnScreen();
                    Dimension size = m.canvas.getSize();
                    return Edt.background(() -> drive(m, bounds, canvas, size)).thenAccept(result -> {
                        checks.addAll(result.checks());
                        captures.putAll(result.images());
                    });
                })
                .thenCompose(v -> {
                    // the focus goes back to the page window before the frame is disposed (Windows would otherwise
                    // activate the next window in the z-order, maybe of another application)
                    if (previouslyFocused != null && previouslyFocused.isShowing()) {
                        previouslyFocused.toFront();
                        previouslyFocused.requestFocus();
                    }
                    return Edt.until(() -> previouslyFocused == null || previouslyFocused.isFocused(), 2000,
                            "page window focused").handle((r, e) -> null);
                });
    }

    private record DriveResult(List<Check> checks, Map<String, BufferedImage> images) {
    }

    /** Attempts of each keyboard sequence : another application may take the foreground at any time. */
    private static final int ATTEMPTS = 3;

    /**
     * Runs on a background thread (Robot). Each keyboard sequence is retried (at most {@link #ATTEMPTS} times, the menu
     * frame focused again first) when its effect is missing : a sequence changes no state unless it had its effect.
     */
    private static DriveResult drive(MenuFrame m, Rectangle frame, Point canvas, Dimension size) throws Exception {
        List<Check> checks = new ArrayList<>();
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        // AWT on Windows lays the native menus out right to left when the keyboard layout is Arabic or Hebrew : the arrow
        // keys then move the other way in the menu bar
        boolean rtl = AwtSupport.rightToLeftInput();
        int next = rtl ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        checks.add(Check.info("keyboard layout (menu bar direction on Windows)", AwtSupport.inputLocale()
                + (rtl ? ", right to left" : ", left to right")));
        // the native menu loops consume the keys : Java sees no key event while a menu is open
        try (RobotSession robot = RobotSession.open().nativeKeys(true)) {
            // the pointer away from the menus (a stationary pointer under a new menu highlights an item)
            robot.move(new Point(canvas.x + size.width - 8, canvas.y + size.height - 8));
            robot.delay(300);
            BufferedImage closed = robot.capture(frame);
            images.put("frame", closed);

            // F10, Down : the File menu opens (the capture differs from the closed frame)
            BufferedImage file = null;
            int attempt = 0;
            while (file == null && attempt < ATTEMPTS && focused(robot, m)) {
                attempt++;
                if (robot.key(KeyEvent.VK_F10) && robot.key(KeyEvent.VK_DOWN)) {
                    robot.delay(600);
                    BufferedImage captured = robot.capture(frame);
                    if (!Checks.sha256(captured).equals(Checks.sha256(closed))) {
                        file = captured;
                    }
                }
                if (file == null) {
                    RobotSession.logRetry("awt-menus F10, Down", attempt, "the File menu did not open, skipped "
                            + robot.skipped());
                    closeMenus(robot);
                }
            }
            checks.add(Check.attempts("F10, Down", attempt));
            if (file != null) {
                BufferedImage opened = file;
                images.put("file-menu", opened);
                checks.add(Checks.expect("F10, Down : the File menu opens (capture differs)", true,
                        () -> !Checks.sha256(opened).equals(Checks.sha256(closed))));
                if (robot.key(next) && robot.key(next)) {
                    robot.delay(600);
                    images.put("view-menu", robot.capture(frame));
                }
            } else {
                checks.add(Check.info("F10, Down : the File menu opens", "skipped: not focused"));
            }
            closeMenus(robot);

            // activation from the native menu : the peer calls back into Java (handleAction)
            m.events.clear();
            attempt = 0;
            boolean activated = false;
            while (!activated && attempt < ATTEMPTS && focused(robot, m)) {
                attempt++;
                if (robot.key(KeyEvent.VK_F10) && robot.key(KeyEvent.VK_DOWN) && robot.key(KeyEvent.VK_ENTER)) {
                    activated = waitFor(robot, () -> !m.events.isEmpty());
                }
                if (!activated) {
                    RobotSession.logRetry("awt-menus F10, Down, Enter", attempt, "no action, skipped " + robot.skipped());
                    closeMenus(robot);
                }
            }
            checks.add(Check.attempts("F10, Down, Enter", attempt));
            if (activated) {
                checks.add(Checks.expect("F10, Down, Enter : native activation of File > New", "action New",
                        () -> String.join(", ", m.events)));
            } else {
                checks.add(Check.info("F10, Down, Enter : native activation of File > New", "skipped: not focused"));
            }
            m.events.clear();
            attempt = 0;
            activated = false;
            while (!activated && attempt < ATTEMPTS && focused(robot, m)) {
                attempt++;
                if (robot.key(KeyEvent.VK_F10) && robot.key(next) && robot.key(next) && robot.key(KeyEvent.VK_DOWN)
                        && robot.key(KeyEvent.VK_ENTER)) {
                    // the item toggles once : never sent again once its event arrived
                    activated = waitFor(robot, () -> !m.events.isEmpty());
                }
                if (!activated) {
                    RobotSession.logRetry("awt-menus F10, Right, Right, Down, Enter", attempt, "no item event, skipped "
                            + robot.skipped());
                    closeMenus(robot);
                }
            }
            checks.add(Check.attempts("F10, Right, Right, Down, Enter", attempt));
            if (activated) {
                checks.add(Checks.expect("native activation of View > Toolbar (CheckboxMenuItem)",
                        "item Toolbar DESELECTED, state false", () -> String.join(", ", m.events) + ", state "
                                + m.toolbar.getState()));
            } else {
                checks.add(Check.info("native activation of View > Toolbar (CheckboxMenuItem)", "skipped: not focused"));
            }
            robot.key(KeyEvent.VK_ESCAPE);
            robot.delay(200);

            popup(m, robot, canvas, size, checks, images);
            if (!robot.skipped().isEmpty()) {
                checks.add(Check.info("skipped inputs", "skipped: not focused (" + robot.skipped().size() + ")"));
            }
        }
        return new DriveResult(checks, images);
    }

    /**
     * {@code true} once the menu frame is focused and this process owns the foreground (asked again if needed).
     */
    private static boolean focused(RobotSession robot, MenuFrame m) throws Exception {
        return robot.ensureFocus(m.frame);
    }

    /**
     * Escape twice : closes an open menu, then leaves the menu bar.
     */
    private static void closeMenus(RobotSession robot) {
        robot.key(KeyEvent.VK_ESCAPE);
        robot.key(KeyEvent.VK_ESCAPE);
        robot.delay(300);
    }

    /**
     * {@code PopupMenu.show} : on Windows the peer tracks the menu synchronously (the calling thread waits until the menu
     * is closed), so it is shown from a helper thread and closed with Escape (sent again while the menu stays open, at
     * most {@link #ATTEMPTS} times).
     */
    private static void popup(MenuFrame m, RobotSession robot, Point canvas, Dimension size, List<Check> checks,
            Map<String, BufferedImage> images) throws Exception {
        if (!focused(robot, m)) {
            checks.add(Check.info("PopupMenu.show, Escape", "skipped: not focused"));
            return;
        }
        Thread shower = new Thread(() -> m.popup.show(m.canvas, 30, 30), "showcase-popup-menu");
        shower.setDaemon(true);
        shower.start();
        robot.delay(700);
        images.put("popup-menu", robot.capture(new Rectangle(canvas.x, canvas.y, Math.min(size.width, 360),
                Math.min(size.height, 300))));
        boolean escaped = false;
        int attempt = 0;
        while (shower.isAlive() && attempt < ATTEMPTS) {
            attempt++;
            if (robot.key(KeyEvent.VK_ESCAPE)) {
                escaped = true;
            }
            shower.join(TimeUnit.SECONDS.toMillis(attempt == ATTEMPTS ? 3 : 1));
        }
        checks.add(Check.attempts("PopupMenu Escape", attempt));
        boolean sent = escaped;
        boolean returned = !shower.isAlive();
        checks.add(Checks.expect("PopupMenu.show(canvas, 30, 30), then Escape", "closed",
                () -> !sent ? "skipped: not focused" : returned ? "closed" : "still open"));
    }

    /**
     * Waits (2 s at most) until {@code condition} is true.
     */
    private static boolean waitFor(RobotSession robot, java.util.function.BooleanSupplier condition) {
        for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
            robot.delay(20);
            robot.idle();
        }
        return condition.getAsBoolean();
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(captures));
    }

    @Override
    public void dispose(Component content) {
        if (menuFrame != null) {
            menuFrame.frame.dispose();
        }
        if (backdrop != null) {
            backdrop.dispose();
        }
        backdrop = null;
        menuFrame = null;
        modelView = null;
        inputView = null;
        captures.clear();
    }
}
