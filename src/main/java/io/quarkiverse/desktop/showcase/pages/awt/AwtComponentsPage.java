package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Label;
import java.awt.List;
import java.awt.MediaTracker;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.ScrollPane;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.ItemEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import javax.accessibility.Accessible;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Every AWT heavyweight component (native peers : Win32 controls on Windows, Java painted peers on Linux, whose text
 * components are Swing) : Button, Checkbox and CheckboxGroup, Choice, Label, List, Scrollbar, TextField, TextArea,
 * Canvas, ScrollPane and Panel, plus a {@code java.applet.Applet} run through its life cycle in a Frame by a minimal
 * applet viewer.
 * <p>
 * Capture method A : {@code printAll} renders the native controls through their peer ({@code WM_PRINT} on Windows).
 * Checks : component states and model round trips through the peers (selections, scroll positions, text selections),
 * events dispatched to the components, a {@code TextEvent} posted by the native control after {@code setText}, the
 * accessible roles (resource bundle of {@code javax.accessibility}), and the applet life cycle.
 */
@Singleton
public class AwtComponentsPage implements FeaturePage {

    private static final String[] PLANETS = { "Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus",
            "Neptune" };
    private static final String[] GREEK = { "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta",
            "Iota", "Kappa" };
    private static final String AREA_TEXT = "TextArea with both scroll bars: a line that is longer than the visible width "
            + "of the control\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8\nline 9\nline 10";
    private static final String WRAP_TEXT = "SCROLLBARS_VERTICAL_ONLY wraps words: AWT text areas without a horizontal "
            + "scroll bar wrap long lines at the width of the control.";

    // per build state (one content at a time)
    private Components c;
    private ChecksView checksView;
    private ChecksView appletView;
    private Container appletHolder;
    private Frame appletFrame;

    /** The components of one build. */
    private static final class Components {
        Button button;
        Button disabledButton;
        Button coloredButton;
        Checkbox checked;
        Checkbox unchecked;
        Checkbox disabledCheckbox;
        CheckboxGroup group;
        Checkbox small;
        Checkbox medium;
        Checkbox large;
        Choice colors;
        Choice disabledChoice;
        Choice numbers;
        Label left;
        Label center;
        Label right;
        Label fancy;
        Label symbols;
        List planets;
        List greek;
        Scrollbar horizontal;
        Scrollbar vertical;
        Scrollbar disabledScrollbar;
        TextField field;
        TextField password;
        TextField readOnly;
        TextField disabledField;
        TextArea areaBoth;
        TextArea areaWrap;
        TextArea areaNone;
        Canvas canvas;
        ScrollPane scrollAlways;
        ScrollPane scrollAsNeeded;
        ScrollPane scrollNever;
        final AtomicInteger actions = new AtomicInteger();
        final java.util.List<String> events = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger textEvents = new AtomicInteger();
    }

    @Override
    public String id() {
        return "awt-components";
    }

    @Override
    public String title() {
        return "AWT components";
    }

