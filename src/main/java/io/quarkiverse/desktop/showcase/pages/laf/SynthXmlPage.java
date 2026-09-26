package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.plaf.synth.ColorType;
import javax.swing.plaf.synth.Region;
import javax.swing.plaf.synth.SynthConstants;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthLookAndFeel;
import javax.swing.plaf.synth.SynthStyle;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * A {@link SynthLookAndFeel} loaded from XML files bundled with the application
 * ({@code showcase/laf/synth/showcase-synth.xml}, then {@code showcase-synth-progress.xml} into the same look and
 * feel) : styles bound by region and by component name, states, fonts, colors (short and long color type names, named
 * colors : reflective field lookups), insets, opacity, image painters and image icons (images loaded from classpath
 * resource URLs : {@code resource:} in a native executable), {@code <object>} beans decoded by the JavaBeans XML decoder
 * (JDK classes and the application class {@link ShowcaseSynthPainter} with bean properties), a custom
 * {@code SynthPainter} referenced by {@code <painter idref>}, {@code <graphicsUtils>}, input maps (key strokes parsed
 * from strings), style properties and {@code <defaultsProperty>} of every type.
 * <p>
 * The PNG images of {@code showcase/laf/synth/} were generated once with Java2D (simple shapes : rounded gradient
 * buttons, check boxes, radio buttons, arrows, a text field frame, scroll bar parts, tabs, a table header, title bar
 * icons, a question mark icon), anti-aliased except the text field frame, written with ImageIO.
 */
@Singleton
public class SynthXmlPage extends AbstractLafPage {

    static final String XML = "/showcase/laf/synth/showcase-synth.xml";
    static final String PROGRESS_XML = "/showcase/laf/synth/showcase-synth-progress.xml";

    private static final int ACCENT = 0xFF3F72AF;
    private static final int PROGRESS_ACCENT = 0xFF2E7D32;

    @Override
    public String id() {
        return "laf-synth-xml";
    }

    @Override
    public String title() {
        return "Synth from XML";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    protected Component buildPage() throws Exception {
        SynthLookAndFeel synth = new SynthLookAndFeel();
        try (InputStream in = Edt.resourceStream(XML)) {
            synth.load(in, SynthXmlPage.class);
        }
        try (InputStream in = Edt.resourceStream(PROGRESS_XML)) {
            synth.load(in, SynthXmlPage.class);
        }
        UIManager.setLookAndFeel(synth);
        gallery = LafGallery.build();

        List<Check> lafChecks = LafSupport.lookAndFeelChecks("javax.swing.plaf.synth.SynthLookAndFeel", false, false);

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("Synth look and feel from XML"));
        content.add(Ui.text("SynthLookAndFeel.load(InputStream, Class) of two XML files bundled with the application : "
                + "image painters, image icons, beans (a custom SynthPainter with bean properties), states, fonts, "
                + "colors, insets, input maps, properties. Unstyled parts render with the defaults of Synth.", 1000));
        content.add(galleryView());
        content.add(LafSupport.twoColumns("Parsed styles", styleChecks(), 250));
        content.add(LafSupport.twoColumns("Painters and images (pixel probes of offscreen renders)", pixelChecks(), 250));
        content.addAll(standardSections(gallery, lafChecks,
                Map.of("FileChooser.lookInLabelText", "Look In:", "FileChooser.saveInLabelText", "Save In:"),
                List.of("FileChooser.fileNameLabelText"), false));
        return LafSupport.page(content.toArray(Component[]::new));
    }

    private static SynthContext context(JComponent c, Region region, int state) {
        return new SynthContext(c, region, SynthLookAndFeel.getStyle(c, region), state);
    }

    private static String insets(Insets in) {
        return in == null ? "null" : in.top + "," + in.left + "," + in.bottom + "," + in.right;
    }

    private static String icon(Object icon) {
        return icon instanceof Icon i ? i.getIconWidth() + "x" + i.getIconHeight() : String.valueOf(icon);
    }

