package io.quarkiverse.desktop.showcase.pages.java2d;

import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.ACCENT;
import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.FILLS;
import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.INK;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Strokes : {@code BasicStroke} caps x joins, miter limits, widths (with and without anti-aliasing), dash arrays and
 * phases, zero length segments, {@code KEY_STROKE_CONTROL} (normalize vs pure), strokes under a non uniform transform,
 * custom {@code Stroke} implementations, {@code createStrokedShape}, and the {@code Graphics} outline primitives (1 px
 * native loops and wide strokes).
 * <p>
 * Capture method C. Every stroke goes through the Marlin rendering engine ({@code sun.java2d.marlin.DMarlinRenderingEngine},
 * instantiated by name), the span iterators and the fill/mask loops. Checks : outline geometry (bounds, containment,
 * dash counts), argument validation, and exact pixel probes inside solid strokes.
 */
@Singleton
public class StrokesPage implements FeaturePage {

    private static final int COLUMNS = 6;
    private static final int TILE_WIDTH = 164;
    private static final int TILE_HEIGHT = 150;

    private static final int[] CAPS = { BasicStroke.CAP_BUTT, BasicStroke.CAP_ROUND, BasicStroke.CAP_SQUARE };
    private static final int[] JOINS = { BasicStroke.JOIN_MITER, BasicStroke.JOIN_ROUND, BasicStroke.JOIN_BEVEL };

    @Override
    public String id() {
        return "j2d-strokes";
    }

    @Override
    public String title() {
        return "Strokes";
    }

