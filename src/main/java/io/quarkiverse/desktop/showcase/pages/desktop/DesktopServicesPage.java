package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.desktop.AppForegroundEvent;
import java.awt.desktop.AppForegroundListener;
import java.awt.desktop.QuitStrategy;
import java.awt.desktop.SystemSleepEvent;
import java.awt.desktop.SystemSleepListener;
import java.awt.desktop.UserSessionEvent;
import java.awt.desktop.UserSessionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Desktop integration : the {@code Desktop} actions and application handlers (support matrix, argument validation, the
 * safe calls), the {@code Taskbar} features (progress and badge on the showcase window), the {@code SystemTray} with a
 * {@code TrayIcon} added and removed (no balloon message), cursors (the 14 predefined ones, the system custom cursors
 * of {@code cursors.properties}, a custom cursor from an image) and the {@code SplashScreen} API.
 * <p>
 * AWT only. Nothing visible happens outside the showcase windows unless {@code -Dshowcase.sideEffects=true} (then
 * {@code Desktop.browse/open/edit/mail} and {@code TrayIcon.displayMessage} are called ; {@code Desktop.print} never :
 * the default print service is a real printer).
 */
@Singleton
public class DesktopServicesPage implements FeaturePage {

    private static final int HALF = 494;
    private static final String[] PREDEFINED_CURSORS = { "Default Cursor", "Crosshair Cursor", "Text Cursor",
            "Wait Cursor", "Southwest Resize Cursor", "Southeast Resize Cursor", "Northwest Resize Cursor",
            "Northeast Resize Cursor", "North Resize Cursor", "South Resize Cursor", "West Resize Cursor",
            "East Resize Cursor", "Hand Cursor", "Move Cursor" };
    private static final String[] SYSTEM_CURSORS = { "CopyDrop.32x32", "MoveDrop.32x32", "LinkDrop.32x32",
            "CopyNoDrop.32x32", "MoveNoDrop.32x32", "LinkNoDrop.32x32", "Invalid.32x32" };

    // per build state
    private ChecksView desktopView;
    private ChecksView taskbarView;
    private ChecksView trayView;
    private Window taskbarWindow;
    private TrayIcon trayIcon;

    @Override
    public String id() {
        return "desktop-services";
    }

