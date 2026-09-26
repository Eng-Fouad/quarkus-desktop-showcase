package io.quarkiverse.desktop.showcase.pages.java2d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The shapes of {@code java.awt.geom} : lines, rectangles, ellipses, arcs (negative and over 360 degree extents),
 * curves (subdivision, equation solvers), polygons, paths with both winding rules ({@code GeneralPath},
 * {@code Path2D.append}), constructive area geometry (curved holes, transformed areas), {@code Rectangle} integer
 * operations, flattening (recursion limits), containment and intersection tests, and transformed shapes.
 * <p>
 * Capture method C : the tiles are drawn with Java2D into a {@code TYPE_INT_ARGB} image (software loops and the Marlin
 * renderer, no on-screen pipeline), shown as is. Checks : geometry computations (bounds, containment, segment counts,
 * curve roots) and exact pixel probes inside solid fills.
 */
@Singleton
public class ShapesPage implements FeaturePage {

    private static final int COLUMNS = 6;
    private static final int TILE_WIDTH = 164;
    private static final int TILE_HEIGHT = 150;
    private static final int SHAPE_SIZE = 110;

    private static final int BACKGROUND = 0xFFF7F9FC;
    private static final int BORDER = 0xFFB0BEC5;
    private static final int INK = 0xFF263238;
    private static final int[] FILLS = { 0xFF4FC3F7, 0xFFFFB74D, 0xFF81C784, 0xFFE57373, 0xFFBA68C8, 0xFF4DB6AC };

    private record Tile(String caption, Consumer<Graphics2D> painter) {
    }

    @Override
    public String id() {
        return "j2d-shapes";
    }

    @Override
    public String title() {
        return "Shapes and geometry";
    }

    @Override
    public String category() {
        return Categories.JAVA2D;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() {
        List<Tile> tiles = tiles();
        int rows = (tiles.size() + COLUMNS - 1) / COLUMNS;
        BufferedImage image = Snapshots.offscreen(COLUMNS * TILE_WIDTH, rows * TILE_HEIGHT, g -> {
            for (int i = 0; i < tiles.size(); i++) {
                Graphics2D tile = (Graphics2D) g.create((i % COLUMNS) * TILE_WIDTH, (i / COLUMNS) * TILE_HEIGHT,
                        TILE_WIDTH, TILE_HEIGHT);
                try {
                    paintTile(tile, tiles.get(i), FILLS[i % FILLS.length]);
                } finally {
                    tile.dispose();
                }
            }
        });

        List<Check> checks = new ArrayList<>(geometryChecks());
        // solid fills are exact : the center of the filled rectangle (tile 2) and of the ellipse (tile 4)
        checks.add(Checks.expect("pixel inside Rectangle2D fill", Checks.argb(FILLS[1]),
                () -> Checks.argb(image.getRGB(TILE_WIDTH + TILE_WIDTH / 2, TILE_HEIGHT / 2 - 10))));
        checks.add(Checks.expect("pixel inside Ellipse2D fill", Checks.argb(FILLS[3]),
                () -> Checks.argb(image.getRGB(3 * TILE_WIDTH + TILE_WIDTH / 2, TILE_HEIGHT / 2 - 10))));
        checks.add(Checks.expect("pixel outside the tiles' shapes", Checks.argb(BACKGROUND),
                () -> Checks.argb(image.getRGB(6, 6))));

        return Ui.column(14,
                Ui.text("java.awt.geom shapes drawn into a TYPE_INT_ARGB image (anti-aliasing on, pure strokes). Red "
                        + "dots are control points, black dots the vertices of a flattened path.", 1000),
                Ui.image(image),
                ChecksView.table("Geometry", checks));
    }

    private static void paintTile(Graphics2D g, Tile tile, int fill) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(BACKGROUND, true));
        g.fillRect(0, 0, TILE_WIDTH, TILE_HEIGHT);
        g.setColor(new Color(BORDER, true));
        g.drawRect(2, 2, TILE_WIDTH - 5, TILE_HEIGHT - 5);

        Graphics2D shape = (Graphics2D) g.create((TILE_WIDTH - SHAPE_SIZE) / 2, 10, SHAPE_SIZE, SHAPE_SIZE);
        try {
            shape.setColor(new Color(fill, true));
            shape.setStroke(new BasicStroke(2f));
            tile.painter().accept(shape);
        } finally {
            shape.dispose();
        }

