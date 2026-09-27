import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/**
 * Compares two snapshot runs (see tools/Snapshot.java) : environment, images pixel by pixel, checks value by value, and
 * errors.
 * <p>
 * usage: java tools/Compare.java comparison/jvm comparison/native comparison/diff [tolerance] [--require-focus]
 * <p>
 * Writes summary.txt, index.html and diff images into the output directory. Exit code 0 when both runs match and
 * none reported errors, 1 otherwise (two runs without any page or image do not match). {@code --require-focus} (for
 * unattended runs, e.g. CI) also fails a page that needs the focus when it never got it in a run ({@code focusAttempts}
 * 0) : its Robot input was skipped, even if both runs skipped it alike.
 * <p>
 * Environment : every top level key of the reports (except pages) is compared, a difference is an {@code ENV DIFF}
 * mismatch (another Java2D pipeline, DPI awareness, look and feel, desktop features...), except for the keys of
 * {@link #ENV_INFO_KEYS} (raw values that legitimately differ between a JVM and a native executable) : reported as
 * {@code ENV NOTE}.
 * <p>
 * Robot screen captures ({@code <page>--screen.png}, opt-in) depend on what is on screen : their differences are
 * reported as {@code SCREEN}, not as mismatches.
 * <p>
 * Images whose differences are at most {@link #NOISE_MAX_DELTA} per channel on less than {@link #NOISE_MAX_RATIO} of
 * the pixels are reported as NOISE : the same variations exist between two JVM runs using different execution modes
 * (e.g. JIT vs -Xint), they come from floating point evaluation, not from the native image. On macOS, the raw Robot
 * screen captures that both reports list ({@code captures} of a page) are NOISE up to {@link #MAC_CAPTURE_MAX_DELTA} per
 * channel : their colors come through the color profile of the display, a level or more apart from one run to the next.
 * <p>
 * The images and check values of a page flagged {@code runtimeDependent} in the reports (a page showing where the JVM
 * and a native image legitimately differ) are reported as EXPECTED when they differ : they are not mismatches, but
 * failed checks and errors of such a page still are.
 * <p>
 * The checks named {@code <action> (attempts)} count the attempts that an action on the live desktop needed (focus,
 * Robot input) : their differences are reported as {@code attempts:} notes, not as mismatches.
 */
public class Compare {

    static final int NOISE_MAX_DELTA = 2;
    static final double NOISE_MAX_RATIO = 0.005;
    /** The color tolerance of the Robot pages on macOS (RobotSession.COLOR_TOLERANCE). */
    static final int MAC_CAPTURE_MAX_DELTA = 6;
    static final List<String> ENV_INFO_KEYS = List.of("runtime", "javaVendorVersion", "javaHome", "dpiaware", "uiScale",
            "javaAwtHeadless", "mainThreadParked", "property.sun.java.launcher");
    static final List<String> NOT_ENV_KEYS = List.of("pages", "uncaughtOutsidePages");
    /** Suffix of the checks counting the attempts of an action on the live desktop (core/Check.attempts). */
    static final String ATTEMPTS = " (attempts)";

    record ImageResult(String file, String status, long differing, long total, int maxDelta, String diffFile) {
    }