    @Override
    public String category() {
        return Categories.AWT;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() {
        c = new Components();
        Container row1 = Ui.row(12, buttons(), checkboxes(), choices(), labels());
        Container row2 = Ui.row(12, lists(), scrollbars(), textFields());
        Container row3 = Ui.row(12, textAreas(), canvas(), scrollPanes());

        checksView = ChecksView.table("Component checks", java.util.List.of(Check.info("state", "pending")));
        appletView = ChecksView.table("Applet life cycle", java.util.List.of(Check.info("applet", "pending")), 220, 560);
        appletHolder = Ui.column(0, Ui.caption("(the applet frame is rendered here once the page is ready)"));
        Container applet = AwtSupport.group("java.applet.Applet in a Frame (deprecated for removal)",
                Ui.row(16, appletHolder, appletView));

        return Ui.column(12,
                Ui.text("The heavyweight components of java.awt, each with a native peer. Rendered with printAll (native "
                        + "WM_PRINT on Windows). The checks read the state back through the peers and dispatch events "
                        + "to the components.", 1000),
                row1, row2, row3, applet, checksView);
    }

    // ------------------------------------------------------------------------------------------------- components

    private Component buttons() {
        Panel p = AwtSupport.panel(new GridLayout(4, 1, 0, 6), 200, 132);
        c.button = new Button("Default");
        c.button.setName("defaultButton");
        c.button.addActionListener(e -> {
            c.actions.incrementAndGet();
            c.events.add("action " + e.getActionCommand() + " mods=" + e.getModifiers());
        });
        c.disabledButton = new Button("Disabled");
        c.disabledButton.setEnabled(false);
        c.coloredButton = new Button("Colored");
        c.coloredButton.setBackground(new Color(0x1976D2));
        c.coloredButton.setForeground(Color.WHITE);
        c.coloredButton.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        Button longLabel = new Button("A much longer label than usual");
        p.add(c.button);
        p.add(c.disabledButton);
        p.add(c.coloredButton);
        p.add(longLabel);
        return AwtSupport.group("Button", p);
    }

    private Component checkboxes() {
        Panel p = AwtSupport.panel(new GridLayout(6, 1, 0, 0), 200, 132);
        c.checked = new Checkbox("Checked", true);
        c.unchecked = new Checkbox("Unchecked", false);
        c.unchecked.addItemListener(e -> c.events.add("item " + e.getItem() + " "
                + (e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED")));
        c.disabledCheckbox = new Checkbox("Disabled, checked", true);
        c.disabledCheckbox.setEnabled(false);
        c.group = new CheckboxGroup();
        c.small = new Checkbox("Small (radio)", false, c.group);
        c.medium = new Checkbox("Medium (radio)", true, c.group);
        c.large = new Checkbox("Large (radio)", false, c.group);
        for (Checkbox box : new Checkbox[] { c.checked, c.unchecked, c.disabledCheckbox, c.small, c.medium, c.large }) {
            p.add(box);
        }
        return AwtSupport.group("Checkbox, CheckboxGroup", p);
    }

    private Component choices() {
        Panel p = AwtSupport.panel(new GridLayout(3, 1, 0, 14), 180, 104);
        c.colors = new Choice();
        for (String color : new String[] { "Red", "Green", "Blue", "Cyan" }) {
            c.colors.add(color);
        }
        c.colors.select("Blue");
        c.disabledChoice = new Choice();
        c.disabledChoice.add("Disabled");
        c.disabledChoice.setEnabled(false);
        c.numbers = new Choice();
        for (int i = 1; i <= 20; i++) {
            c.numbers.add("Item " + i);
        }
        c.numbers.select(11);
        p.add(c.colors);
        p.add(c.disabledChoice);
        p.add(c.numbers);
        return AwtSupport.group("Choice", p);
    }

    private Component labels() {
        Panel p = AwtSupport.panel(new GridLayout(5, 1, 0, 3), 250, 132);
        c.left = new Label("Label.LEFT", Label.LEFT);
        c.center = new Label("Label.CENTER", Label.CENTER);
        c.right = new Label("Label.RIGHT", Label.RIGHT);
        for (Label label : new Label[] { c.left, c.center, c.right }) {
            label.setBackground(new Color(0xE3F2FD));
            p.add(label);
        }
        c.fancy = new Label("Serif italic, colored", Label.CENTER);
        c.fancy.setFont(new Font(Font.SERIF, Font.ITALIC, 16));
        c.fancy.setForeground(new Color(0xAD1457));
        p.add(c.fancy);
        // symbols and dingbats : on Windows, the Symbol and Wingdings component fonts of the logical Dialog font are encoded
        // by charset classes the font configuration instantiates by reflection (sun.awt.Symbol, sun.awt.windows.WingDings)
        c.symbols = new Label("∀∂∃∇∈ ✂✈✓✔❖ éßç", Label.CENTER);
        c.symbols.setName("symbolsLabel");
        p.add(c.symbols);
        return AwtSupport.group("Label", p);
    }

    private Component lists() {
        Panel p = AwtSupport.panel(new GridLayout(1, 2, 10, 0), 300, 128);
        c.planets = new List(6, false);
        for (String planet : PLANETS) {
            c.planets.add(planet);
        }
        c.planets.select(2);
        c.planets.addActionListener(e -> c.events.add("list action " + e.getActionCommand()));
        c.greek = new List(6, true);
        for (String letter : GREEK) {
            c.greek.add(letter);
        }
        c.greek.select(0);
        c.greek.select(2);
        c.greek.select(3);
        p.add(c.planets);
        p.add(c.greek);
        return AwtSupport.group("List (single, multiple selection)", p);
    }

    private Component scrollbars() {
        Panel p = AwtSupport.panel(null, 250, 128);
        c.horizontal = new Scrollbar(Scrollbar.HORIZONTAL, 30, 20, 0, 100);
        c.horizontal.setBounds(0, 0, 200, 18);
        c.horizontal.addAdjustmentListener(e -> c.events.add("adjustment " + adjustmentType(e) + " value="
                + e.getValue()));
        c.disabledScrollbar = new Scrollbar(Scrollbar.HORIZONTAL, 60, 10, 0, 100);
        c.disabledScrollbar.setBounds(0, 34, 200, 18);
        c.disabledScrollbar.setEnabled(false);
        c.vertical = new Scrollbar(Scrollbar.VERTICAL, 70, 10, 0, 100);
        c.vertical.setBounds(220, 0, 18, 128);
        p.add(c.horizontal);
        p.add(c.disabledScrollbar);
        p.add(c.vertical);
        return AwtSupport.group("Scrollbar", p);
    }

    private Component textFields() {
        Panel p = AwtSupport.panel(new GridLayout(4, 1, 0, 8), 300, 128);
        c.field = new TextField("Hello AWT", 20);
        c.field.addTextListener(e -> c.textEvents.incrementAndGet());
        c.password = new TextField("secret", 20);
        c.password.setEchoChar('*');
        c.readOnly = new TextField("Read only (not editable)", 20);
        c.readOnly.setEditable(false);
        c.disabledField = new TextField("Disabled", 20);
        c.disabledField.setEnabled(false);
        p.add(c.field);
        p.add(c.password);
        p.add(c.readOnly);
        p.add(c.disabledField);
        return AwtSupport.group("TextField (echo char, read only, disabled)", p);
    }

    private Component textAreas() {
        Panel p = AwtSupport.panel(new GridLayout(1, 3, 8, 0), 470, 130);
        c.areaBoth = new TextArea(AREA_TEXT, 6, 20, TextArea.SCROLLBARS_BOTH);
        c.areaWrap = new TextArea(WRAP_TEXT, 6, 14, TextArea.SCROLLBARS_VERTICAL_ONLY);
        c.areaNone = new TextArea("SCROLLBARS_NONE\nno scroll bar\nat all", 6, 14, TextArea.SCROLLBARS_NONE);
        c.areaNone.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        p.add(c.areaBoth);
        p.add(c.areaWrap);
        p.add(c.areaNone);
        return AwtSupport.group("TextArea (both, vertical only, none)", p);
    }

    private Component canvas() {
        c.canvas = new PaintedCanvas();
        c.canvas.setName("paintedCanvas");
        c.canvas.setPreferredSize(new Dimension(170, 130));
        return AwtSupport.group("Canvas (custom paint)", c.canvas);
    }

    private Component scrollPanes() {
        Panel p = AwtSupport.panel(new GridLayout(1, 3, 8, 0), 310, 130);
        c.scrollAlways = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
        c.scrollAlways.add(grid(400, 300));
        c.scrollAsNeeded = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        Panel small = new Panel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        small.add(new Label("fits"));
        small.setBackground(new Color(0xE8F5E9));
        c.scrollAsNeeded.add(small);
        c.scrollNever = new ScrollPane(ScrollPane.SCROLLBARS_NEVER);
        c.scrollNever.add(grid(400, 300));
        p.add(c.scrollAlways);
        p.add(c.scrollAsNeeded);
        p.add(c.scrollNever);
        return AwtSupport.group("ScrollPane (always, as needed, never)", p);
    }

    /**
     * A large lightweight child for the scroll panes : a checkered grid with coordinates.
     */
    private static Component grid(int width, int height) {
        Component grid = Ui.painted(width, height, g -> {
            AwtSupport.textHints(g);
            g.setColor(new Color(0xFFFDE7));
            g.fillRect(0, 0, width, height);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
            for (int y = 0; y < height; y += 20) {
                for (int x = 0; x < width; x += 20) {
                    if ((x / 20 + y / 20) % 2 == 0) {
                        g.setColor(new Color(0xFFE082));
                        g.fillRect(x, y, 20, 20);
                    }
                }
            }
            g.setColor(new Color(0x5D4037));
            for (int y = 0; y < height; y += 60) {
                for (int x = 0; x < width; x += 80) {
                    g.drawString(x + "," + y, x + 3, y + 13);
                }
            }
        });
        return grid;
    }

    /**
     * A Canvas painted with Java2D (a heavyweight : the peer paints the background, paint draws the rest).
     */
    static final class PaintedCanvas extends Canvas {

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                AwtSupport.textHints(g);
                int w = getWidth();
                int h = getHeight();
                g.setPaint(new GradientPaint(0, 0, new Color(0x263238), w, h, new Color(0x00695C)));
                g.fillRect(0, 0, w, h);
                g.setColor(new Color(0x80CBC4));
                g.fill(new Ellipse2D.Double(18, 18, 60, 60));
                g.setColor(new Color(0xFFCA28));
                g.fill(new RoundRectangle2D.Double(70, 50, 80, 50, 16, 16));
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
                g.drawString("Canvas.paint", 12, h - 12);
            } finally {
                g.dispose();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        Components comps = c;
        ChecksView view = checksView;
        // the native controls exist once the content is displayable : read their state back through the peers
        return Edt.rounds(2)
                .thenApply(v -> {
                    // scroll positions and text selections need the peers
                    comps.scrollAlways.setScrollPosition(40, 30);
                    comps.scrollNever.setScrollPosition(40, 30);
                    comps.areaBoth.setCaretPosition(0);
                    comps.greek.makeVisible(0);
                    comps.numbers.select(11);
                    // TextEvent : posted asynchronously by the peer (Windows : EN_CHANGE notification of the EDIT control)
                    comps.field.setText("Hello AWT peer");
                    comps.field.setText("Hello AWT");
                    return null;
                })
                .thenCompose(v -> Edt.until(() -> comps.textEvents.get() > 0, 3000, "TextEvent")
                        .handle((r, error) -> null))
                .thenCompose(v -> Edt.rounds(2))
                .thenAccept(v -> view.setChecks(checks(comps)))
                .thenCompose(v -> runApplet())
                .thenCompose(v -> Edt.stable(content, 2000));
    }

    private java.util.List<Check> checks(Components comps) {
        java.util.List<Check> checks = new ArrayList<>();
        // Button
        checks.add(Checks.expect("Button label / default action command", "Default / Default",
                () -> comps.button.getLabel() + " / " + comps.button.getActionCommand()));
        checks.add(Checks.expect("ActionEvent dispatched to the Button", "action cmd-1 mods=0 -> 1 action(s)", () -> {
            int before = comps.actions.get();
            comps.button.dispatchEvent(new ActionEvent(comps.button, ActionEvent.ACTION_PERFORMED, "cmd-1"));
            return comps.events.getLast() + " -> " + (comps.actions.get() - before) + " action(s)";
        }));
        checks.add(Checks.expect("disabled Button isEnabled", false, comps.disabledButton::isEnabled));
        // Checkbox
        checks.add(Checks.expect("Checkbox states (checked, unchecked, disabled)", "true, false, true",
                () -> comps.checked.getState() + ", " + comps.unchecked.getState() + ", " + comps.disabledCheckbox.getState()));
        checks.add(Checks.expect("CheckboxGroup selected", "Medium (radio)",
                () -> comps.group.getSelectedCheckbox().getLabel()));
        checks.add(Checks.expect("CheckboxGroup.setSelectedCheckbox(Large) : states", "false, false, true", () -> {
            comps.group.setSelectedCheckbox(comps.large);
            String states = comps.small.getState() + ", " + comps.medium.getState() + ", " + comps.large.getState();
            comps.group.setSelectedCheckbox(comps.medium);
            return states;
        }));
        checks.add(Checks.expect("ItemEvent dispatched to a Checkbox", "item Unchecked SELECTED", () -> {
            comps.unchecked.dispatchEvent(new ItemEvent(comps.unchecked, ItemEvent.ITEM_STATE_CHANGED, "Unchecked",
                    ItemEvent.SELECTED));
            return comps.events.getLast();
        }));
        // Choice
        checks.add(Checks.expect("Choice count / selected", "4 / Blue (2)", () -> comps.colors.getItemCount() + " / "
                + comps.colors.getSelectedItem() + " (" + comps.colors.getSelectedIndex() + ")"));
        checks.add(Checks.expect("Choice insert, remove, select", "Red Magenta Green Blue Cyan / Cyan", () -> {
            comps.colors.insert("Magenta", 1);
            comps.colors.select("Cyan");
            String items = items(comps.colors);
            String selected = comps.colors.getSelectedItem();
            comps.colors.remove("Magenta");
            comps.colors.select("Blue");
            return items + " / " + selected;
        }));
        checks.add(Checks.expect("Choice of 20 items : selected", "Item 12", comps.numbers::getSelectedItem));
        // Label
        checks.add(Checks.expect("Label alignments", "0 1 2", () -> comps.left.getAlignment() + " "
                + comps.center.getAlignment() + " " + comps.right.getAlignment()));
        // List
        checks.add(Checks.expect("List (single) selected", "Earth [2], multiple=false",
                () -> comps.planets.getSelectedItem() + " " + Arrays.toString(comps.planets.getSelectedIndexes())
                        + ", multiple=" + comps.planets.isMultipleMode()));
        checks.add(Checks.expect("List (multiple) selected", "[0, 2, 3] Alpha Gamma Delta, rows=6",
                () -> Arrays.toString(comps.greek.getSelectedIndexes()) + " "
                        + String.join(" ", comps.greek.getSelectedItems()) + ", rows=" + comps.greek.getRows()));
        checks.add(Checks.expect("List deselect / isIndexSelected", "[0, 3] false", () -> {
            comps.greek.deselect(2);
            String result = Arrays.toString(comps.greek.getSelectedIndexes()) + " " + comps.greek.isIndexSelected(2);
            comps.greek.select(2);
            return result;
        }));
        checks.add(Checks.expect("List makeVisible(9) / getVisibleIndex", 9, () -> {
            comps.greek.makeVisible(9);
            int index = comps.greek.getVisibleIndex();
            comps.greek.makeVisible(0);
            return index;
        }));
        checks.add(Checks.expect("ActionEvent dispatched to the List", "list action Earth", () -> {
            comps.planets.dispatchEvent(new ActionEvent(comps.planets, ActionEvent.ACTION_PERFORMED, "Earth"));
            return comps.events.getLast();
        }));
        // Scrollbar
        checks.add(Checks.expect("Scrollbar model (value, visible, min, max, unit, block)", "30 20 0 100 1 10",
                () -> comps.horizontal.getValue() + " " + comps.horizontal.getVisibleAmount() + " "
                        + comps.horizontal.getMinimum() + " " + comps.horizontal.getMaximum() + " "
                        + comps.horizontal.getUnitIncrement() + " " + comps.horizontal.getBlockIncrement()));
        checks.add(Checks.expect("Scrollbar.setValue(95) is clamped to max - visible", 80, () -> {
            comps.horizontal.setValue(95);
            int value = comps.horizontal.getValue();
            comps.horizontal.setValue(30);
            return value;
        }));
        checks.add(Checks.expect("AdjustmentEvent dispatched to the Scrollbar", "adjustment TRACK value=42", () -> {
            comps.horizontal.dispatchEvent(new AdjustmentEvent(comps.horizontal, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                    AdjustmentEvent.TRACK, 42));
            return comps.events.getLast();
        }));
        checks.add(Checks.expect("Scrollbar orientations", "0 1", () -> comps.horizontal.getOrientation() + " "
                + comps.vertical.getOrientation()));
        // TextField
        checks.add(Checks.expect("TextField text / columns", "Hello AWT / 20",
                () -> comps.field.getText() + " / " + comps.field.getColumns()));
        checks.add(Checks.expect("TextField echo char", "'*' true secret",
                () -> "'" + comps.password.getEchoChar() + "' " + comps.password.echoCharIsSet() + " "
                        + comps.password.getText()));
        checks.add(Checks.expect("TextField select(6, 9) through the peer", "AWT [6, 9]", () -> {
            comps.field.select(6, 9);
            String result = comps.field.getSelectedText() + " [" + comps.field.getSelectionStart() + ", "
                    + comps.field.getSelectionEnd() + "]";
            comps.field.select(0, 0);
            return result;
        }));
        checks.add(Checks.expect("TextField editable (read only, disabled)", "false, true",
                () -> comps.readOnly.isEditable() + ", " + comps.disabledField.isEditable()));
        checks.add(Check.of("TextEvent posted by the peer after setText", comps.textEvents.get() > 0,
                comps.textEvents.get() > 0 ? "received" : "none within 3 s"));
        // TextArea
        checks.add(Checks.expect("TextArea rows, columns, scroll bar policies", "6x20 0, 6x14 1, 6x14 3",
                () -> comps.areaBoth.getRows() + "x" + comps.areaBoth.getColumns() + " "
                        + comps.areaBoth.getScrollbarVisibility() + ", " + comps.areaWrap.getRows() + "x"
                        + comps.areaWrap.getColumns() + " " + comps.areaWrap.getScrollbarVisibility() + ", "
                        + comps.areaNone.getRows() + "x" + comps.areaNone.getColumns() + " "
                        + comps.areaNone.getScrollbarVisibility()));
        checks.add(Checks.expect("TextArea append, insert, replaceRange", "[one] two three!", () -> {
            TextArea area = comps.areaNone;
            String original = area.getText();
            area.setText("one two");
            area.append(" three");
            area.insert("[", 0);
            area.replaceRange("]", 4, 4);
            area.append("!");
            String result = area.getText();
            area.setText(original);
            return result;
        }));
        checks.add(Checks.expect("TextArea select(0, 8) through the peer", "TextArea",
                () -> {
                    comps.areaBoth.select(0, 8);
                    String selected = comps.areaBoth.getSelectedText();
                    comps.areaBoth.select(0, 0);
                    return selected;
                }));
        checks.add(Checks.expect("TextArea text length", AREA_TEXT.length(), () -> comps.areaBoth.getText().length()));
        // ScrollPane
        checks.add(Checks.expect("ScrollPane policies", "1 0 2", () -> comps.scrollAlways.getScrollbarDisplayPolicy() + " "
                + comps.scrollAsNeeded.getScrollbarDisplayPolicy() + " " + comps.scrollNever.getScrollbarDisplayPolicy()));
        checks.add(Checks.expect("ScrollPane.setScrollPosition(40, 30)", "40,30 / 40,30", () -> point(comps.scrollAlways)
                + " / " + point(comps.scrollNever)));
        checks.add(Checks.expect("ScrollPane adjustable maxima (child size)", "400x300",
                () -> comps.scrollAlways.getHAdjustable().getMaximum() + "x" + comps.scrollAlways.getVAdjustable().getMaximum()));
        checks.add(Checks.info("ScrollPane viewport / scroll bar sizes", () -> AwtSupport.size(comps.scrollAlways.getViewportSize())
                + ", vbar " + comps.scrollAlways.getVScrollbarWidth() + ", hbar " + comps.scrollAlways.getHScrollbarHeight()));
        // FileDialog (a native dialog : never shown here, see the desktop pages) : its model
        checks.add(Checks.expect("FileDialog model (mode, title, file, directory, multiple, files before showing)",
                "0 Open a text file notes.txt true 0 / 1", () -> {
                    java.awt.FileDialog dialog = new java.awt.FileDialog((Frame) null, "Open a text file",
                            java.awt.FileDialog.LOAD);
                    dialog.setFile("notes.txt");
                    dialog.setDirectory(".");
                    dialog.setMultipleMode(true);
                    dialog.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
                    java.awt.FileDialog save = new java.awt.FileDialog((Frame) null, "Save", java.awt.FileDialog.SAVE);
                    return dialog.getMode() + " " + dialog.getTitle() + " " + dialog.getFile() + " "
                            + dialog.isMultipleMode() + " " + dialog.getFiles().length + " / " + save.getMode();
                }));
        // peers, geometry, fonts
        java.util.List<Component> all = java.util.List.of(comps.button, comps.checked, comps.colors, comps.left, comps.planets,
                comps.horizontal, comps.field, comps.areaBoth, comps.canvas, comps.scrollAlways);
        checks.add(Checks.expect("heavyweights : displayable, not lightweight", "10 / 0", () -> all.stream()
                .filter(Component::isDisplayable).count() + " / " + all.stream().filter(Component::isLightweight).count()));
        checks.add(Checks.expect("heavyweights : baseline (not implemented by AWT peers)", "-1 -1 -1",
                () -> comps.button.getBaseline(100, 24) + " " + comps.left.getBaseline(100, 24) + " "
                        + comps.field.getBaseline(100, 24)));
        checks.add(Checks.expect("accessible roles (javax.accessibility bundle)",
                "push button, check box, check box, combo box, label, list, scroll bar, text, text, canvas, scroll pane",
                () -> String.join(", ", java.util.List.of(comps.button, comps.checked, comps.medium, comps.colors,
                        comps.left, comps.planets, comps.horizontal, comps.field, comps.areaBoth, comps.canvas,
                        comps.scrollAlways).stream()
                        .map(x -> ((Accessible) x).getAccessibleContext().getAccessibleRole().toDisplayString(Locale.US))
                        .toList())));
        checks.add(Checks.info("preferred sizes (Button, Checkbox, Choice, Label, List, TextField, TextArea)",
                () -> String.join(" ", java.util.List.of(comps.button, comps.checked, comps.colors, comps.left, comps.planets,
                        comps.field, comps.areaBoth).stream().map(x -> AwtSupport.size(x.getPreferredSize())).toList())));
        // Windows : with an Arabic or Hebrew keyboard layout at start-up, AWT lays the native controls out right to left
        // (and printAll of a right-to-left EDIT control shows no text)
        checks.add(Check.info("keyboard layout (native control direction on Windows)", AwtSupport.inputLocale()
                + (AwtSupport.rightToLeftInput() ? ", right to left" : ", left to right")));
        checks.add(Checks.info("default font of the components",
                () -> comps.button.getFont().getFamily(Locale.ROOT) + " " + comps.button.getFont().getSize()));
        checks.add(Checks.info("FontMetrics of the Button (height, ascent, width of its label)", () -> {
            var metrics = comps.button.getFontMetrics(comps.button.getFont());
            return metrics.getHeight() + ", " + metrics.getAscent() + ", " + metrics.stringWidth(comps.button.getLabel());
        }));
        return checks;
    }

    private static String items(Choice choice) {
        java.util.List<String> items = new ArrayList<>();
        for (int i = 0; i < choice.getItemCount(); i++) {
            items.add(choice.getItem(i));
        }
        return String.join(" ", items);
    }

    private static String point(ScrollPane pane) {
        return pane.getScrollPosition().x + "," + pane.getScrollPosition().y;
    }

    private static String adjustmentType(AdjustmentEvent e) {
        return switch (e.getAdjustmentType()) {
            case AdjustmentEvent.UNIT_INCREMENT -> "UNIT_INCREMENT";
            case AdjustmentEvent.UNIT_DECREMENT -> "UNIT_DECREMENT";
            case AdjustmentEvent.BLOCK_INCREMENT -> "BLOCK_INCREMENT";
            case AdjustmentEvent.BLOCK_DECREMENT -> "BLOCK_DECREMENT";
            case AdjustmentEvent.TRACK -> "TRACK";
            default -> "type " + e.getAdjustmentType();
        };
    }

    // ----------------------------------------------------------------------------------------------------- applet

    /**
     * Runs the applet life cycle in a Frame (init, start, rendered, stop, destroy), shows the rendered applet in the
     * page and the life cycle in its table.
     */
    @SuppressWarnings({ "deprecation", "removal" })
    private CompletionStage<Void> runApplet() {
        ChecksView view = appletView;
        Container holder = appletHolder;
        java.util.List<Check> checks = new ArrayList<>();
        Label status = new Label("Status:");
        status.setName("appletStatus");
        AppletDemo.Viewer viewer = new AppletDemo.Viewer(status);
        AppletDemo.ShowcaseApplet applet = new AppletDemo.ShowcaseApplet();
        applet.setName("showcaseApplet");
        viewer.register(applet);

        Frame frame = new Frame(viewer.getParameter("title"));
        appletFrame = frame;
        if (ShowcaseMode.snapshot()) {
            frame.setAutoRequestFocus(false);
        }
        Panel root = new Panel(new BorderLayout());
        root.add(applet, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        frame.add(root);
        frame.pack();
        Rectangle area = AwtSupport.secondaryArea(frame.getWidth(), frame.getHeight());
        frame.setLocation(area.x, area.y);
        frame.setVisible(true);

        applet.init();
        viewer.setActive(true);
        applet.start();
        root.validate();

        return Edt.background(() -> {
            // image loading through the applet context (Toolkit.createImage(URL) of a URL relative to the code base)
            MediaTracker tracker = new MediaTracker(applet);
            Image image = applet.image();
            if (image != null) {
                tracker.addImage(image, 0);
                tracker.waitForAll(5000);
            }
            return tracker.isErrorAny() ? "error" : image == null ? "none" : "loaded";
        }).handle((loading, error) -> error == null ? loading : Checks.describe(error))
                .thenCompose(loading -> Edt.rounds(2).thenCompose(v -> Edt.stable(root, 1500)).thenApply(v -> loading))
                .thenAccept(loading -> {
                    Image image = applet.image();
                    checks.add(Checks.expect("Applet.getImage(codeBase, name) through the AppletContext", "loaded 48x48",
                            () -> loading + " " + (image == null ? "?" : image.getWidth(null) + "x" + image.getHeight(null))));
                    BufferedImage rendered = Snapshots.render(root);
                    viewer.setActive(false);
                    applet.stop();
                    applet.destroy();
                    frame.dispose();
                    appletFrame = null;

                    checks.add(Checks.expect("life cycle", "init(active=false) start(active=true) stop(active=false) destroy",
                            () -> String.join(" ", applet.lifecycle())));
                    checks.add(Checks.expect("showStatus calls", "started stopped", () -> String.join(" ", viewer.statuses())));
                    checks.add(Checks.expect("getParameter(greeting)", "Hello from java.applet",
                            () -> applet.getParameter("greeting")));
                    checks.add(Checks.expect("getAppletInfo / parameter info rows", "quarkus-desktop showcase applet / 4",
                            () -> applet.getAppletInfo() + " / " + applet.getParameterInfo().length));
                    checks.add(Checks.expect("AppletContext.getApplets / getApplet(name)", "1 / true",
                            () -> Collections.list(applet.getAppletContext().getApplets()).size() + " / "
                                    + (applet.getAppletContext().getApplet("showcaseApplet") == applet)));
                    checks.add(Checks.expect("accessible role (AccessibleApplet)", "frame",
                            () -> applet.getAccessibleContext().getAccessibleRole().toDisplayString(Locale.US)));
                    checks.add(Checks.expect("code base resolves the image (same URL scheme)", true,
                            () -> new java.net.URL(applet.getCodeBase(), "applet-image.png").getProtocol()
                                    .equals(applet.getCodeBase().getProtocol())));
                    checks.add(Checks.info("rendered applet frame content", () -> rendered.getWidth() + "x" + rendered.getHeight()));
                    holder.removeAll();
                    holder.add(Ui.image(rendered));
                    holder.invalidate();
                    view.setChecks(checks);
                    Snapshots.layout(holder);
                    holder.repaint();
                });
    }

    @Override
    public void dispose(Component content) {
        if (appletFrame != null) {
            appletFrame.dispose();
        }
        appletFrame = null;
        c = null;
        checksView = null;
        appletView = null;
        appletHolder = null;
    }
}
