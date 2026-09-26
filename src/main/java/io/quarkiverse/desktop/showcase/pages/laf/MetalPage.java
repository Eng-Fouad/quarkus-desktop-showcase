package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import javax.swing.plaf.metal.OceanTheme;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Metal, the cross platform look and feel : the Ocean theme (live gallery), the Steel theme ({@link DefaultMetalTheme}),
 * {@code swing.boldMetal=false}, two custom {@link MetalTheme}s (colors, fonts, custom defaults such as button
 * gradients), and look and feel decorated windows ({@code JFrame.setDefaultLookAndFeelDecorated}, the dialog decoration
 * styles : {@code MetalRootPaneUI} and its title pane).
 * <p>
 * The theme variants are rendered offscreen while their theme is installed (a Metal theme is global : Metal delegates
 * read it while painting) : thumbnails here, full size extra snapshots. {@code MetalHighContrastTheme} is package
 * private : Metal only selects it when Windows runs in high contrast mode, a custom high contrast theme stands in for
 * it.
 */
@Singleton
public class MetalPage extends AbstractLafPage {

    private static final String METAL = "javax.swing.plaf.metal.MetalLookAndFeel";
    private static final String OCEAN_GRADIENT = "0.30 0.00 #FFDDE8F3 #FFFFFFFF #FFB8CFE5";

    private JFrame frame;
    private JDialog dialog;

    @Override
    public String id() {
        return "laf-metal";
    }

