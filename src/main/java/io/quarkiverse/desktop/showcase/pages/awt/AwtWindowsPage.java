package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Top-level windows : Frame states (maximized within {@code setMaximizedBounds}, iconified, restored, with the
 * WindowStateListener log), decorated, undecorated and non resizable frames, a Dialog, the window types UTILITY and
 * POPUP, always-on-top windows, multi-size icon images, {@code setOpacity}, {@code setShape}, per-pixel translucency,
 * the heavyweight/lightweight mixing cut-out, and the four Dialog modality types (which windows receive events while a
 * dialog is shown), window events and the API constraints of decorated windows.
 * <p>
 * A {@code needsFocus} page (it opens, maximizes and iconifies windows). The windows are shown above an opaque backdrop
 * window in the lower right part of the screen (all always on top, away from the main window), captured with Robot as
 * the extra image {@code gallery} : opacity, shapes and translucency only exist on screen. Pixel probes of the capture
 * check the composition.
 */
@Singleton
public class AwtWindowsPage implements FeaturePage {

    private static final int AREA_WIDTH = 1400;
    private static final int AREA_HEIGHT = 900;
    private static final int CELL_WIDTH = 340;
    private static final int CELL_HEIGHT = 280;
    private static final int MARGIN = 20;
    private static final int BACKDROP = 0xDDE3EA;
    private static final int OPACITY_COLOR = 0x1565C0;
    private static final float OPACITY = 0.6f;
    private static final int SHAPE_COLOR = 0x2E7D32;
    private static final int TRANSLUCENT_RED = 0xC62828;
    private static final int OPAQUE_CIRCLE = 0x6A1B9A;
    private static final int CUTOUT_COLOR = 0xFF9800;
    private static final int HIDDEN_COLOR = 0x1E88E5;
    private static final String[] CELLS = { "decorated Frame, icon images", "Frame, not resizable", "undecorated Frame",
            "Dialog (owner: first frame)", "Window.Type.UTILITY", "Window.Type.POPUP", "setOpacity(0.6)",
            "setShape(RoundRectangle2D)", "setShape(Ellipse2D) + Button", "per-pixel translucency",
            "mixing : lightweights above a Button", "Frame MAXIMIZED_BOTH (maximized bounds)" };

    // per build state
    private final List<Window> windows = new ArrayList<>();
    private final Map<String, BufferedImage> captures = new LinkedHashMap<>();
    private ChecksView apiView;
    private ChecksView statesView;
    private ChecksView modalityView;
    private ChecksView galleryView;

    @Override
    public String id() {
        return "awt-windows";
    }

    @Override
    public String title() {
        return "Windows and dialogs";
    }

