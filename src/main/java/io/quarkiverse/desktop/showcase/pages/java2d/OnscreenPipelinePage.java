package io.quarkiverse.desktop.showcase.pages.java2d;

import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.SCENE_HEIGHT;
import static io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.SCENE_WIDTH;

import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.ImageCapabilities;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.awt.image.VolatileImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Diff;
import io.quarkiverse.desktop.showcase.pages.java2d.Java2dSupport.Slot;

/**
 * The on-screen pipeline (Direct3D, OpenGL or GDI on Windows, XRender, X11 or GLX on Linux) : the reference scene
 * rendered by the software loops, into an accelerated {@code VolatileImage} of a component, and on screen by
 * heavyweight {@code Canvas}es and a {@code Window} : painted in {@code paint}, and through {@code BufferStrategy}s
 * (the default flip strategy, an unaccelerated blit strategy, the strategy of a window) with several frames shown.
 * <p>
 * The on-screen content is read back with {@code Robot} (capture method B) from two small undecorated, non focusable,
 * always-on-top windows placed at the right edge of the screen (they never take the focus ; the captures are repeated
 * until two consecutive ones are identical), and compared with the software rendering at the device scale. Checks :
 * the graphics configuration and its capabilities, buffer strategy classes and capabilities, frames, lost contents,
 * and the differences between the pipelines (values of this machine : compared between the JVM and a native
 * executable, where a missing native registration shows up as another pipeline, a failure or other pixels).
 */
@Singleton
public class OnscreenPipelinePage implements FeaturePage {

    private static final Logger LOG = Logger.getLogger(OnscreenPipelinePage.class);

    private static final int GAP = 8;
    private static final int[] FRAME_COLORS = { 0xFFE53935, 0xFF43A047, 0xFF1E88E5 };
    private static final String[] TARGETS = { "Canvas.paint", "Canvas BufferStrategy (default)",
            "Canvas BufferStrategy (blit, unaccelerated)", "Window BufferStrategy" };

    // per build state (pages are singletons showing one content at a time)
    private Window canvasWindow;
    private Window strategyWindow;
    private Canvas[] canvases;
    private Slot volatileSlot;
    private Slot volatileDiffSlot;
    private Slot[] captureSlots;
    private Slot[] diffSlots;
    private ChecksView results;
    private ChecksView strategies;
    private BufferedImage software;
    private boolean capturesStable;
    private CompletionStage<?> readyStage;

    @Override
    public String id() {
        return "j2d-onscreen-pipeline";
    }

    @Override
    public String title() {
        return "On-screen pipeline and BufferStrategy";
    }

