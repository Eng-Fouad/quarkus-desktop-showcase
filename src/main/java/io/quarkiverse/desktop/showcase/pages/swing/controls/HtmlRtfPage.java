package io.quarkiverse.desktop.showcase.pages.swing.controls;

import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.RESOURCES;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.caption;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.column;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.fitHeight;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.row;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.section;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JToolTip;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.ComponentView;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.ElementIterator;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabStop;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.CSS;
import javax.swing.text.html.FormSubmitEvent;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLWriter;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.MinimalHTMLWriter;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.rtf.RTFEditorKit;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.Readiness;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.EventLog;

/**
 * HTML and RTF : {@code JEditorPane.setPage(URL)} of an HTML classpath resource (linked and inline CSS, tables, lists,
 * PNG/JPEG/GIF images and a missing one, a form, links, an {@code <object>} tag), a hyperlink activated by a synthetic
 * click, the form submitted ({@code FormSubmitEvent}), {@code HTMLDocument} manipulation, {@code HTMLWriter} and
 * {@code MinimalHTMLWriter}, HTML in {@code JLabel}/{@code JButton}/{@code JToolTip}, RTF read with the 5 RTF character
 * sets, font character sets ({@code \fcharset}) and unicode escapes, RTF written and read back.
 * <p>
 * Native paths exercised on purpose : {@code resource:} URLs (page, relative style sheet, relative images, form action),
 * the content type of a resource connection, editor kits created by class name, {@code default.css} and the HTML 3.2 DTD
 * ({@code html32.bdtd}), the RTF character set tables ({@code charsets/*.txt}), the charsets {@code windows-1251},
 * {@code windows-1253} and {@code MS932} (all charsets in the image?), the Toolkit image decoders (JPEG through
 * {@code libjavajpeg} and JNI callbacks, GIF, PNG), the {@code html.missingImage} icon resource.
 */
@Singleton
public class HtmlRtfPage implements FeaturePage {

    private static final String PAGE = RESOURCES + "html/showcase.html";
    private static final int WIDTH = 960;
    private static final String[][] CHARSETS = {
            { "ansi", "ansi: caf\u00E9 \u00FCber se\u00F1or stra\u00DFe gar\u00E7on \u00E0 la carte \u00A35" },
            { "mac", "mac: caf\u00E9 \u00FCber se\u00F1or stra\u00DFe gar\u00E7on \u00E0 la carte \u00A35" },
            { "pc", "pc: caf\u00E9 \u00FCber se\u00F1or stra\u00DFe gar\u00E7on \u00E0 la carte \u00A35" },
            { "pca", "pca: caf\u00E9 \u00FCber se\u00F1or stra\u00DFe gar\u00E7on \u00E0 la carte \u00A35 "
                    + "sm\u00F8rrebr\u00F8d" },
            { "next", "next: caf\u00E9 \u00FCber se\u00F1or stra\u00DFe gar\u00E7on \u00E0 la carte \u00A35" } };
    private static final String MANIPULATED = "<html><body><h2 id=\"title\">Before</h2><ul id=\"list\"><li>one</li></ul>"
            + "<p id=\"para\">Paragraph</p></body></html>";

    // per build state
    private ChecksView results;
    private final Readiness readiness = new Readiness();
    private JEditorPane page;
    private boolean loaded;
    private EventLog links;
    private String formData;
    private String formDetails;
    private List<Check> rtfChecks;
    private List<Check> manipulationChecks;

    @Override
    public String id() {
        return "swing-html-rtf";
    }

    @Override
    public String title() {
        return "HTML and RTF";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 40;
    }

    /**
     * An HTML editor kit whose images load synchronously (deterministic rendering) and which does not submit forms
     * itself (it fires a {@code FormSubmitEvent}).
     */
    static final class SyncImagesKit extends HTMLEditorKit {

        SyncImagesKit() {
            setAutoFormSubmission(false);
        }

        @Override
        public ViewFactory getViewFactory() {
            return new HTMLFactory() {
                @Override
                public View create(Element element) {
                    View view = super.create(element);
                    if (view instanceof ImageView image) {
                        image.setLoadsSynchronously(true);
                    }
                    return view;
                }
            };
        }
    }

