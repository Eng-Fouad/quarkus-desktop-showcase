package io.quarkiverse.desktop.showcase.pages.datatransfer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.desktop.DesktopSupport;

/**
 * AWT drag and drop : a {@code DragGestureRecognizer} on a lightweight source of tokens, a {@code DragSource} drag with
 * a drag image and a {@code DragSourceListener}/{@code DragSourceMotionListener}, and two {@code DropTarget}s : a
 * lightweight bin (retargeted by the heavyweight ancestor) and a heavyweight {@code java.awt.List}. Two drags are
 * driven by Robot (the operating system drag loop : OLE on Windows, XDnD on Linux) : a move without modifier and a copy
 * with Ctrl, retried when a drag does not end with a drop.
 * <p>
 * AWT only. Needs the focus (Robot input) : the mouse pointer is moved back afterwards, the Robot only presses the
 * mouse button over the page's own components after checking their pixels on screen.
 */
@Singleton
public class DragAndDropPage implements FeaturePage {

    private static final String[] TOKENS = { "A", "B", "C" };
    private static final int[] TOKEN_COLORS = { 0xE53935, 0x1E88E5, 0x43A047 };
    private static final int TOKEN_SIZE = 80;
    private static final int TOKEN_GAP = 20;
    private static final int BIN_COLOR = 0xFFF8E1;
    private static final int SOURCE_COLOR = 0xECEFF1;

    // per build state
    private TokenSource source;
    private TokenBin bin;
    private java.awt.List list;
    private ChecksView robotView;
    private DragLog log;
    private DragSourceMotionListener motionListener;

    @Override
    public String id() {
        return "dt-dnd";
    }

    @Override
    public String title() {
        return "Drag and drop (AWT)";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean needsFocus() {
        return true;
    }

    @Override
    public Component build() {
        log = new DragLog();
        source = new TokenSource(log);
        bin = new TokenBin();
        list = new java.awt.List(5);
        list.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        list.setPreferredSize(new Dimension(200, 120));

        DragSource dragSource = DragSource.getDefaultDragSource();
        DragGestureRecognizer recognizer = dragSource.createDefaultDragGestureRecognizer(source,
                DnDConstants.ACTION_COPY_OR_MOVE, source);
        DropTarget binTarget = new DropTarget(bin, DnDConstants.ACTION_COPY_OR_MOVE, new TargetListener("bin", log,
                bin::drop));
        DropTarget listTarget = new DropTarget(list, DnDConstants.ACTION_COPY_OR_MOVE, new TargetListener("list", log,
                list::add));

        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("DragSource.getDefaultDragSource()", true, () -> dragSource != null));
        checks.add(Checks.info("DragSource.isDragImageSupported()", DragSource::isDragImageSupported));
        checks.add(Checks.info("DragSource.getDragThreshold()", DragSource::getDragThreshold));
        checks.add(Checks.expect("DragSource.getFlavorMap() is the SystemFlavorMap", true,
                () -> dragSource.getFlavorMap() == SystemFlavorMap.getDefaultFlavorMap()));
        checks.add(Checks.info("drag cursors (DnD.Cursor.* desktop properties)", () -> String.join(", ",
                cursorName(DragSource.DefaultCopyDrop), cursorName(DragSource.DefaultMoveDrop),
                cursorName(DragSource.DefaultLinkDrop), cursorName(DragSource.DefaultCopyNoDrop),
                cursorName(DragSource.DefaultMoveNoDrop), cursorName(DragSource.DefaultLinkNoDrop))));
        checks.add(Checks.info("desktop property DnD.gestureMotionThreshold",
                () -> Toolkit.getDefaultToolkit().getDesktopProperty("DnD.gestureMotionThreshold")));
        checks.add(Checks.info("drag gesture recognizer", () -> recognizer.getClass().getName()));
        checks.add(Checks.expect("recognizer source actions and component", "3 true",
                () -> recognizer.getSourceActions() + " " + (recognizer.getComponent() == source)));
        checks.add(Checks.expect("DropTarget active, default actions, component", "true 3 true true",
                () -> binTarget.isActive() + " " + binTarget.getDefaultActions() + " " + (binTarget.getComponent() == bin)
                        + " " + (listTarget.getComponent() == list)));
        checks.add(Checks.expect("DropTarget.setActive(false) then true", "false true", () -> {
            binTarget.setActive(false);
            boolean inactive = binTarget.isActive();
            binTarget.setActive(true);
            return inactive + " " + binTarget.isActive();
        }));
        checks.add(Checks.expect("Component.getDropTarget()", true, () -> bin.getDropTarget() == binTarget));
        checks.add(Checks.expect("DnDConstants COPY MOVE COPY_OR_MOVE LINK REFERENCE", "1 2 3 1073741824 1073741824",
                () -> DnDConstants.ACTION_COPY + " " + DnDConstants.ACTION_MOVE + " " + DnDConstants.ACTION_COPY_OR_MOVE
                        + " " + DnDConstants.ACTION_LINK + " " + DnDConstants.ACTION_REFERENCE));
        checks.add(Checks.expect("token flavor", "application/x-showcase-token; class=java.lang.String",
                () -> tokenFlavor().getMimeType()));

        robotView = ChecksView.table("Robot-driven drags", List.of(Check.info("state", "pending")));
        return Ui.column(14,
                Ui.text("Drag a token from the source to the bin (move) or to the list (copy with Ctrl). The source uses "
                        + "a DragGestureRecognizer and starts the drag with a drag image ; the bin is a lightweight "
                        + "drop target, the list a heavyweight one. In snapshot mode, Robot performs both drags.", 1000),
                Ui.row(24,
                        Ui.column(4, Ui.caption("DragSource (lightweight)"), source),
                        Ui.column(4, Ui.caption("DropTarget : lightweight bin"), bin),
                        Ui.column(4, Ui.caption("DropTarget : java.awt.List (heavyweight)"), list)),
                ChecksView.table("Drag and drop API", checks),
                robotView);
    }

