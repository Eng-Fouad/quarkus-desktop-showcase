package io.quarkiverse.desktop.showcase.pages.beans;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.CardLayout;
import java.awt.Checkbox;
import java.awt.CheckboxMenuItem;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.SystemColor;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * {@link java.beans.XMLEncoder} and {@link java.beans.XMLDecoder} : an application bean graph (AWT values,
 * collections, arrays, an immutable value with constructor properties, a custom persistence delegate, a bean with an
 * explicit bean info), an AWT form (containers, layouts with constraints, choice and list items, fonts and colors), a
 * menu bar with shortcuts, and the JDK values with dedicated persistence delegates ({@code SystemColor},
 * {@code AWTKeyStroke}, {@code TextAttribute}, {@code Collections} wrappers...), encoded to XML and decoded back ;
 * a hand-written XML using every element of the decoder syntax ; error reporting to exception listeners.
 * <p>
 * The decoded form is shown next to a form built like the encoded one. The XML text is deterministic (its
 * {@code version} attribute normalized) : its hash is compared between the runs.
 * <p>
 * Native executables : the persistence delegates of the JDK are found by name ({@code java.beans.MetaData$<type>_...}),
 * the encoder introspects the AWT classes, the decoder creates them and calls their methods by reflection. AWT only ;
 * the Swing form variant is {@code pages.swing.beans.SwingXmlPersistencePage}.
 */
@Singleton
public class XmlPersistencePage implements FeaturePage {

    /** A class that the encoder cannot instantiate (no public no-argument constructor, no persistence delegate). */
    @RegisterForReflection
    public static final class NoDefaultConstructor {

        private final int value;

