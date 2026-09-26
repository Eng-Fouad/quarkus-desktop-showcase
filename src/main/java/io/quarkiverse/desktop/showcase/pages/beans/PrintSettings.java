package io.quarkiverse.desktop.showcase.pages.beans;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.beans.ConstructorProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * An application bean persisted with {@link java.beans.XMLEncoder} : primitive, String, enumeration, date and class
 * properties, AWT values (color, font, insets, rectangle, dimension, point), collections, an array, an immutable value
 * ({@link Margins}, {@link ConstructorProperties}), a value needing a custom persistence delegate ({@link MediaSpec})
 * and a bean with an explicit bean info ({@link Ticket}).
 * <p>
 * Value-based {@link #equals} : the decoded bean equals the encoded one. Registered for reflection (the encoder and
 * decoder read and write its properties by reflection).
 */
@RegisterForReflection
public class PrintSettings {

    private String name = "default";
    private int copies = 1;
    private long jobId;
    private double scale = 1.0;
    private float ratio = 1f;
    private char separator = ',';
    private boolean duplex;
    private PrintJobBean.Orientation orientation = PrintJobBean.Orientation.PORTRAIT;
    private Date created;
    private Class<?> type;
    private String note;
    private Color color;
    private Font font;
    private Insets insets;
    private Rectangle area;
    private Dimension size;
    private Point origin;
    private List<String> tags = new ArrayList<>();
    private Map<String, Integer> counters = new LinkedHashMap<>();
    private int[] pages = {};
    private Margins margins;
    private MediaSpec media;
    private Ticket ticket;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCopies() {
        return copies;
    }

    public void setCopies(int copies) {
        this.copies = copies;
    }

    public long getJobId() {
        return jobId;
    }

    public void setJobId(long jobId) {
        this.jobId = jobId;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public float getRatio() {
        return ratio;
    }

    public void setRatio(float ratio) {
        this.ratio = ratio;
    }

    public char getSeparator() {
        return separator;
    }

    public void setSeparator(char separator) {
        this.separator = separator;
    }

    public boolean isDuplex() {
        return duplex;
    }

    public void setDuplex(boolean duplex) {
        this.duplex = duplex;
    }

    public PrintJobBean.Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(PrintJobBean.Orientation orientation) {
        this.orientation = orientation;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
    }

    public Insets getInsets() {
        return insets;
    }

    public void setInsets(Insets insets) {
        this.insets = insets;
    }

    public Rectangle getArea() {
        return area;
    }

    public void setArea(Rectangle area) {
        this.area = area;
    }

    public Dimension getSize() {
        return size;
    }

    public void setSize(Dimension size) {
        this.size = size;
    }

    public Point getOrigin() {
        return origin;
    }

    public void setOrigin(Point origin) {
        this.origin = origin;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Integer> getCounters() {
        return counters;
    }

    public void setCounters(Map<String, Integer> counters) {
        this.counters = counters;
    }

    public int[] getPages() {
        return pages;
    }

    public void setPages(int[] pages) {
        this.pages = pages;
    }

    public Margins getMargins() {
        return margins;
    }

    public void setMargins(Margins margins) {
        this.margins = margins;
    }

    public MediaSpec getMedia() {
        return media;
    }

    public void setMedia(MediaSpec media) {
        this.media = media;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    private List<Object> values() {
        return Arrays.asList(name, copies, jobId, scale, ratio, separator, duplex, orientation, created, type, note, color,
                font, insets, area, size, origin, tags, counters, Arrays.toString(pages), margins, media,
                ticket == null ? null : ticket.toString());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PrintSettings other && values().equals(other.values());
    }

    @Override
    public int hashCode() {
        return Objects.hash(values().toArray());
    }

    /**
     * An immutable value : the encoder uses the constructor named by {@link ConstructorProperties} and the getters.
     */
    @RegisterForReflection
    public static final class Margins {

        private final int top;
        private final int left;
        private final int bottom;
        private final int right;

        @ConstructorProperties({ "top", "left", "bottom", "right" })
        public Margins(int top, int left, int bottom, int right) {
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
        }

        public int getTop() {
            return top;
        }

        public int getLeft() {
            return left;
        }

        public int getBottom() {
            return bottom;
        }

        public int getRight() {
            return right;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Margins m && top == m.top && left == m.left && bottom == m.bottom && right == m.right;
        }

        @Override
        public int hashCode() {
            return Objects.hash(top, left, bottom, right);
        }

        @Override
        public String toString() {
            return top + "," + left + "," + bottom + "," + right;
        }
    }

    /**
     * A value without a public no-argument constructor nor constructor properties : encoded with a
     * {@link java.beans.DefaultPersistenceDelegate} naming its constructor arguments (set on the encoder).
     */
    @RegisterForReflection
    public static final class MediaSpec {

        private final String id;
        private final double width;
        private final double height;

        public MediaSpec(String id, double width, double height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        public String getId() {
            return id;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MediaSpec other && id.equals(other.id) && width == other.width && height == other.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, width, height);
        }

        @Override
        public String toString() {
            return id + " " + width + "x" + height;
        }
    }
}
