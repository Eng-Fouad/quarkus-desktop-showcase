package io.quarkiverse.desktop.showcase.pages.swing.controls;

import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.caption;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.column;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.paintIcon;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.resourceIcon;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.row;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.runAction;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.section;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.swatch;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.MediaTracker;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.ButtonGroup;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.metal.MetalProgressBarUI;

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
 * Buttons and range controls : {@code JButton} (icons for every model state from classpath resources, HTML text,
 * mnemonics, default button, text positions), {@code JToggleButton}, {@code JCheckBox}, {@code JRadioButton} with
 * {@code ButtonGroup}, {@code Action}s, then {@code JSlider} (ticks, labels, inverted, vertical, filled),
 * {@code JProgressBar} (determinate, string painted, vertical, indeterminate with a frozen frame) and
 * {@code JScrollBar}, with a shared {@code BoundedRangeModel}.
 * <p>
 * Native paths exercised on purpose : UI delegates created by reflection ({@code createUI}), look and feel action maps
 * loaded by reflection ({@code LazyActionMap} : button, slider, scroll bar), input maps parsed with
 * {@code KeyStroke.getKeyStroke(String)} (reflection on the {@code KeyEvent} fields), {@code ImageIcon(URL)} of PNG and GIF
 * resources ({@code resource:} URLs in a native executable), HTML text ({@code default.css}, the HTML DTD), the Ocean
 * disabled icon filter.
 */
@Singleton
public class ButtonsPage implements FeaturePage {

    private static final int ACCENT = 0x1565C0;

    // per build state (pages are singletons showing one content at a time)
    private ChecksView results;
    private final Readiness readiness = new Readiness();
    private JButton defaultButton;
    private JRootPane nestedRoot;
    private JSlider invertedSlider;
    private JSlider sharedSlider;
    private JProgressBar sharedProgress;
    private JProgressBar indeterminate;
    private final List<JComponent> gallery = new ArrayList<>();

    @Override
    public String id() {
        return "swing-buttons";
    }

