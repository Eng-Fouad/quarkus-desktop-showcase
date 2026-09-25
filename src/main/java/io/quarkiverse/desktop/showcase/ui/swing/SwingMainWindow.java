package io.quarkiverse.desktop.showcase.ui.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.AbstractMainWindow;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;

/**
 * Main window of the default variant : a {@link JFrame} with a {@link JTree} of the pages (by category) and the
 * selected page in a {@link JScrollPane}, side by side in a {@link JSplitPane}.
 * <p>
 * The look and feel and the snapshot mode settings come from {@link SwingSetup}.
 */
@Singleton
public class SwingMainWindow extends AbstractMainWindow {

    private JFrame frame;
    private JTree nav;
    private final Map<FeaturePage, DefaultMutableTreeNode> nodes = new LinkedHashMap<>();
    private JLabel pageTitle;
    private JPanel pageFrame;
    private JScrollPane scroll;
    private JComponent focusSink;
    private boolean selecting;

    @Override
    public String kind() {
        return "swing";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void open(List<FeaturePage> pages) {
        this.pages = List.copyOf(pages);

        frame = new JFrame(windowTitle());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setUp(frame);
        frame.setJMenuBar(menuBar());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Pages");
        Map<String, DefaultMutableTreeNode> categories = new LinkedHashMap<>();
        for (FeaturePage page : pages) {
            DefaultMutableTreeNode category = categories.computeIfAbsent(page.category(), name -> {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(name);
                root.add(node);
                return node;
            });
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(page);
            category.add(node);
            nodes.put(page, node);
        }
        nav = new JTree(new DefaultTreeModel(root));
        nav.setRootVisible(false);
        nav.setShowsRootHandles(true);
        nav.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // a focused tree paints a focus rectangle around the selected row
        nav.setFocusable(!ShowcaseMode.snapshot());
        nav.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                Object item = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : value;
                Object text = item instanceof FeaturePage page ? page.title() : item;
                return super.getTreeCellRendererComponent(tree, text, selected, expanded, leaf, row, hasFocus);
            }
        });
        for (int row = 0; row < nav.getRowCount(); row++) {
            nav.expandRow(row);
        }
        nav.addTreeSelectionListener(e -> {
            if (selecting || e.getNewLeadSelectionPath() == null) {
                return;
            }
            Object item = ((DefaultMutableTreeNode) e.getNewLeadSelectionPath().getLastPathComponent()).getUserObject();
            if (item instanceof FeaturePage page && page != currentPage) {
                show(page);
            }
        });
        JScrollPane navScroll = new JScrollPane(nav);
        navScroll.setPreferredSize(new Dimension(270, 100));
        navScroll.setMinimumSize(new Dimension(150, 100));

        pageTitle = new JLabel("Quarkus Desktop Showcase");
        pageTitle.setFont(pageTitle.getFont().deriveFont(Font.BOLD, 15f));
        pageTitle.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        pageFrame = new JPanel(new PageFrameLayout());
        pageFrame.setBackground(Color.WHITE);
        JPanel holder = new JPanel(new TopLeftLayout());
        holder.add(pageFrame);
        scroll = new JScrollPane(holder);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        JPanel center = new JPanel(new BorderLayout());
        center.add(pageTitle, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navScroll, center);
        split.setDividerLocation(270);
        split.setContinuousLayout(true);
        split.setFocusable(false);

        focusSink = new JPanel();
        focusSink.setFocusable(true);
        focusSink.setPreferredSize(new Dimension(1, 1));
        JLabel status = new JLabel(statusText());
        status.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.add(status, BorderLayout.CENTER);
        statusBar.add(focusSink, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.add(split, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.setVisible(true);
    }

    private JMenuBar menuBar() {
        int shortcut = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuItem previous = new JMenuItem("Previous page");
        previous.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, shortcut));
        previous.addActionListener(e -> step(-1));
        JMenuItem next = new JMenuItem("Next page");
        next.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, shortcut));
        next.addActionListener(e -> step(1));
        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut));
        quit.addActionListener(e -> quit());
        JMenu showcase = new JMenu("Showcase");
        showcase.setMnemonic(KeyEvent.VK_S);
        showcase.add(previous);
        showcase.add(next);
        showcase.addSeparator();
        showcase.add(quit);

        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "Quarkus Desktop Showcase\n" + statusText() + "\nJava " + System.getProperty("java.version")
                        + "\nLook and feel " + UIManager.getLookAndFeel().getName(),
                "About", JOptionPane.INFORMATION_MESSAGE));
        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(about);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(showcase);
        menuBar.add(help);
        // the menu bar never takes the focus with the mouse
        menuBar.setFocusable(false);
        return menuBar;
    }

    @Override
    protected void show(FeaturePage page) {
        super.show(page);
        scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
    }

    @Override
    protected void selectInNavigation(FeaturePage page) {
        DefaultMutableTreeNode node = nodes.get(page);
        if (node == null) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        selecting = true;
        try {
            nav.setSelectionPath(path);
        } finally {
            selecting = false;
        }
        nav.scrollPathToVisible(path);
    }

    @Override
    protected void showPageTitle(String text) {
        pageTitle.setText(text);
    }

    @Override
    protected Component focusSink() {
        return focusSink;
    }

    @Override
    public Window window() {
        return frame;
    }

    @Override
    public Component rootContent() {
        return frame.getRootPane();
    }

    @Override
    public Container pageFrame() {
        return pageFrame;
    }
}
