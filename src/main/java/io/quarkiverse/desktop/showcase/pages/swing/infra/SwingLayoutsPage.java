package io.quarkiverse.desktop.showcase.pages.swing.infra;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.GroupLayout;
import javax.swing.LayoutStyle;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.OverlayLayout;
import javax.swing.SizeRequirements;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.swing.infra.InfraSupport.Block;

/**
 * The Swing layout managers : {@link BoxLayout} (with {@link Box} glue, struts, rigid areas and fillers),
 * {@link OverlayLayout}, {@link SpringLayout} (and {@link Spring} arithmetic), {@link GroupLayout} (baseline rows,
 * linked sizes, preferred gaps), a {@link GridBagLayout} form (baseline anchors, weights, insets, remainder cells) and
 * the {@link LayoutStyle} of the look and feel.
 * <p>
 * Checks : the bounds of fixed size blocks are exact (they do not depend on fonts), and so are the {@link Spring},
 * {@link SizeRequirements} and {@link LayoutStyle} values ; the bounds of the forms depend on the fonts (same machine
 * comparison only, informational).
 */
@Singleton
public class SwingLayoutsPage implements FeaturePage {

    private static final int[] COLORS = { 0x4FC3F7, 0xFFB74D, 0x81C784, 0xE57373, 0xBA68C8, 0x4DB6AC, 0xFFF176 };

    // per build state
    private Demos demos;
    private ChecksView exact;
    private ChecksView styles;
    private ChecksView forms;

    /** The containers and components of one build, measured once laid out. */
    private static final class Demos {
        Box tiled;
        Block tiledA, tiledB, tiledC;
        Box aligned;
        Block alignedLeft, alignedCenter, alignedRight;
        Box filled;
        Box.Filler filler;
        Block fillerC;
        Box lineRtl;
        Block rtlA, rtlB;
        JPanel overlay;
        Block overlayBig, overlayMid, overlaySmall;
        JPanel spring;
        JLabel[] springLabels;
        JTextField[] springFields;
        JTextField springHalf;
        JPanel group;
        JLabel groupLabel;
        JTextField groupField;
        JButton groupFind, groupCancel;
        JCheckBox groupCase, groupWrap, groupWhole, groupBack;
        JPanel groupBlocks;
        Block gA, gB, gC, gHidden;
        JPanel gridBag;
        GridBagLayout gridBagLayout;
        JLabel gbFirst;
        JTextField gbFirstField;
        JButton gbOk, gbCancel;
        JPanel gridBlocks;
        GridBagLayout gridBlocksLayout;
        Block gridA, gridB, gridC, gridD;
        JScrollPane scroll;
    }

    @Override
    public String id() {
        return "swing-layouts";
    }

    @Override
    public String title() {
        return "Swing layouts";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 910;
    }

