package io.quarkiverse.desktop.showcase.pages.images;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Helpers of the images pages : deterministic test images computed pixel by pixel (no Java2D rendering involved, so
 * they are identical everywhere), tiles grids with captions, pixel comparisons, and late content (computed in the
 * background, shown once ready).
 */
final class ImagesSupport {

    static final int WIDTH = 1000;
    static final int INK = 0x1A1A1A;
    static final int MUTED = 0x546E7A;
    static final int RULE = 0xE3E8EC;
    static final int TILE_BACKGROUND = 0xF4F6F8;

    private ImagesSupport() {
    }

    // -------------------------------------------------------------------------------------------- test images

    /**
     * The RGB test pattern : color bars, horizontal and vertical ramps, a checkerboard and a disc, computed per pixel.
     */
    static int patternRgb(int x, int y, int w, int h) {
        int bar = x * 8 / w;
        if (y < h / 4) {
            // color bars : white yellow cyan green magenta red blue black
            int[] bars = { 0xFFFFFF, 0xFFFF00, 0x00FFFF, 0x00FF00, 0xFF00FF, 0xFF0000, 0x0000FF, 0x000000 };
            return bars[bar];
        }
        if (y < h / 2) {
            // ramps : red, green and blue ramps side by side, gray ramp below
            int v = (x * 255) / Math.max(1, w - 1);
            int third = y - h / 4;
            int band = third * 4 / Math.max(1, h / 4);
            return switch (band) {
                case 0 -> v << 16;
                case 1 -> v << 8;
                case 2 -> v;
                default -> v << 16 | v << 8 | v;
            };
        }
        // lower half : checkerboard with a disc
        int cx = w * 3 / 4;
        int cy = h * 3 / 4;
        int r = h / 5;
        int dx = x - cx;
        int dy = y - cy;
        if (dx * dx + dy * dy <= r * r) {
            return 0xFF8F00;
        }
        boolean light = ((x / 8) + (y / 8)) % 2 == 0;
        return light ? 0xE3F2FD : 0x1565C0;
    }

