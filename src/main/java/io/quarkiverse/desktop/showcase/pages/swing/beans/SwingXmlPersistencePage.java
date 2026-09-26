package io.quarkiverse.desktop.showcase.pages.swing.beans;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.beans.XmlSupport;

/**
 * {@link java.beans.XMLEncoder} and {@link java.beans.XMLDecoder} of a Swing form : a panel with a tabbed pane, grid bag
 * and box layouts, borders (titled, line, matte, compound, empty), combo box and list models, a tree of
 * {@code DefaultMutableTreeNode}s, text components, a slider and buttons ; a frame with a menu bar and key stroke
 * accelerators ; the tooltip manager (a shared instance). The persistence delegates of these classes
 * ({@code java.beans.MetaData$javax_swing_...}) are found by name.
 * <p>
 * The decoded form is shown next to a form built like the encoded one. Swing variant of the AWT page
 * {@code pages.beans.XmlPersistencePage}.
 */
@Singleton
public class SwingXmlPersistencePage implements FeaturePage {

    @Override
    public String id() {
        return "beans-xml-persistence-swing";
    }

    @Override
    public String title() {
        return "XMLEncoder and XMLDecoder (Swing form)";
    }

    @Override
    public String category() {
        return Categories.A11Y_BEANS;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Component build() {
        List<Check> checks = new ArrayList<>();
        JPanel encoded = form();
        XmlSupport.Encoded formXml = XmlSupport.encode(encoded);
        XmlSupport.Decoded formDecoded = XmlSupport.decode(formXml.xml(), null);
        Component decoded = formDecoded.objects().isEmpty() ? Ui.text("not decoded")
                : (Component) formDecoded.objects().get(0);
        checks.add(Checks.expect("form : encoding exceptions", "none", () -> exceptions(formXml.exceptions())));
        checks.add(Checks.expect("form : decoding exceptions, objects", "none, 1",
                () -> exceptions(formDecoded.exceptions()) + ", " + formDecoded.objects().size()));
        checks.add(Check.pass("form XML : lines, SHA-256", formXml.lines() + ", " + formXml.sha256()));
        // the persistence delegate of DefaultListModel writes the size of the model, not its elements (JDK behavior)
        checks.add(Checks.expect("form : decoded tree (list model elements lost by the JDK encoder)",
                describe(encoded).replace("JList [Cover, Summary, Details]", "JList [null, null, null]"),
                () -> describe(decoded)));
        checks.add(Checks.expect("form XML : persistence delegates at work",
                "JTabbedPane addTab, Box, combo box items, DefaultListModel, DefaultMutableTreeNode, TitledBorder, "
                        + "MatteBorder, GridBagConstraints",
                () -> encodings(formXml.xml())));

        JFrame frame = frame();
        XmlSupport.Encoded frameXml = XmlSupport.encode(frame);
        XmlSupport.Decoded frameDecoded = XmlSupport.decode(frameXml.xml(), null);
        // the mnemonic of a JMenu : the encoder replays setDisplayedMnemonicIndex before the text is set (JDK behavior)
        checks.add(Checks.expect("frame : encoding exceptions (JMenu mnemonic)", "1 : java.lang.Exception: Encoder: "
                + "discarding statement JMenu.setDisplayedMnemonicIndex(Integer); <- java.lang.IllegalArgumentException: "
                + "index == 0", () -> exceptions(frameXml.exceptions())));
        checks.add(Checks.expect("frame : decoding exceptions, objects",
                "1 : java.lang.IllegalArgumentException: index == 0, 1",
                () -> exceptions(frameDecoded.exceptions()) + ", " + frameDecoded.objects().size()));
        checks.add(Check.pass("frame XML : lines, SHA-256", frameXml.lines() + ", " + frameXml.sha256()));
        checks.add(Checks.expect("frame : decoded title, menus and accelerators", menus(frame), () -> {
            JFrame copy = (JFrame) frameDecoded.objects().get(0);
            try {
                return menus(copy);
            } finally {
                copy.dispose();
            }
        }));
        frame.dispose();

        XmlSupport.Encoded valuesXml = XmlSupport.encode(new ArrayList<>(List.of(
                KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke('x'), ToolTipManager.sharedInstance())));
        checks.add(Checks.expect("KeyStroke, ToolTipManager.sharedInstance() : round trip",
                "shift ctrl pressed P, typed x, the shared instance", () -> {
                    List<?> values = (List<?>) XmlSupport.decode(valuesXml.xml(), null).objects().get(0);
                    return values.get(0) + ", " + values.get(1) + ", "
                            + (values.get(2) == ToolTipManager.sharedInstance() ? "the shared instance" : "another");
                }));
        checks.add(Check.pass("values XML : lines, SHA-256", valuesXml.lines() + ", " + valuesXml.sha256()));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new Ui.StackLayout(true, 14));
        content.add(Ui.text("A Swing form encoded with XMLEncoder and decoded with XMLDecoder. Left : built by code, "
                + "right : decoded from the XML written for it.", 1000));
        content.add(Ui.row(24, Ui.column(4, Ui.caption("Built by code"), form()),
                Ui.column(4, Ui.caption("Decoded from XML"), decoded)));
        content.add(Ui.title("XML of the form (" + formXml.lines() + " lines)"));
        content.add(Ui.text(XmlSupport.excerpt(formXml.xml(), 60), new Font(Font.MONOSPACED, Font.PLAIN, 11),
                Ui.TEXT_COLOR, 1000));
        content.add(ChecksView.table("Swing persistence", checks));
        return content;
    }

