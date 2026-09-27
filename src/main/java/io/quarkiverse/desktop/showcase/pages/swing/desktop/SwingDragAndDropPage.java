package io.quarkiverse.desktop.showcase.pages.swing.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.RobotSession;
import io.quarkiverse.desktop.showcase.pages.desktop.DesktopSupport;

/**
 * Swing drag and drop and data transfer : {@code TransferHandler}s between a {@code JList}, a {@code JTextArea} and a
 * {@code JTree} (drop modes, drop locations, move and copy actions, a drag image), driven by Robot, plus the
 * non-drag uses of transfer handlers on a private clipboard (the default handlers of JList, JTable, JTree and the text
 * components, a property-based {@code TransferHandler("text")} that reads and writes a bean property by reflection) and
 * the {@code TransferSupport} and {@code DropMode} API.
 * <p>
 * Needs the focus (Robot input). The user's clipboard is never used.
 */
@Singleton
public class SwingDragAndDropPage implements FeaturePage {

    private static final int PANEL_COLOR = 0xE8F5E9;
    private static final List<String> ITEMS = List.of("Alpha", "Bravo", "Charlie", "Delta");

    // per build state
    private JList<String> list;
    private DefaultListModel<String> listModel;
    private JTextArea area;
    private JTree tree;
    private JPanel panel;
    private ChecksView robotView;
    private DropLog log;

    @Override
    public String id() {
        return "dt-dnd-swing";
    }

    @Override
    public String title() {
        return "Drag and drop (Swing)";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public boolean needsFocus() {
        return true;
    }

    @Override
    public Component build() {
        log = new DropLog();
        listModel = new DefaultListModel<>();
        ITEMS.forEach(listModel::addElement);
        list = new JList<>(listModel);
        list.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new ListHandler(log));

        area = new JTextArea("Dropped text:\n", 6, 18);
        area.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        // the default handler of the text components (TextTransferHandler), recording the drop location
        area.setTransferHandler(new RecordingTextHandler(area.getTransferHandler(), log));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Showcase");
        DefaultMutableTreeNode fruits = new DefaultMutableTreeNode("Fruits");
        fruits.add(new DefaultMutableTreeNode("Apple"));
        fruits.add(new DefaultMutableTreeNode("Banana"));
        DefaultMutableTreeNode colors = new DefaultMutableTreeNode("Colors");
        colors.add(new DefaultMutableTreeNode("Red"));
        colors.add(new DefaultMutableTreeNode("Green"));
        root.add(fruits);
        root.add(colors);
        tree = new JTree(new DefaultTreeModel(root));
        tree.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new TreeHandler(log));
        expandAll(tree);

        panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 12));
        panel.setBackground(new Color(PANEL_COLOR));
        panel.add(titled("JList (DropMode.INSERT)", scroll(list, 150, 140)));
        panel.add(titled("JTextArea (default handler)", scroll(area, 230, 140)));
        panel.add(titled("JTree (DropMode.ON_OR_INSERT)", scroll(tree, 200, 140)));
        panel.setPreferredSize(new Dimension(1000, 200));

        robotView = ChecksView.table("Robot-driven drags", List.of(Check.info("state", "pending")));
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JLabel intro = new JLabel("<html><body style='width:720px'>Drag a list item to the text area or onto a tree "
                + "node, and a tree node into the list (a move : the node leaves the tree). TransferHandlers export "
                + "and import the data ; the drop locations come from the drop modes. In snapshot mode, Robot performs "
                + "the three drags.</body></html>");
        top.add(intro, BorderLayout.NORTH);
        top.add(panel, BorderLayout.CENTER);
        page.add(top, BorderLayout.NORTH);
        JPanel tables = new JPanel(new BorderLayout(0, 14));
        tables.setOpaque(false);
        tables.add(ChecksView.table("TransferHandler API (private clipboard)", apiChecks()), BorderLayout.NORTH);
        tables.add(robotView, BorderLayout.CENTER);
        page.add(tables, BorderLayout.CENTER);
        return page;
    }

    private static JComponent scroll(JComponent c, int w, int h) {
        JScrollPane scroll = new JScrollPane(c);
        scroll.setPreferredSize(new Dimension(w, h));
        return scroll;
    }

    private static JComponent titled(String title, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        p.add(label, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static void expandAll(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = robotView;
        JList<String> l = list;
        JTextArea a = area;
        JTree t = tree;
        JPanel p = panel;
        DropLog dropLog = log;
        return Edt.rounds(3).thenCompose(v -> Edt.delay(300))
                .thenCompose(v -> Edt.background(() -> drags(l, a, t, p, dropLog)))
                .thenAccept(checks -> {
                    // fixed selections in the snapshot
                    l.clearSelection();
                    t.clearSelection();
                    view.setChecks(checks);
                })
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public void dispose(Component content) {
        list = null;
        listModel = null;
        area = null;
        tree = null;
        panel = null;
        robotView = null;
        log = null;
    }

    // ------------------------------------------------------------------------------------------ API checks

    private static List<Check> apiChecks() {
        List<Check> checks = new ArrayList<>();
        Clipboard clip = new Clipboard("showcase-swing-dnd");

        // default handlers of the components (installed by their UI delegates)
        JList<String> defaultList = new JList<>(ITEMS.toArray(String[]::new));
        defaultList.setSelectedIndices(new int[] { 0, 2 });
        checks.add(Checks.expect("JList default handler: export (text)", "Alpha\nCharlie", () -> {
            defaultList.getTransferHandler().exportToClipboard(defaultList, clip, TransferHandler.COPY);
            return clip.getData(DataFlavor.stringFlavor);
        }));
        checks.add(Checks.info("JList default handler: export (HTML)",
                () -> String.valueOf(clip.getData(new DataFlavor("text/html; class=java.lang.String")))
                        .replace("\n", " ")));
        checks.add(Checks.info("JList default handler: flavors",
                () -> Arrays.stream(clip.getAvailableDataFlavors()).map(f -> f.getPrimaryType() + "/" + f.getSubType())
                        .distinct().sorted().toList()));
        JTable table = new JTable(new Object[][] { { "a1", 1 }, { "b1", 2 } }, new Object[] { "Name", "Value" });
        table.setRowSelectionInterval(0, 1);
        checks.add(Checks.expect("JTable default handler: export (text, tabs shown as |)", "a1|1\nb1|2", () -> {
            table.getTransferHandler().exportToClipboard(table, clip, TransferHandler.COPY);
            return String.valueOf(clip.getData(DataFlavor.stringFlavor)).replace('\t', '|');
        }));
        JTree defaultTree = new JTree();
        defaultTree.setSelectionRow(1);
        checks.add(Checks.expect("JTree default handler: export (text)", "colors", () -> {
            defaultTree.getTransferHandler().exportToClipboard(defaultTree, clip, TransferHandler.COPY);
            return clip.getData(DataFlavor.stringFlavor);
        }));
        JTextField field = new JTextField("cut this text");
        field.select(4, 9);
        checks.add(Checks.expect("JTextField: exportToClipboard MOVE (cut)", "this |cut text", () -> {
            field.getTransferHandler().exportToClipboard(field, clip, TransferHandler.MOVE);
            return clip.getData(DataFlavor.stringFlavor) + "|" + field.getText();
        }));
        JTextArea target = new JTextArea("paste: ");
        target.setCaretPosition(target.getDocument().getLength());
        checks.add(Checks.expect("JTextArea: importData (paste at the caret)", "paste: imported", () -> {
            target.getTransferHandler().importData(new TransferHandler.TransferSupport(target,
                    new StringSelection("imported")));
            return target.getText();
        }));

        // a property-based handler : reads and writes the "text" bean property (Introspector + reflective invocation)
        JLabel from = new JLabel("property transfer");
        JLabel to = new JLabel("-");
        TransferHandler textProperty = new TransferHandler("text");
        from.setTransferHandler(textProperty);
        to.setTransferHandler(textProperty);
        checks.add(Checks.expect("TransferHandler(\"text\"): export JLabel.getText", "property transfer", () -> {
            textProperty.exportToClipboard(from, clip, TransferHandler.COPY);
            DataFlavor flavor = clip.getAvailableDataFlavors()[0];
            return clip.getData(flavor);
        }));
        checks.add(Checks.expect("TransferHandler(\"text\"): flavor", DataFlavor.javaJVMLocalObjectMimeType
                + "; class=java.lang.String", () -> clip.getAvailableDataFlavors()[0].getMimeType()));
        checks.add(Checks.expect("TransferHandler(\"text\"): import JLabel.setText", "true property transfer", () -> {
            boolean imported = textProperty.importData(new TransferHandler.TransferSupport(to, clip.getContents(null)));
            return imported + " " + to.getText();
        }));
        TransferHandler foreground = new TransferHandler("foreground");
        JLabel colored = new JLabel("colored");
        colored.setForeground(new Color(0x6A1B9A));
        JLabel plain = new JLabel("plain");
        checks.add(Checks.expect("TransferHandler(\"foreground\"): Color property", "true #FF6A1B9A", () -> {
            foreground.exportToClipboard(colored, clip, TransferHandler.COPY);
            boolean imported = foreground.importData(new TransferHandler.TransferSupport(plain, clip.getContents(null)));
            return imported + " " + Checks.argb(plain.getForeground().getRGB());
        }));
        checks.add(Checks.expect("TransferHandler(\"icon\").canImport(a string)", false,
                () -> new TransferHandler("icon").canImport(new TransferHandler.TransferSupport(plain,
                        new StringSelection("not an icon")))));
        checks.add(Checks.expect("copy / cut / paste actions", "copy cut paste",
                () -> TransferHandler.getCopyAction().getValue(Action.NAME) + " "
                        + TransferHandler.getCutAction().getValue(Action.NAME) + " "
                        + TransferHandler.getPasteAction().getValue(Action.NAME)));
        checks.add(Checks.expect("drag image and offset", "24x16 -12,-8", () -> {
            TransferHandler h = new TransferHandler("text");
            h.setDragImage(new BufferedImage(24, 16, BufferedImage.TYPE_INT_ARGB));
            h.setDragImageOffset(new Point(-12, -8));
            return h.getDragImage().getWidth(null) + "x" + h.getDragImage().getHeight(null) + " "
                    + h.getDragImageOffset().x + "," + h.getDragImageOffset().y;
        }));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(plain,
                new StringSelection("support"));
        checks.add(Checks.expect("TransferSupport (not a drop)", "false true", () -> support.isDrop() + " "
                + support.isDataFlavorSupported(DataFlavor.stringFlavor)));
        checks.add(DesktopSupport.expectThrows("TransferSupport.getDropLocation() (not a drop)",
                IllegalStateException.class, support::getDropLocation));
        checks.add(DesktopSupport.expectThrows("TransferSupport.getUserDropAction() (not a drop)",
                IllegalStateException.class, support::getUserDropAction));
        checks.add(Checks.expect("DropMode values", "USE_SELECTION ON INSERT INSERT_ROWS INSERT_COLS ON_OR_INSERT "
                + "ON_OR_INSERT_ROWS ON_OR_INSERT_COLS", () -> String.join(" ", Arrays.stream(DropMode.values())
                        .map(Enum::name).toList())));
        checks.add(Checks.expect("default drop modes (JList, JTree, JTextArea)", "USE_SELECTION USE_SELECTION "
                + "USE_SELECTION", () -> new JList<>().getDropMode() + " " + new JTree().getDropMode() + " "
                        + new JTextArea().getDropMode()));
        checks.add(DesktopSupport.expectThrows("JList.setDropMode(INSERT_COLS)", IllegalArgumentException.class,
                () -> {
                    new JList<>().setDropMode(DropMode.INSERT_COLS);
                    return "accepted";
                }));
        checks.add(DesktopSupport.expectThrows("JTextArea.setDropMode(ON)", IllegalArgumentException.class, () -> {
            new JTextArea().setDropMode(DropMode.ON);
            return "accepted";
        }));
        return checks;
    }

    // ----------------------------------------------------------------------------------------------- Robot drags

    private record Plan(Point from, Point to, List<Point> probes, Component component) {
    }

    private static List<Check> drags(JList<String> list, JTextArea area, JTree tree, JPanel panel, DropLog log)
            throws Exception {
        List<Check> checks = new ArrayList<>();
        boolean done;
        // the operating system runs its own loop during a drag : no waitForIdle
        try (RobotSession robot = RobotSession.open().idleAfterInput(false)) {
            done = drag(checks, robot, log, "drag 1 (list item to the text area)", "text area",
                    plan(panel, cell(list, 1), below(area)))
                    && drag(checks, robot, log, "drag 2 (list item onto a tree node)", "tree",
                            plan(panel, cell(list, 2), row(tree, "Fruits", 0.5)))
                    && drag(checks, robot, log, "drag 3 (tree node into the list, move)", "list",
                            plan(panel, row(tree, "Red", 0.5), boundary(list, 1)));
        }
        if (!done) {
            return checks;
        }
        checks.add(Checks.expect("list items", "[Alpha, Red, Bravo, Charlie, Delta]",
                () -> DesktopSupport.onEdt(() -> Collections.list(((DefaultListModel<String>) list.getModel())
                        .elements()).toString())));
        checks.add(Checks.expect("text area", "Dropped text:\nBravo", () -> DesktopSupport.onEdt(area::getText)));
        checks.add(Checks.expect("tree", "Fruits [Apple, Banana, Charlie] Colors [Green]",
                () -> DesktopSupport.onEdt(() -> treeText(tree))));
        return checks;
    }

    private static String treeText(JTree tree) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            List<String> children = new ArrayList<>();
            for (int j = 0; j < node.getChildCount(); j++) {
                children.add(String.valueOf(((DefaultMutableTreeNode) node.getChildAt(j)).getUserObject()));
            }
            parts.add(node.getUserObject() + " " + children);
        }
        return String.join(" ", parts);
    }

    /** The screen point of a location of a component, computed on the EDT. */
    private interface Where extends Callable<Point> {
    }

    private static Where cell(JList<?> list, int index) {
        return () -> {
            Rectangle r = list.getCellBounds(index, index);
            return DesktopSupport.onScreen(list, r.x + 30, r.y + r.height / 2);
        };
    }

    private static Where boundary(JList<?> list, int index) {
        // just below the boundary above the cell : an INSERT drop location at that index
        return () -> {
            Rectangle r = list.getCellBounds(index, index);
            return DesktopSupport.onScreen(list, r.x + 30, r.y + 2);
        };
    }

    private static Where below(JTextComponent text) {
        return () -> DesktopSupport.onScreen(text, 40, text.getHeight() - 12);
    }

    private static Where row(JTree tree, String name, double fraction) {
        return () -> {
            for (int row = 0; row < tree.getRowCount(); row++) {
                TreePath path = tree.getPathForRow(row);
                if (name.equals(String.valueOf(((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject()))) {
                    Rectangle r = tree.getRowBounds(row);
                    return DesktopSupport.onScreen(tree, r.x + Math.min(20, r.width / 2),
                            r.y + (int) (r.height * fraction));
                }
            }
            throw new IllegalStateException("no tree row " + name);
        };
    }

    private static Callable<Plan> plan(JPanel panel, Where from, Where to) {
        return () -> {
            List<Point> probes = List.of(DesktopSupport.onScreen(panel, 4, 4),
                    DesktopSupport.onScreen(panel, panel.getWidth() - 5, 4),
                    DesktopSupport.onScreen(panel, panel.getWidth() - 5, panel.getHeight() - 5),
                    DesktopSupport.onScreen(panel, 4, panel.getHeight() - 5));
            return new Plan(from.call(), to.call(), probes, panel);
        };
    }

    private static boolean drag(List<Check> checks, RobotSession robot, DropLog log, String name,
            String expectedTarget, Callable<Plan> planner) throws Exception {
        String outcome = null;
        int attempt = 0;
        while (attempt < 3) {
            attempt++;
            log.reset();
            Plan plan = DesktopSupport.onEdt(planner);
            robot.move(plan.from());
            DesktopSupport.sleep(150);
            java.util.Map<Point, Integer> probes = new java.util.LinkedHashMap<>();
            plan.probes().forEach(p -> probes.put(p, PANEL_COLOR));
            if (!robot.ensureFocus(plan.component())) {
                outcome = DesktopSupport.NOT_FOCUSED;
                break;
            }
            if (!robot.awaitVisible(plan.component(), probes)) {
                outcome = "skipped: the page is not visible on screen";
                continue;
            }
            if (!robot.press(InputEvent.BUTTON1_DOWN_MASK)) {
                outcome = DesktopSupport.NOT_FOCUSED;
                break;
            }
            Point start = new Point(plan.from().x + 12, plan.from().y + 3);
            robot.glide(plan.from(), start, 8, 20);
            robot.glide(start, plan.to(), 24, 15);
            DesktopSupport.sleep(250);
            robot.release(InputEvent.BUTTON1_DOWN_MASK);
            // the drag loop of the operating system may miss the release of the button until the next input
            DesktopSupport.finishDrop(robot, plan.to(), () -> log.exported);
            outcome = null;
            if (log.imported) {
                break;
            }
            DesktopSupport.sleep(300);
        }
        checks.add(Check.attempts(name, attempt));
        if (outcome != null) {
            checks.add(Check.info(name, outcome));
            return false;
        }
        synchronized (log) {
            checks.add(Checks.expect(name + ": imported by", expectedTarget, () -> log.target));
            checks.add(Checks.info(name + ": drop location", () -> log.location));
            checks.add(Checks.info(name + ": drop action (source actions)", () -> log.action));
            checks.add(Checks.info(name + ": exportDone", () -> log.done));
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ transfer handlers

    /**
     * What the handlers saw during the current drag (EDT writes, background thread reads under the lock).
     */
    static final class DropLog {

        volatile boolean imported;
        volatile boolean exported;
        String target;
        String location;
        String action;
        String done;

        synchronized void reset() {
            imported = false;
            exported = false;
            target = null;
            location = null;
            action = null;
            done = null;
        }

        synchronized void imported(String target, String location, TransferHandler.TransferSupport support) {
            this.target = target;
            this.location = location;
            this.action = actionName(support.getDropAction()) + " (" + actionName(support.getSourceDropActions())
                    + ")";
            imported = true;
        }

        synchronized void done(String source, int action) {
            this.done = source + " " + actionName(action);
            exported = true;
        }
    }

    static String actionName(int action) {
        return switch (action) {
            case TransferHandler.NONE -> "NONE";
            case TransferHandler.COPY -> "COPY";
            case TransferHandler.MOVE -> "MOVE";
            case TransferHandler.COPY_OR_MOVE -> "COPY_OR_MOVE";
            default -> String.valueOf(action);
        };
    }

    /**
     * The list : exports the selected item (copy only), imports strings at the drop location (insert).
     */
    private static final class ListHandler extends TransferHandler {

        private final DropLog log;

        ListHandler(DropLog log) {
            this.log = log;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            return new StringSelection(((JList<?>) c).getSelectedValue().toString());
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            log.done("list", action);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                JList<String> list = (JList<String>) support.getComponent();
                JList.DropLocation location = (JList.DropLocation) support.getDropLocation();
                String value = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                ((DefaultListModel<String>) list.getModel()).add(location.getIndex(), value);
                log.imported("list", "index " + location.getIndex() + ", insert " + location.isInsert(), support);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * The tree : exports the selected leaf (move : removed when the drop succeeded), imports strings as a child of the
     * node under the drop location (ON) or at the insertion index (INSERT).
     */
    private static final class TreeHandler extends TransferHandler {

        private final DropLog log;

        TreeHandler(DropLog log) {
            this.log = log;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            TreePath path = ((JTree) c).getSelectionPath();
            DefaultMutableTreeNode node = path == null ? null : (DefaultMutableTreeNode) path.getLastPathComponent();
            return node == null || !node.isLeaf() ? null : new StringSelection(String.valueOf(node.getUserObject()));
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            if (action == MOVE && data != null) {
                JTree tree = (JTree) source;
                TreePath path = tree.getSelectionPath();
                if (path != null) {
                    ((DefaultTreeModel) tree.getModel()).removeNodeFromParent(
                            (DefaultMutableTreeNode) path.getLastPathComponent());
                }
            }
            log.done("tree", action);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return false;
            }
            // not onto a leaf, and not the tree's own node
            JTree.DropLocation location = (JTree.DropLocation) support.getDropLocation();
            return location.getPath() != null
                    && !((DefaultMutableTreeNode) location.getPath().getLastPathComponent()).isLeaf()
                    && support.getComponent() != null;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                JTree tree = (JTree) support.getComponent();
                JTree.DropLocation location = (JTree.DropLocation) support.getDropLocation();
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) location.getPath().getLastPathComponent();
                String value = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                int index = location.getChildIndex() < 0 ? parent.getChildCount() : location.getChildIndex();
                ((DefaultTreeModel) tree.getModel()).insertNodeInto(new DefaultMutableTreeNode(value), parent, index);
                log.imported("tree", "path " + parent.getUserObject() + ", child index " + location.getChildIndex(),
                        support);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * The default handler of a text component, recording the drop location of the drops it accepts.
     */
    private static final class RecordingTextHandler extends TransferHandler {

        private final TransferHandler delegate;
        private final DropLog log;

        RecordingTextHandler(TransferHandler delegate, DropLog log) {
            this.delegate = delegate;
            this.log = log;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return delegate.getSourceActions(c);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return delegate.canImport(support);
        }

        @Override
        public boolean importData(TransferSupport support) {
            String location = support.isDrop()
                    ? "index " + ((JTextComponent.DropLocation) support.getDropLocation()).getIndex()
                    : "not a drop";
            boolean imported = delegate.importData(support);
            if (imported && support.isDrop()) {
                log.imported("text area", location, support);
            }
            return imported;
        }
    }
}
