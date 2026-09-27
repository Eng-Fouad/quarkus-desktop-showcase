package io.quarkiverse.desktop.showcase.core;

import java.awt.Component;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;

/**
 * Snapshot mode : renders every page to a PNG file and writes a report (environment, checks and errors), then exits.
 * The output of a JVM run and of a native run are compared by tools/Compare.java.
 * <p>
 * Enabled by {@code -Dshowcase.snapshot.dir=<dir>} (tools/Snapshot.java sets it). Files: {@code _main-window.png},
 * {@code <page-id>.png}, {@code <page-id>--<extra>.png}, {@code report.json}.
 */
@Singleton
public class SnapshotRunner {

    private static final Logger LOG = Logger.getLogger(SnapshotRunner.class);

    @ConfigProperty(name = "showcase.snapshot.dir")
    Optional<String> dir;

    @ConfigProperty(name = "showcase.snapshot.exit", defaultValue = "true")
    boolean exit;

    @ConfigProperty(name = "showcase.snapshot.settle-millis", defaultValue = "400")
    int settleMillis;

    @ConfigProperty(name = "showcase.snapshot.ready-timeout-seconds", defaultValue = "30")
    int readyTimeoutSeconds;

    @ConfigProperty(name = "showcase.snapshot.scale", defaultValue = "1")
    double scale;

    /** Also capture the window from the screen with Robot ({@code <page-id>--screen.png}). */
    @ConfigProperty(name = "showcase.snapshot.screen", defaultValue = "false")
    boolean screen;

    @ConfigProperty(name = "showcase.snapshot.focus-lock-timeout-seconds", defaultValue = "600")
    int focusLockTimeoutSeconds;

    private final Map<String, List<String>> uncaught = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile String currentPageId = "_startup";
    /** The environment of the intermediate reports. */
    private Map<String, Object> environment;

