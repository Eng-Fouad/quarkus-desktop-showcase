package io.quarkiverse.desktop.showcase.pages.swing.a11y;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleEditableText;
import javax.accessibility.AccessibleExtendedComponent;
import javax.accessibility.AccessibleExtendedText;
import javax.accessibility.AccessibleHyperlink;
import javax.accessibility.AccessibleHypertext;
import javax.accessibility.AccessibleRelation;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStreamable;
import javax.accessibility.AccessibleTable;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleValue;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.tree.DefaultMutableTreeNode;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.TextBlock;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.a11y.AccessibleDump;

/**
 * The {@code javax.accessibility} API of the Swing components : label relations, mnemonics and accelerators (key
 * bindings), tooltips and icons, values (slider, progress bar, spinner), editable and extended text, hypertext links of
 * an HTML editor pane, tables, trees, lists, combo boxes, tabbed panes and menus, accessible property change events,
 * and the actions performed through the accessibility API.
 * <p>
 * Swing variant of the AWT page {@code pages.a11y.AccessibilityPage} (same dump format).
 */
@Singleton
public class SwingAccessibilityPage implements FeaturePage {

    private static final int GALLERY_WIDTH = 1000;
    private static final int GALLERY_HEIGHT = 300;

    private Gallery gallery;
    private ChecksView results;
    private TextBlock tree;

    private record Gallery(JPanel panel, JMenuBar menuBar, JMenuItem open, JLabel label, JTextField field, JButton button,
            JToggleButton bold, JCheckBox duplex, JRadioButton portrait, JComboBox<String> combo, JList<String> list,
            JSlider slider, JProgressBar progress, JSpinner spinner, JTextArea area, JPasswordField password,
            JEditorPane html, JTable table, JTree treeView, JTabbedPane tabs, JToolBar toolBar, JFrame frame) {
    }

    @Override
    public String id() {
        return "a11y-contexts-swing";
    }

    @Override
    public String title() {
        return "Accessibility API (Swing)";
    }

    @Override
    public String category() {
        return Categories.A11Y_BEANS;
    }

    @Override
    public int order() {
        return 20;
    }

    private static <C extends JComponent> C place(JPanel panel, C component, int x, int y, int w, int h) {
        component.setBounds(x, y, w, h);
        panel.add(component);
        return component;
    }

