package io.quarkiverse.desktop.showcase.pages.print;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import javax.imageio.ImageIO;
import javax.print.CancelablePrintJob;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.AttributeSetUtilities;
import javax.print.attribute.DocAttribute;
import javax.print.attribute.HashAttributeSet;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.ColorSupported;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.CopiesSupported;
import javax.print.attribute.standard.Destination;
import javax.print.attribute.standard.DialogTypeSelection;
import javax.print.attribute.standard.DocumentName;
import javax.print.attribute.standard.Fidelity;
import javax.print.attribute.standard.Finishings;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.JobSheets;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaName;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.MediaTray;
import javax.print.attribute.standard.NumberUp;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.OutputBin;
import javax.print.attribute.standard.PageRanges;
import javax.print.attribute.standard.PrintQuality;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.attribute.standard.PrinterName;
import javax.print.attribute.standard.PrinterResolution;
import javax.print.attribute.standard.SheetCollate;
import javax.print.attribute.standard.Sides;
import javax.print.event.PrintJobAttributeEvent;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * {@code javax.print} : the print services of the machine ({@link PrintServiceLookup}, environment dependent, looked up
 * only), the PostScript {@link StreamPrintService} (flavors, attribute categories, defaults and supported values),
 * {@link DocFlavor}s and the standard attributes, and {@link DocPrintJob}s of PNG, GIF and JPEG images (byte array,
 * input stream and URL representations) and of {@link Printable}s to the stream service, with their
 * {@link PrintJobListener} events : completed, failed (unsupported flavor, fidelity), canceled.
 * <p>
 * Never prints to a printer : every job goes to a PostScript stream in memory. AWT only ; the lookups and jobs run in
 * the background ({@link #ready}).
 */
@Singleton
public class JavaxPrintPage implements FeaturePage {

    private static final int IMAGE_WIDTH = 160;
    private static final int IMAGE_HEIGHT = 100;

    private ChecksView services;
    private ChecksView stream;
    private ChecksView jobs;

    @Override
    public String id() {
        return "print-javax-print";
    }

    @Override
    public String title() {
        return "javax.print services";
    }

    @Override
    public String category() {
        return Categories.PRINTING;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Component build() throws IOException {
        BufferedImage source = sourceImage();
        List<Component> tiles = new ArrayList<>();
        for (String format : List.of("png", "gif", "jpeg")) {
            byte[] bytes = encode(source, format);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            tiles.add(Ui.column(4, Ui.image(decoded), Ui.caption(format.toUpperCase(Locale.ROOT) + ", " + bytes.length
                    + " bytes, printed as " + switch (format) {
                        case "png" -> "BYTE_ARRAY.PNG";
                        case "gif" -> "INPUT_STREAM.GIF";
                        default -> "URL.JPEG";
                    })));
        }
        services = ChecksView.table("Print services of this machine (environment : looked up, never printed to)",
                List.of(Check.info("lookup", "pending")));
        stream = ChecksView.table("PostScript stream print service", List.of(Check.info("lookup", "pending")));
        jobs = ChecksView.table("DocPrintJob to the PostScript stream", List.of(Check.info("print jobs", "pending")));
        return Ui.column(14,
                Ui.text("javax.print print services, doc flavors and attributes. The images below are encoded with "
                        + "ImageIO and printed with DocPrintJob to the PostScript stream print service (never to a "
                        + "printer), with a PrintJobListener recording the job events.", 1000),
                Ui.row(24, tiles.toArray(Component[]::new)),
                services,
                stream,
                ChecksView.table("DocFlavor and attributes", flavorAndAttributeChecks()),
                jobs);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView servicesView = services;
        ChecksView streamView = stream;
        ChecksView jobsView = jobs;
        return Edt.background(JavaxPrintPage::serviceChecks).thenAccept(servicesView::setChecks)
                .thenCompose(v -> Edt.background(JavaxPrintPage::streamServiceChecks)).thenAccept(streamView::setChecks)
                .thenCompose(v -> Edt.background(JavaxPrintPage::jobChecks)).thenAccept(jobsView::setChecks);
    }

    @Override
    public void dispose(Component content) {
        services = null;
        stream = null;
        jobs = null;
    }

    // ---------------------------------------------------------------------------------------------------- images

    /**
     * A deterministic opaque image : a gradient, circles and a caption.
     */
    static BufferedImage sourceImage() {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setPaint(new GradientPaint(0, 0, new Color(0x1E88E5), IMAGE_WIDTH, IMAGE_HEIGHT, new Color(0xFDD835)));
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            int[] colors = { 0xE53935, 0x43A047, 0x8E24AA };
            for (int i = 0; i < colors.length; i++) {
                g.setColor(new Color(colors[i]));
                g.fill(new Ellipse2D.Double(12 + i * 46, 18, 42, 42));
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.drawString("javax.print", 12, 86);
        } finally {
            g.dispose();
        }
        return image;
    }

    static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new IOException("No ImageIO writer for " + format);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------------------------------ machine services

    private static List<Check> serviceChecks() {
        List<Check> checks = new ArrayList<>();
        PrintService[] all;
        try {
            all = PrintServiceLookup.lookupPrintServices(null, null);
        } catch (Throwable t) {
            return List.of(Check.fail("PrintServiceLookup.lookupPrintServices(null, null)", Checks.describe(t)));
        }
        checks.add(Check.info("PrintServiceLookup.lookupPrintServices(null, null)", all.length + " : "
                + String.join(" | ", Arrays.stream(all).map(PrintService::getName).toList())));
        checks.add(Checks.info("service classes", () -> String.join(", ",
                new TreeSet<>(Arrays.stream(all).map(s -> s.getClass().getSimpleName()).toList()))));
        PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        checks.add(Check.info("PrintServiceLookup.lookupDefaultPrintService()",
                defaultService == null ? "none" : defaultService.getName()));
        checks.add(Checks.info("lookupPrintServices(INPUT_STREAM.AUTOSENSE)",
                () -> PrintServiceLookup.lookupPrintServices(DocFlavor.INPUT_STREAM.AUTOSENSE, null).length));
        checks.add(Checks.info("lookupPrintServices(SERVICE_FORMATTED.PAGEABLE, color)", () -> {
            PrintRequestAttributeSet color = new HashPrintRequestAttributeSet();
            color.add(Chromaticity.COLOR);
            return PrintServiceLookup.lookupPrintServices(DocFlavor.SERVICE_FORMATTED.PAGEABLE, color).length;
        }));
        checks.add(Checks.info("lookupMultiDocPrintServices(PNG, PDF)",
                () -> PrintServiceLookup.lookupMultiDocPrintServices(
                        new DocFlavor[] { DocFlavor.INPUT_STREAM.PNG, DocFlavor.INPUT_STREAM.PDF }, null).length));
        if (defaultService == null) {
            return checks;
        }
        // the queries of a printer service ask its driver (native code) : values of this machine only
        PrintService s = defaultService;
        checks.add(Checks.info("default : supported doc flavors", () -> s.getSupportedDocFlavors().length));
        checks.add(Checks.info("default : supported attribute categories", () -> names(s.getSupportedAttributeCategories())));
        checks.add(Checks.info("default : printer name, accepting jobs, color", () -> s.getAttribute(PrinterName.class)
                + ", " + s.getAttribute(PrinterIsAcceptingJobs.class) + ", " + s.getAttribute(ColorSupported.class)));
        checks.add(Checks.info("default : default media, orientation, copies, sides",
                () -> s.getDefaultAttributeValue(Media.class) + ", " + s.getDefaultAttributeValue(OrientationRequested.class)
                        + ", " + s.getDefaultAttributeValue(Copies.class) + ", " + s.getDefaultAttributeValue(Sides.class)));
        checks.add(Checks.info("default : supported media",
                () -> count(s.getSupportedAttributeValues(Media.class, null, null))));
        checks.add(Checks.info("default : supported resolutions", () -> {
            Object values = s.getSupportedAttributeValues(PrinterResolution.class, null, null);
            if (!(values instanceof PrinterResolution[] resolutions)) {
                return String.valueOf(values);
            }
            return String.join(", ", Arrays.stream(resolutions).map(r -> r.toString(ResolutionSyntax.DPI, "dpi")).toList());
        }));
        checks.add(Checks.info("default : copies supported", () -> s.getSupportedAttributeValues(Copies.class, null, null)));
        checks.add(Checks.info("default : isDocFlavorSupported(INPUT_STREAM.PNG / SERVICE_FORMATTED.PRINTABLE)",
                () -> s.isDocFlavorSupported(DocFlavor.INPUT_STREAM.PNG) + " / "
                        + s.isDocFlavorSupported(DocFlavor.SERVICE_FORMATTED.PRINTABLE)));
        checks.add(Checks.info("default : isAttributeCategorySupported(Destination)",
                () -> s.isAttributeCategorySupported(Destination.class)));
        return checks;
    }

    // ------------------------------------------------------------------------------------------ PS stream service

    private static List<Check> streamServiceChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("lookupStreamPrintServiceFactories(null, application/postscript)", "PSStreamPrinterFactory",
                () -> String.join(", ", Arrays.stream(StreamPrintServiceFactory.lookupStreamPrintServiceFactories(null,
                        PrintSupport.POSTSCRIPT)).map(f -> f.getClass().getSimpleName()).toList())));
        checks.add(Checks.expect("lookupStreamPrintServiceFactories(PAGEABLE, application/pdf)", 0,
                () -> StreamPrintServiceFactory.lookupStreamPrintServiceFactories(DocFlavor.SERVICE_FORMATTED.PAGEABLE,
                        "application/pdf").length));
        StreamPrintServiceFactory factory = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(null,
                PrintSupport.POSTSCRIPT)[0];
        checks.add(Checks.expect("factory.getOutputFormat()", PrintSupport.POSTSCRIPT, factory::getOutputFormat));
        checks.add(Checks.expect("factory.getSupportedDocFlavors()",
                "application/x-java-jvm-local-objectref; class=\"java.awt.print.Pageable\" | "
                        + "application/x-java-jvm-local-objectref; class=\"java.awt.print.Printable\" | "
                        + "image/gif; class=\"[B\" | image/gif; class=\"java.io.InputStream\" | "
                        + "image/gif; class=\"java.net.URL\" | "
                        + "image/jpeg; class=\"[B\" | image/jpeg; class=\"java.io.InputStream\" | "
                        + "image/jpeg; class=\"java.net.URL\" | "
                        + "image/png; class=\"[B\" | image/png; class=\"java.io.InputStream\" | "
                        + "image/png; class=\"java.net.URL\"",
                () -> sorted(factory.getSupportedDocFlavors())));
        StreamPrintService service = factory.getPrintService(new ByteArrayOutputStream());
        checks.add(Checks.expect("service.getName(), getOutputFormat()", "Postscript output, application/postscript",
                () -> service.getName() + ", " + service.getOutputFormat()));
        checks.add(Checks.expect("service.getSupportedAttributeCategories()",
                "Chromaticity Copies Fidelity JobName Media MediaPrintableArea OrientationRequested PageRanges "
                        + "RequestingUserName SheetCollate Sides",
                () -> names(service.getSupportedAttributeCategories())));
        checks.add(Checks.expect("service.getAttributes()", "color-supported=supported",
                () -> attributes(service.getAttributes())));
        checks.add(Checks.expect("isDocFlavorSupported(PNG bytes / TEXT_PLAIN string / PDF stream)", "true / false / false",
                () -> service.isDocFlavorSupported(DocFlavor.BYTE_ARRAY.PNG) + " / "
                        + service.isDocFlavorSupported(DocFlavor.STRING.TEXT_PLAIN) + " / "
                        + service.isDocFlavorSupported(DocFlavor.INPUT_STREAM.PDF)));
        checks.add(Checks.expect("isAttributeCategorySupported(Destination / NumberUp / Sides)", "false / false / true",
                () -> service.isAttributeCategorySupported(Destination.class) + " / "
                        + service.isAttributeCategorySupported(NumberUp.class) + " / "
                        + service.isAttributeCategorySupported(Sides.class)));
        checks.add(Checks.expect("default copies, chromaticity, orientation, sides, collate, fidelity, page ranges",
                "1, color, portrait, one-sided, uncollated, false, 1-2147483647", () -> String.join(", ",
                        List.of(Copies.class, Chromaticity.class, OrientationRequested.class, Sides.class,
                                SheetCollate.class, Fidelity.class, PageRanges.class).stream()
                                .map(c -> String.valueOf(service.getDefaultAttributeValue(c))).toList())));
        String country = Locale.getDefault().getCountry();
        boolean letter = country.isEmpty() || country.equals("US") || country.equals("CA");
        checks.add(Checks.expect("default media and printable area (locale " + Locale.getDefault().toLanguageTag() + ")",
                letter ? "na-letter, (0.5,0.5)->(7.5,10.0)in" : "iso-a4, (0.5,0.5)->(7.268,10.693)in",
                () -> service.getDefaultAttributeValue(Media.class) + ", "
                        + ((MediaPrintableArea) service.getDefaultAttributeValue(MediaPrintableArea.class))
                                .toString(MediaPrintableArea.INCH, "in")));
        checks.add(Checks.expect("supported orientations", "portrait, landscape, reverse-landscape", () -> String.join(", ",
                Arrays.stream((OrientationRequested[]) service.getSupportedAttributeValues(OrientationRequested.class,
                        null, null)).map(String::valueOf).toList())));
        checks.add(Checks.expect("supported copies, sides", "1-1000, one-sided two-sided-long-edge two-sided-short-edge",
                () -> service.getSupportedAttributeValues(Copies.class, null, null) + ", " + String.join(" ",
                        Arrays.stream((Sides[]) service.getSupportedAttributeValues(Sides.class, null, null))
                                .map(String::valueOf).toList())));
        checks.add(Checks.info("supported media", () -> count(service.getSupportedAttributeValues(Media.class, null, null))));
        // the service accepts 1 to 999 copies, although it announces 1-1000 as supported values
        checks.add(Checks.expect("isAttributeValueSupported(Copies 999 / Copies 1000 / ISO_A3 / NA_LEGAL)",
                "true / false / true / true", () -> service.isAttributeValueSupported(new Copies(999), null, null)
                        + " / " + service.isAttributeValueSupported(new Copies(1000), null, null) + " / "
                        + service.isAttributeValueSupported(MediaSizeName.ISO_A3, null, null) + " / "
                        + service.isAttributeValueSupported(MediaSizeName.NA_LEGAL, null, null)));
        checks.add(Checks.expect("getUnsupportedAttributes(PNG, {Copies 5, NumberUp 2, Destination})",
                "number-up=2 spool-data-destination=file:/showcase/out.ps", () -> {
                    PrintRequestAttributeSet set = new HashPrintRequestAttributeSet();
                    set.add(new Copies(5));
                    set.add(new NumberUp(2));
                    set.add(new Destination(URI.create("file:/showcase/out.ps")));
                    return attributes(service.getUnsupportedAttributes(DocFlavor.INPUT_STREAM.PNG, set));
                }));
        checks.add(Checks.expect("getServiceUIFactory(), isDisposed() before / after dispose()", "null, false / true", () -> {
            StreamPrintService other = factory.getPrintService(new ByteArrayOutputStream());
            boolean before = other.isDisposed();
            other.dispose();
            return other.getServiceUIFactory() + ", " + before + " / " + other.isDisposed();
        }));
        return checks;
    }

    // ------------------------------------------------------------------------------------ flavors and attributes

    private static List<Check> flavorAndAttributeChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("STRING.TEXT_PLAIN : MIME type, representation, charset",
                "text/plain; charset=\"utf-16\", java.lang.String, utf-16", () -> DocFlavor.STRING.TEXT_PLAIN.getMimeType()
                        + ", " + DocFlavor.STRING.TEXT_PLAIN.getRepresentationClassName() + ", "
                        + DocFlavor.STRING.TEXT_PLAIN.getParameter("charset")));
        checks.add(Checks.expect("new DocFlavor(\"image/png\", \"[B\") equals BYTE_ARRAY.PNG", "true, image/png; class=\"[B\"",
                () -> {
                    DocFlavor flavor = new DocFlavor("image/png", "[B");
                    return flavor.equals(DocFlavor.BYTE_ARRAY.PNG) + ", " + flavor;
                }));
        checks.add(Checks.expect("new DocFlavor(\"TEXT/Plain; Charset=UTF-8\", ...) : normalized",
                "text/plain; charset=\"utf-8\", text, plain", () -> {
                    DocFlavor flavor = new DocFlavor("TEXT/Plain; Charset=UTF-8", "java.io.InputStream");
                    return flavor.getMimeType() + ", " + flavor.getMediaType() + ", " + flavor.getMediaSubtype();
                }));
        checks.add(Checks.expect("new DocFlavor(\"not a mime type\", ...)", "java.lang.IllegalArgumentException", () -> {
            try {
                return new DocFlavor("not a mime type", "[B").toString();
            } catch (IllegalArgumentException e) {
                return e.getClass().getName();
            }
        }));
        checks.add(Checks.expect("SERVICE_FORMATTED.PRINTABLE, INPUT_STREAM.AUTOSENSE",
                "application/x-java-jvm-local-objectref; class=\"java.awt.print.Printable\", "
                        + "application/octet-stream; class=\"java.io.InputStream\"",
                () -> DocFlavor.SERVICE_FORMATTED.PRINTABLE + ", " + DocFlavor.INPUT_STREAM.AUTOSENSE));
        checks.add(Checks.info("DocFlavor.hostEncoding", () -> DocFlavor.hostEncoding));
        // SimpleDoc loads the representation class of the flavor by name (Class.forName) to check the print data
        checks.add(Checks.expect("SimpleDoc : bytes as BYTE_ARRAY.PNG, a Printable as PRINTABLE, a String as INPUT_STREAM.GIF",
                "image/png, java.awt.print.Printable, java.lang.IllegalArgumentException: data is not of declared type",
                () -> {
                    Doc png = new SimpleDoc(new byte[] { 1, 2, 3 }, DocFlavor.BYTE_ARRAY.PNG, null);
                    Doc printable = new SimpleDoc(new SampleBook.Failing(), DocFlavor.SERVICE_FORMATTED.PRINTABLE, null);
                    String wrong;
                    try {
                        wrong = new SimpleDoc("text", DocFlavor.INPUT_STREAM.GIF, null).getDocFlavor().toString();
                    } catch (IllegalArgumentException e) {
                        wrong = Checks.describe(e);
                    }
                    return png.getDocFlavor().getMimeType() + ", "
                            + printable.getDocFlavor().getRepresentationClassName() + ", " + wrong;
                }));
        checks.add(Checks.expect("SimpleDoc.getStreamForBytes() / getReaderForText()", "3 bytes / null", () -> {
            Doc doc = new SimpleDoc(new byte[] { 1, 2, 3 }, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            return doc.getStreamForBytes().readAllBytes().length + " bytes / " + doc.getReaderForText();
        }));
        checks.add(Checks.expect("standard attributes : toString()",
                "iso-a4 landscape two-sided-long-edge monochrome high collated common staple manual top",
                () -> String.join(" ", List.<Attribute> of(MediaSizeName.ISO_A4, OrientationRequested.LANDSCAPE, Sides.DUPLEX,
                        Chromaticity.MONOCHROME, PrintQuality.HIGH, SheetCollate.COLLATED, DialogTypeSelection.COMMON,
                        Finishings.STAPLE, MediaTray.MANUAL, OutputBin.TOP).stream().map(String::valueOf).toList())));
        checks.add(Checks.expect("standard attributes : getName() / getCategory()",
                "copies/Copies media/Media orientation-requested/OrientationRequested sides/Sides job-name/JobName "
                        + "number-up/NumberUp page-ranges/PageRanges",
                () -> String.join(" ", List.<Attribute> of(new Copies(2), MediaName.ISO_A4_WHITE,
                        OrientationRequested.PORTRAIT, Sides.ONE_SIDED, new JobName("x", Locale.ENGLISH), new NumberUp(4),
                        new PageRanges(1)).stream().map(a -> a.getName() + "/" + a.getCategory().getSimpleName()).toList())));
        checks.add(Checks.expect("OrientationRequested / JobSheets : enumeration values", "3 4 5 6 / none standard", () -> {
            List<String> values = new ArrayList<>();
            for (OrientationRequested o : List.of(OrientationRequested.PORTRAIT, OrientationRequested.LANDSCAPE,
                    OrientationRequested.REVERSE_LANDSCAPE, OrientationRequested.REVERSE_PORTRAIT)) {
                values.add(String.valueOf(o.getValue()));
            }
            return String.join(" ", values) + " / " + JobSheets.NONE + " " + JobSheets.STANDARD;
        }));
        checks.add(Checks.expect("PageRanges(\"1-3, 5, 7-9\") : toString, members, contains 8, next(3)",
                "1-3,5,7-9, [1-3] [5-5] [7-9], true, 5", () -> {
                    PageRanges ranges = new PageRanges("1-3, 5, 7-9");
                    List<String> members = new ArrayList<>();
                    for (int[] member : ranges.getMembers()) {
                        members.add("[" + member[0] + "-" + member[1] + "]");
                    }
                    return ranges + ", " + String.join(" ", members) + ", " + ranges.contains(8) + ", " + ranges.next(3);
                }));
        checks.add(findMedia());
        checks.add(Checks.expect("MediaSize of ISO_A4 / NA_LETTER / ISO_A6", "210.0x297.0 mm / 8.5x11.0 in / 105.0x148.0 mm",
                () -> MediaSize.getMediaSizeForName(MediaSizeName.ISO_A4).toString(Size2DSyntax.MM, "mm") + " / "
                        + MediaSize.getMediaSizeForName(MediaSizeName.NA_LETTER).toString(Size2DSyntax.INCH, "in") + " / "
                        + MediaSize.ISO.A6.toString(Size2DSyntax.MM, "mm")));
        checks.add(Checks.expect("MediaPrintableArea(10, 10, 190, 277, MM)", "(10.0,10.0)->(190.0,277.0)mm",
                () -> new MediaPrintableArea(10, 10, 190, 277, MediaPrintableArea.MM).toString(MediaPrintableArea.MM, "mm")));
        checks.add(Checks.expect("PrinterResolution(600, 1200, DPI) : dpi, dpcm", "600x1200 dpi, 236x472 dpcm", () -> {
            PrinterResolution resolution = new PrinterResolution(600, 1200, ResolutionSyntax.DPI);
            return resolution.toString(ResolutionSyntax.DPI, "dpi") + ", "
                    + resolution.getCrossFeedResolution(ResolutionSyntax.DPCM) + "x"
                    + resolution.getFeedResolution(ResolutionSyntax.DPCM) + " dpcm";
        }));
        checks.add(Checks.expect("CopiesSupported(1, 99) contains 50 / 100", "1-99 true / false",
                () -> new CopiesSupported(1, 99) + " " + new CopiesSupported(1, 99).contains(50) + " / "
                        + new CopiesSupported(1, 99).contains(100)));
        checks.add(Checks.expect("HashPrintRequestAttributeSet : add, replace, size, get, remove",
                "true false true 3 copies=3 true 2", () -> {
                    PrintRequestAttributeSet set = new HashPrintRequestAttributeSet();
                    boolean added = set.add(new Copies(2));
                    boolean again = set.add(new Copies(2));
                    boolean replaced = set.add(new Copies(3));
                    set.add(MediaSizeName.ISO_A4);
                    set.add(new JobName("report", null));
                    int size = set.size();
                    String copies = attributes(new HashAttributeSet(set.get(Copies.class)));
                    boolean removed = set.remove(JobName.class);
                    return added + " " + again + " " + replaced + " " + size + " " + copies + " " + removed + " "
                            + set.size();
                }));
        checks.add(Checks.expect("AttributeSetUtilities.unmodifiableView(set).add(...)",
                "javax.print.attribute.UnmodifiableSetException", () -> {
                    try {
                        AttributeSetUtilities.unmodifiableView(new HashPrintRequestAttributeSet()).add(new Copies(1));
                        return "added";
                    } catch (RuntimeException e) {
                        return e.getClass().getName();
                    }
                }));
        checks.add(Checks.expect("HashDocAttributeSet.add(JobName) (not a DocAttribute)", "java.lang.ClassCastException",
                () -> {
                    try {
                        new HashDocAttributeSet().add(new JobName("x", null));
                        return "added";
                    } catch (ClassCastException e) {
                        return e.getClass().getName();
                    }
                }));
        checks.add(Checks.expect(
                "verifyAttributeCategory(Copies, PrintRequestAttribute) / (DocumentName, PrintRequestAttribute)",
                "Copies / java.lang.ClassCastException", () -> {
                    String first = AttributeSetUtilities.verifyAttributeCategory(Copies.class, PrintRequestAttribute.class)
                            .getSimpleName();
                    try {
                        AttributeSetUtilities.verifyAttributeValue(new DocumentName("x", null), PrintRequestAttribute.class);
                        return first + " / accepted";
                    } catch (ClassCastException e) {
                        return first + " / " + e.getClass().getName();
                    }
                }));
        checks.add(Checks.expect("Destination(URI) is a PrintRequestAttribute, not a DocAttribute", "true false",
                () -> {
                    Destination destination = new Destination(URI.create("file:/showcase/out.ps"));
                    boolean doc = ((Object) destination) instanceof DocAttribute;
                    return (destination instanceof PrintRequestAttribute) + " " + doc;
                }));
        return checks;
    }

    /**
     * {@code MediaSize.findMedia} returns the first registered {@code MediaSize} of exactly that size : 8.5 x 11 in is
     * {@code NA.LETTER} (na-letter) and {@code Engineering.A} (a), registered in the order their classes initialize.
     * The static initializer of {@code MediaSize} initializes {@code ISO, JIS, NA, Engineering, Other} : NA.LETTER comes
     * first. But when {@code MediaSize.NA} is the first class touched, {@code NA.<clinit>} triggers
     * {@code MediaSize.<clinit>}, which skips NA (being initialized) and registers Engineering.A before NA.LETTER.
     * <p>
     * On macOS (no printer : no print service touches MediaSize) the first access depends on the pages shown before
     * in the process. After swing-printing (default variant) : {@code CPrinterJob.print} sent its jobs for a
     * StreamPrintService straight to {@code spoolToService} (no {@code setAttributes}), and {@code PSStreamPrintJob}
     * starts with {@code mediaSize = MediaSize.NA.LETTER} : "a". After print-java2d alone ({@code PrinterJob
     * .getPageFormat} calls {@code MediaSize.getMediaSizeForName}), or when this call is the first : na-letter. Both are
     * the JDK's answer for this process ; anything else fails. The value is fixed for a given variant and page set (the
     * same in the JVM and in the native executable, run after run), and tools/Compare.java reports it when it changes
     * between two runs of the same pages. It is not pinned to one value : that would tie this check to the pages shown
     * before it, or need a macOS-only MediaSize access at startup, which would hide the class initialization order (a
     * MediaSize initialized at build time in the native executable shows here as a JVM / native difference).
     */
    private static Check findMedia() {
        String name = "MediaSize.findMedia(8.5, 11, INCH), (210, 297, MM)";
        Callable<String> action = () -> MediaSize.findMedia(8.5f, 11f, Size2DSyntax.INCH) + ", "
                + MediaSize.findMedia(210, 297, Size2DSyntax.MM);
        if (!Platforms.isMac()) {
            return Checks.expect(name, "na-letter, iso-a4", action);
        }
        String value;
        try {
            value = action.call();
        } catch (Throwable t) {
            return Check.fail(name, Checks.describe(t));
        }
        boolean ok = value.equals("na-letter, iso-a4") || value.equals("a, iso-a4");
        return Check.of(name, ok, ok ? value : "expected na-letter, iso-a4 or a, iso-a4 but got " + value);
    }

    // ------------------------------------------------------------------------------------------------ print jobs

    /**
     * Records the {@link PrintJobListener} and {@link PrintJobAttributeListener} events of a job (listeners may be called
     * on the printing thread).
     */
    static final class JobEvents implements PrintJobListener, PrintJobAttributeListener {

        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void printDataTransferCompleted(PrintJobEvent event) {
            events.add("dataTransferCompleted");
        }

        @Override
        public void printJobCompleted(PrintJobEvent event) {
            events.add("completed");
        }

        @Override
        public void printJobFailed(PrintJobEvent event) {
            events.add("failed");
        }

        @Override
        public void printJobCanceled(PrintJobEvent event) {
            events.add("canceled");
        }

        @Override
        public void printJobNoMoreEvents(PrintJobEvent event) {
            events.add("noMoreEvents");
        }

        @Override
        public void printJobRequiresAttention(PrintJobEvent event) {
            events.add("requiresAttention");
        }

        @Override
        public void attributeUpdate(PrintJobAttributeEvent event) {
            events.add("attributes " + attributes(event.getAttributes()));
        }

        String events() {
            synchronized (events) {
                return events.isEmpty() ? "no event" : String.join(", ", events);
            }
        }
    }

    /**
     * A job on a new PostScript stream : its outcome, events and PostScript.
     */
    private record Job(String outcome, String events, PrintSupport.PostScript postScript) {

        String summary() {
            return outcome + " ; events : " + events + (postScript == null ? ""
                    : " ; " + postScript.pages() + " page(s), " + postScript.images() + " image(s)");
        }
    }

    private interface JobAction {
        void print(DocPrintJob job) throws Exception;
    }

    private static Job job(JobAction action) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocPrintJob job = PrintSupport.postScriptService(out).createPrintJob();
        JobEvents events = new JobEvents();
        job.addPrintJobListener(events);
        job.addPrintJobAttributeListener(events, null);
        String outcome;
        try {
            action.print(job);
            outcome = "printed";
        } catch (Exception e) {
            outcome = Checks.describe(e);
        }
        return new Job(outcome, events.events(), out.size() == 0 ? null : PrintSupport.PostScript.of(out.toByteArray()));
    }

    private static PrintRequestAttributeSet requestSet(Attribute... attributes) {
        PrintRequestAttributeSet set = new HashPrintRequestAttributeSet();
        for (Attribute attribute : attributes) {
            set.add(attribute);
        }
        return set;
    }

    private static List<Check> jobChecks() throws IOException {
        List<Check> checks = new ArrayList<>();
        BufferedImage source = sourceImage();
        byte[] png = encode(source, "png");
        byte[] gif = encode(source, "gif");
        byte[] jpeg = encode(source, "jpeg");
        Path jpegFile = Edt.tempDir().resolve("print-javax-print.jpg");
        Files.write(jpegFile, jpeg);
        PrintRequestAttributeSet a4 = requestSet(MediaSizeName.ISO_A4, new JobName("showcase image", Locale.ENGLISH));

        checks.add(Check.pass("ImageIO encodings : PNG, GIF, JPEG sizes", png.length + ", " + gif.length + ", " + jpeg.length
                + " bytes"));
        Job pngJob = job(job -> job.print(new SimpleDoc(png, DocFlavor.BYTE_ARRAY.PNG, null), a4));
        checks.add(Checks.expect("PNG (BYTE_ARRAY.PNG)", "printed ; events : completed ; 1 page(s), 1 image(s)",
                pngJob::summary));
        Job gifJob = job(job -> job.print(new SimpleDoc(new ByteArrayInputStream(gif), DocFlavor.INPUT_STREAM.GIF, null), a4));
        checks.add(Checks.expect("GIF (INPUT_STREAM.GIF)", "printed ; events : completed ; 1 page(s), 1 image(s)",
                gifJob::summary));
        Job jpegJob = job(job -> job.print(new SimpleDoc(jpegFile.toUri().toURL(), DocFlavor.URL.JPEG, null), a4));
        checks.add(Checks.expect("JPEG (URL.JPEG, a file URL)", "printed ; events : completed ; 1 page(s), 1 image(s)",
                jpegJob::summary));
        checks.add(Check.pass("PostScript SHA-256 : PNG / GIF / JPEG", hash(pngJob) + " / " + hash(gifJob) + " / "
                + hash(jpegJob)));
        // PNG is lossless, GIF of this image is not (256 colors) : the PNG and GIF documents differ
        checks.add(Checks.expect("PNG and GIF documents differ, PNG printed twice is identical", "true, true", () -> {
            Job again = job(job -> job.print(new SimpleDoc(png, DocFlavor.BYTE_ARRAY.PNG, null), a4));
            return !hash(pngJob).equals(hash(gifJob)) + ", " + hash(pngJob).equals(hash(again));
        }));
        checks.add(Checks.expect("doc attribute OrientationRequested.LANDSCAPE : another page transform", "true, 1",
                () -> {
                    HashDocAttributeSet docAttributes = new HashDocAttributeSet();
                    docAttributes.add(OrientationRequested.LANDSCAPE);
                    Job landscape = job(job -> job.print(new SimpleDoc(png, DocFlavor.BYTE_ARRAY.PNG, docAttributes), a4));
                    return !hash(landscape).equals(hash(pngJob)) + ", " + landscape.postScript().pages();
                }));
        checks.add(Checks.expect("a Printable (SERVICE_FORMATTED.PRINTABLE, NA_LETTER)",
                "printed ; events : completed ; 3 page(s), 0 image(s)",
                () -> job(job -> job.print(new SimpleDoc(new SampleBook.Sequence(null, 0, 3),
                        DocFlavor.SERVICE_FORMATTED.PRINTABLE, null), requestSet(MediaSizeName.NA_LETTER))).summary()));
        checks.add(Checks.expect("an unsupported flavor (STRING.TEXT_PLAIN)",
                "sun.print.PrintJobFlavorException: invalid flavor ; events : failed",
                () -> job(job -> job.print(new SimpleDoc("text", DocFlavor.STRING.TEXT_PLAIN, null), null)).summary()));
        checks.add(Checks.expect("Fidelity.FIDELITY_TRUE with NumberUp 2",
                "sun.print.PrintJobAttributeException: unsupported category: class javax.print.attribute.standard.NumberUp ; "
                        + "events : failed",
                () -> job(job -> job.print(new SimpleDoc(png, DocFlavor.BYTE_ARRAY.PNG, null),
                        requestSet(Fidelity.FIDELITY_TRUE, new NumberUp(2)))).summary()));
        checks.add(Checks.expect("a failing Printable",
                "javax.print.PrintException: java.awt.print.PrinterException: simulated failure on page 2 <- "
                        + "java.awt.print.PrinterException: simulated failure on page 2 ; events : failed ; "
                        + "1 page(s), 0 image(s)",
                () -> job(job -> job.print(new SimpleDoc(new SampleBook.Failing(), DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                        null), null)).summary()));
        checks.add(Checks.expect("CancelablePrintJob.cancel() before print()",
                "true, javax.print.PrintException: Job is not yet submitted.", () -> {
                    DocPrintJob job = PrintSupport.postScriptService(new ByteArrayOutputStream()).createPrintJob();
                    try {
                        ((CancelablePrintJob) job).cancel();
                        return (job instanceof CancelablePrintJob) + ", canceled";
                    } catch (PrintException e) {
                        return (job instanceof CancelablePrintJob) + ", " + Checks.describe(e);
                    }
                }));
        checks.add(Checks.expect("CancelablePrintJob.cancel() while printing page 2",
                "javax.print.PrintException: java.awt.print.PrinterAbortException <- java.awt.print.PrinterAbortException ; "
                        + "events : canceled, failed",
                () -> {
                    return job(job -> job.print(new SimpleDoc(new CancelingPrintable(job),
                            DocFlavor.SERVICE_FORMATTED.PRINTABLE, null), null)).summary();
                }));
        checks.add(Checks.expect("cancel() after the job, print() again",
                "javax.print.PrintException: Job could not be cancelled. / "
                        + "javax.print.PrintException: already printing",
                () -> {
                    DocPrintJob job = PrintSupport.postScriptService(new ByteArrayOutputStream()).createPrintJob();
                    job.print(new SimpleDoc(png, DocFlavor.BYTE_ARRAY.PNG, null), null);
                    String cancel;
                    try {
                        ((CancelablePrintJob) job).cancel();
                        cancel = "canceled";
                    } catch (PrintException e) {
                        cancel = Checks.describe(e);
                    }
                    try {
                        job.print(new SimpleDoc(png, DocFlavor.BYTE_ARRAY.PNG, null), null);
                        return cancel + " / printed";
                    } catch (PrintException e) {
                        return cancel + " / " + Checks.describe(e);
                    }
                }));
        checks.add(Checks.expect("job.getAttributes() : job name, job.getPrintService()", "showcase image, Postscript output",
                () -> {
                    DocPrintJob job = PrintSupport.postScriptService(new ByteArrayOutputStream()).createPrintJob();
                    job.print(new SimpleDoc(png, DocFlavor.BYTE_ARRAY.PNG, null), a4);
                    return job.getAttributes().get(JobName.class) + ", " + job.getPrintService().getName();
                }));
        return checks;
    }

    private static String hash(Job job) {
        return job.postScript() == null ? "none" : job.postScript().sha256();
    }

    /**
     * Cancels its own print job while printing its second page.
     */
    static final class CancelingPrintable implements Printable {

        private final DocPrintJob job;
        private boolean canceled;

        CancelingPrintable(DocPrintJob job) {
            this.job = job;
        }

        @Override
        public int print(java.awt.Graphics g, PageFormat format, int page) {
            if (page > 2) {
                return NO_SUCH_PAGE;
            }
            if (page == 1 && !canceled) {
                canceled = true;
                try {
                    ((CancelablePrintJob) job).cancel();
                } catch (PrintException e) {
                    throw new IllegalStateException(e);
                }
            }
            g.drawRect(100, 100, 100, 100);
            return PAGE_EXISTS;
        }
    }

    // ---------------------------------------------------------------------------------------------------- format

    private static String names(Class<?>[] classes) {
        return String.join(" ", new TreeSet<>(Arrays.stream(classes).map(Class::getSimpleName).toList()));
    }

    private static String sorted(DocFlavor[] flavors) {
        return String.join(" | ", new TreeSet<>(Arrays.stream(flavors).map(DocFlavor::toString).toList()));
    }

    static String attributes(AttributeSet set) {
        return String.join(" ", new TreeSet<>(Arrays.stream(set.toArray()).map(a -> a.getName() + "=" + a).toList()));
    }

    private static String count(Object values) {
        return values instanceof Object[] array ? array.length + " values" : String.valueOf(values);
    }
}