    private static String cursorName(Cursor cursor) {
        return cursor == null ? "null" : cursor.getName();
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = robotView;
        DragLog dragLog = log;
        motionListener = e -> dragLog.motion = true;
        DragSource.getDefaultDragSource().addDragSourceMotionListener(motionListener);
        TokenSource tokens = source;
        TokenBin target = bin;
        java.awt.List targetList = list;
        // wait for the layout and the on-screen painting of the source
        return Edt.rounds(3).thenCompose(v -> Edt.delay(300))
                .thenCompose(v -> Edt.background(() -> drags(tokens, target, targetList, dragLog)))
                .thenAccept(view::setChecks)
                .thenCompose(v -> Edt.rounds(2));
    }

    @Override
    public void dispose(Component content) {
        if (motionListener != null) {
            DragSource.getDefaultDragSource().removeDragSourceMotionListener(motionListener);
        }
        motionListener = null;
        source = null;
        bin = null;
        list = null;
        robotView = null;
        log = null;
    }

    // --------------------------------------------------------------------------------------------- Robot drags

    private record DragPlan(Point from, Point to, java.util.Map<Point, Integer> probes, Component component) {
    }

    private static List<Check> drags(TokenSource source, TokenBin bin, java.awt.List list, DragLog log)
            throws Exception {
        List<Check> checks = new ArrayList<>();
        boolean done;
        try (DesktopSupport.RobotSession robot = new DesktopSupport.RobotSession()) {
            done = drag(checks, robot, log, "drag 1 (move to the bin)", 0, false, () -> plan(source, 0, bin, bin))
                    && drag(checks, robot, log, "drag 2 (copy to the list, Ctrl)", 1, true,
                            () -> plan(source, 1, bin, list));
        }
        if (!done) {
            // the drags did not run (focus refused, window covered) : the final state is not verified
            return checks;
        }
        checks.add(Checks.expect("tokens left in the source", "B C", () -> DesktopSupport.onEdt(source::tokens)));
        checks.add(Checks.expect("bin contents", "A", () -> DesktopSupport.onEdt(bin::contents)));
        checks.add(Checks.expect("list items", "[B]", () -> DesktopSupport.onEdt(() -> List.of(list.getItems())
                .toString())));
        checks.add(Checks.expect("DragSourceMotionListener notified", true, () -> log.motion));
        return checks;
    }

