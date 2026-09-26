package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.Painter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.plaf.nimbus.State;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Nimbus : the live gallery (painters instantiated by name through reflection, derived colors), a variant with the
 * {@code nimbusBase}, {@code nimbusBlueGrey} and {@code control} base colors overridden before the look and feel is
 * installed, the {@code JComponent.sizeVariant} client property (mini, small, regular, large), and per component
 * {@code Nimbus.Overrides} : content margins, custom {@link Painter}s, {@code Nimbus.Overrides.InheritDefaults} and a
 * custom {@link State}.
 * <p>
 * Every painter of the defaults table is resolved (56 painter classes, {@code NimbusDefaults.LazyPainter} :
 * {@code Class.forName} + constructor {@code (PaintContext, int)}).
 */
@Singleton
public class NimbusPage extends AbstractLafPage {

    private static final String NIMBUS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

    private static final int TEAL = 0xFF00897B;
    private static final int RED = 0xFFE53935;
    private static final int YELLOW = 0xFFFDD835;
    private static final int GREEN = 0xFF43A047;
    private static final int FIELD = 0xFFE3F2FD;

    @Override
    public String id() {
        return "laf-nimbus";
    }

    @Override
    public String title() {
        return "Nimbus";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    protected Component buildPage() throws Exception {
        // variant : base colors overridden (developer defaults) before Nimbus derives its colors
        state.put("nimbusBase", new ColorUIResource(0x6A1B9A));
        state.put("nimbusBlueGrey", new ColorUIResource(0x8D6E63));
        state.put("control", new ColorUIResource(0xF3E5F5));
        UIManager.setLookAndFeel(new NimbusLookAndFeel());
        List<Check> colorChecks = new ArrayList<>();
        colorChecks.add(Checks.expect("nimbusBase override : nimbusBase", Checks.argb(0xFF6A1B9A),
                () -> LafSupport.color(UIManager.getColor("nimbusBase"))));
        // nimbusSelectionBackground and nimbusFocus are base colors of their own : not derived from nimbusBase
        colorChecks.add(Checks.expect("nimbusBase override : nimbusSelectionBackground", Checks.argb(0xFF39698A),
                () -> LafSupport.color(UIManager.getColor("nimbusSelectionBackground"))));
        colorChecks.add(Checks.expect("nimbusBase override : nimbusFocus", Checks.argb(0xFF73A4D1),
                () -> LafSupport.color(UIManager.getColor("nimbusFocus"))));
        LafGallery purple = LafGallery.build();
        BufferedImage purpleImage = LafSupport.renderStaged(purple.panel());
        extras.put("nimbus-base", purpleImage);
        state.put("nimbusBase", null);
        state.put("nimbusBlueGrey", null);
        state.put("control", null);

        // live : Nimbus with its own colors, installed by class name (reflective instantiation)
        UIManager.setLookAndFeel(NIMBUS);
        gallery = LafGallery.build();
        colorChecks.addAll(0, derivedColors());

        List<Check> lafChecks = LafSupport.lookAndFeelChecks(NIMBUS, false, false);
        lafChecks.add(Checks.info("defaultFont", () -> LafSupport.font(UIManager.getFont("defaultFont"))));

        JComponent sizes = sizeVariants();
        List<Check> sizeChecks = sizeChecks();
        JComponent overrides = overrides();
        List<Check> overrideChecks = overrideChecks();

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("Nimbus look and feel"));
        content.add(Ui.text("Synth based look and feel painted by vector painters (instantiated by class name) with "
                + "colors derived from a few base colors. Below : the live gallery, the size variants, per component "
                + "Nimbus.Overrides, and the gallery with the base colors overridden.", 1000));
        content.add(galleryView());
        content.add(Ui.title("JComponent.sizeVariant : mini, small, regular, large"));
        content.add(sizes);
        content.add(Ui.title("Nimbus.Overrides : margins, painters, InheritDefaults=false, custom State"));
        content.add(overrides);
        content.add(Ui.title("nimbusBase, nimbusBlueGrey and control overridden"));
        Map<String, BufferedImage> variants = new LinkedHashMap<>();
        variants.put("nimbusBase #6A1B9A, nimbusBlueGrey #8D6E63, control #F3E5F5", purpleImage);
        content.add(LafSupport.thumbnails(variants, 0.6, 1));
        content.add(LafSupport.twoColumns("Colors", colorChecks, 230));
        content.add(LafSupport.twoColumns("Painters", painterChecks(), 230));
        content.add(LafSupport.twoColumns("Size variants and overrides", concat(sizeChecks, overrideChecks), 260));
        content.addAll(standardSections(gallery, lafChecks,
                Map.of("FileChooser.lookInLabelText", "Look In:", "FileChooser.saveInLabelText", "Save In:"),
                List.of("FileChooser.fileNameLabelText", "FileChooser.upFolderToolTipText"), true));
        return LafSupport.page(content.toArray(Component[]::new));
    }

