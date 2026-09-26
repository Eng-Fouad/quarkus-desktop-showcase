package io.quarkiverse.desktop.showcase.pages.swing.controls;

import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.caption;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.column;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.ints;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.row;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.runAction;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.section;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.swatch;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.text.Position;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.Readiness;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.EventLog;

/**
 * Trees : {@code JTree} with a {@code DefaultTreeModel} of {@code DefaultMutableTreeNode}s, root handles, root shown or
 * hidden, the three Metal line styles ({@code JTree.lineStyle}), a custom renderer, selections, an active
 * {@code DefaultTreeCellEditor}, a {@code TreeWillExpandListener} vetoing an expansion, right to left orientation and
 * the large model (fixed row height) ; model events, node enumerations and tree paths.
 * <p>
 * Native paths exercised on purpose : tree actions loaded by reflection ({@code LazyActionMap}), the Ocean tree icons
 * ({@code expanded.gif}, {@code collapsed.gif}, {@code collapsed-rtl.gif} resources), key strokes of the input map.
 */
@Singleton
public class TreePage implements FeaturePage {

    private static final int FOLDER = 0xFFB300;
    private static final int JAVA = 0xE65100;
    private static final int FILE = 0x607D8B;

    private ChecksView results;
    private final Readiness readiness = new Readiness();
    private JTree project;
    private JTree editing;
    private JTree vetoing;
    private EventLog expansions;

    @Override
    public String id() {
        return "swing-tree";
    }

    @Override
    public String title() {
        return "Trees";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 70;
    }

    /**
     * The model : a project tree. {@code locked} is a folder whose expansion is vetoed.
     */
    static DefaultMutableTreeNode projectTree() {
        DefaultMutableTreeNode root = node("quarkus-desktop-showcase");
        DefaultMutableTreeNode src = add(root, "src");
        DefaultMutableTreeNode main = add(src, "main");
        DefaultMutableTreeNode java = add(main, "java");
        DefaultMutableTreeNode pages = add(java, "pages");
        leaves(pages, "ButtonsPage.java", "TablePage.java", "TreePage.java");
        leaves(java, "ShowcaseMain.java");
        DefaultMutableTreeNode resources = add(main, "resources");
        leaves(resources, "application.properties");
        DefaultMutableTreeNode showcase = add(resources, "showcase");
        leaves(showcase, "star-16.png");
        DefaultMutableTreeNode docs = add(root, "docs");
        leaves(docs, "index.adoc", "native.adoc");
        DefaultMutableTreeNode locked = add(root, "locked");
        leaves(locked, "secret.txt");
        DefaultMutableTreeNode tools = add(root, "tools");
        leaves(tools, "Compare.java", "Snapshot.java");
        leaves(root, "pom.xml", "README.md");
        return root;
    }

    private static DefaultMutableTreeNode node(String name) {
        return new DefaultMutableTreeNode(name);
    }

    private static DefaultMutableTreeNode add(DefaultMutableTreeNode parent, String name) {
        DefaultMutableTreeNode child = node(name);
        parent.add(child);
        return child;
    }

    private static void leaves(DefaultMutableTreeNode parent, String... names) {
        for (String name : names) {
            DefaultMutableTreeNode leaf = node(name);
            leaf.setAllowsChildren(false);
            parent.add(leaf);
        }
    }

