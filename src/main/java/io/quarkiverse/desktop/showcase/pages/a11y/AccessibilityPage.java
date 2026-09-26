package io.quarkiverse.desktop.showcase.pages.a11y;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.CheckboxMenuItem;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.ScrollPane;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.CompletionStage;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibilityProvider;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRelation;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleValue;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.TextBlock;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The {@code javax.accessibility} API of the AWT components : the accessible context tree of a panel of heavyweight
 * components (roles, names, states, actions, values, text, selections, relations), of a component with its own
 * accessible context, and of a frame, dialog, window, menu bar and popup menu that are not shown ; accessible actions,
 * value and selection changes applied to the components ; accessible property change events ; role and state names in
 * German, Japanese and Simplified Chinese (the {@code com.sun.accessibility.internal.resources.accessibility} bundles :
 * a native executable only includes the bundles of its locales, see {@code quarkus.locales}) ; the
 * {@link AccessibilityProvider} service providers (the Java Access Bridge on Windows).
 * <p>
 * AWT only. The Swing variant is {@code pages.swing.a11y.SwingAccessibilityPage}.
 */
@Singleton
public class AccessibilityPage implements FeaturePage {

    private static final int GALLERY_WIDTH = 1000;
    private static final int GALLERY_HEIGHT = 200;

    // per build state (build() sets it, dispose() clears it)
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final List<Window> windows = new ArrayList<>();
    private Gallery gallery;
    private ChecksView results;
    private TextBlock tree;

    /** The components of the page and the hidden windows. */
    private record Gallery(Panel panel, Label label, TextField field, Button button, Checkbox bold, Checkbox portrait,
            Choice choice, java.awt.List list, Scrollbar scrollbar, TextArea area, Canvas canvas, Gauge gauge,
            ScrollPane scroll, Frame frame, MenuBar menuBar, MenuItem open, CheckboxMenuItem autosave, Dialog dialog,
            Window window, PopupMenu popup) {
    }

    @Override
    public String id() {
        return "a11y-contexts";
    }

    @Override
    public String title() {
        return "Accessibility API (AWT)";
    }

    @Override
    public String category() {
        return Categories.A11Y_BEANS;
    }

    @Override
    public int order() {
        return 10;
    }

    /**
     * A component with its own accessible context : a progress gauge (role, name and {@link AccessibleValue}).
     */
    static final class Gauge extends Component implements Accessible {