        public NoDefaultConstructor(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @Override
    public String id() {
        return "beans-xml-persistence";
    }

    @Override
    public String title() {
        return "XMLEncoder and XMLDecoder";
    }

    @Override
    public String category() {
        return Categories.A11Y_BEANS;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public Component build() {
        List<Check> beanChecks = new ArrayList<>();
        List<Check> awtChecks = new ArrayList<>();
        List<Check> decoderChecks = new ArrayList<>();

        // application bean graph
        PrintSettings settings = settings();
        XmlSupport.Encoded settingsXml = XmlSupport.encode(settings);
        beanChecks.addAll(roundTrip("PrintSettings", settingsXml, settings));
        beanChecks.add(Checks.expect("PrintSettings XML : encodings used",
                "Color constructor, Font delegate, Insets delegate, Date delegate, Margins constructor properties, "
                        + "MediaSpec custom delegate, Ticket bean info (2 properties), char, Enum.valueOf, "
                        + "public fields of Rectangle and Point",
                () -> encodings(settingsXml.xml())));
        beanChecks.add(Checks.expect("encoder references identical instances (id, idref)", "same instance", () -> {
            Point shared = new Point(1, 2);
            String xml = XmlSupport.encode(new ArrayList<>(List.of(shared, shared))).xml();
            List<?> decoded = (List<?>) XmlSupport.decode(xml, null).objects().get(0);
            return decoded.get(0) == decoded.get(1) ? "same instance" : "copies";
        }));

        // JDK values with dedicated persistence delegates (java.beans.MetaData)
        List<Object> values = values();
        XmlSupport.Encoded valuesXml = XmlSupport.encode(values);
        beanChecks.addAll(roundTrip("JDK values", valuesXml, null));
        beanChecks.add(Checks.expect("JDK values : decoded value by value", "all equal", () -> {
            List<?> decoded = (List<?>) XmlSupport.decode(valuesXml.xml(), null).objects().get(0);
            List<String> different = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                if (!deepEquals(values.get(i), decoded.get(i))) {
                    different.add(i + ": " + decoded.get(i));
                }
            }
            return different.isEmpty() ? "all equal" : String.join(", ", different);
        }));
        beanChecks.add(Checks.expect("JDK values : element classes", "SystemColor MenuShortcut TextAttribute "
                + "UnmodifiableRandomAccessList SingletonMap EmptySet SynchronizedRandomAccessList UnmodifiableMap "
                + "Hashtable TreeMap int[][] Font", () -> {
                    List<?> decoded = (List<?>) XmlSupport.decode(valuesXml.xml(), null).objects().get(0);
                    return String.join(" ", decoded.stream().map(o -> o.getClass().getSimpleName()).toList());
                }));

        // AWT form and menu bar
        Panel encodedForm = form();
        XmlSupport.Encoded formXml = XmlSupport.encode(encodedForm);
        XmlSupport.Decoded formDecoded = XmlSupport.decode(formXml.xml(), null);
        Component decodedForm = formDecoded.objects().isEmpty() ? Ui.text("not decoded")
                : (Component) formDecoded.objects().get(0);
        awtChecks.add(Checks.expect("form : encoding exceptions", "none", () -> exceptions(formXml.exceptions())));
        awtChecks.add(Checks.expect("form : decoding exceptions", "none", () -> exceptions(formDecoded.exceptions())));
        awtChecks.add(Check.pass("form XML : lines, SHA-256", formXml.lines() + ", " + formXml.sha256()));
        awtChecks.add(Checks.expect("form : decoded tree equals the encoded tree", describe(encodedForm),
                () -> describe(decodedForm)));
        awtChecks.add(Checks.expect("form XML : persistence delegates at work",
                "BorderLayout constraints, GridBagConstraints, Choice items, List items, Container add, fonts, colors",
                () -> formEncodings(formXml.xml())));
        MenuBar menuBar = menuBar();
        XmlSupport.Encoded menuXml = XmlSupport.encode(menuBar);
        // the MenuBar persistence delegate of the JDK only keeps the help menu : the other menus are lost
        awtChecks.add(Checks.expect("menu bar : round trip (the JDK keeps the help menu only)",
                "MenuBar [Menu \"Help\" [MenuItem \"About\"]] help none", () -> describe(
                        (MenuBar) XmlSupport.decode(menuXml.xml(), null).objects().get(0))));
        awtChecks.add(Checks.expect("menu bar : encoded", "MenuBar [Menu \"File\" [MenuItem \"Print...\" Ctrl+P, "
                + "CheckboxMenuItem \"Landscape\" true, MenuItem \"-\", Menu \"Recent\" [MenuItem \"report.ps\"]], "
                + "Menu \"Help\" [MenuItem \"About\"]] help Help", () -> describe(menuBar)));
        awtChecks.add(Checks.expect("menu bar : encoding exceptions", "none", () -> exceptions(menuXml.exceptions())));
        awtChecks.add(Check.pass("menu bar XML : lines, SHA-256", menuXml.lines() + ", " + menuXml.sha256()));

        // the decoder syntax (hand-written XML resource) and error reporting
        decoderChecks.addAll(decoderChecks());

        return Ui.column(14,
                Ui.text("XMLEncoder writes object graphs as the sequence of constructor and method calls that recreate "
                        + "them ; XMLDecoder replays them. Left : an AWT form built by code, right : the same form decoded "
                        + "from the XML written for it.", 1000),
                Ui.row(24, Ui.column(4, Ui.caption("Built by code"), form()),
                        Ui.column(4, Ui.caption("Decoded from XML"), decodedForm)),
                Ui.title("XML of the application bean (" + settingsXml.lines() + " lines)"),
                Ui.text(XmlSupport.excerpt(settingsXml.xml(), 40), new Font(Font.MONOSPACED, Font.PLAIN, 11),
                        Ui.TEXT_COLOR, 1000),
                ChecksView.table("Application beans and JDK values", beanChecks),
                ChecksView.table("AWT components", awtChecks),
                ChecksView.table("XMLDecoder syntax and errors", decoderChecks));
    }

    // ----------------------------------------------------------------------------------------------------- data

    static PrintSettings settings() {
        PrintSettings settings = new PrintSettings();
        settings.setName("Quarterly report <draft> & \"final\"");
        settings.setCopies(3);
        settings.setJobId(9_000_000_001L);
        settings.setScale(0.75);
        settings.setRatio(1.25f);
        settings.setSeparator(';');
        settings.setDuplex(true);
        settings.setOrientation(PrintJobBean.Orientation.LANDSCAPE);
        settings.setCreated(new Date(0));
        settings.setType(Ticket.class);
        settings.setColor(new Color(30, 136, 229, 200));
        settings.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 13));
        settings.setInsets(new Insets(1, 2, 3, 4));
        settings.setArea(new Rectangle(10, 20, 300, 400));
        settings.setSize(new Dimension(640, 480));
        settings.setOrigin(new Point(-5, 7));
        settings.setTags(new ArrayList<>(List.of("finance", "q3", "confidential")));
        Map<String, Integer> counters = new LinkedHashMap<>();
        counters.put("pages", 12);
        counters.put("sheets", 6);
        settings.setCounters(counters);
        settings.setPages(new int[] { 1, 2, 3, 5, 8 });
        settings.setMargins(new PrintSettings.Margins(36, 36, 54, 36));
        settings.setMedia(new PrintSettings.MediaSpec("iso-a4", 595.28, 841.89));
        settings.setTicket(new Ticket("Q-042", 7));
        return settings;
    }

