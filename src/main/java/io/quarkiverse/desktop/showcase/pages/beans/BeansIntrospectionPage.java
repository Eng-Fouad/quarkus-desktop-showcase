package io.quarkiverse.desktop.showcase.pages.beans;

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.Beans;
import java.beans.EventHandler;
import java.beans.EventSetDescriptor;
import java.beans.Expression;
import java.beans.IndexedPropertyChangeEvent;
import java.beans.IndexedPropertyDescriptor;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeListenerProxy;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.beans.PropertyVetoException;
import java.beans.SimpleBeanInfo;
import java.beans.Statement;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkus.runtime.annotations.RegisterForProxy;

/**
 * {@code java.beans} introspection and dynamic invocation : the {@link Introspector} on an application bean
 * (annotations {@code @JavaBean} and {@code @BeanProperty}), on a bean with an explicit {@link BeanInfo} (found by
 * name, with icons loaded from resources) and on AWT components (the JDK's {@code ComponentBeanInfo}) ; the
 * {@link PropertyEditorManager} and the JDK property editors ({@code ColorEditor} and {@code FontEditor} are AWT panels,
 * shown here) ; {@link java.beans.PropertyChangeSupport} and {@link java.beans.VetoableChangeSupport} (bound, indexed and
 * constrained properties, veto and revert) ; {@link EventHandler} listeners (dynamic proxies) wiring AWT components ;
 * {@link Statement} and {@link Expression} ; {@link Beans#instantiate} of a class and of a serialized bean.
 * <p>
 * Native executables : everything here is reflection. The application registers its own classes
 * ({@code @RegisterForReflection}) and the proxies of the listener interfaces it creates with {@link EventHandler}
 * ({@link RegisterForProxy}) ; the JDK classes reached by name ({@code com.sun.beans.editors.*},
 * {@code com.sun.beans.infos.ComponentBeanInfo}) and the members of the AWT classes introspected or invoked by name
 * are not registered by the application : missing registrations show here.
 */
@Singleton
@RegisterForProxy(targets = { ActionListener.class, PropertyChangeListener.class, PrintJobBean.JobListener.class })
public class BeansIntrospectionPage implements FeaturePage {

    @Override
    public String id() {
        return "beans-introspection";
    }

    @Override
    public String title() {
        return "JavaBeans introspection";
    }

