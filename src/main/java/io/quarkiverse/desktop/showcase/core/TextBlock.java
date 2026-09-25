package io.quarkiverse.desktop.showcase.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * A lightweight AWT component painting (optionally wrapped) text with Java2D : usable in AWT and Swing pages, no native
 * peer (renders the same way whatever the capture method and the UI scale).
 * <p>
 * Text is painted with grayscale anti-aliasing and integer metrics, independently of the desktop settings. Lines are
 * split at {@code \n}, then wrapped at the wrap width (if positive) between words.
 */
public class TextBlock extends Component {

    private String text;
    private final int wrapWidth;
    private List<String> lines;
    private float ascent;
    private int lineHeight;
    private int textWidth;

    public TextBlock(String text, Font font, Color color, int wrapWidth) {
        this.text = text == null ? "" : text;
        this.wrapWidth = wrapWidth;
        setFont(font);
        setForeground(color);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        lines = null;
        invalidate();
        repaint();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        lines = null;
    }

    private void measure() {
        if (lines != null) {
            return;
        }
        Font font = getFont();
        LineMetrics metrics = font.getLineMetrics("Ag", frc());
        ascent = metrics.getAscent();
        lineHeight = (int) Math.ceil(metrics.getAscent() + metrics.getDescent() + metrics.getLeading());
        lines = wrap(text, font, wrapWidth);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, width(font, line));
        }
        textWidth = width;
    }

    /**
     * {@code text} split at line breaks, then wrapped at {@code wrapWidth} (no wrapping if {@code wrapWidth <= 0}).
     */
    public static List<String> wrap(String text, Font font, int wrapWidth) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (wrapWidth <= 0 || width(font, paragraph) <= wrapWidth) {
                result.add(paragraph);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(font, candidate) <= wrapWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                // a word longer than a line is broken anywhere
                String rest = word;
                while (width(font, rest) > wrapWidth && rest.length() > 1) {
                    int n = rest.length() - 1;
                    while (n > 1 && width(font, rest.substring(0, n)) > wrapWidth) {
                        n--;
                    }
                    result.add(rest.substring(0, n));
                    rest = rest.substring(n);
                }
                line.append(rest);
            }
            result.add(line.toString());
        }
        return result;
    }

    static int width(Font font, String s) {
        return s.isEmpty() ? 0 : (int) Math.ceil(font.getStringBounds(s, frc()).getWidth());
    }

    /**
     * The font render context of the core components : anti-aliased, integer metrics. Never kept in a static field :
     * application classes are initialized at build time, AWT classes at run time.
     */
    static FontRenderContext frc() {
        return new FontRenderContext(null, true, false);
    }

    static int lineHeight(Font font) {
        LineMetrics metrics = font.getLineMetrics("Ag", frc());
        return (int) Math.ceil(metrics.getAscent() + metrics.getDescent() + metrics.getLeading());
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        measure();
        return new Dimension(textWidth, lineHeight * lines.size());
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void paint(Graphics graphics) {
        measure();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            prepare(g);
            g.setFont(getFont());
            g.setColor(getForeground());
            float y = ascent;
            for (String line : lines) {
                g.drawString(line, 0, y);
                y += lineHeight;
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * The rendering hints of the text painted by the core components.
     */
    static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
}
