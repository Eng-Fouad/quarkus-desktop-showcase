package io.quarkiverse.desktop.showcase.pages.java2d;

import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.SCENE_HEIGHT;
import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.SCENE_WIDTH;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.ImageCapabilities;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.VolatileImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Grid;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Surface;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Tile;

/**
 * Surfaces and image types : the same scene (solid bars, a gradient ramp, aliased and anti-aliased shapes and text, a
 * translucent sprite and overlay, a {@code copyArea}) rendered into every kind of {@code BufferedImage} (the 13
 * predefined types, 2 and 4 bit binary images, {@code TYPE_CUSTOM} images with other color models, sample models and
 * data types, including LINEAR_RGB, GRAY and CIEXYZ color spaces), into compatible images of the screen configuration
 * and into {@code VolatileImage}s (the accelerated pipeline : Direct3D, OpenGL, XRender, or GDI/X11).
 * <p>
 * Capture method C (and {@code VolatileImage.getSnapshot()}). Each kind of surface selects its own Java2D loops :
 * native loops per surface type for the predefined types, the generic "any" loops (loaded by name) and color
 * conversions for the custom ones. Checks : color and sample models, pixel probes in solid areas (platform
 * independent), pixel hashes (compared between the JVM and a native executable), image API, volatile image state
 * and differences between the accelerated and the software rendering.
 */
@Singleton
public class SurfacesPage implements FeaturePage {

    private static final int COLUMNS = 4;
    private static final int TILE_WIDTH = 250;
    private static final int TILE_HEIGHT = 162;

