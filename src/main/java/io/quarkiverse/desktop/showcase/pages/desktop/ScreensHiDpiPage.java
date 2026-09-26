package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.BufferCapabilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MediaTracker;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.imageio.ImageIO;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Screens and HiDPI : the graphics environment, every screen device (configurations, default and normalizing
 * transforms, capabilities, display modes, translucency and full-screen support), multi-resolution images (the variant
 * chosen for each {@code KEY_RESOLUTION_VARIANT} value and scale, a custom {@code AbstractMultiResolutionImage}, variants
 * loaded from classpath resources with ImageIO and the Toolkit) and lightweight content rendered at 1x, 1.5x and 2x.
 * <p>
 * AWT only. Full-screen exclusive mode is only entered with {@code -Dshowcase.fullscreen=true}. The environment dependent
 * values are informational : the transforms show whether an executable is DPI aware only in a run without the
 * {@code sun.java2d.uiScale=1} default of the snapshot tool ({@code --hidpi}).
 */
@Singleton
public class ScreensHiDpiPage implements FeaturePage {

    private static final int HALF = 494;
    private static final int[] VARIANT_FILLS = { 0x1565C0, 0x2E7D32, 0xC62828 };
    private static final String[] VARIANT_NAMES = { "1x", "1.5x", "2x" };
    private static final double[] SCALES = { 1, 1.25, 1.5, 2, 3 };

    private ChecksView fullScreenView;
    private ChecksView resourcesView;
    private Holder resourceStrip;

    @Override
    public String id() {
        return "desktop-screens-hidpi";
    }

