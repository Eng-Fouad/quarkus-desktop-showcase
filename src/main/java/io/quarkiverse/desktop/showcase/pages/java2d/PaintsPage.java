package io.quarkiverse.desktop.showcase.pages.java2d;

import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.INK;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint.ColorSpaceType;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.SystemColor;
import java.awt.TexturePaint;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Grid;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Tile;

/**
 * Paints : {@code Color} with alpha, HSB, {@code brighter/darker}, {@code SystemColor} (loaded from the desktop by the
 * toolkit), colors of the other color spaces ({@code LINEAR_RGB}, {@code CIEXYZ}, {@code PYCC}, {@code GRAY} : ICC
 * conversions through LittleCMS and the profiles of java.desktop), {@code GradientPaint} (acyclic, cyclic),
 * {@code LinearGradientPaint} (3 cycle methods x 2 color spaces), {@code RadialGradientPaint} (focus, reflect, repeat,
 * gradient transform), {@code TexturePaint} (anchors, transforms, textures of 3 image types : int, byte and "any"
 * paint contexts) and custom {@code Paint}/{@code PaintContext} implementations.
 * <p>
 * Capture method C. Checks : exact samples of 1-pixel-high gradient strips, cycle method symmetries, texture
 * periodicity, custom paint pixels against their formula, color space conversions, and argument validation.
 */
@Singleton
public class PaintsPage implements FeaturePage {

    private static final int COLUMNS = 6;
    private static final int TILE_WIDTH = 164;
    private static final int TILE_HEIGHT = 150;

    private static final float[] FRACTIONS = { 0f, 0.3f, 0.7f, 1f };
    private static final int[] STOPS = { 0xFFE53935, 0xFFFFEB3B, 0x6643A047, 0xFF1E88E5 };
    /** Samples of the 1-pixel-high strips, gradient from x = 80 to 120 (ColorSpaceType x CycleMethod). */
    private static final String[] LINEAR_SAMPLES = {
            "#FFE53935 #FFE53935 #B3A1C642 #FE1E88E4 #FF1E88E5",
            "#B3A1C642 #FFE53935 #B3A1C642 #FE1E88E4 #B3A1C642",
            "#B3A1C642 #FFE53935 #B3A1C642 #FE1E88E4 #B3A1C642",
            "#FFE53835 #FFE53835 #B3C0CA40 #FE1C88E5 #FF1C88E5",
            "#B3C0CA40 #FFE53835 #B3C0CA40 #FE1C88E5 #B3C0CA40",
            "#B3C0CA40 #FFE53835 #B3C0CA40 #FE1C88E5 #B3C0CA40" };

    @Override
    public String id() {
        return "j2d-paints";
    }

    @Override
    public String title() {
        return "Paints and colors";
    }

    @Override
    public String category() {
        return Categories.JAVA2D;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Component build() {
        Grid grid = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, tiles());
        BufferedImage image = grid.paint();
        return Ui.column(14,
                Ui.text("Colors and paints drawn into a TYPE_INT_ARGB image, over a checkerboard where the paint is "
                        + "translucent. Red ticks mark the start and end points of the gradients.", 1000),
                Ui.image(image),
                ChecksView.table("Colors and color spaces", colorChecks()),
                ChecksView.table("Paints", paintChecks(grid, image)));
    }

    // ------------------------------------------------------------------------------------------------------ tiles