    private List<Check> styleChecks() {
        List<Check> checks = new ArrayList<>();
        JButton button = new JButton("Button");
        SynthContext b = context(button, Region.BUTTON, SynthConstants.ENABLED);
        SynthStyle bs = b.getStyle();
        checks.add(Checks.expect("Button : font (idref)", "Dialog bold 12.0", () -> LafSupport.font(bs.getFont(b))));
        checks.add(Checks.expect("Button : insets", "4,12,4,12", () -> insets(bs.getInsets(b, null))));
        checks.add(Checks.expect("Button.margin (InsetsUIResource bean)", "2,10,2,10",
                () -> insets((Insets) bs.get(b, "Button.margin"))));
        checks.add(Checks.expect("Button.margin installed", "2,10,2,10", () -> insets(button.getMargin())));
        checks.add(Checks.expect("Button.textShiftOffset (integer)", 1, () -> bs.getInt(b, "Button.textShiftOffset", -1)));
        checks.add(Checks.expect("Button : FOCUS (long color type name)", Checks.argb(0xFFE65100),
                () -> LafSupport.color(bs.getColor(b, ColorType.FOCUS))));
        checks.add(Checks.expect("Button : opaque", false, () -> bs.isOpaque(b)));

        JTextField field = new JTextField("Text");
        SynthContext f = context(field, Region.TEXT_FIELD, SynthConstants.ENABLED);
        checks.add(Checks.expect("TextField : FOCUS (named color ORANGE)", Checks.argb(0xFFFFC800),
                () -> LafSupport.color(f.getStyle().getColor(f, ColorType.FOCUS))));
        checks.add(Checks.expect("TextField : BACKGROUND", Checks.argb(0xFFF4F6FA),
                () -> LafSupport.color(f.getStyle().getColor(f, ColorType.BACKGROUND))));
        checks.add(Checks.expect("TextField : insets", "4,6,4,6", () -> insets(field.getInsets())));
        checks.add(Checks.expect("TextField input map (bindKey)", 10,
                () -> field.getInputMap(JComponent.WHEN_FOCUSED).allKeys().length));
        checks.add(Checks.expect("TextField ctrl A", "select-all",
                () -> field.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("ctrl A"))));

        JCheckBox check = new JCheckBox("Check");
        checks.add(Checks.expect("CheckBox.icon (imageIcon)", "14x14",
                () -> icon(context(check, Region.CHECK_BOX, SynthConstants.ENABLED).getStyle()
                        .getIcon(context(check, Region.CHECK_BOX, SynthConstants.ENABLED), "CheckBox.icon"))));
        JRadioButton radio = new JRadioButton("Radio", true);
        SynthContext r = context(radio, Region.RADIO_BUTTON, SynthConstants.ENABLED | SynthConstants.SELECTED);
        checks.add(Checks.expect("RadioButton.icon, SELECTED state", "14x14",
                () -> icon(r.getStyle().getIcon(r, "RadioButton.icon"))));
        checks.add(Checks.expect("RadioButton : SELECTED TEXT_BACKGROUND", Checks.argb(0xFF3F72AF),
                () -> LafSupport.color(r.getStyle().getColor(r, ColorType.TEXT_BACKGROUND))));

        JTable table = new JTable(2, 2);
        checks.add(Checks.expect("Table.gridColor (color idref)", Checks.argb(0xFFD5DCE6),
                () -> LafSupport.color(table.getGridColor())));
        checks.add(Checks.expect("Table.rowHeight", 18, table::getRowHeight));
        JComponent header = (JComponent) table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(
                table, "Name", false, false, -1, 0);
        checks.add(Checks.expect("header renderer name", "TableHeader.renderer", header::getName));
        SynthContext h = context(header, Region.LABEL, SynthConstants.ENABLED);
        checks.add(Checks.expect("header renderer style (bound by name) : insets", "2,6,2,6",
                () -> insets(h.getStyle().getInsets(h, null))));

        JProgressBar progress = new JProgressBar();
        SynthContext p = context(progress, Region.PROGRESS_BAR, SynthConstants.ENABLED);
        checks.add(Checks.expect("ProgressBar (2nd file) : font", "Dialog bold 11.0",
                () -> LafSupport.font(p.getStyle().getFont(p))));
        checks.add(Checks.expect("ProgressBar.horizontalSize (dimension)", "150x18", () -> {
            Dimension d = (Dimension) p.getStyle().get(p, "ProgressBar.horizontalSize");
            return d.width + "x" + d.height;
        }));

