package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.im.InputContext;
import java.awt.im.InputMethodHighlight;
import java.awt.im.InputMethodRequests;
import java.awt.im.InputSubset;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Input methods with AWT : the {@code InputContext} API, the input method highlights ({@code InputMethodHighlight},
 * {@code Toolkit.mapInputMethodHighlight}, rendered by {@code TextLayout}), an on-the-spot active client (a lightweight
 * component implementing {@code InputMethodRequests}) and an AWT {@code TextField} receiving synthetic
 * {@code InputMethodEvent}s (composed then committed Japanese text), as a host input method would send them.
 * <p>
 * AWT only. No host input method is needed (and none is switched) : the events are dispatched to the components.
 */
@Singleton
public class InputMethodsPage implements FeaturePage {

    static final String RAW = "にほんご";
    static final String CONVERTED = "日本語";
    static final String NEXT = "かなかんじ";

    @Override
    public String id() {
        return "desktop-input-methods";
    }

    @Override
    public String title() {
        return "Input methods (AWT)";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 60;
    }

    // per build state
    private ChecksView fieldView;
    private TextField field;
    private List<String> fieldEvents;
    private BufferedImage compositionImage;

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = fieldView;
        TextField textField = field;
        List<String> events = fieldEvents;
        List<Check> checks = new ArrayList<>();
        // the text field has no InputMethodRequests (a passive client) : the input context routes the events to the
        // Java composition window (sun.awt.im.CompositionArea in an InputMethodJFrame, Swing), and sends the committed
        // text to the text field as KEY_TYPED events. Its peer must exist : after the page is displayed.
        return Edt.rounds(2).thenCompose(v -> {
            textField.dispatchEvent(new InputMethodEvent(textField, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    composed(RAW, InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT), 0, TextHitInfo.leading(4),
                    TextHitInfo.leading(4)));
            return Edt.until(() -> compositionWindow() != null, 3000, "composition window").handle((r, e) -> null);
        }).thenCompose(v -> Edt.rounds(3)).thenCompose(v -> {
            Window window = compositionWindow();
            checks.add(Check.info("composition window (composed text of a passive client)", window == null ? "none"
                    : window.getClass().getName() + ", title '" + titleOf(window) + "'"));
            if (window != null && window.getComponentCount() > 0) {
                Component root = window.getComponent(0);
                compositionImage = Snapshots.render(root);
            }
            textField.dispatchEvent(new InputMethodEvent(textField, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    new AttributedString(CONVERTED).getIterator(), 3, null, null));
            return Edt.until(() -> textField.getText().length() >= CONVERTED.length() && compositionWindow() == null,
                    3000, "committed text").handle((r, e) -> null);
        }).thenAccept(v -> {
            checks.add(Checks.expect("composition window hidden after the commit", true,
                    () -> compositionWindow() == null));
            // Windows : the peer is a native edit control (a passive client). Linux : the peer is a Swing text field
            // providing InputMethodRequests (an active client), verified there as information only
            checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect(
                    "TextField: text committed (KEY_TYPED events to the peer)", CONVERTED, textField::getText)));
            // consumed by the composition area handler : the listeners of a passive client see no input method event
            checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("TextField: InputMethodListener events", "[]",
                    events::toString)));
            checks.add(Checks.expect("TextField: getInputMethodRequests() (Windows peer)", Platforms.pick("-", "null", "-"),
                    () -> Platforms.isWindows() ? String.valueOf(textField.getInputMethodRequests()) : "-"));
            checks.add(Checks.expect("InputMethodEvent.paramString()",
                    "INPUT_METHOD_TEXT_CHANGED, \"日本語\" + \"\", 3 characters committed, no caret, no visible position",
                    () -> new InputMethodEvent(textField, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                            new AttributedString(CONVERTED).getIterator(), 3, null, null).paramString()));
            view.setChecks(checks);
        });
    }

    /**
     * The visible Java input method window (a {@code sun.awt.im.InputMethodWindow}), if any.
     */
    private static Window compositionWindow() {
        for (Window w : Window.getWindows()) {
            if (w.isVisible() && w.getClass().getName().startsWith("sun.awt.im.")) {
                return w;
            }
        }
        return null;
    }

    private static String titleOf(Window window) {
        return window instanceof java.awt.Frame frame ? frame.getTitle() : "";
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        BufferedImage image = compositionImage != null ? compositionImage
                : new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return CompletableFuture.completedFuture(Map.of("composition-window", image));
    }

    @Override
    public void dispose(Component content) {
        fieldView = null;
        field = null;
        fieldEvents = null;
        compositionImage = null;
    }

    @Override
    public Component build() {
        Font font = new Font(Platforms.Families.japanese(), Font.PLAIN, 20);
        ActiveClient client = new ActiveClient(font);
        TextField field = new TextField(20);
        field.setFont(new Font(Font.DIALOG, Font.PLAIN, 16));
        List<String> fieldEvents = Collections.synchronizedList(new ArrayList<>());
        field.addInputMethodListener(new InputMethodListener() {
            @Override
            public void inputMethodTextChanged(InputMethodEvent e) {
                fieldEvents.add("text changed, " + e.getCommittedCharacterCount() + " committed");
            }

            @Override
            public void caretPositionChanged(InputMethodEvent e) {
                fieldEvents.add("caret changed");
            }
        });

        List<Check> clientChecks = new ArrayList<>();
        // the sequence of a host input method : raw input, conversion, commit, then a new composition
        dispatch(client, composed(RAW, InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT), 0, TextHitInfo.leading(4));
        clientChecks.add(Checks.expect("1. raw composed text", "committed '' composed 'にほんご' caret TextHitInfo[4L]",
                client::state));
        dispatch(client, composed(CONVERTED, InputMethodHighlight.SELECTED_CONVERTED_TEXT_HIGHLIGHT), 0,
                TextHitInfo.trailing(2));
        clientChecks.add(Checks.expect("2. converted composed text",
                "committed '' composed '日本語' caret TextHitInfo[2T]", client::state));
        dispatch(client, new AttributedString(CONVERTED).getIterator(), 3, null);
        clientChecks.add(Checks.expect("3. committed", "committed '日本語' composed '' caret null", client::state));
        AttributedString next = new AttributedString(NEXT);
        next.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT, InputMethodHighlight.UNSELECTED_CONVERTED_TEXT_HIGHLIGHT,
                0, 2);
        next.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT, InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT, 2, 5);
        dispatch(client, next.getIterator(), 0, TextHitInfo.leading(2));
        client.dispatchEvent(new InputMethodEvent(client, InputMethodEvent.CARET_POSITION_CHANGED, TextHitInfo.leading(5),
                TextHitInfo.leading(5)));
        clientChecks.add(Checks.expect("4. new composition, caret moved",
                "committed '日本語' composed 'かなかんじ' caret TextHitInfo[5L]", client::state));
        clientChecks.add(Checks.expect("events received by the client", "4 text changed, 1 caret changed",
                () -> client.textEvents + " text changed, " + client.caretEvents + " caret changed"));
        clientChecks.add(Checks.expect("InputMethodRequests: committed length, insert offset, committed text",
                "3 3 日本語", () -> client.getCommittedTextLength() + " " + client.getInsertPositionOffset() + " "
                        + text(client.getCommittedText(0, 3, null))));
        clientChecks.add(Checks.expect("InputMethodRequests: cancelLatestCommittedText, selected text", "null ''",
                () -> client.cancelLatestCommittedText(null) + " '" + text(client.getSelectedText(null)) + "'"));

        fieldView = ChecksView.table("AWT TextField", List.of(Check.info("state", "pending")), 250, 494);
        this.field = field;
        this.fieldEvents = fieldEvents;

        return Ui.column(14,
                Ui.text("Synthetic InputMethodEvents (composed, converted then committed Japanese text) sent to an "
                        + "on-the-spot active client (a lightweight component implementing InputMethodRequests, painting "
                        + "the composed text with TextLayout) and to an AWT TextField. The highlights are mapped to text "
                        + "attributes by the toolkit.", 1000),
                Ui.row(24, Ui.column(4, Ui.caption("active client (committed + composed text, caret)"), client),
                        Ui.column(4, Ui.caption("java.awt.TextField"), field)),
                Ui.column(4, Ui.caption("InputMethodHighlight styles (TextLayout) : the 4 standard highlights, then "
                        + "explicit INPUT_METHOD_UNDERLINE styles"), Ui.image(highlights(font))),
                Ui.row(12, ChecksView.table("Active client", clientChecks, 250, 494),
                        fieldView),
                ChecksView.table("InputContext and highlights", contextChecks(field)));
    }

    private static void dispatch(Component client, AttributedCharacterIterator text, int committed, TextHitInfo caret) {
        client.dispatchEvent(new InputMethodEvent(client, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, text, committed,
                caret, caret));
    }

    static AttributedCharacterIterator composed(String text, InputMethodHighlight highlight) {
        AttributedString s = new AttributedString(text);
        s.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT, highlight);
        return s.getIterator();
    }

    static String text(AttributedCharacterIterator it) {
        if (it == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------ InputContext API

    private static List<Check> contextChecks(TextField field) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.info("TextField.getInputContext()", () -> {
            InputContext context = field.getInputContext();
            return context == null ? "null" : context.getClass().getName();
        }));
        InputContext context = InputContext.getInstance();
        try {
            checks.add(Checks.info("InputContext.getInstance()", () -> context.getClass().getName()));
            checks.add(Checks.info("getLocale()", () -> String.valueOf(context.getLocale())));
            checks.add(Checks.run("setCharacterSubsets(KANJI) then null", () -> {
                context.setCharacterSubsets(new Character.Subset[] { InputSubset.KANJI });
                context.setCharacterSubsets(null);
                return "set and reset";
            }));
            checks.add(Checks.run("endComposition()", () -> {
                context.endComposition();
                return "ended";
            }));
            checks.add(Check.info("isCompositionEnabled()", DesktopSupport.orElse(() -> {
                try {
                    return String.valueOf(context.isCompositionEnabled());
                } catch (UnsupportedOperationException e) {
                    return "UnsupportedOperationException";
                }
            }, "failed")));
            checks.add(Check.info("reconvert()", DesktopSupport.orElse(() -> {
                try {
                    context.reconvert();
                    return "done";
                } catch (UnsupportedOperationException e) {
                    return "UnsupportedOperationException";
                }
            }, "failed")));
            checks.add(Checks.info("getInputMethodControlObject()",
                    () -> String.valueOf(context.getInputMethodControlObject())));
        } finally {
            context.dispose();
        }
        checks.add(Checks.expect("InputSubset names", "LATIN LATIN_DIGITS TRADITIONAL_HANZI SIMPLIFIED_HANZI KANJI "
                + "HANJA HALFWIDTH_KATAKANA FULLWIDTH_LATIN FULLWIDTH_DIGITS", () -> String.join(" ",
                        List.of(InputSubset.LATIN, InputSubset.LATIN_DIGITS, InputSubset.TRADITIONAL_HANZI,
                                InputSubset.SIMPLIFIED_HANZI, InputSubset.KANJI, InputSubset.HANJA,
                                InputSubset.HALFWIDTH_KATAKANA, InputSubset.FULLWIDTH_LATIN, InputSubset.FULLWIDTH_DIGITS)
                                .stream().map(Object::toString).toList())));
        checks.add(Checks.expect("standard highlights (selected, state, variation)",
                "true 0 0, false 0 0, true 1 0, false 1 0", () -> String.join(", ", highlights().stream()
                        .map(h -> h.isSelected() + " " + h.getState() + " " + h.getVariation()).toList())));
        checks.add(Checks.expect("InputMethodHighlight with an explicit style", "true 1 2 {input method underline=4}",
                () -> {
                    InputMethodHighlight h = new InputMethodHighlight(true, InputMethodHighlight.CONVERTED_TEXT, 2,
                            Map.of(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_GRAY));
                    return h.isSelected() + " " + h.getState() + " " + h.getVariation() + " " + styles(h.getStyle());
                }));
        String[] names = { "SELECTED_RAW", "UNSELECTED_RAW", "SELECTED_CONVERTED", "UNSELECTED_CONVERTED" };
        List<InputMethodHighlight> standard = highlights();
        for (int i = 0; i < standard.size(); i++) {
            InputMethodHighlight h = standard.get(i);
            checks.add(Checks.info("Toolkit.mapInputMethodHighlight(" + names[i] + ")",
                    () -> styles(Toolkit.getDefaultToolkit().mapInputMethodHighlight(h))));
        }
        return checks;
    }

    static List<InputMethodHighlight> highlights() {
        return List.of(InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT,
                InputMethodHighlight.UNSELECTED_RAW_TEXT_HIGHLIGHT, InputMethodHighlight.SELECTED_CONVERTED_TEXT_HIGHLIGHT,
                InputMethodHighlight.UNSELECTED_CONVERTED_TEXT_HIGHLIGHT);
    }

    /**
     * A text attribute map with deterministic keys and values ({@code TextAttribute} names, colors as #RRGGBB).
     */
    static String styles(Map<TextAttribute, ?> map) {
        if (map == null) {
            return "null";
        }
        Map<String, String> sorted = new TreeMap<>();
        map.forEach((key, value) -> {
            String name = key.toString();
            name = name.substring(name.indexOf('(') + 1, name.lastIndexOf(')'));
            sorted.put(name, value instanceof Color c ? DesktopSupport.rgb(c.getRGB()) : String.valueOf(value));
        });
        return sorted.toString();
    }

    private static BufferedImage highlights(Font font) {
        List<AttributedString> samples = new ArrayList<>();
        for (InputMethodHighlight h : highlights()) {
            AttributedString s = new AttributedString(CONVERTED + " abc");
            s.addAttribute(TextAttribute.FONT, font);
            s.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT, h);
            samples.add(s);
        }
        for (Integer style : List.of(TextAttribute.UNDERLINE_LOW_ONE_PIXEL, TextAttribute.UNDERLINE_LOW_TWO_PIXEL,
                TextAttribute.UNDERLINE_LOW_DOTTED, TextAttribute.UNDERLINE_LOW_GRAY, TextAttribute.UNDERLINE_LOW_DASHED)) {
            AttributedString s = new AttributedString(CONVERTED + " abc");
            s.addAttribute(TextAttribute.FONT, font);
            s.addAttribute(TextAttribute.INPUT_METHOD_UNDERLINE, style);
            samples.add(s);
        }
        int cell = 108;
        return Snapshots.offscreen(cell * samples.size(), 44, g -> {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, cell * samples.size(), 44);
            g.setColor(new Color(0x263238));
            for (int i = 0; i < samples.size(); i++) {
                TextLayout layout = new TextLayout(samples.get(i).getIterator(), g.getFontRenderContext());
                layout.draw(g, i * cell + 4, 30);
            }
        });
    }

    // --------------------------------------------------------------------------------------------- active client

    /**
     * An on-the-spot input method client : committed text followed by the composed text, painted with
     * {@code TextLayout} (which maps the input method highlights to styles through the toolkit), and a caret.
     */
    static final class ActiveClient extends Component implements InputMethodRequests, InputMethodListener {

        private final StringBuilder committed = new StringBuilder();
        private AttributedString composed;
        private String composedText = "";
        private TextHitInfo caret;
        int textEvents;
        int caretEvents;

        ActiveClient(Font font) {
            setFont(font);
            enableInputMethods(true);
            addInputMethodListener(this);
        }

        String state() {
            return "committed '" + committed + "' composed '" + composedText + "' caret " + caret;
        }

        @Override
        public InputMethodRequests getInputMethodRequests() {
            return this;
        }

        @Override
        public void inputMethodTextChanged(InputMethodEvent e) {
            textEvents++;
            AttributedCharacterIterator text = e.getText();
            composed = null;
            composedText = "";
            if (text != null) {
                int count = e.getCommittedCharacterCount();
                char c = text.first();
                for (int i = 0; i < count; i++, c = text.next()) {
                    committed.append(c);
                }
                int start = text.getIndex();
                if (start < text.getEndIndex()) {
                    StringBuilder sb = new StringBuilder();
                    for (; c != CharacterIterator.DONE; c = text.next()) {
                        sb.append(c);
                    }
                    composedText = sb.toString();
                    composed = new AttributedString(text, start, text.getEndIndex());
                    composed.addAttribute(TextAttribute.FONT, getFont());
                }
            }
            caret = e.getCaret();
            e.consume();
            repaint();
        }

        @Override
        public void caretPositionChanged(InputMethodEvent e) {
            caretEvents++;
            caret = e.getCaret();
            e.consume();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(420, 56);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        private FontRenderContext frc() {
            return new FontRenderContext(null, true, false);
        }

        private float committedWidth() {
            return committed.isEmpty() ? 0
                    : new TextLayout(committed.toString(), getFont(), frc()).getAdvance();
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                g.setColor(new Color(0xFAFAFA));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0x90A4AE));
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g.setColor(new Color(0x212121));
                float x = 10;
                float baseline = 36;
                if (!committed.isEmpty()) {
                    new TextLayout(committed.toString(), getFont(), frc()).draw(g, x, baseline);
                    x += committedWidth();
                }
                if (composed != null) {
                    TextLayout layout = new TextLayout(composed.getIterator(), frc());
                    layout.draw(g, x, baseline);
                    if (caret != null) {
                        float[] info = layout.getCaretInfo(caret);
                        g.setColor(new Color(0xD50000));
                        g.fillRect(Math.round(x + info[0]), 10, 2, 32);
                    }
                }
            } finally {
                g.dispose();
            }
        }

        // InputMethodRequests

        @Override
        public Rectangle getTextLocation(TextHitInfo offset) {
            Point p = isShowing() ? getLocationOnScreen() : new Point();
            return new Rectangle(p.x + 10 + Math.round(committedWidth()), p.y + 8, 0, 32);
        }

        @Override
        public TextHitInfo getLocationOffset(int x, int y) {
            return null;
        }

        @Override
        public int getInsertPositionOffset() {
            return committed.length();
        }

        @Override
        public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                AttributedCharacterIterator.Attribute[] attributes) {
            return new AttributedString(committed.substring(beginIndex, endIndex)).getIterator();
        }

        @Override
        public int getCommittedTextLength() {
            return committed.length();
        }

        @Override
        public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
            return null;
        }

        @Override
        public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
            return new AttributedString("").getIterator();
        }
    }
}
