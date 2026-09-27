package io.quarkiverse.desktop.showcase.pages.java2d;

import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.ACCENT;
import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.FILLS;
import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.INK;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

/**
 * Transforms, clipping and rendering hints : {@code AffineTransform} operations (translate, rotate, scale, flip, shear,
 * quadrant rotations, concatenation order, nested transforms), transformed text and images ({@code drawImage} with an
 * {@code AffineTransform}, {@code drawRenderedImage}), clips (intersected rectangles, ellipses, areas with holes, text
 * outlines, rotated clips, {@code Graphics.create}), {@code copyArea}, and every {@code RenderingHints} key :
 * anti-aliasing, interpolation (up and down scaling), dithering, alpha interpolation, color rendering, text
 * anti-aliasing modes (LCD included), LCD contrast, fractional metrics, resolution variants of multi-resolution images.
 * <p>
 * Capture method C. Scaled and rotated images go through the native transform helpers (nearest neighbor, bilinear and
 * bicubic loops), clips through the span clip renderer and regions. Checks : matrices, types and inverses of
 * transforms, clip state of {@code Graphics2D}, pixel probes, interpolated samples and the hint keys and values.
 */
@Singleton
public class TransformsClipPage implements FeaturePage {

    private static final int COLUMNS = 6;
    private static final int TILE_WIDTH = 164;
    private static final int TILE_HEIGHT = 150;
    /** Center of the drawing area of a tile. */
    private static final double CX = 74;
    private static final double CY = 59;

    @Override
    public String id() {
        return "j2d-transforms-clip";
    }

    @Override
    public String title() {
        return "Transforms, clipping and hints";
    }

