package io.quarkiverse.desktop.showcase.core;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Event dispatch thread (EDT) helpers shared by pages and the snapshot runner : asynchronous waits that complete on the
 * EDT, and classpath resources.
 * <p>
 * Every stage returned here completes on the EDT, so that {@code thenApply}/{@code thenCompose}/{@code thenAccept}
 * continuations (non async variants) run on the EDT too. They work in the AWT-only variant (no {@code javax.swing}).
 */
public final class Edt {

    /**
     * Runs asynchronous stages on the EDT ({@code thenRunAsync(..., Edt.EDT)}).
     */
    public static final Executor EDT = EventQueue::invokeLater;

    private static final long POLL_MILLIS = 20;
    private static final long STABLE_POLL_MILLIS = 50;

    // created at run time (never in a static initializer : Quarkus initializes this class at build time)
    private static ScheduledExecutorService scheduler;
    private static Path tempDir;

    private Edt() {
    }

    public static boolean isEdt() {
        return EventQueue.isDispatchThread();
    }

    /**
     * Completes (on the EDT) after {@code count} chained {@code invokeLater} rounds : pending layout, repaint and
     * events posted before are processed first.
     */
    public static CompletionStage<Void> rounds(int count) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        round(Math.max(1, count), done);
        return done;
    }

    private static void round(int remaining, CompletableFuture<Void> done) {
        EventQueue.invokeLater(() -> {
            if (remaining <= 1) {
                done.complete(null);
            } else {
                round(remaining - 1, done);
            }
        });
    }

    /**
     * Completes (on the EDT) after {@code millis}.
     */
    public static CompletionStage<Void> delay(long millis) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        scheduler().schedule(() -> EventQueue.invokeLater(() -> done.complete(null)), millis, TimeUnit.MILLISECONDS);
        return done;
    }

    /**
     * Completes (on the EDT) when {@code stage} completes, or exceptionally with a {@link TimeoutException}
     * ({@code "Timeout waiting for <what>"}) after {@code millis}.
     */
    public static <T> CompletionStage<T> timeout(CompletionStage<T> stage, long millis, String what) {
        CompletableFuture<T> result = new CompletableFuture<>();
        ScheduledFuture<?> timer = scheduler().schedule(() -> EventQueue.invokeLater(
                () -> result.completeExceptionally(new TimeoutException("Timeout waiting for " + what))),
                millis, TimeUnit.MILLISECONDS);
        stage.whenComplete((value, error) -> EventQueue.invokeLater(() -> {
            timer.cancel(false);
            if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(value);
            }
        }));
        return result;
    }

    /**
     * Completes (on the EDT) once {@code condition} is true, evaluated on the EDT every 20 ms, or exceptionally with a
     * {@link TimeoutException} after {@code timeoutMillis}.
     */
    public static CompletionStage<Void> until(BooleanSupplier condition, long timeoutMillis, String what) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        EventQueue.invokeLater(() -> poll(condition, deadline, what, done));
        return done;
    }

    private static void poll(BooleanSupplier condition, long deadline, String what, CompletableFuture<Void> done) {
        try {
            if (condition.getAsBoolean()) {
                done.complete(null);
            } else if (System.nanoTime() - deadline > 0) {
                done.completeExceptionally(new TimeoutException("Timeout waiting for " + what));
            } else {
                scheduler().schedule(() -> EventQueue.invokeLater(() -> poll(condition, deadline, what, done)),
                        POLL_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable t) {
            done.completeExceptionally(t);
        }
    }

    /**
     * Completes (on the EDT) once {@code component} renders the same pixels 3 times in a row (rendered with
     * {@link Snapshots#render} every 50 ms), or after {@code timeoutMillis} (never exceptionally : the timeout only
     * ends the wait).
     */
    public static CompletionStage<Void> stable(Component component, long timeoutMillis) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        EventQueue.invokeLater(() -> stable(component, deadline, null, 0, done));
        return done;
    }

    private static void stable(Component component, long deadline, int[] previous, int same,
            CompletableFuture<Void> done) {
        int[] pixels;
        try {
            BufferedImage image = Snapshots.render(component, 1);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        } catch (Throwable t) {
            done.complete(null);
            return;
        }
        int identical = previous != null && Arrays.equals(previous, pixels) ? same + 1 : 1;
        if (identical >= 3 || System.nanoTime() - deadline > 0) {
            done.complete(null);
        } else {
            scheduler().schedule(() -> EventQueue.invokeLater(() -> stable(component, deadline, pixels, identical, done)),
                    STABLE_POLL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Runs {@code action} later on the EDT.
     */
    public static <T> CompletionStage<T> supply(Callable<T> action) {
        CompletableFuture<T> done = new CompletableFuture<>();
        EventQueue.invokeLater(() -> {
            try {
                done.complete(action.call());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }

    /**
     * Runs {@code action} on a background thread (never block the EDT : clipboard or print service lookups, file I/O,
     * waiting for a window...) ; the returned stage completes on the EDT.
     */
    public static <T> CompletionStage<T> background(Callable<T> action) {
        CompletableFuture<T> done = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                T value = action.call();
                EventQueue.invokeLater(() -> done.complete(value));
            } catch (Throwable t) {
                EventQueue.invokeLater(() -> done.completeExceptionally(t));
            }
        }, "showcase-background");
        thread.setDaemon(true);
        thread.start();
        return done;
    }

    /**
     * {@code true} when one of the windows of this application is the focused window and no other process owns the
     * foreground of the desktop. Check it right before each Robot key press : keyboard input must never go to another
     * application.
     * <p>
     * Java alone is not enough on Windows : a window of a background process can be the focused window of its process
     * (Windows activated it within the process but refused the foreground) while the keyboard input goes to another
     * application. The foreground window is therefore also asked to Windows ({@link Foreground}).
     */
    public static boolean ownsFocus() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() != null
                && !Boolean.FALSE.equals(Foreground.thisProcess());
    }

    static synchronized ScheduledExecutorService scheduler() {
        if (scheduler == null) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
                Thread thread = new Thread(runnable, "showcase-scheduler");
                thread.setDaemon(true);
                return thread;
            });
            executor.setRemoveOnCancelPolicy(true);
            scheduler = executor;
        }
        return scheduler;
    }

    // --------------------------------------------------------------------------------------------------- resources

    /**
     * Classpath resource, {@code path} being absolute (e.g. {@code /showcase/java2d/texture.png}). Its URL scheme is
     * {@code jar:} or {@code file:} on the JVM and {@code resource:} in a native executable : never show or check a
     * resource URL.
     */
    public static URL resource(String path) {
        return Objects.requireNonNull(Edt.class.getResource(path), () -> "Resource not found: " + path);
    }

    public static InputStream resourceStream(String path) throws IOException {
        return resource(path).openStream();
    }

    public static byte[] resourceBytes(String path) {
        try (InputStream in = resource(path).openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String resourceText(String path) {
        return new String(resourceBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Copies a classpath resource to a file of {@link #tempDir()} (for APIs that need a file).
     */
    public static Path resourceToTempFile(String path) {
        try (InputStream in = resource(path).openStream()) {
            Path file = tempDir().resolve(path.substring(path.lastIndexOf('/') + 1));
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A temporary directory of this process (deleted on exit) : several showcase processes may run at the same time.
     * Its path differs from run to run : never show or check it.
     */
    public static synchronized Path tempDir() {
        if (tempDir == null) {
            try {
                Path dir = Files.createTempDirectory("quarkus-desktop-showcase-");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(dir), "showcase-temp-cleanup"));
                tempDir = dir;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return tempDir;
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException | RuntimeException e) {
            // best effort
        }
    }
}
