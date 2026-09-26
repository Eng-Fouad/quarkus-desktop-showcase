package io.quarkiverse.desktop.showcase.pages.java2d;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.quarkiverse.desktop.showcase.core.Checks;

/**
 * Helpers shared by the Java2D pages : a grid of captioned tiles painted offscreen (capture method C), a checkerboard
 * showing translucency, a nearest neighbor magnifier, pixel probes, image differences, and the reference scene painted
 * into every kind of surface (buffered images of all types, volatile images, on-screen canvases and buffer strategies).
 * <p>
 * Colors are {@code int} constants and every AWT object is created when a page is built : an application class never
 * holds AWT objects in static fields (Quarkus initializes application classes at build time, AWT classes at run time).
 */
final class Java2dSupport {

    static final int BACKGROUND = 0xFFF7F9FC;
    static final int BORDER = 0xFFB0BEC5;
    static final int INK = 0xFF263238;
    static final int ACCENT = 0xFFE53935;
    static final int CHECKER_LIGHT = 0xFFFFFFFF;
    static final int CHECKER_DARK = 0xFFD5DBE1;
    static final int[] FILLS = { 0xFF4FC3F7, 0xFFFFB74D, 0xFF81C784, 0xFFE57373, 0xFFBA68C8, 0xFF4DB6AC };

    /** Height of the caption area at the bottom of a tile. */
    static final int CAPTION_HEIGHT = 24;
    /** Margin around the drawing area of a tile. */
    static final int INSET = 8;

    /** Size of the reference scene (see {@link #scene}). */
    static final int SCENE_WIDTH = 200;
    static final int SCENE_HEIGHT = 120;

    /**
     * Probe points of the reference scene : solid areas whose value only depends on the surface type (name, x, y).
     */
    static final Object[][] SCENE_PROBES = {
            { "red", 16, 28 },
            { "blue", 56, 28 },
            { "yellow", 116, 12 },
            { "yellow under 50% blue", 116, 30 },
            { "ramp", 60, 48 },
            { "copyArea", 125, 110 },
            { "50% blue on clear", 189, 60 },
    };

    private Java2dSupport() {
    }

    /**
     * Paints the drawing area of a tile : {@code width x height} pixels, the origin at the top left corner of the area.
     */
    @FunctionalInterface
    interface Painter {
        void paint(Graphics2D g, int width, int height);
    }

    record Tile(String caption, Painter painter) {
    }

    /**
     * Tiles painted in a grid : a background, a border, the drawing area (clipped, anti-aliasing on, pure strokes, gray
     * scale text anti-aliasing, integer metrics) and a centered caption.
     */
    static final class Grid {

        final int columns;
        final int tileWidth;
        final int tileHeight;
        final List<Tile> tiles;

        Grid(int columns, int tileWidth, int tileHeight, List<Tile> tiles) {
            this.columns = columns;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.tiles = List.copyOf(tiles);
        }

        int areaWidth() {
            return tileWidth - 2 * INSET;
        }

        int areaHeight() {
            return tileHeight - INSET - CAPTION_HEIGHT;
        }

        int width() {
            return columns * tileWidth;
        }

        int height() {
            return (tiles.size() + columns - 1) / columns * tileHeight;
        }

        /**
         * Index of the tile with {@code caption}.
         */
        int index(String caption) {
            for (int i = 0; i < tiles.size(); i++) {
                if (tiles.get(i).caption().equals(caption)) {
                    return i;
                }
            }
            throw new IllegalArgumentException("No tile " + caption);
        }

        /**
         * Top left corner of the drawing area of tile {@code index}, in the grid image.
         */
        Point origin(int index) {
            return new Point((index % columns) * tileWidth + INSET, (index / columns) * tileHeight + INSET);
        }

        /**
         * {@code #AARRGGBB} of the pixel at ({@code x}, {@code y}) of the drawing area of the tile with {@code caption}.
         */
        String probe(BufferedImage image, String caption, int x, int y) {
            Point o = origin(index(caption));
            return Checks.argb(image.getRGB(o.x + x, o.y + y));
        }