    @Override
    public String title() {
        return "Buttons and range controls";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() {
        gallery.clear();
        results = ChecksView.table("Checks", List.of(Check.info("state", "pending")));
        JPanel content = column(10,
                Ui.text("JButton, JToggleButton, JCheckBox and JRadioButton in every model state (the rollover, armed and "
                        + "pressed states are set on the models : snapshot mode drops real mouse events), Actions, then "
                        + "JSlider, JProgressBar and JScrollBar. Icons are PNG and GIF classpath resources loaded with "
                        + "ImageIcon(URL).", 1000),
                section("JButton", buttons()),
                section("Button model states (icon per state : setRolloverIcon, setPressedIcon, setSelectedIcon...)",
                        states()),
                section("JToggleButton, JCheckBox, JRadioButton, ButtonGroup, Action with SELECTED_KEY", toggles()),
                section("JSlider", sliders()),
                section("JProgressBar and JScrollBar (a BoundedRangeModel shared by a slider and a progress bar)",
                        progress()),
                results);
        content.setOpaque(true);
        content.setBackground(java.awt.Color.WHITE);
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
        if (indeterminate != null) {
            // stops the animation timer of the progress bar UI
            indeterminate.setIndeterminate(false);
        }
        results = null;
        defaultButton = null;
        nestedRoot = null;
        invertedSlider = null;
        sharedSlider = null;
        sharedProgress = null;
        indeterminate = null;
        gallery.clear();
    }

    // ----------------------------------------------------------------------------------------------------- gallery

    private static ImageIcon star(String name) {
        return resourceIcon("icons/" + name, name);
    }

    private JComponent buttons() {
        JButton plain = new JButton("Plain");

        JButton iconText = new JButton("Icon and text", star("star-16.png"));
        iconText.setRolloverEnabled(true);
        iconText.setRolloverIcon(star("star-rollover-16.png"));
        iconText.setPressedIcon(star("star-pressed-16.png"));

        JButton html = new JButton("<html><b>HTML</b> <font color=#C62828>button</font><br><i>second line</i></html>");

        JButton disabled = new JButton("Disabled", star("star-16.png"));
        disabled.setEnabled(false);

        JButton mnemonic = new JButton(saveAsAction());

        JButton iconOnly = new JButton(resourceIcon("icons/play-16.gif", "play"));
        iconOnly.setMargin(new Insets(4, 4, 4, 4));

        JButton flat = new JButton("Flat", swatch(ACCENT, 14, 14));
        flat.setContentAreaFilled(false);
        flat.setBorderPainted(false);

        JButton above = new JButton("Text below", star("star-32.png"));
        above.setVerticalTextPosition(SwingConstants.BOTTOM);
        above.setHorizontalTextPosition(SwingConstants.CENTER);

        JButton leading = new JButton("Icon trailing", star("star-16.png"));
        leading.setHorizontalTextPosition(SwingConstants.LEADING);
        leading.setIconTextGap(12);

        // a default button lives in a root pane : a nested one, so that the page does not change the main window
        defaultButton = new JButton("Default");
        JButton cancel = new JButton("Cancel");
        nestedRoot = new JRootPane();
        JPanel dialogLike = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        dialogLike.add(defaultButton);
        dialogLike.add(cancel);
        nestedRoot.setContentPane(dialogLike);
        nestedRoot.setDefaultButton(defaultButton);
        nestedRoot.setBorder(BorderFactory.createEtchedBorder());

        track(plain, iconText, html, disabled, mnemonic, iconOnly, flat, above, leading, defaultButton, cancel);
        return column(8,
                row(10, plain, iconText, html, disabled, mnemonic, iconOnly, flat),
                row(10, above, leading, labeled("nested JRootPane.setDefaultButton", nestedRoot)));
    }

    private static JComponent labeled(String text, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.add(caption(text), BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JComponent states() {
        JPanel grid = new JPanel(new GridLayout(1, 7, 8, 0));
        grid.setOpaque(false);
        grid.add(labeled("normal", stateButton(new JButton("Normal"))));
        JButton rollover = stateButton(new JButton("Rollover"));
        rollover.getModel().setRollover(true);
        grid.add(labeled("model.setRollover(true)", rollover));
        JButton pressed = stateButton(new JButton("Pressed"));
        pressed.getModel().setArmed(true);
        pressed.getModel().setPressed(true);
        grid.add(labeled("armed + pressed", pressed));
        JToggleButton selected = stateButton(new JToggleButton("Selected"));
        selected.setSelected(true);
        grid.add(labeled("JToggleButton selected", selected));
        JToggleButton rolloverSelected = stateButton(new JToggleButton("Roll+sel"));
        rolloverSelected.setSelected(true);
        rolloverSelected.getModel().setRollover(true);
        grid.add(labeled("rollover + selected", rolloverSelected));
        JButton disabled = stateButton(new JButton("Disabled"));
        disabled.setEnabled(false);
        grid.add(labeled("disabled (Ocean filter)", disabled));
        JToggleButton disabledSelected = stateButton(new JToggleButton("Dis+sel"));
        disabledSelected.setSelected(true);
        disabledSelected.setEnabled(false);
        grid.add(labeled("disabled + selected", disabledSelected));
        return grid;
    }

    private <B extends AbstractButton> B stateButton(B button) {
        button.setIcon(star("star-16.png"));
        button.setRolloverEnabled(true);
        button.setRolloverIcon(star("star-rollover-16.png"));
        button.setPressedIcon(star("star-pressed-16.png"));
        button.setSelectedIcon(star("star-selected-16.png"));
        button.setRolloverSelectedIcon(star("star-selected-16.png"));
        track(button);
        return button;
    }

    private JComponent toggles() {
        ButtonGroup alignment = new ButtonGroup();
        JToggleButton left = new JToggleButton("Left");
        JToggleButton center = new JToggleButton("Center");
        JToggleButton right = new JToggleButton("Right");
        for (JToggleButton b : List.of(left, center, right)) {
            alignment.add(b);
        }
        center.setSelected(true);

        JCheckBox unchecked = new JCheckBox("Unchecked");
        JCheckBox checked = new JCheckBox("Checked", true);
        JCheckBox disabledChecked = new JCheckBox("Disabled checked", true);
        disabledChecked.setEnabled(false);
        JCheckBox custom = new JCheckBox("Custom icons", true);
        custom.setIcon(swatch(0xB0BEC5, 14, 14));
        custom.setSelectedIcon(swatch(0x2E7D32, 14, 14));
        JCheckBox htmlBox = new JCheckBox("<html>HTML <u>check</u> <b>box</b></html>", true);
        JCheckBox flat = new JCheckBox("Flat border", true);
        flat.setBorderPaintedFlat(true);
        flat.setBorderPainted(true);

        ButtonGroup radios = new ButtonGroup();
        JRadioButton small = new JRadioButton("Small");
        JRadioButton medium = new JRadioButton("Medium", true);
        JRadioButton large = new JRadioButton("Large");
        JRadioButton disabledRadio = new JRadioButton("Disabled");
        disabledRadio.setEnabled(false);
        for (JRadioButton b : List.of(small, medium, large, disabledRadio)) {
            radios.add(b);
        }

        // two controls sharing one Action : their selection follows Action.SELECTED_KEY
        Action wrap = new AbstractAction("Wrap (Action)") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        wrap.putValue(Action.SELECTED_KEY, Boolean.TRUE);
        JCheckBox wrapBox = new JCheckBox(wrap);
        JToggleButton wrapToggle = new JToggleButton(wrap);
        // BasicToggleButtonUI.paint lays the text out with g.getFontMetrics() (not the metrics of the component, which
        // include the text anti-aliasing hints of the look and feel) : at its preferred width, some text gets clipped
        wrapToggle.setPreferredSize(new Dimension(wrapToggle.getPreferredSize().width + 8,
                wrapToggle.getPreferredSize().height));

        track(left, center, right, unchecked, checked, disabledChecked, custom, htmlBox, flat, small, medium, large,
                disabledRadio, wrapBox, wrapToggle);
        return column(6,
                row(8, left, center, right, new JLabel("   "), wrapToggle, wrapBox),
                row(8, unchecked, checked, disabledChecked, custom, htmlBox, flat),
                row(8, small, medium, large, disabledRadio));
    }

    private JComponent sliders() {
        JSlider plain = new JSlider(0, 100, 40);
        plain.setMajorTickSpacing(25);
        plain.setMinorTickSpacing(5);
        plain.setPaintTicks(true);
        plain.setPaintLabels(true);
        plain.setPreferredSize(new Dimension(300, plain.getPreferredSize().height));

        invertedSlider = new JSlider(0, 100, 60);
        invertedSlider.setInverted(true);
        invertedSlider.setMajorTickSpacing(50);
        invertedSlider.setPaintTicks(true);
        Dictionary<Integer, JComponent> labels = new Hashtable<>();
        labels.put(0, new JLabel("Low"));
        labels.put(50, new JLabel("Mid"));
        labels.put(100, new JLabel("High"));
        invertedSlider.setLabelTable(labels);
        invertedSlider.setPaintLabels(true);
        invertedSlider.setPreferredSize(new Dimension(260, invertedSlider.getPreferredSize().height));

        JSlider filled = new JSlider(0, 10, 7);
        filled.putClientProperty("JSlider.isFilled", Boolean.TRUE);
        filled.setMajorTickSpacing(5);
        filled.setMinorTickSpacing(1);
        filled.setSnapToTicks(true);
        filled.setPaintTicks(true);
        filled.setPreferredSize(new Dimension(200, filled.getPreferredSize().height));

        JSlider vertical = new JSlider(SwingConstants.VERTICAL, 0, 10, 3);
        vertical.setMajorTickSpacing(5);
        vertical.setMinorTickSpacing(1);
        vertical.setPaintTicks(true);
        vertical.setPaintLabels(true);
        vertical.setPreferredSize(new Dimension(vertical.getPreferredSize().width, 120));

        JSlider disabled = new JSlider(0, 100, 25);
        disabled.setEnabled(false);
        disabled.setPreferredSize(new Dimension(120, disabled.getPreferredSize().height));

        track(plain, invertedSlider, filled, vertical, disabled);
        return row(18,
                column(2, caption("ticks + labels (createStandardLabels)"), plain),
                column(2, caption("inverted, custom label table"), invertedSlider),
                column(2, caption("JSlider.isFilled, snapToTicks"), filled, caption("disabled"), disabled),
                column(2, caption("vertical"), vertical));
    }

    private JComponent progress() {
        BoundedRangeModel model = new DefaultBoundedRangeModel(35, 0, 0, 100);
        sharedSlider = new JSlider(model);
        sharedSlider.setPreferredSize(new Dimension(180, sharedSlider.getPreferredSize().height));
        sharedProgress = new JProgressBar(model);
        sharedProgress.setStringPainted(true);

        JProgressBar determinate = new JProgressBar(0, 200);
        determinate.setValue(130);
        determinate.setStringPainted(true);

        JProgressBar custom = new JProgressBar(0, 5);
        custom.setValue(3);
        custom.setString("Step 3 of 5");
        custom.setStringPainted(true);

        JProgressBar plain = new JProgressBar(0, 100);
        plain.setValue(80);
        plain.setBorderPainted(false);

        JProgressBar vertical = new JProgressBar(SwingConstants.VERTICAL, 0, 100);
        vertical.setValue(60);
        vertical.setStringPainted(true);
        vertical.setPreferredSize(new Dimension(vertical.getPreferredSize().width, 110));

        // indeterminate : animated by a timer of the UI delegate, frozen at a fixed frame (deterministic snapshots)
        indeterminate = new JProgressBar();
        indeterminate.setUI(new FrozenProgressBarUI());
        indeterminate.setIndeterminate(true);

        JScrollBar horizontal = new JScrollBar(JScrollBar.HORIZONTAL, 30, 20, 0, 100);
        horizontal.setPreferredSize(new Dimension(220, horizontal.getPreferredSize().height));
        JScrollBar disabledBar = new JScrollBar(JScrollBar.HORIZONTAL, 10, 50, 0, 100);
        disabledBar.setEnabled(false);
        disabledBar.setPreferredSize(new Dimension(220, disabledBar.getPreferredSize().height));
        JScrollBar verticalBar = new JScrollBar(JScrollBar.VERTICAL, 60, 10, 0, 100);
        verticalBar.setPreferredSize(new Dimension(verticalBar.getPreferredSize().width, 110));

        track(sharedSlider, sharedProgress, determinate, custom, plain, vertical, indeterminate, horizontal,
                disabledBar, verticalBar);
        JPanel bars = new JPanel(new GridLayout(0, 2, 12, 4));
        bars.setOpaque(false);
        bars.add(caption("shared model : slider"));
        bars.add(caption("shared model : progress bar"));
        bars.add(sharedSlider);
        bars.add(sharedProgress);
        bars.add(caption("130 of 200, string painted"));
        bars.add(caption("custom string"));
        bars.add(determinate);
        bars.add(custom);
        bars.add(caption("no border, no string"));
        bars.add(caption("indeterminate (frozen frame)"));
        bars.add(plain);
        bars.add(indeterminate);
        bars.add(caption("JScrollBar"));
        bars.add(caption("JScrollBar disabled"));
        bars.add(horizontal);
        bars.add(disabledBar);
        return row(24, bars, column(2, caption("vertical"), row(12, vertical, verticalBar)));
    }

    private void track(JComponent... components) {
        gallery.addAll(List.of(components));
    }

    private static Action saveAsAction() {
        Action action = new AbstractAction("Save As") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        action.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_A);
        action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, 5);
        action.putValue(Action.SHORT_DESCRIPTION, "Save under another name");
        action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control shift S"));
        action.putValue(Action.ACTION_COMMAND_KEY, "save-as");
        return action;
    }

    /**
     * An indeterminate progress bar UI whose animation stays at a fixed frame.
     */
    static final class FrozenProgressBarUI extends MetalProgressBarUI {

        @Override
        protected void incrementAnimationIndex() {
            // frozen
        }

        @Override
        protected void setAnimationIndex(int newValue) {
            super.setAnimationIndex(Math.max(0, getFrameCount() / 4));
        }

        int frames() {
            return getFrameCount();
        }
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private List<Check> checks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("UI delegates (UIDefaults.getUI : createUI by reflection)",
                "MetalButtonUI MetalCheckBoxUI MetalProgressBarUI MetalRadioButtonUI MetalScrollBarUI MetalSliderUI "
                        + "MetalToggleButtonUI",
                () -> {
                    TreeSet<String> names = new TreeSet<>();
                    for (JComponent c : gallery) {
                        if (!(c.getUI() instanceof FrozenProgressBarUI)) {
                            names.add(c.getUI().getClass().getSimpleName());
                        }
                    }
                    return String.join(" ", names);
                }));
        checks.addAll(iconChecks());
        checks.addAll(actionChecks());
        checks.addAll(toggleChecks());
        checks.addAll(rangeChecks());
        return checks;
    }