        private int value = 70;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(160, 24);
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(new Color(0xCFD8DC));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(0x43A047));
            g.fillRect(0, 0, getWidth() * value / 100, getHeight());
            g.setColor(new Color(0x263238));
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleGauge();
                accessibleContext.setAccessibleName("Download");
            }
            return accessibleContext;
        }

        final class AccessibleGauge extends AccessibleAWTComponent implements AccessibleValue {

            @Override
            public AccessibleRole getAccessibleRole() {
                return AccessibleRole.PROGRESS_BAR;
            }

            @Override
            public AccessibleValue getAccessibleValue() {
                return this;
            }

            @Override
            public Number getCurrentAccessibleValue() {
                return value;
            }

            @Override
            public boolean setCurrentAccessibleValue(Number n) {
                int old = value;
                value = Math.max(0, Math.min(100, n.intValue()));
                firePropertyChange(ACCESSIBLE_VALUE_PROPERTY, old, value);
                repaint();
                return true;
            }

            @Override
            public Number getMinimumAccessibleValue() {
                return 0;
            }

            @Override
            public Number getMaximumAccessibleValue() {
                return 100;
            }
        }
    }

    private static <C extends Component> C place(Panel panel, C component, int x, int y, int w, int h) {
        component.setBounds(x, y, w, h);
        panel.add(component);
        return component;
    }

    @Override
    public Component build() {
        events.clear();
        gallery = gallery();
        results = ChecksView.table("Accessible contexts", List.of(Check.info("state", "pending")));
        tree = Ui.text("pending", new Font(Font.MONOSPACED, Font.PLAIN, 11), Ui.TEXT_COLOR, 1000);
        return Ui.column(14,
                Ui.text("AWT components (heavyweight, with native peers) and their accessible contexts : what an "
                        + "assistive technology reads. The frame, menu bar, dialog, window and popup menu at the end of "
                        + "the tree are not shown.", 1000),
                gallery.panel(),
                Ui.title("Accessible tree"),
                tree,
                results);
    }

    private Gallery gallery() {
        Panel panel = new Panel(null) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(GALLERY_WIDTH, GALLERY_HEIGHT);
            }
        };
        panel.setBackground(new Color(0xF5F5F5));
        panel.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        panel.getAccessibleContext().setAccessibleName("Print options");

        Label label = place(panel, new Label("Document:"), 10, 10, 80, 24);
        TextField field = place(panel, new TextField("Quarkus Desktop"), 90, 10, 180, 24);
        label.getAccessibleContext().getAccessibleRelationSet()
                .add(new AccessibleRelation(AccessibleRelation.LABEL_FOR, field));
        field.getAccessibleContext().setAccessibleName("Document name");
        Button button = place(panel, new Button("Print"), 290, 10, 90, 26);
        button.setActionCommand("print-command");
        button.addActionListener(e -> events.add("Button " + e.getActionCommand()));
        button.getAccessibleContext().setAccessibleDescription("Prints the document");
        Checkbox bold = place(panel, new Checkbox("Bold", true), 400, 10, 80, 24);
        CheckboxGroup orientation = new CheckboxGroup();
        Checkbox portrait = place(panel, new Checkbox("Portrait", orientation, true), 490, 10, 90, 24);
        place(panel, new Checkbox("Landscape", orientation, false), 580, 10, 100, 24);
        Choice choice = place(panel, new Choice(), 700, 10, 120, 24);
        for (String item : List.of("Letter", "A4", "A5")) {
            choice.add(item);
        }
        choice.select("A4");
        choice.getAccessibleContext().setAccessibleName("Paper");
        java.awt.List list = place(panel, new java.awt.List(4, true), 840, 10, 150, 80);
        for (String item : List.of("First copy", "Second copy", "Third copy", "Fourth copy", "Fifth copy")) {
            list.add(item);
        }
        list.select(1);
        list.select(3);
        list.getAccessibleContext().setAccessibleName("Copies");
        Scrollbar scrollbar = place(panel, new Scrollbar(Scrollbar.HORIZONTAL, 30, 10, 0, 110), 10, 50, 370, 18);
        scrollbar.getAccessibleContext().setAccessibleName("Zoom");
        TextArea area = place(panel, new TextArea("First line of notes.\nSecond line. Third sentence!", 3, 30,
                TextArea.SCROLLBARS_NONE), 400, 50, 420, 60);
        area.getAccessibleContext().setAccessibleName("Notes");
        Canvas canvas = place(panel, new Canvas() {
            @Override
            public void paint(Graphics g) {
                g.setColor(new Color(0x1E88E5));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }, 10, 80, 60, 60);
        canvas.getAccessibleContext().setAccessibleName("Preview");
        Gauge gauge = place(panel, new Gauge(), 90, 90, 160, 24);
        ScrollPane scroll = place(panel, new ScrollPane(ScrollPane.SCROLLBARS_NEVER), 400, 120, 420, 70);
        Panel inner = new Panel(new FlowLayout(FlowLayout.LEFT));
        inner.add(new Label("Inside a scroll pane"));
        scroll.add(inner);

        // never shown : no native peer
        Frame frame = new Frame("Accessible frame");
        MenuBar menuBar = new MenuBar();
        Menu file = new Menu("File");
        MenuItem open = new MenuItem("Open...", new MenuShortcut(KeyEvent.VK_O));
        open.setActionCommand("open");
        open.addActionListener(e -> events.add("MenuItem " + e.getActionCommand()));
        file.add(open);
        CheckboxMenuItem autosave = new CheckboxMenuItem("Autosave", true);
        file.add(autosave);
        file.addSeparator();
        Menu recent = new Menu("Recent");
        recent.add(new MenuItem("report.ps"));
        MenuItem disabled = new MenuItem("(empty)");
        disabled.setEnabled(false);
        recent.add(disabled);
        file.add(recent);
        menuBar.add(file);
        Menu help = new Menu("Help");
        help.add(new MenuItem("About"));
        menuBar.setHelpMenu(help);
        frame.setMenuBar(menuBar);
        Dialog dialog = new Dialog(frame, "Settings", true);
        dialog.add(new Button("OK"));
        Window window = new Window(frame);
        PopupMenu popup = new PopupMenu("Context");
        popup.add(new MenuItem("Copy"));
        popup.add(new MenuItem("Paste"));
        frame.add(popup);
        windows.clear();
        windows.addAll(List.of(frame, dialog, window));
        return new Gallery(panel, label, field, button, bold, portrait, choice, list, scrollbar, area, canvas, gauge,
                scroll, frame, menuBar, open, autosave, dialog, window, popup);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = results;
        Gallery g = gallery;
        // the text selections need the native peers (created once the content is shown)
        g.field().select(0, 7);
        g.area().select(6, 10);
        // the tree before the checks change values and selections
        List<String> dump = new ArrayList<>(AccessibleDump.dump(g.panel()));
        for (Accessible accessible : List.<Accessible> of(g.frame(), g.menuBar(), g.open(), g.autosave(), g.dialog(),
                g.window(), g.popup())) {
            dump.addAll(AccessibleDump.dump(accessible));
        }
        tree.setText(String.join("\n", dump));
        List<Check> checks = new ArrayList<>();
        checks.add(Check.pass("accessible tree : nodes, SHA-256", dump.size() + ", " + Checks.sha256(String.join("\n", dump))));
        checks.addAll(checks(g));
        // the accessible actions of the button and of the menu item post action events : checked once dispatched
        return Edt.rounds(5)
                .thenCompose(v -> Edt.until(() -> events.stream().filter(e -> !e.startsWith("property")).count() >= 2,
                        3000, "action events").handle((r, error) -> null))
                .thenRun(() -> {
                    checks.add(Checks.expect("action events of the accessible actions",
                            "Button print-command, MenuItem open", () -> String.join(", ",
                                    events.stream().filter(e -> !e.startsWith("property")).toList())));
                    view.setChecks(checks);
                });
    }

    @Override
    public void dispose(Component content) {
        for (Window window : windows) {
            window.dispose();
        }
        windows.clear();
        gallery = null;
        results = null;
        tree = null;
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private List<Check> checks(Gallery g) {
        List<Check> checks = new ArrayList<>();
        AccessibleContext panel = g.panel().getAccessibleContext();
        checks.add(Checks.expect("panel : role, name, children", "panel, Print options, 13", () -> AccessibleDump.role(panel)
                + ", " + panel.getAccessibleName() + ", " + panel.getAccessibleChildrenCount()));
        checks.add(Checks.expect("roles of the children",
                "label, text, push button, check box, check box, check box, combo box, list, scroll bar, text, canvas, "
                        + "progress bar, scroll pane",
                () -> {
                    List<String> roles = new ArrayList<>();
                    for (int i = 0; i < panel.getAccessibleChildrenCount(); i++) {
                        roles.add(AccessibleDump.role(panel.getAccessibleChild(i).getAccessibleContext()));
                    }
                    return String.join(", ", roles);
                }));
        checks.add(Checks.expect("label : name, relation", "Document:, label for Document name", () -> {
            AccessibleContext c = g.label().getAccessibleContext();
            AccessibleRelation relation = c.getAccessibleRelationSet().get(AccessibleRelation.LABEL_FOR);
            return c.getAccessibleName() + ", " + relation.toDisplayString(Locale.ENGLISH) + " "
                    + ((Accessible) relation.getTarget()[0]).getAccessibleContext().getAccessibleName();
        }));
        checks.add(Checks.expect("text field : parent role, index in parent", "panel, 1", () -> {
            AccessibleContext c = g.field().getAccessibleContext();
            return AccessibleDump.role(c.getAccessibleParent().getAccessibleContext()) + ", "
                    + c.getAccessibleIndexInParent();
        }));
        checks.add(Checks.expect("text field : AccessibleText characters, word and sentence at 9",
                "15, D, Desktop, Quarkus Desktop", () -> {
                    AccessibleText text = g.field().getAccessibleContext().getAccessibleText();
                    return text.getCharCount() + ", " + text.getAtIndex(AccessibleText.CHARACTER, 8) + ", "
                            + text.getAtIndex(AccessibleText.WORD, 9) + ", " + text.getAtIndex(AccessibleText.SENTENCE, 9);
                }));
        checks.add(Checks.expect("text field : selection", "0-7 Quarkus", () -> {
            AccessibleText text = g.field().getAccessibleContext().getAccessibleText();
            return text.getSelectionStart() + "-" + text.getSelectionEnd() + " " + text.getSelectedText();
        }));
        checks.add(Checks.expect("text area : characters, sentence at 25, word after 21, selection", "49, Second line. , "
                + "line, line", () -> {
                    AccessibleText text = g.area().getAccessibleContext().getAccessibleText();
                    return text.getCharCount() + ", " + text.getAtIndex(AccessibleText.SENTENCE, 25) + ", "
                            + text.getAfterIndex(AccessibleText.WORD, 21) + ", " + text.getSelectedText();
                }));
        checks.add(Checks.expect("button : name, description, actions", "Print, Prints the document, 1 click", () -> {
            AccessibleContext c = g.button().getAccessibleContext();
            AccessibleAction action = c.getAccessibleAction();
            return c.getAccessibleName() + ", " + c.getAccessibleDescription() + ", " + action.getAccessibleActionCount()
                    + " " + action.getAccessibleActionDescription(0);
        }));
        checks.add(Checks.expect("button : doAccessibleAction(0)", true,
                () -> g.button().getAccessibleContext().getAccessibleAction().doAccessibleAction(0)));
        checks.add(Checks.expect("check box : checked state", true, () -> g.bold().getAccessibleContext()
                .getAccessibleStateSet().contains(AccessibleState.CHECKED)));
        checks.add(Checks.expect("check box : accessible property events of an item event",
                "AccessibleState checked -> null, AccessibleState null -> checked", () -> {
                    List<String> changes = new ArrayList<>();
                    AccessibleContext c = g.bold().getAccessibleContext();
                    java.beans.PropertyChangeListener listener = e -> changes.add(e.getPropertyName() + " "
                            + display(e.getOldValue()) + " -> " + display(e.getNewValue()));
                    c.addPropertyChangeListener(listener);
                    try {
                        g.bold().setState(false);
                        g.bold().dispatchEvent(new ItemEvent(g.bold(), ItemEvent.ITEM_STATE_CHANGED, "Bold",
                                ItemEvent.DESELECTED));
                        g.bold().setState(true);
                        g.bold().dispatchEvent(new ItemEvent(g.bold(), ItemEvent.ITEM_STATE_CHANGED, "Bold",
                                ItemEvent.SELECTED));
                    } finally {
                        c.removePropertyChangeListener(listener);
                    }
                    return String.join(", ", changes);
                }));
        checks.add(Checks.expect("radio button (Checkbox in a group) : role, states", "check box, checked",
                () -> AccessibleDump.role(g.portrait().getAccessibleContext()) + ", "
                        + (g.portrait().getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED)
                                ? "checked" : "unchecked")));
        checks.add(Checks.expect("choice : name, selection, children", "Paper, no selection, 0", () -> {
            AccessibleContext c = g.choice().getAccessibleContext();
            AccessibleSelection selection = c.getAccessibleSelection();
            return c.getAccessibleName() + ", " + (selection == null ? "no selection"
                    : selection.getAccessibleSelectionCount() + " " + g.choice().getSelectedItem())
                    + ", " + c.getAccessibleChildrenCount();
        }));
        checks.add(Checks.expect("list : children, selected items (index, name)", "5, 1 null|3 null", () -> {
            AccessibleContext c = g.list().getAccessibleContext();
            AccessibleSelection selection = c.getAccessibleSelection();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < selection.getAccessibleSelectionCount(); i++) {
                AccessibleContext item = selection.getAccessibleSelection(i).getAccessibleContext();
                names.add(item.getAccessibleIndexInParent() + " " + item.getAccessibleName());
            }
            return c.getAccessibleChildrenCount() + ", " + String.join("|", names);
        }));
        checks.add(Checks.expect("list : addAccessibleSelection(0), removeAccessibleSelection(3)", "[0, 1]", () -> {
            AccessibleSelection selection = g.list().getAccessibleContext().getAccessibleSelection();
            selection.addAccessibleSelection(0);
            selection.removeAccessibleSelection(3);
            return Arrays.toString(g.list().getSelectedIndexes());
        }));
        checks.add(Checks.expect("list item : role, states", "list item, enabled,focusable,selected,showing,visible",
                () -> {
                    AccessibleContext item = g.list().getAccessibleContext().getAccessibleChild(1).getAccessibleContext();
                    return AccessibleDump.role(item) + ", " + AccessibleDump.states(item);
                }));
        checks.add(Checks.expect("scroll bar : value, set 55", "30 in 0..110, 55", () -> {
            AccessibleValue value = g.scrollbar().getAccessibleContext().getAccessibleValue();
            String before = value.getCurrentAccessibleValue() + " in " + value.getMinimumAccessibleValue() + ".."
                    + value.getMaximumAccessibleValue();
            value.setCurrentAccessibleValue(55);
            return before + ", " + g.scrollbar().getValue();
        }));
        checks.add(Checks.expect("custom accessible component : role, name, value event", "progress bar, Download, "
                + "AccessibleValue 70 -> 85", () -> {
                    AccessibleContext c = g.gauge().getAccessibleContext();
                    List<String> changes = new ArrayList<>();
                    c.addPropertyChangeListener(e -> changes.add(e.getPropertyName() + " " + e.getOldValue() + " -> "
                            + e.getNewValue()));
                    c.getAccessibleValue().setCurrentAccessibleValue(85);
                    return AccessibleDump.role(c) + ", " + c.getAccessibleName() + ", " + String.join(", ", changes);
                }));
        checks.add(Checks.expect("canvas, scroll pane : roles, children", "canvas 0, scroll pane 1", () -> {
            AccessibleContext canvas = g.canvas().getAccessibleContext();
            AccessibleContext scroll = g.scroll().getAccessibleContext();
            return AccessibleDump.role(canvas) + " " + canvas.getAccessibleChildrenCount() + ", "
                    + AccessibleDump.role(scroll) + " " + scroll.getAccessibleChildrenCount();
        }));
        checks.add(Checks.expect("setAccessibleDescription : property event", "AccessibleDescription null -> Zoom level",
                () -> {
                    AccessibleContext c = g.scrollbar().getAccessibleContext();
                    List<String> changes = new ArrayList<>();
                    c.addPropertyChangeListener(e -> changes.add(e.getPropertyName() + " " + e.getOldValue() + " -> "
                            + e.getNewValue()));
                    c.setAccessibleDescription("Zoom level");
                    return String.join(", ", changes);
                }));
        checks.add(Checks.expect("hidden frame, dialog, window : roles, states",
                "frame [enabled,focusable,resizable], dialog [enabled,focusable,modal,resizable], window [enabled,focusable]",
                () -> String.join(", ", List.<Accessible> of(g.frame(), g.dialog(), g.window()).stream()
                        .map(a -> AccessibleDump.role(a.getAccessibleContext()) + " ["
                                + AccessibleDump.states(a.getAccessibleContext()) + "]")
                        .toList())));
        // the accessible contexts of the AWT menu components have no accessible children
        checks.add(Checks.expect("menu bar, popup menu : children", "menu bar 0, popup menu 0", () -> {
            AccessibleContext bar = g.menuBar().getAccessibleContext();
            AccessibleContext popup = g.popup().getAccessibleContext();
            return AccessibleDump.role(bar) + " " + bar.getAccessibleChildrenCount() + ", " + AccessibleDump.role(popup)
                    + " " + popup.getAccessibleChildrenCount();
        }));
        checks.add(Checks.expect("menu item, check box menu item",
                "menu item \"Open...\" [] {actions click; value 0 in 0..0; selected 0} / "
                        + "check box \"Autosave\" [] {value null in null..null; selected 0}",
                () -> AccessibleDump.describe(
                g.open().getAccessibleContext()) + " / " + AccessibleDump.describe(g.autosave().getAccessibleContext())));
        checks.add(Checks.expect("menu item : doAccessibleAction(0)", true,
                () -> g.open().getAccessibleContext().getAccessibleAction().doAccessibleAction(0)));
        // localized names : bundles of the java.desktop module for de, ja and zh_CN
        checks.add(Checks.expect("AccessibleRole.PUSH_BUTTON in en / de / ja / zh_CN",
                "push button / Schaltfl\u00e4che / \u30d7\u30c3\u30b7\u30e5\u30fb\u30dc\u30bf\u30f3 / \u6309\u94ae",
                () -> localized(AccessibleRole.PUSH_BUTTON)));
        checks.add(Checks.expect("AccessibleState.CHECKED in en / de / ja / zh_CN",
                "checked / markiert / \u30c1\u30a7\u30c3\u30af / \u5df2\u9009\u4e2d",
                () -> localized(AccessibleState.CHECKED)));
        checks.add(Checks.expect("AccessibleRole.PAGE_TAB_LIST in en / de / ja / zh_CN",
                "page tab list / Registerkartenliste / \u30da\u30fc\u30b8\u30fb\u30bf\u30d6\u30fb\u30ea\u30b9\u30c8 / "
                        + "\u9875\u6807\u7b7e\u5217\u8868",
                () -> localized(AccessibleRole.PAGE_TAB_LIST)));
        checks.add(Checks.expect("AccessibleRelation(LABEL_FOR) in en / de / zh_CN",
                "label for / Label f\u00fcr / \u6807\u7b7e\u5c5e\u4e8e", () -> {
                    AccessibleRelation relation = new AccessibleRelation(AccessibleRelation.LABEL_FOR);
                    return relation.toDisplayString(Locale.ENGLISH) + " / " + relation.toDisplayString(Locale.GERMAN)
                            + " / " + relation.toDisplayString(Locale.SIMPLIFIED_CHINESE);
                }));
        checks.add(Checks.expect("AccessibilityProvider service providers",
                Platforms.pick("none", "com.sun.java.accessibility.internal.ProviderImpl", "none"),
                () -> {
                    List<String> providers = ServiceLoader.load(AccessibilityProvider.class).stream()
                            .map(p -> p.type().getName()).sorted().toList();
                    return providers.isEmpty() ? "none" : String.join(", ", providers);
                }));
        checks.add(Checks.info("javax.accessibility.assistive_technologies",
                () -> String.valueOf(System.getProperty("javax.accessibility.assistive_technologies"))));
        return checks;
    }

    private static String localized(javax.accessibility.AccessibleBundle bundle) {
        return String.join(" / ", List.of(Locale.ENGLISH, Locale.GERMAN, Locale.JAPANESE, Locale.SIMPLIFIED_CHINESE)
                .stream().map(bundle::toDisplayString).toList());
    }

    private static String display(Object value) {
        return value instanceof AccessibleState state ? state.toDisplayString(Locale.ENGLISH) : String.valueOf(value);
    }
}
