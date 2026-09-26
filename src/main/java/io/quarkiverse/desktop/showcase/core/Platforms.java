package io.quarkiverse.desktop.showcase.core;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Operating system specific choices, so that pages run on Windows, Linux and macOS.
 * <p>
 * Snapshots are only compared between runs on the same machine (JVM vs native), so pages may render differently from one
 * operating system to another, but a check must not fail just because a font or a setting of another operating system
 * is missing.
 * <p>
 * Nothing here is computed in a static initializer : Quarkus initializes application classes at build time, the values
 * must be those of the running machine.
 */
public final class Platforms {

    public enum Os {
        MAC,
        WINDOWS,
        LINUX
    }

    /** The logical font families of Java2D, available everywhere. */
    public static final List<String> LOGICAL_FAMILIES = List.of("Dialog", "DialogInput", "Serif", "SansSerif",
            "Monospaced");

    private static volatile Set<String> installedFamilies;

    private Platforms() {
    }

    public static Os current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return name.startsWith("mac") ? Os.MAC : name.startsWith("windows") ? Os.WINDOWS : Os.LINUX;
    }

    public static boolean isMac() {
        return current() == Os.MAC;
    }

    public static boolean isWindows() {
        return current() == Os.WINDOWS;
    }

    public static boolean isLinux() {
        return current() == Os.LINUX;
    }

    /**
     * The major version of macOS ({@code "26.0"} gives 26), 0 on the other operating systems : the Aqua look and feel
     * renders differently from one macOS release to another.
     */
    public static int macMajorVersion() {
        if (!isMac()) {
            return 0;
        }
        String version = System.getProperty("os.version", "0");
        int dot = version.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * {@code true} on a Mac with Apple silicon (the only macOS platform with GraalVM 25.1 or later builds).
     */
    public static boolean isAppleSilicon() {
        return isMac() && "aarch64".equals(System.getProperty("os.arch"));
    }

    /**
     * The value for the current operating system.
     */
    public static <T> T pick(T mac, T windows, T linux) {
        return switch (current()) {
            case MAC -> mac;
            case WINDOWS -> windows;
            case LINUX -> linux;
        };
    }

    /**
     * The font family names of {@link GraphicsEnvironment#getAvailableFontFamilyNames(Locale)} ({@link Locale#ROOT}).
     */
    public static Set<String> installedFamilies() {
        Set<String> families = installedFamilies;
        if (families == null) {
            families = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
            installedFamilies = families;
        }
        return families;
    }

    /**
     * The first installed font family among the candidates, or {@code fallback} (use a logical family as fallback).
     */
    public static String firstFamily(String fallback, String... candidates) {
        Set<String> installed = installedFamilies();
        for (String candidate : candidates) {
            if (installed.contains(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    /**
     * Font families commonly available on each operating system : the first installed one, or a logical family.
     */
    public static final class Families {

        private Families() {
        }

        /** Sans serif UI font. */
        public static String sans() {
            return pickFamily(List.of("Helvetica Neue"), List.of("Segoe UI", "Arial"),
                    List.of("DejaVu Sans", "Liberation Sans", "Noto Sans"), "SansSerif");
        }

        /** Classic sans serif. */
        public static String helvetica() {
            return pickFamily(List.of("Helvetica"), List.of("Arial"), List.of("Liberation Sans", "DejaVu Sans"),
                    "SansSerif");
        }

        /** Serif font. */
        public static String serif() {
            // macOS : Times is the target of the logical Serif font (sun.font.CFontManager)
            return pickFamily(List.of("Times", "Times New Roman"), List.of("Times New Roman"),
                    List.of("Liberation Serif", "DejaVu Serif", "Noto Serif"), "Serif");
        }

        /** Monospaced font. */
        public static String mono() {
            return pickFamily(List.of("Menlo"), List.of("Consolas", "Courier New"),
                    List.of("DejaVu Sans Mono", "Liberation Mono", "Noto Sans Mono"), "Monospaced");
        }

        /** Arabic system font. */
        public static String arabic() {
            return pickFamily(List.of("Geeza Pro"), List.of("Segoe UI", "Arial"),
                    List.of("Noto Sans Arabic", "Noto Naskh Arabic", "DejaVu Sans"), "SansSerif");
        }

        /** Hebrew system font. */
        public static String hebrew() {
            return pickFamily(List.of("Arial Hebrew"), List.of("Segoe UI", "Arial"),
                    List.of("Noto Sans Hebrew", "DejaVu Sans"), "SansSerif");
        }

        /** Simplified Chinese font. */
        public static String chinese() {
            return pickFamily(List.of("Hiragino Sans GB"), List.of("Microsoft YaHei", "SimSun"),
                    List.of("Noto Sans CJK SC", "WenQuanYi Micro Hei", "Droid Sans Fallback"), "SansSerif");
        }

        /** Japanese font. */
        public static String japanese() {
            return pickFamily(List.of("Hiragino Sans"), List.of("Yu Gothic", "Meiryo", "MS Gothic"),
                    List.of("Noto Sans CJK JP", "IPAGothic", "Droid Sans Fallback"), "SansSerif");
        }

        /** Korean font. */
        public static String korean() {
            return pickFamily(List.of("Apple SD Gothic Neo"), List.of("Malgun Gothic"),
                    List.of("Noto Sans CJK KR", "NanumGothic", "Droid Sans Fallback"), "SansSerif");
        }

        /** Devanagari font. */
        public static String devanagari() {
            return pickFamily(List.of("Kohinoor Devanagari"), List.of("Nirmala UI", "Mangal"),
                    List.of("Noto Sans Devanagari", "Lohit Devanagari"), "SansSerif");
        }

        /** Thai font. */
        public static String thai() {
            return pickFamily(List.of("Thonburi"), List.of("Leelawadee UI", "Tahoma"),
                    List.of("Noto Sans Thai", "Loma"), "SansSerif");
        }

        /** Color emoji font. */
        public static String emoji() {
            return pickFamily(List.of("Apple Color Emoji"), List.of("Segoe UI Emoji"), List.of("Noto Color Emoji"),
                    "Dialog");
        }

        private static String pickFamily(List<String> mac, List<String> windows, List<String> linux, String fallback) {
            return firstFamily(fallback, pick(mac, windows, linux).toArray(String[]::new));
        }
    }
}
