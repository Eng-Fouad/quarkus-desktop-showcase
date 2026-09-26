package io.quarkiverse.desktop.showcase.pages.swing.controls;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * The generated images of the swing-controls pages ({@code src/main/resources/showcase/swing-controls/}) : the
 * painters, the solid colors that the pages probe after loading the resources, and the generator.
 * <p>
 * The resources were written once with {@link #generate(Path)} (JDK 25, e.g.
 * {@code java -cp target/classes GenAssets.java src/main/resources/showcase/swing-controls}, a launcher calling this
 * package-private method by reflection) and committed. They are original work of this project.
 */
final class ControlsAssets {

    static final int STAR = 0xF9A825;
    static final int STAR_ROLLOVER = 0xFB8C00;
    static final int STAR_PRESSED = 0xC62828;
    static final int STAR_SELECTED = 0x2E7D32;
    static final int PLAY = 0x1565C0;
    static final int LOGO = 0x4695EB;
    static final int SUN = 0xFFD54F;
    static final int BADGE_BACKGROUND = 0x263238;
    static final int BADGE_ACCENT = 0xFF6F00;

    /** Star icons : file name suffix, color. */
    static final String[][] STARS = {
            { "star-16.png", "F9A825" }, { "star-rollover-16.png", "FB8C00" }, { "star-pressed-16.png", "C62828" },
            { "star-selected-16.png", "2E7D32" } };

    private ControlsAssets() {
    }

    /**
     * A five pointed star filled with {@code rgb} (outline one shade darker), anti-aliased, on a transparent
     * background. The pixel at the center is exactly {@code rgb}.
     */
    static BufferedImage star(int size, int rgb) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Path2D.Double path = new Path2D.Double();
            double c = size / 2.0;
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                double r = i % 2 == 0 ? size * 0.48 : size * 0.2;
                double x = c + r * Math.cos(angle);
                double y = c + 0.5 + r * Math.sin(angle);
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.closePath();
            g.setColor(new Color(rgb));
            g.fill(path);
            g.setColor(new Color(rgb).darker());
            g.setStroke(new BasicStroke(size / 16f));
            g.draw(path);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * A 16 x 16 indexed image (GIF) : a {@code PLAY} triangle on a transparent background, not anti-aliased.
     */
    static BufferedImage play() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_INDEXED, palette(PLAY, 0x0D47A1));
        Graphics2D g = image.createGraphics();
        try {
            g.setComposite(java.awt.AlphaComposite.Src);
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, 16, 16);
            g.setColor(new Color(0x0D47A1));
            g.fillPolygon(new int[] { 3, 14, 3 }, new int[] { 1, 8, 15 }, 3);
            g.setColor(new Color(PLAY));
            g.fillPolygon(new int[] { 4, 12, 4 }, new int[] { 3, 8, 13 }, 3);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 64 x 64 : a solid {@code LOGO} rounded square with a white ring, transparent corners.
     */
    static BufferedImage logo() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(LOGO));
            g.fill(new RoundRectangle2D.Double(0, 0, 64, 64, 18, 18));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(6f));
            g.draw(new Ellipse2D.Double(18, 18, 28, 28));
            g.fillRect(38, 40, 12, 6);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 160 x 100 JPEG : a sky gradient, a {@code SUN} disc and hills.
     */
    static BufferedImage photo() {
        BufferedImage image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0x1E88E5), 0, 100, new Color(0xBBDEFB)));
            g.fillRect(0, 0, 160, 100);
            g.setColor(new Color(SUN));
            g.fill(new Ellipse2D.Double(100, 12, 36, 36));
            g.setColor(new Color(0x43A047));
            g.fill(new Ellipse2D.Double(-40, 60, 150, 90));
            g.setColor(new Color(0x2E7D32));
            g.fill(new Ellipse2D.Double(60, 70, 160, 80));
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 88 x 31 indexed (GIF) "button" badge : {@code BADGE_BACKGROUND}, an {@code BADGE_ACCENT} block and white bars.
     */
    static BufferedImage badge() {
        BufferedImage image = new BufferedImage(88, 31, BufferedImage.TYPE_BYTE_INDEXED,
                palette(BADGE_BACKGROUND, BADGE_ACCENT));
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(BADGE_BACKGROUND));
            g.fillRect(0, 0, 88, 31);
            g.setColor(new Color(BADGE_ACCENT));
            g.fillRect(2, 2, 26, 27);
            g.setColor(Color.WHITE);
            g.drawRect(0, 0, 87, 30);
            for (int i = 0; i < 3; i++) {
                g.fillRect(34, 7 + i * 7, 48 - i * 12, 3);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * A palette : transparent (index 0), {@code a}, {@code b}, white, black.
     */
    private static IndexColorModel palette(int a, int b) {
        int[] colors = { 0x00000000, 0xFF000000 | a, 0xFF000000 | b, 0xFFFFFFFF, 0xFF000000 };
        return new IndexColorModel(8, colors.length, colors, 0, true, 0, java.awt.image.DataBuffer.TYPE_BYTE);
    }

    /**
     * Writes the generated resources into {@code dir} ({@code .../resources/showcase/swing-controls}).
     */
    static void generate(Path dir) throws IOException {
        Path icons = Files.createDirectories(dir.resolve("icons"));
        for (String[] star : STARS) {
            ImageIO.write(star(16, Integer.parseInt(star[1], 16)), "png", icons.resolve(star[0]).toFile());
        }
        ImageIO.write(star(32, STAR), "png", icons.resolve("star-32.png").toFile());
        ImageIO.write(play(), "gif", icons.resolve("play-16.gif").toFile());
        Path images = Files.createDirectories(dir.resolve("html").resolve("images"));
        ImageIO.write(logo(), "png", images.resolve("logo.png").toFile());
        ImageIO.write(badge(), "gif", images.resolve("badge.gif").toFile());
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(images.resolve("photo.jpg").toFile())) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            writer.write(null, new IIOImage(photo(), null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