    @Override
    public String title() {
        return "Metal (Ocean, Steel, themes)";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    protected Component buildPage() throws Exception {
        // theme variants, each rendered while installed
        Map<String, BufferedImage> variants = new LinkedHashMap<>();
        List<Check> themeChecks = new ArrayList<>();
        variant("steel", "Steel (DefaultMetalTheme)", new DefaultMetalTheme(), null,
                new Expected("Dialog bold 12.0", 0xFFCCCCFF, "null"), variants, themeChecks);
        variant("ocean-plain", "Ocean, swing.boldMetal=false", new OceanTheme(), Boolean.FALSE,
                new Expected("Dialog plain 12.0", 0xFFB8CFE5, OCEAN_GRADIENT), variants, themeChecks);
        variant("custom", "Custom theme", new ShowcaseTheme(), null,
                new Expected("SansSerif plain 12.0", 0xFFC8E6C9, "0.30 0.00 #FFE8F5E9 #FFFFFFFF #FFA5D6A7"), variants,
                themeChecks);
        variant("contrast", "Custom high contrast theme", new ContrastTheme(), null,
                new Expected("Dialog bold 13.0", 0xFFFFFFFF, "null"), variants, themeChecks);

        // the live gallery : Ocean, bold fonts (the defaults)
        MetalLookAndFeel.setCurrentTheme(new OceanTheme());
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        themeChecks.addAll(0, themeChecks("Ocean", 0x6382BF, 0xA3B8CC));
        gallery = LafGallery.build();

        List<Check> lafChecks = LafSupport.lookAndFeelChecks(METAL, false, true);
        lafChecks.add(Checks.info("Metal auditory cues", () -> ((Object[]) UIManager.get("AuditoryCues.allAuditoryCues"))
                .length + " cues, OptionPane.errorSound " + UIManager.get("OptionPane.errorSound")));
        lafChecks.add(Checks.info("desktop property win.highContrast.on",
                () -> Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on")));

        List<Check> decorationChecks = decoratedWindows();

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("Metal look and feel"));
        content.add(Ui.text("The cross platform look and feel with its default Ocean theme (below, live). Metal themes "
                + "are global : each variant is rendered while its theme is installed (full size images are extra "
                + "snapshots). A LaF decorated JFrame and JDialog are shown next to the main window.", 1000));
        content.add(galleryView());
        content.add(Ui.title("Theme variants"));
        content.add(LafSupport.thumbnails(variants, 0.49, 2));
        content.add(LafSupport.twoColumns("Themes", themeChecks, 230));
        content.add(ChecksView.table("LaF decorated windows (MetalRootPaneUI, MetalTitlePane)", decorationChecks, 330,
                ChecksView.WIDTH));
        content.addAll(standardSections(gallery, lafChecks, Map.of("MetalTitlePane.closeTitle", "Close",
                "MetalTitlePane.iconifyTitle", "Minimize", "FileChooser.lookInLabelText", "Look In:"),
                List.of("FileChooser.fileNameLabelText", "FileChooser.filesOfTypeLabelText"), true));
        return LafSupport.page(content.toArray(Component[]::new));
    }

    /** Platform independent values of a theme (the Metal fonts are logical fonts unless swing.useSystemFontSettings). */
    private record Expected(String controlTextFont, int primaryControl, String buttonGradient) {
    }

    private void variant(String key, String label, MetalTheme theme, Boolean boldMetal, Expected expected,
            Map<String, BufferedImage> images, List<Check> checks) throws Exception {
        MetalLookAndFeel.setCurrentTheme(theme);
        state.put("swing.boldMetal", boldMetal);
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        LafGallery variant = LafGallery.build();
        BufferedImage image = LafSupport.renderStaged(variant.panel());
        images.put(label, image);
        extras.put("theme-" + key, image);
        checks.add(Checks.expect(label + " : theme", theme.getName(), () -> MetalLookAndFeel.getCurrentTheme().getName()));
        checks.add(Checks.expect(label + " : control text font", expected.controlTextFont(),
                () -> LafSupport.font(MetalLookAndFeel.getControlTextFont())));
        checks.add(Checks.expect(label + " : primary control", Checks.argb(expected.primaryControl()),
                () -> LafSupport.color(MetalLookAndFeel.getPrimaryControl())));
        checks.add(Checks.expect(label + " : Button.gradient", expected.buttonGradient(),
                () -> gradient(UIManager.get("Button.gradient"))));
        state.put("swing.boldMetal", null);
    }

    private static List<Check> themeChecks(String name, int primary1, int primary2) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("Ocean : theme", name, () -> MetalLookAndFeel.getCurrentTheme().getName()));
        checks.add(Checks.expect("Ocean : primary control dark shadow (primary 1)", Checks.argb(0xFF000000 | primary1),
                () -> LafSupport.color(MetalLookAndFeel.getPrimaryControlDarkShadow())));
        checks.add(Checks.expect("Ocean : primary control shadow (primary 2)", Checks.argb(0xFF000000 | primary2),
                () -> LafSupport.color(MetalLookAndFeel.getPrimaryControlShadow())));
        checks.add(Checks.expect("Ocean : control text font", "Dialog bold 12.0",
                () -> LafSupport.font(MetalLookAndFeel.getControlTextFont())));
        checks.add(Checks.expect("Ocean : Button.gradient", OCEAN_GRADIENT, () -> gradient(UIManager.get("Button.gradient"))));
        return checks;
    }

    private static String gradient(Object value) {
        if (!(value instanceof List<?> list)) {
            return String.valueOf(value);
        }
        List<String> parts = new ArrayList<>();
        for (Object o : list) {
            parts.add(o instanceof Color c ? LafSupport.color(c) : o instanceof Number n ? Checks.num(n.doubleValue(), 2)
                    : String.valueOf(o));
        }
        return String.join(" ", parts);
    }

    // ---------------------------------------------------------------------------------------- decorated windows