    // ----------------------------------------------------------------------------------------------------- form

    static JPanel form() {
        JPanel form = new JPanel(new BorderLayout(8, 8));
        form.setBackground(new Color(0xECEFF1));
        form.setBorder(new CompoundBorder(BorderFactory.createLineBorder(new Color(0x90A4AE), 2),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        form.setPreferredSize(new Dimension(470, 300));
        JLabel title = new JLabel("Print settings", JLabel.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        title.setForeground(new Color(0x1E88E5));
        form.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        // a grid bag layout with one component only : the persistence delegate of GridBagLayout writes the constraints
        // in the order of a hash table keyed by components (identity hash codes), a random order with several ones
        JPanel general = new JPanel(new java.awt.GridLayout(0, 2, 4, 2));
        general.setBorder(BorderFactory.createTitledBorder("Printer"));
        general.add(new JLabel("Printer:"));
        JComboBox<String> printer = new JComboBox<>(new DefaultComboBoxModel<>(new String[] { "PostScript", "PDF" }));
        printer.setSelectedIndex(1);
        general.add(printer);
        general.add(new JLabel("Title:"));
        general.add(new JTextField("Quarterly report", 12));
        general.add(new JCheckBox("Collate copies", true));
        JPanel qualityPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        JSlider quality = new JSlider(0, 100, 40);
        quality.setMajorTickSpacing(25);
        quality.setPaintTicks(true);
        qualityPanel.add(quality, c);
        general.add(qualityPanel);
        tabs.addTab("General", general);

        Box pages = Box.createVerticalBox();
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Cover");
        model.addElement("Summary");
        model.addElement("Details");
        JList<String> list = new JList<>(model);
        list.setVisibleRowCount(3);
        pages.add(new JScrollPane(list));
        pages.add(Box.createVerticalStrut(6));
        JTextArea notes = new JTextArea("Notes for the printer", 2, 20);
        notes.setBorder(BorderFactory.createMatteBorder(1, 4, 1, 1, new Color(0xFB8C00)));
        pages.add(notes);
        tabs.addTab("Pages", pages);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Document");
        DefaultMutableTreeNode chapters = new DefaultMutableTreeNode("Chapters");
        chapters.add(new DefaultMutableTreeNode("Introduction"));
        root.add(chapters);
        root.add(new DefaultMutableTreeNode("Appendix"));
        tabs.addTab("Outline", new JScrollPane(new JTree(root)));
        form.add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton ok = new JButton("OK");
        ok.setMnemonic(KeyEvent.VK_O);
        ok.setToolTipText("Apply the settings");
        buttons.add(ok);
        buttons.add(new JButton("Cancel"));
        form.add(buttons, BorderLayout.SOUTH);
        return form;
    }

    private static JFrame frame() {
        JFrame frame = new JFrame("Encoded frame");
        // explicit name : a frame without name gets a generated one from a process wide counter
        frame.setName("encodedFrame");
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem print = new JMenuItem("Print...");
        print.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        file.add(print);
        file.addSeparator();
        JMenuItem exit = new JMenuItem("Exit");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
        file.add(exit);
        bar.add(file);
        bar.add(new JMenu("Help"));
        frame.setJMenuBar(bar);
        frame.getContentPane().add(new JLabel("frame content"), BorderLayout.CENTER);
        return frame;
    }

    // ----------------------------------------------------------------------------------------------------- format

    private static String exceptions(List<String> exceptions) {
        return exceptions.isEmpty() ? "none" : exceptions.size() + " : " + String.join(" | ", exceptions);
    }

    private static String encodings(String xml) {
        List<String> found = new ArrayList<>();
        if (xml.contains("<void method=\"addTab\">")) {
            found.add("JTabbedPane addTab");
        }
        if (xml.contains("<object class=\"javax.swing.Box\"")) {
            found.add("Box");
        }
        if (xml.contains("<string>PostScript</string>")) {
            found.add("combo box items");
        }
        if (xml.contains("javax.swing.DefaultListModel")) {
            found.add("DefaultListModel");
        }
        if (xml.contains("javax.swing.tree.DefaultMutableTreeNode")) {
            found.add("DefaultMutableTreeNode");
        }
        if (xml.contains("javax.swing.border.TitledBorder")) {
            found.add("TitledBorder");
        }
        if (xml.contains("javax.swing.border.MatteBorder")) {
            found.add("MatteBorder");
        }
        if (xml.contains("java.awt.GridBagConstraints")) {
            found.add("GridBagConstraints");
        }
        return String.join(", ", found);
    }

    private static String menus(JFrame frame) {
        List<String> menus = new ArrayList<>();
        JMenuBar bar = frame.getJMenuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            List<String> items = new ArrayList<>();
            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem item = menu.getItem(j);
                items.add(item == null ? "separator" : item.getText() + " " + item.getAccelerator());
            }
            menus.add(menu.getText() + " mnemonic " + menu.getMnemonic() + " " + items);
        }
        return frame.getTitle() + " " + menus + ", content " + frame.getContentPane().getComponentCount();
    }

    /**
     * A deterministic description of a Swing component tree.
     */
    static String describe(Component component) {
        StringBuilder sb = new StringBuilder(component.getClass().getSimpleName());
        if (component instanceof JLabel label) {
            sb.append(" \"").append(label.getText()).append('"');
        } else if (component instanceof AbstractButton button) {
            sb.append(" \"").append(button.getText()).append("\" ").append(button.isSelected());
            if (button.getMnemonic() != 0) {
                sb.append(" mnemonic ").append(button.getMnemonic());
            }
            if (button.getToolTipText() != null) {
                sb.append(" tooltip \"").append(button.getToolTipText()).append('"');
            }
        } else if (component instanceof JTextComponent text) {
            sb.append(" \"").append(text.getText()).append('"');
        } else if (component instanceof JComboBox<?> combo) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < combo.getItemCount(); i++) {
                items.add(String.valueOf(combo.getItemAt(i)));
            }
            sb.append(' ').append(items).append(" selected ").append(combo.getSelectedIndex());
        } else if (component instanceof JList<?> list) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < list.getModel().getSize(); i++) {
                items.add(String.valueOf(list.getModel().getElementAt(i)));
            }
            sb.append(' ').append(items).append(" rows ").append(list.getVisibleRowCount());
        } else if (component instanceof JSlider slider) {
            sb.append(' ').append(slider.getValue()).append(" ticks ").append(slider.getMajorTickSpacing());
        } else if (component instanceof JTree tree) {
            sb.append(' ').append(tree(tree.getModel(), tree.getModel().getRoot()));
        } else if (component instanceof JTabbedPane tabs) {
            List<String> titles = new ArrayList<>();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                titles.add(tabs.getTitleAt(i));
            }
            sb.append(" tabs ").append(titles);
        }
        if (component instanceof JComponent c && c.getBorder() != null
                && !(c.getBorder() instanceof javax.swing.plaf.UIResource)) {
            sb.append(" border ").append(border(c.getBorder()));
        }
        if (component.isFontSet() && !(component.getFont() instanceof javax.swing.plaf.UIResource)) {
            Font font = component.getFont();
            sb.append(" font ").append(font.getFamily()).append(' ').append(font.getStyle()).append(' ').append(font.getSize());
        }
        if (component.isForegroundSet() && !(component.getForeground() instanceof javax.swing.plaf.UIResource)) {
            sb.append(" fg ").append(Checks.argb(component.getForeground().getRGB()));
        }
        if (component.isBackgroundSet() && !(component.getBackground() instanceof javax.swing.plaf.UIResource)) {
            sb.append(" bg ").append(Checks.argb(component.getBackground().getRGB()));
        }
        if (component instanceof Container container && !(component instanceof JComboBox)
                && !(component instanceof AbstractButton) && !(component instanceof JSlider) && !(component instanceof JTree)
                && !(component instanceof JList) && !(component instanceof JTextComponent)) {
            if (container.getLayout() != null && !(container.getLayout() instanceof javax.swing.plaf.UIResource)) {
                sb.append(" layout ").append(container.getLayout().getClass().getSimpleName());
            }
            List<String> children = new ArrayList<>();
            for (Component child : container.getComponents()) {
                if (!(child instanceof javax.swing.plaf.UIResource) && !(child instanceof javax.swing.JScrollBar)) {
                    children.add(describe(child));
                }
            }
            if (!children.isEmpty()) {
                sb.append(" (").append(String.join(", ", children)).append(')');
            }
        }
        return sb.toString();
    }

    private static String border(Border border) {
        if (border instanceof CompoundBorder compound) {
            return "Compound(" + border(compound.getOutsideBorder()) + ", " + border(compound.getInsideBorder()) + ")";
        }
        if (border instanceof TitledBorder titled) {
            return "Titled \"" + titled.getTitle() + "\"";
        }
        return border.getClass().getSimpleName();
    }

    private static String tree(TreeModel model, Object node) {
        int count = model.getChildCount(node);
        if (count == 0) {
            return String.valueOf(node);
        }
        List<String> children = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            children.add(tree(model, model.getChild(node, i)));
        }
        return node + children.toString();
    }
}