    /**
     * The path of the node with the given names from the root, e.g. {@code path(root, "src", "main")}.
     */
    static TreePath path(DefaultMutableTreeNode root, String... names) {
        TreePath path = new TreePath(root);
        DefaultMutableTreeNode current = root;
        for (String name : names) {
            DefaultMutableTreeNode next = null;
            for (Enumeration<TreeNode> e = current.children(); e.hasMoreElements();) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) e.nextElement();
                if (name.equals(child.getUserObject())) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                throw new IllegalArgumentException("no node " + name);
            }
            path = path.pathByAddingChild(next);
            current = next;
        }
        return path;
    }

    private static DefaultMutableTreeNode rootOf(JTree tree) {
        return (DefaultMutableTreeNode) tree.getModel().getRoot();
    }

    /**
     * Folder, Java and other file icons (painted swatches), folders in bold.
     */
    static final class ProjectRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            String name = String.valueOf(node.getUserObject());
            boolean folder = node.getAllowsChildren();
            setIcon(swatch(folder ? FOLDER : name.endsWith(".java") ? JAVA : FILE, 12, 12));
            setFont(tree.getFont().deriveFont(folder ? Font.BOLD : Font.PLAIN));
            return this;
        }
    }

    private static JTree tree(String lineStyle) {
        JTree tree = new JTree(new DefaultTreeModel(projectTree(), true));
        tree.putClientProperty("JTree.lineStyle", lineStyle);
        tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return tree;
    }

    @Override
    public Component build() {
        results = ChecksView.table("Checks", List.of(Check.info("state", "pending")));
        expansions = new EventLog();

        project = tree("Angled");
        project.setCellRenderer(new ProjectRenderer());
        project.setShowsRootHandles(true);
        DefaultMutableTreeNode root = rootOf(project);
        project.expandPath(path(root, "src", "main", "java", "pages"));
        project.expandPath(path(root, "src", "main", "resources"));
        project.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        project.setSelectionPaths(new TreePath[] { path(root, "src", "main", "java", "pages", "TablePage.java"),
                path(root, "src", "main", "resources", "application.properties") });

        editing = tree("Horizontal");
        editing.setRootVisible(false);
        editing.setShowsRootHandles(true);
        editing.setEditable(true);
        editing.expandPath(path(rootOf(editing), "docs"));
        editing.expandPath(path(rootOf(editing), "tools"));
        editing.setSelectionPath(path(rootOf(editing), "docs", "native.adoc"));

        vetoing = tree("None");
        vetoing.setRowHeight(22);
        vetoing.setLargeModel(true);
        vetoing.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        vetoing.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) throws ExpandVetoException {
                String name = name(e.getPath());
                if (name.equals("locked")) {
                    expansions.add("willExpand " + name + " (veto)");
                    throw new ExpandVetoException(e, "locked");
                }
                expansions.add("willExpand " + name);
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {
                expansions.add("willCollapse " + name(e.getPath()));
            }
        });
        vetoing.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent e) {
                expansions.add("expanded " + name(e.getPath()));
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent e) {
                expansions.add("collapsed " + name(e.getPath()));
            }
        });
        DefaultMutableTreeNode vetoRoot = rootOf(vetoing);
        vetoing.expandPath(path(vetoRoot, "locked"));
        vetoing.expandPath(path(vetoRoot, "docs"));
        vetoing.expandPath(path(vetoRoot, "tools"));
        vetoing.collapsePath(path(vetoRoot, "tools"));
        vetoing.setSelectionPath(path(vetoRoot, "docs", "index.adoc"));

        for (JTree tree : List.of(project, editing, vetoing)) {
            tree.setPreferredSize(new Dimension(310, Math.max(300, tree.getPreferredSize().height)));
        }
        JPanel content = column(10,
                Ui.text("JTree with the Metal line styles Angled, Horizontal and None. Left : root shown, custom "
                        + "renderer, two selected paths. Middle : root hidden, the cell editor active on README.md. "
                        + "Right : right to left, large model (row height 22), the expansion of \"locked\" vetoed by a "
                        + "TreeWillExpandListener.", 1000),
                section("JTree", row(12, labeled("Angled, custom renderer", project),
                        labeled("Horizontal, editing", editing), labeled("None, RIGHT_TO_LEFT, veto", vetoing))),
                results);
        content.setOpaque(true);
        content.setBackground(Color.WHITE);
        ControlsSupport.readyWhenShown(this, content);
        return content;
    }

    private static JComponent labeled(String text, JComponent component) {
        component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xCFD8DC)),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return column(2, caption(text), component);
    }

    private static String name(TreePath path) {
        return String.valueOf(((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject());
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        return readiness.get(content, this::prepare);
    }

    private CompletionStage<?> prepare(Component content) {
        ChecksView view = results;
        JTree tree = editing;
        return Edt.rounds(2).thenCompose(v -> {
            // once laid out : the editor is placed at the bounds of the row
            tree.startEditingAtPath(path(rootOf(tree), "README.md"));
            return Edt.rounds(2);
        }).thenAccept(v -> view.setChecks(checks()));
    }

    @Override
    public void dispose(Component content) {
        readiness.reset();
        if (editing != null && editing.isEditing()) {
            editing.cancelEditing();
        }
        results = null;
        project = null;
        editing = null;
        vetoing = null;
        expansions = null;
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private static String names(Enumeration<TreeNode> nodes) {
        List<String> names = new ArrayList<>();
        while (nodes.hasMoreElements()) {
            names.add(String.valueOf(((DefaultMutableTreeNode) nodes.nextElement()).getUserObject()));
        }
        return String.join(" ", names);
    }

    private List<Check> checks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("UI delegate, line styles", "MetalTreeUI, Angled Horizontal None",
                () -> project.getUI().getClass().getSimpleName() + ", " + project.getClientProperty("JTree.lineStyle")
                        + " " + editing.getClientProperty("JTree.lineStyle") + " "
                        + vetoing.getClientProperty("JTree.lineStyle")));
        checks.add(Checks.expect("expansion events (TreeWillExpandListener veto of locked)",
                "willExpand locked (veto) | willExpand docs | expanded docs | willExpand tools | expanded tools | "
                        + "willCollapse tools | collapsed tools",
                () -> expansions.toString()));
        checks.add(Checks.expect("isExpanded : locked, docs, tools", "false true false", () -> {
            DefaultMutableTreeNode root = rootOf(vetoing);
            return vetoing.isExpanded(path(root, "locked")) + " " + vetoing.isExpanded(path(root, "docs")) + " "
                    + vetoing.isExpanded(path(root, "tools"));
        }));
        checks.add(Checks.expect("row counts (visible nodes) : project, editing, vetoing", "17, 10, 9",
                () -> project.getRowCount() + ", " + editing.getRowCount() + ", " + vetoing.getRowCount()));
        checks.add(Checks.expect("getRowForPath(getPathForRow(i)) and closest row of each row center", "true", () -> {
            for (JTree tree : List.of(project, editing, vetoing)) {
                for (int r = 0; r < tree.getRowCount(); r++) {
                    Rectangle bounds = tree.getRowBounds(r);
                    if (tree.getRowForPath(tree.getPathForRow(r)) != r
                            || tree.getClosestRowForLocation(bounds.x + bounds.width / 2,
                                    bounds.y + bounds.height / 2) != r) {
                        return "row " + r;
                    }
                }
            }
            return true;
        }));
        checks.add(Checks.expect("large model : fixed row heights", "22 22 22", () -> vetoing.getRowBounds(0).height + " "
                + vetoing.getRowBounds(3).height + " " + vetoing.getRowBounds(vetoing.getRowCount() - 1).height));
        checks.add(Checks.expect("indentation : left to right (row 1 starts right of row 0) / right to left (row 1 ends "
                + "left of row 0)", "true / true", () -> {
                    Rectangle ltr0 = project.getRowBounds(0);
                    Rectangle ltr1 = project.getRowBounds(1);
                    Rectangle rtl0 = vetoing.getRowBounds(0);
                    Rectangle rtl1 = vetoing.getRowBounds(1);
                    return (ltr1.x > ltr0.x) + " / " + (rtl1.x + rtl1.width < rtl0.x + rtl0.width);
                }));
        checks.add(Checks.expect("selection : project rows, lead", "6,10 / application.properties",
                () -> ints(project.getSelectionRows()) + " / " + name(project.getLeadSelectionPath())));
        checks.add(Checks.expect("editing : isEditing, editing path, editor text field", "true, README.md, README.md",
                () -> {
                    Component field = findField(editing);
                    return editing.isEditing() + ", " + name(editing.getEditingPath()) + ", "
                            + (field instanceof JTextField text ? text.getText() : "no text field");
                }));
        checks.addAll(modelChecks());
        checks.addAll(behaviorChecks());
        return checks;
    }

    private static List<Check> modelChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("breadth first enumeration (first 8), depth first (first 5)",
                "quarkus-desktop-showcase src docs locked tools pom.xml README.md main / ButtonsPage.java TablePage.java "
                        + "TreePage.java pages ShowcaseMain.java",
                () -> {
                    List<String> breadth = List.of(names(projectTree().breadthFirstEnumeration()).split(" "));
                    List<String> depth = List.of(names(projectTree().depthFirstEnumeration()).split(" "));
                    return String.join(" ", breadth.subList(0, 8)) + " / " + String.join(" ", depth.subList(0, 5));
                }));
        checks.add(Checks.expect("preorder / postorder of src/main/resources",
                "resources application.properties showcase star-16.png / application.properties star-16.png showcase "
                        + "resources",
                () -> {
                    DefaultMutableTreeNode resources = (DefaultMutableTreeNode) path(projectTree(), "src", "main",
                            "resources").getLastPathComponent();
                    return names(resources.preorderEnumeration()) + " / " + names(resources.postorderEnumeration());
                }));
        checks.add(Checks.expect("node queries : depth, level, leaf count, siblings, path to root",
                "5, 3, 4, 6, quarkus-desktop-showcase/src/main/java", () -> {
                    DefaultMutableTreeNode root = projectTree();
                    DefaultMutableTreeNode java = (DefaultMutableTreeNode) path(root, "src", "main", "java")
                            .getLastPathComponent();
                    List<String> names = new ArrayList<>();
                    for (TreeNode n : java.getPath()) {
                        names.add(String.valueOf(((DefaultMutableTreeNode) n).getUserObject()));
                    }
                    return root.getDepth() + ", " + java.getLevel() + ", " + java.getLeafCount() + ", "
                            + ((DefaultMutableTreeNode) root.getChildAt(1)).getSiblingCount() + ", "
                            + String.join("/", names);
                }));
        checks.add(Checks.expect("TreePath : count, parent, isDescendant, pathByAddingChild",
                "4, main, true false, 5", () -> {
                    DefaultMutableTreeNode root = projectTree();
                    TreePath java = path(root, "src", "main", "java");
                    TreePath src = path(root, "src");
                    TreePath child = java.pathByAddingChild(new DefaultMutableTreeNode("x"));
                    return java.getPathCount() + ", " + name(java.getParentPath()) + ", " + src.isDescendant(java) + " "
                            + java.isDescendant(src) + ", " + child.getPathCount();
                }));
        checks.add(Checks.expect("TreeModelEvents : insert, change, remove, valueForPathChanged, reload",
                "inserted [2] | changed [0] | removed [2] | changed [5] | structure",
                () -> {
                    DefaultMutableTreeNode root = projectTree();
                    DefaultTreeModel model = new DefaultTreeModel(root, true);
                    EventLog log = new EventLog();
                    model.addTreeModelListener(new TreeModelListener() {
                        @Override
                        public void treeNodesChanged(TreeModelEvent e) {
                            log.add("changed " + java.util.Arrays.toString(e.getChildIndices()));
                        }

                        @Override
                        public void treeNodesInserted(TreeModelEvent e) {
                            log.add("inserted " + java.util.Arrays.toString(e.getChildIndices()));
                        }

                        @Override
                        public void treeNodesRemoved(TreeModelEvent e) {
                            log.add("removed " + java.util.Arrays.toString(e.getChildIndices()));
                        }

                        @Override
                        public void treeStructureChanged(TreeModelEvent e) {
                            log.add("structure");
                        }
                    });
                    DefaultMutableTreeNode docs = (DefaultMutableTreeNode) path(root, "docs").getLastPathComponent();
                    DefaultMutableTreeNode added = new DefaultMutableTreeNode("added.adoc", false);
                    model.insertNodeInto(added, docs, 2);
                    model.nodeChanged(docs.getChildAt(0));
                    model.removeNodeFromParent(added);
                    model.valueForPathChanged(path(root, "README.md"), "NOTES.md");
                    model.reload();
                    return log.toString();
                }));
        return checks;
    }

    private static List<Check> behaviorChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("selection modes : 2 paths added in SINGLE / CONTIGUOUS (rows 1, 3) / DISCONTIGUOUS",
                "1 / 1 / 2", () -> {
                    List<String> counts = new ArrayList<>();
                    for (int mode : new int[] { TreeSelectionModel.SINGLE_TREE_SELECTION,
                            TreeSelectionModel.CONTIGUOUS_TREE_SELECTION,
                            TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION }) {
                        JTree tree = new JTree(projectTree());
                        tree.getSelectionModel().setSelectionMode(mode);
                        tree.addSelectionPaths(new TreePath[] { tree.getPathForRow(1), tree.getPathForRow(3) });
                        counts.add(String.valueOf(tree.getSelectionCount()));
                    }
                    return String.join(" / ", counts);
                }));
        checks.add(Checks.expect("tree actions (BasicTreeUI.loadActionMap) : selectNext, selectChild (expand), "
                + "selectChild, selectParent, selectLast", "src | src expanded | main | src | README.md", () -> {
                    JTree tree = new JTree(projectTree());
                    tree.setSelectionRow(0);
                    List<String> states = new ArrayList<>();
                    runAction(tree, "selectNext");
                    states.add(name(tree.getSelectionPath()));
                    runAction(tree, "selectChild");
                    states.add(name(tree.getSelectionPath()) + (tree.isExpanded(tree.getSelectionPath()) ? " expanded"
                            : ""));
                    runAction(tree, "selectChild");
                    states.add(name(tree.getSelectionPath()));
                    runAction(tree, "selectParent");
                    states.add(name(tree.getSelectionPath()));
                    runAction(tree, "selectLast");
                    states.add(name(tree.getSelectionPath()));
                    return String.join(" | ", states);
                }));
        checks.add(Checks.expect("getNextMatch : \"RE\" forward, \"to\" backward from the last row", "README.md, tools",
                () -> {
                    JTree tree = new JTree(projectTree());
                    return name(tree.getNextMatch("RE", 0, Position.Bias.Forward)) + ", "
                            + name(tree.getNextMatch("to", tree.getRowCount() - 1, Position.Bias.Backward));
                }));
        checks.add(Checks.expect("editing : stopEditing with a new value, then cancelEditing", "NOTES.md, README.md",
                () -> {
                    JTree tree = new JTree(new DefaultTreeModel(projectTree(), true));
                    tree.setEditable(true);
                    tree.setSize(300, 400);
                    tree.doLayout();
                    TreePath readme = path(rootOf(tree), "README.md");
                    tree.startEditingAtPath(readme);
                    ((JTextField) findField(tree)).setText("NOTES.md");
                    tree.stopEditing();
                    String stopped = name(readme);
                    TreePath pom = path(rootOf(tree), "pom.xml");
                    tree.startEditingAtPath(pom);
                    tree.cancelEditing();
                    tree.startEditingAtPath(path(rootOf(tree), "NOTES.md"));
                    ((JTextField) findField(tree)).setText("README.md");
                    tree.stopEditing();
                    return stopped + ", " + name(tree.getPathForRow(tree.getRowCount() - 1));
                }));
        checks.add(Checks.expect("defaults : toggleClickCount, scrollsOnExpand, row height (renderer based)", "2, true, 0",
                () -> {
                    JTree tree = new JTree(projectTree());
                    return tree.getToggleClickCount() + ", " + tree.getScrollsOnExpand() + ", " + tree.getRowHeight();
                }));
        checks.add(Checks.expect("Metal/Ocean tree icons : expanded, collapsed (gif resources), leaf, open, closed",
                "18x18 18x18 16x20 16x18 16x18", () -> {
                    List<String> sizes = new ArrayList<>();
                    for (String key : List.of("Tree.expandedIcon", "Tree.collapsedIcon", "Tree.leafIcon",
                            "Tree.openIcon", "Tree.closedIcon")) {
                        Icon icon = UIManager.getIcon(key);
                        sizes.add(icon == null ? "null" : icon.getIconWidth() + "x" + icon.getIconHeight());
                    }
                    return String.join(" ", sizes);
                }));
        return checks;
    }

    private static Component findField(java.awt.Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTextField) {
                return c;
            }
            if (c instanceof java.awt.Container child) {
                Component found = findField(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
