package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.AWTKeyStroke;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.List;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Focus;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.RobotSession;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * The focus subsystem of AWT : the traversal policies ({@link ContainerOrderFocusTraversalPolicy},
 * {@link DefaultFocusTraversalPolicy} which asks the native peers whether they are focusable, a custom policy), focus
 * cycle roots (down and up cycles), focus traversal keys, {@code requestFocusInWindow} results, a
 * {@link KeyEventDispatcher} consuming a key, a {@link KeyEventPostProcessor}, a {@link VetoableChangeListener} vetoing
 * a focus change, and a temporary focus loss to another window.
 * <p>
 * The policy computations need no focus (checked in every run) ; the focus changes are real (a {@code needsFocus}
 * page) : programmatic ({@code focusNextComponent}, cycles) and Robot key presses (Tab, Shift+Tab, a custom traversal
 * key), sent only while a showcase window is focused. The component names are those of the focus log.
 */
@Singleton
public class AwtFocusPage implements FeaturePage {

    // per build state
    private Components c;
    private ChecksView policyView;
    private ChecksView focusView;
    private Container logHolder;
    private KeyEventDispatcher dispatcher;
    private KeyEventPostProcessor postProcessor;
    private VetoableChangeListener veto;
    private Frame otherFrame;

    /** The components of one build. */
    private static final class Components {
        Container root;
        Button b1;
        TextField t1;
        Label l1;
        Checkbox c1;
        Choice ch1;
        List li1;
        Button disabled;
        Button notFocusable;
        Canvas canvas;
        Panel cycle;
        Button in1;
        TextField in2;
        Button in3;
        TextArea ta;
        Button last;
        final java.util.List<String> log = Collections.synchronizedList(new ArrayList<>());
        volatile boolean recording;
    }

    @Override
    public String id() {
        return "awt-focus";
    }

    @Override
    public String title() {
        return "Focus traversal";
    }

