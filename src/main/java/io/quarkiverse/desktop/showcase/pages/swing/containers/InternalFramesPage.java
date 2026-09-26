package io.quarkiverse.desktop.showcase.pages.swing.containers;

import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.block;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.captioned;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.column;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.icon;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.rect;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.row;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.section;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.sized;
import static io.quarkiverse.desktop.showcase.pages.swing.containers.SwingKit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.BorderFactory;
import javax.swing.DefaultDesktopManager;
import javax.swing.DesktopManager;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.plaf.basic.BasicInternalFrameUI;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * {@link JDesktopPane} and {@link JInternalFrame} : active and inactive frames with menu bars and frame icons, a
 * palette in the palette layer, an iconified frame (desktop icon), a maximized frame (through the desktop action
 * {@code maximize}), a maximized then restored frame, a closed frame, a frame vetoing its closing, Metal option dialog
 * frame types (error, question, warning borders), and the drag modes : a frame moved and resized through the
 * {@link DesktopManager} in {@code OUTLINE_DRAG_MODE} (the outline is drawn in XOR mode on the on-screen surface) and
 * in {@code LIVE_DRAG_MODE} (the "faster" mode copying screen areas).
 * <p>
 * Every frame is made non focusable before it is selected : selecting an internal frame requests the focus for its
 * content otherwise (the showcase never takes the focus from the user's application). Capture method A, plus an extra
 * image showing the XOR outline drawn over the desktop.
 */
@Singleton
public class InternalFramesPage implements FeaturePage {

    private static final int DESKTOP_WIDTH = 1000;
    private static final int DESKTOP_HEIGHT = 340;
    // int constants : an application class never holds AWT objects in static fields (build time initialization)
    private static final int OUTLINE_START_X = 700;
    private static final int OUTLINE_START_Y = 20;
    private static final int OUTLINE_START_WIDTH = 230;
    private static final int OUTLINE_START_HEIGHT = 120;
    private static final int OUTLINE_X = 720;
    private static final int OUTLINE_Y = 170;
    private static final int OUTLINE_WIDTH = 250;
    private static final int OUTLINE_HEIGHT = 140;

    private State state;

    private static final class State {
        final List<String> events = new ArrayList<>();
        final List<String> manager = new ArrayList<>();
        JDesktopPane main;
        JDesktopPane maximizedDesktop;
        JDesktopPane dialogDesktop;
        JInternalFrame editor;
        JInternalFrame inspector;
        JInternalFrame palette;
        JInternalFrame minimized;
        JInternalFrame outline;
        JInternalFrame closed;
        JInternalFrame vetoing;
        JInternalFrame maximized;
        JInternalFrame behind;
        JInternalFrame restored;
        JInternalFrame live;
        final List<JInternalFrame> dialogs = new ArrayList<>();
        SwingKit.CheckColumns framesTable;
        SwingKit.CheckColumns managerTable;
        BufferedImage xorImage;
        String vetoResult;
    }

    @Override
    public String id() {
        return "swing-internal-frames";
    }

    @Override
    public String title() {
        return "Internal frames";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public Component build() {
        State s = new State();
        state = s;

        // --- the main desktop, OUTLINE drag mode
        JDesktopPane main = desktop(s, 0xDDE6EE);
        main.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
        s.editor = frame(s, main, "Editor", true, new Rectangle(20, 20, 300, 190), 0xE3F2FD);
        JMenuBar menuBar = new JMenuBar();
        for (String name : List.of("File", "Edit", "View")) {
            menuBar.add(new JMenu(name));
        }
        s.editor.setJMenuBar(menuBar);
        s.editor.setFrameIcon(icon(0x1E88E5, SwingKit.DIAMOND, 16));
        s.inspector = frame(s, main, "Inspector", true, new Rectangle(230, 80, 260, 170), 0xFFF3E0);
        s.inspector.setFrameIcon(icon(0xFB8C00, SwingKit.CIRCLE, 16));

        s.palette = new JInternalFrame("Palette", false, true, false, false);
        s.palette.putClientProperty("JInternalFrame.isPalette", Boolean.TRUE);
        JPanel tools = new JPanel(new GridLayout(3, 2, 4, 4));
        tools.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        int[] colors = { 0xE53935, 0x43A047, 0x1E88E5, 0xFDD835, 0x8E24AA, 0x00897B };
        for (int i = 0; i < colors.length; i++) {
            tools.add(new JButton(icon(colors[i], i % 4, 16)));
        }
        s.palette.getContentPane().add(tools);
        s.palette.setBounds(520, 20, 150, 170);
        add(s, main, s.palette, JLayeredPane.PALETTE_LAYER);

        s.minimized = frame(s, main, "Minimized", true, new Rectangle(560, 200, 200, 100), 0xE8F5E9);
        s.outline = frame(s, main, "Outline drag", true,
                new Rectangle(OUTLINE_START_X, OUTLINE_START_Y, OUTLINE_START_WIDTH, OUTLINE_START_HEIGHT), 0xF3E5F5);
        s.closed = frame(s, main, "Closed", true, new Rectangle(380, 30, 120, 80), 0xFFEBEE);
        s.vetoing = frame(s, main, "Vetoes closing", true, new Rectangle(360, 250, 190, 80), 0xFFFDE7);
        s.vetoing.addVetoableChangeListener(e -> {
            if (JInternalFrame.IS_CLOSED_PROPERTY.equals(e.getPropertyName()) && Boolean.TRUE.equals(e.getNewValue())) {
                throw new PropertyVetoException("closing vetoed", e);
            }
        });
        s.main = sized(main, DESKTOP_WIDTH, DESKTOP_HEIGHT);

        // --- maximized frame
        JDesktopPane maximizedDesktop = desktop(s, 0xE0E0E0);
        s.behind = frame(s, maximizedDesktop, "Behind", true, new Rectangle(20, 20, 200, 120), 0xFFF3E0);
        s.maximized = frame(s, maximizedDesktop, "Maximized", true, new Rectangle(60, 40, 220, 140), 0xE0F7FA);
        s.maximizedDesktop = sized(maximizedDesktop, 490, 250);

        // --- option dialog frame types, restored frame, live drag
        JDesktopPane dialogDesktop = desktop(s, 0xECEFF1);
        dialogDesktop.setDragMode(JDesktopPane.LIVE_DRAG_MODE);
        int[] types = { JOptionPane.ERROR_MESSAGE, JOptionPane.QUESTION_MESSAGE, JOptionPane.WARNING_MESSAGE };
        String[] names = { "Error", "Question", "Warning" };
        for (int i = 0; i < types.length; i++) {
            JInternalFrame dialog = new JInternalFrame(names[i], false, true, false, false);
            dialog.putClientProperty("JInternalFrame.frameType", "optionDialog");
            dialog.putClientProperty("JInternalFrame.messageType", types[i]);
            JLabel label = new JLabel(names[i] + " frame type", SwingConstants.CENTER);
            dialog.getContentPane().add(label, BorderLayout.CENTER);
            dialog.setBounds(10 + i * 160, 10, 150, 90);
            add(s, dialogDesktop, dialog, JLayeredPane.DEFAULT_LAYER);
            s.dialogs.add(dialog);
        }
        s.restored = frame(s, dialogDesktop, "Restored", true, new Rectangle(20, 120, 200, 110), 0xF1F8E9);
        s.live = frame(s, dialogDesktop, "Live drag", true, new Rectangle(250, 110, 180, 100), 0xFCE4EC);
        s.dialogDesktop = sized(dialogDesktop, 490, 250);

        s.framesTable = SwingKit.pending("Frames and desktop");
        s.managerTable = SwingKit.pending("Desktop manager, drag modes and events");
        return column(18,
                Ui.heading("Internal frames"),
                Ui.text("JInternalFrames in JDesktopPanes (Metal title panes and borders). States are set once the page "
                        + "is showing : the Editor frame is selected, Minimized is iconified, Outline drag was moved and "
                        + "resized through the desktop manager in OUTLINE drag mode (XOR outline on screen), Closed was "
                        + "closed, closing Vetoes closing was vetoed.", SwingKit.WIDTH),
                section("JDesktopPane, OUTLINE_DRAG_MODE", null, s.main),
                row(20, captioned("maximized with the desktop action 'maximize'", s.maximizedDesktop),
                        captioned("option dialog frame types, restored frame, LIVE_DRAG_MODE", s.dialogDesktop)),
                s.framesTable, s.managerTable);
    }

    private static JDesktopPane desktop(State s, int rgb) {
        JDesktopPane desktop = new JDesktopPane();
        desktop.setBackground(new Color(rgb));
        desktop.setDesktopManager(new LoggingDesktopManager(s.manager));
        return desktop;
    }

    private static JInternalFrame frame(State s, JDesktopPane desktop, String title, boolean all, Rectangle bounds,
            int rgb) {
        JInternalFrame frame = new JInternalFrame(title, all, all, all, all);
        JLabel content = block(title, rgb, 100, 60);
        content.setVerticalAlignment(SwingConstants.TOP);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.getContentPane().add(content, BorderLayout.CENTER);
        frame.setBounds(bounds);
        add(s, desktop, frame, JLayeredPane.DEFAULT_LAYER);
        return frame;
    }

    private static void add(State s, JDesktopPane desktop, JInternalFrame frame, Integer layer) {
        frame.addInternalFrameListener(new EventLog(s.events));
        desktop.add(frame, layer);
        // not showing yet : show() fires INTERNAL_FRAME_OPENED, the selection happens in ready()
        frame.setVisible(true);
        SwingKit.unfocusable(frame);
        SwingKit.unfocusable(frame.getDesktopIcon());
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        return Edt.rounds(2)
                .thenAccept(v -> {
                    s.events.add("-- ready");
                    s.manager.add("-- ready");
                    select(s.inspector);
                    select(s.editor);
                    iconify(s.minimized);
                    outlineDragAndResize(s);
                    close(s.closed);
                    try {
                        s.vetoing.setClosed(true);
                        s.vetoResult = "closed";
                    } catch (PropertyVetoException e) {
                        s.vetoResult = "PropertyVetoException: " + e.getMessage();
                    }
                    select(s.behind);
                    select(s.maximized);
                    SwingKit.perform(s.maximizedDesktop, "maximize", null);
                    maximizeAndRestore(s.restored);
                    liveDrag(s);
                    select(s.dialogs.get(1));
                })
                .thenCompose(v -> Edt.rounds(2))
                .thenAccept(v -> {
                    s.framesTable.setChecks(frameChecks(s));
                    s.managerTable.setChecks(managerChecks(s));
                    // the selection may have changed with the checks : the Editor frame is the selected one
                    select(s.editor);
                    s.xorImage = xorOutline(s);
                })
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        Map<String, BufferedImage> extras = new LinkedHashMap<>();
        if (state.xorImage != null) {
            extras.put("outline-xor", state.xorImage);
        }
        return CompletableFuture.completedFuture(extras);
    }

    @Override
    public void dispose(Component content) {
        state = null;
    }

    private static void select(JInternalFrame frame) {
        try {
            frame.setSelected(true);
        } catch (PropertyVetoException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void iconify(JInternalFrame frame) {
        try {
            frame.setIcon(true);
        } catch (PropertyVetoException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void close(JInternalFrame frame) {
        try {
            frame.setClosed(true);
        } catch (PropertyVetoException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void maximizeAndRestore(JInternalFrame frame) {
        try {
            frame.setMaximum(true);
            frame.setMaximum(false);
        } catch (PropertyVetoException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Moves then resizes the Outline drag frame as the title pane and border listeners do on mouse drags : in OUTLINE
     * mode the desktop manager draws the outline in XOR mode with the on-screen graphics of the desktop, and sets the
     * bounds when the drag ends. The last position is dragged to twice : a first XOR drawing may lose the surface
     * (JDK-6635462), in which case the manager ignores the position.
     */
    private static void outlineDragAndResize(State s) {
        DesktopManager dm = s.main.getDesktopManager();
        JInternalFrame f = s.outline;
        dm.beginDraggingFrame(f);
        dm.dragFrame(f, 710, 90);
        dm.dragFrame(f, OUTLINE_X, OUTLINE_Y);
        dm.dragFrame(f, OUTLINE_X, OUTLINE_Y);
        dm.endDraggingFrame(f);
        dm.beginResizingFrame(f, SwingConstants.SOUTH_EAST);
        dm.resizeFrame(f, OUTLINE_X, OUTLINE_Y, 240, 130);
        dm.resizeFrame(f, OUTLINE_X, OUTLINE_Y, OUTLINE_WIDTH, OUTLINE_HEIGHT);
        dm.resizeFrame(f, OUTLINE_X, OUTLINE_Y, OUTLINE_WIDTH, OUTLINE_HEIGHT);
        dm.endResizingFrame(f);
    }

    private static void liveDrag(State s) {
        DesktopManager dm = s.dialogDesktop.getDesktopManager();
        JInternalFrame f = s.live;
        dm.beginDraggingFrame(f);
        dm.dragFrame(f, 270, 120);
        dm.dragFrame(f, 290, 135);
        dm.endDraggingFrame(f);
    }

    /**
     * The main desktop with the outline of an OUTLINE mode drag drawn in XOR mode (white), as seen during the drag.
     */
    private static BufferedImage xorOutline(State s) {
        BufferedImage image = Snapshots.render(s.main);
        Graphics2D g = image.createGraphics();
        try {
            g.setXORMode(Color.WHITE);
            g.drawRect(OUTLINE_START_X, OUTLINE_START_Y, OUTLINE_START_WIDTH - 1, OUTLINE_START_HEIGHT - 1);
            g.drawRect(OUTLINE_X, OUTLINE_Y, OUTLINE_START_WIDTH - 1, OUTLINE_START_HEIGHT - 1);
            g.drawRect(OUTLINE_X + 1, OUTLINE_Y + 1, OUTLINE_START_WIDTH - 3, OUTLINE_START_HEIGHT - 3);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static String states(JInternalFrame f) {
        return "selected=" + f.isSelected() + " icon=" + f.isIcon() + " maximum=" + f.isMaximum() + " closed="
                + f.isClosed();
    }

    private static List<Check> frameChecks(State s) {
        List<Check> checks = new ArrayList<>();
        JDesktopPane main = s.main;
        checks.add(Checks.expect("main: frames, in palette layer", "6 1",
                () -> main.getAllFrames().length + " " + main.getAllFramesInLayer(JLayeredPane.PALETTE_LAYER).length));
        checks.add(Checks.expect("main: selected frame", "Editor", () -> main.getSelectedFrame().getTitle()));
        checks.add(Checks.expect("Editor: states", "selected=true icon=false maximum=false closed=false",
                () -> states(s.editor)));
        checks.add(Checks.expect("Inspector: states", "selected=false icon=false maximum=false closed=false",
                () -> states(s.inspector)));
        checks.add(Checks.expect("Editor / Inspector: child index (front first)", "1 2",
                () -> main.getIndexOf(s.editor) + " " + main.getIndexOf(s.inspector)));
        checks.add(Checks.expect("Editor: menu bar menus, frame icon", "3 16x16",
                () -> s.editor.getJMenuBar().getMenuCount() + " " + s.editor.getFrameIcon().getIconWidth() + "x"
                        + s.editor.getFrameIcon().getIconHeight()));
        checks.add(Checks.expect("Editor: UI, title pane", "javax.swing.plaf.metal.MetalInternalFrameUI "
                + "javax.swing.plaf.metal.MetalInternalFrameTitlePane", () -> ui(s.editor) + " "
                        + ((BasicInternalFrameUI) s.editor.getUI()).getNorthPane().getClass().getName()));
        checks.add(Checks.info("Editor: title pane / content bounds", () -> rect(((BasicInternalFrameUI) s.editor.getUI())
                .getNorthPane().getBounds()) + " / " + rect(s.editor.getContentPane().getBounds())));
        checks.add(Checks.expect("Editor: title pane buttons", 3,
                () -> SwingKit.findAll(((BasicInternalFrameUI) s.editor.getUI()).getNorthPane(), JButton.class).size()));
        checks.add(Checks.expect("Editor: border", "javax.swing.plaf.metal.MetalBorders$InternalFrameBorder",
                () -> s.editor.getBorder().getClass().getName()));
        checks.add(Checks.expect("Palette: layer, palette property, border", "100 true "
                + "javax.swing.plaf.metal.MetalBorders$PaletteBorder", () -> s.palette.getLayer() + " "
                        + s.palette.getClientProperty("JInternalFrame.isPalette") + " "
                        + s.palette.getBorder().getClass().getName()));
        checks.add(Checks.info("Palette: title pane height",
                () -> ((BasicInternalFrameUI) s.palette.getUI()).getNorthPane().getHeight()));
        checks.add(Checks.expect("Minimized: states", "selected=false icon=true maximum=false closed=false",
                () -> states(s.minimized)));
        checks.add(Checks.expect("Minimized: frame hidden, icon showing", "false true",
                () -> s.minimized.isShowing() + " " + s.minimized.getDesktopIcon().isShowing()));
        checks.add(Checks.info("Minimized: desktop icon bounds", () -> rect(s.minimized.getDesktopIcon().getBounds())));
        checks.add(Checks.expect("Minimized: desktop icon UI", "javax.swing.plaf.metal.MetalDesktopIconUI",
                () -> ui(s.minimized.getDesktopIcon())));
        checks.add(Checks.expect("Closed: states, parent", "selected=false icon=false maximum=false closed=true null",
                () -> states(s.closed) + " " + s.closed.getParent()));
        checks.add(Checks.expect("Vetoes closing: setClosed(true)", "PropertyVetoException: closing vetoed",
                () -> s.vetoResult));
        checks.add(Checks.expect("Vetoes closing: states", "selected=false icon=false maximum=false closed=false",
                () -> states(s.vetoing)));
        checks.add(Checks.expect("Maximized: states, bounds", "selected=true icon=false maximum=true closed=false "
                + "0,0 490x250", () -> states(s.maximized) + " " + rect(s.maximized.getBounds())));
        checks.add(Checks.expect("Maximized: normal bounds", "60,40 220x140", () -> rect(s.maximized.getNormalBounds())));
        checks.add(Checks.expect("Restored: states, bounds", "selected=false icon=false maximum=false closed=false "
                + "20,120 200x110", () -> states(s.restored) + " " + rect(s.restored.getBounds())));
        String[] borders = { "Error", "Question", "Warning" };
        for (int i = 0; i < borders.length; i++) {
            JInternalFrame dialog = s.dialogs.get(i);
            checks.add(Checks.expect(borders[i] + " frame: border", "javax.swing.plaf.metal.MetalBorders$OptionDialogBorder",
                    () -> dialog.getBorder().getClass().getName()));
        }
        checks.add(Checks.expect("dialog desktop: selected frame", "Question",
                () -> s.dialogDesktop.getSelectedFrame().getTitle()));
        checks.add(Checks.expect("main: selectFrame(true) from Editor", "Inspector", () -> {
            JInternalFrame next = main.selectFrame(true);
            return next == null ? "null" : next.getTitle();
        }));
        checks.add(Checks.expect("main: action selectNextFrame from Inspector", "Vetoes closing", () -> {
            SwingKit.perform(main, "selectNextFrame", null);
            return main.getSelectedFrame().getTitle();
        }));
        checks.add(Checks.expect("main: desktop UI, drag mode", "javax.swing.plaf.basic.BasicDesktopPaneUI 1",
                () -> ui(main) + " " + main.getDragMode()));
        checks.add(Checks.expect("main: bindings ctrl F4 / ctrl F10 / ctrl F6 (ancestor of focused)",
                "close / maximize / selectNextFrame", () -> {
                    javax.swing.InputMap map = main.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
                    return map.get(KeyStroke.getKeyStroke("ctrl F4")) + " / "
                            + map.get(KeyStroke.getKeyStroke("ctrl F10")) + " / "
                            + map.get(KeyStroke.getKeyStroke("ctrl F6"));
                }));
        // MetalInternalFrameUI removes the showSystemMenu action BasicInternalFrameUI.loadActionMap put (no system menu)
        checks.add(Checks.expect("Editor: showSystemMenu action (removed by Metal once loaded)", false,
                () -> s.editor.getActionMap().get("showSystemMenu") != null));
        return checks;
    }

    private static List<Check> managerChecks(State s) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("Outline drag: bounds after the OUTLINE drag and resize",
                rect(new Rectangle(OUTLINE_X, OUTLINE_Y, OUTLINE_WIDTH, OUTLINE_HEIGHT)), () -> rect(s.outline.getBounds())));
        checks.add(Checks.expect("Live drag: bounds after the LIVE drag", "290,135 180x100", () -> rect(s.live.getBounds())));
        checks.add(Checks.expect("desktop manager class", LoggingDesktopManager.class.getSimpleName(),
                () -> s.main.getDesktopManager().getClass().getSimpleName()));
        int ready = s.manager.indexOf("-- ready");
        List<String> calls = ready < 0 ? s.manager : s.manager.subList(ready + 1, s.manager.size());
        int chunk = 8;
        for (int i = 0; i < calls.size(); i += chunk) {
            List<String> part = calls.subList(i, Math.min(calls.size(), i + chunk));
            checks.add(Check.info("desktop manager calls " + (i + 1) + "-" + (i + part.size()), String.join(", ", part)));
        }
        List<String> before = s.events.subList(0, Math.max(0, s.events.indexOf("-- ready")));
        checks.add(Check.info("internal frame events while building", String.join(", ", before)));
        List<String> after = s.events.subList(s.events.indexOf("-- ready") + 1, s.events.size());
        for (int i = 0; i < after.size(); i += chunk) {
            List<String> part = after.subList(i, Math.min(after.size(), i + chunk));
            checks.add(Check.info("internal frame events " + (i + 1) + "-" + (i + part.size()), String.join(", ", part)));
        }
        return checks;
    }

    /**
     * Logs the internal frame events : {@code <title> <event>}.
     */
    private record EventLog(List<String> log) implements InternalFrameListener {

        private void add(InternalFrameEvent e, String name) {
            log.add(e.getInternalFrame().getTitle() + " " + name);
        }

        @Override
        public void internalFrameOpened(InternalFrameEvent e) {
            add(e, "opened");
        }

        @Override
        public void internalFrameClosing(InternalFrameEvent e) {
            add(e, "closing");
        }

        @Override
        public void internalFrameClosed(InternalFrameEvent e) {
            add(e, "closed");
        }

        @Override
        public void internalFrameIconified(InternalFrameEvent e) {
            add(e, "iconified");
        }

        @Override
        public void internalFrameDeiconified(InternalFrameEvent e) {
            add(e, "deiconified");
        }

        @Override
        public void internalFrameActivated(InternalFrameEvent e) {
            add(e, "activated");
        }

        @Override
        public void internalFrameDeactivated(InternalFrameEvent e) {
            add(e, "deactivated");
        }
    }

    /**
     * The default desktop manager, logging its calls ({@code dragFrame}/{@code resizeFrame} calls are counted by the
     * {@code end*} entries).
     */
    private static final class LoggingDesktopManager extends DefaultDesktopManager {

        private final List<String> log;
        private int drags;

        LoggingDesktopManager(List<String> log) {
            this.log = log;
        }

        private static String title(JComponent c) {
            return c instanceof JInternalFrame f ? f.getTitle() : c.getClass().getSimpleName();
        }

        @Override
        public void openFrame(JInternalFrame f) {
            log.add("open " + f.getTitle());
            super.openFrame(f);
        }

        @Override
        public void closeFrame(JInternalFrame f) {
            log.add("close " + f.getTitle());
            super.closeFrame(f);
        }

        @Override
        public void maximizeFrame(JInternalFrame f) {
            log.add("maximize " + f.getTitle());
            super.maximizeFrame(f);
        }

        @Override
        public void minimizeFrame(JInternalFrame f) {
            log.add("minimize " + f.getTitle());
            super.minimizeFrame(f);
        }

        @Override
        public void iconifyFrame(JInternalFrame f) {
            log.add("iconify " + f.getTitle());
            super.iconifyFrame(f);
        }

        @Override
        public void deiconifyFrame(JInternalFrame f) {
            log.add("deiconify " + f.getTitle());
            super.deiconifyFrame(f);
        }

        @Override
        public void activateFrame(JInternalFrame f) {
            log.add("activate " + f.getTitle());
            super.activateFrame(f);
        }

        @Override
        public void deactivateFrame(JInternalFrame f) {
            log.add("deactivate " + f.getTitle());
            super.deactivateFrame(f);
        }

        @Override
        public void beginDraggingFrame(JComponent f) {
            drags = 0;
            super.beginDraggingFrame(f);
        }

        @Override
        public void dragFrame(JComponent f, int newX, int newY) {
            drags++;
            super.dragFrame(f, newX, newY);
        }

        @Override
        public void endDraggingFrame(JComponent f) {
            log.add("drag " + title(f) + " x" + drags);
            super.endDraggingFrame(f);
        }

        @Override
        public void beginResizingFrame(JComponent f, int direction) {
            drags = 0;
            super.beginResizingFrame(f, direction);
        }

        @Override
        public void resizeFrame(JComponent f, int newX, int newY, int newWidth, int newHeight) {
            drags++;
            super.resizeFrame(f, newX, newY, newWidth, newHeight);
        }

        @Override
        public void endResizingFrame(JComponent f) {
            log.add("resize " + title(f) + " x" + drags);
            super.endResizingFrame(f);
        }
    }
}
