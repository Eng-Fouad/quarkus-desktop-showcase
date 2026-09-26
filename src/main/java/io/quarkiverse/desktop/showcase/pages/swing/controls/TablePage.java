package io.quarkiverse.desktop.showcase.pages.swing.controls;

import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.column;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.date;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.ints;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.resourceIcon;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.row;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.runAction;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.section;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.swatch;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.typed;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SortOrder;
import javax.swing.RowSorter.SortKey;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

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
 * Tables : a {@code JTable} with a column of every class that has a default renderer ({@code Object}, {@code Number},
 * {@code Float}, {@code Double}, {@code Date}, {@code Icon}, {@code Boolean}) plus {@code Long}, {@code BigDecimal} and a
 * custom renderer, multi-key sorting ({@code TableRowSorter}, header sort icons), a {@code RowFilter}, a moved column,
 * row heights, cell selection, and an active editor with an invalid number ; editing of number, decimal, text and
 * boolean cells through the default editors.
 * <p>
 * Native paths exercised on purpose : {@code JTable.GenericEditor} creates the edited value with the {@code String}
 * constructor of the column class, by reflection ({@code Integer}, {@code Double}, {@code BigDecimal}, {@code String}) ;
 * table actions loaded by reflection ({@code LazyActionMap}) ; the Metal sort icons ({@code sortUp.png},
 * {@code sortDown.png}) ; the date and number formats of the locale ; an icon from a classpath resource.
 */
@Singleton
public class TablePage implements FeaturePage {

    static final String[] COLUMNS = { "Name", "Count", "Ratio", "Price", "Total", "Big", "Date", "Icon", "Done",
            "Color" };
    static final Class<?>[] CLASSES = { String.class, Integer.class, Float.class, Double.class, Long.class,
            BigDecimal.class, Date.class, Icon.class, Boolean.class, Integer.class };
    private static final int NAME = 0;
    private static final int COUNT = 1;
    private static final int PRICE = 3;
    private static final int BIG = 5;
    private static final int DATE = 6;
    private static final int ICON = 7;
    private static final int DONE = 8;
    private static final int COLOR = 9;

    private ChecksView results;
    private final Readiness readiness = new Readiness();
    private JTable table;
    private JTable filtered;
    private JComponent tables;

    @Override
    public String id() {
        return "swing-table";
    }

    @Override
    public String title() {
        return "Tables";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 60;
    }

    /**
     * The model : typed columns, every cell editable except the icons.
     */
    static final class ShowcaseModel extends DefaultTableModel {

        ShowcaseModel(Object[][] rows) {
            super(rows, COLUMNS);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return CLASSES[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column != ICON;
        }
    }

