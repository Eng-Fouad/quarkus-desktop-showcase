package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.AWTEvent;
import java.awt.AWTKeyStroke;
import java.awt.ActiveEvent;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.im.InputContext;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Focus;
import io.quarkiverse.desktop.showcase.core.Keys;
import io.quarkiverse.desktop.showcase.core.MacPreferences;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.RobotSession;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Event dispatch : Robot driven mouse (move, click, double click, drag, right click, wheel) and keyboard input on a
 * recording heavyweight Canvas (the events come from the native peer), a global {@code AWTEventListener}, event
 * coalescing ({@code Component.coalesceEvents} overridden : detected by reflection on the application class, and the
 * queue's own coalescing of {@code MOUSE_MOVED}), {@code ActiveEvent}, {@code InvocationEvent},
 * {@code invokeAndWait}, {@code SecondaryLoop}, a custom {@code EventQueue} pushed and popped, container, hierarchy and
 * component events, the 1.0 event model ({@code Component.mouseDown}), and the key stroke and key text utilities
 * ({@code AWTKeyStroke} parses key names through reflection on {@code KeyEvent}).
 * <p>
 * A {@code needsFocus} page : the Robot input is only sent in snapshot mode while a showcase window is focused, and
 * every key press is preceded by that check.
 */
@Singleton
public class AwtEventsPage implements FeaturePage {

    private static final int CANVAS_COLOR = 0x37474F;
    private static final int CANVAS_WIDTH = 460;
    private static final int CANVAS_HEIGHT = 200;
    /** Id of the custom events of the coalescing demo. */
    static final int CUSTOM_EVENT = AWTEvent.RESERVED_ID_MAX + 42;

    // per build state
    private RecordingCanvas canvas;
    private java.awt.Frame targetFrame;
    private Container robotLogHolder;
    private ChecksView dispatchView;
    private ChecksView loopView;
    private ChecksView robotView;
    private ChecksView keysView;
    private AWTEventListener globalListener;
    private final Map<String, BufferedImage> captures = new LinkedHashMap<>();

    @Override
    public String id() {
        return "awt-events";
    }

    @Override
    public String title() {
        return "Event dispatch";
    }

    @Override
    public String category() {
        return Categories.AWT;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean needsFocus() {
        return true;
    }

    @Override
    public Component build() {
        captures.clear();
        canvas = new RecordingCanvas();
        canvas.setName("recordingCanvas");
        canvas.setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
        robotLogHolder = Ui.column(0, AwtSupport.log(List.of("(pending)"), 520));
        dispatchView = ChecksView.table("Synthetic events, listeners, coalescing", List.of(Check.info("state", "pending")));
        loopView = ChecksView.table("Event queue : invocation, secondary loop, push / pop",
                List.of(Check.info("state", "pending")));
        robotView = ChecksView.table("Robot input on the canvas", List.of(Check.info("state", "pending")));
        keysView = ChecksView.table("Key strokes and key texts", keyChecks());
        return Ui.column(12,
                Ui.text("A copy of this canvas, in an always-on-top frame of the lower right part of the screen, records the "
                        + "events of its native peer while Robot moves, clicks, drags, scrolls and types on it (snapshot mode, "
                        + "only while that frame is focused, and every click only after a pixel probe proved the canvas is "
                        + "not covered). Event log (coordinates relative to the canvas) :", 1000),
                Ui.row(16, AwtSupport.group("Recording Canvas (heavyweight)", canvas), robotLogHolder),
                robotView, dispatchView, loopView, keysView);
    }

    /**
     * The heavyweight target of the Robot input : records every mouse, wheel, key and focus event.
     */
    static final class RecordingCanvas extends Canvas implements MouseListener, MouseMotionListener, MouseWheelListener,
            KeyListener, FocusListener {

        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        volatile boolean recording;

        RecordingCanvas() {
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this);
            addKeyListener(this);
            addFocusListener(this);
            setFocusable(true);
        }

        private void record(AWTEvent e) {
            if (recording) {
                if (Platforms.isMac()) {
                    String entry = AwtSupport.describe(e);
                    synchronized (log) {
                        if (!repeatedMove(e, entry, log.isEmpty() ? null : log.get(log.size() - 1))) {
                            log.add(entry);
                        }
                    }
                } else {
                    log.add(AwtSupport.describe(e));
                }
            }
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(new Color(CANVAS_COLOR));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(0xB0BEC5));
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            g.drawString("Robot input target (Canvas)", 10, getHeight() - 10);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            record(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            record(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            record(e);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            record(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            record(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            record(e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            record(e);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            record(e);
        }

        @Override
        public void keyTyped(KeyEvent e) {
            record(e);
        }

        @Override
        public void keyPressed(KeyEvent e) {
            record(e);
        }

        @Override
        public void keyReleased(KeyEvent e) {
            record(e);
        }

        @Override
        public void focusGained(FocusEvent e) {
            record(e);
        }

        @Override
        public void focusLost(FocusEvent e) {
            record(e);
        }
    }

    // --------------------------------------------------------------------------------------- coalescing components

    /**
     * A custom event carrying a count : merged by {@link CoalescingComponent#coalesceEvents}.
     */
    static final class CountEvent extends AWTEvent {

        final int count;

        CountEvent(Component source, int count) {
            super(source, CUSTOM_EVENT);
            this.count = count;
        }
    }

    /**
     * Overrides {@code coalesceEvents} : {@code Component} detects it by reflection
     * ({@code getDeclaredMethod("coalesceEvents", AWTEvent.class, AWTEvent.class)} on every application subclass) and
     * only then lets the event queue call it.
     */
    static class CoalescingComponent extends Component {

        final AtomicInteger coalesceCalls = new AtomicInteger();
        final List<Integer> dispatched = Collections.synchronizedList(new ArrayList<>());

        CoalescingComponent() {
            // new event model only : custom event ids are then delivered to processEvent
            enableEvents(0);
        }

        @Override
        protected AWTEvent coalesceEvents(AWTEvent existingEvent, AWTEvent newEvent) {
            if (existingEvent instanceof CountEvent a && newEvent instanceof CountEvent b) {
                coalesceCalls.incrementAndGet();
                return new CountEvent(this, a.count + b.count);
            }
            return super.coalesceEvents(existingEvent, newEvent);
        }

        @Override
        protected void processEvent(AWTEvent e) {
            if (e instanceof CountEvent count) {
                dispatched.add(count.count);
            } else {
                super.processEvent(e);
            }
        }
    }

    /**
     * The same component without {@code coalesceEvents} : every event is dispatched.
     */
    static class PlainComponent extends Component {

        final List<Integer> dispatched = Collections.synchronizedList(new ArrayList<>());

        PlainComponent() {
            enableEvents(0);
        }

        @Override
        protected void processEvent(AWTEvent e) {
            if (e instanceof CountEvent count) {
                dispatched.add(count.count);
            } else {
                super.processEvent(e);
            }
        }
    }

    /**
     * The 1.0 event model : no listener, {@code mouseDown} overridden (deprecated, still delivered).
     */
    @SuppressWarnings("deprecation")
    static class LegacyComponent extends Container {

        final List<String> calls = new ArrayList<>();

        @Override
        public boolean mouseDown(Event evt, int x, int y) {
            calls.add("mouseDown(" + x + ", " + y + ") id=" + evt.id + " clickCount=" + evt.clickCount);
            return true;
        }

        @Override
        public boolean keyDown(Event evt, int key) {
            calls.add("keyDown(" + key + ") id=" + evt.id);
            return true;
        }
    }

    /**
     * A component receiving mouse events without listener ({@code enableEvents} and {@code processMouseEvent}).
     */
    static class EnabledComponent extends Component {

        final List<String> calls = new ArrayList<>();

        EnabledComponent() {
            enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            calls.add(AwtSupport.idName(e));
            super.processMouseEvent(e);
        }
    }

    /**
     * An event queue pushed on top of the system queue : records the classes of the events it dispatches.
     */
    static final class RecordingQueue extends EventQueue {

        final List<String> recorded = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void dispatchEvent(AWTEvent event) {
            if (event.getSource() instanceof CoalescingComponent || event instanceof MarkerEvent) {
                recorded.add(event.getClass().getSimpleName());
            }
            super.dispatchEvent(event);
        }

        void popSelf() {
            pop();
        }
    }

    /**
     * An {@link ActiveEvent} : the event queue calls {@code dispatch()} itself.
     */
    static final class MarkerEvent extends AWTEvent implements ActiveEvent {

        final Runnable action;

        MarkerEvent(Object source, Runnable action) {
            super(source, AWTEvent.RESERVED_ID_MAX + 43);
            this.action = action;
        }

        @Override
        public void dispatch() {
            action.run();
        }
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        RecordingCanvas target = new RecordingCanvas();
        target.setName("targetCanvas");
        target.setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
        List<String> global = Collections.synchronizedList(new ArrayList<>());
        // macOS : the description of the last event of the target seen by the listener (on the EDT only)
        String[] lastGlobal = new String[1];
        globalListener = e -> {
            if (e.getSource() == target && target.recording) {
                if (Platforms.isMac()) {
                    String entry = AwtSupport.describe(e);
                    if (!repeatedMove(e, entry, lastGlobal[0])) {
                        global.add(AwtSupport.idName(e));
                    }
                    lastGlobal[0] = entry;
                } else {
                    global.add(AwtSupport.idName(e));
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(globalListener, AWTEvent.MOUSE_EVENT_MASK
                | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK
                | AWTEvent.FOCUS_EVENT_MASK);
        return dispatchChecks((Container) content)
                .thenCompose(v -> queueChecks())
                .thenCompose(v -> robot(target, global))
                .whenComplete((v, error) -> {
                    if (error != null) {
                        robotView.setChecks(List.of(Check.fail("events", Checks.describe(error))));
                    }
                });
    }

    // ---------------------------------------------------------------------------------------- synthetic dispatch

    private CompletionStage<Void> dispatchChecks(Container content) {
        List<Check> checks = new ArrayList<>();
        List<String> received = new ArrayList<>();
        Component target = new Component() {
        };
        target.setName("syntheticTarget");
        target.setBounds(0, 0, 50, 50);
        target.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                received.add(AwtSupport.describe(e));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                received.add(AwtSupport.describe(e));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                received.add(AwtSupport.describe(e));
            }
        });
        int shift = InputEvent.SHIFT_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK;
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0, shift, 10, 12, 1, false,
                MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, 0, InputEvent.SHIFT_DOWN_MASK, 10, 12, 1,
                false, MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, 0, InputEvent.SHIFT_DOWN_MASK, 10, 12, 1,
                false, MouseEvent.BUTTON1));
        // the modifier texts are those of the platform (getModifiersExText : "⇧" on macOS, see keyChecks)
        checks.add(Checks.expect("MouseEvents dispatched to a listener",
                "MOUSE_PRESSED (10,12) button=1 clicks=1 mods=" + Keys.join("Shift", "Button1")
                        + ", MOUSE_RELEASED (10,12) button=1 clicks=1 mods=" + Keys.text("Shift")
                        + ", MOUSE_CLICKED (10,12) button=1 clicks=1 mods=" + Keys.text("Shift"),
                () -> String.join(", ", received)));

        LegacyComponent legacy = new LegacyComponent();
        legacy.setBounds(0, 0, 50, 50);
        legacy.dispatchEvent(new MouseEvent(legacy, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 5, 6, 2,
                false, MouseEvent.BUTTON1));
        postLegacyKey(legacy);
        checks.add(Checks.expect("1.0 event model (mouseDown, keyDown without listener)",
                "mouseDown(5, 6) id=501 clickCount=2, keyDown(1008) id=403", () -> String.join(", ", legacy.calls)));

        EnabledComponent enabled = new EnabledComponent();
        enabled.dispatchEvent(new MouseEvent(enabled, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
        enabled.dispatchEvent(new MouseEvent(enabled, MouseEvent.MOUSE_MOVED, 0, 0, 2, 2, 0, false));
        checks.add(Checks.expect("enableEvents(MOUSE_EVENT_MASK) without listener : processMouseEvent", "MOUSE_ENTERED",
                () -> String.join(", ", enabled.calls)));

        List<String> containerEvents = new ArrayList<>();
        Container parent = new Container();
        parent.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                containerEvents.add(AwtSupport.idName(e) + " " + e.getChild().getName());
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
                containerEvents.add(AwtSupport.idName(e) + " " + e.getChild().getName());
            }
        });
        Component child = new Component() {
        };
        child.setName("child");
        List<String> hierarchy = new ArrayList<>();
        child.addHierarchyListener(e -> hierarchy.add(AwtSupport.idName(e) + " " + hierarchyFlags(e.getChangeFlags())));
        parent.add(child);
        parent.remove(child);
        checks.add(Checks.expect("ContainerEvents (add, remove)", "COMPONENT_ADDED child, COMPONENT_REMOVED child",
                () -> String.join(", ", containerEvents)));
        checks.add(Checks.expect("HierarchyEvents of the child (add, remove)",
                "HIERARCHY_CHANGED PARENT_CHANGED, HIERARCHY_CHANGED PARENT_CHANGED", () -> String.join(", ", hierarchy)));

        // posted asynchronously
        List<String> resized = Collections.synchronizedList(new ArrayList<>());
        Component sized = new Component() {
        };
        sized.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resized.add(AwtSupport.describe(e));
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                resized.add(AwtSupport.idName(e));
            }
        });
        sized.setBounds(0, 0, 10, 10);
        sized.setBounds(5, 5, 40, 30);

        // coalescing : 10 events posted while the EDT is busy (this callback)
        CoalescingComponent coalescing = new CoalescingComponent();
        PlainComponent plain = new PlainComponent();
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        for (int i = 0; i < 10; i++) {
            queue.postEvent(new CountEvent(coalescing, 1));
            queue.postEvent(new CountEvent(plain, 1));
        }

        // the queue coalesces consecutive MOUSE_MOVED events of the same component (real input only : snapshot mode drops
        // posted mouse events outside the needsFocus pages)
        List<String> moves = Collections.synchronizedList(new ArrayList<>());
        Component moveTarget = new Component() {
        };
        moveTarget.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                moves.add(e.getX() + "," + e.getY());
            }
        });
        boolean realInput = ShowcaseMode.realInput();
        if (realInput) {
            for (int i = 1; i <= 5; i++) {
                queue.postEvent(new MouseEvent(moveTarget, MouseEvent.MOUSE_MOVED, 0, 0, i * 10, i, 0, false));
            }
        }

        return Edt.until(() -> coalescing.dispatched.size() >= 1 && plain.dispatched.size() >= 10 && resized.size() >= 2,
                3000, "posted events").handle((v, error) -> null)
                .thenCompose(v -> Edt.rounds(2))
                .thenAccept(v -> {
                    checks.add(Checks.expect("ComponentEvents after setBounds (posted)",
                            "COMPONENT_RESIZED 40x30, COMPONENT_RESIZED 40x30, COMPONENT_MOVED",
                            () -> String.join(", ", resized)));
                    checks.add(Checks.expect("coalesceEvents overridden : 10 posted events",
                            "coalesceEvents called 9 times, dispatched [10]",
                            () -> "coalesceEvents called " + coalescing.coalesceCalls.get() + " times, dispatched "
                                    + coalescing.dispatched));
                    checks.add(Checks.expect("coalesceEvents not overridden : 10 posted events", "dispatched 10 events",
                            () -> "dispatched " + plain.dispatched.size() + " events"));
                    checks.add(realInput
                            ? Checks.expect("5 MOUSE_MOVED posted : coalesced by the queue", "50,5",
                                    () -> String.join(" ", moves))
                            : Check.info("5 MOUSE_MOVED posted : coalesced by the queue", "skipped: real input only"));
                    dispatchView.setChecks(checks);
                });
    }

    /**
     * The 1.0 way : {@code Component.postEvent(Event)} calls {@code handleEvent}, which calls {@code keyDown}.
     */
    @SuppressWarnings("deprecation")
    private static void postLegacyKey(Component legacy) {
        legacy.postEvent(new Event(legacy, 0L, Event.KEY_ACTION, 0, 0, Event.F1, 0));
    }

    private static String hierarchyFlags(long flags) {
        List<String> names = new ArrayList<>();
        if ((flags & HierarchyEvent.PARENT_CHANGED) != 0) {
            names.add("PARENT_CHANGED");
        }
        if ((flags & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
            names.add("DISPLAYABILITY_CHANGED");
        }
        if ((flags & HierarchyEvent.SHOWING_CHANGED) != 0) {
            names.add("SHOWING_CHANGED");
        }
        return String.join("|", names);
    }

    // ------------------------------------------------------------------------------------------------ event queue

    private CompletionStage<Void> queueChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("invokeAndWait on the EDT", "java.lang.Error: Cannot call invokeAndWait from the event "
                + "dispatcher thread", () -> {
                    try {
                        EventQueue.invokeAndWait(() -> {
                        });
                        return "no error";
                    } catch (Error e) {
                        return Checks.describe(e);
                    }
                }));
        // EventQueue.getCurrentEvent / isDispatchThread inside an invocation event
        CompletableFuture<String> current = new CompletableFuture<>();
        EventQueue.invokeLater(() -> current.complete(EventQueue.getCurrentEvent().getClass().getSimpleName() + " "
                + EventQueue.isDispatchThread() + " " + (EventQueue.getMostRecentEventTime() > 0)));

        // an invocation event whose runnable throws, with catchExceptions and a notifier
        Object notifier = new Object();
        AtomicBoolean notified = new AtomicBoolean();
        InvocationEvent failing = new InvocationEvent(this, () -> {
            throw new IllegalStateException("thrown by the runnable");
        }, notifier, true);

        return Edt.background(() -> {
            List<String> results = new ArrayList<>();
            AtomicBoolean ranOnEdt = new AtomicBoolean();
            EventQueue.invokeAndWait(() -> ranOnEdt.set(EventQueue.isDispatchThread()));
            results.add("ran on the EDT " + ranOnEdt.get());
            try {
                EventQueue.invokeAndWait(() -> {
                    throw new IllegalArgumentException("from the runnable");
                });
                results.add("no exception");
            } catch (InvocationTargetException e) {
                results.add(e.getClass().getSimpleName() + " <- " + Checks.describe(e.getCause()));
            }
            synchronized (notifier) {
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(failing);
                notifier.wait(3000);
                notified.set(failing.isDispatched());
            }
            return results;
        }).thenCompose(results -> {
            checks.add(Checks.expect("invokeAndWait from a background thread", "ran on the EDT true", () -> results.get(0)));
            checks.add(Checks.expect("invokeAndWait of a throwing runnable",
                    "InvocationTargetException <- java.lang.IllegalArgumentException: from the runnable", () -> results.get(1)));
            checks.add(Checks.expect("InvocationEvent(catchExceptions) : dispatched, notified, getThrowable",
                    "true true java.lang.IllegalStateException: thrown by the runnable",
                    () -> failing.isDispatched() + " " + notified.get() + " " + Checks.describe(failing.getThrowable())));
            return current;
        }).thenCompose(currentEvent -> {
            checks.add(Checks.expect("getCurrentEvent / isDispatchThread / getMostRecentEventTime > 0 in invokeLater",
                    "InvocationEvent true true", () -> currentEvent));
            return secondaryLoop(checks);
        }).thenCompose(v -> pushPop(checks))
                .thenAccept(v -> loopView.setChecks(checks));
    }

    /**
     * {@code SecondaryLoop.enter()} on the EDT pumps the events posted meanwhile ; the last one exits the loop.
     */
    private static CompletionStage<Void> secondaryLoop(List<Check> checks) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        EventQueue.invokeLater(() -> {
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            AtomicInteger pumped = new AtomicInteger();
            AtomicBoolean exited = new AtomicBoolean();
            Thread poster = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    int n = i + 1;
                    EventQueue.invokeLater(() -> {
                        pumped.incrementAndGet();
                        if (n == 5) {
                            exited.set(loop.exit());
                        }
                    });
                }
            }, "showcase-secondary-loop-poster");
            poster.setDaemon(true);
            poster.start();
            boolean entered = loop.enter();
            checks.add(Checks.expect("SecondaryLoop : enter() / events pumped inside / exit()", "true / 5 / true",
                    () -> entered + " / " + pumped.get() + " / " + exited.get()));
            checks.add(Checks.expect("SecondaryLoop : exit() again", false, loop::exit));
            // completed outside of the secondary loop
            EventQueue.invokeLater(() -> done.complete(null));
        });
        return done;
    }

    /**
     * A custom EventQueue pushed on top of the queue stack, then popped.
     */
    private static CompletionStage<Void> pushPop(List<Check> checks) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        RecordingQueue recording = new RecordingQueue();
        EventQueue system = Toolkit.getDefaultToolkit().getSystemEventQueue();
        system.push(recording);
        CoalescingComponent source = new CoalescingComponent();
        system.postEvent(new CountEvent(source, 7));
        system.postEvent(new MarkerEvent(source, () -> {
            boolean edt = EventQueue.isDispatchThread();
            recording.popSelf();
            checks.add(Checks.expect("pushed EventQueue dispatched (custom event, ActiveEvent)", "CountEvent MarkerEvent",
                    () -> String.join(" ", recording.recorded)));
            checks.add(Checks.expect("ActiveEvent.dispatch() on the EDT / events of the source", "true / [7]",
                    () -> edt + " / " + source.dispatched));
            EventQueue.invokeLater(() -> {
                checks.add(Checks.expect("after pop : the recording queue no longer dispatches", "CountEvent MarkerEvent",
                        () -> String.join(" ", recording.recorded)));
                done.complete(null);
            });
        }));
        return done;
    }

    // ------------------------------------------------------------------------------------------------- key strokes

    @SuppressWarnings("deprecation")
    private static String oldMouseModifiers() {
        return MouseEvent.getMouseModifiersText(InputEvent.SHIFT_MASK | InputEvent.BUTTON1_MASK);
    }

    /**
     * The key and modifier texts come from {@code Toolkit.getProperty("AWT.xxx", default)} : the
     * {@code sun.awt.resources.awt} bundle (Windows, Linux), and on macOS first the {@code sun.awt.resources.awtosx}
     * bundle that {@code LWCToolkit} installs as the platform resources (the symbols ⌘ ⌃ ⌥ ⇧ ⎋ ⌫ ⇞ ↑ ⌨) :
     * {@link Keys#text} gives the names of this platform.
     */
    private static List<Check> keyChecks() {
        List<Check> checks = new ArrayList<>();
        // AWTKeyStroke.getAWTKeyStroke(String) reads the VK_ constants by reflection (KeyEvent.class.getField)
        checks.add(Checks.expect("AWTKeyStroke.getAWTKeyStroke(\"ctrl shift pressed A\")", "shift ctrl pressed A",
                () -> AWTKeyStroke.getAWTKeyStroke("ctrl shift pressed A").toString()));
        checks.add(Checks.expect("AWTKeyStroke.getAWTKeyStroke(\"alt released F4\") / typed x", "alt released F4 / typed x",
                () -> AWTKeyStroke.getAWTKeyStroke("alt released F4") + " / " + AWTKeyStroke.getAWTKeyStroke('x')));
        checks.add(Checks.expect("AWTKeyStroke for a KeyEvent", "ctrl pressed ENTER", () -> AWTKeyStroke.getAWTKeyStrokeForEvent(
                new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_ENTER, '\n'))
                .toString()));
        checks.add(Checks.expect("AWTKeyStroke instances are cached", true,
                () -> AWTKeyStroke.getAWTKeyStroke("ctrl pressed Z") == AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_Z,
                        InputEvent.CTRL_DOWN_MASK)));
        checks.add(Checks.expect("KeyEvent.getKeyText (F1, NUMPAD5, ESCAPE, BACK_SPACE, PAGE_UP, SEMICOLON, KP_UP)",
                String.join(", ", List.of("F1", "NumPad-5", "Escape", "Backspace", "Page Up", "Semicolon", "Up")
                        .stream().map(Keys::text).toList()),
                () -> String.join(", ", List.of(KeyEvent.VK_F1,
                        KeyEvent.VK_NUMPAD5, KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_PAGE_UP,
                        KeyEvent.VK_SEMICOLON, KeyEvent.VK_KP_UP).stream().map(KeyEvent::getKeyText).toList())));
        checks.add(Checks.expect("KeyEvent.getExtendedKeyCodeForChar (a, A, 1, e acute, cyrillic zhe, arabic alef)",
                "0x41 0x41 0x31 0x10000E9 0x1000436 0x1000627", () -> String.join(" ", "aA1éжا".chars()
                        .mapToObj(ch -> "0x" + Integer.toHexString(KeyEvent.getExtendedKeyCodeForChar(ch))
                                .toUpperCase(Locale.ROOT))
                        .toList())));
        checks.add(Checks.expect("InputEvent.getModifiersExText (all modifiers, buttons)",
                Keys.join("Meta", "Ctrl", "Alt", "Shift", "Alt Graph") + " / Button1+Button2+Button3",
                () -> InputEvent.getModifiersExText(InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                        | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.META_DOWN_MASK) + " / "
                        + InputEvent.getModifiersExText(InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK
                                | InputEvent.BUTTON3_DOWN_MASK)));
        checks.add(Checks.expect("InputEvent.getMaskForButton(1, 2, 3)", "1024 2048 4096",
                () -> InputEvent.getMaskForButton(1) + " " + InputEvent.getMaskForButton(2) + " "
                        + InputEvent.getMaskForButton(3)));
        checks.add(Checks.expect("MouseEvent.getMouseModifiersText (old masks SHIFT | BUTTON1)",
                Keys.join("Shift", "Button1"), AwtEventsPage::oldMouseModifiers));
        checks.add(Checks.info("MouseInfo.getNumberOfButtons() / extra mouse buttons enabled",
                () -> MouseInfo.getNumberOfButtons() + " / " + Toolkit.getDefaultToolkit().areExtraMouseButtonsEnabled()));
        checks.add(Checks.info("input method locale (keyboard layout)", () -> {
            Locale locale = InputContext.getInstance().getLocale();
            return locale == null ? "none" : locale.toLanguageTag();
        }));
        return checks;
    }

    // ------------------------------------------------------------------------------------------------------ robot

    private CompletionStage<Void> robot(RecordingCanvas target, List<String> global) {
        if (!ShowcaseMode.snapshot() || !ShowcaseMode.realInput()) {
            robotView.setChecks(List.of(Check.info("Robot input", "skipped: snapshot mode only")));
            return CompletableFuture.completedFuture(null);
        }
        // the target frame gets the focus below (Focus.acquire) : this process may have lost the foreground meanwhile
        java.awt.Window previouslyFocused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusedWindow();
        // the Robot target : an always-on-top frame of its own (the windows of other applications never cover it)
        targetFrame = new java.awt.Frame("Robot target");
        targetFrame.setAlwaysOnTop(true);
        targetFrame.add(target, BorderLayout.CENTER);
        if (Platforms.isMac()) {
            targetFrame.add(restStrip(), BorderLayout.EAST);
        }
        targetFrame.pack();
        java.awt.Rectangle area = AwtSupport.secondaryArea(targetFrame.getWidth(), targetFrame.getHeight());
        targetFrame.setLocation(area.x, area.y);
        targetFrame.setVisible(true);
        target.requestFocusInWindow();
        java.awt.Frame frame = targetFrame;
        return Focus.acquire(frame)
                .thenCompose(attempts -> {
                    target.requestFocusInWindow();
                    return Edt.until(target::isFocusOwner, 3000, "target canvas focused")
                            .handle((v, error) -> error == null ? attempts : 0);
                })
                // X11 without window manager : the ConfigureNotify of pack (the frame at the location of a new window)
                // takes the location of the frame back there until the one of setLocation, which may come after the
                // focus ; the origin of the Robot coordinates is read once it is back (see Focus.awaitPlaced). Under a
                // window manager, both are dropped (handled before it reparented the frame, which is not moved then)
                .thenCompose(attempts -> Focus.awaitPlaced(frame, area.getLocation(), 64, 2000)
                        .thenApply(placed -> attempts))
                .thenCompose(focused -> {
                    if (focused == 0) {
                        return CompletableFuture.completedFuture(List.of(Check.attempts("target frame focused", 0),
                                Check.info("Robot input", "skipped: not focused")));
                    }
                    Point origin = target.getLocationOnScreen();
                    boolean capsLock = capsLock();
                    return Edt.background(() -> drive(target, global, origin, !capsLock)).thenApply(result -> {
                        List<Check> checks = new ArrayList<>(result);
                        checks.add(0, Check.attempts("target frame focused", focused));
                        checks.add(0, Check.info("keyboard input", capsLock ? "skipped: caps lock is on" : "sent"));
                        checks.add(1, Check.info("keyboard layout (input method locale)", AwtSupport.inputLocale()));
                        List<String> log = new ArrayList<>(target.log);
                        checks.add(Checks.expect("AWTEventListener saw the same events as the canvas listeners", true,
                                () -> counts(log).equals(countNames(global))));
                        checks.add(Check.info("event counts", counts(log)));
                        robotLogHolder.removeAll();
                        robotLogHolder.add(AwtSupport.log(log, 520));
                        robotLogHolder.invalidate();
                        io.quarkiverse.desktop.showcase.core.Snapshots.layout(robotLogHolder);
                        return checks;
                    });
                })
                .thenCompose(checks -> {
                    robotView.setChecks(checks);
                    // the focus goes back to the page window before the target frame is disposed : Windows would
                    // otherwise activate the next window in the z-order, maybe of another application
                    if (previouslyFocused != null && previouslyFocused.isShowing()) {
                        previouslyFocused.toFront();
                        previouslyFocused.requestFocus();
                    }
                    return Edt.until(() -> previouslyFocused == null || previouslyFocused.isFocused(), 2000,
                            "page window focused").handle((v, e) -> null);
                })
                .thenAccept(v -> {
                    targetFrame.dispose();
                    targetFrame = null;
                });
    }

    private static boolean capsLock() {
        try {
            return Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * Expected mouse results of the Robot sequence (see {@link #results}) : the checks, and whether the sequence runs
     * again ({@link #complete}). X11 resets the click count once the pointer moves away from the press point
     * (XWindow.handleMotionNotify) : the release after the drag has none. Windows sends one wheel event per Robot call,
     * X11 one per notch (a press and release of the wheel buttons). Computed at run time (Quarkus initializes this
     * class at build time).
     * <p>
     * macOS : {@code CRobot} counts the clicks itself (native {@code GetClickCount} of {@code CRobot.m}, whatever the
     * button) : a mouse move resets the time of the last click, and a release longer than the double click interval
     * after it has the click count 0 : as on X11, the release after the drag has none. One wheel event per Robot call
     * ({@code CRobot.mouseWheel} posts one scroll wheel event of {@code wheelAmt} lines), whose delta AWT negates
     * ({@code CPlatformResponder.dispatchScrollEvent} : "invert the wheelRotation for the peer") : {@code -1 2} for
     * {@code mouseWheel(1)}, {@code mouseWheel(-2)} without natural scrolling (seen on macOS 27, JVM and native). With
     * natural scrolling, or when that preference cannot be read ({@link MacPreferences#naturalScrolling()}), the
     * direction of the posted events is not known (never observed : see {@link MacPreferences#naturalScrolling()}) :
     * the magnitudes and the opposite signs only ({@link #wheelValue}).
     */
    private static List<String> expectedMouse() {
        if (Platforms.isMac()) {
            return List.of("MOUSE_ENTERED (40,40)", "1 2 1", "(100,70) (140,90) / MOUSE_RELEASED (140,90) button=1",
                    wheelSigned() ? "-1 2" : "1 2, opposite signs");
        }
        boolean x11 = Platforms.isLinux();
        return List.of("MOUSE_ENTERED (40,40)", "1 2 1",
                "(100,70) (140,90) / MOUSE_RELEASED (140,90) button=1" + (x11 ? "" : " clicks=1"),
                x11 ? "1 -1 -1" : "1 -2");
    }

    /**
     * {@code true} when the signs of the wheel rotations are known : always on Windows and Linux ; on macOS only
     * without natural scrolling (see {@link #expectedMouse()}). On a background thread (it may run
     * {@code /usr/bin/defaults} the first time).
     */
    private static boolean wheelSigned() {
        return !Platforms.isMac() || Boolean.FALSE.equals(MacPreferences.naturalScrolling());
    }

    /**
     * The value of the "wheel rotations" check for the rotations {@code rotations} (see {@link #results}) : the
     * rotations, or, when their signs are not known ({@link #wheelSigned()}), their magnitudes and whether the two
     * signs are opposite ({@code mouseWheel(1)} then {@code mouseWheel(-2)} : "1 2, opposite signs").
     */
    private static String wheelValue(String rotations) {
        if (wheelSigned()) {
            return rotations;
        }
        List<Integer> values = rotations.isBlank() ? List.of()
                : Arrays.stream(rotations.split(" ")).map(Integer::valueOf).toList();
        boolean opposite = values.size() == 2 && values.get(0) != 0
                && Integer.signum(values.get(0)) == -Integer.signum(values.get(1));
        return (values.isEmpty() ? "none"
                : String.join(" ", values.stream().map(value -> String.valueOf(Math.abs(value))).toList()))
                + (opposite ? ", opposite signs" : ", not opposite signs");
    }

    /**
     * The pressed keys of the Robot sequence, named as {@code KeyEvent.getKeyText} names them on this platform (the
     * symbols of {@code sun.awt.resources.awtosx} on macOS : {@link Keys#text}). Ctrl+B is Ctrl on every platform : it
     * types the control character U+0002.
     */
    private static String expectedKeys() {
        return String.join(" ", List.of("A", "Shift", "A", "Space", "Enter", "Left", "Home", "Ctrl", "B").stream()
                .map(Keys::text).toList());
    }

    /** Width of the strip right of the canvas in the target frame (macOS, see {@link #rest}). */
    private static final int STRIP_WIDTH = 24;

    /**
     * The strip of the target frame right of the canvas, on macOS (see {@link #rest}) : a part of the content view of
     * the window that is not the canvas.
     */
    private static Component restStrip() {
        java.awt.Panel strip = new java.awt.Panel();
        strip.setName("restStrip");
        strip.setPreferredSize(new Dimension(STRIP_WIDTH, CANVAS_HEIGHT));
        return strip;
    }

    /**
     * Where the pointer starts and ends the Robot sequence : outside the canvas, still in the target frame. Windows and
     * Linux : 4 px above the canvas, in the frame insets (the title bar). macOS sends the content view of the window no
     * mouse event while the pointer is over the title bar, not even the exit (seen on macOS 27 : no MOUSE_EXITED, no
     * MOUSE_MOVED) : {@code LWWindowPeer}, which makes the MOUSE_ENTERED and MOUSE_EXITED of the components from the
     * mouse events of the window ({@code generateMouseEnterExitEventsForComponents}), would still have the canvas
     * under the pointer, and post no MOUSE_ENTERED when the pointer comes back. There the pointer rests in the middle
     * of the strip right of the canvas ({@link #restStrip()}, part of the content view) : the move from the strip into
     * the canvas posts the MOUSE_EXITED of the strip and the MOUSE_ENTERED of the canvas. The screen pixels that Robot
     * reads there include the pointer (see {@link #probe}) : its arrow ({@code NSCursor.arrow} of macOS 27, a 28 x 40
     * image with its hot spot at 5,5, opaque from 3 px left of and above the tip to 11 px right of and 18 px below it)
     * is scaled by the pointer size of the Accessibility settings (at most 4 : 12 px left of the tip), and stays right
     * of the canvas, out of the pixels probed and captured, whatever that size.
     */
    private static Point rest(Point origin) {
        return Platforms.isMac() ? new Point(origin.x + CANVAS_WIDTH + STRIP_WIDTH / 2, origin.y + CANVAS_HEIGHT / 2)
                : new Point(origin.x + CANVAS_WIDTH / 2, origin.y - 4);
    }

    /**
     * The point whose screen pixel proves, right before a click at {@code click}, that the canvas is there (not covered
     * by another window) : the click point itself, except on macOS, where the screen pixels that Robot reads include
     * the mouse pointer ({@code CRobot.getRGBPixel} reads them with the native {@code nativeGetScreenPixels}, a
     * {@code CGWindowListCreateImage} of the screen : seen on macOS 27, the pixel under the pointer is the arrow's),
     * and the pointer is on the click point : 10 px up and left of it, outside the arrow drawn down and right from its
     * tip.
     */
    private static Point probe(Point click) {
        return Platforms.isMac() ? new Point(click.x - 10, click.y - 10) : click;
    }

    /**
     * macOS only : {@code true} for a MOUSE_MOVED whose description {@code entry} is the one of the event recorded just
     * before ({@code previous}) : there each pointer move of Robot comes twice to the window (seen on macOS 27 : two
     * MOUSE_MOVED at the same point for each {@code Robot.mouseMove} ; AppKit calls {@code mouseMoved:} of the content
     * view {@code AWTView} both as the first responder of a window that accepts mouse moved events and as the owner of
     * a tracking area with {@code NSTrackingMouseMoved}), and the event queue merges the second into the first only
     * while the first is still queued ({@code EventQueue.coalesceMouseEvent}) : one or two events depending on the
     * timing (they differed between a JVM and a native run). The log and the counts keep one. Not called on Windows
     * and Linux (every event recorded).
     */
    private static boolean repeatedMove(AWTEvent e, String entry, String previous) {
        return e.getID() == MouseEvent.MOUSE_MOVED && entry.equals(previous);
    }

    /**
     * The mouse results of {@code results} (see {@link #results}) as the checks compare them : the wheel rotations
     * through {@link #wheelValue}.
     */
    private static List<String> mouseValues(List<String> results) {
        return List.of(results.get(0), results.get(1), results.get(2), wheelValue(results.get(3)));
    }

    /** Attempts of the Robot sequence : another application may take the foreground at any time. */
    private static final int ATTEMPTS = 3;

    /**
     * Runs on a background thread. Every press is preceded by a pixel probe of the click point (the canvas color) and
     * by the focus check of {@link RobotSession}. The whole sequence runs again (at most {@link #ATTEMPTS} times, the
     * target frame focused again first) when an event is missing.
     */
    private List<Check> drive(RecordingCanvas target, List<String> global, Point origin, boolean keys) throws Exception {
        List<Check> checks = new ArrayList<>();
        BufferedImage painted = null;
        if (Platforms.isMac()) {
            // the direction of the Robot wheel events there (see expectedMouse) : read here, off the EDT
            Boolean natural = MacPreferences.naturalScrolling();
            checks.add(Check.info("scroll direction (macOS preference com.apple.swipescrolldirection)",
                    natural == null ? "unknown" : natural ? "natural" : "not natural"));
            // what the canvas paints, for its capture (see snapToPainted)
            painted = Edt.supply(() -> paintedCanvas(target)).toCompletableFuture().get(10,
                    java.util.concurrent.TimeUnit.SECONDS);
        }
        try (RobotSession robot = RobotSession.open()) {
            if (Platforms.isMac()) {
                // the screen pixels include the pointer there (see probe) : away from the pixels probed below
                robot.move(rest(origin));
            }
            // the new frame is painted (a slower first paint shows the background of the native window until then)
            robot.delay(300);
            robot.idle();
            Point probe = new Point(origin.x + CANVAS_WIDTH - 20, origin.y + 20);
            robot.waitForPixel(probe, CANVAS_COLOR, 3000);
            Color seen = robot.robot().getPixelColor(probe.x, probe.y);
            checks.add(Checks.expect("Robot.getPixelColor inside the canvas", Checks.argb(0xFF000000 | CANVAS_COLOR),
                    () -> Checks.argb(0xFF000000 | RobotSession.snap(seen.getRGB(), CANVAS_COLOR))));
            int attempt = 0;
            List<String> log;
            do {
                attempt++;
                if (attempt > 1 && !robot.ensureFocus(target)) {
                    break;
                }
                robot.skipped().clear();
                global.clear();
                sequence(robot, target, origin, keys, painted);
                log = new ArrayList<>(target.log);
                if (!complete(log, keys) && attempt < ATTEMPTS) {
                    RobotSession.logRetry("awt-events Robot sequence", attempt, results(log, keys) + " skipped "
                            + robot.skipped());
                }
            } while (!complete(log, keys) && attempt < ATTEMPTS);
            checks.add(Check.attempts("Robot mouse and keyboard sequence", attempt));
            if (!robot.skipped().isEmpty()) {
                checks.add(Check.info("skipped inputs", "skipped: " + String.join(", ", robot.skipped())));
            }
            List<String> expectedMouse = expectedMouse();
            List<String> results = results(target.log, keys);
            checks.add(Checks.expect("mouse enters the canvas", expectedMouse.get(0), () -> results.get(0)));
            checks.add(Checks.expect("mouse clicks : click counts", expectedMouse.get(1), () -> results.get(1)));
            checks.add(Checks.expect("drag : MOUSE_DRAGGED events, then the release", expectedMouse.get(2),
                    () -> results.get(2)));
            List<String> finalLog = new ArrayList<>(target.log);
            checks.add(Checks.expect("right button : popup trigger (Windows : on release, Linux : on press)",
                    io.quarkiverse.desktop.showcase.core.Platforms.isWindows() ? "MOUSE_RELEASED" : "MOUSE_PRESSED",
                    () -> finalLog.stream().filter(entry -> entry.contains("popupTrigger"))
                            .map(entry -> entry.substring(0, entry.indexOf(' '))).findFirst().orElse("none")));
            checks.add(Checks.expect("wheel rotations", expectedMouse.get(3), () -> wheelValue(results.get(3))));
            if (!wheelSigned()) {
                // macOS with natural scrolling (or unknown) : the signs as they came, not checked (see expectedMouse)
                checks.add(Check.info("wheel rotations with their signs (natural scrolling : not verified)",
                        results.get(3)));
            }
            if (keys) {
                checks.add(Checks.expect("pressed keys", expectedKeys(), () -> results.get(4)));
                // the characters depend on the keyboard layout (Arabic letters with an Arabic layout)
                checks.add(Check.info("typed characters (keyboard layout dependent)", String.join(" ", finalLog.stream()
                        .filter(entry -> entry.startsWith("KEY_TYPED"))
                        .map(entry -> entry.replaceAll(".*char=('.'|\\S+).*", "$1")).toList())));
                checks.add(Checks.expect("typed characters of Space, Enter, Ctrl+B", "' ' \\u000A \\u0002",
                        () -> String.join(" ", finalLog.stream().filter(entry -> entry.startsWith("KEY_TYPED"))
                                .map(entry -> entry.replaceAll(".*char=('.'|\\S+).*", "$1")).skip(2).toList())));
                checks.add(Checks.expect("Shift key location", "LEFT", () -> finalLog.stream()
                        .filter(entry -> entry.startsWith("KEY_PRESSED code=" + Keys.text("Shift")))
                        .map(entry -> entry.replaceAll(".*location=(\\S+).*", "$1")).findFirst().orElse("none")));
            }
        }
        return checks;
    }

    /**
     * {@code true} when the log has every expected event of the sequence.
     */
    private static boolean complete(List<String> log, boolean keys) {
        List<String> results = results(log, keys);
        return mouseValues(results).equals(expectedMouse()) && (!keys || results.get(4).equals(expectedKeys()))
                && log.stream().anyMatch(entry -> entry.contains("popupTrigger"));
    }

    /**
     * The first mouse entry, the click counts, the drag, the wheel rotations and the pressed keys of {@code events}.
     */
    private static List<String> results(List<String> events, boolean keys) {
        List<String> log = new ArrayList<>(events);
        List<String> results = new ArrayList<>();
        results.add(log.stream().filter(entry -> entry.startsWith("MOUSE_ENTERED")).findFirst().orElse("none"));
        results.add(String.join(" ", log.stream().filter(entry -> entry.startsWith("MOUSE_CLICKED"))
                .map(entry -> entry.replaceAll(".*clicks=(\\d+).*", "$1")).toList()));
        results.add(String.join(" ", log.stream().filter(entry -> entry.startsWith("MOUSE_DRAGGED"))
                .map(entry -> entry.replaceAll("MOUSE_DRAGGED (\\(\\d+,\\d+\\)).*", "$1")).toList())
                + " / " + log.stream().filter(entry -> entry.startsWith("MOUSE_RELEASED (140,90) button=1"))
                        .findFirst().orElse("none"));
        results.add(String.join(" ", log.stream().filter(entry -> entry.startsWith("MOUSE_WHEEL"))
                .map(entry -> entry.replaceAll(".*rotation=(-?\\d+).*", "$1")).toList()));
        results.add(keys ? String.join(" ", log.stream().filter(entry -> entry.startsWith("KEY_PRESSED"))
                .map(entry -> entry.replaceAll("KEY_PRESSED code=(.*?) char=.*", "$1")).toList()) : "");
        return results;
    }

    /**
     * One run of the Robot sequence : mouse moves, clicks, a drag, the right button, the wheel and keys.
     */
    private void sequence(RobotSession robot, RecordingCanvas target, Point origin, boolean keys,
            BufferedImage painted) {
        // start outside the canvas (in the frame insets, still our window ; in a strip of the frame on macOS, see rest),
        // then record
        robot.move(rest(origin));
        robot.delay(100);
        // a retry focused the frame again (RobotSession.ensureFocus : the window focused) : the FOCUS_GAINED of the
        // canvas comes later, it is handled before the log is cleared (it was recorded once on windows-11-arm)
        io.quarkiverse.desktop.showcase.pages.desktop.DesktopSupport.await(target::isFocusOwner, 3000);
        robot.idle();
        target.log.clear();
        target.recording = true;

        robot.move(new Point(origin.x + 40, origin.y + 40));
        robot.move(new Point(origin.x + 60, origin.y + 50));
        Point click = new Point(origin.x + 60, origin.y + 50);
        if (robot.pixelIs(probe(click), CANVAS_COLOR)) {
            robot.press(InputEvent.BUTTON1_DOWN_MASK);
            robot.release(InputEvent.BUTTON1_DOWN_MASK);
            robot.press(InputEvent.BUTTON1_DOWN_MASK);
            robot.release(InputEvent.BUTTON1_DOWN_MASK);
        }
        robot.delay(600);
        if (robot.pixelIs(probe(click), CANVAS_COLOR)) {
            robot.press(InputEvent.BUTTON1_DOWN_MASK);
            robot.move(new Point(origin.x + 100, origin.y + 70));
            robot.move(new Point(origin.x + 140, origin.y + 90));
            robot.release(InputEvent.BUTTON1_DOWN_MASK);
        }
        robot.delay(600);
        Point right = new Point(origin.x + 140, origin.y + 90);
        if (robot.pixelIs(probe(right), CANVAS_COLOR)) {
            robot.press(InputEvent.BUTTON3_DOWN_MASK);
            robot.release(InputEvent.BUTTON3_DOWN_MASK);
        }
        if (robot.pixelIs(probe(right), CANVAS_COLOR)) {
            robot.wheel(1);
            robot.wheel(-2);
        }
        if (keys) {
            robot.key(KeyEvent.VK_A);
            robot.key(KeyEvent.VK_SHIFT, KeyEvent.VK_A);
            robot.key(KeyEvent.VK_SPACE);
            robot.key(KeyEvent.VK_ENTER);
            robot.key(KeyEvent.VK_LEFT);
            robot.key(KeyEvent.VK_HOME);
            robot.key(KeyEvent.VK_CONTROL, KeyEvent.VK_B);
        }
        robot.move(rest(origin));
        robot.delay(200);
        robot.idle();
        // 8 px inside the frame : Windows 11 rounds the corners of top-level windows, whatever is behind them shows
        BufferedImage screen = robot.capture(new java.awt.Rectangle(origin.x + 8, origin.y + 8,
                CANVAS_WIDTH - 16, CANVAS_HEIGHT - 16));
        if (painted != null) {
            snapToPainted(screen, painted, 8, 8);
        }
        captures.put("canvas-screen", screen);
        target.recording = false;
    }

    /**
     * What {@code canvas} paints (on the EDT), for {@link #snapToPainted}.
     */
    private static BufferedImage paintedCanvas(RecordingCanvas canvas) {
        BufferedImage image = new BufferedImage(Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()),
                BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        try {
            canvas.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * macOS : Robot reads the screen through the color profile of the display, and the colors it reads vary by a level
     * or two from run to run (the canvas painted #37474F was read #36474F in most runs, #38474F in others ; the
     * antialiased text by 1 or 2 levels too) : each pixel of the capture {@code screen} within
     * {@link RobotSession#COLOR_TOLERANCE} of the pixel the canvas painted there ({@code painted}, at
     * {@code dx, dy} from the capture) gets the painted color ({@link RobotSession#snap}, the per pixel form of
     * {@link RobotSession#sameImage}), the others (a real difference : a window covering the canvas, the pointer) stay
     * as read. The canvas painted into an image renders its text like the screen (macOS 27, the same glyphs of the
     * native font scaler : at most 3 levels apart as read).
     */
    private static void snapToPainted(BufferedImage screen, BufferedImage painted, int dx, int dy) {
        for (int y = 0; y < screen.getHeight() && y + dy < painted.getHeight(); y++) {
            for (int x = 0; x < screen.getWidth() && x + dx < painted.getWidth(); x++) {
                int argb = screen.getRGB(x, y);
                screen.setRGB(x, y, (argb & 0xFF000000) | RobotSession.snap(argb, painted.getRGB(x + dx, y + dy)));
            }
        }
    }

    private static String counts(List<String> log) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String entry : log) {
            int space = entry.indexOf(' ');
            counts.merge(space < 0 ? entry : entry.substring(0, space), 1, Integer::sum);
        }
        return counts.toString();
    }

    private static String countNames(List<String> names) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String name : new ArrayList<>(names)) {
            counts.merge(name, 1, Integer::sum);
        }
        return counts.toString();
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(captures));
    }

    @Override
    public void dispose(Component content) {
        if (targetFrame != null) {
            targetFrame.dispose();
        }
        targetFrame = null;
        if (globalListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(globalListener);
        }
        globalListener = null;
        canvas = null;
        robotLogHolder = null;
        dispatchView = null;
        loopView = null;
        robotView = null;
        keysView = null;
        captures.clear();
    }
}