    @Override
    public String category() {
        return Categories.A11Y_BEANS;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Component build() throws Exception {
        List<Check> introspection = new ArrayList<>();
        List<Check> editors = new ArrayList<>();
        List<Check> events = new ArrayList<>();
        List<Check> invocation = new ArrayList<>();

        BeanInfo info = Introspector.getBeanInfo(PrintJobBean.class);
        introspectionChecks(info, introspection);
        List<Component> editorPanels = editorChecks(editors);
        eventChecks(events);
        Panel wiring = eventHandlerChecks(events);
        invocationChecks(invocation);
        Component icons = iconChecks(introspection);

        return Ui.column(14,
                Ui.text("JavaBeans : introspection of application beans and AWT components, property editors (the JDK's "
                        + "ColorEditor and FontEditor below are AWT panels), property change and veto support, "
                        + "EventHandler proxies (the label shows the label of the button whose action event it received), "
                        + "Statement, Expression and Beans.instantiate.", 1000),
                Ui.row(24,
                        Ui.column(4, Ui.caption("PropertyEditorManager.findEditor(Color.class).getCustomEditor()"),
                                editorPanels.get(0)),
                        Ui.column(4, Ui.caption("findEditor(Font.class).getCustomEditor()"), editorPanels.get(1)),
                        Ui.column(4, Ui.caption("EventHandler wiring"), wiring),
                        Ui.column(4, Ui.caption("TicketBeanInfo icons"), icons)),
                Ui.title("BeanInfo of PrintJobBean"),
                Ui.text(String.join("\n", describe(info)), new Font(Font.MONOSPACED, Font.PLAIN, 11), Ui.TEXT_COLOR, 1000),
                ChecksView.table("Introspector", introspection),
                ChecksView.table("Property editors", editors),
                ChecksView.table("Property change support and EventHandler", events),
                ChecksView.table("Statement, Expression, Beans", invocation));
    }

    // ------------------------------------------------------------------------------------------------ introspection

    /**
     * One line per descriptor of {@code info}.
     */
    static List<String> describe(BeanInfo info) {
        List<String> lines = new ArrayList<>();
        BeanDescriptor bean = info.getBeanDescriptor();
        lines.add("bean " + bean.getName() + " \"" + bean.getShortDescription() + "\" default property "
                + info.getDefaultPropertyIndex() + ", default event set " + info.getDefaultEventIndex());
        for (PropertyDescriptor p : info.getPropertyDescriptors()) {
            List<String> flags = new ArrayList<>();
            if (p.isBound()) {
                flags.add("bound");
            }
            if (p.isConstrained()) {
                flags.add("constrained");
            }
            if (p.isPreferred()) {
                flags.add("preferred");
            }
            if (p.isHidden()) {
                flags.add("hidden");
            }
            if (p.isExpert()) {
                flags.add("expert");
            }
            String type = p.getPropertyType() == null ? "-" : p.getPropertyType().getSimpleName();
            String line = "property " + p.getName() + " : " + type + " " + name(p.getReadMethod()) + "/"
                    + name(p.getWriteMethod()) + " " + flags;
            if (p instanceof IndexedPropertyDescriptor indexed) {
                line += " indexed " + indexed.getIndexedPropertyType().getSimpleName() + " "
                        + name(indexed.getIndexedReadMethod()) + "/" + name(indexed.getIndexedWriteMethod());
            }
            if (!p.getName().equals(p.getShortDescription())) {
                line += " \"" + p.getShortDescription() + "\"";
            }
            if (p.getValue("enumerationValues") instanceof Object[] values && values.length > 0) {
                line += " enumerationValues " + values.length / 3;
            }
            lines.add(line);
        }
        for (EventSetDescriptor e : info.getEventSetDescriptors()) {
            List<String> methods = new ArrayList<>();
            for (Method m : e.getListenerMethods()) {
                methods.add(m.getName());
            }
            Collections.sort(methods);
            lines.add("event set " + e.getName() + " : " + e.getListenerType().getSimpleName() + " " + methods + " "
                    + name(e.getAddListenerMethod()) + "/" + name(e.getRemoveListenerMethod())
                    + (e.getGetListenerMethod() == null ? "" : "/" + name(e.getGetListenerMethod())));
        }
        TreeSet<String> methods = new TreeSet<>();
        for (MethodDescriptor m : info.getMethodDescriptors()) {
            if (m.getMethod().getDeclaringClass() == info.getBeanDescriptor().getBeanClass()) {
                methods.add(m.getName());
            }
        }
        lines.add("methods declared by the bean : " + String.join(" ", methods));
        return lines;
    }

    private static String name(Method method) {
        return method == null ? "-" : method.getName();
    }

    private static PropertyDescriptor property(BeanInfo info, String name) {
        for (PropertyDescriptor p : info.getPropertyDescriptors()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        throw new IllegalArgumentException("No property " + name);
    }

    private static String names(PropertyDescriptor[] properties) {
        return String.join(" ", Arrays.stream(properties).map(PropertyDescriptor::getName).toList());
    }

    private static void introspectionChecks(BeanInfo info, List<Check> checks) {
        checks.add(Checks.expect("PrintJobBean : bean descriptor", "PrintJobBean, A print job of the showcase", () -> info
                .getBeanDescriptor().getName() + ", " + info.getBeanDescriptor().getShortDescription()));
        checks.add(Checks.expect("PrintJobBean : properties", "class color copies duplex jobListeners orientation pages "
                + "password propertyChangeListeners scale summary title", () -> names(info.getPropertyDescriptors())));
        checks.add(Checks.expect("PrintJobBean : default property and event set (@JavaBean)", "title, job", () -> info
                .getPropertyDescriptors()[info.getDefaultPropertyIndex()].getName() + ", "
                + info.getEventSetDescriptors()[info.getDefaultEventIndex()].getName()));
        checks.add(Checks.expect("title : bound, preferred, description (@BeanProperty)",
                "true, true, The title printed in the page header", () -> {
                    PropertyDescriptor p = property(info, "title");
                    return p.isBound() + ", " + p.isPreferred() + ", " + p.getShortDescription();
                }));
        checks.add(Checks.expect("copies : constrained (throws PropertyVetoException), enumeration values",
                "true, [ONE_COPY, 1, ONE_COPY, TWO_COPIES, 2, PrintJobBean.TWO_COPIES]", () -> {
                    PropertyDescriptor p = property(info, "copies");
                    return p.isConstrained() + ", " + Arrays.toString((Object[]) p.getValue("enumerationValues"));
                }));
        checks.add(Checks.expect("duplex expert, scale hidden, color visualUpdate", "true, true, true", () -> property(info,
                "duplex").isExpert() + ", " + property(info, "scale").isHidden() + ", "
                + property(info, "color").getValue("visualUpdate")));
        checks.add(Checks.expect("pages : indexed", "IndexedPropertyDescriptor int getPages/setPages", () -> {
            IndexedPropertyDescriptor p = (IndexedPropertyDescriptor) property(info, "pages");
            return p.getClass().getSimpleName() + " " + p.getIndexedPropertyType() + " "
                    + p.getIndexedReadMethod().getName() + "/" + p.getIndexedWriteMethod().getName();
        }));
        checks.add(Checks.expect("summary read-only, password write-only", "getSummary/-, -/setPassword", () -> name(property(
                info, "summary").getReadMethod()) + "/" + name(property(info, "summary").getWriteMethod()) + ", "
                + name(property(info, "password").getReadMethod()) + "/" + name(property(info, "password").getWriteMethod())));
        checks.add(Checks.expect("event sets", "job propertyChange vetoableChange", () -> String.join(" ",
                new TreeSet<>(Arrays.stream(info.getEventSetDescriptors()).map(EventSetDescriptor::getName).toList()))));
        checks.add(Checks.expect("Introspector.getBeanInfo(PrintJobBean, Object) : properties", 11,
                () -> Introspector.getBeanInfo(PrintJobBean.class, Object.class).getPropertyDescriptors().length));
        // explicit bean info, found by name (Class.forName of <bean>BeanInfo)
        checks.add(Checks.expect("Ticket : explicit TicketBeanInfo", "Ticket (explicit bean info), code seat, "
                + "Ticket code / Seat number, default seat", () -> {
                    BeanInfo ticket = Introspector.getBeanInfo(Ticket.class, Object.class);
                    PropertyDescriptor[] properties = ticket.getPropertyDescriptors();
                    return ticket.getBeanDescriptor().getDisplayName() + ", " + names(properties) + ", "
                            + properties[0].getDisplayName() + " / " + properties[1].getDisplayName() + ", default "
                            + properties[ticket.getDefaultPropertyIndex()].getName();
                }));
        checks.add(Checks.expect("Ticket, IGNORE_ALL_BEANINFO : properties", "class code internal seat",
                () -> names(Introspector.getBeanInfo(Ticket.class, Introspector.IGNORE_ALL_BEANINFO)
                        .getPropertyDescriptors())));
        // AWT components : the JDK's com.sun.beans.infos.ComponentBeanInfo describes java.awt.Component
        checks.add(Checks.expect("java.awt.Component (ComponentBeanInfo) : properties, enabled expert, visible hidden",
                "background enabled focusable font foreground name visible, true, true", () -> {
                    BeanInfo component = Introspector.getBeanInfo(Component.class);
                    return names(component.getPropertyDescriptors()) + ", "
                            + property(component, "enabled").isExpert() + ", " + property(component, "visible").isHidden();
                }));
        checks.add(Checks.expect("java.awt.Button : label property, event sets", "label String getLabel/setLabel, action "
                + "component focus hierarchy hierarchyBounds inputMethod key mouse mouseMotion mouseWheel propertyChange",
                () -> {
                    BeanInfo button = Introspector.getBeanInfo(Button.class);
                    PropertyDescriptor label = property(button, "label");
                    TreeSet<String> sets = new TreeSet<>();
                    for (EventSetDescriptor e : button.getEventSetDescriptors()) {
                        sets.add(e.getName());
                    }
                    return "label " + label.getPropertyType().getSimpleName() + " " + label.getReadMethod().getName() + "/"
                            + label.getWriteMethod().getName() + ", " + String.join(" ", sets);
                }));
        checks.add(Checks.info("java.awt.Button : properties", () -> Introspector.getBeanInfo(Button.class)
                .getPropertyDescriptors().length));
        checks.add(Checks.expect("Introspector.decapitalize(\"FooBah\" / \"URL\" / \"X\")", "fooBah / URL / x",
                () -> Introspector.decapitalize("FooBah") + " / " + Introspector.decapitalize("URL") + " / "
                        + Introspector.decapitalize("X")));
        checks.add(Checks.expect("Introspector.getBeanInfoSearchPath()", "[sun.beans.infos]",
                () -> Arrays.toString(Introspector.getBeanInfoSearchPath())));
        checks.add(Checks.expect("new PropertyDescriptor(\"title\", PrintJobBean.class)", "getTitle/setTitle String",
                () -> {
                    PropertyDescriptor p = new PropertyDescriptor("title", PrintJobBean.class);
                    return p.getReadMethod().getName() + "/" + p.getWriteMethod().getName() + " "
                            + p.getPropertyType().getSimpleName();
                }));
    }

    private static Component iconChecks(List<Check> checks) throws Exception {
        BeanInfo info = Introspector.getBeanInfo(Ticket.class);
        Image small = info.getIcon(BeanInfo.ICON_COLOR_16x16);
        Image large = info.getIcon(BeanInfo.ICON_COLOR_32x32);
        BufferedImage smallPixels = grab(small, 16);
        BufferedImage largePixels = grab(large, 32);
        checks.add(Checks.expect("TicketBeanInfo.getIcon : 16x16, 32x32, mono", "16x16 #FFFB8C00, 32x32 #FFFB8C00, null",
                () -> smallPixels.getWidth() + "x" + smallPixels.getHeight() + " " + Checks.argb(smallPixels.getRGB(2, 8))
                        + ", " + largePixels.getWidth() + "x" + largePixels.getHeight() + " "
                        + Checks.argb(largePixels.getRGB(4, 16)) + ", " + info.getIcon(BeanInfo.ICON_MONO_16x16)));
        checks.add(Checks.expect("SimpleBeanInfo.loadImage(missing resource)", "null",
                () -> String.valueOf(new SimpleBeanInfo() {
                    Image load() {
                        return loadImage("/showcase/print-misc/beans/missing.png");
                    }
                }.load())));
        BufferedImage scaled = Snapshots.offscreen(96, 48, g -> {
            g.drawImage(smallPixels, 8, 16, null);
            g.drawImage(largePixels, 40, 8, null);
        });
        return Ui.image(scaled);
    }

    /**
     * The pixels of a toolkit image, grabbed synchronously ({@link PixelGrabber} waits for the image production).
     */
    private static BufferedImage grab(Image image, int size) throws InterruptedException {
        if (image == null) {
            throw new IllegalStateException("no image");
        }
        int[] pixels = new int[size * size];
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, size, size, pixels, 0, size);
        if (!grabber.grabPixels(10_000)) {
            throw new IllegalStateException("image not loaded, status " + grabber.getStatus());
        }
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, size, size, pixels, 0, size);
        return result;
    }

