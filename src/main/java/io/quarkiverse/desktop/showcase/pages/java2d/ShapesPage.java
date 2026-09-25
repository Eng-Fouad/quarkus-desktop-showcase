package io.quarkiverse.desktop.showcase.pages.java2d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
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
 * The shapes of {@code java.awt.geom} : lines, rectangles, ellipses, arcs, curves, polygons, paths with both winding
 * rules, constructive area geometry, flattening and transformed shapes.
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
                }));
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
        return checks;
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