    private static List<Check> iconChecks() {
        List<Check> checks = new ArrayList<>();
        for (String name : List.of("star-16.png", "star-32.png", "play-16.gif")) {
            checks.add(Checks.expect("ImageIcon(URL) " + name + " : size, load status",
                    (name.contains("32") ? "32x32" : "16x16") + ", COMPLETE", () -> {
                        ImageIcon icon = resourceIcon("icons/" + name, name);
                        return icon.getIconWidth() + "x" + icon.getIconHeight() + ", "
                                + (icon.getImageLoadStatus() == MediaTracker.COMPLETE ? "COMPLETE"
                                        : icon.getImageLoadStatus());
                    }));
        }
        for (String[] star : ControlsAssets.STARS) {
            checks.add(Checks.expect("PNG " + star[0] + " : center pixel", "#FF" + star[1],
                    () -> Checks.argb(paintIcon(star(star[0])).getRGB(8, 8))));
        }
        checks.add(Checks.expect("GIF play-16.gif : pixels inside / transparent corner",
                Checks.argb(0xFF000000 | ControlsAssets.PLAY) + " / #00000000", () -> {
                    BufferedImage image = paintIcon(resourceIcon("icons/play-16.gif", "play"));
                    return Checks.argb(image.getRGB(6, 8)) + " / " + Checks.argb(image.getRGB(15, 0));
                }));
        checks.add(Checks.expect("disabled icon (Ocean filter of the star) : gray pixel", "gray", () -> {
            JButton button = new JButton(star("star-16.png"));
            Icon disabled = button.getDisabledIcon();
            int rgb = paintIcon(disabled).getRGB(8, 8);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return r == g && g == b ? "gray" : Checks.argb(rgb);
        }));
        return checks;
    }