    @Override
    public String category() {
        return Categories.JAVA2D;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public Component build() {
        software = render(new BufferedImage(SCENE_WIDTH, SCENE_HEIGHT, BufferedImage.TYPE_INT_RGB), 1, 1);
        volatileSlot = new Slot(SCENE_WIDTH, SCENE_HEIGHT);
        volatileDiffSlot = new Slot(SCENE_WIDTH, SCENE_HEIGHT);
        captureSlots = new Slot[TARGETS.length];
        diffSlots = new Slot[TARGETS.length];
        for (int i = 0; i < TARGETS.length; i++) {
            captureSlots[i] = new Slot(SCENE_WIDTH, SCENE_HEIGHT);
            diffSlots[i] = new Slot(SCENE_WIDTH, SCENE_HEIGHT);
        }
        results = ChecksView.table("Pipelines compared with the software rendering", List.of(Check.info("state",
                "pending")));
        strategies = ChecksView.table("BufferStrategy", List.of(Check.info("state", "pending")));

        List<Component> cells = new ArrayList<>();
        cells.add(cell("software loops (TYPE_INT_RGB)", Ui.image(software)));
        cells.add(cell("Component.createVolatileImage", volatileSlot));
        cells.add(cell("difference (red, orange <= 2 levels)", volatileDiffSlot));
        Container offscreen = Ui.row(12, cells.toArray(Component[]::new));
        Container onscreen1 = Ui.row(12, cell(TARGETS[0] + " (on screen)", captureSlots[0]),
                cell("difference", diffSlots[0]), cell(TARGETS[1], captureSlots[1]), cell("difference", diffSlots[1]));
        Container onscreen2 = Ui.row(12, cell(TARGETS[2], captureSlots[2]), cell("difference", diffSlots[2]),
                cell(TARGETS[3], captureSlots[3]), cell("difference", diffSlots[3]));

        Container content = Ui.column(14,
                Ui.text("The reference scene rendered by the software loops, by the accelerated pipeline into a "
                        + "VolatileImage, and on screen by heavyweight canvases and a window (read back with Robot from "
                        + "two small always-on-top windows at the right edge of the screen). Differences : red pixels, "
                        + "orange when at most 2 levels.", 1000),
                ChecksView.table("Screen configuration and pipeline", configurationChecks()),
                Ui.title("Offscreen"),
                offscreen,
                Ui.title("On screen"),
                onscreen1,
                onscreen2,
                results,
                strategies);
        if (!ShowcaseMode.snapshot()) {
            // core.SnapshotRunner is the only caller of ready() : run it once the content is in the window
            ChecksView view = results;
            Edt.rounds(3).thenAccept(v -> {
                if (results == view && content.isDisplayable()) {
                    ready(content);
                }
            });
        }
        return content;
    }

    private static Component cell(String caption, Component image) {
        return Ui.column(4, Ui.caption(caption), image);
    }

    private static BufferedImage render(BufferedImage image, double scaleX, double scaleY) {
        Graphics2D g = image.createGraphics();
        try {
            g.scale(scaleX, scaleY);
            Java2dSupport.scene(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static GraphicsConfiguration screenConfiguration() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }

    private static List<Check> configurationChecks() {
        List<Check> checks = new ArrayList<>();
        GraphicsConfiguration gc = screenConfiguration();
        GraphicsDevice device = gc.getDevice();
        checks.add(Check.info("pipeline", ShowcaseMode.pipeline()));
        checks.add(Checks.info("sun.java2d properties", () -> {
            List<String> values = new ArrayList<>();
            // not dpiaware : quarkus-desktop sets it by default in a native executable (the java launcher does the
            // same through its manifest), the environment of the report shows it (ENV NOTE)
            for (String name : List.of("d3d", "opengl", "noddraw", "xrender", "uiScale")) {
                String value = System.getProperty("sun.java2d." + name);
                if (value != null) {
                    values.add(name + "=" + value);
                }
            }
            return values.isEmpty() ? "none" : String.join(" ", values);
        }));
        checks.add(Checks.expect("GraphicsDevice.getType()", "TYPE_RASTER_SCREEN",
                () -> device.getType() == GraphicsDevice.TYPE_RASTER_SCREEN ? "TYPE_RASTER_SCREEN" : device.getType()));
        checks.add(Checks.info("configurations / default color model", () -> device.getConfigurations().length + " / "
                + gc.getColorModel().getPixelSize() + " bits " + gc.getColorModel().getClass().getSimpleName()));
        checks.add(Checks.info("getColorModel(TRANSLUCENT) / isTranslucencyCapable()", () -> gc.getColorModel(
                java.awt.Transparency.TRANSLUCENT).getPixelSize() + " bits / " + gc.isTranslucencyCapable()));
        checks.add(Checks.info("default transform / normalizing transform", () -> {
            AffineTransform d = gc.getDefaultTransform();
            AffineTransform n = gc.getNormalizingTransform();
            return Checks.num(d.getScaleX(), 2) + "x" + Checks.num(d.getScaleY(), 2) + " / "
                    + Checks.num(n.getScaleX(), 2) + "x" + Checks.num(n.getScaleY(), 2);
        }));
        checks.add(Checks.info("image capabilities : accelerated / true volatile", () -> gc.getImageCapabilities()
                .isAccelerated() + " / " + gc.getImageCapabilities().isTrueVolatile()));
        checks.add(Checks.info("buffer capabilities", () -> describe(gc.getBufferCapabilities())));
        // the value changes with the GPU load (and the Direct3D one wraps around the int range) : only called
        checks.add(Checks.run("getAvailableAcceleratedMemory() (value not shown)", () -> {
            device.getAvailableAcceleratedMemory();
            return "returned";
        }));
        return checks;
    }

    static String describe(BufferCapabilities caps) {
        return "page flipping " + caps.isPageFlipping() + ", flip contents " + caps.getFlipContents()
                + ", full screen required " + caps.isFullScreenRequired() + ", multi-buffer "
                + caps.isMultiBufferAvailable() + ", front accelerated " + caps.getFrontBufferCapabilities().isAccelerated()
                + ", back accelerated " + caps.getBackBufferCapabilities().isAccelerated();
    }

    // ------------------------------------------------------------------------------------------ windows and ready

    /** A canvas painting the scene in {@code paint} (no background clear in {@code update}). */
    static final class SceneCanvas extends Canvas {

        @Override
        public void update(Graphics g) {
            paint(g);
        }

        @Override
        public void paint(Graphics g) {
            Java2dSupport.scene((Graphics2D) g);
        }
    }

    /** A canvas rendered only through its buffer strategy (system repaints ignored). */
    static final class StrategyCanvas extends Canvas {

        StrategyCanvas() {
            setIgnoreRepaint(true);
        }

        @Override
        public void update(Graphics g) {
        }

        @Override
        public void paint(Graphics g) {
        }
    }

    private static Window window(int width, int height) {
        Window window = new Window((Frame) null);
        window.setLayout(null);
        window.setBackground(new Color(0xECEFF1));
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setSize(width, height);
        return window;
    }

    private void openWindows() {
        GraphicsConfiguration gc = screenConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int canvasWidth = 3 * SCENE_WIDTH + 4 * GAP;
        int height = SCENE_HEIGHT + 2 * GAP;
        int strategyWidth = SCENE_WIDTH + 2 * GAP;
        // right edge of the default screen, away from the snapshot windows (top left) and from notifications (bottom)
        int right = screen.x + screen.width - insets.right - 40;
        int y = Math.min(screen.y + insets.top + 600, screen.y + screen.height - insets.bottom - height - 160);
        y -= y % 2;
        int x = right - strategyWidth - GAP * 2 - canvasWidth;
        x -= x % 2;

        canvasWindow = window(canvasWidth, height);
        canvasWindow.setLocation(x, y);
        canvases = new Canvas[] { new SceneCanvas(), new StrategyCanvas(), new StrategyCanvas() };
        for (int i = 0; i < canvases.length; i++) {
            canvases[i].setBounds(GAP + i * (SCENE_WIDTH + GAP), GAP, SCENE_WIDTH, SCENE_HEIGHT);
            canvasWindow.add(canvases[i]);
        }
        strategyWindow = window(strategyWidth, height);
        strategyWindow.setIgnoreRepaint(true);
        strategyWindow.setLocation(x + canvasWidth + GAP * 2, y);
        canvasWindow.setVisible(true);
        strategyWindow.setVisible(true);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        if (readyStage == null) {
            readyStage = start(content);
        }
        return readyStage;
    }

    private CompletionStage<?> start(Component content) {
        List<Check> resultChecks = new ArrayList<>();
        List<Check> strategyChecks = new ArrayList<>();
        try {
            volatileImage(content, resultChecks);
            openWindows();
        } catch (RuntimeException | Error e) {
            resultChecks.add(Check.fail("windows", Checks.describe(e)));
            results.setChecks(resultChecks);
            strategies.setChecks(strategyChecks);
            return CompletableFuture.completedFuture(null);
        }
        ChecksView resultsView = results;
        ChecksView strategiesView = strategies;
        return Edt.until(() -> canvasWindow.isShowing() && strategyWindow.isShowing() && canvases[2].isShowing(), 5000,
                "windows showing")
                .thenCompose(v -> Edt.rounds(3))
                .thenCompose(v -> Edt.delay(300))
                .thenCompose(v -> {
                    renderStrategies(strategyChecks);
                    canvases[0].repaint();
                    return Edt.rounds(2);
                })
                .thenCompose(v -> {
                    Toolkit.getDefaultToolkit().sync();
                    return Edt.delay(300);
                })
                .thenCompose(v -> capture(null, 0))
                .handle((captures, error) -> {
                    if (error != null) {
                        resultChecks.add(Check.fail("on-screen captures", Checks.describe(error)));
                    } else {
                        compare(captures, resultChecks);
                    }
                    resultsView.setChecks(resultChecks);
                    strategiesView.setChecks(strategyChecks);
                    return null;
                });
    }

    /**
     * The scene rendered into {@code content.createVolatileImage} (the accelerated offscreen surface of the pipeline).
     */
    private void volatileImage(Component content, List<Check> checks) {
        String name = "Component.createVolatileImage";
        try {
            VolatileImage image = content.createVolatileImage(SCENE_WIDTH, SCENE_HEIGHT);
            if (image == null) {
                checks.add(Check.fail(name, "null (component not displayable)"));
                return;
            }
            try {
                GraphicsConfiguration gc = content.getGraphicsConfiguration();
                checks.add(Checks.info(name + " : accelerated / true volatile", () -> image.getCapabilities()
                        .isAccelerated() + " / " + image.getCapabilities().isTrueVolatile()));
                BufferedImage snapshot = null;
                for (int attempt = 0; attempt < 5 && snapshot == null; attempt++) {
                    image.validate(gc);
                    Graphics2D g = image.createGraphics();
                    try {
                        Java2dSupport.scene(g);
                    } finally {
                        g.dispose();
                    }
                    BufferedImage s = image.getSnapshot();
                    snapshot = image.contentsLost() ? null : s;
                }
                if (snapshot == null) {
                    checks.add(Check.fail(name, "contents lost 5 times"));
                    return;
                }
                BufferedImage argb = Java2dSupport.toArgb(snapshot, SCENE_WIDTH, SCENE_HEIGHT);
                BufferedImage reference = Java2dSupport.toArgb(software, SCENE_WIDTH, SCENE_HEIGHT);
                volatileSlot.setImage(argb);
                volatileDiffSlot.setImage(Java2dSupport.diffImage(argb, reference));
                checks.add(Checks.info(name + " : probes", () -> Java2dSupport.sceneProbes(argb)));
                checks.add(Checks.info(name + " : versus software", () -> Java2dSupport.diff(argb, reference)));
            } finally {
                image.flush();
            }
            checks.add(Checks.info(name + " with accelerated capabilities", () -> {
                VolatileImage accelerated = content.createVolatileImage(SCENE_WIDTH, SCENE_HEIGHT,
                        new ImageCapabilities(true));
                try {
                    return "accelerated " + accelerated.getCapabilities().isAccelerated();
                } finally {
                    accelerated.flush();
                }
            }));
        } catch (RuntimeException | Error e) {
            checks.add(Check.fail(name, Checks.describe(e)));
        }
    }

    /**
     * Creates the buffer strategies and shows 4 frames on each : 3 solid colors, then the scene.
     */
    private void renderStrategies(List<Check> checks) {
        strategy(checks, TARGETS[1], canvases[1], 0, () -> canvases[1].createBufferStrategy(2));
        // flip with copied contents (supported or not : an AWTException), then an unaccelerated blit strategy
        checks.add(Checks.info("Canvas createBufferStrategy(2, flip COPIED)", () -> {
            try {
                canvases[2].createBufferStrategy(2, new BufferCapabilities(new ImageCapabilities(true),
                        new ImageCapabilities(true), BufferCapabilities.FlipContents.COPIED));
                return strategyName(canvases[2].getBufferStrategy());
            } catch (java.awt.AWTException e) {
                // not supported by the pipeline (e.g. GDI)
                return Checks.describe(e);
            }
        }));
        strategy(checks, TARGETS[2], canvases[2], 0, () -> canvases[2].createBufferStrategy(2,
                new BufferCapabilities(new ImageCapabilities(false), new ImageCapabilities(false), null)));
        strategy(checks, TARGETS[3], strategyWindow, GAP, () -> strategyWindow.createBufferStrategy(2));
    }

    private interface Creation {
        void create() throws Exception;
    }

    private static void strategy(List<Check> checks, String name, Component component, int offset, Creation creation) {
        try {
            creation.create();
            BufferStrategy strategy = component instanceof Canvas canvas ? canvas.getBufferStrategy()
                    : ((Window) component).getBufferStrategy();
            checks.add(Check.info(name + " : strategy", strategyName(strategy)));
            checks.add(Check.info(name + " : capabilities", describe(strategy.getCapabilities())));
            int shown = 0;
            int lost = 0;
            int restored = 0;
            for (int frame = 0; frame <= FRAME_COLORS.length; frame++) {
                int attempts = 0;
                do {
                    do {
                        Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                        try {
                            g.translate(offset, offset);
                            if (frame < FRAME_COLORS.length) {
                                g.setColor(new Color(FRAME_COLORS[frame], true));
                                g.fillRect(0, 0, SCENE_WIDTH, SCENE_HEIGHT);
                            } else {
                                Java2dSupport.scene(g);
                            }
                        } finally {
                            g.dispose();
                        }
                        if (strategy.contentsRestored()) {
                            restored++;
                        }
                    } while (strategy.contentsRestored() && ++attempts < 5);
                    strategy.show();
                    shown++;
                    if (strategy.contentsLost()) {
                        lost++;
                    }
                } while (strategy.contentsLost() && ++attempts < 5);
            }
            checks.add(Check.info(name + " : frames shown / contents lost / restored", shown + " / " + lost + " / "
                    + restored));
        } catch (Exception | Error e) {
            checks.add(Check.fail(name, Checks.describe(e)));
        }
    }

    private static String strategyName(BufferStrategy strategy) {
        if (strategy == null) {
            return "null";
        }
        Class<?> type = strategy.getClass();
        // the java.awt.Component strategies (FlipBufferStrategy, BltBufferStrategy...) or pipeline specific subclasses
        while (type.isAnonymousClass()) {
            type = type.getSuperclass();
        }
        return type.getName().substring(type.getName().lastIndexOf('.') + 1);
    }

    // ------------------------------------------------------------------------------------------------- captures

    /** Screen bounds (device pixels) of the four targets, the scene area of each. */
    private Rectangle[] targetBounds() {
        Point window = strategyWindow.getLocationOnScreen();
        Point[] origins = { canvases[0].getLocationOnScreen(), canvases[1].getLocationOnScreen(),
                canvases[2].getLocationOnScreen(), new Point(window.x + GAP, window.y + GAP) };
        Rectangle[] bounds = new Rectangle[origins.length];
        for (int i = 0; i < origins.length; i++) {
            bounds[i] = new Rectangle(origins[i].x, origins[i].y, SCENE_WIDTH, SCENE_HEIGHT);
        }
        return bounds;
    }

    /**
     * Captures the targets until two consecutive captures are identical (at most 6 times), in the background.
     */
    private CompletionStage<List<BufferedImage>> capture(List<BufferedImage> previous, int attempt) {
        Rectangle[] bounds = targetBounds();
        return Edt.background(() -> {
            Robot robot = new Robot(screenConfiguration().getDevice());
            List<BufferedImage> images = new ArrayList<>();
            for (Rectangle r : bounds) {
                images.add(largest(robot.createMultiResolutionScreenCapture(r)));
            }
            return images;
        }).thenCompose(images -> {
            if (previous != null && same(previous, images)) {
                LOG.debugf("on-screen captures stable after %d attempts", attempt + 1);
                capturesStable = true;
                return CompletableFuture.completedFuture(images);
            }
            if (attempt >= 5) {
                capturesStable = false;
                LOG.warnf("on-screen captures not stable after %d attempts (windows overlapped?)", attempt + 1);
                return CompletableFuture.completedFuture(images);
            }
            canvasWindow.toFront();
            strategyWindow.toFront();
            return Edt.delay(250).thenCompose(v -> capture(images, attempt + 1));
        });
    }

    /** The largest resolution variant (device pixels) as a TYPE_INT_ARGB image. */
    private static BufferedImage largest(MultiResolutionImage capture) {
        java.awt.Image best = null;
        for (java.awt.Image variant : capture.getResolutionVariants()) {
            if (best == null || variant.getWidth(null) > best.getWidth(null)) {
                best = variant;
            }
        }
        return Java2dSupport.toArgb(best, best.getWidth(null), best.getHeight(null));
    }

    private static boolean same(List<BufferedImage> a, List<BufferedImage> b) {
        for (int i = 0; i < a.size(); i++) {
            if (!Checks.sha256(a.get(i)).equals(Checks.sha256(b.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private void compare(List<BufferedImage> captures, List<Check> checks) {
        AffineTransform tx = screenConfiguration().getDefaultTransform();
        for (int i = 0; i < captures.size(); i++) {
            BufferedImage capture = captures.get(i);
            BufferedImage reference = render(new BufferedImage(capture.getWidth(), capture.getHeight(),
                    BufferedImage.TYPE_INT_RGB), tx.getScaleX(), tx.getScaleY());
            BufferedImage referenceArgb = Java2dSupport.toArgb(reference, reference.getWidth(), reference.getHeight());
            captureSlots[i].setImage(capture);
            diffSlots[i].setImage(Java2dSupport.diffImage(capture, referenceArgb));
            Diff diff = Java2dSupport.diff(capture, referenceArgb);
            checks.add(Check.info(TARGETS[i] + " : versus software", diff));
            checks.add(Check.info(TARGETS[i] + " : probes", Java2dSupport.sceneProbes(scaledDown(capture))));
        }
        checks.add(Check.info("on-screen captures stable (two identical captures in a row)", capturesStable));
        checks.add(Checks.info("on-screen Graphics : class / device configuration", () -> {
            Graphics2D g = (Graphics2D) canvases[0].getGraphics();
            try {
                return g.getClass().getSimpleName() + " / " + g.getDeviceConfiguration().getClass().getSimpleName();
            } finally {
                g.dispose();
            }
        }));
        checks.add(Checks.expect("Canvas / Window getBufferStrategy() after show()", "true / true",
                () -> (canvases[1].getBufferStrategy() != null) + " / " + (strategyWindow.getBufferStrategy() != null)));
    }

    /** {@code capture} at scale 1 (nearest neighbor) : probes use the coordinates of the scene. */
    private static BufferedImage scaledDown(BufferedImage capture) {
        if (capture.getWidth() == SCENE_WIDTH) {
            return capture;
        }
        return Java2dSupport.image(SCENE_WIDTH, SCENE_HEIGHT, BufferedImage.TYPE_INT_ARGB, (g, w, h) -> {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(capture, 0, 0, w, h, null);
        });
    }

    @Override
    public void dispose(Component content) {
        for (Window window : new Window[] { canvasWindow, strategyWindow }) {
            if (window != null) {
                window.dispose();
            }
        }
        canvasWindow = null;
        strategyWindow = null;
        canvases = null;
        volatileSlot = null;
        volatileDiffSlot = null;
        captureSlots = null;
        diffSlots = null;
        results = null;
        strategies = null;
        software = null;
        readyStage = null;
    }
}
