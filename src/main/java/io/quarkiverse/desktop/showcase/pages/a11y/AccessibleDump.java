package io.quarkiverse.desktop.showcase.pages.a11y;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Predicate;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleHypertext;
import javax.accessibility.AccessibleIcon;
import javax.accessibility.AccessibleKeyBinding;
import javax.accessibility.AccessibleRelation;
import javax.accessibility.AccessibleRelationSet;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.accessibility.AccessibleTable;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleValue;

/**
 * A deterministic text dump of an {@link AccessibleContext} tree (the information an assistive technology reads) :
 * role, name, description, states, and the {@link AccessibleAction}, {@link AccessibleValue}, {@link AccessibleText},
 * {@link AccessibleSelection}, {@link AccessibleTable}, {@link AccessibleHypertext}, {@link AccessibleIcon},
 * {@link AccessibleKeyBinding} and {@link AccessibleRelation} information of every node.
 * <p>
 * Roles, states and relations are shown in English ({@link javax.accessibility.AccessibleBundle#toDisplayString(Locale)}).
 * The states that depend on the keyboard focus of the window ({@code focused}, {@code active}) are left out, and so
 * are bounds (they depend on fonts). Only the first children of large containers (lists, tables) are dumped.
 * <p>
 * AWT only ({@code javax.accessibility}) : used by the accessibility pages and to find the components of dialogs whose
 * classes the AWT pages cannot reference (the Swing print dialog).
 */
public final class AccessibleDump {

    /** Children dumped per node at most. */
    public static final int MAX_CHILDREN = 24;

    private AccessibleDump() {
    }

    /**
     * The dump of {@code root} and its descendants, one line per node, indented by depth.
     */
    public static List<String> dump(Accessible root) {
        List<String> lines = new ArrayList<>();
        dump(root, 0, lines);
        return lines;
    }

    private static void dump(Accessible accessible, int depth, List<String> lines) {
        AccessibleContext context = accessible == null ? null : accessible.getAccessibleContext();
        String indent = "  ".repeat(depth);
        if (context == null) {
            lines.add(indent + "(no accessible context)");
            return;
        }
        lines.add(indent + describe(context));
        int count = context.getAccessibleChildrenCount();
        for (int i = 0; i < count && i < MAX_CHILDREN; i++) {
            dump(context.getAccessibleChild(i), depth + 1, lines);
        }
        if (count > MAX_CHILDREN) {
            lines.add(indent + "  ... " + (count - MAX_CHILDREN) + " more children");
        }
    }

    /**
     * One node : {@code role "name" (description) [states] {details}}.
     */
    public static String describe(AccessibleContext context) {
        StringBuilder sb = new StringBuilder(role(context));
        String name = context.getAccessibleName();
        if (name != null) {
            sb.append(" \"").append(oneLine(name)).append('"');
        }
        String description = context.getAccessibleDescription();
        if (description != null && !description.equals(name)) {
            sb.append(" (").append(oneLine(description)).append(')');
        }
        sb.append(" [").append(states(context)).append(']');
        List<String> details = details(context);
        if (!details.isEmpty()) {
            sb.append(" {").append(String.join("; ", details)).append('}');
        }
        return sb.toString();
    }

    public static String role(AccessibleContext context) {
        return context.getAccessibleRole() == null ? "no role" : context.getAccessibleRole().toDisplayString(Locale.ENGLISH);
    }

    /**
     * The states, sorted, without {@code focused} and {@code active}.
     */
    public static String states(AccessibleContext context) {
        AccessibleStateSet set = context.getAccessibleStateSet();
        TreeSet<String> states = new TreeSet<>();
        if (set != null) {
            for (AccessibleState state : set.toArray()) {
                if (state != AccessibleState.FOCUSED && state != AccessibleState.ACTIVE) {
                    states.add(state.toDisplayString(Locale.ENGLISH));
                }
            }
        }
        return String.join(",", states);
    }