    public static void main(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        boolean requireFocus = false;
        for (String arg : args) {
            if (arg.equals("--require-focus")) {
                requireFocus = true;
            } else if (arg.startsWith("--")) {
                System.err.println("Unknown option " + arg);
                System.exit(2);
            } else {
                positional.add(arg);
            }
        }
        if (positional.size() < 3) {
            System.err.println("usage: java tools/Compare.java <dirA> <dirB> <outDir> [tolerance] [--require-focus]");
            System.exit(2);
        }
        Path a = Path.of(positional.get(0));
        Path b = Path.of(positional.get(1));
        Path out = Path.of(positional.get(2));
        int tolerance = positional.size() > 3 ? Integer.parseInt(positional.get(3)) : 0;
        Files.createDirectories(out);

        // Reports
        Map<String, Object> reportA = readReport(a);
        Map<String, Object> reportB = readReport(b);
        Map<String, Map<String, Object>> pagesA = pages(reportA);
        Map<String, Map<String, Object>> pagesB = pages(reportB);
        TreeSet<String> pageIds = new TreeSet<>(pagesA.keySet());
        pageIds.addAll(pagesB.keySet());
        Set<String> runtimeDependent = new TreeSet<>();
        for (Map<String, Map<String, Object>> pages : List.of(pagesA, pagesB)) {
            pages.forEach((id, page) -> {
                if (Boolean.TRUE.equals(page.get("runtimeDependent"))) {
                    runtimeDependent.add(id);
                }
            });
        }

        // the raw Robot screen captures of both runs, on macOS
        Set<String> macCaptures = new TreeSet<>();
        if (String.valueOf(reportA.get("os")).startsWith("Mac") && String.valueOf(reportB.get("os")).startsWith("Mac")) {
            Set<String> capturesB = new TreeSet<>();
            pagesB.values().forEach(page -> capturesB.addAll(names(page.get("captures"))));
            pagesA.values().forEach(page -> names(page.get("captures")).stream().filter(capturesB::contains)
                    .forEach(macCaptures::add));
        }

        // Images
        TreeSet<String> names = new TreeSet<>();
        names.addAll(pngs(a));
        names.addAll(pngs(b));
        List<ImageResult> images = new ArrayList<>();
        for (String name : names) {
            ImageResult r = compareImage(a.resolve(name), b.resolve(name), out, name, tolerance);
            if (r.status.equals("DIFFERENT") && macCaptures.contains(name) && r.maxDelta <= MAC_CAPTURE_MAX_DELTA
                    && r.differing < NOISE_MAX_RATIO * r.total) {
                r = new ImageResult(r.file, "NOISE", r.differing, r.total, r.maxDelta, r.diffFile);
            }
            if (!r.status.equals("IDENTICAL") && !r.status.equals("NOISE") && runtimeDependent.contains(pageId(name))) {
                r = new ImageResult(r.file, "EXPECTED", r.differing, r.total, r.maxDelta, r.diffFile);
            } else if (!r.status.equals("IDENTICAL") && !r.status.equals("NOISE") && name.endsWith("--screen.png")) {
                r = new ImageResult(r.file, "SCREEN", r.differing, r.total, r.maxDelta, r.diffFile);
            }
            images.add(r);
        }

        List<String> lines = new ArrayList<>();
        int mismatches = 0;
        int errorPages = 0;
        int sameErrorPages = 0;
        Set<String> expectedPages = new TreeSet<>();

        lines.add("A = " + a + " (" + reportA.getOrDefault("runtime", "?") + ")");
        lines.add("B = " + b + " (" + reportB.getOrDefault("runtime", "?") + ")");
        TreeSet<String> envKeys = new TreeSet<>(reportA.keySet());
        envKeys.addAll(reportB.keySet());
        envKeys.removeAll(NOT_ENV_KEYS);
        for (String key : envKeys) {
            if (!String.valueOf(reportA.get(key)).equals(String.valueOf(reportB.get(key)))) {
                if (ENV_INFO_KEYS.contains(key)) {
                    lines.add("ENV NOTE " + key + ": A=" + reportA.get(key) + " | B=" + reportB.get(key));
                } else {
                    lines.add("ENV DIFF " + key + ": A=" + reportA.get(key) + " | B=" + reportB.get(key));
                    mismatches++;
                }
            }
        }
        lines.add("");
        lines.add("== Images (tolerance " + tolerance + ")");
        for (ImageResult r : images) {
            if (r.status.equals("EXPECTED")) {
                expectedPages.add(pageId(r.file));
            } else if (!r.status.equals("IDENTICAL") && !r.status.equals("NOISE") && !r.status.equals("SCREEN")) {
                mismatches++;
            }
            lines.add(String.format("%-10s %-60s %s", r.status, r.file,
                    r.total > 0 && r.differing > 0 ? String.format("%d px (%.3f%%), max delta %d", r.differing, 100.0 * r.differing / r.total, r.maxDelta) : ""));
        }

        lines.add("");
        lines.add("== Checks and errors");
        Map<String, List<String>> pageNotes = new LinkedHashMap<>();
        for (String id : pageIds) {
            List<String> notes = new ArrayList<>();
            Map<String, Object> pa = pagesA.get(id);
            Map<String, Object> pb = pagesB.get(id);
            if (pa == null || pb == null) {
                notes.add("page only in " + (pa == null ? "B" : "A"));
            } else {
                Map<String, String> ca = checks(pa);
                Map<String, String> cb = checks(pb);
                TreeSet<String> checkNames = new TreeSet<>(ca.keySet());
                checkNames.addAll(cb.keySet());
                for (String c : checkNames) {
                    if (!String.valueOf(ca.get(c)).equals(String.valueOf(cb.get(c)))) {
                        if (c.endsWith(ATTEMPTS) && ca.containsKey(c) && cb.containsKey(c)) {
                            // how many attempts an action on the live desktop needed : not a difference of the runtimes
                            notes.add("attempts: check '" + c + "': A=" + ca.get(c) + " | B=" + cb.get(c));
                        } else if (runtimeDependent.contains(id)) {
                            notes.add("expected: check '" + c + "': A=" + ca.get(c) + " | B=" + cb.get(c));
                            expectedPages.add(id);
                        } else {
                            notes.add("check '" + c + "': A=" + ca.get(c) + " | B=" + cb.get(c));
                        }
                    }
                }
                if (!String.valueOf(pa.get("extras")).equals(String.valueOf(pb.get("extras")))) {
                    notes.add("extras: A=" + pa.get("extras") + " | B=" + pb.get("extras"));
                }
                if (requireFocus) {
                    for (var run : List.of(Map.entry("A", pa), Map.entry("B", pb))) {
                        Map<String, Object> page = run.getValue();
                        if (Boolean.TRUE.equals(page.get("needsFocus"))
                                && !(page.get("focusAttempts") instanceof Number n && n.intValue() > 0)) {
                            notes.add("focus: the page never got the focus in " + run.getKey() + " (--require-focus)");
                        }
                    }
                }
            }
            List<?> errorsA = pa != null && pa.get("errors") instanceof List<?> l ? l : List.of();
            List<?> errorsB = pb != null && pb.get("errors") instanceof List<?> l ? l : List.of();
            boolean sameErrors = !errorsA.isEmpty() && String.valueOf(errorsA).equals(String.valueOf(errorsB));
            if (sameErrors) {
                // not a difference between the two runs (e.g. a JDK bug or a missing device on this machine)
                errorsA.forEach(e -> notes.add("same error in both: " + e));
            } else {
                errorsA.forEach(e -> notes.add("error in A: " + e));
                errorsB.forEach(e -> notes.add("error in B: " + e));
            }
            if (!notes.isEmpty()) {
                if (notes.stream().anyMatch(n -> n.startsWith("error"))) {
                    errorPages++;
                }
                if (sameErrors) {
                    sameErrorPages++;
                }
                if (notes.stream().anyMatch(n -> !n.startsWith("error") && !n.startsWith("same error")
                        && !n.startsWith("expected") && !n.startsWith("attempts"))) {
                    mismatches++;
                }
                pageNotes.put(id, notes);
                lines.add(id);
                notes.forEach(n -> lines.add("    " + n));
            }
        }
        for (var entry : Map.of("A", reportA, "B", reportB).entrySet()) {
            Object other = entry.getValue().get("uncaughtOutsidePages");
            if (other instanceof Map<?, ?> map && !map.isEmpty()) {
                lines.add("uncaught outside pages in " + entry.getKey() + ": " + map);
                errorPages++;
            }
        }

        if (pageIds.isEmpty() || images.isEmpty()) {
            // e.g. both runs failed before the first page : nothing was compared
            lines.add("no page or image to compare in " + a + " and " + b);
            mismatches++;
        }

        long identical = images.stream().filter(r -> r.status.equals("IDENTICAL")).count();
        long noise = images.stream().filter(r -> r.status.equals("NOISE")).count();
        String verdict = mismatches == 0 && errorPages == 0 ? "MATCH" : "MISMATCH";
        lines.add(0, String.format("%s : %d/%d images identical, %d floating point noise, %d mismatches, %d pages with errors, "
                + "%d pages with the same errors in both runs, %d runtime dependent pages with EXPECTED differences %s",
                verdict, identical, images.size(), noise, mismatches, errorPages, sameErrorPages, expectedPages.size(),
                expectedPages));
        Files.write(out.resolve("summary.txt"), lines, StandardCharsets.UTF_8);
        writeHtml(out, a, b, reportA, reportB, images, pageNotes, lines.getFirst());
        lines.forEach(System.out::println);
        System.exit(verdict.equals("MATCH") ? 0 : 1);
    }