    private List<Check> decoratedWindows() {
        List<Check> checks = new ArrayList<>();
        boolean snapshot = ShowcaseMode.snapshot();
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        try {
            frame = new JFrame("Decorated JFrame");
            dialog = new JDialog(frame, "Decorated JDialog", false);
        } finally {
            JFrame.setDefaultLookAndFeelDecorated(false);
            JDialog.setDefaultLookAndFeelDecorated(false);
        }
        windows.add(dialog);
        windows.add(frame);
        JPanel frameContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        frameContent.add(new JLabel("LaF decorated frame"));
        frameContent.add(new JButton("Button"));
        frameContent.add(new JCheckBox("Check", true));
        frameContent.add(new JTextField("Text", 10));
        frame.setContentPane(frameContent);
        frame.setIconImages(List.of(Snapshots.offscreen(16, 16, g -> {
            g.setColor(new Color(0x1E88E5));
            g.fillRect(2, 2, 12, 12);
        })));
        JPanel dialogContent = new JPanel(new BorderLayout());
        dialogContent.add(new JLabel("  LaF decorated dialog (PLAIN_DIALOG)"), BorderLayout.CENTER);
        dialog.setContentPane(dialogContent);
        for (Window w : List.of(frame, dialog)) {
            w.setAutoRequestFocus(false);
            if (snapshot) {
                // never active : the title bar is always painted inactive
                w.setFocusableWindowState(false);
            }
        }
        frame.setBounds(1460, 40, 380, 200);
        dialog.setBounds(1460, 260, 380, 140);
        frame.setVisible(true);
        dialog.setVisible(true);

        checks.add(Checks.expect("JFrame.isUndecorated()", true, frame::isUndecorated));
        checks.add(Checks.expect("JFrame root pane decoration style", JRootPane.FRAME,
                () -> frame.getRootPane().getWindowDecorationStyle()));
        checks.add(Checks.expect("JDialog root pane decoration style", JRootPane.PLAIN_DIALOG,
                () -> dialog.getRootPane().getWindowDecorationStyle()));
        checks.add(Checks.expect("JFrame root pane UI", "javax.swing.plaf.metal.MetalRootPaneUI",
                () -> frame.getRootPane().getUI().getClass().getName()));
        checks.add(Checks.expect("JFrame title pane", "MetalTitlePane", () -> titlePane(frame.getRootPane())));
        checks.add(Checks.info("JFrame title pane height", () -> titlePaneHeight(frame.getRootPane())));

        // every other decoration style : packed (displayable) dialogs, never shown, rendered side by side
        int[] styles = { JRootPane.INFORMATION_DIALOG, JRootPane.ERROR_DIALOG, JRootPane.QUESTION_DIALOG,
                JRootPane.WARNING_DIALOG, JRootPane.COLOR_CHOOSER_DIALOG, JRootPane.FILE_CHOOSER_DIALOG };
        String[] names = { "INFORMATION_DIALOG", "ERROR_DIALOG", "QUESTION_DIALOG", "WARNING_DIALOG",
                "COLOR_CHOOSER_DIALOG", "FILE_CHOOSER_DIALOG" };
        List<BufferedImage> images = new ArrayList<>();
        for (int i = 0; i < styles.length; i++) {
            JDialog d = new JDialog(frame, names[i], false);
            windows.add(0, d);
            d.setUndecorated(true);
            d.getRootPane().setWindowDecorationStyle(styles[i]);
            JLabel label = new JLabel("  " + names[i]);
            label.setPreferredSize(new Dimension(300, 50));
            d.getContentPane().add(label);
            d.pack();
            int style = styles[i];
            checks.add(Checks.expect(names[i] + " title pane", "MetalTitlePane", () -> {
                if (d.getRootPane().getWindowDecorationStyle() != style) {
                    throw new IllegalStateException("style " + d.getRootPane().getWindowDecorationStyle());
                }
                return titlePane(d.getRootPane());
            }));
            images.add(Snapshots.render(d.getRootPane()));
        }
        int width = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int height = images.stream().mapToInt(img -> img.getHeight() + 8).sum();
        extras.put("dialog-styles", Snapshots.offscreen(width, height, g -> {
            int y = 0;
            for (BufferedImage img : images) {
                g.drawImage(img, 0, y, null);
                y += img.getHeight() + 8;
            }
        }));
        return checks;
    }

    private static String titlePane(JRootPane root) {
        for (Component c : root.getLayeredPane().getComponents()) {
            if (c.getClass().getName().equals("javax.swing.plaf.metal.MetalTitlePane")) {
                return "MetalTitlePane";
            }
        }
        return "none";
    }

    private static int titlePaneHeight(JRootPane root) {
        for (Component c : root.getLayeredPane().getComponents()) {
            if (c.getClass().getName().equals("javax.swing.plaf.metal.MetalTitlePane")) {
                return c.getHeight();
            }
        }
        return -1;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        JFrame f = frame;
        JDialog d = dialog;
        return Edt.until(() -> f.isShowing() && d.isShowing(), 5000, "decorated windows shown")
                .thenCompose(v -> Edt.stable(f.getRootPane(), 2000))
                .thenCompose(v -> Edt.stable(d.getRootPane(), 2000))
                .thenCompose(v -> super.ready(content));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        extras.put("decorated-frame", Snapshots.render(frame.getRootPane()));
        extras.put("decorated-dialog", Snapshots.render(dialog.getRootPane()));
        return super.extraSnapshots(content);
    }

