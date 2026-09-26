package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.JComponent;
import javax.swing.JFileChooser;

import org.jboss.logging.Logger;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Base of the look and feel pages : saves the look and feel state before {@link #buildPage()} and restores it in
 * {@link #dispose(Component)} (or when the build fails), disposes the windows the page opened, and provides the
 * standard sections every look and feel page shows for its gallery.
 */
abstract class AbstractLafPage implements FeaturePage {

    private static final Logger LOG = Logger.getLogger(AbstractLafPage.class);

    /** Per build state (pages are singletons showing one content at a time). */
    protected LafState state;
    protected final List<Window> windows = new ArrayList<>();
    protected final Map<String, BufferedImage> extras = new LinkedHashMap<>();
    /** The live gallery of the page, if any. */
    protected LafGallery gallery;

    @Override
    public String category() {
        return Categories.LAF;
    }

    @Override
    public final Component build() throws Exception {
        windows.clear();
        extras.clear();
        gallery = null;
        galleryStaged = false;
        state = LafState.save();
        try {
            return buildPage();
        } catch (Exception | Error e) {
            restore();
            throw e;
        }
    }

    /**
     * Builds the content ; the look and feel installed when it returns stays installed until the page is disposed.
     */
    protected abstract Component buildPage() throws Exception;

    /** Whether the gallery is shown as an image rendered in a packed, never shown window (snapshot mode). */
    private boolean galleryStaged;

    @Override
    public CompletionStage<?> ready(Component content) {
        return gallery == null || galleryStaged ? CompletableFuture.completedFuture(null)
                : Edt.stable(gallery.panel(), 3000);
    }

    /**
     * {@code true} for a look and feel whose rendering depends on the activation of the window (e.g. the Windows look
     * and feel paints the menus of the menu bar of an inactive window grayed, and its default buttons differently) :
     * in snapshot mode the gallery is then rendered in a packed window that is never shown (never active), and shown
     * as an image, so that the snapshots do not depend on which window of the desktop is active.
     */
    protected boolean activationDependent() {
        return false;
    }

    /**
     * The gallery component of the page : the live gallery, or its image (see {@link #activationDependent()}).
     */
    protected Component galleryView() {
        galleryStaged = activationDependent() && ShowcaseMode.snapshot();
        return galleryStaged ? Ui.image(LafSupport.renderStaged(gallery.panel())) : gallery.panel();
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(extras));
    }

    @Override
    public void dispose(Component content) {
        restore();
    }

    private void restore() {
        LafSupport.dispose(windows);
        windows.clear();
        extras.clear();
        gallery = null;
        if (state != null) {
            String error = state.restore();
            state = null;
            if (error != null) {
                LOG.errorf("Page %s failed to restore the look and feel: %s", id(), error);
            }
        }
    }

    /**
     * The standard sections for {@code gallery} under the current look and feel : look and feel properties, icons,
     * UI delegates, resource bundle strings, key bindings and action maps.
     */
    protected List<Component> standardSections(LafGallery gallery, List<Check> lafChecks, Map<String, String> strings,
            List<String> infoStrings, boolean requireActions) {
        Map<String, BufferedImage> icons = new LinkedHashMap<>();
        List<Check> iconChecks = LafSupport.iconChecks(icons);
        List<JComponent> components = new ArrayList<>(gallery.components());
        List<Check> fileChooser = new ArrayList<>();
        // the file chooser delegate of the look and feel (shell folders on Windows : JNI and COM) : created, not shown
        fileChooser.add(Checks.info("JFileChooser UI", () -> {
            JFileChooser chooser = new JFileChooser();
            components.add(chooser);
            return LafSupport.simple(chooser.getUI().getClass().getName());
        }));
        List<Check> ui = LafSupport.uiChecks(components);
        List<Component> sections = new ArrayList<>();
        sections.add(LafSupport.twoColumns("Look and feel", concat(lafChecks, fileChooser), 190));
        sections.add(LafSupport.twoColumns("Gallery state", gallery.stateChecks(), 230));
        sections.add(Ui.title("Icons of the look and feel defaults"));
        if (!icons.isEmpty()) {
            sections.add(Ui.image(LafSupport.iconStrip(icons)));
        }
        sections.add(LafSupport.twoColumns("Icons (size, pixels hash)", iconChecks, 190));
        sections.add(LafSupport.twoColumns("UI delegates (UIDefaults.getUI : class by name + createUI)", ui, 170));
        sections.add(LafSupport.twoColumns("Resource bundle strings", LafSupport.stringChecks(strings, infoStrings), 230));
        sections.add(LafSupport.twoColumns("Key bindings (actions, keys focused / ancestor / window)",
                LafSupport.keyChecks(gallery.keyed(), requireActions), 150));
        return sections;
    }

    protected static List<Check> concat(List<Check> a, List<Check> b) {
        List<Check> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
