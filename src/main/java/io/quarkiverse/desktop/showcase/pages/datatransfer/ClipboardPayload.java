package io.quarkiverse.desktop.showcase.pages.datatransfer;

import java.io.Serializable;
import java.util.Objects;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The value of the custom serializable data flavor of the clipboard page.
 * <p>
 * Application-level native configuration : the clipboard serializes it (a local read goes through
 * {@code sun.awt.datatransfer.TransferableProxy}, a native read through {@code DataTransferer}), and
 * {@code DataFlavor} loads it by name from the MIME type ({@code class=} parameter). Neither is visible to the native
 * image builder, so the class is registered for reflection and serialization. Its fields are a {@code String} and an
 * {@code int} only : a collection field would need the registration of the JDK collection class for serialization too.
 */
@RegisterForReflection(serialization = true)
public class ClipboardPayload implements Serializable {

    private static final long serialVersionUID = 20260926L;

    private final String name;
    private final int number;

    public ClipboardPayload(String name, int number) {
        this.name = name;
        this.number = number;
    }

    public String name() {
        return name;
    }

    public int number() {
        return number;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClipboardPayload p && p.name.equals(name) && p.number == number;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, number);
    }

    @Override
    public String toString() {
        return name + " #" + number;
    }
}
