import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * One JVM vs native iteration : JVM build and snapshots (optionally under the tracing agent), native build and snapshots,
 * comparison. Results: comparison/jvm-&lt;label&gt;, comparison/native-&lt;label&gt;, comparison/diff-&lt;label&gt;
 * (summary.txt, index.html), build logs in comparison/logs-&lt;label&gt;.
 * <p>
 * usage: java tools/Cycle.java &lt;label&gt; [--trace] [--exact] [--require-focus] [--jvm-only] [--skip-jvm]
 * [--skip-native-build] [--offline] [--awt-only] [--hidpi] [--pipeline=gdi|opengl|x11] [--pages=ids] [--categories=names] [--maven-args=a,b]
 * [--native-args=a,b] [-- snapshot options...]
 * <p>
 * --awt-only builds and runs the AWT only variant (mvn -Dawt-only, target/awt-only). --hidpi runs both snapshot runs
 * without the -Dsun.java2d.uiScale=1 default (DPI awareness check). --pipeline runs both snapshot runs with another
 * Java2D pipeline (see tools/Snapshot.java : gdi, opengl, x11). --maven-args is a comma separated list of extra
 * Maven arguments for both builds. --native-args is a comma separated list of native-image options, e.g.
 * --native-args=-H:+PrintClassInitialization. --exact builds with --exact-reachability-metadata and runs the native
 * executable with -XX:MissingRegistrationReportingMode=Warn : the reflection, JNI and resource accesses missing from the
 * metadata are reported in the native run.log instead of failing silently or at the first one. --require-focus fails
 * the comparison when a page that needs the focus never got it (see tools/Compare.java : for unattended runs). Options
 * after {@code --} apply to both snapshot runs. --jvm-only compares two JVM runs (comparison/jvm-&lt;label&gt; and
 * comparison/jvm2-&lt;label&gt;) instead of a JVM and a native run : the determinism of the pages on a platform without
 * native-image (Windows on arm64), with the same exit codes. The native build gets
 * {@code -Dquarkus.native.native-image-xmx=8g} unless --maven-args sets it.
 * <p>
 * The native build writes {@value #NATIVE_BUILD} next to the executable : whether it was built with --exact, its
 * options and the commit of the sources. --skip-native-build reuses the executable of an earlier cycle : with --exact,
 * the cycle stops (exit code 2) unless that executable was built with --exact (a native executable built without exact
 * reachability metadata reports no missing registration : "0 missing" would prove nothing), and it warns when the
 * executable was built from other sources than the JVM build of the cycle.
 * <p>
 * Exit code 0 when every step succeeded and the runs match ; 1 when a build failed, a snapshot run failed (no report, a
 * crash, the watchdog), the native run of --exact reported accesses missing from the metadata, or the comparison is not
 * a MATCH (the cycle then still runs its remaining steps) ; 2 for a usage error.
 * <p>
 * Maven builds with the JDK running this tool : run it with GraalVM's java for native builds
 * ({@code $GRAALVM_HOME/bin/java tools/Cycle.java win1}), which also runs the JVM snapshots on GraalVM (the same JDK
 * build as the native executable).
 * <p>
 * macOS (GraalVM 25.1 or later, and Quarkus built from the pull request "Enable quarkus-awt on macOS",
 * https://github.com/quarkusio/quarkus/pull/56979 : {@code --maven-args=-Dquarkus.platform.version=999-SNAPSHOT}) : after the native build,
 * comparison/logs-&lt;label&gt;/native-artifacts.txt lists the libraries next to the executable, its linked libraries
 * ({@code otool -L}), its run paths and its signature ; the cycle stops when a library that AWT needs is missing.
 */
public class Cycle {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].startsWith("--")) {
            System.err.println("usage: java tools/Cycle.java <label> [--trace] [--exact] [--require-focus] [--jvm-only] [--skip-jvm] "
                    + "[--skip-native-build] [--offline] [--awt-only] [--hidpi] [--pipeline=gdi|opengl|x11] [--pages=ids] "
                    + "[--categories=names] [--maven-args=a,b] [--native-args=a,b] [-- snapshot options...]");
            System.exit(2);
        }
        String label = args[0];
        Snapshot.checkLabel(label);
        boolean requireFocus = false;
        boolean trace = false;
        boolean skipJvm = false;
        boolean skipNativeBuild = false;
        boolean offline = false;
        boolean exact = false;
        boolean jvmOnly = false;
        String nativeArgs = null;
        List<String> mavenArgs = new ArrayList<>();
        Snapshot.Options snapshot = new Snapshot.Options();
        boolean inOptions = false;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (inOptions) {
                snapshot.options.add(arg);
            } else if (arg.equals("--")) {
                inOptions = true;
            } else if (arg.equals("--trace")) {
                trace = true;
            } else if (arg.equals("--skip-jvm")) {
                skipJvm = true;
            } else if (arg.equals("--skip-native-build")) {
                skipNativeBuild = true;
            } else if (arg.equals("--offline")) {
                offline = true;
            } else if (arg.equals("--exact")) {
                exact = true;
            } else if (arg.equals("--require-focus")) {
                requireFocus = true;
            } else if (arg.equals("--jvm-only")) {
                jvmOnly = true;
            } else if (arg.equals("--awt-only")) {
                snapshot.awtOnly = true;
            } else if (arg.equals("--hidpi")) {
                snapshot.hidpi = true;
            } else if (arg.startsWith("--pipeline=")) {
                snapshot.pipeline = Snapshot.pipeline(arg.substring("--pipeline=".length()));
            } else if (arg.startsWith("--pages=")) {
                snapshot.pages = arg.substring("--pages=".length());
            } else if (arg.startsWith("--categories=")) {
                snapshot.categories = arg.substring("--categories=".length());
            } else if (arg.startsWith("--maven-args=")) {
                mavenArgs.addAll(List.of(arg.substring("--maven-args=".length()).split(",")));
            } else if (arg.startsWith("--native-args=")) {
                nativeArgs = arg.substring("--native-args=".length());
            } else {
                System.err.println("Unknown option " + arg);
                System.exit(2);
            }
        }
        if (jvmOnly && (trace || exact || skipJvm || skipNativeBuild || nativeArgs != null)) {
            // --trace : a JDK without native-image (Windows on arm64) has no tracing agent either
            System.err.println("--jvm-only compares two JVM runs : not with --trace, --exact, --skip-jvm, "
                    + "--skip-native-build or --native-args");
            System.exit(2);
        }
        if (snapshot.awtOnly) {
            mavenArgs.add("-Dawt-only");
        }
        if (skipNativeBuild && !jvmOnly) {
            checkNativeBuild(Snapshot.targetDir(snapshot.awtOnly), exact);
        }

        Path logs = Path.of("comparison", "logs-" + label);
        Files.createDirectories(logs);
        // what failed : the cycle goes on (the comparison shows the most), and exits 1 at the end
        List<String> failures = new ArrayList<>();

        if (!skipJvm) {
            step("JVM build");
            List<String> build = new ArrayList<>(List.of("-B", "package", "-DskipTests"));
            build.addAll(mavenArgs);
            if (maven(logs.resolve("jvm-build.log"), offline, build) != 0) {
                step("JVM build FAILED, see " + logs.resolve("jvm-build.log"));
                System.exit(1);
            }
            step("JVM snapshots");
            if (Snapshot.run("jvm", "jvm-" + label, snapshot) != 0) {
                failures.add("JVM snapshots (comparison/jvm-" + label + "/run.log)");
            }
            if (trace) {
                step("JVM snapshots under the tracing agent");
                Snapshot.Options traced = snapshot.copy();
                traced.trace = true;
                if (Snapshot.run("jvm", "trace-" + label, traced) != 0) {
                    failures.add("JVM snapshots under the tracing agent (comparison/trace-" + label + "/run.log)");
                }
                Path metadata = Path.of("comparison", "trace-" + label, "metadata", "reachability-metadata.json");
                Path diff = Path.of("comparison", "trace-" + label, "metadata-diff.md");
                List<String> diffArgs = new ArrayList<>(List.of("tools/MetadataDiff.java", metadata.toString()));
                if (snapshot.awtOnly) {
                    diffArgs.add("--awt-only");
                }
                // the quarkus-awt of the build (e.g. 999-SNAPSHOT with macOS support) rather than the one of pom.xml
                mavenArgs.stream().filter(a -> a.startsWith("-Dquarkus.platform.version=")).findFirst()
                        .ifPresent(a -> diffArgs.add("--quarkus-version=" + a.substring(a.indexOf('=') + 1)));
                java(diff, diffArgs.toArray(String[]::new));
                Files.readAllLines(diff).stream().filter(l -> l.startsWith("## ")).forEach(System.out::println);
            }
        }

        // the run compared with the JVM snapshots : the native executable, or a second JVM run (--jvm-only)
        String second = (jvmOnly ? "jvm2-" : "native-") + label;
        if (jvmOnly) {
            step("second JVM snapshots");
            if (Snapshot.run("jvm", second, snapshot) != 0) {
                failures.add("second JVM snapshots (comparison/" + second + "/run.log)");
            }
        }

        if (!skipNativeBuild && !jvmOnly) {
            step("native build");
            List<String> build = new ArrayList<>(List.of("-B", "package", "-Dnative", "-DskipTests"));
            if (mavenArgs.stream().noneMatch(a -> a.startsWith("-Dquarkus.native.native-image-xmx="))) {
                build.add("-Dquarkus.native.native-image-xmx=8g");
            }
            build.addAll(mavenArgs);
            List<String> additional = new ArrayList<>();
            if (exact) {
                additional.add("--exact-reachability-metadata");
            }
            if (nativeArgs != null) {
                additional.add("-H:+UnlockExperimentalVMOptions," + nativeArgs + ",-H:-UnlockExperimentalVMOptions");
            }
            if (!additional.isEmpty()) {
                build.add("-Dquarkus.native.additional-build-args=" + String.join(",", additional));
            }
            Path log = logs.resolve("native-build.log");
            if (maven(log, offline, build) != 0) {
                step("native build FAILED, see " + log);
                Files.readAllLines(log).stream().filter(l -> l.contains("Fatal error") || l.startsWith("Error:")
                        || l.contains("[ERROR]")).limit(8).forEach(System.out::println);
                System.exit(1);
            }
            Files.readAllLines(log).stream().filter(l -> l.contains("Finished generating") || l.contains("Peak RSS"))
                    .forEach(System.out::println);
            writeNativeBuild(Snapshot.targetDir(snapshot.awtOnly), exact, nativeArgs, mavenArgs);
            if (Snapshot.isMac() && !macArtifacts(Snapshot.targetDir(snapshot.awtOnly), logs, snapshot.awtOnly)) {
                System.exit(1);
            }
        }

        if (!jvmOnly) {
            step("native snapshots");
            if (exact) {
                // report every access missing from the metadata instead of failing at the first one
                snapshot.nativeOptions.add("-XX:MissingRegistrationReportingMode=Warn");
            }
            if (Snapshot.run("native", second, snapshot) != 0) {
                failures.add("native snapshots (comparison/" + second + "/run.log)");
            }
        }
        if (exact) {
            long missing = Snapshot.missingMetadata(Path.of("comparison", "native-" + label, "run.log"));
            if (missing > 0) {
                failures.add(missing + " run.log lines about accesses missing from the metadata (comparison/native-"
                        + label + "/run.log)");
            }
        }

        step("compare");
        Path summary = logs.resolve("compare.txt");
        List<String> compare = new ArrayList<>(List.of("tools/Compare.java", "comparison/jvm-" + label,
                "comparison/" + second, "comparison/diff-" + label));
        if (requireFocus) {
            compare.add("--require-focus");
        }
        int compared = java(summary, compare.toArray(String[]::new));
        List<String> result = Files.readAllLines(summary);
        String verdict = result.stream().filter(l -> l.startsWith("MATCH") || l.startsWith("MISMATCH")).findFirst()
                .orElse(result.isEmpty() ? "no comparison" : result.getFirst());
        System.out.println(verdict);
        if (compared != 0) {
            failures.add("comparison : " + verdict.split(" : ")[0] + " (comparison/diff-" + label + "/summary.txt)");
        }

        if (!failures.isEmpty()) {
            step("cycle " + label + " FAILED : " + String.join(" ; ", failures));
            System.exit(1);
        }
        step("cycle " + label + " OK");
    }

    /**
     * The description of the native build of a cycle, next to the executable (target/ or target/awt-only/).
     */
    static final String NATIVE_BUILD = "cycle-native-build.properties";

    /**
     * Describes the native build that just succeeded, for the cycles that reuse its executable (--skip-native-build).
     */
    static void writeNativeBuild(Path target, boolean exact, String nativeArgs, List<String> mavenArgs)
            throws IOException, InterruptedException {
        Properties build = new Properties();
        build.setProperty("exact", String.valueOf(exact));
        build.setProperty("native-args", nativeArgs == null ? "" : nativeArgs);
        build.setProperty("maven-args", String.join(",", mavenArgs));
        build.setProperty("commit", git("rev-parse", "HEAD"));
        build.setProperty("dirty", String.valueOf(!git("status", "--porcelain", "--untracked-files=no").isEmpty()));
        build.setProperty("executable-modified",
                String.valueOf(Files.getLastModifiedTime(Snapshot.nativeExecutable(target)).toMillis()));
        try (Writer out = Files.newBufferedWriter(target.resolve(NATIVE_BUILD))) {
            build.store(out, "The native build of Cycle.java");
        }
    }

    /**
     * --skip-native-build : the native executable must come from a cycle with --exact when this one has it ; a warning
     * when it was built from other sources than the current ones.
     */
    static void checkNativeBuild(Path target, boolean exact) throws IOException, InterruptedException {
        Path file = target.resolve(NATIVE_BUILD);
        Path runner;
        try {
            runner = Snapshot.nativeExecutable(target);
        } catch (IllegalStateException e) {
            step("--skip-native-build : " + e.getMessage());
            System.exit(2);
            return;
        }
        Properties build = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                build.load(in);
            }
        }
        if (!String.valueOf(Files.getLastModifiedTime(runner).toMillis()).equals(build.getProperty("executable-modified"))) {
            // no description, or the executable was built since (mvn package -Dnative)
            if (exact) {
                step("--exact --skip-native-build : " + runner + " was not built by a cycle with --exact (no " + file
                        + ", or the executable was built since) : run the cycle without --skip-native-build");
                System.exit(2);
            }
            step("WARNING : " + runner + " was not built by a cycle : its native image options are unknown");
            return;
        }
        boolean builtExact = Boolean.parseBoolean(build.getProperty("exact"));
        if (exact && !builtExact) {
            step("--exact --skip-native-build : " + runner + " was built without --exact-reachability-metadata (" + file
                    + ") : it reports no missing registration, run the cycle without --skip-native-build");
            System.exit(2);
        }
        if (!exact && builtExact) {
            step("WARNING : " + runner + " was built with --exact-reachability-metadata, and runs without "
                    + "-XX:MissingRegistrationReportingMode=Warn : it stops at the first missing registration");
        }
        String head = git("rev-parse", "HEAD");
        if (!head.equals(build.getProperty("commit")) || Boolean.parseBoolean(build.getProperty("dirty"))) {
            step("WARNING : " + runner + " was built from commit " + build.getProperty("commit")
                    + (Boolean.parseBoolean(build.getProperty("dirty")) ? " with local changes" : "")
                    + ", the JVM build of this cycle from " + head + " : the runs may differ by the sources");
        }
    }

    /**
     * The output of a git command in the current directory, empty when git fails.
     */
    static String git(String... args) throws InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return process.waitFor() == 0 ? output : "";
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * The libraries that a macOS native executable using AWT loads from its directory (copied by GraalVM 25.1 and later ;
     * libjava and libjvm are shims generated by GraalVM).
     */
    static final List<String> MAC_LIBRARIES = List.of("libawt.dylib", "libawt_lwawt.dylib", "libosxapp.dylib",
            "libfontmanager.dylib", "libfreetype.dylib", "libjavajpeg.dylib", "liblcms.dylib", "libmlib_image.dylib",
            "libjava.dylib", "libjvm.dylib");

    /**
     * macOS : writes native-artifacts.txt (libraries, otool -L, run paths, signature) ; {@code false} when a library that
     * AWT needs is missing.
     */
    static boolean macArtifacts(Path target, Path logs, boolean awtOnly) throws IOException, InterruptedException {
        Path runner = Snapshot.nativeExecutable(target);
        Path out = logs.resolve("native-artifacts.txt");
        List<String> lines = new ArrayList<>();
        try (var files = Files.list(target)) {
            files.filter(p -> p.getFileName().toString().endsWith(".dylib")).sorted()
                    .forEach(p -> lines.add(p.getFileName() + " " + size(p)));
        }
        lines.add("");
        lines.addAll(command("otool", "-L", runner.toString()));
        lines.add("");
        List<String> loadCommands = command("otool", "-l", runner.toString());
        for (int i = 0; i < loadCommands.size(); i++) {
            if (loadCommands.get(i).contains("LC_RPATH")) {
                lines.addAll(loadCommands.subList(i, Math.min(i + 3, loadCommands.size())));
            }
        }
        lines.add("");
        lines.addAll(command("codesign", "-dv", runner.toString()));
        lines.addAll(command("xattr", "-l", runner.toString()));
        Files.write(out, lines);
        List<String> missing = MAC_LIBRARIES.stream().filter(l -> !Files.isRegularFile(target.resolve(l))).toList();
        if (!missing.isEmpty()) {
            step("native build : " + missing + " missing next to " + runner + " (GraalVM 25.1 or later copies them), see "
                    + out);
            return false;
        }
        if (!awtOnly && !Files.isRegularFile(target.resolve("libosxui.dylib"))) {
            step("WARNING : libosxui.dylib (Aqua look and feel) missing next to " + runner);
        }
        step("native artifacts : " + out);
        return true;
    }

    private static String size(Path p) {
        try {
            return Files.size(p) + " bytes";
        } catch (IOException e) {
            return "?";
        }
    }

    static List<String> command(String... command) throws InterruptedException {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            List<String> lines = new ArrayList<>();
            lines.add("$ " + String.join(" ", command));
            lines.addAll(new String(process.getInputStream().readAllBytes()).lines().toList());
            process.waitFor();
            return lines;
        } catch (IOException e) {
            return List.of("$ " + String.join(" ", command) + " : " + e.getMessage());
        }
    }

    static void step(String message) {
        System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message);
    }

    static int maven(Path log, boolean offline, List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        boolean windows = Snapshot.isWindows();
        Path wrapper = Path.of(windows ? "mvnw.cmd" : "mvnw");
        if (Files.exists(wrapper)) {
            command.add(wrapper.toAbsolutePath().toString());
        } else {
            command.add(windows ? "mvn.cmd" : "mvn");
        }
        if (offline) {
            command.add("-o");
        }
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        // build with the JDK running this tool (GraalVM for native builds), whatever the shell environment says
        Path javaHome = Path.of(System.getProperty("java.home"));
        builder.environment().put("JAVA_HOME", javaHome.toString());
        if (Files.exists(javaHome.resolve("bin").resolve(windows ? "native-image.cmd" : "native-image"))) {
            builder.environment().put("GRAALVM_HOME", javaHome.toString());
        }
        return builder.start().waitFor();
    }

    static int java(Path output, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Snapshot.javaExecutable());
        command.addAll(List.of(args));
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start().waitFor();
    }
}
