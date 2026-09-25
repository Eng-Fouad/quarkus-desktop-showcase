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
 * usage: java tools/Cycle.java &lt;label&gt; [--trace] [--skip-jvm] [--skip-native-build] [--offline] [--awt-only]
 * [--hidpi] [--pages=ids] [--categories=names] [--maven-args=a,b] [--native-args=a,b] [-- snapshot options...]
 * <p>
 * --awt-only builds and runs the AWT only variant (mvn -Dawt-only, target/awt-only). --hidpi runs both snapshot runs
 * without the -Dsun.java2d.uiScale=1 default (DPI awareness check). --maven-args is a comma separated list of extra
 * Maven arguments for both builds. --native-args is a comma separated list of native-image options, e.g.
 * --native-args=-H:+PrintClassInitialization. Options after {@code --} apply to both snapshot runs.
 * <p>
 * Maven builds with the JDK running this tool : run it with GraalVM's java for native builds
 * ({@code $GRAALVM_HOME/bin/java tools/Cycle.java win1}), which also runs the JVM snapshots on GraalVM (the same JDK
 * build as the native executable).
 */
public class Cycle {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].startsWith("--")) {
            System.err.println("usage: java tools/Cycle.java <label> [--trace] [--skip-jvm] [--skip-native-build] [--offline] "
                    + "[--awt-only] [--hidpi] [--pages=ids] [--categories=names] [--maven-args=a,b] [--native-args=a,b] "
                    + "[-- snapshot options...]");
            System.exit(2);
        }
        String label = args[0];
        boolean trace = false;
        boolean skipJvm = false;
        boolean skipNativeBuild = false;
        boolean offline = false;
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
                java(diff, diffArgs.toArray(String[]::new));
                Files.readAllLines(diff).stream().filter(l -> l.startsWith("## ")).forEach(System.out::println);
            }
        }

        if (!skipNativeBuild) {
            step("native build");
            List<String> build = new ArrayList<>(List.of("-B", "package", "-Dnative", "-DskipTests",
                    "-Dquarkus.native.native-image-xmx=8g"));
            build.addAll(mavenArgs);
            if (nativeArgs != null) {
                build.add("-Dquarkus.native.additional-build-args=-H:+UnlockExperimentalVMOptions," + nativeArgs
                        + ",-H:-UnlockExperimentalVMOptions");
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
        }

        step("native snapshots");
        Snapshot.run("native", "native-" + label, snapshot);

        step("compare");
        Path summary = logs.resolve("compare.txt");
        java(summary, "tools/Compare.java", "comparison/jvm-" + label, "comparison/native-" + label,
                "comparison/diff-" + label);
        System.out.println(Files.readAllLines(summary).getFirst());
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