    private static DragPlan plan(TokenSource source, int token, TokenBin bin, Component target) {
        Point from = DesktopSupport.onScreen(source, source.tokenX(token) + TOKEN_SIZE / 2, 20 + TOKEN_SIZE / 2);
        Point to = DesktopSupport.center(target);
        // pixels of the token, of the source and of the bin : the page must be visible on screen
        java.util.Map<Point, Integer> probes = new java.util.LinkedHashMap<>();
        probes.put(DesktopSupport.onScreen(source, source.tokenX(token) + TOKEN_SIZE / 2, 28), TOKEN_COLORS[token]);
        probes.put(DesktopSupport.onScreen(source, 4, 4), SOURCE_COLOR);
        probes.put(DesktopSupport.onScreen(bin, bin.getWidth() - 6, 6), BIN_COLOR);
        return new DragPlan(from, to, probes, source);
    }

    /**
     * Performs one drag (up to 3 attempts).
     *
     * @return {@code false} when the drag was skipped
     */
    private static boolean drag(List<Check> checks, DesktopSupport.RobotSession robot, DragLog log, String name,
            int token, boolean copy, java.util.concurrent.Callable<DragPlan> planner) throws Exception {
        String outcome = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            log.reset();
            DragPlan plan = DesktopSupport.onEdt(planner);
            robot.move(plan.from());
            DesktopSupport.sleep(150);
            if (!robot.ensureFocus(plan.component())) {
                outcome = DesktopSupport.NOT_FOCUSED;
                break;
            }
            if (!robot.awaitVisible(plan.component(), plan.probes())) {
                outcome = "skipped: the page is not visible on screen";
                continue;
            }
            if (copy && !robot.keyPress(KeyEvent.VK_CONTROL)) {
                outcome = DesktopSupport.NOT_FOCUSED;
                break;
            }
            if (!robot.press(InputEvent.BUTTON1_DOWN_MASK)) {
                robot.releaseAll();
                outcome = DesktopSupport.NOT_FOCUSED;
                break;
            }
            // beyond the drag threshold first, then to the target
            Point start = new Point(plan.from().x + 16, plan.from().y + 4);
            robot.glide(plan.from(), start, 8, 20);
            robot.glide(start, plan.to(), 24, 15);
            DesktopSupport.sleep(200);
            robot.release(InputEvent.BUTTON1_DOWN_MASK);
            robot.releaseAll();
            DesktopSupport.await(() -> log.ended, 4000);
            outcome = null;
            if (log.ended && log.success) {
                break;
            }
            if (log.started && !log.ended) {
                // a slow drop (seen at 150 %) : wait longer. A drag that never ends (e.g. the native drag and drop
                // callbacks are missing) is not retried : another attempt would only fail with "Drag and drop in
                // progress", the checks below show the state reached
                DesktopSupport.await(() -> log.ended, 4000);
                if (!log.ended) {
                    break;
                }
                if (log.success) {
                    break;
                }
            }
            DesktopSupport.sleep(300);
        }
        if (outcome != null) {
            checks.add(Check.info(name, outcome));
            return false;
        }
        synchronized (log) {
            checks.add(Checks.expect(name + ": DragSourceDropEvent", "success " + (copy ? "COPY" : "MOVE"),
                    () -> (log.success ? "success " : "failure ") + action(log.dropAction)));
            checks.add(Checks.expect(name + ": data dropped", TOKENS[token], () -> log.data));
            checks.add(Checks.expect(name + ": DropTargetDropEvent",
                    "action " + (copy ? "COPY" : "MOVE") + ", source actions COPY_OR_MOVE, local transfer true",
                    () -> log.dropEvent));
            checks.add(Checks.expect(name + ": flavors of the drop", "[application/x-java-serialized-object; "
                    + "class=java.lang.String, application/x-showcase-token; class=java.lang.String]",
                    () -> log.dropFlavors));
            checks.add(Check.info(name + ": DragSourceListener events", DesktopSupport.collapse(log.sourceEvents)));
            checks.add(Check.info(name + ": DropTargetListener events", DesktopSupport.collapse(log.targetEvents)));
            checks.add(Checks.expect(name + ": drag image", DragSource.isDragImageSupported() ? "used" : "unsupported",
                    () -> log.dragImage));
        }
        return true;
    }

    static String action(int action) {
        return switch (action) {
            case DnDConstants.ACTION_NONE -> "NONE";
            case DnDConstants.ACTION_COPY -> "COPY";
            case DnDConstants.ACTION_MOVE -> "MOVE";
            case DnDConstants.ACTION_COPY_OR_MOVE -> "COPY_OR_MOVE";
            case DnDConstants.ACTION_LINK -> "LINK";
            default -> String.valueOf(action);
        };
    }

    static DataFlavor tokenFlavor() {
        return ClipboardData.flavor("application/x-showcase-token; class=java.lang.String");
    }

    // ------------------------------------------------------------------------------------------ event recording

    /**
     * The events of the current drag (EDT writes, background thread reads under the lock).
     */
    static final class DragLog {

        final List<String> sourceEvents = Collections.synchronizedList(new ArrayList<>());
        final List<String> targetEvents = Collections.synchronizedList(new ArrayList<>());
        volatile boolean started;
        volatile boolean ended;
        volatile boolean success;
        volatile boolean motion;
        int dropAction;
        String data;
        String dropEvent;
        String dropFlavors;
        String dragImage = "none";

        synchronized void reset() {
            sourceEvents.clear();
            targetEvents.clear();
            started = false;
            ended = false;
            success = false;
            dropAction = 0;
            data = null;
            dropEvent = null;
            dropFlavors = null;
        }
    }

    private static final class TargetListener implements DropTargetListener {

        private final String name;
        private final DragLog log;
        private final java.util.function.Consumer<String> sink;

        TargetListener(String name, DragLog log, java.util.function.Consumer<String> sink) {
            this.name = name;
            this.log = log;
            this.sink = sink;
        }

        @Override
        public void dragEnter(DropTargetDragEvent e) {
            log.targetEvents.add(name + " dragEnter");
            if (e.isDataFlavorSupported(tokenFlavor())) {
                e.acceptDrag(e.getDropAction());
            } else {
                e.rejectDrag();
            }
        }

        @Override
        public void dragOver(DropTargetDragEvent e) {
            log.targetEvents.add(name + " dragOver");
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent e) {
            log.targetEvents.add(name + " dropActionChanged");
        }

        @Override
        public void dragExit(DropTargetEvent e) {
            log.targetEvents.add(name + " dragExit");
        }

        @Override
        public void drop(DropTargetDropEvent e) {
            log.targetEvents.add(name + " drop");
            try {
                e.acceptDrop(e.getDropAction());
                Transferable t = e.getTransferable();
                String token = (String) t.getTransferData(tokenFlavor());
                synchronized (log) {
                    log.data = token;
                    log.dropEvent = "action " + action(e.getDropAction()) + ", source actions "
                            + action(e.getSourceActions()) + ", local transfer " + e.isLocalTransfer();
                    log.dropFlavors = ClipboardData.mimeTypes(e.getCurrentDataFlavors()).toString();
                }
                sink.accept(token);
                e.dropComplete(true);
            } catch (Exception ex) {
                synchronized (log) {
                    log.data = Checks.describe(ex);
                }
                e.dropComplete(false);
            }
        }
    }

    /**
     * The transferable of a token : a custom flavor and the string flavor.
     */
    private static final class TokenTransferable implements Transferable {

        private final String token;

        TokenTransferable(String token) {
            this.token = token;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { tokenFlavor(), DataFlavor.stringFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(tokenFlavor()) || flavor.equals(DataFlavor.stringFlavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
            }
            return token;
        }
    }

    // ------------------------------------------------------------------------------------------------ components

    private static void paintToken(Graphics2D g, String token, int color, int x, int y, int size) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(color));
        g.fill(new RoundRectangle2D.Float(x, y, size, size, 18, 18));
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, size / 3));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(token, x + (size - fm.stringWidth(token)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
    }

    /**
     * The drag source : tokens A, B and C ; a moved token disappears.
     */
    private static final class TokenSource extends Component implements DragGestureListener, DragSourceListener {

        private final List<String> tokens = new ArrayList<>(List.of(TOKENS));
        private final DragLog log;

        TokenSource(DragLog log) {
            this.log = log;
        }

        int tokenX(int index) {
            return TOKEN_GAP + index * (TOKEN_SIZE + TOKEN_GAP);
        }

        String tokens() {
            return String.join(" ", tokens);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(TOKEN_GAP + TOKENS.length * (TOKEN_SIZE + TOKEN_GAP), TOKEN_SIZE + 40);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(SOURCE_COLOR));
                g.fillRect(0, 0, getWidth(), getHeight());
                for (int i = 0; i < TOKENS.length; i++) {
                    if (tokens.contains(TOKENS[i])) {
                        paintToken(g, TOKENS[i], TOKEN_COLORS[i], tokenX(i), 20, TOKEN_SIZE);
                    } else {
                        g.setColor(new Color(0xB0BEC5));
                        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10,
                                new float[] { 6, 4 }, 0));
                        g.draw(new RoundRectangle2D.Float(tokenX(i), 20, TOKEN_SIZE, TOKEN_SIZE, 18, 18));
                    }
                }
            } finally {
                g.dispose();
            }
        }

        @Override
        public void dragGestureRecognized(DragGestureEvent e) {
            Point origin = e.getDragOrigin();
            for (int i = 0; i < TOKENS.length; i++) {
                if (tokens.contains(TOKENS[i]) && origin.x >= tokenX(i) && origin.x < tokenX(i) + TOKEN_SIZE
                        && origin.y >= 20 && origin.y < 20 + TOKEN_SIZE) {
                    Transferable t = new TokenTransferable(TOKENS[i]);
                    log.started = true;
                    if (DragSource.isDragImageSupported()) {
                        log.dragImage = "used";
                        e.startDrag(null, dragImage(i), new Point(-24, -24), t, this);
                    } else {
                        e.startDrag(null, t, this);
                    }
                    return;
                }
            }
        }

        private static Image dragImage(int index) {
            BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
                paintToken(g, TOKENS[index], TOKEN_COLORS[index], 0, 0, 48);
            } finally {
                g.dispose();
            }
            return image;
        }

        @Override
        public void dragEnter(DragSourceDragEvent e) {
            log.sourceEvents.add("dragEnter");
        }

        @Override
        public void dragOver(DragSourceDragEvent e) {
            log.sourceEvents.add("dragOver");
        }

        @Override
        public void dropActionChanged(DragSourceDragEvent e) {
            log.sourceEvents.add("dropActionChanged");
        }

        @Override
        public void dragExit(DragSourceEvent e) {
            log.sourceEvents.add("dragExit");
        }

        @Override
        public void dragDropEnd(DragSourceDropEvent e) {
            log.sourceEvents.add("dragDropEnd");
            String token = null;
            try {
                token = (String) e.getDragSourceContext().getTransferable().getTransferData(tokenFlavor());
            } catch (Exception ex) {
                // not a token
            }
            if (e.getDropSuccess() && e.getDropAction() == DnDConstants.ACTION_MOVE && token != null) {
                tokens.remove(token);
                repaint();
            }
            synchronized (log) {
                log.success = e.getDropSuccess();
                log.dropAction = e.getDropAction();
            }
            log.ended = true;
        }
    }

    /**
     * The lightweight drop target : shows the dropped tokens.
     */
    private static final class TokenBin extends Component {

        private final List<String> dropped = new ArrayList<>();

        void drop(String token) {
            dropped.add(token);
            repaint();
        }

        String contents() {
            return String.join(" ", dropped);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(260, TOKEN_SIZE + 40);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(BIN_COLOR));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0xFFB300));
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                for (int i = 0; i < dropped.size(); i++) {
                    int index = List.of(TOKENS).indexOf(dropped.get(i));
                    paintToken(g, dropped.get(i), TOKEN_COLORS[Math.max(0, index)], 20 + i * 60, 36, 48);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