    @Override
    public Component build() throws Exception {
        results = ChecksView.table("Checks", List.of(Check.info("state", "pending")));
        loaded = false;
        links = new EventLog();
        formData = null;
        formDetails = null;

        page = new JEditorPane();
        page.setEditable(false);
        page.setEditorKitForContentType("text/html", new SyncImagesKit());
        page.setContentType("text/html");
        page.addPropertyChangeListener("page", e -> loaded = true);
        page.addHyperlinkListener(e -> {
            if (e instanceof FormSubmitEvent submit) {
                formDetails = submit.getMethod() + ", target " + submit.getTarget() + ", action URL "
                        + (submit.getURL() != null ? "resolved" : "null");
                formData = submit.getData();
            } else {
                links.add(e.getEventType() + " " + e.getDescription() + " (" + e.getSourceElement().getName()
                        + ", URL " + (e.getURL() != null ? "resolved" : "null") + ")");
            }
        });
        // asynchronous : HTMLDocument has a load priority, JEditorPane loads it with a SwingWorker ("page" event)
        page.setPage(Edt.resource(PAGE));
        page.setPreferredSize(new Dimension(WIDTH, 400));

        JPanel content = column(10,
                Ui.text("An HTML resource shown by JEditorPane.setPage(URL), HTML in Swing components, HTMLDocument "
                        + "manipulation and writers, and RTF documents read and written by RTFEditorKit.", 1000),
                section("JEditorPane.setPage(URL) : linked style sheet, tables, lists, images, form, links", page),
                section("HTML in JLabel, JButton and JToolTip (BasicHTML)", htmlComponents()),
                section("HTMLDocument : setInnerHTML, insertAfterStart, insertBeforeEnd, insertAfterEnd, "
                        + "insertBeforeStart, setOuterHTML / HTMLWriter output", manipulation()),
                section("RTFEditorKit : styled.rtf (fonts, colors, indents, tabs, \\fcharset, \\u) / the 5 RTF "
                        + "character sets", rtf()),
                results);
        content.setOpaque(true);
        content.setBackground(Color.WHITE);
        ControlsSupport.readyWhenShown(this, content);
        return content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        return readiness.get(content, this::prepare);
    }

    private CompletionStage<?> prepare(Component content) {
        ChecksView view = results;
        JEditorPane pane = page;
        return Edt.until(() -> loaded, 15_000, "HTML page loaded")
                .thenCompose(v -> {
                    fitHeight(pane, WIDTH);
                    ((JComponent) content).revalidate();
                    return Edt.rounds(3);
                })
                .thenCompose(v -> {
                    clickLink(pane);
                    submitForm(pane);
                    return Edt.until(() -> formData != null, 5_000, "form submit event");
                })
                .handle((v, error) -> error)
                .thenCompose(error -> {
                    view.setChecks(checks(error));
                    return Edt.rounds(2);
                })
                .thenCompose(v -> Edt.stable(content, 3_000));
    }

    @Override
    public void dispose(Component content) {
        readiness.reset();
        results = null;
        page = null;
        links = null;
        rtfChecks = null;
        manipulationChecks = null;
    }

    // ------------------------------------------------------------------------------------------------ interactions

