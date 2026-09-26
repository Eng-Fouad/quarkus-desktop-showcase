package io.quarkiverse.desktop.showcase.pages.beans;

import java.awt.Color;
import java.beans.BeanProperty;
import java.beans.JavaBean;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventListener;
import java.util.EventObject;
import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * An application JavaBean : bound, constrained, indexed, read-only and write-only properties, an enumeration, an AWT
 * {@link Color}, an event set of its own, and the {@link JavaBean} / {@link BeanProperty} annotations read by the
 * {@link java.beans.Introspector}.
 * <p>
 * Native executables : the {@code Introspector}, {@code XMLEncoder} and {@code XMLDecoder} find the members of a bean by
 * reflection, so the application registers its bean classes ({@link RegisterForReflection}).
 */
@JavaBean(description = "A print job of the showcase", defaultProperty = "title", defaultEventSet = "job")
@RegisterForReflection
public class PrintJobBean {

    /** Page orientation of a job. */
    @RegisterForReflection
    public enum Orientation {
        PORTRAIT,
        LANDSCAPE
    }

    /** Listener of the job events of a {@link PrintJobBean}. */
    public interface JobListener extends EventListener {

        void jobStarted(JobEvent event);

        void jobFinished(JobEvent event);
    }

    /** A job event. */
    public static class JobEvent extends EventObject {

        private final String what;

        public JobEvent(Object source, String what) {
            super(source);
            this.what = what;
        }

        public String getWhat() {
            return what;
        }
    }

    /** Values of the {@code copies} property named by {@link BeanProperty#enumerationValues()}. */
    public static final int ONE_COPY = 1;
    public static final int TWO_COPIES = 2;

    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private final VetoableChangeSupport vetoes = new VetoableChangeSupport(this);
    private final List<JobListener> listeners = new ArrayList<>();

    private String title = "Untitled";
    private int copies = 1;
    private boolean duplex;
    private Orientation orientation = Orientation.PORTRAIT;
    private Color color = new Color(0x1E88E5);
    private int[] pages = { 1, 2, 3 };
    private double scale = 1.0;
    private String password;

    public String getTitle() {
        return title;
    }

    @BeanProperty(bound = true, preferred = true, description = "The title printed in the page header")
    public void setTitle(String title) {
        String old = this.title;
        this.title = title;
        changes.firePropertyChange("title", old, title);
    }

    public int getCopies() {
        return copies;
    }

    /**
     * A constrained property : vetoable change listeners may refuse the new value.
     */
    @BeanProperty(bound = true, description = "Number of copies", enumerationValues = { "ONE_COPY",
            "PrintJobBean.TWO_COPIES" })
    public void setCopies(int copies) throws PropertyVetoException {
        int old = this.copies;
        vetoes.fireVetoableChange("copies", old, copies);
        this.copies = copies;
        changes.firePropertyChange("copies", old, copies);
    }

    public boolean isDuplex() {
        return duplex;
    }

    @BeanProperty(bound = true, expert = true)
    public void setDuplex(boolean duplex) {
        boolean old = this.duplex;
        this.duplex = duplex;
        changes.firePropertyChange("duplex", old, duplex);
    }

    public Orientation getOrientation() {
        return orientation;
    }

    @BeanProperty(bound = true)
    public void setOrientation(Orientation orientation) {
        Orientation old = this.orientation;
        this.orientation = orientation;
        changes.firePropertyChange("orientation", old, orientation);
    }

    public Color getColor() {
        return color;
    }

    @BeanProperty(bound = true, visualUpdate = true)
    public void setColor(Color color) {
        Color old = this.color;
        this.color = color;
        changes.firePropertyChange("color", old, color);
    }

    public int[] getPages() {
        return pages.clone();
    }

    public void setPages(int[] pages) {
        int[] old = this.pages;
        this.pages = pages.clone();
        changes.firePropertyChange("pages", old, this.pages);
    }

    /** Indexed read method. */
    public int getPages(int index) {
        return pages[index];
    }

    /** Indexed write method : fires an {@link java.beans.IndexedPropertyChangeEvent}. */
    public void setPages(int index, int page) {
        int old = pages[index];
        pages[index] = page;
        changes.fireIndexedPropertyChange("pages", index, old, page);
    }

    public double getScale() {
        return scale;
    }

    @BeanProperty(bound = false, hidden = true)
    public void setScale(double scale) {
        this.scale = scale;
    }

    /** A read-only property. */
    public String getSummary() {
        return title + " x" + copies + (duplex ? " duplex " : " ") + orientation + " pages " + Arrays.toString(pages);
    }

    /** A write-only property. */
    public void setPassword(String password) {
        this.password = password;
    }

    boolean hasPassword() {
        return password != null;
    }

    /** A plain method (a method descriptor, not a property). */
    public void start() {
        JobEvent event = new JobEvent(this, "started " + title);
        for (JobListener listener : List.copyOf(listeners)) {
            listener.jobStarted(event);
        }
    }

    public void addJobListener(JobListener listener) {
        listeners.add(listener);
    }

    public void removeJobListener(JobListener listener) {
        listeners.remove(listener);
    }

    public JobListener[] getJobListeners() {
        return listeners.toArray(JobListener[]::new);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
        changes.addPropertyChangeListener(property, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return changes.getPropertyChangeListeners();
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        vetoes.addVetoableChangeListener(listener);
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        vetoes.removeVetoableChangeListener(listener);
    }
}