    @Override
    public Component build() {
        gallery = gallery();
        results = ChecksView.table("Accessible contexts", List.of(Check.info("state", "pending")));
        tree = Ui.text("pending", new Font(Font.MONOSPACED, Font.PLAIN, 11), Ui.TEXT_COLOR, 1000);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new Ui.StackLayout(true, 14));
        content.add(Ui.text("Swing components and their accessible contexts (the menu bar is part of the panel ; the "
                + "frame at the end of the tree is not shown).", 1000));
        content.add(gallery.panel());
        content.add(Ui.title("Accessible tree"));
        content.add(tree);
        content.add(results);
        return content;
    }

    private static Gallery gallery() {
        JPanel panel = new JPanel(null) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(GALLERY_WIDTH, GALLERY_HEIGHT);
            }
        };
        panel.setBackground(new Color(0xF5F5F5));
        panel.getAccessibleContext().setAccessibleName("Print options");

        JMenuBar menuBar = place(panel, new JMenuBar(), 0, 0, GALLERY_WIDTH, 22);
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem open = new JMenuItem("Open...", KeyEvent.VK_O);
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        file.add(open);
        file.add(new JCheckBoxMenuItem("Autosave", true));
        menuBar.add(file);
        menuBar.add(new JMenu("Help"));

        JLabel label = place(panel, new JLabel("Name:"), 10, 32, 60, 24);
        JTextField field = place(panel, new JTextField("Quarkus Desktop"), 70, 32, 180, 24);
        label.setLabelFor(field);
        label.setDisplayedMnemonic(KeyEvent.VK_N);
        JButton button = place(panel, new JButton("Print", new ImageIcon(icon(), "printer icon")), 260, 32, 110, 26);
        button.setMnemonic(KeyEvent.VK_P);
        button.setToolTipText("Prints the document");
        JToggleButton bold = place(panel, new JToggleButton("Bold", true), 380, 32, 80, 26);
        JCheckBox duplex = place(panel, new JCheckBox("Duplex", true), 470, 32, 80, 24);
        duplex.setOpaque(false);
        ButtonGroup orientation = new ButtonGroup();
        JRadioButton portrait = place(panel, new JRadioButton("Portrait", true), 550, 32, 90, 24);
        JRadioButton landscape = place(panel, new JRadioButton("Landscape"), 640, 32, 100, 24);
        portrait.setOpaque(false);
        landscape.setOpaque(false);
        orientation.add(portrait);
        orientation.add(landscape);
        JComboBox<String> combo = place(panel, new JComboBox<>(new String[] { "Letter", "A4", "A5" }), 750, 32, 110, 24);
        combo.setSelectedIndex(1);
        combo.getAccessibleContext().setAccessibleName("Paper");

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String item : List.of("First copy", "Second copy", "Third copy", "Fourth copy", "Fifth copy")) {
            model.addElement(item);
        }
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(new int[] { 1, 3 });
        list.getAccessibleContext().setAccessibleName("Copies");
        place(panel, new JScrollPane(list), 870, 32, 120, 100);

        JSlider slider = place(panel, new JSlider(0, 100, 30), 10, 66, 200, 24);
        slider.setOpaque(false);
        slider.getAccessibleContext().setAccessibleName("Zoom");
        JProgressBar progress = place(panel, new JProgressBar(0, 100), 220, 70, 150, 16);
        progress.setValue(70);
        progress.setStringPainted(true);
        JSpinner spinner = place(panel, new JSpinner(new SpinnerNumberModel(2, 1, 99, 1)), 380, 66, 60, 24);
        spinner.getAccessibleContext().setAccessibleName("Copies count");
        JPasswordField password = place(panel, new JPasswordField("secret"), 450, 66, 120, 24);
        password.getAccessibleContext().setAccessibleName("Password");
        JToolBar toolBar = place(panel, new JToolBar("Tools"), 580, 66, 280, 26);
        toolBar.setFloatable(false);
        toolBar.add(new JButton("Cut"));
        toolBar.add(new JButton("Copy"));
        toolBar.addSeparator();
        toolBar.add(new JButton("Paste"));

        JTextArea area = new JTextArea("First line of notes.\nSecond line. Third sentence!");
        area.getAccessibleContext().setAccessibleName("Notes");
        JScrollPane areaScroll = place(panel, new JScrollPane(area), 10, 100, 280, 50);
        areaScroll.setBorder(BorderFactory.createTitledBorder("Notes"));

        JEditorPane html = new JEditorPane("text/html", "<html><body style='font-family:sans-serif;font-size:10px'>"
                + "See <a href='https://quarkus.io'>Quarkus</a> and <a href='https://docs.oracle.com'>the JDK "
                + "documentation</a>.</body></html>");
        html.setEditable(false);
        html.getAccessibleContext().setAccessibleName("Links");
        place(panel, html, 300, 100, 300, 44);

        JTable table = new JTable(new Object[][] { { "Cover", 1, true }, { "Summary", 2, false }, { "Details", 5, true } },
                new Object[] { "Section", "Pages", "Print" });
        table.setRowSelectionInterval(1, 1);
        table.getAccessibleContext().setAccessibleName("Sections");
        place(panel, new JScrollPane(table), 610, 140, 250, 80);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Document");
        DefaultMutableTreeNode chapters = new DefaultMutableTreeNode("Chapters");
        chapters.add(new DefaultMutableTreeNode("Introduction"));
        chapters.add(new DefaultMutableTreeNode("Conclusion"));
        root.add(chapters);
        root.add(new DefaultMutableTreeNode("Appendix"));
        JTree treeView = new JTree(root);
        treeView.expandRow(1);
        treeView.setSelectionRow(2);
        treeView.getAccessibleContext().setAccessibleName("Outline");
        place(panel, new JScrollPane(treeView), 870, 140, 120, 150);

        JTabbedPane tabs = place(panel, new JTabbedPane(), 10, 160, 590, 130);
        JPanel general = new JPanel();
        general.add(new JLabel("General settings"));
        tabs.addTab("General", general);
        tabs.addTab("Layout", new JPanel());
        tabs.addTab("Advanced", new JPanel());
        tabs.setSelectedIndex(1);

        JFrame frame = new JFrame("Accessible frame");
        return new Gallery(panel, menuBar, open, label, field, button, bold, duplex, portrait, combo, list, slider,
                progress, spinner, area, password, html, table, treeView, tabs, toolBar, frame);
    }

    private static BufferedImage icon() {
        return Snapshots.offscreen(16, 16, g -> {
            g.setColor(new Color(0x546E7A));
            g.fillRect(2, 5, 12, 7);
            g.setColor(Color.WHITE);
            g.fillRect(4, 2, 8, 4);
            g.fillRect(4, 10, 8, 5);
        });
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Gallery g = gallery;
        ChecksView view = results;
        TextBlock text = tree;
        // the HTML document of the editor pane is loaded synchronously (text set in the constructor)
        return io.quarkiverse.desktop.showcase.core.Edt.rounds(3).thenRun(() -> {
            List<String> dump = new ArrayList<>(AccessibleDump.dump(g.panel()));
            dump.addAll(AccessibleDump.dump(g.frame()));
            text.setText(String.join("\n", dump));
            List<Check> checks = new ArrayList<>();
            checks.add(Check.pass("accessible tree : nodes, SHA-256", dump.size() + ", "
                    + Checks.sha256(String.join("\n", dump))));
            checks.addAll(checks(g));
            view.setChecks(checks);
        });
    }

    @Override
    public void dispose(Component content) {
        if (gallery != null) {
            gallery.frame().dispose();
        }
        gallery = null;
        results = null;
        tree = null;
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private static String relation(AccessibleContext context, String key) {
        AccessibleRelation relation = context.getAccessibleRelationSet().get(key);
        if (relation == null) {
            return "none";
        }
        List<String> targets = new ArrayList<>();
        for (Object target : relation.getTarget()) {
            targets.add(target instanceof Accessible a ? String.valueOf(a.getAccessibleContext().getAccessibleName())
                    : "?");
        }
        return relation.toDisplayString(Locale.ENGLISH) + " " + targets;
    }

    private static String keys(AccessibleContext context) {
        if (!(context.getAccessibleComponent() instanceof AccessibleExtendedComponent extended)
                || extended.getAccessibleKeyBinding() == null) {
            return "none";
        }
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < extended.getAccessibleKeyBinding().getAccessibleKeyBindingCount(); i++) {
            keys.add(String.valueOf(extended.getAccessibleKeyBinding().getAccessibleKeyBinding(i)));
        }
        return String.join(" | ", keys);
    }

    private static List<String> events(AccessibleContext context, Runnable action) {
        List<String> events = new ArrayList<>();
        PropertyChangeListener listener = e -> events.add(e.getPropertyName() + " " + display(e.getOldValue()) + " -> "
                + display(e.getNewValue()));
        context.addPropertyChangeListener(listener);
        try {
            action.run();
        } finally {
            context.removePropertyChangeListener(listener);
        }
        return events;
    }

    private static String display(Object value) {
        if (value instanceof AccessibleState state) {
            return state.toDisplayString(Locale.ENGLISH);
        }
        if (value instanceof Accessible accessible) {
            return AccessibleDump.role(accessible.getAccessibleContext()) + " \""
                    + accessible.getAccessibleContext().getAccessibleName() + "\"";
        }
        return String.valueOf(value);
    }

    private static List<Check> checks(Gallery g) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("label : relation, key binding", "label for [Name:], pressed N", () -> {
            AccessibleContext c = g.label().getAccessibleContext();
            return relation(c, AccessibleRelation.LABEL_FOR) + ", " + keys(c);
        }));
        // the text field gets its name and the mnemonic of its label, but no labeled by relation
        checks.add(Checks.expect("text field : name from its label, relation, key binding", "Name:, none, pressed N",
                () -> {
                    AccessibleContext c = g.field().getAccessibleContext();
                    return c.getAccessibleName() + ", " + relation(c, AccessibleRelation.LABELED_BY) + ", " + keys(c);
                }));
        checks.add(Checks.expect("button : description (tooltip), key binding, icon, actions",
                "Prints the document, pressed P, 16x16 printer icon, 1 click", () -> {
                    AccessibleContext c = g.button().getAccessibleContext();
                    AccessibleExtendedComponent extended = (AccessibleExtendedComponent) c.getAccessibleComponent();
                    return extended.getToolTipText() + ", " + keys(c) + ", " + c.getAccessibleIcon()[0].getAccessibleIconWidth()
                            + "x" + c.getAccessibleIcon()[0].getAccessibleIconHeight() + " "
                            + c.getAccessibleIcon()[0].getAccessibleIconDescription() + ", "
                            + c.getAccessibleAction().getAccessibleActionCount() + " "
                            + c.getAccessibleAction().getAccessibleActionDescription(0);
                }));
        checks.add(Checks.expect("toggle button, check box : role, checked",
                "toggle button checked, check box checked", () -> AccessibleDump.role(g.bold().getAccessibleContext())
                        + " " + (g.bold().getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED)
                                ? "checked" : "unchecked")
                        + ", " + AccessibleDump.role(g.duplex().getAccessibleContext()) + " "
                        + (g.duplex().getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED)
                                ? "checked" : "unchecked")));
        checks.add(Checks.expect("check box : doAccessibleAction(0) events",
                "AccessibleState checked -> null, AccessibleState selected -> null, AccessibleValue 1 -> 0", () -> {
                    List<String> events = events(g.duplex().getAccessibleContext(),
                            () -> g.duplex().getAccessibleContext().getAccessibleAction().doAccessibleAction(0));
                    return String.join(", ", events.stream().distinct().toList());
                }));
        checks.add(Checks.expect("radio button : member of", "member of [Portrait, Landscape]",
                () -> relation(g.portrait().getAccessibleContext(), AccessibleRelation.MEMBER_OF)));
        checks.add(Checks.expect("combo box : name, selection, actions", "Paper, 1 A4, 1 togglePopup", () -> {
            AccessibleContext c = g.combo().getAccessibleContext();
            AccessibleSelection selection = c.getAccessibleSelection();
            return c.getAccessibleName() + ", " + selection.getAccessibleSelectionCount() + " "
                    + selection.getAccessibleSelection(0).getAccessibleContext().getAccessibleName() + ", "
                    + c.getAccessibleAction().getAccessibleActionCount() + " "
                    + c.getAccessibleAction().getAccessibleActionDescription(0);
        }));
        checks.add(Checks.expect("list : children, selected items, clear then add selection 4",
                "5, Second copy|Fourth copy, [4]",
                () -> {
                    AccessibleContext c = g.list().getAccessibleContext();
                    AccessibleSelection selection = c.getAccessibleSelection();
                    List<String> names = new ArrayList<>();
                    for (int i = 0; i < selection.getAccessibleSelectionCount(); i++) {
                        names.add(selection.getAccessibleSelection(i).getAccessibleContext().getAccessibleName());
                    }
                    selection.clearAccessibleSelection();
                    selection.addAccessibleSelection(4);
                    return c.getAccessibleChildrenCount() + ", " + String.join("|", names) + ", "
                            + Arrays.toString(g.list().getSelectedIndices());
                }));
        checks.add(Checks.expect("slider, progress bar, spinner : values", "30 in 0..100, 70 in 0..100, 2 in 1..99", () -> {
            List<String> values = new ArrayList<>();
            for (JComponent c : List.of(g.slider(), g.progress(), g.spinner())) {
                AccessibleValue value = c.getAccessibleContext().getAccessibleValue();
                values.add(value.getCurrentAccessibleValue() + " in " + value.getMinimumAccessibleValue() + ".."
                        + value.getMaximumAccessibleValue());
            }
            return String.join(", ", values);
        }));
        checks.add(Checks.expect("slider : setCurrentAccessibleValue(55) events", "AccessibleValue 30 -> 55", () -> String
                .join(", ", events(g.slider().getAccessibleContext(),
                        () -> g.slider().getAccessibleContext().getAccessibleValue().setCurrentAccessibleValue(55)))));
        checks.add(Checks.expect("spinner : actions, increment", "2 increment|decrement, 3", () -> {
            AccessibleContext c = g.spinner().getAccessibleContext();
            String actions = c.getAccessibleAction().getAccessibleActionCount() + " "
                    + c.getAccessibleAction().getAccessibleActionDescription(0) + "|"
                    + c.getAccessibleAction().getAccessibleActionDescription(1);
            c.getAccessibleAction().doAccessibleAction(0);
            return actions + ", " + g.spinner().getValue();
        }));
        checks.add(Checks.expect("text area : sentence, word, line (extended text), text range",
                "Second line. , line, Second line. Third sentence!\n, line of", () -> {
                    AccessibleText text = g.area().getAccessibleContext().getAccessibleText();
                    AccessibleExtendedText extended = (AccessibleExtendedText) text;
                    return text.getAtIndex(AccessibleText.SENTENCE, 25) + ", " + text.getAtIndex(AccessibleText.WORD, 8)
                            + ", " + extended.getTextSequenceAt(AccessibleExtendedText.LINE, 25).text + ", "
                            + extended.getTextRange(6, 13);
                }));
        checks.add(Checks.expect("text area : editable text (insert, replace, delete)",
                "Draft: First line of notes.\nSecond line. Third sentence! -> Draft: First page of notes.\nSecond line.",
                () -> {
                    AccessibleEditableText editable = g.area().getAccessibleContext().getAccessibleEditableText();
                    editable.insertTextAtIndex(0, "Draft: ");
                    String inserted = g.area().getText();
                    editable.replaceText(13, 17, "page");
                    editable.delete(g.area().getText().indexOf(" Third"), g.area().getText().length());
                    return inserted + " -> " + g.area().getText();
                }));
        checks.add(Checks.expect("text field : text events of setText", "AccessibleText null -> 0, AccessibleCaret 15 -> 0",
                () -> String.join(", ", events(g.field().getAccessibleContext(), () -> g.field().setText("Q")).stream()
                        .limit(2).toList())));
        // the text of a password field is read as echo characters
        checks.add(Checks.expect("password field : role, word at 0, characters",
                "password text, \u2022\u2022\u2022\u2022\u2022\u2022, 6", () -> {
            AccessibleContext c = g.password().getAccessibleContext();
            AccessibleText text = c.getAccessibleText();
            return AccessibleDump.role(c) + ", " + text.getAtIndex(AccessibleText.WORD, 0) + ", " + text.getCharCount();
        }));
        checks.add(Checks.expect("editor pane : links, link texts and targets, mime types",
                "2, Quarkus -> https://quarkus.io | the JDK documentation -> https://docs.oracle.com, not streamable", () -> {
                    AccessibleContext c = g.html().getAccessibleContext();
                    AccessibleHypertext hypertext = (AccessibleHypertext) c.getAccessibleText();
                    List<String> links = new ArrayList<>();
                    for (int i = 0; i < hypertext.getLinkCount(); i++) {
                        AccessibleHyperlink link = hypertext.getLink(i);
                        links.add(link.getAccessibleActionDescription(0) + " -> " + link.getAccessibleActionObject(0));
                    }
                    String mime = c instanceof AccessibleStreamable streamable
                            ? Arrays.toString(Arrays.stream(streamable.getMimeTypes()).map(Object::toString)
                                    .map(s -> s.replaceAll("; class=.*", "")).distinct().toArray())
                            : "not streamable";
                    return hypertext.getLinkCount() + ", " + String.join(" | ", links) + ", " + mime;
                }));
        checks.add(Checks.expect("table : rows x columns, headers, cell (1, 0), selected rows",
                "3x3, Section|Pages|Print, Summary, [1]", () -> {
                    AccessibleTable table = g.table().getAccessibleContext().getAccessibleTable();
                    AccessibleTable header = table.getAccessibleColumnHeader();
                    List<String> headers = new ArrayList<>();
                    for (int col = 0; col < header.getAccessibleColumnCount(); col++) {
                        headers.add(header.getAccessibleAt(0, col).getAccessibleContext().getAccessibleName());
                    }
                    return table.getAccessibleRowCount() + "x" + table.getAccessibleColumnCount() + ", "
                            + String.join("|", headers) + ", "
                            + table.getAccessibleAt(1, 0).getAccessibleContext().getAccessibleName() + ", "
                            + Arrays.toString(table.getSelectedAccessibleRows());
                }));
        checks.add(Checks.expect("tree : children of the root, expanded, selected node",
                "Chapters|Appendix, expanded, selected",
                () -> {
                    AccessibleContext c = g.treeView().getAccessibleContext();
                    AccessibleContext root = c.getAccessibleChild(0).getAccessibleContext();
                    List<String> names = new ArrayList<>();
                    for (int i = 0; i < root.getAccessibleChildrenCount(); i++) {
                        names.add(root.getAccessibleChild(i).getAccessibleContext().getAccessibleName());
                    }
                    AccessibleContext chapters = root.getAccessibleChild(0).getAccessibleContext();
                    return String.join("|", names) + ", "
                            + (chapters.getAccessibleStateSet().contains(AccessibleState.EXPANDED) ? "expanded"
                                    : "collapsed")
                            + ", " + (chapters.getAccessibleChild(0).getAccessibleContext().getAccessibleStateSet()
                                    .contains(AccessibleState.SELECTED) ? "selected" : "not selected");
                }));
        checks.add(Checks.expect("tabbed pane : role, tabs, selected", "page tab list, General|Layout|Advanced, Layout", () -> {
            AccessibleContext c = g.tabs().getAccessibleContext();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < c.getAccessibleChildrenCount(); i++) {
                names.add(c.getAccessibleChild(i).getAccessibleContext().getAccessibleName());
            }
            return AccessibleDump.role(c) + ", " + String.join("|", names) + ", "
                    + c.getAccessibleSelection().getAccessibleSelection(0).getAccessibleContext().getAccessibleName();
        }));
        checks.add(Checks.expect("menu item : key bindings (the mnemonic, not the accelerator)", "pressed O",
                () -> keys(g.open().getAccessibleContext())));
        checks.add(Checks.expect("menu bar, tool bar : roles, children", "menu bar 2, tool bar 4", () -> {
            AccessibleContext bar = g.menuBar().getAccessibleContext();
            AccessibleContext tools = g.toolBar().getAccessibleContext();
            return AccessibleDump.role(bar) + " " + bar.getAccessibleChildrenCount() + ", " + AccessibleDump.role(tools)
                    + " " + tools.getAccessibleChildrenCount();
        }));
        checks.add(Checks.expect("text area scroll pane : titled border text", "Notes", () -> {
            Component scroll = g.area().getParent().getParent();
            AccessibleContext c = ((Accessible) scroll).getAccessibleContext();
            return ((AccessibleExtendedComponent) c.getAccessibleComponent()).getTitledBorderText();
        }));
        checks.add(Checks.expect("hidden frame : role, states", "frame [enabled,focusable,resizable]", () -> AccessibleDump
                .role(g.frame().getAccessibleContext()) + " [" + AccessibleDump.states(g.frame().getAccessibleContext())
                + "]"));
        checks.add(Checks.expect("AccessibleRole.TABLE / AccessibleState.EXPANDED in de, ja, zh_CN",
                "Tabelle / eingeblendet, \u8868 / \u5c55\u958b, \u8868 / \u5df2\u5c55\u5f00",
                () -> String.join(", ", List.of(Locale.GERMAN, Locale.JAPANESE, Locale.SIMPLIFIED_CHINESE).stream()
                        .map(l -> AccessibleRole.TABLE.toDisplayString(l) + " / "
                                + AccessibleState.EXPANDED.toDisplayString(l))
                        .toList())));
        return checks;
    }
}