    /**
     * Base and derived colors of Nimbus : fixed values (the same on every operating system), derived colors computed
     * with HSB offsets from the base colors.
     */
    private static List<Check> derivedColors() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("nimbusBase", 0xFF33628C);
        expected.put("nimbusBlueGrey", 0xFFA9B0BE);
        expected.put("control", 0xFFD6D9DF);
        expected.put("nimbusSelectionBackground", 0xFF39698A);
        expected.put("nimbusFocus", 0xFF73A4D1);
        expected.put("nimbusLightBackground", 0xFFFFFFFF);
        expected.put("text", 0xFF000000);
        expected.put("nimbusDisabledText", 0xFF8E8F91);
        expected.put("nimbusSelection", 0xFF39698A);
        expected.put("nimbusBorder", 0xFF9297A1);
        expected.put("nimbusInfoBlue", 0xFF2F5CB4);
        expected.put("nimbusOrange", 0xFFBF6204);
        expected.put("Table.alternateRowColor", 0xFFF2F2F2);
        List<Check> checks = new ArrayList<>();
        expected.forEach((key, argb) -> checks.add(Checks.expect(key, Checks.argb(argb),
                () -> LafSupport.color(UIManager.getColor(key)))));
        return checks;
    }

    /**
     * Resolves every painter of the look and feel defaults (lazy values : class by name + reflective constructor).
     */
    private static List<Check> painterChecks() {
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        TreeSet<String> keys = new TreeSet<>();
        for (Object key : defaults.keySet().toArray()) {
            if (key instanceof String s && s.endsWith("Painter")) {
                keys.add(s);
            }
        }
        int resolved = 0;
        List<String> failed = new ArrayList<>();
        TreeSet<String> classes = new TreeSet<>();
        for (String key : keys) {
            Object value = defaults.get(key);
            if (value instanceof Painter<?>) {
                resolved++;
                classes.add(LafSupport.simple(value.getClass().getName()));
            } else {
                failed.add(key);
            }
        }
        List<Check> checks = new ArrayList<>();
        checks.add(Check.info("painter keys", keys.size()));
        checks.add(Check.of("painters resolved", failed.isEmpty(), resolved + " of " + keys.size()));
        checks.add(Check.of("painters not resolved", failed.isEmpty(), failed.isEmpty() ? "none"
                : failed.size() + " : " + String.join(", ", failed.subList(0, Math.min(5, failed.size())))));
        // 56 lazy painter classes (by name) and the ToolBarSeparatorPainter created directly
        checks.add(Checks.expect("distinct painter classes", 57, classes::size));
        checks.add(Check.info("painter classes hash", Checks.sha256(String.join(",", classes))));
        checks.add(Checks.info("Button[Enabled].backgroundPainter",
                () -> LafSupport.simple(UIManager.get("Button[Enabled].backgroundPainter").getClass().getName())));
        checks.add(Checks.info("ScrollBar:ScrollBarThumb[Enabled].backgroundPainter",
                () -> LafSupport.simple(UIManager.get("ScrollBar:ScrollBarThumb[Enabled].backgroundPainter")
                        .getClass().getName())));
        return checks;
    }

    // --------------------------------------------------------------------------------------------- size variants

    private static final List<String> VARIANTS = java.util.Arrays.asList("mini", "small", null, "large");

    private static JComponent sizeVariants() {
        JPanel rows = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        for (String variant : VARIANTS) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.add(new JLabel(variant == null ? "regular" : variant));
            row.add(new JButton("Button"));
            row.add(new JCheckBox("Check", true));
            row.add(new JRadioButton("Radio", true));
            row.add(new JTextField("Text", 6));
            row.add(new JComboBox<>(new String[] { "Combo" }));
            row.add(new JSpinner(new SpinnerNumberModel(42, 0, 100, 1)));
            JSlider slider = new JSlider(0, 100, 40);
            slider.setPreferredSize(new Dimension(110, slider.getPreferredSize().height));
            row.add(slider);
            JProgressBar progress = new JProgressBar(0, 100);
            progress.setValue(50);
            row.add(progress);
            for (Component c : row.getComponents()) {
                ((JComponent) c).putClientProperty("JComponent.sizeVariant", variant);
            }
            // the size variant is read when the style is installed
            SwingUtilities.updateComponentTreeUI(row);
            rows.add(row);
        }
        rows.setPreferredSize(new Dimension(LafGallery.GALLERY_WIDTH, rows.getPreferredSize().height));
        return rows;
    }

    private static List<Check> sizeChecks() {
        List<String> heights = new ArrayList<>();
        List<String> fonts = new ArrayList<>();
        for (String variant : VARIANTS) {
            JButton button = new JButton("Button");
            button.putClientProperty("JComponent.sizeVariant", variant);
            button.updateUI();
            heights.add(button.getPreferredSize().width + "x" + button.getPreferredSize().height);
            fonts.add(Checks.num(button.getFont().getSize2D(), 2));
        }
        return List.of(Check.info("JButton preferred size (mini, small, regular, large)", String.join(", ", heights)),
                Check.info("JButton font size (mini, small, regular, large)", String.join(", ", fonts)));
    }

    // ------------------------------------------------------------------------------------------------- overrides

    private static JButton overriddenButton() {
        JButton button = new JButton("Overrides");
        UIDefaults overrides = new UIDefaults();
        overrides.put("Button.contentMargins", new InsetsUIResource(10, 24, 10, 24));
        overrides.put("Button[Enabled].backgroundPainter", new FlatPainter(TEAL));
        overrides.put("Button[Enabled].textForeground", new ColorUIResource(0xFFFFFF));
        button.putClientProperty("Nimbus.Overrides", overrides);
        button.putClientProperty("Nimbus.Overrides.InheritDefaults", Boolean.TRUE);
        SwingUtilities.updateComponentTreeUI(button);
        return button;
    }

    private static JButton notInheritingButton() {
        JButton button = new JButton("InheritDefaults false");
        UIDefaults overrides = new UIDefaults();
        overrides.put("Button.contentMargins", new InsetsUIResource(4, 30, 4, 30));
        overrides.put("Button[Enabled].backgroundPainter", new FlatPainter(RED));
        button.putClientProperty("Nimbus.Overrides", overrides);
        button.putClientProperty("Nimbus.Overrides.InheritDefaults", Boolean.FALSE);
        SwingUtilities.updateComponentTreeUI(button);
        return button;
    }

    private static JButton customStateButton() {
        JButton button = new JButton("Custom state");
        UIDefaults overrides = new UIDefaults();
        overrides.put("Button.States", "Enabled,MouseOver,Pressed,Disabled,Focused,Selected,Default,Highlighted");
        overrides.put("Button.Highlighted", new HighlightedState());
        overrides.put("Button[Enabled+Highlighted].backgroundPainter", new FlatPainter(YELLOW));
        button.putClientProperty("Nimbus.Overrides", overrides);
        button.putClientProperty(HighlightedState.PROPERTY, Boolean.TRUE);
        SwingUtilities.updateComponentTreeUI(button);
        return button;
    }

    private static JProgressBar progressBar() {
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(70);
        progress.setStringPainted(true);
        UIDefaults overrides = new UIDefaults();
        overrides.put("ProgressBar[Enabled].foregroundPainter", new FlatPainter(GREEN));
        progress.putClientProperty("Nimbus.Overrides", overrides);
        SwingUtilities.updateComponentTreeUI(progress);
        return progress;
    }

    private static JSlider slider() {
        JSlider slider = new JSlider(0, 100, 30);
        slider.setPreferredSize(new Dimension(160, slider.getPreferredSize().height));
        UIDefaults overrides = new UIDefaults();
        overrides.put("Slider:SliderThumb[Enabled].backgroundPainter", new DiamondPainter(TEAL));
        slider.putClientProperty("Nimbus.Overrides", overrides);
        SwingUtilities.updateComponentTreeUI(slider);
        return slider;
    }

    private static JTextField textField() {
        JTextField field = new JTextField("Overridden", 12);
        UIDefaults overrides = new UIDefaults();
        overrides.put("TextField.contentMargins", new InsetsUIResource(8, 12, 8, 12));
        overrides.put("TextField[Enabled].backgroundPainter", new FlatPainter(FIELD));
        field.putClientProperty("Nimbus.Overrides", overrides);
        SwingUtilities.updateComponentTreeUI(field);
        return field;
    }

    private static JComponent overrides() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.add(overriddenButton());
        row.add(notInheritingButton());
        row.add(customStateButton());
        row.add(progressBar());
        row.add(slider());
        row.add(textField());
        row.setPreferredSize(new Dimension(LafGallery.GALLERY_WIDTH, row.getPreferredSize().height));
        return row;
    }

    private static List<Check> overrideChecks() {
        List<Check> checks = new ArrayList<>();
        JButton overridden = overriddenButton();
        checks.add(Checks.expect("Overrides : insets (contentMargins)", "10,24,10,24", () -> insets(overridden.getInsets())));
        checks.add(pixel("Overrides : painter pixel", overridden, TEAL));
        JButton notInheriting = notInheritingButton();
        checks.add(Checks.expect("InheritDefaults false : insets", "4,30,4,30", () -> insets(notInheriting.getInsets())));
        checks.add(pixel("InheritDefaults false : painter pixel", notInheriting, RED));
        checks.add(pixel("custom State : painter pixel", customStateButton(), YELLOW));
        JButton plain = new JButton("Custom state");
        checks.add(Checks.info("standard button insets", () -> insets(plain.getInsets())));
        checks.add(pixel("TextField override : painter pixel", textField(), FIELD));
        return checks;
    }

    private static Check pixel(String name, JComponent component, int argb) {
        return Checks.expect(name, Checks.argb(argb), () -> {
            BufferedImage image = LafSupport.renderStaged(component);
            // a text field : right of the text ; a button : left of the text (inside the margins)
            int x = component instanceof JTextField ? image.getWidth() - 8 : 4;
            return Checks.argb(image.getRGB(x, image.getHeight() / 2));
        });
    }

    private static String insets(Insets in) {
        return in.top + "," + in.left + "," + in.bottom + "," + in.right;
    }

    /**
     * A custom Nimbus painter : a flat fill with a darker bottom line. Application class used by instance.
     */
    static final class FlatPainter implements Painter<JComponent> {

        private final int argb;

        FlatPainter(int argb) {
            this.argb = argb;
        }

        @Override
        public void paint(Graphics2D g, JComponent c, int width, int height) {
            g.setColor(new Color(argb, true));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(argb, true).darker());
            g.fillRect(0, height - 2, width, 2);
        }
    }

    /**
     * A custom slider thumb painter : a diamond.
     */
    static final class DiamondPainter implements Painter<JComponent> {

        private final int argb;

        DiamondPainter(int argb) {
            this.argb = argb;
        }

        @Override
        public void paint(Graphics2D g, JComponent c, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(argb, true));
                int cx = width / 2;
                int cy = height / 2;
                int r = Math.min(width, height) / 2 - 1;
                g2.fillPolygon(new int[] { cx, cx + r, cx, cx - r }, new int[] { cy - r, cy, cy + r, cy }, 4);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * A custom Nimbus state : components whose client property {@code showcase.highlighted} is true.
     */
    static final class HighlightedState extends State<JComponent> {

        static final String PROPERTY = "showcase.highlighted";

        HighlightedState() {
            super("Highlighted");
        }

        @Override
        protected boolean isInState(JComponent c) {
            return Boolean.TRUE.equals(c.getClientProperty(PROPERTY));
        }
    }
}
