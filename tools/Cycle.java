import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One JVM vs native iteration : JVM build and snapshots (optionally under the tracing agent), native build and snapshots,
 * comparison. Results: comparison/jvm-&lt;label&gt;, comparison/native-&lt;label&gt;, comparison/diff-&lt;label&gt;
 * (summary.txt, index.html), build logs in comparison/logs-&lt;label&gt;.
 * <p>
 * usage: java tools/Cycle.java &lt;label&gt; [--trace] [--exact] [--skip-jvm] [--skip-native-build] [--offline]
 * [--awt-only] [--hidpi] [--pages=ids] [--categories=names] [--maven-args=a,b] [--native-args=a,b]
 * [-- snapshot options...]
 * <p>
 * --awt-only builds and runs the AWT only variant (mvn -Dawt-only, target/awt-only). --hidpi runs both snapshot runs
 * without the -Dsun.java2d.uiScale=1 default (DPI awareness check). --maven-args is a comma separated list of extra
 * Maven arguments for both builds. --native-args is a comma separated list of native-image options, e.g.
 * --native-args=-H:+PrintClassInitialization. --exact builds with --exact-reachability-metadata and runs the native
 * executable with -XX:MissingRegistrationReportingMode=Warn : the reflection, JNI and resource accesses missing from the
 * metadata are reported in the native run.log instead of failing silently or at the first one. Options after
 * {@code --} apply to both snapshot runs.
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
            System.err.println("usage: java tools/Cycle.java <label> [--trace] [--exact] [--skip-jvm] [--skip-native-build] [--offline] "
                    + "[--awt-only] [--hidpi] [--pages=ids] [--categories=names] [--maven-args=a,b] [--native-args=a,b] "
                    + "[-- snapshot options...]");
            System.exit(2);
        }
        String label = args[0];
        boolean trace = false;
        boolean skipJvm = false;
        boolean skipNativeBuild = false;
        boolean offline = false;
        boolean exact = false;
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
            } else if (arg.equals("--awt-only")) {
                snapshot.awtOnly = true;
            } else if (arg.equals("--hidpi")) {
                snapshot.hidpi = true;
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
        if (snapshot.awtOnly) {
            mavenArgs.add("-Dawt-only");
        }

        Path logs = Path.of("comparison", "logs-" + label);
        Files.createDirectories(logs);

        if (!skipJvm) {
            step("JVM build");
            List<String> build = new ArrayList<>(List.of("-B", "package", "-DskipTests"));
            build.addAll(mavenArgs);
            if (maven(logs.resolve("jvm-build.log"), offline, build) != 0) {
                step("JVM build FAILED, see " + logs.resolve("jvm-build.log"));
                System.exit(1);
            }
            step("JVM snapshots");
            Snapshot.run("jvm", "jvm-" + label, snapshot);
            if (trace) {
                step("JVM snapshots under the tracing agent");
                Snapshot.Options traced = snapshot.copy();
                traced.trace = true;
                Snapshot.run("jvm", "trace-" + label, traced);
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

        if (!skipNativeBuild) {
            step("native build");
            List<String> build = new ArrayList<>(List.of("-B", "package", "-Dnative", "-DskipTests",
                    "-Dquarkus.native.native-image-xmx=8g"));
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
            if (Snapshot.isMac() && !macArtifacts(Snapshot.targetDir(snapshot.awtOnly), logs, snapshot.awtOnly)) {
                System.exit(1);
            }
        }

        step("native snapshots");
        if (exact) {
            // report every access missing from the metadata instead of failing at the first one
            snapshot.nativeOptions.add("-XX:MissingRegistrationReportingMode=Warn");
        }
        Snapshot.run("native", "native-" + label, snapshot);

        step("compare");
        Path summary = logs.resolve("compare.txt");
        java(summary, "tools/Compare.java", "comparison/jvm-" + label, "comparison/native-" + label,
                "comparison/diff-" + label);
        System.out.println(Files.readAllLines(summary).getFirst());
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
