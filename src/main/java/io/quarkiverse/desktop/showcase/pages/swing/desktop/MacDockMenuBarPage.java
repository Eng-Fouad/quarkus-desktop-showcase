package io.quarkiverse.desktop.showcase.pages.swing.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.WindowConstants;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.desktop.DesktopSupport;
import io.quarkiverse.desktop.showcase.pages.desktop.MacDesktop;

/**
 * The Dock and the menu bar of macOS (macOS only) : the application features of {@code java.awt.Taskbar} (badge, icon,
 * Dock menu, progress, attention request : the setters with {@code -Dshowcase.sideEffects=true} only, restored when the
 * page is left), a {@code JMenuBar} in the macOS menu bar ({@code apple.laf.useScreenMenuBar=true} : the Aqua
 * {@code ScreenMenuBar}, whose native menus call back into {@code com.apple.laf.ScreenMenu}), an AWT {@code MenuBar}
 * (always in the macOS menu bar) and the default menu bar of {@code java.awt.Desktop}. The menu bars show in the macOS
 * menu bar when their frame is active : check it on screen. On other operating systems the page only states that it is
 * not available.
 */
@Singleton
public class MacDockMenuBarPage implements FeaturePage {

    private static final String SCREEN_MENU_BAR = "apple.laf.useScreenMenuBar";

    // per build state
    private final List<Frame> frames = new ArrayList<>();
    private final List<Runnable> restore = new ArrayList<>();

    @Override
    public String id() {
        return "desktop-mac-dock-menubar";
    }

    @Override
    public String title() {
        return "macOS Dock and menu bar";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 220;
    }

