package io.quarkiverse.desktop.showcase.pages.overview;

import java.awt.Component;
import java.awt.DisplayMode;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;

import javax.imageio.ImageIO;
import javax.print.DocFlavor;
import javax.print.PrintServiceLookup;
import javax.print.StreamPrintServiceFactory;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.Environment;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The environment of the run (the same values as the top of report.json, without those that legitimately differ between
 * the JVM and a native executable), the screens, the toolkit, the desktop features and the JDK services a native
 * executable must provide (print services, ImageIO plugins, data transfer flavor map).
 * <p>
 * AWT only. The services are looked up in the background in {@link #ready} (they may be slow) : an example of checks
 * computed once the page is built.
 */
@Singleton
public class EnvironmentPage implements FeaturePage {

    private static final int HALF_WIDTH = 494;

    // per build state : pages are singletons showing one content at a time (build() sets it, dispose() clears it)
    private ChecksView services;

    @Override
    public String id() {
        return "overview-environment";
    }

    @Override
    public String title() {
        return "Environment";
    }

    @Override
    public String category() {
        return Categories.OVERVIEW;
    }

    @Override
    public Component build() {
        Map<String, Object> env = Environment.describe();
        List<Check> runtime = new ArrayList<>();
        runtime.add(Checks.expect("GraphicsEnvironment.isHeadless()", false, GraphicsEnvironment::isHeadless));
        // quarkus-desktop runs the application on a new thread named main in macOS native executables : main everywhere
        // (the thread that starts the application : the showcase has no QuarkusApplication any more, the check keeps
        // its name so that the runs compare with the older ones)
        runtime.add(Checks.expect("QuarkusApplication.run() thread", "main", ShowcaseMode::mainThread));
        if (Platforms.isMac()) {
            runtime.add(Checks.expect("Toolkit (macOS)", "sun.lwawt.macosx.LWCToolkit",
                    () -> Toolkit.getDefaultToolkit().getClass().getName()));
            runtime.add(Checks.expect("GraphicsEnvironment (macOS)", "sun.awt.CGraphicsEnvironment",
                    () -> GraphicsEnvironment.getLocalGraphicsEnvironment().getClass().getName()));
            // AWT owns the application (the first thread of the process runs its event loop) : the desktop handlers of
            // java.awt.Desktop (About, Quit, open files) work
            runtime.add(Checks.expect("AppKit Thread (AWT owns the application)", true, () -> Thread.getAllStackTraces()
                    .keySet().stream().anyMatch(t -> "AppKit Thread".equals(t.getName()))));
        }
        env.forEach((key, value) -> {
            if (!Environment.INFO_KEYS.contains(key) && !key.equals("headless") && !key.equals("desktopFeatures")) {
                runtime.add(Check.info(key, value));
            }
        });

        List<Check> features = new ArrayList<>();
        if (env.get("desktopFeatures") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> features.add(Check.info(String.valueOf(key), value)));
        }

        services = ChecksView.table("JDK services", List.of(Check.info("lookup", "pending")), 220, HALF_WIDTH);

        return Ui.column(14,
                Ui.heading("Quarkus Desktop Showcase"),
                Ui.text("Every page exercises an AWT, Java2D or Swing feature area. In snapshot mode, pages are rendered "
                        + "to images and compared between a JVM run and a native run, together with their checks.", 1000),
                ChecksView.table("Runtime environment", runtime),
                Ui.row(12, ChecksView.table("Screens and toolkit", screens(), 220, HALF_WIDTH),
                        Ui.column(14, ChecksView.table("Desktop features", features, 280, HALF_WIDTH), services)));
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = services;
        // stages returned by Edt complete on the EDT : the view is updated on the EDT
        return Edt.background(EnvironmentPage::lookupServices).thenAccept(view::setChecks);
    }

    @Override
    public void dispose(Component content) {
        services = null;
    }

    private static List<Check> screens() {
        List<Check> checks = new ArrayList<>();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        checks.add(Check.info("screen devices", devices.length));
        for (int i = 0; i < devices.length; i++) {
            GraphicsDevice device = devices[i];
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            AffineTransform tx = gc.getDefaultTransform();
            DisplayMode mode = device.getDisplayMode();
            String prefix = "screen " + i + (device == ge.getDefaultScreenDevice() ? " (default)" : "");
            checks.add(Check.info(prefix + " bounds", Checks.bounds(gc.getBounds())));
            checks.add(Check.info(prefix + " transform", Checks.num(tx.getScaleX(), 2) + " x " + Checks.num(tx.getScaleY(), 2)));
            checks.add(Check.info(prefix + " display mode", mode.getWidth() + "x" + mode.getHeight() + ", "
                    + mode.getBitDepth() + " bits, " + mode.getRefreshRate() + " Hz"));
            checks.add(Check.info(prefix + " configurations", device.getConfigurations().length));
        }
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        checks.add(Checks.info("Toolkit.getScreenSize()", () -> toolkit.getScreenSize().width + "x"
                + toolkit.getScreenSize().height));
        checks.add(Checks.info("Toolkit.getScreenResolution()", toolkit::getScreenResolution));
        checks.add(Checks.info("Toolkit.getScreenInsets()", () -> {
            Insets in = toolkit.getScreenInsets(ge.getDefaultScreenDevice().getDefaultConfiguration());
            return in.top + ", " + in.left + ", " + in.bottom + ", " + in.right;
        }));
        checks.add(Checks.info("Toolkit.getBestCursorSize(32, 32)", () -> toolkit.getBestCursorSize(32, 32).width
                + "x" + toolkit.getBestCursorSize(32, 32).height));
        checks.add(Checks.info("Toolkit.getMaximumCursorColors()", toolkit::getMaximumCursorColors));
        checks.add(Checks.info("Toolkit.isFrameStateSupported(MAXIMIZED_BOTH)",
                () -> toolkit.isFrameStateSupported(Frame.MAXIMIZED_BOTH)));
        checks.add(Checks.info("Toolkit.getMenuShortcutKeyMaskEx()", toolkit::getMenuShortcutKeyMaskEx));
        checks.add(Checks.info("Toolkit.areExtraMouseButtonsEnabled()", toolkit::areExtraMouseButtonsEnabled));
        checks.add(Checks.info("MouseInfo.getNumberOfButtons()", MouseInfo::getNumberOfButtons));
        checks.add(Checks.info("Toolkit.getColorModel()", () -> toolkit.getColorModel().getPixelSize() + " bits"));
        String themeProperty = Platforms.pick("apple.awt.graphics.UseQuartz", "win.xpstyle.themeActive",
                "gnome.Net/ThemeName");
        checks.add(Checks.info("desktop property " + themeProperty, () -> toolkit.getDesktopProperty(themeProperty)));
        return checks;
    }

    /**
     * JDK services found through the module service catalog (Quarkus disables the GraalVM service loader feature :
     * their providers must be registered by quarkus-desktop) and through the ImageIO registry.
     */
    private static List<Check> lookupServices() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.info("print services", () -> PrintServiceLookup.lookupPrintServices(null, null).length));
        checks.add(Checks.info("default print service",
                () -> PrintServiceLookup.lookupDefaultPrintService() == null ? "none" : "present"));
        checks.add(Checks.expect("PostScript stream print services", 1,
                () -> StreamPrintServiceFactory.lookupStreamPrintServiceFactories(DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                        "application/postscript").length));
        checks.add(Checks.info("ImageIO reader formats", () -> formats(ImageIO.getReaderFormatNames())));
        checks.add(Checks.info("ImageIO writer formats", () -> formats(ImageIO.getWriterFormatNames())));
        checks.add(Checks.info("ImageIO reader MIME types", () -> formats(ImageIO.getReaderMIMETypes())));
        checks.add(Checks.info("SystemFlavorMap natives of stringFlavor", () -> ((SystemFlavorMap) SystemFlavorMap
                .getDefaultFlavorMap()).getNativesForFlavor(DataFlavor.stringFlavor)));
        return checks;
    }

    private static String formats(String[] names) {
        TreeSet<String> sorted = new TreeSet<>();
        Arrays.stream(names).map(n -> n.toLowerCase(Locale.ROOT)).forEach(sorted::add);
        return String.join(" ", sorted);
    }
}
