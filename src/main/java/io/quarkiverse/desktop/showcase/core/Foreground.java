package io.quarkiverse.desktop.showcase.core;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import org.jboss.logging.Logger;

/**
 * Which process owns the foreground window of the desktop, as the operating system sees it (Windows : user32
 * {@code GetForegroundWindow}, {@code WindowFromPoint} and {@code GetWindowThreadProcessId}, called with the Foreign
 * Function and Memory API).
 * <p>
 * Java can report a focused showcase window while another application owns the foreground : Windows refuses to bring
 * the window of a background process to the front (the foreground lock), and may still activate it within its own
 * process. Keyboard input (Robot) then goes to that other application. {@link Edt#ownsFocus()} therefore also asks
 * Windows.
 * <p>
 * Other operating systems : unknown ({@code null}), the Java focus state is trusted. The downcalls are registered for
 * native executables in the showcase's reachability-metadata.json ({@code foreign}), and native access is enabled
 * ({@code Enable-Native-Access} of the JVM run jar, {@code --enable-native-access} of the native build).
 */
public final class Foreground {

    private static final Logger LOG = Logger.getLogger(Foreground.class);

    // created at run time (never in a static initializer : Quarkus initializes this class at build time)
    private static volatile User32 user32;
    private static volatile boolean unavailable;

    private Foreground() {
    }

    private record User32(MethodHandle getForegroundWindow, MethodHandle windowFromPoint,
            MethodHandle getWindowThreadProcessId) {
    }

    /**
     * {@code true} when the operating system tells which process owns the foreground (Windows).
     */
    public static boolean available() {
        return user32() != null;
    }

    /**
     * {@code TRUE} when the foreground window belongs to this process, {@code FALSE} when it belongs to another one (or
     * there is none, e.g. while the activation changes), {@code null} when unknown (not Windows).
     */
    public static Boolean thisProcess() {
        User32 u = user32();
        if (u == null) {
            return null;
        }
        try {
            long hwnd = (long) u.getForegroundWindow().invokeExact();
            return hwnd != 0 && processOf(u, hwnd) == ProcessHandle.current().pid();
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    /**
     * {@code TRUE} when the top level window under the screen point {@code p} (user space coordinates of the screen,
     * as for {@link java.awt.Robot}) belongs to this process, {@code FALSE} when it belongs to another one, {@code null}
     * when unknown (not Windows). A click there cannot reach another application.
     */
    public static Boolean thisProcessAt(Point p) {
        User32 u = user32();
        if (u == null) {
            return null;
        }
        try {
            Point device = toDevice(p);
            // POINT {LONG x; LONG y} passed by value : 8 bytes, passed like a 64-bit integer on Windows x64
            long point = ((long) device.y << 32) | (device.x & 0xFFFFFFFFL);
            long hwnd = (long) u.windowFromPoint().invokeExact(point);
            return hwnd != 0 && processOf(u, hwnd) == ProcessHandle.current().pid();
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    /**
     * A short description of the foreground state for the reports : {@code this process}, {@code another process} or
     * {@code unknown}.
     */
    public static String describe() {
        Boolean ours = thisProcess();
        return ours == null ? "unknown" : ours ? "this process" : "another process";
    }

    private static long processOf(User32 u, long hwnd) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pid = arena.allocate(ValueLayout.JAVA_INT);
            int thread = (int) u.getWindowThreadProcessId().invokeExact(hwnd, pid);
            return thread == 0 ? -1 : Integer.toUnsignedLong(pid.get(ValueLayout.JAVA_INT, 0));
        }
    }

    /**
     * The device pixel of a screen point in user space : the process is DPI aware, Windows works in device pixels.
     */
    private static Point toDevice(Point p) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();
            if (bounds.contains(p)) {
                AffineTransform tx = gc.getDefaultTransform();
                return new Point((int) Math.floor(bounds.x + (p.x - bounds.x) * tx.getScaleX()),
                        (int) Math.floor(bounds.y + (p.y - bounds.y) * tx.getScaleY()));
            }
        }
        return p;
    }

    private static User32 user32() {
        User32 u = user32;
        if (u != null || unavailable) {
            return u;
        }
        synchronized (Foreground.class) {
            if (user32 == null && !unavailable) {
                if (!Platforms.isWindows()) {
                    unavailable = true;
                } else {
                    try {
                        Linker linker = Linker.nativeLinker();
                        SymbolLookup lookup = SymbolLookup.libraryLookup("user32", Arena.global());
                        user32 = new User32(
                                linker.downcallHandle(lookup.find("GetForegroundWindow").orElseThrow(),
                                        FunctionDescriptor.of(ValueLayout.JAVA_LONG)),
                                linker.downcallHandle(lookup.find("WindowFromPoint").orElseThrow(),
                                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)),
                                linker.downcallHandle(lookup.find("GetWindowThreadProcessId").orElseThrow(),
                                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
                                                ValueLayout.ADDRESS)));
                    } catch (Throwable t) {
                        disable(t);
                    }
                }
            }
            return user32;
        }
    }

    private static void disable(Throwable t) {
        if (!unavailable) {
            LOG.warnf("The foreground window cannot be queried, the Java focus state is trusted : %s", Checks.describe(t));
        }
        unavailable = true;
        user32 = null;
    }
}
