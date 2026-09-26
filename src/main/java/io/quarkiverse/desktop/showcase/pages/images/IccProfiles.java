package io.quarkiverse.desktop.showcase.pages.images;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates ICC profiles with code (version 2.1 matrix/TRC RGB display profiles), so that no third party profile has to
 * be bundled : a wide gamut RGB space with the Adobe RGB (1998) primaries (adapted to D50) and a gamma 2.2 curve.
 * Pure Java (no AWT) : the page creates {@code ICC_Profile} objects from these bytes.
 */
final class IccProfiles {

    static final String WIDE_DESCRIPTION = "Showcase wide gamut RGB (gamma 2.2)";
    /** D50, the profile connection space illuminant. */
    static final double[] D50 = { 0.9642, 1.0, 0.8249 };
    /** Adobe RGB (1998) colorants adapted to D50. */
    static final double[][] WIDE_PRIMARIES = { { 0.6097, 0.3111, 0.0195 }, { 0.2053, 0.6257, 0.0609 },
            { 0.1492, 0.0632, 0.7446 } };

    private IccProfiles() {
    }

    /**
     * The wide gamut RGB profile.
     */
    static byte[] wideGamut() {
        return rgbProfile(WIDE_DESCRIPTION, WIDE_PRIMARIES, 2.2);
    }

    /**
     * A matrix/TRC RGB display profile (ICC v2.1, 'mntr', RGB, PCS XYZ).
     */
    static byte[] rgbProfile(String description, double[][] primaries, double gamma) {
        Map<String, byte[]> tags = new LinkedHashMap<>();
        tags.put("desc", textDescription(description));
        tags.put("cprt", text("No copyright, generated test profile"));
        tags.put("wtpt", xyz(D50));
        tags.put("rXYZ", xyz(primaries[0]));
        tags.put("gXYZ", xyz(primaries[1]));
        tags.put("bXYZ", xyz(primaries[2]));
        byte[] curve = curve(gamma);
        tags.put("rTRC", curve);
        tags.put("gTRC", curve);
        tags.put("bTRC", curve);
        return profile(tags);
    }

    private static byte[] profile(Map<String, byte[]> tags) {
        int headerSize = 128;
        int tableSize = 4 + 12 * tags.size();
        // shared tag data (the three TRC tags point to the same curve)
        List<byte[]> data = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        int offset = headerSize + tableSize;
        Map<byte[], Integer> placed = new java.util.IdentityHashMap<>();
        int[] tagOffsets = new int[tags.size()];
        int i = 0;
        for (byte[] tag : tags.values()) {
            Integer existing = placed.get(tag);
            if (existing == null) {
                placed.put(tag, offset);
                data.add(tag);
                offsets.add(offset);
                tagOffsets[i] = offset;
                offset += pad(tag.length);
            } else {
                tagOffsets[i] = existing;
            }
            i++;
        }
        int size = offset;
        ByteBuffer b = ByteBuffer.allocate(size);
        // header
        b.putInt(size);
        b.putInt(0); // preferred CMM
        b.putInt(0x02100000); // version 2.1
        b.put(ascii("mntr")).put(ascii("RGB ")).put(ascii("XYZ "));
        b.putShort((short) 2026).putShort((short) 1).putShort((short) 2).putShort((short) 3).putShort((short) 4)
                .putShort((short) 5);
        b.put(ascii("acsp"));
        b.putInt(0); // platform
        b.putInt(0); // flags
        b.putInt(0); // manufacturer
        b.putInt(0); // model
        b.putLong(0); // attributes
        b.putInt(0); // rendering intent : perceptual
        b.putInt(s15Fixed16(D50[0])).putInt(s15Fixed16(D50[1])).putInt(s15Fixed16(D50[2]));
        b.putInt(0); // creator
        b.position(headerSize);
        // tag table
        b.putInt(tags.size());
        i = 0;
        for (Map.Entry<String, byte[]> tag : tags.entrySet()) {
            b.put(ascii(tag.getKey())).putInt(tagOffsets[i]).putInt(tag.getValue().length);
            i++;
        }
        for (int t = 0; t < data.size(); t++) {
            b.position(offsets.get(t));
            b.put(data.get(t));
        }
        return b.array();
    }

    private static int pad(int length) {
        return (length + 3) & ~3;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    static int s15Fixed16(double v) {
        return (int) Math.round(v * 65536);
    }

    private static byte[] xyz(double[] xyz) {
        return ByteBuffer.allocate(20).put(ascii("XYZ ")).putInt(0).putInt(s15Fixed16(xyz[0])).putInt(
                s15Fixed16(xyz[1])).putInt(s15Fixed16(xyz[2])).array();
    }

    /**
     * {@code curv} with a single entry : a u8Fixed8 gamma.
     */
    private static byte[] curve(double gamma) {
        return ByteBuffer.allocate(14).put(ascii("curv")).putInt(0).putInt(1).putShort((short) Math.round(gamma * 256))
                .array();
    }

    private static byte[] text(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("text"));
        out.writeBytes(new byte[4]);
        out.writeBytes(ascii(s));
        out.write(0);
        return out.toByteArray();
    }

    /**
     * {@code desc} (textDescriptionType of ICC v2) : ASCII description, empty Unicode and ScriptCode parts.
     */
    static byte[] textDescription(String s) {
        byte[] ascii = ascii(s);
        ByteBuffer b = ByteBuffer.allocate(12 + ascii.length + 1 + 8 + 3 + 67);
        b.put(ascii("desc")).putInt(0).putInt(ascii.length + 1).put(ascii).put((byte) 0);
        b.putInt(0).putInt(0); // unicode language code and count
        b.putShort((short) 0).put((byte) 0); // scriptcode code and count
        return b.array();
    }

    /**
     * The ASCII text of a {@code desc} tag (v2 textDescriptionType) or of a {@code mluc} tag (v4, first record).
     */
    static String describe(byte[] tag) {
        if (tag == null || tag.length < 12) {
            return "none";
        }
        ByteBuffer b = ByteBuffer.wrap(tag);
        String type = new String(tag, 0, 4, StandardCharsets.US_ASCII);
        if (type.equals("desc")) {
            int count = b.getInt(8);
            return new String(tag, 12, Math.max(0, count - 1), StandardCharsets.US_ASCII);
        }
        if (type.equals("mluc")) {
            int records = b.getInt(8);
            if (records > 0) {
                int length = b.getInt(20);
                int offset = b.getInt(24);
                return new String(tag, offset, length, StandardCharsets.UTF_16BE);
            }
        }
        if (type.equals("text")) {
            return new String(tag, 8, tag.length - 9, StandardCharsets.US_ASCII);
        }
        return type;
    }
}
