package io.quarkiverse.desktop.showcase.pages.swing.containers;

import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.captioned;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.column;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.icon;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.rect;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.section;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.size;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.sized;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * {@link JOptionPane} in every message type and option type, with custom options, a custom message array, input with
 * a text field, a combo box (fewer than 20 values) and a list (20 values or more), HTML and long wrapped messages,
 * localized button texts (German, Japanese, Simplified Chinese), each created with {@code createDialog}, decorated by the
 * look and feel ({@code JRootPane} window decoration styles, Metal title panes) and rendered with printAll without
 * being shown ; internal option panes ({@code createInternalFrame}) in a desktop pane of the page ; real windows : a
 * modeless option pane dialog answered with {@code doClick}, an application modal {@link JDialog} (its
 * {@code setVisible(true)} returns once it is hidden, events being pumped by a secondary loop meanwhile) and a
 * {@link JWindow}, all placed outside the main window, never focusable, rendered as extra images.
 * <p>
 * The localized texts come from the {@code com.sun.swing.internal.plaf.basic.resources.basic} bundles : a native
 * executable only has them for the locales it was built with ({@code quarkus.locales}).
 */
@Singleton
public class OptionPaneDialogsPage implements FeaturePage {

    private static final int WINDOW_X = 1460;

    private State state;

    private static final class State {
        final List<JDialog> dialogs = new ArrayList<>();
        final List<Check> paneChecks = new ArrayList<>();
        final Map<String, BufferedImage> extras = new LinkedHashMap<>();
        JOptionPane textInput;
        JOptionPane comboInput;
        JOptionPane listInput;
        JOptionPane customOptions;
        JDesktopPane desktop;
        final List<JInternalFrame> internalFrames = new ArrayList<>();
        JDialog shownOption;
        JOptionPane shownPane;
        JDialog modal;
        JWindow window;
        volatile boolean modalReturned;
        SwingKit.CheckColumns panesTable;
        SwingKit.CheckColumns windowsTable;
    }

    @Override
    public String id() {
        return "swing-option-pane-dialogs";
    }

    @Override
    public String title() {
        return "Option panes and dialogs";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 110;
    }

    @Override
    public Component build() {
        State s = new State();
        state = s;
        List<Check> checks = s.paneChecks;

        // --- message types (default option), decorated with the matching window decoration style
        int[] types = { JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.WARNING_MESSAGE,
                JOptionPane.QUESTION_MESSAGE, JOptionPane.PLAIN_MESSAGE };
        String[] typeNames = { "ERROR", "INFORMATION", "WARNING", "QUESTION", "PLAIN" };
        List<Component> messageTiles = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            JOptionPane pane = new JOptionPane(typeNames[i].charAt(0) + typeNames[i].substring(1).toLowerCase(Locale.ROOT)
                    + " message", types[i]);
            messageTiles.add(tile(s, typeNames[i], pane, typeNames[i] + "_MESSAGE"));
            String name = typeNames[i];
            checks.add(Checks.info(name + ": icon", () -> iconDescription(messageIcon(pane))));
        }
        checks.add(Checks.expect("ERROR: buttons", "OK", () -> buttons(firstPane(s, 0))));