    /**
     * A synthetic click (dispatched to the component, never dropped) on the relative link : the HTMLEditorKit
     * LinkController fires a HyperlinkEvent ACTIVATED.
     */
    private static void clickLink(JEditorPane pane) {
        try {
            String text = pane.getDocument().getText(0, pane.getDocument().getLength());
            int offset = text.indexOf("relative link") + 3;
            Rectangle2D r = pane.modelToView2D(offset);
            pane.dispatchEvent(new MouseEvent(pane, MouseEvent.MOUSE_CLICKED, 0, 0, (int) r.getCenterX(),
                    (int) r.getCenterY(), 1, false, MouseEvent.BUTTON1));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Clicks the submit button of the form (a JButton created by FormView).
     */
    private static void submitForm(JEditorPane pane) {
        JButton send = find(pane, JButton.class, "Send");
        if (send != null) {
            send.doClick(0);
        }
    }

    private static <C extends Component> C find(Container container, Class<C> type, String text) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child) && (text == null || text.equals(textOf(child)))) {
                return type.cast(child);
            }
            if (child instanceof Container c) {
                C found = find(c, type, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String textOf(Component c) {
        return c instanceof JButton b ? b.getText() : c instanceof JLabel l ? l.getText() : null;
    }

    // ----------------------------------------------------------------------------------------------------- gallery

    private static JComponent htmlComponents() {
        String star = Edt.resource(RESOURCES + "icons/star-16.png").toExternalForm();
        JLabel label = new JLabel("<html><table border=1 cellpadding=2><tr><td bgcolor=#E3F2FD><b>JLabel</b></td>"
                + "<td><font color=#C62828>red</font> <i>italic</i> <img src=\"" + star + "\" width=16 height=16>"
                + "</td></tr><tr><td colspan=2><u>table in a label</u>, &euro; &#x263A;</td></tr></table></html>");
        JButton button = new JButton("<html><center><b>HTML</b> button<br><font size=-1 color=#1565C0>two lines"
                + "</font></center></html>");
        JToolTip tip = new JToolTip();
        tip.setTipText("<html><b>JToolTip</b> with <i>HTML</i><br>shown as a component</html>");
        JLabel disabled = new JLabel("<html><b>html.disable</b> = true</html>");
        disabled.putClientProperty("html.disable", Boolean.TRUE);
        disabled.setText(disabled.getText());
        JLabel plain = new JLabel("<html>a label without <b>closing</b> tags");
        JLabel object = new JLabel("<html>label : <object classid=\"javax.swing.JButton\"></object></html>");
        return row(16, column(2, caption("JLabel : table, colors, <img> of a resource URL"), label),
                column(2, caption("JButton"), button),
                column(2, caption("JToolTip (component)"), tip),
                column(2, caption("client property html.disable"), disabled, caption("unclosed tags"), plain),
                column(2, caption("<object> in a JLabel"), object));
    }

    private JComponent manipulation() throws Exception {
        HTMLEditorKit kit = new HTMLEditorKit();
        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(kit);
        pane.setEditable(false);
        pane.setText(MANIPULATED);
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        doc.setInnerHTML(doc.getElement("title"), "After <i>setInnerHTML</i>");
        doc.insertAfterStart(doc.getElement("list"), "<li>zero (insertAfterStart)</li>");
        doc.insertBeforeEnd(doc.getElement("list"), "<li>two (insertBeforeEnd)</li>");
        doc.insertAfterEnd(doc.getElement("list"), "<p>insertAfterEnd</p>");
        doc.insertBeforeStart(doc.getElement("para"), "<p>insertBeforeStart</p>");
        doc.setOuterHTML(doc.getElement("para"), "<p id=\"para\"><b>setOuterHTML</b> replaced the paragraph</p>");
        fitHeight(pane, 430);

        StringWriter out = new StringWriter();
        new HTMLWriter(out, doc, 0, doc.getLength()).write();
        String html = normalize(out.toString());

        manipulationChecks = new ArrayList<>();
        manipulationChecks.add(Checks.expect("HTMLDocument : list items, title text", "3, After setInnerHTML", () -> {
            Element title = doc.getElement("title");
            return doc.getElement("list").getElementCount() + ", "
                    + doc.getText(title.getStartOffset(), title.getEndOffset() - title.getStartOffset()).trim();
        }));
        manipulationChecks.add(Checks.expect("HTMLWriter output contains the edits", "true true true true", () -> html
                .contains("<i>setInnerHTML</i>") + " " + html.contains("zero (insertAfterStart)") + " "
                + html.contains("insertBeforeStart") + " " + html.contains("<b>setOuterHTML</b>")));
        manipulationChecks.add(Checks.info("HTMLWriter output SHA-256 (line separators normalized)",
                () -> Checks.sha256(html) + ", " + html.split("\n").length + " lines"));

        Component source = Ui.text(html.replace("\t", "  "), new Font(Font.MONOSPACED, Font.PLAIN, 10), 0x263238, 480);
        return row(20, column(2, caption("JEditorPane (text/html) after the edits"), pane),
                column(2, caption("HTMLWriter"), source));
    }

    private JComponent rtf() throws Exception {
        RTFEditorKit kit = new RTFEditorKit();
        StyledDocument doc = (StyledDocument) kit.createDefaultDocument();
        try (InputStream in = Edt.resourceStream(RESOURCES + "rtf/styled.rtf")) {
            kit.read(in, doc, 0);
        }
        JTextPane pane = new JTextPane(doc);
        pane.setEditable(false);
        fitHeight(pane, 560);

        ByteArrayOutputStream written = new ByteArrayOutputStream();
        kit.write(written, doc, 0, doc.getLength());
        String rtfText = normalize(written.toString(StandardCharsets.ISO_8859_1));

        StringBuilder decoded = new StringBuilder();
        rtfChecks = new ArrayList<>();
        for (String[] charset : CHARSETS) {
            String text = readRtf(Edt.resourceBytes(RESOURCES + "rtf/" + charset[0] + ".rtf"));
            decoded.append(text).append('\n');
            rtfChecks.add(Checks.expect("RTF \\" + charset[0] + " character set", charset[1], () -> text));
        }
        rtfChecks.addAll(styledRtfChecks(doc, rtfText));

        JTextArea charsets = new JTextArea(decoded.toString().trim());
        charsets.setEditable(false);
        charsets.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        charsets.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0xB0BEC5)));
        List<String> lines = List.of(rtfText.split("\n"));
        Component source = Ui.text(String.join("\n", lines.subList(0, Math.min(14, lines.size()))),
                new Font(Font.MONOSPACED, Font.PLAIN, 10), 0x263238, 360);
        return column(8, row(16, column(2, caption("JTextPane of the RTFReader document"), pane),
                column(2, caption("RTFEditorKit.write (first lines)"), source)),
                column(2, caption("\\ansi \\mac \\pc \\pca \\next : the same text in 5 character sets"), charsets));
    }

    private static String readRtf(byte[] bytes) throws IOException, BadLocationException {
        RTFEditorKit kit = new RTFEditorKit();
        Document doc = kit.createDefaultDocument();
        kit.read(new ByteArrayInputStream(bytes), doc, 0);
        return doc.getText(0, doc.getLength()).trim();
    }

    /**
     * {@code true} if the texts are equal, otherwise where they differ (code points around the first difference).
     */
    static String difference(String a, String b) {
        if (a.equals(b)) {
            return "true";
        }
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return "differs at " + i + " : " + codes(a, i) + " vs " + codes(b, i);
    }

    private static String codes(String s, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < Math.min(s.length(), from + 4); i++) {
            sb.append(String.format(Locale.ROOT, "U+%04X ", (int) s.charAt(i)));
        }
        return sb.toString().trim();
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private static String paragraph(StyledDocument doc, String startsWith) throws BadLocationException {
        String text = doc.getText(0, doc.getLength());
        int offset = text.indexOf(startsWith);
        if (offset < 0) {
            return "(not found)";
        }
        Element p = doc.getParagraphElement(offset);
        return doc.getText(p.getStartOffset(), p.getEndOffset() - p.getStartOffset()).trim();
    }

    private static AttributeSet run(StyledDocument doc, String text) throws BadLocationException {
        return doc.getCharacterElement(doc.getText(0, doc.getLength()).indexOf(text) + 1).getAttributes();
    }

    private static String hex(Color color) {
        return color == null ? "null" : String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xFFFFFF);
    }

    private static List<Check> styledRtfChecks(StyledDocument doc, String written) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("\\fcharset204 (windows-1251)", "Cyrillic (fcharset204, windows-1251): "
                + "\u041F\u0440\u0438\u0432\u0435\u0442", () -> paragraph(doc, "Cyrillic")));
        checks.add(Checks.expect("\\fcharset161 (windows-1253)", "Greek (fcharset161, windows-1253): "
                + "\u0391\u03B8\u03AE\u03BD\u03B1", () -> paragraph(doc, "Greek")));
        checks.add(Checks.expect("\\fcharset128 (MS932, double byte)", "Japanese (fcharset128, ms932): "
                + "\u65E5\u672C\u8A9E", () -> paragraph(doc, "Japanese")));
        checks.add(Checks.expect("\\u unicode escapes", "Unicode escapes: \u20AC \u65E5\u672C "
                + "\u0627\u0644\u0639\u0631\u0628\u064A\u0629", () -> paragraph(doc, "Unicode")));
        checks.add(Checks.expect("RTF runs : bold, italic, underline, red, family, size of large",
                "true true true #C62828 Monospaced 16", () -> StyleConstants.isBold(run(doc, "bold")) + " "
                        + StyleConstants.isItalic(run(doc, "italic")) + " "
                        + StyleConstants.isUnderline(run(doc, "underline")) + " "
                        + hex(StyleConstants.getForeground(run(doc, "red"))) + " "
                        + StyleConstants.getFontFamily(run(doc, "Monospaced")) + " "
                        + StyleConstants.getFontSize(run(doc, "large"))));
        checks.add(Checks.expect("RTF paragraphs : alignments (title, right, justified), left / first line indents",
                "1 2 3, 36.0 / -18.0", () -> {
                    String text = doc.getText(0, doc.getLength());
                    AttributeSet justified = doc.getParagraphElement(text.indexOf("A justified")).getAttributes();
                    return StyleConstants.getAlignment(doc.getParagraphElement(0).getAttributes()) + " "
                            + StyleConstants.getAlignment(doc.getParagraphElement(text.indexOf("Right aligned"))
                                    .getAttributes())
                            + " " + StyleConstants.getAlignment(justified) + ", "
                            + StyleConstants.getLeftIndent(justified) + " / " + StyleConstants.getFirstLineIndent(justified);
                }));
        checks.add(Checks.expect("RTF tab stops (\\tx2880 \\tqr\\tx7200) : reader attribute \"tabs\" (not a TabSet)",
                "144.0/0 360.0/1, TabSet null", () -> {
                    AttributeSet p = doc.getParagraphElement(doc.getText(0, doc.getLength()).indexOf("tab\t"))
                            .getAttributes();
                    List<String> stops = new ArrayList<>();
                    if (p.getAttribute("tabs") instanceof TabStop[] tabs) {
                        for (TabStop tab : tabs) {
                            stops.add(tab.getPosition() + "/" + tab.getAlignment());
                        }
                    }
                    return String.join(" ", stops) + ", TabSet " + StyleConstants.getTabSet(p);
                }));
        checks.add(Checks.expect("RTFEditorKit.write then read : same text (trailing line breaks ignored), bold and red kept", "true, true, #C62828",
                () -> {
                    RTFEditorKit kit = new RTFEditorKit();
                    StyledDocument back = (StyledDocument) kit.createDefaultDocument();
                    kit.read(new ByteArrayInputStream(written.getBytes(StandardCharsets.ISO_8859_1)), back, 0);
                    return difference(doc.getText(0, doc.getLength()).stripTrailing(),
                            back.getText(0, back.getLength()).stripTrailing()) + ", "
                            + StyleConstants.isBold(run(back, "bold")) + ", "
                            + hex(StyleConstants.getForeground(run(back, "red")));
                }));
        checks.add(Checks.info("RTFEditorKit.write output SHA-256 (line separators normalized)",
                () -> Checks.sha256(written) + ", " + written.length() + " chars"));
        checks.add(Checks.expect("new JEditorPane(\"text/rtf\", text) : kit, text", "RTFEditorKit, pc: caf\u00E9",
                () -> {
                    JEditorPane pane = new JEditorPane("text/rtf", new String(Edt.resourceBytes(RESOURCES + "rtf/pc.rtf"),
                            StandardCharsets.US_ASCII));
                    return pane.getEditorKit().getClass().getSimpleName() + ", "
                            + pane.getDocument().getText(0, 8);
                }));
        return checks;
    }

    private List<Check> checks(Throwable error) {
        List<Check> checks = new ArrayList<>();
        checks.add(error == null ? Check.pass("page loaded, link clicked, form submitted", "yes")
                : Check.fail("page loaded, link clicked, form submitted", Checks.describe(error)));
        checks.addAll(pageChecks());
        checks.addAll(componentChecks());
        checks.addAll(manipulationChecks);
        checks.addAll(writerChecks());
        checks.addAll(rtfChecks);
        return checks;
    }

    private List<Check> pageChecks() {
        List<Check> checks = new ArrayList<>();
        JEditorPane pane = page;
        checks.add(Checks.expect("content type after setPage (URLConnection of the resource)", "text/html",
                pane::getContentType));
        checks.add(Checks.expect("editor kits by content type (created by class name)",
                "HTMLEditorKit RTFEditorKit RTFEditorKit PlainEditorKit", () -> {
                    List<String> names = new ArrayList<>();
                    for (String type : List.of("text/html", "text/rtf", "application/rtf", "text/plain")) {
                        names.add(JEditorPane.createEditorKitForContentType(type).getClass().getSimpleName());
                    }
                    return String.join(" ", names);
                }));
        checks.add(Checks.expect("document title, base URL set", "Quarkus Desktop HTML, true", () -> {
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            return doc.getProperty(Document.TitleProperty) + ", " + (doc.getBase() != null);
        }));
        checks.add(Checks.expect("linked style.css : h1 color, .tag background ; inline .note background",
                "#1B3A6B, #4695EB, #FFF8E1", () -> {
                    StyleSheet sheet = ((HTMLDocument) pane.getDocument()).getStyleSheet();
                    return hex(sheet.getForeground(sheet.getRule("h1"))) + ", "
                            + hex(sheet.getBackground(sheet.getRule(".tag"))) + ", "
                            + hex(sheet.getBackground(sheet.getRule(".note")));
                }));
        checks.add(Checks.expect("default.css : h1 font-size, pre font-family", "x-large, Monospaced", () -> {
            StyleSheet sheet = new HTMLEditorKit().getStyleSheet();
            return sheet.getRule("h1").getAttribute(CSS.Attribute.FONT_SIZE) + ", "
                    + sheet.getRule("pre").getAttribute(CSS.Attribute.FONT_FAMILY);
        }));
        checks.add(Checks.expect("images : src, size, center pixel (PNG / JPEG / GIF), missing",
                "images/logo.png 64x64 #FF4695EB | images/photo.jpg 160x100 sun | images/badge.gif 88x31 #FFFF6F00 | "
                        + "images/missing.png no image",
                () -> String.join(" | ", images(pane))));
        checks.add(Checks.expect("links (HTMLDocument.Iterator over A)", "details.html#part2 https://quarkus.io/", () -> {
            List<String> hrefs = new ArrayList<>();
            for (HTMLDocument.Iterator it = ((HTMLDocument) pane.getDocument()).getIterator(HTML.Tag.A); it
                    .isValid(); it.next()) {
                hrefs.add(String.valueOf(it.getAttributes().getAttribute(HTML.Attribute.HREF)));
            }
            return String.join(" ", hrefs);
        }));
        checks.add(Checks.expect("synthetic click on the relative link : HyperlinkEvent",
                "ACTIVATED details.html#part2 (content, URL resolved)", () -> links.toString()));
        checks.add(Checks.expect("form models (FormView)",
                "DefaultButtonModel:2 OptionComboBoxModel:1 OptionListModel:1 PlainDocument:2 TextAreaDocument:1 "
                        + "ToggleButtonModel:5",
                () -> {
                    TreeMap<String, Integer> models = new TreeMap<>();
                    ElementIterator it = new ElementIterator(pane.getDocument());
                    for (Element e = it.first(); e != null; e = it.next()) {
                        Object model = e.getAttributes().getAttribute(StyleConstants.ModelAttribute);
                        if (model != null) {
                            models.merge(model.getClass().getSimpleName(), 1, Integer::sum);
                        }
                    }
                    List<String> entries = new ArrayList<>();
                    models.forEach((k, v) -> entries.add(k + ":" + v));
                    return String.join(" ", entries);
                }));
        checks.add(Checks.expect("form submitted (FormSubmitEvent, autoFormSubmission false) : data",
                "name=Ada+Lovelace&password=secret&news=on&size=m&lang=java&tags=awt&tags=native"
                        + "&comment=Hello+from+a+textarea&token=42",
                () -> formData));
        checks.add(Checks.expect("form submitted : method, target, action URL", "GET, target _self, action URL resolved",
                () -> formDetails));
        checks.add(Checks.expect("<object classid=javax.swing.JButton> in JEditorPane : ObjectView (Class.forName, "
                + "newInstance, <param> set through the bean property setter)", "JButton ObjectView JButton", () -> {
                    JButton button = find(pane, JButton.class, "ObjectView JButton");
                    return button == null ? "not created" : "JButton " + button.getText();
                }));
        checks.add(Checks.expect("ParserDelegator (html32.bdtd) : start tags, first ones", "129 : html head title link style body h1 span",
                () -> {
                    List<String> tags = new ArrayList<>();
                    HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
                        @Override
                        public void handleStartTag(HTML.Tag tag, MutableAttributeSet a, int pos) {
                            tags.add(tag.toString());
                        }

                        @Override
                        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet a, int pos) {
                            if (!a.isDefined(HTML.Attribute.ENDTAG)) {
                                tags.add(tag.toString());
                            }
                        }
                    };
                    new ParserDelegator().parse(new StringReader(Edt.resourceText(PAGE)), callback, true);
                    return tags.size() + " : " + String.join(" ", tags.subList(0, 8));
                }));
        checks.add(Checks.expect("StyleSheet : stringToColor(teal), declaration", "#008080 | color=red, "
                + "font-weight=bold, margin-bottom=4px, margin-left=8px, margin-right=8px, margin-top=4px", () -> {
                    StyleSheet sheet = new StyleSheet();
                    AttributeSet declaration = sheet.getDeclaration("margin: 4px 8px; color: red; font-weight: bold");
                    TreeMap<String, String> values = new TreeMap<>();
                    for (Enumeration<?> names = declaration.getAttributeNames(); names.hasMoreElements();) {
                        Object name = names.nextElement();
                        values.put(String.valueOf(name), String.valueOf(declaration.getAttribute(name)));
                    }
                    List<String> entries = new ArrayList<>();
                    values.forEach((k, v) -> entries.add(k + "=" + v));
                    return hex(sheet.stringToColor("teal")) + " | " + String.join(", ", entries);
                }));
        return checks;
    }

    /**
     * The image views of the page : source, size and a probe pixel.
     */
    private static List<String> images(JEditorPane pane) {
        List<String> images = new ArrayList<>();
        collectImages(pane.getUI().getRootView(pane), images);
        return images;
    }

    private static void collectImages(View view, List<String> images) {
        if (view instanceof ImageView imageView) {
            String src = String.valueOf(imageView.getElement().getAttributes().getAttribute(HTML.Attribute.SRC));
            Image image = imageView.getImage();
            if (image == null || image.getWidth(null) <= 0) {
                images.add(src + " no image");
            } else {
                int w = image.getWidth(null);
                int h = image.getHeight(null);
                BufferedImage pixels = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = pixels.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
                String probe;
                if (src.endsWith("photo.jpg")) {
                    // lossy : the sun disc within a tolerance
                    int rgb = pixels.getRGB(118, 30);
                    probe = close(rgb, ControlsAssets.SUN, 12) ? "sun" : Checks.argb(rgb);
                } else if (src.endsWith("badge.gif")) {
                    probe = Checks.argb(pixels.getRGB(14, 15));
                } else if (src.endsWith("logo.png")) {
                    probe = Checks.argb(pixels.getRGB(10, 32));
                } else {
                    probe = Checks.argb(pixels.getRGB(w / 2, h / 2));
                }
                images.add(src + " " + w + "x" + h + " " + probe);
            }
        }
        for (int i = 0; i < view.getViewCount(); i++) {
            collectImages(view.getView(i), images);
        }
    }

    private static void collectComponents(View view, List<String> texts) {
        if (view instanceof ComponentView componentView) {
            Component c = componentView.getComponent();
            texts.add(c instanceof JLabel label ? label.getText() : c == null ? "null" : c.getClass().getSimpleName());
        }
        for (int i = 0; i < view.getViewCount(); i++) {
            collectComponents(view.getView(i), texts);
        }
    }

    private static boolean close(int argb, int rgb, int tolerance) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((argb >> shift) & 0xFF) - ((rgb >> shift) & 0xFF)) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static List<Check> componentChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("BasicHTML : view of an HTML label / html.disable label / plain label", "true false false",
                () -> {
                    JLabel html = new JLabel("<html><b>x</b></html>");
                    JLabel disabled = new JLabel();
                    disabled.putClientProperty("html.disable", Boolean.TRUE);
                    disabled.setText("<html><b>x</b></html>");
                    JLabel plain = new JLabel("x");
                    return (html.getClientProperty(BasicHTML.propertyKey) != null) + " "
                            + (disabled.getClientProperty(BasicHTML.propertyKey) != null) + " "
                            + (plain.getClientProperty(BasicHTML.propertyKey) != null);
                }));
        checks.add(Checks.expect("HTML label <img> of a resource URL (BasicHTML loads it synchronously) : size, center",
                "16x16 #FFF9A825", () -> {
                    String star = Edt.resource(RESOURCES + "icons/star-16.png").toExternalForm();
                    JLabel label = new JLabel("<html><img src=\"" + star + "\"></html>");
                    List<String> images = new ArrayList<>();
                    collectImages((View) label.getClientProperty(BasicHTML.propertyKey), images);
                    // "<src> <size> <pixel>" : the src is a URL, never shown
                    return images.isEmpty() ? "no image view" : images.get(0).substring(images.get(0).indexOf(' ') + 1);
                }));
        checks.add(Checks.expect("<object> in a JLabel : BasicHTML refuses it unless swing.html.object=true",
                "?? (swing.html.object null)", () -> {
                    JLabel label = new JLabel("<html><object classid=\"javax.swing.JButton\"></object></html>");
                    List<String> texts = new ArrayList<>();
                    collectComponents((View) label.getClientProperty(BasicHTML.propertyKey), texts);
                    return String.join(" ", texts) + " (swing.html.object " + System.getProperty("swing.html.object")
                            + ")";
                }));
        checks.add(Checks.expect("JToolTip UI / BasicHTML.isHTMLString", "MetalToolTipUI / true false", () -> {
            JToolTip tip = new JToolTip();
            return tip.getUI().getClass().getSimpleName() + " / " + BasicHTML.isHTMLString("<html>x")
                    + " " + BasicHTML.isHTMLString("x <html>");
        }));
        return checks;
    }

    private static List<Check> writerChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("MinimalHTMLWriter of a styled document : style rules, bold, color",
                "true true true", () -> {
                    DefaultStyledDocument doc = new DefaultStyledDocument();
                    doc.insertString(0, "Minimal writer: bold and blue", null);
                    SimpleAttributeSet bold = new SimpleAttributeSet();
                    StyleConstants.setBold(bold, true);
                    StyleConstants.setForeground(bold, new Color(0x1565C0));
                    doc.setCharacterAttributes(16, 4, bold, false);
                    StringWriter out = new StringWriter();
                    new MinimalHTMLWriter(out, doc).write();
                    String html = out.toString();
                    return html.contains("<style>") + " " + html.contains("<b>bold</b>") + " "
                            + html.toLowerCase(Locale.ROOT).contains("#1565c0");
                }));
        checks.add(Checks.expect("HTMLEditorKit.write of a document read from text : round trip text", "true", () -> {
            HTMLEditorKit kit = new HTMLEditorKit();
            HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
            kit.read(new StringReader(MANIPULATED), doc, 0);
            StringWriter out = new StringWriter();
            kit.write(out, doc, 0, doc.getLength());
            HTMLDocument back = (HTMLDocument) kit.createDefaultDocument();
            kit.read(new StringReader(out.toString()), back, 0);
            return back.getText(0, back.getLength()).equals(doc.getText(0, doc.getLength()));
        }));
        return checks;
    }
}
