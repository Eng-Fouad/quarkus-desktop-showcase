package io.quarkiverse.desktop.showcase.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

/**
 * User preferences of macOS that change what AWT receives from Robot input, read at run time (never at build time :
 * Quarkus initializes the page classes when it builds a native executable). Read with {@code /usr/bin/defaults}, which
 * asks the preferences daemon (the plist files on disk may lag behind it).
 */
public final class MacPreferences {

    private static final Logger LOG = Logger.getLogger(MacPreferences.class);

    /** Not read yet. */
    private static final String UNREAD = "unread";

    private static volatile Object naturalScrolling = UNREAD;

    private MacPreferences() {
    }

    /**
     * The scroll direction of macOS (System Settings, Mouse / Trackpad, "Natural scrolling") : the global default
     * {@code com.apple.swipescrolldirection}, {@code true} when it is absent (the default of macOS). It gives the
     * direction of the wheel events of {@code java.awt.Robot} : {@code CRobot.mouseWheel} posts a scroll wheel event of
     * {@code wheelAmt} lines (native {@code CGEventCreateScrollWheelEvent}), which macOS turns like the events of a
     * real wheel, and AWT negates the delta of the {@code NSEvent} it receives
     * ({@code CPlatformResponder.dispatchScrollEvent} : "invert the wheelRotation for the peer"). Without natural
     * scrolling, {@code Robot.mouseWheel(n)} gives a rotation of {@code -n} (seen on macOS 27, in the JVM and in a
     * native executable) ; with natural scrolling, the default of macOS, the posted event is inverted like a real
     * wheel's and the rotation is {@code n}, as specified by {@code Robot.mouseWheel}.
     *
     * @return {@code null} on another operating system, or when the preference cannot be read
     */
    public static Boolean naturalScrolling() {
        Object value = naturalScrolling;
        if (value == UNREAD) {
            value = Platforms.isMac() ? readNaturalScrolling() : null;
            naturalScrolling = value;
        }
        return (Boolean) value;
    }

    private static Boolean readNaturalScrolling() {
        try {
            Process process = new ProcessBuilder("/usr/bin/defaults", "read", "-g", "com.apple.swipescrolldirection")
                    .redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("defaults read -g com.apple.swipescrolldirection did not end");
                return null;
            }
            if (process.exitValue() == 0 && (output.equals("0") || output.equals("1"))) {
                return output.equals("1");
            }
            if (output.contains("does not exist") || output.contains("Could not find key")) {
                // never changed : the default of macOS
                return true;
            }
            LOG.warnf("defaults read -g com.apple.swipescrolldirection : exit %d, %s", process.exitValue(), output);
            return null;
        } catch (IOException e) {
            LOG.warnf("defaults read -g com.apple.swipescrolldirection : %s", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
