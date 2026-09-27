package io.quarkiverse.desktop.showcase.core;

/**
 * Result of a non-visual verification, reported in the page and in the snapshot report.
 *
 * @param name unique within a page
 * @param value deterministic textual outcome (never a timing, an address, a hash code or a random value)
 * @param ok {@code true} passed, {@code false} failed, {@code null} informational
 */
public record Check(String name, String value, Boolean ok) {

    /** Suffix of the name of the {@link #attempts} checks. */
    public static final String ATTEMPTS = " (attempts)";

    public static Check info(String name, Object value) {
        return new Check(name, String.valueOf(value), null);
    }

    public static Check pass(String name, Object value) {
        return new Check(name, String.valueOf(value), true);
    }

    public static Check fail(String name, Object value) {
        return new Check(name, String.valueOf(value), false);
    }

    public static Check of(String name, boolean ok, Object value) {
        return new Check(name, String.valueOf(value), ok);
    }

    /**
     * The number of attempts that an action depending on the desktop needed (focus, Robot input : another application
     * may take the foreground at any time) : informational, in report.json only. It is not painted by
     * {@link ChecksView} and tools/Compare.java reports its differences as notes, not as mismatches : it may differ from
     * run to run.
     */
    public static Check attempts(String action, int attempts) {
        return new Check(action + ATTEMPTS, String.valueOf(attempts), null);
    }

    /**
     * {@code true} for an {@link #attempts} check.
     */
    public boolean isAttempts() {
        return ok == null && name.endsWith(ATTEMPTS);
    }
}
