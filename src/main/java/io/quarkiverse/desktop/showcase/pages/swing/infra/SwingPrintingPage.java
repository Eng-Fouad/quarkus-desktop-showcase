package io.quarkiverse.desktop.showcase.pages.swing.infra;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.print.DocFlavor;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.desktop.awt.EdtExecutor;
import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Swing printing : {@link JTable#print(JTable.PrintMode, MessageFormat, MessageFormat, boolean,
 * PrintRequestAttributeSet, boolean, javax.print.PrintService)} (FIT_WIDTH and NORMAL, header and footer) and
 * {@link javax.swing.text.JTextComponent#print(MessageFormat, MessageFormat, boolean, javax.print.PrintService,
 * PrintRequestAttributeSet, boolean)} (plain text and HTML) to a PostScript {@link StreamPrintService} in memory, the
 * same {@link Printable}s rendered page by page into images, and {@link JComponent#printAll} at scale 1 and 2.
 * <p>
 * Never a real printer : the jobs go to the PostScript stream print service (service provider
 * {@code sun.print.PSStreamPrinterFactory}), no dialog is shown ({@code showPrintDialog} and {@code interactive} are
 * false). The PostScript prolog lists the PostScript fonts of {@code <java.home>/lib/psfontj2d.properties} (35 fonts)
 * : a native executable without that file falls back to 12 fonts and draws text as shapes.
 */
@Singleton
public class SwingPrintingPage implements FeaturePage {

    private static final double A4_W = 595.275590551181;
    private static final double A4_H = 841.8897637795275;
    private static final double MARGIN = 72;
    private static final double THUMB = 0.3;

    /** Reads the extra snapshots on the EDT. */
    @Inject
    EdtExecutor edt;
    /** The page transform of a landscape page : {@code [0.0 -a b 0.0 tx ty] concat}. */
    private static final String ROTATED = "^\\[0\\.0 -[0-9.]+ [0-9.]+ 0\\.0 [0-9.]+ [0-9.]+\\] concat$";

    // per build state
    private Demo demo;

    private static final class Demo {
        JPanel content;
        JTable table;
        JTextArea text;
        JEditorPane html;
        JPanel form;
        JPanel thumbnails;
        ChecksView checks;
        final Map<String, BufferedImage> extras = new TreeMap<>();
        final AtomicBoolean printingSeen = new AtomicBoolean();
        final AtomicBoolean paintingSeen = new AtomicBoolean();
    }

    @Override
    public String id() {
        return "swing-printing";
    }

    @Override
    public String title() {
        return "Swing printing";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 960;
    }

    @Override
    public Component build() {
        Demo d = new Demo();
        demo = d;
        d.table = table();
        JScrollPane tableScroll = new JScrollPane(d.table);
        tableScroll.setPreferredSize(new Dimension(500, 180));
        d.text = new JTextArea(text(), 8, 30);
        JScrollPane textScroll = new JScrollPane(d.text);
        textScroll.setPreferredSize(new Dimension(250, 180));
        d.html = new JEditorPane("text/html", html());
        d.html.setEditable(false);
        // show the top of the document (setting the content moves the caret to its end)
        d.html.setCaretPosition(0);
        JScrollPane htmlScroll = new JScrollPane(d.html);
        htmlScroll.setPreferredSize(new Dimension(250, 180));
        d.form = form(d);
        d.thumbnails = InfraSupport.row(10, Ui.caption("pages : pending"));
        d.checks = ChecksView.table("PostScript output and printables", List.of(Check.info("printing", "pending")));
        d.content = InfraSupport.column(14,
                Ui.text("A table, a plain text area and an HTML editor pane printed to a PostScript stream print "
                        + "service (in memory, never a printer), and rendered page by page into images (thumbnails "
                        + "below, full pages as extra snapshots). The form on the right is printed with printAll at "
                        + "scale 1 and 2.", 1000),
                InfraSupport.row(14, tableScroll, textScroll, htmlScroll),
                Ui.title("Printable pages (A4, 1 inch margins, 72 dpi, shown at 30 %)"),
                d.thumbnails,
                InfraSupport.row(14, InfraSupport.column(4, d.form, Ui.caption("the form (on screen)")),
                        InfraSupport.column(4, Ui.caption("form.printAll at scale 1 and 2 : see the extra snapshots"))),
                d.checks);
        return d.content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Demo d = demo;
        return Edt.rounds(2).thenAccept(v -> {
            List<Check> checks = new ArrayList<>();
            List<Component> thumbs = new ArrayList<>();
            printAll(d, checks);
            services(checks);
            PrintRequestAttributeSet portrait = attributes(OrientationRequested.PORTRAIT, "showcase table");

            // JTable.print, FIT_WIDTH and NORMAL
            Ps fit = print(checks, "JTable.print FIT_WIDTH", out -> d.table.print(JTable.PrintMode.FIT_WIDTH,
                    new MessageFormat("JTable.print FIT_WIDTH"), new MessageFormat("Page {0}"), false, portrait, false,
                    out));
            Ps normal = print(checks, "JTable.print NORMAL", out -> d.table.print(JTable.PrintMode.NORMAL,
                    new MessageFormat("JTable.print NORMAL"), new MessageFormat("Page {0}"), false,
                    attributes(OrientationRequested.PORTRAIT, "showcase table normal"), false, out));
            List<BufferedImage> fitPages = pages(checks, "JTable.getPrintable(FIT_WIDTH)",
                    () -> d.table.getPrintable(JTable.PrintMode.FIT_WIDTH, new MessageFormat("JTable.print FIT_WIDTH"),
                            new MessageFormat("Page {0}")));
            List<BufferedImage> normalPages = pages(checks, "JTable.getPrintable(NORMAL)",
                    () -> d.table.getPrintable(JTable.PrintMode.NORMAL, new MessageFormat("JTable.print NORMAL"),
                            new MessageFormat("Page {0}")));
            checks.add(Checks.expect("FIT_WIDTH : PostScript pages = printable pages", "true",
                    () -> fit != null && fit.pages() == fitPages.size()));
            checks.add(Checks.expect("NORMAL mode splits the columns : more pages than FIT_WIDTH", "true",
                    () -> normal != null && normal.pages() > fit.pages() && normalPages.size() > fitPages.size()));

            // JTextComponent.print, plain and HTML
            Ps plain = print(checks, "JTextArea.print", out -> d.text.print(new MessageFormat("JTextArea.print"),
                    new MessageFormat("- {0} -"), false, out, attributes(OrientationRequested.PORTRAIT, "text"),
                    false));
            Ps html = print(checks, "JEditorPane.print (HTML)", out -> d.html.print(new MessageFormat("HTML"),
                    new MessageFormat("- {0} -"), false, out, attributes(OrientationRequested.LANDSCAPE, "html"),
                    false));
            List<BufferedImage> textPages = pages(checks, "JTextArea.getPrintable",
                    () -> d.text.getPrintable(new MessageFormat("JTextArea.print"), new MessageFormat("- {0} -")));
            List<BufferedImage> htmlPages = pages(checks, "JEditorPane.getPrintable",
                    () -> d.html.getPrintable(new MessageFormat("HTML"), new MessageFormat("- {0} -")));
            checks.add(Checks.expect("JTextArea : PostScript pages = printable pages", "true",
                    () -> plain != null && plain.pages() == textPages.size()));
            checks.add(Checks.expect("HTML in landscape : rotated page transform", "true",
                    () -> html != null && html.count(ROTATED) > 0));
            checks.add(Checks.expect("components enabled again after printing", "true",
                    () -> d.text.isEnabled() && d.html.isEnabled()));

            for (int i = 0; i < fitPages.size() && i < 3; i++) {
                thumbs.add(thumbnail(fitPages.get(i), "table FIT_WIDTH page " + (i + 1)));
                d.extras.put("table-fit-page-" + (i + 1), fitPages.get(i));
            }
            if (!normalPages.isEmpty()) {
                d.extras.put("table-normal-page-2", normalPages.get(Math.min(1, normalPages.size() - 1)));
            }
            if (!textPages.isEmpty()) {
                thumbs.add(thumbnail(textPages.get(0), "text area page 1"));
                d.extras.put("text-page-1", textPages.get(0));
            }
            if (!htmlPages.isEmpty()) {
                thumbs.add(thumbnail(htmlPages.get(0), "HTML page 1"));
                d.extras.put("html-page-1", htmlPages.get(0));
            }
            d.thumbnails.removeAll();
            thumbs.forEach(d.thumbnails::add);
            d.checks.setChecks(checks);
            d.content.revalidate();
        });
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        Demo d = demo;
        return CompletableFuture.supplyAsync(() -> new TreeMap<>(d.extras), edt);
    }

    @Override
    public void dispose(Component content) {
        demo = null;
    }

    // ------------------------------------------------------------------------------------------------ content

    private static JTable table() {
        String[] products = { "Coffee", "Tea", "Dates", "Bread", "Milk", "Rice", "Honey", "Sugar", "Salt", "Flour" };
        String[] categories = { "Beverages", "Beverages", "Fruit", "Bakery", "Dairy", "Grocery", "Grocery",
                "Grocery", "Grocery", "Bakery" };
        DefaultTableModel model = new DefaultTableModel(
                new Object[] { "#", "Product", "Category", "Quantity", "Unit price", "Total" }, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 || column == 2 ? String.class : column == 0 || column == 3 ? Integer.class
                        : Double.class;
            }
        };
        for (int i = 0; i < 60; i++) {
            int quantity = 1 + (i * 7) % 23;
            double price = 0.75 + (i % 10) * 1.25;
            model.addRow(new Object[] { i + 1, products[i % products.length], categories[i % categories.length],
                    quantity, price, Math.round(quantity * price * 100) / 100.0 });
        }
        JTable table = new JTable(model);
        int[] widths = { 40, 170, 130, 90, 100, 100 };
        for (int c = 0; c < widths.length; c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        }
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        return table;
    }

    private static String text() {
        StringBuilder sb = new StringBuilder();
        String[] words = { "Swing", "prints", "text", "components", "through", "a", "TextComponentPrintable", "that",
                "lays", "out", "a", "copy", "of", "the", "document", "at", "the", "page", "width" };
        for (int line = 1; line <= 80; line++) {
            sb.append(String.format(Locale.ROOT, "%02d ", line));
            for (int w = 0; w < 6 + line % 5; w++) {
                sb.append(words[(line * 3 + w) % words.length]).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String html() {
        return "<html><body style='font-family: sans-serif'>"
                + "<h1>Printing HTML</h1>"
                + "<p>An <b>HTMLDocument</b> printed by <i>JEditorPane.print</i> : headings, <u>styles</u>, "
                + "<span style='color: #1565C0'>colors</span>, lists and tables.</p>"
                + "<ul><li>First item</li><li>Second item<ul><li>nested</li></ul></li><li>Third item</li></ul>"
                + "<ol><li>one</li><li>two</li><li>three</li></ol>"
                + "<table border='1' cellpadding='4'><tr><th>Format</th><th>Service</th></tr>"
                + "<tr><td>PostScript</td><td>StreamPrintService</td></tr>"
                + "<tr><td>Images</td><td>Printable.print</td></tr></table>"
                + "<p style='text-align: right'>right aligned paragraph</p>"
                + "<pre>preformatted   text</pre></body></html>";
    }

    private static JPanel form(Demo d) {
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (isPaintingForPrint()) {
                    d.printingSeen.set(true);
                } else {
                    d.paintingSeen.set(true);
                }
            }
        };
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Order"),
                BorderFactory.createEmptyBorder(4, 6, 6, 6)));
        form.add(new JLabel("Customer:"));
        form.add(new JTextField("Duke", 10));
        form.add(new JLabel("Express:"));
        form.add(new JCheckBox("yes", true));
        form.add(new JLabel("Total:"));
        form.add(new JLabel("123.45"));
        form.add(new JButton("Print"));
        form.add(new JButton("Cancel"));
        form.setPreferredSize(new Dimension(260, 150));
        return form;
    }

    // ------------------------------------------------------------------------------------------------ printing

    private static PrintRequestAttributeSet attributes(OrientationRequested orientation, String job) {
        PrintRequestAttributeSet set = new HashPrintRequestAttributeSet();
        set.add(MediaSizeName.ISO_A4);
        set.add(orientation);
        set.add(new MediaPrintableArea(25.4f, 25.4f, 159.2f, 246.2f, MediaPrintableArea.MM));
        set.add(new JobName(job, Locale.ROOT));
        return set;
    }

    private interface PrintAction {
        Object print(StreamPrintService service) throws PrinterException;
    }

    /** A PostScript document produced by a stream print service. */
    private record Ps(byte[] bytes) {

        String text() {
            return new String(bytes, StandardCharsets.ISO_8859_1).replace("\r", "");
        }

        int pages() {
            return count("^%%Page: ");
        }

        int count(String regex) {
            Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text());
            int n = 0;
            while (m.find()) {
                n++;
            }
            return n;
        }

        /** Fonts listed in the prolog ({@code /FL [ ... ] D}). */
        int prologFonts() {
            String t = text();
            int start = t.indexOf("/FL [");
            int end = t.indexOf("] D", start);
            if (start < 0 || end < 0) {
                return -1;
            }
            return (int) t.substring(start + 5, end).lines().filter(l -> !l.isBlank()).count();
        }

        String pageSize() {
            Matcher m = Pattern.compile("/PageSize \\[([0-9.]+) ([0-9.]+)\\]").matcher(text());
            return m.find() ? Checks.num(Double.parseDouble(m.group(1)), 2) + " x "
                    + Checks.num(Double.parseDouble(m.group(2)), 2) : "none";
        }
    }

    private static StreamPrintServiceFactory postScriptFactory() {
        StreamPrintServiceFactory[] factories = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(
                DocFlavor.SERVICE_FORMATTED.PAGEABLE, "application/postscript");
        return factories.length == 0 ? null : factories[0];
    }

    /**
     * Runs {@code action} with a PostScript stream print service writing into memory, and checks the output.
     */
    private static Ps print(List<Check> checks, String name, PrintAction action) {
        StreamPrintServiceFactory factory = postScriptFactory();
        if (factory == null) {
            checks.add(Check.fail(name, "no PostScript stream print service"));
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamPrintService service = factory.getPrintService(out);
        Check result = Checks.run(name + " (no dialog, not interactive)", () -> action.print(service));
        checks.add(result);
        service.dispose();
        if (!Boolean.TRUE.equals(result.ok())) {
            return null;
        }
        Ps ps = new Ps(out.toByteArray());
        checks.add(Checks.expect(name + " : header, trailer", "%!PS-Adobe-3.0, %%EOF",
                () -> ps.text().lines().findFirst().orElse("") + ", "
                        + (ps.text().stripTrailing().endsWith("%%EOF") ? "%%EOF" : "no %%EOF")));
        checks.add(Checks.info(name + " : pages, size", () -> ps.pages() + " pages, " + ps.bytes().length + " bytes"));
        checks.add(Checks.expect(name + " : prolog fonts (psfontj2d.properties)", 35, ps::prologFonts));
        checks.add(Checks.expect(name + " : A4 page size", "595.28 x 841.89", ps::pageSize));
        // with the PostScript fonts of psfontj2d.properties, text is drawn with "<hex> width x y S" operators
        // (otherwise as filled glyph outlines)
        checks.add(Checks.expect(name + " : text drawn with PostScript fonts", "true", () -> ps.count(" S$") > 0));
        checks.add(Checks.info(name + " : operators S (text), F (set font), WF / EF (fill)",
                () -> ps.count(" S$") + ", " + ps.count(" F$") + ", " + ps.count("(^| )(WF|EF)$")));
        checks.add(Checks.info(name + " : hash", () -> Checks.sha256(ps.bytes())));
        return ps;
    }

    private interface PrintableSupplier {
        Printable get() throws Exception;
    }

    private static PageFormat a4() {
        Paper paper = new Paper();
        paper.setSize(A4_W, A4_H);
        paper.setImageableArea(MARGIN, MARGIN, A4_W - 2 * MARGIN, A4_H - 2 * MARGIN);
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(PageFormat.PORTRAIT);
        return format;
    }

    /**
     * Renders the pages of a printable into images (72 dpi) until {@code NO_SUCH_PAGE}.
     */
    private static List<BufferedImage> pages(List<Check> checks, String name, PrintableSupplier supplier) {
        List<BufferedImage> pages = new ArrayList<>();
        PageFormat format = a4();
        checks.add(Checks.run(name + " : pages rendered into images", () -> {
            Printable printable = supplier.get();
            for (int index = 0; index < 20; index++) {
                BufferedImage image = new BufferedImage((int) Math.ceil(A4_W), (int) Math.ceil(A4_H),
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                int result;
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, image.getWidth(), image.getHeight());
                    g.setColor(new Color(0xE0E0E0));
                    g.drawRect((int) MARGIN - 1, (int) MARGIN - 1, (int) (A4_W - 2 * MARGIN) + 1,
                            (int) (A4_H - 2 * MARGIN) + 1);
                    result = printable.print(g, format, index);
                } finally {
                    g.dispose();
                }
                if (result == Printable.NO_SUCH_PAGE) {
                    break;
                }
                pages.add(image);
            }
            return pages.size() + " pages";
        }));
        return pages;
    }

    private static Component thumbnail(BufferedImage page, String caption) {
        int w = (int) Math.round(page.getWidth() * THUMB);
        int h = (int) Math.round(page.getHeight() * THUMB);
        BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(page, 0, 0, w, h, null);
            g.setColor(new Color(0x90A4AE));
            g.drawRect(0, 0, w - 1, h - 1);
        } finally {
            g.dispose();
        }
        return InfraSupport.column(4, Ui.image(thumb), Ui.caption(caption));
    }

    private static void services(List<Check> checks) {
        checks.add(Checks.expect("StreamPrintServiceFactory PostScript (PAGEABLE) : factories", 1,
                () -> StreamPrintServiceFactory.lookupStreamPrintServiceFactories(DocFlavor.SERVICE_FORMATTED.PAGEABLE,
                        "application/postscript").length));
        checks.add(Checks.expect("PrinterJob.lookupStreamPrintServices(\"application/postscript\")", 1,
                () -> PrinterJob.lookupStreamPrintServices("application/postscript").length));
        checks.add(Checks.expect("stream print service : name, output format", "Postscript output, "
                + "application/postscript", () -> {
                    StreamPrintService service = postScriptFactory().getPrintService(new ByteArrayOutputStream());
                    try {
                        return service.getName() + ", " + service.getOutputFormat();
                    } finally {
                        service.dispose();
                    }
                }));
        checks.add(Checks.info("PrinterJob.getPrinterJob() (never used to print here)",
                () -> PrinterJob.getPrinterJob().getClass().getName()));
        checks.add(Checks.expect("PrinterJob.setPrintService(stream service) accepted", "true", () -> {
            PrinterJob job = PrinterJob.getPrinterJob();
            StreamPrintService service = postScriptFactory().getPrintService(new ByteArrayOutputStream());
            try {
                job.setPrintService(service);
                return job.getPrintService() == service;
            } finally {
                service.dispose();
            }
        }));
    }

    private static void printAll(Demo d, List<Check> checks) {
        JPanel form = d.form;
        d.printingSeen.set(false);
        BufferedImage one = render(form, 1);
        BufferedImage two = render(form, 2);
        d.extras.put("form-printall-1x", one);
        d.extras.put("form-printall-2x", two);
        checks.add(Checks.expect("printAll : isPaintingForPrint() seen by paintComponent", "true",
                d.printingSeen::get));
        checks.add(Checks.expect("printAll at scale 1 and 2 : image sizes", "260x150, 520x300",
                () -> one.getWidth() + "x" + one.getHeight() + ", " + two.getWidth() + "x" + two.getHeight()));
        checks.add(Checks.expect("printAll : form background pixel at scale 1 and 2", "true",
                () -> one.getRGB(130, 146) == two.getRGB(260, 293) && (one.getRGB(130, 146) >>> 24) == 0xFF));
    }

    private static BufferedImage render(JComponent c, double scale) {
        BufferedImage image = new BufferedImage((int) Math.ceil(c.getWidth() * scale),
                (int) Math.ceil(c.getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(scale, scale);
            c.printAll(g);
        } finally {
            g.dispose();
        }
        return image;
    }
}
