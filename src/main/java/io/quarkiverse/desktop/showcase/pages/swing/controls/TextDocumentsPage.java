package io.quarkiverse.desktop.showcase.pages.swing.controls;

import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.caption;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.column;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.event;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.fitHeight;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.resourceIcon;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.row;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.runAction;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.section;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.ElementIterator;
import javax.swing.text.GapContent;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.NavigationFilter;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StringContent;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.swing.text.Utilities;
import javax.swing.text.View;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

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
 * Swing text documents : {@code JTextArea} (wrapping, tabs, highlighters, a fixed selection, bidirectional text),
 * {@code JTextPane} with a {@code StyledDocument} (named styles, character and paragraph attributes, tab stops, an
 * embedded icon and component), the document model ({@code PlainDocument}, {@code GapContent}/{@code StringContent},
 * elements, positions, segments), undo and redo, editor kit actions, key maps and navigation filters.
 * <p>
 * Native paths exercised on purpose : the undo presentation names (resource bundle of the Basic look and feel), text
 * actions of the look and feel ({@code LazyActionMap}), key strokes parsed from strings, {@code BreakIterator} word
 * boundaries (JDK break iterator data), bidirectional text analysis, an icon from a classpath resource.
 */
@Singleton
public class TextDocumentsPage implements FeaturePage {

    private static final String PARAGRAPH = "Swing text components keep their content in a Document model and render "
            + "it with a tree of views. This JTextArea wraps lines at word boundaries (setLineWrap, "
            + "setWrapStyleWord) ; the yellow and red marks are Highlighter highlights, the blue range is a fixed "
            + "selection.";
    private static final String TABLE = "Name\tKind\tSize\nbuild\tdirectory\t-\npom.xml\tfile\t5,560\nREADME.md\tfile\t11,977\n"
            + "src/main/java/io/quarkiverse/desktop/showcase/ShowcaseMain.java\tfile\t2,048";
    private static final String BIDI = "English, \u05e2\u05d1\u05e8\u05d9\u05ea (Hebrew) and "
            + "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 123 (Arabic) in one line.";

    private ChecksView results;
    private final Readiness readiness = new Readiness();
    private JTextArea wrapped;
    private JTextPane styled;

    @Override
    public String id() {
        return "swing-text-documents";
    }

