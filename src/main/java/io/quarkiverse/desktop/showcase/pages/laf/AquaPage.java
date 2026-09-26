package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.swing.UIManager;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The Aqua look and feel (macOS only) : the default and system look and feel on macOS, which also draws the AWT
 * components there (every {@code sun.lwawt} peer has a Swing delegate). Its UI delegates are created by name, its
 * singletons and borders with reflection ({@code AquaUtils.RecyclableSingletonFromDefaultConstructor},
 * {@code AquaBorder.deriveBorderForSize}), it paints with the native JRSUI renderer ({@code libosxui}) into multi
 * resolution images (the extra snapshot at scale 2 draws the Retina variants), and its icons come from {@code NSImage}.
 * Installed by class name. On other operating systems the page only states that it is not available.
 */
@Singleton
public class AquaPage extends AbstractLafPage {

    static final String AQUA = "com.apple.laf.AquaLookAndFeel";

    /**
     * UIClassID to UI delegate class under Aqua ({@code AquaLookAndFeel.initClassDefaults}) ; TabbedPaneUI depends on
     * the macOS version ({@code AquaTabbedPaneContrastUI} or {@code AquaTabbedPaneUI}) : informational.
     */
    static final Map<String, String> AQUA_UI_CLASSES = Map.ofEntries(
            Map.entry("ButtonUI", "com.apple.laf.AquaButtonUI"),
            Map.entry("CheckBoxUI", "com.apple.laf.AquaButtonCheckBoxUI"),
            Map.entry("CheckBoxMenuItemUI", "com.apple.laf.AquaMenuItemUI"),
            Map.entry("ColorChooserUI", "javax.swing.plaf.basic.BasicColorChooserUI"),
            Map.entry("ComboBoxUI", "com.apple.laf.AquaComboBoxUI"),
            Map.entry("DesktopIconUI", "com.apple.laf.AquaInternalFrameDockIconUI"),
            Map.entry("DesktopPaneUI", "com.apple.laf.AquaInternalFramePaneUI"),
            Map.entry("EditorPaneUI", "com.apple.laf.AquaEditorPaneUI"),
            Map.entry("FileChooserUI", "com.apple.laf.AquaFileChooserUI"),
            Map.entry("FormattedTextFieldUI", "com.apple.laf.AquaTextFieldFormattedUI"),
            Map.entry("InternalFrameUI", "com.apple.laf.AquaInternalFrameUI"),
            Map.entry("LabelUI", "com.apple.laf.AquaLabelUI"),
            Map.entry("ListUI", "com.apple.laf.AquaListUI"),
            Map.entry("MenuBarUI", "com.apple.laf.AquaMenuBarUI"),
            Map.entry("MenuItemUI", "com.apple.laf.AquaMenuItemUI"),
            Map.entry("MenuUI", "com.apple.laf.AquaMenuUI"),
            Map.entry("OptionPaneUI", "com.apple.laf.AquaOptionPaneUI"),
            Map.entry("PanelUI", "com.apple.laf.AquaPanelUI"),
            Map.entry("PasswordFieldUI", "com.apple.laf.AquaTextPasswordFieldUI"),
            Map.entry("PopupMenuSeparatorUI", "com.apple.laf.AquaPopupMenuSeparatorUI"),
            Map.entry("PopupMenuUI", "com.apple.laf.AquaPopupMenuUI"),
            Map.entry("ProgressBarUI", "com.apple.laf.AquaProgressBarUI"),
            Map.entry("RadioButtonMenuItemUI", "com.apple.laf.AquaMenuItemUI"),
            Map.entry("RadioButtonUI", "com.apple.laf.AquaButtonRadioUI"),
            Map.entry("RootPaneUI", "com.apple.laf.AquaRootPaneUI"),
            Map.entry("ScrollBarUI", "com.apple.laf.AquaScrollBarUI"),
            Map.entry("ScrollPaneUI", "com.apple.laf.AquaScrollPaneUI"),
            Map.entry("SeparatorUI", "com.apple.laf.AquaPopupMenuSeparatorUI"),
            Map.entry("SliderUI", "com.apple.laf.AquaSliderUI"),
            Map.entry("SpinnerUI", "com.apple.laf.AquaSpinnerUI"),
            Map.entry("SplitPaneUI", "com.apple.laf.AquaSplitPaneUI"),
            Map.entry("TableHeaderUI", "com.apple.laf.AquaTableHeaderUI"),
            Map.entry("TableUI", "com.apple.laf.AquaTableUI"),
            Map.entry("TextAreaUI", "com.apple.laf.AquaTextAreaUI"),
            Map.entry("TextFieldUI", "com.apple.laf.AquaTextFieldUI"),
            Map.entry("TextPaneUI", "com.apple.laf.AquaTextPaneUI"),
            Map.entry("ToggleButtonUI", "com.apple.laf.AquaButtonToggleUI"),
            Map.entry("ToolBarSeparatorUI", "com.apple.laf.AquaToolBarSeparatorUI"),
            Map.entry("ToolBarUI", "com.apple.laf.AquaToolBarUI"),
            Map.entry("ToolTipUI", "com.apple.laf.AquaToolTipUI"),
            Map.entry("TreeUI", "com.apple.laf.AquaTreeUI"),
            Map.entry("ViewportUI", "javax.swing.plaf.basic.BasicViewportUI"));