    private static List<String> details(AccessibleContext context) {
        List<String> details = new ArrayList<>();
        AccessibleAction action = context.getAccessibleAction();
        if (action != null && action.getAccessibleActionCount() > 0) {
            List<String> actions = new ArrayList<>();
            for (int i = 0; i < action.getAccessibleActionCount(); i++) {
                actions.add(String.valueOf(action.getAccessibleActionDescription(i)));
            }
            // text components have dozens of editor actions : their number and the hash of their sorted names
            details.add(actions.size() <= 6 ? "actions " + String.join("|", actions)
                    : "actions " + actions.size() + " (sorted names SHA-256 "
                            + io.quarkiverse.desktop.showcase.core.Checks.sha256(String.join("|", new TreeSet<>(actions)))
                            + ")");
        }
        AccessibleValue value = context.getAccessibleValue();
        if (value != null) {
            details.add("value " + value.getCurrentAccessibleValue() + " in " + value.getMinimumAccessibleValue() + ".."
                    + value.getMaximumAccessibleValue());
        }
        AccessibleText text = context.getAccessibleText();
        if (text != null) {
            String selected = text.getSelectedText();
            details.add("text " + text.getCharCount() + " chars, caret " + text.getCaretPosition()
                    + (selected == null || selected.isEmpty() ? ""
                            : ", selection " + text.getSelectionStart() + "-" + text.getSelectionEnd() + " \""
                                    + oneLine(selected) + "\""));
            if (text instanceof AccessibleHypertext hypertext) {
                details.add("links " + hypertext.getLinkCount());
            }
        }
        if (context.getAccessibleEditableText() != null) {
            details.add("editable");
        }
        AccessibleSelection selection = context.getAccessibleSelection();
        if (selection != null) {
            details.add("selected " + selection.getAccessibleSelectionCount());
        }
        AccessibleTable table = context.getAccessibleTable();
        if (table != null) {
            details.add("table " + table.getAccessibleRowCount() + "x" + table.getAccessibleColumnCount());
        }
        AccessibleIcon[] icons = context.getAccessibleIcon();
        if (icons != null && icons.length > 0) {
            List<String> sizes = new ArrayList<>();
            for (AccessibleIcon icon : icons) {
                sizes.add(icon.getAccessibleIconWidth() + "x" + icon.getAccessibleIconHeight()
                        + (icon.getAccessibleIconDescription() == null ? ""
                                : " \"" + iconDescription(icon.getAccessibleIconDescription()) + "\""));
            }
            details.add("icons " + String.join("|", sizes));
        }
        if (context.getAccessibleComponent() instanceof javax.accessibility.AccessibleExtendedComponent extended) {
            AccessibleKeyBinding bindings = extended.getAccessibleKeyBinding();
            if (bindings != null && bindings.getAccessibleKeyBindingCount() > 0) {
                List<String> keys = new ArrayList<>();
                for (int i = 0; i < bindings.getAccessibleKeyBindingCount(); i++) {
                    keys.add(String.valueOf(bindings.getAccessibleKeyBinding(i)));
                }
                details.add("keys " + String.join("|", keys));
            }
        }
        AccessibleRelationSet relations = context.getAccessibleRelationSet();
        if (relations != null && relations.size() > 0) {
            List<String> list = new ArrayList<>();
            for (AccessibleRelation relation : relations.toArray()) {
                List<String> targets = new ArrayList<>();
                for (Object target : relation.getTarget()) {
                    targets.add(target instanceof Accessible a && a.getAccessibleContext() != null
                            ? "\"" + a.getAccessibleContext().getAccessibleName() + "\""
                            : "?");
                }
                list.add(relation.toDisplayString(Locale.ENGLISH) + " " + String.join(",", targets));
            }
            details.add("relations " + String.join("|", list));
        }
        return details;
    }

    /**
     * The description of an {@code ImageIcon(URL)} is its URL ({@code jrt:} or {@code jar:} on the JVM, {@code resource:}
     * in a native executable) : only its file name is kept.
     */
    static String iconDescription(String description) {
        if (description.contains(":/") || description.startsWith("resource:")) {
            return "URL of " + description.substring(description.lastIndexOf('/') + 1);
        }
        return description;
    }

    /**
     * The accessible descendants of {@code root} (depth first, {@code root} included) matching {@code filter}.
     */
    public static List<AccessibleContext> find(Accessible root, Predicate<AccessibleContext> filter) {
        List<AccessibleContext> found = new ArrayList<>();
        find(root, filter, found, 0);
        return found;
    }

    private static void find(Accessible accessible, Predicate<AccessibleContext> filter, List<AccessibleContext> found,
            int depth) {
        AccessibleContext context = accessible == null ? null : accessible.getAccessibleContext();
        if (context == null || depth > 40) {
            return;
        }
        if (filter.test(context)) {
            found.add(context);
        }
        for (int i = 0; i < context.getAccessibleChildrenCount(); i++) {
            find(context.getAccessibleChild(i), filter, found, depth + 1);
        }
    }

    /**
     * The contexts of {@code role} (English display string) under {@code root}.
     */
    public static List<AccessibleContext> byRole(Accessible root, String role) {
        return find(root, context -> role(context).equals(role));
    }

    /**
     * The names of the contexts of {@code role} under {@code root}, in order.
     */
    public static List<String> names(Accessible root, String role) {
        List<String> names = new ArrayList<>();
        for (AccessibleContext context : byRole(root, role)) {
            names.add(String.valueOf(context.getAccessibleName()));
        }
        return names;
    }

    private static String oneLine(String s) {
        String line = s.replace("\r", "").replace("\n", "\\n");
        return line.length() > 60 ? line.substring(0, 57) + "..." : line;
    }
}
