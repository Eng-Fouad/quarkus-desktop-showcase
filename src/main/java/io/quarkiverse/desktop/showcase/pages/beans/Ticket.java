package io.quarkiverse.desktop.showcase.pages.beans;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * An application JavaBean described by an explicit {@link TicketBeanInfo} (found by name by the
 * {@link java.beans.Introspector} : {@code <bean class name>BeanInfo}), edited by a {@link TicketEditor} registered
 * with the {@link java.beans.PropertyEditorManager}.
 * <p>
 * Registered for reflection with its bean info and editor (native executables).
 */
@RegisterForReflection
public class Ticket {

    private String code = "Q-001";
    private int seat = 12;
    private String internal = "not a property of the bean info";

    public Ticket() {
    }

    public Ticket(String code, int seat) {
        this.code = code;
        this.seat = seat;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getSeat() {
        return seat;
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    public String getInternal() {
        return internal;
    }

    public void setInternal(String internal) {
        this.internal = internal;
    }

    @Override
    public String toString() {
        return code + "/" + seat;
    }
}