    private List<Check> actionChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("KeyStroke.getKeyStroke(\"control shift S\")", "shift ctrl pressed S",
                () -> KeyStroke.getKeyStroke("control shift S").toString()));
        checks.add(Checks.expect("KeyStroke.getKeyStroke : typed a, released ENTER, alt F4", "typed a | released ENTER | alt pressed F4",
                () -> KeyStroke.getKeyStroke("typed a") + " | " + KeyStroke.getKeyStroke("released ENTER") + " | "
                        + KeyStroke.getKeyStroke("alt F4")));
        checks.add(Checks.expect("KeyEvent.getKeyText(VK_A / VK_F4 / VK_ENTER) (bundle sun.awt.resources.awt)",
                "A / F4 / Enter", () -> KeyEvent.getKeyText(KeyEvent.VK_A) + " / " + KeyEvent.getKeyText(KeyEvent.VK_F4)
                        + " / " + KeyEvent.getKeyText(KeyEvent.VK_ENTER)));
        checks.add(Checks.expect("Action -> JButton properties",
                "text=Save As, mnemonic=65, displayedMnemonicIndex=5, tooltip=Save under another name, command=save-as",
                () -> {
                    JButton b = new JButton(saveAsAction());
                    return "text=" + b.getText() + ", mnemonic=" + b.getMnemonic() + ", displayedMnemonicIndex="
                            + b.getDisplayedMnemonicIndex() + ", tooltip=" + b.getToolTipText() + ", command="
                            + b.getActionCommand();
                }));
        checks.add(Checks.expect("Action.setEnabled(false/true) -> button enabled", "false / true", () -> {
            Action action = saveAsAction();
            JButton b = new JButton(action);
            action.setEnabled(false);
            String first = String.valueOf(b.isEnabled());
            action.setEnabled(true);
            return first + " / " + b.isEnabled();
        }));
        checks.add(Checks.expect("Action LARGE_ICON_KEY preferred over SMALL_ICON by a button", "32x32", () -> {
            Action action = saveAsAction();
            action.putValue(Action.SMALL_ICON, star("star-16.png"));
            action.putValue(Action.LARGE_ICON_KEY, star("star-32.png"));
            Icon icon = new JButton(action).getIcon();
            return icon.getIconWidth() + "x" + icon.getIconHeight();
        }));
        checks.add(Checks.expect("doClick(0) : ActionEvent command, modifiers", "save-as, 0 | count 1", () -> {
            EventLog log = new EventLog();
            JButton b = new JButton(saveAsAction());
            b.addActionListener(e -> log.add(e.getActionCommand() + ", " + e.getModifiers()));
            b.doClick(0);
            return log + " | count " + log.size();
        }));
        checks.add(Checks.expect("ActionMap pressed + released (BasicButtonListener.loadActionMap)", "fired 1", () -> {
            EventLog log = new EventLog();
            JButton b = new JButton("probe");
            b.addActionListener(e -> log.add("fired"));
            runAction(b, "pressed");
            runAction(b, "released");
            return "fired " + log.size();
        }));
        checks.add(Checks.expect("InputMap SPACE / released SPACE (LazyInputMap)", "pressed / released", () -> {
            JButton b = new JButton("probe");
            return b.getInputMap().get(KeyStroke.getKeyStroke("SPACE")) + " / "
                    + b.getInputMap().get(KeyStroke.getKeyStroke("released SPACE"));
        }));
        checks.add(Checks.expect("HTML text view (BasicHTML.propertyKey) : HTML / plain button", "true / false", () -> {
            JButton htmlButton = new JButton("<html><b>bold</b></html>");
            return (htmlButton.getClientProperty(BasicHTML.propertyKey) != null) + " / "
                    + (new JButton("plain").getClientProperty(BasicHTML.propertyKey) != null);
        }));
        checks.add(Checks.expect("nested JRootPane default button", "Default, isDefaultButton=true",
                () -> nestedRoot.getDefaultButton().getText() + ", isDefaultButton=" + defaultButton.isDefaultButton()));
        return checks;
    }

    private static List<Check> toggleChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("ButtonGroup : doClick(Right) item events, selection", "Center DESELECTED | Right SELECTED, right",
                () -> {
                    EventLog log = new EventLog();
                    ButtonGroup group = new ButtonGroup();
                    List<JToggleButton> buttons = new ArrayList<>();
                    for (String name : List.of("Left", "Center", "Right")) {
                        JToggleButton b = new JToggleButton(name);
                        b.setActionCommand(name.toLowerCase(java.util.Locale.ROOT));
                        group.add(b);
                        buttons.add(b);
                    }
                    buttons.get(1).setSelected(true);
                    for (JToggleButton b : buttons) {
                        b.addItemListener(e -> log.add(b.getText() + " "
                                + (e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED")));
                    }
                    buttons.get(2).doClick(0);
                    return log + ", " + group.getSelection().getActionCommand();
                }));
        checks.add(Checks.expect("ButtonGroup.clearSelection", "null, 0 selected", () -> {
            ButtonGroup group = new ButtonGroup();
            JRadioButton a = new JRadioButton("a", true);
            JRadioButton b = new JRadioButton("b");
            group.add(a);
            group.add(b);
            group.clearSelection();
            return group.getSelection() + ", " + ((a.isSelected() ? 1 : 0) + (b.isSelected() ? 1 : 0)) + " selected";
        }));
        checks.add(Checks.expect("JCheckBox doClick x2 : selected, item events", "false -> true -> false, SELECTED DESELECTED",
                () -> {
                    EventLog log = new EventLog();
                    JCheckBox box = new JCheckBox("probe");
                    box.addItemListener(e -> log.add(e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED"));
                    String before = String.valueOf(box.isSelected());
                    box.doClick(0);
                    String middle = String.valueOf(box.isSelected());
                    box.doClick(0);
                    return before + " -> " + middle + " -> " + box.isSelected() + ", " + String.join(" ", log.events());
                }));
        checks.add(Checks.expect("Action SELECTED_KEY shared by a JCheckBox and a JToggleButton", "true true -> false false",
                () -> {
                    Action action = new AbstractAction("wrap") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                        }
                    };
                    action.putValue(Action.SELECTED_KEY, Boolean.TRUE);
                    JCheckBox box = new JCheckBox(action);
                    JToggleButton toggle = new JToggleButton(action);
                    String before = box.isSelected() + " " + toggle.isSelected();
                    toggle.doClick(0);
                    return before + " -> " + box.isSelected() + " " + toggle.isSelected();
                }));
        checks.add(Checks.expect("JRadioButton mnemonic from setMnemonic('M') / displayed index", "77 / 0", () -> {
            JRadioButton radio = new JRadioButton("Medium");
            radio.setMnemonic('M');
            return radio.getMnemonic() + " / " + radio.getDisplayedMnemonicIndex();
        }));
        return checks;
    }

    private List<Check> rangeChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("JSlider.createStandardLabels(25) keys", "0 25 50 75 100", () -> {
            JSlider slider = new JSlider(0, 100, 40);
            TreeSet<Integer> keys = new TreeSet<>();
            for (var e = slider.createStandardLabels(25).keys(); e.hasMoreElements();) {
                keys.add((Integer) e.nextElement());
            }
            return String.join(" ", keys.stream().map(String::valueOf).toList());
        }));
        checks.add(Checks.expect("JSlider actions (BasicSliderUI.loadActionMap) : unit, block, max, min", "41 51 100 0",
                () -> {
                    JSlider slider = new JSlider(0, 100, 40);
                    List<String> values = new ArrayList<>();
                    for (String name : List.of("positiveUnitIncrement", "positiveBlockIncrement", "maxScroll",
                            "minScroll")) {
                        runAction(slider, name);
                        values.add(String.valueOf(slider.getValue()));
                    }
                    return String.join(" ", values);
                }));
        checks.add(Checks.expect("inverted JSlider : positiveUnitIncrement from 60", "59", () -> {
            JSlider slider = new JSlider(0, 100, 60);
            slider.setInverted(true);
            runAction(slider, "positiveUnitIncrement");
            return slider.getValue();
        }));
        checks.add(Checks.expect("snapToTicks JSlider (minor 1, major 5) : block increment from 7", "8", () -> {
            JSlider slider = new JSlider(0, 10, 7);
            slider.setMajorTickSpacing(5);
            slider.setMinorTickSpacing(1);
            slider.setSnapToTicks(true);
            runAction(slider, "positiveBlockIncrement");
            return slider.getValue();
        }));
        checks.add(Checks.expect("inverted JSlider on screen : valueForXPosition(left edge / right edge)", "100 / 0",
                () -> {
                    BasicSliderUI ui = (BasicSliderUI) invertedSlider.getUI();
                    return ui.valueForXPosition(0) + " / " + ui.valueForXPosition(invertedSlider.getWidth());
                }));
        checks.add(Checks.expect("custom label table size", 3, () -> invertedSlider.getLabelTable().size()));
        checks.add(Checks.expect("shared BoundedRangeModel : slider.setValue(70) -> progress value, string, events",
                "70, 70%, 1 change event", () -> {
                    EventLog log = new EventLog();
                    sharedProgress.addChangeListener(e -> log.add("change"));
                    sharedSlider.setValue(70);
                    return sharedProgress.getValue() + ", " + sharedProgress.getString() + ", " + log.size()
                            + " change event";
                }));
        checks.add(Checks.expect("JProgressBar 130/200 : percent complete, string", "0.650, 65%", () -> {
            JProgressBar bar = new JProgressBar(0, 200);
            bar.setValue(130);
            return Checks.num(bar.getPercentComplete()) + ", " + bar.getString();
        }));
        checks.add(Checks.expect("JProgressBar indeterminate (frozen UI) : indeterminate, frame count > 0", "true, true",
                () -> indeterminate.isIndeterminate() + ", "
                        + (((FrozenProgressBarUI) indeterminate.getUI()).frames() > 0)));
        checks.add(Checks.expect("JScrollBar : model, setValue(95) clamped to max - extent", "0..100 extent 20, 80", () -> {
            JScrollBar bar = new JScrollBar(JScrollBar.HORIZONTAL, 30, 20, 0, 100);
            bar.setValue(95);
            return bar.getMinimum() + ".." + bar.getMaximum() + " extent " + bar.getVisibleAmount() + ", "
                    + bar.getValue();
        }));
        checks.add(Checks.expect("JScrollBar actions (BasicScrollBarUI.loadActionMap) : unit, block, min, max",
                "31 41 0 80 | adjustment events 4", () -> {
                    JScrollBar bar = new JScrollBar(JScrollBar.HORIZONTAL, 30, 20, 0, 100);
                    bar.setUnitIncrement(1);
                    bar.setBlockIncrement(10);
                    EventLog log = new EventLog();
                    bar.addAdjustmentListener(e -> log.add(String.valueOf(e.getAdjustmentType())));
                    List<String> values = new ArrayList<>();
                    for (String name : List.of("positiveUnitIncrement", "positiveBlockIncrement", "minScroll",
                            "maxScroll")) {
                        runAction(bar, name);
                        values.add(String.valueOf(bar.getValue()));
                    }
                    return String.join(" ", values) + " | adjustment events " + log.size();
                }));
        return checks;
    }
}
