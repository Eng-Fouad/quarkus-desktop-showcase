package io.quarkiverse.desktop.showcase.pages.print;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Snapshots;

/**
 * The document printed by the printing pages : a {@link Book} of 5 pages with 4 {@link Printable}s and 3 paper sizes
 * (Letter portrait, Letter landscape, A6 reverse landscape, A4 portrait twice).
 * <p>
 * The pages use text in the logical fonts, shapes, strokes and an opaque image (printed as PostScript paths, text and
 * images), and one page uses gradients and translucent colors (printed as rasterized bands).
 * <p>
 * No AWT object is kept in a static field : colors are {@code int} RGB constants (application classes are initialized at
 * build time in a native executable).
 */
public final class SampleBook {

    /** Number of pages of {@link #book}. */
    public static final int PAGES = 5;

    static final int INK = 0x263238;
    static final int MUTED = 0x607D8B;
    static final int FRAME = 0xB0BEC5;
    static final int BLUE = 0x1E88E5;
    static final int RED = 0xE53935;
    static final int YELLOW = 0xFDD835;
    static final int GREEN = 0x43A047;
    static final int PURPLE = 0x8E24AA;
    static final int ORANGE = 0xFB8C00;
    static final int[] PALETTE = { BLUE, RED, YELLOW, GREEN, PURPLE, ORANGE, 0x00ACC1, 0x6D4C41 };

    /** The solid blue square of the cover (Letter portrait) : page coordinates of its top left corner and its size. */
    static final int COVER_SQUARE_X = 96;
    static final int COVER_SQUARE_Y = 330;
    static final int COVER_SQUARE_SIZE = 80;

    private SampleBook() {
    }

    // ---------------------------------------------------------------------------------------------------------- paper

    /**
     * US Letter, 1 inch margins (the default {@link Paper}).
     */
    public static Paper letter() {
        return new Paper();
    }

    /**
     * ISO A6 (105 x 148 mm), 1/4 inch margins.
     */
    public static Paper a6() {
        Paper paper = new Paper();
        paper.setSize(297.64, 419.53);
        paper.setImageableArea(18, 18, 297.64 - 36, 419.53 - 36);
        return paper;
    }

    /**
     * ISO A4 (210 x 297 mm), 1/2 inch margins.
     */
    public static Paper a4() {
        Paper paper = new Paper();
        paper.setSize(595.28, 841.89);
        paper.setImageableArea(36, 36, 595.28 - 72, 841.89 - 72);
        return paper;
    }

    public static PageFormat format(Paper paper, int orientation) {
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(orientation);
        return format;
    }

    /**
     * The 5 pages : cover (Letter portrait), chart (Letter landscape), gradients (A6 reverse landscape), sequence pages
     * 1 and 2 (A4 portrait, one {@link Printable} appended for 2 pages).
     */
    public static Book book(PrintLog log) {
        Book book = new Book();
        book.append(new Cover(log), format(letter(), PageFormat.PORTRAIT));
        book.append(new Chart(log), format(letter(), PageFormat.LANDSCAPE));
        book.append(new Gradients(log), format(a6(), PageFormat.REVERSE_LANDSCAPE));
        book.append(new Sequence(log, 3, 2), format(a4(), PageFormat.PORTRAIT), 2);
        return book;
    }

    /**
     * {@code page} of {@code book} drawn "as on paper" : the paper (portrait) scaled by {@code scale}, the page
     * transformed by {@link PageFormat#getMatrix()} (landscape pages appear rotated) and clipped to the imageable area,
     * as a printer job does.
     */
    public static BufferedImage renderOnPaper(Book book, int page, double scale) {
        PageFormat format = book.getPageFormat(page);
        Printable printable = book.getPrintable(page);
        Paper paper = format.getPaper();
        int width = (int) Math.ceil(paper.getWidth() * scale);
        int height = (int) Math.ceil(paper.getHeight() * scale);
        return Snapshots.offscreen(width, height, g -> {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            Graphics2D pg = (Graphics2D) g.create();
            try {
                pg.scale(scale, scale);
                pg.transform(new AffineTransform(format.getMatrix()));
                pg.clip(new Rectangle2D.Double(format.getImageableX(), format.getImageableY(),
                        format.getImageableWidth(), format.getImageableHeight()));
                printable.print(pg, format, page);
            } catch (PrinterException e) {
                throw new IllegalStateException(e);
            } finally {
                pg.dispose();
            }
            g.setColor(new Color(FRAME));
            g.drawRect(0, 0, width - 1, height - 1);
        });
    }

    // ------------------------------------------------------------------------------------------------------ call log

    /**
     * The calls of {@link Printable#print} during a print job : the printer job calls it several times per page, with
     * different graphics ({@code PeekGraphics} to analyze the page, then {@code PSPathGraphics} to print it as paths, or
     * {@code ProxyGraphics2D} once per rasterized band). Thread safe (print jobs run on a background thread).
     */
    public static final class PrintLog {