    static BufferedImage pattern(int w, int h, int type) {
        BufferedImage image = new BufferedImage(w, h, type);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, 0xFF000000 | patternRgb(x, y, w, h));
            }
        }
        return image;
    }

    /**
     * The test pattern with an alpha ramp (opaque on the left, transparent on the right).
     */
    static BufferedImage patternArgb(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = 255 - (x * 200) / Math.max(1, w - 1);
                image.setRGB(x, y, alpha << 24 | patternRgb(x, y, w, h));
            }
        }
        return image;
    }

    /**
     * A smooth "photographic" image (gradients and soft discs), suited to lossy compression.
     */
    static BufferedImage photo(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double fx = x / (double) w;
                double fy = y / (double) h;
                double sky = 1 - fy;
                double r = 40 + 150 * fy + 60 * Math.exp(-sq((fx - 0.3) * 5) - sq((fy - 0.35) * 6));
                double g = 90 + 120 * sky * (1 - fx * 0.3);
                double b = 120 + 130 * sky;
                double sun = Math.exp(-sq((fx - 0.72) * 9) - sq((fy - 0.3) * 9));
                r += 120 * sun;
                g += 100 * sun;
                if (fy > 0.7 + 0.08 * Math.sin(fx * 9)) {
                    r = 50 + 40 * fx;
                    g = 110 + 60 * (1 - fy);
                    b = 40;
                }
                image.setRGB(x, y, rgb(r, g, b));
            }
        }
        return image;
    }

    private static double sq(double v) {
        return v * v;
    }

    static int rgb(double r, double g, double b) {
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /**
     * The pattern as a gray image of {@code type} ({@code TYPE_BYTE_GRAY} or {@code TYPE_USHORT_GRAY}), set through the
     * raster (no color conversion).
     */
    static BufferedImage gray(int w, int h, int type) {
        BufferedImage image = new BufferedImage(w, h, type);
        int max = type == BufferedImage.TYPE_USHORT_GRAY ? 65535 : 255;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = patternRgb(x, y, w, h);
                int luma = (((rgb >> 16) & 0xFF) * 77 + ((rgb >> 8) & 0xFF) * 150 + (rgb & 0xFF) * 29) >> 8;
                int value = max == 255 ? luma : luma * 257 + ((x + y) % 7) * 11;
                image.getRaster().setSample(x, y, 0, value);
            }
        }
        return image;
    }

    /**
     * An indexed image : {@code bits} per pixel (1, 2, 4 or 8) with a fixed palette, pixels set through the raster.
     */
    static BufferedImage indexed(int w, int h, int bits, int transparentIndex) {
        int size = 1 << bits;
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            // a palette covering hues and grays
            double t = i / (double) Math.max(1, size - 1);
            r[i] = (byte) clamp(255 * (0.5 + 0.5 * Math.cos(2 * Math.PI * t)));
            g[i] = (byte) clamp(255 * (0.5 + 0.5 * Math.cos(2 * Math.PI * (t - 1 / 3.0))));
            b[i] = (byte) clamp(255 * (0.5 + 0.5 * Math.cos(2 * Math.PI * (t - 2 / 3.0))));
        }
        IndexColorModel model = transparentIndex >= 0 ? new IndexColorModel(bits, size, r, g, b, transparentIndex)
                : new IndexColorModel(bits, size, r, g, b);
        BufferedImage image = bits == 8 ? new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, model)
                : new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY, model);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = ((x * size) / w + (y / 8)) % size;
                if (bits == 1) {
                    index = ((x / 6) + (y / 6)) % 2;
                }
                image.getRaster().setSample(x, y, 0, index);
            }
        }
        return image;
    }

    /**
     * A black and white image (TYPE_BYTE_BINARY with its default palette) : checkerboard and a disc.
     */
    static BufferedImage binary(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = x - w / 2;
                int dy = y - h / 2;
                boolean disc = dx * dx + dy * dy < (h / 3) * (h / 3);
                boolean check = ((x / 6) + (y / 6)) % 2 == 0;
                image.getRaster().setSample(x, y, 0, disc ^ check ? 1 : 0);
            }
        }
        return image;
    }

    // ---------------------------------------------------------------------------------------------- comparisons

    /**
     * {@code "exact"} when the ARGB pixels are equal, otherwise the mean and maximum absolute channel difference.
     */
    static String compare(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return "size " + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight();
        }
        long sum = 0;
        int max = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                for (int shift = 0; shift < 32; shift += 8) {
                    int d = Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF));
                    sum += d;
                    max = Math.max(max, d);
                }
            }
        }
        if (max == 0) {
            return "exact";
        }
        return "mean " + Checks.num(sum / (4.0 * a.getWidth() * a.getHeight()), 2) + ", max " + max;
    }

    static double meanError(BufferedImage a, BufferedImage b) {
        String c = compare(a, b);
        if (c.equals("exact")) {
            return 0;
        }
        if (!c.startsWith("mean ")) {
            return Double.MAX_VALUE;
        }
        return Double.parseDouble(c.substring(5, c.indexOf(',')));
    }

    /**
     * {@code image} converted to {@code TYPE_INT_ARGB} (drawn with the Src rule : color conversions of the source color
     * model apply, transparent pixels keep their color).
     */
    static BufferedImage argb(Image image) {
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            // Src : transparent pixels keep their color components
            g.setComposite(java.awt.AlphaComposite.Src);
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    static String size(BufferedImage image) {
        return image.getWidth() + "x" + image.getHeight();
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /**
     * A tile of a grid : an image (scaled down to fit, never up) and caption lines.
     */
    record Tile(BufferedImage image, String... caption) {
    }

    static BufferedImage grid(List<Tile> tiles, int columns, int tileWidth, int tileHeight) {
        int rows = Math.max(1, (tiles.size() + columns - 1) / columns);
        return Snapshots.offscreen(columns * tileWidth, rows * tileHeight, g -> {
            for (int i = 0; i < tiles.size(); i++) {
                Graphics2D t = (Graphics2D) g.create((i % columns) * tileWidth, (i / columns) * tileHeight, tileWidth,
                        tileHeight);
                try {
                    paintTile(t, tiles.get(i), tileWidth, tileHeight);
                } finally {
                    t.dispose();
                }
            }
        });
    }

    private static void paintTile(Graphics2D g, Tile tile, int w, int h) {
        g.setColor(new Color(TILE_BACKGROUND));
        g.fillRect(2, 2, w - 4, h - 4);
        int captionHeight = 13 * tile.caption().length + 4;
        int areaW = w - 12;
        int areaH = h - 10 - captionHeight;
        BufferedImage image = tile.image();
        if (image != null) {
            double scale = Math.min(1, Math.min(areaW / (double) image.getWidth(), areaH / (double) image.getHeight()));
            int iw = (int) Math.round(image.getWidth() * scale);
            int ih = (int) Math.round(image.getHeight() * scale);
            int x = (w - iw) / 2;
            int y = 6 + (areaH - ih) / 2;
            checker(g, x, y, iw, ih);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, x, y, iw, ih, null);
        } else {
            g.setColor(new Color(Ui.ERROR_COLOR));
            g.drawLine(8, 8, w - 8, h - captionHeight - 4);
            g.drawLine(w - 8, 8, 8, h - captionHeight - 4);
        }
        labelHints(g);
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        float y = h - captionHeight + 10;
        for (int i = 0; i < tile.caption().length; i++) {
            g.setColor(new Color(i == 0 ? INK : MUTED));
            String line = fit(g, tile.caption()[i], w - 8);
            int width = g.getFontMetrics().stringWidth(line);
            g.drawString(line, (w - width) / 2f, y);
            y += 13;
        }
    }

    private static String fit(Graphics2D g, String s, int width) {
        String line = s;
        while (line.length() > 3 && g.getFontMetrics().stringWidth(line) > width) {
            line = line.substring(0, line.length() - 2);
        }
        return line.equals(s) ? s : line + "…";
    }

    /**
     * A light checkerboard behind transparent images.
     */
    static void checker(Graphics2D g, int x, int y, int w, int h) {
        for (int j = 0; j < h; j += 6) {
            for (int i = 0; i < w; i += 6) {
                g.setColor(new Color(((i + j) / 6) % 2 == 0 ? 0xFFFFFF : 0xDDDDDD));
                g.fillRect(x + i, y + j, Math.min(6, w - i), Math.min(6, h - j));
            }
        }
    }

    static void labelHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    // -------------------------------------------------------------------------------------------- late content

    /**
     * Replaces the children of {@code holder} (on the EDT) and lays the page out again.
     */
    static void show(Container holder, Component... parts) {
        holder.removeAll();
        for (Component part : parts) {
            holder.add(part);
        }
        holder.invalidate();
        Snapshots.layout(holder);
        holder.repaint();
    }

    /**
     * Runs {@code action}, adding its checks to {@code checks}, or a failed check {@code name} with the exception.
     */
    static void section(List<Check> checks, String name, SectionAction action) {
        try {
            action.run(checks);
        } catch (Throwable t) {
            checks.add(Check.fail(name, Checks.describe(t)));
        }
    }

    interface SectionAction {
        void run(List<Check> checks) throws Exception;
    }

    static String lower(String[] names) {
        List<String> list = new ArrayList<>();
        for (String n : names) {
            String l = n.toLowerCase(Locale.ROOT);
            if (!list.contains(l)) {
                list.add(l);
            }
        }
        list.sort(null);
        return String.join(" ", list);
    }
}