        // --- option types and custom options
        List<Component> optionTiles = new ArrayList<>();
        JOptionPane yesNo = new JOptionPane("Overwrite the existing file?", JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_OPTION);
        optionTiles.add(tile(s, "YES_NO_OPTION", yesNo, "QUESTION"));
        JOptionPane yesNoCancel = new JOptionPane("Save the changes before closing?", JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_CANCEL_OPTION);
        optionTiles.add(tile(s, "YES_NO_CANCEL_OPTION", yesNoCancel, "QUESTION"));
        JOptionPane okCancel = new JOptionPane("Delete 3 files?", JOptionPane.WARNING_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        optionTiles.add(tile(s, "OK_CANCEL_OPTION", okCancel, "WARNING"));
        s.customOptions = new JOptionPane("The document was modified.", JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[] { "Save", "Don't Save", "Cancel" }, "Save");
        optionTiles.add(tile(s, "custom options, initial value Save", s.customOptions, "WARNING"));
        JCheckBox remember = new JCheckBox("Remember my choice", true);
        remember.setOpaque(false);
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(40);
        progress.setStringPainted(true);
        JOptionPane custom = new JOptionPane(new Object[] { "A message array :", remember, progress,
                icon(0x26A69A, SwingKit.DIAMOND, 24) }, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                icon(0x5C6BC0, SwingKit.CIRCLE, 32));
        optionTiles.add(tile(s, "message array, custom icon", custom, "PLAIN"));
        checks.add(Checks.expect("YES_NO: buttons", "Yes No", () -> buttons(yesNo)));
        checks.add(Checks.expect("YES_NO_CANCEL: buttons", "Yes No Cancel", () -> buttons(yesNoCancel)));
        checks.add(Checks.expect("OK_CANCEL: buttons", "OK Cancel", () -> buttons(okCancel)));
        checks.add(Checks.expect("custom options: buttons, initial value", "Save Don't Save Cancel / Save",
                () -> buttons(s.customOptions) + " / " + s.customOptions.getInitialValue()));
        checks.add(Checks.expect("message array: components found", "true true",
                () -> (SwingKit.find(custom, JCheckBox.class) != null) + " "
                        + (SwingKit.find(custom, JProgressBar.class) != null)));

        // --- input
        List<Component> inputTiles = new ArrayList<>();
        s.textInput = new JOptionPane("Your name :", JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        s.textInput.setWantsInput(true);
        s.textInput.setInitialSelectionValue("Duke");
        inputTiles.add(tile(s, "input : text field", s.textInput, "QUESTION"));
        s.comboInput = new JOptionPane("Toolkit :", JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        s.comboInput.setWantsInput(true);
        s.comboInput.setSelectionValues(new Object[] { "AWT", "Swing", "JavaFX", "SWT", "Compose" });
        s.comboInput.setInitialSelectionValue("Swing");
        inputTiles.add(tile(s, "input : 5 values (combo box)", s.comboInput, "QUESTION"));
        Object[] items = new Object[25];
        for (int i = 0; i < items.length; i++) {
            items[i] = String.format(Locale.ROOT, "Item %02d", i + 1);
        }
        s.listInput = new JOptionPane("Pick an item :", JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        s.listInput.setWantsInput(true);
        s.listInput.setSelectionValues(items);
        s.listInput.setInitialSelectionValue("Item 03");
        inputTiles.add(tile(s, "input : 25 values (list)", s.listInput, "QUESTION"));
        JOptionPane html = new JOptionPane("<html><b>HTML</b> message with <i>styles</i>,<br>a <u>line break</u> and "
                + "<font color=#1565C0>colors</font></html>", JOptionPane.INFORMATION_MESSAGE);
        inputTiles.add(tile(s, "HTML message", html, "INFORMATION"));
        JOptionPane wrapped = new JOptionPane("This long message is wrapped by the option pane because "
                + "getMaxCharactersPerLineCount is overridden to return 28 characters per line.",
                JOptionPane.INFORMATION_MESSAGE) {
            @Override
            public int getMaxCharactersPerLineCount() {
                return 28;
            }
        };
        inputTiles.add(tile(s, "wrapped at 28 characters", wrapped, "INFORMATION"));
        checks.add(Checks.expect("input text: input component, initial text", "MultiplexingTextField Duke", () -> {
            JTextField field = SwingKit.find(s.textInput, JTextField.class);
            return field.getClass().getSimpleName() + " " + field.getText();
        }));
        checks.add(Checks.expect("input combo: input component, selected", "JComboBox Swing", () -> {
            JComboBox<?> combo = SwingKit.find(s.comboInput, JComboBox.class);
            return combo.getClass().getSimpleName() + " " + combo.getSelectedItem();
        }));
        checks.add(Checks.expect("input list: input component, selected", "JList Item 03", () -> {
            JList<?> list = SwingKit.find(s.listInput, JList.class);
            return list.getClass().getSimpleName() + " " + list.getSelectedValue();
        }));
        checks.add(Checks.expect("wrapped: message lines", 5, () -> SwingKit.findAll(wrapped, JLabel.class).stream()
                .filter(l -> l.getText() != null && !l.getText().isEmpty()).count()));

        // --- locales
        List<Component> localeTiles = new ArrayList<>();
        Locale[] locales = { Locale.GERMAN, Locale.JAPANESE, Locale.SIMPLIFIED_CHINESE };
        for (Locale locale : locales) {
            JOptionPane pane = new JOptionPane(locale.getDisplayLanguage(locale) + " (" + locale + ")",
                    JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION);
            pane.setLocale(locale);
            pane.updateUI();
            localeTiles.add(tile(s, "YES_NO_CANCEL, locale " + locale, pane, "QUESTION"));
            checks.add(Checks.info(locale + ": buttons", () -> buttons(pane)));
        }
        checks.add(Checks.expect("de: Yes No Cancel OK (UIManager)", "Ja Nein Abbrechen OK",
                () -> localized(Locale.GERMAN)));
        checks.add(Checks.expect("ja: Yes No Cancel OK (UIManager)", "はい(Y) いいえ(N) "
                + "取消 OK", () -> localized(Locale.JAPANESE)));
        checks.add(Checks.expect("zh_CN: Yes No Cancel OK (UIManager)", "是(Y) 否(N) 取消 确定",
                () -> localized(Locale.SIMPLIFIED_CHINESE)));
        checks.add(Checks.expect("en: title and input title (UIManager)", "Select an Option / Input",
                () -> UIManager.getString("OptionPane.titleText", Locale.ENGLISH) + " / "
                        + UIManager.getString("OptionPane.inputDialogTitle", Locale.ENGLISH)));

        // --- decoration styles
        List<Component> styleTiles = new ArrayList<>();
        int[] styles = { JRootPane.FRAME, JRootPane.PLAIN_DIALOG, JRootPane.INFORMATION_DIALOG, JRootPane.ERROR_DIALOG,
                JRootPane.COLOR_CHOOSER_DIALOG, JRootPane.FILE_CHOOSER_DIALOG, JRootPane.QUESTION_DIALOG,
                JRootPane.WARNING_DIALOG };
        String[] styleNames = { "FRAME", "PLAIN_DIALOG", "INFORMATION_DIALOG", "ERROR_DIALOG", "COLOR_CHOOSER_DIALOG",
                "FILE_CHOOSER_DIALOG", "QUESTION_DIALOG", "WARNING_DIALOG" };
        for (int i = 0; i < styles.length; i++) {
            JDialog dialog = new JDialog((Dialog) null, styleNames[i], false);
            dialog.setUndecorated(true);
            dialog.getRootPane().setWindowDecorationStyle(styles[i]);
            JLabel label = new JLabel(styleNames[i].toLowerCase(Locale.ROOT).replace('_', ' '), JLabel.CENTER);
            label.setPreferredSize(new java.awt.Dimension(220, 34));
            dialog.getContentPane().add(label);
            dialog.pack();
            s.dialogs.add(dialog);
            styleTiles.add(Ui.image(Snapshots.render(dialog.getRootPane())));
        }
        checks.add(Checks.expect("decorated root pane: title pane class", "javax.swing.plaf.metal.MetalTitlePane",
                () -> titlePane(s.dialogs.get(s.dialogs.size() - 1).getRootPane()).getClass().getName()));
        checks.add(Checks.expect("decorated root pane: border (WARNING_DIALOG)",
                "javax.swing.plaf.metal.MetalBorders$WarningDialogBorder",
                () -> s.dialogs.get(s.dialogs.size() - 1).getRootPane().getBorder().getClass().getName()));
        checks.add(Checks.expect("look and feel supports window decorations, default decorated", "true false",
                () -> UIManager.getLookAndFeel().getSupportsWindowDecorations() + " "
                        + JDialog.isDefaultLookAndFeelDecorated()));

        // --- internal option panes
        JDesktopPane desktop = new JDesktopPane();
        desktop.setBackground(new Color(0xDDE6EE));
        desktop.setSize(1000, 210);
        s.desktop = sized(desktop, 1000, 210);
        JOptionPane internalQuestion = new JOptionPane("Internal question ?", JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_OPTION);
        JOptionPane internalError = new JOptionPane("Internal error message", JOptionPane.ERROR_MESSAGE);
        JOptionPane internalInput = new JOptionPane("Internal input :", JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        internalInput.setWantsInput(true);
        internalInput.setInitialSelectionValue("value");
        JOptionPane[] internals = { internalQuestion, internalError, internalInput };
        String[] titles = { "Question", "Error", "Input" };
        int x = 20;
        for (int i = 0; i < internals.length; i++) {
            JInternalFrame frame = internals[i].createInternalFrame(desktop, titles[i]);
            frame.setLocation(x, 20);
            x += frame.getWidth() + 30;
            SwingKit.unfocusable(frame);
            // not showing yet : no selection, no focus request
            frame.setVisible(true);
            s.internalFrames.add(frame);
        }
        checks.add(Checks.expect("internal: frames, layer, frame type", "3 200 optionDialog",
                () -> desktop.getAllFrames().length + " " + s.internalFrames.getFirst().getLayer() + " "
                        + s.internalFrames.getFirst().getClientProperty("JInternalFrame.frameType")));
        checks.add(Checks.expect("internal: closable, iconifiable, border", "false false "
                + "javax.swing.plaf.metal.MetalBorders$OptionDialogBorder", () -> {
                    JInternalFrame f = s.internalFrames.get(1);
                    return f.isClosable() + " " + f.isIconifiable() + " " + f.getBorder().getClass().getName();
                }));
        checks.add(Checks.info("internal: frame sizes", () -> {
            List<String> sizes = new ArrayList<>();
            s.internalFrames.forEach(f -> sizes.add(size(f.getSize())));
            return String.join(" ", sizes);
        }));
        checks.add(Checks.expect("getDesktopPaneForComponent", true,
                () -> JOptionPane.getDesktopPaneForComponent(internalQuestion) == desktop));

        s.panesTable = SwingKit.pending("Option panes");
        s.windowsTable = SwingKit.pending("Answers, real windows and modality");
        return column(18,
                Ui.heading("Option panes and dialogs"),
                Ui.text("Each JOptionPane is created with createDialog, decorated by the look and feel (JRootPane "
                        + "window decoration style of its message type) and rendered with printAll without being shown. "
                        + "The real windows (a modeless option pane dialog, a modal JDialog, a JWindow) are shown "
                        + "outside the main window and rendered as extra images.", SwingKit.WIDTH),
                section("Message types", null, SwingKit.wrap(SwingKit.WIDTH, 16, messageTiles)),
                section("Option types, custom options and messages", null, SwingKit.wrap(SwingKit.WIDTH, 16, optionTiles)),
                section("Input, HTML and wrapped messages", null, SwingKit.wrap(SwingKit.WIDTH, 16, inputTiles)),
                section("Localized buttons", "The locale of each option pane is set before its UI is installed.",
                        SwingKit.wrap(SwingKit.WIDTH, 16, localeTiles)),
                section("JRootPane window decoration styles (Metal title panes)", null,
                        SwingKit.wrap(SwingKit.WIDTH, 12, styleTiles)),
                section("Internal option panes (createInternalFrame)", null, s.desktop),
                s.panesTable, s.windowsTable);
    }

    /**
     * A tile : {@code pane} in a dialog created by {@code createDialog}, decorated by the look and feel with the window
     * decoration style of its message type, packed (displayable, never shown) and rendered.
     */
    private static Component tile(State s, String caption, JOptionPane pane, String title) {
        JDialog dialog = pane.createDialog(null, title);
        int style = switch (pane.getMessageType()) {
            case JOptionPane.ERROR_MESSAGE -> JRootPane.ERROR_DIALOG;
            case JOptionPane.INFORMATION_MESSAGE -> JRootPane.INFORMATION_DIALOG;
            case JOptionPane.WARNING_MESSAGE -> JRootPane.WARNING_DIALOG;
            case JOptionPane.QUESTION_MESSAGE -> JRootPane.QUESTION_DIALOG;
            default -> JRootPane.PLAIN_DIALOG;
        };
        // window decorations can only change while the dialog is not displayable
        dialog.dispose();
        dialog.setUndecorated(true);
        dialog.getRootPane().setWindowDecorationStyle(style);
        dialog.pack();
        s.dialogs.add(dialog);
        return captioned(caption, Ui.image(Snapshots.render(dialog.getRootPane())));
    }

    private static JOptionPane firstPane(State s, int index) {
        return SwingKit.find(s.dialogs.get(index).getRootPane(), JOptionPane.class);
    }

    private static Icon messageIcon(JOptionPane pane) {
        return switch (pane.getMessageType()) {
            case JOptionPane.ERROR_MESSAGE -> UIManager.getIcon("OptionPane.errorIcon");
            case JOptionPane.INFORMATION_MESSAGE -> UIManager.getIcon("OptionPane.informationIcon");
            case JOptionPane.WARNING_MESSAGE -> UIManager.getIcon("OptionPane.warningIcon");
            case JOptionPane.QUESTION_MESSAGE -> UIManager.getIcon("OptionPane.questionIcon");
            default -> null;
        };
    }

    private static String iconDescription(Icon icon) {
        return icon == null ? "none" : icon.getClass().getName() + " " + icon.getIconWidth() + "x" + icon.getIconHeight();
    }

    private static String buttons(JOptionPane pane) {
        List<String> texts = new ArrayList<>();
        for (JButton button : SwingKit.findAll(pane, JButton.class)) {
            // buttons of the option pane only (not the arrow button of an input combo box)
            if (button.getText() != null && !button.getText().isEmpty()) {
                texts.add(button.getText());
            }
        }
        return String.join(" ", texts);
    }

    private static JButton button(JOptionPane pane, String text) {
        return SwingKit.find(pane, JButton.class, b -> text.equals(b.getText()));
    }

    private static String localized(Locale locale) {
        return UIManager.getString("OptionPane.yesButtonText", locale) + " "
                + UIManager.getString("OptionPane.noButtonText", locale) + " "
                + UIManager.getString("OptionPane.cancelButtonText", locale) + " "
                + UIManager.getString("OptionPane.okButtonText", locale);
    }

    private static Component titlePane(JRootPane root) {
        for (Component c : root.getLayeredPane().getComponents()) {
            if (c.getClass().getName().endsWith("TitlePane")) {
                return c;
            }
        }
        throw new IllegalStateException("no title pane");
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        Window owner = SwingUtilities.getWindowAncestor(content);
        List<Check> checks = new ArrayList<>();
        return Edt.rounds(2)
                .thenAccept(v -> {
                    try {
                        // the first internal option pane is the active one (its content is not focusable)
                        s.internalFrames.getFirst().setSelected(true);
                    } catch (java.beans.PropertyVetoException e) {
                        throw new IllegalStateException(e);
                    }
                    checks.add(Checks.expect("internal: selected frame", "Question",
                            () -> s.desktop.getSelectedFrame().getTitle()));
                    answers(s, checks);
                    showOptionDialog(s, owner, checks);
                    showWindow(s, checks);
                    s.modal = modalDialog(owner);
                    checks.add(Checks.expect("modal: modal, modality type, focusable window state",
                            "true APPLICATION_MODAL false", () -> s.modal.isModal() + " " + s.modal.getModalityType()
                                    + " " + s.modal.getFocusableWindowState()));
                    // setVisible(true) of a modal dialog only returns once the dialog is hidden : called later
                    EventQueue.invokeLater(() -> {
                        s.modal.setVisible(true);
                        s.modalReturned = true;
                    });
                })
                .thenCompose(v -> Edt.until(() -> s.modal.isShowing(), 10_000, "modal dialog shown"))
                .thenCompose(v -> Edt.rounds(2))
                .thenAccept(v -> {
                    checks.add(Checks.expect("modal: showing, setVisible returned", "true false",
                            () -> s.modal.isShowing() + " " + s.modalReturned));
                    s.extras.put("modal-dialog", Snapshots.render(s.modal.getRootPane()));
                    s.modal.setVisible(false);
                })
                .thenCompose(v -> Edt.until(() -> s.modalReturned, 10_000, "modal setVisible(true) returned"))
                .thenAccept(v -> {
                    checks.add(Checks.expect("modal: hidden, setVisible returned", "false true",
                            () -> s.modal.isShowing() + " " + s.modalReturned));
                    s.panesTable.setChecks(s.paneChecks);
                    s.windowsTable.setChecks(checks);
                })
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(state.extras));
    }

    @Override
    public void dispose(Component content) {
        State s = state;
        state = null;
        if (s == null) {
            return;
        }
        if (s.modal != null) {
            s.modal.setVisible(false);
            s.modal.dispose();
        }
        if (s.shownOption != null) {
            s.shownOption.dispose();
        }
        if (s.window != null) {
            s.window.dispose();
        }
        s.dialogs.forEach(Window::dispose);
    }

    /**
     * Answers the input panes and the custom options pane programmatically (input set, then a button clicked).
     */
    private static void answers(State s, List<Check> checks) {
        checks.add(Checks.expect("text input: typed Quarkus, OK", "Quarkus 0", () -> {
            SwingKit.find(s.textInput, JTextField.class).setText("Quarkus");
            button(s.textInput, "OK").doClick(0);
            return s.textInput.getInputValue() + " " + s.textInput.getValue();
        }));
        checks.add(Checks.expect("combo input: AWT selected, OK", "AWT 0", () -> {
            SwingKit.find(s.comboInput, JComboBox.class).setSelectedItem("AWT");
            button(s.comboInput, "OK").doClick(0);
            return s.comboInput.getInputValue() + " " + s.comboInput.getValue();
        }));
        checks.add(Checks.expect("list input: Item 13 selected, Cancel", "uninitializedValue 2", () -> {
            JList<?> list = SwingKit.find(s.listInput, JList.class);
            list.setSelectedIndex(12);
            button(s.listInput, "Cancel").doClick(0);
            return s.listInput.getInputValue() + " " + s.listInput.getValue();
        }));
        checks.add(Checks.expect("custom options: Don't Save clicked", "Don't Save", () -> {
            button(s.customOptions, "Don't Save").doClick(0);
            return s.customOptions.getValue();
        }));
        checks.add(Checks.expect("shared owner frame (null parent)", "javax.swing.SwingUtilities$SharedOwnerFrame",
                () -> JOptionPane.getFrameForComponent(null).getClass().getName()));
        checks.add(Checks.expect("option pane UI, layout", "javax.swing.plaf.basic.BasicOptionPaneUI "
                + "javax.swing.BoxLayout", () -> ui(s.textInput) + " " + s.textInput.getLayout().getClass().getName()));
        checks.add(Checks.expect("option pane actions (loadActionMap) : close", true,
                () -> s.textInput.getActionMap().get("close") != null));
    }

    /**
     * A modeless option pane dialog shown outside the main window, answered with doClick : the option pane hides its
     * dialog once a value is set.
     */
    private static void showOptionDialog(State s, Window owner, List<Check> checks) {
        s.shownPane = new JOptionPane("Replace the file?", JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION);
        s.shownOption = s.shownPane.createDialog(owner, "Modeless option pane");
        JDialog dialog = s.shownOption;
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        passive(dialog);
        dialog.setLocation(WINDOW_X, 60);
        dialog.setVisible(true);
        checks.add(Checks.expect("option dialog: showing, modal, resizable", "true false false",
                () -> dialog.isShowing() + " " + dialog.isModal() + " " + dialog.isResizable()));
        checks.add(Checks.expect("option dialog: title, owner", "Modeless option pane true",
                () -> dialog.getTitle() + " " + (dialog.getOwner() == owner)));
        checks.add(Checks.info("option dialog: content size", () -> size(dialog.getContentPane().getSize())));
        s.extras.put("option-dialog", Snapshots.render(dialog.getRootPane()));
        checks.add(Checks.expect("option dialog: No clicked, value, dialog hidden", "1 false", () -> {
            button(s.shownPane, "No").doClick(0);
            return s.shownPane.getValue() + " " + dialog.isVisible();
        }));
    }

    private static void showWindow(State s, List<Check> checks) {
        JWindow window = new JWindow();
        passive(window);
        JPanel content = new JPanel(new BorderLayout(8, 8)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setPaint(new GradientPaint(0, 0, new Color(0xE3F2FD), 0, getHeight(), new Color(0x90CAF9)));
                    g.fillRect(0, 0, getWidth(), getHeight());
                } finally {
                    g.dispose();
                }
            }
        };
        content.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x1565C0), 2),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel label = new JLabel("JWindow : undecorated, not focusable", icon(0x1565C0, SwingKit.SQUARE, 24),
                JLabel.LEFT);
        content.add(label, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(new JButton("Dismiss"));
        content.add(buttons, BorderLayout.SOUTH);
        window.setContentPane(content);
        // located before pack creates the X window : a new window starts in the upper left corner of the screen area
        // outside the insets (java.awt.Window : 0,24 below the tray of the Linux image). Packed there, then moved, the
        // X window was resized at 0,24 and then moved : X11 reports both in ConfigureNotify events that the peer of a
        // JWindow applies to its bounds from the toolkit thread, and the bounds read 0,24 again after setLocation until
        // the second one came (openbox keeps the location that the window asks for)
        window.setLocation(WINDOW_X, 320);
        window.pack();
        window.setVisible(true);
        s.window = window;
        checks.add(Checks.expect("JWindow: showing, focusable window state, type", "true false NORMAL",
                () -> window.isShowing() + " " + window.getFocusableWindowState() + " " + window.getType()));
        checks.add(Checks.expect("JWindow: owner", "javax.swing.SwingUtilities$SharedOwnerFrame",
                () -> window.getOwner().getClass().getName()));
        checks.add(Checks.info("JWindow: bounds", () -> rect(window.getBounds())));
        s.extras.put("jwindow", Snapshots.render(window.getRootPane()));
    }

    private static JDialog modalDialog(Window owner) {
        JDialog dialog = new JDialog(owner, "Modal JDialog", Dialog.ModalityType.APPLICATION_MODAL);
        passive(dialog);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        panel.add(new JLabel("An application modal dialog : the main window is blocked while it is shown.",
                UIManager.getIcon("OptionPane.informationIcon"), JLabel.LEFT), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton ok = new JButton("OK");
        buttons.add(ok);
        buttons.add(new JButton("Cancel"));
        panel.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.pack();
        dialog.setLocation(WINDOW_X, 480);
        return dialog;
    }

    /**
     * A window that never takes the focus when shown (the showcase never steals the focus of the user's application).
     */
    private static void passive(Window window) {
        window.setAutoRequestFocus(false);
        window.setFocusableWindowState(false);
    }
}
