package io.quarkiverse.desktop.showcase.core;

import java.awt.Desktop;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.im.InputContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * The environment of a run : written at the top of report.json and shown by the {@code overview-environment} page.
 * <p>
 * tools/Compare.java compares most keys between two runs (a difference is an {@code ENV DIFF} mismatch : e.g. another
 * Java2D pipeline, a DPI unaware native executable, a missing desktop feature), and only reports the informational ones
 * ({@link #INFO_KEYS} : raw system properties that legitimately differ between a JVM and a native executable).
 * <p>
 * Call it on the EDT.
 */
public final class Environment {

    /**
     * Keys that are expected to differ between the JVM and a native executable : reported, never a mismatch.
     */
    public static final List<String> INFO_KEYS = List.of("runtime", "javaVendorVersion", "javaHome", "dpiaware",
            "uiScale", "javaAwtHeadless", "mainThreadParked", "property.sun.java.launcher",
            // os.name of a native executable on Windows Server 2025 : see overview-native-limits
            "os");

    private Environment() {
    }

    public static Map<String, Object> describe() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("runtime", ShowcaseMode.runtime());
        env.put("javaVersion", System.getProperty("java.version"));
        env.put("javaVendorVersion", String.valueOf(System.getProperty("java.vendor.version")));
        env.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch"));
        env.put("locale", Locale.getDefault().toLanguageTag());
        env.put("ui", ShowcaseMode.ui());
        boolean headless = GraphicsEnvironment.isHeadless();
        env.put("headless", headless);
        env.put("javaAwtHeadless", String.valueOf(System.getProperty("java.awt.headless")));
        env.put("javaHome", String.valueOf(System.getProperty("java.home")));
        // the thread starting the application (StartupEvent) : main with the JVM and in native executables (on macOS the
        // first thread runs the Cocoa event loop, set by quarkus-desktop in native executables, informational)
        env.put("mainThread", ShowcaseMode.mainThread());
        env.put("mainThreadParked", property("io.quarkiverse.desktop.main-thread-parked"));
        // raw values : a native executable gets its defaults from quarkus-desktop (sun.java2d.dpiaware), the tools force
        // sun.java2d.uiScale=1 unless --hidpi
        env.put("dpiaware", property("sun.java2d.dpiaware"));
        env.put("uiScale", property("sun.java2d.uiScale"));
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        env.put("graphicsEnvironment", ge.getClass().getName());
        env.put("pipeline", ShowcaseMode.pipeline());
        if (!headless) {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            env.put("toolkit", toolkit.getClass().getName());
            GraphicsDevice device = ge.getDefaultScreenDevice();
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            env.put("screen", bounds.width + "x" + bounds.height + ", " + ge.getScreenDevices().length + " device(s)");
            // effective DPI handling : a DPI unaware Windows executable gets 1.0 and 96 dpi where the JVM gets 1.5 and 144
            AffineTransform tx = device.getDefaultConfiguration().getDefaultTransform();
            env.put("defaultTransform", Checks.num(tx.getScaleX(), 2) + "x" + Checks.num(tx.getScaleY(), 2));
            env.put("screenResolution", toolkit.getScreenResolution());
            env.put("fontHints", fontHints(toolkit));
            // the keyboard layout changes the rendering (Windows lays native controls and menus out right to left for
            // an Arabic or Hebrew layout) and the characters Robot types : runs are comparable with the same one only
            env.put("inputLocale", inputLocale());
            // the foreground check of Edt.ownsFocus (Windows, Foreign Function and Memory API) : the same in both runs
            env.put("foregroundCheck", Foreground.available() ? "GetForegroundWindow" : "none");
        }
        env.put("fontFamilies", Platforms.installedFamilies().size());
        env.put("lookAndFeel", "none");
        ArcContainer container = Arc.container();
        if (container != null) {
            container.select(EnvironmentProbe.class).forEach(probe -> probe.describe(env));
        }
        if (!headless && Platforms.isMac()) {
            MacEnvironment.describe(env);
        }
        if (!headless) {
            env.put("desktopFeatures", desktopFeatures());
        }
        return env;
    }

    private static String inputLocale() {
        try {
            Locale locale = InputContext.getInstance().getLocale();
            return locale == null ? "none" : locale.toLanguageTag();
        } catch (Throwable t) {
            return "error: " + Checks.describe(t);
        }
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value == null ? "unset" : value;
    }

    private static String fontHints(Toolkit toolkit) {
        Object hints = toolkit.getDesktopProperty("awt.font.desktophints");
        if (!(hints instanceof Map<?, ?> map)) {
            return String.valueOf(hints);
        }
        Map<String, String> sorted = new TreeMap<>();
        map.forEach((k, v) -> sorted.put(String.valueOf(k), String.valueOf(v)));
        return sorted.toString();
    }

    /**
     * Support of the desktop integration features (the analogue of JavaFX's conditional features).
     */
    public static Map<String, Object> desktopFeatures() {
        Map<String, Object> features = new LinkedHashMap<>();
        boolean desktop = Desktop.isDesktopSupported();
        features.put("Desktop", desktop);
        if (desktop) {
            Desktop d = Desktop.getDesktop();
            for (Desktop.Action action : Desktop.Action.values()) {
                features.put("Desktop." + action.name(), d.isSupported(action));
            }
        }
        boolean taskbar = Taskbar.isTaskbarSupported();
        features.put("Taskbar", taskbar);
        if (taskbar) {
            Taskbar t = Taskbar.getTaskbar();
            for (Taskbar.Feature feature : Taskbar.Feature.values()) {
                features.put("Taskbar." + feature.name(), t.isSupported(feature));
            }
        }
        features.put("SystemTray", SystemTray.isSupported());
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        for (GraphicsDevice.WindowTranslucency translucency : GraphicsDevice.WindowTranslucency.values()) {
            features.put("WindowTranslucency." + translucency.name(), device.isWindowTranslucencySupported(translucency));
        }
        features.put("FullScreen", device.isFullScreenSupported());
        features.put("DisplayChange", device.isDisplayChangeSupported());
        return features;
    }
}