    void onStartup(@Observes StartupEvent event) {
        if (enabled()) {
            ShowcaseMode.enableSnapshot();
            // uncaught exceptions of every thread, the EDT included (EventDispatchThread reports them to this handler)
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                uncaught.computeIfAbsent(currentPageId, id -> Collections.synchronizedList(new ArrayList<>()))
                        .add(thread.getName() + ": " + Checks.describe(error));
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    error.printStackTrace();
                }
            });
        }
    }

    public boolean enabled() {
        return dir.isPresent();
    }

    /**
     * Runs the snapshots of {@code pages} (on the EDT), then exits.
     */
    public void run(MainWindow window, List<FeaturePage> pages) {
        Path out = Path.of(dir.orElseThrow()).toAbsolutePath();
        LOG.infof("Snapshot run of %d pages into %s", pages.size(), out);
        InputFilter.install();

        List<Map<String, Object>> results = new ArrayList<>();
        CompletionStage<Void> chain = Edt.rounds(5)
                .thenCompose(v -> Edt.delay(settleMillis))
                .thenCompose(v -> Edt.rounds(2))
                .thenCompose(v -> releaseFocus(window))
                .thenRunAsync(() -> writeImage(Snapshots.render(window.rootContent(), scale), out.resolve("_main-window.png")),
                        Edt.EDT);
        for (FeaturePage page : pages) {
            chain = chain.thenComposeAsync(v -> capture(window, page, out), Edt.EDT).thenAccept(result -> {
                results.add(result);
                // written after every page too : a run that crashes (e.g. a native executable) still has a report
                writeReport(out, results, false);
            });
        }
        chain.whenCompleteAsync((v, error) -> {
            if (error != null) {
                LOG.error("Snapshot run aborted", error);
            }
            currentPageId = "_end";
            try {
                window.clear();
            } catch (Throwable t) {
                LOG.error("Failed to dispose the last page", t);
            }
            writeReport(out, results, true);
            long failed = results.stream().filter(r -> !((List<?>) r.get("errors")).isEmpty()).count();
            LOG.infof("Snapshot run finished : %d pages, %d with errors", results.size(), failed);
            if (exit) {
                for (Window w : Window.getWindows()) {
                    w.dispose();
                }
                Quarkus.asyncExit();
            }
        }, Edt.EDT);
    }

    /**
     * The showcase failed to start (e.g. the main window cannot be created) : writes a report with the error, so that the
     * run is still compared.
     */
    public void startFailed(Throwable error) {
        if (enabled()) {
            uncaught.computeIfAbsent("_startup", id -> Collections.synchronizedList(new ArrayList<>()))
                    .add("start: " + Checks.describe(error));
            writeReport(Path.of(dir.orElseThrow()).toAbsolutePath(), List.of(), true);
        }
    }

    private CompletionStage<Void> releaseFocus(MainWindow window) {
        window.releaseFocus();
        return Edt.until(window::focusReleased, 500, "focus released")
                .handle((v, error) -> null)
                .thenCompose(v -> Edt.rounds(2));
    }

    private CompletionStage<Map<String, Object>> capture(MainWindow window, FeaturePage page, Path out) {
        currentPageId = page.id();
        long start = System.nanoTime();
        List<String> errors = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", page.id());
        result.put("title", page.title());
        result.put("category", page.category());
        result.put("runtimeDependent", page.runtimeDependent());
        result.put("needsFocus", page.needsFocus());

        CompletionStage<FocusLock> locking = page.needsFocus() ? FocusLock.acquire(focusLockTimeoutSeconds * 1000L)
                : CompletableFuture.completedFuture(null);
        return locking.thenComposeAsync(lock -> {
            CompletionStage<Void> focus = CompletableFuture.completedFuture(null);
            if (lock != null) {
                result.put("focusLock", lock.acquired() ? "acquired" : "not acquired");
                ShowcaseMode.realInput(true);
                focus = bringToFront(window, result);
            }
            return focus.thenComposeAsync(v -> capturePage(window, page, out, result, errors, start), Edt.EDT)
                    .whenCompleteAsync((r, error) -> {
                        if (lock != null) {
                            ShowcaseMode.realInput(false);
                            lock.close();
                        }
                    }, Edt.EDT);
        }, Edt.EDT);
    }

    /**
     * Brings the main window to the front with {@link Focus#acquire} (the focus of this process and the foreground of
     * the desktop, a few attempts) : the attempts are recorded in the page result ({@code focusAttempts}, 0 when the
     * window could not get the focus).
     */
    private CompletionStage<Void> bringToFront(MainWindow window, Map<String, Object> result) {
        Window w = window.window();
        w.setAutoRequestFocus(true);
        return Focus.acquire(w)
                .handle((attempts, error) -> {
                    int n = error == null ? attempts : 0;
                    result.put("focusAttempts", n);
                    LOG.infof("Page window %s", n > 0 ? "focused (attempt " + n + ")"
                            : "not focused (foreground : " + Foreground.describe() + ")");
                    return null;
                });
    }

    private CompletionStage<Map<String, Object>> capturePage(MainWindow window, FeaturePage page, Path out,
            Map<String, Object> result, List<String> errors, long start) {
        // the last page started before a crash is the one without an "ok" / "error(s)" line
        LOG.infof("%-40s started", page.id());
        window.select(page);
        Component content = window.currentContent();
        Throwable buildError = window.currentError();
        if (buildError != null) {
            errors.add("build: " + Checks.describe(buildError));
        }

        CompletionStage<?> ready;
        try {
            ready = buildError == null ? page.ready(content) : CompletableFuture.completedFuture(null);
        } catch (Throwable t) {
            ready = CompletableFuture.failedFuture(t);
        }

        return Edt.timeout(ready, readyTimeoutSeconds * 1000L, "ready")
                .handle((v, error) -> {
                    if (error != null) {
                        errors.add("ready: " + Checks.describe(unwrap(error)));
                    }
                    return null;
                })
                .thenCompose(v -> Edt.rounds(3))
                .thenCompose(v -> Edt.delay(settleMillis))
                .thenCompose(v -> Edt.rounds(2))
                // a page may have requested the focus while getting ready
                .thenCompose(v -> releaseFocus(window))
                .thenComposeAsync(v -> {
                    BufferedImage image = Snapshots.render(window.pageFrame(), scale);
                    String file = page.id() + ".png";
                    writeImage(image, out.resolve(file));
                    result.put("snapshot", file);
                    result.put("width", image.getWidth());
                    result.put("height", image.getHeight());

                    CompletionStage<Map<String, BufferedImage>> extras;
                    try {
                        extras = buildError == null ? page.extraSnapshots(content)
                                : CompletableFuture.completedFuture(Map.of());
                    } catch (Throwable t) {
                        extras = CompletableFuture.failedFuture(t);
                    }
                    return Edt.timeout(extras, readyTimeoutSeconds * 1000L, "extra snapshots");
                }, Edt.EDT)
                .handleAsync((extras, error) -> {
                    Map<String, BufferedImage> images = new TreeMap<>();
                    if (error != null) {
                        errors.add("extraSnapshots: " + Checks.describe(unwrap(error)));
                    } else if (extras != null) {
                        images.putAll(extras);
                    }
                    if (screen) {
                        try {
                            images.put("screen", Snapshots.screen(window.rootContent()));
                        } catch (Throwable t) {
                            errors.add("screen: " + Checks.describe(t));
                        }
                    }
                    List<String> extraFiles = new ArrayList<>();
                    images.forEach((name, image) -> {
                        String file = page.id() + "--" + name + ".png";
                        writeImage(image, out.resolve(file));
                        extraFiles.add(file);
                    });
                    Collections.sort(extraFiles);
                    result.put("extras", extraFiles);
                    List<Map<String, Object>> checks = new ArrayList<>();
                    for (Check check : content == null ? List.<Check> of() : Checks.collect(content)) {
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("name", check.name());
                        c.put("value", check.value());
                        c.put("ok", check.ok());
                        checks.add(c);
                        if (Boolean.FALSE.equals(check.ok())) {
                            errors.add("check failed: " + check.name() + " = " + check.value());
                        }
                    }
                    List<String> pageUncaught = uncaught.getOrDefault(page.id(), List.of());
                    synchronized (pageUncaught) {
                        pageUncaught.forEach(e -> errors.add("uncaught: " + e));
                    }
                    result.put("checks", checks);
                    result.put("errors", errors);
                    result.put("millis", (System.nanoTime() - start) / 1_000_000);
                    LOG.infof("%-40s %s", page.id(), errors.isEmpty() ? "ok" : errors.size() + " error(s)");
                    return result;
                }, Edt.EDT);
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static void writeImage(BufferedImage image, Path file) {
        try {
            PngWriter.write(image, file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes report.json. The environment is described once for the intermediate reports (written after every page) and
     * again for the final one ({@code complete}), at the end of the run.
     */
    private void writeReport(Path out, List<Map<String, Object>> pages, boolean complete) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (complete || environment == null) {
            Map<String, Object> env = new LinkedHashMap<>();
            try {
                env.putAll(Environment.describe());
            } catch (Throwable t) {
                env.put("environmentError", Checks.describe(t));
            }
            environment = env;
        }
        report.putAll(environment);
        report.put("pages", pages);
        Map<String, Object> other = new LinkedHashMap<>(uncaught);
        pages.forEach(p -> other.remove((String) p.get("id")));
        report.put("uncaughtOutsidePages", other);
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("report.json"), Json.write(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Failed to write report", e);
        }
    }
}