        private final Map<Integer, List<String>> calls = new TreeMap<>();
        private String printerGraphics;

        synchronized void record(Graphics g, PageFormat format, int page) {
            calls.computeIfAbsent(page, p -> new ArrayList<>()).add(g.getClass().getSimpleName());
            if (printerGraphics == null && g instanceof PrinterGraphics pg && g instanceof Graphics2D g2
                    && !g.getClass().getSimpleName().startsWith("Peek")) {
                GraphicsConfiguration gc = g2.getDeviceConfiguration();
                printerGraphics = g.getClass().getSimpleName() + " of " + pg.getPrinterJob().getClass().getSimpleName()
                        + ", device type " + (gc == null ? "none" : deviceType(gc.getDevice().getType()))
                        + ", device bounds " + (gc == null ? "none" : Checks.bounds(gc.getBounds()))
                        + ", transform " + PrintSupport.matrix(g2.getTransform());
            }
        }

        private static String deviceType(int type) {
            return switch (type) {
                case java.awt.GraphicsDevice.TYPE_PRINTER -> "TYPE_PRINTER";
                case java.awt.GraphicsDevice.TYPE_IMAGE_BUFFER -> "TYPE_IMAGE_BUFFER";
                case java.awt.GraphicsDevice.TYPE_RASTER_SCREEN -> "TYPE_RASTER_SCREEN";
                default -> String.valueOf(type);
            };
        }

        /**
         * {@code page: graphics, graphics...} per page (1-based), identical consecutive graphics counted.
         */
        public synchronized String calls() {
            List<String> pages = new ArrayList<>();
            calls.forEach((page, names) -> {
                List<String> compact = new ArrayList<>();
                String previous = null;
                int count = 0;
                for (String name : names) {
                    if (name.equals(previous)) {
                        count++;
                        continue;
                    }
                    if (previous != null) {
                        compact.add(count > 1 ? previous + " x" + count : previous);
                    }
                    previous = name;
                    count = 1;
                }
                if (previous != null) {
                    compact.add(count > 1 ? previous + " x" + count : previous);
                }
                pages.add((page + 1) + ": " + String.join(", ", compact));
            });
            return String.join(" | ", pages);
        }

        /**
         * The first printer graphics seen (not the peek graphics) : class, printer job, device and initial transform.
         */
        public synchronized String printerGraphics() {
            return printerGraphics == null ? "none" : printerGraphics;
        }
    }

    // -------------------------------------------------------------------------------------------------------- pages

    private abstract static class Page implements Printable {

        private final PrintLog log;
        final String name;

        Page(PrintLog log, String name) {
            this.log = log;
            this.name = name;
        }

        @Override
        public int print(Graphics graphics, PageFormat format, int page) throws PrinterException {
            if (!exists(page)) {
                return NO_SUCH_PAGE;
            }
            if (log != null) {
                log.record(graphics, format, page);
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                double x = format.getImageableX();
                double y = format.getImageableY();
                double w = format.getImageableWidth();
                double h = format.getImageableHeight();
                g.setColor(new Color(FRAME));
                g.setStroke(new BasicStroke(1f));
                g.draw(new Rectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1));
                paint(g, format, page, x, y, w, h);
                g.setColor(new Color(MUTED));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                g.drawString(footer(format, page), (float) (x + 6), (float) (y + h - 6));
            } finally {
                g.dispose();
            }
            return PAGE_EXISTS;
        }

        boolean exists(int page) {
            return true;
        }

        String footer(PageFormat format, int page) {
            return name + " - page " + (page + 1) + " - " + Checks.num(format.getPaper().getWidth(), 0) + " x "
                    + Checks.num(format.getPaper().getHeight(), 0) + " pt - "
                    + PrintSupport.orientation(format.getOrientation());
        }