    @Override
    public void dispose(Component content) {
        frame = null;
        dialog = null;
        super.dispose(content);
    }

    // ----------------------------------------------------------------------------------------------------- themes

    /**
     * A custom Metal theme : green primary colors, warm secondary colors, plain fonts of other logical families and a
     * custom defaults entry (button gradient, like Ocean). Used by instance : no reflection.
     */
    static final class ShowcaseTheme extends DefaultMetalTheme {

        private final ColorUIResource primary1 = new ColorUIResource(0x1B5E20);
        private final ColorUIResource primary2 = new ColorUIResource(0x66BB6A);
        private final ColorUIResource primary3 = new ColorUIResource(0xC8E6C9);
        private final ColorUIResource secondary1 = new ColorUIResource(0x6D4C41);
        private final ColorUIResource secondary2 = new ColorUIResource(0xBCAAA4);
        private final ColorUIResource secondary3 = new ColorUIResource(0xF3EDE7);
        private final FontUIResource control = new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 12);
        private final FontUIResource title = new FontUIResource(Font.SERIF, Font.BOLD, 13);
        private final FontUIResource user = new FontUIResource(Font.MONOSPACED, Font.PLAIN, 12);

        @Override
        public String getName() {
            return "Showcase";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return primary1;
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return primary2;
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return primary3;
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return secondary1;
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return secondary2;
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return secondary3;
        }

        @Override
        public FontUIResource getControlTextFont() {
            return control;
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return control;
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return control;
        }

        @Override
        public FontUIResource getUserTextFont() {
            return user;
        }

        @Override
        public FontUIResource getWindowTitleFont() {
            return title;
        }

        @Override
        public void addCustomEntriesToTable(UIDefaults table) {
            super.addCustomEntriesToTable(table);
            table.put("Button.gradient", List.of(0.3f, 0f, new ColorUIResource(0xE8F5E9),
                    new ColorUIResource(0xFFFFFF), new ColorUIResource(0xA5D6A7)));
            table.put("ToggleButton.gradient", table.get("Button.gradient"));
        }
    }

    /**
     * A high contrast theme (black on white, bold) standing in for the package private {@code MetalHighContrastTheme}.
     */
    static final class ContrastTheme extends DefaultMetalTheme {

        private final ColorUIResource black = new ColorUIResource(0x000000);
        private final ColorUIResource gray = new ColorUIResource(0x808080);
        private final ColorUIResource light = new ColorUIResource(0xCCCCCC);
        private final ColorUIResource white = new ColorUIResource(0xFFFFFF);
        private final ColorUIResource yellow = new ColorUIResource(0xFFFF00);
        private final FontUIResource bold = new FontUIResource(Font.DIALOG, Font.BOLD, 13);

        @Override
        public String getName() {
            return "Showcase contrast";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return black;
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return light;
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return white;
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return black;
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return gray;
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return white;
        }

        @Override
        public ColorUIResource getTextHighlightColor() {
            return black;
        }

        @Override
        public ColorUIResource getHighlightedTextColor() {
            return yellow;
        }

        @Override
        public ColorUIResource getMenuSelectedBackground() {
            return black;
        }

        @Override
        public ColorUIResource getMenuSelectedForeground() {
            return yellow;
        }

        @Override
        public FontUIResource getControlTextFont() {
            return bold;
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return bold;
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return bold;
        }

        @Override
        public FontUIResource getUserTextFont() {
            return bold;
        }

        @Override
        public void addCustomEntriesToTable(UIDefaults table) {
            super.addCustomEntriesToTable(table);
            table.put("ToolTip.border", new javax.swing.plaf.BorderUIResource.LineBorderUIResource(black, 2));
            table.put("Table.gridColor", gray);
        }
    }
}
