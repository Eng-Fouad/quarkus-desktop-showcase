package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.UIManager;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * CDE/Motif : installed by class name (its package is not exported : {@code UIManager.setLookAndFeel(String)}
 * instantiates it reflectively), 31 Motif UI delegates, the Motif icons and resource bundle. On Linux, the AWT text
 * components are Swing components with the Motif based {@code XAWTLookAndFeel} : the same delegates.
 */
@Singleton
public class MotifPage extends AbstractLafPage {

    private static final String MOTIF = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";

    @Override
    public String id() {
        return "laf-motif";
    }

    @Override
    public String title() {
        return "CDE/Motif";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    protected Component buildPage() throws Exception {
        UIManager.setLookAndFeel(MOTIF);
        gallery = LafGallery.build();

        List<Check> lafChecks = LafSupport.lookAndFeelChecks(MOTIF, false, false);
        lafChecks.add(Checks.info("Desktop.background", () -> LafSupport.color(UIManager.getColor("Desktop.background"))));
        lafChecks.add(Checks.info("activeCaption", () -> LafSupport.color(UIManager.getColor("activeCaption"))));
        lafChecks.add(Checks.info("ScrollBar.width", () -> UIManager.get("ScrollBar.width")));

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("CDE/Motif look and feel"));
        content.add(Ui.text("The Motif look and feel, installed with UIManager.setLookAndFeel(className) (its package "
                + "is not exported : reflective instantiation). Motif delegates, icons and resource bundle.", 1000));
        content.add(galleryView());
        content.addAll(standardSections(gallery, lafChecks,
                Map.of("FileChooser.openButtonText", "OK", "FileChooser.enterFileNameLabelText", "Enter file name:",
                        "FileChooser.pathLabelText", "Enter path or folder name:", "FileChooser.filterLabelText",
                        "Filter"),
                List.of("FileChooser.foldersLabelText", "FileChooser.filesLabelText"), true));
        return LafSupport.page(content.toArray(Component[]::new));
    }
}