        abstract void paint(Graphics2D g, PageFormat format, int page, double x, double y, double w, double h);
    }

    /** Text in the logical fonts, shapes, strokes and an opaque image. */
    static final class Cover extends Page {

        Cover(PrintLog log) {
            super(log, "Cover");
        }

        @Override
        void paint(Graphics2D g, PageFormat format, int page, double x, double y, double w, double h) {
            float left = (float) x + 24;
            g.setColor(new Color(INK));
            g.setFont(new Font(Font.SERIF, Font.BOLD, 30));
            g.drawString("Printing with Java2D", left, (float) y + 60);
            g.setColor(new Color(MUTED));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString("java.awt.print : Printable, Pageable, Book, PageFormat, Paper", left, (float) y + 86);
            g.setColor(new Color(BLUE));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Line2D.Double(left, y + 100, x + w - 24, y + 100));

            g.setColor(new Color(INK));
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 12));
            String[] paragraph = {
                    "A Book associates a Printable and a PageFormat with every page. The printer job asks each",
                    "Printable to render its pages, several times per page : once to analyze what is drawn, then",
                    "to print it as PostScript paths and text, or band by band when the page needs rasterizing." };
            for (int i = 0; i < paragraph.length; i++) {
                g.drawString(paragraph[i], left, (float) y + 128 + i * 16);
            }
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            g.drawString("PrinterJob job = PrinterJob.getPrinterJob(); job.setPageable(book);", left, (float) y + 190);
            g.setFont(new Font(Font.DIALOG, Font.ITALIC, 12));
            g.drawString("Dialog italic, ", left, (float) y + 214);
            g.setFont(new Font(Font.DIALOG_INPUT, Font.BOLD, 12));
            g.drawString("DialogInput bold", left + 90, (float) y + 214);

            // shapes : solid fills (exact pixels in the rendered images), strokes, a dashed outline
            g.setColor(new Color(BLUE));
            g.fill(new Rectangle2D.Double(left, COVER_SQUARE_Y, COVER_SQUARE_SIZE, COVER_SQUARE_SIZE));
            g.setColor(new Color(RED));
            g.fill(new Ellipse2D.Double(x + 130, COVER_SQUARE_Y, 110, 80));
            g.setColor(new Color(INK));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 6, 4 }, 0));
            g.draw(new RoundRectangle2D.Double(x + 256, COVER_SQUARE_Y, 90, 80, 24, 24));
            Polygon star = new Polygon();
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                double r = i % 2 == 0 ? 42 : 18;
                star.addPoint((int) Math.round(x + 410 + r * Math.cos(angle)),
                        (int) Math.round(COVER_SQUARE_Y + 42 + r * Math.sin(angle)));
            }
            g.setColor(new Color(YELLOW));
            g.fill(star);
            g.setColor(new Color(INK));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(star);
            for (int i = 0; i < 5; i++) {
                g.setStroke(new BasicStroke(1 + 2 * i, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(PALETTE[i]));
                g.draw(new Line2D.Double(left + i * 90, y + 460, left + i * 90 + 60, y + 430));
            }

            // an opaque image (printed with the PostScript colorimage operator, not rasterized)
            g.drawImage(pattern(), (int) left, (int) (y + 490), 96, 96, null);
            g.setColor(new Color(INK));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.drawString("TYPE_INT_RGB image, 48 x 48 pixels drawn at 96 x 96 points", left + 110, (float) y + 540);
        }

        /**
         * A deterministic 48 x 48 opaque image : checkerboard and circles.
         */
        static BufferedImage pattern() {
            BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
            for (int py = 0; py < 48; py++) {
                for (int px = 0; px < 48; px++) {
                    int dx = px - 24;
                    int dy = py - 24;
                    int ring = (int) Math.sqrt(dx * dx + dy * dy) / 6;
                    boolean check = ((px / 8) + (py / 8)) % 2 == 0;
                    image.setRGB(px, py, ring < 4 ? PALETTE[ring] : check ? 0xFFFFFF : 0xCFD8DC);
                }
            }
            return image;
        }
    }

    /** A bar chart on a landscape page, with rotated text. */
    static final class Chart extends Page {

        private static final int[] VALUES = { 42, 67, 55, 81, 38, 92, 74, 60 };

        Chart(PrintLog log) {
            super(log, "Chart");
        }

        @Override
        void paint(Graphics2D g, PageFormat format, int page, double x, double y, double w, double h) {
            g.setColor(new Color(INK));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.drawString("Bar chart on a landscape page", (float) x + 24, (float) y + 40);
            double left = x + 80;
            double bottom = y + h - 70;
            double top = y + 70;
            double barWidth = (w - 140) / VALUES.length;
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            for (int v = 0; v <= 100; v += 20) {
                double gy = bottom - (bottom - top) * v / 100;
                g.setColor(new Color(FRAME));
                g.draw(new Line2D.Double(left, gy, left + barWidth * VALUES.length, gy));
                g.setColor(new Color(MUTED));
                g.drawString(String.valueOf(v), (float) left - 26, (float) gy + 4);
            }
            for (int i = 0; i < VALUES.length; i++) {
                double bx = left + i * barWidth + 8;
                double bh = (bottom - top) * VALUES[i] / 100;
                g.setColor(new Color(PALETTE[i % PALETTE.length]));
                g.fill(new Rectangle2D.Double(bx, bottom - bh, barWidth - 16, bh));
                g.setColor(new Color(INK));
                g.drawString("Q" + (i % 4 + 1) + "/" + (i / 4 + 1), (float) bx + 4, (float) bottom + 16);
                g.drawString(String.valueOf(VALUES[i]), (float) bx + 4, (float) (bottom - bh - 4));
            }
            g.setColor(new Color(INK));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Line2D.Double(left, top - 10, left, bottom));
            g.draw(new Line2D.Double(left, bottom, left + barWidth * VALUES.length + 10, bottom));
            // rotated axis title : text under a rotation, printed with a rotated PostScript font
            Graphics2D rotated = (Graphics2D) g.create();
            try {
                rotated.translate(x + 30, (top + bottom) / 2 + 40);
                rotated.rotate(-Math.PI / 2);
                rotated.setFont(new Font(Font.SERIF, Font.ITALIC, 14));
                rotated.drawString("units sold", 0f, 0f);
            } finally {
                rotated.dispose();
            }
        }
    }

    /** Gradients, translucent colors and an image with alpha : the page is printed as rasterized bands. */
    static final class Gradients extends Page {

        Gradients(PrintLog log) {
            super(log, "Gradients");
        }

        @Override
        void paint(Graphics2D g, PageFormat format, int page, double x, double y, double w, double h) {
            g.setColor(new Color(INK));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.drawString("Rasterized page", (float) x + 12, (float) y + 24);
            g.setPaint(new GradientPaint((float) x + 12, 0, new Color(BLUE), (float) (x + 132), 0, new Color(YELLOW)));
            g.fill(new Rectangle2D.Double(x + 12, y + 36, 120, 70));
            g.setPaint(new LinearGradientPaint(new Point2D.Double(x + 144, y + 36), new Point2D.Double(x + 264, y + 106),
                    new float[] { 0f, 0.5f, 1f }, new Color[] { new Color(RED), new Color(GREEN), new Color(PURPLE) },
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fill(new RoundRectangle2D.Double(x + 144, y + 36, 120, 70, 16, 16));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x + 330, y + 71), 40f,
                    new Point2D.Double(x + 318, y + 60), new float[] { 0f, 1f },
                    new Color[] { Color.WHITE, new Color(ORANGE) }, MultipleGradientPaint.CycleMethod.REFLECT));
            g.fill(new Ellipse2D.Double(x + 290, y + 31, 80, 80));
            for (int i = 0; i < 3; i++) {
                g.setColor(new Color(PALETTE[i] | 0x90000000, true));
                g.fill(new Ellipse2D.Double(x + 30 + i * 45, y + 125, 90, 90));
            }
            BufferedImage alpha = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < 64; py++) {
                for (int px = 0; px < 64; px++) {
                    alpha.setRGB(px, py, ((px * 4) << 24) | PALETTE[(py / 16) % PALETTE.length]);
                }
            }
            g.drawImage(alpha, (int) x + 250, (int) y + 138, 96, 96, null);
        }
    }

    /**
     * A {@link Printable} of {@code count} pages starting at page index {@code first} (pages of a book have the index
     * of the book : 3 and 4 here ; printed alone, 0, 1 and 2 until {@link Printable#NO_SUCH_PAGE}).
     */
    public static final class Sequence extends Page {

        private final int first;
        private final int count;

        public Sequence(PrintLog log, int first, int count) {
            super(log, "Sequence");
            this.first = first;
            this.count = count;
        }

        @Override
        boolean exists(int page) {
            return page >= first && page < first + count;
        }

        @Override
        void paint(Graphics2D g, PageFormat format, int page, double x, double y, double w, double h) {
            int number = page - first + 1;
            g.setColor(new Color(INK));
            g.setFont(new Font(Font.SERIF, Font.BOLD, 120));
            String text = String.valueOf(number);
            g.drawString(text, (float) (x + w / 2 - g.getFontMetrics().stringWidth(text) / 2.0), (float) y + 170);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString("Printable page index " + page + ", page " + number + " of " + count, (float) x + 24,
                    (float) y + 210);
            double cell = (w - 48) / 8;
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 8; col++) {
                    g.setColor(new Color(PALETTE[(row + col + page) % PALETTE.length]));
                    g.fill(new Rectangle2D.Double(x + 24 + col * cell + 3, y + 240 + row * cell + 3, cell - 6, cell - 6));
                }
            }
        }
    }

    /** Throws a {@link PrinterException} when asked for its second page. */
    public static final class Failing extends Page {

        public Failing() {
            super(null, "Failing");
        }

        @Override
        public int print(Graphics graphics, PageFormat format, int page) throws PrinterException {
            if (page == 1) {
                throw new PrinterException("simulated failure on page 2");
            }
            return page > 1 ? NO_SUCH_PAGE : super.print(graphics, format, page);
        }

        @Override
        void paint(Graphics2D g, PageFormat format, int page, double x, double y, double w, double h) {
            g.setColor(new Color(RED));
            g.fill(new Rectangle2D.Double(x + 20, y + 20, 100, 100));
        }
    }
}