    /**
     * Colors of the "Color" column (RGB integers) as a swatch and a hex string.
     */
    static final class ColorRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            int rgb = value instanceof Integer i ? i : 0x9E9E9E;
            setIcon(swatch(rgb, 12, 12));
            setText(String.format(Locale.ROOT, "#%06X", rgb));
            return this;
        }
    }

    static Object[][] rows() {
        ImageIcon star = resourceIcon("icons/star-16.png", "star");
        ImageIcon play = resourceIcon("icons/play-16.gif", "play");
        return new Object[][] {
                { "Kiwi", 12, 0.5f, 3.14159, 9_876_543_210L, new BigDecimal("12345.678"), date(2024, 3, 15, 12, 0), star,
                        true, 0x7CB342 },
                { "apple", 7, 1.25f, 0.99, 42L, new BigDecimal("0.10"), date(2023, 12, 31, 12, 0), play, false, 0xE53935 },
                { "Banana", 25, 0.125f, 1234.5, -1L, new BigDecimal("-3.5"), date(2024, 1, 1, 12, 0), star, true,
                        0xFDD835 },
                { "cherry", 3, 2.0f, 12.0, 0L, new BigDecimal("1E+3"), date(2022, 6, 30, 12, 0), play, false, 0xC62828 },
                { "Mango", 18, 0.75f, 2.5, 1_000L, new BigDecimal("99.999"), date(2024, 7, 4, 12, 0), star, false,
                        0xFFA726 },
                { "Orange", 18, 1.5f, 0.001, 123_456L, new BigDecimal("7"), date(2021, 2, 28, 12, 0), play, true,
                        0xEF6C00 },
                { "Plum", 9, 3.75f, 100.0, 77L, new BigDecimal("0.001"), date(2020, 2, 29, 12, 0), star, true, 0x6A1B9A },
                { "Date", 30, 0.0f, 5.55, 5L, new BigDecimal("5.55"), date(2019, 10, 10, 12, 0), play, false, 0x8D6E63 },
                { "(nulls)", null, null, null, null, null, null, null, null, null } };
    }

    private static TableRowSorter<TableModel> sorter(TableModel model) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        Collator collator = Collator.getInstance(Locale.US);
        sorter.setComparator(NAME, collator);
        return sorter;
    }

    private static JTable newTable(TableModel model) {
        JTable table = new JTable(model);
        table.setDefaultRenderer(Integer.class, table.getDefaultRenderer(Number.class));
        table.getColumnModel().getColumn(COLOR).setCellRenderer(new ColorRenderer());
        return table;
    }

    @Override
    public Component build() {
        results = ChecksView.table("Checks", List.of(Check.info("state", "pending")));
        ShowcaseModel model = new ShowcaseModel(rows());

        table = newTable(model);
        TableRowSorter<TableModel> sorter = sorter(model);
        sorter.setSortKeys(List.of(new SortKey(DONE, SortOrder.DESCENDING), new SortKey(COUNT, SortOrder.ASCENDING)));
        table.setRowSorter(sorter);
        table.setRowHeight(20);
        table.setRowHeight(2, 34);
        table.getColumnModel().getColumn(NAME).setPreferredWidth(110);
        table.getColumnModel().getColumn(DATE).setPreferredWidth(110);
        table.getColumnModel().getColumn(BIG).setPreferredWidth(90);
        table.setRowSelectionInterval(4, 5);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(980, table.getPreferredSize().height + table.getTableHeader()
                .getPreferredSize().height + 4));

        filtered = newTable(model);
        TableRowSorter<TableModel> filter = sorter(model);
        filter.setRowFilter(RowFilter.andFilter(List.of(RowFilter.regexFilter("(?i)^[a-m]", NAME),
                RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 5, COUNT))));
        filter.setSortKeys(List.of(new SortKey(NAME, SortOrder.ASCENDING)));
        filtered.setRowSorter(filter);
        filtered.getColumnModel().moveColumn(ICON, 1);
        filtered.getColumnModel().moveColumn(DONE, 2);
        filtered.setShowVerticalLines(false);
        filtered.setGridColor(new Color(0x90A4AE));
        filtered.setCellSelectionEnabled(true);
        filtered.changeSelection(1, 3, false, false);
        filtered.changeSelection(2, 4, false, true);
        filtered.setFillsViewportHeight(true);
        JScrollPane filteredScroll = new JScrollPane(filtered);
        filteredScroll.setPreferredSize(new Dimension(980, 110));

        JPanel content = column(10,
                Ui.text("JTable with the default renderers and editors of each column class. Top : sorted by Done "
                        + "(descending) then Count, rows 4-5 selected, a taller row, and the Count editor left open with "
                        + "an invalid value (red border). Bottom : a RowFilter (name a-m, count > 5), sorted by name, "
                        + "columns moved, cell selection, no vertical lines.", 1000),
                tables = column(10, section("Sorted table, active editor", scroll),
                        section("Filtered table (RowFilter.andFilter of regexFilter and numberFilter)", filteredScroll)),
                Ui.text("JTable hides the selection, the focused cell and the header sort icons when it paints for "
                        + "print (printAll : the capture of the page) : the extra snapshot --painted shows the tables "
                        + "painted like on screen.", 1000),
                results);
        content.setOpaque(true);
        content.setBackground(Color.WHITE);
        ControlsSupport.readyWhenShown(this, content);
        return content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        return readiness.get(content, this::prepare);
    }

    private CompletionStage<?> prepare(Component content) {
        ChecksView view = results;
        JTable shown = table;
        return Edt.rounds(2).thenCompose(v -> {
            // an active editor : an invalid number keeps the editor open with a red border. Started once the table is
            // laid out : a column resize (columnMarginChanged) stops or cancels the editing
            shown.editCellAt(0, COUNT);
            ((JTextField) shown.getEditorComponent()).setText("abc");
            shown.getCellEditor().stopCellEditing();
            return Edt.rounds(2);
        }).thenAccept(v -> view.setChecks(checks()));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(Map.of("painted", ControlsSupport.paintedSnapshot(tables)));
    }

    @Override
    public void dispose(Component content) {
        readiness.reset();
        if (table != null && table.isEditing()) {
            table.getCellEditor().cancelCellEditing();
        }
        results = null;
        table = null;
        filtered = null;
        tables = null;
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private static String text(JTable table, int row, int column) {
        Component c = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
        if (c instanceof JLabel label) {
            return label.getIcon() != null && label.getText().isEmpty()
                    ? "icon " + label.getIcon().getIconWidth() + "x" + label.getIcon().getIconHeight()
                    : label.getText();
        }
        return c instanceof JCheckBox box ? "checkbox " + box.isSelected() : c.getClass().getSimpleName();
    }

    /**
     * Edits the cell (model coordinates, no sorter) with {@code text} through the default editor : the model value.
     */
    private static String edit(int row, int column, String text) {
        JTable table = newTable(new ShowcaseModel(rows()));
        if (!table.editCellAt(row, column)) {
            return "not editable";
        }
        ((JTextField) table.getEditorComponent()).setText(text);
        boolean stopped = table.getCellEditor().stopCellEditing();
        return (stopped ? "" : "not stopped, ") + typed(table.getModel().getValueAt(row, column));
    }

    private List<Check> checks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("UI delegates : table, header", "BasicTableUI, BasicTableHeaderUI",
                () -> table.getUI().getClass().getSimpleName() + ", "
                        + table.getTableHeader().getUI().getClass().getSimpleName()));
        checks.add(Checks.expect("default renderers : Object Number Float Double Date Icon ImageIcon Boolean",
                "UIResource NumberRenderer DoubleRenderer DoubleRenderer DateRenderer IconRenderer IconRenderer "
                        + "BooleanRenderer",
                () -> {
                    JTable t = new JTable();
                    List<String> names = new ArrayList<>();
                    for (Class<?> c : List.of(Object.class, Number.class, Float.class, Double.class, Date.class,
                            Icon.class, ImageIcon.class, Boolean.class)) {
                        names.add(t.getDefaultRenderer(c).getClass().getSimpleName());
                    }
                    return String.join(" ", names);
                }));
        checks.add(Checks.expect("default editors : Object Number Boolean", "GenericEditor NumberEditor BooleanEditor",
                () -> {
                    JTable t = new JTable();
                    return t.getDefaultEditor(Object.class).getClass().getSimpleName() + " "
                            + t.getDefaultEditor(Number.class).getClass().getSimpleName() + " "
                            + t.getDefaultEditor(Boolean.class).getClass().getSimpleName();
                }));
        checks.add(Checks.expect("rendered texts of model row 0 (no sorter)",
                "Kiwi | 12 | 0.5 | 3.142 | 9876543210 | 12345.678 | Mar 15, 2024 | icon 16x16 | checkbox true | #7CB342",
                () -> {
                    JTable t = newTable(new ShowcaseModel(rows()));
                    List<String> texts = new ArrayList<>();
                    for (int c = 0; c < COLUMNS.length; c++) {
                        texts.add(text(t, 0, c));
                    }
                    return String.join(" | ", texts);
                }));
        checks.add(Checks.expect("rendered texts of the null row", "(nulls) |  |  |  |  |  |  |  | checkbox false | #9E9E9E",
                () -> {
                    JTable t = newTable(new ShowcaseModel(rows()));
                    List<String> texts = new ArrayList<>();
                    for (int c = 0; c < COLUMNS.length; c++) {
                        texts.add(text(t, 8, c));
                    }
                    return String.join(" | ", texts);
                }));
        checks.addAll(sortingChecks());
        checks.addAll(editingChecks());
        checks.addAll(layoutChecks());
        return checks;
    }

    private List<Check> sortingChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("sort keys Done DESC, Count ASC : view order",
                "Plum Kiwi Orange Banana cherry apple Mango Date (nulls)", () -> {
                    List<String> names = new ArrayList<>();
                    for (int r = 0; r < table.getRowCount(); r++) {
                        names.add(String.valueOf(table.getValueAt(r, table.convertColumnIndexToView(NAME))));
                    }
                    return String.join(" ", names);
                }));
        checks.add(Checks.expect("convertRowIndexToModel of view rows 0..8", "6,0,5,2,3,1,4,7,8", () -> {
            int[] rows = new int[table.getRowCount()];
            for (int r = 0; r < rows.length; r++) {
                rows[r] = table.convertRowIndexToModel(r);
            }
            return ints(rows);
        }));
        checks.add(Checks.expect("Name sorted by a Collator (en-US) : case insensitive", "(nulls) apple Banana cherry Date "
                + "Kiwi Mango Orange Plum", () -> {
                    JTable t = newTable(new ShowcaseModel(rows()));
                    TableRowSorter<TableModel> s = sorter(t.getModel());
                    s.setSortKeys(List.of(new SortKey(NAME, SortOrder.ASCENDING)));
                    t.setRowSorter(s);
                    List<String> names = new ArrayList<>();
                    for (int r = 0; r < t.getRowCount(); r++) {
                        names.add(String.valueOf(t.getValueAt(r, NAME)));
                    }
                    return String.join(" ", names);
                }));
        checks.add(Checks.expect("RowFilter and(regex (?i)^[a-m], count > 5) : view rows", "apple Banana Date Kiwi Mango",
                () -> {
                    List<String> names = new ArrayList<>();
                    for (int r = 0; r < filtered.getRowCount(); r++) {
                        names.add(String.valueOf(filtered.getModel().getValueAt(filtered.convertRowIndexToModel(r),
                                NAME)));
                    }
                    return String.join(" ", names);
                }));
        checks.add(Checks.expect("RowFilter counts : dateFilter(after 2023-06-01), orFilter, notFilter(regex), "
                + "numberFilter(EQUAL 18)", "4, 3, 6, 2", () -> {
                    TableModel model = new ShowcaseModel(rows());
                    List<String> counts = new ArrayList<>();
                    for (RowFilter<Object, Object> f : List.of(
                            RowFilter.dateFilter(RowFilter.ComparisonType.AFTER, date(2023, 6, 1, 0, 0), DATE),
                            RowFilter.orFilter(List.of(RowFilter.regexFilter("^P", NAME),
                                    RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, 8, COUNT))),
                            RowFilter.notFilter(RowFilter.regexFilter("an", NAME)),
                            RowFilter.numberFilter(RowFilter.ComparisonType.EQUAL, 18, COUNT))) {
                        TableRowSorter<TableModel> s = new TableRowSorter<>(model);
                        s.setRowFilter(f);
                        counts.add(String.valueOf(s.getViewRowCount()));
                    }
                    return String.join(", ", counts);
                }));
        checks.add(Checks.expect("moved columns : view column names, convertColumnIndexToModel(1)",
                "Name Icon Done Count Ratio Price Total Big Date Color, 7", () -> {
                    List<String> names = new ArrayList<>();
                    for (int c = 0; c < filtered.getColumnCount(); c++) {
                        names.add(filtered.getColumnName(c));
                    }
                    return String.join(" ", names) + ", " + filtered.convertColumnIndexToModel(1);
                }));
        checks.add(Checks.expect("header sort icons (Metal icons/sortUp.png, sortDown.png) : class, sizes",
                "ImageIconUIResource 12x12, 12x12", () -> {
                    Icon up = UIManager.getIcon("Table.ascendingSortIcon");
                    Icon down = UIManager.getIcon("Table.descendingSortIcon");
                    return up.getClass().getSimpleName() + " " + up.getIconWidth() + "x" + up.getIconHeight() + ", "
                            + down.getIconWidth() + "x" + down.getIconHeight();
                }));
        return checks;
    }

    private static List<Check> editingChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("edit Count (Integer(String) by reflection)", "Integer 1234", () -> edit(0, COUNT, "1234")));
        checks.add(Checks.expect("edit Price (Double(String))", "Double 2.5", () -> edit(0, PRICE, "2.5")));
        checks.add(Checks.expect("edit Big (BigDecimal(String))", "BigDecimal 3.14159265358979323846",
                () -> edit(0, BIG, "3.14159265358979323846")));
        checks.add(Checks.expect("edit Name (String(String))", "String renamed", () -> edit(0, NAME, "renamed")));
        checks.add(Checks.expect("edit Total (Long(String))", "Long -42", () -> edit(0, 4, "-42")));
        checks.add(Checks.expect("edit Count with \"abc\" : not stopped, red border, still editing",
                "false, #FFFF0000, true", () -> {
                    JTable t = newTable(new ShowcaseModel(rows()));
                    t.editCellAt(0, COUNT);
                    JTextField field = (JTextField) t.getEditorComponent();
                    field.setText("abc");
                    boolean stopped = t.getCellEditor().stopCellEditing();
                    return stopped + ", " + Checks.argb(((LineBorder) field.getBorder()).getLineColor().getRGB()) + ", "
                            + t.isEditing();
                }));
        checks.add(Checks.expect("edit Count with \"\" : null value", "null", () -> edit(0, COUNT, "")));
        checks.add(Checks.expect("edit Done (BooleanEditor, JCheckBox)", "Boolean false", () -> {
            JTable t = newTable(new ShowcaseModel(rows()));
            t.editCellAt(0, DONE);
            ((JCheckBox) t.getEditorComponent()).setSelected(false);
            t.getCellEditor().stopCellEditing();
            return typed(t.getModel().getValueAt(0, DONE));
        }));
        checks.add(Checks.expect("Icon column not editable", "false", () -> newTable(new ShowcaseModel(rows()))
                .editCellAt(0, ICON)));
        checks.add(Checks.expect("TableModelEvents : setValueAt, addRow, removeRow, structure",
                "UPDATE 1/1 | INSERT 9/-1 | DELETE 0/-1 | UPDATE -1/-1", () -> {
                    DefaultTableModel model = new ShowcaseModel(rows());
                    EventLog log = new EventLog();
                    model.addTableModelListener(e -> log.add((switch (e.getType()) {
                        case TableModelEvent.INSERT -> "INSERT";
                        case TableModelEvent.DELETE -> "DELETE";
                        default -> "UPDATE";
                    }) + " " + e.getFirstRow() + "/" + e.getColumn()));
                    model.setValueAt(99, 1, COUNT);
                    model.addRow(new Object[COLUMNS.length]);
                    model.removeRow(0);
                    model.fireTableStructureChanged();
                    return log.toString();
                }));
        checks.add(Checks.expect("table actions (BasicTableUI.loadActionMap) : next row, next column, next row cell ; "
                + "startEditing without focus (asks for the focus, no editor)", "1/0, 1/1, 2/1 | editing false", () -> {
                    JTable t = newTable(new ShowcaseModel(rows()));
                    t.changeSelection(0, 0, false, false);
                    List<String> cells = new ArrayList<>();
                    for (String action : List.of("selectNextRow", "selectNextColumn", "selectNextRowCell")) {
                        runAction(t, action);
                        cells.add(t.getSelectionModel().getLeadSelectionIndex() + "/"
                                + t.getColumnModel().getSelectionModel().getLeadSelectionIndex());
                    }
                    runAction(t, "startEditing");
                    return String.join(", ", cells) + " | editing " + t.isEditing();
                }));
        checks.add(Checks.expect("selection : row mode rows / cell mode block", "4,5 / rows 1,2 columns 3,4", () -> {
            JTable t = newTable(new ShowcaseModel(rows()));
            t.setRowSelectionInterval(4, 5);
            JTable c = newTable(new ShowcaseModel(rows()));
            c.setCellSelectionEnabled(true);
            c.changeSelection(1, 3, false, false);
            c.changeSelection(2, 4, false, true);
            return ints(t.getSelectedRows()) + " / rows " + ints(c.getSelectedRows()) + " columns "
                    + ints(c.getSelectedColumns());
        }));
        return checks;
    }

    private List<Check> layoutChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("row heights : default, row 2, rowAtPoint(center of each row)", "20, 34, true", () -> {
            for (int r = 0; r < table.getRowCount(); r++) {
                Rectangle cell = table.getCellRect(r, 0, true);
                if (table.rowAtPoint(new Point(cell.x + 2, cell.y + cell.height / 2)) != r) {
                    return "row " + r;
                }
            }
            return table.getRowHeight() + ", " + table.getRowHeight(2) + ", true";
        }));
        checks.add(Checks.expect("AUTO_RESIZE_SUBSEQUENT_COLUMNS : column widths fill the table width", "true", () -> {
            int sum = 0;
            for (int c = 0; c < table.getColumnCount(); c++) {
                TableColumn column = table.getColumnModel().getColumn(c);
                sum += column.getWidth();
            }
            return sum == table.getWidth() ? "true" : sum + " != " + table.getWidth();
        }));
        checks.add(Checks.expect("shown editor : editing row/column, border", "0/1, #FFFF0000",
                () -> table.getEditingRow() + "/" + table.getEditingColumn() + ", "
                        + Checks.argb(((LineBorder) ((JComponent) table.getEditorComponent()).getBorder()).getLineColor()
                                .getRGB())));
        checks.add(Checks.expect("header renderer", "DefaultTableCellHeaderRenderer", () -> {
            TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
            return renderer.getClass().getSimpleName();
        }));
        checks.add(Checks.expect("columns : class names", "String Integer Float Double Long BigDecimal Date Icon Boolean "
                + "Integer", () -> {
                    List<String> names = new ArrayList<>();
                    for (int c = 0; c < table.getModel().getColumnCount(); c++) {
                        names.add(table.getModel().getColumnClass(c).getSimpleName());
                    }
                    return String.join(" ", names);
                }));
        return checks;
    }
}