        g.setColor(new Color(INK, true));
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        int width = g.getFontMetrics().stringWidth(tile.caption());
        g.drawString(tile.caption(), (TILE_WIDTH - width) / 2, TILE_HEIGHT - 14);
    }

    /**
     * Fills {@code shape} with the current color, then strokes it with the ink color.
     */
    private static void fillAndDraw(Graphics2D g, Shape shape) {
        g.fill(shape);
        Color fill = g.getColor();
        g.setColor(new Color(INK, true));
        g.draw(shape);
        g.setColor(fill);
    }

    private static void points(Graphics2D g, int rgb, double... coordinates) {
        Color previous = g.getColor();
        g.setColor(new Color(rgb));
        for (int i = 0; i + 1 < coordinates.length; i += 2) {
            g.fill(new Ellipse2D.Double(coordinates[i] - 3, coordinates[i + 1] - 3, 6, 6));
        }
        g.setColor(previous);
    }

    private static Path2D star(int windingRule) {
        Path2D.Double path = new Path2D.Double(windingRule);
        double cx = 55;
        double cy = 57;
        double r = 52;
        for (int i = 0; i < 5; i++) {
            // every second vertex of a pentagon : a self-intersecting pentagram
            double angle = -Math.PI / 2 + i * 4 * Math.PI / 5;
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    private static Area circle() {
        return new Area(new Ellipse2D.Double(10, 10, 70, 70));
    }

    private static Area square() {
        return new Area(new Rectangle2D.Double(40, 40, 60, 60));
    }

    private static List<Tile> tiles() {
        return List.of(
                new Tile("Line2D", g -> {
                    g.setColor(new Color(INK, true));
                    for (int i = 0; i < 6; i++) {
                        g.setStroke(new BasicStroke(1 + i, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.draw(new Line2D.Double(8 + i * 18, 100, 20 + i * 16, 8 + i * 6));
                    }
                }),
                new Tile("Rectangle2D", g -> fillAndDraw(g, new Rectangle2D.Double(10, 20, 90, 70))),
                new Tile("RoundRectangle2D", g -> fillAndDraw(g, new RoundRectangle2D.Double(8, 15, 94, 80, 30, 20))),
                new Tile("Ellipse2D", g -> fillAndDraw(g, new Ellipse2D.Double(5, 20, 100, 70))),
                new Tile("Arc2D OPEN", g -> fillAndDraw(g, new Arc2D.Double(10, 10, 90, 90, 30, 250, Arc2D.OPEN))),
                new Tile("Arc2D CHORD", g -> fillAndDraw(g, new Arc2D.Double(10, 10, 90, 90, 30, 250, Arc2D.CHORD))),
                new Tile("Arc2D PIE", g -> fillAndDraw(g, new Arc2D.Double(10, 10, 90, 90, 30, 250, Arc2D.PIE))),
                new Tile("QuadCurve2D", g -> {
                    QuadCurve2D curve = new QuadCurve2D.Double(10, 95, 55, -20, 100, 95);
                    fillAndDraw(g, curve);
                    points(g, 0xE53935, 55, 5);
                }),
                new Tile("CubicCurve2D", g -> {
                    CubicCurve2D curve = new CubicCurve2D.Double(5, 60, 30, -10, 80, 130, 105, 50);
                    g.setColor(new Color(INK, true));
                    g.setStroke(new BasicStroke(3f));
                    g.draw(curve);
                    points(g, 0xE53935, 30, 5, 80, 105);
                }),
                new Tile("Polygon", g -> {
                    Polygon polygon = new Polygon();
                    for (int i = 0; i < 7; i++) {
                        double angle = -Math.PI / 2 + i * 2 * Math.PI / 7;
                        double r = i % 2 == 0 ? 50 : 34;
                        polygon.addPoint((int) Math.round(55 + r * Math.cos(angle)),
                                (int) Math.round(57 + r * Math.sin(angle)));
                    }
                    fillAndDraw(g, polygon);
                }),
                new Tile("Path2D WIND_NON_ZERO", g -> fillAndDraw(g, star(Path2D.WIND_NON_ZERO))),
                new Tile("Path2D WIND_EVEN_ODD", g -> fillAndDraw(g, star(Path2D.WIND_EVEN_ODD))),
                new Tile("Area add", g -> {
                    Area area = circle();
                    area.add(square());
                    fillAndDraw(g, area);
                }),
                new Tile("Area subtract", g -> {
                    Area area = circle();
                    area.subtract(square());
                    fillAndDraw(g, area);
                }),
                new Tile("Area intersect", g -> {
                    Area area = circle();
                    area.intersect(square());
                    fillAndDraw(g, area);
                }),
                new Tile("Area exclusiveOr", g -> {
                    Area area = circle();
                    area.exclusiveOr(square());
                    fillAndDraw(g, area);
                }),
                new Tile("flattened Ellipse2D", g -> {
                    Ellipse2D ellipse = new Ellipse2D.Double(5, 20, 100, 70);
                    Path2D flat = new Path2D.Double();
                    flat.append(ellipse.getPathIterator(null, 2.0), false);
                    fillAndDraw(g, flat);
                    double[] coords = new double[6];
                    for (PathIterator it = ellipse.getPathIterator(null, 2.0); !it.isDone(); it.next()) {
                        if (it.currentSegment(coords) != PathIterator.SEG_CLOSE) {
                            points(g, INK, coords[0], coords[1]);
                        }
                    }
                }),
                new Tile("createTransformedShape", g -> {
                    AffineTransform tx = new AffineTransform();
                    tx.translate(55, 55);
                    tx.rotate(Math.toRadians(30));
                    tx.shear(0.3, 0);
                    tx.translate(-35, -25);
                    fillAndDraw(g, tx.createTransformedShape(new Rectangle2D.Double(0, 0, 70, 50)));
                }),
                new Tile("GeneralPath (legacy)", g -> fillAndDraw(g, heart())),
                new Tile("Path2D.append connect / not", g -> {
                    g.setColor(new Color(INK, true));
                    g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(appended(true, 0));
                    g.draw(appended(false, 55));
                    points(g, 0xE53935, 50, 10, 60, 45, 50, 65, 60, 100);
                }),
                new Tile("Area with curved holes", g -> fillAndDraw(g, face())),
                new Tile("Rectangle union / intersection", g -> {
                    Rectangle r1 = new Rectangle(10, 10, 60, 50);
                    Rectangle r2 = new Rectangle(40, 35, 60, 60);
                    g.fill(r1.intersection(r2));
                    g.setColor(new Color(INK, true));
                    g.draw(r1);
                    g.draw(r2);
                    g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 4, 3 },
                            0));
                    g.draw(r1.union(r2));
                }),
                new Tile("FlatteningPathIterator limit 1 / 10", g -> {
                    CubicCurve2D curve = wave();
                    Path2D coarse = new Path2D.Double();
                    coarse.append(new FlatteningPathIterator(curve.getPathIterator(null), 0.1, 1), false);
                    g.setStroke(new BasicStroke(3f));
                    g.draw(coarse);
                    Path2D fine = new Path2D.Double();
                    fine.append(new FlatteningPathIterator(curve.getPathIterator(null), 0.1, 10), false);
                    g.setColor(new Color(INK, true));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(fine);
                    double[] coords = new double[6];
                    for (PathIterator it = coarse.getPathIterator(null); !it.isDone(); it.next()) {
                        it.currentSegment(coords);
                        points(g, 0xE53935, coords[0], coords[1]);
                    }
                }),
                new Tile("Area.createTransformedArea", g -> {
                    Area area = circle();
                    area.add(square());
                    fillAndDraw(g, area.createTransformedArea(rotation()));
                }),
                new Tile("contains / intersects grid", g -> {
                    Ellipse2D ellipse = new Ellipse2D.Double(5, 15, 100, 80);
                    for (int y = 1; y < 109; y += 12) {
                        for (int x = 1; x < 109; x += 12) {
                            g.setColor(new Color(ellipse.contains(x, y, 12, 12) ? 0xFF81C784
                                    : ellipse.intersects(x, y, 12, 12) ? 0xFFFFE082 : 0xFFECEFF1, true));
                            g.fillRect(x, y, 11, 11);
                        }
                    }
                    g.setColor(new Color(INK, true));
                    g.draw(ellipse);
                }),
                new Tile("Line2D.relativeCCW", g -> {
                    Line2D line = new Line2D.Double(10, 90, 100, 20);
                    for (int y = 5; y < 110; y += 10) {
                        for (int x = 5; x < 110; x += 10) {
                            int ccw = line.relativeCCW(x, y);
                            points(g, ccw < 0 ? 0xE53935 : ccw > 0 ? 0x1E88E5 : INK, x, y);
                        }
                    }
                    g.setColor(new Color(INK, true));
                    g.setStroke(new BasicStroke(2f));
                    g.draw(line);
                }),
                new Tile("RoundRectangle2D arc sizes", g -> {
                    double[][] arcs = { { 0, 0 }, { 20, 10 }, { 40, 40 }, { 50, 50 } };
                    for (int i = 0; i < arcs.length; i++) {
                        fillAndDraw(g, new RoundRectangle2D.Double(3 + (i % 2) * 56, 3 + (i / 2) * 56, 48, 48, arcs[i][0],
                                arcs[i][1]));
                    }
                }),
                new Tile("Arc2D extents -90, 450, -300", g -> {
                    fillAndDraw(g, new Arc2D.Double(0, 0, 60, 60, 0, -90, Arc2D.PIE));
                    Color fill = g.getColor();
                    g.setColor(new Color(INK, true));
                    g.setStroke(new BasicStroke(3f));
                    g.draw(new Arc2D.Double(55, 5, 50, 50, 30, 450, Arc2D.OPEN));
                    g.setColor(fill);
                    fillAndDraw(g, new Arc2D.Double(20, 50, 70, 58, 200, -300, Arc2D.CHORD));
                }),
                new Tile("CubicCurve2D.subdivide", g -> {
                    CubicCurve2D left = new CubicCurve2D.Double();
                    CubicCurve2D right = new CubicCurve2D.Double();
                    wave().subdivide(left, right);
                    g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(left);
                    g.setColor(new Color(INK, true));
                    g.draw(right);
                    g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 3, 3 },
                            0));
                    for (CubicCurve2D c : new CubicCurve2D[] { left, right }) {
                        Path2D polygon = new Path2D.Double();
                        polygon.moveTo(c.getX1(), c.getY1());
                        polygon.lineTo(c.getCtrlX1(), c.getCtrlY1());
                        polygon.lineTo(c.getCtrlX2(), c.getCtrlY2());
                        polygon.lineTo(c.getX2(), c.getY2());
                        g.draw(polygon);
                    }
                    points(g, 0xE53935, left.getX2(), left.getY2());
                }),
                new Tile("QuadCurve2D.subdivide", g -> {
                    QuadCurve2D quad = new QuadCurve2D.Double(5, 100, 55, -40, 105, 100);
                    QuadCurve2D left = new QuadCurve2D.Double();
                    QuadCurve2D right = new QuadCurve2D.Double();
                    quad.subdivide(left, right);
                    g.setStroke(new BasicStroke(4f));
                    g.draw(left);
                    g.setColor(new Color(INK, true));
                    g.draw(right);
                    Path2D copy = new Path2D.Double(quad);
                    copy.transform(AffineTransform.getScaleInstance(0.5, 0.5));
                    copy.transform(AffineTransform.getTranslateInstance(28, 50));
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(copy);
                }));
    }

    /** Rotation by 45 degrees and scale 0.8 around (55, 55). */
    private static AffineTransform rotation() {
        AffineTransform tx = AffineTransform.getTranslateInstance(55, 55);
        tx.scale(0.8, 0.8);
        tx.rotate(Math.toRadians(45));
        tx.translate(-55, -55);
        return tx;
    }

    /** A heart with a hole (even-odd rule). */
    private static GeneralPath heart() {
        GeneralPath path = new GeneralPath(Path2D.WIND_EVEN_ODD);
        path.moveTo(55, 104);
        path.curveTo(-12, 52, 18, -8, 55, 30);
        path.curveTo(92, -8, 122, 52, 55, 104);
        path.closePath();
        path.moveTo(40, 40);
        path.quadTo(55, 20, 70, 40);
        path.quadTo(55, 60, 40, 40);
        path.closePath();
        return path;
    }

    private static Path2D appended(boolean connect, double dy) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(5, 10 + dy);
        path.lineTo(50, 10 + dy);
        path.append(new Line2D.Double(60, 45 + dy, 105, 10 + dy), connect);
        return path;
    }

    private static Area face() {
        Area face = new Area(new Ellipse2D.Double(5, 5, 100, 100));
        face.subtract(new Area(new Ellipse2D.Double(28, 30, 18, 22)));
        face.subtract(new Area(new Ellipse2D.Double(64, 30, 18, 22)));
        face.subtract(new Area(new Arc2D.Double(25, 35, 60, 50, 200, 140, Arc2D.CHORD)));
        return face;
    }

    private static CubicCurve2D wave() {
        return new CubicCurve2D.Double(5, 100, 25, -20, 85, 130, 105, 10);
    }

    private static List<Check> geometryChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("Area add : bounds", "10.00,10.00 90.00x90.00", () -> {
            Area area = circle();
            area.add(square());
            return Checks.bounds(area);
        }));
        checks.add(Checks.expect("Area subtract : singular, rectangular", "true, false", () -> {
            Area area = circle();
            area.subtract(square());
            return area.isSingular() + ", " + area.isRectangular();
        }));
        checks.add(Checks.expect("Area intersect : bounds", "40.00,40.00 40.00x40.00", () -> {
            Area area = circle();
            area.intersect(square());
            return Checks.bounds(area);
        }));
        checks.add(Checks.expect("Area exclusiveOr : contains (45, 45) / (20, 45)", "false / true", () -> {
            Area area = circle();
            area.exclusiveOr(square());
            return area.contains(45, 45) + " / " + area.contains(20, 45);
        }));
        checks.add(Checks.expect("Path2D pentagram : contains center (NON_ZERO / EVEN_ODD)", "true / false",
                () -> star(Path2D.WIND_NON_ZERO).contains(55, 57) + " / " + star(Path2D.WIND_EVEN_ODD).contains(55, 57)));
        checks.add(Checks.expect("Ellipse2D path segments", "MOVETO CUBICTO CUBICTO CUBICTO CUBICTO CLOSE",
                () -> segments(new Ellipse2D.Double(0, 0, 100, 50).getPathIterator(null))));
        checks.add(Checks.info("Ellipse2D flattened segments (flatness 0.5)",
                () -> count(new Ellipse2D.Double(0, 0, 100, 50).getPathIterator(null, 0.5))));
        checks.add(Checks.expect("QuadCurve2D flatness", "115.000",
                () -> Checks.num(new QuadCurve2D.Double(10, 95, 55, -20, 100, 95).getFlatness())));
        checks.add(Checks.expect("CubicCurve2D.solveCubic(x^3 - 6x^2 + 11x - 6)", "1.000000 2.000000 3.000000", () -> {
            double[] eqn = { -6, 11, -6, 1 };
            int n = CubicCurve2D.solveCubic(eqn);
            double[] roots = Arrays.copyOf(eqn, n);
            Arrays.sort(roots);
            return String.join(" ", Arrays.stream(roots).mapToObj(r -> Checks.num(r, 6)).toList());
        }));
        checks.add(Checks.expect("Line2D intersectsLine / ptSegDist", "true / 5.000", () -> {
            Line2D a = new Line2D.Double(0, 0, 10, 10);
            return a.intersectsLine(0, 10, 10, 0) + " / " + Checks.num(new Line2D.Double(0, 0, 10, 0).ptSegDist(5, 5));
        }));
        checks.add(Checks.expect("Arc2D PIE : bounds, containsAngle(45 / 300)", "10.00,10.00 90.00x90.00, true / false",
                () -> {
                    Arc2D arc = new Arc2D.Double(10, 10, 90, 90, 30, 250, Arc2D.PIE);
                    return Checks.bounds(arc.getFrame()) + ", " + arc.containsAngle(45) + " / " + arc.containsAngle(300);
                }));
        checks.add(Checks.expect("Arc2D OPEN : start / end point", "93.97,32.50 / 62.81,99.32", () -> {
            Arc2D arc = new Arc2D.Double(10, 10, 90, 90, 30, 250, Arc2D.OPEN);
            return point(arc.getStartPoint()) + " / " + point(arc.getEndPoint());
        }));
        checks.add(Checks.expect("RoundRectangle2D contains its corner", "false",
                () -> new RoundRectangle2D.Double(0, 0, 100, 80, 30, 20).contains(1, 1)));
        checks.add(Checks.expect("Rectangle2D outcode / intersection", "3 / 50.00,50.00 50.00x50.00", () -> {
            Rectangle2D r = new Rectangle2D.Double(0, 0, 100, 100);
            return r.outcode(-5, -5) + " / " + Checks.bounds(r.createIntersection(new Rectangle2D.Double(50, 50, 80, 80)));
        }));
        checks.add(Checks.expect("Polygon contains / bounds", "true / 0,0 100x80", () -> {
            Polygon p = new Polygon(new int[] { 0, 100, 50 }, new int[] { 80, 80, 0 }, 3);
            return p.contains(50, 50) + " / " + p.getBounds().x + "," + p.getBounds().y + " " + p.getBounds().width + "x"
                    + p.getBounds().height;
        }));
        checks.add(Checks.expect("AffineTransform.createTransformedShape : bounds", "-37.50,0.00 80.80x89.95", () -> {
            AffineTransform tx = AffineTransform.getRotateInstance(Math.toRadians(30));
            return Checks.bounds(tx.createTransformedShape(new Rectangle2D.Double(0, 0, 50, 75)));
        }));
        checks.add(Checks.expect("BasicStroke.createStrokedShape : bounds", "-2.00,-2.00 104.00x4.00",
                () -> Checks.bounds(new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
                        .createStrokedShape(new Line2D.Double(0, 0, 100, 0)))));
        checks.add(Checks.expect("GeneralPath : a Path2D.Float, default winding rule, hole", "true, WIND_NON_ZERO, false",
                () -> (heart() instanceof Path2D.Float) + ", "
                        + (new GeneralPath().getWindingRule() == Path2D.WIND_NON_ZERO ? "WIND_NON_ZERO" : "WIND_EVEN_ODD")
                        + ", " + heart().contains(55, 40)));
        checks.add(Checks.expect("Path2D.append connect / not : segments",
                "MOVETO LINETO LINETO LINETO / MOVETO LINETO MOVETO LINETO",
                () -> segments(appended(true, 0).getPathIterator(null)) + " / "
                        + segments(appended(false, 0).getPathIterator(null))));
        checks.add(Checks.expect("Area with curved holes : singular / polygonal / rectangular", "false / false / false",
                () -> face().isSingular() + " / " + face().isPolygonal() + " / " + face().isRectangular()));
        checks.add(Checks.expect("Area with curved holes : contains an eye center / the cheek", "false / true",
                () -> face().contains(37, 41) + " / " + face().contains(20, 60)));
        checks.add(Checks.expect("Rectangle union / intersection / intersects", "10,10 90x85 / 40,35 30x25 / true", () -> {
            Rectangle r1 = new Rectangle(10, 10, 60, 50);
            Rectangle r2 = new Rectangle(40, 35, 60, 60);
            return rect(r1.union(r2)) + " / " + rect(r1.intersection(r2)) + " / " + r1.intersects(r2);
        }));
        checks.add(Checks.expect("Rectangle intersection of disjoint rectangles : bounds, isEmpty", "30,0 -10x10, true",
                () -> {
                    Rectangle r = new Rectangle(0, 0, 20, 10).intersection(new Rectangle(30, 0, 20, 10));
                    return rect(r) + ", " + r.isEmpty();
                }));
        checks.add(Checks.expect("Rectangle.add(point) / grow(2, 3)", "0,0 50x30 / -2,-3 24x16", () -> {
            Rectangle a = new Rectangle(0, 0, 20, 10);
            a.add(new java.awt.Point(50, 30));
            Rectangle b = new Rectangle(0, 0, 20, 10);
            b.grow(2, 3);
            return rect(a) + " / " + rect(b);
        }));
        checks.add(Checks.expect("FlatteningPathIterator(flatness 0.1) limit 1 / 10 : segments", "3 / 45", () ->
                count(new FlatteningPathIterator(wave().getPathIterator(null), 0.1, 1)) + " / "
                        + count(new FlatteningPathIterator(wave().getPathIterator(null), 0.1, 10))));
        checks.add(Checks.expect("Area.createTransformedArea(rotate 45, scale 0.8) : bounds", "21.06,15.69 67.88x90.23", () -> {
            Area area = circle();
            area.add(square());
            return Checks.bounds(area.createTransformedArea(rotation()));
        }));
        checks.add(Checks.expect("Ellipse2D contains / intersects 12 px cells (of 81)", "31 / 59", () -> {
            Ellipse2D ellipse = new Ellipse2D.Double(5, 15, 100, 80);
            int contains = 0;
            int intersects = 0;
            for (int y = 1; y < 109; y += 12) {
                for (int x = 1; x < 109; x += 12) {
                    contains += ellipse.contains(x, y, 12, 12) ? 1 : 0;
                    intersects += ellipse.intersects(x, y, 12, 12) ? 1 : 0;
                }
            }
            return contains + " / " + intersects;
        }));
        checks.add(Checks.expect("Line2D(10, 90, 100, 20).relativeCCW (10, 20) / (55, 55) / (100, 90)", "1 / 0 / -1",
                () -> {
                    Line2D line = new Line2D.Double(10, 90, 100, 20);
                    return line.relativeCCW(10, 20) + " / " + line.relativeCCW(55, 55) + " / " + line.relativeCCW(100, 90);
                }));
        checks.add(Checks.expect("Arc2D extent -90 / 450 : extent, containsAngle(315 / 45)",
                "-90.0 true / 450.0 true", () -> {
            Arc2D a = new Arc2D.Double(0, 0, 60, 60, 0, -90, Arc2D.PIE);
            Arc2D b = new Arc2D.Double(55, 5, 50, 50, 30, 450, Arc2D.OPEN);
            return Checks.num(a.getAngleExtent(), 1) + " " + a.containsAngle(315) + " / " + Checks.num(b.getAngleExtent(), 1)
                    + " " + b.containsAngle(45);
        }));
        checks.add(Checks.expect("CubicCurve2D.subdivide : split point / halves meet", "55.00,55.00 / true", () -> {
            CubicCurve2D left = new CubicCurve2D.Double();
            CubicCurve2D right = new CubicCurve2D.Double();
            wave().subdivide(left, right);
            return point(left.getP2()) + " / " + left.getP2().equals(right.getP1());
        }));
        checks.add(Checks.expect("QuadCurve2D.solveQuadratic(x^2 - 5x + 6)", "2.000000 3.000000", () -> {
            double[] eqn = { 6, -5, 1 };
            int n = QuadCurve2D.solveQuadratic(eqn);
            double[] roots = Arrays.copyOf(eqn, n);
            Arrays.sort(roots);
            return String.join(" ", Arrays.stream(roots).mapToObj(r -> Checks.num(r, 6)).toList());
        }));
        checks.add(Checks.expect("Point2D.distance((0, 0), (3, 4)) / Line2D.ptLineDist", "5.000 / 0.000",
                () -> Checks.num(Point2D.distance(0, 0, 3, 4)) + " / "
                        + Checks.num(new Line2D.Double(10, 90, 100, 20).ptLineDist(55, 55))));
        checks.add(Checks.expect("Dimension.setSize(2.5, 3.5) (ceil)", "3x4", () -> {
            java.awt.Dimension d = new java.awt.Dimension();
            d.setSize(2.5, 3.5);
            return d.width + "x" + d.height;
        }));
        return checks;
    }

    private static String rect(Rectangle r) {
        return r.x + "," + r.y + " " + r.width + "x" + r.height;
    }

    private static String point(Point2D p) {
        return Checks.num(p.getX(), 2) + "," + Checks.num(p.getY(), 2);
    }

    private static String segments(PathIterator it) {
        List<String> names = new ArrayList<>();
        double[] coords = new double[6];
        for (; !it.isDone(); it.next()) {
            names.add(switch (it.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO -> "MOVETO";
                case PathIterator.SEG_LINETO -> "LINETO";
                case PathIterator.SEG_QUADTO -> "QUADTO";
                case PathIterator.SEG_CUBICTO -> "CUBICTO";
                default -> "CLOSE";
            });
        }
        return String.join(" ", names);
    }

    private static int count(PathIterator it) {
        int n = 0;
        for (double[] coords = new double[6]; !it.isDone(); it.next()) {
            it.currentSegment(coords);
            n++;
        }
        return n;
    }
}
