package io.quarkiverse.desktop.showcase.pages.images;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.net.URL;
import java.util.ArrayList;
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
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.desktop.MacDesktop;

/**
 * The macOS images of the toolkit (macOS only) : {@code Toolkit.getImage("NSImage://<name>")} gives the named images of
 * AppKit ({@code sun.lwawt.macosx.CImage}), and {@code Toolkit.getImage(URL)} loads {@code name@2x.png} next to
 * {@code name.png} as a {@code MultiResolutionImage} (the 1x icon is blue, the 2x one red : the extra snapshot at scale
 * 2 must show the red one). quarkus-awt's macOS substitution of {@code LWCToolkit} disables both : quarkus-desktop
 * removes it. The named images depend on the macOS version (their sizes are informational). On other operating systems
 * the page only states that it is not available. AWT only.
 */
@Singleton
public class MacImagesPage implements FeaturePage {

    /** Names of AppKit images (NSImageName constants) ; the JDK itself uses NSImage://NSSecurity. */
    static final List<String> NS_IMAGE_NAMES = List.of("NSApplicationIcon", "NSComputer", "NSFolder", "NSTrashEmpty",
            "NSTrashFull", "NSNetwork", "NSUser", "NSUserGroup", "NSEveryone", "NSCaution", "NSInfo", "NSSecurity",
            "NSActionTemplate", "NSAddTemplate", "NSRemoveTemplate", "NSStopProgressTemplate", "NSRefreshTemplate");

    private static final String ICON = "/showcase/images/icon-mr.png";
    private static final int CELL = 48;

    // per build state
    private final Map<String, Image> images = new LinkedHashMap<>();
    private Image multiResolution;
    private ChecksView results;
    private Component grid;

    @Override
    public String id() {
        return "desktop-mac-nsimage";
    }

    @Override
    public String title() {
        return "macOS images (NSImage, @2x)";
    }

    @Override
    public String category() {
        return Categories.IMAGES;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public Component build() {
        if (!Platforms.isMac()) {
            return MacDesktop.notAvailable("macOS images",
                    "The NSImage:// names and the automatic @2x images of Toolkit.getImage only exist on macOS.",
                    List.of());
        }
        images.clear();
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (String name : NS_IMAGE_NAMES) {
            images.put(name, toolkit.getImage("NSImage://" + name));
        }
        URL icon = Edt.resource(ICON);
        multiResolution = toolkit.getImage(icon);
        results = ChecksView.table("Images", List.of(Check.info("loading", "pending")), 330, ChecksView.WIDTH);
        grid = Ui.painted(NS_IMAGE_NAMES.size() / 2 * CELL + CELL, 2 * CELL + CELL, this::paintGrid);
        return Ui.column(14, Ui.heading("macOS images"),
                Ui.text("The named images of AppKit (Toolkit.getImage(\"NSImage://NSFolder\")...), and an icon with a "
                        + "@2x variant loaded as a multi resolution image : blue at 1x, red at 2x (extra snapshot).",
                        1000),
                grid, results);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        if (results == null) {
            return CompletableFuture.completedFuture(null);
        }
        ChecksView view = results;
        Component tracked = grid;
        return Edt.background(() -> {
            MediaTracker tracker = new MediaTracker(tracked);
            int id = 0;
            for (Image image : images.values()) {
                tracker.addImage(image, id++);
            }
            tracker.addImage(multiResolution, id);
            tracker.waitForAll(10_000);
            return checks();
        }).thenAccept(checks -> {
            view.setChecks(checks);
            grid.repaint();
        });
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        if (grid == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return CompletableFuture.completedFuture(Map.of("2x", Snapshots.render(grid, 2)));
    }

    @Override
    public void dispose(Component content) {
        images.clear();
        multiResolution = null;
        results = null;
        grid = null;
    }

    private List<Check> checks() {
        List<Check> checks = new ArrayList<>();
        for (Map.Entry<String, Image> e : images.entrySet()) {
            Image image = e.getValue();
            String size = image.getWidth(null) + "x" + image.getHeight(null);
            if (e.getKey().equals("NSApplicationIcon") || e.getKey().equals("NSFolder")) {
                checks.add(Check.of("NSImage://" + e.getKey(), image.getWidth(null) > 0 && image.getHeight(null) > 0,
                        size));
            } else {
                checks.add(Check.info("NSImage://" + e.getKey(), size));
            }
        }
        checks.add(Checks.expect("getImage(URL of icon-mr.png) is a MultiResolutionImage", true,
                () -> multiResolution instanceof MultiResolutionImage));
        checks.add(Checks.expect("resolution variants", "32x32 64x64", () -> {
            if (!(multiResolution instanceof MultiResolutionImage mr)) {
                return "not a multi resolution image";
            }
            List<String> sizes = new ArrayList<>();
            for (Image variant : mr.getResolutionVariants()) {
                sizes.add(variant.getWidth(null) + "x" + variant.getHeight(null));
            }
            return String.join(" ", sizes);
        }));
        checks.add(Checks.expect("base size", "32x32",
                () -> multiResolution.getWidth(null) + "x" + multiResolution.getHeight(null)));
        return checks;
    }

    private void paintGrid(Graphics2D g) {
        int i = 0;
        for (Image image : images.values()) {
            int x = (i % (NS_IMAGE_NAMES.size() / 2 + 1)) * CELL;
            int y = (i / (NS_IMAGE_NAMES.size() / 2 + 1)) * CELL;
            g.drawImage(image, x + 8, y + 8, 32, 32, null);
            i++;
        }
        if (multiResolution != null) {
            g.drawImage(multiResolution, 8, 2 * CELL + 8, 32, 32, null);
        }
    }
}