    @Override
    public Component build() {
        Demos d = new Demos();
        demos = d;

        JPanel boxes = InfraSupport.column(8,
                Ui.caption("X_AXIS : A, glue, B, strut 10, C (300 px)"), tiled(d),
                Ui.caption("Y_AXIS : alignmentX 0, 0.5, 1 (300 px)"), aligned(d),
                Ui.caption("Box.Filler 10/30/60 between blocks (200 px)"), filled(d),
                Ui.caption("LINE_AXIS, RIGHT_TO_LEFT (200 px)"), lineRtl(d));
        JPanel overlay = InfraSupport.column(8, Ui.caption("alignments 0.5, 0 and 1"), overlay(d));
        JPanel spring = InfraSupport.column(8, Ui.caption("labels aligned with Spring.max, half width field"),
                spring(d));
        JPanel group = InfraSupport.column(8, Ui.caption("baseline row, linked button sizes, preferred gaps"), group(d),
                Ui.caption("blocks : gap 10, A, gap 20, B, RELATED, C ; hidden D (honorsVisibility false)"),
                groupBlocks(d),
                Ui.caption("ScrollPaneLayout : view, row and column headers, corner, scroll bars always shown"),
                scrollLayout(d));
        JPanel gridBag = InfraSupport.column(8, Ui.caption("baseline anchors, weights, insets, REMAINDER"), gridBag(d),
                Ui.caption("blocks : weightx 0, 1, 2 ; weighty 1 ; anchors"), gridBlocks(d));

        exact = ChecksView.table("Exact geometry (fixed size blocks, springs, size requirements)",
                List.of(Check.info("layout", "pending")));
        styles = ChecksView.table("LayoutStyle (Metal)", List.of(Check.info("layout", "pending")), 250,
                InfraSupport.HALF);
        forms = ChecksView.table("Forms (font dependent)", List.of(Check.info("layout", "pending")), 190,
                InfraSupport.HALF);

        return InfraSupport.column(14,
                Ui.text("Swing layout managers laid out inside the page. Colored blocks have a fixed size (minimum = "
                        + "preferred = maximum), so their bounds are exact expectations ; the forms use real "
                        + "components (their bounds depend on the fonts).", 1000),
                InfraSupport.row(14,
                        InfraSupport.titled("BoxLayout and Box", 330, boxes),
                        InfraSupport.titled("OverlayLayout", 190, overlay),
                        InfraSupport.titled("SpringLayout", 480, spring)),
                InfraSupport.row(14,
                        InfraSupport.titled("GroupLayout", 520, group),
                        InfraSupport.titled("GridBagLayout", 494, gridBag)),
                exact,
                InfraSupport.row(12, styles, forms));
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Demos d = demos;
        ChecksView exactView = exact;
        ChecksView stylesView = styles;
        ChecksView formsView = forms;
        return Edt.rounds(2).thenAccept(v -> {
            exactView.setChecks(exactChecks(d));
            stylesView.setChecks(styleChecks(d));
            formsView.setChecks(formChecks(d));
        });
    }

    @Override
    public void dispose(Component content) {
        demos = null;
        exact = null;
        styles = null;
        forms = null;
    }

    // ------------------------------------------------------------------------------------------------ BoxLayout

    private static Block block(String name, int index, int width, int height) {
        return new Block(name, COLORS[index % COLORS.length], width, height);
    }

    private static Box fixed(Box box, int width, int height) {
        Dimension size = new Dimension(width, height);
        box.setPreferredSize(size);
        box.setMinimumSize(size);
        box.setMaximumSize(size);
        box.setOpaque(true);
        box.setBackground(new java.awt.Color(0xECEFF1));
        return box;
    }

    private static Box tiled(Demos d) {
        Box box = fixed(Box.createHorizontalBox(), 300, 30);
        d.tiledA = block("A", 0, 40, 24);
        d.tiledB = block("B", 1, 40, 24);
        d.tiledC = block("C", 2, 40, 24);
        box.add(d.tiledA);
        box.add(Box.createHorizontalGlue());
        box.add(d.tiledB);
        box.add(Box.createHorizontalStrut(10));
        box.add(d.tiledC);
        d.tiled = box;
        return box;
    }

    private static Box aligned(Demos d) {
        Box box = fixed(Box.createVerticalBox(), 300, 72);
        d.alignedLeft = block("0", 3, 80, 20).align(Component.LEFT_ALIGNMENT, 0.5f);
        d.alignedCenter = block("0.5", 4, 140, 20).align(Component.CENTER_ALIGNMENT, 0.5f);
        d.alignedRight = block("1", 5, 200, 20).align(Component.RIGHT_ALIGNMENT, 0.5f);
        box.add(d.alignedLeft);
        box.add(Box.createRigidArea(new Dimension(0, 6)));
        box.add(d.alignedCenter);
        box.add(Box.createVerticalStrut(6));
        box.add(d.alignedRight);
        d.aligned = box;
        return box;
    }

    private static Box filled(Demos d) {
        Box box = fixed(Box.createHorizontalBox(), 200, 30);
        d.filler = new Box.Filler(new Dimension(10, 0), new Dimension(30, 0), new Dimension(60, Short.MAX_VALUE));
        d.fillerC = block("C", 2, 40, 24);
        box.add(block("A", 0, 40, 24));
        box.add(d.filler);
        box.add(d.fillerC);
        d.filled = box;
        return box;
    }

    private static Box lineRtl(Demos d) {
        Box box = fixed(new Box(BoxLayout.LINE_AXIS), 200, 30);
        d.rtlA = block("A", 0, 40, 24);
        d.rtlB = block("B", 1, 40, 24);
        box.add(d.rtlA);
        box.add(Box.createHorizontalStrut(8));
        box.add(d.rtlB);
        box.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        d.lineRtl = box;
        return box;
    }

