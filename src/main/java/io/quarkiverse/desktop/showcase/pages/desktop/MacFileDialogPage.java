package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The native file dialogs of macOS (macOS only) : AWT {@code FileDialog} is the Cocoa open and save panel
 * ({@code sun.lwawt.macosx.CFileDialog}, whose native code calls back into {@code queryFilenameFilter} for the file name
 * filter, which macOS honours), with multiple selection and the directory mode of
 * {@code apple.awt.fileDialogForDirectories}. A native panel cannot be closed programmatically : in snapshot mode the
 * page only checks the dialog model (created, never shown) ; with {@code -Dshowcase.interactive=true} a button of the
 * page shows the panels and records what they return. On other operating systems the page only states that it is not
 * available. AWT only.
 */
@Singleton
public class MacFileDialogPage implements FeaturePage {

    private static final String DIRECTORIES = "apple.awt.fileDialogForDirectories";

    // per build state
    private ChecksView interactive;

    @Override
    public String id() {
        return "desktop-mac-file-dialog";
    }

    @Override
    public String title() {
        return "macOS file dialogs";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 230;
    }

    @Override
    public Component build() {
        if (!Platforms.isMac()) {
            return MacDesktop.notAvailable("macOS file dialogs",
                    "The Cocoa open and save panels of AWT FileDialog only exist on macOS.", List.of());
        }
        Frame owner = frameOf();
        File directory = Edt.tempDir().toFile();
        List<Check> model = new ArrayList<>();
        FileDialog load = new FileDialog(owner, "Open (showcase)", FileDialog.LOAD);
        load.setMultipleMode(true);
        load.setDirectory(directory.getPath());
        load.setFile("*.txt");
        load.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        model.add(Checks.expect("LOAD mode", FileDialog.LOAD, load::getMode));
        model.add(Checks.expect("isMultipleMode()", true, load::isMultipleMode));
        model.add(Checks.expect("getDirectory() round trip", true, () -> directory.getPath().equals(load.getDirectory())));
        model.add(Checks.expect("getFile()", "*.txt", load::getFile));
        model.add(Checks.expect("getFilenameFilter() accepts a.txt, rejects a.png", "true false",
                () -> load.getFilenameFilter().accept(directory, "a.txt") + " " + load.getFilenameFilter().accept(directory,
                        "a.png")));
        model.add(Checks.expect("getFiles() before showing", 0, () -> load.getFiles().length));
        FileDialog save = new FileDialog(owner, "Save (showcase)", FileDialog.SAVE);
        save.setFile("showcase.txt");
        model.add(Checks.expect("SAVE mode", FileDialog.SAVE, save::getMode));
        model.add(Checks.expect("SAVE getFile()", "showcase.txt", save::getFile));
        model.add(Checks.expect("peer not created (never shown)", false, () -> load.isDisplayable() || save.isDisplayable()));
        load.dispose();
        save.dispose();
        model.add(Checks.info(DIRECTORIES, () -> String.valueOf(System.getProperty(DIRECTORIES))));

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("macOS file dialogs"));
        content.add(Ui.text("FileDialog is the Cocoa open or save panel on macOS. The panels need the user : they are only "
                + "shown with -Dshowcase.interactive=true (open with multiple selection and a .txt filter, open a "
                + "directory with apple.awt.fileDialogForDirectories=true, save).", 1000));
        content.add(ChecksView.table("FileDialog model", model, 330, ChecksView.WIDTH));
        if (MacDesktop.interactive()) {
            interactive = ChecksView.table("Interactive", List.of(Check.info("panels", "not shown yet")), 330,
                    ChecksView.WIDTH);
            java.awt.Button show = new java.awt.Button("Show the panels");
            show.addActionListener(e -> showPanels(directory));
            content.add(show);
            content.add(interactive);
        }
        return Ui.column(14, content.toArray(Component[]::new));
    }

    @Override
    public void dispose(Component content) {
        interactive = null;
    }

    /**
     * Shows the three panels one after the other (modal : the user closes them), and records their results.
     */
    private void showPanels(File directory) {
        List<Check> checks = new ArrayList<>();
        Frame owner = frameOf();
        FileDialog open = new FileDialog(owner, "Open .txt files (showcase)", FileDialog.LOAD);
        open.setMultipleMode(true);
        open.setDirectory(directory.getPath());
        open.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        open.setVisible(true);
        checks.add(Check.info("open : files", Arrays.stream(open.getFiles()).map(File::getName).toList()));
        open.dispose();
        String previous = System.getProperty(DIRECTORIES);
        System.setProperty(DIRECTORIES, "true");
        try {
            FileDialog folder = new FileDialog(owner, "Choose a directory (showcase)", FileDialog.LOAD);
            folder.setVisible(true);
            checks.add(Check.info("directory : file", String.valueOf(folder.getFile())));
            folder.dispose();
        } finally {
            if (previous == null) {
                System.clearProperty(DIRECTORIES);
            } else {
                System.setProperty(DIRECTORIES, previous);
            }
        }
        FileDialog save = new FileDialog(owner, "Save (showcase, nothing is written)", FileDialog.SAVE);
        save.setFile("showcase.txt");
        save.setVisible(true);
        checks.add(Check.info("save : file", String.valueOf(save.getFile())));
        save.dispose();
        ChecksView view = interactive;
        if (view != null) {
            view.setChecks(checks);
        }
    }

    private static Frame frameOf() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isShowing()) {
                return frame;
            }
        }
        return null;
    }
}