    @Override
    public String category() {
        return Categories.JAVA2D;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Component build() {
        Grid transforms = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, transformTiles());
        BufferedImage transformsImage = transforms.paint();
        Grid hints = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, hintTiles());
        BufferedImage hintsImage = hints.paint();
        return Ui.column(14,
                Ui.text("Transforms and clips drawn into TYPE_INT_ARGB images : the dashed outline is the untransformed "
                        + "figure. Hint tiles show magnified pixels (nearest neighbor) where the difference is small.",
                        1000),
                Ui.title("Transforms and clipping"),
                Ui.image(transformsImage),
                Ui.title("Rendering hints"),
                Ui.image(hintsImage),
                ChecksView.table("AffineTransform", transformChecks()),
                ChecksView.table("Graphics2D transform and clip state, copyArea", clipChecks(transforms, transformsImage)),
                ChecksView.table("Rendering hints", hintChecks(hints, hintsImage)));
    }

    // ------------------------------------------------------------------------------------------ transform tiles

    /** An "F" (36 x 50), asymmetric : its orientation shows the transform. */
    static Path2D figure(double x, double y) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(0, 0);
        p.lineTo(36, 0);
        p.lineTo(36, 10);
        p.lineTo(12, 10);
        p.lineTo(12, 20);
        p.lineTo(28, 20);
        p.lineTo(28, 30);
        p.lineTo(12, 30);
        p.lineTo(12, 50);
        p.lineTo(0, 50);
        p.closePath();
        p.transform(AffineTransform.getTranslateInstance(x, y));
        return p;
    }

    /** {@code op} applied around the center of the tile. */
    private static AffineTransform around(AffineTransform op) {
        AffineTransform tx = AffineTransform.getTranslateInstance(CX, CY);
        tx.concatenate(op);
        tx.translate(-CX, -CY);
        return tx;
    }

    private static void ghost(Graphics2D g, Shape shape) {
        Graphics2D d = (Graphics2D) g.create();
        try {
            d.setColor(new Color(0xFF90A4AE, true));
            d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 3, 3 }, 0));
            d.draw(shape);
        } finally {
            d.dispose();
        }
    }

    private static void figureWith(Graphics2D g, AffineTransform tx, int fill) {
        Shape base = figure(CX - 18, CY - 25);
        ghost(g, base);
        Graphics2D t = (Graphics2D) g.create();
        try {
            t.transform(tx);
            t.setColor(new Color(fill, true));
            t.fill(base);
            t.setColor(new Color(INK, true));
            t.draw(base);
        } finally {
            t.dispose();
        }
    }

    private static Tile transformTile(String caption, AffineTransform op, int fill) {
        return new Tile(caption, (g, w, h) -> figureWith(g, around(op), fill));
    }

    private static List<Tile> transformTiles() {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile("translate(40, 26)", (g, w, h) -> {
            Shape base = figure(16, 8);
            ghost(g, base);
            g.translate(40, 26);
            g.setColor(new Color(FILLS[0], true));
            g.fill(base);
            g.setColor(new Color(INK, true));
            g.draw(base);
        }));
        tiles.add(transformTile("rotate(30) around the center", AffineTransform.getRotateInstance(Math.toRadians(30)),
                FILLS[1]));
        tiles.add(transformTile("scale(1.6, 0.7)", AffineTransform.getScaleInstance(1.6, 0.7), FILLS[2]));
        tiles.add(transformTile("scale(-1, 1) : flip", AffineTransform.getScaleInstance(-1, 1), FILLS[3]));
        tiles.add(transformTile("shear(0.5, 0)", AffineTransform.getShearInstance(0.5, 0), FILLS[4]));
        tiles.add(transformTile("quadrantRotate(1)", AffineTransform.getQuadrantRotateInstance(1), FILLS[5]));
        tiles.add(new Tile("concatenate (red)\npreConcatenate (blue)", (g, w, h) -> {
            Shape base = figure(10, 10);
            ghost(g, base);
            AffineTransform rotate = AffineTransform.getRotateInstance(Math.toRadians(20));
            AffineTransform translate = AffineTransform.getTranslateInstance(70, 0);
            AffineTransform concatenated = new AffineTransform(rotate);
            concatenated.concatenate(translate);
            AffineTransform preConcatenated = new AffineTransform(rotate);
            preConcatenated.preConcatenate(translate);
            g.setColor(new Color(0xB3E53935, true));
            g.fill(concatenated.createTransformedShape(base));
            g.setColor(new Color(0xB31E88E5, true));
            g.fill(preConcatenated.createTransformedShape(base));
        }));
        tiles.add(new Tile("nested transforms", (g, w, h) -> {
            g.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < 14; i++) {
                g.setColor(Color.getHSBColor(i / 14f, 0.8f, 0.8f));
                g.draw(new Rectangle2D.Double(CX - 50, CY - 50, 100, 100));
                g.translate(CX, CY);
                g.rotate(Math.toRadians(12));
                g.scale(0.86, 0.86);
                g.translate(-CX, -CY);
            }
        }));
        tiles.add(new Tile("rotated and sheared text", (g, w, h) -> {
            g.setColor(new Color(INK, true));
            g.setFont(Java2dSupport.font(Font.BOLD, 16));
            Graphics2D r = (Graphics2D) g.create();
            r.rotate(Math.toRadians(-30), 20, 90);
            r.drawString("rotate(-30)", 20, 90);
            r.dispose();
            g.setColor(new Color(0xFF1565C0, true));
            g.setFont(Java2dSupport.font(Font.BOLD, 16).deriveFont(AffineTransform.getShearInstance(-0.4, 0)));
            g.drawString("font shear", 40, 104);
            g.setFont(Java2dSupport.font(Font.PLAIN, 13).deriveFont(AffineTransform.getScaleInstance(0.8, 1.6)));
            g.drawString("font scale", 70, 40);
        }));
        for (Object[] hint : new Object[][] { { "NEAREST", RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR },
                { "BILINEAR", RenderingHints.VALUE_INTERPOLATION_BILINEAR },
                { "BICUBIC", RenderingHints.VALUE_INTERPOLATION_BICUBIC } }) {
            tiles.add(new Tile("drawImage(image, tx)\n" + hint[0], (g, w, h) -> {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint[1]);
                AffineTransform tx = new AffineTransform();
                tx.translate(CX, CY - 6);
                tx.rotate(Math.toRadians(25));
                tx.scale(3, 3);
                tx.translate(-12, -12);
                g.drawImage(smallImage(), tx, null);
            }));
        }
        tiles.add(new Tile("drawRenderedImage\nscale 2 + shear", (g, w, h) -> {
            AffineTransform tx = new AffineTransform(2, 0, -0.8, 2, 50, 8);
            g.drawRenderedImage(smallImage(), tx);
            g.drawRenderedImage(PaintsPage.texture(BufferedImage.TYPE_INT_ARGB), new AffineTransform(3, 0.6, 0, 3, 90, 40));
        }));
        tiles.add(new Tile("clipRect twice : intersection", (g, w, h) -> {
            Graphics2D c = (Graphics2D) g.create();
            c.clipRect(10, 10, 90, 64);
            c.clipRect(50, 40, 90, 70);
            stripes(c, w, h);
            c.dispose();
            outline(g, new Rectangle(10, 10, 90, 64));
            outline(g, new Rectangle(50, 40, 90, 70));
        }));
        tiles.add(new Tile("clip(Ellipse2D)", (g, w, h) -> {
            g.clip(new Ellipse2D.Double(10, 6, 128, 106));
            stripes(g, w, h);
        }));
        tiles.add(new Tile("clip(Area with a hole)", (g, w, h) -> {
            Area ring = new Area(new Ellipse2D.Double(20, 6, 108, 106));
            ring.subtract(new Area(new Rectangle2D.Double(54, 36, 40, 46)));
            g.clip(ring);
            g.setPaint(new GradientPaint(0, 0, new Color(0x8E24AA), w, h, new Color(0xFFB300)));
            g.fillRect(0, 0, w, h);
        }));
        tiles.add(new Tile("clip(text outline)", (g, w, h) -> {
            GlyphVector gv = Java2dSupport.font(Font.BOLD, 58).createGlyphVector(new FontRenderContext(null, true, false),
                    "Clip");
            g.clip(gv.getOutline(6, 82));
            stripes(g, w, h);
        }));
        tiles.add(new Tile("clipRect after rotate(20)", (g, w, h) -> {
            Graphics2D c = (Graphics2D) g.create();
            AffineTransform base = c.getTransform();
            c.rotate(Math.toRadians(20), CX, CY);
            c.clipRect(24, 24, 100, 70);
            // back to the untransformed tile : the clip stays rotated
            c.setTransform(base);
            stripes(c, w, h);
            c.dispose();
        }));
        tiles.add(new Tile("nested Graphics.create x4", (g, w, h) -> {
            Graphics2D c = g;
            List<Graphics2D> created = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                c = (Graphics2D) c.create(10, 8, w - 20 - i * 26, h - 16 - i * 22);
                created.add(c);
                c.setColor(new Color(FILLS[i], true));
                c.fillRect(-100, -100, 400, 400);
                c.setColor(new Color(INK, true));
                c.drawString(String.valueOf(i + 1), 3, 12);
            }
            created.forEach(Graphics2D::dispose);
        }));
        tiles.add(new Tile("AA shape, rotated hard clip", (g, w, h) -> {
            g.rotate(Math.toRadians(-15), CX, CY);
            g.clipRect(20, 16, 110, 86);
            g.rotate(Math.toRadians(15), CX, CY);
            g.setColor(new Color(FILLS[3], true));
            g.fill(new Ellipse2D.Double(8, 4, 132, 110));
        }));
        tiles.add(new Tile("copyArea : tiling a pattern", (g, w, h) -> {
            g.setColor(new Color(FILLS[0], true));
            g.fillRect(0, 0, 36, 28);
            g.setColor(new Color(INK, true));
            g.fill(figure(4, 2).createTransformedShape(AffineTransform.getScaleInstance(0.45, 0.45)));
            g.setColor(new Color(ACCENT, true));
            g.fillOval(20, 10, 12, 12);
            for (int x = 36; x < w; x += 36) {
                g.copyArea(0, 0, 36, 28, x, 0);
            }
            for (int y = 28; y < h; y += 28) {
                g.copyArea(0, 0, w, 28, 0, y);
            }
        }));
        tiles.add(new Tile("copyArea under scale(2)", (g, w, h) -> {
            g.scale(2, 2);
            g.setColor(new Color(FILLS[2], true));
            g.fillRect(2, 2, 30, 24);
            g.setColor(new Color(INK, true));
            g.drawString("2x", 8, 19);
            g.copyArea(2, 2, 30, 24, 36, 0);
            g.copyArea(2, 2, 66, 24, 0, 28);
        }));
        return tiles;
    }

    private static void stripes(Graphics2D g, int w, int h) {
        for (int i = -h; i < w; i += 12) {
            g.setColor(new Color(i / 12 % 2 == 0 ? 0xFF1E88E5 : 0xFFFFC107, true));
            g.fill(new Path2D.Double(new Rectangle2D.Double(i, 0, 6, h),
                    AffineTransform.getShearInstance(-0.6, 0)).createTransformedShape(null));
        }
        g.setColor(new Color(0x401E88E5, true));
        g.fillRect(0, 0, w, h);
    }

    private static void outline(Graphics2D g, Shape shape) {
        g.setColor(new Color(ACCENT, true));
        g.setStroke(new BasicStroke(1));
        g.draw(shape);
    }

    /** 24 x 24 : a quarter checker, a disc and a diagonal, for transformed and scaled image drawing. */
    static BufferedImage smallImage() {
        return Java2dSupport.image(24, 24, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setColor(new Color(0xFFFFF3E0, true));
            g.fillRect(0, 0, 24, 24);
            g.setColor(new Color(0xFF3949AB, true));
            g.fillRect(0, 0, 12, 12);
            g.fillRect(12, 12, 12, 12);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xFFE53935, true));
            g.fillOval(6, 6, 12, 12);
            g.setColor(new Color(0xFFFFFFFF, true));
            g.drawLine(0, 23, 23, 0);
        });
    }

    // ----------------------------------------------------------------------------------------------- hint tiles

    /** 12 x 9 pixels of solid colors, for up scaling. */
    static BufferedImage tinyImage() {
        return Java2dSupport.image(12, 9, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            int[] colors = { 0xFFE53935, 0xFFFFFFFF, 0xFF1E88E5, 0xFF000000, 0xFFFFEB3B, 0xFF43A047 };
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    g.setColor(new Color(colors[(x / 2 + y / 3 * 2 + (x + y) % 2) % colors.length], true));
                    g.fillRect(x, y, 1, 1);
                }
            }
        });
    }

    /** 592 x 472 : fine stripes and circles, for down scaling (moire with NEAREST). */
    static BufferedImage largeImage() {
        return Java2dSupport.image(592, 472, BufferedImage.TYPE_INT_RGB, (g, w, h) -> {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0x263238));
            for (int r = 4; r < 300; r += 6) {
                g.drawOval(296 - r, 236 - r, 2 * r, 2 * r);
            }
            for (int x = 0; x < w; x += 5) {
                g.drawLine(x, 0, x + 60, 60);
            }
        });
    }

    private static BufferedImage aaSample(boolean antialiasing) {
        return Java2dSupport.image(37, 29, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(0xFFF7F9FC, true));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0xFF3949AB, true));
            g.fill(new Ellipse2D.Double(2.5, 2.5, 22, 22));
            g.setColor(new Color(INK, true));
            g.setStroke(new BasicStroke(1.3f));
            g.draw(new java.awt.geom.Line2D.Double(14, 27, 35, 3));
        });
    }

    private static BufferedImage scaled(BufferedImage source, int width, int height, Object interpolation) {
        return Java2dSupport.image(width, height, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.drawImage(source, 0, 0, w, h, null);
        });
    }

    /** A gray and color gradient drawn into a TYPE_BYTE_INDEXED image with {@code dithering}. */
    private static BufferedImage dithered(Object dithering) {
        return Java2dSupport.image(70, 110, BufferedImage.TYPE_BYTE_INDEXED, (g, w, h) -> {
            g.setRenderingHint(RenderingHints.KEY_DITHERING, dithering);
            g.setPaint(new GradientPaint(0, 0, new Color(0x202020), 0, 55, new Color(0xE0E0E0)));
            g.fillRect(0, 0, w, 55);
            g.setPaint(new GradientPaint(0, 55, new Color(0x6A1B9A), 0, 110, new Color(0xFFB74D)));
            g.fillRect(0, 55, w, 55);
        });
    }

    /** Text drawn into an opaque image (LCD text needs an opaque destination). */
    private static BufferedImage textImage(int width, int height, Consumer<Graphics2D> painter) {
        return Java2dSupport.image(width, height, BufferedImage.TYPE_INT_RGB, (g, w, h) -> {
            g.setColor(new Color(0xF7F9FC));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0x263238));
            g.setFont(Java2dSupport.font(Font.PLAIN, 12));
            painter.accept(g);
        });
    }

    /**
     * Names and values of the text anti-aliasing hint (a method, not a static field : application classes are
     * initialized at build time in a native executable, AWT classes at run time).
     */
    private static Object[][] textModeHints() {
        return new Object[][] {
            { "OFF", RenderingHints.VALUE_TEXT_ANTIALIAS_OFF },
            { "ON", RenderingHints.VALUE_TEXT_ANTIALIAS_ON },
            { "GASP", RenderingHints.VALUE_TEXT_ANTIALIAS_GASP },
            { "LCD_HRGB", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB },
            { "LCD_HBGR", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR },
            { "LCD_VRGB", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB },
            { "LCD_VBGR", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR } };
    }

    private static BufferedImage textModes() {
        return textImage(148, 118, g -> {
            Object[][] modes = textModeHints();
            for (int i = 0; i < modes.length; i++) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, modes[i][1]);
                g.drawString(modes[i][0] + " Quartz jog", 2, 14 + i * 16);
            }
        });
    }

    private static BufferedImage lcdContrast() {
        return textImage(148, 118, g -> {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int[] contrasts = { 100, 140, 180, 220, 250 };
            for (int i = 0; i < contrasts.length; i++) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, contrasts[i]);
                g.drawString("contrast " + contrasts[i] + " Wave", 2, 16 + i * 22);
            }
        });
    }

    private static BufferedImage fractionalMetrics() {
        return textImage(148, 118, g -> {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = 16;
            for (Object value : new Object[] { RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON }) {
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, value);
                for (float size : new float[] { 11f, 11.5f, 13f }) {
                    g.setFont(Java2dSupport.font(Font.PLAIN, size));
                    String text = "illuminati " + Checks.num(size, 1);
                    g.setColor(new Color(0x263238));
                    g.drawString(text, 2, y);
                    FontMetrics fm = g.getFontMetrics();
                    g.setColor(new Color(0xE53935));
                    g.fillRect(2, y + 2, fm.stringWidth(text), 2);
                    y += 18;
                }
            }
        });
    }

    /** A multi-resolution image : 1x red, 2x green, 3x blue variants of a 24 x 24 image. */
    static BaseMultiResolutionImage multiResolution() {
        Image[] variants = new Image[3];
        int[] colors = { 0xFFE53935, 0xFF43A047, 0xFF1E88E5 };
        for (int i = 0; i < 3; i++) {
            int scale = i + 1;
            int color = colors[i];
            variants[i] = Java2dSupport.image(24 * scale, 24 * scale, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
                g.setColor(new Color(color, true));
                g.fillRect(0, 0, w, h);
                g.setColor(Color.WHITE);
                g.setFont(Java2dSupport.font(Font.BOLD, 14 * scale));
                g.drawString(scale + "x", 2 * scale, 18 * scale);
            });
        }
        return new BaseMultiResolutionImage(variants);
    }

    private static final String RESOLUTION_TILE = "RESOLUTION_VARIANT x1.5\nDEFAULT BASE SIZE DPI";

    /** Names and values of the resolution variant hint (a method : see {@link #textModeHints()}). */
    private static Object[][] variantHints() {
        return new Object[][] {
            { "DEFAULT", RenderingHints.VALUE_RESOLUTION_VARIANT_DEFAULT },
            { "BASE", RenderingHints.VALUE_RESOLUTION_VARIANT_BASE },
            { "SIZE_FIT", RenderingHints.VALUE_RESOLUTION_VARIANT_SIZE_FIT },
            { "DPI_FIT", RenderingHints.VALUE_RESOLUTION_VARIANT_DPI_FIT } };
    }

    private static List<Tile> hintTiles() {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile("ANTIALIAS_OFF (x4)", (g, w, h) -> Java2dSupport.magnified(g, aaSample(false), 4)));
        tiles.add(new Tile("ANTIALIAS_ON (x4)", (g, w, h) -> Java2dSupport.magnified(g, aaSample(true), 4)));
        tiles.add(new Tile("x12 upscale : NEAREST", (g, w, h) -> g.drawImage(scaled(tinyImage(), 144, 108,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR), 2, 4, null)));
        tiles.add(new Tile("x12 upscale : BILINEAR", (g, w, h) -> g.drawImage(scaled(tinyImage(), 144, 108,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR), 2, 4, null)));
        tiles.add(new Tile("x12 upscale : BICUBIC", (g, w, h) -> g.drawImage(scaled(tinyImage(), 144, 108,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC), 2, 4, null)));
        tiles.add(new Tile("1/4 downscale : NEAREST", (g, w, h) -> g.drawImage(scaled(largeImage(), 148, 118,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR), 0, 0, null)));
        tiles.add(new Tile("1/4 downscale : BILINEAR", (g, w, h) -> g.drawImage(scaled(largeImage(), 148, 118,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR), 0, 0, null)));
        tiles.add(new Tile("1/4 downscale : BICUBIC", (g, w, h) -> g.drawImage(scaled(largeImage(), 148, 118,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC), 0, 0, null)));
        tiles.add(new Tile("BYTE_INDEXED dithering\nENABLE / DISABLE", (g, w, h) -> {
            g.drawImage(dithered(RenderingHints.VALUE_DITHER_ENABLE), 2, 4, null);
            g.drawImage(dithered(RenderingHints.VALUE_DITHER_DISABLE), 76, 4, null);
        }));
        tiles.add(new Tile("ALPHA_INTERPOLATION etc.\nQUALITY / SPEED", (g, w, h) -> {
            Java2dSupport.checker(g, w, h, 10);
            for (int i = 0; i < 2; i++) {
                Graphics2D q = (Graphics2D) g.create(i * 74, 0, 74, h);
                q.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, i == 0
                        ? RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY : RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
                q.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, i == 0
                        ? RenderingHints.VALUE_COLOR_RENDER_QUALITY : RenderingHints.VALUE_COLOR_RENDER_SPEED);
                q.setRenderingHint(RenderingHints.KEY_RENDERING, i == 0
                        ? RenderingHints.VALUE_RENDER_QUALITY : RenderingHints.VALUE_RENDER_SPEED);
                q.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.6f));
                q.drawImage(Java2dSupport.sprite(), 4, 8, 66, 66, null);
                q.setColor(new Color(0x80E53935, true));
                q.fillRect(10, 70, 54, 40);
                q.dispose();
            }
        }));
        tiles.add(new Tile("TEXT_ANTIALIASING modes", (g, w, h) -> g.drawImage(textModes(), 0, 0, null)));
        tiles.add(new Tile("LCD_CONTRAST 100 to 250", (g, w, h) -> g.drawImage(lcdContrast(), 0, 0, null)));
        tiles.add(new Tile("FRACTIONALMETRICS OFF / ON", (g, w, h) -> g.drawImage(fractionalMetrics(), 0, 0, null)));
        tiles.add(new Tile(RESOLUTION_TILE, (g, w, h) -> {
            BaseMultiResolutionImage image = multiResolution();
            Object[][] variants = variantHints();
            for (int i = 0; i < variants.length; i++) {
                Graphics2D v = (Graphics2D) g.create(i * 37, 10, 37, 60);
                v.setRenderingHint(RenderingHints.KEY_RESOLUTION_VARIANT, variants[i][1]);
                v.scale(1.5, 1.5);
                v.drawImage(image, 0, 0, 24, 24, null);
                v.dispose();
            }
        }));
        tiles.add(new Tile("STROKE_CONTROL (x4)\nNORMALIZE / PURE", (g, w, h) -> {
            for (int i = 0; i < 2; i++) {
                Object value = i == 0 ? RenderingHints.VALUE_STROKE_NORMALIZE : RenderingHints.VALUE_STROKE_PURE;
                BufferedImage img = Java2dSupport.image(18, 28, BufferedImage.TYPE_INT_ARGB, (ig, iw, ih) -> {
                    ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    ig.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, value);
                    ig.setColor(new Color(INK, true));
                    ig.draw(new Rectangle2D.Double(2.3, 2.3, 12.4, 22.6));
                    ig.draw(new java.awt.geom.Line2D.Double(4, 20, 13, 6));
                });
                Graphics2D m = (Graphics2D) g.create(2 + i * 74, 2, 72, 112);
                Java2dSupport.magnified(m, img, 4);
                m.dispose();
            }
        }));
        return tiles;
    }

    // ----------------------------------------------------------------------------------------------------- checks

    private static String matrix(AffineTransform tx) {
        double[] m = new double[6];
        tx.getMatrix(m);
        return "[" + Checks.num(m[0]) + " " + Checks.num(m[2]) + " " + Checks.num(m[4]) + "] [" + Checks.num(m[1]) + " "
                + Checks.num(m[3]) + " " + Checks.num(m[5]) + "]";
    }

    static String typeFlags(int type) {
        if (type == AffineTransform.TYPE_IDENTITY) {
            return "IDENTITY";
        }
        List<String> flags = new ArrayList<>();
        String[] names = { "TRANSLATION", "UNIFORM_SCALE", "GENERAL_SCALE", "FLIP", "QUADRANT_ROTATION",
                "GENERAL_ROTATION", "GENERAL_TRANSFORM" };
        int[] bits = { AffineTransform.TYPE_TRANSLATION, AffineTransform.TYPE_UNIFORM_SCALE,
                AffineTransform.TYPE_GENERAL_SCALE, AffineTransform.TYPE_FLIP, AffineTransform.TYPE_QUADRANT_ROTATION,
                AffineTransform.TYPE_GENERAL_ROTATION, AffineTransform.TYPE_GENERAL_TRANSFORM };
        for (int i = 0; i < bits.length; i++) {
            if ((type & bits[i]) != 0) {
                flags.add(names[i]);
            }
        }
        return String.join("|", flags);
    }

    private static String point(Point2D p) {
        return Checks.num(p.getX()) + "," + Checks.num(p.getY());
    }

    private static List<Check> transformChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("getType : identity / translate / scale(2, 2) / scale(2, 3)",
                "IDENTITY / TRANSLATION / UNIFORM_SCALE / GENERAL_SCALE",
                () -> typeFlags(new AffineTransform().getType()) + " / "
                        + typeFlags(AffineTransform.getTranslateInstance(3, 4).getType()) + " / "
                        + typeFlags(AffineTransform.getScaleInstance(2, 2).getType()) + " / "
                        + typeFlags(AffineTransform.getScaleInstance(2, 3).getType())));
        checks.add(Checks.expect("getType : quadrant / rotate(30) / flip / shear",
                "QUADRANT_ROTATION / GENERAL_ROTATION / FLIP / GENERAL_TRANSFORM",
                () -> typeFlags(AffineTransform.getQuadrantRotateInstance(1).getType()) + " / "
                        + typeFlags(AffineTransform.getRotateInstance(Math.toRadians(30)).getType()) + " / "
                        + typeFlags(AffineTransform.getScaleInstance(-1, 1).getType()) + " / "
                        + typeFlags(AffineTransform.getShearInstance(0.5, 0).getType())));
        checks.add(Checks.expect("rotate(30) matrix", "[0.866 -0.500 0.000] [0.500 0.866 0.000]",
                () -> matrix(AffineTransform.getRotateInstance(Math.toRadians(30)))));
        checks.add(Checks.expect("rotate(30) around the tile center", "[0.866 -0.500 39.414] [0.500 0.866 -29.095]",
                () -> matrix(around(AffineTransform.getRotateInstance(Math.toRadians(30))))));
        checks.add(Checks.expect("quadrantRotate(1, 10, 20) : exact", "[0.0, -1.0, 30.0] [1.0, 0.0, 10.0]", () -> {
            double[] m = new double[6];
            AffineTransform.getQuadrantRotateInstance(1, 10, 20).getMatrix(m);
            return "[" + m[0] + ", " + m[2] + ", " + m[4] + "] [" + m[1] + ", " + m[3] + ", " + m[5] + "]";
        }));
        checks.add(Checks.expect("getRotateInstance(0, 1) : type, exact matrix", "QUADRANT_ROTATION, 0.0 -1.0 1.0 0.0",
                () -> {
                    AffineTransform tx = AffineTransform.getRotateInstance(0, 1);
                    return typeFlags(tx.getType()) + ", " + tx.getScaleX() + " " + tx.getShearX() + " " + tx.getShearY()
                            + " " + tx.getScaleY();
                }));
        checks.add(Checks.expect("rotate(20) concatenate / preConcatenate translate(70, 0)",
                "[0.940 -0.342 65.778] [0.342 0.940 23.941] / [0.940 -0.342 70.000] [0.342 0.940 0.000]", () -> {
                    AffineTransform a = AffineTransform.getRotateInstance(Math.toRadians(20));
                    a.concatenate(AffineTransform.getTranslateInstance(70, 0));
                    AffineTransform b = AffineTransform.getRotateInstance(Math.toRadians(20));
                    b.preConcatenate(AffineTransform.getTranslateInstance(70, 0));
                    return matrix(a) + " / " + matrix(b);
                }));
        AffineTransform general = new AffineTransform(1.2, 0.4, -0.3, 0.9, 15, -7);
        checks.add(Checks.expect("determinant of [1.2 -0.3 15] [0.4 0.9 -7]", "1.200",
                () -> Checks.num(general.getDeterminant())));
        checks.add(Checks.expect("createInverse", "[0.750 0.250 -9.500] [-0.333 1.000 12.000]",
                () -> matrix(general.createInverse())));
        checks.add(Checks.expect("inverse x transform = identity (1e-12)", true, () -> {
            AffineTransform product = general.createInverse();
            product.concatenate(general);
            double[] m = new double[6];
            product.getMatrix(m);
            double[] id = { 1, 0, 0, 1, 0, 0 };
            for (int i = 0; i < 6; i++) {
                if (Math.abs(m[i] - id[i]) > 1e-12) {
                    return false;
                }
            }
            return true;
        }));
        checks.add(Checks.expect("transform / inverseTransform / deltaTransform of (10, 20)",
                "21.000,15.000 / 3.000,28.667 / 6.000,22.000", () -> point(general.transform(new Point2D.Double(10, 20),
                        null)) + " / " + point(general.inverseTransform(new Point2D.Double(10, 20), null)) + " / "
                        + point(general.deltaTransform(new Point2D.Double(10, 20), null))));
        checks.add(Checks.expect("scale(0, 1).createInverse()", "NoninvertibleTransformException: Determinant is 0",
                () -> {
                    try {
                        AffineTransform.getScaleInstance(0, 1).createInverse();
                        return "no exception";
                    } catch (NoninvertibleTransformException e) {
                        return e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                }));
        checks.add(Checks.expect("translate(10, 20).toString()", "AffineTransform[[1.0, 0.0, 10.0], [0.0, 1.0, 20.0]]",
                () -> AffineTransform.getTranslateInstance(10, 20).toString()));
        checks.add(Checks.expect("translate(5, 5) then (-5, -5) : isIdentity", true, () -> {
            AffineTransform tx = AffineTransform.getTranslateInstance(5, 5);
            tx.translate(-5, -5);
            return tx.isIdentity();
        }));
        checks.add(Checks.expect("equals / hashCode of equal transforms", "true / true", () -> {
            AffineTransform a = around(AffineTransform.getShearInstance(0.5, 0));
            AffineTransform b = around(AffineTransform.getShearInstance(0.5, 0));
            return a.equals(b) + " / " + (a.hashCode() == b.hashCode());
        }));
        checks.add(Checks.expect("figure under shear(0.5, 0) around the center : bounds", "43.50,34.00 43.00x50.00",
                () -> Checks.bounds(around(AffineTransform.getShearInstance(0.5, 0))
                        .createTransformedShape(figure(CX - 18, CY - 25)))));
        checks.add(Checks.expect("Path2D.transform(flip) : contains (20, 5) before / after", "true / false", () -> {
            Path2D f = figure(0, 0);
            boolean before = f.contains(20, 5);
            f.transform(around(AffineTransform.getScaleInstance(-1, 1)));
            return before + " / " + f.contains(20, 5);
        }));
        return checks;
    }

    private static List<Check> clipChecks(Grid grid, BufferedImage image) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("getTransform after translate(10, 20), rotate(90)",
                "[0.000 -1.000 10.000] [1.000 0.000 20.000]",
                () -> withGraphics(g -> {
                    g.translate(10, 20);
                    g.rotate(Math.PI / 2);
                    return matrix(g.getTransform());
                })));
        checks.add(Checks.expect("getClipBounds after clipRect(10, 10, 50, 50), translate(5, 5)", "5,5 50x50",
                () -> withGraphics(g -> {
                    g.clipRect(10, 10, 50, 50);
                    g.translate(5, 5);
                    Rectangle r = g.getClipBounds();
                    return r.x + "," + r.y + " " + r.width + "x" + r.height;
                })));
        checks.add(Checks.expect("rotated clip : device bounds", "15.04,9.01 117.91x99.98", () -> withGraphics(g -> {
            g.rotate(Math.toRadians(20), CX, CY);
            g.clipRect(24, 24, 100, 70);
            g.setTransform(new AffineTransform());
            return Checks.bounds(g.getClip());
        })));
        checks.add(Checks.expect("hitClip inside / outside the clip ellipse", "true / false", () -> withGraphics(g -> {
            g.clip(new Ellipse2D.Double(10, 6, 128, 106));
            return g.hitClip(70, 50, 4, 4) + " / " + g.hitClip(0, 0, 4, 4);
        })));
        checks.add(Checks.expect("create(10, 10, 30, 30) : clip bounds / translation", "0,0 30x30 / 10.0,10.0",
                () -> withGraphics(g -> {
                    Graphics2D c = (Graphics2D) g.create(10, 10, 30, 30);
                    try {
                        Rectangle r = c.getClipBounds();
                        return r.x + "," + r.y + " " + r.width + "x" + r.height + " / " + c.getTransform().getTranslateX()
                                + "," + c.getTransform().getTranslateY();
                    } finally {
                        c.dispose();
                    }
                })));
        checks.add(Checks.expect("setClip(null) : getClip()", "null", () -> withGraphics(g -> {
            g.clipRect(0, 0, 5, 5);
            g.setClip(null);
            return String.valueOf(g.getClip());
        })));
        checks.add(Checks.expect("clip(Area with a hole) : contains the hole center", false, () -> withGraphics(g -> {
            Area ring = new Area(new Ellipse2D.Double(20, 6, 108, 106));
            ring.subtract(new Area(new Rectangle2D.Double(54, 36, 40, 46)));
            g.clip(ring);
            return g.getClip().contains(74, 59);
        })));
        checks.add(Checks.expect("clipRect intersection : inside / outside pixels", "true / #FFF7F9FC", () -> {
            String inside = grid.probe(image, "clipRect twice : intersection", 70, 60);
            return !inside.equals("#FFF7F9FC") + " / " + grid.probe(image, "clipRect twice : intersection", 30, 30);
        }));
        checks.add(Checks.expect("clip(Ellipse2D) : corner pixel untouched", "#FFF7F9FC",
                () -> grid.probe(image, "clip(Ellipse2D)", 12, 8)));
        checks.add(Checks.expect("copyArea tiling : copies equal the source", true, () -> {
            java.awt.Point o = grid.origin(grid.index("copyArea : tiling a pattern"));
            for (int y = 0; y < 28; y++) {
                for (int x = 0; x < 36; x++) {
                    int source = image.getRGB(o.x + x, o.y + y);
                    if (image.getRGB(o.x + x + 72, o.y + y + 56) != source || image.getRGB(o.x + x + 108, o.y + y) != source) {
                        return false;
                    }
                }
            }
            return true;
        }));
        checks.add(Checks.expect("copyArea under scale(2) : copied pixel", true, () -> {
            java.awt.Point o = grid.origin(grid.index("copyArea under scale(2)"));
            // (10, 10) in user space is (20, 20) in the tile ; copied by (36, 0) then (0, 28) : x 2 in device space
            return image.getRGB(o.x + 20, o.y + 20) == image.getRGB(o.x + 20 + 72, o.y + 20 + 56);
        }));
        checks.add(Checks.info("text clip tile : pixel hash", () -> {
            java.awt.Point o = grid.origin(grid.index("clip(text outline)"));
            return Checks.sha256(image.getSubimage(o.x, o.y, grid.areaWidth(), grid.areaHeight()));
        }));
        return checks;
    }

    private static List<Check> hintChecks(Grid grid, BufferedImage image) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("KEY_ANTIALIASING / VALUE_ANTIALIAS_ON",
                "Global antialiasing enable key / Antialiased rendering mode",
                () -> RenderingHints.KEY_ANTIALIASING + " / " + RenderingHints.VALUE_ANTIALIAS_ON));
        checks.add(Checks.expect("defaults of a BufferedImage Graphics2D : AA / text AA / stroke / interpolation",
                "Nonantialiased rendering mode / Default antialiasing text mode / Default stroke normalization / null",
                () -> withGraphics(g -> g.getRenderingHint(RenderingHints.KEY_ANTIALIASING) + " / "
                        + g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING) + " / "
                        + g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL) + " / "
                        + g.getRenderingHint(RenderingHints.KEY_INTERPOLATION))));
        checks.add(Checks.expect("getRenderingHints() after 3 hints set", "7 [Fractional metrics enable key, "
                + "Global antialiasing enable key, Global rendering quality key, Image interpolation method key, "
                + "Stroke normalization control key, Text-specific LCD contrast key, Text-specific antialiasing enable key]",
                () -> withGraphics(g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 180);
            Map<String, String> sorted = new TreeMap<>();
            g.getRenderingHints().forEach((k, v) -> sorted.put(String.valueOf(k), String.valueOf(v)));
            return sorted.size() + " " + sorted.keySet();
        })));
        checks.add(Checks.expect("KEY_TEXT_LCD_CONTRAST.isCompatibleValue(250 / 300)", "true / false",
                () -> RenderingHints.KEY_TEXT_LCD_CONTRAST.isCompatibleValue(250) + " / "
                        + RenderingHints.KEY_TEXT_LCD_CONTRAST.isCompatibleValue(300)));
        checks.add(Checks.expect("RenderingHints.put(KEY_ANTIALIASING, VALUE_INTERPOLATION_BICUBIC)",
                "IllegalArgumentException: Bicubic image interpolation mode incompatible with Global antialiasing enable key",
                () -> {
            try {
                new RenderingHints(null).put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                return "no exception";
            } catch (IllegalArgumentException e) {
                return e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }));
        checks.add(Checks.expect("AA off / on : partially covered pixels", "0 / 150", () -> partial(aaSample(false)) + " / "
                + partial(aaSample(true))));
        checks.add(Checks.expect("x12 NEAREST / BILINEAR / BICUBIC : pixel (17, 40)", "#FF1E88E5 / #FF3A91DF / #FF2E94F0",
                () -> interpolated(17, 40)));
        checks.add(Checks.expect("x12 NEAREST : every block is a source pixel", true, () -> {
            BufferedImage tiny = tinyImage();
            BufferedImage big = scaled(tiny, 144, 108, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int y = 0; y < 108; y++) {
                for (int x = 0; x < 144; x++) {
                    if (big.getRGB(x, y) != tiny.getRGB(x / 12, y / 12)) {
                        return false;
                    }
                }
            }
            return true;
        }));
        checks.add(Checks.expect("1/4 NEAREST / BILINEAR / BICUBIC : pixel hashes differ", true, () -> {
            String a = Checks.sha256(scaled(largeImage(), 148, 118, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR));
            String b = Checks.sha256(scaled(largeImage(), 148, 118, RenderingHints.VALUE_INTERPOLATION_BILINEAR));
            String c = Checks.sha256(scaled(largeImage(), 148, 118, RenderingHints.VALUE_INTERPOLATION_BICUBIC));
            return !a.equals(b) && !b.equals(c);
        }));
        checks.add(Checks.info("1/4 BICUBIC : pixel hash", () -> Checks.sha256(scaled(largeImage(), 148, 118,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC))));
        // the conversion to an IndexColorModel always dithers (ordered dither tables) : the hint makes no difference
        checks.add(Checks.expect("BYTE_INDEXED DITHER_ENABLE / DISABLE : colors, same pixels", "50 / 50, true", () -> {
            BufferedImage enabled = dithered(RenderingHints.VALUE_DITHER_ENABLE);
            BufferedImage disabled = dithered(RenderingHints.VALUE_DITHER_DISABLE);
            return colors(enabled) + " / " + colors(disabled) + ", "
                    + Checks.sha256(enabled).equals(Checks.sha256(disabled));
        }));
        checks.add(Checks.expect("RESOLUTION_VARIANT DEFAULT / BASE / SIZE_FIT / DPI_FIT : variant drawn",
                "2x / 1x / 2x / 1x", () -> {
            java.awt.Point o = grid.origin(grid.index(RESOLUTION_TILE));
            List<String> values = new ArrayList<>();
            for (int i = 0; i < variantHints().length; i++) {
                int rgb = image.getRGB(o.x + i * 37 + 34, o.y + 10 + 34) & 0xFFFFFF;
                values.add(rgb == 0xE53935 ? "1x" : rgb == 0x43A047 ? "2x" : rgb == 0x1E88E5 ? "3x" : Checks.argb(rgb));
            }
            return String.join(" / ", values);
        }));
        checks.add(Checks.expect("MultiResolutionImage.getResolutionVariant(30, 30) / (60, 60)", "48x48 / 72x72", () -> {
            BaseMultiResolutionImage mri = multiResolution();
            Image a = mri.getResolutionVariant(30, 30);
            Image b = mri.getResolutionVariant(60, 60);
            return a.getWidth(null) + "x" + a.getHeight(null) + " / " + b.getWidth(null) + "x" + b.getHeight(null);
        }));
        checks.add(Checks.expect("LCD_HRGB text has color fringes / GASP none", "true / false", () -> {
            BufferedImage lcd = textImage(80, 20, g -> {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g.drawString("Quartz jog", 2, 14);
            });
            BufferedImage gray = textImage(80, 20, g -> {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.drawString("Quartz jog", 2, 14);
            });
            return fringes(lcd) + " / " + fringes(gray);
        }));
        checks.add(Checks.info("FRACTIONALMETRICS OFF / ON : stringWidth of \"illuminati 11.5\"", () -> {
            List<String> widths = new ArrayList<>();
            for (Object value : new Object[] { RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON }) {
                widths.add(withGraphics(g -> {
                    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, value);
                    g.setFont(Java2dSupport.font(Font.PLAIN, 11.5f));
                    return g.getFontMetrics().stringWidth("illuminati 11.5") + " ("
                            + Checks.num(g.getFont().getStringBounds("illuminati 11.5", g.getFontRenderContext()).getWidth(), 2)
                            + ")";
                }));
            }
            return String.join(" / ", widths);
        }));
        checks.add(Checks.info("text modes tile : pixel hash", () -> Checks.sha256(textModes())));
        return checks;
    }

    private interface GraphicsFunction<T> {
        T apply(Graphics2D g) throws Exception;
    }

    private static <T> T withGraphics(GraphicsFunction<T> function) throws Exception {
        BufferedImage image = new BufferedImage(148, 118, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            return function.apply(g);
        } finally {
            g.dispose();
        }
    }

    private static int partial(BufferedImage image) {
        int opaqueInk = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (rgb != 0xFFF7F9FC && rgb != 0xFF3949AB && rgb != (INK | 0xFF000000)) {
                    opaqueInk++;
                }
            }
        }
        return opaqueInk;
    }

    private static String interpolated(int x, int y) {
        List<String> values = new ArrayList<>();
        for (Object hint : new Object[] { RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR, RenderingHints.VALUE_INTERPOLATION_BICUBIC }) {
            values.add(Checks.argb(scaled(tinyImage(), 144, 108, hint).getRGB(x, y)));
        }
        return String.join(" / ", values);
    }

    private static int colors(BufferedImage image) {
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                set.add(image.getRGB(x, y));
            }
        }
        return set.size();
    }

    /** {@code true} if a pixel has clearly different red and blue components (subpixel rendering). */
    private static boolean fringes(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (Math.abs(((rgb >> 16) & 0xFF) - (rgb & 0xFF)) > 40) {
                    return true;
                }
            }
        }
        return false;
    }
}
