package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.plaf.synth.SynthConstants;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthIcon;
import javax.swing.plaf.synth.SynthPainter;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The custom {@link SynthPainter} of the Synth XML page, created by the XML file itself :
 * {@code <object id="painter" class="...ShowcaseSynthPainter">} (the JavaBeans decoder instantiates it through its public
 * no-arg constructor) with {@code <void property="accent">} and {@code <void property="arrowSize">} (bean properties
 * set through their setters, found by {@code java.beans.Introspector}), then referenced by
 * {@code <painter idref="painter" method="...">}.
 * <p>
 * {@code @RegisterForReflection} : the class, its constructor and its bean methods are reached by reflection only (from
 * {@code com.sun.beans.decoder}), a native executable needs them registered.
 */
@RegisterForReflection
public class ShowcaseSynthPainter extends SynthPainter {

    private Color accent = new Color(0x3F72AF);
    private int arrowSize = 6;

    public ShowcaseSynthPainter() {
    }

    public Color getAccent() {
        return accent;
    }

    public void setAccent(Color accent) {
        this.accent = accent;
    }

    public int getArrowSize() {
        return arrowSize;
    }

    public void setArrowSize(int arrowSize) {
        this.arrowSize = arrowSize;
    }

    private static Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    private static boolean selected(SynthContext context) {
        return (context.getComponentState() & SynthConstants.SELECTED) != 0;
    }

    private Color light() {
        return new Color((accent.getRed() + 3 * 255) / 4, (accent.getGreen() + 3 * 255) / 4,
                (accent.getBlue() + 3 * 255) / 4);
    }

    @Override
    public void paintProgressBarForeground(SynthContext context, Graphics g, int x, int y, int w, int h,
            int orientation) {
        // a solid accent bar : pixel probes of the page expect the accent color inside it
        g.setColor(accent);
        g.fillRect(x, y, w, h);
        g.setColor(accent.darker());
        g.fillRect(x, y + h - 2, w, 2);
    }

