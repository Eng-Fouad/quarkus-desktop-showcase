package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
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
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The same gallery of about 50 Swing components, built under the current look and feel : every look and feel page shows
 * it, so that the look and feels render identical content.
 * <p>
 * Plain class (not a CDI bean) : {@link #build()} creates a fresh gallery, on the EDT, with fixed texts, values,
 * selections and sizes (no animation, no focus, no clock). The popup menu cannot be shown inside a panel : it is
 * painted into an image (under the same look and feel) shown in the gallery, its items being part of
 * {@link #components()}.
 */
public final class LafGallery {

    /** Width of the gallery. */
    public static final int GALLERY_WIDTH = 1000;
    /** Width inside the gallery border. */
    static final int INNER_WIDTH = GALLERY_WIDTH - 16;

    private final JPanel panel;
    private final List<JComponent> detached = new ArrayList<>();
    private final Map<String, JComponent> keyed = new LinkedHashMap<>();

    private LafGallery() {
        // fixed widths, heights depending on the look and feel (fonts, insets). Stack layouts : components always get
        // their preferred size (a grid bag layout shrinks everything to the minimum sizes when a row is too wide)
        panel = new FixedWidthPanel(new Ui.StackLayout(true, 8), GALLERY_WIDTH);
        panel.setName("gallery");
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(columns());
        panel.add(containers());
        panel.add(menus());
    }

    /**
     * A new gallery, built under the current look and feel.
     */
    public static LafGallery build() {
        return new LafGallery();
    }

    public JPanel panel() {
        return panel;
    }

    /**
     * Every component of the gallery : the panel tree and the popup menu tree.
     */
    public List<JComponent> components() {
        List<JComponent> all = new ArrayList<>(LafSupport.components(panel));
        for (JComponent c : detached) {
            all.addAll(LafSupport.components(c));
        }
        return all;
    }

    /**
     * The components whose key bindings and action maps are checked, by name.
     */
    public Map<String, JComponent> keyed() {
        return keyed;
    }

    /**
     * The state of the gallery components (selections, values, sizes that do not depend on fonts) : the same under
     * every look and feel, on every operating system.
     */
    public List<Check> stateChecks() {
        List<Check> checks = new ArrayList<>();
        JTable table = (JTable) keyed.get("JTable");
        checks.add(Checks.expect("JTable selected row (sorted view)", "1 = Beta",
                () -> table.getSelectedRow() + " = " + table.getValueAt(table.getSelectedRow(), 0)));
        checks.add(Checks.expect("JTable sort keys", "0 ASCENDING", () -> table.getRowSorter().getSortKeys().stream()
                .map(k -> k.getColumn() + " " + k.getSortOrder()).collect(java.util.stream.Collectors.joining(","))));
        checks.add(Checks.expect("JTable column classes", "String Integer Boolean", () -> table.getColumnClass(0)
                .getSimpleName() + " " + table.getColumnClass(1).getSimpleName() + " "
                + table.getColumnClass(2).getSimpleName()));
        JList<?> list = (JList<?>) keyed.get("JList");
        checks.add(Checks.expect("JList selected value", "Beta", list::getSelectedValue));
        JTree tree = (JTree) keyed.get("JTree");
        checks.add(Checks.expect("JTree selection", "[Root, Documents, Report]", () -> tree.getSelectionPath()
                .toString()));
        checks.add(Checks.expect("JTree row count", 5, tree::getRowCount));
        checks.add(Checks.expect("JSlider value", 60, () -> ((JSlider) keyed.get("JSlider")).getValue()));
        checks.add(Checks.expect("JSlider label table", "0 25 50 75 100", () -> {
            java.util.TreeMap<Integer, String> labels = new java.util.TreeMap<>();
            java.util.Dictionary<?, ?> labelTable = ((JSlider) keyed.get("JSlider")).getLabelTable();
            for (Object key : java.util.Collections.list(labelTable.keys())) {
                labels.put((Integer) key, ((JLabel) labelTable.get(key)).getText());
            }
            return String.join(" ", labels.values());
        }));
        checks.add(Checks.expect("JFormattedTextField text / value", "1,234.50 / 1234.5", () -> {
            JFormattedTextField f = (JFormattedTextField) keyed.get("JFormattedTextField");
            return f.getText() + " / " + f.getValue();
        }));
        checks.add(Checks.expect("JPasswordField password length", 6,
                () -> ((JPasswordField) keyed.get("JPasswordField")).getPassword().length));
        checks.add(Checks.expect("JComboBox selected item", "Combo item 2",
                () -> ((JComboBox<?>) keyed.get("JComboBox")).getSelectedItem()));
        checks.add(Checks.expect("JSpinner value", 42, () -> ((JSpinner) keyed.get("JSpinner")).getValue()));
        checks.add(Checks.expect("JTabbedPane selected tab", "Advanced", () -> {
            JTabbedPane tabs = (JTabbedPane) keyed.get("JTabbedPane");
            return tabs.getTitleAt(tabs.getSelectedIndex());
        }));
        checks.add(Checks.expect("JInternalFrame selected", true, () -> ((JInternalFrame) keyed.get("JInternalFrame"))
                .isSelected()));
        checks.add(Checks.expect("JTextArea line count", 3, () -> ((JTextArea) keyed.get("JTextArea")).getLineCount()));
        checks.add(Checks.expect("JMenuBar menus", 4, () -> ((JMenuBar) keyed.get("JMenuBar")).getMenuCount()));
        checks.add(Checks.expect("JToolBar components", 5, () -> keyed.get("JToolBar").getComponentCount()));
        checks.add(Checks.expect("JOptionPane options", "Yes No Cancel", () -> optionTexts(
                (JOptionPane) keyed.get("JOptionPane"))));
        checks.add(Checks.expect("popup menu items", 8, () -> ((JPopupMenu) detached.get(0)).getComponentCount()));
        checks.add(Checks.expect("popup accelerator (\"ctrl shift S\")", "shift ctrl pressed S",
                () -> ((JMenuItem) ((JPopupMenu) detached.get(0)).getComponent(1)).getAccelerator().toString()));
        return checks;
    }

    private static String optionTexts(JOptionPane pane) {
        List<String> texts = new ArrayList<>();
        for (JComponent c : LafSupport.components(pane)) {
            if (c instanceof JButton b) {
                texts.add(b.getText());
            }
        }
        return String.join(" ", texts);
    }

    private <C extends JComponent> C key(String name, C component) {
        keyed.put(name, component);
        return component;
    }

    // ----------------------------------------------------------------------------------------------------- columns

    private JComponent columns() {
        JPanel columns = new FixedWidthPanel(new GridLayout(1, 4, 8, 0), INNER_WIDTH);
        columns.add(buttons());
        columns.add(text());
        columns.add(values());
        columns.add(structure());
        return columns;
    }

    private JComponent buttons() {
        JButton disabled = new JButton("Disabled");
        disabled.setEnabled(false);
        JToggleButton toggle = new JToggleButton("Toggle");
        toggle.setSelected(true);
        JCheckBox checked = new JCheckBox("Checked");
        checked.setSelected(true);
        JRadioButton radio = new JRadioButton("Radio");
        JRadioButton other = new JRadioButton("Other");
        ButtonGroup group = new ButtonGroup();
        group.add(radio);
        group.add(other);
        radio.setSelected(true);
        JCheckBox disabledCheck = new JCheckBox("Disabled");
        disabledCheck.setSelected(true);
        disabledCheck.setEnabled(false);
        JRadioButton disabledRadio = new JRadioButton("Off");
        disabledRadio.setEnabled(false);
        JLabel html = new JLabel("<html><b>HTML</b> <i>label</i> <font color=#c62828>red</font> <u>text</u></html>");
        return section("Buttons",
                row(key("JButton", new JButton("Button")), disabled),
                row(new JButton("Icon", new SwatchIcon(0x1E88E5)), key("JToggleButton", toggle)),
                row(key("JCheckBox", checked), new JCheckBox("Check")),
                row(key("JRadioButton", radio), other),
                row(disabledCheck, disabledRadio),
                row(html));
    }

    private JComponent text() {
        JLabel label = key("JLabel (mnemonic)", new JLabel("Name:"));
        JTextField field = key("JTextField", new JTextField("Text field", 9));
        label.setLabelFor(field);
        label.setDisplayedMnemonic(KeyEvent.VK_N);
        JPasswordField password = key("JPasswordField", new JPasswordField("secret", 12));
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        JFormattedTextField formatted = key("JFormattedTextField", new JFormattedTextField(format));
        formatted.setValue(1234.5);
        formatted.setColumns(12);
        JSpinner number = key("JSpinner", new JSpinner(new SpinnerNumberModel(42, 0, 100, 1)));
        JSpinner list = new JSpinner(new SpinnerListModel(List.of("North", "East", "South", "West")));
        list.setValue("East");
        JComboBox<String> combo = key("JComboBox",
                new JComboBox<>(new String[] { "Combo item 1", "Combo item 2", "Combo item 3" }));
        combo.setSelectedIndex(1);
        JComboBox<String> editable = new JComboBox<>(new String[] { "Editable", "Second" });
        editable.setEditable(true);
        JTextArea area = key("JTextArea", new JTextArea("Text area\nsecond line\nthird line", 3, 16));
        JScrollPane areaScroll = new JScrollPane(area);
        return section("Text",
                row(label, field),
                row(password),
                row(formatted),
                row(number, list),
                row(combo),
                row(editable),
                row(areaScroll));
    }

    private JComponent values() {
        JSlider slider = key("JSlider", new JSlider(0, 100, 60));
        slider.setMajorTickSpacing(25);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setPreferredSize(new Dimension(200, slider.getPreferredSize().height));
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(65);
        progress.setStringPainted(true);
        progress.setPreferredSize(new Dimension(200, progress.getPreferredSize().height));
        JScrollBar scrollBar = key("JScrollBar", new JScrollBar(JScrollBar.HORIZONTAL, 30, 20, 0, 100));
        scrollBar.setPreferredSize(new Dimension(200, scrollBar.getPreferredSize().height));
        JList<String> list = key("JList", new JList<>(new String[] { "Alpha", "Beta", "Gamma", "Delta", "Epsilon" }));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(1);
        list.setVisibleRowCount(4);
        list.setFixedCellWidth(176);
        JScrollPane listScroll = key("JScrollPane", new JScrollPane(list));
        JSeparator separator = new JSeparator();
        separator.setPreferredSize(new Dimension(200, separator.getPreferredSize().height));
        JLabel iconLabel = new JLabel("Label with icon", new SwatchIcon(0x43A047), SwingConstants.LEADING);
        return section("Values", row(slider), row(progress), row(scrollBar), row(listScroll), row(separator),
                row(iconLabel));
    }

    private JComponent structure() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        DefaultMutableTreeNode documents = new DefaultMutableTreeNode("Documents");
        documents.add(new DefaultMutableTreeNode("Report"));
        documents.add(new DefaultMutableTreeNode("Notes"));
        DefaultMutableTreeNode pictures = new DefaultMutableTreeNode("Pictures");
        pictures.add(new DefaultMutableTreeNode("Holiday"));
        root.add(documents);
        root.add(pictures);
        JTree tree = key("JTree", new JTree(new DefaultTreeModel(root)));
        tree.expandRow(0);
        tree.expandRow(1);
        tree.setSelectionPath(new TreePath(new Object[] { root, documents, documents.getChildAt(0) }));
        tree.setVisibleRowCount(5);
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setPreferredSize(new Dimension(200, treeScroll.getPreferredSize().height));

        DefaultTableModel model = new DefaultTableModel(new Object[][] {
                { "Gamma", 30, Boolean.TRUE },
                { "Alpha", 10, Boolean.FALSE },
                { "Delta", 40, Boolean.TRUE },
                { "Beta", 20, Boolean.FALSE } }, new Object[] { "Name", "Size", "Done" }) {
            @Override
            public Class<?> getColumnClass(int column) {
                return switch (column) {
                    case 1 -> Integer.class;
                    case 2 -> Boolean.class;
                    default -> String.class;
                };
            }
        };
        JTable table = key("JTable", new JTable(model));
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        table.setRowSelectionInterval(1, 1);
        table.setPreferredScrollableViewportSize(new Dimension(200, 4 * table.getRowHeight()));
        JScrollPane tableScroll = new JScrollPane(table);
        // what JTable.addNotify does too : the header is part of the preferred size before the gallery is displayable
        tableScroll.setColumnHeaderView(table.getTableHeader());
        return section("Trees and tables", row(treeScroll), row(tableScroll));
    }

    // -------------------------------------------------------------------------------------------------- containers

    private JComponent containers() {
        JTabbedPane tabs = key("JTabbedPane", new JTabbedPane());
        JPanel general = new JPanel(new FlowLayout(FlowLayout.LEFT));
        general.add(new JLabel("General settings"));
        JPanel advanced = new JPanel(new FlowLayout(FlowLayout.LEFT));
        advanced.add(new JCheckBox("Advanced option", true));
        tabs.addTab("General", general);
        tabs.addTab("Advanced", advanced);
        tabs.addTab("Disabled", new JPanel());
        tabs.setEnabledAt(2, false);
        tabs.setSelectedIndex(1);
        tabs.setPreferredSize(new Dimension(280, 150));

        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("Left", SwingConstants.CENTER));
        JPanel right = new JPanel(new BorderLayout());
        right.add(new JLabel("Right", SwingConstants.CENTER));
        JSplitPane split = key("JSplitPane", new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right));
        split.setOneTouchExpandable(true);
        split.setDividerLocation(90);
        split.setPreferredSize(new Dimension(180, 150));

        JDesktopPane desktop = key("JDesktopPane", new JDesktopPane());
        desktop.setPreferredSize(new Dimension(492, 150));
        GalleryInternalFrame active = new GalleryInternalFrame("Active frame");
        active.getContentPane().add(new JLabel("  Selected internal frame"));
        active.setBounds(8, 8, 230, 110);
        GalleryInternalFrame inactive = new GalleryInternalFrame("Inactive frame");
        inactive.getContentPane().add(new JLabel("  Internal frame"));
        inactive.setBounds(250, 8, 236, 86);
        GalleryInternalFrame iconified = new GalleryInternalFrame("Iconified");
        JInternalFrame.JDesktopIcon icon = iconified.getDesktopIcon();
        icon.setSize(icon.getPreferredSize());
        icon.setLocation(250, 104);
        desktop.add(inactive);
        desktop.add(active);
        desktop.add(icon);
        inactive.setVisible(true);
        active.setVisible(true);
        icon.setVisible(true);
        active.showSelected();
        keyed.put("JInternalFrame", active);

        JPanel row = new FixedWidthPanel(new FlowLayout(FlowLayout.LEFT, 8, 0), INNER_WIDTH);
        row.add(tabs);
        row.add(split);
        row.add(desktop);
        return row;
    }

    // ------------------------------------------------------------------------------------------------------- menus

    private JComponent menus() {
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        JMenuBar bar = key("JMenuBar", new JMenuBar());
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(new JMenuItem("New"));
        bar.add(file);
        JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);
        bar.add(edit);
        bar.add(new JMenu("View"));
        JMenu help = new JMenu("Help");
        help.setEnabled(false);
        bar.add(help);

        JToolBar toolBar = key("JToolBar", new JToolBar("Tools"));
        toolBar.add(new JButton("New"));
        toolBar.add(new JButton("Open", new SwatchIcon(0xFB8C00)));
        toolBar.addSeparator();
        JToggleButton bold = new JToggleButton("Bold");
        bold.setSelected(true);
        toolBar.add(bold);
        toolBar.add(new JToggleButton("Italic"));

        JToolTip tip = new JToolTip();
        tip.setTipText("Tool tip text");

        JPanel left = new FixedWidthPanel(new Ui.StackLayout(true, 8), 300);
        left.add(bar);
        left.add(toolBar);
        left.add(tip);

        JPopupMenu popup = popupMenu(ctrl);
        detached.add(popup);
        BufferedImage popupImage = renderPopup(popup);
        JLabel popupLabel = new JLabel(new ImageIconOf(popupImage));
        popupLabel.setName("popup menu (image)");
        popupLabel.setVerticalAlignment(SwingConstants.TOP);

        JOptionPane option = key("JOptionPane", new JOptionPane("Save the changes?", JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_CANCEL_OPTION));

        // the option pane makes its initial button the default button of the nearest root pane : its own root pane,
        // never the root pane of the window showing the gallery
        JRootPane optionRoot = new JRootPane();
        optionRoot.setContentPane(option);

        JPanel row = new FixedWidthPanel(new FlowLayout(FlowLayout.LEFT, 8, 0), INNER_WIDTH);
        row.add(left);
        row.add(popupLabel);
        row.add(optionRoot);
        return row;
    }

    private static JPopupMenu popupMenu(int ctrl) {
        JPopupMenu popup = new JPopupMenu("Popup");
        JMenuItem open = new JMenuItem("Open");
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ctrl));
        popup.add(open);
        JMenuItem saveAs = new JMenuItem("Save as...", new SwatchIcon(0x8E24AA));
        saveAs.setAccelerator(KeyStroke.getKeyStroke("ctrl shift S"));
        popup.add(saveAs);
        popup.add(new JCheckBoxMenuItem("Word wrap", true));
        ButtonGroup sizes = new ButtonGroup();
        JRadioButtonMenuItem large = new JRadioButtonMenuItem("Large", true);
        JRadioButtonMenuItem small = new JRadioButtonMenuItem("Small");
        sizes.add(large);
        sizes.add(small);
        popup.add(large);
        popup.add(small);
        popup.addSeparator();
        JMenu recent = new JMenu("Recent");
        recent.add(new JMenuItem("First"));
        popup.add(recent);
        JMenuItem disabled = new JMenuItem("Disabled");
        disabled.setEnabled(false);
        popup.add(disabled);
        return popup;
    }

    /**
     * The popup menu painted into an image (a popup menu cannot be a visible child of a panel).
     */
    private static BufferedImage renderPopup(JPopupMenu popup) {
        popup.setSize(popup.getPreferredSize());
        Snapshots.layout(popup);
        BufferedImage image = new BufferedImage(popup.getWidth(), popup.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            popup.print(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    // ----------------------------------------------------------------------------------------------------- layout

    private static JComponent section(String title, JComponent... rows) {
        JPanel section = new JPanel(new Ui.StackLayout(true, 0));
        section.setBorder(BorderFactory.createTitledBorder(title));
        for (JComponent row : rows) {
            section.add(row);
        }
        return section;
    }

    private static JComponent row(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        for (JComponent component : components) {
            row.add(component);
        }
        return row;
    }

    /**
     * A panel with a fixed preferred width (its height is the one of its layout).
     */
    static final class FixedWidthPanel extends JPanel {

        private final int width;

        FixedWidthPanel(java.awt.LayoutManager layout, int width) {
            super(layout);
            this.width = width;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, super.getPreferredSize().height);
        }
    }

    /**
     * A small colored square icon (no image resource : the same pixels everywhere).
     */
    static final class SwatchIcon implements Icon {

        private final int rgb;

        SwatchIcon(int rgb) {
            this.rgb = rgb;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(rgb));
                g2.fillRoundRect(x + 1, y + 1, 12, 12, 4, 4);
                g2.setColor(new Color(rgb).darker());
                g2.drawRoundRect(x + 1, y + 1, 12, 12, 4, 4);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    /**
     * An icon showing an image (an {@code ImageIcon} of a {@code BufferedImage} without a media tracker).
     */
    static final class ImageIconOf implements Icon {

        private final BufferedImage image;

        ImageIconOf(BufferedImage image) {
            this.image = image;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.drawImage(image, x, y, null);
        }

        @Override
        public int getIconWidth() {
            return image.getWidth();
        }

        @Override
        public int getIconHeight() {
            return image.getHeight();
        }
    }

    /**
     * An internal frame shown selected without being focused (a real selection needs the frame to be showing and moves
     * the keyboard focus) : the protected {@code isSelected} field is set and the property change fired, as
     * {@link JInternalFrame#setSelected(boolean)} does. Also an application subclass of a Swing component (AWT looks
     * up its declared {@code coalesceEvents} method reflectively).
     */
    static final class GalleryInternalFrame extends JInternalFrame {

        GalleryInternalFrame(String title) {
            super(title, true, true, true, true);
        }

        void showSelected() {
            isSelected = true;
            firePropertyChange(IS_SELECTED_PROPERTY, Boolean.FALSE, Boolean.TRUE);
            repaint();
        }
    }
}