    @Override
    public String category() {
        return Categories.AWT;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean needsFocus() {
        return true;
    }

    @Override
    public Component build() {
        captures.clear();
        apiView = ChecksView.table("Window API", apiChecks());
        statesView = ChecksView.table("Frame states", List.of(Check.info("state", "pending")));
        modalityView = ChecksView.table("Dialog modality : which windows receive an ActionEvent while the dialog is shown",
                List.of(Check.info("state", "pending")));
        galleryView = ChecksView.table("On-screen composition (Robot capture of the windows, extra image \"gallery\")",
                List.of(Check.info("state", "pending")));
        return Ui.column(12,
                Ui.text("Top-level windows of AWT. They are shown above an opaque backdrop window in the lower right part "
                        + "of the screen and captured with Robot (opacity, shapes and translucency only exist on screen), "
                        + "then disposed. Modality : a button of each window receives an ActionEvent posted to the event "
                        + "queue while the dialog is shown ; events of blocked windows are dropped.", 1000),
                apiView, modalityView, statesView, galleryView);
    }

    // ------------------------------------------------------------------------------------------------- API checks

    private static List<Check> apiChecks() {
        List<Check> checks = new ArrayList<>();
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        for (GraphicsDevice.WindowTranslucency t : GraphicsDevice.WindowTranslucency.values()) {
            checks.add(Checks.info("isWindowTranslucencySupported(" + t + ")", () -> device.isWindowTranslucencySupported(t)));
        }
        checks.add(Checks.info("GraphicsConfiguration.isTranslucencyCapable()",
                () -> device.getDefaultConfiguration().isTranslucencyCapable()));
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        checks.add(Checks.info("isFrameStateSupported(NORMAL, ICONIFIED, MAXIMIZED_HORIZ, MAXIMIZED_VERT, MAXIMIZED_BOTH)",
                () -> toolkit.isFrameStateSupported(Frame.NORMAL) + " " + toolkit.isFrameStateSupported(Frame.ICONIFIED) + " "
                        + toolkit.isFrameStateSupported(Frame.MAXIMIZED_HORIZ) + " "
                        + toolkit.isFrameStateSupported(Frame.MAXIMIZED_VERT) + " "
                        + toolkit.isFrameStateSupported(Frame.MAXIMIZED_BOTH)));
        checks.add(Checks.info("isModalityTypeSupported(MODELESS, DOCUMENT, APPLICATION, TOOLKIT)", () -> {
            List<String> values = new ArrayList<>();
            for (Dialog.ModalityType type : Dialog.ModalityType.values()) {
                values.add(String.valueOf(toolkit.isModalityTypeSupported(type)));
            }
            return String.join(" ", values);
        }));
        checks.add(Checks.info("isModalExclusionTypeSupported(NO, APPLICATION, TOOLKIT)", () -> {
            List<String> values = new ArrayList<>();
            for (Dialog.ModalExclusionType type : Dialog.ModalExclusionType.values()) {
                values.add(String.valueOf(toolkit.isModalExclusionTypeSupported(type)));
            }
            return String.join(" ", values);
        }));
        checks.add(Checks.info("Toolkit.isAlwaysOnTopSupported()", toolkit::isAlwaysOnTopSupported));
        checks.add(Checks.expect("decorated Frame : setOpacity(0.5)", "IllegalComponentStateException: The frame is decorated",
                () -> exception(() -> new Frame().setOpacity(0.5f))));
        checks.add(Checks.expect("decorated Frame : setShape", "IllegalComponentStateException: The frame is decorated",
                () -> exception(() -> new Frame().setShape(new Ellipse2D.Double(0, 0, 10, 10)))));
        checks.add(Checks.expect("decorated Frame : translucent background",
                "IllegalComponentStateException: The frame is decorated",
                () -> exception(() -> new Frame().setBackground(new Color(0, 0, 0, 0)))));
        checks.add(Checks.expect("decorated Dialog : setOpacity(0.5)", "IllegalComponentStateException: The dialog is decorated",
                () -> exception(() -> new Dialog((Frame) null).setOpacity(0.5f))));
        checks.add(Checks.expect("setUndecorated on a displayable Frame",
                "IllegalComponentStateException: The frame is displayable.", () -> {
                    Frame frame = new Frame();
                    try {
                        frame.addNotify();
                        return exception(() -> frame.setUndecorated(true));
                    } finally {
                        frame.dispose();
                    }
                }));
        checks.add(Checks.expect("setType on a displayable Window", "IllegalComponentStateException", () -> {
            Frame frame = new Frame();
            try {
                frame.addNotify();
                return exception(() -> frame.setType(Window.Type.UTILITY)).replaceAll(":.*", "");
            } finally {
                frame.dispose();
            }
        }));
        checks.add(Checks.expect("setOpacity(1.5) is out of range", "IllegalArgumentException", () -> {
            Window window = new Window((Frame) null);
            return exception(() -> window.setOpacity(1.5f)).replaceAll(":.*", "");
        }));
        checks.add(Checks.expect("setMinimumSize(300x200) then setSize(100x80)", "300x200", () -> {
            Frame frame = new Frame();
            frame.setMinimumSize(new Dimension(300, 200));
            frame.setSize(100, 80);
            return AwtSupport.size(frame.getSize());
        }));
        checks.add(Checks.expect("undecorated Frame insets", "0,0,0,0", () -> {
            Frame frame = new Frame();
            frame.setUndecorated(true);
            try {
                frame.addNotify();
                return AwtSupport.insets(frame.getInsets());
            } finally {
                frame.dispose();
            }
        }));
        checks.add(Checks.info("decorated Frame insets (normal / not resizable / UTILITY)", () -> insets(Window.Type.NORMAL, true)
                + " / " + insets(Window.Type.NORMAL, false) + " / " + insets(Window.Type.UTILITY, true)));
        checks.add(Checks.expect("window types (default, UTILITY, POPUP)", "NORMAL UTILITY POPUP", () -> {
            Frame frame = new Frame();
            Frame utility = new Frame();
            utility.setType(Window.Type.UTILITY);
            Window popup = new Window((Frame) null);
            popup.setType(Window.Type.POPUP);
            return frame.getType() + " " + utility.getType() + " " + popup.getType();
        }));
        checks.add(Checks.expect("Dialog defaults : modal, modality type, resizable, title", "false MODELESS true ''", () -> {
            Dialog dialog = new Dialog((Frame) null);
            return dialog.isModal() + " " + dialog.getModalityType() + " " + dialog.isResizable() + " '"
                    + dialog.getTitle() + "'";
        }));
        checks.add(Checks.expect("Dialog(owner, modal=true) modality type / Dialog.DEFAULT_MODALITY_TYPE",
                "APPLICATION_MODAL / APPLICATION_MODAL",
                () -> new Dialog((Frame) null, true).getModalityType() + " / " + Dialog.DEFAULT_MODALITY_TYPE));
        return checks;
    }

    private static String insets(Window.Type type, boolean resizable) {
        Frame frame = new Frame();
        frame.setType(type);
        frame.setResizable(resizable);
        try {
            frame.addNotify();
            return AwtSupport.insets(frame.getInsets());
        } finally {
            frame.dispose();
        }
    }

    private static String exception(Runnable action) {
        try {
            action.run();
            return "no exception";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        Rectangle area = AwtSupport.secondaryArea(AREA_WIDTH, AREA_HEIGHT);
        return modality(area)
                .thenCompose(v -> gallery(area, focused))
                .whenComplete((v, error) -> {
                    if (error != null) {
                        galleryView.setChecks(List.of(Check.fail("windows", Checks.describe(error))));
                    }
                    disposeWindows();
                });
    }

    // --------------------------------------------------------------------------------------------------- modality

    private record ModalityWindows(Frame document, Frame other, Frame excluded, List<String> received) {
    }

    private CompletionStage<Void> modality(Rectangle area) {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        Frame document = modalityFrame("document root", area.x + MARGIN, area.y + MARGIN, received);
        Frame other = modalityFrame("other root", area.x + MARGIN + 260, area.y + MARGIN, received);
        Frame excluded = modalityFrame("excluded", area.x + MARGIN + 520, area.y + MARGIN, received);
        excluded.setModalExclusionType(Dialog.ModalExclusionType.TOOLKIT_EXCLUDE);
        for (Frame frame : List.of(document, other, excluded)) {
            frame.setVisible(true);
        }
        ModalityWindows w = new ModalityWindows(document, other, excluded, received);
        List<Check> checks = new ArrayList<>();
        CompletionStage<Void> chain = Edt.rounds(3);
        String[] expected = { "document root, other root, excluded, dialog", "other root, excluded, dialog",
                "excluded, dialog", "excluded, dialog" };
        Dialog.ModalityType[] types = Dialog.ModalityType.values();
        for (int i = 0; i < types.length; i++) {
            Dialog.ModalityType type = types[i];
            String expectation = expected[i];
            chain = chain.thenCompose(v -> modalityTest(type, w, area))
                    .thenAccept(result -> checks.add(Checks.expect(type.name(), expectation, () -> result)));
        }
        return chain.whenComplete((v, error) -> {
            if (error != null) {
                checks.add(Check.fail("modality", Checks.describe(error)));
            }
            checks.add(Checks.expect("excluded frame : modal exclusion type", "TOOLKIT_EXCLUDE",
                    () -> excluded.getModalExclusionType()));
            for (Frame frame : List.of(document, other, excluded)) {
                frame.dispose();
            }
            modalityView.setChecks(checks);
        });
    }

    private Frame modalityFrame(String name, int x, int y, List<String> received) {
        Frame frame = new Frame(name);
        frame.setName(name);
        frame.setAutoRequestFocus(false);
        frame.setAlwaysOnTop(true);
        Button button = new Button("Button of " + name);
        button.setName(name);
        button.addActionListener(e -> received.add(name));
        frame.add(button);
        frame.setBounds(x, y, 240, 110);
        windows.add(frame);
        return frame;
    }

    /**
     * Shows a dialog of {@code type} owned by the document root. While it is shown (inside its secondary event loop
     * for the modal types), an ActionEvent is posted to a button of each window ; then the dialog is disposed.
     */
    private CompletionStage<String> modalityTest(Dialog.ModalityType type, ModalityWindows w, Rectangle area) {
        CompletableFuture<String> result = new CompletableFuture<>();
        w.received().clear();
        Dialog dialog = new Dialog(w.document(), "Dialog " + type, type);
        dialog.setAutoRequestFocus(false);
        dialog.setAlwaysOnTop(true);
        Button button = new Button("Button of the dialog");
        button.addActionListener(e -> w.received().add("dialog"));
        dialog.add(button);
        dialog.setBounds(area.x + MARGIN + 780, area.y + MARGIN, 300, 110);
        windows.add(dialog);
        EventQueue.invokeLater(() -> {
            // the dialog is visible here (for the modal types : inside its modal loop, where blocked windows are filtered)
            EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
            for (Component source : List.of(w.document().getComponent(0), w.other().getComponent(0),
                    w.excluded().getComponent(0), button)) {
                queue.postEvent(new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "modality"));
            }
            // after the posted events (same queue, same priority : in order)
            EventQueue.invokeLater(() -> {
                String received = String.join(", ", w.received());
                boolean visible = dialog.isVisible();
                dialog.dispose();
                // completed later, outside the modal loop of the dialog
                EventQueue.invokeLater(() -> result.complete(visible ? received : "dialog not visible : " + received));
            });
        });
        // modal types : returns once the dialog is disposed (a secondary loop pumps the events meanwhile)
        dialog.setVisible(true);
        return result;
    }

    // ---------------------------------------------------------------------------------------------------- gallery

    private CompletionStage<Void> gallery(Rectangle area, Window previouslyFocused) {
        List<Check> checks = new ArrayList<>();
        List<String> stateLog = Collections.synchronizedList(new ArrayList<>());
        List<String> windowLog = Collections.synchronizedList(new ArrayList<>());

        Window backdrop = new Backdrop(area);
        show(backdrop);
        Frame iconFrame = decoratedFrame(cell(area, 0), "Frame with icon images", true);
        iconFrame.setIconImages(icons());
        show(iconFrame);
        show(decoratedFrame(cell(area, 1), "Not resizable", false));
        Frame undecorated = new Frame("undecorated");
        undecorated.setUndecorated(true);
        undecorated.add(label("undecorated Frame", 0xFFF3E0, 0x4E342E));
        undecorated.setBounds(inner(cell(area, 2)));
        undecorated.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                windowLog.add(AwtSupport.idName(e));
            }

            @Override
            public void windowClosing(WindowEvent e) {
                windowLog.add(AwtSupport.idName(e));
                e.getWindow().dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                windowLog.add(AwtSupport.idName(e));
            }
        });
        show(undecorated);
        Dialog dialog = new Dialog(iconFrame, "Dialog");
        dialog.add(label("Dialog owned by the first frame", 0xFFFFFF, 0x263238));
        dialog.setBounds(inner(cell(area, 3)));
        show(dialog);
        Frame utility = decoratedFrame(cell(area, 4), "UTILITY", true);
        utility.setType(Window.Type.UTILITY);
        show(utility);
        Window popup = new Window(iconFrame);
        popup.setType(Window.Type.POPUP);
        popup.add(label("Window.Type.POPUP", 0xFFFDE7, 0x5D4037));
        popup.setBounds(inner(cell(area, 5)));
        show(popup);

        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        boolean translucency = device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        boolean shapes = device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT);
        boolean perPixelTranslucency = device
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        if (translucency) {
            Window translucent = new Window((Frame) null);
            translucent.setBackground(new Color(OPACITY_COLOR));
            translucent.add(label("setOpacity(0.6)", OPACITY_COLOR, 0xFFFFFF));
            translucent.setBounds(inner(cell(area, 6)));
            translucent.setOpacity(OPACITY);
            show(translucent);
        }
        if (shapes) {
            Window rounded = new Window((Frame) null);
            rounded.add(label("setShape(RoundRectangle2D)", SHAPE_COLOR, 0xFFFFFF));
            rounded.setBounds(inner(cell(area, 7)));
            rounded.setShape(new RoundRectangle2D.Double(0, 0, 300, 200, 90, 90));
            show(rounded);

            Window ellipse = new Window((Frame) null);
            ellipse.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 84));
            ellipse.setBackground(new Color(0x00838F));
            ellipse.add(new Button("Heavyweight Button"));
            ellipse.setBounds(inner(cell(area, 8)));
            ellipse.setShape(new Ellipse2D.Double(0, 0, 300, 200));
            show(ellipse);
        }
        if (perPixelTranslucency) {
            Window perPixel = new TranslucentWindow();
            perPixel.setBounds(inner(cell(area, 9)));
            show(perPixel);
        }
        checks.add(Check.info("translucency supported (TRANSLUCENT, PERPIXEL_TRANSPARENT, PERPIXEL_TRANSLUCENT)",
                translucency + " " + shapes + " " + perPixelTranslucency));

        Window mixing = mixingWindow();
        mixing.setBounds(inner(cell(area, 10)));
        show(mixing);

        Rectangle maximizedCell = cell(area, 11);
        Frame states = decoratedFrame(new Rectangle(maximizedCell.x, maximizedCell.y, 240, 160), "Frame states", true);
        states.setMaximizedBounds(new Rectangle(maximizedCell.x + 10, maximizedCell.y + 10, CELL_WIDTH - 20,
                CELL_HEIGHT - 60));
        states.setBounds(inner(maximizedCell).x, inner(maximizedCell).y, 240, 150);
        states.addWindowStateListener(e -> stateLog.add(AwtSupport.describe(e)));
        states.addWindowListener(new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
                stateLog.add(AwtSupport.idName(e));
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                stateLog.add(AwtSupport.idName(e));
            }
        });
        show(states);

        return Edt.rounds(3)
                .thenCompose(v -> state(states, Frame.MAXIMIZED_BOTH, stateLog))
                .thenCompose(v -> state(states, Frame.NORMAL, stateLog))
                .thenCompose(v -> state(states, Frame.ICONIFIED, stateLog))
                .thenCompose(v -> state(states, Frame.NORMAL, stateLog))
                .thenCompose(v -> state(states, Frame.MAXIMIZED_BOTH, stateLog))
                .thenCompose(v -> {
                    List<Check> stateChecks = new ArrayList<>();
                    // the frame states need a window manager supporting them (Linux : not under a bare X server)
                    stateChecks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("WindowStateListener and iconify events",
                            "WINDOW_STATE_CHANGED NORMAL -> MAXIMIZED_BOTH, WINDOW_STATE_CHANGED MAXIMIZED_BOTH -> NORMAL, "
                                    + "WINDOW_ICONIFIED, WINDOW_STATE_CHANGED NORMAL -> ICONIFIED, WINDOW_DEICONIFIED, "
                                    + "WINDOW_STATE_CHANGED ICONIFIED -> NORMAL, WINDOW_STATE_CHANGED NORMAL -> MAXIMIZED_BOTH",
                            () -> String.join(", ", stateLog))));
                    stateChecks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("getExtendedState()", "MAXIMIZED_BOTH",
                            () -> AwtSupport.frameState(states.getExtendedState()))));
                    stateChecks.add(Checks.info("maximized bounds / frame bounds (relative to the cell)", () -> {
                        Rectangle max = states.getMaximizedBounds();
                        Rectangle b = states.getBounds();
                        return (max.x - maximizedCell.x) + "," + (max.y - maximizedCell.y) + " " + max.width + "x" + max.height
                                + " / " + (b.x - maximizedCell.x) + "," + (b.y - maximizedCell.y) + " " + b.width + "x"
                                + b.height;
                    }));
                    stateChecks.add(Checks.expect("icon images", "7 : 16 20 24 32 40 48 64", () -> {
                        List<String> sizes = new ArrayList<>();
                        for (Image icon : iconFrame.getIconImages()) {
                            sizes.add(String.valueOf(icon.getWidth(null)));
                        }
                        return iconFrame.getIconImages().size() + " : " + String.join(" ", sizes);
                    }));
                    stateChecks.add(Checks.expect("owned windows of the first frame (Dialog, POPUP window)", "Dialog POPUP",
                            () -> String.join(" ", List.of(iconFrame.getOwnedWindows()).stream()
                                    .map(w -> w instanceof Dialog ? "Dialog" : w.getType().name()).toList())));
                    stateChecks.add(Checks.expect("always on top", "true true",
                            () -> iconFrame.isAlwaysOnTop() + " " + backdrop.isAlwaysOnTop()));
                    stateChecks.add(Checks.expect("focusable window state (frame, backdrop)", "true false",
                            () -> iconFrame.isFocusableWindow() + " " + backdrop.isFocusableWindow()));
                    statesView.setChecks(stateChecks);
                    // the main window gets the focus back : the title bars of the gallery are inactive in the capture
                    if (previouslyFocused != null && previouslyFocused.isShowing()) {
                        return Focus.acquire(previouslyFocused).thenCompose(attempts -> {
                            checks.add(Check.attempts("page window focused again", attempts));
                            return Edt.delay(1200);
                        });
                    }
                    return Edt.delay(1200);
                })
                .thenCompose(v -> Edt.background(() -> {
                    try (RobotSession robot = RobotSession.open()) {
                        robot.idle();
                        return robot.capture(area);
                    }
                }))
                .thenCompose(image -> {
                    captures.put("gallery", image);
                    checks.addAll(probes(image, area, translucency, shapes, perPixelTranslucency));
                    // WINDOW_CLOSING dispatched to the undecorated frame : its listener disposes it
                    undecorated.dispatchEvent(new WindowEvent(undecorated, WindowEvent.WINDOW_CLOSING));
                    return Edt.until(() -> windowLog.size() >= 3, 3000, "WINDOW_CLOSED").handle((r, e) -> null);
                })
                .thenAccept(v -> {
                    checks.add(Checks.expect("window events of the undecorated frame", "WINDOW_OPENED WINDOW_CLOSING WINDOW_CLOSED",
                            () -> String.join(" ", windowLog)));
                    checks.add(Checks.expect("undecorated frame after WINDOW_CLOSING : displayable", false,
                            undecorated::isDisplayable));
                    galleryView.setChecks(checks);
                });
    }

    private void show(Window window) {
        window.setAutoRequestFocus(false);
        window.setAlwaysOnTop(true);
        windows.add(window);
        window.setVisible(true);
    }

    /**
     * Waits for the WINDOW_STATE_CHANGED event of a state change (at most 3 s).
     */
    private static CompletionStage<Void> state(Frame frame, int state, List<String> log) {
        // the native window reports the new state asynchronously (a WINDOW_STATE_CHANGED event posted by the peer)
        int before = log.size();
        String suffix = "-> " + AwtSupport.frameState(state);
        frame.setExtendedState(state);
        return Edt.until(() -> {
            synchronized (log) {
                return log.subList(before, log.size()).stream().anyMatch(entry -> entry.endsWith(suffix));
            }
        }, 3000, "frame state " + AwtSupport.frameState(state))
                .handle((v, error) -> null)
                .thenCompose(v -> Edt.delay(300));
    }

    private static Rectangle cell(Rectangle area, int index) {
        return new Rectangle(area.x + MARGIN + (index % 4) * CELL_WIDTH, area.y + MARGIN + (index / 4) * CELL_HEIGHT,
                CELL_WIDTH, CELL_HEIGHT);
    }

    /**
     * The 300 x 200 window bounds of a cell.
     */
    private static Rectangle inner(Rectangle cell) {
        return new Rectangle(cell.x + 20, cell.y + 20, 300, 200);
    }

    private static Frame decoratedFrame(Rectangle cell, String title, boolean resizable) {
        Frame frame = new Frame(title);
        frame.setResizable(resizable);
        frame.add(label(title, 0xFFFFFF, 0x263238));
        frame.setBounds(inner(cell));
        return frame;
    }

    private static Component label(String text, int background, int foreground) {
        Panel panel = new Panel(new BorderLayout());
        panel.setBackground(new Color(background));
        Label label = new Label(text, Label.CENTER);
        label.setForeground(new Color(foreground));
        label.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Multi-size icon images (16 to 64 pixels) painted with Java2D : the native peer picks the sizes it needs.
     */
    private static List<Image> icons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] { 16, 20, 24, 32, 40, 48, 64 }) {
            icons.add(Snapshots.offscreen(size, size, g -> {
                AwtSupport.textHints(g);
                g.scale(size / 32.0, size / 32.0);
                g.setColor(new Color(0xE65100));
                g.fill(new RoundRectangle2D.Double(1, 1, 30, 30, 10, 10));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(8, 8, 16, 16));
                g.setColor(new Color(0x1A237E));
                g.fillRect(14, 4, 4, 24);
            }));
        }
        return icons;
    }

    /**
     * Heavyweight/lightweight mixing : two lightweight circles above a heavyweight Button (added first : above it in the
     * z-order). The first one cuts its bounds out of the button (the default for a non-opaque lightweight) and shows
     * through, the second one has an empty mixing cut-out shape ({@code setMixingCutoutShape(new Rectangle())}) and stays
     * hidden behind the button.
     */
    private static Window mixingWindow() {
        Window window = new Window((Frame) null);
        window.setLayout(null);
        window.setBackground(new Color(0xFFFFFF));
        window.add(circle(CUTOUT_COLOR, 40, 50));
        Component hidden = circle(HIDDEN_COLOR, 170, 50);
        hidden.setMixingCutoutShape(new Rectangle());
        window.add(hidden);
        Button button = new Button("Heavyweight Button under lightweights");
        button.setBounds(20, 20, 260, 160);
        window.add(button);
        return window;
    }

    private static Component circle(int rgb, int x, int y) {
        Component circle = new Component() {
            @Override
            public void paint(Graphics g) {
                g.setColor(new Color(rgb));
                g.fillOval(0, 0, getWidth(), getHeight());
            }
        };
        circle.setBounds(x, y, 90, 90);
        return circle;
    }

    /**
     * The opaque window below the others : a solid color, the cell captions.
     */
    static final class Backdrop extends Window {

        Backdrop(Rectangle area) {
            super((Frame) null);
            setFocusableWindowState(false);
            setBounds(area);
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                AwtSupport.textHints(g);
                g.setColor(new Color(BACKDROP));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0x90A4AE));
                g.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
                FontMetrics fm = g.getFontMetrics();
                for (int i = 0; i < CELLS.length; i++) {
                    int x = MARGIN + (i % 4) * CELL_WIDTH;
                    int y = MARGIN + (i / 4) * CELL_HEIGHT;
                    g.drawRect(x + 4, y + 4, CELL_WIDTH - 8, CELL_HEIGHT - 8);
                    g.drawString(CELLS[i], x + (CELL_WIDTH - fm.stringWidth(CELLS[i])) / 2, y + CELL_HEIGHT - 20);
                }
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Per-pixel translucency : a transparent background, a half transparent red band, an opaque circle.
     */
    static final class TranslucentWindow extends Window {

        TranslucentWindow() {
            super((Frame) null);
            setBackground(new Color(0, 0, 0, 0));
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(TRANSLUCENT_RED & 0xFFFFFF | 0x80000000, true));
                g.fillRect(0, 60, 300, 80);
                g.setColor(new Color(OPAQUE_CIRCLE));
                g.fill(new Ellipse2D.Double(190, 40, 100, 100));
            } finally {
                g.dispose();
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------- probes

    private static List<Check> probes(BufferedImage image, Rectangle area, boolean translucency, boolean shapes,
            boolean perPixelTranslucency) {
        List<Check> checks = new ArrayList<>();
        // near the windows, their drop shadows darken the backdrop slightly : 3 levels of tolerance
        checks.add(near("backdrop (outside the windows)", BACKDROP, image, area, cell(area, 6), 8, 272, 3));
        if (!translucency || !shapes || !perPixelTranslucency) {
            checks.add(Check.info("composition probes", "skipped: translucency not supported"));
            return checks;
        }
        // setOpacity : the window color blended over the backdrop
        checks.add(blend("setOpacity(0.6) : window over the backdrop", image, area, cell(area, 6), 30, 30, OPACITY_COLOR,
                OPACITY));
        // setShape : outside the rounded corner the backdrop, inside the window color
        checks.add(near("setShape(RoundRectangle2D) : outside the corner", BACKDROP, image, area, cell(area, 7), 22, 22, 3));
        checks.add(Checks.expect("setShape(RoundRectangle2D) : inside", Checks.argb(0xFF000000 | SHAPE_COLOR),
                () -> pixel(image, area, cell(area, 7), 30, 60)));
        checks.add(near("setShape(Ellipse2D) : outside the ellipse", BACKDROP, image, area, cell(area, 8), 24, 24, 3));
        // per-pixel translucency
        checks.add(Checks.expect("per-pixel translucency : transparent area / opaque circle",
                Checks.argb(0xFF000000 | BACKDROP) + " / " + Checks.argb(0xFF000000 | OPAQUE_CIRCLE),
                () -> pixel(image, area, cell(area, 9), 40, 40) + " / " + pixel(image, area, cell(area, 9), 260, 110)));
        checks.add(blend("per-pixel translucency : 50 % red band", image, area, cell(area, 9), 60, 120, TRANSLUCENT_RED,
                128 / 255f));
        // mixing : the lightweight circle is visible through the cut-out of the heavyweight button
        checks.add(Checks.expect("mixing : lightweight above the button, default cut-out (center)",
                Checks.argb(0xFF000000 | CUTOUT_COLOR), () -> pixel(image, area, cell(area, 10), 20 + 85, 20 + 95)));
        checks.add(Checks.expect("mixing : lightweight with an empty cut-out shape stays hidden", true,
                () -> !pixel(image, area, cell(area, 10), 20 + 215, 20 + 95).equals(Checks.argb(0xFF000000 | HIDDEN_COLOR))));
        return checks;
    }

    private static Check near(String name, int rgb, BufferedImage image, Rectangle area, Rectangle cell, int x, int y,
            int tolerance) {
        int actual = image.getRGB(cell.x - area.x + x, cell.y - area.y + y);
        boolean ok = true;
        for (int shift = 16; shift >= 0; shift -= 8) {
            ok &= Math.abs(((actual >> shift) & 0xFF) - ((rgb >> shift) & 0xFF)) <= tolerance;
        }
        return Check.of(name, ok, ok ? Checks.argb(0xFF000000 | rgb) + " (+-" + tolerance + ")"
                : "expected about " + Checks.argb(0xFF000000 | rgb) + " but got " + Checks.argb(actual));
    }

    private static String pixel(BufferedImage image, Rectangle area, Rectangle cell, int x, int y) {
        return Checks.argb(image.getRGB(cell.x - area.x + x, cell.y - area.y + y));
    }

    /**
     * {@code rgb} with {@code alpha} over the backdrop, within 4 levels per channel (composition rounding).
     */
    private static Check blend(String name, BufferedImage image, Rectangle area, Rectangle cell, int x, int y, int rgb,
            float alpha) {
        int expected = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (rgb >> shift) & 0xFF;
            int b = (BACKDROP >> shift) & 0xFF;
            expected |= Math.round(alpha * c + (1 - alpha) * b) << shift;
        }
        int actual = image.getRGB(cell.x - area.x + x, cell.y - area.y + y);
        boolean ok = true;
        for (int shift = 16; shift >= 0; shift -= 8) {
            ok &= Math.abs(((actual >> shift) & 0xFF) - ((expected >> shift) & 0xFF)) <= 4;
        }
        return Check.of(name, ok, ok ? "blend of " + Checks.argb(0xFF000000 | rgb) + " at " + Checks.num(alpha, 2) + " (+-4)"
                : "expected about " + Checks.argb(expected) + " but got " + Checks.argb(actual));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(captures));
    }

    private void disposeWindows() {
        for (Window window : windows) {
            window.dispose();
        }
        windows.clear();
    }

    @Override
    public void dispose(Component content) {
        disposeWindows();
        captures.clear();
        apiView = null;
        statesView = null;
        modalityView = null;
        galleryView = null;
    }
}
