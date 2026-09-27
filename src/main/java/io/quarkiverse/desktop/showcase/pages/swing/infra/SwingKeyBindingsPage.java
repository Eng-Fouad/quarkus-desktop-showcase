package io.quarkiverse.desktop.showcase.pages.swing.infra;

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JEditorPane;
import javax.swing.JFormattedTextField;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.InputMapUIResource;
import javax.swing.table.JTableHeader;
import javax.swing.tree.DefaultMutableTreeNode;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Focus;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Key bindings : {@link InputMap} / {@link ActionMap} at the three conditions ({@code WHEN_FOCUSED},
 * {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}, {@code WHEN_IN_FOCUSED_WINDOW} with {@link ComponentInputMap}), custom
 * maps parented to the look and feel maps, mnemonics and menu accelerators ({@code KeyboardManager}),
 * {@link KeyEventDispatcher} / {@link KeyEventPostProcessor}, the look and feel bindings of 30 components (the
 * action maps of 21 UI classes are loaded lazily through reflection : {@code LazyActionMap} calls their
 * {@code loadActionMap} methods), {@link KeyStroke#getKeyStroke(String)} (reflection on the {@code VK_} fields of
 * {@link KeyEvent}, also used by every look and feel input map)
 * and its {@code toString()} round trip, {@link KeyEvent#getKeyText(int)} (bundle {@code sun.awt.resources.awt}),
 * extended key codes.
 * <p>
 * Synthetic key events are dispatched with {@code Component.dispatchEvent} (never dropped, no focus needed). The page
 * needs the focus for two parts : a label mnemonic moving the focus, and real key presses typed with {@link Robot}
 * into a text field (native key events : the key code, extended key code and location fields are set from native
 * code). Each Robot key press is sent only while one of the showcase windows is focused.
 */
@Singleton
public class SwingKeyBindingsPage implements FeaturePage {

    private static final int CTRL_SHIFT = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;

    /** Keys typed with Robot into the Robot field (letters and digits on any Latin keyboard layout). */
    private static final int[] ROBOT_KEYS = { KeyEvent.VK_Q, KeyEvent.VK_U, KeyEvent.VK_A, KeyEvent.VK_R,
            KeyEvent.VK_K, KeyEvent.VK_U, KeyEvent.VK_S, KeyEvent.VK_SPACE, KeyEvent.VK_2, KeyEvent.VK_5 };

    /** KeyStroke.getKeyStroke(String) inputs and the expected toString() (null : invalid). */
    private static final String[][] STROKES = {
            { "INSERT", "pressed INSERT" },
            { "control DELETE", "ctrl pressed DELETE" },
            { "alt shift X", "shift alt pressed X" },
            { "alt shift released X", "shift alt released X" },
            { "typed a", "typed a" },
            { "typed A", "typed A" },
            { "typed ~", "typed ~" },
            { "ctrl shift F10", "shift ctrl pressed F10" },
            { "meta BACK_SPACE", "meta pressed BACK_SPACE" },
            { "altGraph E", "altGraph pressed E" },
            { "shift ENTER", "shift pressed ENTER" },
            { "released ESCAPE", "released ESCAPE" },
            { "released SPACE", "released SPACE" },
            { "ctrl ADD", "ctrl pressed ADD" },
            { "NUMPAD5", "pressed NUMPAD5" },
            { "shift ctrl alt meta F24", "shift ctrl meta alt pressed F24" },
            { "button1 shift typed x", "shift button1 typed x" },
            { "ctrl shift typed a", "shift ctrl typed a" },
            { "ctrl PAGE_DOWN", "ctrl pressed PAGE_DOWN" },
            { "HOME", "pressed HOME" },
            { "ctrl EURO_SIGN", "ctrl pressed EURO_SIGN" },
            { "COMMA", "pressed COMMA" },
            { "ctrl OPEN_BRACKET", "ctrl pressed OPEN_BRACKET" },
            { "control shift pressed Z", "shift ctrl pressed Z" },
            { "F1", "pressed F1" },
            { "shift KP_LEFT", "shift pressed KP_LEFT" },
            { "alt BACK_QUOTE", "alt pressed BACK_QUOTE" },
            { "ctrl DEAD_ACUTE", "ctrl pressed DEAD_ACUTE" },
            { "ctrl 1", "ctrl pressed 1" },
            { "CONTEXT_MENU", "pressed CONTEXT_MENU" },
            { "WINDOWS", "pressed WINDOWS" },
            { "ctrl alt DELETE", "ctrl alt pressed DELETE" },
            { "shift", null },
            { "ctrl   shift    A", "shift ctrl pressed A" },
            { "typed SPACE", null },
            { "ctrl foo", null },
            { "Alt X", null },
            { "", null },
    };

    // per build state
    private Demo ui;
    private ChecksView dispatchView;
    private ChecksView focusView;
    private JTextArea logArea;
    private KeyEventDispatcher dispatcher;
    private KeyEventPostProcessor postProcessor;

    /** The components of one build. */
    private static final class Demo {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        JPanel content;
        JPanel form;
        JTextField nameField;
        JTextField otherField;
        JTextField robotField;
        JLabel nameLabel;
        JLabel shortcuts;
        JButton keep;
        JCheckBox bold;
        JPanel pad;
        JList<String> list;
        JTable table;
        JTree tree;
        JSlider slider;
        JSpinner spinner;
        JTabbedPane tabs;
        JTextArea area;
        final AtomicBoolean robotFieldFocused = new AtomicBoolean();
        final List<KeyEvent> robotPressed = Collections.synchronizedList(new ArrayList<>());
        final StringBuffer robotTyped = new StringBuffer();
    }

    @Override
    public String id() {
        return "swing-keybindings";
    }

    @Override
    public String title() {
        return "Key bindings";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 920;
    }

    @Override
    public boolean needsFocus() {
        return true;
    }

    @Override
    public Component build() {
        Demo u = new Demo();
        ui = u;
        JPanel demo = demo(u);
        logArea = InfraSupport.log(List.of("(synthetic key events are dispatched once the page is shown)"), 58, 30);
        dispatchView = ChecksView.table("Synthetic key events (Component.dispatchEvent)",
                List.of(Check.info("dispatch", "pending")));
        focusView = ChecksView.table("Focus and Robot (real key events)", List.of(Check.info("robot", "pending")));
        u.content = InfraSupport.column(14,
                Ui.text("Key bindings of Swing : the demo components on the left receive synthetic key events (shown "
                        + "in the log on the right), then a label mnemonic moves the focus and Robot types into the "
                        + "Robot field (only while a showcase window is focused). The tables check the KeyStroke "
                        + "grammar, key names and the look and feel bindings.", 1000),
                InfraSupport.row(14, InfraSupport.titled("Components", 480, demo),
                        InfraSupport.titled("Event log", 534, logScroll(logArea))),
                dispatchView,
                focusView,
                ChecksView.table("KeyStroke.getKeyStroke(String) and toString() round trip", strokeChecks()),
                InfraSupport.row(12,
                        ChecksView.table("Key names, modifiers, extended key codes", keyNameChecks(), 250,
                                InfraSupport.HALF),
                        ChecksView.table("Look and feel bindings (LazyActionMap)", lafChecks(), 170,
                                InfraSupport.HALF)));
        return u.content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Demo u = ui;
        ChecksView dispatchChecks = dispatchView;
        ChecksView focusChecks = focusView;
        JTextArea area = logArea;
        List<Check> focus = new ArrayList<>();
        return Edt.rounds(2)
                .thenAccept(v -> {
                    dispatchChecks.setChecks(synthetic(u));
                    area.setText(String.join("\n", u.log));
                })
                .thenCompose(v -> focusPhase(u, focus))
                .handle((v, error) -> {
                    if (error != null) {
                        focus.add(Check.fail("focus phase", Checks.describe(error)));
                    }
                    focusChecks.setChecks(focus);
                    area.setText(String.join("\n", u.log));
                    u.content.revalidate();
                    return null;
                });
    }

    @Override
    public void dispose(Component content) {
        removeDispatchers();
        ui = null;
        dispatchView = null;
        focusView = null;
        logArea = null;
    }

    private void removeDispatchers() {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (dispatcher != null) {
            kfm.removeKeyEventDispatcher(dispatcher);
            dispatcher = null;
        }
        if (postProcessor != null) {
            kfm.removeKeyEventPostProcessor(postProcessor);
            postProcessor = null;
        }
    }

    // ------------------------------------------------------------------------------------------------ demo

    private static Action action(Demo u, String name, String logLine) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                u.log.add(logLine);
            }
        };
    }

    private static JPanel demo(Demo u) {
        // menu bar with accelerators (fired through KeyboardManager, the menus are never opened)
        JMenuItem newItem = new JMenuItem(action(u, "New", "menu New (accelerator)"));
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, CTRL_SHIFT));
        JMenuItem openItem = new JMenuItem(action(u, "Open", "menu Open (accelerator)"));
        openItem.setAccelerator(KeyStroke.getKeyStroke("ctrl shift P"));
        JMenuItem exportItem = new JMenuItem(action(u, "Export", "menu Export (disabled, must not fire)"));
        exportItem.setAccelerator(KeyStroke.getKeyStroke("ctrl shift E"));
        exportItem.setEnabled(false);
        JMenu file = new JMenu("File");
        file.add(newItem);
        file.add(openItem);
        file.add(exportItem);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(file);
        menuBar.add(new JMenu("Edit"));

        u.form = new JPanel(new GridBagLayout());
        u.form.setOpaque(false);
        u.nameLabel = new JLabel("Name:");
        u.nameLabel.setDisplayedMnemonic(KeyEvent.VK_M);
        u.nameField = new JTextField("duke", 12);
        u.nameField.setName("field");
        u.otherField = new JTextField("labelFor of Name", 12);
        u.robotField = new JTextField(12);
        u.nameLabel.setLabelFor(u.otherField);
        u.otherField.addActionListener(e -> u.log.add("other field ActionListener " + e.getActionCommand()));
        addRow(u.form, 0, u.nameLabel, u.nameField);
        addRow(u.form, 1, new JLabel("Other:"), u.otherField);
        addRow(u.form, 2, new JLabel("Robot:"), u.robotField);

        // WHEN_FOCUSED : a custom map whose parent is the look and feel map
        InputMap custom = new InputMap();
        custom.setParent(u.nameField.getInputMap(JComponent.WHEN_FOCUSED));
        custom.put(KeyStroke.getKeyStroke("F2"), "field-f2");
        custom.put(KeyStroke.getKeyStroke("ctrl shift U"), "upper-case");
        custom.put(KeyStroke.getKeyStroke("F6"), "field-f6");
        custom.put(KeyStroke.getKeyStroke("F7"), "none");
        custom.put(KeyStroke.getKeyStroke("F9"), "field-f9");
        custom.put(KeyStroke.getKeyStroke("ctrl shift I"), "field-ctrl-shift-i");
        u.nameField.setInputMap(JComponent.WHEN_FOCUSED, custom);
        ActionMap actions = u.nameField.getActionMap();
        actions.put("field-f2", action(u, "F2", "field WHEN_FOCUSED F2"));
        actions.put("field-f9", action(u, "F9", "field WHEN_FOCUSED F9 (must not fire)"));
        actions.put("field-ctrl-shift-i", action(u, "I", "field ctrl shift I (must not fire)"));
        Action disabled = action(u, "F6", "field F6 (disabled, must not fire)");
        disabled.setEnabled(false);
        actions.put("field-f6", disabled);
        actions.put("upper-case", new AbstractAction("upper-case") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTextField field = (JTextField) e.getSource();
                field.setText(field.getText().toUpperCase(java.util.Locale.ROOT));
                u.log.add("field upper-case " + field.getText());
            }
        });
        u.nameField.addActionListener(e -> u.log.add("field ActionListener " + e.getActionCommand()));
        u.nameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F9) {
                    e.consume();
                    u.log.add("field KeyListener consumed F9");
                }
            }
        });

        // WHEN_ANCESTOR_OF_FOCUSED_COMPONENT on the form
        InputMap ancestor = u.form.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        for (String key : List.of("F3", "F6", "F7")) {
            ancestor.put(KeyStroke.getKeyStroke(key), "form-" + key);
            u.form.getActionMap().put("form-" + key,
                    action(u, key, "form WHEN_ANCESTOR_OF_FOCUSED_COMPONENT " + key));
        }

        // WHEN_IN_FOCUSED_WINDOW on a label (ComponentInputMap) and the legacy registerKeyboardAction
        u.shortcuts = new JLabel("F4 : window binding · ctrl shift L : registerKeyboardAction");
        u.shortcuts.setFont(u.shortcuts.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        u.shortcuts.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F4"), "window-f4");
        u.shortcuts.getActionMap().put("window-f4", action(u, "F4", "label WHEN_IN_FOCUSED_WINDOW F4"));
        u.shortcuts.registerKeyboardAction(e -> u.log.add("registerKeyboardAction " + e.getActionCommand()),
                "ctrl shift L", KeyStroke.getKeyStroke("ctrl shift L"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // mnemonics : not focusable, so that the mnemonic "pressed" actions never move the focus
        u.keep = new JButton("Keep");
        u.keep.setMnemonic(KeyEvent.VK_K);
        u.keep.setFocusable(false);
        u.keep.addActionListener(e -> u.log.add("button Keep ActionEvent"));
        u.bold = new JCheckBox("Bold");
        u.bold.setMnemonic(KeyEvent.VK_J);
        u.bold.setFocusable(false);
        u.bold.setOpaque(false);
        u.bold.addItemListener(e -> u.log.add("check box Bold selected " + u.bold.isSelected()));

        // a focusable pad with typed / released / modifier bindings
        u.pad = new JPanel(new BorderLayout());
        u.pad.setName("pad");
        u.pad.setFocusable(true);
        u.pad.setBackground(new Color(0xE3F2FD));
        u.pad.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0x90A4AE)));
        JLabel padLabel = new JLabel(" pad : typed +, released SPACE, F5, shift F5 ");
        padLabel.setFont(padLabel.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        u.pad.add(padLabel);
        InputMap padMap = u.pad.getInputMap(JComponent.WHEN_FOCUSED);
        Map<String, String> padBindings = new LinkedHashMap<>();
        padBindings.put("typed +", "pad typed +");
        padBindings.put("released SPACE", "pad released SPACE");
        padBindings.put("F5", "pad F5");
        padBindings.put("shift F5", "pad shift F5");
        padBindings.forEach((key, line) -> {
            padMap.put(KeyStroke.getKeyStroke(key), key);
            u.pad.getActionMap().put(key, action(u, key, line));
        });

        JPanel buttons = InfraSupport.row(8, u.keep, u.bold, u.pad);

        // look and feel bindings driven by synthetic events
        u.list = new JList<>(new String[] { "alpha", "beta", "gamma", "delta" });
        u.list.setSelectedIndex(0);
        u.list.setVisibleRowCount(4);
        JScrollPane listScroll = new JScrollPane(u.list);
        listScroll.setPreferredSize(new Dimension(80, 78));
        u.table = new JTable(new Object[][] { { "A", 1 }, { "B", 2 }, { "C", 3 }, { "D", 4 } },
                new String[] { "Row", "Value" });
        u.table.setRowSelectionInterval(0, 0);
        JScrollPane tableScroll = new JScrollPane(u.table);
        tableScroll.setPreferredSize(new Dimension(130, 90));
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        DefaultMutableTreeNode branch = new DefaultMutableTreeNode("Branch");
        branch.add(new DefaultMutableTreeNode("Leaf 1"));
        branch.add(new DefaultMutableTreeNode("Leaf 2"));
        root.add(branch);
        root.add(new DefaultMutableTreeNode("Leaf"));
        u.tree = new JTree(root);
        u.tree.setSelectionRow(1);
        JScrollPane treeScroll = new JScrollPane(u.tree);
        treeScroll.setPreferredSize(new Dimension(110, 90));
        u.area = new JTextArea("two lines\nof text", 2, 10);
        JScrollPane areaScroll = new JScrollPane(u.area);
        areaScroll.setPreferredSize(new Dimension(100, 44));
        JPanel lafRow = InfraSupport.row(8, listScroll, tableScroll, treeScroll, areaScroll);

        u.slider = new JSlider(0, 100, 50);
        u.slider.setOpaque(false);
        u.slider.setPreferredSize(new Dimension(150, 24));
        u.spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        u.tabs = new JTabbedPane();
        u.tabs.addTab("One", new JLabel(" first "));
        u.tabs.addTab("Two", new JLabel(" second "));
        u.tabs.setPreferredSize(new Dimension(140, 50));
        JPanel rangeRow = InfraSupport.row(8, u.slider, u.spinner, u.tabs);

        JPanel demo = InfraSupport.column(8, menuBar, u.form, u.shortcuts, buttons, lafRow, rangeRow);
        return demo;
    }

    private static JScrollPane logScroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(520, 452));
        return scroll;
    }

    private static void addRow(JPanel form, int row, JComponent label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(2, 0, 2, 8);
        c.anchor = GridBagConstraints.BASELINE_TRAILING;
        form.add(label, c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.BASELINE_LEADING;
        c.insets = new Insets(2, 0, 2, 0);
        form.add(field, c);
    }

    // ------------------------------------------------------------------------------------------------ synthetic

    private static void press(Component target, int keyCode, int modifiers) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    private static void release(Component target, int keyCode, int modifiers) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers,
                keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    private static void stroke(Component target, int keyCode, int modifiers) {
        press(target, keyCode, modifiers);
        release(target, keyCode, modifiers);
    }

    private static void typed(Component target, char c) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                KeyEvent.VK_UNDEFINED, c));
    }

    /**
     * Runs {@code dispatch} and checks the log lines it produced.
     */
    private static Check step(Demo u, String name, String expected, Runnable dispatch) {
        return Checks.expect(name, expected, () -> {
            int before = u.log.size();
            dispatch.run();
            List<String> lines = new ArrayList<>(u.log.subList(before, u.log.size()));
            return lines.isEmpty() ? "(nothing)" : String.join(" ; ", lines);
        });
    }

    private static Check state(String name, Object expected, Runnable dispatch, Supplier<Object> result) {
        return Checks.expect(name, expected, () -> {
            dispatch.run();
            return result.get();
        });
    }

    private List<Check> synthetic(Demo u) {
        List<Check> checks = new ArrayList<>();
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        dispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_I
                    && e.getModifiersEx() == CTRL_SHIFT
                    && SwingUtilities.isDescendingFrom(e.getComponent(), u.content)) {
                u.log.add("KeyEventDispatcher consumed ctrl shift I");
                return true;
            }
            return false;
        };
        postProcessor = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F11 && !e.isConsumed()
                    && SwingUtilities.isDescendingFrom(e.getComponent(), u.content)) {
                u.log.add("KeyEventPostProcessor unconsumed F11");
            }
            return false;
        };
        kfm.addKeyEventDispatcher(dispatcher);
        kfm.addKeyEventPostProcessor(postProcessor);
        try {
            JTextField f = u.nameField;
            checks.add(step(u, "F2 : WHEN_FOCUSED binding of the field", "field WHEN_FOCUSED F2",
                    () -> stroke(f, KeyEvent.VK_F2, 0)));
            checks.add(step(u, "F3 : WHEN_ANCESTOR_OF_FOCUSED_COMPONENT binding of the form",
                    "form WHEN_ANCESTOR_OF_FOCUSED_COMPONENT F3", () -> stroke(f, KeyEvent.VK_F3, 0)));
            checks.add(step(u, "F4 : WHEN_IN_FOCUSED_WINDOW binding of a label", "label WHEN_IN_FOCUSED_WINDOW F4",
                    () -> stroke(f, KeyEvent.VK_F4, 0)));
            checks.add(step(u, "F6 : disabled action of the field, the form binding runs",
                    "form WHEN_ANCESTOR_OF_FOCUSED_COMPONENT F6", () -> stroke(f, KeyEvent.VK_F6, 0)));
            checks.add(step(u, "F7 : \"none\" binding of the field does not consume",
                    "form WHEN_ANCESTOR_OF_FOCUSED_COMPONENT F7", () -> stroke(f, KeyEvent.VK_F7, 0)));
            checks.add(step(u, "F9 : consumed by a KeyListener before the bindings", "field KeyListener consumed F9",
                    () -> stroke(f, KeyEvent.VK_F9, 0)));
            checks.add(step(u, "ctrl shift U : custom map parented to the look and feel map",
                    "field upper-case DUKE", () -> stroke(f, KeyEvent.VK_U, CTRL_SHIFT)));
            checks.add(state("ctrl A : inherited look and feel binding select-all", "0-4",
                    () -> stroke(f, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
                    () -> f.getSelectionStart() + "-" + f.getSelectionEnd()));
            checks.add(step(u, "ctrl shift I : consumed by a KeyEventDispatcher",
                    "KeyEventDispatcher consumed ctrl shift I", () -> stroke(f, KeyEvent.VK_I, CTRL_SHIFT)));
            checks.add(step(u, "F11 : unbound, seen by a KeyEventPostProcessor", "KeyEventPostProcessor unconsumed F11",
                    () -> stroke(f, KeyEvent.VK_F11, 0)));
            checks.add(step(u, "alt K : button mnemonic (pressed, released)", "button Keep ActionEvent",
                    () -> stroke(f, KeyEvent.VK_K, InputEvent.ALT_DOWN_MASK)));
            checks.add(step(u, "alt J : check box mnemonic", "check box Bold selected true",
                    () -> stroke(f, KeyEvent.VK_J, InputEvent.ALT_DOWN_MASK)));
            checks.add(step(u, "ctrl shift N : menu item accelerator (KeyboardManager)", "menu New (accelerator)",
                    () -> stroke(f, KeyEvent.VK_N, CTRL_SHIFT)));
            checks.add(step(u, "ctrl shift P : menu item accelerator from a string", "menu Open (accelerator)",
                    () -> stroke(f, KeyEvent.VK_P, CTRL_SHIFT)));
            checks.add(Checks.expect("ctrl shift O : text field toggle-componentOrientation wins over menus",
                    "RIGHT_TO_LEFT (nothing logged), then LEFT_TO_RIGHT", () -> {
                        int before = u.log.size();
                        stroke(f, KeyEvent.VK_O, CTRL_SHIFT);
                        String first = f.getComponentOrientation().isLeftToRight() ? "LEFT_TO_RIGHT" : "RIGHT_TO_LEFT";
                        String logged = u.log.size() == before ? " (nothing logged)"
                                : " (logged " + u.log.get(before) + ")";
                        stroke(f, KeyEvent.VK_O, CTRL_SHIFT);
                        return first + logged + ", then "
                                + (f.getComponentOrientation().isLeftToRight() ? "LEFT_TO_RIGHT" : "RIGHT_TO_LEFT");
                    }));
            checks.add(step(u, "ctrl shift E : accelerator of a disabled menu item", "(nothing)",
                    () -> stroke(f, KeyEvent.VK_E, CTRL_SHIFT)));
            checks.add(step(u, "ctrl shift L : registerKeyboardAction", "registerKeyboardAction ctrl shift L",
                    () -> stroke(f, KeyEvent.VK_L, CTRL_SHIFT)));
            checks.add(step(u, "typed + : KEY_TYPED binding of the pad", "pad typed +", () -> typed(u.pad, '+')));
            checks.add(step(u, "SPACE : released binding of the pad", "pad released SPACE",
                    () -> stroke(u.pad, KeyEvent.VK_SPACE, 0)));
            checks.add(step(u, "F5, shift F5 : modifiers select the binding", "pad F5 ; pad shift F5", () -> {
                stroke(u.pad, KeyEvent.VK_F5, 0);
                stroke(u.pad, KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK);
            }));
            checks.add(step(u, "SwingUtilities.notifyAction", "field WHEN_FOCUSED F2",
                    () -> SwingUtilities.notifyAction(f.getActionMap().get("field-f2"), KeyStroke.getKeyStroke("F2"),
                            new KeyEvent(f, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_F2, KeyEvent.CHAR_UNDEFINED), f,
                            0)));
            checks.add(state("DOWN on the list : selectNextRow", 1, () -> stroke(u.list, KeyEvent.VK_DOWN, 0),
                    () -> u.list.getSelectedIndex()));
            checks.add(state("ctrl END on the table : selectLastRow", 3,
                    () -> stroke(u.table, KeyEvent.VK_END, InputEvent.CTRL_DOWN_MASK), () -> u.table.getSelectedRow()));
            checks.add(state("RIGHT on the tree : selectChild expands", "expanded true, rows 5",
                    () -> stroke(u.tree, KeyEvent.VK_RIGHT, 0),
                    () -> "expanded " + u.tree.isExpanded(1) + ", rows " + u.tree.getRowCount()));
            checks.add(state("RIGHT on the slider : positiveUnitIncrement", 51,
                    () -> stroke(u.slider, KeyEvent.VK_RIGHT, 0), () -> u.slider.getValue()));
            checks.add(state("UP on the spinner editor : increment", 6,
                    () -> stroke(((JSpinner.DefaultEditor) u.spinner.getEditor()).getTextField(), KeyEvent.VK_UP, 0),
                    () -> u.spinner.getValue()));
            checks.add(state("ctrl PAGE_DOWN on the tabbed pane : navigatePageDown", 1,
                    () -> stroke(u.tabs, KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK),
                    () -> u.tabs.getSelectedIndex()));
            checks.add(state("ctrl A on the text area : select-all", "0-" + u.area.getDocument().getLength(),
                    () -> stroke(u.area, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
                    () -> u.area.getSelectionStart() + "-" + u.area.getSelectionEnd()));
        } finally {
            removeDispatchers();
        }

        // InputMap / ActionMap API
        checks.add(Checks.info("field WHEN_FOCUSED map chain", () -> {
            List<String> chain = new ArrayList<>();
            for (InputMap m = u.nameField.getInputMap(); m != null; m = m.getParent()) {
                chain.add(m.getClass().getName().substring(m.getClass().getName().lastIndexOf('.') + 1));
            }
            return String.join(" > ", chain);
        }));
        checks.add(Checks.expect("field WHEN_FOCUSED map chain ends with the look and feel map", "true", () -> {
            InputMap m = u.nameField.getInputMap();
            while (m.getParent() != null) {
                m = m.getParent();
            }
            return m instanceof InputMapUIResource;
        }));
        checks.add(Checks.expect("custom map : own keys, own + parent keys", "6, more than 6",
                () -> u.nameField.getInputMap().keys().length + ", "
                        + (u.nameField.getInputMap().allKeys().length > 6 ? "more than 6" : "not more than 6")));
        checks.add(Checks.info("custom map : allKeys()", () -> u.nameField.getInputMap().allKeys().length));
        checks.add(Checks.expect("WHEN_IN_FOCUSED_WINDOW map is a ComponentInputMap of its component", "true",
                () -> u.shortcuts.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW) instanceof ComponentInputMap map
                        && map.getComponent() == u.shortcuts));
        checks.add(Checks.expect("getConditionForKeyStroke(ctrl shift L)", JComponent.WHEN_IN_FOCUSED_WINDOW,
                () -> u.shortcuts.getConditionForKeyStroke(KeyStroke.getKeyStroke("ctrl shift L"))));
        checks.add(Checks.expect("getRegisteredKeyStrokes() of the label", "F4, shift ctrl L",
                () -> java.util.Arrays.stream(u.shortcuts.getRegisteredKeyStrokes())
                        .map(ks -> ks.toString().replace("pressed ", "")).sorted().collect(Collectors.joining(", "))));
        checks.add(Checks.expect("getActionForKeyStroke(ctrl shift L) present", "true",
                () -> u.shortcuts.getActionForKeyStroke(KeyStroke.getKeyStroke("ctrl shift L")) != null));
        checks.add(Checks.expect("text field (look and feel) : field text after the events", "DUKE",
                () -> u.nameField.getText()));
        return checks;
    }

    // ------------------------------------------------------------------------------------------------ focus, Robot

    private CompletionStage<Void> focusPhase(Demo u, List<Check> checks) {
        // the page window focused, this process owning the foreground
        return Focus.acquire(SwingUtilities.getWindowAncestor(u.nameField)).thenCompose(attempts -> {
            if (attempts == 0) {
                checks.add(Check.info("alt M : label mnemonic moves the focus to its labelFor", "skipped: not focused"));
                checks.add(Check.info("Robot typing", "skipped: not focused"));
                return CompletableFuture.completedFuture(null);
            }
            return focusedPhase(u, checks);
        });
    }

    private CompletionStage<Void> focusedPhase(Demo u, List<Check> checks) {
        // label mnemonic : pressed alt M (window binding) focuses the label, released alt M (on the label) focuses the
        // labelFor component
        press(u.nameField, KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK);
        return Edt.until(u.nameLabel::isFocusOwner, 2000, "label focused")
                .thenCompose(v -> {
                    release(u.nameLabel, KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK);
                    return Edt.until(u.otherField::isFocusOwner, 2000, "labelFor focused");
                })
                .handle((v, error) -> {
                    String name = "alt M : label mnemonic moves the focus to its labelFor";
                    if (error == null) {
                        checks.add(Check.pass(name, "labelFor focused"));
                        // notify-field-accept acts on the focused text field
                        checks.add(step(u, "ENTER on the focused field : notify-field-accept",
                                "other field ActionListener labelFor of Name",
                                () -> stroke(u.otherField, KeyEvent.VK_ENTER, 0)));
                    } else {
                        checks.add(Edt.ownsFocus() ? Check.fail(name, Checks.describe(unwrap(error)))
                                : Check.info(name, "skipped: not focused"));
                        // the key must not stay pressed in the Swing KeyboardState
                        release(u.nameField, KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK);
                    }
                    return null;
                })
                .thenCompose(v -> robot(u, checks));
    }

    private static CompletionStage<Void> robot(Demo u, List<Check> checks) {
        JTextField field = u.robotField;
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                u.robotFieldFocused.set(true);
            }

            @Override
            public void focusLost(FocusEvent e) {
                u.robotFieldFocused.set(false);
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                u.robotPressed.add(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                u.robotTyped.append(e.getKeyChar());
            }
        });
        // a released binding (function keys may be taken by system wide hot keys) : the character is still typed
        field.getInputMap().put(KeyStroke.getKeyStroke("released 5"), "robot-released-5");
        field.getActionMap().put("robot-released-5", action(u, "5", "Robot field WHEN_FOCUSED released 5"));
        field.requestFocusInWindow();
        return Edt.until(field::isFocusOwner, 2000, "Robot field focused")
                .thenCompose(v -> {
                    u.robotFieldFocused.set(true);
                    return Edt.background(() -> type(u));
                })
                // the last key events may still be queued
                .thenCompose(outcome -> Edt.rounds(3).thenApply(v -> outcome))
                .handle((outcome, error) -> {
                    String name = "Robot typing";
                    if (error != null) {
                        checks.add(Edt.ownsFocus() ? Check.fail(name, Checks.describe(unwrap(error)))
                                : Check.info(name, "skipped: not focused"));
                        return null;
                    }
                    checks.add(Check.attempts(name, outcome.attempts()));
                    // informational : whether the keyboard input reached the page depends on the desktop, but the
                    // value is compared between the runs
                    checks.add(Check.info("Robot canary (a lone Shift) reached the Robot field",
                            outcome.canary() ? "received" : "skipped: " + outcome.reason()));
                    if (!outcome.complete()) {
                        checks.add(Check.info(name, "skipped: " + outcome.reason()));
                        return null;
                    }
                    List<KeyEvent> pressed = new ArrayList<>(u.robotPressed);
                    checks.add(Check.pass(name, pressed.size() + " key presses"));
                    checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("Robot KEY_PRESSED key texts",
                            "Shift Q U A R K U S Space 2 5 Shift Q",
                            () -> pressed.stream().map(e -> KeyEvent.getKeyText(e.getKeyCode()))
                                    .collect(Collectors.joining(" ")))));
                    checks.add(Check.info("Robot KEY_TYPED characters (keyboard layout)", u.robotTyped.toString()));
                    checks.add(Check.info("Robot field text", field.getText()));
                    checks.add(Checks.expect("Robot released 5 : binding triggered by a native key event",
                            "true", () -> u.log.contains("Robot field WHEN_FOCUSED released 5")));
                    checks.add(Checks.info("Robot extended key codes", () -> pressed.stream()
                            .map(e -> Integer.toString(e.getExtendedKeyCode())).collect(Collectors.joining(" "))));
                    checks.add(Checks.info("Robot key locations", () -> pressed.stream()
                            .map(e -> Integer.toString(e.getKeyLocation())).collect(Collectors.joining(" "))));
                    checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("Robot shifted Q : modifiersEx text",
                            "Shift", () -> InputEvent.getModifiersExText(pressed.get(pressed.size() - 1)
                                    .getModifiersEx()))));
                    checks.add(Checks.expect("Robot KeyStroke.getKeyStrokeForEvent(second press)", "pressed Q",
                            () -> KeyStroke.getKeyStrokeForEvent(pressed.get(1)).toString()));
                    return null;
                });
    }

    /** The outcome of the Robot typing. */
    private record Typing(boolean canary, boolean complete, String reason, int attempts) {

        Typing(boolean canary, boolean complete, String reason) {
            this(canary, complete, reason, 1);
        }

        Typing attempts(int count) {
            return new Typing(canary, complete, reason, count);
        }
    }

    /** Attempts of the Robot typing : another application may take the foreground at any time. */
    private static final int ATTEMPTS = 3;

    /**
     * {@link #typeOnce}, again while the typing is incomplete (at most {@link #ATTEMPTS} times) : the window is focused
     * again ({@link Focus#acquireBlocking}) and the Robot field emptied first.
     */
    private static Typing type(Demo u) throws Exception {
        Typing outcome = typeOnce(u);
        int attempt = 1;
        while (!outcome.complete() && attempt < ATTEMPTS) {
            attempt++;
            JTextField field = u.robotField;
            java.awt.Window window = onEdt(() -> SwingUtilities.getWindowAncestor(field));
            if (Focus.acquireBlocking(window) == 0) {
                break;
            }
            onEdt(() -> {
                u.robotPressed.clear();
                u.robotTyped.setLength(0);
                field.setText("");
                return field.requestFocusInWindow();
            });
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (!u.robotFieldFocused.get() && System.nanoTime() - deadline < 0) {
                Thread.sleep(20);
            }
            outcome = typeOnce(u);
        }
        return outcome.attempts(attempt);
    }

    /**
     * Types a lone Shift (the canary : harmless for any application), {@link #ROBOT_KEYS}, then shift Q with Robot, on a
     * background thread. Java may report a focused showcase window while another application owns the foreground
     * (Windows refuses to bring a window to the front from the background) : the keyboard input then goes to that
     * application. So each key is sent only while a showcase window is focused and the Robot field owns the focus,
     * and only once the previous key press has reached the Robot field : at most one key could go elsewhere if the
     * foreground changes during the sequence, and none when it was not ours at the start (the canary).
     */
    private static Typing typeOnce(Demo u) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(0);
        if (!send(robot, u, KeyEvent.VK_SHIFT)) {
            return new Typing(false, false, focusReason(u, 0));
        }
        for (int key : ROBOT_KEYS) {
            if (!send(robot, u, key)) {
                return new Typing(true, false, focusReason(u, u.robotPressed.size()));
            }
        }
        if (!sendable(u)) {
            return new Typing(true, false, focusReason(u, u.robotPressed.size()));
        }
        int before = u.robotPressed.size();
        robot.keyPress(KeyEvent.VK_SHIFT);
        try {
            if (received(u, before) && !send(robot, u, KeyEvent.VK_Q)) {
                return new Typing(true, false, focusReason(u, u.robotPressed.size()));
            }
        } finally {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }
        robot.waitForIdle();
        boolean complete = u.robotPressed.size() == ROBOT_KEYS.length + 3;
        return new Typing(true, complete, complete ? "" : focusReason(u, u.robotPressed.size()));
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        try {
            return Edt.supply(action).toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static boolean sendable(Demo u) {
        return Edt.ownsFocus() && u.robotFieldFocused.get();
    }

    /**
     * Presses and releases {@code key} if the Robot field has the focus, then waits (1 s at most) until the press
     * reached the Robot field.
     */
    private static boolean send(Robot robot, Demo u, int key) throws InterruptedException {
        if (!sendable(u)) {
            return false;
        }
        int before = u.robotPressed.size();
        robot.keyPress(key);
        robot.keyRelease(key);
        return received(u, before);
    }

    private static boolean received(Demo u, int before) throws InterruptedException {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (u.robotPressed.size() <= before) {
            if (System.nanoTime() - deadline > 0) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    private static String focusReason(Demo u, int received) {
        return !Edt.ownsFocus() ? "not focused"
                : !u.robotFieldFocused.get() ? "the Robot field lost the focus"
                        : "keyboard input not received after " + received
                                + " key presses (another application owns the foreground)";
    }

    // ------------------------------------------------------------------------------------------------ static checks

    private static List<Check> strokeChecks() {
        List<Check> checks = new ArrayList<>();
        for (String[] stroke : STROKES) {
            checks.add(Checks.expect("\"" + stroke[0] + "\"", stroke[1] == null ? "null" : stroke[1], () -> {
                KeyStroke ks = KeyStroke.getKeyStroke(stroke[0]);
                return ks == null ? "null" : ks.toString();
            }));
        }
        checks.add(Checks.expect("\"ctrl shift F10\" code, char, modifiers (old and new), release",
                "121, 65535, 195, false",
                () -> {
                    KeyStroke ks = KeyStroke.getKeyStroke("ctrl shift F10");
                    return ks.getKeyCode() + ", " + (int) ks.getKeyChar() + ", " + ks.getModifiers() + ", "
                            + ks.isOnKeyRelease();
                }));
        checks.add(Checks.expect("getKeyStroke('a')", "typed a", () -> KeyStroke.getKeyStroke('a').toString()));
        checks.add(Checks.expect("getKeyStroke(Character 'b', CTRL_DOWN_MASK)", "ctrl typed b",
                () -> KeyStroke.getKeyStroke(Character.valueOf('b'), InputEvent.CTRL_DOWN_MASK).toString()));
        checks.add(Checks.expect("getKeyStroke(VK_S, CTRL_DOWN_MASK | SHIFT_DOWN_MASK)", "shift ctrl pressed S",
                () -> KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_SHIFT).toString()));
        // 2 is the old InputEvent.CTRL_MASK : mapped to CTRL_DOWN_MASK
        checks.add(Checks.expect("getKeyStroke(VK_S, CTRL_MASK) (old modifiers)", "ctrl pressed S",
                () -> KeyStroke.getKeyStroke(KeyEvent.VK_S, 2).toString()));
        checks.add(Checks.expect("getKeyStroke(VK_S, 0, true)", "released S",
                () -> KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true).toString()));
        checks.add(Checks.expect("cached instances : \"ctrl S\" == (VK_S, CTRL_DOWN_MASK)", "true",
                () -> KeyStroke.getKeyStroke("ctrl S") == KeyStroke.getKeyStroke(KeyEvent.VK_S,
                        InputEvent.CTRL_DOWN_MASK)));
        checks.add(Checks.expect("AWTKeyStroke.getAWTKeyStroke(\"shift F3\")", "shift pressed F3",
                () -> AWTKeyStroke.getAWTKeyStroke("shift F3").toString()));
        checks.add(Checks.expect("getKeyStrokeForEvent(KEY_PRESSED ctrl C, KEY_TYPED c)", "ctrl pressed C | typed c",
                () -> {
                    JLabel source = new JLabel();
                    KeyEvent pressed = new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_DOWN_MASK,
                            KeyEvent.VK_C, 'c');
                    KeyEvent typed = new KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'c');
                    return KeyStroke.getKeyStrokeForEvent(pressed) + " | " + KeyStroke.getKeyStrokeForEvent(typed);
                }));
        return checks;
    }

    private static List<Check> keyNameChecks() {
        List<Check> checks = new ArrayList<>();
        // Toolkit.getProperty(key, default) returns the default when the bundle sun.awt.resources.awt is missing
        checks.add(InfraSupport.notOnMac(Checks.expect("Toolkit.getProperty(\"AWT.control\") (awt bundle)", "Ctrl",
                () -> Toolkit.getProperty("AWT.control", "<missing>"))));
        Object[][] keys = {
                { KeyEvent.VK_ENTER, "Enter" }, { KeyEvent.VK_BACK_SPACE, "Backspace" }, { KeyEvent.VK_TAB, "Tab" },
                { KeyEvent.VK_ESCAPE, "Escape" }, { KeyEvent.VK_SPACE, "Space" }, { KeyEvent.VK_PAGE_UP, "Page Up" },
                { KeyEvent.VK_HOME, "Home" }, { KeyEvent.VK_LEFT, "Left" }, { KeyEvent.VK_F1, "F1" },
                { KeyEvent.VK_F24, "F24" }, { KeyEvent.VK_A, "A" }, { KeyEvent.VK_5, "5" },
                { KeyEvent.VK_NUMPAD5, "NumPad-5" }, { KeyEvent.VK_MULTIPLY, "NumPad *" },
                { KeyEvent.VK_COMMA, "Comma" }, { KeyEvent.VK_EURO_SIGN, "Euro" }, { KeyEvent.VK_CONTROL, "Ctrl" },
                { KeyEvent.VK_SHIFT, "Shift" }, { KeyEvent.VK_ALT_GRAPH, "Alt Graph" },
                { KeyEvent.VK_WINDOWS, "Windows" }, { KeyEvent.VK_CONTEXT_MENU, "Context Menu" },
                { KeyEvent.VK_DEAD_ACUTE, "Dead Acute" }, { KeyEvent.VK_KP_LEFT, "Left" },
                { 0xFFFF, "Unknown keyCode: 0xffff" },
        };
        List<String> names = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (Object[] key : keys) {
            names.add(KeyEvent.getKeyText((Integer) key[0]));
            expected.add((String) key[1]);
        }
        checks.add(InfraSupport.notOnMac(Checks.expect("KeyEvent.getKeyText (24 keys)", String.join(" · ", expected),
                () -> String.join(" · ", names))));
        checks.add(InfraSupport.notOnMac(Checks.expect("getModifiersExText(CTRL | SHIFT)", "Ctrl+Shift",
                () -> InputEvent.getModifiersExText(CTRL_SHIFT))));
        checks.add(InfraSupport.notOnMac(Checks.expect("getModifiersExText(META | ALT | ALT_GRAPH | BUTTON1)",
                "Meta+Alt+Alt Graph+Button1", () -> InputEvent.getModifiersExText(InputEvent.META_DOWN_MASK
                        | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK))));
        checks.add(Checks.expect("getExtendedKeyCodeForChar a, A, 1, €",
                KeyEvent.VK_A + " " + KeyEvent.VK_A + " " + KeyEvent.VK_1 + " " + KeyEvent.VK_EURO_SIGN,
                () -> KeyEvent.getExtendedKeyCodeForChar('a') + " " + KeyEvent.getExtendedKeyCodeForChar('A') + " "
                        + KeyEvent.getExtendedKeyCodeForChar('1') + " " + KeyEvent.getExtendedKeyCodeForChar('€')));
        checks.add(Checks.info("getExtendedKeyCodeForChar é, ß, ش, ж (hex)",
                () -> hex(KeyEvent.getExtendedKeyCodeForChar('é')) + " " + hex(KeyEvent.getExtendedKeyCodeForChar('ß'))
                        + " " + hex(KeyEvent.getExtendedKeyCodeForChar('ش')) + " "
                        + hex(KeyEvent.getExtendedKeyCodeForChar('ж'))));
        checks.add(Checks.expect("KeyEvent.getKeyModifiersText(SHIFT_MASK) (old API)", "Shift",
                () -> KeyEvent.getKeyModifiersText(1)));
        checks.add(Checks.expect("KeyEvent locations of a synthetic event", "LEFT 2", () -> {
            KeyEvent e = new KeyEvent(new JLabel(), KeyEvent.KEY_PRESSED, 0, InputEvent.SHIFT_DOWN_MASK,
                    KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT);
            return (e.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT ? "LEFT " : "? ") + e.getKeyLocation();
        }));
        checks.add(Checks.expect("KeyEvent.isActionKey(F1, A)", "true, false",
                () -> new KeyEvent(new JLabel(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED)
                        .isActionKey() + ", "
                        + new KeyEvent(new JLabel(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a').isActionKey()));
        checks.add(Checks.info("Toolkit.getMenuShortcutKeyMaskEx()",
                () -> InputEvent.getModifiersExText(Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx())));
        return checks;
    }

    private static String texts(List<KeyEvent> events) {
        synchronized (events) {
            return events.stream().map(e -> KeyEvent.getKeyText(e.getKeyCode())).collect(Collectors.joining(" "));
        }
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static String hex(int code) {
        return "0x" + Integer.toHexString(code);
    }

    private record LafBinding(String label, Supplier<JComponent> component, int condition, String key, String action) {
    }

    private static List<Check> lafChecks() {
        int focused = JComponent.WHEN_FOCUSED;
        int ancestor = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
        int window = JComponent.WHEN_IN_FOCUSED_WINDOW;
        List<LafBinding> bindings = List.of(
                new LafBinding("JButton", JButton::new, focused, "SPACE", "pressed"),
                new LafBinding("JToggleButton", JToggleButton::new, focused, "released SPACE", "released"),
                new LafBinding("JCheckBox", JCheckBox::new, focused, "SPACE", "pressed"),
                new LafBinding("JRadioButton", JRadioButton::new, focused, "SPACE", "pressed"),
                new LafBinding("JComboBox", JComboBox::new, ancestor, "DOWN", "selectNext"),
                new LafBinding("JDesktopPane", JDesktopPane::new, ancestor, "ctrl F5", "restore"),
                new LafBinding("JInternalFrame (Metal removes it)", JInternalFrame::new, -1, null, "showSystemMenu"),
                new LafBinding("JLabel", () -> {
                    JLabel label = new JLabel("x");
                    label.setLabelFor(new JTextField());
                    label.setDisplayedMnemonic('X');
                    return label;
                }, window, "alt X", "press"),
                new LafBinding("JList", JList::new, focused, "DOWN", "selectNextRow"),
                new LafBinding("JMenuBar", JMenuBar::new, window, "F10", "takeFocus"),
                new LafBinding("JMenu", () -> new JMenu("m"), -1, null, "selectMenu"),
                new LafBinding("JMenuItem", () -> {
                    JMenuItem item = new JMenuItem("i");
                    item.setAccelerator(KeyStroke.getKeyStroke("ctrl shift M"));
                    return item;
                }, window, "ctrl shift M", "doClick"),
                new LafBinding("JOptionPane", JOptionPane::new, window, "ESCAPE", "close"),
                new LafBinding("JRootPane", JRootPane::new, ancestor, "shift F10", "postPopup"),
                new LafBinding("JScrollBar", JScrollBar::new, ancestor, "RIGHT", "positiveUnitIncrement"),
                new LafBinding("JScrollPane", JScrollPane::new, ancestor, "PAGE_DOWN", "scrollDown"),
                new LafBinding("JSlider", JSlider::new, focused, "RIGHT", "positiveUnitIncrement"),
                new LafBinding("JSpinner", JSpinner::new, ancestor, "UP", "increment"),
                new LafBinding("JSplitPane", JSplitPane::new, ancestor, "F6", "toggleFocus"),
                new LafBinding("JTabbedPane", JTabbedPane::new, ancestor, "ctrl PAGE_DOWN", "navigatePageDown"),
                new LafBinding("JTable", JTable::new, ancestor, "ctrl END", "selectLastRow"),
                new LafBinding("JTableHeader", JTableHeader::new, ancestor, "SPACE", "toggleSortOrder"),
                new LafBinding("JToolBar", JToolBar::new, ancestor, "UP", "navigateUp"),
                new LafBinding("JTree", JTree::new, focused, "RIGHT", "selectChild"),
                new LafBinding("JTextField", JTextField::new, focused, "ctrl C", "copy-to-clipboard"),
                new LafBinding("JPasswordField", JPasswordField::new, focused, "ctrl V", "paste-from-clipboard"),
                new LafBinding("JFormattedTextField", JFormattedTextField::new, focused, "ESCAPE", "reset-field-edit"),
                new LafBinding("JTextArea", JTextArea::new, focused, "ENTER", "insert-break"),
                new LafBinding("JTextPane", JTextPane::new, focused, "ENTER", "insert-break"),
                new LafBinding("JEditorPane", JEditorPane::new, focused, "ctrl HOME", "caret-begin"));
        List<Check> checks = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        for (LafBinding b : bindings) {
            String expected = (b.key() == null ? "" : b.key() + " -> ") + b.action()
                    + (b.label().startsWith("JInternalFrame") ? " (no action)" : " (action present)");
            checks.add(InfraSupport.notOnMac(Checks.expect(b.label(), expected, () -> {
                JComponent c = b.component().get();
                String bound = b.action();
                if (b.key() != null) {
                    Object binding = c.getInputMap(b.condition()).get(KeyStroke.getKeyStroke(b.key()));
                    bound = String.valueOf(binding);
                }
                // ActionMap.get loads the lazy action map of the UI class (reflective loadActionMap)
                Action action = c.getActionMap().get(b.action());
                sizes.add(b.label() + " " + c.getActionMap().allKeys().length);
                return (b.key() == null ? "" : b.key() + " -> ") + bound
                        + (action != null ? " (action present)" : " (no action)");
            })));
        }
        checks.add(Check.info("ActionMap sizes", String.join(", ", sizes)));
        return checks;
    }
}