    private static List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile("Color alpha 0x99", (g, w, h) -> {
            Java2dSupport.checker(g, w, h, 10);
            g.setColor(new Color(0x99E53935, true));
            g.fill(new Ellipse2D.Double(22, 6, 66, 66));
            g.setColor(new Color(0x9943A047, true));
            g.fill(new Ellipse2D.Double(60, 6, 66, 66));
            g.setColor(new Color(0x991E88E5, true));
            g.fill(new Ellipse2D.Double(41, 42, 66, 66));
        }));
        tiles.add(new Tile("Color.getHSBColor : 24 hues", (g, w, h) -> {
            for (int i = 0; i < 24; i++) {
                g.setColor(Color.getHSBColor(i / 24f, 0.75f, 0.95f));
                g.fill(new Arc2D.Double(24, 4, 100, 100, i * 15, 15.5, Arc2D.PIE));
            }
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(54, 34, 40, 40));
        }));
        tiles.add(new Tile("darker() / brighter()", (g, w, h) -> {
            Color darker = new Color(0x3F7FBF);
            Color brighter = new Color(0x3F7FBF);
            for (int i = 0; i < 6; i++) {
                g.setColor(darker);
                g.fillRect(4 + i * 24, 10, 22, 42);
                g.setColor(brighter);
                g.fillRect(4 + i * 24, 62, 22, 42);
                darker = darker.darker();
                brighter = brighter.brighter();
            }
        }));
        tiles.add(new Tile("SystemColor", (g, w, h) -> {
            Color[] colors = systemColors();
            for (int i = 0; i < colors.length; i++) {
                int x = 6 + (i % 4) * 35;
                int y = 4 + (i / 4) * 36;
                g.setColor(colors[i]);
                g.fillRect(x, y, 32, 32);
                g.setColor(new Color(INK, true));
                g.drawRect(x, y, 31, 31);
            }
        }));
        tiles.add(new Tile("GradientPaint acyclic", (g, w, h) -> {
            g.setPaint(new GradientPaint(40, 20, new Color(0xFF6F00), 108, 90, new Color(0x283593)));
            g.fill(new RoundRectangle2D.Double(4, 4, w - 8, h - 8, 20, 20));
            ticks(g, 40, 20, 108, 90);
        }));
        tiles.add(new Tile("GradientPaint cyclic", (g, w, h) -> {
            g.setPaint(new GradientPaint(60, 40, new Color(0xFF6F00), 80, 60, new Color(0x283593), true));
            g.fill(new RoundRectangle2D.Double(4, 4, w - 8, h - 8, 20, 20));
            ticks(g, 60, 40, 80, 60);
        }));
        for (ColorSpaceType space : ColorSpaceType.values()) {
            for (CycleMethod cycle : CycleMethod.values()) {
                tiles.add(new Tile("Linear " + cycle + "\n" + space, (g, w, h) -> {
                    Java2dSupport.checker(g, w, h, 10);
                    g.setPaint(new LinearGradientPaint(new Point2D.Double(52, 0), new Point2D.Double(96, 0), FRACTIONS,
                            colors(STOPS), cycle, space, new AffineTransform()));
                    g.fillRect(0, 12, w, h - 24);
                    ticks(g, 52, 12, 96, 12);
                }));
            }
        }
        tiles.add(new Tile("Radial NO_CYCLE", (g, w, h) -> {
            g.setPaint(radial(74, 55, 50, 74, 55, CycleMethod.NO_CYCLE, new AffineTransform()));
            g.fillRect(0, 0, w, h);
        }));
        tiles.add(new Tile("Radial with focus", (g, w, h) -> {
            g.setPaint(radial(74, 55, 50, 50, 35, CycleMethod.NO_CYCLE, new AffineTransform()));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(Java2dSupport.ACCENT, true));
            g.fill(new Ellipse2D.Double(48, 33, 4, 4));
        }));
        tiles.add(new Tile("Radial REFLECT, r = 20", (g, w, h) -> {
            g.setPaint(radial(74, 55, 20, 74, 55, CycleMethod.REFLECT, new AffineTransform()));
            g.fillRect(0, 0, w, h);
        }));
        tiles.add(new Tile("Radial REPEAT, transformed", (g, w, h) -> {
            AffineTransform tx = new AffineTransform();
            tx.rotate(Math.toRadians(30), 74, 55);
            tx.translate(74, 55);
            tx.scale(1, 0.5);
            tx.translate(-74, -55);
            g.setPaint(radial(74, 55, 18, 74, 55, CycleMethod.REPEAT, tx));
            g.fillRect(0, 0, w, h);
        }));
        tiles.add(new Tile("TexturePaint INT_ARGB", (g, w, h) -> {
            Java2dSupport.checker(g, w, h, 10);
            g.setPaint(new TexturePaint(texture(BufferedImage.TYPE_INT_ARGB), new Rectangle2D.Double(0, 0, 16, 16)));
            g.fill(new Ellipse2D.Double(4, 4, w - 8, h - 8));
        }));
        tiles.add(new Tile("TexturePaint anchor 5,5 24x24", (g, w, h) -> {
            g.setPaint(new TexturePaint(texture(BufferedImage.TYPE_INT_ARGB), new Rectangle2D.Double(5, 5, 24, 24)));
            g.fillRect(0, 0, w, h);
        }));
        tiles.add(new Tile("TexturePaint rotated 30", (g, w, h) -> {
            g.rotate(Math.toRadians(30), w / 2.0, h / 2.0);
            g.setPaint(new TexturePaint(texture(BufferedImage.TYPE_INT_RGB), new Rectangle2D.Double(0, 0, 16, 16)));
            g.fill(new Rectangle2D.Double(24, 14, 100, 90));
        }));
        tiles.add(new Tile("TexturePaint BYTE_INDEXED", (g, w, h) -> {
            g.setPaint(new TexturePaint(texture(BufferedImage.TYPE_BYTE_INDEXED), new Rectangle2D.Double(0, 0, 16, 16)));
            g.fill(new RoundRectangle2D.Double(4, 4, w - 8, h - 8, 30, 30));
        }));
        tiles.add(new Tile("TexturePaint USHORT_565_RGB", (g, w, h) -> {
            g.setPaint(new TexturePaint(texture(BufferedImage.TYPE_USHORT_565_RGB),
                    new Rectangle2D.Double(0, 0, 16, 16)));
            g.scale(1.5, 1.5);
            g.fill(new Ellipse2D.Double(4, 4, 90, 70));
        }));
        tiles.add(new Tile("custom Paint : XOR pattern", (g, w, h) -> {
            g.setPaint(new PatternPaint(PatternPaint.XOR));
            g.fillRect(0, 0, w, h);
        }));
        tiles.add(new Tile("custom Paint : rings", (g, w, h) -> {
            g.setPaint(new PatternPaint(PatternPaint.RINGS));
            g.fill(new Ellipse2D.Double(4, 4, w - 8, h - 8));
        }));
        tiles.add(new Tile("gradient text", (g, w, h) -> {
            g.setFont(Java2dSupport.font(Font.BOLD, 40));
            g.setPaint(new LinearGradientPaint(0, 20, 0, 90, new float[] { 0f, 0.5f, 1f },
                    colors(new int[] { 0xFFE53935, 0xFF8E24AA, 0xFF1E88E5 })));
            g.drawString("Paint", 14, 60);
            g.setPaint(new GradientPaint(0, 70, new Color(0xFB8C00), 148, 70, new Color(0x00897B)));
            g.setFont(Java2dSupport.font(Font.BOLD, 22));
            g.drawString("gradient", 26, 100);
        }));
        tiles.add(new Tile("gradient dashed stroke", (g, w, h) -> {
            g.setPaint(new GradientPaint(0, 0, new Color(0xD81B60), 30, 30, new Color(0x1E88E5), true));
            g.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] { 18, 14 }, 0));
            g.draw(new Ellipse2D.Double(16, 12, 116, 88));
        }));
        tiles.add(new Tile("colors of other color spaces", (g, w, h) -> {
            Color[] colors = colorSpaceColors();
            for (int i = 0; i < colors.length; i++) {
                g.setColor(colors[i]);
                g.fillRect(4 + (i % 4) * 35, 8 + (i / 4) * 52, 32, 46);
            }
        }));
        tiles.add(new Tile("fade to transparent", (g, w, h) -> {
            Java2dSupport.checker(g, w, h, 10);
            g.setPaint(new LinearGradientPaint(0, 0, w, 0, new float[] { 0f, 1f },
                    new Color[] { new Color(0xFF6A1B9A, true), new Color(0x006A1B9A, true) }));
            g.fillRect(0, 10, w, 44);
            g.setPaint(new RadialGradientPaint(74, 84, 40, new float[] { 0f, 1f },
                    new Color[] { new Color(0xFF00897B, true), new Color(0x0000897B, true) }));
            g.fillRect(0, 58, w, h - 58);
        }));
        tiles.add(new Tile("12 stops, LINEAR_RGB", (g, w, h) -> {
            float[] fractions = new float[12];
            Color[] colors = new Color[12];
            for (int i = 0; i < 12; i++) {
                fractions[i] = i / 11f;
                colors[i] = Color.getHSBColor(i / 12f, 0.9f, 0.9f);
            }
            g.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(w, h), fractions, colors,
                    CycleMethod.NO_CYCLE, ColorSpaceType.LINEAR_RGB, new AffineTransform()));
            g.fillRect(0, 0, w, h);
        }));
        tiles.add(new Tile("gradient fill, AA off / on", (g, w, h) -> {
            GradientPaint paint = new GradientPaint(0, 10, new Color(0x43A047), 0, 100, new Color(0x1B5E20));
            g.setPaint(paint);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.fill(new Ellipse2D.Double(4, 12, 66, 94));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.fill(new Ellipse2D.Double(78, 12, 66, 94));
        }));
        return tiles;
    }

    private static Color[] colors(int[] argb) {
        Color[] colors = new Color[argb.length];
        for (int i = 0; i < argb.length; i++) {
            colors[i] = new Color(argb[i], true);
        }
        return colors;
    }

    private static RadialGradientPaint radial(double cx, double cy, float radius, double fx, double fy, CycleMethod cycle,
            AffineTransform tx) {
        return new RadialGradientPaint(new Point2D.Double(cx, cy), radius, new Point2D.Double(fx, fy),
                new float[] { 0f, 0.5f, 1f }, colors(new int[] { 0xFFFFFFFF, 0xFFFFA726, 0xFFB71C1C }), cycle,
                ColorSpaceType.SRGB, tx);
    }

    /** Red ticks at the start and end points of a gradient. */
    private static void ticks(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.setColor(new Color(Java2dSupport.ACCENT, true));
        g.setStroke(new BasicStroke(1.5f));
        for (double[] p : new double[][] { { x1, y1 }, { x2, y2 } }) {
            g.draw(new java.awt.geom.Line2D.Double(p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4));
            g.draw(new java.awt.geom.Line2D.Double(p[0] - 4, p[1] + 4, p[0] + 4, p[1] - 4));
        }
    }

    /**
     * A 16 x 16 texture of {@code type} : a quarter checker with a disc (translucent where the type has alpha).
     */
    static BufferedImage texture(int type) {
        return Java2dSupport.image(16, 16, type, (g, w, h) -> {
            g.setColor(new Color(0xFFFFF8E1, true));
            g.fillRect(0, 0, 16, 16);
            g.setColor(new Color(0xFF5D4037, true));
            g.fillRect(0, 0, 8, 8);
            g.fillRect(8, 8, 8, 8);
            g.setColor(new Color(0x9926A69A, true));
            g.fillOval(3, 3, 10, 10);
            g.setComposite(java.awt.AlphaComposite.Src);
            g.setColor(new Color(0x00000000, true));
            g.fillRect(15, 0, 1, 16);
        });
    }

    private static Color[] systemColors() {
        return new Color[] { SystemColor.desktop, SystemColor.activeCaption, SystemColor.inactiveCaption,
                SystemColor.window, SystemColor.windowText, SystemColor.menu, SystemColor.menuText, SystemColor.control,
                SystemColor.controlShadow, SystemColor.controlDkShadow, SystemColor.textHighlight, SystemColor.info };
    }

    private static final String[] SYSTEM_COLOR_NAMES = { "desktop", "activeCaption", "inactiveCaption", "window",
            "windowText", "menu", "menuText", "control", "controlShadow", "controlDkShadow", "textHighlight", "info" };

    /**
     * Colors created in the LINEAR_RGB, CIEXYZ, PYCC and GRAY color spaces (converted to sRGB by the color management
     * module, LittleCMS).
     */
    private static Color[] colorSpaceColors() {
        ColorSpace linear = ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
        ColorSpace xyz = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
        ColorSpace pycc = ColorSpace.getInstance(ColorSpace.CS_PYCC);
        ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        return new Color[] {
                new Color(linear, new float[] { 0.5f, 0.1f, 0.05f }, 1f),
                new Color(linear, new float[] { 0.05f, 0.4f, 0.1f }, 1f),
                new Color(xyz, new float[] { 0.4f, 0.3f, 0.6f }, 1f),
                new Color(xyz, new float[] { 0.6f, 0.7f, 0.2f }, 1f),
                new Color(pycc, new float[] { 0.5f, 0.3f, 0.7f }, 1f),
                new Color(pycc, new float[] { 0.6f, 0.6f, 0.3f }, 1f),
                new Color(gray, new float[] { 0.2f }, 1f),
                new Color(gray, new float[] { 0.6f }, 1f) };
    }

    // ------------------------------------------------------------------------------------------------ custom paint

    /**
     * A procedural paint computed with integer arithmetic in user space (translation only) : an XOR pattern or
     * concentric rings.
     */
    static final class PatternPaint implements Paint {

        static final int XOR = 0;
        static final int RINGS = 1;

        final int kind;
        final AtomicInteger contexts = new AtomicInteger();

        PatternPaint(int kind) {
            this.kind = kind;
        }

        static int color(int kind, int x, int y) {
            if (kind == XOR) {
                int v = (x ^ y) & 0xFF;
                return 0xFF000000 | v << 16 | (255 - v) << 8 | ((v * 3) & 0xFF);
            }
            int dx = x - 74;
            int dy = y - 55;
            int band = ((dx * dx + dy * dy) >> 6) % 6;
            return new int[] { 0xFF1A237E, 0xFF3949AB, 0xFF7986CB, 0xFFC5CAE9, 0xFF7986CB, 0xFF3949AB }[band];
        }

        @Override
        public PaintContext createContext(ColorModel cm, Rectangle deviceBounds, Rectangle2D userBounds,
                AffineTransform xform, RenderingHints hints) {
            contexts.incrementAndGet();
            int tx = (int) Math.round(xform.getTranslateX());
            int ty = (int) Math.round(xform.getTranslateY());
            return new PaintContext() {
                @Override
                public void dispose() {
                }

                @Override
                public ColorModel getColorModel() {
                    return ColorModel.getRGBdefault();
                }

                @Override
                public Raster getRaster(int x, int y, int w, int h) {
                    WritableRaster raster = getColorModel().createCompatibleWritableRaster(w, h);
                    int[] pixels = new int[w * h];
                    for (int j = 0; j < h; j++) {
                        for (int i = 0; i < w; i++) {
                            pixels[j * w + i] = color(kind, x + i - tx, y + j - ty);
                        }
                    }
                    raster.setDataElements(0, 0, w, h, pixels);
                    return raster;
                }
            };
        }

        @Override
        public int getTransparency() {
            return Transparency.OPAQUE;
        }
    }

    // ----------------------------------------------------------------------------------------------------- checks

    private static List<Check> colorChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("Color.decode(\"#1E90FF\")", "#FF1E90FF",
                () -> Checks.argb(Color.decode("#1E90FF").getRGB())));
        checks.add(Checks.expect("new Color(1f, 0.5f, 0.25f, 0.5f)", "#80FF8040",
                () -> Checks.argb(new Color(1f, 0.5f, 0.25f, 0.5f).getRGB())));
        checks.add(Checks.expect("Color.RGBtoHSB(30, 144, 255)", "0.582 0.882 1.000",
                () -> floats(Color.RGBtoHSB(30, 144, 255, null))));
        checks.add(Checks.expect("Color.HSBtoRGB(0.5, 0.5, 0.5)", "#FF408080",
                () -> Checks.argb(Color.HSBtoRGB(0.5f, 0.5f, 0.5f))));
        checks.add(Checks.expect("0x3F7FBF darker() / brighter()", "#FF2C5885 / #FF5AB5FF", () -> {
            Color c = new Color(0x3F7FBF);
            return Checks.argb(c.darker().getRGB()) + " / " + Checks.argb(c.brighter().getRGB());
        }));
        checks.add(Checks.expect("getRGBComponents of 0x80FF8040", "1.000 0.502 0.251 0.502",
                () -> floats(new Color(0x80FF8040, true).getRGBComponents(null))));
        checks.add(Checks.expect("Color equals / hashCode = getRGB", "true / true", () -> {
            Color a = new Color(12, 34, 56, 78);
            Color b = new Color(0x4E0C2238, true);
            return a.equals(b) + " / " + (a.hashCode() == a.getRGB());
        }));
        checks.add(Checks.expect("sRGB.toCIEXYZ(1, 1, 1) (D50)", "0.964 1.000 0.825",
                () -> floats(ColorSpace.getInstance(ColorSpace.CS_sRGB).toCIEXYZ(new float[] { 1, 1, 1 }))));
        checks.add(Checks.expect("LINEAR_RGB.fromRGB(0.5, 0.5, 0.5)", "0.214 0.214 0.214",
                () -> floats(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB).fromRGB(new float[] { 0.5f, 0.5f, 0.5f }))));
        checks.add(Checks.expect("Color(0x808080).getColorComponents(LINEAR_RGB)", "0.216 0.216 0.216",
                () -> floats(new Color(0x808080).getColorComponents(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), null))));
        checks.add(Checks.expect("colors of LINEAR_RGB, CIEXYZ, PYCC, GRAY",
                "#FFBB593F #FF3FA959 #FFB77CE7 #FFD2E270 #FFEFB92E #FF43FFF7 #FF7B7B7B #FFCBCBCB", () -> {
            List<String> values = new ArrayList<>();
            for (Color c : colorSpaceColors()) {
                values.add(Checks.argb(c.getRGB()));
            }
            return String.join(" ", values);
        }));
        checks.add(Checks.expect("PYCC : type / components / name(0)", "13 / 3 / Unnamed color component(0)", () -> {
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_PYCC);
            return cs.getType() + " / " + cs.getNumComponents() + " / " + cs.getName(0);
        }));
        checks.add(Checks.expect("CIEXYZ : min / max value of component 0", "0.000 / 2.000",
                () -> Checks.num(ColorSpace.getInstance(ColorSpace.CS_CIEXYZ).getMinValue(0)) + " / "
                        + Checks.num(ColorSpace.getInstance(ColorSpace.CS_CIEXYZ).getMaxValue(0))));
        checks.add(Checks.expect("Color(GRAY 0.5).getColorSpace() / getColorComponents()", "GRAY / 0.500", () -> {
            Color c = new Color(ColorSpace.getInstance(ColorSpace.CS_GRAY), new float[] { 0.5f }, 1f);
            return (c.getColorSpace().getType() == ColorSpace.TYPE_GRAY ? "GRAY" : "other") + " / "
                    + floats(c.getColorComponents(null));
        }));
        checks.add(Checks.info("SystemColor values", () -> {
            Color[] colors = systemColors();
            List<String> values = new ArrayList<>();
            for (int i = 0; i < colors.length; i++) {
                values.add(SYSTEM_COLOR_NAMES[i] + "=" + Checks.argb(colors[i].getRGB()));
            }
            return String.join(" ", values);
        }));
        checks.add(Checks.expect("SystemColor.window.getTransparency()", "OPAQUE",
                () -> Java2dSupport.transparency(SystemColor.window.getTransparency())));
        return checks;
    }

    private static List<Check> paintChecks(Grid grid, BufferedImage image) {
        List<Check> checks = new ArrayList<>();
        // 1 pixel high strips : x from 0 to 200
        GradientPaint acyclic = new GradientPaint(0, 0, new Color(0xFF0000), 100, 0, new Color(0x0000FF));
        GradientPaint cyclic = new GradientPaint(0, 0, new Color(0xFF0000), 100, 0, new Color(0x0000FF), true);
        checks.add(Checks.expect("GradientPaint samples x = 0 / 25 / 50 / 75 / 100",
                "#FFFF0000 #FFBF003F #FF7F007F #FF3F00BF #FF0000FF", () -> samples(strip(acyclic),
                0, 25, 50, 75, 100)));
        checks.add(Checks.expect("GradientPaint acyclic : x = 150 is the end color", true,
                () -> strip(acyclic).getRGB(150, 0) == strip(acyclic).getRGB(100, 0)));
        // the gradient contexts sample integer coordinates through lookup tables : symmetries hold within 2 levels
        checks.add(symmetric("GradientPaint cyclic : x = 150 mirrors x = 50", strip(cyclic), 150, 50));
        for (ColorSpaceType space : ColorSpaceType.values()) {
            for (CycleMethod cycle : CycleMethod.values()) {
                LinearGradientPaint paint = new LinearGradientPaint(new Point2D.Double(80, 0), new Point2D.Double(120, 0),
                        FRACTIONS, colors(STOPS), cycle, space, new AffineTransform());
                checks.add(Checks.expect("Linear " + cycle + " " + space + " x = 60 / 80 / 100 / 120 / 140",
                        LINEAR_SAMPLES[space.ordinal() * 3 + cycle.ordinal()],
                        () -> samples(strip(paint), 60, 80, 100, 120, 140)));
            }
        }
        checks.add(symmetric("Linear REFLECT : x = 70 mirrors x = 90 around 80", strip(new LinearGradientPaint(
                new Point2D.Double(80, 0), new Point2D.Double(120, 0), FRACTIONS, colors(STOPS), CycleMethod.REFLECT)),
                70, 90));
        checks.add(symmetric("Linear REPEAT : x = 130 repeats x = 90", strip(new LinearGradientPaint(
                new Point2D.Double(80, 0), new Point2D.Double(120, 0), FRACTIONS, colors(STOPS), CycleMethod.REPEAT)),
                130, 90));
        checks.add(Checks.expect("black to white midpoint : SRGB / LINEAR_RGB", "#FF7F7F7F / #FFBBBBBB", () -> {
            List<String> values = new ArrayList<>();
            for (ColorSpaceType space : ColorSpaceType.values()) {
                values.add(Checks.argb(strip(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(200, 0),
                        new float[] { 0, 1 }, new Color[] { Color.BLACK, Color.WHITE }, CycleMethod.NO_CYCLE, space,
                        new AffineTransform())).getRGB(100, 0)));
            }
            return String.join(" / ", values);
        }));
        checks.add(Checks.expect("Radial : center / radius 25 / beyond the radius", "#FFFFFFFF #FFFFA727 #FFB71C1C", () -> {
            BufferedImage strip = strip(new RadialGradientPaint(new Point2D.Double(100, 0), 50,
                    new Point2D.Double(100, 0), new float[] { 0f, 0.5f, 1f },
                    colors(new int[] { 0xFFFFFFFF, 0xFFFFA726, 0xFFB71C1C }), CycleMethod.NO_CYCLE));
            return samples(strip, 100, 125, 180);
        }));
        checks.add(symmetric("Radial REFLECT : x = 110 mirrors x = 130 (r = 20)", strip(new RadialGradientPaint(
                new Point2D.Double(100, 0.5), 20, new Point2D.Double(100, 0.5), new float[] { 0f, 1f },
                colors(new int[] { 0xFFFFFFFF, 0xFF000000 }), CycleMethod.REFLECT)), 110, 130));
        checks.add(Checks.expect("RadialGradientPaint getters", "74.0,55.0 18.0 74.0,55.0 REPEAT SRGB 3", () -> {
            RadialGradientPaint p = radial(74, 55, 18, 74, 55, CycleMethod.REPEAT, new AffineTransform());
            return p.getCenterPoint().getX() + "," + p.getCenterPoint().getY() + " " + p.getRadius() + " "
                    + p.getFocusPoint().getX() + "," + p.getFocusPoint().getY() + " " + p.getCycleMethod() + " "
                    + p.getColorSpace() + " " + p.getFractions().length;
        }));
        checks.add(Checks.expect("LinearGradientPaint getters", "0.0 0.3 0.7 1.0 / REFLECT / LINEAR_RGB / identity true",
                () -> {
                    LinearGradientPaint p = new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(1, 0),
                            FRACTIONS, colors(STOPS), CycleMethod.REFLECT, ColorSpaceType.LINEAR_RGB,
                            new AffineTransform());
                    StringBuilder sb = new StringBuilder();
                    for (float f : p.getFractions()) {
                        sb.append(sb.isEmpty() ? "" : " ").append(f);
                    }
                    return sb + " / " + p.getCycleMethod() + " / " + p.getColorSpace() + " / identity "
                            + p.getTransform().isIdentity();
                }));
        checks.add(Checks.expect("getTransparency : translucent Color / GradientPaint / Linear with alpha stop",
                "TRANSLUCENT / OPAQUE / TRANSLUCENT", () -> Java2dSupport.transparency(new Color(0x80FF0000, true)
                        .getTransparency()) + " / " + Java2dSupport.transparency(acyclic.getTransparency()) + " / "
                        + Java2dSupport.transparency(new LinearGradientPaint(0, 0, 1, 0, FRACTIONS, colors(STOPS))
                                .getTransparency())));
        checks.add(Checks.expect("TexturePaint getTransparency INT_RGB / INT_ARGB", "OPAQUE / TRANSLUCENT",
                () -> Java2dSupport.transparency(new TexturePaint(texture(BufferedImage.TYPE_INT_RGB),
                        new Rectangle2D.Double(0, 0, 16, 16)).getTransparency()) + " / "
                        + Java2dSupport.transparency(new TexturePaint(texture(BufferedImage.TYPE_INT_ARGB),
                                new Rectangle2D.Double(0, 0, 16, 16)).getTransparency())));
        for (int type : new int[] { BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_BYTE_INDEXED,
                BufferedImage.TYPE_USHORT_565_RGB }) {
            // a USHORT_565_RGB texture goes through the generic (color model) paint context, which expands 5 and 6 bit
            // components with a rounding different from DirectColorModel.getRGB
            checks.add(Checks.expect("TexturePaint " + Java2dSupport.imageType(type) + " : periodic, max delta to texture",
                    type == BufferedImage.TYPE_USHORT_565_RGB ? "true / 1" : "true / 0", () -> {
                        BufferedImage tex = texture(type);
                        BufferedImage img = Java2dSupport.image(64, 64, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
                            g.setPaint(new TexturePaint(tex, new Rectangle2D.Double(0, 0, 16, 16)));
                            g.fillRect(0, 0, 64, 64);
                        });
                        boolean periodic = true;
                        int delta = 0;
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                periodic &= img.getRGB(x, y) == img.getRGB(x + 32, y + 16);
                                delta = Math.max(delta, Java2dSupport.delta(img.getRGB(x, y), tex.getRGB(x, y)));
                            }
                        }
                        return periodic + " / " + delta;
                    }));
        }
        checks.add(Checks.expect("custom Paint : createContext calls per fill", 1, () -> {
            PatternPaint paint = new PatternPaint(PatternPaint.XOR);
            Java2dSupport.image(40, 40, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
                g.setPaint(paint);
                g.fill(new Ellipse2D.Double(0, 0, 40, 40));
            });
            return paint.contexts.get();
        }));
        checks.add(pattern(grid, image, "custom Paint : XOR pattern", PatternPaint.XOR, 37, 91));
        checks.add(pattern(grid, image, "custom Paint : rings", PatternPaint.RINGS, 74, 30));
        checks.add(Checks.expect("LinearGradientPaint(0, 0, 0, 0, ...)",
                "IllegalArgumentException: Start point cannot equalendpoint" /* sic, JDK message */,
                () -> error(() -> new LinearGradientPaint(0, 0, 0, 0, FRACTIONS, colors(STOPS)))));
        checks.add(Checks.expect("fractions { 0, 0.5, 0.5 }",
                "IllegalArgumentException: Keyframe fractions must be increasing: 0.5",
                () -> error(() -> new LinearGradientPaint(0, 0, 10, 0, new float[] { 0, 0.5f, 0.5f },
                        colors(new int[] { 0xFF000000, 0xFF808080, 0xFFFFFFFF })))));
        checks.add(Checks.expect("RadialGradientPaint radius 0", "IllegalArgumentException: Radius must be greater than zero",
                () -> error(() -> new RadialGradientPaint(0, 0, 0, FRACTIONS, colors(STOPS)))));
        checks.add(Checks.expect("GradientPaint with a null color", "NullPointerException: Colors cannot be null",
                () -> error(() -> new GradientPaint(0, 0, null, 1, 1, Color.RED))));
        checks.add(Checks.info("HSB wheel tile : pixel hash", () -> Checks.sha256(tile(grid, image,
                "Color.getHSBColor : 24 hues"))));
        return checks;
    }

    /** Pixels {@code x1} and {@code x2} of {@code strip} are equal within 2 levels per channel. */
    private static Check symmetric(String name, BufferedImage strip, int x1, int x2) {
        int a = strip.getRGB(x1, 0);
        int b = strip.getRGB(x2, 0);
        return Check.of(name, Java2dSupport.delta(a, b) <= 2, Checks.argb(a) + " / " + Checks.argb(b));
    }

    private static Check pattern(Grid grid, BufferedImage image, String caption, int kind, int x, int y) {
        return Checks.expect(caption + " : pixel (" + x + ", " + y + ")", Checks.argb(PatternPaint.color(kind, x, y)),
                () -> grid.probe(image, caption, x, y));
    }

    private static BufferedImage tile(Grid grid, BufferedImage image, String caption) {
        java.awt.Point o = grid.origin(grid.index(caption));
        return image.getSubimage(o.x, o.y, grid.areaWidth(), grid.areaHeight());
    }

    /** A 201 x 1 strip filled with {@code paint}. */
    private static BufferedImage strip(Paint paint) {
        return Java2dSupport.image(201, 1, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setPaint(paint);
            g.fillRect(0, 0, w, h);
        });
    }

    private static String samples(BufferedImage strip, int... xs) {
        List<String> values = new ArrayList<>();
        for (int x : xs) {
            values.add(Checks.argb(strip.getRGB(x, 0)));
        }
        return String.join(" ", values);
    }

    private static String floats(float[] values) {
        List<String> list = new ArrayList<>();
        for (float v : values) {
            list.add(Checks.num(v));
        }
        return String.join(" ", list);
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
