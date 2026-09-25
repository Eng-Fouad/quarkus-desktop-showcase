import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Audits the static initializers of the JDK desktop classes (modules java.desktop, java.datatransfer,
 * jdk.unsupported.desktop, jdk.accessibility) that quarkus-desktop and quarkus-awt leave initialized at build time :
 * Quarkus initializes every class at build time unless registered otherwise.
 * <p>
 * For each such class, reports what its static initializer can reach, transitively : native methods, native library
 * loading, threads/timers, native memory, the toolkit or the display, NIO channels (an open channel in the image heap
 * fails the build), system properties or environment reads (values frozen at build time), resource bundles (locale
 * frozen at build time), and initialization of classes that are initialized at run time.
 * <p>
 * usage: java tools/ClinitAudit.java [windows|linux] [--awt-only] [--version=999-SNAPSHOT] [--quarkus-version=3.40.0]
 * [class_initialization_report.csv]
 * <p>
 * Run time initialized : the RUNTIME_INITIALIZED_PACKAGES / RUNTIME_INITIALIZED_CLASSES lists (common and platform) of
 * the installed quarkus-desktop deployment jars (see tools/MetadataDiff.java), plus the packages named by quarkus-awt's
 * AwtProcessor (heuristic : its String constants that are packages of the desktop modules), matched along the
 * superclass chain (GraalVM initializes a subclass of a run time initialized class at run time). With the CSV of
 * {@code -H:+PrintClassInitialization}, only the classes really initialized at build time in the image are reported.
 * Uses the class file API of JDK 24+ (no library needed).
 */
public class ClinitAudit {

    static final String CLINIT = "<clinit>()V";

    // Hazard categories, from the most to the least severe
    static final String NATIVE = "NATIVE_CALL";
    static final String LIBRARY = "LOADS_LIBRARY";
    static final String THREAD = "THREAD_OR_TIMER";
    static final String MEMORY = "NATIVE_MEMORY_OR_CLEANER";
    static final String CHANNEL = "OPENS_NIO_CHANNEL";
    static final String TOOLKIT = "TOOLKIT_OR_DISPLAY";
    static final String RUNTIME_INIT = "INITIALIZES_RUNTIME_CLASS";
    static final String BUNDLE = "RESOURCE_BUNDLE";
    static final String PROPERTY = "PROPERTY_OR_ENV";
    static final List<String> ORDER = List.of(NATIVE, LIBRARY, THREAD, MEMORY, CHANNEL, TOOLKIT, RUNTIME_INIT, BUNDLE,
            PROPERTY);

    record Call(String target, boolean classInit) {
    }

    static final Map<String, List<Call>> calls = new HashMap<>();
    static final Map<String, Map<String, String>> hazards = new HashMap<>(); // method -> hazard -> witness
    static final Set<String> nativeMethods = new HashSet<>();
    static final Map<String, String> superClasses = new HashMap<>();
    static final Set<String> desktopClasses = new TreeSet<>();
    static final Set<String> classesWithClinit = new HashSet<>();

