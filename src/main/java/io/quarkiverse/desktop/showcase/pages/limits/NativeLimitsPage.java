package io.quarkiverse.desktop.showcase.pages.limits;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.SplashScreen;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import javax.accessibility.AccessibilityProvider;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Keys;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Where a native executable legitimately differs from the JVM (a {@link #runtimeDependent()} page : its differences are
 * EXPECTED) : no {@code java} launcher (no {@code sun.java.launcher}, so no automatic DPI awareness on Windows without
 * {@code sun.java2d.dpiaware}, no launcher splash screen), no JDK home ({@code java.home} is a temporary font home
 * created by quarkus-awt), no class path, {@code resource:} URLs, the locales and charsets included at build time, the
 * Windows visual styles, which need a comctl32 v6 manifest in the executable (java.exe has one), and the break
 * iterators of the build locale only (GraalVM).
 * <p>
 * The "Must hold in both runtimes" table contains real checks : a failure there is a missing native configuration
 * (the splash screen library, the Java Access Bridge provider, the charsets of the Windows font configuration).
 */
@Singleton
public class NativeLimitsPage implements FeaturePage {

    private static final int HALF_WIDTH = 494;

    /** The Thai sample of the text-international page. */
    private static final String THAI = "สวัสดีชาวโลก กิ่ง ป่า น้ำ";

    @Override
    public String id() {
        return "overview-native-limits";
    }

    @Override
    public String title() {
        return "Native limits";
    }

    @Override
    public String category() {
        return Categories.OVERVIEW;
    }

    @Override
    public int order() {
        // after overview-environment (default order 100)
        return 110;
    }

    @Override
    public boolean runtimeDependent() {
        return true;
    }

    @Override
    public Component build() {
        return Ui.column(14,
                Ui.heading("Native limits"),
                Ui.text("What legitimately differs between the JVM (the java launcher and a JDK home) and a GraalVM native "
                        + "executable. The left table is expected to differ; the right table must hold in both runtimes "
                        + "(a failure there is missing native configuration). Raw DPI values are only meaningful in "
                        + "--hidpi runs (the snapshot tool forces sun.java2d.uiScale=1 otherwise).", 1000),
                Ui.row(12,
                        ChecksView.table("Expected differences", expectedDifferences(), 200, HALF_WIDTH),
                        Ui.column(14, ChecksView.table("Must hold in both runtimes", mustHold(), 240, HALF_WIDTH),
                                visualStyles())));
    }

    // ------------------------------------------------------------------------------------------ expected differences

    private static List<Check> expectedDifferences() {
        List<Check> checks = new ArrayList<>();
        checks.add(Check.info("runtime", ShowcaseMode.runtime()));
        checks.add(Check.info("java.vm.name", property("java.vm.name")));
        checks.add(Check.info("java.vendor.version", property("java.vendor.version")));
        checks.add(Check.info("executable", executable()));
        // Windows Server 2025 (build 26100 and later) : the JDK names it "Windows Server 2025", GraalVM (its own table of
        // WindowsSystemPropertiesSupport, which stops at Windows Server 2022) "Windows Server 2022"
        checks.add(Check.info("os.name", property("os.name")));
        // the java launcher sets sun.java.launcher=SUN_STANDARD, which makes WindowsFlags declare the process DPI aware
        checks.add(Check.info("sun.java.launcher", property("sun.java.launcher")));
        checks.add(Check.info("sun.java2d.dpiaware", property("sun.java2d.dpiaware")));
        checks.add(Check.info("sun.java2d.uiScale", property("sun.java2d.uiScale")));
        checks.add(Checks.info("effective DPI (screen resolution, default transform)", () -> {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
            AffineTransform tx = gc.getDefaultTransform();
            return Toolkit.getDefaultToolkit().getScreenResolution() + " dpi, " + Checks.num(tx.getScaleX(), 2) + " x "
                    + Checks.num(tx.getScaleY(), 2);
        }));
        checks.add(Check.info("java.home", javaHomeKind()));
        checks.add(Check.info("java.class.path", classPathKind()));
        checks.add(Check.info("sun.boot.library.path", System.getProperty("sun.boot.library.path") == null ? "unset" : "set"));
        checks.add(Checks.info("classpath resource URL scheme", () -> Edt.resource("/showcase/awt/applet.properties").getProtocol()));
        checks.add(Checks.info("module jdk.accessibility in the boot layer",
                () -> ModuleLayer.boot().findModule("jdk.accessibility").isPresent()));
        checks.add(Checks.info("available locales", () -> Locale.getAvailableLocales().length));
        checks.add(Checks.info("available charsets", () -> Charset.availableCharsets().size()));
        checks.add(Check.info("native.encoding / sun.jnu.encoding",
                property("native.encoding") + " / " + property("sun.jnu.encoding")));
        checks.add(Check.info("SplashScreen", "only the java launcher shows one (-splash:, SplashScreen-Image)"));
        // GraalVM substitutes BreakIterator.getWordInstance/getLineInstance/getCharacterInstance/getSentenceInstance(Locale)
        // (com.oracle.svm.core.jdk.localization.substitutions.Target_java_text_BreakIterator) : a native executable
        // returns copies of the break iterators of the default locale of the image build, whatever the locale. The
        // dictionary based Thai line breaks of jdk.localedata (thai_dict) cannot be used, even when the Thai locale data
        // and the dictionary are included in the executable
        checks.add(Checks.info("BreakIterator.getLineInstance(th) : boundaries of a Thai sample (native : the break "
                + "iterators of the build locale, whatever the locale)", () -> {
                    BreakIterator it = BreakIterator.getLineInstance(Locale.forLanguageTag("th"));
                    it.setText(THAI);
                    List<String> boundaries = new ArrayList<>();
                    for (int b = it.first(); b != BreakIterator.DONE; b = it.next()) {
                        boundaries.add(String.valueOf(b));
                    }
                    return String.join(" ", boundaries);
                }));
        // java.lang.reflect.Array.get boxes the element of a primitive array : HotSpot creates a new wrapper object, a
        // native executable uses Integer.valueOf (its cache). XMLEncoder identifies values by identity : a small int of an
        // int[] is written as <int> by the JVM, and by the name of a constant with the same cached value (for instance
        // TextAttribute.SUPERSCRIPT_SUPER) by a native executable
        checks.add(Checks.info("Array.get(new int[] { 1 }, 0) == Integer.valueOf(1) (boxing of array elements)",
                () -> java.lang.reflect.Array.get(new int[] { 1 }, 0) == Integer.valueOf(1)));
        return checks;
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value == null ? "unset" : value;
    }

    /**
     * The file name of the executable (never its path).
     */
    private static String executable() {
        return ProcessHandle.current().info().command()
                .map(command -> Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT))
                .orElse("unknown");
    }

    /**
     * What {@code java.home} designates (never the path) : a JDK runtime image, the temporary font home of quarkus-awt,
     * or nothing.
     */
    private static String javaHomeKind() {
        String home = System.getProperty("java.home");
        if (home == null || home.isEmpty()) {
            return "unset";
        }
        Path path = Path.of(home);
        if (Files.isRegularFile(path.resolve("lib").resolve("modules"))) {
            return "JDK runtime image (lib/modules)";
        }
        if (home.contains("quarkus-awt-tmp-fonts")) {
            return "temporary font home of quarkus-awt"
                    + (Files.exists(path.resolve("lib").resolve("fontconfig.properties")) ? " (lib/fontconfig.properties)"
                            : "");
        }
        return Files.isDirectory(path) ? "another directory" : "missing directory";
    }

    private static String classPathKind() {
        String classPath = System.getProperty("java.class.path");
        if (classPath == null || classPath.isEmpty()) {
            return classPath == null ? "unset" : "empty";
        }
        return classPath.split(java.io.File.pathSeparator).length + " entries";
    }

    // --------------------------------------------------------------------------------------------------- must hold

    private static List<Check> mustHold() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("GraphicsEnvironment.isHeadless()", false, GraphicsEnvironment::isHeadless));
        // loads the splashscreen library, then asks it for a launcher splash screen : none here in both runtimes
        checks.add(Checks.expect("SplashScreen.getSplashScreen()", "null", () -> String.valueOf(SplashScreen.getSplashScreen())));
        // Java Access Bridge : Toolkit start-up fails with AWTError when assistive_technologies names it and the provider
        // is missing (a user who ran jabswitch -enable)
        checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("AccessibilityProvider services",
                Platforms.isWindows() ? "com.sun.java.accessibility.AccessBridge" : "", () -> {
                    List<String> names = new ArrayList<>();
                    for (AccessibilityProvider provider : ServiceLoader.load(AccessibilityProvider.class)) {
                        names.add(provider.getName());
                    }
                    return String.join(" ", names);
                })));
        checks.add(Check.info("javax.accessibility.assistive_technologies",
                property("javax.accessibility.assistive_technologies")));
        checks.add(Check.info("~/.accessibility.properties",
                Files.exists(Path.of(System.getProperty("user.home"), ".accessibility.properties")) ? "present" : "absent"));
        // the charsets of the Windows font configuration (logical fonts of the heavyweight components)
        checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("charsets of the Windows font configuration",
                "windows-1252 windows-1251 windows-1253 windows-1255 windows-31j GBK x-windows-949 x-windows-874 "
                        + "x-windows-950 x-MS950-HKSCS UTF-16LE",
                () -> String.join(" ", List.of("windows-1252", "windows-1251", "windows-1253", "windows-1255",
                        "windows-31j", "GBK", "x-windows-949", "x-windows-874", "x-windows-950", "x-MS950-HKSCS",
                        "UTF-16LE").stream().filter(Charset::isSupported).toList()))));
        checks.add(Checks.expect("Locale.getDefault()", "en-US", () -> Locale.getDefault().toLanguageTag()));
        // AWT on Windows lays its native controls and menus out right to left when the keyboard layout (input language)
        // is Arabic or Hebrew : compare runs made with the same keyboard layout
        checks.add(Checks.info("keyboard layout (input method locale)", () -> {
            Locale locale = java.awt.im.InputContext.getInstance().getLocale();
            return locale == null ? "none" : locale.toLanguageTag();
        }));
        // macOS : Toolkit.getProperty reads first the platform resources that LWCToolkit installs, the
        // sun.awt.resources.awtosx bundle ("⌃", the control key symbol) : Keys.text
        checks.add(Checks.expect("Toolkit.getProperty(AWT.control) (sun.awt.resources.awt bundle)", Keys.text("Ctrl"),
                () -> Toolkit.getProperty("AWT.control", "missing bundle")));
        return checks;
    }

    // ----------------------------------------------------------------------------------------------- visual styles

    /**
     * Native controls : with the comctl32 v6 manifest of java.exe they use the Windows visual styles (themed), without a
     * manifest the classic look.
     */
    private static Component visualStyles() {
        Panel panel = new Panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        panel.setBackground(new Color(0xF3F4F6));
        panel.setPreferredSize(new Dimension(HALF_WIDTH, 84));
        panel.add(new Button("Button"));
        CheckboxGroup group = new CheckboxGroup();
        panel.add(new Checkbox("Checkbox", true));
        panel.add(new Checkbox("Radio", true, group));
        Choice choice = new Choice();
        choice.add("Choice");
        panel.add(choice);
        panel.add(new TextField("TextField", 8));
        Scrollbar scrollbar = new Scrollbar(Scrollbar.HORIZONTAL, 40, 20, 0, 100);
        scrollbar.setPreferredSize(new Dimension(140, 18));
        panel.add(scrollbar);
        Container column = Ui.column(6, Ui.title("Windows visual styles (manifest of the executable)"),
                Ui.caption("themed with the comctl32 v6 manifest of java.exe, classic without a manifest"), panel);
        return column;
    }
}
