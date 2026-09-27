package io.quarkiverse.desktop.showcase.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Captioned tiles painted offscreen into one image, in a grid : for each tile a background, a border, the drawing area
 * (clipped, anti-aliasing on, pure strokes, gray scale text anti-aliasing, integer metrics) and a centered caption.
 * Pages show the image ({@link Ui#image}) and probe its pixels ({@link #probe}) : the capture does not depend on the
 * on-screen pipeline.
 * <p>
 * Colors are {@code int} constants and every AWT object is created when the grid is painted : an application class
 * never holds AWT objects in static fields (Quarkus initializes application classes at build time, AWT classes at run
 * time).
 */
public final class Grid {

    public static final int BACKGROUND = 0xFFF7F9FC;
    public static final int BORDER = 0xFFB0BEC5;
    public static final int INK = 0xFF263238;

    /** Height of the caption area at the bottom of a tile. */
    public static final int CAPTION_HEIGHT = 24;
    /** Margin around the drawing area of a tile. */
    public static final int INSET = 8;

    /**
     * Paints the drawing area of a tile : {@code width x height} pixels, the origin at the top left corner of the area.
     */
    @FunctionalInterface
    public interface Painter {
        void paint(Graphics2D g, int width, int height);
    }

    /**
     * A tile : its caption (lines separated by {@code \n}) and the painter of its drawing area.
     */
    public record Tile(String caption, Painter painter) {
    }

    private final int columns;
    private final int tileWidth;
    private final int tileHeight;
    private final List<Tile> tiles;

    public Grid(int columns, int tileWidth, int tileHeight, List<Tile> tiles) {
        this.columns = columns;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.tiles = List.copyOf(tiles);
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public int areaWidth() {
        return tileWidth - 2 * INSET;
    }

    public int areaHeight() {
        return tileHeight - INSET - CAPTION_HEIGHT;
    }

    public int width() {
        return columns * tileWidth;
    }

    public int height() {
        return (tiles.size() + columns - 1) / columns * tileHeight;
    }

    /**
     * Index of the tile with {@code caption}.
     */
    public int index(String caption) {
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
    public Point origin(int index) {
        return new Point((index % columns) * tileWidth + INSET, (index / columns) * tileHeight + INSET);
    }

    /**
     * {@code #AARRGGBB} of the pixel at ({@code x}, {@code y}) of the drawing area of the tile with {@code caption}.
     */
    public String probe(BufferedImage image, String caption, int x, int y) {
        Point o = origin(index(caption));
        return Checks.argb(image.getRGB(o.x + x, o.y + y));
    }

    public BufferedImage paint() {
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

    // ---------------------------------------------------------------------------------------------------- painting

    /**
     * The logical font {@code Dialog} of {@code style} and {@code size}.
     */
    public static Font font(int style, float size) {
        return new Font(Font.DIALOG, style, 1).deriveFont(style, size);
    }

    /**
     * Anti-aliasing on, pure strokes, gray scale text anti-aliasing and integer metrics.
     */
    public static void defaultHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        textHints(g);
    }

    /**
     * Gray scale text anti-aliasing and integer metrics.
     */
    public static void textHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }
}
