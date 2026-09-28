import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs the showcase in snapshot mode : every page is rendered to comparison/&lt;label&gt;/&lt;page&gt;.png, with the
 * environment, checks and errors in comparison/&lt;label&gt;/report.json and the console output in
 * comparison/&lt;label&gt;/run.log.
 * <p>
 * usage: java tools/Snapshot.java jvm|native [label] [--pages=ids] [--categories=names] [--awt-only] [--hidpi]
 * [--pipeline=gdi|opengl|x11] [--screen] [--trace] [--timeout=seconds] [-- options...]
 * <ul>
 * <li>java tools/Snapshot.java jvm</li>
 * <li>java tools/Snapshot.java native native-d3d-off -- -Dsun.java2d.d3d=false</li>
 * <li>java tools/Snapshot.java jvm jvm-j2d --pages=j2d-,overview-environment</li>
 * <li>java tools/Snapshot.java jvm --awt-only (runs target/awt-only, built with mvn package -Dawt-only)</li>
 * </ul>
 * Defaults (unless given after {@code --}) : {@code -Duser.language=en -Duser.country=US} (a native executable defaults
 * to the locale of the build machine) and {@code -Dsun.java2d.uiScale=1} (AWT heavyweight components render correctly
 * with printAll at scale 1 only). {@code --hidpi} keeps the real UI scale : its report shows whether the executable is
 * DPI aware (defaultTransform, screenResolution).
 * <p>
 * {@code --pipeline} selects another Java2D pipeline than the default one of the platform, for both runs of a
 * comparison : {@code gdi} ({@code -Dsun.java2d.d3d=false} : GDI instead of Direct3D on Windows), {@code opengl}
 * ({@code -Dsun.java2d.opengl=true} : WGL on Windows, GLX on Linux), {@code x11} ({@code -Dsun.java2d.xrender=false} :
 * X11 instead of XRender on Linux). The environment key {@code pipeline} of the reports shows the pipeline in use.
 * <p>
 * macOS : the native executable is {@code target/*-runner} (next to its {@code .dylib} libraries). The defaults also
 * pin what the {@code java} launcher sets or what changes the rendering : {@code -Dapple.awt.application.name=Quarkus
 * Desktop Showcase}, {@code -Dapple.awt.application.appearance=NSAppearanceNameAqua},
 * {@code -Dapple.laf.useScreenMenuBar=false} ; JVM runs get {@code --add-opens java.desktop/java.awt=ALL-UNNAMED
 * --add-opens java.desktop/sun.lwawt=ALL-UNNAMED} (the capture of the AWT components, core/MacPeers). Before the
 * watchdog kills a run, {@code sample} writes the threads of the process to hang-sample.txt. Run from a Terminal window
 * of a graphical session (over ssh AWT is headless) ; Robot needs the Screen Recording and Accessibility permissions of
 * the terminal (System Settings, Privacy &amp; Security).
 * <p>
 * {@code --pages} : comma separated page ids, an entry ending with {@code -} or {@code *} being a prefix.
 * {@code --categories} : comma separated category keys (overview, awt, java2d, text, images, swing, laf, desktop,
 * printing, a11y, sound) or names. {@code --trace} (jvm only) runs the JVM under the GraalVM tracing agent, metadata in
 * comparison/&lt;label&gt;/metadata (the agent comes with GraalVM : the java of GRAALVM_HOME is used if the current one
 * has no agent). Options after {@code --} are passed to the JVM (before -jar) or to the native executable. The default
 * label is the mode, suffixed with {@code -awt} and {@code -hidpi} for these variants.
 * <p>
 * Exit code 0 when the showcase exited normally and wrote its report, 1 otherwise (no report, a crash, the watchdog).
 */
public class Snapshot {

    static final java.util.regex.Pattern MISSING_METADATA = java.util.regex.Pattern.compile(
            "Missing[A-Za-z]*RegistrationError|NoSuchFieldError|NoSuchMethodError|UnsatisfiedLinkError|ClassNotFoundException"
                    + "|MissingResourceException");

    public static void main(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        Options o = new Options();
        boolean inOptions = false;
        for (String arg : args) {
            if (inOptions) {
                o.options.add(arg);
            } else if (arg.equals("--")) {
                inOptions = true;
            } else if (arg.startsWith("--timeout=")) {
                o.timeoutSeconds = Long.parseLong(arg.substring("--timeout=".length()));
            } else if (arg.startsWith("--pages=")) {
                o.pages = arg.substring("--pages=".length());
            } else if (arg.startsWith("--categories=")) {
                o.categories = arg.substring("--categories=".length());
            } else if (arg.equals("--awt-only")) {
                o.awtOnly = true;
            } else if (arg.equals("--hidpi")) {
                o.hidpi = true;
            } else if (arg.startsWith("--pipeline=")) {
                o.pipeline = pipeline(arg.substring("--pipeline=".length()));
            } else if (arg.equals("--screen")) {
                o.screen = true;
            } else if (arg.equals("--trace")) {
                o.trace = true;
            } else if (arg.startsWith("--")) {
                System.err.println("Unknown option " + arg);
                System.exit(2);
            } else {
                positional.add(arg);
            }
        }
        if (o.trace && !positional.isEmpty() && !positional.getFirst().equals("jvm")) {
            System.err.println("--trace runs the JVM under the tracing agent : jvm mode only");
            System.exit(2);
        }
        if (positional.isEmpty() || !List.of("jvm", "native").contains(positional.getFirst())) {
            System.err.println("usage: java tools/Snapshot.java jvm|native [label] [--pages=ids] [--categories=names] "
                    + "[--awt-only] [--hidpi] [--pipeline=gdi|opengl|x11] [--screen] [--trace] [--timeout=seconds] "
                    + "[-- options...]");
            System.exit(2);
        }
        String mode = positional.get(0);
        String label = positional.size() > 1 ? positional.get(1) : defaultLabel(mode, o);
        checkLabel(label);
        System.exit(run(mode, label, o));
    }

    /**
     * Options of a snapshot run.
     */
    static final class Options {
        String pages;
        String categories;
        boolean awtOnly;
        boolean hidpi;
        /** A Java2D pipeline of {@link #PIPELINES}, or {@code null} for the default one. */
        String pipeline;
        boolean screen;
        boolean trace;
        long timeoutSeconds = 900;
        List<String> options = new ArrayList<>();
        /** Options for native runs only (e.g. -XX:MissingRegistrationReportingMode=Warn). */
        List<String> nativeOptions = new ArrayList<>();

        Options copy() {
            Options c = new Options();
            c.pages = pages;
            c.categories = categories;
            c.awtOnly = awtOnly;
            c.hidpi = hidpi;
            c.pipeline = pipeline;
            c.screen = screen;
            c.trace = trace;
            c.timeoutSeconds = timeoutSeconds;
            c.options = new ArrayList<>(options);
            c.nativeOptions = new ArrayList<>(nativeOptions);
            return c;
        }
    }

    /**
     * The Java2D pipelines of {@code --pipeline} : name and system property.
     */
    static final java.util.Map<String, String> PIPELINES = java.util.Map.of("gdi", "-Dsun.java2d.d3d=false", "opengl",
            "-Dsun.java2d.opengl=true", "x11", "-Dsun.java2d.xrender=false");

    static String pipeline(String name) {
        if (!PIPELINES.containsKey(name)) {
            System.err.println("Unknown pipeline " + name + " : " + new java.util.TreeSet<>(PIPELINES.keySet()));
            System.exit(2);
        }
        return name;
    }

    /**
     * Exits (code 2) unless {@code label} is a single directory name : letters, digits, {@code .}, {@code _} and
     * {@code -} (a label with spaces is usually options passed as one argument).
     */
    static void checkLabel(String label) {
        if (!label.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            System.err.println("Invalid label '" + label + "' : letters, digits, '.', '_' and '-' only");
            System.exit(2);
        }
    }

    static String defaultLabel(String mode, Options o) {
        return mode + (o.awtOnly ? "-awt" : "") + (o.hidpi ? "-hidpi" : "") + (o.pipeline != null ? "-" + o.pipeline : "");
    }

    static int run(String mode, String label, Options o) throws IOException, InterruptedException {
        Path out = Path.of("comparison", label);
        deleteRecursively(out);
        Files.createDirectories(out);
        Path target = targetDir(o.awtOnly);

        List<String> command = new ArrayList<>();
        if (mode.equals("jvm")) {
            command.add(o.trace ? tracingJavaExecutable() : javaExecutable());
            if (o.trace) {
                command.add("-agentlib:native-image-agent=config-output-dir=" + out.resolve("metadata"));
            }
        } else {
            command.add(nativeExecutable(target).toString());
        }
        command.add("-Dshowcase.snapshot.dir=" + out);
        if (o.pages != null && !o.pages.isBlank()) {
            command.add("-Dshowcase.pages=" + o.pages);
        }
        if (o.categories != null && !o.categories.isBlank()) {
            command.add("-Dshowcase.categories=" + o.categories);
        }
        if (o.screen) {
            command.add("-Dshowcase.snapshot.screen=true");
        }
        // Quarkus native executables default to the build machine locale, the JVM to the user's : compare with a fixed one
        if (o.options.stream().noneMatch(opt -> opt.startsWith("-Duser.language="))) {
            command.add("-Duser.language=en");
            command.add("-Duser.country=US");
        }
        // heavyweight AWT components print correctly at scale 1 only ; --hidpi keeps the real scale
        if (!o.hidpi && o.options.stream().noneMatch(opt -> opt.startsWith("-Dsun.java2d.uiScale="))) {
            command.add("-Dsun.java2d.uiScale=1");
        }
        if (o.pipeline != null) {
            String property = PIPELINES.get(o.pipeline);
            String prefix = property.substring(0, property.indexOf('=') + 1);
            if (o.options.stream().noneMatch(opt -> opt.startsWith(prefix))) {
                command.add(property);
            }
        }
        if (isMac()) {
            // set by the java launcher (the application name) or changing the rendering : the same in both runs
            for (String[] property : new String[][] { { "apple.awt.application.name", "Quarkus Desktop Showcase" },
                    { "apple.awt.application.appearance", "NSAppearanceNameAqua" },
                    { "apple.laf.useScreenMenuBar", "false" } }) {
                if (o.options.stream().noneMatch(opt -> opt.startsWith("-D" + property[0] + "="))) {
                    command.add("-D" + property[0] + "=" + property[1]);
                }
            }
            if (mode.equals("jvm")) {
                // core/MacPeers : the Swing delegates of the AWT peers (the native build gets them from the mac profile)
                command.addAll(List.of("--add-opens", "java.desktop/java.awt=ALL-UNNAMED", "--add-opens",
                        "java.desktop/sun.lwawt=ALL-UNNAMED"));
            }
        }
        command.addAll(o.options);
        if (mode.equals("native")) {
            command.addAll(o.nativeOptions);
        }
        if (mode.equals("jvm")) {
            command.add("-jar");
            command.add(target.resolve("quarkus-app").resolve("quarkus-run.jar").toString());
        }

        Path log = out.resolve("run.log");
        Files.writeString(log, "command: " + String.join(" ", command) + "\n");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
        int exit;
        if (process.waitFor(o.timeoutSeconds, TimeUnit.SECONDS)) {
            exit = process.exitValue();
        } else {
            if (isMac()) {
                // the threads of the stuck process (the first thread must be in CFRunLoopRun / -[NSApplication run])
                sample(process.pid(), out.resolve("hang-sample.txt"));
            }
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly().waitFor();
            Files.writeString(log, "WATCHDOG: killed after " + o.timeoutSeconds + "s\n", StandardOpenOption.APPEND);
            exit = -1;
        }
        Files.writeString(log, "exit=" + exit + "\n", StandardOpenOption.APPEND);

        long images;
        try (Stream<Path> files = Files.list(out)) {
            images = files.filter(p -> p.toString().endsWith(".png")).count();
        }
        Path reportFile = out.resolve("report.json");
        boolean report = Files.exists(reportFile);
        String warning = "";
        if (report) {
            String ui = String.valueOf(Compare.readReport(out).get("ui"));
            if (o.awtOnly != ui.equals("awt")) {
                warning = " WARNING: main window " + ui + " (" + target + " is not the " + (o.awtOnly ? "awt-only" : "default")
                        + " variant ?)";
            }
        }
        long missing = missingMetadata(log);
        if (missing > 0) {
            warning += " " + missing + " run.log lines about missing metadata (" + MISSING_METADATA.pattern() + ")";
        }
        System.out.println(label + ": exit=" + exit + ", " + images + " images, "
                + (report ? "report.json written" : "NO report.json") + warning);
        // a report written before a crash or the watchdog is not a successful run
        return report && exit == 0 ? 0 : 1;
    }

    /**
     * The lines of a run.log about missing native image metadata (with -XX:MissingRegistrationReportingMode=Warn : every
     * one of them), without the note that GraalVM prints once in that mode.
     */
    static long missingMetadata(Path log) throws IOException {
        return lines(log).stream().filter(l -> !l.startsWith("Note: "))
                .filter(l -> MISSING_METADATA.matcher(l).find()).count();
    }

    /**
     * The lines of a log written by another process : UTF-8, malformed bytes replaced (on Windows, a process writes its
     * output in the ANSI code page unless told otherwise, and Files.readAllLines fails on the first non-ASCII byte).
     */
    static List<String> lines(Path log) throws IOException {
        return new String(Files.readAllBytes(log), StandardCharsets.UTF_8).lines().toList();
    }

    /**
     * target (default variant) or target/awt-only (built with -Dawt-only).
     */
    static Path targetDir(boolean awtOnly) {
        return awtOnly ? Path.of("target", "awt-only") : Path.of("target");
    }

    static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString());
    }

    /**
     * A java executable with the GraalVM tracing agent : the current one, or the one of GRAALVM_HOME.
     */
    static String tracingJavaExecutable() {
        for (String home : new String[] { System.getProperty("java.home"), System.getenv("GRAALVM_HOME") }) {
            if (home == null) {
                continue;
            }
            Path h = Path.of(home);
            if (Files.exists(h.resolve("bin").resolve("native-image-agent.dll"))
                    || Files.exists(h.resolve("lib").resolve("libnative-image-agent.so"))
                    || Files.exists(h.resolve("lib").resolve("libnative-image-agent.dylib"))) {
                return h.resolve("bin").resolve(isWindows() ? "java.exe" : "java").toString();
            }
        }
        throw new IllegalStateException("No GraalVM tracing agent : run with GraalVM or set GRAALVM_HOME");
    }

    /**
     * macOS : writes 3 seconds of samples of the threads of a process ({@code sample}, no root needed for own processes).
     */
    static void sample(long pid, Path file) {
        try {
            new ProcessBuilder("sample", String.valueOf(pid), "3", "-file", file.toString()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(30, TimeUnit.SECONDS);
        } catch (IOException e) {
            // best effort
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The native executable of the variant : target/*-runner(.exe).
     */
    static Path nativeExecutable(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (Stream<Path> files = Files.list(target)) {
                // macOS and Linux : the executable file without extension (not the .dylib/.so libraries next to it)
                List<Path> runners = files.filter(p -> p.getFileName().toString().endsWith(isWindows() ? "-runner.exe" : "-runner"))
                        .filter(Files::isRegularFile).filter(p -> isWindows() || Files.isExecutable(p)).toList();
                if (!runners.isEmpty()) {
                    return runners.getFirst().toAbsolutePath();
                }
            }
        }
        throw new IllegalStateException("No native executable in " + target + " : build it with mvn package -Dnative"
                + (target.endsWith("awt-only") ? " -Dawt-only" : ""));
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