    /**
     * The id of the page an image belongs to : {@code <page>.png}, {@code <page>--<extra>.png}, and the size suffix
     * of SIZE results.
     */
    static String pageId(String image) {
        String name = image.contains(" (") ? image.substring(0, image.indexOf(" (")) : image;
        name = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        return name.contains("--") ? name.substring(0, name.indexOf("--")) : name;
    }

    static List<String> pngs(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".png")).toList();
        }
    }

    static ImageResult compareImage(Path fa, Path fb, Path out, String name, int tolerance) throws IOException {
        if (!Files.exists(fa)) {
            return new ImageResult(name, "ONLY_B", 0, 0, 0, null);
        }
        if (!Files.exists(fb)) {
            return new ImageResult(name, "ONLY_A", 0, 0, 0, null);
        }
        BufferedImage ia = ImageIO.read(fa.toFile());
        BufferedImage ib = ImageIO.read(fb.toFile());
        if (ia == null || ib == null) {
            return new ImageResult(name + " (unreadable)", "SIZE", 0, 0, 0, null);
        }
        if (ia.getWidth() != ib.getWidth() || ia.getHeight() != ib.getHeight()) {
            return new ImageResult(name + " (" + ia.getWidth() + "x" + ia.getHeight() + " vs " + ib.getWidth() + "x"
                    + ib.getHeight() + ")", "SIZE", 0, 0, 0, null);
        }
        int w = ia.getWidth();
        int h = ia.getHeight();
        BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        long differing = 0;
        int maxDelta = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pa = ia.getRGB(x, y);
                int pb = ib.getRGB(x, y);
                int delta = 0;
                for (int shift = 0; shift <= 24; shift += 8) {
                    delta = Math.max(delta, Math.abs(((pa >> shift) & 0xFF) - ((pb >> shift) & 0xFF)));
                }
                int gray = (((pa >> 16) & 0xFF) + ((pa >> 8) & 0xFF) + (pa & 0xFF)) / 3;
                gray = 200 + gray * 55 / 255;
                if (delta > tolerance) {
                    differing++;
                    maxDelta = Math.max(maxDelta, delta);
                    int red = 128 + Math.min(127, delta);
                    diff.setRGB(x, y, (red << 16));
                } else {
                    diff.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
                }
            }
        }
        if (differing == 0) {
            return new ImageResult(name, "IDENTICAL", 0, (long) w * h, 0, null);
        }
        String diffFile = "diff-" + name;
        ImageIO.write(diff, "png", out.resolve(diffFile).toFile());
        boolean noise = maxDelta <= NOISE_MAX_DELTA && differing < NOISE_MAX_RATIO * w * h;
        return new ImageResult(name, noise ? "NOISE" : "DIFFERENT", differing, (long) w * h, maxDelta, diffFile);
    }

    /**
     * The strings of a JSON array value of a report, empty when absent.
     */
    static List<String> names(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readReport(Path dir) throws IOException {
        Path file = dir.resolve("report.json");
        if (!Files.exists(file)) {
            return Map.of();
        }
        return (Map<String, Object>) new JsonParser(Files.readString(file)).parse();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> pages(Map<String, Object> report) {
        Map<String, Map<String, Object>> pages = new LinkedHashMap<>();
        Object list = report.get("pages");
        if (list instanceof List<?> l) {
            for (Object p : l) {
                Map<String, Object> page = (Map<String, Object>) p;
                pages.put(String.valueOf(page.get("id")), page);
            }
        }
        return pages;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> checks(Map<String, Object> page) {
        Map<String, String> checks = new LinkedHashMap<>();
        Object list = page.get("checks");
        if (list instanceof List<?> l) {
            for (Object c : l) {
                Map<String, Object> check = (Map<String, Object>) c;
                checks.put(String.valueOf(check.get("name")), check.get("value") + " [" + check.get("ok") + "]");
            }
        }
        return checks;
    }

    static void writeHtml(Path out, Path a, Path b, Map<String, Object> ra, Map<String, Object> rb, List<ImageResult> images,
            Map<String, List<String>> pageNotes, String verdict) throws IOException {
        StringBuilder html = new StringBuilder("""
                <!doctype html><meta charset="utf-8"><title>JVM vs native</title>
                <style>body{font:14px -apple-system,sans-serif;margin:20px}table{border-collapse:collapse}
                td,th{border:1px solid #ccc;padding:3px 8px;text-align:left}.IDENTICAL{color:#1b7f3a}.NOISE{color:#8a6d00}.EXPECTED,.SCREEN{color:#1565c0}.DIFFERENT,.SIZE,.ONLY_A,.ONLY_B{color:#c62828;font-weight:bold}
                .row{display:flex;gap:8px;margin:8px 0 24px}.row figure{margin:0;flex:1}.row img{width:100%;border:1px solid #ccc}
                figcaption{font-size:12px;color:#555}pre{background:#f6f6f6;padding:8px;white-space:pre-wrap}</style>
                """);
        html.append("<h1>").append(esc(verdict)).append("</h1>");
        html.append("<p>A: ").append(esc(a.toString())).append(" (").append(esc(String.valueOf(ra.get("runtime"))))
                .append(") &mdash; B: ").append(esc(b.toString())).append(" (").append(esc(String.valueOf(rb.get("runtime"))))
                .append(")</p><table><tr><th>image</th><th>status</th><th>differing pixels</th><th>max delta</th></tr>");
        for (ImageResult r : images) {
            html.append("<tr><td><a href='#").append(esc(r.file)).append("'>").append(esc(r.file)).append("</a></td><td class='")
                    .append(r.status).append("'>").append(r.status).append("</td><td>")
                    .append(r.total == 0 ? "" : r.differing + String.format(" (%.3f%%)", 100.0 * r.differing / r.total))
                    .append("</td><td>").append(r.maxDelta).append("</td></tr>");
        }
        html.append("</table>");
        if (!pageNotes.isEmpty()) {
            html.append("<h2>Checks and errors</h2><pre>");
            pageNotes.forEach((id, notes) -> {
                html.append(esc(id)).append('\n');
                notes.forEach(n -> html.append("    ").append(esc(n)).append('\n'));
            });
            html.append("</pre>");
        }
        html.append("<h2>Images</h2>");
        for (ImageResult r : images) {
            String file = r.file.contains(" (") ? r.file.substring(0, r.file.indexOf(" (")) : r.file;
            html.append("<h3 id='").append(esc(r.file)).append("'>").append(esc(r.file)).append(" <span class='")
                    .append(r.status).append("'>").append(r.status).append("</span></h3><div class='row'>");
            Path base = out.toAbsolutePath().normalize();
            figure(html, base.relativize(a.resolve(file).toAbsolutePath().normalize()), "A " + ra.get("runtime"));
            figure(html, base.relativize(b.resolve(file).toAbsolutePath().normalize()), "B " + rb.get("runtime"));
            if (r.diffFile != null) {
                figure(html, Path.of(r.diffFile), "differences (red)");
            }
            html.append("</div>");
        }
        Files.writeString(out.resolve("index.html"), html, StandardCharsets.UTF_8);
    }

    static void figure(StringBuilder html, Path src, String caption) {
        html.append("<figure><img loading='lazy' src='").append(esc(src.toString())).append("'><figcaption>")
                .append(esc(caption)).append("</figcaption></figure>");
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    /**
     * Minimal JSON parser for the reports written by the showcase.
     */
    static final class JsonParser {
        private final String s;
        private int i;

        JsonParser(String s) {
            this.s = s;
        }

        Object parse() {
            ws();
            char c = s.charAt(i);
            Object value;
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>();
                i++;
                ws();
                if (s.charAt(i) == '}') {
                    i++;
                    return map;
                }
                while (true) {
                    ws();
                    String key = (String) parse();
                    ws();
                    i++; // :
                    map.put(key, parse());
                    ws();
                    if (s.charAt(i++) == '}') {
                        return map;
                    }
                }
            } else if (c == '[') {
                List<Object> list = new ArrayList<>();
                i++;
                ws();
                if (s.charAt(i) == ']') {
                    i++;
                    return list;
                }
                while (true) {
                    list.add(parse());
                    ws();
                    if (s.charAt(i++) == ']') {
                        return list;
                    }
                }
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (s.charAt(i) != '"') {
                    char ch = s.charAt(i++);
                    if (ch == '\\') {
                        char e = s.charAt(i++);
                        switch (e) {
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case 'u' -> {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                            }
                            default -> sb.append(e);
                        }
                    } else {
                        sb.append(ch);
                    }
                }
                i++;
                value = sb.toString();
            } else if (s.startsWith("true", i)) {
                i += 4;
                value = true;
            } else if (s.startsWith("false", i)) {
                i += 5;
                value = false;
            } else if (s.startsWith("null", i)) {
                i += 4;
                value = null;
            } else {
                int start = i;
                while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                    i++;
                }
                value = Double.parseDouble(s.substring(start, i));
            }
            return value;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