        BufferedImage paint() {
            BufferedImage image = new BufferedImage(width(), height(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                for (int i = 0; i < tiles.size(); i++) {
                    Graphics2D tile = (Graphics2D) g.create((i % columns) * tileWidth, (i / columns) * tileHeight,
                            tileWidth, tileHeight);
                    try {
                        paintTile(tile, tiles.get(i));
                    } finally {
                        tile.dispose();
                    }
                }
            } finally {
                g.dispose();
            }
            return image;
        }

        private void paintTile(Graphics2D g, Tile tile) {
            g.setColor(new Color(BACKGROUND, true));
            g.fillRect(0, 0, tileWidth, tileHeight);
            g.setColor(new Color(BORDER, true));
            g.drawRect(2, 2, tileWidth - 5, tileHeight - 5);
            Graphics2D area = (Graphics2D) g.create(INSET, INSET, areaWidth(), areaHeight());
            try {
                defaultHints(area);
                area.setColor(new Color(INK, true));
                area.setFont(font(Font.PLAIN, 11));
                tile.painter().paint(area, areaWidth(), areaHeight());
            } finally {
                area.dispose();
            }
            textHints(g);
            g.setColor(new Color(INK, true));
            g.setFont(font(Font.PLAIN, 11));
            String[] lines = tile.caption().split("\n");
            int lineHeight = 12;
            int y = tileHeight - 9 - (lines.length - 1) * lineHeight;
            for (String line : lines) {
                int width = g.getFontMetrics().stringWidth(line);
                g.drawString(line, (tileWidth - width) / 2, y);
                y += lineHeight;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- painting

    static Font font(int style, float size) {
        return new Font(Font.DIALOG, style, 1).deriveFont(style, size);
    }

    /**
     * Anti-aliasing on, pure strokes, gray scale text anti-aliasing and integer metrics.
     */
    static void defaultHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        textHints(g);
    }

    static void textHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    /**
     * A checkerboard (translucent content is painted over it).
     */
    static void checker(Graphics2D g, int width, int height, int cell) {
        Color light = new Color(CHECKER_LIGHT, true);
        Color dark = new Color(CHECKER_DARK, true);
        for (int y = 0; y < height; y += cell) {
            for (int x = 0; x < width; x += cell) {
                g.setColor(((x / cell) + (y / cell)) % 2 == 0 ? light : dark);
                g.fillRect(x, y, Math.min(cell, width - x), Math.min(cell, height - y));
            }
        }
    }

    /**
     * A {@code width x height} image of {@code type} painted by {@code painter}.
     */
    static BufferedImage image(int width, int height, int type, Painter painter) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        try {
            painter.paint(g, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Draws {@code source} magnified {@code factor} times (nearest neighbor : every source pixel becomes a square).
     */
    static void magnified(Graphics2D g, BufferedImage source, int factor) {
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 0, 0, source.getWidth() * factor, source.getHeight() * factor, null);
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
    }

    /**
     * Number of pixels with a non-zero alpha.
     */
    static int covered(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------------------------------- image differences

    /**
     * Differences between two images of the same size : number of differing pixels and largest channel difference.
     */
    record Diff(int pixels, int maxDelta, int total) {

        @Override
        public String toString() {
            return pixels + " of " + total + " pixels differ, max delta " + maxDelta;
        }
    }

    static Diff diff(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new IllegalArgumentException("sizes differ: " + a.getWidth() + "x" + a.getHeight() + " / "
                    + b.getWidth() + "x" + b.getHeight());
        }
        int pixels = 0;
        int max = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int delta = delta(a.getRGB(x, y), b.getRGB(x, y));
                if (delta > 0) {
                    pixels++;
                    max = Math.max(max, delta);
                }
            }
        }
        return new Diff(pixels, max, a.getWidth() * a.getHeight());
    }

    static int delta(int argb1, int argb2) {
        int max = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            max = Math.max(max, Math.abs(((argb1 >>> shift) & 0xFF) - ((argb2 >>> shift) & 0xFF)));
        }
        return max;
    }