    // ---------------------------------------------------------------------------------------------------- editors

    private static List<Component> editorChecks(List<Check> checks) {
        checks.add(Checks.expect("findEditor : int boolean long double float short byte",
                "IntegerEditor BooleanEditor LongEditor DoubleEditor FloatEditor ShortEditor ByteEditor", () -> String.join(
                        " ", List.of(int.class, boolean.class, long.class, double.class, float.class, short.class,
                                byte.class).stream().map(t -> editorName(PropertyEditorManager.findEditor(t))).toList())));
        checks.add(Checks.expect("findEditor : Integer String Color Font enum Object",
                "IntegerEditor StringEditor ColorEditor FontEditor EnumEditor none", () -> String.join(" ",
                        List.of(Integer.class, String.class, Color.class, Font.class, PrintJobBean.Orientation.class,
                                Object.class).stream().map(t -> editorName(PropertyEditorManager.findEditor(t))).toList())));
        checks.add(Checks.expect("PropertyEditorManager.getEditorSearchPath()", "[sun.beans.editors]",
                () -> Arrays.toString(PropertyEditorManager.getEditorSearchPath())));
        checks.add(Checks.expect("int editor : setAsText(\"42\"), value, Java initialization", "42, 42", () -> {
            PropertyEditor editor = PropertyEditorManager.findEditor(int.class);
            editor.setAsText("42");
            return editor.getValue() + ", " + editor.getJavaInitializationString();
        }));
        checks.add(Checks.expect("long / float / double / byte / short editors : Java initialization",
                "5L / 1.5F / 2.25 / ((byte)7) / ((short)9)", () -> {
                    List<String> strings = new ArrayList<>();
                    for (Object[] pair : new Object[][] { { long.class, 5L }, { float.class, 1.5f },
                            { double.class, 2.25 }, { byte.class, (byte) 7 }, { short.class, (short) 9 } }) {
                        PropertyEditor editor = PropertyEditorManager.findEditor((Class<?>) pair[0]);
                        editor.setValue(pair[1]);
                        strings.add(editor.getJavaInitializationString());
                    }
                    return String.join(" / ", strings);
                }));
        checks.add(Checks.expect("boolean editor : tags, setAsText(\"False\")", "[True, False], false, false", () -> {
            PropertyEditor editor = PropertyEditorManager.findEditor(boolean.class);
            editor.setAsText("False");
            return Arrays.toString(editor.getTags()) + ", " + editor.getValue() + ", "
                    + editor.getJavaInitializationString();
        }));
        checks.add(Checks.expect("String editor : Java initialization of a\"b\\n", "\"a\\\"b\\n\"", () -> {
            PropertyEditor editor = PropertyEditorManager.findEditor(String.class);
            editor.setValue("a\"b\n");
            return editor.getJavaInitializationString();
        }));
        checks.add(Checks.expect("enum editor : tags, setAsText(\"LANDSCAPE\")", "[PORTRAIT, LANDSCAPE], LANDSCAPE, "
                + PrintJobBean.Orientation.class.getName() + ".LANDSCAPE", () -> {
                    PropertyEditor editor = PropertyEditorManager.findEditor(PrintJobBean.Orientation.class);
                    editor.setAsText("LANDSCAPE");
                    return Arrays.toString(editor.getTags()) + ", " + editor.getValue() + ", "
                            + editor.getJavaInitializationString();
                }));
        PropertyEditor color = PropertyEditorManager.findEditor(Color.class);
        color.setValue(new Color(255, 128, 0));
        checks.add(Checks.expect("Color editor : text, Java initialization, paintable, custom editor",
                "255,128,0, new java.awt.Color(-32768,true), true, true", () -> color.getAsText() + ", "
                        + color.getJavaInitializationString() + ", " + color.isPaintable() + ", "
                        + color.supportsCustomEditor()));
        checks.add(Checks.expect("Color editor : paintValue pixel", "#FFFF8000", () -> {
            BufferedImage image = Snapshots.offscreen(20, 10, g -> color.paintValue(g, new java.awt.Rectangle(0, 0, 20, 10)));
            return Checks.argb(image.getRGB(10, 5));
        }));
        PropertyEditor font = PropertyEditorManager.findEditor(Font.class);
        font.setValue(new Font(Font.SERIF, Font.BOLD, 18));
        checks.add(Checks.expect("Font editor : Java initialization, text",
                "new java.awt.Font(\"Serif\", 1, 18), Serif BOLD 18",
                () -> font.getJavaInitializationString() + ", " + font.getAsText()));
        // an application editor, registered then found (instantiated by reflection)
        // unregistered, the editor is still found by its name : <type name>Editor
        checks.add(Checks.expect("registerEditor(Ticket, TicketEditor) : find, setAsText, Java initialization; unregistered",
                "TicketEditor, Q-002/14, new " + Ticket.class.getName() + "(\"Q-002\", 14), then TicketEditor", () -> {
                    PropertyEditorManager.registerEditor(Ticket.class, TicketEditor.class);
                    String result;
                    try {
                        PropertyEditor editor = PropertyEditorManager.findEditor(Ticket.class);
                        editor.setAsText("Q-002/14");
                        result = editorName(editor) + ", " + editor.getValue() + ", " + editor.getJavaInitializationString();
                    } finally {
                        PropertyEditorManager.registerEditor(Ticket.class, null);
                    }
                    return result + ", then " + editorName(PropertyEditorManager.findEditor(Ticket.class));
                }));
        checks.add(Checks.expect("PropertyDescriptor.createPropertyEditor (TicketBeanInfo)",
                "TicketEditor [Q-001/12, Q-002/14]",
                () -> {
                    PropertyDescriptor code = Introspector.getBeanInfo(Ticket.class).getPropertyDescriptors()[0];
                    PropertyEditor editor = code.createPropertyEditor(new Ticket());
                    return editorName(editor) + " " + Arrays.toString(editor.getTags());
                }));
        return List.of(color.getCustomEditor(), font.getCustomEditor());
    }

