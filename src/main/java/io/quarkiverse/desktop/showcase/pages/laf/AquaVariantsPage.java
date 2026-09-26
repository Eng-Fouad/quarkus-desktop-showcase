package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.Border;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The client properties of the Aqua look and feel (macOS only) : the {@code JButton.buttonType} styles, the segmented
 * buttons ({@code JButton.segmentPosition}), the size variants ({@code JComponent.sizeVariant} mini, small, regular,
 * large), the search field ({@code JTextField.variant}), the circular progress bar, pop down and square combo boxes and
 * the tree line styles. Aqua creates the button borders of the styles by name, and derives the border of a size variant
 * with the public copy constructor of its class ({@code AquaBorder.deriveBorderForSize}, reflection) : a missing
 * registration leaves the component without border (the "null borders" check). On other operating systems the page only
 * states that it is not available.
 */
@Singleton
public class AquaVariantsPage extends AbstractLafPage {

    // com/apple/laf/AquaButtonExtendedTypes.java (TypeSpecifier names)
    static final List<String> BUTTON_TYPES = List.of("toolbar", "icon", "text", "toggle", "combobox",
            "comboboxInternal", "comboboxEndCap", "square", "gradient", "bevel", "textured", "roundRect", "recessed",
            "well", "help", "round", "texturedRound", "disclosure", "disclosureTriangle", "scrollColumnSizer");
    // JButton.buttonType + JButton.segmentPosition : the border is looked up as type + "-" + position
    static final List<String> SEGMENTED_TYPES = List.of("segmented", "segmentedRoundRect", "segmentedTexturedRounded",
            "segmentedTextured", "segmentedCapsule", "segmentedGradient");
    static final List<String> SEGMENT_POSITIONS = List.of("first", "middle", "last", "only");
    // com/apple/laf/AquaUtilControlSize.java
    static final List<String> SIZE_VARIANTS = List.of("mini", "small", "regular", "large");

    private final List<JComponent> styled = new ArrayList<>();

    @Override
    public String id() {
        return "laf-aqua-client-properties";
    }

    @Override
    public String title() {
        return "Aqua variants (macOS)";
    }

    @Override
    public int order() {
        return 66;
    }