    @Override
    public void paintSliderTrackBackground(SynthContext context, Graphics g, int x, int y, int w, int h,
            int orientation) {
        Graphics2D g2 = prepare(g);
        try {
            g2.setColor(light());
            if (orientation == SwingConstants.HORIZONTAL) {
                g2.fill(new RoundRectangle2D.Float(x, y + h / 2f - 2, w, 4, 4, 4));
            } else {
                g2.fill(new RoundRectangle2D.Float(x + w / 2f - 2, y, 4, h, 4, 4));
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintSliderThumbBackground(SynthContext context, Graphics g, int x, int y, int w, int h,
            int orientation) {
        Graphics2D g2 = prepare(g);
        try {
            float d = Math.min(w, h) - 1;
            Ellipse2D.Float thumb = new Ellipse2D.Float(x + (w - d) / 2f, y + (h - d) / 2f, d, d);
            g2.setColor(accent);
            g2.fill(thumb);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(thumb);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintArrowButtonForeground(SynthContext context, Graphics g, int x, int y, int w, int h,
            int direction) {
        Graphics2D g2 = prepare(g);
        try {
            float s = arrowSize;
            float cx = x + w / 2f;
            float cy = y + h / 2f;
            Path2D.Float arrow = new Path2D.Float();
            switch (direction) {
                case SwingConstants.NORTH -> {
                    arrow.moveTo(cx - s / 2, cy + s / 4);
                    arrow.lineTo(cx + s / 2, cy + s / 4);
                    arrow.lineTo(cx, cy - s / 4);
                }
                case SwingConstants.WEST -> {
                    arrow.moveTo(cx + s / 4, cy - s / 2);
                    arrow.lineTo(cx + s / 4, cy + s / 2);
                    arrow.lineTo(cx - s / 4, cy);
                }
                case SwingConstants.EAST -> {
                    arrow.moveTo(cx - s / 4, cy - s / 2);
                    arrow.lineTo(cx - s / 4, cy + s / 2);
                    arrow.lineTo(cx + s / 4, cy);
                }
                default -> {
                    arrow.moveTo(cx - s / 2, cy - s / 4);
                    arrow.lineTo(cx + s / 2, cy - s / 4);
                    arrow.lineTo(cx, cy + s / 4);
                }
            }
            arrow.closePath();
            g2.setColor((context.getComponentState() & SynthConstants.DISABLED) != 0 ? Color.GRAY : accent.darker());
            g2.fill(arrow);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintSeparatorForeground(SynthContext context, Graphics g, int x, int y, int w, int h,
            int orientation) {
        g.setColor(light());
        if (orientation == SwingConstants.HORIZONTAL) {
            g.fillRect(x, y + h / 2, w, 1);
        } else {
            g.fillRect(x + w / 2, y, 1, h);
        }
    }

    @Override
    public void paintSplitPaneDividerBackground(SynthContext context, Graphics g, int x, int y, int w, int h,
            int orientation) {
        g.setColor(light());
        g.fillRect(x, y, w, h);
        g.setColor(accent);
        for (int i = -2; i <= 2; i++) {
            if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                g.fillRect(x + w / 2 - 1, y + h / 2 + i * 5, 2, 2);
            } else {
                g.fillRect(x + w / 2 + i * 5, y + h / 2 - 1, 2, 2);
            }
        }
    }

    @Override
    public void paintInternalFrameTitlePaneBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = prepare(g);
        try {
            Color top = selected(context) ? accent.brighter() : new Color(0xB0B7C3);
            Color bottom = selected(context) ? accent : new Color(0x8C94A3);
            g2.setPaint(new GradientPaint(x, y, top, x, y + h, bottom));
            g2.fillRect(x, y, w, h);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintInternalFrameBorder(SynthContext context, Graphics g, int x, int y, int w, int h) {
        g.setColor(selected(context) ? accent : new Color(0x8C94A3));
        g.drawRect(x, y, w - 1, h - 1);
        g.drawRect(x + 1, y + 1, w - 3, h - 3);
    }

    @Override
    public void paintDesktopPaneBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        g.setColor(light());
        g.fillRect(x, y, w, h);
    }

    @Override
    public void paintToolTipBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = prepare(g);
        try {
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 8, 8);
            g2.setColor(new Color(0xFFF8E1));
            g2.fill(shape);
            g2.setColor(new Color(0xC9A227));
            g2.draw(shape);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintMenuBarBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        paintBar(g, x, y, w, h);
    }

    @Override
    public void paintToolBarBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        paintBar(g, x, y, w, h);
    }

    private void paintBar(Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = prepare(g);
        try {
            g2.setPaint(new GradientPaint(x, y, Color.WHITE, x, y + h, light()));
            g2.fillRect(x, y, w, h);
            g2.setColor(accent);
            g2.fillRect(x, y + h - 1, w, 1);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintPopupMenuBorder(SynthContext context, Graphics g, int x, int y, int w, int h) {
        g.setColor(accent);
        g.drawRect(x, y, w - 1, h - 1);
    }

    @Override
    public void paintScrollPaneBorder(SynthContext context, Graphics g, int x, int y, int w, int h) {
        g.setColor(new Color(0xA9B8CC));
        g.drawRect(x, y, w - 1, h - 1);
    }

    @Override
    public void paintTabbedPaneContentBorder(SynthContext context, Graphics g, int x, int y, int w, int h) {
        g.setColor(accent);
        g.drawRect(x, y, w - 1, h - 1);
    }

    /**
     * The buttons of the internal frame title panes (style bound by component name) : a translucent white rounded
     * rectangle on the title bar gradient.
     */
    @Override
    public void paintButtonBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = prepare(g);
        try {
            g2.setColor(new Color(255, 255, 255, 70));
            g2.fill(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 6, 6));
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintTreeCellBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        if (selected(context)) {
            g.setColor(accent);
            g.fillRect(x, y, w, h);
        }
    }

    @Override
    public void paintMenuItemBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        if ((context.getComponentState() & (SynthConstants.MOUSE_OVER | SynthConstants.SELECTED)) != 0) {
            g.setColor(light());
            g.fillRect(x, y, w, h);
        }
    }

    /**
     * The submenu arrow : an {@link Icon} and a {@link SynthIcon} (sized per context) without width for the menus of a
     * menu bar. Created by the XML file as a bean ({@code <object class="...ShowcaseSynthPainter$MenuArrowIcon">} with
     * its {@code image} property), hence {@code @RegisterForReflection}.
     */
    @RegisterForReflection
    public static class MenuArrowIcon implements Icon, SynthIcon {

        private Icon image;

        public MenuArrowIcon() {
        }

        public Icon getImage() {
            return image;
        }

        public void setImage(Icon image) {
            this.image = image;
        }

        private static boolean topLevel(SynthContext context) {
            return context != null && context.getComponent() instanceof JMenu menu && menu.isTopLevelMenu();
        }

        @Override
        public void paintIcon(SynthContext context, Graphics g, int x, int y, int w, int h) {
            if (!topLevel(context) && image != null) {
                image.paintIcon(context == null ? null : context.getComponent(), g, x, y + (h - image.getIconHeight()) / 2);
            }
        }

        @Override
        public int getIconWidth(SynthContext context) {
            return topLevel(context) || image == null ? 0 : image.getIconWidth();
        }

        @Override
        public int getIconHeight(SynthContext context) {
            return topLevel(context) || image == null ? 0 : image.getIconHeight();
        }

        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            if (!(c instanceof JMenu menu && menu.isTopLevelMenu()) && image != null) {
                image.paintIcon(c, g, x, y);
            }
        }

        @Override
        public int getIconWidth() {
            return image == null ? 0 : image.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return image == null ? 0 : image.getIconHeight();
        }
    }
}