    /**
     * {@code a} faded, with the pixels differing from {@code b} in red (a delta of 1 or 2 levels in orange).
     */
    static BufferedImage diffImage(BufferedImage a, BufferedImage b) {
        BufferedImage out = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int pa = a.getRGB(x, y);
                int delta = x < b.getWidth() && y < b.getHeight() ? delta(pa, b.getRGB(x, y)) : 255;
                if (delta == 0) {
                    int gray = (((pa >> 16) & 0xFF) + ((pa >> 8) & 0xFF) + (pa & 0xFF)) / 3;
                    int faded = 255 - (255 - gray) / 4;
                    out.setRGB(x, y, 0xFF000000 | faded << 16 | faded << 8 | faded);
                } else {
                    out.setRGB(x, y, delta <= 2 ? 0xFFFF9800 : 0xFFE53935);
                }
            }
        }
        return out;
    }

    // ----------------------------------------------------------------------------------------- the reference scene

    /**
     * The reference scene ({@link #SCENE_WIDTH} x {@link #SCENE_HEIGHT}), painted the same way into every surface :
     * cleared with {@code AlphaComposite.Src} (transparent where the surface has alpha, black otherwise), solid color
     * bars, a gray ramp, anti-aliased and aliased shapes and text, a translucent sprite, a translucent overlay, a
     * {@code copyArea}, and a translucent stripe over the cleared area (the last 20 columns).
     */
    static void scene(Graphics2D g) {
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, SCENE_WIDTH, SCENE_HEIGHT);
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g.setColor(new Color(0xFFFFFF));
        g.fillRect(0, 0, 180, SCENE_HEIGHT);
        int[] bars = { 0xFF0000, 0x00FF00, 0x0000FF, 0x00FFFF, 0xFF00FF, 0xFFFF00 };
        for (int i = 0; i < bars.length; i++) {
            g.setColor(new Color(bars[i]));
            g.fillRect(6 + i * 20, 6, 20, 30);
        }
        g.setPaint(new GradientPaint(6, 0, new Color(0x000000), 174, 0, new Color(0xFFFFFF)));
        g.fillRect(6, 42, 168, 12);

        g.setColor(new Color(0x263238));
        g.drawLine(6, 116, 110, 62);
        g.drawRect(4, 58, 112, 58);
        g.setFont(font(Font.BOLD, 11));
        g.drawString("aliased", 60, 112);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xFB8C00));
        g.fill(new Ellipse2D.Double(128, 58, 44, 44));
        g.setColor(new Color(0x3949AB));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(10, 104, 120, 64));
        g.setStroke(new BasicStroke());
        g.setColor(new Color(0x263238));
        g.setFont(font(Font.BOLD, 16));
        g.drawString("Java2D", 10, 80);

        g.drawImage(sprite(), 150, 8, null);
        g.setColor(new Color(0x800000FF, true));
        g.fillRect(90, 20, 60, 34);
        g.copyArea(6, 6, 58, 10, 114, 100);
        g.fillRect(182, 10, 14, 100);
    }

    /**
     * A 24 x 24 translucent sprite : a half transparent green disc with an opaque ring.
     */
    static BufferedImage sprite() {
        return image(24, 24, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x9943A047, true));
            g.fillOval(1, 1, 22, 22);
            g.setColor(new Color(0xFF1B5E20, true));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(2, 2, 20, 20);
        });
    }

    /**
     * The probe values of the scene in {@code image} : {@code name=#AARRGGBB} separated by spaces.
     */
    static String sceneProbes(BufferedImage image) {
        List<String> values = new ArrayList<>();
        for (Object[] probe : SCENE_PROBES) {
            values.add(Checks.argb(image.getRGB((Integer) probe[1], (Integer) probe[2])));
        }
        return String.join(" ", values);
    }

    /**
     * {@code image} converted to {@code TYPE_INT_ARGB} with {@code drawImage} (the blit loops from the image type).
     */
    static BufferedImage toArgb(java.awt.Image image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------- every image kind

    /**
     * A named {@code BufferedImage} of one of the kinds below.
     */
    record Surface(String name, BufferedImage image) {
    }

    /**
     * {@code width x height} images of every kind : the 13 predefined types, 2 and 4 bit {@code TYPE_BYTE_BINARY}
     * (custom palettes) and {@code TYPE_CUSTOM} images (other color models, sample models and data types). Each kind
     * selects other Java2D loops : native loops for the predefined types, the generic "any" loops (pixel writers,
     * {@code OpaqueCopyArgbToAny}/{@code AnyToArgb}, loaded by name) and color conversions for the custom ones.
     */
    static List<Surface> bufferedImages(int width, int height) {
        List<Surface> surfaces = new ArrayList<>();
        for (int type : predefinedTypes()) {
            surfaces.add(new Surface(imageType(type), new BufferedImage(width, height, type)));
        }
        surfaces.add(new Surface("TYPE_BYTE_BINARY 2 bit", new BufferedImage(width, height,
                BufferedImage.TYPE_BYTE_BINARY, new java.awt.image.IndexColorModel(2, 4,
                        new byte[] { 0, 85, (byte) 170, (byte) 255 }, new byte[] { 0, 85, (byte) 170, (byte) 255 },
                        new byte[] { 0, 85, (byte) 170, (byte) 255 }))));
        byte[] r = new byte[16];
        byte[] g = new byte[16];
        byte[] b = new byte[16];
        for (int i = 0; i < 16; i++) {
            // the 16 colors of CGA/EGA
            int intensity = (i & 8) != 0 ? 0x55 : 0;
            r[i] = (byte) (((i & 4) != 0 ? 0xAA : 0) + intensity);
            g[i] = (byte) (i == 6 ? 0x55 : ((i & 2) != 0 ? 0xAA : 0) + intensity);
            b[i] = (byte) (((i & 1) != 0 ? 0xAA : 0) + intensity);
        }
        surfaces.add(new Surface("TYPE_BYTE_BINARY 4 bit", new BufferedImage(width, height,
                BufferedImage.TYPE_BYTE_BINARY, new java.awt.image.IndexColorModel(4, 16, r, g, b))));
        java.awt.color.ColorSpace srgb = java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB);
        surfaces.add(custom("CUSTOM ARGB 4444 ushort", new java.awt.image.DirectColorModel(srgb, 16, 0x0F00, 0x00F0,
                0x000F, 0xF000, false, java.awt.image.DataBuffer.TYPE_USHORT), width, height));
        surfaces.add(custom("CUSTOM RGB 332 byte", new java.awt.image.DirectColorModel(8, 0xE0, 0x1C, 0x03), width,
                height));
        surfaces.add(custom("CUSTOM RGBA 8888 int", new java.awt.image.DirectColorModel(srgb, 32, 0xFF000000, 0x00FF0000,
                0x0000FF00, 0x000000FF, false, java.awt.image.DataBuffer.TYPE_INT), width, height));
        surfaces.add(custom("CUSTOM LINEAR_RGB alpha float", new java.awt.image.ComponentColorModel(
                java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_LINEAR_RGB), true, false,
                java.awt.Transparency.TRANSLUCENT, java.awt.image.DataBuffer.TYPE_FLOAT), width, height));
        surfaces.add(custom("CUSTOM GRAY alpha byte", new java.awt.image.ComponentColorModel(
                java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY), true, false,
                java.awt.Transparency.TRANSLUCENT, java.awt.image.DataBuffer.TYPE_BYTE), width, height));
        java.awt.image.ComponentColorModel banded = new java.awt.image.ComponentColorModel(srgb, false, false,
                java.awt.Transparency.OPAQUE, java.awt.image.DataBuffer.TYPE_BYTE);
        surfaces.add(new Surface("CUSTOM sRGB banded byte", new BufferedImage(banded,
                java.awt.image.Raster.createBandedRaster(java.awt.image.DataBuffer.TYPE_BYTE, width, height, 3, null),
                false, null)));
        surfaces.add(custom("CUSTOM CIEXYZ ushort", new java.awt.image.ComponentColorModel(
                java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_CIEXYZ), false, false,
                java.awt.Transparency.OPAQUE, java.awt.image.DataBuffer.TYPE_USHORT), width, height));
        return surfaces;
    }

    private static Surface custom(String name, java.awt.image.ColorModel model, int width, int height) {
        return new Surface(name, new BufferedImage(model, model.createCompatibleWritableRaster(width, height),
                model.isAlphaPremultiplied(), null));
    }

    /**
     * {@code ColorModel / SampleModel / DataBuffer} simple class names, bits per pixel, bands and transparency.
     */
    static String model(BufferedImage image) {
        java.awt.image.ColorModel cm = image.getColorModel();
        return cm.getClass().getSimpleName() + " " + cm.getPixelSize() + " bits " + transparency(cm.getTransparency())
                + (cm.isAlphaPremultiplied() ? " premultiplied" : "") + ", "
                + image.getSampleModel().getClass().getSimpleName() + " " + image.getSampleModel().getNumBands()
                + " bands, " + image.getRaster().getDataBuffer().getClass().getSimpleName() + " "
                + image.getRaster().getDataBuffer().getSize();
    }

    // -------------------------------------------------------------------------------------------------------- slots

    /**
     * A lightweight component showing an image set later (e.g. once the page is ready), scaled to a fixed size with
     * nearest neighbor interpolation ; a light placeholder before.
     */
    static final class Slot extends java.awt.Component {

        private final int width;
        private final int height;
        private transient BufferedImage image;

        Slot(int width, int height) {
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
        public void paint(java.awt.Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                if (image == null) {
                    g.setColor(new Color(0xFFECEFF1, true));
                    g.fillRect(0, 0, width, height);
                    return;
                }
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(image, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------- names

    private static final Map<Integer, String> IMAGE_TYPES = Map.ofEntries(
            Map.entry(BufferedImage.TYPE_CUSTOM, "TYPE_CUSTOM"),
            Map.entry(BufferedImage.TYPE_INT_RGB, "TYPE_INT_RGB"),
            Map.entry(BufferedImage.TYPE_INT_ARGB, "TYPE_INT_ARGB"),
            Map.entry(BufferedImage.TYPE_INT_ARGB_PRE, "TYPE_INT_ARGB_PRE"),
            Map.entry(BufferedImage.TYPE_INT_BGR, "TYPE_INT_BGR"),
            Map.entry(BufferedImage.TYPE_3BYTE_BGR, "TYPE_3BYTE_BGR"),
            Map.entry(BufferedImage.TYPE_4BYTE_ABGR, "TYPE_4BYTE_ABGR"),
            Map.entry(BufferedImage.TYPE_4BYTE_ABGR_PRE, "TYPE_4BYTE_ABGR_PRE"),
            Map.entry(BufferedImage.TYPE_USHORT_565_RGB, "TYPE_USHORT_565_RGB"),
            Map.entry(BufferedImage.TYPE_USHORT_555_RGB, "TYPE_USHORT_555_RGB"),
            Map.entry(BufferedImage.TYPE_BYTE_GRAY, "TYPE_BYTE_GRAY"),
            Map.entry(BufferedImage.TYPE_USHORT_GRAY, "TYPE_USHORT_GRAY"),
            Map.entry(BufferedImage.TYPE_BYTE_BINARY, "TYPE_BYTE_BINARY"),
            Map.entry(BufferedImage.TYPE_BYTE_INDEXED, "TYPE_BYTE_INDEXED"));

    /**
     * The name of a {@code BufferedImage.TYPE_*} constant.
     */
    static String imageType(int type) {
        return IMAGE_TYPES.getOrDefault(type, "type " + type);
    }

    /**
     * The 13 predefined {@code BufferedImage} types, in constant order.
     */
    static int[] predefinedTypes() {
        return new int[] { BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE,
                BufferedImage.TYPE_INT_BGR, BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_4BYTE_ABGR,
                BufferedImage.TYPE_4BYTE_ABGR_PRE, BufferedImage.TYPE_USHORT_565_RGB, BufferedImage.TYPE_USHORT_555_RGB,
                BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_USHORT_GRAY, BufferedImage.TYPE_BYTE_BINARY,
                BufferedImage.TYPE_BYTE_INDEXED };
    }

    static String transparency(int transparency) {
        return switch (transparency) {
            case java.awt.Transparency.OPAQUE -> "OPAQUE";
            case java.awt.Transparency.BITMASK -> "BITMASK";
            case java.awt.Transparency.TRANSLUCENT -> "TRANSLUCENT";
            default -> "transparency " + transparency;
        };
    }
}
