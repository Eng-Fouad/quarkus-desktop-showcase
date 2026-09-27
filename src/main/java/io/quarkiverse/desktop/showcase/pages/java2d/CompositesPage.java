package io.quarkiverse.desktop.showcase.pages.java2d;

import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.INK;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.VolatileImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Grid;
import io.quarkiverse.desktop.showcase.core.Grid.Tile;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Surface;

/**
 * Composites : the 12 Porter-Duff rules of {@code AlphaComposite} (with and without extra alpha), the XOR mode
 * ({@code Graphics.setXORMode}) on every kind of image (the 13 predefined types, 2 and 4 bit binary images, custom
 * color models and a {@code VolatileImage}), custom {@code Composite}/{@code CompositeContext} implementations, and
 * alpha composites applied to images, text, erasers and punches.
 * <p>
 * Capture method C. XOR mode draws with the XOR loops of each surface type : the native ones, and the generic
 * {@code sun.java2d.loops.Xor*ANY} and {@code XorCopyArgbToAny} loops that Java2D loads by name (not registered by
 * quarkus-awt). Checks : Porter-Duff results against their equations, XOR drawn twice restores every surface,
 * custom composite pixels against their formula, and the {@code AlphaComposite} API.
 */
@Singleton
public class CompositesPage implements FeaturePage {

    private static final int COLUMNS = 6;
    private static final int TILE_WIDTH = 164;
    private static final int TILE_HEIGHT = 150;

    private static final int[] RULES = { AlphaComposite.CLEAR, AlphaComposite.SRC, AlphaComposite.DST,
            AlphaComposite.SRC_OVER, AlphaComposite.DST_OVER, AlphaComposite.SRC_IN, AlphaComposite.DST_IN,
            AlphaComposite.SRC_OUT, AlphaComposite.DST_OUT, AlphaComposite.SRC_ATOP, AlphaComposite.DST_ATOP,
            AlphaComposite.XOR };
    private static final String[] RULE_NAMES = { "CLEAR", "SRC", "DST", "SRC_OVER", "DST_OVER", "SRC_IN", "DST_IN",
            "SRC_OUT", "DST_OUT", "SRC_ATOP", "DST_ATOP", "XOR" };

    private static final int PD_WIDTH = 120;
    private static final int PD_HEIGHT = 90;
    private static final int PD_DST = 0xCC1E88E5;
    private static final int PD_SRC = 0xCCE53935;
    /** Probe points of the Porter-Duff images : destination only, both, source only. */
    private static final int[][] PD_PROBES = { { 20, 40 }, { 60, 45 }, { 95, 45 } };

    private static final int XOR_WIDTH = 120;
    private static final int XOR_HEIGHT = 60;
    private static final int XOR_COLOR = 0x3366CC;

    @Override
    public String id() {
        return "j2d-composites";
    }

    @Override
    public String title() {
        return "Composites and XOR mode";
    }

    @Override
    public String category() {
        return Categories.JAVA2D;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public Component build() {
        List<Check> porterDuff = new ArrayList<>();
        Grid rules = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, ruleTiles(porterDuff));
        BufferedImage rulesImage = rules.paint();

        List<Check> xor = new ArrayList<>();
        Grid xorGrid = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, xorTiles(xor));
        BufferedImage xorImage = xorGrid.paint();