    private static List<Object> values() {
        Map<TextAttribute, Object> attributes = new LinkedHashMap<>();
        attributes.put(TextAttribute.FAMILY, Font.SANS_SERIF);
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        Map<String, Integer> sorted = new TreeMap<>(Map.of("b", 2, "a", 1));
        Hashtable<String, String> table = new Hashtable<>(Map.of("key", "value"));
        return new ArrayList<>(Arrays.asList(
                // not an AWTKeyStroke : once javax.swing.KeyStroke is initialized, AWTKeyStroke creates KeyStrokes
                // (the Swing variant of this page encodes key strokes)
                SystemColor.window,
                new MenuShortcut(KeyEvent.VK_S, true),
                TextAttribute.UNDERLINE,
                Collections.unmodifiableList(new ArrayList<>(List.of("x", "y"))),
                Collections.singletonMap("one", 1),
                Collections.emptySet(),
                Collections.synchronizedList(new ArrayList<>(List.of(1, 2))),
                Collections.unmodifiableMap(new TreeMap<>(Map.of("k", "v"))),
                table,
                sorted,
                new int[][] { { 1, 2 }, { 3 } },
                new Font(attributes)));
    }

    /**
     * The form : a border layout panel with a title, a grid bag of fields and a row of buttons.
     */
    static Panel form() {
        Panel form = new Panel(new BorderLayout(8, 8));
        // explicit names : a component without name gets a generated one from a process wide counter (e.g. panel12),
        // which the encoder would write
        form.setName("form");
        form.setBackground(new Color(0xECEFF1));
        Label title = named(new Label("Print settings", Label.CENTER), "title");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        title.setForeground(new Color(0x1E88E5));
        form.add(title, BorderLayout.NORTH);

        // a grid bag layout with one component only : the persistence delegate of GridBagLayout writes the constraints
        // in the order of a hash table keyed by components (identity hash codes), a random order with several ones
        Panel fields = named(new Panel(new GridLayout(0, 2, 4, 2)), "fields");
        fields.add(named(new Label("Printer:"), "printerLabel"));
        Choice printer = named(new Choice(), "printer");
        printer.add("PostScript");
        printer.add("PDF");
        printer.add("Paper");
        fields.add(printer);
        fields.add(named(new Label("Copies:"), "copiesLabel"));
        fields.add(named(new TextField("2", 4), "copies"));
        fields.add(named(new Checkbox("Collate copies", true), "collate"));
        Panel list = named(new Panel(new GridBagLayout()), "listPanel");
        java.awt.List pages = named(new java.awt.List(3, true), "pages");
        pages.add("Cover");
        pages.add("Summary");
        pages.add("Details");
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridwidth = 2;
        list.add(pages, c);
        fields.add(list);
        form.add(fields, BorderLayout.CENTER);

        Panel buttons = named(new Panel(new FlowLayout(FlowLayout.RIGHT)), "buttons");
        buttons.add(named(new Button("OK"), "ok"));
        buttons.add(named(new Button("Cancel"), "cancel"));
        form.add(buttons, BorderLayout.SOUTH);

        Panel cards = named(new Panel(new CardLayout()), "cards");
        cards.add(named(new Label("first card"), "firstCard"), "first");
        cards.add(named(new Label("second card"), "secondCard"), "second");
        form.add(cards, BorderLayout.EAST);
        Panel grid = named(new Panel(new GridLayout(2, 1)), "grid");
        grid.add(named(new Label("A"), "a"));
        grid.add(named(new Label("B"), "b"));
        form.add(grid, BorderLayout.WEST);
        return form;
    }