    private static String editorName(PropertyEditor editor) {
        return editor == null ? "none" : editor.getClass().getSimpleName();
    }

    // ----------------------------------------------------------------------------------------------------- events

    private static String event(PropertyChangeEvent e) {
        String index = e instanceof IndexedPropertyChangeEvent indexed ? "[" + indexed.getIndex() + "]" : "";
        return e.getPropertyName() + index + " " + e.getOldValue() + " -> " + e.getNewValue();
    }

    private static void eventChecks(List<Check> checks) {
        checks.add(Checks.expect("bound properties : all listeners / title listener",
                "title Untitled -> Report, duplex false -> true, orientation PORTRAIT -> LANDSCAPE / title Untitled -> Report",
                () -> {
                    PrintJobBean bean = new PrintJobBean();
                    List<String> all = new ArrayList<>();
                    List<String> title = new ArrayList<>();
                    bean.addPropertyChangeListener(e -> all.add(event(e)));
                    bean.addPropertyChangeListener("title", e -> title.add(event(e)));
                    bean.setTitle("Report");
                    bean.setTitle("Report"); // same value : no event
                    bean.setDuplex(true);
                    bean.setOrientation(PrintJobBean.Orientation.LANDSCAPE);
                    return String.join(", ", all) + " / " + String.join(", ", title);
                }));
        checks.add(Checks.expect("indexed property : IndexedPropertyChangeEvent", "pages[1] 2 -> 5", () -> {
            PrintJobBean bean = new PrintJobBean();
            List<String> changes = new ArrayList<>();
            bean.addPropertyChangeListener(e -> changes.add(event(e)));
            bean.setPages(1, 5);
            return String.join(", ", changes);
        }));
        checks.add(Checks.expect("getPropertyChangeListeners()", "PropertyChangeListener, PropertyChangeListenerProxy(title)",
                () -> {
                    PrintJobBean bean = new PrintJobBean();
                    bean.addPropertyChangeListener(e -> {
                    });
                    bean.addPropertyChangeListener("title", e -> {
                    });
                    List<String> kinds = new ArrayList<>();
                    for (PropertyChangeListener l : bean.getPropertyChangeListeners()) {
                        kinds.add(l instanceof PropertyChangeListenerProxy proxy
                                ? "PropertyChangeListenerProxy(" + proxy.getPropertyName() + ")"
                                : "PropertyChangeListener");
                    }
                    return String.join(", ", kinds);
                }));
        // a veto : the listeners already notified receive the revert event (old and new values swapped)
        checks.add(Checks.expect("constrained property : accepted, proposed, reverted, vetoed, value",
                "copies 1 -> 5, copies 5 -> 0, copies 0 -> 5, veto copies 5 -> 0 : at least one copy, copies 5", () -> {
                    PrintJobBean bean = new PrintJobBean();
                    List<String> log = new ArrayList<>();
                    bean.addVetoableChangeListener(e -> log.add(event(e)));
                    bean.addVetoableChangeListener(e -> {
                        if (((Integer) e.getNewValue()) < 1) {
                            throw new PropertyVetoException("at least one copy", e);
                        }
                    });
                    bean.setCopies(5);
                    try {
                        bean.setCopies(0);
                    } catch (PropertyVetoException e) {
                        log.add("veto " + event(e.getPropertyChangeEvent()) + " : " + e.getMessage());
                    }
                    log.add("copies " + bean.getCopies());
                    return String.join(", ", log);
                }));
    }

