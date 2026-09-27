package io.quarkiverse.desktop.showcase.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A lightweight component showing an image set later (e.g. once the page is ready), scaled to a fixed size with nearest
 * neighbor interpolation ; a light placeholder before.
 */
public final class Slot extends Component {

    private final int width;
    private final int height;
    private transient BufferedImage image;

    public Slot(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Shows {@code image} (call it on the EDT).
     */
    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void paint(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (image == null) {
                g.setColor(new Color(0xFFECEFF1, true));
                g.fillRect(0, 0, width, height);
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
    }
}
