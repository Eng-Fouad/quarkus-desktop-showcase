package io.quarkiverse.desktop.showcase.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

/**
 * A machine-wide lock held while a page with {@link FeaturePage#needsFocus()} runs, so that several showcase
 * processes (JVM and native runs, other checkouts) never fight over the keyboard focus :
 * a {@link FileLock} on {@code ${java.io.tmpdir}/quarkus-desktop-showcase-focus.lock}.
 */
public final class FocusLock implements AutoCloseable {

    public static final String FILE_NAME = "quarkus-desktop-showcase-focus.lock";

    private static final Logger LOG = Logger.getLogger(FocusLock.class);

    private final FileChannel channel;
    private final FileLock lock;
    private final long waitedMillis;

    private FocusLock(FileChannel channel, FileLock lock, long waitedMillis) {
        this.channel = channel;
        this.lock = lock;
        this.waitedMillis = waitedMillis;
    }

    /**
     * Acquires the lock on a background thread (never blocking the EDT) ; the stage completes on the EDT, with a lock
     * that is not {@link #acquired()} after {@code timeoutMillis} or when the lock file cannot be used.
     */
    public static CompletionStage<FocusLock> acquire(long timeoutMillis) {
        return Edt.background(() -> tryAcquire(timeoutMillis));
    }

    private static FocusLock tryAcquire(long timeoutMillis) throws InterruptedException {
        Path file = Path.of(System.getProperty("java.io.tmpdir"), FILE_NAME);
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        FileChannel channel;
        try {
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOG.warnf("Cannot open the focus lock file %s: %s", file, e);
            return new FocusLock(null, null, 0);
        }
        boolean logged = false;
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return new FocusLock(channel, lock, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                }
            } catch (IOException | OverlappingFileLockException e) {
                LOG.warnf("Cannot lock the focus lock file %s: %s", file, e);
                close(channel);
                return new FocusLock(null, null, 0);
            }
            if (System.nanoTime() - deadline > 0) {
                LOG.warnf("Focus lock %s not acquired after %d ms, going on without it", file, timeoutMillis);
                close(channel);
                return new FocusLock(null, null, timeoutMillis);
            }
            if (!logged) {
                LOG.infof("Waiting for the focus lock %s (another showcase process runs a page that needs the focus)",
                        file);
                logged = true;
            }
            Thread.sleep(100);
        }
    }

    public boolean acquired() {
        return lock != null;
    }

    public long waitedMillis() {
        return waitedMillis;
    }

    @Override
    public void close() {
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException e) {
                // released when the channel is closed
            }
        }
        close(channel);
    }

    private static void close(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                // nothing to do
            }
        }
    }
}