    public static void main(String[] args) throws Exception {
        String platform = MetadataDiff.currentPlatform();
        boolean awtOnly = false;
        String version = "999-SNAPSHOT";
        String quarkusVersion = MetadataDiff.quarkusVersion();
        Path report = null;
        for (String arg : args) {
            if (arg.equals("--awt-only")) {
                awtOnly = true;
            } else if (arg.startsWith("--version=")) {
                version = arg.substring("--version=".length());
            } else if (arg.startsWith("--quarkus-version=")) {
                quarkusVersion = arg.substring("--quarkus-version=".length());
            } else if (arg.endsWith(".csv")) {
                report = Path.of(arg);
            } else {
                platform = arg.toLowerCase(Locale.ROOT).startsWith("win") ? "WINDOWS" : "LINUX";
            }
        }

        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        for (String module : MetadataDiff.MODULES) {
            Path root = jrt.getPath("/modules", module);
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".class") && !f.endsWith("module-info.class"))
                        .toList()) {
                    scan(Files.readAllBytes(p));
                }
            }
        }

        // Classes initialized at run time
        Set<String> runtimeClasses = new HashSet<>();
        List<String> runtimePackages = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        for (String[] extension : MetadataDiff.extensions(awtOnly)) {
            Path jar = MetadataDiff.M2.resolve("io/quarkiverse/desktop/" + extension[0] + "/" + version + "/" + extension[0]
                    + "-" + version + ".jar");
            if (!Files.exists(jar)) {
                sources.add(jar + " : NOT FOUND");
                continue;
            }
            Map<String, String[]> lists = MetadataDiff.readLists(jar, extension[1]);
            sources.add(jar + " (" + Files.getLastModifiedTime(jar) + ")");
            for (String kind : List.of("RUNTIME_INITIALIZED_CLASSES", platform + "_RUNTIME_INITIALIZED_CLASSES")) {
                for (String name : lists.getOrDefault(kind, new String[0])) {
                    runtimeClasses.add(name.trim().replace('.', '/'));
                }
            }
            for (String kind : List.of("RUNTIME_INITIALIZED_PACKAGES", platform + "_RUNTIME_INITIALIZED_PACKAGES")) {
                for (String name : lists.getOrDefault(kind, new String[0])) {
                    runtimePackages.add(name.trim().replace('.', '/') + "/");
                }
            }
        }
        Path awtDeployment = MetadataDiff.M2.resolve("io/quarkus/quarkus-awt-deployment/" + quarkusVersion
                + "/quarkus-awt-deployment-" + quarkusVersion + ".jar");
        if (Files.exists(awtDeployment)) {
            MetadataDiff.Universe universe = MetadataDiff.Universe.load();
            List<String> packages = new ArrayList<>();
            for (String constant : MetadataDiff.stringConstants(awtDeployment, "io/quarkus/awt/deployment/AwtProcessor.class")) {
                if (universe.packages.contains(constant) && !universe.classes.containsKey(constant)) {
                    packages.add(constant);
                    runtimePackages.add(constant.replace('.', '/') + "/");
                }
            }
            sources.add(awtDeployment + " : run time packages (heuristic) " + packages);
        } else {
            sources.add(awtDeployment + " : NOT FOUND");
        }
        Set<String> runtimeInitialized = new TreeSet<>();
        for (String c : desktopClasses) {
            if (isRuntime(c, runtimeClasses, runtimePackages)) {
                runtimeInitialized.add(c);
            }
        }

        // Class init report : which classes were really initialized at build time in the image
        Set<String> buildTimeInImage = null;
        if (report != null && Files.exists(report)) {
            buildTimeInImage = new HashSet<>();
            for (String line : Files.readAllLines(report)) {
                String[] cols = line.split(",", 3);
                if (cols.length >= 2 && cols[1].contains("BUILD_TIME")) {
                    buildTimeInImage.add(cols[0].trim().replace('.', '/'));
                }
            }
        }

        // Class initialization edges into run time initialized classes are hazards
        for (Map.Entry<String, List<Call>> e : calls.entrySet()) {
            for (Call call : e.getValue()) {
                if (call.classInit) {
                    String target = call.target.substring(0, call.target.indexOf('.'));
                    if (runtimeInitialized.contains(target)) {
                        hazard(e.getKey(), RUNTIME_INIT, target.replace('/', '.'));
                    }
                }
            }
        }
        propagate(runtimeInitialized);

        // Report
        Map<String, Map<String, List<String>>> byPackage = new TreeMap<>();
        int count = 0;
        for (String c : desktopClasses) {
            if (runtimeInitialized.contains(c) || !classesWithClinit.contains(c)) {
                continue;
            }
            if (buildTimeInImage != null && !buildTimeInImage.contains(c)) {
                continue;
            }
            Map<String, String> h = hazards.getOrDefault(c + "." + CLINIT, Map.of());
            if (h.isEmpty()) {
                continue;
            }
            count++;
            String pkg = c.substring(0, c.lastIndexOf('/')).replace('/', '.');
            List<String> lines = new ArrayList<>();
            for (String category : ORDER) {
                if (h.containsKey(category)) {
                    lines.add(category + " via " + path(c + "." + CLINIT, category));
                }
            }
            byPackage.computeIfAbsent(pkg, k -> new TreeMap<>()).put(c.replace('/', '.'), lines);
        }
        System.out.println("# Build-time initialized JDK desktop classes (" + platform + ", JDK " + Runtime.version()
                + ") whose static initializer reaches a hazard"
                + (buildTimeInImage != null ? " - limited to classes initialized at build time in the image" : "") + ": "
                + count);
        System.out.println();
        sources.forEach(s -> System.out.println("- run time initialization from: " + s));
        System.out.println("- " + desktopClasses.size() + " classes scanned, " + runtimeInitialized.size()
                + " initialized at run time");
        byPackage.forEach((pkg, classes) -> {
            System.out.println("\n## " + pkg);
            classes.forEach((c, lines) -> {
                System.out.println("- " + c);
                lines.forEach(l -> System.out.println("    " + l));
            });
        });
    }

    static boolean isRuntime(String c, Set<String> classes, List<String> packages) {
        for (String current = c; current != null; current = superClasses.get(current)) {
            if (classes.contains(current)) {
                return true;
            }
            for (String p : packages) {
                if (current.startsWith(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String internalName(ClassDesc desc) {
        String d = desc.descriptorString();
        return d.startsWith("L") && d.endsWith(";") ? d.substring(1, d.length() - 1) : null;
    }

    static void scan(byte[] bytes) {
        ClassModel cm = ClassFile.of().parse(bytes);
        String owner = cm.thisClass().asInternalName();
        desktopClasses.add(owner);
        cm.superclass().ifPresent(s -> {
            superClasses.put(owner, s.asInternalName());
            call(owner + "." + CLINIT, s.asInternalName() + "." + CLINIT, true);
        });
        for (MethodModel mm : cm.methods()) {
            String name = mm.methodName().stringValue();
            String method = owner + "." + name + mm.methodType().stringValue();
            if ((mm.flags().flagsMask() & ClassFile.ACC_NATIVE) != 0) {
                nativeMethods.add(method);
            }
            if (name.equals("<clinit>")) {
                classesWithClinit.add(owner);
            }
            mm.code().ifPresent(code -> {
                String lastClassConstant = null;
                for (CodeElement element : code) {
                    String classConstant = null;
                    switch (element) {
                        case ConstantInstruction ci -> {
                            if (ci.constantValue() instanceof ClassDesc cd) {
                                classConstant = internalName(cd);
                            }
                        }
                        case InvokeInstruction ii -> {
                            String o = ii.owner().asInternalName();
                            String n = ii.name().stringValue();
                            if ((n.equals("ensureClassInitialized") || n.equals("ensureInitialized"))
                                    && lastClassConstant != null) {
                                // AWTAccessor.ensureClassInitialized(X.class), Lookup.ensureInitialized(X.class)
                                call(method, lastClassConstant + "." + CLINIT, true);
                            }
                            call(method, o + "." + n + ii.type().stringValue(), false);
                            if (ii.opcode() == Opcode.INVOKESTATIC || n.equals("<init>")) {
                                call(method, o + "." + CLINIT, true);
                            }
                            api(method, o, n);
                        }
                        case FieldInstruction fi -> {
                            if (fi.opcode() == Opcode.GETSTATIC || fi.opcode() == Opcode.PUTSTATIC) {
                                call(method, fi.owner().asInternalName() + "." + CLINIT, true);
                            }
                        }
                        case NewObjectInstruction ni -> call(method, ni.className().asInternalName() + "." + CLINIT, true);
                        case InvokeDynamicInstruction indy -> {
                            // lambdas passed to doPrivileged and the like are executed immediately : follow them
                            for (ConstantDesc arg : indy.bootstrapArgs()) {
                                if (arg instanceof DirectMethodHandleDesc h) {
                                    String o = internalName(h.owner());
                                    if (o != null) {
                                        call(method, o + "." + h.methodName() + h.lookupDescriptor(), false);
                                    }
                                }
                            }
                        }
                        default -> {
                        }
                    }
                    if (element instanceof java.lang.classfile.Instruction) {
                        lastClassConstant = classConstant;
                    }
                }
            });
        }
    }

    static void api(String method, String owner, String name) {
        switch (owner) {
            case "java/lang/System", "java/lang/Runtime" -> {
                if (name.equals("loadLibrary") || name.equals("load")) {
                    hazard(method, LIBRARY, owner + "." + name);
                } else if (name.startsWith("getProperty") || name.equals("getenv")) {
                    hazard(method, PROPERTY, owner + "." + name);
                }
            }
            case "jdk/internal/loader/BootLoader", "jdk/internal/loader/NativeLibraries" -> {
                if (name.startsWith("loadLibrary") || name.equals("load")) {
                    hazard(method, LIBRARY, owner + "." + name);
                }
            }
            case "sun/security/action/GetPropertyAction", "sun/security/action/GetBooleanAction",
                    "sun/security/action/GetIntegerAction" ->
                hazard(method, PROPERTY, owner + "." + name);
            case "java/lang/Boolean", "java/lang/Integer", "java/lang/Long" -> {
                if (name.equals("getBoolean") || name.equals("getInteger") || name.equals("getLong")) {
                    hazard(method, PROPERTY, owner + "." + name);
                }
            }
            case "java/util/Locale", "java/util/TimeZone" -> {
                if (name.equals("getDefault")) {
                    hazard(method, PROPERTY, owner + "." + name);
                }
            }
            case "java/util/ResourceBundle" -> {
                if (name.equals("getBundle")) {
                    hazard(method, BUNDLE, owner + "." + name);
                }
            }
            case "java/lang/Thread" -> {
                if (name.equals("start") || name.equals("startVirtualThread") || name.equals("ofPlatform")) {
                    hazard(method, THREAD, owner + "." + name);
                }
            }
            case "java/util/Timer", "java/util/concurrent/ThreadPoolExecutor",
                    "java/util/concurrent/ScheduledThreadPoolExecutor" -> {
                if (name.equals("<init>")) {
                    hazard(method, THREAD, owner + "." + name);
                }
            }
            case "java/util/concurrent/Executors" -> hazard(method, THREAD, owner + "." + name);
            case "java/nio/ByteBuffer" -> {
                if (name.equals("allocateDirect")) {
                    hazard(method, MEMORY, owner + "." + name);
                }
            }
            case "java/lang/ref/Cleaner" -> hazard(method, MEMORY, owner + "." + name);
            case "jdk/internal/misc/Unsafe", "sun/misc/Unsafe" -> {
                if (name.startsWith("allocateMemory") || name.startsWith("reallocateMemory") || name.equals("freeMemory")
                        || name.equals("setMemory") || name.equals("copyMemory")) {
                    hazard(method, MEMORY, owner + "." + name);
                }
            }
            case "java/io/RandomAccessFile", "java/io/FileInputStream", "java/io/FileOutputStream" -> {
                if (name.equals("getChannel")) {
                    hazard(method, CHANNEL, owner + "." + name);
                }
            }
            case "java/nio/channels/FileChannel" -> {
                if (name.equals("open")) {
                    hazard(method, CHANNEL, owner + "." + name);
                }
            }
            case "java/nio/file/Files" -> {
                if (name.equals("newByteChannel")) {
                    hazard(method, CHANNEL, owner + "." + name);
                }
            }
            case "java/awt/Toolkit", "java/awt/GraphicsEnvironment", "sun/awt/SunToolkit" -> {
                if (name.equals("getDefaultToolkit") || name.equals("getLocalGraphicsEnvironment")
                        || name.equals("isHeadless") || name.startsWith("getScreen")) {
                    hazard(method, TOOLKIT, owner + "." + name);
                }
            }
            default -> {
                if (owner.startsWith("java/lang/foreign/")) {
                    hazard(method, MEMORY, owner + "." + name);
                }
            }
        }
    }

    static void call(String from, String to, boolean classInit) {
        calls.computeIfAbsent(from, k -> new ArrayList<>()).add(new Call(to, classInit));
    }

    static void hazard(String method, String category, String witness) {
        hazards.computeIfAbsent(method, k -> new LinkedHashMap<>()).putIfAbsent(category, witness);
    }

    static void propagate(Set<String> runtimeInitialized) {
        for (String m : nativeMethods) {
            hazard(m, NATIVE, "native method");
        }
        Map<String, Set<String>> callers = new HashMap<>();
        calls.forEach((from, targets) -> targets.forEach(t -> {
            String targetClass = t.target.substring(0, t.target.indexOf('.'));
            // a run time initialized class stops the propagation of its own initializer
            if (t.classInit && runtimeInitialized.contains(targetClass)) {
                return;
            }
            callers.computeIfAbsent(t.target, k -> new HashSet<>()).add(from);
        }));
        Deque<String> queue = new ArrayDeque<>(hazards.keySet());
        while (!queue.isEmpty()) {
            String m = queue.poll();
            Map<String, String> h = hazards.get(m);
            for (String caller : callers.getOrDefault(m, Set.of())) {
                boolean changed = false;
                for (String category : h.keySet()) {
                    if (!hazards.computeIfAbsent(caller, k -> new LinkedHashMap<>()).containsKey(category)) {
                        hazards.get(caller).put(category, m);
                        changed = true;
                    }
                }
                if (changed) {
                    queue.add(caller);
                }
            }
        }
    }

    static String path(String method, String category) {
        List<String> steps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = method;
        while (current != null && seen.add(current) && steps.size() < 12) {
            String next = hazards.getOrDefault(current, Map.of()).get(category);
            if (next == null || !hazards.containsKey(next)) {
                if (next != null) {
                    steps.add(next);
                }
                break;
            }
            steps.add(shorten(next));
            current = next;
        }
        return String.join(" -> ", steps);
    }

    static String shorten(String method) {
        int paren = method.indexOf('(');
        return (paren > 0 ? method.substring(0, paren) : method).replace('/', '.');
    }
}
