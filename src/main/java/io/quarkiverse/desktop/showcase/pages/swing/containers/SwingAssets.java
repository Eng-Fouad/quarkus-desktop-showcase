package io.quarkiverse.desktop.showcase.pages.swing.containers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Generator of the resources of {@code src/main/resources/showcase/swing-containers} (not used at run time). The
 * output is deterministic (same bytes on every run) : {@code SwingAssets.generate(Path.of(
 * "src/main/resources/showcase/swing-containers"))}, e.g. from jshell with {@code target/classes} on the class path.
 * <ul>
 * <li>{@code tile.gif} : 12x12 indexed image, a blue diamond on a light background, orange bottom and right edges
 * (tiled by a MatteBorder, decoded by the Toolkit GIF decoder through {@code ImageIcon(URL)})</li>
 * <li>{@code badge.png} : 16x16 ARGB, a green disc with a white check mark (a tab icon, decoded by the Toolkit PNG
 * decoder through {@code ImageIcon(URL)})</li>
 * </ul>
 */
final class SwingAssets {

    private SwingAssets() {
    }

    static void generate(Path dir) throws IOException {
        IndexColorModel cm = new IndexColorModel(8, 3, new byte[] { (byte) 0xFF, (byte) 0x1E, (byte) 0xFF },
                new byte[] { (byte) 0xF3, (byte) 0x88, (byte) 0xA7 }, new byte[] { (byte) 0xE0, (byte) 0xE5, (byte) 0x26 });
        BufferedImage tile = new BufferedImage(12, 12, BufferedImage.TYPE_BYTE_INDEXED, cm);
        WritableRaster raster = tile.getRaster();
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                int d = Math.abs(x - 5) + Math.abs(y - 5);
                raster.setSample(x, y, 0, d <= 3 ? 1 : (x == 11 || y == 11 ? 2 : 0));
            }
        }
        ImageIO.write(tile, "gif", dir.resolve("tile.gif").toFile());

        BufferedImage badge = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = badge.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x2E7D32));
            g.fill(new Ellipse2D.Float(0.5f, 0.5f, 15, 15));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float check = new Path2D.Float();
            check.moveTo(4, 8.5f);
            check.lineTo(7, 11.5f);
            check.lineTo(12, 5);
            g.draw(check);
        } finally {
            g.dispose();
        }
        ImageIO.write(badge, "png", dir.resolve("badge.png").toFile());
    }
}
