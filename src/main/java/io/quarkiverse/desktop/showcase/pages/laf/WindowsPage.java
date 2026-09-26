package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.UIManager;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The Windows look and feel (Windows only) : visual styles painted by uxtheme through {@code sun.awt.windows.ThemeReader}
 * (JNI), system colors read reflectively from {@code SystemColor} fields, {@code win.*} desktop properties (fonts,
 * colors, metrics set by native code), shell icons ({@code ShellFolder} : JNI and COM), the {@code WindowsFileChooserUI}
 * ; and Windows Classic (no visual styles), rendered offscreen while installed. Both are installed by class name.
 * <p>
 * On other operating systems the page only states that the look and feel is not available.
 */
@Singleton
public class WindowsPage extends AbstractLafPage {

    private static final String WINDOWS = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
    private static final String CLASSIC = "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel";

    @Override
    public String id() {
        return "laf-windows";
    }

    @Override
    public String title() {
        return "Windows and Windows Classic";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    protected boolean activationDependent() {
        return true;
    }

    @Override
    protected Component buildPage() throws Exception {
        if (!Platforms.isWindows()) {
            return notAvailable();
        }
        List<Check> installed = installedChecks();

        // Windows Classic, rendered while installed
        UIManager.setLookAndFeel(CLASSIC);
        List<Check> classicChecks = new ArrayList<>();
        classicChecks.add(Checks.expect("Windows Classic : getName()", "Windows Classic",
                () -> UIManager.getLookAndFeel().getName()));
        classicChecks.add(Checks.info("Windows Classic : Button.background",
                () -> LafSupport.color(UIManager.getColor("Button.background"))));
        LafGallery classic = LafGallery.build();
        classicChecks.addAll(prefixed("Windows Classic : ", LafSupport.uiChecks(classic.components()).subList(0, 6)));
        BufferedImage classicImage = LafSupport.renderStaged(classic.panel());
        extras.put("windows-classic", classicImage);

        // live : Windows
        UIManager.setLookAndFeel(WINDOWS);
        gallery = LafGallery.build();
        List<Check> lafChecks = LafSupport.lookAndFeelChecks(WINDOWS, true, false);

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("Windows look and feel"));
        content.add(Ui.text("Windows visual styles (uxtheme through ThemeReader JNI), desktop properties and shell "
                + "icons (live gallery below), and Windows Classic (rendered while installed, full size as an extra "
                + "snapshot).", 1000));
        content.add(galleryView());
        content.add(Ui.title("Windows Classic"));
        Map<String, BufferedImage> variants = new LinkedHashMap<>();
        variants.put("WindowsClassicLookAndFeel", classicImage);
        content.add(LafSupport.thumbnails(variants, 0.6, 1));
        content.add(LafSupport.twoColumns("Installed look and feels, Windows Classic", concat(installed, classicChecks),
                230));
        content.add(LafSupport.twoColumns("Desktop properties (win.*)", desktopProperties(), 230));
        content.addAll(standardSections(gallery, lafChecks,
                Map.of("FileChooser.lookInLabelText", "Look in:", "FileChooser.saveInLabelText", "Save in:",
                        "FileChooser.fileNameLabelText", "File name:"),
                List.of("FileChooser.filesOfTypeLabelText", "FileChooser.viewMenuButtonToolTipText"), true));
        return LafSupport.page(content.toArray(Component[]::new));
    }

    private static List<Check> installedChecks() {
        List<String> names = Arrays.stream(UIManager.getInstalledLookAndFeels()).map(UIManager.LookAndFeelInfo::getName)
                .toList();
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("installed : Windows", true, () -> names.contains("Windows")));
        checks.add(Checks.expect("installed : Windows Classic (visual styles active)",
                Toolkit.getDefaultToolkit().getDesktopProperty("win.xpstyle.themeActive") != null,
                () -> names.contains("Windows Classic")));
        checks.add(Checks.expect("system look and feel", WINDOWS, UIManager::getSystemLookAndFeelClassName));
        return checks;
    }

    private static List<Check> prefixed(String prefix, List<Check> checks) {
        return checks.stream().map(c -> new Check(prefix + c.name(), c.value(), c.ok())).toList();
    }

    /**
     * Desktop properties set by native code ({@code WDesktopProperties} JNI upcalls). The theme file is shown by name
     * only (no path).
     */
    private static List<Check> desktopProperties() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        List<Check> checks = new ArrayList<>();
        for (String key : List.of("win.xpstyle.themeActive", "win.xpstyle.colorName", "win.xpstyle.sizeName",
                "win.xpstyle.dllName", "win.highContrast.on", "win.messagebox.font", "win.menu.font",
                "win.frame.captionFont", "win.icon.font", "win.tooltip.font", "win.defaultGUI.font",
                "win.3d.backgroundColor", "win.3d.highlightColor", "win.button.textColor", "win.frame.activeCaptionColor",
                "win.item.highlightColor", "win.scrollbar.width", "win.frame.captionHeight", "win.menu.keyboardCuesOn",
                "win.text.fontSmoothingOn", "win.text.fontSmoothingType", "win.drag.width", "win.icon.hspacing",
                "awt.multiClickInterval", "awt.mouse.numButtons")) {
            checks.add(Checks.info(key, () -> describe(key, toolkit.getDesktopProperty(key))));
        }
        return checks;
    }

    private static String describe(String key, Object value) {
        if (value instanceof Font font) {
            return LafSupport.font(font);
        }
        if (value instanceof java.awt.Color color) {
            return LafSupport.color(color);
        }
        if (value instanceof String s && key.endsWith("dllName")) {
            return s.substring(Math.max(s.lastIndexOf('\\'), s.lastIndexOf('/')) + 1);
        }
        return String.valueOf(value);
    }

    private Component notAvailable() {
        List<Check> checks = new ArrayList<>();
        checks.add(Check.info("availability", "not available on this OS"));
        checks.add(Checks.expect("installed : Windows", false, () -> Arrays.stream(UIManager.getInstalledLookAndFeels())
                .anyMatch(info -> info.getClassName().equals(WINDOWS))));
        return LafSupport.page(Ui.heading("Windows look and feel"),
                Ui.text("The Windows and Windows Classic look and feels only exist on Windows.", 1000),
                ChecksView.table("Windows look and feel", checks));
    }
}
