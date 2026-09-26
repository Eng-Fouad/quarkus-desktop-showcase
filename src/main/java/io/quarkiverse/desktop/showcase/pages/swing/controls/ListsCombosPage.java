package io.quarkiverse.desktop.showcase.pages.swing.controls;

import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.caption;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.column;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.ints;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.row;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.runAction;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.section;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.swatch;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.typed;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.text.Position;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.Readiness;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.EventLog;

/**
 * Lists and combo boxes : {@code JList} in the 3 layout orientations with a custom renderer, the 3 selection modes,
 * type-ahead ({@code getNextMatch}), prototype cells, list model events ; {@code JComboBox} non editable, with a
 * renderer, editable with {@code String} and {@code Integer} items, key selection, and its popup opened (a light weight
 * popup and, with {@code setLightWeightPopupEnabled(false)}, a heavy weight window) rendered as extra snapshots.
 * <p>
 * Native paths exercised on purpose : {@code BasicComboBoxEditor.getItem} converts the edited text with the
 * {@code valueOf(String)} method of the item class found by reflection and invoked through
 * {@code sun.reflect.misc.MethodUtil} ; list and combo box actions loaded by reflection ({@code LazyActionMap}) ;
 * {@code PopupFactory} popups (heavy weight : a {@code JWindow}, a second native window).
 */
@Singleton
public class ListsCombosPage implements FeaturePage {

    private static final String[] FRUITS = { "Apple", "Apricot", "Banana", "Blackberry", "Blueberry", "Cherry",
            "Coconut", "Date", "Fig", "Grape", "Kiwi", "Lemon", "Mango", "Orange", "Peach", "Pear", "Plum",
            "Raspberry" };
    private static final int[] COLORS = { 0xE53935, 0xFB8C00, 0xFDD835, 0x5E35B1, 0x3949AB, 0xC62828, 0x6D4C41,
            0x8D6E63, 0x7B1FA2, 0x43A047, 0x7CB342, 0xFFEE58, 0xFFA726, 0xEF6C00, 0xFFAB91, 0xC0CA33, 0x6A1B9A,
            0xD81B60 };

    private ChecksView results;
    private final Readiness readiness = new Readiness();
    private final List<JList<?>> lists = new ArrayList<>();
    private JComboBox<String> rendererCombo;
    private JComboBox<String> heavyCombo;
    private JComboBox<Integer> integerCombo;
    private final Map<String, BufferedImage> popups = new TreeMap<>();
    private final List<Check> popupChecks = new ArrayList<>();

    @Override
    public String id() {
        return "swing-lists-combos";
    }

    @Override
    public String title() {
        return "Lists and combo boxes";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 50;
    }

    /**
     * Popup menus need a focused window : on Windows, {@code WToolkit.grab} of a window of an application that has no
     * focused window ungrabs at once, and the popup closes (UngrabEvent).
     */
    @Override
    public boolean needsFocus() {
        return true;
    }

