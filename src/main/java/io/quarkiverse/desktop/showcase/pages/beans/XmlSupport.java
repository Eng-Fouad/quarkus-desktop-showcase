package io.quarkiverse.desktop.showcase.pages.beans;

import java.beans.DefaultPersistenceDelegate;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.quarkiverse.desktop.showcase.core.Checks;

/**
 * {@link XMLEncoder} and {@link XMLDecoder} with the exceptions they report collected (both report recoverable errors to
 * an exception listener and go on) and a deterministic text : the {@code version} attribute of the root element (the
 * {@code java.version} of the runtime) is normalized.
 * <p>
 * AWT only ({@code java.beans}) : used by the AWT and the Swing persistence pages.
 */
public final class XmlSupport {

    private XmlSupport() {
    }

    /** Encoded XML and the exceptions reported while encoding. */
    public record Encoded(String xml, List<String> exceptions) {

        public int lines() {
            return (int) xml.lines().count();
        }

        public String sha256() {
            return Checks.sha256(xml);
        }
    }

    /** Decoded objects and the exceptions reported while decoding. */
    public record Decoded(List<Object> objects, List<String> exceptions) {
    }

    /**
     * Encodes {@code objects} ({@code configure} may set persistence delegates on the encoder).
     */
    public static Encoded encode(Consumer<XMLEncoder> configure, Object... objects) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> exceptions = new ArrayList<>();
        try (XMLEncoder encoder = new XMLEncoder(out, "UTF-8", true, 0)) {
            encoder.setExceptionListener(e -> exceptions.add(Checks.describe(e)));
            encoder.setPersistenceDelegate(PrintSettings.MediaSpec.class,
                    new DefaultPersistenceDelegate(new String[] { "id", "width", "height" }));
            if (configure != null) {
                configure.accept(encoder);
            }
            for (Object object : objects) {
                encoder.writeObject(object);
            }
        }
        String xml = new String(out.toByteArray(), StandardCharsets.UTF_8)
                .replaceFirst("<java version=\"[^\"]*\"", "<java version=\"(normalized)\"")
                .replace("\r\n", "\n");
        return new Encoded(xml, List.copyOf(exceptions));
    }

    public static Encoded encode(Object... objects) {
        return encode(null, objects);
    }

    /**
     * Decodes every object of {@code xml}, {@code owner} being the owner of the decoder.
     */
    public static Decoded decode(String xml, Object owner) {
        List<Object> objects = new ArrayList<>();
        List<String> exceptions = new ArrayList<>();
        try (XMLDecoder decoder = new XMLDecoder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), owner,
                e -> exceptions.add(Checks.describe(e)), XmlSupport.class.getClassLoader())) {
            while (true) {
                try {
                    objects.add(decoder.readObject());
                } catch (ArrayIndexOutOfBoundsException end) {
                    break;
                }
            }
        }
        return new Decoded(List.copyOf(objects), List.copyOf(exceptions));
    }

    /**
     * The first {@code count} lines of {@code xml}, long lines cut.
     */
    public static String excerpt(String xml, int count) {
        List<String> lines = new ArrayList<>();
        for (String line : xml.lines().limit(count).toList()) {
            lines.add(line.length() > 150 ? line.substring(0, 147) + "..." : line);
        }
        long total = xml.lines().count();
        if (total > count) {
            lines.add("... " + (total - count) + " more lines");
        }
        return String.join("\n", lines);
    }
}
