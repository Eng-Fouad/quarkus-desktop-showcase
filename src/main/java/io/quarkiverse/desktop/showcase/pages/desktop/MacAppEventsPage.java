package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.desktop.AppForegroundEvent;
import java.awt.desktop.AppForegroundListener;
import java.awt.desktop.AppHiddenEvent;
import java.awt.desktop.AppHiddenListener;
import java.awt.desktop.AppReopenedEvent;
import java.awt.desktop.AppReopenedListener;
import java.awt.desktop.QuitStrategy;
import java.awt.desktop.ScreenSleepEvent;
import java.awt.desktop.ScreenSleepListener;
import java.awt.desktop.SystemEventListener;
import java.awt.desktop.SystemSleepEvent;
import java.awt.desktop.SystemSleepListener;
import java.awt.desktop.UserSessionEvent;
import java.awt.desktop.UserSessionListener;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The application events of macOS (macOS only) : the {@code java.awt.Desktop} handlers (About, Settings, Quit with
 * its strategy, open files, open URIs, print files) and listeners (application reopened, foreground, hidden, system
 * and screen sleep, user session), which the macOS toolkit calls from the native application delegate
 * ({@code com.apple.eawt._AppEventHandler}, JNI call backs). They only work when AWT owns the application (the first
 * thread of the process runs its event loop : quarkus-desktop does it in native executables) : the About handler
 * replacing the standard About panel proves it. In snapshot mode the page only installs them (the log stays empty) ;
 * with {@code -Dshowcase.interactive=true} the page stays and logs the events of the manual checks of the README.
 * Handlers and listeners are removed when the page is left. AWT only.
 */
@Singleton
public class MacAppEventsPage implements FeaturePage {

    private static final List<Desktop.Action> APP_ACTIONS = List.of(Desktop.Action.APP_ABOUT,
            Desktop.Action.APP_PREFERENCES, Desktop.Action.APP_QUIT_HANDLER, Desktop.Action.APP_QUIT_STRATEGY,
            Desktop.Action.APP_OPEN_FILE, Desktop.Action.APP_OPEN_URI, Desktop.Action.APP_PRINT_FILE,
            Desktop.Action.APP_EVENT_FOREGROUND, Desktop.Action.APP_EVENT_HIDDEN, Desktop.Action.APP_EVENT_REOPENED,
            Desktop.Action.APP_EVENT_SCREEN_SLEEP, Desktop.Action.APP_EVENT_SYSTEM_SLEEP,
            Desktop.Action.APP_EVENT_USER_SESSION, Desktop.Action.APP_SUDDEN_TERMINATION,
            Desktop.Action.APP_REQUEST_FOREGROUND, Desktop.Action.APP_HELP_VIEWER, Desktop.Action.APP_MENU_BAR,
            Desktop.Action.BROWSE_FILE_DIR, Desktop.Action.MOVE_TO_TRASH);

    // per build state
    private final List<String> log = new ArrayList<>();
    private ChecksView events;
    private SystemEventListener listener;

    @Override
    public String id() {
        return "desktop-mac-app-events";
    }

