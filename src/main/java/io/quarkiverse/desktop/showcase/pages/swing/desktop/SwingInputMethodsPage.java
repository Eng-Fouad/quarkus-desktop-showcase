package io.quarkiverse.desktop.showcase.pages.swing.desktop;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodHighlight;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;

/**
 * Input methods with Swing : synthetic {@code InputMethodEvent}s (raw then converted composed text, a commit, a new
 * composition with two highlights and a caret move) sent to a {@code JTextField}, a {@code JTextArea} and a
 * {@code JTextField} subclass overriding {@code processInputMethodEvent}, the composed text rendering of the text
 * components, and their {@code InputMethodRequests} (committed text, text location, location offset, cancel of the
 * latest committed text).
 * <p>
 * {@code JTextComponent} delivers committed text as KEY_TYPED events unless its class overrides
 * {@code processInputMethodEvent} : it looks the method up with {@code getDeclaredMethod} on the application subclass,
 * a reflective query a native executable must support (registration of the declared methods of application
 * subclasses of {@code JTextComponent}).
 */
@Singleton
public class SwingInputMethodsPage implements FeaturePage {

    private static final String RAW = "にほんご";
    private static final String CONVERTED = "日本語";
    private static final String NEXT = "かなかんじ";

    // per build state
    private JTextField field;
    private ChecksView locationView;

    @Override
    public String id() {
        return "desktop-input-methods-swing";
    }

    @Override
    public String title() {
        return "Input methods (Swing)";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 65;
    }

