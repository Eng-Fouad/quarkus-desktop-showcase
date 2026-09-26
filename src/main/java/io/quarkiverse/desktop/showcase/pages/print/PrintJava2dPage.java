package io.quarkiverse.desktop.showcase.pages.print;

import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.SimpleDoc;
import javax.print.StreamPrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PageRanges;
import javax.print.attribute.standard.Sides;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.TextBlock;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * {@code java.awt.print} : {@link Printable}, {@link Pageable} and {@link Book} with portrait, landscape and reverse
 * landscape {@link PageFormat}s on 3 paper sizes, rendered "as on paper" into images (the page transform of
 * {@link PageFormat#getMatrix()} applied), and printed to PostScript through the PostScript {@link StreamPrintService}
 * (never to a printer) : with {@link PrinterJob#print} (which spools to the stream service), with a {@link DocPrintJob}
 * of the {@link Pageable} and of a {@link Printable}, with page ranges, copies and duplex, and with a failing page.
 * <p>
 * The PostScript is deterministic (no date, no user name) : its structure, font list, page device and hash are checked.
 * The prolog lists the fonts of {@code <java.home>/lib/psfontj2d.properties} : a native executable whose runtime
 * {@code java.home} lacks that file prints another font list, and its text as glyph outlines.
 * <p>
 * AWT only. The print jobs run in the background ({@link #ready}).
 */
@Singleton
public class PrintJava2dPage implements FeaturePage {

    private static final double THUMBNAIL_SCALE = 0.3;
    private static final String[] PAGE_NAMES = { "Letter portrait", "Letter landscape", "A6 reverse landscape",
            "A4 portrait (1/2)", "A4 portrait (2/2)" };

    private ChecksView postScript;
    private TextBlock excerpt;

    @Override
    public String id() {
        return "print-java2d";
    }

    @Override
    public String title() {
        return "Printable and Book";
    }

    @Override
    public String category() {
        return Categories.PRINTING;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() {
        Book book = SampleBook.book(null);
        List<Component> thumbnails = new ArrayList<>();
        List<BufferedImage> images = new ArrayList<>();
        for (int i = 0; i < book.getNumberOfPages(); i++) {
            BufferedImage image = SampleBook.renderOnPaper(book, i, THUMBNAIL_SCALE);
            images.add(image);
            thumbnails.add(Ui.column(4, Ui.image(image), Ui.caption((i + 1) + " - " + PAGE_NAMES[i])));
        }

        List<Check> formats = formatChecks(book);
        // solid fills are exact : the blue square of the cover, at its center (paper = page coordinates in portrait)
        int probeX = (int) ((SampleBook.COVER_SQUARE_X + SampleBook.COVER_SQUARE_SIZE / 2) * THUMBNAIL_SCALE);
        int probeY = (int) ((SampleBook.COVER_SQUARE_Y + SampleBook.COVER_SQUARE_SIZE / 2) * THUMBNAIL_SCALE);
        formats.add(Checks.expect("rendered cover : pixel inside the blue square", Checks.argb(0xFF000000 | SampleBook.BLUE),
                () -> Checks.argb(images.get(0).getRGB(probeX, probeY))));
        formats.add(Checks.expect("rendered pages : image sizes", "184x238 184x238 90x126 179x253 179x253", () -> {
            List<String> sizes = new ArrayList<>();
            images.forEach(image -> sizes.add(image.getWidth() + "x" + image.getHeight()));
            return String.join(" ", sizes);
        }));

        postScript = ChecksView.table("PostScript through the stream print service",
                List.of(Check.info("print jobs", "pending")));
        excerpt = Ui.text("", new Font(Font.MONOSPACED, Font.PLAIN, 11), Ui.MUTED_COLOR, 1000);

        return Ui.column(14,
                Ui.text("A Book of 5 pages (4 Printables, 3 paper sizes, 3 orientations) drawn \"as on paper\" : the "
                        + "page transform of PageFormat.getMatrix() applied, clipped to the imageable area (frame). The "
                        + "same Book is then printed to PostScript through the PostScript StreamPrintService (never to a "
                        + "printer).", 1000),
                Ui.row(16, thumbnails.toArray(Component[]::new)),
                ChecksView.table("Paper, PageFormat and Book", formats),
                postScript,
                Ui.title("First lines of page 1 (PrinterJob.print)"),
                excerpt);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = postScript;
        TextBlock text = excerpt;
        return Edt.background(PrintJava2dPage::printJobs).thenAccept(result -> {
            view.setChecks(result.checks());
            text.setText(String.join("\n", result.excerpt()));
        });
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        Map<String, BufferedImage> pages = new LinkedHashMap<>();
        Book book = SampleBook.book(null);
        for (int i = 0; i < book.getNumberOfPages(); i++) {
            pages.put("page-" + (i + 1), SampleBook.renderOnPaper(book, i, 1));
        }
        return CompletableFuture.completedFuture(pages);
    }

    @Override
    public void dispose(Component content) {
        postScript = null;
        excerpt = null;
    }

    // ------------------------------------------------------------------------------------------------ page formats

    private static List<Check> formatChecks(Book book) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("new Paper() : Letter, 1 inch margins", "612.00x792.00 imageable 72.00,72.00 468.00x648.00",
                () -> PrintSupport.describe(new Paper())));
        checks.add(Checks.expect("PageFormat PORTRAIT : size, imageable area, matrix",
                "612.00x792.00 imageable 72.00,72.00 468.00x648.00 [1.00 0.00 0.00 1.00 0.00 0.00]", () -> {
                    PageFormat format = SampleBook.format(SampleBook.letter(), PageFormat.PORTRAIT);
                    return PrintSupport.describe(format) + " " + PrintSupport.matrix(format);
                }));
        checks.add(Checks.expect("PageFormat LANDSCAPE : size, imageable area, matrix",
                "792.00x612.00 imageable 72.00,72.00 648.00x468.00 [0.00 -1.00 1.00 0.00 0.00 792.00]", () -> {
                    PageFormat format = SampleBook.format(SampleBook.letter(), PageFormat.LANDSCAPE);
                    return PrintSupport.describe(format) + " " + PrintSupport.matrix(format);
                }));
        checks.add(Checks.expect("PageFormat REVERSE_LANDSCAPE (A6) : size, imageable area, matrix",
                "419.53x297.64 imageable 18.00,18.00 383.53x261.64 [0.00 1.00 -1.00 0.00 297.64 0.00]", () -> {
                    PageFormat format = SampleBook.format(SampleBook.a6(), PageFormat.REVERSE_LANDSCAPE);
                    return PrintSupport.describe(format) + " " + PrintSupport.matrix(format);
                }));
        checks.add(Checks.expect("PageFormat PORTRAIT (A4, 1/2 inch margins)",
                "595.28x841.89 imageable 36.00,36.00 523.28x769.89",
                () -> PrintSupport.describe(SampleBook.format(SampleBook.a4(), PageFormat.PORTRAIT))));
        checks.add(Checks.expect("PageFormat.clone() : independent paper", "LANDSCAPE / PORTRAIT, 612.00 / 595.28", () -> {
            PageFormat original = SampleBook.format(SampleBook.letter(), PageFormat.LANDSCAPE);
            PageFormat copy = (PageFormat) original.clone();
            copy.setOrientation(PageFormat.PORTRAIT);
            copy.setPaper(SampleBook.a4());
            return PrintSupport.orientation(original.getOrientation()) + " / "
                    + PrintSupport.orientation(copy.getOrientation()) + ", "
                    + Checks.num(original.getPaper().getWidth(), 2) + " / " + Checks.num(copy.getPaper().getWidth(), 2);
        }));
        checks.add(Checks.expect("PageFormat.setOrientation(7)", "java.lang.IllegalArgumentException", () -> {
            try {
                new PageFormat().setOrientation(7);
                return "accepted";
            } catch (IllegalArgumentException e) {
                return e.getClass().getName();
            }
        }));
        checks.add(Checks.expect("Book.getNumberOfPages()", SampleBook.PAGES, book::getNumberOfPages));
        checks.add(Checks.expect("Book : printable and orientation per page",
                "Cover PORTRAIT, Chart LANDSCAPE, Gradients REVERSE_LANDSCAPE, Sequence PORTRAIT, Sequence PORTRAIT", () -> {
                    List<String> pages = new ArrayList<>();
                    for (int i = 0; i < book.getNumberOfPages(); i++) {
                        pages.add(book.getPrintable(i).getClass().getSimpleName() + " "
                                + PrintSupport.orientation(book.getPageFormat(i).getOrientation()));
                    }
                    return String.join(", ", pages);
                }));
        checks.add(Checks.expect("Book : pages 4 and 5 share one Printable", true,
                () -> book.getPrintable(3) == book.getPrintable(4)));
        checks.add(Checks.expect("Book.setPage(1, ...) then getPageFormat(1)", "PORTRAIT 595.28", () -> {
            Book copy = SampleBook.book(null);
            copy.setPage(1, new SampleBook.Sequence(null, 1, 1), SampleBook.format(SampleBook.a4(), PageFormat.PORTRAIT));
            return PrintSupport.orientation(copy.getPageFormat(1).getOrientation()) + " "
                    + Checks.num(copy.getPageFormat(1).getPaper().getWidth(), 2);
        }));
        checks.add(Checks.expect("Book.getPageFormat(5)", "java.lang.ArrayIndexOutOfBoundsException", () -> {
            try {
                return PrintSupport.describe(book.getPageFormat(SampleBook.PAGES));
            } catch (IndexOutOfBoundsException e) {
                return e.getClass().getName();
            }
        }));
        checks.add(Checks.expect("Pageable.UNKNOWN_NUMBER_OF_PAGES", -1, () -> Pageable.UNKNOWN_NUMBER_OF_PAGES));
        checks.add(Checks.expect("Sequence printable : pages 0, 1, 2, 3 printed alone", "0 0 0 1", () -> {
            Printable printable = new SampleBook.Sequence(null, 0, 3);
            PageFormat format = new PageFormat();
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            List<String> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                java.awt.Graphics2D g = image.createGraphics();
                try {
                    results.add(String.valueOf(printable.print(g, format, i)));
                } finally {
                    g.dispose();
                }
            }
            return String.join(" ", results);
        }));
        return checks;
    }

    // -------------------------------------------------------------------------------------------------- print jobs

    private record Result(List<Check> checks, List<String> excerpt) {
    }

    private static Result printJobs() {
        List<Check> checks = new ArrayList<>();
        List<String> excerpt = new ArrayList<>();

        checks.add(Checks.expect("PrinterJob.getPrinterJob()", Platforms.pick("CPrinterJob", "WPrinterJob", "PSPrinterJob"),
                () -> PrinterJob.getPrinterJob().getClass().getSimpleName()));
        checks.add(Checks.expect("PrinterJob.lookupStreamPrintServices(\"application/postscript\")", "Postscript output",
                () -> {
                    List<String> names = new ArrayList<>();
                    for (var factory : PrinterJob.lookupStreamPrintServices(PrintSupport.POSTSCRIPT)) {
                        names.add(factory.getPrintService(new ByteArrayOutputStream()).getName());
                    }
                    return String.join(", ", names);
                }));

        // 1. PrinterJob.print() of the Book : RasterPrinterJob spools to the stream service (a PSStreamPrintJob)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SampleBook.PrintLog log = new SampleBook.PrintLog();
        PrintSupport.PostScript book;
        try {
            StreamPrintService service = PrintSupport.postScriptService(out);
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(service);
            checks.add(Check.pass("PrinterJob.setPrintService(stream service)", job.getPrintService().getName()));
            // WPrinterJob asks the driver of the default printer (native), PSPrinterJob the print service attributes
            checks.add(Checks.info("PrinterJob.defaultPage()", () -> PrintSupport.describe(job.defaultPage())));
            checks.add(Checks.expect("PrinterJob.getPageFormat(ISO_A4, LANDSCAPE, 10 mm margins)",
                    "LANDSCAPE 841.89x595.28 imageable 28.35,28.35 785.20x538.58", () -> {
                        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
                        attributes.add(MediaSizeName.ISO_A4);
                        attributes.add(OrientationRequested.LANDSCAPE);
                        attributes.add(new MediaPrintableArea(10, 10, 190, 277, MediaPrintableArea.MM));
                        PageFormat format = job.getPageFormat(attributes);
                        return PrintSupport.orientation(format.getOrientation()) + " " + PrintSupport.describe(format);
                    }));
            checks.add(Checks.info("PrinterJob.validatePage(imageable area larger than the paper)", () -> {
                        PageFormat format = new PageFormat();
                        Paper paper = new Paper();
                        paper.setImageableArea(-20, -20, 700, 900);
                        format.setPaper(paper);
                        return PrintSupport.describe(job.validatePage(format));
                    }));
            job.setJobName("quarkus-desktop-showcase book");
            job.setCopies(1);
            checks.add(Checks.expect("PrinterJob job name, copies, cancelled", "quarkus-desktop-showcase book, 1, false",
                    () -> job.getJobName() + ", " + job.getCopies() + ", " + job.isCancelled()));
            job.setPageable(SampleBook.book(log));
            job.print();
            book = PrintSupport.PostScript.of(out.toByteArray());
            excerpt.addAll(book.excerpt());
        } catch (Throwable t) {
            checks.add(Check.fail("PrinterJob.print() of the Book", Checks.describe(t)));
            return new Result(checks, excerpt);
        }
        checks.add(Checks.expect("PrinterJob.print() : pages", SampleBook.PAGES, book::pages));
        checks.add(Checks.expect("PrinterJob.print() : document structure",
                "%!PS-Adobe-3.0 %%BeginProlog %%EndProlog %%BeginSetup %%EndSetup %%Page: x5 %%EOF", book::structure));
        checks.add(Checks.expect("PrinterJob.print() : setpagedevice (first page)",
                "<< /PageSize [612.0 792.0] /DeferredMediaSelection true /ImagingBBox null /ManualFeed false /NumCopies 1 >> "
                        + "setpagedevice",
                book::pageDevice));
        // 35 fonts of psfontj2d.properties ; 12 built-in fonts when java.home/lib/psfontj2d.properties is missing
        checks.add(Checks.expect("PrinterJob.print() : prolog PostScript fonts (psfontj2d.properties)", 35, book::fonts));
        checks.add(Check.pass("PrinterJob.print() : text shown with PostScript fonts", book.textShows()));
        checks.add(Check.pass("PrinterJob.print() : colorimage operators (image + raster bands)", book.images()));
        checks.add(Check.pass("PrinterJob.print() : size, SHA-256", book.size() + " bytes, " + book.sha256()));
        checks.add(Checks.expect("Printable.print calls per page",
                "1: PeekGraphics, PSPathGraphics | 2: PeekGraphics, PSPathGraphics | 3: PeekGraphics, ProxyGraphics2D x2 | "
                        + "4: PeekGraphics, PSPathGraphics | 5: PeekGraphics, PSPathGraphics",
                log::calls));
        checks.add(Checks.expect("printer graphics (first page)",
                "PSPathGraphics of PSPrinterJob, device type TYPE_PRINTER, device bounds 0.00,0.00 2550.00x3300.00, "
                        + "transform [4.17 0.00 0.00 4.17 0.00 0.00]",
                log::printerGraphics));

        // 2. the same Book as a Pageable doc of a DocPrintJob : the same PostScript
        checks.add(Checks.expect("DocPrintJob of the Book (SERVICE_FORMATTED.PAGEABLE) : same PostScript", book.sha256(),
                () -> {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DocPrintJob job = PrintSupport.postScriptService(bytes).createPrintJob();
                    job.print(new SimpleDoc(SampleBook.book(null), DocFlavor.SERVICE_FORMATTED.PAGEABLE, null),
                            new HashPrintRequestAttributeSet());
                    return PrintSupport.PostScript.of(bytes.toByteArray()).sha256();
                }));

        // 3. page ranges, copies and duplex
        checks.add(Checks.expect("PrinterJob.print(PageRanges 2-3, Copies 2, DUPLEX) : pages, page device",
                "2, << /PageSize [612.0 792.0] /DeferredMediaSelection true /ImagingBBox null /ManualFeed false /NumCopies 2 "
                        + "/Duplex true  >> setpagedevice",
                () -> {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    PrinterJob job = PrinterJob.getPrinterJob();
                    job.setPrintService(PrintSupport.postScriptService(bytes));
                    job.setPageable(SampleBook.book(null));
                    PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
                    attributes.add(new PageRanges(2, 3));
                    attributes.add(new Copies(2));
                    attributes.add(Sides.DUPLEX);
                    job.print(attributes);
                    PrintSupport.PostScript ps = PrintSupport.PostScript.of(bytes.toByteArray());
                    return ps.pages() + ", " + ps.pageDevice();
                }));

        // 4. a Printable doc (the stream print job creates the page format : A5, 1 inch margins, landscape)
        checks.add(Checks.expect("DocPrintJob of a Printable (ISO_A5, LANDSCAPE) : pages, page device",
                "3, << /PageSize [419.5275573730469 595.2755737304688] /DeferredMediaSelection true /ImagingBBox null "
                        + "/ManualFeed false /NumCopies 1 >> setpagedevice",
                () -> {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DocPrintJob job = PrintSupport.postScriptService(bytes).createPrintJob();
                    PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
                    attributes.add(MediaSizeName.ISO_A5);
                    attributes.add(OrientationRequested.LANDSCAPE);
                    job.print(new SimpleDoc(new SampleBook.Sequence(null, 0, 3), DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                            null), attributes);
                    PrintSupport.PostScript ps = PrintSupport.PostScript.of(bytes.toByteArray());
                    return ps.pages() + ", " + ps.pageDevice();
                }));

        // 5. a page throwing a PrinterException : the exception reaches PrinterJob.print
        checks.add(Checks.expect("PrinterJob.print() of a failing Printable",
                "java.awt.print.PrinterException: javax.print.PrintException: java.awt.print.PrinterException: "
                        + "simulated failure on page 2",
                () -> {
                    PrinterJob job = PrinterJob.getPrinterJob();
                    job.setPrintService(PrintSupport.postScriptService(new ByteArrayOutputStream()));
                    job.setPrintable(new SampleBook.Failing());
                    try {
                        job.print();
                        return "printed";
                    } catch (PrinterException e) {
                        return Checks.describe(e);
                    }
                }));
        return new Result(checks, excerpt);
    }
}
