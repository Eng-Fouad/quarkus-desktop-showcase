package io.quarkiverse.desktop.showcase.pages.desktop;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.im.InputContext;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import io.quarkiverse.desktop.showcase.core.Keys;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.RobotSession;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * {@code java.awt.Robot} : screen captures of the page's own components (single and multi-resolution),
 * {@code getPixelColor}, mouse input (move, the three buttons, double click, drag, wheel) recorded by a lightweight pad,
 * and keyboard input typed into an AWT {@code TextField}.
 * <p>
 * AWT only. Needs the focus : keys are only pressed while a showcase window is focused, the mouse buttons only over the
 * page (its pixels are verified on screen first), and the mouse pointer is moved back afterwards.
 */
@Singleton
public class RobotPage implements FeaturePage {

    private static final int[] COLORS = { 0xC62828, 0x2E7D32, 0x1565C0, 0xF9A825 };
    private static final int SQUARE = 70;
    private static final int PAD_COLOR = 0xE3F2FD;
    private static final int PAD_WIDTH = 360;
    private static final int PAD_HEIGHT = 140;

    // per build state
    private Pattern pattern;
    private Pad pad;
    private TextField field;
    private ChecksView robotView;
    private List<String> keys;
    private BufferedImage capture;

    @Override
    public String id() {
        return "desktop-robot";
    }