    @Override
    public Component build() throws Exception {
        if (!Platforms.isMac()) {
            return MacDesktop.notAvailable("macOS Dock and menu bar",
                    "The Dock (badge, icon, menu, progress) and the macOS menu bar (screen menu bar of Swing) only exist "
                            + "on macOS.",
                    List.of());
        }
        List<Check> dock = new ArrayList<>();
        dock.add(Checks.expect("Taskbar.isTaskbarSupported()", true, Taskbar::isTaskbarSupported));
        Taskbar taskbar = Taskbar.getTaskbar();
        for (Taskbar.Feature feature : List.of(Taskbar.Feature.ICON_BADGE_TEXT, Taskbar.Feature.ICON_BADGE_NUMBER,
                Taskbar.Feature.ICON_IMAGE, Taskbar.Feature.MENU, Taskbar.Feature.PROGRESS_VALUE,
                Taskbar.Feature.USER_ATTENTION)) {
            dock.add(Checks.expect("isSupported " + feature, true, () -> taskbar.isSupported(feature)));
        }
        // _AppDockIconHandler looks the image creator up by reflection (CImage#getCreator)
        dock.add(Checks.info("getIconImage() size", () -> {
            Image icon = taskbar.getIconImage();
            return icon == null ? "null" : icon.getWidth(null) + "x" + icon.getHeight(null);
        }));
        dock.add(Checks.info("getMenu()", () -> taskbar.getMenu() == null ? "null" : "a menu"));
        if (DesktopSupport.sideEffects()) {
            Image previousIcon = taskbar.getIconImage();
            PopupMenu previousMenu = taskbar.getMenu();
            restore.add(() -> taskbar.setIconBadge(null));
            restore.add(() -> taskbar.setProgressValue(-1));
            restore.add(() -> taskbar.setIconImage(previousIcon));
            restore.add(() -> taskbar.setMenu(previousMenu));
            dock.add(call("setIconBadge(\"7\")", () -> taskbar.setIconBadge("7")));
            dock.add(call("setIconImage(generated 128x128)", () -> taskbar.setIconImage(dockIcon())));
            dock.add(Checks.info("getIconImage() size after setIconImage", () -> {
                Image icon = taskbar.getIconImage();
                return icon == null ? "null" : icon.getWidth(null) + "x" + icon.getHeight(null);
            }));
            PopupMenu menu = new PopupMenu("Showcase");
            menu.add(new MenuItem("Showcase item 1"));
            menu.add(new MenuItem("Showcase item 2"));
            dock.add(call("setMenu(PopupMenu)", () -> taskbar.setMenu(menu)));
            dock.add(call("setProgressValue(42)", () -> taskbar.setProgressValue(42)));
            dock.add(call("requestUserAttention(true, false)", () -> taskbar.requestUserAttention(true, false)));
        } else {
            dock.add(Check.info("setIconBadge, setIconImage, setMenu, setProgressValue, requestUserAttention",
                    "supported, not called (-Dshowcase.sideEffects=true)"));
        }

        List<Check> menus = new ArrayList<>();
        String previous = System.getProperty(SCREEN_MENU_BAR);
        restore.add(() -> {
            if (previous == null) {
                System.clearProperty(SCREEN_MENU_BAR);
            } else {
                System.setProperty(SCREEN_MENU_BAR, previous);
            }
        });
        // read when the menu bar UI is installed
        System.setProperty(SCREEN_MENU_BAR, "true");
        JFrame swingFrame = new JFrame("Screen menu bar");
        swingFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        swingFrame.setAutoRequestFocus(!ShowcaseMode.snapshot());
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Showcase");
        file.add(new JMenuItem("Screen menu item"));
        file.addSeparator();
        file.add(new JMenuItem("Another item"));
        bar.add(file);
        bar.add(new JMenu("Second"));
        swingFrame.setJMenuBar(bar);
        swingFrame.add(new JLabel("  The JMenuBar of this frame is in the macOS menu bar when it is active"),
                BorderLayout.CENTER);
        swingFrame.setBounds(1480, 100, 380, 160);
        frames.add(swingFrame);
        swingFrame.setVisible(true);
        menus.add(Checks.expect("JMenuBar UI", "AquaMenuBarUI", () -> bar.getUI().getClass().getSimpleName()));
        menus.add(Checks.info("JMenuBar height in the frame (0 : in the macOS menu bar)", bar::getHeight));

        Frame awtFrame = new Frame("AWT menu bar");
        awtFrame.setAutoRequestFocus(!ShowcaseMode.snapshot());
        MenuBar awtBar = new MenuBar();
        Menu awtMenu = new Menu("AWT menu");
        awtMenu.add(new MenuItem("AWT item"));
        awtBar.add(awtMenu);
        awtFrame.setMenuBar(awtBar);
        awtFrame.setBounds(1480, 300, 380, 120);
        frames.add(awtFrame);
        awtFrame.setVisible(true);
        menus.add(Checks.expect("AWT MenuBar peer created", true, () -> awtFrame.getMenuBar() == awtBar
                && awtBar.getMenuCount() == 1));
        menus.add(Checks.expect("Desktop APP_MENU_BAR supported", true,
                () -> Desktop.getDesktop().isSupported(Desktop.Action.APP_MENU_BAR)));
        if (DesktopSupport.sideEffects()) {
            JMenuBar defaultBar = new JMenuBar();
            defaultBar.add(new JMenu("Default"));
            restore.add(() -> Desktop.getDesktop().setDefaultMenuBar(null));
            menus.add(call("Desktop.setDefaultMenuBar", () -> Desktop.getDesktop().setDefaultMenuBar(defaultBar)));
        } else {
            menus.add(Check.info("Desktop.setDefaultMenuBar", "supported, not called (-Dshowcase.sideEffects=true)"));
        }

        return Ui.column(14, Ui.heading("macOS Dock and menu bar"),
                Ui.text("The Dock tile of the application, and two frames next to the main window : a Swing JFrame whose "
                        + "JMenuBar is in the macOS menu bar (apple.laf.useScreenMenuBar=true while the page is shown), "
                        + "and an AWT Frame with a MenuBar (always in the macOS menu bar). Activate them to see their "
                        + "menus.", 1000),
                ChecksView.table("Dock (java.awt.Taskbar)", dock, 380, ChecksView.WIDTH),
                ChecksView.table("Menu bars", menus, 380, ChecksView.WIDTH));
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        return frames.isEmpty() ? CompletableFuture.completedFuture(null) : Edt.rounds(3);
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        Map<String, BufferedImage> extras = new LinkedHashMap<>();
        if (!frames.isEmpty() && frames.getFirst() instanceof JFrame swingFrame) {
            extras.put("screen-menu-bar-frame", Snapshots.render(swingFrame.getContentPane()));
        }
        return CompletableFuture.completedFuture(extras);
    }

    @Override
    public void dispose(Component content) {
        frames.forEach(Frame::dispose);
        frames.clear();
        for (Runnable r : restore) {
            try {
                r.run();
            } catch (RuntimeException e) {
                // best effort
            }
        }
        restore.clear();
    }

    private static Check call(String name, Runnable action) {
        Callable<Object> call = () -> {
            action.run();
            return "called";
        };
        return Checks.run(name, call);
    }

    /**
     * A generated Dock icon : a green rounded square with a white Q (deterministic).
     */
    private static BufferedImage dockIcon() {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x3A7B5C));
            g.fillRoundRect(8, 8, 112, 112, 28, 28);
            g.setColor(Color.WHITE);
            g.fillOval(34, 30, 60, 60);
            g.setColor(new Color(0x3A7B5C));
            g.fillOval(46, 42, 36, 36);
            g.setColor(Color.WHITE);
            g.fillRect(70, 78, 12, 26);
        } finally {
            g.dispose();
        }
        return image;
    }
}
