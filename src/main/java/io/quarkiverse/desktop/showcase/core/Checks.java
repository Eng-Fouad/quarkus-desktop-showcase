package io.quarkiverse.desktop.showcase.core;

import java.awt.Component;
import java.awt.Container;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;

/**
 * Helpers to run checks, attach them to a page component and format deterministic values.
 * <p>
 * Checks attached to a component of the page content (with {@link #attach} or {@link ChecksView}) are collected after
 * the page is ready and written to report.json.
 */
public final class Checks {

    // Component does not override equals / hashCode : identity keys. Weak : content of left pages is collected.
    private static final Map<Component, List<Check>> ATTACHED = Collections.synchronizedMap(new WeakHashMap<>());

    private Checks() {
    }

    /**
     * Runs {@code action} : passed with the returned value, or failed with the exception.
     */
    public static Check run(String name, Callable<?> action) {
        try {
            return Check.pass(name, action.call());
        } catch (Throwable t) {
            return Check.fail(name, describe(t));
        }
    }

    /**
     * Runs {@code action} : passed if the returned value equals {@code expected} (compared as strings when the types
     * differ, e.g. an Integer and a String).
     */
    public static Check expect(String name, Object expected, Callable<?> action) {
        try {
            Object actual = action.call();
            boolean ok = expected == null ? actual == null
                    : expected.equals(actual) || (actual != null && expected.toString().equals(actual.toString()));
            return Check.of(name, ok, ok ? actual : "expected " + expected + " but got " + actual);
        } catch (Throwable t) {
            return Check.fail(name, describe(t));
        }
    }

    /**
     * Runs {@code action} : informational check with the returned value, or failed with the exception.
     */
    public static Check info(String name, Callable<?> action) {
        try {
            return Check.info(name, action.call());
        } catch (Throwable t) {
            return Check.fail(name, describe(t));
        }
    }

    /**
     * {@code check} as is on {@code os}, and informational on the other operating systems (for expectations verified
     * on one operating system only).
     */
    public static Check onlyOn(Platforms.Os os, Check check) {
        return Platforms.current() == os || !Boolean.FALSE.equals(check.ok()) ? check
                : Check.info(check.name(), check.value());
    }

    public static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        if (t.getMessage() != null) {
            sb.append(": ").append(t.getMessage());
        }
        Throwable cause = t.getCause();
        int depth = 0;
        while (cause != null && cause != t && depth++ < 5) {
            sb.append(" <- ").append(cause.getClass().getName());
            if (cause.getMessage() != null) {
                sb.append(": ").append(cause.getMessage());
            }
            t = cause;
            cause = cause.getCause();
        }
        return sb.toString();
    }

    /**
     * Attaches {@code checks} to {@code component} (replacing the checks attached before).
     */
    public static <C extends Component> C attach(C component, List<Check> checks) {
        ATTACHED.put(component, List.copyOf(checks));
        return component;
    }

    public static List<Check> of(Component component) {
        List<Check> checks = ATTACHED.get(component);
        return checks == null ? List.of() : checks;
    }

    /**
     * Collects the checks attached to {@code root} and to all its descendants (depth first, in component order).
     */
    public static List<Check> collect(Component root) {
        List<Check> all = new ArrayList<>(of(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                all.addAll(collect(child));
            }
        }
        return all;
    }

    // ------------------------------------------------------------------------------------ deterministic formatting

    /**
     * {@code value} with {@code decimals} decimals, {@code .} as decimal separator, and no negative zero.
     */
    public static String num(double value, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", value);
        return s.matches("-0(\\.0*)?") ? s.substring(1) : s;
    }

    /**
     * {@code value} with 3 decimals.
     */
    public static String num(double value) {
        return num(value, 3);
    }

    /**
     * {@code x,y wxh} of the bounds of {@code shape}, 2 decimals.
     */
    public static String bounds(Shape shape) {
        Rectangle2D r = shape instanceof Rectangle2D rect ? rect : shape.getBounds2D();
        return num(r.getX(), 2) + "," + num(r.getY(), 2) + " " + num(r.getWidth(), 2) + "x" + num(r.getHeight(), 2);
    }

    /**
     * {@code #AARRGGBB}.
     */
    public static String argb(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    /**
     * SHA-256 of {@code bytes}, first 16 hex digits (enough to detect a difference).
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 of the ARGB pixels of {@code image} (first 16 hex digits).
     */
    public static String sha256(java.awt.image.BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(argb.length * 4 + 8);
        bytes.putInt(w).putInt(h);
        bytes.asIntBuffer().put(argb);
        return sha256(bytes.array());
    }
}
