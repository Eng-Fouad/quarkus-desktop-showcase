package io.quarkiverse.desktop.showcase.pages.swing.desktop;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JToolBar;
import javax.swing.WindowConstants;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.desktop.MacDesktop;

/**
 * The macOS window properties (macOS only) : the client properties of a {@code JRootPane} that
 * {@code sun.lwawt.macosx.CPlatformWindow} applies to the native window (transparent title bar, full size content,
 * hidden title, document modified dot and proxy icon, small title bar, shadow, alpha, close / minimize / zoom buttons,
 * full screen button, textured "brush metal" background, draggable background). A plain frame and a frame with the
 * properties are shown next to the main window, their content is rendered as extra snapshots (the title bar itself is
 * drawn by macOS : check it on screen). The sheet ({@code apple.awt.documentModalSheet}) and the full screen animation
 * need the user : see the manual checks of the README.
 */
@Singleton
public class MacWindowsPage implements FeaturePage {

    /** The JRootPane client properties of CPlatformWindow with the value the page sets, in order. */
    static Map<String, Object> properties(File document) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("apple.awt.transparentTitleBar", Boolean.TRUE);
        p.put("apple.awt.fullWindowContent", Boolean.TRUE);
        p.put("apple.awt.windowTitleVisible", Boolean.FALSE);
        p.put("Window.documentModified", Boolean.TRUE);
        p.put("Window.documentFile", document);
        p.put("Window.shadow", Boolean.TRUE);
        p.put("Window.alpha", 0.97f);
        p.put("Window.closeable", Boolean.TRUE);
        p.put("Window.minimizable", Boolean.TRUE);
        p.put("Window.zoomable", Boolean.FALSE);
        p.put("Window.hidesOnDeactivate", Boolean.FALSE);
        p.put("apple.awt.fullscreenable", Boolean.TRUE);
        p.put("apple.awt.brushMetalLook", Boolean.TRUE);
        p.put("apple.awt.draggableWindowBackground", Boolean.FALSE);
        return p;
    }

    // per build state
    private final List<JFrame> frames = new ArrayList<>();
    private ChecksView results;

    @Override
    public String id() {
        return "desktop-mac-windows";
    }

    @Override
    public String title() {
        return "macOS window properties";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 210;
    }

    @Override
    public Component build() throws Exception {
        if (!Platforms.isMac()) {
            return MacDesktop.notAvailable("macOS window properties",
                    "The window client properties of the macOS toolkit (transparent title bar, full size content, "
                            + "document modified...) only apply on macOS.",
                    List.of());
        }
        File document = Edt.tempDir().resolve("document.txt").toFile();
        List<Check> checks = new ArrayList<>();

        JFrame plain = frame("Plain window", 1480, 100);
        JFrame styled = frame("Styled window", 1480, 360);
        JRootPane root = styled.getRootPane();
        // read when the native window is created
        root.putClientProperty("Window.style", "small");
        JToolBar toolbar = new JToolBar();
        toolbar.add(new JLabel("Textured tool bar"));
        styled.add(toolbar, BorderLayout.NORTH);
        plain.setVisible(true);
        styled.setVisible(true);
        // the others are applied to the native window when they change
        properties(document).forEach((key, value) -> checks.add(Checks.run("putClientProperty " + key, () -> {
            root.putClientProperty(key, value);
            return value instanceof File ? "a file" : value;
        })));
        properties(document).forEach((key, value) -> checks.add(Checks.expect("getClientProperty " + key,
                value instanceof File ? "a file" : value, () -> {
                    Object v = root.getClientProperty(key);
                    return v instanceof File ? "a file" : v;
                })));
        checks.add(Checks.expect("getClientProperty Window.style", "small", () -> root.getClientProperty("Window.style")));
        // the window insets are read in ready(), once stable
        checks.add(Checks.expect("styled window isResizable()", true, styled::isResizable));
        results = ChecksView.table("macOS window properties", checks, 380, ChecksView.WIDTH);
        return Ui.column(14, Ui.heading("macOS window properties"),
                Ui.text("Two frames next to the main window : a plain one, and one with the JRootPane client properties "
                        + "of the macOS toolkit (small transparent title bar without title, full size content, document "
                        + "modified dot and proxy icon, no zoom button, textured tool bar). Their title bars are drawn by "
                        + "macOS : look at them on screen.", 1000),
                results);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        if (frames.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        JFrame plain = frames.get(0);
        JFrame styled = frames.get(1);
        // CPlatformWindow applies the client properties to the native window on the AppKit thread, without waiting : the
        // insets change a little later (full size content : top 0), read them once they no longer change
        return Edt.untilStable(() -> insets(plain.getInsets()) + " / " + insets(styled.getInsets()), 300, 3000,
                "stable window insets").handle((v, error) -> null).thenRun(() -> {
                    List<Check> checks = new ArrayList<>(results.getChecks());
                    checks.add(Checks.info("plain window insets", () -> insets(plain.getInsets())));
                    checks.add(Checks.info("styled window insets (full size content : top 0 expected)",
                            () -> insets(styled.getInsets())));
                    results.setChecks(checks);
                }).thenCompose(v -> Edt.rounds(3));
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        Map<String, BufferedImage> extras = new LinkedHashMap<>();
        for (int i = 0; i < frames.size(); i++) {
            extras.put(i == 0 ? "plain" : "styled", Snapshots.render(frames.get(i).getContentPane()));
        }
        return CompletableFuture.completedFuture(extras);
    }

    @Override
    public void dispose(Component content) {
        frames.forEach(JFrame::dispose);
        frames.clear();
        results = null;
    }

    private JFrame frame(String title, int x, int y) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setAutoRequestFocus(!ShowcaseMode.snapshot());
        frame.add(new JLabel("  " + title + " : content"), BorderLayout.CENTER);
        frame.setBounds(x, y, 360, 220);
        frames.add(frame);
        return frame;
    }

    private static String insets(Insets insets) {
        return insets.top + ", " + insets.left + ", " + insets.bottom + ", " + insets.right;
    }
}
