package io.quarkiverse.desktop.showcase.ui.awt;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.AbstractMainWindow;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkiverse.desktop.showcase.core.TextBlock;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Main window of the AWT only variant (no {@code javax.swing}) : a {@link Frame} with a {@link java.awt.List} of the
 * pages on the left, and a {@link CardLayout} showing the selected page (in a {@link ScrollPane}) or a placeholder.
 */
@Singleton
public class AwtMainWindow extends AbstractMainWindow {

    private static final String PAGE_CARD = "page";
    private static final String EMPTY_CARD = "empty";

    private Frame frame;
    private Panel root;
    private java.awt.List nav;
    private final List<Object> navItems = new ArrayList<>();
    private TextBlock pageTitle;
    private Panel pageFrame;
    private CardLayout cardLayout;
    private Panel cards;
    private ScrollPane scroll;
    private Canvas focusSink;

    @Override
    public String kind() {
        return "awt";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void open(List<FeaturePage> pages) {
        this.pages = List.copyOf(pages);
        frame = new Frame(windowTitle());
        setUp(frame);
        frame.setMenuBar(menuBar());

        nav = new java.awt.List(40, false);
        // a focused list paints a focus rectangle and another selection color
        nav.setFocusable(!ShowcaseMode.snapshot());
        String category = null;
        for (FeaturePage page : pages) {
            if (!page.category().equals(category)) {
                category = page.category();
                nav.add(category.toUpperCase(Locale.ROOT));
                navItems.add(category);
            }
            nav.add("    " + page.title());
            navItems.add(page);
        }
        nav.addItemListener(e -> {
            int index = nav.getSelectedIndex();
            if (index < 0) {
                return;
            }
            Object item = navItems.get(index);
            if (item instanceof FeaturePage page) {
                if (page != currentPage) {
                    show(page);
                }
            } else {
                pages.stream().filter(p -> p.category().equals(item)).findFirst().ifPresent(this::select);
            }
        });
        Panel navPanel = new Panel(new BorderLayout());
        navPanel.add(nav, BorderLayout.CENTER);
        navPanel.setPreferredSize(new java.awt.Dimension(270, 100));

        pageTitle = Ui.text("Quarkus Desktop Showcase", new Font(Font.DIALOG, Font.BOLD, 15), Ui.TEXT_COLOR, 0);
        Panel titleBar = new Panel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        titleBar.add(pageTitle);

        pageFrame = new Panel(new PageFrameLayout());
        pageFrame.setBackground(Color.WHITE);
        Panel holder = new Panel(new TopLeftLayout());
        holder.add(pageFrame);
        scroll = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        scroll.add(holder);
        scroll.getHAdjustable().setUnitIncrement(16);
        scroll.getVAdjustable().setUnitIncrement(16);

        cardLayout = new CardLayout();
        cards = new Panel(cardLayout);
        cards.add(scroll, PAGE_CARD);
        Panel empty = new Panel(new FlowLayout(FlowLayout.LEFT, 16, 16));
        empty.add(Ui.text("Select a page on the left."));
        cards.add(empty, EMPTY_CARD);
        cardLayout.show(cards, EMPTY_CARD);

        Panel center = new Panel(new BorderLayout());
        center.add(titleBar, BorderLayout.NORTH);
        center.add(cards, BorderLayout.CENTER);

        focusSink = new Canvas();
        focusSink.setFocusable(true);
        focusSink.setPreferredSize(new java.awt.Dimension(1, 1));
        Panel statusBar = new Panel(new BorderLayout());
        Panel statusText = new Panel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        statusText.add(Ui.text(statusText()));
        statusBar.add(statusText, BorderLayout.CENTER);
        statusBar.add(focusSink, BorderLayout.EAST);

        root = new Panel(new BorderLayout());
        root.add(navPanel, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);
        frame.add(root);
        frame.setVisible(true);
    }

    private MenuBar menuBar() {
        MenuItem previous = new MenuItem("Previous page", new MenuShortcut(KeyEvent.VK_OPEN_BRACKET));
        previous.addActionListener(e -> step(-1));
        MenuItem next = new MenuItem("Next page", new MenuShortcut(KeyEvent.VK_CLOSE_BRACKET));
        next.addActionListener(e -> step(1));
        MenuItem quit = new MenuItem("Quit", new MenuShortcut(KeyEvent.VK_Q));
        quit.addActionListener(e -> quit());
        Menu showcase = new Menu("Showcase");
        showcase.add(previous);
        showcase.add(next);
        showcase.addSeparator();
        showcase.add(quit);

        MenuItem about = new MenuItem("About");
        about.addActionListener(e -> about());
        Menu help = new Menu("Help");
        help.add(about);

        MenuBar menuBar = new MenuBar();
        menuBar.add(showcase);
        menuBar.setHelpMenu(help);
        return menuBar;
    }

    private void about() {
        Dialog dialog = new Dialog(frame, "About", true);
        dialog.setLayout(new BorderLayout());
        Panel text = new Panel(new FlowLayout(FlowLayout.LEFT, 16, 16));
        text.add(Ui.text("Quarkus Desktop Showcase\n" + statusText() + "\nJava " + System.getProperty("java.version")));
        Button ok = new Button("OK");
        ok.addActionListener(e -> dialog.dispose());
        Panel buttons = new Panel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        dialog.add(text, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    @Override
    protected void show(FeaturePage page) {
        super.show(page);
        cardLayout.show(cards, PAGE_CARD);
        scroll.setScrollPosition(0, 0);
        contentChanged();
    }

    @Override
    public void clear() {
        super.clear();
        if (cardLayout != null) {
            cardLayout.show(cards, EMPTY_CARD);
        }
    }

    @Override
    protected void selectInNavigation(FeaturePage page) {
        int index = navItems.indexOf(page);
        if (index >= 0) {
            nav.select(index);
            nav.makeVisible(Math.max(0, index - 3));
            nav.makeVisible(index);
        }
    }

    @Override
    protected void showPageTitle(String text) {
        pageTitle.setText(text);
        pageTitle.getParent().validate();
    }

    @Override
    protected Component focusSink() {
        return focusSink;
    }

    @Override
    public Window window() {
        return frame;
    }

    @Override
    public Component rootContent() {
        return root;
    }

    @Override
    public Container pageFrame() {
        return pageFrame;
    }
}