    @Override
    public Component build() {
        Font font = new Font(Platforms.Families.japanese(), Font.PLAIN, 18);
        field = new JTextField(18);
        JTextArea area = new JTextArea("line 1\n", 3, 18);
        OverridingTextField overriding = new OverridingTextField(18);
        List<Check> checks = new ArrayList<>();
        sequence(checks, "JTextField", field, font, "");
        sequence(checks, "JTextArea", area, font, "line 1\n");
        sequence(checks, "JTextField subclass overriding processInputMethodEvent", overriding, font, "");
        checks.add(Checks.expect("subclass: processInputMethodEvent calls", 5, () -> overriding.calls));

        // cancelLatestCommittedText removes the latest committed text from the document
        JTextField cancel = new JTextField(10);
        cancel.dispatchEvent(event(cancel, new AttributedString(CONVERTED).getIterator(), 3, null));
        checks.add(Checks.expect("cancelLatestCommittedText (returned, remaining)", "日本語 ''", () -> {
            AttributedCharacterIterator latest = cancel.getInputMethodRequests().cancelLatestCommittedText(null);
            return text(latest) + " '" + cancel.getText() + "'";
        }));
        checks.add(Checks.info("InputMethodRequests implementation",
                () -> field.getInputMethodRequests().getClass().getName()));
        checks.add(Checks.expect("input methods enabled by default", true,
                () -> new JTextField().getInputMethodRequests() != null && new JTextField().isFocusable()));

        locationView = ChecksView.table("Text location (layout dependent)", List.of(Check.info("state", "pending")));
        JPanel components = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        components.setOpaque(false);
        components.add(titled("JTextField", field));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(240, 90));
        components.add(titled("JTextArea", scroll));
        components.add(titled("overriding processInputMethodEvent", overriding));

        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setOpaque(false);
        JLabel intro = new JLabel("<html><body style='width:720px'>Synthetic input method events in Swing text "
                + "components : the composed text (かなかんじ, two highlights) stays underlined in the document until it "
                + "is committed ; 日本語 was committed before.</body></html>");
        page.add(intro, BorderLayout.NORTH);
        page.add(components, BorderLayout.CENTER);
        JPanel tables = new JPanel(new BorderLayout(0, 14));
        tables.setOpaque(false);
        tables.add(ChecksView.table("Composition and commit", checks, 330, 1000), BorderLayout.NORTH);
        tables.add(locationView, BorderLayout.CENTER);
        page.add(tables, BorderLayout.SOUTH);
        return page;
    }

    private static JComponent titled(String title, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        p.add(label, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder());
        return p;
    }

    /**
     * The event sequence of a host input method, with checks after each step.
     */
    private static void sequence(List<Check> checks, String name, JTextComponent text, Font font, String initial) {
        text.setFont(font);
        text.setCaretPosition(text.getDocument().getLength());
        AtomicInteger typed = new AtomicInteger();
        text.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                typed.incrementAndGet();
            }
        });
        InputMethodRequests requests = text.getInputMethodRequests();

        text.dispatchEvent(event(text, composed(RAW, InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT), 0,
                TextHitInfo.leading(4)));
        checks.add(Checks.expect(name + ": raw composed text (document, committed length)",
                initial + RAW + " " + initial.length(), () -> text.getText() + " " + requests.getCommittedTextLength()));

        text.dispatchEvent(event(text, composed(CONVERTED, InputMethodHighlight.SELECTED_CONVERTED_TEXT_HIGHLIGHT), 0,
                TextHitInfo.trailing(2)));
        text.dispatchEvent(event(text, new AttributedString(CONVERTED).getIterator(), 3, null));
        checks.add(Checks.expect(name + ": committed (document, committed text)", initial + CONVERTED + " " + CONVERTED,
                () -> text.getText() + " " + text(requests.getCommittedText(initial.length(),
                        initial.length() + 3, null))));
        boolean overriding = text instanceof OverridingTextField;
        checks.add(Checks.expect(name + ": KEY_TYPED events for the committed text", overriding ? 0 : 3, typed::get));

        AttributedString next = new AttributedString(NEXT);
        next.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT, InputMethodHighlight.UNSELECTED_CONVERTED_TEXT_HIGHLIGHT,
                0, 2);
        next.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT, InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT, 2, 5);
        text.dispatchEvent(event(text, next.getIterator(), 0, TextHitInfo.leading(2)));
        text.dispatchEvent(new InputMethodEvent(text, InputMethodEvent.CARET_POSITION_CHANGED, TextHitInfo.leading(4),
                TextHitInfo.leading(4)));
        int base = initial.length() + 3;
        checks.add(Checks.expect(name + ": new composition (document, committed length, caret)",
                initial + CONVERTED + NEXT + " " + base + " " + (base + 4),
                () -> text.getText() + " " + requests.getCommittedTextLength() + " " + text.getCaretPosition()));
        checks.add(Checks.expect(name + ": insert position, selected text", base + " null",
                () -> requests.getInsertPositionOffset() + " " + text(requests.getSelectedText(null))));
    }

    private static InputMethodEvent event(Component source, AttributedCharacterIterator text, int committed,
            TextHitInfo caret) {
        return new InputMethodEvent(source, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, text, committed, caret, caret);
    }

    private static AttributedCharacterIterator composed(String text, InputMethodHighlight highlight) {
        AttributedString s = new AttributedString(text);
        s.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT, highlight);
        return s.getIterator();
    }

    private static String text(AttributedCharacterIterator it) {
        if (it == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = locationView;
        JTextField text = field;
        return Edt.rounds(2).thenAccept(v -> {
            List<Check> checks = new ArrayList<>();
            InputMethodRequests requests = text.getInputMethodRequests();
            checks.add(Checks.info("getTextLocation (at the caret), relative to the field", () -> {
                Rectangle r = requests.getTextLocation(TextHitInfo.leading(0));
                Point origin = text.getLocationOnScreen();
                return (r.x - origin.x) + "," + (r.y - origin.y) + " " + r.width + "x" + r.height;
            }));
            checks.add(Checks.info("getLocationOffset(2 px right of that location)", () -> {
                Rectangle r = requests.getTextLocation(TextHitInfo.leading(0));
                return String.valueOf(requests.getLocationOffset(r.x + 2, r.y + r.height / 2));
            }));
            checks.add(Checks.expect("getLocationOffset(outside the composed text)", "null", () -> {
                Point origin = text.getLocationOnScreen();
                return String.valueOf(requests.getLocationOffset(origin.x + 2, origin.y + 2));
            }));
            view.setChecks(checks);
        });
    }

    @Override
    public void dispose(Component content) {
        field = null;
        locationView = null;
    }

    /**
     * An active input method client subclass : overrides {@code processInputMethodEvent} (detected by
     * {@code JTextComponent} with {@code getDeclaredMethod}), so the committed text is inserted by an action instead
     * of KEY_TYPED events.
     */
    static final class OverridingTextField extends JTextField {

        int calls;

        OverridingTextField(int columns) {
            super(columns);
        }

        @Override
        protected void processInputMethodEvent(InputMethodEvent e) {
            calls++;
            super.processInputMethodEvent(e);
        }
    }
}