    private static <C extends Component> C named(C component, String name) {
        component.setName(name);
        return component;
    }

    private static <M extends java.awt.MenuComponent> M named(M component, String name) {
        component.setName(name);
        return component;
    }

    private static MenuBar menuBar() {
        MenuBar bar = named(new MenuBar(), "bar");
        Menu file = named(new Menu("File"), "file");
        file.add(named(new MenuItem("Print...", new MenuShortcut(KeyEvent.VK_P)), "print"));
        file.add(named(new CheckboxMenuItem("Landscape", true), "landscape"));
        file.add(named(new MenuItem("-"), "separator"));
        Menu recent = named(new Menu("Recent"), "recent");
        recent.add(named(new MenuItem("report.ps"), "report"));
        file.add(recent);
        bar.add(file);
        Menu help = named(new Menu("Help"), "help");
        help.add(named(new MenuItem("About"), "about"));
        bar.add(help);
        bar.setHelpMenu(help);
        return bar;
    }

    // --------------------------------------------------------------------------------------------------- checks

    /**
     * Encoding and decoding exceptions, the decoded object equality (when {@code original} is not null), size and hash.
     */
    private static List<Check> roundTrip(String name, XmlSupport.Encoded encoded, Object original) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect(name + " : encoding exceptions", "none", () -> exceptions(encoded.exceptions())));
        XmlSupport.Decoded decoded = XmlSupport.decode(encoded.xml(), null);
        checks.add(Checks.expect(name + " : decoding exceptions, objects", "none, 1",
                () -> exceptions(decoded.exceptions()) + ", " + decoded.objects().size()));
        if (original != null) {
            checks.add(Checks.expect(name + " : decoded equals encoded", true,
                    () -> original.equals(decoded.objects().get(0))));
        }
        checks.add(Check.pass(name + " XML : lines, SHA-256", encoded.lines() + ", " + encoded.sha256()));
        return checks;
    }

    private static String exceptions(List<String> exceptions) {
        return exceptions.isEmpty() ? "none" : exceptions.size() + " : " + String.join(" | ", exceptions);
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            count++;
        }
        return count;
    }

    private static String encodings(String xml) {
        List<String> found = new ArrayList<>();
        if (xml.contains("<object class=\"java.awt.Color\">") && xml.contains("<int>200</int>")) {
            found.add("Color constructor");
        }
        if (xml.contains("<object class=\"java.awt.Font\">")) {
            found.add("Font delegate");
        }
        if (xml.contains("<object class=\"java.awt.Insets\">")) {
            found.add("Insets delegate");
        }
        if (xml.contains("<object class=\"java.util.Date\">")) {
            found.add("Date delegate");
        }
        if (xml.contains("PrintSettings$Margins\">\n       <int>36</int>")
                || xml.contains("PrintSettings$Margins\">\n    <int>36</int>")
                || xml.matches("(?s).*PrintSettings\\$Margins\">\\s*<int>36</int>.*")) {
            found.add("Margins constructor properties");
        }
        if (xml.matches("(?s).*PrintSettings\\$MediaSpec\">\\s*<string>iso-a4</string>.*")) {
            found.add("MediaSpec custom delegate");
        }
        if (xml.contains("<object class=\"io.quarkiverse.desktop.showcase.pages.beans.Ticket\">")
                && !xml.contains("\"internal\"")) {
            found.add("Ticket bean info (2 properties)");
        }
        if (xml.contains("<char>;</char>")) {
            found.add("char");
        }
        if (xml.contains("<object class=\"java.lang.Enum\" method=\"valueOf\">")) {
            found.add("Enum.valueOf");
        }
        if (xml.contains("<void class=\"java.awt.Rectangle\" method=\"getField\">")
                && xml.contains("<void class=\"java.awt.Point\" method=\"getField\">")) {
            found.add("public fields of Rectangle and Point");
        }
        return String.join(", ", found);
    }

    private static String formEncodings(String xml) {
        List<String> found = new ArrayList<>();
        if (xml.contains("<string>North</string>") || xml.contains("<string>Center</string>")) {
            found.add("BorderLayout constraints");
        }
        if (xml.contains("java.awt.GridBagConstraints")) {
            found.add("GridBagConstraints");
        }
        if (xml.contains("<string>PostScript</string>")) {
            found.add("Choice items");
        }
        if (xml.contains("<string>Summary</string>")) {
            found.add("List items");
        }
        if (xml.contains("<void method=\"add\">")) {
            found.add("Container add");
        }
        if (xml.contains("<object class=\"java.awt.Font\">")) {
            found.add("fonts");
        }
        if (xml.contains("<object class=\"java.awt.Color\">")) {
            found.add("colors");
        }
        return String.join(", ", found);
    }

    /**
     * A deterministic description of a component tree : classes, texts, items, states, layouts and children.
     */
    static String describe(Component component) {
        StringBuilder sb = new StringBuilder(component.getClass().getSimpleName());
        if (component instanceof Label label) {
            sb.append(" \"").append(label.getText()).append("\" align ").append(label.getAlignment());
        } else if (component instanceof Button button) {
            sb.append(" \"").append(button.getLabel()).append('"');
        } else if (component instanceof Checkbox checkbox) {
            sb.append(" \"").append(checkbox.getLabel()).append("\" ").append(checkbox.getState());
        } else if (component instanceof TextField field) {
            sb.append(" \"").append(field.getText()).append("\" columns ").append(field.getColumns());
        } else if (component instanceof Choice choice) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < choice.getItemCount(); i++) {
                items.add(choice.getItem(i));
            }
            sb.append(' ').append(items);
        } else if (component instanceof java.awt.List list) {
            sb.append(' ').append(Arrays.toString(list.getItems())).append(" multiple ").append(list.isMultipleMode());
        }
        if (component.isFontSet()) {
            Font font = component.getFont();
            sb.append(" font ").append(font.getFamily()).append(' ').append(font.getStyle()).append(' ')
                    .append(font.getSize());
        }
        if (component.isForegroundSet()) {
            sb.append(" fg ").append(Checks.argb(component.getForeground().getRGB()));
        }
        if (component.isBackgroundSet()) {
            sb.append(" bg ").append(Checks.argb(component.getBackground().getRGB()));
        }
        if (component instanceof Container container && !(component instanceof java.awt.List)
                && !(component instanceof Choice)) {
            if (container.getLayout() != null) {
                sb.append(" layout ").append(container.getLayout().getClass().getSimpleName());
            }
            if (container.getLayout() instanceof BorderLayout border) {
                List<String> constraints = new ArrayList<>();
                for (Component child : container.getComponents()) {
                    constraints.add(String.valueOf(border.getConstraints(child)));
                }
                sb.append(' ').append(constraints);
            } else if (container.getLayout() instanceof GridBagLayout grid) {
                List<String> cells = new ArrayList<>();
                for (Component child : container.getComponents()) {
                    GridBagConstraints c = grid.getConstraints(child);
                    cells.add(c.gridx + "," + c.gridy + " w" + c.gridwidth + " f" + c.fill + " a" + c.anchor);
                }
                sb.append(' ').append(cells);
            }
            List<String> children = new ArrayList<>();
            for (Component child : container.getComponents()) {
                children.add(describe(child));
            }
            if (!children.isEmpty()) {
                sb.append(" (").append(String.join(", ", children)).append(')');
            }
        }
        return sb.toString();
    }

    static String describe(MenuBar bar) {
        List<String> menus = new ArrayList<>();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            menus.add(describe(bar.getMenu(i)));
        }
        return "MenuBar " + menus + " help " + (bar.getHelpMenu() == null ? "none" : bar.getHelpMenu().getLabel());
    }

    private static String describe(MenuItem item) {
        String text = item.getClass().getSimpleName() + " \"" + item.getLabel() + "\"";
        if (item.getShortcut() != null) {
            text += " " + item.getShortcut();
        }
        if (item instanceof CheckboxMenuItem checkbox) {
            text += " " + checkbox.getState();
        }
        if (item instanceof Menu menu) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < menu.getItemCount(); i++) {
                items.add(describe(menu.getItem(i)));
            }
            text += " " + items;
        }
        return text;
    }

    private static boolean deepEquals(Object a, Object b) {
        if (a != null && a.getClass().isArray()) {
            return Arrays.deepEquals(new Object[] { a }, new Object[] { b });
        }
        return java.util.Objects.equals(a, b);
    }

    // -------------------------------------------------------------------------------------------------- decoder

    private static List<Check> decoderChecks() {
        List<Check> checks = new ArrayList<>();
        PrintJobBean owner = new PrintJobBean();
        XmlSupport.Decoded decoded = XmlSupport.decode(Edt.resourceText("/showcase/print-misc/beans/decoder-elements.xml"),
                owner);
        checks.add(Checks.expect("decoder-elements.xml : exceptions, objects", "none, 1",
                () -> exceptions(decoded.exceptions()) + ", " + decoded.objects().size()));
        List<?> list = decoded.objects().isEmpty() ? List.of() : (List<?>) decoded.objects().get(0);
        checks.add(Checks.expect("values : string, int, long, short, byte, float, double, char, char code, boolean, true, "
                + "false, null, class",
                "String text & <markup>, Integer 42, Long 9000000000, Short 7, Byte -3, Float 1.5, Double 2.25, "
                        + "Character Q, Character A, Boolean true, Boolean true, Boolean false, null, Class java.awt.Point",
                () -> describeValues(list.subList(0, 14))));
        checks.add(Checks.expect("object field, field, new, object with arguments, static methods",
                "Color java.awt.Color[r=255,g=200,b=0], Integer 1, Point java.awt.Point[x=3,y=4], "
                        + "Dimension java.awt.Dimension[width=30,height=20], Integer 77, Integer 9",
                () -> describeValues(list.subList(14, 20))));
        checks.add(Checks.expect("property (void and property elements), instance field, arrays",
                "Rectangle java.awt.Rectangle[x=7,y=6,width=30,height=20], int[] [1, 0, 3], String[] [a, b]",
                () -> describeValues(list.subList(20, 23))));
        checks.add(Checks.expect("application bean (indexed setter), object idref, var idref, void id, self reference",
                "From XML x1 LANDSCAPE pages [1, 8, 3], same instance, same instance, From XML x1 LANDSCAPE pages [1, 8, 3], "
                        + "the list itself", () -> {
                            PrintJobBean job = (PrintJobBean) list.get(23);
                            return job.getSummary() + ", " + (list.get(24) == job ? "same instance" : "another")
                                    + ", " + (list.get(25) == job ? "same instance" : "another") + ", " + list.get(26)
                                    + ", " + (list.get(27) == list ? "the list itself" : "another");
                        }));
        checks.add(Checks.expect("decoder owner : property set by the XML", "set through the decoder owner",
                owner::getTitle));
        checks.add(Checks.expect("malformed XML : exception listener", "org.xml.sax.SAXParseException", () -> {
            XmlSupport.Decoded broken = XmlSupport.decode("<java><object class=\"java.awt.Point\"></java>", null);
            return broken.exceptions().isEmpty() ? "no exception" : broken.exceptions().get(0).split(":")[0];
        }));
        // the message is the class loader's : "no/such/Type" from the Quarkus class loader of the JVM mode, "no.such.Type"
        // from Class.forName in a native executable
        checks.add(Checks.expect("unknown class : exception listener", "java.lang.ClassNotFoundException: no.such.Type", () -> {
            XmlSupport.Decoded unknown = XmlSupport.decode("<java><object class=\"no.such.Type\"/></java>", null);
            return unknown.exceptions().isEmpty() ? "no exception" : unknown.exceptions().get(0).replace('/', '.');
        }));
        checks.add(Checks.expect("encoding a class without no-argument constructor : exception listener",
                "java.lang.InstantiationException", () -> {
                    XmlSupport.Encoded encoded = XmlSupport.encode(new NoDefaultConstructor(5));
                    return encoded.exceptions().isEmpty() ? "no exception" : encoded.exceptions().get(0).split(":")[0];
                }));
        return checks;
    }

    private static String describeValues(List<?> values) {
        List<String> parts = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                parts.add("null");
            } else if (value instanceof Class<?> type) {
                parts.add("Class " + type.getName());
            } else if (value.getClass().isArray()) {
                List<String> items = new ArrayList<>();
                for (int i = 0; i < Array.getLength(value); i++) {
                    items.add(String.valueOf(Array.get(value, i)));
                }
                parts.add(value.getClass().getSimpleName() + " " + items);
            } else {
                parts.add(value.getClass().getSimpleName() + " " + value);
            }
        }
        return String.join(", ", parts);
    }
}