        List<Check> custom = new ArrayList<>();
        Grid other = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, otherTiles(custom));
        BufferedImage otherImage = other.paint();
        custom.addAll(apiChecks());

        return Ui.column(14,
                Ui.text("Composites drawn into TYPE_INT_ARGB images and shown over a checkerboard. Porter-Duff tiles : "
                        + "a blue square (alpha 0.8, destination) then a red disc (alpha 0.8, source) drawn with the "
                        + "rule. XOR tiles : primitives drawn once in XOR mode (color 0x3366CC, XOR color white) into "
                        + "each kind of image, converted for display.", 1000),
                Ui.title("AlphaComposite rules"),
                Ui.image(rulesImage),
                Ui.title("setXORMode on every kind of image"),
                Ui.image(xorImage),
                Ui.title("Custom composites and applied alpha"),
                Ui.image(otherImage),
                ChecksView.table("Porter-Duff results (destination only / both / source only)", porterDuff),
                ChecksView.table("XOR mode : drawn twice restores the image ; probes after one pass (rect / disc / "
                        + "background)", xor),
                ChecksView.table("Custom composites and AlphaComposite API", custom));
    }

    // ---------------------------------------------------------------------------------------------- Porter-Duff

    private static List<Tile> ruleTiles(List<Check> checks) {
        List<Tile> tiles = new ArrayList<>();
        for (float extra : new float[] { 1f, 0.6f }) {
            for (int i = 0; i < RULES.length; i++) {
                int rule = RULES[i];
                String name = RULE_NAMES[i] + (extra == 1f ? "" : " " + extra);
                BufferedImage image = porterDuff(rule, extra);
                checks.add(porterDuffCheck(name, image, rule, extra));
                tiles.add(new Tile(name, (g, w, h) -> {
                    Java2dSupport.checker(g, w, h, 10);
                    g.drawImage(image, (w - PD_WIDTH) / 2, (h - PD_HEIGHT) / 2, null);
                }));
            }
        }
        return tiles;
    }

    private static BufferedImage porterDuff(int rule, float extra) {
        return Java2dSupport.image(PD_WIDTH, PD_HEIGHT, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(PD_DST, true));
            g.fillRect(8, 8, 64, 64);
            g.setComposite(AlphaComposite.getInstance(rule, extra));
            g.setColor(new Color(PD_SRC, true));
            g.fill(new Ellipse2D.Double(40, 20, 70, 64));
        });
    }

    /**
     * The probes of a Porter-Duff image : passed if each is within 3 levels of the result of the rule's equation
     * (8 bit arithmetic in the loops), the value being the exact pixels.
     */
    private static Check porterDuffCheck(String name, BufferedImage image, int rule, float extra) {
        List<String> values = new ArrayList<>();
        boolean ok = true;
        int[] dst = { PD_DST, PD_DST, 0 };
        int[] src = { 0, PD_SRC, PD_SRC };
        for (int p = 0; p < PD_PROBES.length; p++) {
            int actual = image.getRGB(PD_PROBES[p][0], PD_PROBES[p][1]);
            int expected = src[p] == 0 ? dst[p] : expected(rule, src[p], extra, dst[p]);
            values.add(Checks.argb(actual));
            ok &= close(expected, actual);
            if (!close(expected, actual)) {
                values.add("(expected about " + Checks.argb(expected) + ")");
            }
        }
        return Check.of("AlphaComposite " + name, ok, String.join(" / ", values));
    }

    private static boolean close(int expected, int actual) {
        int ea = expected >>> 24;
        int aa = actual >>> 24;
        if (Math.abs(ea - aa) > 2) {
            return false;
        }
        // the loops work on 8 bit premultiplied components : the error of a color is about 255 / alpha levels
        return ea < 8 || Java2dSupport.delta(expected | 0xFF000000, actual | 0xFF000000) <= Math.max(3, 510 / ea);
    }

    /**
     * The Porter-Duff equations of {@code AlphaComposite} for non premultiplied colors : {@code src} with its alpha
     * multiplied by {@code extra}, over {@code dst}.
     */
    static int expected(int rule, int src, float extra, int dst) {
        double as = (src >>> 24) / 255.0 * extra;
        double ad = dst == 0 ? 0 : (dst >>> 24) / 255.0;
        double[] f = switch (rule) {
            case AlphaComposite.CLEAR -> new double[] { 0, 0 };
            case AlphaComposite.SRC -> new double[] { 1, 0 };
            case AlphaComposite.DST -> new double[] { 0, 1 };
            case AlphaComposite.SRC_OVER -> new double[] { 1, 1 - as };
            case AlphaComposite.DST_OVER -> new double[] { 1 - ad, 1 };
            case AlphaComposite.SRC_IN -> new double[] { ad, 0 };
            case AlphaComposite.DST_IN -> new double[] { 0, as };
            case AlphaComposite.SRC_OUT -> new double[] { 1 - ad, 0 };
            case AlphaComposite.DST_OUT -> new double[] { 0, 1 - as };
            case AlphaComposite.SRC_ATOP -> new double[] { ad, 1 - as };
            case AlphaComposite.DST_ATOP -> new double[] { 1 - ad, as };
            default -> new double[] { 1 - ad, 1 - as };
        };
        double ar = as * f[0] + ad * f[1];
        if (ar <= 0) {
            return 0;
        }
        int result = (int) Math.round(ar * 255) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double cs = ((src >> shift) & 0xFF) / 255.0;
            double cd = ((dst >> shift) & 0xFF) / 255.0;
            double c = (as * cs * f[0] + ad * cd * f[1]) / ar;
            result |= (int) Math.round(Math.min(1, c) * 255) << shift;
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------------- XOR

    private static List<Tile> xorTiles(List<Check> checks) {
        List<Tile> tiles = new ArrayList<>();
        List<Surface> once = Java2dSupport.bufferedImages(XOR_WIDTH, XOR_HEIGHT);
        List<Surface> twice = Java2dSupport.bufferedImages(XOR_WIDTH, XOR_HEIGHT);
        List<Surface> none = Java2dSupport.bufferedImages(XOR_WIDTH, XOR_HEIGHT);
        for (int i = 0; i < once.size(); i++) {
            Surface surface = once.get(i);
            String name = surface.name();
            try {
                xor(surface.image(), 1);
                xor(twice.get(i).image(), 2);
                xor(none.get(i).image(), 0);
                boolean restored = Arrays.equals(pixels(twice.get(i).image()), pixels(none.get(i).image()));
                checks.add(Check.of("XOR " + name, restored, "restored " + restored + ", " + xorProbes(surface.image())));
                BufferedImage display;
                try {
                    display = Java2dSupport.toArgb(surface.image(), XOR_WIDTH, XOR_HEIGHT);
                } catch (ArrayIndexOutOfBoundsException e) {
                    // JDK behavior : XOR flips the bits of float samples (drawn twice, they are restored), giving values
                    // out of [0, 1] that ComponentColorModel.getRGB looks up out of its 16 bit table
                    checks.add(Check.info("XOR " + name + " : converted after one pass", Checks.describe(e)));
                    tiles.add(new Tile(caption(name), (g, w, h) -> failed(g, e)));
                    continue;
                }
                tiles.add(new Tile(caption(name), (g, w, h) -> g.drawImage(display, (w - XOR_WIDTH) / 2, 16, null)));
            } catch (RuntimeException | Error e) {
                checks.add(Check.fail("XOR " + name, Checks.describe(e)));
                tiles.add(new Tile(caption(name), (g, w, h) -> failed(g, e)));
            }
        }
        // the accelerated surface of the default screen (D3D, OpenGL, XRender...)
        try {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
            BufferedImage one = volatileSnapshot(gc, g -> xorScene(g, 1));
            BufferedImage two = volatileSnapshot(gc, g -> xorScene(g, 2));
            BufferedImage zero = volatileSnapshot(gc, g -> xorScene(g, 0));
            boolean restored = Arrays.equals(pixels(two), pixels(zero));
            checks.add(Check.of("XOR VolatileImage", restored, "restored " + restored + ", " + xorProbes(one)));
            tiles.add(new Tile("VolatileImage (screen)", (g, w, h) -> g.drawImage(one, (w - XOR_WIDTH) / 2, 16, null)));
        } catch (RuntimeException | Error e) {
            checks.add(Check.fail("XOR VolatileImage", Checks.describe(e)));
            tiles.add(new Tile("VolatileImage (screen)", (g, w, h) -> failed(g, e)));
        }
        return tiles;
    }

    private static String caption(String name) {
        return name.replace("TYPE_", "").replace("CUSTOM ", "CUSTOM\n");
    }

    private static void failed(Graphics2D g, Throwable e) {
        g.setColor(new Color(Ui.ERROR_COLOR));
        g.setFont(Java2dSupport.font(Font.PLAIN, 10));
        String name = e.getClass().getSimpleName();
        for (int i = 0, y = 20; i < name.length(); i += 24, y += 13) {
            g.drawString(name.substring(i, Math.min(name.length(), i + 24)), 4, y);
        }
    }

    private static void xor(BufferedImage image, int passes) {
        Graphics2D g = image.createGraphics();
        try {
            xorScene(g, passes);
        } finally {
            g.dispose();
        }
    }

    /**
     * A white background, then {@code passes} times the same primitives in XOR mode : rectangles, lines, polygons,
     * paths, ovals, aliased and anti-aliased text, anti-aliased shapes and an image.
     */
    private static void xorScene(Graphics2D g, int passes) {
        g.setComposite(AlphaComposite.Src);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, XOR_WIDTH, XOR_HEIGHT);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(XOR_COLOR));
        g.setXORMode(Color.WHITE);
        BufferedImage sprite = Java2dSupport.sprite();
        Path2D.Double triangle = new Path2D.Double();
        triangle.moveTo(26, 34);
        triangle.lineTo(46, 58);
        triangle.lineTo(6, 58);
        triangle.closePath();
        for (int pass = 0; pass < passes; pass++) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.fillRect(6, 6, 40, 24);
            g.drawLine(4, 56, 116, 4);
            g.drawRect(52, 6, 26, 24);
            g.fillOval(84, 6, 30, 30);
            g.drawPolygon(new int[] { 52, 64, 76 }, new int[] { 58, 36, 58 }, 3);
            g.fill(triangle);
            g.draw(new QuadCurve2D.Double(56, 56, 70, 20, 84, 56));
            g.setFont(Java2dSupport.font(Font.BOLD, 12));
            g.drawString("XOR", 80, 54);
            g.drawImage(sprite, 96, 36, 12, 12, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.fill(new Ellipse2D.Double(28, 38, 14, 14));
            g.drawString("aa", 104, 30);
        }
        g.setPaintMode();
    }

    private static String xorProbes(BufferedImage image) {
        return Checks.argb(image.getRGB(20, 12)) + " / " + Checks.argb(image.getRGB(99, 22)) + " / "
                + Checks.argb(image.getRGB(3, 40));
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /**
     * Renders into a new {@code VolatileImage} of {@code gc} (validate / contentsLost loop, at most 5 attempts) and
     * returns its snapshot.
     */
    static BufferedImage volatileSnapshot(GraphicsConfiguration gc, Consumer<Graphics2D> painter) {
        VolatileImage image = gc.createCompatibleVolatileImage(XOR_WIDTH, XOR_HEIGHT);
        try {
            for (int attempt = 0; attempt < 5; attempt++) {
                if (image.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                    image.flush();
                    image = gc.createCompatibleVolatileImage(XOR_WIDTH, XOR_HEIGHT);
                }
                Graphics2D g = image.createGraphics();
                try {
                    painter.accept(g);
                } finally {
                    g.dispose();
                }
                BufferedImage snapshot = image.getSnapshot();
                if (!image.contentsLost()) {
                    return snapshot;
                }
            }
            throw new IllegalStateException("VolatileImage contents lost 5 times");
        } finally {
            image.flush();
        }
    }

    // ---------------------------------------------------------------------------------------- custom and applied

    private static List<Tile> otherTiles(List<Check> checks) {
        List<Tile> tiles = new ArrayList<>();
        for (int mode : new int[] { BlendComposite.MULTIPLY, BlendComposite.DIFFERENCE }) {
            String caption = "custom Composite " + (mode == BlendComposite.MULTIPLY ? "MULTIPLY" : "DIFFERENCE");
            BufferedImage image = blend(mode, BufferedImage.TYPE_INT_ARGB);
            checks.add(Checks.expect(caption + " : pixel (30, 50)",
                    Checks.argb(BlendComposite.blend(mode, 0xFFFFA000, STRIPES[1])), () -> Checks.argb(image.getRGB(30, 50))));
            tiles.add(new Tile(caption, (g, w, h) -> g.drawImage(image, 4, 4, null)));
        }
        BufferedImage rgb = blend(BlendComposite.MULTIPLY, BufferedImage.TYPE_INT_RGB);
        checks.add(Checks.expect("custom Composite MULTIPLY on INT_RGB : pixel (100, 50)",
                Checks.argb(BlendComposite.blend(BlendComposite.MULTIPLY, 0xFFFFA000, STRIPES[4])),
                () -> Checks.argb(rgb.getRGB(100, 50))));
        tiles.add(new Tile("MULTIPLY on TYPE_INT_RGB", (g, w, h) -> g.drawImage(rgb, 4, 4, null)));
        tiles.add(new Tile("drawImage, alpha 0.35 / 0.7", (g, w, h) -> {
            Java2dSupport.checker(g, w, h, 10);
            BufferedImage sprite = Java2dSupport.sprite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            g.drawImage(sprite, 8, 20, 64, 64, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
            g.drawImage(sprite, 76, 20, 64, 64, null);
        }));
        tiles.add(new Tile("text, alpha 0.5", (g, w, h) -> {
            for (int i = 0; i < STRIPES.length; i++) {
                g.setColor(new Color(STRIPES[i], true));
                g.fillRect(i * 25, 0, 25, h);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g.setColor(new Color(INK, true));
            g.setFont(Java2dSupport.font(Font.BOLD, 30));
            g.drawString("Alpha", 22, 50);
            g.setFont(Java2dSupport.font(Font.PLAIN, 14));
            g.drawString("SRC_OVER 0.5", 26, 90);
        }));
        tiles.add(new Tile("SRC : translucent punch", (g, w, h) -> {
            BufferedImage image = Java2dSupport.image(w, h, BufferedImage.TYPE_INT_ARGB, (ig, iw, ih) -> {
                ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ig.setColor(new Color(0xFF37474F, true));
                ig.fillRect(10, 10, iw - 20, ih - 20);
                ig.setComposite(AlphaComposite.Src);
                ig.setColor(new Color(0x40FF5722, true));
                ig.fill(new Ellipse2D.Double(34, 20, 80, 78));
            });
            Java2dSupport.checker(g, w, h, 10);
            g.drawImage(image, 0, 0, null);
        }));
        tiles.add(new Tile("DST_OUT : gradient eraser", (g, w, h) -> {
            BufferedImage image = Java2dSupport.image(w, h, BufferedImage.TYPE_INT_ARGB, (ig, iw, ih) -> {
                for (int i = 0; i < STRIPES.length; i++) {
                    ig.setColor(new Color(STRIPES[i], true));
                    ig.fillRect(0, i * 20, iw, 20);
                }
                ig.setComposite(AlphaComposite.DstOut);
                ig.setPaint(new GradientPaint(0, 0, new Color(0x00000000, true), iw, 0, new Color(0xFF000000, true)));
                ig.fillRect(0, 0, iw, ih);
            });
            Java2dSupport.checker(g, w, h, 10);
            g.drawImage(image, 0, 0, null);
        }));
        tiles.add(new Tile("XOR rubber band over a gradient", (g, w, h) -> {
            g.setPaint(new GradientPaint(0, 0, new Color(0x1A237E), w, h, new Color(0xFFCA28)));
            g.fillRect(0, 0, w, h);
            g.setColor(Color.BLACK);
            g.setXORMode(Color.WHITE);
            g.drawRect(20, 20, 100, 70);
            g.drawRect(30, 30, 80, 50);
            g.drawRect(30, 30, 80, 50);
            g.fillRect(40, 40, 60, 30);
            g.setPaintMode();
        }));
        return tiles;
    }

    private static final int[] STRIPES = { 0xFFE53935, 0xFF43A047, 0xFF1E88E5, 0xFFFDD835, 0xFF8E24AA, 0xFF00ACC1 };

    private static BufferedImage blend(int mode, int type) {
        return Java2dSupport.image(140, 110, type, (g, w, h) -> {
            for (int i = 0; i < STRIPES.length; i++) {
                g.setColor(new Color(STRIPES[i], true));
                g.fillRect(i * 24, 0, 24, h);
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(new BlendComposite(mode));
            g.setColor(new Color(0xFFFFA000, true));
            g.fill(new Ellipse2D.Double(10, 10, 120, 90));
        });
    }

    /**
     * A blend mode as a custom {@code Composite} : Java2D renders it through its general composite pipe (the paint
     * context and the destination as rasters, then {@code compose}). Integer arithmetic.
     */
    static final class BlendComposite implements Composite {

        static final int MULTIPLY = 0;
        static final int DIFFERENCE = 1;

        final int mode;
        final AtomicInteger contexts = new AtomicInteger();

        BlendComposite(int mode) {
            this.mode = mode;
        }

        /** {@code src} blended with {@code dst}, weighted by the source alpha. */
        static int blend(int mode, int src, int dst) {
            int sa = src >>> 24;
            int da = dst >>> 24;
            int result = (sa + (da * (255 - sa) + 127) / 255) << 24;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int s = (src >> shift) & 0xFF;
                int d = (dst >> shift) & 0xFF;
                int b = mode == MULTIPLY ? (s * d + 127) / 255 : Math.abs(s - d);
                result |= ((b * sa + d * (255 - sa) + 127) / 255) << shift;
            }
            return result;
        }

        @Override
        public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
            contexts.incrementAndGet();
            return new CompositeContext() {
                @Override
                public void dispose() {
                }

                @Override
                public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                    int width = Math.min(src.getWidth(), dstIn.getWidth());
                    int height = Math.min(src.getHeight(), dstIn.getHeight());
                    Object sp = null;
                    Object dp = null;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            sp = src.getDataElements(src.getMinX() + x, src.getMinY() + y, sp);
                            dp = dstIn.getDataElements(dstIn.getMinX() + x, dstIn.getMinY() + y, dp);
                            int rgb = blend(mode, srcColorModel.getRGB(sp), dstColorModel.getRGB(dp));
                            dstOut.setDataElements(dstOut.getMinX() + x, dstOut.getMinY() + y,
                                    dstColorModel.getDataElements(rgb, null));
                        }
                    }
                }
            };
        }
    }

    // ----------------------------------------------------------------------------------------------------- checks

    private static List<Check> apiChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("custom Composite : createContext calls per fill", 1, () -> {
            BlendComposite composite = new BlendComposite(BlendComposite.MULTIPLY);
            Java2dSupport.image(20, 20, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
                g.setComposite(composite);
                g.fillRect(2, 2, 16, 16);
            });
            return composite.contexts.get();
        }));
        checks.add(Checks.expect("AlphaComposite.getInstance(SRC_OVER, 0.5) : rule / alpha", "3 / 0.500", () -> {
            AlphaComposite c = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
            return c.getRule() + " / " + Checks.num(c.getAlpha());
        }));
        checks.add(Checks.expect("derive(0.25f) / derive(DST_IN)", "3 0.250 / 6 0.500", () -> {
            AlphaComposite c = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
            AlphaComposite a = c.derive(0.25f);
            AlphaComposite b = c.derive(AlphaComposite.DST_IN);
            return a.getRule() + " " + Checks.num(a.getAlpha()) + " / " + b.getRule() + " " + Checks.num(b.getAlpha());
        }));
        checks.add(Checks.expect("getInstance(SRC_OVER, 1f) is AlphaComposite.SrcOver", true,
                () -> AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f) == AlphaComposite.SrcOver));
        checks.add(Checks.expect("equals / hashCode of equal composites", "true / true", () -> {
            AlphaComposite a = AlphaComposite.getInstance(AlphaComposite.DST_ATOP, 0.3f);
            AlphaComposite b = AlphaComposite.DstAtop.derive(0.3f);
            return a.equals(b) + " / " + (a.hashCode() == b.hashCode());
        }));
        checks.add(Checks.expect("getInstance(SRC_OVER, 1.5f)", "IllegalArgumentException: alpha value out of range",
                () -> error(() -> AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.5f))));
        checks.add(Checks.expect("getInstance(99)", "IllegalArgumentException: unknown composite rule",
                () -> error(() -> AlphaComposite.getInstance(99))));
        checks.add(Checks.expect("getComposite() in XOR mode / after setPaintMode()", "XORComposite / SrcOver", () -> {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setXORMode(Color.WHITE);
                // sun.java2d.loops.XORComposite : the internal composite of the XOR mode
                String xor = g.getComposite().getClass().getSimpleName();
                g.setPaintMode();
                return xor + " / " + (g.getComposite() == AlphaComposite.SrcOver ? "SrcOver" : g.getComposite());
            } finally {
                g.dispose();
            }
        }));
        checks.add(Checks.expect("XOR mode : white XOR (color ^ XOR color) on INT_RGB", "#FF3366CC", () -> {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 4, 4);
                g.setColor(new Color(XOR_COLOR));
                g.setXORMode(Color.WHITE);
                g.fillRect(0, 0, 4, 4);
            } finally {
                g.dispose();
            }
            return Checks.argb(image.getRGB(1, 1));
        }));
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