    @Override
    public String title() {
        return "Robot";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
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
    public Component build() throws AWTException {
        pattern = new Pattern();
        pad = new Pad();
        field = new TextField(24);
        field.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        keys = Collections.synchronizedList(new ArrayList<>());
        List<String> keyLog = keys;
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                keyLog.add(KeyEvent.getKeyText(e.getKeyCode()));
            }
        });

        List<Check> api = new ArrayList<>();
        Robot robot = new Robot();
        api.add(Checks.expect("setAutoDelay / getAutoDelay", 15, () -> {
            robot.setAutoDelay(15);
            return robot.getAutoDelay();
        }));
        api.add(Checks.expect("setAutoWaitForIdle / isAutoWaitForIdle", true, () -> {
            robot.setAutoWaitForIdle(true);
            return robot.isAutoWaitForIdle();
        }));
        api.add(DesktopSupport.expectThrows("delay(-1)", IllegalArgumentException.class, () -> {
            robot.delay(-1);
            return "delayed";
        }));
        api.add(DesktopSupport.expectThrows("delay(60001)", IllegalArgumentException.class, () -> {
            robot.delay(60001);
            return "delayed";
        }));
        api.add(DesktopSupport.expectThrows("waitForIdle() on the event dispatch thread",
                IllegalThreadStateException.class, () -> {
                    robot.waitForIdle();
                    return "waited";
                }));
        api.add(DesktopSupport.expectThrows("mousePress(invalid button mask)", IllegalArgumentException.class, () -> {
            robot.mousePress(1 << 30);
            return "pressed";
        }));
        api.add(Checks.info("Robot per screen device", () -> {
            GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            for (GraphicsDevice device : devices) {
                new Robot(device);
            }
            return devices.length + " created";
        }));
        api.add(Checks.expect("InputEvent.getMaskForButton(1..3)", "1024 2048 4096",
                () -> InputEvent.getMaskForButton(1) + " " + InputEvent.getMaskForButton(2) + " "
                        + InputEvent.getMaskForButton(3)));
        api.add(Checks.info("MouseInfo.getNumberOfButtons()", MouseInfo::getNumberOfButtons));

        robotView = ChecksView.table("Robot input and capture", List.of(Check.info("state", "pending")));
        return Ui.column(14,
                Ui.text("Robot captures the colored squares from the screen, reads pixels, moves the mouse and clicks, "
                        + "drags and scrolls over the pad, and types into the text field. The captured image is compared "
                        + "as an extra snapshot.", 1000),
                Ui.row(24,
                        Ui.column(4, Ui.caption("captured with Robot"), pattern),
                        Ui.column(4, Ui.caption("mouse pad (events recorded)"), pad),
                        Ui.column(4, Ui.caption("java.awt.TextField (typed with Robot)"), field)),
                ChecksView.table("Robot API", api),
                robotView);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = robotView;
        Pattern p = pattern;
        Pad m = pad;
        TextField f = field;
        List<String> keyLog = keys;
        return Edt.rounds(3).thenCompose(v -> Edt.delay(300))
                .thenCompose(v -> Edt.background(() -> run(p, m, f, keyLog)))
                .thenAccept(view::setChecks);
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        BufferedImage image = capture != null ? capture
                : new BufferedImage(4 * SQUARE, SQUARE, BufferedImage.TYPE_INT_ARGB);
        return CompletableFuture.completedFuture(Map.of("capture", image));
    }

    @Override
    public void dispose(Component content) {
        pattern = null;
        pad = null;
        field = null;
        robotView = null;
        keys = null;
        capture = null;
    }

    // ------------------------------------------------------------------------------------------------ Robot run

    private List<Check> run(Pattern pattern, Pad pad, TextField field, List<String> keyLog) throws Exception {
        List<Check> checks = new ArrayList<>();
        Point saved;
        try (RobotSession session = RobotSession.open()) {
            saved = session.savedPointer();
            Robot robot = session.robot();
            Rectangle bounds = DesktopSupport.onEdt(() -> new Rectangle(pattern.getLocationOnScreen(),
                    pattern.getSize()));
            Map<Point, Integer> probes = new LinkedHashMap<>();
            for (int i = 0; i < COLORS.length; i++) {
                probes.put(new Point(bounds.x + i * SQUARE + SQUARE / 2, bounds.y + SQUARE / 2), COLORS[i]);
            }
            Point padOrigin = DesktopSupport.onEdt(pad::getLocationOnScreen);
            probes.put(new Point(padOrigin.x + 6, padOrigin.y + 6), PAD_COLOR);
            probes.put(new Point(padOrigin.x + PAD_WIDTH - 6, padOrigin.y + PAD_HEIGHT - 6), PAD_COLOR);
            if (!session.ensureFocus(pattern)) {
                checks.add(Check.info("Robot input and capture", DesktopSupport.NOT_FOCUSED));
                return checks;
            }
            if (!session.awaitVisible(pattern, probes)) {
                checks.add(Check.info("Robot input and capture", "skipped: the page is not visible on screen"));
                return checks;
            }
            captures(checks, robot, bounds);
            // the mouse sequence, then the keyboard, again (at most ATTEMPTS times, the window focused again first) while
            // an event is missing : another application may take the foreground at any time
            List<Check> mouseChecks = new ArrayList<>();
            boolean mouseDone = false;
            int attempt = 0;
            while (!complete(mouseChecks) && attempt < ATTEMPTS && session.ensureFocus(pad)) {
                attempt++;
                if (attempt > 1) {
                    RobotSession.logRetry("desktop-robot mouse", attempt - 1, failed(mouseChecks));
                }
                mouseChecks = new ArrayList<>();
                mouseDone = mouse(mouseChecks, session, pad, padOrigin);
            }
            checks.add(Check.attempts("mouse", attempt));
            checks.addAll(mouseChecks);
            if (attempt == 0) {
                checks.add(Check.info("mouse buttons", DesktopSupport.NOT_FOCUSED));
            }
            if (mouseDone) {
                List<Check> keyChecks = new ArrayList<>();
                attempt = 0;
                while (!complete(keyChecks) && attempt < ATTEMPTS && session.ensureFocus(field)) {
                    attempt++;
                    if (attempt > 1) {
                        RobotSession.logRetry("desktop-robot keyboard", attempt - 1, failed(keyChecks));
                    }
                    keyChecks = new ArrayList<>();
                    keyboard(keyChecks, session, field, keyLog);
                }
                checks.add(Check.attempts("keyboard", attempt));
                checks.addAll(keyChecks);
                if (attempt == 0) {
                    checks.add(Check.info("keyboard", DesktopSupport.NOT_FOCUSED));
                }
            }
        }
        checks.add(Checks.expect("mouse pointer moved back", true, () -> saved != null
                && DesktopSupport.await(() -> saved.equals(MouseInfo.getPointerInfo().getLocation()), 1000)));
        return checks;
    }

    /** Attempts of the mouse and of the keyboard sequences. */
    private static final int ATTEMPTS = 3;

    /**
     * The failed checks and skipped inputs of {@code checks}, as {@code name = value} (for the log).
     */
    private static List<String> failed(List<Check> checks) {
        return checks.stream().filter(c -> Boolean.FALSE.equals(c.ok()) || c.value().startsWith("skipped"))
                .map(c -> c.name() + " = " + c.value()).toList();
    }

    /**
     * {@code true} when {@code checks} is not empty, has no failed check and no skipped input.
     */
    private static boolean complete(List<Check> checks) {
        return !checks.isEmpty() && checks.stream().noneMatch(c -> Boolean.FALSE.equals(c.ok())
                || c.value().startsWith("skipped"));
    }

    private void captures(List<Check> checks, Robot robot, Rectangle bounds) {
        BufferedImage image = robot.createScreenCapture(bounds);
        capture = image;
        List<String> expected = new ArrayList<>();
        List<String> captured = new ArrayList<>();
        List<String> pixels = new ArrayList<>();
        for (int i = 0; i < COLORS.length; i++) {
            int x = i * SQUARE + SQUARE / 2;
            expected.add(DesktopSupport.rgb(COLORS[i]));
            captured.add(DesktopSupport.rgb(RobotSession.snap(image.getRGB(x, SQUARE / 2), COLORS[i])));
            pixels.add(DesktopSupport.rgb(
                    RobotSession.snap(robot.getPixelColor(bounds.x + x, bounds.y + SQUARE / 2).getRGB(), COLORS[i])));
        }
        checks.add(Checks.expect("createScreenCapture: size", bounds.width + "x" + bounds.height,
                () -> image.getWidth() + "x" + image.getHeight()));
        checks.add(Checks.expect("createScreenCapture: square colors", expected, () -> captured));
        checks.add(Checks.expect("getPixelColor: square colors", expected, () -> pixels));
        checks.add(Checks.expect("createScreenCapture: pixels equal the painted pattern", true,
                () -> RobotSession.sameImage(image, pattern(bounds.width, bounds.height))));
        MultiResolutionImage multi = robot.createMultiResolutionScreenCapture(bounds);
        checks.add(Checks.info("createMultiResolutionScreenCapture: variants", () -> {
            List<String> sizes = new ArrayList<>();
            for (Image variant : multi.getResolutionVariants()) {
                sizes.add(variant.getWidth(null) + "x" + variant.getHeight(null));
            }
            return sizes;
        }));
        checks.add(Checks.expect("multi-resolution capture: base variant color", DesktopSupport.rgb(COLORS[0]), () -> {
            Image base = multi.getResolutionVariant(bounds.width, bounds.height);
            BufferedImage b = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = b.createGraphics();
            try {
                g.drawImage(base, 0, 0, bounds.width, bounds.height, null);
            } finally {
                g.dispose();
            }
            // a screen color : within the tolerance of the display color profile on macOS, like the checks above
            return DesktopSupport.rgb(RobotSession.snap(b.getRGB(SQUARE / 2, SQUARE / 2), COLORS[0]));
        }));
    }

    /**
     * @return {@code false} when the input was skipped (no showcase window focused)
     */
    private static boolean mouse(List<Check> checks, RobotSession session, Pad pad, Point origin)
            throws Exception {
        Point target = new Point(origin.x + 60, origin.y + 40);
        session.move(new Point(target.x - 20, target.y - 10));
        DesktopSupport.sleep(50);
        session.move(target);
        checks.add(Checks.expect("mouse move: MOUSE_MOVED at", "60,40",
                () -> DesktopSupport.await(() -> pad.last("moved").equals("60,40"), 2000) ? "60,40" : pad.last("moved")));
        checks.add(Checks.expect("MouseInfo.getPointerInfo()", "pointer on the pad, default device", () -> {
            PointerInfo info = MouseInfo.getPointerInfo();
            return (info.getLocation().equals(target) ? "pointer on the pad" : "pointer at " + info.getLocation())
                    + (info.getDevice() == GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                            ? ", default device"
                            : ", another device");
        }));
        pad.clear();
        if (!click(session, InputEvent.BUTTON1_DOWN_MASK)) {
            checks.add(Check.info("mouse buttons", DesktopSupport.NOT_FOCUSED));
            return false;
        }
        checks.add(Checks.expect("button 1 click", "pressed b1 x1 Button1, released b1 x1, clicked b1 x1",
                () -> pad.awaitButtons("clicked b1")));
        // longer than the double click interval : the next click starts a new sequence
        DesktopSupport.sleep(multiClickInterval() + 150);
        pad.clear();
        click(session, InputEvent.BUTTON1_DOWN_MASK);
        click(session, InputEvent.BUTTON1_DOWN_MASK);
        checks.add(Checks.expect("button 1 double click", "pressed b1 x1 Button1, released b1 x1, clicked b1 x1, "
                + "pressed b1 x2 Button1, released b1 x2, clicked b1 x2", () -> pad.awaitButtons("clicked b1 x2")));
        pad.clear();
        newClickSequence(session, target);
        click(session, InputEvent.BUTTON2_DOWN_MASK);
        checks.add(Checks.expect("button 2 click", "pressed b2 x1 Button2, released b2 x1, clicked b2 x1",
                () -> pad.awaitButtons("clicked b2")));
        pad.clear();
        newClickSequence(session, target);
        click(session, InputEvent.BUTTON3_DOWN_MASK);
        checks.add(Checks.expect("button 3 click", "pressed b3 x1 Button3, released b3 x1, clicked b3 x1",
                () -> pad.awaitButtons("clicked b3")));
        checks.add(Checks.expect("button 3 is the popup trigger (on press or release)", true, () -> pad.popupTrigger));

        pad.clear();
        session.wheel(2);
        session.wheel(-1);
        // Windows sends one event per call, X11 one per notch (wheel buttons) : the sums of both directions
        String wheel = wheelSummary();
        checks.add(Checks.expect("mouse wheel: rotations down 2, up 1", wheel, () -> {
            DesktopSupport.await(() -> pad.wheelSummary().equals(wheel), 2000);
            return pad.wheelSummary();
        }));
        checks.add(Checks.info("mouse wheel: scroll type and amount", () -> pad.wheelType));

        pad.clear();
        Point end = new Point(origin.x + 160, origin.y + 90);
        if (!session.press(InputEvent.BUTTON1_DOWN_MASK)) {
            checks.add(Check.info("mouse drag", DesktopSupport.NOT_FOCUSED));
            return false;
        }
        session.glide(target, end, 10, 15);
        session.release(InputEvent.BUTTON1_DOWN_MASK);
        checks.add(Checks.expect("mouse drag: MOUSE_DRAGGED, released at the end", "dragged true, released 160,90, "
                + "clicked false", () -> {
                    DesktopSupport.await(() -> !pad.last("released").isEmpty(), 2000);
                    return "dragged " + !pad.last("dragged").isEmpty() + ", released " + pad.last("released")
                            + ", clicked " + pad.buttons().contains("clicked");
                }));
        checks.add(Checks.expect("mouse drag: last MOUSE_DRAGGED", "160,90", () -> pad.last("dragged")));
        return true;
    }

    /**
     * The sums of the rotations of the {@code MouseWheelEvent}s of {@code Robot.mouseWheel(2)} then
     * {@code mouseWheel(-1)}, down (positive) then up : {@code +2 -1}, inverted on macOS ({@code +1 -2}). There
     * {@code CRobot.m mouseWheel} posts {@code CGEventCreateScrollWheelEvent(..., kCGScrollEventUnitLine, 1, wheelAmt)},
     * where a positive delta scrolls up, and {@code CPlatformResponder.dispatchScrollEvent} makes the rotation of the
     * event the opposite of the delta of the scroll event : {@code mouseWheel(n)} gives a rotation of {@code -n} (one
     * event per call). The JDK test {@code java/awt/Robot/RobotWheelTest} expects that sign on macOS
     * ({@code wheelSign = Platform.isOSX() ? -1 : 1}).
     */
    private static String wheelSummary() {
        return Platforms.isMac() ? "+1 -2" : "+2 -1";
    }

    /**
     * Makes the next click the first one of a new click sequence (click count 1) on macOS : {@code CRobot.m} gives every
     * synthetic click one shared click count, whatever the button ({@code gsClickCount}, incremented by a press within
     * {@code [NSEvent doubleClickInterval]} of the previous press), reset by any mouse movement
     * ({@code gsLastClickTime = 0}) : a Robot move to the same point. Without it, the button 2 click after the double
     * click of button 1 has the click count 3, the button 3 click 4. Windows and Linux count the clicks of each button
     * apart : nothing to do.
     */
    private static void newClickSequence(RobotSession session, Point target) {
        if (Platforms.isMac()) {
            session.move(target);
        }
    }

    private static int multiClickInterval() {
        Object interval = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        return interval instanceof Integer i ? Math.min(i, 2000) : 500;
    }

    private static boolean click(RobotSession session, int mask) {
        if (!session.press(mask)) {
            return false;
        }
        session.release(mask);
        return true;
    }

    private static void keyboard(List<Check> checks, RobotSession session, TextField field,
            List<String> keyLog) throws Exception {
        boolean focused = DesktopSupport.onEdt(() -> {
            // a new attempt starts from an empty field
            field.setText("");
            return field.requestFocusInWindow();
        });
        boolean owner = DesktopSupport.await(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusOwner() == field, 2000);
        if (!focused || !owner) {
            checks.add(Check.info("keyboard", DesktopSupport.NOT_FOCUSED + " (text field)"));
            return;
        }
        keyLog.clear();
        int[] word = { KeyEvent.VK_Q, KeyEvent.VK_U, KeyEvent.VK_A, KeyEvent.VK_R, KeyEvent.VK_K, KeyEvent.VK_U,
                KeyEvent.VK_S, KeyEvent.VK_SPACE };
        boolean typed = true;
        for (int key : word) {
            typed &= session.type(key);
        }
        typed &= session.keyPress(KeyEvent.VK_SHIFT);
        for (int key : new int[] { KeyEvent.VK_A, KeyEvent.VK_W, KeyEvent.VK_T }) {
            typed &= session.type(key);
        }
        session.keyRelease(KeyEvent.VK_SHIFT);
        typed &= session.type(KeyEvent.VK_BACK_SPACE);
        if (!typed) {
            checks.add(Check.info("keyboard", DesktopSupport.NOT_FOCUSED));
            return;
        }
        DesktopSupport.await(() -> keyLog.size() >= 13, 3000);
        DesktopSupport.sleep(100);
        // the key names of the platform (symbols on macOS : Keys)
        checks.add(Checks.expect("key presses (KeyEvent.getKeyText)", "Q U A R K U S " + Keys.text("Space") + " "
                + Keys.text("Shift") + " A W T " + Keys.text("Backspace"),
                () -> {
                    synchronized (keyLog) {
                        return String.join(" ", keyLog);
                    }
                }));
        // the characters depend on the keyboard layout of the user : verified for an English input locale only
        Locale input = DesktopSupport.onEdt(() -> {
            InputContext context = field.getInputContext();
            return context == null ? null : context.getLocale();
        });
        checks.add(Check.info("input locale (InputContext.getLocale)", input == null ? "none" : input.toLanguageTag()));
        boolean english = input != null && input.getLanguage().equals("en");
        checks.add(english ? Checks.expect("text typed", "quarkus AW", () -> DesktopSupport.onEdt(field::getText))
                : Checks.info("text typed (keyboard layout of the input locale)",
                        () -> DesktopSupport.onEdt(field::getText)));
    }

    // -------------------------------------------------------------------------------------------- components

    private static BufferedImage pattern(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            for (int i = 0; i < COLORS.length; i++) {
                g.setColor(new Color(COLORS[i]));
                g.fillRect(i * SQUARE, 0, SQUARE, SQUARE);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Four solid squares, no anti-aliasing : the screen capture must be pixel exact.
     */
    private static final class Pattern extends Component {

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(COLORS.length * SQUARE, SQUARE);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics g) {
            g.drawImage(pattern(getWidth(), getHeight()), 0, 0, null);
        }
    }

    /**
     * Records the mouse events it receives (EDT writes, background thread reads).
     */
    private static final class Pad extends Component {

        private final List<String> buttonEvents = Collections.synchronizedList(new ArrayList<>());
        private final List<Integer> wheel = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, String> last = Collections.synchronizedMap(new LinkedHashMap<>());
        private volatile boolean popupTrigger;
        private volatile String wheelType = "none";

        Pad() {
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    button("pressed", e, true);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    button("released", e, false);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    button("clicked", e, false);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    last.put("moved", e.getX() + "," + e.getY());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    last.put("dragged", e.getX() + "," + e.getY());
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    wheel.add(e.getWheelRotation());
                    wheelType = (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? "unit" : "block")
                            + " scroll, amount " + e.getScrollAmount();
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
            addMouseWheelListener(adapter);
        }

        private void button(String kind, MouseEvent e, boolean modifiers) {
            if (e.isPopupTrigger()) {
                popupTrigger = true;
            }
            String text = kind + " b" + e.getButton() + " x" + e.getClickCount();
            if (modifiers) {
                text += " " + InputEvent.getModifiersExText(e.getModifiersEx());
            }
            buttonEvents.add(text);
            last.put(kind, e.getX() + "," + e.getY());
        }

        void clear() {
            buttonEvents.clear();
            wheel.clear();
            last.remove("released");
            last.remove("dragged");
        }

        String last(String kind) {
            return last.getOrDefault(kind, "");
        }

        String buttons() {
            synchronized (buttonEvents) {
                return String.join(", ", buttonEvents);
            }
        }

        String awaitButtons(String until) {
            DesktopSupport.await(() -> buttons().contains(until), 2000);
            DesktopSupport.sleep(50);
            return buttons();
        }

        String wheelSummary() {
            synchronized (wheel) {
                int down = wheel.stream().mapToInt(Integer::intValue).filter(r -> r > 0).sum();
                int up = wheel.stream().mapToInt(Integer::intValue).filter(r -> r < 0).sum();
                return "+" + down + " " + up;
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(PAD_WIDTH, PAD_HEIGHT);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(PAD_COLOR));
                g.fillRect(0, 0, PAD_WIDTH, PAD_HEIGHT);
                g.setColor(new Color(0x90CAF9));
                g.drawRect(0, 0, PAD_WIDTH - 1, PAD_HEIGHT - 1);
                g.drawLine(60, 30, 60, 50);
                g.drawLine(50, 40, 70, 40);
                g.drawLine(150, 90, 170, 90);
                g.drawLine(160, 80, 160, 100);
            } finally {
                g.dispose();
            }
        }
    }
}