    // ------------------------------------------------------------------------------------------------ OverlayLayout

    private static JPanel overlay(Demos d) {
        JPanel panel = new JPanel() {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                // overlapping children : paint them all when one is repainted
                return false;
            }
        };
        panel.setLayout(new OverlayLayout(panel));
        panel.setBackground(new java.awt.Color(0xECEFF1));
        d.overlaySmall = block("S", 6, 40, 30).align(1f, 1f);
        d.overlayMid = block("M", 1, 100, 60).align(0f, 0f);
        d.overlayBig = block("BIG", 0, 160, 110).align(0.5f, 0.5f);
        // the first child is painted on top
        panel.add(d.overlaySmall);
        panel.add(d.overlayMid);
        panel.add(d.overlayBig);
        d.overlay = panel;
        return panel;
    }

    // ------------------------------------------------------------------------------------------------ SpringLayout

    private static JPanel spring(Demos d) {
        SpringLayout layout = new SpringLayout();
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        String[] labels = { "Name:", "Email address:", "Phone:" };
        d.springLabels = new JLabel[labels.length];
        d.springFields = new JTextField[labels.length];
        Spring labelWidth = Spring.constant(0);
        for (int i = 0; i < labels.length; i++) {
            d.springLabels[i] = new JLabel(labels[i], SwingConstants.TRAILING);
            d.springFields[i] = new JTextField(i == 1 ? "duke@example.org" : i == 0 ? "Duke" : "+1 555 0100", 20);
            panel.add(d.springLabels[i]);
            panel.add(d.springFields[i]);
            labelWidth = Spring.max(labelWidth, Spring.width(d.springLabels[i]));
        }
        Spring y = Spring.constant(6);
        for (int i = 0; i < labels.length; i++) {
            SpringLayout.Constraints label = layout.getConstraints(d.springLabels[i]);
            SpringLayout.Constraints field = layout.getConstraints(d.springFields[i]);
            label.setX(Spring.constant(6));
            label.setWidth(labelWidth);
            field.setX(Spring.sum(Spring.sum(Spring.constant(6), labelWidth), Spring.constant(12)));
            field.setY(y);
            // labels share the baseline of their field
            layout.putConstraint(SpringLayout.BASELINE, d.springLabels[i], 0, SpringLayout.BASELINE,
                    d.springFields[i]);
            y = Spring.sum(field.getConstraint(SpringLayout.SOUTH), Spring.constant(6));
        }
        d.springHalf = new JTextField("half width", 10);
        panel.add(d.springHalf);
        SpringLayout.Constraints half = layout.getConstraints(d.springHalf);
        half.setX(layout.getConstraints(d.springFields[1]).getX());
        half.setY(y);
        half.setWidth(Spring.scale(Spring.width(d.springFields[1]), 0.5f));
        SpringLayout.Constraints container = layout.getConstraints(panel);
        container.setConstraint(SpringLayout.EAST,
                Spring.sum(layout.getConstraint(SpringLayout.EAST, d.springFields[1]), Spring.constant(6)));
        container.setConstraint(SpringLayout.SOUTH, Spring.sum(half.getConstraint(SpringLayout.SOUTH),
                Spring.constant(6)));
        d.spring = panel;
        return panel;
    }

    // ------------------------------------------------------------------------------------------------ GroupLayout

    private static JPanel group(Demos d) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        d.groupLabel = new JLabel("Find what:");
        d.groupField = new JTextField("layout", 16);
        d.groupCase = new JCheckBox("Match case", true);
        d.groupWrap = new JCheckBox("Wrap around");
        d.groupWhole = new JCheckBox("Whole words");
        d.groupBack = new JCheckBox("Search backwards");
        d.groupFind = new JButton("Find");
        d.groupCancel = new JButton("Cancel");
        for (JComponent c : List.of(d.groupCase, d.groupWrap, d.groupWhole, d.groupBack)) {
            c.setOpaque(false);
        }

        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addComponent(d.groupLabel)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(d.groupField)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(d.groupCase)
                                        .addComponent(d.groupWhole))
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(d.groupWrap)
                                        .addComponent(d.groupBack))))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(d.groupFind)
                        .addComponent(d.groupCancel)));
        layout.linkSize(SwingConstants.HORIZONTAL, d.groupFind, d.groupCancel);
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(d.groupLabel)
                        .addComponent(d.groupField)
                        .addComponent(d.groupFind))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(d.groupCase)
                                        .addComponent(d.groupWrap))
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(d.groupWhole)
                                        .addComponent(d.groupBack)))
                        .addComponent(d.groupCancel)));
        d.group = panel;
        return panel;
    }

    private static JPanel groupBlocks(Demos d) {
        JPanel panel = new JPanel();
        panel.setBackground(new java.awt.Color(0xECEFF1));
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setHonorsVisibility(false);
        d.gA = block("A", 0, 50, 24);
        d.gB = block("B", 1, 60, 24);
        d.gC = block("C", 2, 30, 24);
        d.gHidden = block("D", 3, 40, 24);
        d.gHidden.setVisible(false);
        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addGap(10)
                .addComponent(d.gA)
                .addGap(20)
                .addComponent(d.gB)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(d.gC)
                .addGap(10)
                .addComponent(d.gHidden)
                .addGap(10));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGap(4)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addComponent(d.gA)
                        .addComponent(d.gB, 16, 16, 16)
                        .addComponent(d.gC)
                        .addComponent(d.gHidden))
                .addGap(4));
        d.groupBlocks = panel;
        return panel;
    }

    private static JScrollPane scrollLayout(Demos d) {
        JScrollPane scroll = new JScrollPane(block("view 400x300", 0, 400, 300),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setRowHeaderView(block("row", 1, 30, 300));
        scroll.setColumnHeaderView(block("column header", 2, 400, 20));
        scroll.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, block("", 3, 30, 20));
        scroll.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, block("", 4, 10, 10));
        scroll.setPreferredSize(new Dimension(260, 130));
        d.scroll = scroll;
        return scroll;
    }

    // ------------------------------------------------------------------------------------------------ GridBagLayout

    private static JPanel gridBag(Demos d) {
        GridBagLayout layout = new GridBagLayout();
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);

        d.gbFirst = new JLabel("First name:");
        d.gbFirstField = new JTextField("Duke", 14);
        String[][] rows = { { "First name:", null }, { "Last name:", "Java" }, { "Country:", null } };
        for (int row = 0; row < rows.length; row++) {
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.BASELINE_TRAILING;
            c.gridwidth = 1;
            panel.add(row == 0 ? d.gbFirst : new JLabel(rows[row][0]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.BASELINE;
            c.gridwidth = GridBagConstraints.REMAINDER;
            Component field = row == 0 ? d.gbFirstField
                    : row == 1 ? new JTextField(rows[row][1], 14)
                            : new JComboBox<>(new String[] { "Saudi Arabia", "Belgium", "Japan" });
            panel.add(field, c);
        }
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.FIRST_LINE_END;
        c.gridwidth = 1;
        panel.add(new JLabel("Notes:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = GridBagConstraints.REMAINDER;
        JTextArea notes = new JTextArea("GridBagConstraints.BOTH\nweightx 1, weighty 1", 3, 20);
        panel.add(new JScrollPane(notes), c);

        d.gbOk = new JButton("OK");
        d.gbCancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints b = new GridBagConstraints();
        b.insets = new Insets(0, 6, 0, 0);
        b.ipadx = 12;
        buttons.add(d.gbOk, b);
        buttons.add(d.gbCancel, b);
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LAST_LINE_END;
        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(buttons, c);
        panel.setPreferredSize(new Dimension(470, 200));
        d.gridBag = panel;
        d.gridBagLayout = layout;
        return panel;
    }

    private static JPanel gridBlocks(Demos d) {
        GridBagLayout layout = new GridBagLayout();
        JPanel panel = new JPanel(layout);
        panel.setBackground(new java.awt.Color(0xECEFF1));
        Dimension size = new Dimension(460, 80);
        panel.setPreferredSize(size);
        d.gridA = block("A", 0, 40, 20);
        d.gridB = block("B", 1, 40, 20);
        d.gridC = block("C", 2, 40, 20);
        d.gridD = block("D", 3, 40, 20);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        panel.add(d.gridA, c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.CENTER;
        panel.add(d.gridB, c);
        c.gridx = 2;
        c.weightx = 2;
        c.anchor = GridBagConstraints.LAST_LINE_END;
        c.insets = new Insets(0, 0, 5, 5);
        panel.add(d.gridC, c);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 0;
        c.weighty = 0;
        c.insets = new Insets(4, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        d.gridD.setMaximumSize(new Dimension(Short.MAX_VALUE, 20));
        panel.add(d.gridD, c);
        d.gridBlocks = panel;
        d.gridBlocksLayout = layout;
        return panel;
    }

    // ------------------------------------------------------------------------------------------------ checks

    private static List<Check> exactChecks(Demos d) {
        List<Check> checks = new ArrayList<>();
        // BoxLayout X_AXIS : the glue takes the 170 extra pixels, the strut keeps 10
        checks.add(Checks.expect("BoxLayout X_AXIS A, B, C bounds", "0,3 40x24 | 210,3 40x24 | 260,3 40x24",
                () -> InfraSupport.bounds(d.tiledA) + " | " + InfraSupport.bounds(d.tiledB) + " | "
                        + InfraSupport.bounds(d.tiledC)));
        checks.add(Checks.expect("BoxLayout Y_AXIS alignmentX 0, 0.5, 1 bounds",
                "214,0 80x20 | 144,26 140x20 | 14,52 200x20",
                () -> InfraSupport.bounds(d.alignedLeft) + " | " + InfraSupport.bounds(d.alignedCenter) + " | "
                        + InfraSupport.bounds(d.alignedRight)));
        checks.add(Checks.expect("Box.Filler stretched to its maximum", "40,0 60x30 ; C at 100",
                () -> InfraSupport.bounds(d.filler) + " ; C at " + d.fillerC.getX()));
        checks.add(Checks.expect("Box.Filler.changeShape(10, 10, 10)", "10x30 ; C at 50", () -> {
            Dimension ten = new Dimension(10, 0);
            d.filler.changeShape(ten, ten, new Dimension(10, Short.MAX_VALUE));
            d.filled.validate();
            String result = d.filler.getWidth() + "x" + d.filler.getHeight() + " ; C at " + d.fillerC.getX();
            Dimension min = new Dimension(10, 0);
            d.filler.changeShape(min, new Dimension(30, 0), new Dimension(60, Short.MAX_VALUE));
            d.filled.validate();
            return result;
        }));
        checks.add(Checks.expect("BoxLayout LINE_AXIS RIGHT_TO_LEFT : A right of B", "160,3 40x24 | 112,3 40x24",
                () -> InfraSupport.bounds(d.rtlA) + " | " + InfraSupport.bounds(d.rtlB)));
        checks.add(Checks.expect("OverlayLayout BIG, M, S bounds", "0,0 160x110 | 80,55 100x60 | 40,25 40x30",
                () -> InfraSupport.bounds(d.overlayBig) + " | " + InfraSupport.bounds(d.overlayMid) + " | "
                        + InfraSupport.bounds(d.overlaySmall)));
        checks.add(Checks.expect("OverlayLayout preferred size", "180x115",
                () -> InfraSupport.size(d.overlay.getLayout().preferredLayoutSize(d.overlay))));
        checks.add(Checks.expect("GroupLayout blocks A, B, C x (RELATED gap from LayoutStyle)",
                "10 | 80 | " + (140 + LayoutStyle.getInstance().getPreferredGap(d.gB, d.gC, ComponentPlacement.RELATED,
                        SwingConstants.EAST, d.groupBlocks)),
                () -> d.gA.getX() + " | " + d.gB.getX() + " | " + d.gC.getX()));
        checks.add(Checks.expect("GroupLayout size 16 in a CENTER group : A, B bounds", "10,4 50x24 | 80,8 60x16",
                () -> InfraSupport.bounds(d.gA) + " | " + InfraSupport.bounds(d.gB)));
        checks.add(Checks.expect("GroupLayout honorsVisibility(false) : hidden D keeps its space", "true",
                () -> !d.gHidden.isVisible() && d.gHidden.getWidth() == 40
                        && d.gHidden.getX() == d.gC.getX() + 30 + 10));
        checks.add(Checks.expect("ScrollPaneLayout : viewport | row header | column header | upper left corner",
                "31,21 212x92 | 1,21 30x92 | 31,1 212x20 | 1,1 30x20", () -> InfraSupport.bounds(d.scroll.getViewport()) + " | "
                        + InfraSupport.bounds(d.scroll.getRowHeader()) + " | "
                        + InfraSupport.bounds(d.scroll.getColumnHeader()) + " | "
                        + InfraSupport.bounds(d.scroll.getCorner(ScrollPaneConstants.UPPER_LEFT_CORNER))));
        checks.add(Checks.expect("ScrollPaneLayout : vertical | horizontal scroll bar | lower right corner",
                "243,21 15x92 | 31,113 212x15 | 243,113 15x15", () -> InfraSupport.bounds(d.scroll.getVerticalScrollBar()) + " | "
                        + InfraSupport.bounds(d.scroll.getHorizontalScrollBar()) + " | "
                        + InfraSupport.bounds(d.scroll.getCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER))));
        checks.add(Checks.expect("ScrollPaneLayout : viewport extent + headers + scroll bars = scroll pane size", "true",
                () -> {
                    java.awt.Insets in = d.scroll.getInsets();
                    return d.scroll.getRowHeader().getWidth() + d.scroll.getViewport().getWidth()
                            + d.scroll.getVerticalScrollBar().getWidth() + in.left + in.right == d.scroll.getWidth()
                            && d.scroll.getColumnHeader().getHeight() + d.scroll.getViewport().getHeight()
                                    + d.scroll.getHorizontalScrollBar().getHeight() + in.top + in.bottom
                                    == d.scroll.getHeight();
                }));
        checks.add(Checks.expect("GridBagLayout blocks A, B, C, D bounds",
                "0,0 40x20 | 95,18 40x20 | 414,31 40x20 | 0,60 459x20",
                () -> InfraSupport.bounds(d.gridA) + " | " + InfraSupport.bounds(d.gridB) + " | "
                        + InfraSupport.bounds(d.gridC) + " | " + InfraSupport.bounds(d.gridD)));
        checks.add(Checks.expect("GridBagLayout blocks weights (columns ; rows)", "0.0 1.0 2.0 ; 1.0 0.0",
                () -> weights(d.gridBlocksLayout.getLayoutWeights())));
        checks.add(Checks.expect("GridBagLayout blocks dimensions (columns ; rows)", "40 151 268 ; 56 24",
                () -> dims(d.gridBlocksLayout.getLayoutDimensions())));
        checks.add(Checks.expect("GridBagLayout form weights (columns ; rows)", "0.0 1.0 ; 0.0 0.0 0.0 1.0 0.0",
                () -> weights(d.gridBagLayout.getLayoutWeights())));

        // Spring arithmetic
        checks.add(Checks.expect("Spring.constant(5, 20, 50)", "5/20/50 value 20",
                () -> spring(Spring.constant(5, 20, 50))));
        checks.add(Checks.expect("Spring.sum(constant(5, 20, 50), constant(10))", "15/30/60 value 30",
                () -> spring(Spring.sum(Spring.constant(5, 20, 50), Spring.constant(10)))));
        checks.add(Checks.expect("Spring.max(constant(5, 20, 50), constant(8, 25, 30))", "8/25/50 value 25",
                () -> spring(Spring.max(Spring.constant(5, 20, 50), Spring.constant(8, 25, 30)))));
        checks.add(Checks.expect("Spring.minus(constant(5, 20, 50))", "-50/-20/-5 value -20",
                () -> spring(Spring.minus(Spring.constant(5, 20, 50)))));
        checks.add(Checks.expect("Spring.scale(constant(5, 20, 50), 1.5)", "8/30/75 value 30",
                () -> spring(Spring.scale(Spring.constant(5, 20, 50), 1.5f))));
        checks.add(Checks.expect("Spring.sum(...).setValue(45) distributes the strain", "a 30 b 15", () -> {
            Spring a = Spring.constant(10, 20, 40);
            Spring b = Spring.constant(10, 10, 20);
            Spring sum = Spring.sum(a, b);
            sum.setValue(45);
            return "a " + a.getValue() + " b " + b.getValue();
        }));
        checks.add(Checks.expect("Spring.width(block 40x24)", "40/40/40 value 40",
                () -> spring(Spring.width(d.tiledA))));
        checks.add(Checks.expect("SpringLayout half field = 0.5 x email field", "true",
                () -> d.springHalf.getWidth() == Math.round(d.springFields[1].getWidth() * 0.5f)
                        || d.springHalf.getWidth() == (int) (d.springFields[1].getWidth() * 0.5f)));
        checks.add(Checks.expect("SpringLayout labels right edges aligned", "true", () -> Arrays
                .stream(d.springLabels).map(l -> l.getX() + l.getWidth()).distinct().count() == 1));

        // SizeRequirements (used by BoxLayout and OverlayLayout)
        checks.add(Checks.expect("SizeRequirements.calculateTiledPositions(100, 30/40/50 x3)", "0 33 66 ; 33 33 33",
                () -> {
                    SizeRequirements[] children = new SizeRequirements[3];
                    Arrays.fill(children, new SizeRequirements(30, 40, 50, 0.5f));
                    int[] offsets = new int[3];
                    int[] spans = new int[3];
                    SizeRequirements.calculateTiledPositions(100, null, children, offsets, spans);
                    return ints(offsets) + " ; " + ints(spans);
                }));
        checks.add(Checks.expect("SizeRequirements.calculateAlignedPositions(100, alignments 0/0.5/1)",
                "50 25 0 ; 50 50 50", () -> {
                    SizeRequirements[] children = { new SizeRequirements(50, 50, 50, 0f),
                            new SizeRequirements(50, 50, 50, 0.5f), new SizeRequirements(50, 50, 50, 1f) };
                    int[] offsets = new int[3];
                    int[] spans = new int[3];
                    SizeRequirements.calculateAlignedPositions(100, new SizeRequirements(100, 100, 100, 0.5f),
                            children, offsets, spans);
                    return ints(offsets) + " ; " + ints(spans);
                }));
        checks.add(Checks.expect("SizeRequirements.getTiledSizeRequirements", "90/120/150 align 0.5",
                () -> {
                    SizeRequirements[] children = new SizeRequirements[3];
                    Arrays.fill(children, new SizeRequirements(30, 40, 50, 0.5f));
                    SizeRequirements total = SizeRequirements.getTiledSizeRequirements(children);
                    return total.minimum + "/" + total.preferred + "/" + total.maximum + " align "
                            + Checks.num(total.alignment, 1);
                }));
        return checks;
    }

    private static List<Check> styleChecks(Demos d) {
        List<Check> checks = new ArrayList<>();
        LayoutStyle style = LayoutStyle.getInstance();
        checks.add(Checks.expect("LayoutStyle.getInstance()", "MetalLookAndFeel$MetalLayoutStyle",
                () -> style.getClass().getName().substring(style.getClass().getName().lastIndexOf('.') + 1)));
        JLabel label = new JLabel("Label");
        JTextField field = new JTextField("field");
        JButton button1 = new JButton("One");
        JButton button2 = new JButton("Two");
        JCheckBox check1 = new JCheckBox("One");
        JCheckBox check2 = new JCheckBox("Two");
        JPanel parent = new JPanel();
        checks.add(gap("label -> field RELATED EAST", 12, style, label, field, ComponentPlacement.RELATED,
                SwingConstants.EAST, parent));
        checks.add(gap("label -> field UNRELATED EAST", 18, style, label, field, ComponentPlacement.UNRELATED,
                SwingConstants.EAST, parent));
        checks.add(gap("button -> button RELATED EAST", 6, style, button1, button2, ComponentPlacement.RELATED,
                SwingConstants.EAST, parent));
        checks.add(gap("button -> button UNRELATED EAST", 12, style, button1, button2, ComponentPlacement.UNRELATED,
                SwingConstants.EAST, parent));
        checks.add(gap("check box -> check box RELATED SOUTH", 0, style, check1, check2, ComponentPlacement.RELATED,
                SwingConstants.SOUTH, parent));
        checks.add(gap("check box -> check box RELATED EAST", 0, style, check1, check2, ComponentPlacement.RELATED,
                SwingConstants.EAST, parent));
        checks.add(gap("label -> check box INDENT EAST", 12, style, label, check1, ComponentPlacement.INDENT,
                SwingConstants.EAST, parent));
        checks.add(gap("field -> field RELATED SOUTH", 6, style, field, new JTextField("other"),
                ComponentPlacement.RELATED, SwingConstants.SOUTH, parent));
        for (int position : new int[] { SwingConstants.NORTH, SwingConstants.WEST, SwingConstants.SOUTH,
                SwingConstants.EAST }) {
            checks.add(Checks.expect("container gap button " + position(position), 12,
                    () -> style.getContainerGap(button1, position, parent)));
        }
        return checks;
    }

    private static Check gap(String name, int expected, LayoutStyle style, JComponent a, JComponent b,
            ComponentPlacement type, int position, Container parent) {
        return Checks.expect(name, expected, () -> style.getPreferredGap(a, b, type, position, parent));
    }

    private static String position(int position) {
        return switch (position) {
            case SwingConstants.NORTH -> "NORTH";
            case SwingConstants.SOUTH -> "SOUTH";
            case SwingConstants.EAST -> "EAST";
            default -> "WEST";
        };
    }

    private static List<Check> formChecks(Demos d) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.info("SpringLayout form size", () -> InfraSupport.size(d.spring.getSize())));
        for (int i = 0; i < d.springFields.length; i++) {
            int index = i;
            checks.add(Checks.info("Spring row " + i + " label | field",
                    () -> InfraSupport.bounds(d.springLabels[index]) + " | "
                            + InfraSupport.bounds(d.springFields[index])));
        }
        checks.add(Checks.expect("SpringLayout label baseline = field baseline", "true",
                () -> baseline(d.springLabels[0]) == baseline(d.springFields[0])));
        checks.add(Checks.info("GroupLayout label | field | Find",
                () -> InfraSupport.bounds(d.groupLabel) + " | " + InfraSupport.bounds(d.groupField) + " | "
                        + InfraSupport.bounds(d.groupFind)));
        checks.add(Checks.info("GroupLayout check boxes",
                () -> List.of(d.groupCase, d.groupWrap, d.groupWhole, d.groupBack).stream()
                        .map(InfraSupport::bounds).collect(Collectors.joining(" | "))));
        checks.add(Checks.expect("GroupLayout baseline row aligned", "true",
                () -> baseline(d.groupLabel) == baseline(d.groupField)
                        && baseline(d.groupField) == baseline(d.groupFind)));
        checks.add(Checks.expect("GroupLayout.linkSize : Find width = Cancel width", "true",
                () -> d.groupFind.getWidth() == d.groupCancel.getWidth() && d.groupFind.getWidth() > 0));
        checks.add(Checks.info("GridBagLayout form dimensions", () -> dims(d.gridBagLayout.getLayoutDimensions())));
        checks.add(Checks.info("GridBagLayout form origin",
                () -> d.gridBagLayout.getLayoutOrigin().x + "," + d.gridBagLayout.getLayoutOrigin().y));
        checks.add(Checks.expect("GridBagLayout BASELINE_TRAILING label baseline = field baseline", "true",
                () -> baseline(d.gbFirst) == baseline(d.gbFirstField)));
        checks.add(Checks.expect("GridBagLayout LAST_LINE_END buttons at the right edge", "true",
                () -> {
                    Container buttons = d.gbOk.getParent();
                    return buttons.getX() + buttons.getWidth() == d.gridBag.getWidth() - 4;
                }));
        checks.add(Checks.info("GridBagLayout OK | Cancel (ipadx 12)",
                () -> InfraSupport.bounds(d.gbOk) + " | " + InfraSupport.bounds(d.gbCancel)));
        return checks;
    }

    private static int baseline(JComponent c) {
        return c.getY() + c.getBaseline(c.getWidth(), c.getHeight());
    }

    private static String spring(Spring s) {
        return s.getMinimumValue() + "/" + s.getPreferredValue() + "/" + s.getMaximumValue() + " value "
                + s.getValue();
    }

    private static String ints(int[] values) {
        return Arrays.stream(values).mapToObj(Integer::toString).collect(Collectors.joining(" "));
    }

    private static String dims(int[][] dims) {
        return ints(dims[0]) + " ; " + ints(dims[1]);
    }

    private static String weights(double[][] weights) {
        return Arrays.stream(weights[0]).mapToObj(w -> Checks.num(w, 1)).collect(Collectors.joining(" ")) + " ; "
                + Arrays.stream(weights[1]).mapToObj(w -> Checks.num(w, 1)).collect(Collectors.joining(" "));
    }
}
