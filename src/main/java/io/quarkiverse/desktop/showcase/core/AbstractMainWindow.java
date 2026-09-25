package io.quarkiverse.desktop.showcase.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;

/**
 * The page lifecycle shared by both main windows : build, error content, dispose, navigation steps.
 */
public abstract class AbstractMainWindow implements MainWindow {

    private static final Logger LOG = Logger.getLogger(AbstractMainWindow.class);

    protected List<FeaturePage> pages = List.of();
    protected FeaturePage currentPage;
    protected Component currentContent;
    protected Throwable currentError;

    /**
     * The component owning the focus when no page component should (it paints no focus decoration).
     */
    protected abstract Component focusSink();

    /**
     * Shows {@code text} ({@code Category › Title}) above the page frame.
     */
    protected abstract void showPageTitle(String text);

    /**
     * Selects {@code page} in the navigation component, without notifying the selection listener.
     */
    protected abstract void selectInNavigation(FeaturePage page);

    @Override
    public void select(FeaturePage page) {
        selectInNavigation(page);
        if (currentPage != page) {
            show(page);
        }
    }

    /**
     * Shows {@code page} : disposes the current page, then builds the new one (the error content if the build fails).
     */
    protected void show(FeaturePage page) {
        clear();
        currentPage = page;
        showPageTitle(page.category() + " › " + page.title());
        Component content;
        try {
            content = page.build();
        } catch (Throwable t) {
            LOG.errorf(t, "Page %s failed to build", page.id());
            currentError = t;
            content = Ui.error(t);
        }
        currentContent = content;
        Container frame = pageFrame();
        frame.add(content);
        contentChanged();
        if (ShowcaseMode.snapshot()) {
            releaseFocus();
        }
    }

    /**
     * Lays out and repaints the page frame after its content changed.
     */
    protected void contentChanged() {
        Container frame = pageFrame();
        frame.invalidate();
        Snapshots.layout(frame);
        frame.repaint();
    }

    @Override
    public Component currentContent() {
        return currentContent;
    }

    @Override
    public Throwable currentError() {
        return currentError;
    }

    @Override
    public void clear() {
        if (currentPage != null && currentContent != null && currentError == null) {
            try {
                currentPage.dispose(currentContent);
            } catch (Throwable t) {
                LOG.errorf(t, "Page %s failed to dispose", currentPage.id());
            }
        }
        currentPage = null;
        currentContent = null;
        currentError = null;
        if (pageFrame() != null) {
            showPageTitle("Quarkus Desktop Showcase");
            pageFrame().removeAll();
            contentChanged();
        }
    }

    @Override
    public void releaseFocus() {
        focusSink().requestFocusInWindow();
    }

    @Override
    public boolean focusReleased() {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        return manager.getFocusedWindow() != window() || manager.getFocusOwner() == focusSink();
    }

    /**
     * Shows the previous ({@code -1}) or next ({@code 1}) page.
     */
    protected void step(int delta) {
        int index = currentPage == null ? 0 : pages.indexOf(currentPage) + delta;
        if (index >= 0 && index < pages.size()) {
            select(pages.get(index));
        }
    }

    protected String windowTitle() {
        return "Quarkus Desktop Showcase (" + ShowcaseMode.runtime() + ")";
    }

    protected String statusText() {
        String text = kind().equals("swing") ? "Swing UI" : "AWT UI";
        text += " · " + pages.size() + " pages";
        // the runtime differs between the compared runs : keep it out of snapshots
        return ShowcaseMode.snapshot() ? text : ShowcaseMode.runtime() + " · " + text;
    }

    /**
     * Common window setup : icon, position and size in snapshot mode, exit when closed.
     */
    protected void setUp(Window window) {
        window.setIconImages(List.of(icon(16), icon(32), icon(48)));
        if (ShowcaseMode.snapshot()) {
            // the snapshot window never steals the focus of the user's application (pages that need the focus ask for it)
            window.setAutoRequestFocus(false);
            window.setBounds(SNAPSHOT_X, SNAPSHOT_Y, SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT);
        } else {
            window.setSize(SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT);
            window.setLocationByPlatform(true);
        }
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                e.getWindow().dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                clear();
                Quarkus.asyncExit();
            }
        });
    }

    protected void quit() {
        window().dispose();
        Quarkus.asyncExit();
    }

    /**
     * The window icon, painted with Java2D.
     */
    protected static Image icon(int size) {
        return Snapshots.offscreen(size, size, g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(size / 32.0, size / 32.0);
            g.setPaint(new GradientPaint(0, 0, new Color(0x4695EB), 32, 32, new Color(0x1B3A6B)));
            g.fill(new RoundRectangle2D.Float(1, 1, 30, 30, 8, 8));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            g.drawRect(7, 9, 18, 13);
            g.fillRect(12, 24, 8, 2);
        });
    }

    /**
     * Layout of the page frame : the single content component at {@link #PAGE_PADDING}, at least
     * {@link #PAGE_WIDTH} x {@link #PAGE_HEIGHT} with the padding, larger if the content prefers.
     */
    public static final class PageFrameLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int width = PAGE_WIDTH;
            int height = PAGE_HEIGHT;
            for (Component c : parent.getComponents()) {
                Dimension d = c.getPreferredSize();
                width = Math.max(width, d.width + 2 * PAGE_PADDING);
                height = Math.max(height, d.height + 2 * PAGE_PADDING);
            }
            Insets in = parent.getInsets();
            return new Dimension(width + in.left + in.right, height + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int width = parent.getWidth() - in.left - in.right - 2 * PAGE_PADDING;
            int height = parent.getHeight() - in.top - in.bottom - 2 * PAGE_PADDING;
            for (Component c : parent.getComponents()) {
                c.setBounds(in.left + PAGE_PADDING, in.top + PAGE_PADDING, width, height);
            }
        }
    }

    /**
     * Places the single component at the top left corner, at its preferred size (the page frame in the scroll pane :
     * its size never depends on the window size).
     */
    public static final class TopLeftLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Dimension size = new Dimension();
            for (Component c : parent.getComponents()) {
                Dimension d = c.getPreferredSize();
                size.width = Math.max(size.width, d.width);
                size.height = Math.max(size.height, d.height);
            }
            return size;
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            for (Component c : parent.getComponents()) {
                Dimension d = c.getPreferredSize();
                c.setBounds(0, 0, d.width, d.height);
            }
        }
    }
}