    @Override
    public String title() {
        return "Screens and HiDPI";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Component build() throws Exception {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<Check> environment = new ArrayList<>();
        environment.add(Checks.expect("GraphicsEnvironment.isHeadless()", false, GraphicsEnvironment::isHeadless));
        environment.add(Checks.expect("isHeadlessInstance()", false, ge::isHeadlessInstance));
        environment.add(Checks.info("GraphicsEnvironment", () -> ge.getClass().getName()));
        environment.add(Checks.info("screen devices", () -> ge.getScreenDevices().length));
        environment.add(Checks.info("getMaximumWindowBounds()", () -> Checks.bounds(ge.getMaximumWindowBounds())));
        environment.add(Checks.info("getCenterPoint()", () -> ge.getCenterPoint().x + "," + ge.getCenterPoint().y));
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        environment.add(Checks.info("Toolkit.getScreenSize()", () -> toolkit.getScreenSize().width + "x"
                + toolkit.getScreenSize().height));
        environment.add(Checks.info("Toolkit.getScreenResolution()", toolkit::getScreenResolution));
        environment.add(Checks.info("sun.java2d.uiScale", () -> String.valueOf(System.getProperty("sun.java2d.uiScale"))));
        environment.add(Check.info("full-screen exclusive mode test",
                Boolean.getBoolean("showcase.fullscreen") ? "enabled" : "skipped (-Dshowcase.fullscreen=true)"));

        List<Check> devices = new ArrayList<>();
        GraphicsDevice[] screens = ge.getScreenDevices();
        for (int i = 0; i < screens.length; i++) {
            device(devices, "screen " + i + (screens[i] == ge.getDefaultScreenDevice() ? " (default)" : ""), screens[i],
                    toolkit);
        }

        BaseMultiResolutionImage icon = new BaseMultiResolutionImage(icon(32), icon(48), icon(64));
        List<Check> images = new ArrayList<>();
        multiResolution(images, icon);
        resourcesView = ChecksView.table("Resources", List.of(Check.info("state", "pending")));
        resourceStrip = new Holder(3 * 80, 64);

        Component scene = scene(icon);
        scene.setSize(scene.getPreferredSize());
        List<Component> renders = new ArrayList<>();
        for (double scale : new double[] { 1, 1.5, 2 }) {
            renders.add(Ui.column(4, Ui.image(Snapshots.render(scene, scale)),
                    Ui.caption("rendered at " + Checks.num(scale, 1) + "x")));
        }

        fullScreenView = ChecksView.table("Full-screen exclusive mode", List.of(Check.info("state", "pending")));
        return Ui.column(14,
                Ui.text("Screen devices and their configurations, the HiDPI transforms, and multi-resolution images : "
                        + "the variant Java2D picks for each resolution variant hint and scale (blue 1x, green 1.5x, red "
                        + "2x), variants loaded from resources, and a lightweight scene rendered at three scales.", 1000),
                Ui.title("BaseMultiResolutionImage drawn at scales 1, 1.25, 1.5, 2, 3 (one row per KEY_RESOLUTION_VARIANT)"),
                Ui.image(variantStrip(icon)),
                Ui.row(16, renders.toArray(Component[]::new)),
                Ui.row(16, Ui.column(4, resourceStrip, Ui.caption("variants loaded from classpath resources "
                        + "(ImageIO, Toolkit.getImage(URL), Toolkit.createImage(byte[]))"))),
                Ui.row(12, ChecksView.table("Graphics environment", environment, 220, HALF),
                        ChecksView.table("Multi-resolution images", images, 250, HALF)),
                ChecksView.table("Screen devices", devices, 330, 1000),
                resourcesView,
                fullScreenView);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView resourceChecks = resourcesView;
        Holder strip = resourceStrip;
        // Toolkit images decode asynchronously : a MediaTracker waits for them, off the EDT
        return Edt.background(() -> {
            List<Check> checks = new ArrayList<>();
            BufferedImage image = resources(checks);
            return Map.entry(checks, image);
        }).thenCompose(result -> {
            resourceChecks.setChecks(result.getKey());
            strip.setImage(result.getValue());
            return fullScreen();
        });
    }

    private CompletionStage<?> fullScreen() {
        ChecksView view = fullScreenView;
        if (!Boolean.getBoolean("showcase.fullscreen")) {
            view.setChecks(List.of(Check.info("setFullScreenWindow", "skipped (-Dshowcase.fullscreen=true)")));
            return CompletableFuture.completedFuture(null);
        }
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!device.isFullScreenSupported()) {
            view.setChecks(List.of(Check.info("setFullScreenWindow", "full-screen exclusive mode not supported")));
            return CompletableFuture.completedFuture(null);
        }
        Frame frame = new Frame("Showcase full screen");
        frame.setUndecorated(true);
        frame.setBackground(new Color(0x263238));
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.run("setFullScreenWindow(frame)", () -> {
            device.setFullScreenWindow(frame);
            return "entered";
        }));
        return Edt.delay(700).thenAccept(v -> {
            checks.add(Checks.expect("getFullScreenWindow()", true, () -> device.getFullScreenWindow() == frame));
            checks.add(Checks.run("setFullScreenWindow(null)", () -> {
                device.setFullScreenWindow(null);
                return "left";
            }));
            frame.dispose();
            view.setChecks(checks);
        });
    }

    @Override
    public void dispose(Component content) {
        fullScreenView = null;
        resourcesView = null;
        resourceStrip = null;
    }

    /**
     * Shows an image set later (empty until then).
     */
    private static final class Holder extends Component {

        private final int width;
        private final int height;
        private BufferedImage image;

        Holder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(width, height);
        }

        @Override
        public java.awt.Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(java.awt.Graphics g) {
            if (image != null) {
                g.drawImage(image, 0, 0, null);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- devices

    private static void device(List<Check> checks, String name, GraphicsDevice device, Toolkit toolkit) {
        GraphicsConfiguration gc = device.getDefaultConfiguration();
        checks.add(Checks.info(name + ": getIDstring()", device::getIDstring));
        checks.add(Checks.expect(name + ": getType()", "TYPE_RASTER_SCREEN",
                () -> device.getType() == GraphicsDevice.TYPE_RASTER_SCREEN ? "TYPE_RASTER_SCREEN" : device.getType()));
        checks.add(Checks.info(name + ": configurations", () -> device.getConfigurations().length));
        checks.add(Checks.info(name + ": bounds", () -> Checks.bounds(gc.getBounds())));
        checks.add(Checks.info(name + ": getDefaultTransform()", () -> transform(gc.getDefaultTransform())));
        checks.add(Checks.info(name + ": getNormalizingTransform()", () -> transform(gc.getNormalizingTransform())));
        checks.add(Checks.info(name + ": screen insets", () -> {
            Insets in = toolkit.getScreenInsets(gc);
            return in.top + ", " + in.left + ", " + in.bottom + ", " + in.right;
        }));
        checks.add(Checks.info(name + ": color model", () -> gc.getColorModel().getPixelSize() + " bits, alpha "
                + gc.getColorModel().hasAlpha() + ", translucency capable " + gc.isTranslucencyCapable()));
        checks.add(Checks.info(name + ": compatible image types (opaque, bitmask, translucent)",
                () -> gc.createCompatibleImage(4, 4).getType() + " "
                        + gc.createCompatibleImage(4, 4, Transparency.BITMASK).getType() + " "
                        + gc.createCompatibleImage(4, 4, Transparency.TRANSLUCENT).getType()));
        checks.add(Checks.info(name + ": image capabilities", () -> "accelerated "
                + gc.getImageCapabilities().isAccelerated() + ", true volatile " + gc.getImageCapabilities()
                        .isTrueVolatile()));
        checks.add(Checks.info(name + ": buffer capabilities", () -> {
            BufferCapabilities caps = gc.getBufferCapabilities();
            return "page flipping " + caps.isPageFlipping() + ", full screen required " + caps.isFullScreenRequired()
                    + ", multi buffer " + caps.isMultiBufferAvailable() + ", flip contents " + caps.getFlipContents();
        }));
        checks.add(Checks.info(name + ": display mode", () -> mode(device.getDisplayMode())));
        checks.add(Checks.info(name + ": display modes", () -> {
            DisplayMode[] modes = device.getDisplayModes();
            List<String> sizes = Arrays.stream(modes).map(m -> m.getWidth() + "x" + m.getHeight()).distinct()
                    .sorted(Comparator.comparingInt((String s) -> -Integer.parseInt(s.substring(0, s.indexOf('x')))))
                    .limit(6).toList();
            return modes.length + " modes, largest " + sizes;
        }));
        checks.add(Checks.expect(name + ": current mode listed", true,
                () -> Arrays.asList(device.getDisplayModes()).contains(device.getDisplayMode())));
        checks.add(Checks.info(name + ": full screen / display change supported", () -> device.isFullScreenSupported()
                + " / " + device.isDisplayChangeSupported()));
        checks.add(Checks.expect(name + ": getFullScreenWindow()", "null",
                () -> String.valueOf(device.getFullScreenWindow())));
        checks.add(Checks.info(name + ": window translucency (translucent, per-pixel, per-pixel transparent)",
                () -> device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT) + " "
                        + device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)
                        + " " + device.isWindowTranslucencySupported(
                                GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)));
        // the amount changes with the usage of the video memory : only its sign
        checks.add(Checks.info(name + ": available accelerated memory", () -> {
            int memory = device.getAvailableAcceleratedMemory();
            return memory < 0 ? "unknown (-1)" : memory == 0 ? "0" : "> 0";
        }));
        checks.add(Checks.expect(name + ": DisplayMode constants", "0 -1", () -> DisplayMode.REFRESH_RATE_UNKNOWN + " "
                + DisplayMode.BIT_DEPTH_MULTI));
    }

    private static String transform(AffineTransform tx) {
        return Checks.num(tx.getScaleX(), 3) + " x " + Checks.num(tx.getScaleY(), 3);
    }

    private static String mode(DisplayMode mode) {
        return mode.getWidth() + "x" + mode.getHeight() + ", " + (mode.getBitDepth() == DisplayMode.BIT_DEPTH_MULTI
                ? "multi" : mode.getBitDepth() + " bits")
                + ", " + (mode.getRefreshRate() == DisplayMode.REFRESH_RATE_UNKNOWN ? "unknown"
                        : mode.getRefreshRate() + " Hz");
    }

    // ------------------------------------------------------------------------------------- multi-resolution images

    /**
     * The icon of a resolution variant : a solid color per variant (blue 1x, green 1.5x, red 2x) and one white bar per
     * variant, no anti-aliasing (exact pixels). The resources {@code showcase/desktop/hidpi-icon*.png} were written with
     * this code (32, 48 and 64 pixels).
     */
    static BufferedImage icon(int size) {
        int variant = size <= 32 ? 0 : size <= 48 ? 1 : 2;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            int unit = size / 16;
            g.setColor(new Color(0x263238));
            g.fillRect(0, 0, size, size);
            g.setColor(new Color(VARIANT_FILLS[variant]));
            g.fillRect(unit, unit, size - 2 * unit, size - 2 * unit);
            g.setColor(Color.WHITE);
            for (int i = 0; i <= variant; i++) {
                g.fillRect(3 * unit + i * 4 * unit, size - 6 * unit, 2 * unit, 3 * unit);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static Object[] variantHints() {
        return new Object[] { RenderingHints.VALUE_RESOLUTION_VARIANT_DEFAULT,
                RenderingHints.VALUE_RESOLUTION_VARIANT_BASE, RenderingHints.VALUE_RESOLUTION_VARIANT_SIZE_FIT,
                RenderingHints.VALUE_RESOLUTION_VARIANT_DPI_FIT };
    }

    private static final String[] HINT_NAMES = { "DEFAULT", "BASE", "SIZE_FIT", "DPI_FIT" };

    /**
     * {@code image} drawn at 32 x 32 logical pixels into an image scaled by {@code scale}.
     */
    private static BufferedImage drawScaled(Image image, double scale, Object hint) {
        int size = (int) Math.ceil(32 * scale);
        return Snapshots.offscreen(size, size, g -> {
            g.setRenderingHint(RenderingHints.KEY_RESOLUTION_VARIANT, hint);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.scale(scale, scale);
            g.drawImage(image, 0, 0, null);
        });
    }

    private static String variantOf(BufferedImage drawn) {
        int rgb = drawn.getRGB(drawn.getWidth() / 2, drawn.getHeight() / 4) & 0xFFFFFF;
        for (int i = 0; i < VARIANT_FILLS.length; i++) {
            if (VARIANT_FILLS[i] == rgb) {
                return VARIANT_NAMES[i];
            }
        }
        return DesktopSupport.rgb(rgb);
    }

    private static void multiResolution(List<Check> checks, BaseMultiResolutionImage icon) {
        checks.add(Checks.expect("getResolutionVariants()", "[32x32, 48x48, 64x64]", () -> icon.getResolutionVariants()
                .stream().map(v -> v.getWidth(null) + "x" + v.getHeight(null)).toList()));
        checks.add(Checks.expect("getResolutionVariant(w, h) for 16, 32, 40, 48, 50, 100", "32 32 48 48 64 64", () -> {
            List<String> widths = new ArrayList<>();
            for (int size : new int[] { 16, 32, 40, 48, 50, 100 }) {
                widths.add(String.valueOf(icon.getResolutionVariant(size, size).getWidth(null)));
            }
            return String.join(" ", widths);
        }));
        checks.add(Checks.expect("base image size (getWidth/getHeight)", "32x32",
                () -> icon.getWidth(null) + "x" + icon.getHeight(null)));
        Object[] hints = variantHints();
        // offscreen images : the device transform is the identity, DPI_FIT picks the base image
        String[] expected = { "1x 1.5x 1.5x 2x 2x", "1x 1x 1x 1x 1x", "1x 1.5x 1.5x 2x 2x", "1x 1x 1x 1x 1x" };
        for (int h = 0; h < hints.length; h++) {
            Object hint = hints[h];
            checks.add(Checks.expect("variant drawn, " + HINT_NAMES[h] + " (scales 1 1.25 1.5 2 3)", expected[h], () -> {
                List<String> chosen = new ArrayList<>();
                for (double scale : SCALES) {
                    chosen.add(variantOf(drawScaled(icon, scale, hint)));
                }
                return String.join(" ", chosen);
            }));
        }
        VectorIcon vector = new VectorIcon();
        checks.add(Checks.expect("AbstractMultiResolutionImage: sizes requested (scales 1, 1.5, 2)",
                "24.0x24.0 36.0x36.0 48.0x48.0", () -> {
                    for (double scale : new double[] { 1, 1.5, 2 }) {
                        int size = (int) Math.ceil(24 * scale);
                        Snapshots.offscreen(size, size, g -> {
                            g.setRenderingHint(RenderingHints.KEY_RESOLUTION_VARIANT,
                                    RenderingHints.VALUE_RESOLUTION_VARIANT_SIZE_FIT);
                            g.scale(scale, scale);
                            g.drawImage(vector, 0, 0, null);
                        });
                    }
                    return String.join(" ", vector.requested);
                }));
    }

    private static BufferedImage variantStrip(BaseMultiResolutionImage icon) {
        Object[] hints = variantHints();
        int cell = 100;
        return Snapshots.offscreen(90 + SCALES.length * cell, hints.length * cell, g -> {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
            for (int h = 0; h < hints.length; h++) {
                g.setColor(new Color(0x37474F));
                g.drawString(HINT_NAMES[h], 0, h * cell + 50);
                for (int s = 0; s < SCALES.length; s++) {
                    g.drawImage(drawScaled(icon, SCALES[s], hints[h]), 90 + s * cell, h * cell + 2, null);
                }
            }
        });
    }

    /**
     * A resolution independent icon : every variant is painted on demand at the requested size (the sizes requested by
     * Java2D are recorded).
     */
    private static final class VectorIcon extends AbstractMultiResolutionImage {

        private final List<String> requested = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected Image getBaseImage() {
            return paint(24);
        }

        @Override
        public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
            requested.add(Checks.num(destImageWidth, 1) + "x" + Checks.num(destImageHeight, 1));
            return paint((int) Math.ceil(Math.max(destImageWidth, destImageHeight)));
        }

        @Override
        public List<Image> getResolutionVariants() {
            return List.of(getBaseImage());
        }

        private static BufferedImage paint(int size) {
            return Snapshots.offscreen(size, size, g -> {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0x6A1B9A));
                g.fill(new Ellipse2D.Double(0, 0, size, size));
            });
        }
    }

    // --------------------------------------------------------------------------------------------------- resources

    private static BufferedImage resources(List<Check> checks) throws Exception {
        String[] names = { "hidpi-icon.png", "hidpi-icon@1.5x.png", "hidpi-icon@2x.png" };
        int[] sizes = { 32, 48, 64 };
        List<BufferedImage> loaded = new ArrayList<>();
        Map<String, String> results = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            String path = "/showcase/desktop/" + names[i];
            BufferedImage expected = icon(sizes[i]);
            BufferedImage fromStream;
            try (InputStream in = Edt.resourceStream(path)) {
                fromStream = ImageIO.read(in);
            }
            BufferedImage fromUrl = ImageIO.read(Edt.resource(path));
            results.put(names[i] + " ImageIO.read(InputStream)", same(fromStream, expected));
            results.put(names[i] + " ImageIO.read(URL)", same(fromUrl, expected));
            loaded.add(fromStream);
        }
        // Toolkit images : asynchronous decoding (the Toolkit PNG decoder), awaited with a MediaTracker
        Image fromToolkitUrl = Toolkit.getDefaultToolkit().getImage(Edt.resource("/showcase/desktop/" + names[2]));
        Image fromBytes = Toolkit.getDefaultToolkit().createImage(Edt.resourceBytes("/showcase/desktop/" + names[1]));
        MediaTracker tracker = new MediaTracker(new Component() {
        });
        tracker.addImage(fromToolkitUrl, 0);
        tracker.addImage(fromBytes, 1);
        tracker.waitForAll(5000);
        results.put("Toolkit.getImage(URL) " + names[2], tracker.isErrorAny() ? "error" : same(toBuffered(fromToolkitUrl),
                icon(64)));
        results.put("Toolkit.createImage(byte[]) " + names[1], tracker.isErrorAny() ? "error" : same(toBuffered(
                fromBytes), icon(48)));
        results.put("Toolkit image is a MultiResolutionImage", String.valueOf(fromToolkitUrl instanceof MultiResolutionImage));
        results.forEach((name, value) -> checks.add(name.endsWith("MultiResolutionImage")
                ? Check.info(name, value)
                : Checks.expect(name, "identical pixels", () -> value)));
        BaseMultiResolutionImage fromResources = new BaseMultiResolutionImage(loaded.toArray(Image[]::new));
        checks.add(Checks.expect("variants from resources, drawn at 2x", "2x",
                () -> variantOf(drawScaled(fromResources, 2, RenderingHints.VALUE_RESOLUTION_VARIANT_SIZE_FIT))));
        return Snapshots.offscreen(3 * 80, 64, g -> {
            for (int i = 0; i < loaded.size(); i++) {
                g.drawImage(loaded.get(i), i * 80, 0, null);
            }
        });
    }

    private static String same(BufferedImage actual, BufferedImage expected) {
        if (actual == null) {
            return "not decoded";
        }
        return Checks.sha256(toBuffered(actual)).equals(Checks.sha256(expected)) ? "identical pixels"
                : "different pixels (" + actual.getWidth() + "x" + actual.getHeight() + ")";
    }

    private static BufferedImage toBuffered(Image image) {
        BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    // ------------------------------------------------------------------------------------------------ scaled scene

    /**
     * A small lightweight scene (text, anti-aliased shapes, the multi-resolution icon) rendered by
     * {@code Snapshots.render} at several scales, as HiDPI screens render it.
     */
    private static Component scene(BaseMultiResolutionImage icon) {
        return Ui.painted(200, 76, g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setColor(new Color(0xF5F7FA));
            g.fillRect(0, 0, 200, 76);
            g.setColor(new Color(0x90A4AE));
            g.drawRect(0, 0, 199, 75);
            g.drawImage(icon, 8, 6, null);
            g.setColor(new Color(0x263238));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString("HiDPI rendering", 48, 20);
            g.setFont(new Font(Font.SERIF, Font.ITALIC, 11));
            g.drawString(String.format(Locale.ROOT, "Quarkus %s %s", "desktop", "Ω≈ç"), 48, 35);
            g.setColor(new Color(0xEF6C00));
            g.fill(new Ellipse2D.Double(12, 46, 22, 22));
            g.setColor(new Color(0x00897B));
            g.fillRoundRect(48, 46, 142, 22, 10, 10);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
            g.drawString("scaled with the graphics", 56, 61);
        });
    }
}
