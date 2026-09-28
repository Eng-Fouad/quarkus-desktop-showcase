import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Diffs the reachability metadata recorded by the GraalVM tracing agent (JVM run of the showcase, see
 * {@code tools/Snapshot.java --trace} and {@code tools/Cycle.java --trace}) against what quarkus-desktop registers for a
 * platform : the common lists and the lists of that platform of
 * {@code io.quarkiverse.desktop.awt.deployment.AwtClassesAndResources} and
 * {@code io.quarkiverse.desktop.swing.deployment.SwingClassesAndResources}, read from the deployment jars installed in
 * the local Maven repository (so the installed snapshot is compared, not the sources). What the application registers
 * itself (its {@code reachability-metadata.json}, {@link #APP_METADATA} by default) is subtracted and counted apart.
 * <p>
 * usage: java tools/MetadataDiff.java reachability-metadata.json [windows|linux|mac] [--awt-only] [--no-java-beans]
 * [--version=999-SNAPSHOT] [--quarkus-version=3.40.0] [--repository=local Maven repository of quarkus-desktop]
 * [--app-metadata=reachability-metadata.json of the application]
 * <p>
 * Run it on the platform of the trace : the universe is the JDK running the tool, so on another platform the classes
 * and resources that do not exist in this JDK cannot be told apart from those of the other platform (only the lookups
 * that quarkus-desktop registers by name are recognized, the other ones are reported as not registered or not
 * included), and the platform lists are not checked for stale entries.
 * <p>
 * Universe : the classes and resources of the JDK modules java.desktop, java.datatransfer, jdk.unsupported.desktop and
 * jdk.accessibility ({@code jrt:/} of the JDK running the tool) and the packages of these modules. List entries are
 * class names, package names (covering their classes and sub packages), {@code "fqcn#name(paramType,...)"} methods and
 * {@code "fqcn#field"} fields, per the naming convention {@code [WINDOWS_|LINUX_|MAC_]<KIND>} of static String[] fields.
 * The constants of the extension code are read too : the classes registered for serialization ({@code *SERIALIZABLE*})
 * and the resource bundles that the JDK looks up but does not have ({@code ABSENT_RESOURCE_BUNDLES}).
 * <p>
 * Output (Markdown, on stdout) : the agent-recorded accesses (JNI, reflection, resources, resource bundles,
 * serialization, dynamic proxies) that the lists do not cover, grouped by kind ; names also found in the constant pool
 * of quarkus-awt's {@code AwtProcessor} (registered by io.quarkus:quarkus-awt, heuristic) ; platform entries not used by
 * the run ; lookups of classes and resources that do not exist in this JDK ; and list entries that do not exist in this
 * JDK (stale, typos, or another platform).
 */
public class MetadataDiff {

    static final Path M2 = Path.of(System.getProperty("user.home"), ".m2", "repository");
    /** The reachability metadata of the showcase itself (relative to the root of the repository). */
    static final Path APP_METADATA = Path.of("src/main/resources/META-INF/native-image/io.quarkiverse.desktop.showcase/"
            + "quarkus-desktop-showcase/reachability-metadata.json");
    static final List<String> MODULES = List.of("java.desktop", "java.datatransfer", "jdk.unsupported.desktop",
            "jdk.accessibility");
    /** Packages of the desktop modules on every platform (for traces recorded on another operating system). */
    static final List<String> DESKTOP_PACKAGES = List.of("java.awt", "javax.swing", "sun.awt", "sun.java2d", "sun.font",
            "sun.print", "javax.print", "javax.imageio", "com.sun.imageio", "sun.swing", "com.sun.java.swing", "javax.sound",
            "com.sun.media.sound", "java.beans", "com.sun.beans", "sun.datatransfer", "javax.accessibility",
            "com.sun.java.accessibility", "com.sun.accessibility", "jdk.swing.interop", "java.applet", "sun.lwawt",
            "com.apple.eawt", "com.apple.laf");
    /** A method entry of the lists : {@code fqcn#name(paramType,...)}. */
    static final Pattern METHOD_ENTRY = Pattern.compile("([^#]+)#([^(]+)\\((.*)\\)");
    static final List<String> KINDS = List.of("RUNTIME_INITIALIZED_PACKAGES", "RUNTIME_INITIALIZED_CLASSES",
            "REFLECTIVE_CLASSES", "REFLECTIVE_CONSTRUCTORS", "REFLECTIVE_METHODS", "REFLECTIVE_FIELDS",
            "JNI_RUNTIME_ACCESS_CLASSES", "JNI_RUNTIME_ACCESS_METHODS", "JNI_RUNTIME_ACCESS_FIELDS", "RESOURCE_BUNDLES",
            "RESOURCE_GLOBS", "SERVICE_PROVIDERS", "REFLECTIVE_PUBLIC_MEMBERS", "JAVA_BEANS_CLASSES", "REFLECTIVE_TYPES",
            "NEGATIVE_CLASS_LOOKUPS", "METHOD_LOOKUPS", "RESOURCE_LOOKUPS");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: java tools/MetadataDiff.java reachability-metadata.json [windows|linux|mac] [--awt-only] "
                    + "[--no-java-beans] [--version=999-SNAPSHOT] [--quarkus-version=3.40.0] [--repository=path] "
                    + "[--app-metadata=path]");
            System.exit(2);
        }
        Path metadata = Path.of(args[0]);
        String platform = currentPlatform();
        boolean awtOnly = false;
        // the JAVA_BEANS_CLASSES lists count as registered (quarkus.desktop.*.java-beans.jdk-classes=true : the AWT one by
        // default, the Swing one in the application.properties of the showcase) unless --no-java-beans
        boolean javaBeans = true;
        String version = "999-SNAPSHOT";
        String quarkusVersion = quarkusVersion();
        // the local repository of the quarkus-desktop jars (default : ~/.m2/repository)
        Path repository = M2;
        // what the application registers itself
        Path appMetadata = APP_METADATA;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--awt-only")) {
                awtOnly = true;
            } else if (arg.equals("--no-java-beans")) {
                javaBeans = false;
            } else if (arg.startsWith("--version=")) {
                version = arg.substring("--version=".length());
            } else if (arg.startsWith("--quarkus-version=")) {
                quarkusVersion = arg.substring("--quarkus-version=".length());
            } else if (arg.startsWith("--repository=")) {
                repository = Path.of(arg.substring("--repository=".length()));
            } else if (arg.startsWith("--app-metadata=")) {
                appMetadata = Path.of(arg.substring("--app-metadata=".length()));
            } else {
                platform = arg.toLowerCase(Locale.ROOT).startsWith("win") ? "WINDOWS"
                        : arg.toLowerCase(Locale.ROOT).startsWith("mac") ? "MAC" : "LINUX";
            }
        }
        boolean samePlatform = platform.equals(currentPlatform());

        Universe universe = Universe.load();
        Registrations reg = new Registrations(universe, javaBeans);
        List<String> sources = new ArrayList<>();
        for (String[] extension : extensions(awtOnly)) {
            Path jar = repository.resolve("io/quarkiverse/desktop/" + extension[0] + "/" + version + "/" + extension[0] + "-"
                    + version + ".jar");
            if (!Files.exists(jar)) {
                sources.add(jar + " : NOT FOUND (install quarkus-desktop)");
                continue;
            }
            Map<String, String[]> lists = readLists(jar, extension[1]);
            sources.add(jar + " (" + Files.getLastModifiedTime(jar) + ") : " + lists.size() + " lists");
            reg.add(extension[2], lists, platform);
            reg.serializable.addAll(constantValues(jar, extension[1], "SERIALIZABLE"));
            // bundles that the JDK looks up but does not have, registered so that the lookup fails as in the JVM (and,
            // in exact mode, the lookups of their locale variants and providers : see negativeLookupRegistered)
            Set<String> absentBundles = constantValues(jar, extension[1], "ABSENT_RESOURCE_BUNDLES");
            reg.bundles.addAll(absentBundles);
            reg.absentBundles.addAll(absentBundles);
        }
        Path awtDeployment = M2.resolve("io/quarkus/quarkus-awt-deployment/" + quarkusVersion + "/quarkus-awt-deployment-"
                + quarkusVersion + ".jar");
        Set<String> quarkusAwt = new TreeSet<>();
        List<Pattern> quarkusAwtGlobs = new ArrayList<>();
        if (Files.exists(awtDeployment)) {
            for (String constant : stringConstants(awtDeployment, "io/quarkus/awt/deployment/AwtProcessor.class")) {
                if (universe.isClassOrPackage(constant) || isDesktopName(constant)) {
                    quarkusAwt.add(constant);
                } else if ((constant.contains("*") || constant.contains("/")) && !constant.contains(" ")) {
                    quarkusAwtGlobs.add(globToRegex(constant));
                }
            }
            sources.add(awtDeployment + " : " + quarkusAwt.size() + " class names, " + quarkusAwtGlobs.size() + " globs");
        } else {
            sources.add(awtDeployment + " : NOT FOUND");
        }

        AppRegistrations app = AppRegistrations.load(appMetadata);
        sources.add(appMetadata + (app == null ? " : NOT FOUND (the registrations of the application are not subtracted)"
                : " : " + app.types.size() + " types, " + app.members.size() + " members, " + app.globs.size()
                        + " resource globs of the application"));
        if (app == null) {
            app = new AppRegistrations();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> md = (Map<String, Object>) new Compare.JsonParser(Files.readString(metadata)).parse();

        Map<String, String> missingJni = new TreeMap<>();
        Map<String, String> missingReflection = new TreeMap<>();
        Set<String> negativeLookups = new TreeSet<>();
        Set<String> registeredNegativeLookups = new TreeSet<>();
        Set<String> registeredResourceLookups = new TreeSet<>();
        Set<String> registeredByApp = new TreeSet<>();
        Map<String, String> byQuarkusAwt = new TreeMap<>();
        Set<String> serialization = new TreeSet<>();
        Set<String> proxies = new TreeSet<>();
        Set<String> arrays = new TreeSet<>();
        Map<String, String> otherJni = new TreeMap<>();
        Set<String> usedJni = new HashSet<>();
        Set<String> usedReflection = new HashSet<>();

        // resource bundles looked up by the run : their classes and properties files are reported as bundles
        Set<String> agentBundles = new TreeSet<>();
        for (Object o : (List<?>) md.getOrDefault("resources", List.of())) {
            if (map(o).get("bundle") != null) {
                agentBundles.add(String.valueOf(map(o).get("bundle")));
            }
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object o : (List<?>) md.getOrDefault("reflection", List.of())) {
            entries.add(map(o));
        }
        // older agents : a separate "jni" section
        for (Object o : (List<?>) md.getOrDefault("jni", List.of())) {
            Map<String, Object> entry = new LinkedHashMap<>(map(o));
            entry.put("jniAccessible", true);
            entries.add(entry);
        }
        for (Map<String, Object> entry : entries) {
            Object typeValue = entry.get("type");
            if (typeValue instanceof Map<?, ?> proxy && proxy.get("proxy") instanceof List<?> interfaces) {
                if (interfaces.stream().map(String::valueOf).anyMatch(n -> universe.isDesktop(n))) {
                    proxies.add(String.valueOf(interfaces));
                }
                continue;
            }
            if (!(typeValue instanceof String type)) {
                continue;
            }
            boolean jni = Boolean.TRUE.equals(entry.get("jniAccessible"));
            String component = type.replace("[]", "");
            boolean desktop = universe.isDesktop(component);
            if (Boolean.TRUE.equals(entry.get("serializable")) && desktop && !reg.serializable.contains(type)) {
                serialization.add(type);
            }
            if (!desktop) {
                if (jni && !type.startsWith("sun.launcher.") && (type.startsWith("java.") || type.startsWith("javax.")
                        || type.startsWith("jdk.") || type.startsWith("sun.") || type.startsWith("com.sun."))) {
                    otherJni.put(type, members(entry));
                }
                continue;
            }
            if (type.endsWith("[]")) {
                arrays.add(type + (jni ? " (JNI)" : ""));
                continue;
            }
            List<String> missing = jni ? reg.missingJni(type, entry) : reg.missingReflection(type, entry);
            (jni ? usedJni : usedReflection).add(type);
            if (missing.isEmpty() || (!jni && missing.equals(List.of("type")) && isBundle(type, agentBundles))) {
                continue;
            }
            if (!jni) {
                // registered by the application (its own beans probed in java.beans, the JDK members it reads)
                List<String> byApp = app.covered(type, missing);
                if (!byApp.isEmpty()) {
                    missing = new ArrayList<>(missing);
                    missing.removeAll(byApp);
                    if (missing.isEmpty()) {
                        registeredByApp.add(type + "  " + String.join(" ", byApp));
                        continue;
                    }
                }
            }
            String description = String.join(" ", missing);
            // exact class names only : the packages that quarkus-awt names are run time initialization entries, not
            // registrations (a package prefix match hid e.g. the JNI callbacks of sun.awt.dnd.SunDropTargetContextPeer)
            if (quarkusAwt.contains(type)) {
                byQuarkusAwt.put((jni ? "JNI " : "reflection ") + type, description);
            } else if (jni) {
                missingJni.put(type, description);
            } else if (!universe.classes.containsKey(type) && missing.equals(List.of("type"))
                    && reg.negativeLookupRegistered(type)) {
                // Class.forName of a class that does not exist (BeanInfo, Customizer and PersistenceDelegate searches of
                // java.beans, class names probed by Nimbus...) : the JDK expects the ClassNotFoundException, which a
                // native image throws too, unless it is built with --exact-reachability-metadata : quarkus-desktop
                // registers them (NEGATIVE_CLASS_LOOKUPS, and the JavaBeans probes of the classes it registers). These
                // names do not depend on the platform : recognized on any platform
                registeredNegativeLookups.add(type);
            } else if (samePlatform && !universe.classes.containsKey(type) && missing.equals(List.of("type"))) {
                // on the platform of the trace only : on another one, the class may exist there
                negativeLookups.add(type);
            } else {
                missingReflection.put(type, description);
            }
        }

        Set<String> missingResources = new TreeSet<>();
        Set<String> absentResources = new TreeSet<>();
        Set<String> missingBundles = new TreeSet<>();
        for (Object o : (List<?>) md.getOrDefault("resources", List.of())) {
            Map<String, Object> entry = map(o);
            if (entry.get("bundle") != null) {
                String bundle = String.valueOf(entry.get("bundle"));
                if (universe.isDesktop(bundle) && !reg.bundles.contains(bundle)) {
                    missingBundles.add(bundle + (entry.get("module") != null ? " (module " + entry.get("module") + ")" : ""));
                }
                continue;
            }
            String glob = String.valueOf(entry.get("glob"));
            String module = entry.get("module") == null ? null : String.valueOf(entry.get("module"));
            boolean desktop = (module != null && MODULES.contains(module)) || universe.resources.contains(glob);
            if (!desktop || glob.endsWith(".class")) {
                continue;
            }
            if (reg.resourceCovered(glob) || reg.serializedFormProbe(glob) || (glob.endsWith(".properties")
                    && isBundle(glob.substring(0, glob.length() - ".properties".length()).replace('/', '.'), agentBundles))) {
                continue;
            }
            if (app.resourceCovered(glob)) {
                registeredByApp.add("resource " + glob);
                continue;
            }
            if (reg.resourceLookups.stream().anyMatch(p -> p.matcher(glob).matches())) {
                // registered by name (RESOURCE_LOOKUPS) : recognized on any platform
                registeredResourceLookups.add(glob);
                continue;
            }
            if (quarkusAwtGlobs.stream().anyMatch(p -> p.matcher(glob).matches())) {
                byQuarkusAwt.put("resource " + glob, "");
                continue;
            }
            if (samePlatform && module != null && !universe.resources.contains(glob)) {
                // a resource of a desktop module that the JDK looks for but does not have (Beans.instantiate probes
                // java/awt/Button.ser, SwingUtilities2.makeIcon the icons of every look and feel class up to Basic) ;
                // on the platform of the trace only : on another one, the resource may exist there
                absentResources.add(glob + " (module " + module + ")");
                continue;
            }
            missingResources.add(glob + (module != null ? " (module " + module + ")" : ""));
        }

        System.out.println("# Tracing agent metadata vs quarkus-desktop (" + platform + (awtOnly ? ", awt lists only" : "")
                + ")");
        System.out.println();
        System.out.println("- metadata: " + metadata);
        sources.forEach(s -> System.out.println("- lists: " + s));
        System.out.println("- JDK: " + System.getProperty("java.home") + " (" + Runtime.version() + "), universe: "
                + universe.classes.size() + " classes, " + universe.resources.size() + " resources of " + MODULES);
        section("JNI accesses not registered", missingJni);
        section("Reflection accesses not registered", missingReflection);
        list("Lookups of classes that do not exist in this JDK, not registered (expected to fail : only an issue with "
                + "--exact-reachability-metadata)", negativeLookups);
        System.out.println("\n## Lookups of classes that do not exist in this JDK, registered by quarkus-desktop "
                + "(NEGATIVE_CLASS_LOOKUPS, JavaBeans probes of its classes, ABSENT_RESOURCE_BUNDLES lookups) ("
                + registeredNegativeLookups.size() + ")");
        list("Registered by the application (its reachability-metadata.json : the lookups of its own beans, the JDK "
                + "members it reads by reflection)", registeredByApp);
        list("Resources not included", missingResources);
        list("Lookups of resources that do not exist in this JDK (expected to fail, a native executable finds none either : "
                + "only an issue with --exact-reachability-metadata)", absentResources);
        System.out.println("\n## Lookups of resources that do not exist in this JDK, registered by quarkus-desktop "
                + "(RESOURCE_LOOKUPS) (" + registeredResourceLookups.size() + ")");
        list("Resource bundles not included", missingBundles);
        list("Serialization of desktop types (no list kind : the *SERIALIZABLE* constants of the extension code)",
                serialization);
        list("Dynamic proxies with desktop interfaces (no list kind : register in the extension code)", proxies);
        list("Array types of desktop classes (reflection on array classes, usually harmless)", arrays);
        section("Not in the lists but named by io.quarkus:quarkus-awt (heuristic : constant pool of AwtProcessor)",
                byQuarkusAwt);
        section("JNI accesses to JDK types outside the desktop modules (GraalVM or Quarkus may register them : verify with "
                + "a native run)", otherJni);

        for (String kind : List.of("JNI_RUNTIME_ACCESS_CLASSES", "REFLECTIVE_CLASSES", "REFLECTIVE_CONSTRUCTORS")) {
            Set<String> unused = new TreeSet<>();
            for (String name : reg.platformEntries(kind)) {
                Set<String> used = kind.startsWith("JNI") ? usedJni : usedReflection;
                if (universe.classes.containsKey(name) && !used.contains(name)) {
                    unused.add(name);
                }
            }
            list(platform + "_" + kind + " entries not used in this run (candidates to verify, not to remove blindly)",
                    unused);
        }
        Set<String> stale = reg.stale(samePlatform);
        list("Registered names that do not exist in this JDK (stale, typo, or another platform"
                + (samePlatform ? "" : " : " + platform + " lists not checked, the JDK is " + currentPlatform()) + ")",
                stale);
    }

    static List<String[]> extensions(boolean awtOnly) {
        List<String[]> list = new ArrayList<>();
        list.add(new String[] { "quarkus-desktop-awt-deployment", "io.quarkiverse.desktop.awt.deployment.AwtClassesAndResources",
                "awt" });
        if (!awtOnly) {
            list.add(new String[] { "quarkus-desktop-swing-deployment",
                    "io.quarkiverse.desktop.swing.deployment.SwingClassesAndResources", "swing" });
        }
        return list;
    }

    /**
     * The values of the static {@code String} and {@code List} fields of {@code className} whose name contains
     * {@code fragment} : the classes registered for serialization by the extension code ({@code SERIALIZABLE}), the
     * absent resource bundles it registers ({@code ABSENT_RESOURCE_BUNDLES}).
     */
    static Set<String> constantValues(Path jar, String className, String fragment) throws Exception {
        Set<String> classes = new TreeSet<>();
        try (URLClassLoader cl = new URLClassLoader(new URL[] { jar.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
            Class<?> c = Class.forName(className, true, cl);
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getName().contains(fragment)) {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (value instanceof String s) {
                        classes.add(s);
                    } else if (value instanceof List<?> l) {
                        l.forEach(v -> classes.add(String.valueOf(v)));
                    }
                }
            }
        }
        return classes;
    }

    /**
     * Every static String[] field of {@code className}, read from {@code jar} in an isolated class loader.
     */
    static Map<String, String[]> readLists(Path jar, String className) throws Exception {
        Map<String, String[]> lists = new TreeMap<>();
        try (URLClassLoader cl = new URLClassLoader(new URL[] { jar.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
            Class<?> c = Class.forName(className, true, cl);
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == String[].class) {
                    f.setAccessible(true);
                    String[] values = (String[]) f.get(null);
                    lists.put(f.getName(), values == null ? new String[0] : values);
                }
            }
        }
        return lists;
    }

    /**
     * What the lists of one platform register.
     */
    static final class Registrations {
        final Universe universe;
        final Set<String> reflectiveClasses = new HashSet<>();
        // classes registered as types (REFLECTIVE_TYPES) and lookups expected to fail (NEGATIVE_CLASS_LOOKUPS)
        final Set<String> types = new HashSet<>();
        // the REFLECTIVE_TYPES values of the AWT extension (whose processor registers the .ser lookups)
        final Set<String> awtTypes = new HashSet<>();
        final Set<String> negativeClassLookups = new HashSet<>();
        private Set<String> javaBeansProbes;
        private Set<String> javaBeansClasses;
        private Set<String> serializedFormClasses;
        final Set<String> reflectiveConstructors = new HashSet<>();
        final Set<String> reflectiveMethods = new HashSet<>();
        final Set<String> reflectiveMethodOwners = new HashSet<>();
        final Set<String> reflectiveFields = new HashSet<>();
        final Set<String> providers = new HashSet<>();
        final Set<String> jniClasses = new HashSet<>();
        final Set<String> jniMembers = new HashSet<>();
        final Set<String> jniMemberOwners = new HashSet<>();
        final Set<String> bundles = new HashSet<>();
        // the ABSENT_RESOURCE_BUNDLES constants (also in bundles)
        final Set<String> absentBundles = new HashSet<>();
        final List<Pattern> globs = new ArrayList<>();
        // RESOURCE_LOOKUPS : resources that the JDK looks up and does not have, registered for exact metadata
        final List<Pattern> resourceLookups = new ArrayList<>();
        final Map<String, List<String>> platformLists = new TreeMap<>();
        // classes registered with their public constructors, methods (inherited ones included) and fields
        final Set<String> publicMembers = new HashSet<>();
        final Set<String> serializable = new HashSet<>();
        final boolean javaBeans;
        // list name (source.FIELD) -> entries, for the stale check
        final Map<String, String[]> all = new TreeMap<>();

        Registrations(Universe universe, boolean javaBeans) {
            this.universe = universe;
            this.javaBeans = javaBeans;
        }

        void add(String source, Map<String, String[]> lists, String platform) {
            lists.forEach((name, values) -> all.put(source + "." + name, values));
            for (String kind : KINDS) {
                List<String> values = new ArrayList<>();
                values.addAll(List.of(lists.getOrDefault(kind, new String[0])));
                List<String> platformValues = List.of(lists.getOrDefault(platform + "_" + kind, new String[0]));
                values.addAll(platformValues);
                platformLists.computeIfAbsent(kind, k -> new ArrayList<>()).addAll(platformValues);
                for (String value : values) {
                    String v = value.replace(" ", "");
                    switch (kind) {
                        case "REFLECTIVE_CLASSES" -> reflectiveClasses.add(v);
                        case "REFLECTIVE_CONSTRUCTORS" -> reflectiveConstructors.add(v);
                        case "REFLECTIVE_METHODS" -> {
                            reflectiveMethods.add(v);
                            reflectiveMethodOwners.add(owner(v));
                        }
                        case "REFLECTIVE_FIELDS" -> {
                            reflectiveFields.add(v);
                            reflectiveMethodOwners.add(owner(v));
                        }
                        case "SERVICE_PROVIDERS" -> providers.add(v);
                        case "JNI_RUNTIME_ACCESS_CLASSES" -> jniClasses.add(v);
                        case "JNI_RUNTIME_ACCESS_METHODS", "JNI_RUNTIME_ACCESS_FIELDS" -> {
                            jniMembers.add(v);
                            jniMemberOwners.add(owner(v));
                        }
                        case "RESOURCE_BUNDLES" -> bundles.add(v.contains(":") ? v.substring(v.indexOf(':') + 1) : v);
                        case "RESOURCE_GLOBS" -> globs.add(globToRegex(v));
                        case "RESOURCE_LOOKUPS" -> resourceLookups.add(globToRegex(v));
                        case "REFLECTIVE_PUBLIC_MEMBERS" -> publicMembers.add(v);
                        case "REFLECTIVE_TYPES" -> {
                            types.add(v);
                            if (source.equals("awt")) {
                                awtTypes.add(v);
                            }
                        }
                        case "NEGATIVE_CLASS_LOOKUPS" -> negativeClassLookups.add(v);
                        case "JAVA_BEANS_CLASSES" -> {
                            if (javaBeans) {
                                publicMembers.add(v);
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
        }

        List<String> platformEntries(String kind) {
            return platformLists.getOrDefault(kind, List.of());
        }

        /**
         * The classes that the processor of quarkus-desktop registers the JavaBeans lookups for : the classes registered
         * with their public members and the REFLECTIVE_TYPES values (not the var handles of java.lang.invoke).
         */
        Set<String> javaBeansClasses() {
            if (javaBeansClasses == null) {
                javaBeansClasses = new TreeSet<>(publicMembers);
                types.stream().filter(t -> !t.startsWith("java.lang.invoke.")).forEach(javaBeansClasses::add);
            }
            return javaBeansClasses;
        }

        /**
         * The serialized form that Beans.instantiate looks up first ({@code java/awt/Button.ser}) in a package of the
         * classes that the AWT extension registers the JavaBeans lookups for : quarkus-desktop registers these lookups
         * (ReachabilityLookups.javaBeansSerializedForms, called by DesktopAwtProcessor.reachabilityLookups for the
         * classes registered with their public members, of both extensions, and the REFLECTIVE_TYPES values of the AWT
         * extension ; DesktopSwingProcessor registers no serialized forms for its REFLECTIVE_TYPES).
         */
        boolean serializedFormProbe(String resource) {
            if (!resource.endsWith(".ser")) {
                return false;
            }
            if (serializedFormClasses == null) {
                serializedFormClasses = new TreeSet<>(publicMembers);
                awtTypes.stream().filter(t -> !t.startsWith("java.lang.invoke.")).forEach(serializedFormClasses::add);
            }
            String packageName = resource.substring(0, Math.max(0, resource.lastIndexOf('/'))).replace('/', '.');
            return serializedFormClasses.stream().anyMatch(c -> c.startsWith(packageName + ".")
                    && c.lastIndexOf('.') == packageName.length());
        }

        /**
         * A lookup of a class that does not exist is registered by quarkus-desktop : in a NEGATIVE_CLASS_LOOKUPS list, one
         * of the JavaBeans probes that the processor computes for the classes it registers for the JavaBeans API (the
         * classes with their public members and the REFLECTIVE_TYPES values, with their superclasses) : the same rule as
         * io.quarkiverse.desktop.awt.deployment.ReachabilityLookups.javaBeansTypes, or a lookup of an absent resource
         * bundle (ABSENT_RESOURCE_BUNDLES) : the bundle class, its locale variants and its provider interface
         * ({@code <package>.spi.<Name>Provider}), as ReachabilityLookups.missingBundles registers them (the locale
         * variants of the application locales only : any variant counts here).
         */
        boolean negativeLookupRegistered(String type) {
            if (negativeClassLookups.contains(type)) {
                return true;
            }
            for (String bundle : absentBundles) {
                int dot = bundle.lastIndexOf('.');
                if (type.equals(bundle) || type.startsWith(bundle + "_")
                        || type.equals(bundle.substring(0, dot + 1) + "spi." + bundle.substring(dot + 1) + "Provider")) {
                    return true;
                }
            }
            if (javaBeansProbes == null) {
                javaBeansProbes = new HashSet<>();
                Set<Class<?>> seen = new HashSet<>();
                for (String name : javaBeansClasses()) {
                    for (Class<?> c = Universe.loadAny(name); c != null && !c.isInterface() && seen.add(c);
                            c = c.getSuperclass()) {
                        String n = c.getName();
                        for (String suffix : List.of("BeanInfo", "Customizer", "PersistenceDelegate", "Editor")) {
                            javaBeansProbes.add(n + suffix);
                        }
                        javaBeansProbes.add("java.beans.MetaData$" + n.replace('.', '_') + "_PersistenceDelegate");
                        javaBeansProbes.add("com.sun.beans.editors." + n.substring(n.lastIndexOf('.') + 1) + "Editor");
                    }
                }
            }
            return javaBeansProbes.contains(type);
        }

        /**
         * {@code type} is in {@code set}, or in a package of {@code set}.
         */
        boolean in(Set<String> set, String type) {
            if (set.contains(type)) {
                return true;
            }
            for (String pkg = packageOf(type); pkg != null; pkg = packageOf(pkg)) {
                if (set.contains(pkg) && universe.packages.contains(pkg)) {
                    return true;
                }
            }
            return false;
        }

        List<String> missingReflection(String type, Map<String, Object> entry) {
            List<String> missing = new ArrayList<>();
            boolean all = in(reflectiveClasses, type);
            boolean constructors = all || in(reflectiveConstructors, type) || in(providers, type);
            boolean methods = all || in(providers, type);
            boolean typeRegistered = constructors || methods || reflectiveMethodOwners.contains(type)
                    || publicMembers.contains(type) || types.contains(type);
            if (!typeRegistered) {
                missing.add("type");
            }
            for (Map<String, Object> m : list(entry.get("methods"))) {
                String name = String.valueOf(m.get("name"));
                String signature = type + "#" + name + "(" + String.join(",", strings(m.get("parameterTypes"))) + ")";
                boolean covered = name.equals("<init>") ? constructors || reflectiveMethods.contains(signature)
                        : methods || reflectiveMethods.contains(signature);
                if (!covered && publicMember(type, name, strings(m.get("parameterTypes")), null)) {
                    covered = true;
                }
                if (!covered) {
                    missing.add("method " + signature.substring(type.length()));
                }
            }
            for (Map<String, Object> f : list(entry.get("fields"))) {
                if (!all && !reflectiveFields.contains(type + "#" + f.get("name"))
                        && !publicMember(type, null, null, String.valueOf(f.get("name")))) {
                    missing.add("field #" + f.get("name"));
                }
            }
            for (String flag : entry.keySet()) {
                if (flag.startsWith("all") && Boolean.TRUE.equals(entry.get(flag))) {
                    boolean covered = flag.contains("Constructors") ? constructors : flag.contains("Methods") ? methods : all;
                    if (!covered) {
                        missing.add(flag);
                    }
                }
            }
            if (Boolean.TRUE.equals(entry.get("unsafeAllocated")) && !all) {
                missing.add("unsafeAllocated");
            }
            return missing;
        }

        List<String> missingJni(String type, Map<String, Object> entry) {
            List<String> missing = new ArrayList<>();
            boolean all = in(jniClasses, type);
            if (!all && !jniMemberOwners.contains(type)) {
                missing.add("type");
            }
            for (Map<String, Object> m : list(entry.get("methods"))) {
                String signature = type + "#" + m.get("name") + "(" + String.join(",", strings(m.get("parameterTypes")))
                        + ")";
                if (!all && !jniMembers.contains(signature)) {
                    missing.add("method " + signature.substring(type.length()));
                }
            }
            for (Map<String, Object> f : list(entry.get("fields"))) {
                String signature = type + "#" + f.get("name");
                if (!all && !jniMembers.contains(signature)) {
                    missing.add("field " + signature.substring(type.length()));
                }
            }
            return missing;
        }

        /**
         * Whether a public constructor, method or field of {@code type} is registered by a public members registration
         * of {@code type} or of a subclass (Class.getMethods() of a subclass returns the inherited public methods).
         */
        boolean publicMember(String type, String method, List<String> parameterTypes, String field) {
            if (publicMembers.isEmpty()) {
                return false;
            }
            Class<?> declaring = Universe.loadAny(type);
            if (declaring == null || !Modifier.isPublic(declaring.getModifiers())) {
                return false;
            }
            try {
                if (field != null) {
                    if (!Modifier.isPublic(declaring.getDeclaredField(field).getModifiers())) {
                        return false;
                    }
                } else {
                    Class<?>[] parameters = new Class<?>[parameterTypes.size()];
                    for (int i = 0; i < parameters.length; i++) {
                        parameters[i] = Universe.loadAny(parameterTypes.get(i));
                        if (parameters[i] == null) {
                            return false;
                        }
                    }
                    int modifiers = method.equals("<init>") ? declaring.getDeclaredConstructor(parameters).getModifiers()
                            : declaring.getDeclaredMethod(method, parameters).getModifiers();
                    if (!Modifier.isPublic(modifiers)) {
                        return false;
                    }
                    if (method.equals("<init>")) {
                        return publicMembers.contains(type);
                    }
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                return false;
            }
            for (String registered : publicMembers) {
                Class<?> c = Universe.loadAny(registered);
                if (c != null && declaring.isAssignableFrom(c)) {
                    return true;
                }
            }
            return false;
        }

        boolean resourceCovered(String path) {
            if (globs.stream().anyMatch(p -> p.matcher(path).matches())) {
                return true;
            }
            if (path.endsWith(".properties")) {
                String base = path.substring(0, path.length() - ".properties".length()).replace('/', '.');
                for (String bundle : bundles) {
                    if (base.equals(bundle) || (base.startsWith(bundle + "_") && base.indexOf('.', bundle.length()) < 0)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Entries naming classes, packages or members that do not exist in the JDK running the tool.
         */
        Set<String> stale(boolean checkPlatformLists) {
            Set<String> stale = new TreeSet<>();
            all.forEach((list, values) -> {
                String field = list.substring(list.indexOf('.') + 1);
                boolean platformList = field.startsWith("WINDOWS_") || field.startsWith("LINUX_") || field.startsWith("MAC_");
                if (platformList && (!checkPlatformLists || !field.startsWith(currentPlatform() + "_"))) {
                    return;
                }
                String kind = platformList ? field.substring(field.indexOf('_') + 1) : field;
                if (kind.equals("RESOURCE_LOOKUPS")) {
                    // lookups expected to fail : the resources must not exist (the extension registers the ones that
                    // exist with RESOURCE_GLOBS)
                    for (String glob : values) {
                        Pattern pattern = globToRegex(glob);
                        if (universe.resources.stream().anyMatch(r -> pattern.matcher(r).matches())) {
                            stale.add(list + ": " + glob + " (a resource of the JDK matches : a RESOURCE_GLOBS entry)");
                        }
                    }
                    return;
                }
                if (kind.equals("RESOURCE_GLOBS")) {
                    for (String glob : values) {
                        Pattern pattern = globToRegex(glob);
                        if (!glob.startsWith("META-INF") && universe.resources.stream().noneMatch(r -> pattern.matcher(r).matches())
                                && Universe.allResources().stream().noneMatch(r -> pattern.matcher(r).matches())) {
                            stale.add(list + ": " + glob + " (matches no resource of the JDK)");
                        }
                    }
                    return;
                }
                for (String value : values) {
                    String v = value.replace(" ", "");
                    String problem = switch (kind) {
                        case "RESOURCE_BUNDLES" -> {
                            String bundle = v.contains(":") ? v.substring(v.indexOf(':') + 1) : v;
                            String properties = bundle.replace('.', '/') + ".properties";
                            // bundles of other JDK modules (the messages of the XML parser of java.xml...)
                            yield universe.classes.containsKey(bundle) || universe.resources.contains(properties)
                                    || Universe.loadAny(bundle) != null || Universe.allResources().contains(properties)
                                            ? null
                                            : "no such bundle";
                        }
                        case "REFLECTIVE_METHODS", "JNI_RUNTIME_ACCESS_METHODS" -> universe.checkMethod(v);
                        // lookups expected to fail : the class must not exist
                        case "NEGATIVE_CLASS_LOOKUPS" -> universe.classes.containsKey(v) || Universe.loadAny(v) != null
                                ? "the class exists"
                                : null;
                        // the class may not declare the method
                        case "METHOD_LOOKUPS" -> {
                            if (!METHOD_ENTRY.matcher(v).matches()) {
                                yield "not fqcn#name(paramType,...)";
                            }
                            String owner = v.substring(0, v.indexOf('#'));
                            yield universe.classes.containsKey(owner) || Universe.loadAny(owner) != null ? null
                                    : "no such class";
                        }
                        case "JNI_RUNTIME_ACCESS_FIELDS", "REFLECTIVE_FIELDS" -> universe.checkField(v);
                        // entries outside the desktop modules (java.lang.String, byte[]...) are checked in the whole JDK
                        default -> universe.isClassOrPackage(v) || Universe.loadAny(v) != null ? null
                                : "no such class or package";
                    };
                    if (problem != null) {
                        stale.add(list + ": " + value + " (" + problem + ")");
                    }
                }
            });
            return stale;
        }
    }

    /**
     * What the application registers itself in its {@code reachability-metadata.json} : types with their methods and
     * fields (the reflection section, no JNI), and resource globs.
     */
    static final class AppRegistrations {
        final Set<String> types = new HashSet<>();
        // "fqcn#name(paramType,...)" methods and "fqcn#field" fields
        final Set<String> members = new HashSet<>();
        final List<Pattern> globs = new ArrayList<>();

        /**
         * The registrations of the metadata file {@code path}, {@code null} when it does not exist.
         */
        static AppRegistrations load(Path path) throws IOException {
            if (!Files.exists(path)) {
                return null;
            }
            AppRegistrations app = new AppRegistrations();
            Map<String, Object> md = map(new Compare.JsonParser(Files.readString(path)).parse());
            for (Object o : (List<?>) md.getOrDefault("reflection", List.of())) {
                Map<String, Object> entry = map(o);
                if (!(entry.get("type") instanceof String type)) {
                    continue;
                }
                app.types.add(type);
                for (Map<String, Object> m : list(entry.get("methods"))) {
                    app.members.add(type + "#" + m.get("name") + "(" + String.join(",", strings(m.get("parameterTypes")))
                            + ")");
                }
                for (Map<String, Object> f : list(entry.get("fields"))) {
                    app.members.add(type + "#" + f.get("name"));
                }
            }
            for (Object o : (List<?>) md.getOrDefault("resources", List.of())) {
                Object glob = map(o).get("glob");
                if (glob != null) {
                    app.globs.add(globToRegex(String.valueOf(glob)));
                }
            }
            return app;
        }

        /**
         * The items of {@code missing} ({@link Registrations#missingReflection} : {@code type},
         * {@code method #name(paramType,...)}, {@code field #name}...) that the application registers for {@code type}.
         */
        List<String> covered(String type, List<String> missing) {
            List<String> covered = new ArrayList<>();
            for (String item : missing) {
                boolean registered;
                if (item.equals("type")) {
                    registered = types.contains(type);
                } else if (item.startsWith("method #") || item.startsWith("field #")) {
                    registered = members.contains(type + item.substring(item.indexOf('#')));
                } else {
                    registered = false;
                }
                if (registered) {
                    covered.add(item);
                }
            }
            return covered;
        }

        boolean resourceCovered(String path) {
            return globs.stream().anyMatch(p -> p.matcher(path).matches());
        }
    }

    /**
     * The classes and resources of the desktop modules of the JDK running the tool.
     */
    static final class Universe {
        final Map<String, String> classes = new TreeMap<>(); // binary name -> module
        final Set<String> resources = new TreeSet<>();
        final Set<String> packages = new TreeSet<>();

        static Universe load() throws IOException {
            Universe u = new Universe();
            FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            for (String module : MODULES) {
                Path root = jrt.getPath("/modules", module);
                if (!Files.exists(root)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path p : files.filter(Files::isRegularFile).toList()) {
                        String name = root.relativize(p).toString().replace('\\', '/');
                        if (name.endsWith(".class")) {
                            if (!name.equals("module-info.class")) {
                                String binary = name.substring(0, name.length() - 6).replace('/', '.');
                                u.classes.put(binary, module);
                                for (String pkg = packageOf(binary); pkg != null; pkg = packageOf(pkg)) {
                                    u.packages.add(pkg);
                                }
                            }
                        } else {
                            u.resources.add(name);
                        }
                    }
                }
            }
            return u;
        }

        boolean isClassOrPackage(String name) {
            return classes.containsKey(name) || packages.contains(name);
        }

        /**
         * A class of the desktop modules (or of their packages on another platform).
         */
        boolean isDesktop(String name) {
            return classes.containsKey(name) || isDesktopName(name);
        }

        String checkMethod(String entry) {
            Matcher m = METHOD_ENTRY.matcher(entry);
            if (!m.matches()) {
                return "not fqcn#name(paramType,...)";
            }
            Class<?> c = load(m.group(1));
            if (c == null) {
                return "no such class";
            }
            List<String> params = m.group(3).isEmpty() ? List.of() : List.of(m.group(3).split(","));
            List<Executable> candidates = new ArrayList<>();
            try {
                if (m.group(2).equals("<init>")) {
                    candidates.addAll(Arrays.asList(c.getDeclaredConstructors()));
                } else {
                    Arrays.stream(c.getDeclaredMethods()).filter(x -> x.getName().equals(m.group(2))).forEach(candidates::add);
                }
            } catch (Throwable t) {
                return null; // cannot check (linkage)
            }
            for (Executable e : candidates) {
                List<String> types = Arrays.stream(e.getParameterTypes()).map(MetadataDiff::typeName).toList();
                if (types.equals(params)) {
                    return null;
                }
            }
            return candidates.isEmpty() ? "no such " + (m.group(2).equals("<init>") ? "constructor" : "method")
                    : "no such parameter types, found " + candidates.stream().map(e -> Arrays.stream(e.getParameterTypes())
                            .map(MetadataDiff::typeName).toList().toString()).toList();
        }

        String checkField(String entry) {
            int hash = entry.indexOf('#');
            if (hash < 0) {
                return "not fqcn#field";
            }
            Class<?> c = load(entry.substring(0, hash));
            if (c == null) {
                return "no such class";
            }
            try {
                c.getDeclaredField(entry.substring(hash + 1));
                return null;
            } catch (NoSuchFieldException e) {
                return "no such field";
            } catch (Throwable t) {
                return null;
            }
        }

        private static Set<String> allResources;

        /**
         * The resources of every module of the JDK (for list entries outside the desktop modules).
         */
        static synchronized Set<String> allResources() {
            if (allResources == null) {
                Set<String> all = new TreeSet<>();
                try (Stream<Path> modules = Files.list(FileSystems.getFileSystem(URI.create("jrt:/")).getPath("/modules"))) {
                    for (Path module : modules.toList()) {
                        try (Stream<Path> files = Files.walk(module)) {
                            files.filter(Files::isRegularFile).map(f -> module.relativize(f).toString().replace('\\', '/'))
                                    .filter(n -> !n.endsWith(".class")).forEach(all::add);
                        }
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                allResources = all;
            }
            return allResources;
        }

        /**
         * A class of any module of the JDK, arrays ({@code byte[]}, {@code java.lang.String[]}) included.
         */
        static Class<?> loadAny(String name) {
            if (name.endsWith("[]")) {
                Class<?> component = loadAny(name.substring(0, name.length() - 2));
                return component == null ? null : component.arrayType();
            }
            return switch (name) {
                case "boolean" -> boolean.class;
                case "byte" -> byte.class;
                case "char" -> char.class;
                case "short" -> short.class;
                case "int" -> int.class;
                case "long" -> long.class;
                case "float" -> float.class;
                case "double" -> double.class;
                default -> load(name);
            };
        }

        static Class<?> load(String name) {
            try {
                return Class.forName(name, false, ClassLoader.getPlatformClassLoader());
            } catch (Throwable t) {
                return null;
            }
        }
    }

    static String typeName(Class<?> c) {
        return c.isArray() ? typeName(c.getComponentType()) + "[]" : c.getName();
    }

    /**
     * {@code name} is one of {@code bundles}, or a localized variant ({@code <bundle>_<locale>}).
     */
    static boolean isBundle(String name, Set<String> bundles) {
        for (String bundle : bundles) {
            if (name.equals(bundle) || (name.startsWith(bundle + "_") && name.indexOf('.', bundle.length()) < 0)) {
                return true;
            }
        }
        return false;
    }

    static boolean isDesktopName(String name) {
        for (String pkg : DESKTOP_PACKAGES) {
            if (name.equals(pkg) || name.startsWith(pkg + ".")) {
                return true;
            }
        }
        return false;
    }

    static String packageOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : name.substring(0, dot);
    }

    static String owner(String member) {
        int hash = member.indexOf('#');
        return hash < 0 ? member : member.substring(0, hash);
    }

    /**
     * The String constants of a class file (constant pool), without a bytecode library.
     */
    static List<String> stringConstants(Path jar, String entry) throws IOException {
        List<String> result = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile()); InputStream raw = jf.getInputStream(jf.getEntry(entry));
                DataInputStream in = new DataInputStream(raw)) {
            in.readInt();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            List<Integer> strings = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 3, 4 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        i++;
                    }
                    case 7, 16, 19, 20 -> in.readUnsignedShort();
                    case 8 -> strings.add(in.readUnsignedShort());
                    case 9, 10, 11, 12, 17, 18 -> in.readInt();
                    case 15 -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IOException("Unknown constant pool tag " + tag);
                }
            }
            for (int index : strings) {
                result.add(utf8[index]);
            }
        }
        return result;
    }

    static void section(String title, Map<String, String> entries) {
        System.out.println("\n## " + title + " (" + entries.size() + ")");
        entries.forEach((k, v) -> System.out.println("- " + k + (v.isEmpty() ? "" : "  " + v)));
    }

    static void list(String title, Set<String> entries) {
        System.out.println("\n## " + title + " (" + entries.size() + ")");
        entries.forEach(e -> System.out.println("- " + e));
    }

    static String members(Map<String, Object> entry) {
        List<String> parts = new ArrayList<>();
        for (String key : List.of("methods", "fields")) {
            List<Map<String, Object>> l = list(entry.get(key));
            if (!l.isEmpty()) {
                parts.add(key + "=" + l.stream().map(m -> String.valueOf(m.get("name"))).toList());
            }
        }
        return String.join(" ", parts);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static List<Map<String, Object>> list(Object o) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (o instanceof List<?> l) {
            l.forEach(item -> result.add(map(item)));
        }
        return result;
    }

    static List<String> strings(Object o) {
        List<String> result = new ArrayList<>();
        if (o instanceof List<?> l) {
            l.forEach(item -> result.add(String.valueOf(item)));
        }
        return result;
    }

    static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    if (i + 2 < glob.length() && glob.charAt(i + 2) == '/') {
                        // "**/" : any number of directories, none included
                        sb.append("(?:.*/)?");
                        i += 2;
                    } else {
                        sb.append(".*");
                        i++;
                    }
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                sb.append("[^/]");
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString());
    }

    /**
     * WINDOWS, LINUX or MAC.
     */
    static String currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows") ? "WINDOWS" : os.startsWith("mac") ? "MAC" : "LINUX";
    }

    /**
     * The Quarkus version of the showcase pom.xml (quarkus.platform.version), 3.40.0 by default.
     */
    static String quarkusVersion() {
        try {
            Matcher m = Pattern.compile("<quarkus\\.platform\\.version>([^<]+)</quarkus\\.platform\\.version>")
                    .matcher(Files.readString(Path.of("pom.xml")));
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (IOException e) {
            // default
        }
        return "3.40.0";
    }
}