    @Override
    protected Component buildPage() throws Exception {
        if (!Platforms.isMac()) {
            return AquaPage.notAvailable("Aqua client properties",
                    "The client properties of the Aqua look and feel (button types, segmented buttons, size variants) "
                            + "only apply on macOS.",
                    List.of());
        }
        UIManager.setLookAndFeel(AquaPage.AQUA);
        styled.clear();

        JPanel types = flow();
        Map<String, String> borders = new TreeMap<>();
        for (String type : BUTTON_TYPES) {
            JButton button = new JButton(type.equals("icon") || type.startsWith("disclosure") ? "" : type);
            button.putClientProperty("JButton.buttonType", type);
            types.add(styled(button));
            borders.put("buttonType " + type, borderOf(button));
        }
        JPanel segmented = flow();
        for (String type : SEGMENTED_TYPES) {
            for (String position : SEGMENT_POSITIONS) {
                JToggleButton button = new JToggleButton(position);
                button.putClientProperty("JButton.buttonType", type);
                button.putClientProperty("JButton.segmentPosition", position);
                button.setSelected(position.equals("middle"));
                segmented.add(styled(button));
                borders.put(type + " " + position, borderOf(button));
            }
        }

        JPanel variants = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        variants.setOpaque(false);
        List<Check> sizes = new ArrayList<>();
        for (String variant : SIZE_VARIANTS) {
            JPanel row = flow();
            row.add(new JLabel(variant));
            List<JComponent> components = List.of(new JButton("Button"), new JToggleButton("Toggle"),
                    new JCheckBox("Check", true), new JRadioButton("Radio", true),
                    new JComboBox<>(new String[] { "Combo" }), new JTextField("Text", 6), new JSpinner(),
                    new JSlider(0, 100, 40), progress(40), new JScrollBar(JScrollBar.HORIZONTAL, 20, 10, 0, 100),
                    tabs());
            for (JComponent c : components) {
                c.putClientProperty("JComponent.sizeVariant", variant);
                if (c instanceof JScrollBar || c instanceof JSlider) {
                    c.setPreferredSize(new Dimension(80, c.getPreferredSize().height));
                }
                row.add(styled(c));
                if (c instanceof AbstractButton) {
                    borders.put(variant + " " + c.getClass().getSimpleName(), borderOf(c));
                }
            }
            variants.add(row);
            JButton probe = (JButton) components.getFirst();
            sizes.add(Checks.info(variant + " : JButton preferred size, font size", () -> probe.getPreferredSize().width
                    + "x" + probe.getPreferredSize().height + ", " + probe.getFont().getSize2D()));
        }

        JPanel others = flow();
        JTextField search = new JTextField("", 12);
        search.putClientProperty("JTextField.variant", "search");
        search.putClientProperty("JTextField.Search.Prompt", "Search");
        JProgressBar circular = progress(60);
        circular.putClientProperty("JProgressBar.style", "circular");
        JComboBox<String> popDown = new JComboBox<>(new String[] { "Pop down" });
        popDown.putClientProperty("JComboBox.isPopDown", Boolean.TRUE);
        JComboBox<String> square = new JComboBox<>(new String[] { "Square" });
        square.putClientProperty("JComboBox.isSquare", Boolean.TRUE);
        others.add(styled(search));
        others.add(styled(circular));
        others.add(styled(popDown));
        others.add(styled(square));
        for (String style : List.of("Angled", "Horizontal", "None")) {
            JTree tree = new JTree();
            tree.putClientProperty("JTree.lineStyle", style);
            tree.setVisibleRowCount(4);
            others.add(styled(tree));
        }
        borders.put("search field", borderOf(search));

        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("null borders (AquaBorder copy constructors, button type borders)", 0L,
                () -> styled.stream().filter(c -> c instanceof AbstractButton || c instanceof JTextField)
                        .filter(c -> c.getBorder() == null).count()));
        checks.add(Checks.info("search field UI", () -> LafSupport.simple(search.getUI().getClass().getName())));
        checks.add(Checks.info("circular progress bar UI", () -> LafSupport.simple(circular.getUI().getClass().getName())));
        checks.addAll(sizes);
        List<Check> borderChecks = new ArrayList<>();
        borders.forEach((name, border) -> borderChecks.add(Check.info(name, border)));

        return LafSupport.page(Ui.heading("Aqua client properties"),
                Ui.text("Button types, segmented buttons (selected : middle), size variants (mini, small, regular, "
                        + "large) of the controls, the search field, the circular progress bar, pop down and square "
                        + "combo boxes, and the tree line styles.", 1000),
                Ui.title("JButton.buttonType"), types, Ui.title("Segmented buttons"), segmented,
                Ui.title("JComponent.sizeVariant"), variants, Ui.title("Other client properties"), others,
                LafSupport.twoColumns("Checks", checks, 330),
                LafSupport.twoColumns("Border classes", borderChecks, 230));
    }

    @Override
    public void dispose(Component content) {
        styled.clear();
        super.dispose(content);
    }

    private <C extends JComponent> C styled(C component) {
        styled.add(component);
        return component;
    }

    private static String borderOf(JComponent component) {
        Border border = component.getBorder();
        return border == null ? "null" : LafSupport.simple(border.getClass().getName());
    }

    private static JPanel flow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setOpaque(false);
        return panel;
    }

    private static JProgressBar progress(int value) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(value);
        return bar;
    }

    private static JTabbedPane tabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("One", new JLabel("1"));
        tabs.addTab("Two", new JLabel("2"));
        tabs.setPreferredSize(new Dimension(120, 60));
        return tabs;
    }
}
