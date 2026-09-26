package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The GTK+ look and feel (Linux only) : GTK 3 loaded by the toolkit ({@code UNIXToolkit.loadGTK}), the native GTK engine
 * painting into images ({@code GTKEngine} JNI), styles read from the GTK theme ({@code GTKStyle} natives, lazy values
 * created by method name through reflection), Metacity title panes of internal frames (theme XML parsed with the DOM
 * API, the fallback theme is a resource), the {@code GTKFileChooserUI}. Installed by class name.
 * <p>
 * The rendering depends on the GTK theme : pin it (e.g. {@code GTK_THEME=Adwaita}) for comparable runs. On other
 * operating systems the page only states that the look and feel is not available (it is not even shipped on Windows).
 */
@Singleton
public class GtkPage extends AbstractLafPage {

    private static final String GTK = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";

    @Override
    public String id() {
        return "laf-gtk";
    }

    @Override
    public String title() {
        return "GTK+";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    protected boolean activationDependent() {
        return true;
    }

    @Override
    protected Component buildPage() throws Exception {
        if (!Platforms.isLinux()) {
            return notAvailable();
        }
        List<Check> gtk = new ArrayList<>();
        gtk.add(Checks.expect("installed : GTK+", true, () -> Arrays.stream(UIManager.getInstalledLookAndFeels())
                .anyMatch(info -> info.getClassName().equals(GTK))));
        gtk.add(Checks.expect("UIManager.createLookAndFeel(\"GTK+\")", GTK, () -> {
            LookAndFeel laf = UIManager.createLookAndFeel("GTK+");
            return laf.getClass().getName();
        }));
        gtk.add(Checks.info("system look and feel", UIManager::getSystemLookAndFeelClassName));
        gtk.add(Checks.info("jdk.gtk.version", () -> System.getProperty("jdk.gtk.version")));
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (String key : List.of("gnome.Net/ThemeName", "gnome.Gtk/FontName", "gnome.Xft/Antialias",
                "gnome.Xft/RGBA", "gnome.Net/DoubleClickTime", "awt.font.desktophints")) {
            gtk.add(Checks.info("desktop property " + key, () -> {
                Object value = toolkit.getDesktopProperty(key);
                return value instanceof Map<?, ?> map ? map.size() + " hints" : String.valueOf(value);
            }));
        }

        UIManager.setLookAndFeel(GTK);
        gallery = LafGallery.build();
        List<Check> lafChecks = LafSupport.lookAndFeelChecks(GTK, true, null);

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("GTK+ look and feel"));
        content.add(Ui.text("The GTK look and feel paints with the native GTK 3 engine and the current GTK theme "
                + "(pin GTK_THEME for comparable runs).", 1000));
        content.add(galleryView());
        content.add(LafSupport.twoColumns("GTK", gtk, 230));
        content.addAll(standardSections(gallery, lafChecks,
                Map.of("FileChooser.acceptAllFileFilterText", "All Files", "FileChooser.pathLabelText", "Selection:",
                        "FileChooser.filterLabelText", "Filter:"),
                List.of("FileChooser.foldersLabelText", "FileChooser.filesLabelText"), true));
        return LafSupport.page(content.toArray(Component[]::new));
    }

    private Component notAvailable() {
        List<Check> checks = new ArrayList<>();
        checks.add(Check.info("availability", "not available on this OS"));
        checks.add(Checks.expect("installed : GTK+", false, () -> Arrays.stream(UIManager.getInstalledLookAndFeels())
                .anyMatch(info -> info.getClassName().equals(GTK))));
        return LafSupport.page(Ui.heading("GTK+ look and feel"),
                Ui.text("The GTK+ look and feel only exists on Linux (and other Unix desktops).", 1000),
                ChecksView.table("GTK+ look and feel", checks));
    }
}