    @Override
    public String title() {
        return "Text documents, styles and undo";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Component build() throws Exception {
        results = ChecksView.table("Checks", List.of(Check.info("state", "pending")));
        styled = styledPane();
        JPanel content = column(10,
                Ui.text("JTextArea and JTextPane with their document models : plain and styled documents, elements, "
                        + "positions, undo and redo, editor kit actions and highlighters. The text components are not "
                        + "focused (no caret) ; selections and highlights are fixed.", 1000),
                section("JTextArea : wrapping and highlights / tab stops (setTabSize 12) in a JScrollPane", textAreas()),
                section("Bidirectional text : left to right and RIGHT_TO_LEFT orientation", bidi()),
                section("JTextPane : styles, character and paragraph attributes, TabSet, icon and component", styled),
                section("Element tree of a small styled document (AbstractDocument elements and attributes)",
                        elementTree()),
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
        return Edt.rounds(2).thenAccept(v -> view.setChecks(checks()));
    }

    @Override
    public void dispose(Component content) {
        readiness.reset();
        results = null;
        wrapped = null;
        styled = null;
    }

    // ----------------------------------------------------------------------------------------------------- gallery

    private JComponent textAreas() throws BadLocationException {
        wrapped = new JTextArea(PARAGRAPH);
        wrapped.setLineWrap(true);
        wrapped.setWrapStyleWord(true);
        wrapped.setEditable(true);
        wrapped.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xB0BEC5)),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        Highlighter highlighter = wrapped.getHighlighter();
        int doc = PARAGRAPH.indexOf("Document");
        highlighter.addHighlight(doc, doc + "Document".length(),
                new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFFF176)));
        int views = PARAGRAPH.indexOf("views");
        highlighter.addHighlight(views, views + "views".length(), new SquigglePainter(new Color(0xC62828)));
        int start = PARAGRAPH.indexOf("setLineWrap");
        wrapped.select(start, start + "setLineWrap, setWrapStyleWord".length());
        wrapped.getCaret().setSelectionVisible(true);
        fitHeight(wrapped, 470);

        JTextArea table = new JTextArea(TABLE);
        table.setTabSize(12);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setPreferredSize(new Dimension(470, wrapped.getPreferredSize().height));
        return row(20, column(2, caption("wrapped, 3 highlights (painter, layered painter, selection)"), wrapped),
                column(2, caption("no wrapping, monospaced, scroll bars always"), scroll));
    }

    private JComponent bidi() {
        JTextArea ltr = new JTextArea(BIDI + "\n\u05e9\u05dc\u05d5\u05dd 2026");
        JTextArea rtl = new JTextArea(BIDI + "\n\u05e9\u05dc\u05d5\u05dd 2026");
        rtl.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        for (JTextArea area : List.of(ltr, rtl)) {
            area.setBorder(BorderFactory.createLineBorder(new Color(0xB0BEC5)));
            area.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
            area.setPreferredSize(new Dimension(470, area.getPreferredSize().height));
        }
        return row(20, ltr, rtl);
    }

    /**
     * Named styles of a style context : base, heading, emphasis, code, quote.
     */
    private static void addStyles(StyledDocument doc) {
        Style base = doc.addStyle("base", doc.getStyle(StyleContext.DEFAULT_STYLE));
        StyleConstants.setFontFamily(base, Font.SANS_SERIF);
        StyleConstants.setFontSize(base, 13);
        StyleConstants.setSpaceBelow(base, 4);

        Style heading = doc.addStyle("heading", base);
        StyleConstants.setFontSize(heading, 20);
        StyleConstants.setBold(heading, true);
        StyleConstants.setForeground(heading, new Color(0x1B3A6B));
        StyleConstants.setAlignment(heading, StyleConstants.ALIGN_CENTER);

        Style code = doc.addStyle("code", base);
        StyleConstants.setFontFamily(code, Font.MONOSPACED);
        StyleConstants.setBackground(code, new Color(0xECEFF1));
        StyleConstants.setLeftIndent(code, 24);

        Style quote = doc.addStyle("quote", base);
        StyleConstants.setItalic(quote, true);
        StyleConstants.setForeground(quote, new Color(0x555555));
        StyleConstants.setLeftIndent(quote, 40);
        StyleConstants.setRightIndent(quote, 40);
        StyleConstants.setAlignment(quote, StyleConstants.ALIGN_JUSTIFIED);
        StyleConstants.setFirstLineIndent(quote, 20);
        StyleConstants.setLineSpacing(quote, 0.3f);
    }

    private static void append(StyledDocument doc, String text, AttributeSet attributes) throws BadLocationException {
        doc.insertString(doc.getLength(), text, attributes);
    }

    private static SimpleAttributeSet attrs(java.util.function.Consumer<SimpleAttributeSet> setter) {
        SimpleAttributeSet set = new SimpleAttributeSet();
        setter.accept(set);
        return set;
    }

    private static TabSet tabSet() {
        return new TabSet(new TabStop[] {
                new TabStop(140, TabStop.ALIGN_LEFT, TabStop.LEAD_NONE),
                new TabStop(360, TabStop.ALIGN_CENTER, TabStop.LEAD_DOTS),
                new TabStop(620, TabStop.ALIGN_RIGHT, TabStop.LEAD_UNDERLINE),
                new TabStop(800, TabStop.ALIGN_DECIMAL, TabStop.LEAD_NONE) });
    }

    /**
     * Fills {@code doc} : a heading, runs of character attributes, a quote, tab stops, an icon, a component, code.
     */
    private static void fill(StyledDocument doc) throws BadLocationException {
        addStyles(doc);
        Style base = doc.getStyle("base");
        int start = doc.getLength();
        append(doc, "Styled documents\n", null);
        doc.setLogicalStyle(start, doc.getStyle("heading"));
        doc.setCharacterAttributes(start, "Styled documents".length(), doc.getStyle("heading"), false);

        start = doc.getLength();
        append(doc, "Runs : ", base);
        append(doc, "bold", attrs(a -> StyleConstants.setBold(a, true)));
        append(doc, ", ", base);
        append(doc, "italic", attrs(a -> StyleConstants.setItalic(a, true)));
        append(doc, ", ", base);
        append(doc, "underline", attrs(a -> StyleConstants.setUnderline(a, true)));
        append(doc, ", ", base);
        append(doc, "strike", attrs(a -> StyleConstants.setStrikeThrough(a, true)));
        append(doc, ", E = mc", base);
        append(doc, "2", attrs(a -> StyleConstants.setSuperscript(a, true)));
        append(doc, ", H", base);
        append(doc, "2", attrs(a -> StyleConstants.setSubscript(a, true)));
        append(doc, "O, ", base);
        append(doc, "red", attrs(a -> StyleConstants.setForeground(a, new Color(0xC62828))));
        append(doc, " on ", base);
        append(doc, "yellow", attrs(a -> StyleConstants.setBackground(a, new Color(0xFFF176))));
        append(doc, ", ", base);
        append(doc, "Serif 18", attrs(a -> {
            StyleConstants.setFontFamily(a, Font.SERIF);
            StyleConstants.setFontSize(a, 18);
        }));
        append(doc, ", ", base);
        append(doc, "Monospaced", attrs(a -> StyleConstants.setFontFamily(a, Font.MONOSPACED)));
        append(doc, ", an icon ", base);
        doc.insertString(doc.getLength(), " ", attrs(a -> StyleConstants.setIcon(a, resourceIcon("icons/star-16.png",
                "star"))));
        append(doc, " and a component ", base);
        doc.insertString(doc.getLength(), " ", attrs(a -> {
            JCheckBox box = new JCheckBox("embedded JCheckBox", true);
            box.setOpaque(false);
            StyleConstants.setComponent(a, box);
        }));
        append(doc, ".\n", base);
        doc.setLogicalStyle(start, base);

        start = doc.getLength();
        append(doc, "A justified paragraph with left, right and first line indents and a line spacing of 0.3 : "
                + "the quote style. Paragraph attributes apply to whole paragraphs, character attributes to runs of "
                + "text inside them ; the views wrap the text at the width of the pane.\n", null);
        doc.setLogicalStyle(start, doc.getStyle("quote"));
        doc.setCharacterAttributes(start, doc.getLength() - start, doc.getStyle("quote"), false);

        start = doc.getLength();
        append(doc, "left\tcenter (dots)\tright (underline)\t1234.56\n", base);
        append(doc, "tab stops\tTabStop.ALIGN_CENTER\tALIGN_RIGHT\t7.5\n", base);
        SimpleAttributeSet tabs = new SimpleAttributeSet(base);
        StyleConstants.setTabSet(tabs, tabSet());
        doc.setParagraphAttributes(start, doc.getLength() - start, tabs, false);

        start = doc.getLength();
        append(doc, "Style code = doc.addStyle(\"code\", base);", null);
        doc.setLogicalStyle(start, doc.getStyle("code"));
        doc.setCharacterAttributes(start, doc.getLength() - start, doc.getStyle("code"), false);
    }

    private static JTextPane styledPane() throws BadLocationException {
        JTextPane pane = new JTextPane();
        fill(pane.getStyledDocument());
        pane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return fitHeight(pane, 960);
    }

    private static Component elementTree() throws BadLocationException {
        DefaultStyledDocument doc = smallDocument();
        return Ui.text(dump(doc.getDefaultRootElement(), 0), new Font(Font.MONOSPACED, Font.PLAIN, 11), 0x263238, 960);
    }

    private static DefaultStyledDocument smallDocument() throws BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "Hello bold world\nSecond line", null);
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        StyleConstants.setForeground(bold, new Color(0x1565C0));
        doc.setCharacterAttributes(6, 4, bold, false);
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(17, 1, center, false);
        return doc;
    }

    /**
     * A deterministic dump of an element tree : names, offsets, attributes sorted by name (colors as hex, icons and
     * components by type, styles by name).
     */
    static String dump(Element element, int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ".repeat(depth)).append(element.getName()).append(" [").append(element.getStartOffset())
                .append(',').append(element.getEndOffset()).append(')');
        String attributes = attributes(element.getAttributes());
        if (!attributes.isEmpty()) {
            sb.append(' ').append(attributes);
        }
        if (element.isLeaf()) {
            try {
                String text = element.getDocument().getText(element.getStartOffset(),
                        element.getEndOffset() - element.getStartOffset());
                sb.append(" \"").append(text.replace("\n", "\\n")).append('"');
            } catch (BadLocationException e) {
                sb.append(" ?");
            }
        }
        for (int i = 0; i < element.getElementCount(); i++) {
            sb.append('\n').append(dump(element.getElement(i), depth + 1));
        }
        return sb.toString();
    }

    static String attributes(AttributeSet set) {
        TreeMap<String, String> values = new TreeMap<>();
        for (Enumeration<?> names = set.getAttributeNames(); names.hasMoreElements();) {
            Object name = names.nextElement();
            Object value = set.getAttribute(name);
            String text;
            if (value instanceof Color color) {
                text = String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xFFFFFF);
            } else if (value instanceof Icon icon) {
                text = "icon " + icon.getIconWidth() + "x" + icon.getIconHeight();
            } else if (value instanceof Component component) {
                text = component.getClass().getSimpleName();
            } else if (value instanceof Style style) {
                text = "style " + style.getName();
            } else if (value instanceof AttributeSet) {
                text = "{...}";
            } else {
                text = String.valueOf(value);
            }
            values.put(String.valueOf(name), text);
        }
        List<String> entries = new ArrayList<>();
        values.forEach((k, v) -> entries.add(k + "=" + v));
        return entries.isEmpty() ? "" : "{" + String.join(", ", entries) + "}";
    }

    /**
     * Paints a zigzag under the highlighted text (a {@code LayeredHighlighter.LayerPainter}).
     */
    static final class SquigglePainter extends LayeredHighlighter.LayerPainter {

        private final int rgb;

        SquigglePainter(Color color) {
            this.rgb = color.getRGB();
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            // painted by paintLayer
        }

        @Override
        public Shape paintLayer(Graphics graphics, int offs0, int offs1, Shape bounds, JTextComponent c, View view) {
            Rectangle r;
            try {
                Shape shape = view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, bounds);
                r = shape instanceof Rectangle rect ? rect : shape.getBounds();
            } catch (BadLocationException e) {
                return null;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(rgb));
                g.setStroke(new BasicStroke(1f));
                int y = r.y + r.height - 2;
                for (int x = r.x; x < r.x + r.width - 2; x += 4) {
                    g.drawLine(x, y, x + 2, y + 2);
                    g.drawLine(x + 2, y + 2, x + 4, y);
                }
            } finally {
                g.dispose();
            }
            return r;
        }
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private List<Check> checks() {
        List<Check> checks = new ArrayList<>();
        checks.addAll(modelChecks());
        checks.addAll(undoChecks());
        checks.addAll(styleChecks());
        checks.addAll(editingChecks());
        checks.addAll(viewChecks());
        return checks;
    }

    private static List<Check> modelChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("PlainDocument : insert, remove, lines (root element children)",
                "one\\ntwo\\nthree | 3 lines", () -> {
                    PlainDocument doc = new PlainDocument();
                    doc.insertString(0, "one\nthree", null);
                    doc.insertString(4, "two\n", null);
                    doc.insertString(doc.getLength(), " four", null);
                    doc.remove(doc.getLength() - 5, 5);
                    return escape(doc.getText(0, doc.getLength())) + " | "
                            + doc.getDefaultRootElement().getElementCount() + " lines";
                }));
        checks.add(Checks.expect("GapContent and StringContent : same edits, same text", "true : abcXYZdef", () -> {
            List<String> texts = new ArrayList<>();
            for (AbstractDocument.Content content : List.of(new GapContent(4), new StringContent(4))) {
                PlainDocument doc = new PlainDocument(content);
                doc.insertString(0, "abcdef", null);
                doc.insertString(3, "XYZ", null);
                texts.add(doc.getText(0, doc.getLength()));
            }
            return texts.get(0).equals(texts.get(1)) + " : " + texts.get(0);
        }));
        checks.add(Checks.expect("Position : offset 5 after insert 3 at 0, then remove 2 at 0", "8, 6", () -> {
            PlainDocument doc = new PlainDocument();
            doc.insertString(0, "0123456789", null);
            Position position = doc.createPosition(5);
            doc.insertString(0, "abc", null);
            int afterInsert = position.getOffset();
            doc.remove(0, 2);
            return afterInsert + ", " + position.getOffset();
        }));
        checks.add(Checks.expect("Segment : getText(0, 10) with partial return across the gap", "10 chars : 0123XY4567",
                () -> {
                    PlainDocument doc = new PlainDocument(new GapContent(4));
                    doc.insertString(0, "0123456789", null);
                    doc.insertString(4, "XY", null);
                    Segment segment = new Segment();
                    segment.setPartialReturn(false);
                    doc.getText(0, 10, segment);
                    return segment.count + " chars : " + segment;
                }));
        checks.add(Checks.expect("getText beyond the end", "BadLocationException", () -> {
            try {
                new PlainDocument().getText(0, 5);
                return "no exception";
            } catch (BadLocationException e) {
                return "BadLocationException";
            }
        }));
        checks.add(Checks.expect("DocumentListener : insert, remove, attribute change events",
                "insert 0+11 | insert 5+1 | remove 0+6 | change 0+3", () -> {
                    DefaultStyledDocument doc = new DefaultStyledDocument();
                    EventLog log = new EventLog();
                    doc.addDocumentListener(new DocumentListener() {
                        @Override
                        public void insertUpdate(DocumentEvent e) {
                            log.add("insert " + e.getOffset() + "+" + e.getLength());
                        }

                        @Override
                        public void removeUpdate(DocumentEvent e) {
                            log.add("remove " + e.getOffset() + "+" + e.getLength());
                        }

                        @Override
                        public void changedUpdate(DocumentEvent e) {
                            log.add("change " + e.getOffset() + "+" + e.getLength());
                        }
                    });
                    doc.insertString(0, "hello world", null);
                    doc.insertString(5, "\n", null);
                    doc.remove(0, 6);
                    SimpleAttributeSet bold = new SimpleAttributeSet();
                    StyleConstants.setBold(bold, true);
                    doc.setCharacterAttributes(0, 3, bold, false);
                    return log.toString();
                }));
        checks.add(Checks.expect("DocumentEvent.getChange(root) of a line break : removed / added paragraphs", "1 / 2",
                () -> {
                    PlainDocument doc = new PlainDocument();
                    doc.insertString(0, "one line", null);
                    List<String> change = new ArrayList<>();
                    doc.addDocumentListener(new DocumentListener() {
                        @Override
                        public void insertUpdate(DocumentEvent e) {
                            DocumentEvent.ElementChange c = e.getChange(doc.getDefaultRootElement());
                            change.add(c.getChildrenRemoved().length + " / " + c.getChildrenAdded().length);
                        }

                        @Override
                        public void removeUpdate(DocumentEvent e) {
                        }

                        @Override
                        public void changedUpdate(DocumentEvent e) {
                        }
                    });
                    doc.insertString(3, "\n", null);
                    return String.join(",", change);
                }));
        checks.add(Checks.expect("ElementIterator over the styled document : elements, leaves", "7, 4", () -> {
            DefaultStyledDocument doc = smallDocument();
            ElementIterator it = new ElementIterator(doc);
            int elements = 0;
            int leaves = 0;
            for (Element e = it.first(); e != null; e = it.next()) {
                elements++;
                if (e.isLeaf()) {
                    leaves++;
                }
            }
            return elements + ", " + leaves;
        }));
        checks.add(Checks.info("element tree dump SHA-256",
                () -> Checks.sha256(dump(smallDocument().getDefaultRootElement(), 0))));
        checks.add(Checks.expect("AbstractDocument.render(Runnable) under the read lock", "rendered 16", () -> {
            DefaultStyledDocument doc = smallDocument();
            int[] length = new int[1];
            doc.render(() -> length[0] = doc.getDefaultRootElement().getElement(0).getEndOffset() - 1);
            return "rendered " + length[0];
        }));
        checks.add(Checks.expect("DefaultEditorKit.read CRLF text : text, EndOfLineStringProperty, write",
                "a\\nb\\nc | \\r\\n | a\\r\\nb\\r\\nc", () -> {
                    DefaultEditorKit kit = new DefaultEditorKit();
                    Document doc = kit.createDefaultDocument();
                    kit.read(new StringReader("a\r\nb\r\nc"), doc, 0);
                    StringWriter out = new StringWriter();
                    kit.write(out, doc, 0, doc.getLength());
                    return escape(doc.getText(0, doc.getLength())) + " | "
                            + escape(String.valueOf(doc.getProperty(DefaultEditorKit.EndOfLineStringProperty))) + " | "
                            + escape(out.toString());
                }));
        return checks;
    }

    private static String state(DefaultStyledDocument doc) throws BadLocationException {
        return doc.getText(0, doc.getLength()) + (StyleConstants.isBold(doc.getCharacterElement(0).getAttributes())
                ? " (bold)" : " (plain)");
    }

    private static String escape(String text) {
        return text.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static List<Check> undoChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("UndoManager : insert, insert, remove, bold ; undo, undo, redo, redo",
                "Hello World (plain) -> Hello World! (plain) -> Hello World (plain) -> Hello World (bold) | canUndo true, "
                        + "canRedo false",
                () -> {
                    DefaultStyledDocument doc = new DefaultStyledDocument();
                    UndoManager undo = new UndoManager();
                    doc.addUndoableEditListener(undo);
                    doc.insertString(0, "Hello", null);
                    doc.insertString(5, " World!", null);
                    doc.remove(11, 1);
                    SimpleAttributeSet bold = new SimpleAttributeSet();
                    StyleConstants.setBold(bold, true);
                    doc.setCharacterAttributes(0, 5, bold, false);
                    List<String> states = new ArrayList<>();
                    undo.undo();
                    states.add(state(doc));
                    undo.undo();
                    states.add(state(doc));
                    undo.redo();
                    states.add(state(doc));
                    undo.redo();
                    states.add(state(doc));
                    return String.join(" -> ", states) + " | canUndo " + undo.canUndo() + ", canRedo " + undo.canRedo();
                }));
        checks.add(Checks.expect("undo / redo presentation names (Basic resource bundle)",
                "Undo deletion | Redo style change | Undo addition", () -> {
                    DefaultStyledDocument doc = new DefaultStyledDocument();
                    UndoManager undo = new UndoManager();
                    doc.addUndoableEditListener(undo);
                    doc.insertString(0, "Hello", null);
                    SimpleAttributeSet bold = new SimpleAttributeSet();
                    StyleConstants.setBold(bold, true);
                    doc.setCharacterAttributes(0, 5, bold, false);
                    doc.remove(0, 1);
                    String first = undo.getUndoPresentationName();
                    undo.undo();
                    undo.undo();
                    String second = undo.getRedoPresentationName();
                    return first + " | " + second + " | " + undo.getUndoPresentationName();
                }));
        checks.add(Checks.expect("UIManager strings AbstractUndoableEdit.undoText / redoText, AbstractDocument.additionText",
                "Undo / Redo / addition", () -> UIManager.getString("AbstractUndoableEdit.undoText") + " / "
                        + UIManager.getString("AbstractUndoableEdit.redoText") + " / "
                        + UIManager.getString("AbstractDocument.additionText")));
        checks.add(Checks.expect("CompoundEdit : 2 inserts undone at once", "Hello World -> (empty), canRedo true", () -> {
            PlainDocument doc = new PlainDocument();
            CompoundEdit compound = new CompoundEdit();
            doc.addUndoableEditListener(e -> compound.addEdit(e.getEdit()));
            doc.insertString(0, "Hello", null);
            doc.insertString(5, " World", null);
            compound.end();
            String before = doc.getText(0, doc.getLength());
            compound.undo();
            return before + " -> " + (doc.getLength() == 0 ? "(empty)" : doc.getText(0, doc.getLength())) + ", canRedo "
                    + compound.canRedo();
        }));
        checks.add(Checks.expect("UndoManager.setLimit(2) after 3 edits : undo count", "2, text a", () -> {
            PlainDocument doc = new PlainDocument();
            UndoManager undo = new UndoManager();
            undo.setLimit(2);
            doc.addUndoableEditListener(undo);
            for (String s : List.of("a", "b", "c")) {
                doc.insertString(doc.getLength(), s, null);
            }
            int count = 0;
            while (undo.canUndo()) {
                undo.undo();
                count++;
            }
            return count + ", text " + doc.getText(0, doc.getLength());
        }));
        return checks;
    }

    private List<Check> styleChecks() {
        List<Check> checks = new ArrayList<>();
        StyledDocument doc = styled.getStyledDocument();
        checks.add(Checks.expect("named styles : font size, bold, family inherited from the parent style",
                "heading 20 bold SansSerif, code 13 Monospaced", () -> {
                    Style heading = doc.getStyle("heading");
                    Style code = doc.getStyle("code");
                    return "heading " + StyleConstants.getFontSize(heading) + (StyleConstants.isBold(heading) ? " bold " : " ")
                            + StyleConstants.getFontFamily(heading) + ", code " + StyleConstants.getFontSize(code) + " "
                            + StyleConstants.getFontFamily(code);
                }));
        checks.add(Checks.expect("paragraph attributes : alignment of the heading / quote paragraphs", "1 / 3", () -> {
            Element root = doc.getDefaultRootElement();
            return StyleConstants.getAlignment(root.getElement(0).getAttributes()) + " / "
                    + StyleConstants.getAlignment(root.getElement(2).getAttributes());
        }));
        checks.add(Checks.expect("character attributes : bold / superscript / red runs", "bold / superscript / #C62828",
                () -> {
                    String text = doc.getText(0, doc.getLength());
                    AttributeSet bold = doc.getCharacterElement(text.indexOf("bold")).getAttributes();
                    AttributeSet sup = doc.getCharacterElement(text.indexOf("mc2") + 2).getAttributes();
                    AttributeSet red = doc.getCharacterElement(text.indexOf("red")).getAttributes();
                    return (StyleConstants.isBold(bold) ? "bold" : "-") + " / "
                            + (StyleConstants.isSuperscript(sup) ? "superscript" : "-") + " / "
                            + String.format(Locale.ROOT, "#%06X", StyleConstants.getForeground(red).getRGB() & 0xFFFFFF);
                }));
        checks.add(Checks.expect("TabSet : count, tab index after 200, alignments, leaders", "4, 1, 0 2 1 4, 0 1 3 0",
                () -> {
                    TabSet set = tabSet();
                    StringBuilder alignments = new StringBuilder();
                    StringBuilder leaders = new StringBuilder();
                    for (int i = 0; i < set.getTabCount(); i++) {
                        alignments.append(i == 0 ? "" : " ").append(set.getTab(i).getAlignment());
                        leaders.append(i == 0 ? "" : " ").append(set.getTab(i).getLeader());
                    }
                    return set.getTabCount() + ", " + set.getTabIndexAfter(200) + ", " + alignments + ", " + leaders;
                }));
        checks.add(Checks.expect("embedded icon and component elements", "icon 16x16, component JCheckBox", () -> {
            List<String> found = new ArrayList<>();
            ElementIterator it = new ElementIterator(doc);
            for (Element e = it.first(); e != null; e = it.next()) {
                if (e.getName().equals(StyleConstants.IconElementName)) {
                    Icon icon = StyleConstants.getIcon(e.getAttributes());
                    found.add("icon " + icon.getIconWidth() + "x" + icon.getIconHeight());
                } else if (e.getName().equals(StyleConstants.ComponentElementName)) {
                    found.add("component " + StyleConstants.getComponent(e.getAttributes()).getClass().getSimpleName());
                }
            }
            return String.join(", ", found);
        }));
        checks.add(Checks.expect("StyledEditorKit actions on a selection : bold, italic, underline, size 16, alignment right",
                "true true true 16 2", () -> {
                    JTextPane pane = new JTextPane();
                    pane.setText("styled editor kit actions");
                    pane.select(7, 13);
                    for (Action action : List.of(new StyledEditorKit.BoldAction(), new StyledEditorKit.ItalicAction(),
                            new StyledEditorKit.UnderlineAction(), new StyledEditorKit.FontSizeAction("16", 16),
                            new StyledEditorKit.AlignmentAction("right", StyleConstants.ALIGN_RIGHT))) {
                        action.actionPerformed(event(pane, ""));
                    }
                    AttributeSet a = pane.getStyledDocument().getCharacterElement(8).getAttributes();
                    AttributeSet p = pane.getStyledDocument().getParagraphElement(8).getAttributes();
                    return StyleConstants.isBold(a) + " " + StyleConstants.isItalic(a) + " " + StyleConstants.isUnderline(a)
                            + " " + StyleConstants.getFontSize(a) + " " + StyleConstants.getAlignment(p);
                }));
        checks.add(Checks.expect("logical style of the code paragraph / styles of the document",
                "code / base code default heading quote", () -> {
                    int offset = doc.getLength() - 2;
                    List<String> names = new ArrayList<>();
                    for (Enumeration<?> e = ((DefaultStyledDocument) doc).getStyleNames(); e.hasMoreElements();) {
                        names.add(String.valueOf(e.nextElement()));
                    }
                    names.sort(null);
                    return doc.getLogicalStyle(offset).getName() + " / " + String.join(" ", names);
                }));
        return checks;
    }

    private static List<Check> editingChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("Utilities word boundaries (BreakIterator) : word start/end at 9, next word, previous word",
                "6-11, 12, 0", () -> {
                    JTextArea area = new JTextArea("Hello brave new world");
                    return Utilities.getWordStart(area, 9) + "-" + Utilities.getWordEnd(area, 9) + ", "
                            + Utilities.getNextWord(area, 6) + ", " + Utilities.getPreviousWord(area, 6);
                }));
        checks.add(Checks.expect("editor actions : select-word, caret-next-word, delete-previous-word, insert-break, insert-tab",
                "brave | 12 | Hello new world | Hello \\nnew world | Hello \\n\\tnew world", () -> {
                    JTextArea area = new JTextArea("Hello brave new world");
                    List<String> results = new ArrayList<>();
                    area.setCaretPosition(8);
                    runAction(area, DefaultEditorKit.selectWordAction);
                    results.add(area.getSelectedText());
                    area.setCaretPosition(8);
                    runAction(area, DefaultEditorKit.nextWordAction);
                    results.add(String.valueOf(area.getCaretPosition()));
                    runAction(area, DefaultEditorKit.deletePrevWordAction);
                    results.add(area.getText());
                    area.setCaretPosition(6);
                    runAction(area, DefaultEditorKit.insertBreakAction);
                    results.add(escape(area.getText()));
                    runAction(area, DefaultEditorKit.insertTabAction);
                    results.add(escape(area.getText()).replace("\t", "\\t"));
                    return String.join(" | ", results);
                }));
        checks.add(Checks.expect("Keymap : addKeymap, addActionForKeyStroke(ctrl D), lookup, removeKeymap",
                "custom -> default, delete-next, removed", () -> {
                    Keymap keymap = JTextComponent.addKeymap("showcase-custom", JTextComponent.getKeymap(
                            JTextComponent.DEFAULT_KEYMAP));
                    JTextArea area = new JTextArea();
                    Action delete = area.getActionMap().get(DefaultEditorKit.deleteNextCharAction);
                    keymap.addActionForKeyStroke(KeyStroke.getKeyStroke("ctrl D"), delete);
                    String result = "custom -> " + keymap.getResolveParent().getName() + ", "
                            + keymap.getAction(KeyStroke.getKeyStroke("ctrl D")).getValue(Action.NAME);
                    JTextComponent.removeKeymap("showcase-custom");
                    return result + ", " + (JTextComponent.getKeymap("showcase-custom") == null ? "removed" : "kept");
                }));
        checks.add(Checks.expect("NavigationFilter (prompt \"> \") : setCaretPosition(0), moveCaretPosition(0)", "2, 2-5",
                () -> {
                    JTextArea area = new JTextArea("> input");
                    area.setNavigationFilter(new NavigationFilter() {
                        @Override
                        public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
                            fb.setDot(Math.max(dot, 2), bias);
                        }

                        @Override
                        public void moveDot(FilterBypass fb, int dot, Position.Bias bias) {
                            fb.moveDot(Math.max(dot, 2), bias);
                        }
                    });
                    area.setCaretPosition(0);
                    String dot = String.valueOf(area.getCaretPosition());
                    area.setCaretPosition(5);
                    area.moveCaretPosition(0);
                    return dot + ", " + area.getSelectionStart() + "-" + area.getSelectionEnd();
                }));
        checks.add(Checks.expect("Highlighter : add 2, insert before, change, remove", "2 | 8-13 | 0-3 | 1", () -> {
            JTextArea area = new JTextArea("hello world");
            Highlighter h = area.getHighlighter();
            Object first = h.addHighlight(6, 11, DefaultHighlighter.DefaultPainter);
            Object second = h.addHighlight(0, 5, DefaultHighlighter.DefaultPainter);
            String count = String.valueOf(h.getHighlights().length);
            area.getDocument().insertString(0, "> ", null);
            Highlighter.Highlight moved = h.getHighlights()[0];
            String offsets = moved.getStartOffset() + "-" + moved.getEndOffset();
            h.changeHighlight(second, 0, 3);
            Highlighter.Highlight changed = h.getHighlights()[1];
            String change = changed.getStartOffset() + "-" + changed.getEndOffset();
            h.removeHighlight(first);
            return count + " | " + offsets + " | " + change + " | " + h.getHighlights().length;
        }));
        checks.add(Checks.expect("JTextArea lines : count, start of line 2, line of offset 20", "5, 33, 1", () -> {
            JTextArea area = new JTextArea(TABLE);
            return area.getLineCount() + ", " + area.getLineStartOffset(2) + ", " + area.getLineOfOffset(20);
        }));
        checks.add(Checks.expect("bidi root element : runs and levels of the mixed line (digits at level 2)",
                "6 runs, levels 0 1 0 1 2 0", () -> {
                    PlainDocument doc = new PlainDocument();
                    doc.insertString(0, BIDI, null);
                    Element bidi = ((AbstractDocument) doc).getBidiRootElement();
                    List<String> levels = new ArrayList<>();
                    for (int i = 0; i < bidi.getElementCount(); i++) {
                        levels.add(String.valueOf(StyleConstants.getBidiLevel(bidi.getElement(i).getAttributes())));
                    }
                    return bidi.getElementCount() + " runs, levels " + String.join(" ", levels);
                }));
        return checks;
    }

    private List<Check> viewChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("modelToView2D / viewToModel2D round trip on the wrapped area (offsets 0, 40, 120, end)",
                "true", () -> {
                    for (int offset : new int[] { 0, 40, 120, wrapped.getDocument().getLength() - 1 }) {
                        Rectangle2D r = wrapped.modelToView2D(offset);
                        int back = wrapped.viewToModel2D(new java.awt.geom.Point2D.Double(r.getX() + 1, r.getCenterY()));
                        if (back != offset && back != offset + 1) {
                            return "offset " + offset + " -> " + back;
                        }
                    }
                    return true;
                }));
        checks.add(Checks.info("wrapped area rows (Utilities.getRowStart, layout dependent)", () -> {
            int rows = 0;
            int offset = 0;
            int length = wrapped.getDocument().getLength();
            while (offset < length) {
                int end = Utilities.getRowEnd(wrapped, offset);
                if (end < offset) {
                    return "row end " + end;
                }
                rows++;
                offset = end + 1;
            }
            return rows;
        }));
        checks.add(Checks.expect("wrapped area : selection kept (not focused)", "setLineWrap, setWrapStyleWord",
                () -> wrapped.getSelectedText()));
        checks.add(Checks.expect("styled pane : root view children (paragraphs)", 6,
                () -> styled.getUI().getRootView(styled).getView(0).getViewCount()));
        return checks;
    }
}
