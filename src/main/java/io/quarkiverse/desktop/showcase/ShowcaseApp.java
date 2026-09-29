package io.quarkiverse.desktop.showcase;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkiverse.desktop.awt.DesktopStartupEvent;
import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.MacEnvironment;
import io.quarkiverse.desktop.showcase.core.MainWindow;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.SnapshotRunner;
import io.quarkiverse.desktop.showcase.core.UiSetup;
import io.quarkiverse.desktop.showcase.core.UiSelection;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;

/**
 * Page registry and main window : sorts the pages, applies the page filters, opens the main window and starts the
 * snapshot run in snapshot mode.
 * <p>
 * The application has no {@code @QuarkusMain} : quarkus-desktop fires {@link DesktopStartupEvent} on the event dispatch
 * thread once the application started, and the application stops with {@code Quarkus.asyncExit()} : closing the main
 * window, the Quit menu item, or the end of a snapshot run (quarkus-desktop also stops it when its last visible window
 * closes).
 */
@Singleton
public class ShowcaseApp {

    private static final Logger LOG = Logger.getLogger(ShowcaseApp.class);

    @Inject
    @Any
    Instance<FeaturePage> pageBeans;

    @Inject
    @Any
    Instance<MainWindow> windows;

    @Inject
    @Any
    Instance<UiSetup> setups;

    @Inject
    SnapshotRunner snapshots;

    /** Page ids, an entry ending with {@code -} or {@code *} being an id prefix. */
    @ConfigProperty(name = "showcase.pages")
    Optional<List<String>> pageFilter;

    /** Category names or keys (see {@link Categories#KEYS}). */
    @ConfigProperty(name = "showcase.categories")
    Optional<List<String>> categoryFilter;

    /** {@code swing} or {@code awt} : the main window to use (default : the one with the highest priority). */
    @ConfigProperty(name = "showcase.ui")
    Optional<String> ui;

    /**
     * Before the user interface, on the thread that starts the application (off the EDT) : the {@code StartupEvent}
     * observers run before {@link DesktopStartupEvent} is fired.
     */
    void beforeUserInterface(@Observes StartupEvent event) {
        ShowcaseMode.mainThread(Thread.currentThread());
        // macOS, -Dshowcase.robot=true : the Robot permissions, before the user interface starts (off the EDT)
        MacEnvironment.probePermissions();
    }

    /**
     * Opens the main window (on the EDT).
     */
    void start(@Observes DesktopStartupEvent event) {
        try {
            setups.forEach(UiSetup::apply);
            List<FeaturePage> pages = pages();
            MainWindow window = UiSelection.select(windows.stream().toList(), ui.orElse(null));
            LOG.infof("%d pages, %s main window", pages.size(), window.kind());
            window.open(pages);
            if (snapshots.enabled()) {
                // every page is built by the snapshot run (errors and uncaught exceptions are attributed to it)
                snapshots.run(window, pages);
            } else if (!pages.isEmpty()) {
                window.select(pages.getFirst());
            }
        } catch (Throwable t) {
            LOG.error("The showcase failed to start", t);
            snapshots.startFailed(t);
            Quarkus.asyncExit(1);
        }
    }

    List<FeaturePage> pages() {
        List<FeaturePage> pages = pageBeans.stream()
                .sorted(Comparator.comparingInt((FeaturePage p) -> Categories.rank(p.category()))
                        .thenComparingInt(FeaturePage::order)
                        .thenComparing(FeaturePage::id))
                .toList();
        Set<String> ids = new HashSet<>();
        pages.stream().filter(p -> !ids.add(p.id())).forEach(p -> LOG.errorf("Duplicate page id %s", p.id()));
        pages.stream().filter(p -> !Categories.ORDER.contains(p.category()))
                .forEach(p -> LOG.errorf("Unknown category %s of page %s", p.category(), p.id()));
        return pages.stream()
                .filter(page -> pageFilter.map(filters -> filters.stream().anyMatch(f -> matches(f.trim(), page.id())))
                        .orElse(true))
                .filter(page -> categoryFilter
                        .map(filters -> filters.stream().anyMatch(f -> Categories.matches(f, page.category())))
                        .orElse(true))
                .toList();
    }

    private static boolean matches(String filter, String id) {
        if (filter.endsWith("*")) {
            return id.startsWith(filter.substring(0, filter.length() - 1));
        }
        return filter.endsWith("-") ? id.startsWith(filter) : id.equals(filter);
    }
}