    /**
     * Expected pixel probes (see {@link Java2dSupport#SCENE_PROBES}) and raw samples of the red bar, per image kind :
     * software rendering, the same on every platform.
     */
    private static final Map<String, String> PROBES = Map.ofEntries(
            Map.entry("TYPE_INT_RGB",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #FF000080, red [255, 0, 0]"),
            Map.entry("TYPE_INT_ARGB",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #800000FF, red [255, 0, 0, 255]"),
            Map.entry("TYPE_INT_ARGB_PRE",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #800000FF, red [255, 0, 0, 255]"),
            Map.entry("TYPE_INT_BGR",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #FF000080, red [255, 0, 0]"),
            Map.entry("TYPE_3BYTE_BGR",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #FF000080, red [255, 0, 0]"),
            Map.entry("TYPE_4BYTE_ABGR",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #800000FF, red [255, 0, 0, 255]"),
            Map.entry("TYPE_4BYTE_ABGR_PRE",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #800000FF, red [255, 0, 0, 255]"),
            Map.entry("TYPE_USHORT_565_RGB",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7B7D84 #FF525152 #FFFF0000 #FF000084, red [31, 0, 0]"),
            Map.entry("TYPE_USHORT_555_RGB",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7B7B84 #FF525252 #FFFF0000 #FF000084, red [31, 0, 0]"),
            Map.entry("TYPE_BYTE_GRAY",
                    "#FF949494 #FF5F5F5F #FFF2F2F2 #FFBCBCBC #FF999999 #FF949494 #FF454545, red [76]"),
            Map.entry("TYPE_USHORT_GRAY",
                    "#FF959595 #FF5F5F5F #FFF2F2F2 #FFBBBBBB #FF999999 #FF959595 #FF444444, red [19595]"),
            Map.entry("TYPE_BYTE_BINARY",
                    "#FF000000 #FF000000 #FFFFFFFF #FFFFFFFF #FF000000 #FF000000 #FF000000, red [0]"),
            Map.entry("TYPE_BYTE_INDEXED",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF848484 #FF336666 #FFFF0000 #FF000066, red [180]"),
            Map.entry("TYPE_BYTE_BINARY 2 bit",
                    "#FF555555 #FF000000 #FFFFFFFF #FFAAAAAA #FF555555 #FF555555 #FF000000, red [1]"),
            Map.entry("TYPE_BYTE_BINARY 4 bit",
                    "#FFAA0000 #FF0000AA #FFFFFF55 #FFAAAAAA #FF555555 #FFAA0000 #FF0000AA, red [4]"),
            Map.entry("CUSTOM ARGB 4444 ushort",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #77777788 #FF555555 #FFFF0000 #880000FF, red [15, 0, 0, 15]"),
            Map.entry("CUSTOM RGB 332 byte",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF6D6DAA #FF494955 #FFFF0000 #FF0000AA, red [7, 0, 0]"),
            Map.entry("CUSTOM RGBA 8888 int",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #800000FF, red [255, 0, 0, 255]"),
            Map.entry("CUSTOM LINEAR_RGB alpha float",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #800000FF, red [1, 0, 0, 1]"),
            Map.entry("CUSTOM GRAY alpha byte",
                    "#FF7F7F7F #FF4B4B4B #FFF7F7F7 #FF8A8A8A #FF515151 #FF7F7F7F #804B4B4B, red [54, 255]"),
            Map.entry("CUSTOM sRGB banded byte",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #FF000080, red [255, 0, 0]"),
            Map.entry("CUSTOM CIEXYZ ushort",
                    "#FFFF0000 #FF0000FF #FFFFFF00 #FF7F7F80 #FF515151 #FFFF0000 #FF000080, red [14282, 7287, 456]"));

    /** Expected color model / sample model / data buffer descriptions, per image kind. */
    private static final Map<String, String> MODELS = Map.ofEntries(
            Map.entry("TYPE_INT_RGB",
                    "TYPE_INT_RGB, DirectColorModel 24 bits OPAQUE, SinglePixelPackedSampleModel 3 bands, DataBufferInt 24000"),
            Map.entry("TYPE_INT_ARGB",
                    "TYPE_INT_ARGB, DirectColorModel 32 bits TRANSLUCENT, "
                            + "SinglePixelPackedSampleModel 4 bands, DataBufferInt 24000"),
            Map.entry("TYPE_INT_ARGB_PRE",
                    "TYPE_INT_ARGB_PRE, DirectColorModel 32 bits TRANSLUCENT premultiplied, "
                            + "SinglePixelPackedSampleModel 4 bands, DataBufferInt 24000"),
            Map.entry("TYPE_INT_BGR",
                    "TYPE_INT_BGR, DirectColorModel 24 bits OPAQUE, SinglePixelPackedSampleModel 3 bands, DataBufferInt 24000"),
            Map.entry("TYPE_3BYTE_BGR",
                    "TYPE_3BYTE_BGR, ComponentColorModel 24 bits OPAQUE, "
                            + "PixelInterleavedSampleModel 3 bands, DataBufferByte 72000"),
            Map.entry("TYPE_4BYTE_ABGR",
                    "TYPE_4BYTE_ABGR, ComponentColorModel 32 bits TRANSLUCENT, "
                            + "PixelInterleavedSampleModel 4 bands, DataBufferByte 96000"),
            Map.entry("TYPE_4BYTE_ABGR_PRE",
                    "TYPE_4BYTE_ABGR_PRE, ComponentColorModel 32 bits TRANSLUCENT premultiplied, "
                            + "PixelInterleavedSampleModel 4 bands, DataBufferByte 96000"),
            Map.entry("TYPE_USHORT_565_RGB",
                    "TYPE_USHORT_565_RGB, DirectColorModel 16 bits OPAQUE, "
                            + "SinglePixelPackedSampleModel 3 bands, DataBufferUShort 24000"),
            Map.entry("TYPE_USHORT_555_RGB",
                    "TYPE_USHORT_555_RGB, DirectColorModel 15 bits OPAQUE, "
                            + "SinglePixelPackedSampleModel 3 bands, DataBufferUShort 24000"),
            Map.entry("TYPE_BYTE_GRAY",
                    "TYPE_BYTE_GRAY, ComponentColorModel 8 bits OPAQUE, "
                            + "PixelInterleavedSampleModel 1 bands, DataBufferByte 24000"),
            Map.entry("TYPE_USHORT_GRAY",
                    "TYPE_USHORT_GRAY, ComponentColorModel 16 bits OPAQUE, "
                            + "PixelInterleavedSampleModel 1 bands, DataBufferUShort 24000"),
            Map.entry("TYPE_BYTE_BINARY",
                    "TYPE_BYTE_BINARY, IndexColorModel 1 bits OPAQUE, "
                            + "MultiPixelPackedSampleModel 1 bands, DataBufferByte 3000"),
            Map.entry("TYPE_BYTE_INDEXED",
                    "TYPE_BYTE_INDEXED, IndexColorModel 8 bits OPAQUE, "
                            + "PixelInterleavedSampleModel 1 bands, DataBufferByte 24000"),
            Map.entry("TYPE_BYTE_BINARY 2 bit",
                    "TYPE_BYTE_BINARY, IndexColorModel 2 bits OPAQUE, "
                            + "MultiPixelPackedSampleModel 1 bands, DataBufferByte 6000"),
            Map.entry("TYPE_BYTE_BINARY 4 bit",
                    "TYPE_BYTE_BINARY, IndexColorModel 4 bits OPAQUE, "
                            + "MultiPixelPackedSampleModel 1 bands, DataBufferByte 12000"),
            Map.entry("CUSTOM ARGB 4444 ushort",
                    "TYPE_CUSTOM, DirectColorModel 16 bits TRANSLUCENT, "
                            + "SinglePixelPackedSampleModel 4 bands, DataBufferUShort 24000"),
            Map.entry("CUSTOM RGB 332 byte",
                    "TYPE_CUSTOM, DirectColorModel 8 bits OPAQUE, SinglePixelPackedSampleModel 3 bands, DataBufferByte 24000"),
            Map.entry("CUSTOM RGBA 8888 int",
                    "TYPE_CUSTOM, DirectColorModel 32 bits TRANSLUCENT, "
                            + "SinglePixelPackedSampleModel 4 bands, DataBufferInt 24000"),
            Map.entry("CUSTOM LINEAR_RGB alpha float",
                    "TYPE_CUSTOM, ComponentColorModel 128 bits TRANSLUCENT, "
                            + "ComponentSampleModel 4 bands, DataBufferFloat 96000"),
            Map.entry("CUSTOM GRAY alpha byte",
                    "TYPE_CUSTOM, ComponentColorModel 16 bits TRANSLUCENT, "
                            + "PixelInterleavedSampleModel 2 bands, DataBufferByte 48000"),
            Map.entry("CUSTOM sRGB banded byte",
                    "TYPE_CUSTOM, ComponentColorModel 24 bits OPAQUE, BandedSampleModel 3 bands, DataBufferByte 24000"),
            Map.entry("CUSTOM CIEXYZ ushort",
                    "TYPE_CUSTOM, ComponentColorModel 48 bits OPAQUE, "
                            + "PixelInterleavedSampleModel 3 bands, DataBufferUShort 72000"));

    @Override
    public String id() {
        return "j2d-surfaces";
    }

    @Override
    public String title() {
        return "Surfaces and image types";
    }

    @Override
    public String category() {
        return Categories.JAVA2D;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public Component build() {
        List<Tile> tiles = new ArrayList<>();
        List<Check> models = new ArrayList<>();
        List<Check> pixels = new ArrayList<>();

        BufferedImage reference = render(new BufferedImage(SCENE_WIDTH, SCENE_HEIGHT, BufferedImage.TYPE_INT_ARGB));
        for (Surface surface : Java2dSupport.bufferedImages(SCENE_WIDTH, SCENE_HEIGHT)) {
            String name = surface.name();
            try {
                BufferedImage image = render(surface.image());
                models.add(expectOrInfo(MODELS, name + " : model", () -> Java2dSupport.imageType(image.getType()) + ", "
                        + Java2dSupport.model(image)));
                pixels.add(expectOrInfo(PROBES, name + " : probes", () -> Java2dSupport.sceneProbes(image) + ", red "
                        + samples(image, 16, 28)));
                pixels.add(Checks.info(name + " : pixel hash", () -> Checks.sha256(image)));
                tiles.add(tile(name, image));
            } catch (RuntimeException | Error e) {
                models.add(Check.fail(name, Checks.describe(e)));
                tiles.add(failedTile(name, e));
            }
        }

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();
        for (int transparency : new int[] { Transparency.OPAQUE, Transparency.BITMASK, Transparency.TRANSLUCENT }) {
            String name = "compatible " + Java2dSupport.transparency(transparency);
            try {
                BufferedImage image = render(gc.createCompatibleImage(SCENE_WIDTH, SCENE_HEIGHT, transparency));
                models.add(Checks.info(name + " : model", () -> Java2dSupport.imageType(image.getType()) + ", "
                        + Java2dSupport.model(image)));
                pixels.add(Checks.info(name + " : probes", () -> Java2dSupport.sceneProbes(image)));
                tiles.add(tile(name, image));
            } catch (RuntimeException | Error e) {
                models.add(Check.fail(name, Checks.describe(e)));
                tiles.add(failedTile(name, e));
            }
        }

        List<Check> volatiles = new ArrayList<>();
        volatiles.add(Checks.info("GraphicsConfiguration.getImageCapabilities() : accelerated / true volatile",
                () -> gc.getImageCapabilities().isAccelerated() + " / " + gc.getImageCapabilities().isTrueVolatile()));
        volatileTile(tiles, volatiles, gc, "VolatileImage OPAQUE", () -> gc.createCompatibleVolatileImage(SCENE_WIDTH,
                SCENE_HEIGHT), reference);
        volatileTile(tiles, volatiles, gc, "VolatileImage TRANSLUCENT", () -> gc.createCompatibleVolatileImage(
                SCENE_WIDTH, SCENE_HEIGHT, Transparency.TRANSLUCENT), reference);
        volatileTile(tiles, volatiles, gc, "VolatileImage accelerated caps", () -> gc.createCompatibleVolatileImage(
                SCENE_WIDTH, SCENE_HEIGHT, new ImageCapabilities(true), Transparency.OPAQUE), reference);

        Grid grid = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, tiles);
        return Ui.column(14,
                Ui.text("The same scene rendered into every kind of image, then drawn over a checkerboard (drawImage "
                        + "converts each type). The last 20 columns are cleared with AlphaComposite.Src (transparent, or "
                        + "black without alpha) and covered by a 50% blue stripe. Probes : red, blue, yellow, yellow under "
                        + "the 50% blue overlay, ramp, copyArea copy, 50% blue on the cleared area.", 1000),
                Ui.image(grid.paint()),
                ChecksView.table("Color, sample and data models", models, 250, 1000),
                ChecksView.table("Pixels (software loops : probes are platform independent, hashes compared between "
                        + "runs)", pixels, 250, 1000),
                ChecksView.table("VolatileImage (accelerated surfaces of the screen configuration)", volatiles, 250, 1000),
                ChecksView.table("BufferedImage API", apiChecks(gc)));
    }

    private static BufferedImage render(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        try {
            Java2dSupport.scene(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static Tile tile(String name, Image image) {
        return new Tile(caption(name), (g, w, h) -> {
            Java2dSupport.checker(g, SCENE_WIDTH, SCENE_HEIGHT, 10);
            g.drawImage(image, 0, 0, null);
        });
    }

    private static Tile failedTile(String name, Throwable e) {
        return new Tile(caption(name), (g, w, h) -> {
            g.setColor(new Color(Ui.ERROR_COLOR));
            g.drawString(e.getClass().getSimpleName(), 4, 20);
        });
    }

    private static String caption(String name) {
        return name.replace("CUSTOM ", "TYPE_CUSTOM : ");
    }

    private static Check expectOrInfo(Map<String, String> expected, String name, Callable<?> action) {
        String key = name.substring(0, name.lastIndexOf(" : "));
        return expected.containsKey(key) ? Checks.expect(name, expected.get(key), action) : Checks.info(name, action);
    }

    private static String samples(BufferedImage image, int x, int y) {
        return Arrays.toString(image.getRaster().getPixel(x, y, (double[]) null)).replace(".0,", ",").replace(".0]", "]");
    }

    private static void volatileTile(List<Tile> tiles, List<Check> checks, GraphicsConfiguration gc, String name,
            Callable<VolatileImage> factory, BufferedImage reference) {
        VolatileImage image = null;
        try {
            image = factory.call();
            VolatileImage vi = image;
            checks.add(Checks.info(name + " : first validate()", () -> validation(vi.validate(gc))));
            checks.add(Checks.info(name + " : transparency / accelerated / true volatile",
                    () -> Java2dSupport.transparency(vi.getTransparency()) + " / " + vi.getCapabilities().isAccelerated()
                            + " / " + vi.getCapabilities().isTrueVolatile()));
            BufferedImage snapshot = null;
            int attempts = 0;
            for (; attempts < 5 && snapshot == null; attempts++) {
                if (vi.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                    throw new IllegalStateException("IMAGE_INCOMPATIBLE");
                }
                Graphics2D g = vi.createGraphics();
                try {
                    Java2dSupport.scene(g);
                } finally {
                    g.dispose();
                }
                BufferedImage s = vi.getSnapshot();
                if (!vi.contentsLost()) {
                    snapshot = s;
                }
            }
            if (snapshot == null) {
                throw new IllegalStateException("contents lost 5 times");
            }
            BufferedImage result = snapshot;
            int tries = attempts;
            checks.add(Checks.info(name + " : rendering attempts / snapshot", () -> tries + " / "
                    + Java2dSupport.imageType(result.getType())));
            checks.add(Checks.info(name + " : probes", () -> Java2dSupport.sceneProbes(result)));
            BufferedImage software = vi.getTransparency() == Transparency.OPAQUE
                    ? render(new BufferedImage(SCENE_WIDTH, SCENE_HEIGHT, BufferedImage.TYPE_INT_RGB))
                    : reference;
            checks.add(Checks.info(name + " : versus software rendering", () -> Java2dSupport.diff(
                    Java2dSupport.toArgb(result, SCENE_WIDTH, SCENE_HEIGHT),
                    Java2dSupport.toArgb(software, SCENE_WIDTH, SCENE_HEIGHT))));
            checks.add(Checks.info(name + " : pixel hash", () -> Checks.sha256(result)));
            tiles.add(tile(name, result));
        } catch (Exception | Error e) {
            checks.add(e instanceof AWTException ? Check.info(name, Checks.describe(e)) : Check.fail(name,
                    Checks.describe(e)));
            tiles.add(failedTile(name, e));
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    private static String validation(int code) {
        return switch (code) {
            case VolatileImage.IMAGE_OK -> "IMAGE_OK";
            case VolatileImage.IMAGE_RESTORED -> "IMAGE_RESTORED";
            case VolatileImage.IMAGE_INCOMPATIBLE -> "IMAGE_INCOMPATIBLE";
            default -> String.valueOf(code);
        };
    }

    // ----------------------------------------------------------------------------------------------------- checks

    private static List<Check> apiChecks(GraphicsConfiguration gc) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("getType() of the 13 predefined types", true, () -> {
            for (int type : Java2dSupport.predefinedTypes()) {
                if (new BufferedImage(2, 2, type).getType() != type) {
                    return false;
                }
            }
            return true;
        }));
        checks.add(Checks.expect("new BufferedImage(1, 1, 0)", "IllegalArgumentException: Unknown image type 0",
                () -> error(() -> new BufferedImage(1, 1, 0))));
        checks.add(Checks.expect("TYPE_BYTE_BINARY with a 256 color IndexColorModel",
                "IllegalArgumentException: Color map for TYPE_BYTE_BINARY must have no more than 16 entries",
                () -> error(() -> {
                    byte[] ramp = new byte[256];
                    for (int i = 0; i < 256; i++) {
                        ramp[i] = (byte) i;
                    }
                    return new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY, new IndexColorModel(8, 256, ramp, ramp,
                            ramp));
                })));
        checks.add(Checks.expect("getSubimage shares the raster", "true / true", () -> {
            BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            BufferedImage sub = image.getSubimage(10, 10, 20, 20);
            sub.setRGB(0, 0, 0xFF123456);
            return (image.getRGB(10, 10) == 0xFF123456) + " / " + (sub.getRaster().getDataBuffer() == image.getRaster()
                    .getDataBuffer());
        }));
        checks.add(Checks.expect("INT_ARGB coerceData(true) : premultiplied, type, raw pixel of 0x80FF0000",
                "true, TYPE_INT_ARGB, 0x80800000", () -> {
                    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0, 0, 0x80FF0000);
                    image.coerceData(true);
                    int raw = image.getRaster().getDataBuffer().getElem(0);
                    return image.isAlphaPremultiplied() + ", " + Java2dSupport.imageType(image.getType()) + ", 0x"
                            + Integer.toHexString(raw).toUpperCase(java.util.Locale.ROOT);
                }));
        checks.add(Checks.expect("getPropertyNames() / getProperty(\"x\")", "null / UndefinedProperty", () -> {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            return Arrays.toString(image.getPropertyNames()) + " / "
                    + (image.getProperty("x") == Image.UndefinedProperty ? "UndefinedProperty" : "other");
        }));
        checks.add(Checks.expect("createGraphics / getDeviceConfiguration classes (internal)",
                "SunGraphics2D / BufferedImageGraphicsConfig", () -> {
                    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = GraphicsEnvironment.getLocalGraphicsEnvironment().createGraphics(image);
                    try {
                        return g.getClass().getSimpleName() + " / " + g.getDeviceConfiguration().getClass().getSimpleName();
                    } finally {
                        g.dispose();
                    }
                }));
        // BufferedImageGraphicsConfig.getBounds() is unbounded
        checks.add(Checks.expect("image GraphicsConfiguration : bounds / default transform identity",
                "0,0 2147483647x2147483647 / true",
                () -> {
                    BufferedImage image = new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics();
                    try {
                        java.awt.Rectangle r = g.getDeviceConfiguration().getBounds();
                        return r.x + "," + r.y + " " + r.width + "x" + r.height + " / "
                                + g.getDeviceConfiguration().getDefaultTransform().isIdentity();
                    } finally {
                        g.dispose();
                    }
                }));
        checks.add(Checks.expect("setAccelerationPriority(0.3f)", "0.300", () -> {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            image.setAccelerationPriority(0.3f);
            return Checks.num(image.getAccelerationPriority());
        }));
        checks.add(Checks.expect("setAccelerationPriority(2f)",
                "IllegalArgumentException: Priority must be a value between 0 and 1, inclusive",
                () -> error(() -> {
                    new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB).setAccelerationPriority(2f);
                    return null;
                })));
        checks.add(Checks.info("BufferedImage.getCapabilities(screen gc).isAccelerated()",
                () -> new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB).getCapabilities(gc).isAccelerated()));
        checks.add(Checks.expect("getRGB / setRGB round trip on TYPE_USHORT_555_RGB", "#FFFF8400 -> #FFFF8400", () -> {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_USHORT_555_RGB);
            image.setRGB(0, 0, 0xFFFF8400);
            return "#FFFF8400 -> " + Checks.argb(image.getRGB(0, 0));
        }));
        checks.add(Checks.expect("TYPE_BYTE_INDEXED palette size / TYPE_BYTE_BINARY bits per pixel", "256 / 1", () ->
                ((IndexColorModel) new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_INDEXED).getColorModel()).getMapSize()
                        + " / " + new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_BINARY).getColorModel().getPixelSize()));
        return checks;
    }

    private static String error(Callable<?> action) {
        try {
            action.call();
            return "no exception";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
