package io.quarkiverse.desktop.showcase.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A table of checks (status, name, value) painted with Java2D : a lightweight AWT component, usable by AWT and Swing
 * pages. The checks are attached to it ({@link Checks#attach}), so they end up in report.json.
 * <p>
 * Status marks : green check = passed, red cross = failed, gray dot = informational. Names and values are wrapped.
 * The {@link Check#attempts} checks are not painted (they may differ from run to run).
 */
public class ChecksView extends Component {

    /** Default width of the table. */
    public static final int WIDTH = 1000;
    /** Default width of the name column. */
    public static final int NAME_WIDTH = 280;

    private static final int PASS = 0x1B7F3A;
    private static final int FAIL = 0xC62828;
    private static final int INFO = 0x666666;
    private static final int TEXT = 0x222222;
    private static final int RULE = 0xE0E0E0;
    private static final int STATUS_WIDTH = 24;
    private static final int GAP = 12;
    private static final int ROW_GAP = 4;

    private final String title;
    private final int nameWidth;
    private final int width;
    private List<Check> checks;
    private List<Row> rows;
    private int height;

    private record Row(Check check, List<String> names, List<String> values, int y, int height) {
    }

    public ChecksView(String title, List<Check> checks, int nameWidth, int width) {
        this.title = title;
        this.nameWidth = nameWidth;
        this.width = width;
        setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        setChecks(checks);
    }

    /**
     * A table of {@code checks} with the default widths.
     */
    public static ChecksView table(String title, List<Check> checks) {
        return new ChecksView(title, checks, NAME_WIDTH, WIDTH);
    }

    public static ChecksView table(String title, List<Check> checks, int nameWidth, int width) {
        return new ChecksView(title, checks, nameWidth, width);
    }

    public List<Check> getChecks() {
        return checks;
    }

    /**
     * Replaces the checks shown and attached (e.g. checks computed once the page is ready) : call it on the EDT.
     */
    public void setChecks(List<Check> checks) {
        this.checks = List.copyOf(checks);
        Checks.attach(this, this.checks);
        rows = null;
        invalidate();
        if (getParent() != null) {
            getParent().validate();
        }
        repaint();
    }

    private Font titleFont() {
        return getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 2);
    }

    private void measure() {
        if (rows != null) {
            return;
        }
        Font font = getFont();
        int lineHeight = TextBlock.lineHeight(font);
        int valueWidth = width - STATUS_WIDTH - nameWidth - 2 * GAP;
        int y = title == null || title.isEmpty() ? 0 : TextBlock.lineHeight(titleFont()) + 8;
        List<Row> list = new ArrayList<>();
        for (Check check : checks) {
            if (check.isAttempts()) {
                // may differ from run to run : report.json only
                continue;
            }
            List<String> names = TextBlock.wrap(check.name(), font, nameWidth);
            List<String> values = TextBlock.wrap(check.value(), font, valueWidth);
            int h = Math.max(names.size(), values.size()) * lineHeight;
            list.add(new Row(check, names, values, y, h));
            y += h + ROW_GAP;
        }
        rows = list;
        height = y;
    }

    @Override
    public Dimension getPreferredSize() {
        measure();
        return new Dimension(width, Math.max(1, height));
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
            TextBlock.prepare(g);
            Font font = getFont();
            float ascent = font.getLineMetrics("Ag", TextBlock.frc()).getAscent();
            int lineHeight = TextBlock.lineHeight(font);
            if (title != null && !title.isEmpty()) {
                Font titleFont = titleFont();
                g.setFont(titleFont);
                g.setColor(new Color(TEXT));
                g.drawString(title, 0, titleFont.getLineMetrics("Ag", TextBlock.frc()).getAscent());
            }
            g.setFont(font);
            int valueX = STATUS_WIDTH + nameWidth + 2 * GAP;
            for (Row row : rows) {
                g.setColor(new Color(RULE));
                g.drawLine(0, row.y() + row.height() + ROW_GAP / 2, width - 1, row.y() + row.height() + ROW_GAP / 2);
                paintStatus(g, row.check().ok(), row.y() + lineHeight / 2f);
                g.setColor(new Color(TEXT));
                float y = row.y() + ascent;
                for (String line : row.names()) {
                    g.drawString(line, STATUS_WIDTH + GAP, y);
                    y += lineHeight;
                }
                g.setColor(new Color(Boolean.FALSE.equals(row.check().ok()) ? FAIL : TEXT));
                y = row.y() + ascent;
                for (String line : row.values()) {
                    g.drawString(line, valueX, y);
                    y += lineHeight;
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static void paintStatus(Graphics2D g, Boolean ok, float centerY) {
        float cx = STATUS_WIDTH / 2f;
        if (ok == null) {
            g.setColor(new Color(INFO));
            g.fill(new Ellipse2D.Float(cx - 3, centerY - 3, 6, 6));
            return;
        }
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float mark = new Path2D.Float();
        if (ok) {
            g.setColor(new Color(PASS));
            mark.moveTo(cx - 5, centerY);
            mark.lineTo(cx - 1.5f, centerY + 4);
            mark.lineTo(cx + 5.5f, centerY - 5);
        } else {
            g.setColor(new Color(FAIL));
            mark.moveTo(cx - 4.5f, centerY - 4.5f);
            mark.lineTo(cx + 4.5f, centerY + 4.5f);
            mark.moveTo(cx + 4.5f, centerY - 4.5f);
            mark.lineTo(cx - 4.5f, centerY + 4.5f);
        }
        g.draw(mark);
    }
}
