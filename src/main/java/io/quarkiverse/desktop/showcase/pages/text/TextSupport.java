package io.quarkiverse.desktop.showcase.pages.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.quarkiverse.desktop.showcase.core.Snapshots;

/**
 * Drawing helpers of the text pages : labeled rows drawn into one image (capture method C), deterministic hints for
 * labels, pixel classification of rendered text.
 */
final class TextSupport {

    static final int WIDTH = 1000;
    static final int LABEL_COLOR = 0x455A64;
    static final int RULE_COLOR = 0xE3E8EC;
    static final int INK = 0x1A1A1A;

    private TextSupport() {
    }

    /**
     * A row of a sheet : a label on the left and content painted by {@code painter} in a {@code (width - labelWidth) x
     * height} area whose origin is the top left corner of the content.
     */
    record Row(String label, int height, Consumer<Graphics2D> painter) {
    }

    /**
     * Rows stacked in a transparent image {@link #WIDTH} wide (labels wrapped in {@code labelWidth}).
     */
    static BufferedImage sheet(int labelWidth, List<Row> rows) {
        int height = rows.stream().mapToInt(r -> r.height() + 1).sum();
        return Snapshots.offscreen(WIDTH, Math.max(1, height), g -> {
            int y = 0;
            for (Row row : rows) {
                Graphics2D label = (Graphics2D) g.create(0, y, labelWidth - 8, row.height());
                try {
                    labelHints(label);
                    label.setColor(new Color(LABEL_COLOR));
                    label.setFont(labelFont());
                    List<String> lines = wrap(row.label(), label.getFont(), labelWidth - 12);
                    float ly = 14;
                    for (String line : lines) {
                        label.drawString(line, 2, ly);
                        ly += 14;
                    }
                } finally {
                    label.dispose();
                }
                Graphics2D content = (Graphics2D) g.create(labelWidth, y, WIDTH - labelWidth, row.height());
                try {
                    row.painter().accept(content);
                } finally {
                    content.dispose();
                }
                y += row.height();
                g.setColor(new Color(RULE_COLOR));
                g.drawLine(0, y, WIDTH - 1, y);
                y++;
            }
        });
    }

    static Font labelFont() {
        return new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    /**
     * The hints of labels and captions : grayscale anti-aliasing, integer metrics.
     */
    static void labelHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * The hints of samples : grayscale anti-aliasing, fractional metrics (advances independent of hinting).
     */
    static void sampleHints(Graphics2D g) {
        labelHints(g);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /**
     * Draws a small caption (Dialog 11, muted) at {@code (x, y)} (baseline).
     */
    static void caption(Graphics2D g, String text, float x, float y) {
        Graphics2D c = (Graphics2D) g.create();
        try {
            labelHints(c);
            c.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
            c.setColor(new Color(LABEL_COLOR));
            c.drawString(text, x, y);
        } finally {
            c.dispose();
        }
    }

    static List<String> wrap(String text, Font font, int width) {
        List<String> lines = new ArrayList<>();
        FontRenderContext frc = new FontRenderContext(null, true, false);
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringBounds(candidate, frc).getWidth() > width) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * {@code "bw"} (only black and white pixels), {@code "gray"} (gray levels only : grayscale anti-aliasing) or
     * {@code "color"} (colored pixels : LCD sub-pixel anti-aliasing) : the pixels of {@code image} (opaque).
     */
    static String pixelKind(BufferedImage image) {
        boolean gray = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r != g || g != b) {
                    return "color";
                }
                if (r != 0 && r != 255) {
                    gray = true;
                }
            }
        }
        return gray ? "gray" : "bw";
    }

    /**
     * Number of distinct colors of {@code image}.
     */
    static int distinctColors(BufferedImage image) {
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getRGB(x, y));
            }
        }
        return colors.size();
    }
}
