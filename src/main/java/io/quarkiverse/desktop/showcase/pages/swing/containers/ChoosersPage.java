package io.quarkiverse.desktop.showcase.pages.swing.containers;

import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.captioned;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.column;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.icon;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.row;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.section;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.sized;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * {@link JColorChooser} (the five chooser panels : swatches clicked with a synthetic mouse press, HSV, HSL, RGB with the
 * color code field, CMYK ; {@code createDialog}) and {@link JFileChooser} over a generated temporary tree with fixed
 * names, sizes and dates : an open chooser in list view with a file filter and a multiple selection, a save chooser in
 * details view (on Windows the columns and values come from the Windows shell : {@code Win32ShellFolder2} COM calls and
 * JNI), a directories only chooser with a custom {@link FileView} and a {@link FileSystemView} restricted to the tree ;
 * and the {@link FileSystemView} values of the platform (roots, drives, shell display names and icons, computed in the
 * background).
 * <p>
 * The Metal file chooser requests the focus for its file name field when it is added to a window : the choosers are
 * made non focusable (the showcase never takes the focus from the user's application). Shell names, types and icons
 * depend on the machine : they are informational checks, compared between runs on the same machine.
 */
@Singleton
public class ChoosersPage implements FeaturePage {

    /** Last modification time of every generated file : 2024-03-15T10:30:00Z. */
    private static final long MODIFIED = 1_710_498_600_000L;
    private static final int COLOR = 0x3399CC;

    private State state;

    private static final class State {
        Path root;
        final List<JColorChooser> colorChoosers = new ArrayList<>();
        final List<String> colorEvents = new ArrayList<>();
        final List<String> fileEvents = new ArrayList<>();
        JFileChooser open;
        JFileChooser save;
        JFileChooser directories;
        JDialog colorDialog;
        BufferedImage colorDialogImage;
        SwingKit.CheckColumns colorTable;
        SwingKit.CheckColumns fileTable;
        SwingKit.CheckColumns fsvTable;
    }

    @Override
    public String id() {
        return "swing-choosers";
    }

    @Override
    public String title() {
        return "Color and file choosers";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 120;
    }

    @Override
    public Component build() throws IOException {
        State s = new State();
        state = s;
        s.root = tree();

        // --- file choosers
        File root = s.root.toFile();
        s.open = new JFileChooser(root);
        s.open.setDialogType(JFileChooser.OPEN_DIALOG);
        s.open.setMultiSelectionEnabled(true);
        FileNameExtensionFilter text = new FileNameExtensionFilter("Text files (txt, md, csv)", "txt", "md", "csv");
        s.open.addChoosableFileFilter(text);
        s.open.setFileFilter(text);
        s.open.setSelectedFiles(new File[] { new File(root, "notes.txt"), new File(root, "README.md") });
        s.open.setApproveButtonText("Import");
        s.open.setApproveButtonToolTipText("Import the selected files");
        s.open.addActionListener(e -> s.fileEvents.add("open " + e.getActionCommand()));

        s.save = new JFileChooser(root);
        s.save.setDialogType(JFileChooser.SAVE_DIALOG);
        s.save.setSelectedFile(new File(root, "data.csv"));
        JLabel accessory = new JLabel("accessory", icon(0x26A69A, SwingKit.DIAMOND, 24), JLabel.CENTER);
        accessory.setVerticalTextPosition(JLabel.BOTTOM);
        accessory.setHorizontalTextPosition(JLabel.CENTER);
        accessory.setPreferredSize(new Dimension(90, 60));
        s.save.setAccessory(accessory);
        s.save.addActionListener(e -> s.fileEvents.add("save " + e.getActionCommand()));

        s.directories = new JFileChooser(root, new TreeFileSystemView(root));
        s.directories.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        s.directories.setFileView(new UpperCaseFileView());
        s.directories.setControlButtonsAreShown(false);
        s.directories.setDialogTitle("Directories only");
        // no selected directory : in DIRECTORIES_ONLY mode its field shows the absolute path (the temporary directory)

        for (JFileChooser chooser : List.of(s.open, s.save, s.directories)) {
            // the default follows the desktop setting "show hidden files" (awt.file.showHiddenFiles)
            chooser.setFileHidingEnabled(true);
            SwingKit.unfocusable(chooser);
        }

        // --- color choosers
        String[] panels = { "Swatches", "HSV", "HSL", "RGB", "CMYK" };
        List<Component> colorViews = new ArrayList<>();
        for (int i = 0; i < panels.length; i++) {
            JColorChooser chooser = new JColorChooser(new Color(COLOR));
            if (i > 0) {
                chooser.setPreviewPanel(new JPanel());
            }
            JTabbedPane tabs = SwingKit.find(chooser, JTabbedPane.class);
            tabs.setSelectedIndex(i);
            String name = panels[i];
            chooser.getSelectionModel().addChangeListener(e -> s.colorEvents.add(name + " "
                    + Checks.argb(((ColorSelectionModel) e.getSource()).getSelectedColor().getRGB())));
            SwingKit.unfocusable(chooser);
            s.colorChoosers.add(chooser);
            colorViews.add(captioned(name + " panel", chooser));
        }
        s.colorDialog = JColorChooser.createDialog(null, "Pick a color", true, new JColorChooser(new Color(COLOR)),
                e -> s.colorEvents.add("dialog OK"), e -> s.colorEvents.add("dialog Cancel"));
        s.colorDialog.dispose();
        s.colorDialog.setUndecorated(true);
        s.colorDialog.getRootPane().setWindowDecorationStyle(JRootPane.COLOR_CHOOSER_DIALOG);
        s.colorDialog.pack();
        s.colorDialogImage = Snapshots.render(s.colorDialog.getRootPane());

        s.colorTable = SwingKit.pending("Color choosers");
        s.fileTable = SwingKit.pending("File choosers");
        s.fsvTable = SwingKit.pending("FileSystemView (platform)");
        return column(18,
                Ui.heading("Color and file choosers"),
                Ui.text("File choosers over a generated temporary tree (fixed names, sizes and dates, 2024-03-15 "
                        + "10:30 UTC shown in the local time zone), color choosers on #3399CC (HSV 200/75/80, HSL "
                        + "200/60/50, CMYK 75/25/0/20). The swatches chooser received a synthetic mouse press on a "
                        + "swatch. The color chooser dialog (createDialog, Metal decorations) is an extra image.",
                        SwingKit.WIDTH),
                section("JFileChooser", null, column(12,
                        row(16, captioned("OPEN, list view, filter, multiple selection, approve text Import",
                                sized(s.open, 506, 330)),
                                captioned("SAVE, details view, accessory", sized(s.save, 506, 330))),
                        row(16, captioned("DIRECTORIES_ONLY, custom FileView and FileSystemView, no control buttons",
                                sized(s.directories, 506, 260)),
                                Ui.text("The directories only chooser uses a FileSystemView restricted to the "
                                        + "generated tree (its only root) and a FileView showing names in upper case "
                                        + "with painted icons. The open chooser lists directories and the files "
                                        + "accepted by its filter ; the save chooser shows the details view (columns "
                                        + "of the platform shell folder).", 500)))),
                s.fileTable, s.fsvTable,
                section("JColorChooser", null, SwingKit.wrap(SwingKit.WIDTH, 16, colorViews)),
                s.colorTable);
    }

    /**
     * The generated tree (under the temporary directory of the process) : fixed names, contents and dates.
     */
    private static Path tree() throws IOException {
        Path root = Edt.tempDir().resolve("chooser-root");
        Files.createDirectories(root);
        List<Path> paths = new ArrayList<>();
        for (String dir : List.of("Archive", "Documents", "Pictures")) {
            paths.add(Files.createDirectories(root.resolve(dir)));
        }
        paths.add(file(root.resolve("README.md"), 1200));
        paths.add(file(root.resolve("notes.txt"), 2048));
        paths.add(file(root.resolve("data.csv"), 500));
        paths.add(file(root.resolve("photo.png"), 3100));
        paths.add(file(root.resolve("Documents/letter.txt"), 700));
        paths.add(file(root.resolve("Documents/budget.csv"), 900));
        Path hidden = file(root.resolve(".hidden"), 10);
        paths.add(hidden);
        if (Platforms.isWindows()) {
            Files.setAttribute(hidden, "dos:hidden", Boolean.TRUE);
        }
        for (Path path : paths) {
            Files.setLastModifiedTime(path, FileTime.fromMillis(MODIFIED));
        }
        Files.setLastModifiedTime(root, FileTime.fromMillis(MODIFIED));
        return root;
    }

    private static Path file(Path path, int size) throws IOException {
        byte[] bytes = new byte[size];
        byte[] line = ("line of " + path.getFileName() + "\n").getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < size; i++) {
            bytes[i] = line[i % line.length];
        }
        return Files.write(path, bytes);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        List<Check> fileChecks = new ArrayList<>();
        return Edt.rounds(2)
                .thenAccept(v -> {
                    JToggleButton details = SwingKit.find(s.save, JToggleButton.class,
                            b -> UIManager.getString("FileChooser.detailsViewButtonToolTipText").equals(b.getToolTipText()));
                    details.doClick(0);
                    SwingKit.unfocusable(s.save);
                    swatchPress(s);
                })
                // the choosers load their directories in the background
                .thenCompose(v -> Edt.until(() -> loaded(s), 15_000, "file chooser directories loaded"))
                .thenCompose(v -> Edt.rounds(3))
                .thenAccept(v -> {
                    // DIRECTORIES_ONLY : the Metal UI shows the absolute path of the current directory (the temporary
                    // directory, different on every run) in the folder name field ; type a relative name instead
                    SwingKit.find(s.directories, javax.swing.JTextField.class).setText("Documents");
                    fileChecks.addAll(fileChecks(s));
                    s.colorTable.setChecks(colorChecks(s));
                })
                .thenCompose(v -> Edt.background(() -> fileSystemViewChecks(s.root.toFile())))
                .thenAccept(checks -> {
                    s.fsvTable.setChecks(checks);
                    s.fileTable.setChecks(fileChecks);
                })
                .thenCompose(v -> Edt.rounds(2))
                .thenCompose(v -> Edt.stable(content, 3_000));
    }

    private static boolean loaded(State s) {
        JList<?> openList = SwingKit.find(s.open, JList.class, l -> l.getModel().getSize() > 0);
        JTable saveTable = SwingKit.find(s.save, JTable.class);
        JList<?> dirList = SwingKit.find(s.directories, JList.class, l -> l.getModel().getSize() > 0);
        return openList != null && openList.getModel().getSize() == 6 && saveTable != null
                && saveTable.getRowCount() == 7 && dirList != null && dirList.getModel().getSize() == 3;
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        Map<String, BufferedImage> extras = new LinkedHashMap<>();
        extras.put("color-dialog", state.colorDialogImage);
        return CompletableFuture.completedFuture(extras);
    }

    @Override
    public void dispose(Component content) {
        State s = state;
        state = null;
        if (s != null && s.colorDialog != null) {
            s.colorDialog.dispose();
        }
    }

    /**
     * A synthetic mouse press on the swatch at column 5, row 3 of the main swatches (swatches are 10x10, gap 1).
     */
    private static void swatchPress(State s) {
        JComponent main = SwingKit.find(s.colorChoosers.getFirst(), JComponent.class,
                c -> c.getClass().getSimpleName().equals("MainSwatchPanel"));
        int x = 5 * 11 + 5;
        int y = 3 * 11 + 5;
        main.dispatchEvent(new MouseEvent(main, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
        main.dispatchEvent(new MouseEvent(main, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static List<Check> colorChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JColorChooser swatches = s.colorChoosers.getFirst();
        checks.add(Checks.expect("panel names, mnemonics", "Swatches/83 HSV/72 HSL/76 RGB/71 CMYK/77", () -> {
            List<String> names = new ArrayList<>();
            for (AbstractColorChooserPanel panel : swatches.getChooserPanels()) {
                names.add(panel.getDisplayName() + "/" + panel.getMnemonic());
            }
            return String.join(" ", names);
        }));
        checks.add(Checks.expect("panel classes", "DefaultSwatchChooserPanel ColorChooserPanel ColorChooserPanel "
                + "ColorChooserPanel ColorChooserPanel", () -> {
                    List<String> names = new ArrayList<>();
                    for (AbstractColorChooserPanel panel : swatches.getChooserPanels()) {
                        names.add(panel.getClass().getSimpleName());
                    }
                    return String.join(" ", names);
                }));
        checks.add(Checks.expect("swatches: color after the synthetic press (swatch tool tip)",
                Checks.argb(0xFF000000 | swatchColor(swatches)), () -> Checks.argb(swatches.getColor().getRGB())));
        checks.add(Checks.info("color selection events", () -> String.join(", ", s.colorEvents)));
        checks.add(Checks.expect("UI delegate, preview panel", "javax.swing.plaf.basic.BasicColorChooserUI "
                + "DefaultPreviewPanel", () -> ui(swatches) + " " + swatches.getPreviewPanel().getClass().getSimpleName()));
        String[] panels = { "HSV", "HSL", "RGB", "CMYK" };
        // hue 200 is computed as 199.99... (float) and truncated ; CMYK components range over 0..255
        String[] expected = { "199 75 80 0 0", "199 60 50 0 0", "51 153 204 255 0", "191 63 0 50 255" };
        for (int i = 0; i < panels.length; i++) {
            JColorChooser chooser = s.colorChoosers.get(i + 1);
            String name = panels[i];
            checks.add(Checks.expect(name + ": spinner values", expected[i], () -> spinners(chooser, name)));
        }
        checks.add(Checks.expect("RGB: color code field", "3399CC", () -> {
            JTabbedPane tabs = SwingKit.find(s.colorChoosers.get(3), JTabbedPane.class);
            // the color code field is the formatted text field that is not the editor of a spinner
            return SwingKit.find(tabs.getSelectedComponent(), JFormattedTextField.class,
                    f -> javax.swing.SwingUtilities.getAncestorOfClass(JSpinner.class, f) == null).getText();
        }));
        checks.add(Checks.expect("HSV: setColor(#CC3333) updates the spinners", "0 75 80 0 0", () -> {
            JColorChooser chooser = s.colorChoosers.get(1);
            chooser.setColor(new Color(0xCC3333));
            String values = spinners(chooser, "HSV");
            chooser.setColor(new Color(COLOR));
            return values;
        }));
        checks.add(Checks.expect("de: Swatches / RGB / Reset (UIManager)", "Swatches / RGB / Zurücksetzen",
                () -> UIManager.getString("ColorChooser.swatchesNameText", Locale.GERMAN) + " / "
                        + UIManager.getString("ColorChooser.rgbNameText", Locale.GERMAN) + " / "
                        + UIManager.getString("ColorChooser.resetText", Locale.GERMAN)));
        checks.add(Checks.expect("dialog: buttons, style", "OK Cancel Reset 5", () -> {
            List<String> texts = new ArrayList<>();
            SwingKit.findAll(s.colorDialog.getContentPane(), JButton.class).stream()
                    .filter(b -> b.getText() != null && !b.getText().isEmpty() && b.getParent() != null
                            && !(b.getParent() instanceof JSpinner))
                    .forEach(b -> texts.add(b.getText()));
            return String.join(" ", texts.subList(Math.max(0, texts.size() - 3), texts.size())) + " "
                    + s.colorDialog.getRootPane().getWindowDecorationStyle();
        }));
        return checks;
    }

    /**
     * The color of the main swatch at column 5, row 3 (computed like {@code getColorForLocation}).
     */
    private static int swatchColor(JColorChooser chooser) {
        JComponent main = SwingKit.find(chooser, JComponent.class,
                c -> c.getClass().getSimpleName().equals("MainSwatchPanel"));
        java.awt.Point p = new java.awt.Point(5 * 11 + 5, 3 * 11 + 5);
        String tip = main.getToolTipText(new MouseEvent(main, MouseEvent.MOUSE_MOVED, 0, 0, p.x, p.y, 0, false));
        // the tool tip text is "r, g, b"
        String[] parts = tip.split(",\\s*");
        return (Integer.parseInt(parts[0].trim()) << 16) | (Integer.parseInt(parts[1].trim()) << 8)
                | Integer.parseInt(parts[2].trim());
    }

    private static String spinners(JColorChooser chooser, String panel) {
        JTabbedPane tabs = SwingKit.find(chooser, JTabbedPane.class);
        AbstractColorChooserPanel selected = SwingKit.find(tabs.getSelectedComponent(), AbstractColorChooserPanel.class);
        if (!panel.equals(selected.getDisplayName())) {
            throw new IllegalStateException("selected panel " + selected.getDisplayName());
        }
        List<String> values = new ArrayList<>();
        for (JSpinner spinner : SwingKit.findAll(selected, JSpinner.class)) {
            values.add(String.valueOf(spinner.getValue()));
        }
        return String.join(" ", values);
    }

    private static List<Check> fileChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JFileChooser open = s.open;
        checks.add(Checks.expect("open: current directory, dialog type, multi selection", "chooser-root 0 true",
                () -> open.getCurrentDirectory().getName() + " " + open.getDialogType() + " "
                        + open.isMultiSelectionEnabled()));
        checks.add(Checks.expect("open: list (filtered)", "Archive Documents Pictures data.csv notes.txt README.md",
                () -> listNames(SwingKit.find(open, JList.class, l -> l.getModel().getSize() > 0))));
        checks.add(Checks.expect("open: selected files", "notes.txt README.md",
                () -> String.join(" ", Arrays.stream(open.getSelectedFiles()).map(File::getName).toList())));
        checks.add(Checks.expect("open: filters", "All Files | Text files (txt, md, csv)", () -> {
            List<String> names = new ArrayList<>();
            for (FileFilter filter : open.getChoosableFileFilters()) {
                names.add(filter.getDescription());
            }
            return String.join(" | ", names);
        }));
        checks.add(Checks.expect("open: accept(photo.png), accept(Pictures)", "false true",
                () -> open.accept(new File(s.root.toFile(), "photo.png")) + " "
                        + open.accept(new File(s.root.toFile(), "Pictures"))));
        checks.add(Checks.expect("open: approve button text, tool tip", "Import Import the selected files",
                () -> open.getApproveButtonText() + " " + open.getApproveButtonToolTipText()));
        checks.add(Checks.expect("open: UI delegate", "javax.swing.plaf.metal.MetalFileChooserUI", () -> ui(open)));
        checks.add(Checks.expect("open: names through the UI file view", "notes.txt Documents",
                () -> open.getName(new File(s.root.toFile(), "notes.txt")) + " "
                        + open.getName(new File(s.root.toFile(), "Documents"))));
        checks.add(Checks.expect("open: icons of a file and a directory (Ocean GIF resources)", "16x20 16x18", () -> {
            Icon file = open.getIcon(new File(s.root.toFile(), "notes.txt"));
            Icon dir = open.getIcon(new File(s.root.toFile(), "Documents"));
            return file.getIconWidth() + "x" + file.getIconHeight() + " " + dir.getIconWidth() + "x" + dir.getIconHeight();
        }));
        checks.add(Checks.info("open: type description of notes.txt", () -> open.getTypeDescription(
                new File(s.root.toFile(), "notes.txt"))));
        checks.add(Checks.expect("open: approveSelection and cancelSelection events", "open ApproveSelection, open "
                + "CancelSelection", () -> {
                    open.approveSelection();
                    open.cancelSelection();
                    return String.join(", ", s.fileEvents);
                }));
        checks.add(Checks.expect("open: actions Go Up, New Folder", "true true",
                () -> (open.getActionMap().get("Go Up") != null) + " " + (open.getActionMap().get("New Folder") != null)));

        JFileChooser save = s.save;
        JTable table = SwingKit.find(save, JTable.class);
        checks.add(Checks.expect("save: dialog type, selected file, accessory", "1 data.csv true",
                () -> save.getDialogType() + " " + save.getSelectedFile().getName() + " " + (save.getAccessory() != null)));
        checks.add(Checks.expect("save: details rows", 7, table::getRowCount));
        checks.add(Checks.info("save: details columns", () -> {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < table.getColumnCount(); i++) {
                names.add(String.valueOf(table.getColumnModel().getColumn(i).getHeaderValue()));
            }
            return String.join(" | ", names);
        }));
        checks.add(Checks.info("save: details row of notes.txt", () -> {
            for (int row = 0; row < table.getRowCount(); row++) {
                Object name = table.getValueAt(row, 0);
                if (name instanceof File file && file.getName().equals("notes.txt")) {
                    List<String> cells = new ArrayList<>();
                    for (int col = 0; col < table.getColumnCount(); col++) {
                        cells.add(cell(table, row, col));
                    }
                    return String.join(" | ", cells);
                }
            }
            return "not found";
        }));
        checks.add(Checks.expect("save: approve button text", "Save",
                () -> save.getUI().getApproveButtonText(save)));

        JFileChooser dirs = s.directories;
        checks.add(Checks.expect("directories: list (custom file view names)", "ARCHIVE DOCUMENTS PICTURES",
                () -> listNames(SwingKit.find(dirs, JList.class, l -> l.getModel().getSize() > 0))));
        checks.add(Checks.expect("directories: selection mode, control buttons shown", "1 false",
                () -> dirs.getFileSelectionMode() + " " + dirs.getControlButtonsAreShown()));
        checks.add(Checks.expect("directories: changeToParentDirectory at the root", "chooser-root", () -> {
            dirs.changeToParentDirectory();
            return dirs.getCurrentDirectory().getName();
        }));
        checks.add(Checks.expect("directories: file system view", TreeFileSystemView.class.getSimpleName(),
                () -> dirs.getFileSystemView().getClass().getSimpleName()));
        checks.add(Checks.expect("de: Open / Save (UIManager)", "Öffnen / Speichern",
                () -> UIManager.getString("FileChooser.openButtonText", Locale.GERMAN) + " / "
                        + UIManager.getString("FileChooser.saveButtonText", Locale.GERMAN)));
        return checks;
    }

    private static String cell(JTable table, int row, int col) {
        Component c = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
        return c instanceof JLabel label ? label.getText() : String.valueOf(table.getValueAt(row, col));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String listNames(JList<?> list) {
        List<String> names = new ArrayList<>();
        JList raw = list;
        for (int i = 0; i < list.getModel().getSize(); i++) {
            Object value = list.getModel().getElementAt(i);
            Component c = raw.getCellRenderer().getListCellRendererComponent(raw, value, i, false, false);
            names.add(c instanceof JLabel label ? label.getText() : String.valueOf(value));
        }
        return String.join(" ", names);
    }

    /**
     * The platform {@link FileSystemView} (Windows : shell folders through COM and JNI), off the event dispatch thread.
     */
    private static List<Check> fileSystemViewChecks(File root) {
        FileSystemView fsv = FileSystemView.getFileSystemView();
        File notes = new File(root, "notes.txt");
        File documents = new File(root, "Documents");
        File drive = File.listRoots()[0];
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("class", Platforms.pick("javax.swing.filechooser.UnixFileSystemView",
                "javax.swing.filechooser.WindowsFileSystemView", "javax.swing.filechooser.UnixFileSystemView"),
                () -> fsv.getClass().getName()));
        checks.add(Checks.info("roots", () -> names(fsv, fsv.getRoots())));
        checks.add(Checks.info("home / default directory", () -> fsv.getSystemDisplayName(fsv.getHomeDirectory())
                + " / " + fsv.getSystemDisplayName(fsv.getDefaultDirectory())));
        checks.add(Checks.info("first file system root: display name", () -> fsv.getSystemDisplayName(drive)));
        checks.add(Checks.info("first root: drive, floppy, computer node, file system root", () -> fsv.isDrive(drive)
                + " " + fsv.isFloppyDrive(drive) + " " + fsv.isComputerNode(drive) + " " + fsv.isFileSystemRoot(drive)));
        checks.add(Checks.info("type description of notes.txt / Documents",
                () -> fsv.getSystemTypeDescription(notes) + " / " + fsv.getSystemTypeDescription(documents)));
        // Windows : the shell icons at the requested size. Linux : the shell folders have no icons, the file view icons
        // of the look and feel are returned (FileSystemView.getSystemIcon), whatever the size
        Check systemIcons = Checks.expect("system icon sizes (default, 32, 48)", Platforms.isWindows() ? "16x16 32x32 48x48"
                : size(UIManager.getIcon("FileView.fileIcon")) + " " + size(UIManager.getIcon("FileView.fileIcon")) + " "
                        + size(UIManager.getIcon("FileView.directoryIcon")),
                () -> size(fsv.getSystemIcon(notes)) + " " + size(fsv.getSystemIcon(notes, 32, 32)) + " "
                        + size(fsv.getSystemIcon(documents, 48, 48)));
        checks.add(Platforms.isMac() ? Check.info(systemIcons.name(), systemIcons.value()) : systemIcons);
        checks.add(Checks.expect("getFiles (hiding), sorted", "Archive data.csv Documents notes.txt photo.png Pictures "
                + "README.md", () -> sorted(fsv.getFiles(root, true))));
        checks.add(Checks.expect("getFiles (not hiding) count, .hidden hidden", "8 true",
                () -> fsv.getFiles(root, false).length + " " + fsv.isHiddenFile(new File(root, ".hidden"))));
        checks.add(Checks.expect("child, parent, isParent, traversable", "Documents chooser-root true true false",
                () -> fsv.getChild(root, "Documents").getName() + " " + fsv.getParentDirectory(notes).getName() + " "
                        + fsv.isParent(root, notes) + " " + fsv.isTraversable(documents) + " "
                        + fsv.isTraversable(notes)));
        checks.add(Checks.expect("isFileSystem, isLink, isRoot of the tree", "true false false",
                () -> fsv.isFileSystem(notes) + " " + fsv.isLink(notes) + " " + fsv.isRoot(root)));
        checks.add(Checks.info("createNewFolder in Archive (then deleted)", () -> {
            File archive = new File(root, "Archive");
            File folder = fsv.createNewFolder(archive);
            String name = folder.getName();
            Files.delete(folder.toPath());
            // the folder's date back (Linux file systems date the change of a directory's entries)
            Files.setLastModifiedTime(archive.toPath(), FileTime.fromMillis(MODIFIED));
            return name;
        }));
        // the combo box files also list the folders of the user's home : only their count and the first places
        checks.add(Checks.info("chooser combo box files (count, first 4)", () -> {
            File[] files = fsv.getChooserComboBoxFiles();
            String first = names(fsv, Arrays.copyOf(files, Math.min(4, files.length)));
            return files.length + first.substring(first.indexOf(':'));
        }));
        checks.add(Checks.info("chooser shortcut panel files", () -> names(fsv, fsv.getChooserShortcutPanelFiles())));
        return checks;
    }

    private static String size(Icon icon) {
        return icon == null ? "null" : icon.getIconWidth() + "x" + icon.getIconHeight();
    }

    private static String names(FileSystemView fsv, File[] files) {
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(fsv.getSystemDisplayName(file));
        }
        return files.length + ": " + String.join(", ", names);
    }

    private static String sorted(File[] files) {
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(file.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(" ", names);
    }

    /**
     * A file view showing directory names in upper case with a painted icon.
     */
    private static final class UpperCaseFileView extends FileView {

        @Override
        public String getName(File f) {
            return f.getName().toUpperCase(Locale.ROOT);
        }

        @Override
        public Icon getIcon(File f) {
            return f.isDirectory() ? icon(0xFFA726, SwingKit.SQUARE, 16) : null;
        }
    }

    /**
     * A file system view restricted to the generated tree : its root is the only root, nothing above it.
     */
    private static final class TreeFileSystemView extends FileSystemView {

        private final File root;

        TreeFileSystemView(File root) {
            this.root = canonical(root);
        }

        @Override
        public File createNewFolder(File containingDir) throws IOException {
            File folder = new File(containingDir, "New Folder");
            if (!folder.mkdir()) {
                throw new IOException("Cannot create " + folder.getName());
            }
            return folder;
        }

        @Override
        public File[] getRoots() {
            return new File[] { root };
        }

        @Override
        public boolean isRoot(File f) {
            return root.equals(canonical(f));
        }

        @Override
        public File getHomeDirectory() {
            return root;
        }

        @Override
        public File getDefaultDirectory() {
            return root;
        }

        @Override
        public File getParentDirectory(File dir) {
            return dir == null || root.equals(canonical(dir)) ? null : super.getParentDirectory(dir);
        }

        /**
         * The canonical form of a file (one name for a directory : Windows also has short 8.3 names), itself when it
         * cannot be computed.
         */
        private static File canonical(File f) {
            try {
                return f == null ? null : f.getCanonicalFile();
            } catch (IOException e) {
                return f;
            }
        }

        @Override
        public String getSystemDisplayName(File f) {
            return f.getName();
        }

        @Override
        public Icon getSystemIcon(File f) {
            return null;
        }

        @Override
        public File[] getChooserComboBoxFiles() {
            return new File[] { root };
        }
    }

}