    /**
     * A renderer showing a color swatch per fruit, alternating backgrounds and bold selected items.
     */
    static final class FruitRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            int i = java.util.Arrays.asList(FRUITS).indexOf(String.valueOf(value));
            setIcon(swatch(i < 0 ? 0x9E9E9E : COLORS[i], 12, 12));
            setIconTextGap(6);
            if (selected) {
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setFont(getFont().deriveFont(Font.PLAIN));
                setBackground(index % 2 == 0 ? list.getBackground() : new Color(0xF1F5F9));
            }
            return this;
        }
    }

    @Override
    public Component build() {
        lists.clear();
        popups.clear();
        popupChecks.clear();
        results = ChecksView.table("Checks", List.of(Check.info("state", "pending")));
        JPanel content = column(10,
                Ui.text("JComboBox and JList. The popups of two combo boxes are opened programmatically once the page "
                        + "is shown, rendered as extra snapshots (--popup-light, --popup-heavy), then closed.", 1000),
                section("JComboBox", combos()),
                section("JList : VERTICAL (scroll pane, single selection), VERTICAL_WRAP (multiple intervals), "
                        + "HORIZONTAL_WRAP (single interval), disabled", lists()),
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
        return Edt.rounds(2)
                .thenCompose(v -> popup("popup-light", rendererCombo))
                .thenCompose(v -> popup("popup-heavy", heavyCombo))
                .thenAccept(v -> view.setChecks(checks()));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(Map.copyOf(popups));
    }

    @Override
    public void dispose(Component content) {
        readiness.reset();
        for (JComboBox<?> combo : new JComboBox<?>[] { rendererCombo, heavyCombo }) {
            if (combo != null) {
                combo.setPopupVisible(false);
            }
        }
        results = null;
        rendererCombo = null;
        heavyCombo = null;
        integerCombo = null;
        lists.clear();
        popups.clear();
        popupChecks.clear();
    }

    // ------------------------------------------------------------------------------------------------------ popups

    /**
     * Opens the popup of {@code combo}, renders it, records where PopupFactory put it, and closes it.
     */
    private CompletionStage<Void> popup(String name, JComboBox<String> combo) {
        if (!Edt.ownsFocus()) {
            popupChecks.add(Check.info(name, "skipped: not focused"));
            return CompletableFuture.completedFuture(null);
        }
        combo.setPopupVisible(true);
        return Edt.rounds(3).thenAccept(v -> {
            BasicComboPopup popup = (BasicComboPopup) combo.getUI().getAccessibleChild(combo, 0);
            popupChecks.add(Checks.expect(name + " : visible, list selection, PopupFactory kind", "true, "
                    + combo.getSelectedIndex() + ", " + (name.endsWith("light") ? "light weight" : "heavy weight (HeavyWeightWindow)"),
                    () -> combo.isPopupVisible() + ", " + popup.getList().getSelectedIndex() + ", "
                            + kind(combo, popup)));
            if (popup.isShowing()) {
                popups.put(name, Snapshots.render(popup));
            }
            combo.setPopupVisible(false);
        });
    }

    /**
     * Where PopupFactory put the popup : in the layered pane of the window (light weight), in an AWT {@code Panel} of
     * the window (medium weight), or in another window (heavy weight).
     */
    private static String kind(JComponent invoker, JPopupMenu popup) {
        Window window = javax.swing.SwingUtilities.getWindowAncestor(popup);
        if (window != javax.swing.SwingUtilities.getWindowAncestor(invoker)) {
            return "heavy weight (" + (window == null ? "no window" : window.getClass().getSimpleName()) + ")";
        }
        for (Container c = popup.getParent(); c != null && c != window; c = c.getParent()) {
            if (c instanceof Panel) {
                return "medium weight (Panel)";
            }
        }
        return "light weight";
    }

    // ----------------------------------------------------------------------------------------------------- gallery

    private JComponent combos() {
        JComboBox<String> plain = new JComboBox<>(FRUITS);
        plain.setSelectedItem("Cherry");

        rendererCombo = new JComboBox<>(FRUITS);
        rendererCombo.setRenderer(listRenderer());
        rendererCombo.setSelectedIndex(3);
        rendererCombo.setMaximumRowCount(6);

        JComboBox<String> editable = new JComboBox<>(FRUITS);
        editable.setEditable(true);
        editable.setSelectedItem("Dragon fruit");

        integerCombo = new JComboBox<>(new Integer[] { 10, 20, 30 });
        integerCombo.setEditable(true);
        integerCombo.setSelectedItem(20);
        // the editor text only : BasicComboBoxEditor.getItem converts it with Integer.valueOf(String)
        ((javax.swing.JTextField) integerCombo.getEditor().getEditorComponent()).setText("42");

        JComboBox<String> disabled = new JComboBox<>(FRUITS);
        disabled.setSelectedIndex(8);
        disabled.setEnabled(false);

        JComboBox<String> prototype = new JComboBox<>(FRUITS);
        prototype.setPrototypeDisplayValue("Www");
        prototype.setSelectedIndex(10);

        heavyCombo = new JComboBox<>(FRUITS);
        heavyCombo.setLightWeightPopupEnabled(false);
        heavyCombo.setMaximumRowCount(5);
        heavyCombo.setSelectedIndex(12);

        for (JComboBox<?> combo : List.of(plain, rendererCombo, editable, disabled, heavyCombo)) {
            combo.setPreferredSize(new Dimension(128, combo.getPreferredSize().height));
        }
        integerCombo.setPreferredSize(new Dimension(80, integerCombo.getPreferredSize().height));
        return row(10, labeled("non editable", plain), labeled("custom renderer", rendererCombo),
                labeled("editable (typed text)", editable), labeled("Integer items, edited \"42\"", integerCombo),
                labeled("disabled", disabled), labeled("prototype \"Www\"", prototype),
                labeled("heavy weight popup", heavyCombo));
    }

    @SuppressWarnings("unchecked")
    private static <T> ListCellRenderer<T> listRenderer() {
        return (ListCellRenderer<T>) new FruitRenderer();
    }

    private static JComponent labeled(String text, JComponent component) {
        return column(2, caption(text), component);
    }

    private JComponent lists() {
        JList<String> vertical = new JList<>(FRUITS);
        vertical.setCellRenderer(listRenderer());
        vertical.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        vertical.setSelectedIndex(2);
        vertical.setVisibleRowCount(9);
        JScrollPane scroll = new JScrollPane(vertical);
        scroll.setPreferredSize(new Dimension(170, scroll.getPreferredSize().height));

        JList<String> verticalWrap = new JList<>(FRUITS);
        verticalWrap.setLayoutOrientation(JList.VERTICAL_WRAP);
        verticalWrap.setVisibleRowCount(6);
        verticalWrap.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        verticalWrap.setSelectedIndices(new int[] { 1, 2, 5, 9, 10, 17 });
        verticalWrap.setFixedCellWidth(90);

        JList<String> horizontalWrap = new JList<>(FRUITS);
        horizontalWrap.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        horizontalWrap.setVisibleRowCount(6);
        horizontalWrap.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        horizontalWrap.setSelectionInterval(3, 6);
        horizontalWrap.setPrototypeCellValue("Blackberry");

        JList<String> disabled = new JList<>(new String[] { "Disabled", "list", "with a", "selection" });
        disabled.setSelectedIndex(1);
        disabled.setEnabled(false);

        for (JList<?> list : List.of(vertical, verticalWrap, horizontalWrap, disabled)) {
            list.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0xCFD8DC)));
            lists.add(list);
        }
        return row(16, labeled("VERTICAL", scroll), labeled("VERTICAL_WRAP, fixed cell width", verticalWrap),
                labeled("HORIZONTAL_WRAP, prototype cell", horizontalWrap), labeled("disabled", disabled));
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private List<Check> checks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("UI delegates", "BasicListUI MetalComboBoxUI", () -> {
            TreeSet<String> names = new TreeSet<>();
            lists.forEach(l -> names.add(l.getUI().getClass().getSimpleName()));
            names.add(rendererCombo.getUI().getClass().getSimpleName());
            return String.join(" ", names);
        }));
        checks.addAll(listChecks());
        checks.addAll(comboChecks());
        checks.addAll(popupChecks);
        return checks;
    }

    private List<Check> listChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("getNextMatch : \"ch\" forward from 0, \"b\" backward from 7, \"PL\" forward",
                "5, 4, 16", () -> {
                    JList<String> list = new JList<>(FRUITS);
                    return list.getNextMatch("ch", 0, Position.Bias.Forward) + ", "
                            + list.getNextMatch("b", 7, Position.Bias.Backward) + ", "
                            + list.getNextMatch("PL", 0, Position.Bias.Forward);
                }));
        checks.add(Checks.expect("selection modes : setSelectionInterval(2, 4) + addSelectionInterval(7, 8)",
                "SINGLE 8 | SINGLE_INTERVAL 7,8 | MULTIPLE_INTERVAL 2,3,4,7,8", () -> {
                    List<String> results = new ArrayList<>();
                    String[] names = { "SINGLE", "SINGLE_INTERVAL", "MULTIPLE_INTERVAL" };
                    int[] modes = { ListSelectionModel.SINGLE_SELECTION, ListSelectionModel.SINGLE_INTERVAL_SELECTION,
                            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION };
                    for (int i = 0; i < modes.length; i++) {
                        JList<String> list = new JList<>(FRUITS);
                        list.setSelectionMode(modes[i]);
                        list.setSelectionInterval(2, 4);
                        list.addSelectionInterval(7, 8);
                        results.add(names[i] + " " + ints(list.getSelectedIndices()));
                    }
                    return String.join(" | ", results);
                }));
        checks.add(Checks.expect("anchor / lead / selected values after the multiple interval selection",
                "7 / 8 / Apricot Banana Date Fig", () -> {
                    JList<String> list = new JList<>(FRUITS);
                    list.setSelectionInterval(1, 2);
                    list.addSelectionInterval(7, 8);
                    return list.getAnchorSelectionIndex() + " / " + list.getLeadSelectionIndex() + " / "
                            + String.join(" ", list.getSelectedValuesList());
                }));
        checks.add(Checks.expect("ListSelectionEvents : first, last, adjusting", "2-2 false | 2-5 false | 5-5 true | 5-5 true",
                () -> {
                    JList<String> list = new JList<>(FRUITS);
                    EventLog log = new EventLog();
                    list.addListSelectionListener(e -> log.add(e.getFirstIndex() + "-" + e.getLastIndex() + " "
                            + e.getValueIsAdjusting()));
                    list.setSelectedIndex(2);
                    list.setSelectedIndex(5);
                    list.setValueIsAdjusting(true);
                    list.addSelectionInterval(5, 5);
                    list.removeSelectionInterval(5, 5);
                    list.setSelectedIndex(5);
                    return log.toString();
                }));
        checks.add(Checks.expect("DefaultListModel events", "INTERVAL_ADDED 3-3 | CONTENTS_CHANGED 1-1 | "
                + "INTERVAL_REMOVED 0-0 | INTERVAL_REMOVED 1-2 | size 1", () -> {
                    DefaultListModel<String> model = new DefaultListModel<>();
                    model.addAll(List.of("a", "b", "c"));
                    EventLog log = new EventLog();
                    model.addListDataListener(listener(log));
                    model.addElement("d");
                    model.set(1, "B");
                    model.remove(0);
                    model.removeRange(1, 2);
                    return log + " | size " + model.size();
                }));
        checks.add(Checks.expect("list actions (BasicListUI.loadActionMap) : next, next extend, last, first",
                "3 | 3,4 | 17 | 0", () -> {
                    JList<String> list = new JList<>(FRUITS);
                    list.setSelectedIndex(2);
                    List<String> states = new ArrayList<>();
                    for (String action : List.of("selectNextRow", "selectNextRowExtendSelection", "selectLastRow",
                            "selectFirstRow")) {
                        if (action.equals("selectNextRowExtendSelection")) {
                            list.setSelectedIndex(3);
                        }
                        runAction(list, action);
                        states.add(ints(list.getSelectedIndices()));
                    }
                    return String.join(" | ", states);
                }));
        checks.add(Checks.expect("locationToIndex(indexToLocation(i)) on the shown lists (every orientation)", "true",
                () -> {
                    for (JList<?> list : lists) {
                        for (int i = 0; i < list.getModel().getSize(); i++) {
                            Point p = list.indexToLocation(i);
                            if (p != null && list.locationToIndex(new Point(p.x + 2, p.y + 2)) != i) {
                                return "index " + i + " of " + list.getLayoutOrientation();
                            }
                        }
                    }
                    return true;
                }));
        checks.add(Checks.info("VERTICAL_WRAP / HORIZONTAL_WRAP : cell bounds of item 7 (layout dependent)", () -> {
            JList<?> a = lists.get(1);
            JList<?> b = lists.get(2);
            return Checks.bounds(a.getCellBounds(7, 7)) + " / " + Checks.bounds(b.getCellBounds(7, 7));
        }));
        checks.add(Checks.expect("prototype cell value : fixed cell width set, all cells equal", "true, true", () -> {
            JList<?> list = lists.get(2);
            return (list.getFixedCellWidth() > 0) + ", "
                    + (list.getCellBounds(0, 0).width == list.getCellBounds(9, 9).width);
        }));
        return checks;
    }

    private static ListDataListener listener(EventLog log) {
        return new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                log.add("INTERVAL_ADDED " + e.getIndex0() + "-" + e.getIndex1());
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                log.add("INTERVAL_REMOVED " + e.getIndex0() + "-" + e.getIndex1());
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                log.add("CONTENTS_CHANGED " + e.getIndex0() + "-" + e.getIndex1());
            }
        };
    }

    private List<Check> comboChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("editable Integer combo : edited \"42\" -> getItem (valueOf by reflection, MethodUtil)",
                "Integer 42", () -> typed(integerCombo.getEditor().getItem())));
        checks.add(Checks.expect("editable Integer combo : editor action -> selected item, events",
                "Integer 42 | comboBoxChanged, comboBoxEdited", () -> {
                    JComboBox<Integer> combo = new JComboBox<>(new Integer[] { 10, 20, 30 });
                    combo.setEditable(true);
                    combo.setSelectedItem(20);
                    EventLog log = new EventLog();
                    combo.addActionListener(e -> log.add(e.getActionCommand()));
                    ((javax.swing.JTextField) combo.getEditor().getEditorComponent()).setText("42");
                    combo.actionPerformed(ControlsSupport.event(combo.getEditor(), "comboBoxEdited"));
                    return typed(combo.getSelectedItem()) + " | " + String.join(", ", log.events());
                }));
        checks.add(Checks.expect("non editable : setSelectedItem(unknown) ignored / editable : accepted",
                "Cherry / Dragon fruit", () -> {
                    JComboBox<String> plain = new JComboBox<>(FRUITS);
                    plain.setSelectedItem("Cherry");
                    plain.setSelectedItem("Dragon fruit");
                    JComboBox<String> editable = new JComboBox<>(FRUITS);
                    editable.setEditable(true);
                    editable.setSelectedItem("Dragon fruit");
                    return plain.getSelectedItem() + " / " + editable.getSelectedItem();
                }));
        checks.add(Checks.expect("setSelectedIndex events : ItemEvents and ActionEvent", "DESELECTED Apple | "
                + "SELECTED Banana | action comboBoxChanged", () -> {
                    JComboBox<String> combo = new JComboBox<>(FRUITS);
                    EventLog log = new EventLog();
                    combo.addItemListener(e -> log.add((e.getStateChange() == java.awt.event.ItemEvent.SELECTED
                            ? "SELECTED " : "DESELECTED ") + e.getItem()));
                    combo.addActionListener(e -> log.add("action " + e.getActionCommand()));
                    combo.setSelectedIndex(2);
                    return log.toString();
                }));
        // one key per fresh combo : the key selection manager of BasicComboBoxUI accumulates the keys typed within a
        // second of the last key event (without key events, its time stays 0 : every key accumulates)
        checks.add(Checks.expect("KeySelectionManager : b from Apple, b from Banana (a new prefix starts at the "
                + "selection), c, z", "Banana Banana Cherry false",
                () -> {
                    List<String> selected = new ArrayList<>();
                    for (Object[] step : new Object[][] { { 0, 'b' }, { 2, 'b' }, { 0, 'c' } }) {
                        JComboBox<String> combo = new JComboBox<>(FRUITS);
                        combo.setSelectedIndex((Integer) step[0]);
                        combo.selectWithKeyChar((Character) step[1]);
                        selected.add(String.valueOf(combo.getSelectedItem()));
                    }
                    return String.join(" ", selected) + " " + new JComboBox<>(FRUITS).selectWithKeyChar('z');
                }));
        checks.add(Checks.expect("DefaultComboBoxModel events, selection after removing the selected item", "INTERVAL_ADDED 0-0 | CONTENTS_CHANGED -1--1 "
                + "| INTERVAL_REMOVED 1-1 | selected w", () -> {
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(new String[] { "x", "y" });
                    EventLog log = new EventLog();
                    model.addListDataListener(listener(log));
                    model.insertElementAt("w", 0);
                    model.setSelectedItem("x");
                    model.removeElement("x");
                    return log + " | selected " + model.getSelectedItem();
                }));
        checks.add(Checks.expect("renderers : default combo renderer, custom", "UIResource, FruitRenderer", () -> {
            JComboBox<String> combo = new JComboBox<>(FRUITS);
            return combo.getRenderer().getClass().getSimpleName() + ", "
                    + rendererCombo.getRenderer().getClass().getSimpleName();
        }));
        checks.add(Checks.expect("combo actions loaded (BasicComboBoxUI.loadActionMap) : togglePopup, selectNext",
                "true true", () -> {
                    JComboBox<String> combo = new JComboBox<>(FRUITS);
                    return (combo.getActionMap().get("togglePopup") != null) + " "
                            + (combo.getActionMap().get("selectNext") != null);
                }));
        checks.add(Checks.expect("popups closed after the capture", "false false",
                () -> rendererCombo.isPopupVisible() + " " + heavyCombo.isPopupVisible()));
        return checks;
    }
}
