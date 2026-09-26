package io.quarkiverse.desktop.showcase.pages.beans;

import java.beans.PropertyEditorSupport;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * An application property editor of {@link Ticket} values ({@code code/seat} as text), instantiated by reflection by
 * the {@link java.beans.PropertyEditorManager} and by {@link java.beans.PropertyDescriptor#createPropertyEditor}.
 */
@RegisterForReflection
public class TicketEditor extends PropertyEditorSupport {

    @Override
    public String getAsText() {
        return getValue() instanceof Ticket ticket ? ticket.getCode() + "/" + ticket.getSeat() : "";
    }

    @Override
    public void setAsText(String text) {
        String[] parts = text.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("code/seat expected: " + text);
        }
        setValue(new Ticket(parts[0], Integer.parseInt(parts[1])));
    }

    @Override
    public String getJavaInitializationString() {
        return getValue() instanceof Ticket ticket
                ? "new " + Ticket.class.getName() + "(\"" + ticket.getCode() + "\", " + ticket.getSeat() + ")"
                : "null";
    }

    @Override
    public String[] getTags() {
        return new String[] { "Q-001/12", "Q-002/14" };
    }
}
