package io.quarkiverse.desktop.showcase.pages.beans;

import java.io.Serial;
import java.io.Serializable;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A serializable bean instantiated by {@link java.beans.Beans#instantiate(ClassLoader, String)} from the serialized
 * resource {@code showcase/print-misc/beans/greeting.ser} (bean name {@code showcase.print-misc.beans.greeting}).
 * <p>
 * The resource was generated once by serializing {@code new SerializedGreeting("Hello from a serialized bean", 3)}
 * with an {@link java.io.ObjectOutputStream}. Deserialization in a native executable needs the class to be registered
 * for serialization.
 */
@RegisterForReflection(serialization = true)
public class SerializedGreeting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String text;
    private int count;

    public SerializedGreeting() {
    }

    public SerializedGreeting(String text, int count) {
        this.text = text;
        this.count = count;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