    @Override
    public String category() {
        return Categories.AWT;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public boolean needsFocus() {
        return true;
    }

    @Override
    public Component build() {
        c = new Components();
        c.root = focusPanel(c);
        policyView = ChecksView.table("Traversal policies (computed, no focus needed)", java.util.List.of(Check.info("state",
                "pending")));
        focusView = ChecksView.table("Focus changes", java.util.List.of(Check.info("state", "pending")));
        logHolder = Ui.column(0, AwtSupport.log(java.util.List.of("(pending)"), 1000));
        return Ui.column(12,
                Ui.text("A focus cycle root of heavyweight components (names as in the logs). The Panel \"cycle\" is a "
                        + "nested focus cycle root with a reversed policy. Both cycle roots are focusable (AWT leaves a "
                        + "traversal root that cannot own the focus) : they are stops of the traversal. \"b2\" is disabled, \"b3\" is not focusable, "
                        + "the Canvas \"cv\" uses the Right arrow as its forward traversal key.", 1000),
                AwtSupport.group("Focus cycle root (ContainerOrderFocusTraversalPolicy)", c.root),
                policyView, focusView, Ui.title("Focus log (FOCUS_GAINED / FOCUS_LOST of the components, cause)"),
                logHolder);
    }

    private static Container focusPanel(Components c) {
        Panel root = new Panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        root.setName("root");
        root.setBackground(new Color(AwtSupport.GROUP_BACKGROUND));
        root.setPreferredSize(new Dimension(1000, 120));
        root.setFocusCycleRoot(true);
        root.setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        c.b1 = named(new Button("b1"), "b1");
        c.t1 = named(new TextField("t1", 6), "t1");
        c.l1 = named(new Label("l1 (Label)"), "l1");
        c.c1 = named(new Checkbox("c1"), "c1");
        c.ch1 = named(new Choice(), "ch1");
        c.ch1.add("ch1");
        c.li1 = named(new List(2), "li1");
        c.li1.add("li1");
        c.disabled = named(new Button("b2 (disabled)"), "b2");
        c.disabled.setEnabled(false);
        c.notFocusable = named(new Button("b3 (not focusable)"), "b3");
        c.notFocusable.setFocusable(false);
        c.canvas = named(new Canvas() {
            @Override
            public void paint(Graphics g) {
                g.setColor(new Color(0x546E7A));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.WHITE);
                g.drawString("cv", 8, 18);
            }
        }, "cv");
        c.canvas.setPreferredSize(new Dimension(40, 26));
        c.canvas.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                Set.of(AWTKeyStroke.getAWTKeyStroke("pressed RIGHT")));
        c.cycle = named(new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2)), "cycle");
        c.cycle.setBackground(new Color(0xFFE0B2));
        c.cycle.setFocusCycleRoot(true);
        c.cycle.setFocusTraversalPolicy(new ReversePolicy());
        c.in1 = named(new Button("in1"), "in1");
        c.in2 = named(new TextField("in2", 4), "in2");
        c.in3 = named(new Button("in3"), "in3");
        c.cycle.add(c.in1);
        c.cycle.add(c.in2);
        c.cycle.add(c.in3);
        c.ta = named(new TextArea("ta", 2, 8, TextArea.SCROLLBARS_NONE), "ta");
        c.last = named(new Button("last"), "last");
        for (Component component : new Component[] { c.b1, c.t1, c.l1, c.c1, c.ch1, c.li1, c.disabled, c.notFocusable,
                c.canvas, c.cycle, c.ta, c.last }) {
            root.add(component);
        }
        FocusAdapter recorder = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                record(c, e);
            }

            @Override
            public void focusLost(FocusEvent e) {
                record(c, e);
            }
        };
        for (Component component : new Component[] { root, c.b1, c.t1, c.l1, c.c1, c.ch1, c.li1, c.canvas, c.cycle, c.in1,
                c.in2, c.in3, c.ta, c.last }) {
            component.addFocusListener(recorder);
        }
        return root;
    }

    private static void record(Components c, FocusEvent e) {
        if (c.recording) {
            c.log.add((e.getID() == FocusEvent.FOCUS_GAINED ? "GAINED " : "LOST ") + e.getComponent().getName() + " "
                    + e.getCause() + (e.isTemporary() ? " temporary" : ""));
        }
    }

    private static <T extends Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }

    /**
     * A policy traversing the children of its cycle root in reverse order.
     */
    static final class ReversePolicy extends FocusTraversalPolicy {

        private java.util.List<Component> order(Container root) {
            java.util.List<Component> order = new ArrayList<>();
            for (Component child : root.getComponents()) {
                if (child.isFocusable() && child.isEnabled() && child.isVisible()) {
                    order.add(0, child);
                }
            }
            return order;
        }

        @Override
        public Component getComponentAfter(Container root, Component component) {
            java.util.List<Component> order = order(root);
            int index = order.indexOf(component);
            return order.isEmpty() ? null : order.get((index + 1) % order.size());
        }

        @Override
        public Component getComponentBefore(Container root, Component component) {
            java.util.List<Component> order = order(root);
            int index = order.indexOf(component);
            return order.isEmpty() ? null : order.get((index - 1 + order.size()) % order.size());
        }

        @Override
        public Component getFirstComponent(Container root) {
            java.util.List<Component> order = order(root);
            return order.isEmpty() ? null : order.getFirst();
        }

        @Override
        public Component getLastComponent(Container root) {
            java.util.List<Component> order = order(root);
            return order.isEmpty() ? null : order.getLast();
        }

        @Override
        public Component getDefaultComponent(Container root) {
            return getFirstComponent(root);
        }
    }

    // ----------------------------------------------------------------------------------------------------- policies

    private static java.util.List<Check> policyChecks(Components c) {
        java.util.List<Check> checks = new ArrayList<>();
        ContainerOrderFocusTraversalPolicy containerOrder = new ContainerOrderFocusTraversalPolicy();
        checks.add(Checks.expect("ContainerOrderFocusTraversalPolicy : cycle from b1",
                "b1 t1 l1 c1 ch1 li1 cv cycle >in3", () -> cycle(containerOrder, c.root, c.b1)));
        // a focusable focus cycle root is the first component of its own traversal
        checks.add(Checks.expect("ContainerOrder : first / last / default / before b1", "root / last / root / root",
                () -> name(containerOrder.getFirstComponent(c.root)) + " / " + name(containerOrder.getLastComponent(c.root))
                        + " / " + name(containerOrder.getDefaultComponent(c.root)) + " / "
                        + name(containerOrder.getComponentBefore(c.root, c.b1))));
        checks.add(Checks.expect("ContainerOrder after the nested cycle root", "in3 last root",
                () -> name(containerOrder.getComponentAfter(c.root, c.cycle)) + " " + name(containerOrder.getComponentAfter(c.root,
                        c.ta)) + " " + name(containerOrder.getComponentAfter(c.root, c.last))));
        checks.add(Checks.expect("ContainerOrder without implicit down cycle", "b1 t1 l1 c1 ch1 li1 cv cycle ta last root", () -> {
            ContainerOrderFocusTraversalPolicy flat = new ContainerOrderFocusTraversalPolicy();
            flat.setImplicitDownCycleTraversal(false);
            return cycle(flat, c.root, c.b1);
        }));
        // DefaultFocusTraversalPolicy asks the peers (a Label peer is not focusable)
        DefaultFocusTraversalPolicy defaultPolicy = new DefaultFocusTraversalPolicy();
        checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("DefaultFocusTraversalPolicy (peer focusability)",
                "b1 t1 c1 ch1 li1 >in3", () -> cycle(defaultPolicy, c.root, c.b1))));
        checks.add(Checks.expect("ReversePolicy of the nested cycle root", "in3 in2 in1",
                () -> cycle(c.cycle.getFocusTraversalPolicy(), c.cycle, c.in3)));
        checks.add(Checks.expect("focus cycle roots (in1, cycle, b1)", "cycle / root / root",
                () -> name(c.in1.getFocusCycleRootAncestor()) + " / " + name(c.cycle.getFocusCycleRootAncestor()) + " / "
                        + name(c.b1.getFocusCycleRootAncestor())));
        checks.add(Checks.expect("isFocusCycleRoot / isFocusTraversalPolicySet", "true true / true true",
                () -> c.root.isFocusCycleRoot() + " " + c.cycle.isFocusCycleRoot() + " / " + c.root.isFocusTraversalPolicySet()
                        + " " + c.cycle.isFocusTraversalPolicySet()));
        checks.add(Checks.expect("KeyboardFocusManager default forward / backward traversal keys",
                "[ctrl pressed TAB, pressed TAB] / [shift ctrl pressed TAB, shift pressed TAB]", () -> {
                    KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                    return keys(kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)) + " / "
                            + keys(kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
                }));
        checks.add(Checks.info("traversal keys of b1 (inherited from its ancestors)", () -> keys(c.b1.getFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS))));
        checks.add(Checks.expect("custom forward traversal keys of the Canvas", "[pressed RIGHT]",
                () -> keys(c.canvas.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS))));
        checks.add(Checks.expect("isFocusable (b1, l1, b3, cv)", "true true false true", () -> c.b1.isFocusable() + " "
                + c.l1.isFocusable() + " " + c.notFocusable.isFocusable() + " " + c.canvas.isFocusable()));
        return checks;
    }

    /**
     * The traversal of {@code root} by {@code policy} from its first component, until it cycles or enters a nested focus
     * cycle root (shown as {@code >}, the nested policy takes over there).
     */
    private static String cycle(FocusTraversalPolicy policy, Container root, Component first) {
        java.util.List<String> names = new ArrayList<>();
        Set<Component> seen = new LinkedHashSet<>();
        Component component = first;
        while (component != null && seen.add(component) && seen.size() < 40) {
            if (component.getFocusCycleRootAncestor() != root && component != root) {
                names.add(">" + name(component));
                break;
            }
            names.add(name(component));
            component = policy.getComponentAfter(root, component);
        }
        return String.join(" ", names);
    }

    private static String name(Component component) {
        return component == null ? "null" : component.getName();
    }

    private static String keys(Set<AWTKeyStroke> keys) {
        Set<String> names = new TreeSet<>();
        for (AWTKeyStroke key : keys) {
            names.add(key.toString());
        }
        return names.toString();
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        Components comps = c;
        return Edt.rounds(2).thenCompose(v -> {
            // the peers exist : DefaultFocusTraversalPolicy can ask them
            policyView.setChecks(policyChecks(comps));
            if (!ShowcaseMode.snapshot() || !ShowcaseMode.realInput()) {
                focusView.setChecks(java.util.List.of(Check.info("focus changes", "skipped: snapshot mode only")));
                return CompletableFuture.completedFuture(null);
            }
            // the page window focused, this process owning the foreground
            return Focus.acquire(RobotSession.windowOf(comps.b1)).thenCompose(attempts -> {
                if (attempts == 0) {
                    focusView.setChecks(java.util.List.of(Check.info("focus changes", "skipped: not focused")));
                    return CompletableFuture.completedFuture(null);
                }
                return focusChanges(comps);
            });
        });
    }

    private CompletionStage<Void> focusChanges(Components comps) {
        java.util.List<Check> checks = new ArrayList<>();
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        java.util.List<String> dispatched = Collections.synchronizedList(new ArrayList<>());
        java.util.List<String> postProcessed = Collections.synchronizedList(new ArrayList<>());
        java.util.List<String> canvasKeys = Collections.synchronizedList(new ArrayList<>());
        dispatcher = e -> {
            if (e.getKeyCode() == KeyEvent.VK_F7 && e.getComponent() == comps.canvas) {
                dispatched.add(AwtSupport.idName(e));
                return true;
            }
            return false;
        };
        postProcessor = e -> {
            if (e.getKeyCode() == KeyEvent.VK_F9 && e.getComponent() == comps.canvas) {
                postProcessed.add(AwtSupport.idName(e));
            }
            return false;
        };
        veto = (PropertyChangeEvent event) -> {
            if ("focusOwner".equals(event.getPropertyName()) && event.getNewValue() == comps.in2) {
                throw new PropertyVetoException("in2 is vetoed", event);
            }
        };
        kfm.addKeyEventDispatcher(dispatcher);
        kfm.addKeyEventPostProcessor(postProcessor);
        comps.canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                canvasKeys.add(KeyEvent.getKeyText(e.getKeyCode()));
            }
        });

        comps.log.clear();
        comps.recording = true;
        // a disabled component accepts the request (Component.isRequestFocusAccepted does not look at isEnabled)
        checks.add(Checks.expect("requestFocusInWindow (b3 not focusable, b2 disabled)", "false true",
                () -> comps.notFocusable.requestFocusInWindow() + " " + comps.disabled.requestFocusInWindow()));
        CompletionStage<Void> chain = focus(comps.b1, () -> comps.b1.requestFocusInWindow());
        // programmatic traversal : KeyboardFocusManager.focusNextComponent / focusPreviousComponent
        for (int i = 0; i < 3; i++) {
            chain = chain.thenCompose(v -> next(kfm, kfm::focusNextComponent));
        }
        chain = chain.thenCompose(v -> focus(comps.b1, () -> comps.b1.requestFocusInWindow()))
                .thenCompose(v -> next(kfm, kfm::focusPreviousComponent))
                .thenAccept(v -> {
                    java.util.List<String> gained = gained(comps);
                    checks.add(Checks.expect("focusNextComponent x3 from b1, then focusPreviousComponent from b1",
                            "b1 UNKNOWN, t1 TRAVERSAL_FORWARD, l1 TRAVERSAL_FORWARD, c1 TRAVERSAL_FORWARD, b1 UNKNOWN, "
                                    + "root TRAVERSAL_BACKWARD",
                            () -> String.join(", ", gained)));
                    comps.log.clear();
                })
                // cycles : down into the nested cycle root, up out of it
                .thenCompose(v -> focus(comps.in3, () -> {
                    kfm.downFocusCycle(comps.cycle);
                    return true;
                }))
                .thenAccept(v -> checks.add(Checks.expect("downFocusCycle(cycle) : focus owner / current cycle root",
                        "in3 / cycle", () -> name(kfm.getFocusOwner()) + " / " + name(kfm.getCurrentFocusCycleRoot()))))
                .thenCompose(v -> {
                    kfm.upFocusCycle(comps.in3);
                    return Edt.rounds(3).thenCompose(r -> Edt.delay(100));
                })
                .thenAccept(v -> checks.add(Checks.expect("upFocusCycle(in3) : current focus cycle root", "root",
                        () -> name(kfm.getCurrentFocusCycleRoot()))))
                // veto
                .thenCompose(v -> focus(comps.in1, () -> comps.in1.requestFocusInWindow()))
                .thenCompose(v -> {
                    kfm.addVetoableChangeListener(veto);
                    boolean accepted = comps.in2.requestFocusInWindow();
                    return Edt.delay(300).thenApply(d -> accepted);
                })
                .thenAccept(accepted -> {
                    kfm.removeVetoableChangeListener(veto);
                    checks.add(Checks.expect("VetoableChangeListener vetoes focusOwner = in2 : focus owner", "in1",
                            () -> name(kfm.getFocusOwner())));
                    checks.add(Check.info("requestFocusInWindow(in2) result while vetoed", accepted));
                    comps.log.clear();
                })
                // Robot : Tab, Shift+Tab, the custom traversal key, keys for the dispatcher and the post processor
                .thenCompose(v -> robotTabs(comps, checks, 1))
                .thenCompose(v -> robotKeys(comps, checks, dispatched, postProcessed, canvasKeys, 1))
                .thenCompose(v -> temporaryFocus(comps, checks))
                .whenComplete((v, error) -> {
                    comps.recording = false;
                    if (error != null) {
                        checks.add(Check.fail("focus changes", Checks.describe(error)));
                    }
                    focusView.setChecks(checks);
                    java.util.List<String> log = new ArrayList<>(comps.log);
                    logHolder.removeAll();
                    logHolder.add(AwtSupport.log(log, 1000));
                    logHolder.invalidate();
                    Snapshots.layout(logHolder);
                });
        return chain;
    }

    /**
     * The focus moves to a component of another window : the canvas loses it temporarily.
     */
    private CompletionStage<Void> temporaryFocus(Components comps, java.util.List<Check> checks) {
        comps.log.clear();
        Window main = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        return focus(comps.last, () -> comps.last.requestFocusInWindow()).thenCompose(v -> {
            comps.log.clear();
            otherFrame = new Frame("Other window");
            TextField field = named(new TextField("other", 10), "other");
            otherFrame.add(field);
            otherFrame.pack();
            Rectangle area = AwtSupport.secondaryArea(otherFrame.getWidth(), otherFrame.getHeight());
            otherFrame.setLocation(area.x, area.y);
            otherFrame.setVisible(true);
            otherFrame.toFront();
            field.requestFocus();
            return Edt.until(field::isFocusOwner, 3000, "other window focused").handle((r, e) -> e == null);
        }).thenCompose(focused -> {
            java.util.List<String> lost = new ArrayList<>(comps.log);
            checks.add(focused ? Checks.expect("focus moved to another window : FOCUS_LOST of the component",
                    "LOST last ACTIVATION temporary", () -> String.join(", ", lost))
                    : Check.info("focus moved to another window", "skipped: the window was not focused"));
            comps.log.clear();
            // the focus goes back to the page window before the other window is disposed (Windows would otherwise
            // activate the next window in the z-order, maybe of another application)
            if (main != null) {
                main.toFront();
            }
            comps.last.requestFocus();
            return Edt.until(comps.last::isFocusOwner, 3000, "focus back").handle((r, e) -> e == null)
                    .thenApply(back -> {
                        otherFrame.dispose();
                        otherFrame = null;
                        return back;
                    });
        }).thenAccept(back -> checks.add(back ? Checks.expect("back to the page window : FOCUS_GAINED",
                "GAINED last ACTIVATION", () -> String.join(", ", comps.log))
                : Check.info("back to the page window : FOCUS_GAINED", "skipped: the page window was not focused again")));
    }

    /** Attempts of each Robot sequence : another application may take the foreground at any time. */
    private static final int ATTEMPTS = 3;
    private static final String EXPECTED_TABS = "b1 UNKNOWN, t1 TRAVERSAL_FORWARD, l1 TRAVERSAL_FORWARD, "
            + "t1 TRAVERSAL_BACKWARD";

    /**
     * Robot Tab, Tab, Shift+Tab from b1, again (at most {@link #ATTEMPTS} times, the window focused again first) while
     * the traversal is incomplete.
     */
    private static CompletionStage<Void> robotTabs(Components comps, java.util.List<Check> checks, int attempt) {
        // b1 gains the focus by request (UNKNOWN), then by traversal
        CompletionStage<Void> start = comps.b1.isFocusOwner()
                ? focus(comps.in1, () -> comps.in1.requestFocusInWindow())
                : CompletableFuture.completedFuture(null);
        return start.thenCompose(v -> {
            comps.log.clear();
            return focus(comps.b1, () -> comps.b1.requestFocusInWindow());
        }).thenCompose(v -> Edt.background(() -> {
            try (RobotSession robot = RobotSession.open()) {
                if (!robot.ensureFocus(comps.b1)) {
                    return false;
                }
                boolean sent = true;
                for (int[] keys : new int[][] { { KeyEvent.VK_TAB }, { KeyEvent.VK_TAB },
                        { KeyEvent.VK_SHIFT, KeyEvent.VK_TAB } }) {
                    sent &= robot.key(keys);
                    robot.delay(150);
                }
                return sent;
            }
        })).thenCompose(sent -> Edt.rounds(3).thenApply(v -> sent)).thenCompose(sent -> {
            java.util.List<String> gained = gained(comps);
            if (!String.join(", ", gained).equals(EXPECTED_TABS) && attempt < ATTEMPTS) {
                return robotTabs(comps, checks, attempt + 1);
            }
            checks.add(Check.attempts("Robot Tab, Tab, Shift+Tab", attempt));
            checks.add(sent ? Checks.expect("Robot Tab, Tab, Shift+Tab from b1", EXPECTED_TABS,
                    () -> String.join(", ", gained))
                    : Check.info("Robot Tab, Tab, Shift+Tab from b1", "skipped: not focused"));
            comps.log.clear();
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Robot F7 (consumed by the key event dispatcher), F9 (seen by the post processor) and Right (the custom traversal
     * key of the Canvas), again (at most {@link #ATTEMPTS} times) while an event is missing.
     */
    private static CompletionStage<Void> robotKeys(Components comps, java.util.List<Check> checks,
            java.util.List<String> dispatched, java.util.List<String> postProcessed, java.util.List<String> canvasKeys,
            int attempt) {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        dispatched.clear();
        postProcessed.clear();
        canvasKeys.clear();
        return focus(comps.canvas, () -> comps.canvas.requestFocusInWindow())
                .thenCompose(v -> Edt.background(() -> {
                    try (RobotSession robot = RobotSession.open()) {
                        if (!robot.ensureFocus(comps.canvas)) {
                            return false;
                        }
                        boolean sent = robot.key(KeyEvent.VK_F7) && robot.key(KeyEvent.VK_F9);
                        robot.delay(100);
                        sent &= robot.key(KeyEvent.VK_RIGHT);
                        robot.delay(200);
                        return sent;
                    }
                }))
                .thenCompose(sent -> Edt.rounds(3).thenApply(v -> sent))
                .thenCompose(sent -> {
                    String keys = "dispatcher " + String.join(" ", dispatched) + " / canvas "
                            + String.join(" ", canvasKeys);
                    String post = String.join(" ", postProcessed);
                    String owner = name(kfm.getFocusOwner());
                    boolean complete = keys.equals("dispatcher KEY_PRESSED KEY_RELEASED / canvas F9")
                            && post.equals("KEY_PRESSED KEY_RELEASED") && owner.equals("cycle");
                    if (!complete && attempt < ATTEMPTS) {
                        return robotKeys(comps, checks, dispatched, postProcessed, canvasKeys, attempt + 1);
                    }
                    checks.add(Check.attempts("Robot F7, F9, Right", attempt));
                    if (!sent) {
                        checks.add(Check.info("Robot F7, F9, Right on the Canvas", "skipped: not focused"));
                        return CompletableFuture.completedFuture(null);
                    }
                    checks.add(Checks.expect("KeyEventDispatcher consumes F7 (the canvas never sees it)",
                            "dispatcher KEY_PRESSED KEY_RELEASED / canvas F9", () -> keys));
                    checks.add(Checks.expect("KeyEventPostProcessor sees the unconsumed F9", "KEY_PRESSED KEY_RELEASED",
                            () -> post));
                    checks.add(Checks.expect("custom forward traversal key (Right) of the Canvas : focus owner", "cycle",
                            () -> owner));
                    return CompletableFuture.completedFuture(null);
                });
    }

    private static java.util.List<String> gained(Components comps) {
        java.util.List<String> gained = new ArrayList<>();
        synchronized (comps.log) {
            for (String entry : comps.log) {
                if (entry.startsWith("GAINED ")) {
                    gained.add(entry.substring("GAINED ".length()));
                }
            }
        }
        return gained;
    }

    /**
     * Runs {@code request}, then waits until {@code target} owns the focus (at most 2 s).
     */
    private static CompletionStage<Void> focus(Component target, Supplier<Boolean> request) {
        request.get();
        return Edt.until(target::isFocusOwner, 2000, "focus on " + target.getName()).handle((v, e) -> null)
                .thenCompose(v -> Edt.rounds(2));
    }

    private static CompletionStage<Void> next(KeyboardFocusManager kfm, Runnable traversal) {
        Component before = kfm.getFocusOwner();
        traversal.run();
        return Edt.until(() -> kfm.getFocusOwner() != before && kfm.getFocusOwner() != null, 2000, "focus traversal")
                .handle((v, e) -> null).thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public void dispose(Component content) {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (dispatcher != null) {
            kfm.removeKeyEventDispatcher(dispatcher);
        }
        if (postProcessor != null) {
            kfm.removeKeyEventPostProcessor(postProcessor);
        }
        if (veto != null) {
            kfm.removeVetoableChangeListener(veto);
        }
        if (otherFrame != null) {
            otherFrame.dispose();
        }
        dispatcher = null;
        postProcessor = null;
        veto = null;
        otherFrame = null;
        c = null;
        policyView = null;
        focusView = null;
        logHolder = null;
    }
}