    @Override
    public String title() {
        return "macOS application events";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public Component build() {
        if (!Platforms.isMac()) {
            return MacDesktop.notAvailable("macOS application events",
                    "The About, Settings and Quit menu items, the open file, open URI and print file events and the "
                            + "application events of java.awt.Desktop are delivered by macOS only.",
                    List.of(Checks.expect("Desktop.Action.APP_ABOUT supported", false, () -> Desktop.isDesktopSupported()
                            && Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT))));
        }
        log.clear();
        Desktop desktop = Desktop.getDesktop();
        List<Check> support = new ArrayList<>();
        for (Desktop.Action action : APP_ACTIONS) {
            support.add(Checks.expect("isSupported " + action, true, () -> desktop.isSupported(action)));
        }
        List<Check> installed = new ArrayList<>();
        installed.add(Checks.run("setAboutHandler", () -> {
            desktop.setAboutHandler(e -> event("about"));
            return "installed";
        }));
        installed.add(Checks.run("setPreferencesHandler", () -> {
            desktop.setPreferencesHandler(e -> event("preferences"));
            return "installed";
        }));
        installed.add(Checks.run("setQuitHandler", () -> {
            desktop.setQuitHandler((e, response) -> {
                event("quit requested : cancelled by the page");
                response.cancelQuit();
            });
            return "installed";
        }));
        installed.add(Checks.run("setQuitStrategy(CLOSE_ALL_WINDOWS)", () -> {
            desktop.setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
            return "set";
        }));
        installed.add(Checks.run("setOpenFileHandler", () -> {
            desktop.setOpenFileHandler(e -> event("open files " + e.getFiles().size() + ", search term "
                    + e.getSearchTerm()));
            return "installed";
        }));
        installed.add(Checks.run("setOpenURIHandler", () -> {
            desktop.setOpenURIHandler(e -> event("open URI scheme " + e.getURI().getScheme()));
            return "installed";
        }));
        installed.add(Checks.run("setPrintFileHandler", () -> {
            desktop.setPrintFileHandler(e -> event("print files " + e.getFiles().size()));
            return "installed";
        }));
        listener = new AppListener();
        installed.add(Checks.run("addAppEventListener", () -> {
            desktop.addAppEventListener(listener);
            return "installed";
        }));
        installed.add(Checks.run("enableSuddenTermination, disableSuddenTermination", () -> {
            desktop.enableSuddenTermination();
            desktop.disableSuddenTermination();
            return "done";
        }));
        if (DesktopSupport.sideEffects()) {
            installed.add(Checks.run("requestForeground(true)", () -> {
                desktop.requestForeground(true);
                return "requested";
            }));
        } else {
            installed.add(Check.info("requestForeground(true)", "supported, not called (-Dshowcase.sideEffects=true)"));
        }
        events = ChecksView.table("Event log", List.of(Check.info("events", "none")));
        return Ui.column(14, Ui.heading("macOS application events"),
                Ui.text("The handlers and listeners of java.awt.Desktop are installed while this page is shown : use the "
                        + "application menu (About, Settings, Quit : cancelled), hide and show the application, click its "
                        + "Dock icon, or open a file with the application bundle of tools/mac-app-bundle.sh, and look at "
                        + "the event log (with -Dshowcase.interactive=true). The About item must log an event here, "
                        + "not show the standard About panel.", 1000),
                Ui.row(12, ChecksView.table("Supported actions", support, 330, 494),
                        Ui.column(14, ChecksView.table("Handlers and listeners", installed, 300, 494), events)));
    }

    @Override
    public void dispose(Component content) {
        if (Platforms.isMac() && Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            for (Runnable restore : List.<Runnable> of(() -> desktop.setAboutHandler(null),
                    () -> desktop.setPreferencesHandler(null), () -> desktop.setQuitHandler(null),
                    () -> desktop.setQuitStrategy(QuitStrategy.NORMAL_EXIT), () -> desktop.setOpenFileHandler(null),
                    () -> desktop.setOpenURIHandler(null), () -> desktop.setPrintFileHandler(null),
                    () -> {
                        if (listener != null) {
                            desktop.removeAppEventListener(listener);
                        }
                    })) {
                try {
                    restore.run();
                } catch (RuntimeException e) {
                    // best effort
                }
            }
        }
        listener = null;
        events = null;
        log.clear();
    }

    /**
     * Logs an event (from the thread of the toolkit : shown on the EDT). The log stays empty in snapshot mode, unless
     * the user interacts.
     */
    private void event(String description) {
        EventQueue.invokeLater(() -> {
            log.add(description);
            ChecksView view = events;
            if (view != null) {
                List<Check> checks = new ArrayList<>();
                for (int i = 0; i < log.size(); i++) {
                    checks.add(Check.info("event " + (i + 1), log.get(i)));
                }
                view.setChecks(checks);
            }
        });
    }

    /**
     * The application event listeners (a named class : the lambda of an interface with several methods is not possible).
     */
    private final class AppListener implements AppReopenedListener, AppForegroundListener, AppHiddenListener,
            SystemSleepListener, ScreenSleepListener, UserSessionListener {

        @Override
        public void appReopened(AppReopenedEvent e) {
            event("application reopened");
        }

        @Override
        public void appRaisedToForeground(AppForegroundEvent e) {
            if (!ShowcaseMode.snapshot()) {
                // in snapshot mode the showcase may come to the foreground or not : not logged (determinism)
                event("application raised to the foreground");
            }
        }

        @Override
        public void appMovedToBackground(AppForegroundEvent e) {
            if (!ShowcaseMode.snapshot()) {
                event("application moved to the background");
            }
        }

        @Override
        public void appHidden(AppHiddenEvent e) {
            event("application hidden");
        }

        @Override
        public void appUnhidden(AppHiddenEvent e) {
            event("application unhidden");
        }

        @Override
        public void systemAboutToSleep(SystemSleepEvent e) {
            event("system about to sleep");
        }

        @Override
        public void systemAwoke(SystemSleepEvent e) {
            event("system awoke");
        }

        @Override
        public void screenAboutToSleep(ScreenSleepEvent e) {
            event("screen about to sleep");
        }

        @Override
        public void screenAwoke(ScreenSleepEvent e) {
            event("screen awoke");
        }

        @Override
        public void userSessionDeactivated(UserSessionEvent e) {
            event("user session deactivated (" + e.getReason() + ")");
        }

        @Override
        public void userSessionActivated(UserSessionEvent e) {
            event("user session activated (" + e.getReason() + ")");
        }
    }
}