    @Override
    public String category() {
        return Categories.JAVA2D;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Component build() {
        Grid grid = new Grid(COLUMNS, TILE_WIDTH, TILE_HEIGHT, tiles());
        BufferedImage image = grid.paint();

        List<Check> geometry = geometryChecks();
        List<Check> rendering = renderingChecks(grid, image);
        return Ui.column(14,
                Ui.text("BasicStroke and custom strokes drawn into a TYPE_INT_ARGB image. Red lines and dots show the "
                        + "path being stroked. Magnified tiles show single pixels (nearest neighbor, x6).", 1000),
                Ui.image(image),
                ChecksView.table("Stroke geometry", geometry),
                ChecksView.table("Rendering", rendering));
    }

    // ------------------------------------------------------------------------------------------------------ tiles

    private static List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        for (int cap : CAPS) {
            for (int join : JOINS) {
                tiles.add(new Tile(capName(cap) + "\n" + joinName(join), (g, w, h) -> {
                    g.setColor(new Color(FILLS[join], true));
                    g.setStroke(new BasicStroke(14, cap, join));
                    g.draw(zigzag());
                    path(g, zigzag());
                }));
            }
        }
        tiles.add(new Tile("JOIN_MITER, miterlimit 10", (g, w, h) -> miters(g, 10)));
        tiles.add(new Tile("JOIN_MITER, miterlimit 4", (g, w, h) -> miters(g, 4)));
        tiles.add(new Tile("widths 0 to 8, AA off", (g, w, h) -> widths(g, false)));
        tiles.add(new Tile("widths 0 to 8, AA on", (g, w, h) -> widths(g, true)));
        tiles.add(new Tile("dash arrays", (g, w, h) -> {
            g.setColor(new Color(INK, true));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 12, 6 }, 0));
            g.draw(new Line2D.Double(8, 14, 140, 14));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[] { 18, 4, 4, 4 }, 0));
            g.draw(new Line2D.Double(8, 40, 140, 40));
            g.setColor(new Color(FILLS[0], true));
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] { 1, 6 }, 0));
            g.draw(new Line2D.Double(8, 66, 140, 66));
            g.setColor(new Color(FILLS[3], true));
            g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] { 0, 14 }, 0));
            g.draw(new Line2D.Double(10, 94, 140, 94));
        }));
        tiles.add(new Tile("dash phases 0, 4, 8, 12, 16", (g, w, h) -> {
            g.setColor(new Color(ACCENT, true));
            g.fillRect(9, 4, 1, 104);
            g.setColor(new Color(INK, true));
            for (int i = 0; i < 5; i++) {
                g.setStroke(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 16, 8 },
                        i * 4));
                g.draw(new Line2D.Double(10, 12 + i * 22, 140, 12 + i * 22));
            }
        }));
        tiles.add(new Tile("dashed shapes", (g, w, h) -> {
            g.setColor(new Color(FILLS[4], true));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 10, 5 }, 0));
            g.draw(new Ellipse2D.Double(6, 6, 80, 60));
            g.setColor(new Color(FILLS[5], true));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10, new float[] { 4, 4 }, 0));
            g.draw(new RoundRectangle2D.Double(70, 30, 70, 50, 20, 20));
            g.setColor(new Color(INK, true));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10,
                    new float[] { 14, 4, 2, 4 }, 0));
            g.draw(new CubicCurve2D.Double(6, 110, 40, 40, 90, 150, 142, 96));
        }));
        tiles.add(new Tile("zero length segments\nBUTT, ROUND, SQUARE", (g, w, h) -> {
            for (int i = 0; i < 3; i++) {
                double x = 28 + i * 46;
                g.setColor(new Color(FILLS[i], true));
                g.setStroke(new BasicStroke(24, CAPS[i], BasicStroke.JOIN_MITER));
                g.draw(dot(x, 56));
                g.setColor(new Color(ACCENT, true));
                g.setStroke(new BasicStroke(1));
                g.draw(new Line2D.Double(x - 4, 56, x + 4, 56));
                g.draw(new Line2D.Double(x, 52, x, 60));
            }
        }));
        tiles.add(new Tile("STROKE_NORMALIZE (x6)", (g, w, h) -> Java2dSupport.magnified(g, thinLines(false), 6)));
        tiles.add(new Tile("STROKE_PURE (x6)", (g, w, h) -> Java2dSupport.magnified(g, thinLines(true), 6)));
        tiles.add(new Tile("width 3 under scale(3, 1)", (g, w, h) -> {
            g.setColor(new Color(FILLS[1], true));
            g.scale(3, 1);
            g.setStroke(new BasicStroke(3));
            g.draw(new Ellipse2D.Double(4, 10, 40, 96));
            g.draw(new Line2D.Double(10, 58, 38, 58));
        }));
        tiles.add(new Tile("custom Stroke : double line", (g, w, h) -> {
            g.setColor(new Color(INK, true));
            g.setStroke(new DoubleLineStroke(12, 1.5f));
            g.draw(new RoundRectangle2D.Double(12, 12, 124, 90, 36, 36));
            path(g, new RoundRectangle2D.Double(12, 12, 124, 90, 36, 36));
        }));
        tiles.add(new Tile("custom Stroke : zigzag", (g, w, h) -> {
            g.setColor(new Color(FILLS[5], true));
            g.setStroke(new ZigzagStroke(4, 7, new BasicStroke(1.5f)));
            g.draw(new Ellipse2D.Double(14, 12, 120, 90));
        }));
        tiles.add(new Tile("custom Stroke : arrows", (g, w, h) -> {
            CubicCurve2D curve = new CubicCurve2D.Double(8, 100, 30, -10, 110, 130, 140, 14);
            g.setColor(new Color(FILLS[3], true));
            g.setStroke(new ArrowStroke(18, 7));
            g.draw(curve);
            path(g, curve);
        }));
        tiles.add(new Tile("stroked Area, JOIN_ROUND", (g, w, h) -> {
            Area area = new Area(new Ellipse2D.Double(10, 10, 80, 80));
            area.add(new Area(new Rectangle2D.Double(50, 40, 86, 66)));
            area.subtract(new Area(new Ellipse2D.Double(34, 34, 32, 32)));
            g.setColor(new Color(0xFFE3F2FD, true));
            g.fill(area);
            g.setColor(new Color(FILLS[0], true));
            g.setStroke(new BasicStroke(6, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(area);
        }));
        tiles.add(new Tile("Graphics outlines, 1 px, AA off", (g, w, h) -> primitives(g, new BasicStroke())));
        tiles.add(new Tile("Graphics outlines, 3 px, AA off", (g, w, h) -> primitives(g, new BasicStroke(3))));
        tiles.add(new Tile("wide curves", (g, w, h) -> {
            g.setColor(new Color(FILLS[2], true));
            g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            QuadCurve2D quad = new QuadCurve2D.Double(10, 50, 74, -30, 138, 50);
            g.draw(quad);
            g.setColor(new Color(FILLS[4], true));
            g.setStroke(new BasicStroke(8, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            CubicCurve2D cubic = new CubicCurve2D.Double(10, 104, 50, 40, 100, 150, 138, 70);
            g.draw(cubic);
            path(g, quad);
            path(g, cubic);
        }));
        tiles.add(new Tile("translucent self-intersecting", (g, w, h) -> {
            g.setColor(new Color(0x803949AB, true));
            g.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(loop());
            path(g, loop());
        }));
        tiles.add(new Tile("thin AA lines 0.25 to 1", (g, w, h) -> {
            g.setColor(new Color(INK, true));
            float[] widths = { 0.25f, 0.5f, 0.75f, 1f };
            for (int i = 0; i < widths.length; i++) {
                g.setStroke(new BasicStroke(widths[i]));
                for (int j = 0; j < 5; j++) {
                    g.draw(new Line2D.Double(4 + i * 36 + j * 5, 6, 12 + i * 36 + j * 5, 106));
                }
            }
        }));
        tiles.add(new Tile("createStrokedShape outline", (g, w, h) -> {
            Shape outline = new BasicStroke(18, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
                    .createStrokedShape(chevron());
            g.setColor(new Color(0xFFFFE0B2, true));
            g.fill(outline);
            g.setColor(new Color(INK, true));
            g.setStroke(new BasicStroke(1));
            g.draw(outline);
            path(g, chevron());
        }));
        return tiles;
    }

    /** The path of the caps x joins tiles : 4 segments with sharp joins. */
    private static Path2D zigzag() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(16, 88);
        path.lineTo(46, 30);
        path.lineTo(76, 88);
        path.lineTo(106, 30);
        path.lineTo(130, 72);
        return path;
    }

    private static Path2D chevron() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(24, 96);
        path.lineTo(74, 24);
        path.lineTo(124, 96);
        return path;
    }

    /** A cubic curve with a loop (self intersecting). */
    private static CubicCurve2D loop() {
        return new CubicCurve2D.Double(10, 100, 190, -30, -40, -30, 138, 100);
    }

    /** A zero length segment at ({@code x}, {@code y}). */
    private static Path2D dot(double x, double y) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x, y);
        path.lineTo(x, y);
        return path;
    }

    /** Draws the stroked path in thin red, with its vertices. */
    private static void path(Graphics2D g, Shape shape) {
        Graphics2D p = (Graphics2D) g.create();
        try {
            p.setColor(new Color(ACCENT, true));
            p.setStroke(new BasicStroke(1));
            p.draw(shape);
            double[] coords = new double[6];
            for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
                int type = it.currentSegment(coords);
                int n = switch (type) {
                    case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> 0;
                    case PathIterator.SEG_QUADTO -> 2;
                    case PathIterator.SEG_CUBICTO -> 4;
                    default -> -1;
                };
                if (n >= 0) {
                    p.fill(new Ellipse2D.Double(coords[n] - 2.5, coords[n + 1] - 2.5, 5, 5));
                }
            }
        } finally {
            p.dispose();
        }
    }

    /** Vertices with miter ratios 2.7, 3.3, 4.4, 5.7 and 8.5, stroked with JOIN_MITER and {@code limit}. */
    private static Path2D miterPath() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(8, 98);
        path.lineTo(38, 30);
        path.lineTo(68, 98);
        path.lineTo(86, 30);
        path.lineTo(104, 98);
        path.lineTo(113, 30);
        path.lineTo(122, 98);
        return path;
    }

    private static void miters(Graphics2D g, float limit) {
        g.setColor(new Color(FILLS[1], true));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, limit));
        g.draw(miterPath());
        path(g, miterPath());
    }

    private static final float[] WIDTHS = { 0, 0.5f, 1, 1.5f, 2, 3, 5, 8 };

    private static void widths(Graphics2D g, boolean antialiasing) {
        for (int i = 0; i < WIDTHS.length; i++) {
            int y = 8 + i * 14;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(INK, true));
            g.setFont(Java2dSupport.font(Font.PLAIN, 10));
            g.drawString(Checks.num(WIDTHS[i], 1), 2, y + 4);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(0xFF1565C0, true));
            g.setStroke(new BasicStroke(WIDTHS[i]));
            g.draw(new Line2D.Double(30, y, 140, y + 2.5));
        }
    }

    /**
     * Thin lines at fractional positions, AA on, 24 x 19 : snapped to pixel centers with STROKE_NORMALIZE, exact with
     * STROKE_PURE.
     */
    private static BufferedImage thinLines(boolean pure) {
        return Java2dSupport.image(24, 19, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    pure ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
            g.setColor(new Color(INK, true));
            g.setStroke(new BasicStroke(1));
            g.draw(new Line2D.Double(3.3, 1, 3.3, 17));
            g.draw(new Line2D.Double(7.5, 1, 7.5, 17));
            g.draw(new Line2D.Double(11.8, 1, 11.8, 17));
            g.draw(new Line2D.Double(14, 9.5, 23, 9.5));
            g.draw(new Line2D.Double(14, 13.2, 23, 13.2));
            g.draw(new Line2D.Double(14, 1, 22, 7));
            g.draw(new Rectangle2D.Double(15.4, 15.4, 6, 2.6));
        });
    }

    private static void primitives(Graphics2D g, BasicStroke stroke) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setStroke(stroke);
        g.setColor(new Color(INK, true));
        g.drawLine(6, 8, 142, 8);
        g.drawLine(6, 16, 60, 50);
        g.drawRect(70, 16, 40, 30);
        g.drawOval(116, 16, 26, 30);
        g.setColor(new Color(0xFF1565C0, true));
        g.drawArc(6, 58, 44, 44, 30, 240);
        g.drawRoundRect(56, 58, 40, 44, 16, 16);
        g.drawPolyline(new int[] { 104, 114, 124, 134, 142 }, new int[] { 100, 60, 100, 60, 90 }, 5);
        g.setColor(new Color(0xFFC62828, true));
        g.drawPolygon(new int[] { 70, 90, 110 }, new int[] { 44, 20, 44 }, 3);
    }

    // ------------------------------------------------------------------------------------------------- custom strokes

    /**
     * Two parallel lines : the outline of a wide {@code BasicStroke}, stroked again with a thin one.
     */
    static final class DoubleLineStroke implements Stroke {

        private final float width;
        private final float line;
        final AtomicInteger calls = new AtomicInteger();

        DoubleLineStroke(float width, float line) {
            this.width = width;
            this.line = line;
        }

        @Override
        public Shape createStrokedShape(Shape shape) {
            calls.incrementAndGet();
            Shape outline = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND).createStrokedShape(shape);
            return new BasicStroke(line).createStrokedShape(outline);
        }
    }

    /**
     * A zigzag line following the flattened path ({@code FlatteningPathIterator}), stroked with {@code base}.
     */
    static final class ZigzagStroke implements Stroke {

        private final double amplitude;
        private final double wavelength;
        private final Stroke base;

        ZigzagStroke(double amplitude, double wavelength, Stroke base) {
            this.amplitude = amplitude;
            this.wavelength = wavelength;
            this.base = base;
        }

        @Override
        public Shape createStrokedShape(Shape shape) {
            Path2D.Double result = new Path2D.Double();
            double[] c = new double[6];
            double lastX = 0;
            double lastY = 0;
            double startX = 0;
            double startY = 0;
            double distance = 0;
            int side = 1;
            for (PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), 0.5); !it.isDone(); it
                    .next()) {
                int type = it.currentSegment(c);
                if (type == PathIterator.SEG_MOVETO) {
                    result.moveTo(c[0], c[1]);
                    lastX = startX = c[0];
                    lastY = startY = c[1];
                    continue;
                }
                double x = type == PathIterator.SEG_CLOSE ? startX : c[0];
                double y = type == PathIterator.SEG_CLOSE ? startY : c[1];
                double dx = x - lastX;
                double dy = y - lastY;
                double length = Math.sqrt(dx * dx + dy * dy);
                if (length == 0) {
                    continue;
                }
                double ux = dx / length;
                double uy = dy / length;
                double next = wavelength / 2 - distance;
                while (next < length) {
                    double px = lastX + ux * next - uy * amplitude * side;
                    double py = lastY + uy * next + ux * amplitude * side;
                    result.lineTo(px, py);
                    side = -side;
                    next += wavelength / 2;
                }
                distance = length - (next - wavelength / 2);
                lastX = x;
                lastY = y;
                if (type == PathIterator.SEG_CLOSE) {
                    result.lineTo(x, y);
                }
            }
            return base.createStrokedShape(result);
        }
    }

    /**
     * Arrow heads placed every {@code spacing} pixels along the flattened path, oriented along it.
     */
    static final class ArrowStroke implements Stroke {

        private final double spacing;
        private final double size;

        ArrowStroke(double spacing, double size) {
            this.spacing = spacing;
            this.size = size;
        }

        @Override
        public Shape createStrokedShape(Shape shape) {
            Path2D.Double arrow = new Path2D.Double();
            arrow.moveTo(size, 0);
            arrow.lineTo(-size, -size * 0.7);
            arrow.lineTo(-size * 0.4, 0);
            arrow.lineTo(-size, size * 0.7);
            arrow.closePath();
            Path2D.Double result = new Path2D.Double(Path2D.WIND_NON_ZERO);
            double[] c = new double[6];
            double lastX = 0;
            double lastY = 0;
            double next = spacing / 2;
            double travelled = 0;
            for (PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), 0.25); !it.isDone(); it
                    .next()) {
                int type = it.currentSegment(c);
                if (type == PathIterator.SEG_MOVETO) {
                    lastX = c[0];
                    lastY = c[1];
                    continue;
                }
                if (type == PathIterator.SEG_CLOSE) {
                    continue;
                }
                double dx = c[0] - lastX;
                double dy = c[1] - lastY;
                double length = Math.sqrt(dx * dx + dy * dy);
                while (length > 0 && next <= travelled + length) {
                    double t = (next - travelled) / length;
                    double ux = dx / length;
                    double uy = dy / length;
                    AffineTransform at = new AffineTransform(ux, uy, -uy, ux, lastX + dx * t, lastY + dy * t);
                    result.append(at.createTransformedShape(arrow), false);
                    next += spacing;
                }
                travelled += length;
                lastX = c[0];
                lastY = c[1];
            }
            return result;
        }
    }

    // ----------------------------------------------------------------------------------------------------- checks

    private static List<Check> geometryChecks() {
        List<Check> checks = new ArrayList<>();
        String[] expectedBounds = {
                // CAP_BUTT : the start cap corner at (16, 88) - 7 * (0.888, 0.459) ; JOIN_MITER : 30 - 7 / sin(27.35)
                "9.78,14.76 126.30x88.47", "9.78,23.00 126.30x72.00", "9.78,26.53 126.30x64.69",
                "9.00,14.76 128.00x88.47", "9.00,23.00 128.00x72.00", "9.00,26.53 128.00x68.47",
                "6.57,14.76 132.98x88.47", "6.57,23.00 132.98x74.43", "6.57,26.53 132.98x70.91" };
        int i = 0;
        for (int cap : CAPS) {
            for (int join : JOINS) {
                String expected = expectedBounds[i++];
                checks.add(Checks.expect("stroked bounds " + capName(cap) + " " + joinName(join), expected,
                        () -> Checks.bounds(new BasicStroke(14, cap, join).createStrokedShape(zigzag()))));
            }
        }
        checks.add(Checks.expect("miter limit 10 / 4 : stroked bounds", "5.71,10.95 118.77x99.92 / 5.71,20.23 118.77x85.30",
                () -> Checks.bounds(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10)
                        .createStrokedShape(miterPath())) + " / "
                        + Checks.bounds(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4)
                                .createStrokedShape(miterPath()))));
        checks.add(Checks.expect("new BasicStroke()", "1.0 CAP_SQUARE JOIN_MITER 10.0 null 0.0",
                () -> describe(new BasicStroke())));
        checks.add(Checks.expect("BasicStroke getters", "4.0 CAP_ROUND JOIN_BEVEL 10.0 [5.0, 3.0] 2.0",
                () -> describe(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 10,
                        new float[] { 5, 3 }, 2))));
        checks.add(Checks.expect("BasicStroke equals / hashCode of equal strokes", "true / true", () -> {
            BasicStroke a = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] { 4, 2 }, 1);
            BasicStroke b = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] { 4, 2 }, 1);
            return a.equals(b) + " / " + (a.hashCode() == b.hashCode());
        }));
        checks.add(Checks.expect("new BasicStroke(-1)", "IllegalArgumentException: negative width",
                () -> error(() -> new BasicStroke(-1))));
        checks.add(Checks.expect("JOIN_MITER with miterlimit 0.5", "IllegalArgumentException: miter limit < 1",
                () -> error(() -> new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 0.5f))));
        checks.add(Checks.expect("dash { 0, 0 }", "IllegalArgumentException: dash lengths all zero",
                () -> error(() -> new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                        new float[] { 0, 0 }, 0))));
        checks.add(Checks.expect("dash phase -1", "IllegalArgumentException: negative dash phase",
                () -> error(() -> new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                        new float[] { 1, 1 }, -1))));
        checks.add(Checks.expect("width 10 caps contain (103, 0) : BUTT / ROUND / SQUARE", "false / true / true",
                () -> capsContain(103, 0)));
        checks.add(Checks.expect("width 10 caps contain (104, 4) : BUTT / ROUND / SQUARE", "false / false / true",
                () -> capsContain(104, 4)));
        checks.add(Checks.expect("dash { 12, 6 } on a 100 px line : dashes", 6, () -> subpaths(
                new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 12, 6 }, 0)
                        .createStrokedShape(new Line2D.Double(0, 0, 100, 0)))));
        checks.add(Checks.expect("dash { 16, 8 } phase 0 / 4 / 8 / 12 / 16 : dashes on 130 px", "6 / 6 / 6 / 6 / 6",
                () -> {
                    List<String> counts = new ArrayList<>();
                    for (int p = 0; p <= 16; p += 4) {
                        counts.add(String.valueOf(subpaths(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                10, new float[] { 16, 8 }, p).createStrokedShape(new Line2D.Double(10, 0, 140, 0)))));
                    }
                    return String.join(" / ", counts);
                }));
        // Marlin emits the first dash of an open path last (it keeps it to join it with the last one when the path is
        // closed) : sorted x ranges
        checks.add(Checks.expect("dash { 16, 8 } phase 4 : dashes (x ranges)",
                "10.0-22.0 30.0-46.0 54.0-70.0 78.0-94.0 102.0-118.0 126.0-140.0",
                () -> dashRanges(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                        new float[] { 16, 8 }, 4).createStrokedShape(new Line2D.Double(10, 0, 140, 0)))));
        checks.add(Checks.expect("dashed Ellipse2D { 10, 5 } : dashes", 15, () -> subpaths(
                new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 10, 5 }, 0)
                        .createStrokedShape(new Ellipse2D.Double(6, 6, 80, 60)))));
        checks.add(Checks.expect("zero length dashes { 0, 14 } CAP_ROUND : dots on 130 px", 10, () -> subpaths(
                new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] { 0, 14 }, 0)
                        .createStrokedShape(new Line2D.Double(10, 0, 140, 0)))));
        checks.add(Checks.expect("zero length segment BUTT / ROUND / SQUARE : bounds",
                "50.00,38.00 0.00x24.00 / 38.00,38.00 24.00x24.00 / 38.00,38.00 24.00x24.00", () -> {
                    List<String> bounds = new ArrayList<>();
                    for (int cap : CAPS) {
                        bounds.add(Checks.bounds(new BasicStroke(24, cap, BasicStroke.JOIN_MITER)
                                .createStrokedShape(dot(50, 50))));
                    }
                    return String.join(" / ", bounds);
                }));
        checks.add(Checks.expect("stroked shape winding rule", "WIND_NON_ZERO",
                () -> new BasicStroke(3).createStrokedShape(loop()).getPathIterator(null).getWindingRule()
                        == PathIterator.WIND_NON_ZERO ? "WIND_NON_ZERO" : "WIND_EVEN_ODD"));
        checks.add(Checks.expect("width 3 under scale(3, 1) : device bounds", "7.50,8.50 129.00x99.00",
                () -> Checks.bounds(AffineTransform.getScaleInstance(3, 1)
                        .createTransformedShape(new BasicStroke(3).createStrokedShape(new Ellipse2D.Double(4, 10, 40, 96))))));
        checks.add(Checks.expect("DoubleLineStroke : bounds", "5.25,5.25 137.50x103.50",
                () -> Checks.bounds(new DoubleLineStroke(12, 1.5f)
                        .createStrokedShape(new RoundRectangle2D.Double(12, 12, 124, 90, 36, 36)))));
        checks.add(Checks.expect("ZigzagStroke : bounds", "8.18,6.24 130.99x101.71",
                () -> Checks.bounds(new ZigzagStroke(4, 7, new BasicStroke(1.5f))
                        .createStrokedShape(new Ellipse2D.Double(14, 12, 120, 90)))));
        checks.add(Checks.expect("ArrowStroke : arrows", 10, () -> subpaths(new ArrowStroke(18, 7)
                .createStrokedShape(new CubicCurve2D.Double(8, 100, 30, -10, 110, 130, 140, 14)))));
        checks.add(Checks.expect("Area outline of a stroked loop : singular / polygonal", "false / false", () -> {
            Area area = new Area(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(loop()));
            return area.isSingular() + " / " + area.isPolygonal();
        }));
        return checks;
    }

    private static List<Check> renderingChecks(Grid grid, BufferedImage image) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("custom Stroke : createStrokedShape calls per draw", 1, () -> {
            DoubleLineStroke stroke = new DoubleLineStroke(6, 1);
            Java2dSupport.image(20, 20, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
                g.setStroke(stroke);
                g.draw(new Rectangle2D.Double(4, 4, 12, 12));
            });
            return stroke.calls.get();
        }));
        checks.add(Checks.expect("Graphics2D.getStroke() returns the stroke set", true, () -> {
            BasicStroke stroke = new BasicStroke(7);
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setStroke(stroke);
                return g.getStroke() == stroke;
            } finally {
                g.dispose();
            }
        }));
        checks.add(Checks.expect("STROKE_NORMALIZE / PURE : covered pixels", "116 / 187",
                () -> Java2dSupport.covered(thinLines(false)) + " / " + Java2dSupport.covered(thinLines(true))));
        checks.add(Checks.expect("STROKE_NORMALIZE / PURE : pixel (3, 8)", "#FF263238 / #CC263238",
                () -> Checks.argb(thinLines(false).getRGB(3, 8)) + " / " + Checks.argb(thinLines(true).getRGB(3, 8))));
        checks.add(Checks.expect("CAP_BUTT JOIN_MITER tile : pixel inside the stroke", Checks.argb(FILLS[0]),
                () -> grid.probe(image, capName(BasicStroke.CAP_BUTT) + "\n" + joinName(BasicStroke.JOIN_MITER), 35, 61)));
        checks.add(Checks.expect("CAP_ROUND JOIN_ROUND tile : pixel inside the stroke", Checks.argb(FILLS[1]),
                () -> grid.probe(image, capName(BasicStroke.CAP_ROUND) + "\n" + joinName(BasicStroke.JOIN_ROUND), 46, 36)));
        checks.add(Checks.expect("1 px outlines : line / rect edge / rect inside", "#FF263238 / #FF263238 / #FFF7F9FC",
                () -> grid.probe(image, "Graphics outlines, 1 px, AA off", 70, 8) + " / "
                        + grid.probe(image, "Graphics outlines, 1 px, AA off", 90, 46) + " / "
                        + grid.probe(image, "Graphics outlines, 1 px, AA off", 90, 36)));
        checks.add(Checks.expect("3 px outlines : line center / edge", "#FF263238 / #FF263238",
                () -> grid.probe(image, "Graphics outlines, 3 px, AA off", 70, 8) + " / "
                        + grid.probe(image, "Graphics outlines, 3 px, AA off", 70, 9)));
        checks.add(Checks.expect("translucent self-intersecting stroke : overlap alpha = single alpha", true, () -> {
            BufferedImage img = Java2dSupport.image(148, 118, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0x803949AB, true));
                g.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(loop());
            });
            // the loop crosses itself around (74, 44) ; (20, 90) is on a single part of the stroke
            return img.getRGB(74, 44) == img.getRGB(20, 91);
        }));
        checks.add(Checks.expect("widths AA off : pixels per width 0 / 1 / 3 / 8 (column 80)", "1 / 1 / 3 / 8", () -> {
            List<String> counts = new ArrayList<>();
            for (float width : new float[] { 0, 1, 3, 8 }) {
                BufferedImage img = Java2dSupport.image(20, 30, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
                    g.setColor(new Color(INK, true));
                    g.setStroke(new BasicStroke(width));
                    g.draw(new Line2D.Double(0, 15, 20, 15));
                });
                int n = 0;
                for (int y = 0; y < 30; y++) {
                    n += (img.getRGB(10, y) >>> 24) != 0 ? 1 : 0;
                }
                counts.add(String.valueOf(n));
            }
            return String.join(" / ", counts);
        }));
        checks.add(Checks.info("zigzag tile : pixel hash", () -> Checks.sha256(image.getSubimage(
                grid.origin(grid.index("custom Stroke : zigzag")).x, grid.origin(grid.index("custom Stroke : zigzag")).y,
                grid.areaWidth(), grid.areaHeight()))));
        return checks;
    }

    // ------------------------------------------------------------------------------------------------------ helpers

    private static String capsContain(double x, double y) {
        List<String> values = new ArrayList<>();
        for (int cap : CAPS) {
            values.add(String.valueOf(new BasicStroke(10, cap, BasicStroke.JOIN_MITER)
                    .createStrokedShape(new Line2D.Double(0, 0, 100, 0)).contains(x, y)));
        }
        return String.join(" / ", values);
    }

    private static String describe(BasicStroke s) {
        return s.getLineWidth() + " " + capName(s.getEndCap()) + " " + joinName(s.getLineJoin()) + " " + s.getMiterLimit()
                + " " + (s.getDashArray() == null ? "null" : Arrays.toString(s.getDashArray())) + " " + s.getDashPhase();
    }

    private static String error(Runnable action) {
        try {
            action.run();
            return "no exception";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Number of sub paths (MOVETO segments). */
    static int subpaths(Shape shape) {
        int n = 0;
        double[] coords = new double[6];
        for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            if (it.currentSegment(coords) == PathIterator.SEG_MOVETO) {
                n++;
            }
        }
        return n;
    }

    /** The x ranges of the sub paths of {@code shape} (straight segments only), sorted. */
    private static String dashRanges(Shape shape) {
        List<double[]> ranges = new ArrayList<>();
        double[] c = new double[6];
        double[] current = null;
        for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO) {
                current = new double[] { c[0], c[0] };
                ranges.add(current);
            } else if (type != PathIterator.SEG_CLOSE && current != null) {
                current[0] = Math.min(current[0], c[0]);
                current[1] = Math.max(current[1], c[0]);
            }
        }
        ranges.sort((a, b) -> Double.compare(a[0], b[0]));
        List<String> values = new ArrayList<>();
        for (double[] r : ranges) {
            values.add(Checks.num(r[0], 1) + "-" + Checks.num(r[1], 1));
        }
        return String.join(" ", values);
    }

    static String capName(int cap) {
        return switch (cap) {
            case BasicStroke.CAP_BUTT -> "CAP_BUTT";
            case BasicStroke.CAP_ROUND -> "CAP_ROUND";
            default -> "CAP_SQUARE";
        };
    }

    static String joinName(int join) {
        return switch (join) {
            case BasicStroke.JOIN_MITER -> "JOIN_MITER";
            case BasicStroke.JOIN_ROUND -> "JOIN_ROUND";
            default -> "JOIN_BEVEL";
        };
    }
}