    @Override
    public String title() {
        return "Desktop, Taskbar, SystemTray and cursors";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Component build() throws AWTException {
        List<Check> cursorChecks = new ArrayList<>();
        List<Component> swatches = new ArrayList<>();
        cursors(cursorChecks, swatches);
        desktopView = ChecksView.table("Desktop", List.of(Check.info("state", "pending")), 250, HALF);
        taskbarView = ChecksView.table("Taskbar (on the showcase window)", List.of(Check.info("state", "pending")),
                250, HALF);
        trayView = ChecksView.table("SystemTray and TrayIcon", List.of(Check.info("state", "pending")), 250, HALF);

        List<Check> splash = new ArrayList<>();
        // created by the java launcher only (-splash: or the SplashScreen-Image manifest entry) : a native executable
        // has no launcher, and the snapshot runs pass no -splash option
        splash.add(Checks.expect("SplashScreen.getSplashScreen()", "null", () -> {
            SplashScreen screen = SplashScreen.getSplashScreen();
            return screen == null ? "null" : "visible " + screen.isVisible() + ", " + Checks.bounds(screen.getBounds());
        }));

        Component row1 = Ui.row(6, swatches.subList(0, 11).toArray(Component[]::new));
        Component row2 = Ui.row(6, swatches.subList(11, swatches.size()).toArray(Component[]::new));
        return Ui.column(14,
                Ui.text("Desktop, Taskbar and SystemTray support and safe calls ; the showcase window shows a taskbar "
                        + "progress and badge while this page is displayed, a tray icon is added and removed. Hover the "
                        + "swatches to see the cursors (cursors do not appear in the snapshots).", 1000),
                Ui.row(24,
                        Ui.column(4, Ui.image(scaled(trayImage(), 3)), Ui.caption("tray icon (x3)")),
                        Ui.column(4, Ui.image(scaled(badgeImage(), 3)), Ui.caption("taskbar badge (x3)")),
                        Ui.column(4, Ui.image(scaled(cursorImage(), 2)), Ui.caption("custom cursor (x2)"))),
                Ui.column(6, Ui.title("Cursors (predefined, system custom, custom)"), row1, row2),
                Ui.row(12, desktopView, Ui.column(14, taskbarView, trayView)),
                Ui.row(12, ChecksView.table("Cursors", cursorChecks, 250, HALF),
                        ChecksView.table("SplashScreen", splash, 250, HALF)));
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView desktop = desktopView;
        ChecksView taskbar = taskbarView;
        ChecksView tray = trayView;
        Window window = DesktopSupport.windowOf(content);
        taskbarWindow = window;
        // Desktop and Taskbar run COM calls on the shell folder thread (Windows) : off the EDT
        return Edt.background(() -> desktopChecks())
                .thenAccept(desktop::setChecks)
                .thenCompose(v -> Edt.background(() -> taskbarChecks(window)))
                .thenAccept(taskbar::setChecks)
                .thenCompose(v -> Edt.supply(this::trayChecks))
                .thenAccept(tray::setChecks);
    }

    @Override
    public void dispose(Component content) {
        Window window = taskbarWindow;
        if (window != null && Taskbar.isTaskbarSupported()) {
            Taskbar taskbar = Taskbar.getTaskbar();
            try {
                if (taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
                    taskbar.setWindowProgressState(window, Taskbar.State.OFF);
                }
                if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW)) {
                    taskbar.setWindowIconBadge(window, null);
                }
            } catch (RuntimeException e) {
                // best effort
            }
        }
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        trayIcon = null;
        taskbarWindow = null;
        desktopView = null;
        taskbarView = null;
        trayView = null;
    }

    // --------------------------------------------------------------------------------------------------- Desktop

    private static List<Check> desktopChecks() {
        List<Check> checks = new ArrayList<>();
        boolean supported = Desktop.isDesktopSupported();
        checks.add(Check.info("Desktop.isDesktopSupported()", supported));
        if (!supported) {
            checks.add(DesktopSupport.expectThrows("Desktop.getDesktop()", UnsupportedOperationException.class,
                    Desktop::getDesktop));
            return checks;
        }
        Desktop desktop = Desktop.getDesktop();
        List<String> yes = new ArrayList<>();
        List<String> no = new ArrayList<>();
        for (Desktop.Action action : Desktop.Action.values()) {
            (desktop.isSupported(action) ? yes : no).add(action.name());
        }
        checks.add(Check.info("supported actions", String.join(" ", yes)));
        checks.add(Check.info("unsupported actions", String.join(" ", no)));

        // argument validation happens before the native call : nothing is opened, edited, printed or mailed
        File missing = new File(Edt.tempDir().toFile(), "missing-desktop-file.txt");
        checks.add(Checks.expect("the test file does not exist", false, missing::exists));
        if (!missing.exists()) {
            checks.add(expectThrowsIf(desktop, Desktop.Action.OPEN, "open(missing file)", IllegalArgumentException.class,
                    () -> {
                        desktop.open(missing);
                        return "opened";
                    }));
            checks.add(expectThrowsIf(desktop, Desktop.Action.EDIT, "edit(missing file)", IllegalArgumentException.class,
                    () -> {
                        desktop.edit(missing);
                        return "edited";
                    }));
            checks.add(expectThrowsIf(desktop, Desktop.Action.PRINT, "print(missing file)",
                    IllegalArgumentException.class, () -> {
                        desktop.print(missing);
                        return "printed";
                    }));
            checks.add(expectThrowsIf(desktop, Desktop.Action.MOVE_TO_TRASH, "moveToTrash(missing file)",
                    IllegalArgumentException.class, () -> desktop.moveToTrash(missing)));
            checks.add(expectThrowsIf(desktop, Desktop.Action.BROWSE_FILE_DIR, "browseFileDirectory(missing file)",
                    IllegalArgumentException.class, () -> {
                        desktop.browseFileDirectory(missing);
                        return "browsed";
                    }));
        }
        checks.add(expectThrowsIf(desktop, Desktop.Action.MAIL, "mail(URI that is not mailto:)",
                IllegalArgumentException.class, () -> {
                    desktop.mail(URI.create("https://quarkus.io/"));
                    return "mailed";
                }));

        // application handlers (macOS) : UnsupportedOperationException elsewhere ; null restores the default handler
        checks.add(handler(desktop, Desktop.Action.APP_ABOUT, "setAboutHandler(null)", () -> {
            desktop.setAboutHandler(null);
            return "reset";
        }));
        checks.add(handler(desktop, Desktop.Action.APP_PREFERENCES, "setPreferencesHandler(null)", () -> {
            desktop.setPreferencesHandler(null);
            return "reset";
        }));
        checks.add(handler(desktop, Desktop.Action.APP_OPEN_FILE, "setOpenFileHandler(null)", () -> {
            desktop.setOpenFileHandler(null);
            return "reset";
        }));
        checks.add(handler(desktop, Desktop.Action.APP_OPEN_URI, "setOpenURIHandler(null)", () -> {
            desktop.setOpenURIHandler(null);
            return "reset";
        }));
        checks.add(handler(desktop, Desktop.Action.APP_PRINT_FILE, "setPrintFileHandler(null)", () -> {
            desktop.setPrintFileHandler(null);
            return "reset";
        }));
        checks.add(handler(desktop, Desktop.Action.APP_QUIT_HANDLER, "setQuitHandler(null)", () -> {
            desktop.setQuitHandler(null);
            return "reset";
        }));
        checks.add(handler(desktop, Desktop.Action.APP_QUIT_STRATEGY, "setQuitStrategy(NORMAL_EXIT)", () -> {
            desktop.setQuitStrategy(QuitStrategy.NORMAL_EXIT);
            return "set";
        }));
        checks.add(handler(desktop, Desktop.Action.APP_SUDDEN_TERMINATION, "enable/disableSuddenTermination()", () -> {
            desktop.enableSuddenTermination();
            desktop.disableSuddenTermination();
            return "enabled and disabled";
        }));
        checks.add(unsupportedOnly(desktop, Desktop.Action.APP_REQUEST_FOREGROUND, "requestForeground(false)", () -> {
            desktop.requestForeground(false);
            return "requested";
        }));
        checks.add(unsupportedOnly(desktop, Desktop.Action.APP_HELP_VIEWER, "openHelpViewer()", () -> {
            desktop.openHelpViewer();
            return "opened";
        }));
        checks.add(expectThrowsIf(desktop, Desktop.Action.APP_MENU_BAR, "setDefaultMenuBar(null)",
                UnsupportedOperationException.class, () -> {
                    desktop.setDefaultMenuBar(null);
                    return "reset";
                }));
        checks.add(Checks.expect("add/removeAppEventListener (session, sleep, foreground)", "added and removed", () -> {
            Listener listener = new Listener();
            desktop.addAppEventListener(listener);
            desktop.removeAppEventListener(listener);
            return "added and removed";
        }));

        // actions with visible effects : only on request
        if (DesktopSupport.sideEffect(DesktopSupport.BROWSE)) {
            checks.add(Checks.run("browse(https://quarkus.io/)", () -> {
                desktop.browse(URI.create("https://quarkus.io/"));
                return "called";
            }));
            checks.add(Checks.run("mail(mailto:)", () -> {
                desktop.mail(URI.create("mailto:showcase@example.org?subject=Quarkus%20Desktop"));
                return "called";
            }));
        } else {
            checks.add(Check.info("browse / open / edit / mail", "not called (-Dshowcase.sideEffects=true)"));
        }
        checks.add(Check.info("print", "never called (the default print service is a real printer)"));
        return checks;
    }

    /**
     * {@code action} run when {@code desktopAction} is supported (expected to throw {@code supportedError}, or to
     * succeed if it is {@code null}), expected to throw {@code UnsupportedOperationException} otherwise.
     */
    private static Check expectThrowsIf(Desktop desktop, Desktop.Action desktopAction, String name,
            Class<? extends Throwable> supportedError, Callable<?> action) {
        if (!desktop.isSupported(desktopAction)) {
            return DesktopSupport.expectThrows(name, UnsupportedOperationException.class, action);
        }
        return supportedError == null ? Checks.run(name, action)
                : DesktopSupport.expectThrows(name, supportedError, action);
    }

    /**
     * {@code action} expected to throw {@code UnsupportedOperationException} when {@code desktopAction} is not
     * supported, and not called when it is (it has a visible effect).
     */
    private static Check unsupportedOnly(Desktop desktop, Desktop.Action desktopAction, String name,
            Callable<?> action) {
        return desktop.isSupported(desktopAction) ? Check.info(name, "supported, not called")
                : DesktopSupport.expectThrows(name, UnsupportedOperationException.class, action);
    }

    private static Check handler(Desktop desktop, Desktop.Action desktopAction, String name, Callable<?> action) {
        return expectThrowsIf(desktop, desktopAction, name, null, action);
    }

    /** A listener of the system events (never notified during the page : no sleep, no session switch). */
    private static final class Listener implements UserSessionListener, SystemSleepListener, AppForegroundListener {

        @Override
        public void userSessionDeactivated(UserSessionEvent e) {
        }

        @Override
        public void userSessionActivated(UserSessionEvent e) {
        }

        @Override
        public void systemAboutToSleep(SystemSleepEvent e) {
        }

        @Override
        public void systemAwoke(SystemSleepEvent e) {
        }

        @Override
        public void appRaisedToForeground(AppForegroundEvent e) {
        }

        @Override
        public void appMovedToBackground(AppForegroundEvent e) {
        }
    }

    // --------------------------------------------------------------------------------------------------- Taskbar

    private static List<Check> taskbarChecks(Window window) {
        List<Check> checks = new ArrayList<>();
        boolean supported = Taskbar.isTaskbarSupported();
        checks.add(Check.info("Taskbar.isTaskbarSupported()", supported));
        if (!supported) {
            checks.add(DesktopSupport.expectThrows("Taskbar.getTaskbar()", UnsupportedOperationException.class,
                    Taskbar::getTaskbar));
            return checks;
        }
        Taskbar taskbar = Taskbar.getTaskbar();
        List<String> yes = new ArrayList<>();
        for (Taskbar.Feature feature : Taskbar.Feature.values()) {
            if (taskbar.isSupported(feature)) {
                yes.add(feature.name());
            }
        }
        checks.add(Check.info("supported features", String.join(" ", yes)));

        // the window features, on the showcase window (reset when the page is left)
        checks.add(feature(taskbar, Taskbar.Feature.PROGRESS_STATE_WINDOW, "setWindowProgressState(NORMAL)", () -> {
            taskbar.setWindowProgressState(window, Taskbar.State.NORMAL);
            return "set";
        }));
        checks.add(feature(taskbar, Taskbar.Feature.PROGRESS_VALUE_WINDOW, "setWindowProgressValue(60)", () -> {
            taskbar.setWindowProgressValue(window, 60);
            return "set";
        }));
        checks.add(feature(taskbar, Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW, "setWindowIconBadge(image)", () -> {
            taskbar.setWindowIconBadge(window, badgeImage());
            return "set";
        }));
        if (!taskbar.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
            checks.add(DesktopSupport.expectThrows("requestWindowUserAttention", UnsupportedOperationException.class,
                    () -> {
                        taskbar.requestWindowUserAttention(window);
                        return "requested";
                    }));
        } else if (DesktopSupport.sideEffect(DesktopSupport.ATTENTION)) {
            checks.add(Checks.run("requestWindowUserAttention", () -> {
                taskbar.requestWindowUserAttention(window);
                return "requested";
            }));
        } else {
            checks.add(Check.info("requestWindowUserAttention", "supported, not called (-Dshowcase.sideEffects=true)"));
        }
        checks.add(Checks.expect("Taskbar.State values", "OFF NORMAL PAUSED INDETERMINATE ERROR",
                () -> String.join(" ", Arrays.stream(Taskbar.State.values()).map(Enum::name).toList())));

        // the application features (macOS dock, Linux Unity launcher) : getters called, setters only on request
        checks.add(feature(taskbar, Taskbar.Feature.ICON_IMAGE, "getIconImage()",
                () -> taskbar.getIconImage() == null ? "null" : "an image"));
        checks.add(feature(taskbar, Taskbar.Feature.MENU, "getMenu()",
                () -> taskbar.getMenu() == null ? "null" : "a menu"));
        applicationSetter(checks, taskbar, Taskbar.Feature.ICON_BADGE_NUMBER, "setIconBadge(\"3\")",
                () -> taskbar.setIconBadge("3"));
        applicationSetter(checks, taskbar, Taskbar.Feature.PROGRESS_VALUE, "setProgressValue(50)",
                () -> taskbar.setProgressValue(50));
        applicationSetter(checks, taskbar, Taskbar.Feature.USER_ATTENTION, "requestUserAttention(true, false)",
                () -> taskbar.requestUserAttention(true, false));
        return checks;
    }

    private static Check feature(Taskbar taskbar, Taskbar.Feature feature, String name, Callable<?> action) {
        return taskbar.isSupported(feature) ? Checks.run(name, action)
                : DesktopSupport.expectThrows(name, UnsupportedOperationException.class, action);
    }

    private static void applicationSetter(List<Check> checks, Taskbar taskbar, Taskbar.Feature feature, String name,
            Runnable action) {
        Callable<Object> call = () -> {
            action.run();
            return "called";
        };
        if (!taskbar.isSupported(feature)) {
            checks.add(DesktopSupport.expectThrows(name, UnsupportedOperationException.class, call));
        } else if (DesktopSupport.sideEffect(DesktopSupport.TASKBAR)) {
            checks.add(Checks.run(name, call));
        } else {
            checks.add(Check.info(name, "supported, not called (-Dshowcase.sideEffects=true)"));
        }
    }

    // ------------------------------------------------------------------------------------------------ SystemTray

    private List<Check> trayChecks() {
        List<Check> checks = new ArrayList<>();
        boolean supported = SystemTray.isSupported();
        checks.add(Check.info("SystemTray.isSupported()", supported));
        checks.add(Checks.expect("TrayIcon.MessageType values", "ERROR WARNING INFO NONE",
                () -> String.join(" ", Arrays.stream(TrayIcon.MessageType.values()).map(Enum::name).toList())));
        if (!supported) {
            checks.add(DesktopSupport.expectThrows("SystemTray.getSystemTray()", UnsupportedOperationException.class,
                    SystemTray::getSystemTray));
            return checks;
        }
        SystemTray tray = SystemTray.getSystemTray();
        checks.add(Checks.info("getTrayIconSize()", () -> tray.getTrayIconSize().width + "x"
                + tray.getTrayIconSize().height));
        AtomicInteger changes = new AtomicInteger();
        PropertyChangeListener listener = e -> changes.incrementAndGet();
        tray.addPropertyChangeListener("trayIcons", listener);
        try {
            PopupMenu popup = new PopupMenu("Showcase");
            popup.add(new MenuItem("Show the showcase"));
            popup.addSeparator();
            popup.add(new MenuItem("Quit"));
            TrayIcon icon = new TrayIcon(trayImage(), "Quarkus Desktop Showcase", popup);
            icon.setImageAutoSize(true);
            icon.setActionCommand("showcase-tray");
            icon.addActionListener(e -> {
            });
            checks.add(Checks.expect("TrayIcon properties", "Quarkus Desktop Showcase true showcase-tray 3",
                    () -> icon.getToolTip() + " " + icon.isImageAutoSize() + " " + icon.getActionCommand() + " "
                            + icon.getPopupMenu().getItemCount()));
            checks.add(Checks.expect("SystemTray.add(TrayIcon)", 1, () -> {
                tray.add(icon);
                return tray.getTrayIcons().length;
            }));
            checks.add(Checks.info("TrayIcon.getSize()", () -> icon.getSize().width + "x" + icon.getSize().height));
            checks.add(DesktopSupport.expectThrows("adding the same TrayIcon again", IllegalArgumentException.class,
                    () -> {
                        tray.add(icon);
                        return "added";
                    }));
            checks.add(Checks.run("TrayIcon.setToolTip / setImage while shown", () -> {
                icon.setToolTip("Quarkus Desktop Showcase (updated)");
                icon.setImage(trayImage());
                return "updated";
            }));
            if (DesktopSupport.sideEffect(DesktopSupport.TRAY_BALLOON)) {
                checks.add(Checks.run("TrayIcon.displayMessage", () -> {
                    icon.displayMessage("Quarkus Desktop Showcase", "A balloon message", TrayIcon.MessageType.INFO);
                    return "displayed";
                }));
            } else {
                checks.add(Check.info("TrayIcon.displayMessage", "not called (-Dshowcase.sideEffects=true)"));
            }
            checks.add(Checks.expect("SystemTray.remove(TrayIcon)", 0, () -> {
                tray.remove(icon);
                return tray.getTrayIcons().length;
            }));
            checks.add(Checks.expect("trayIcons property changes", 2, changes::get));
            if (!ShowcaseMode.snapshot()) {
                // interactive mode : the icon stays in the tray while the page is displayed
                tray.add(icon);
                trayIcon = icon;
            }
        } catch (AWTException e) {
            checks.add(Check.fail("SystemTray", Checks.describe(e)));
        } finally {
            tray.removePropertyChangeListener("trayIcons", listener);
        }
        return checks;
    }

    // --------------------------------------------------------------------------------------------------- Cursors

    private static void cursors(List<Check> checks, List<Component> swatches) throws AWTException {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        List<String> names = new ArrayList<>();
        for (int type = Cursor.DEFAULT_CURSOR; type <= Cursor.MOVE_CURSOR; type++) {
            Cursor cursor = Cursor.getPredefinedCursor(type);
            names.add(cursor.getName());
            swatches.add(swatch(cursor, cursor.getName().replace(" Cursor", "").replace(" Resize", "")));
        }
        checks.add(Checks.expect("predefined cursors (names)", String.join(", ", PREDEFINED_CURSORS),
                () -> String.join(", ", names)));
        checks.add(Checks.expect("predefined cursors (types)", "0 1 2 3 4 5 6 7 8 9 10 11 12 13", () -> {
            List<String> types = new ArrayList<>();
            for (int type = 0; type <= 13; type++) {
                types.add(String.valueOf(Cursor.getPredefinedCursor(type).getType()));
            }
            return String.join(" ", types);
        }));
        checks.add(DesktopSupport.expectThrows("getPredefinedCursor(14)", IllegalArgumentException.class,
                () -> Cursor.getPredefinedCursor(14)));
        checks.add(Checks.expect("Cursor.getDefaultCursor().toString()", "java.awt.Cursor[Default Cursor]",
                () -> Cursor.getDefaultCursor().toString()));

        // system custom cursors : cursors.properties and GIF images, resources of java.desktop
        List<String> system = new ArrayList<>();
        for (String name : SYSTEM_CURSORS) {
            try {
                Cursor cursor = Cursor.getSystemCustomCursor(name);
                system.add(cursor == null ? "null" : cursor.getName() + "(" + cursor.getType() + ")");
                if (cursor != null) {
                    swatches.add(swatch(cursor, name.substring(0, name.indexOf('.'))));
                }
            } catch (AWTException | RuntimeException e) {
                system.add(Checks.describe(e));
            }
        }
        checks.add(Checks.expect("system custom cursors", "CopyDrop32x32(-1), MoveDrop32x32(-1), LinkDrop32x32(-1), "
                + "CopyNoDrop32x32(-1), MoveNoDrop32x32(-1), LinkNoDrop32x32(-1), Invalid32x32(-1)",
                () -> String.join(", ", system)));
        checks.add(Checks.expect("getSystemCustomCursor(unknown)", "null",
                () -> String.valueOf(Cursor.getSystemCustomCursor("Unknown.32x32"))));

        // a custom cursor from an image
        Cursor custom = toolkit.createCustomCursor(cursorImage(), new Point(15, 15), "showcase-target");
        swatches.add(swatch(custom, "custom"));
        checks.add(Checks.expect("createCustomCursor(image, hotspot, name)", "showcase-target -1",
                () -> custom.getName() + " " + custom.getType()));
        checks.add(DesktopSupport.expectThrows("createCustomCursor with a negative hotspot",
                IndexOutOfBoundsException.class, () -> toolkit.createCustomCursor(cursorImage(), new Point(-1, 5),
                        "outside")));
        checks.add(Checks.info("getBestCursorSize(16/32/64)", () -> size(toolkit.getBestCursorSize(16, 16)) + " "
                + size(toolkit.getBestCursorSize(32, 32)) + " " + size(toolkit.getBestCursorSize(64, 64))));
        checks.add(Checks.info("getMaximumCursorColors()", toolkit::getMaximumCursorColors));
        checks.add(Checks.expect("Component.setCursor / getCursor", "Hand Cursor showcase-target", () -> {
            Component c = swatches.get(Cursor.HAND_CURSOR);
            return c.getCursor().getName() + " " + swatches.getLast().getCursor().getName();
        }));
    }

    private static String size(Dimension d) {
        return d.width + "x" + d.height;
    }

    private static Component swatch(Cursor cursor, String label) {
        Component swatch = DesktopSupport.swatch(label, 80, 26, 0xF5F7FA, 0x263238);
        swatch.setCursor(cursor);
        return swatch;
    }

    // ---------------------------------------------------------------------------------------------------- images

    private static BufferedImage trayImage() {
        return Snapshots.offscreen(16, 16, g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x4695EB));
            g.fillRoundRect(0, 0, 16, 16, 5, 5);
            g.setColor(Color.WHITE);
            g.fillRect(4, 4, 8, 6);
            g.fillRect(6, 11, 4, 2);
        });
    }

    private static BufferedImage badgeImage() {
        return Snapshots.offscreen(16, 16, g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xD32F2F));
            g.fillOval(0, 0, 16, 16);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
            g.drawString("3", 5, 12);
        });
    }

    private static BufferedImage cursorImage() {
        return Snapshots.offscreen(32, 32, g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xC62828));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(4, 4, 22, 22);
            g.drawLine(15, 0, 15, 30);
            g.drawLine(0, 15, 30, 15);
        });
    }

    private static BufferedImage scaled(Image image, int factor) {
        int w = image.getWidth(null) * factor;
        int h = image.getHeight(null) * factor;
        return Snapshots.offscreen(w, h, (Graphics2D g) -> g.drawImage(image, 0, 0, w, h, null));
    }
}