        SynthContext d = context(button, Region.BUTTON, SynthConstants.ENABLED);
        checks.add(Checks.expect("Showcase.painter : bean class", "ShowcaseSynthPainter",
                () -> LafSupport.simple(d.getStyle().get(d, "Showcase.painter").getClass().getName())));
        checks.add(Checks.expect("Showcase.painter : accent (bean property)", Checks.argb(ACCENT),
                () -> LafSupport.color(((ShowcaseSynthPainter) d.getStyle().get(d, "Showcase.painter")).getAccent())));
        checks.add(Checks.expect("Showcase.painter : arrowSize (bean property)", 7,
                () -> ((ShowcaseSynthPainter) d.getStyle().get(d, "Showcase.painter")).getArrowSize()));
        checks.add(Checks.expect("graphicsUtils (bean)", "SynthGraphicsUtils",
                () -> LafSupport.simple(d.getStyle().getGraphicsUtils(d).getClass().getName())));

        checks.add(Checks.expect("defaultsProperty insets", "1,2,3,4",
                () -> insets((Insets) UIManager.get("Showcase.insets"))));
        checks.add(Checks.expect("defaultsProperty dimension", "16x24", () -> {
            Dimension dim = (Dimension) UIManager.get("Showcase.dimension");
            return dim.width + "x" + dim.height;
        }));
        checks.add(Checks.expect("defaultsProperty integer", 42, () -> UIManager.get("Showcase.integer")));
        checks.add(Checks.expect("defaultsProperty boolean", true, () -> UIManager.get("Showcase.boolean")));
        checks.add(Checks.expect("defaultsProperty string", "synth", () -> UIManager.get("Showcase.string")));
        checks.add(Checks.expect("defaultsProperty idref (String bean)", "Synth from XML",
                () -> UIManager.get("Showcase.title")));
        return checks;
    }

    /**
     * Pixel probes of components rendered offscreen : the custom painter (accent colors set as bean properties, a
     * second painter bean in the second file) and the image painters (images loaded from resource URLs).
     */
    private List<Check> pixelChecks() {
        List<Check> checks = new ArrayList<>();
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(100);
        checks.add(Checks.expect("progress bar foreground (painter bean of the 2nd file)", Checks.argb(PROGRESS_ACCENT),
                () -> {
                    BufferedImage image = LafSupport.renderStaged(progress);
                    return Checks.argb(image.getRGB(image.getWidth() / 2, 5));
                }));
        JButton button = new JButton("Button");
        checks.add(Checks.info("button image painter : left edge column", () -> {
            BufferedImage image = LafSupport.renderStaged(button);
            return Checks.argb(image.getRGB(0, image.getHeight() / 2)) + " " + Checks.argb(image.getRGB(3,
                    image.getHeight() / 2)) + " size " + image.getWidth() + "x" + image.getHeight();
        }));
        checks.add(Checks.info("button image painter : pixels hash",
                () -> Checks.sha256(LafSupport.renderStaged(new JButton("Button")))));
        JCheckBox check = new JCheckBox("Check", true);
        checks.add(Checks.info("check box selected icon : pixels hash",
                () -> Checks.sha256(LafSupport.renderStaged(check))));
        JTextField field = new JTextField("Text", 8);
        checks.add(Checks.expect("text field image painter : inside", Checks.argb(0xFFFFFFFF), () -> {
            BufferedImage image = LafSupport.renderStaged(field);
            return Checks.argb(image.getRGB(image.getWidth() - 4, image.getHeight() / 2));
        }));
        checks.add(Checks.expect("text field image painter : border", Checks.argb(0xFF8FA3BF), () -> {
            BufferedImage image = LafSupport.renderStaged(new JTextField("Text", 8));
            return Checks.argb(image.getRGB(image.getWidth() / 2, image.getHeight() - 1));
        }));
        return checks;
    }
}