    private static Panel eventHandlerChecks(List<Check> checks) {
        Panel panel = new Panel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 6));
        Button button = new Button("Print report");
        Label label = new Label("(no event yet)");
        panel.add(button);
        panel.add(label);
        // label.setText(event.getSource().getLabel()) : a dynamic proxy invoking by reflection
        ActionListener listener = EventHandler.create(ActionListener.class, label, "text", "source.label");
        button.addActionListener(listener);
        checks.add(Checks.expect("EventHandler.create(ActionListener, label, \"text\", \"source.label\")",
                "proxy true, handler text/source.label/null, label Print report", () -> {
                    button.dispatchEvent(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "print"));
                    EventHandler handler = (EventHandler) Proxy.getInvocationHandler(listener);
                    return "proxy " + Proxy.isProxyClass(listener.getClass()) + ", handler " + handler.getAction() + "/"
                            + handler.getEventPropertyName() + "/" + handler.getListenerMethodName() + ", label "
                            + label.getText();
                }));
        checks.add(Checks.expect("EventHandler.create(JobListener, bean, \"title\", \"what\", \"jobStarted\")",
                "started Report", () -> {
                    PrintJobBean bean = new PrintJobBean();
                    bean.setTitle("Report");
                    bean.addJobListener(EventHandler.create(PrintJobBean.JobListener.class, bean, "title", "what",
                            "jobStarted"));
                    bean.start();
                    return bean.getTitle();
                }));
        checks.add(Checks.expect("EventHandler.create(PropertyChangeListener, target, \"title\", \"newValue\")",
                "Copied", () -> {
                    PrintJobBean source = new PrintJobBean();
                    PrintJobBean target = new PrintJobBean();
                    source.addPropertyChangeListener("title",
                            EventHandler.create(PropertyChangeListener.class, target, "title", "newValue"));
                    source.setTitle("Copied");
                    return target.getTitle();
                }));
        return panel;
    }

    // ------------------------------------------------------------------------------------------------- invocation

    private static void invocationChecks(List<Check> checks) {
        checks.add(Checks.expect("Expression(point, \"getX\")", "3.0", () -> new Expression(new Point(3, 4), "getX",
                new Object[0]).getValue()));
        checks.add(Checks.expect("Expression(Color.class, \"new\", 255, 0, 0)", "java.awt.Color[r=255,g=0,b=0]",
                () -> new Expression(Color.class, "new", new Object[] { 255, 0, 0 }).getValue()));
        checks.add(Checks.expect("Expression(Integer.class, \"parseInt\", \"42\") / (Math.class, \"max\", 3, 7)",
                "42 / 7", () -> new Expression(Integer.class, "parseInt", new Object[] { "42" }).getValue() + " / "
                        + new Expression(Math.class, "max", new Object[] { 3, 7 }).getValue()));
        checks.add(Checks.expect("Expression(int[], \"get\", 1), Statement(int[], \"set\", 1, 9)", "2, [1, 9, 3]", () -> {
            int[] array = { 1, 2, 3 };
            Object value = new Expression(array, "get", new Object[] { 1 }).getValue();
            new Statement(array, "set", new Object[] { 1, 9 }).execute();
            return value + ", " + Arrays.toString(array);
        }));
        checks.add(Checks.expect("Statement(bean, \"setCopies\", 3), Expression(bean, \"getSummary\")",
                "Untitled x3 PORTRAIT pages [1, 2, 3]", () -> {
                    PrintJobBean bean = new PrintJobBean();
                    new Statement(bean, "setCopies", new Object[] { 3 }).execute();
                    return new Expression(bean, "getSummary", new Object[0]).getValue();
                }));
        checks.add(Checks.expect("Statement.toString()", "Point.setLocation(Integer, Integer);",
                () -> new Statement(new Point(), "setLocation", new Object[] { 1, 2 }).toString()));
        checks.add(Checks.expect("Statement(label, \"noSuchMethod\")", "java.lang.NoSuchMethodException", () -> {
            try {
                new Statement(new Label(), "noSuchMethod", new Object[0]).execute();
                return "executed";
            } catch (Exception e) {
                return e.getClass().getName();
            }
        }));
        ClassLoader loader = BeansIntrospectionPage.class.getClassLoader();
        checks.add(Checks.expect("Beans.instantiate(loader, \"java.awt.Button\")", "Button \"\"", () -> {
            Object bean = Beans.instantiate(loader, "java.awt.Button");
            return bean.getClass().getSimpleName() + " \"" + ((Button) bean).getLabel() + "\"";
        }));
        checks.add(Checks.expect("Beans.instantiate(loader, PrintJobBean)", "Untitled x1 PORTRAIT pages [1, 2, 3]",
                () -> ((PrintJobBean) Beans.instantiate(loader, PrintJobBean.class.getName())).getSummary()));
        // a serialized bean : the resource showcase/print-misc/beans/greeting.ser, deserialized
        checks.add(Checks.expect("Beans.instantiate(loader, \"showcase.print-misc.beans.greeting\")",
                "SerializedGreeting Hello from a serialized bean x3", () -> {
                    SerializedGreeting greeting = (SerializedGreeting) Beans.instantiate(loader,
                            "showcase.print-misc.beans.greeting");
                    return greeting.getClass().getSimpleName() + " " + greeting.getText() + " x" + greeting.getCount();
                }));
        checks.add(Checks.expect("Beans.instantiate(loader, \"no.such.Bean\")", "java.lang.ClassNotFoundException", () -> {
            try {
                return Beans.instantiate(loader, "no.such.Bean");
            } catch (ClassNotFoundException e) {
                return e.getClass().getName();
            }
        }));
        checks.add(Checks.expect("Beans.isDesignTime(), isGuiAvailable(), isInstanceOf(button, Component)",
                "false, true, true", () -> Beans.isDesignTime() + ", " + Beans.isGuiAvailable() + ", "
                        + Beans.isInstanceOf(new Button(), Component.class)));
    }
}