    @Override
    public String id() {
        return "laf-aqua";
    }

    @Override
    public String title() {
        return "Aqua (macOS)";
    }

    @Override
    public int order() {
        return 65;
    }

    @Override
    protected boolean activationDependent() {
        // Aqua paints focus rings, default buttons and selections differently in an inactive window
        return true;
    }

    @Override
    protected Component buildPage() throws Exception {
        if (!Platforms.isMac()) {
            return notAvailable("Aqua look and feel", "The Aqua look and feel only exists on macOS.",
                    installed(false));
        }
        List<Check> aqua = new ArrayList<>(installed(true));
        aqua.add(Checks.expect("UIManager.getSystemLookAndFeelClassName()", AQUA,
                UIManager::getSystemLookAndFeelClassName));
        aqua.add(Checks.info("macOS version", Platforms::macMajorVersion));

        UIManager.setLookAndFeel(AQUA);
        gallery = LafGallery.build();
        List<Check> lafChecks = LafSupport.lookAndFeelChecks(AQUA, true, false);
        // Aqua fonts (AquaFonts) : Lucida Grande, 13 points for controls, 11 small, 9 mini
        aqua.add(Checks.expect("Label.font", "Lucida Grande 13", () -> fontOf("Label.font")));
        aqua.add(Checks.expect("Button.font", "Lucida Grande 13", () -> fontOf("Button.font")));
        aqua.add(Checks.info("Focus.color (accent color of the settings)",
                () -> LafSupport.color(UIManager.getColor("Focus.color"))));
        aqua.add(Checks.info("TabbedPaneUI", () -> String.valueOf(UIManager.getDefaults().get("TabbedPaneUI"))));
        List<Check> classes = new ArrayList<>();
        AQUA_UI_CLASSES.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> classes.add(Checks.expect(
                "UIDefaults " + e.getKey(), e.getValue(), () -> String.valueOf(UIManager.getDefaults().get(e.getKey())))));

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("Aqua look and feel"));
        content.add(Ui.text("The macOS look and feel, painted by the native JRSUI renderer. It is also the look of the AWT "
                + "components on macOS (their peers have Aqua delegates). The extra snapshot renders the gallery at "
                + "scale 2 (Retina variants of the native images).", 1000));
        content.add(galleryView());
        content.add(LafSupport.twoColumns("Aqua", aqua, 230));
        content.add(LafSupport.twoColumns("UI class defaults (AquaLookAndFeel.initClassDefaults)", classes, 230));
        content.addAll(standardSections(gallery, lafChecks, Map.of(), List.of("FileChooser.cancelButtonText",
                "FileChooser.saveButtonText", "FileChooser.openButtonText", "OptionPane.okButtonText"), false));
        return LafSupport.page(content.toArray(Component[]::new));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        if (gallery != null) {
            // the Retina variants of the JRSUI images (AquaPainter paints multi resolution images)
            extras.put("gallery-2x", Snapshots.render(gallery.panel(), 2));
        }
        return super.extraSnapshots(content);
    }

    private static String fontOf(String key) {
        Font font = UIManager.getFont(key);
        return font == null ? "null" : font.getFamily() + " " + font.getSize();
    }

    private static List<Check> installed(boolean expected) {
        return List.of(Checks.expect("installed : Mac OS X", expected, () -> Arrays
                .stream(UIManager.getInstalledLookAndFeels()).anyMatch(info -> info.getClassName().equals(AQUA))));
    }

    /**
     * The content of a macOS look and feel page on another operating system.
     */
    static Component notAvailable(String title, String text, List<Check> extra) {
        List<Check> checks = new ArrayList<>();
        checks.add(Check.info("availability", "not available on this OS"));
        checks.addAll(extra);
        return LafSupport.page(Ui.heading(title), Ui.text(text, 1000), ChecksView.table(title, checks));
    }
}
