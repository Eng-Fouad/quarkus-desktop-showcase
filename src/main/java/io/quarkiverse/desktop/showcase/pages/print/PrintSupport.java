package io.quarkiverse.desktop.showcase.pages.print;

import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.print.DocFlavor;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;

import io.quarkiverse.desktop.showcase.core.Checks;

/**
 * Helpers of the printing pages : the PostScript stream print service (the only print service the showcase prints to :
 * the default print service of the machine may be a real printer), the analysis of the PostScript it produces, and the
 * deterministic formatting of page formats.
 * <p>
 * AWT only ({@code java.awt.print}, {@code javax.print}).
 */
public final class PrintSupport {

    /** MIME type of the stream print services the showcase uses. */
    public static final String POSTSCRIPT = "application/postscript";

    private PrintSupport() {
    }

    /**
     * A PostScript stream print service writing into {@code out} ({@code sun.print.PSStreamPrinterFactory}, found through
     * the {@code javax.print.StreamPrintServiceFactory} service providers of the {@code java.desktop} module).
     */
    public static StreamPrintService postScriptService(ByteArrayOutputStream out) {
        StreamPrintServiceFactory[] factories = StreamPrintServiceFactory
                .lookupStreamPrintServiceFactories(DocFlavor.SERVICE_FORMATTED.PAGEABLE, POSTSCRIPT);
        if (factories.length == 0) {
            throw new IllegalStateException("No PostScript stream print service factory");
        }
        return factories[0].getPrintService(out);
    }

    /**
     * {@code WxH imageable X,Y WxH} of {@code format} (its orientation applied), 2 decimals.
     */
    public static String describe(PageFormat format) {
        return Checks.num(format.getWidth(), 2) + "x" + Checks.num(format.getHeight(), 2) + " imageable "
                + Checks.num(format.getImageableX(), 2) + "," + Checks.num(format.getImageableY(), 2) + " "
                + Checks.num(format.getImageableWidth(), 2) + "x" + Checks.num(format.getImageableHeight(), 2);
    }

    /**
     * {@code WxH imageable X,Y WxH} of {@code paper} (portrait), 2 decimals.
     */
    public static String describe(Paper paper) {
        return Checks.num(paper.getWidth(), 2) + "x" + Checks.num(paper.getHeight(), 2) + " imageable "
                + Checks.num(paper.getImageableX(), 2) + "," + Checks.num(paper.getImageableY(), 2) + " "
                + Checks.num(paper.getImageableWidth(), 2) + "x" + Checks.num(paper.getImageableHeight(), 2);
    }

    /**
     * The 6 values of {@link PageFormat#getMatrix()} (page space to paper space), 2 decimals.
     */
    public static String matrix(PageFormat format) {
        return matrix(format.getMatrix());
    }

    public static String matrix(AffineTransform tx) {
        double[] m = new double[6];
        tx.getMatrix(m);
        return matrix(m);
    }

    private static String matrix(double[] m) {
        List<String> values = new ArrayList<>();
        for (double v : m) {
            values.add(Checks.num(v, 2));
        }
        return "[" + String.join(" ", values) + "]";
    }

    public static String orientation(int orientation) {
        return switch (orientation) {
            case PageFormat.PORTRAIT -> "PORTRAIT";
            case PageFormat.LANDSCAPE -> "LANDSCAPE";
            case PageFormat.REVERSE_LANDSCAPE -> "REVERSE_LANDSCAPE";
            default -> "orientation " + orientation;
        };
    }

    /**
     * What a PostScript document produced by {@code sun.print.PSPrinterJob} contains. It has no creation date, title or
     * user name : the bytes are deterministic for the same pages, fonts and JDK files (the prolog lists the PostScript
     * fonts of {@code <java.home>/lib/psfontj2d.properties}, and text of the mapped fonts is shown with PostScript fonts,
     * other text being drawn as glyph outlines).
     *
     * @param size bytes
     * @param sha256 first 16 hex digits of the SHA-256 of the bytes
     * @param pages {@code %%Page:} comments
     * @param structure the document structuring comments, in order, consecutive duplicates counted
     * @param fonts entries of the prolog font list ({@code /FL [...] D})
     * @param pageDevice the {@code setpagedevice} dictionary
     * @param textShows strings shown with a PostScript font ({@code <hex> x y w S})
     * @param images {@code colorimage} operators (drawn images and rasterized bands)
     * @param excerpt the first lines of the first page
     */
    public record PostScript(int size, String sha256, int pages, String structure, int fonts, String pageDevice,
            int textShows, int images, List<String> excerpt) {

        public static PostScript of(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            List<String> lines = text.lines().toList();
            int pages = 0;
            int fonts = 0;
            int shows = 0;
            int images = 0;
            boolean inFonts = false;
            String pageDevice = "none";
            List<String> structure = new ArrayList<>();
            String previous = null;
            int repeat = 0;
            for (String line : lines) {
                if (line.startsWith("%")) {
                    String key = line.startsWith("%%Page:") ? "%%Page:" : line;
                    if (key.equals(previous)) {
                        repeat++;
                    } else {
                        if (previous != null) {
                            structure.add(repeat > 1 ? previous + " x" + repeat : previous);
                        }
                        previous = key;
                        repeat = 1;
                    }
                }
                if (line.startsWith("%%Page: ")) {
                    pages++;
                }
                if (line.equals("/FL [")) {
                    inFonts = true;
                } else if (inFonts && line.equals("] D")) {
                    inFonts = false;
                } else if (inFonts) {
                    fonts++;
                }
                if (line.endsWith(" S") && line.startsWith("<")) {
                    shows++;
                }
                if (line.contains("colorimage")) {
                    images++;
                }
                if (line.contains("setpagedevice") && pageDevice.equals("none")) {
                    pageDevice = line.trim();
                }
            }
            if (previous != null) {
                structure.add(repeat > 1 ? previous + " x" + repeat : previous);
            }
            List<String> excerpt = new ArrayList<>();
            int page = lines.indexOf("%%Page: 1 1");
            if (page >= 0) {
                for (int i = page; i < lines.size() && excerpt.size() < 12; i++) {
                    String line = lines.get(i);
                    excerpt.add(line.length() > 110 ? line.substring(0, 107) + "..." : line);
                }
            }
            return new PostScript(bytes.length, Checks.sha256(bytes), pages, String.join(" ", structure), fonts,
                    pageDevice, shows, images, List.copyOf(excerpt));
        }
    }
}
