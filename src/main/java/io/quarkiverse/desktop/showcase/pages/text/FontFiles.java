package io.quarkiverse.desktop.showcase.pages.text;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the font files of the text pages from the bundled fonts, with code (no hand-crafted binary) :
 * <ul>
 * <li>{@link #rename} : a copy of a TrueType/OpenType font with a new {@code name} table (a family name that no
 * installed font has, plus localized family names : UTF-16, Shift_JIS and GBK encoded records), to test
 * {@code GraphicsEnvironment.registerFont} and localized names ({@code Font.getFamily(Locale)}) ;</li>
 * <li>{@link #collection} : a TrueType collection ({@code .ttc}) of several fonts, identical tables being shared ;</li>
 * <li>{@link Type1} : a small Type 1 font (PFB and PFA), with a few geometric glyphs.</li>
 * </ul>
 * Pure Java, no AWT : usable at build time as well as at run time.
 */
public final class FontFiles {

    /** OpenType name table ids. */
    public static final int FAMILY = 1;
    public static final int SUBFAMILY = 2;
    public static final int FULL_NAME = 4;
    public static final int POSTSCRIPT_NAME = 6;

    /** Windows platform, encoding ids. */
    public static final int WIN_UNICODE = 1;
    public static final int WIN_SHIFT_JIS = 2;
    public static final int WIN_PRC = 3;

    /** Windows language ids (LCID). */
    public static final int EN_US = 0x0409;
    public static final int FR_FR = 0x040C;
    public static final int AR_SA = 0x0401;
    public static final int JA_JP = 0x0411;
    public static final int ZH_CN = 0x0804;
    public static final int DE_DE = 0x0407;

    private FontFiles() {
    }

    /**
     * A record of the name table : platform 3 (Windows) with an encoding and a language, or platform 1 (Macintosh,
     * Roman, English) when {@code platform} is 1.
     */
    public record Name(int platform, int encoding, int language, int nameId, String value) {

        public static Name windows(int language, int nameId, String value) {
            return new Name(3, WIN_UNICODE, language, nameId, value);
        }

        public static Name windows(int encoding, int language, int nameId, String value) {
            return new Name(3, encoding, language, nameId, value);
        }

        public static Name mac(int nameId, String value) {
            return new Name(1, 0, 0, nameId, value);
        }

        byte[] bytes() {
            Charset charset = switch (platform == 1 ? -1 : encoding) {
                case -1 -> StandardCharsets.ISO_8859_1;
                case WIN_SHIFT_JIS -> Charset.forName("Shift_JIS");
                case WIN_PRC -> Charset.forName("GBK");
                default -> StandardCharsets.UTF_16BE;
            };
            byte[] encoded = value.getBytes(charset);
            if (platform == 3 && (encoding == WIN_SHIFT_JIS || encoding == WIN_PRC)) {
                // double-byte encodings are stored as 16 bit units : single bytes get a leading zero
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int i = 0; i < encoded.length; i++) {
                    int b = encoded[i] & 0xFF;
                    if (b < 0x80) {
                        out.write(0);
                        out.write(b);
                    } else {
                        out.write(b);
                        out.write(encoded[++i]);
                    }
                }
                return out.toByteArray();
            }
            return encoded;
        }
    }

    /**
     * The tables of a TrueType/OpenType font file (the first font of a collection is not supported : single fonts
     * only), in file order.
     */
    public static Map<String, byte[]> tables(byte[] font) {
        ByteBuffer b = ByteBuffer.wrap(font);
        int version = b.getInt(0);
        if (version == 0x74746366) { // 'ttcf'
            throw new IllegalArgumentException("font collection");
        }
        int numTables = b.getShort(4) & 0xFFFF;
        Map<String, byte[]> tables = new LinkedHashMap<>();
        for (int i = 0; i < numTables; i++) {
            int record = 12 + 16 * i;
            String tag = new String(font, record, 4, StandardCharsets.ISO_8859_1);
            int offset = b.getInt(record + 8);
            int length = b.getInt(record + 12);
            tables.put(tag, Arrays.copyOfRange(font, offset, offset + length));
        }
        return tables;
    }

    /**
     * The sfnt version of a font file ({@code 0x00010000} TrueType outlines, {@code OTTO} CFF outlines).
     */
    public static int sfntVersion(byte[] font) {
        return ByteBuffer.wrap(font).getInt(0);
    }

    /**
     * {@code font} with its name table replaced by {@code names}.
     */
    public static byte[] rename(byte[] font, List<Name> names) {
        Map<String, byte[]> tables = new LinkedHashMap<>(tables(font));
        tables.put("name", nameTable(names));
        return sfnt(sfntVersion(font), tables);
    }

    static byte[] nameTable(List<Name> names) {
        List<Name> sorted = new ArrayList<>(names);
        sorted.sort(Comparator.comparingInt(Name::platform).thenComparingInt(Name::encoding)
                .thenComparingInt(Name::language).thenComparingInt(Name::nameId));
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(6 + 12 * sorted.size());
        header.putShort((short) 0).putShort((short) sorted.size()).putShort((short) (6 + 12 * sorted.size()));
        for (Name name : sorted) {
            byte[] bytes = name.bytes();
            header.putShort((short) name.platform()).putShort((short) name.encoding()).putShort((short) name.language())
                    .putShort((short) name.nameId()).putShort((short) bytes.length).putShort((short) strings.size());
            strings.writeBytes(bytes);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header.array());
        out.writeBytes(strings.toByteArray());
        return out.toByteArray();
    }

    /**
     * A single font file made of {@code tables} (sorted by tag, 4 byte aligned, with checksums).
     */
    static byte[] sfnt(int version, Map<String, byte[]> tables) {
        List<String> tags = new ArrayList<>(tables.keySet());
        tags.sort(Comparator.naturalOrder());
        int headerSize = 12 + 16 * tags.size();
        ByteBuffer out = ByteBuffer.allocate(headerSize + tables.values().stream().mapToInt(t -> pad(t.length)).sum());
        writeOffsetTable(out, version, tags.size());
        int offset = headerSize;
        for (String tag : tags) {
            byte[] table = tables.get(tag);
            out.put(tag.getBytes(StandardCharsets.ISO_8859_1)).putInt(checksum(table)).putInt(offset)
                    .putInt(table.length);
            offset += pad(table.length);
        }
        for (String tag : tags) {
            byte[] table = tables.get(tag);
            out.put(table);
            out.position(out.position() + pad(table.length) - table.length);
        }
        return out.array();
    }

    /**
     * A TrueType collection ({@code ttcf} version 1) of {@code fonts} : tables with identical content are stored once.
     */
    public static byte[] collection(List<byte[]> fonts) {
        List<Map<String, byte[]>> all = fonts.stream().map(FontFiles::tables).toList();
        // unique tables, in order of first use
        List<byte[]> unique = new ArrayList<>();
        for (Map<String, byte[]> tables : all) {
            for (byte[] table : tables.values()) {
                if (unique.stream().noneMatch(u -> Arrays.equals(u, table))) {
                    unique.add(table);
                }
            }
        }
        int headerSize = 12 + 4 * fonts.size();
        int directories = all.stream().mapToInt(t -> 12 + 16 * t.size()).sum();
        int[] offsets = new int[unique.size()];
        int offset = headerSize + directories;
        for (int i = 0; i < unique.size(); i++) {
            offsets[i] = offset;
            offset += pad(unique.get(i).length);
        }
        ByteBuffer out = ByteBuffer.allocate(offset);
        out.putInt(0x74746366).putInt(0x00010000).putInt(fonts.size());
        int directory = headerSize;
        for (Map<String, byte[]> tables : all) {
            out.putInt(directory);
            directory += 12 + 16 * tables.size();
        }
        for (int f = 0; f < fonts.size(); f++) {
            Map<String, byte[]> tables = all.get(f);
            List<String> tags = new ArrayList<>(tables.keySet());
            tags.sort(Comparator.naturalOrder());
            writeOffsetTable(out, sfntVersion(fonts.get(f)), tags.size());
            for (String tag : tags) {
                byte[] table = tables.get(tag);
                int index = 0;
                while (!Arrays.equals(unique.get(index), table)) {
                    index++;
                }
                out.put(tag.getBytes(StandardCharsets.ISO_8859_1)).putInt(checksum(table)).putInt(offsets[index])
                        .putInt(table.length);
            }
        }
        for (byte[] table : unique) {
            out.put(table);
            out.position(out.position() + pad(table.length) - table.length);
        }
        return out.array();
    }

    private static void writeOffsetTable(ByteBuffer out, int version, int numTables) {
        int entrySelector = 31 - Integer.numberOfLeadingZeros(numTables);
        int searchRange = (1 << entrySelector) * 16;
        out.putInt(version).putShort((short) numTables).putShort((short) searchRange).putShort((short) entrySelector)
                .putShort((short) (numTables * 16 - searchRange));
    }

    private static int pad(int length) {
        return (length + 3) & ~3;
    }

    private static int checksum(byte[] table) {
        int sum = 0;
        for (int i = 0; i < table.length; i += 4) {
            int value = 0;
            for (int j = 0; j < 4; j++) {
                value = (value << 8) | (i + j < table.length ? table[i + j] & 0xFF : 0);
            }
            sum += value;
        }
        return sum;
    }

    // ------------------------------------------------------------------------------------------------------ Type 1

    /**
     * A Type 1 font with the glyphs {@code space}, {@code T Y P E O N}, {@code one} and {@code o} (straight outlines and
     * curves, one glyph with a counter), 1000 units per em, standard encoding : "TYPE ONE" and "1 o" can be written.
     */
    public static final class Type1 {

        public static final String FONT_NAME = "ShowcaseType1-Regular";
        public static final String FAMILY_NAME = "Showcase Type1";
        public static final String FULL_NAME = "Showcase Type1 Regular";

        private static final int EEXEC_KEY = 55665;
        private static final int CHARSTRING_KEY = 4330;

        // charstring commands
        private static final int HSBW = 13;
        private static final int RMOVETO = 21;
        private static final int RLINETO = 5;
        private static final int RRCURVETO = 8;
        private static final int CLOSEPATH = 9;
        private static final int ENDCHAR = 14;
        private static final int RETURN = 11;
        private static final int CALLOTHERSUBR = 16;
        private static final int POP = 17;
        private static final int SETCURRENTPOINT = 33;

        private Type1() {
        }

        /**
         * The font as a PFB file (binary segments).
         */
        public static byte[] pfb() {
            byte[] clear = clearText().getBytes(StandardCharsets.ISO_8859_1);
            byte[] encrypted = encrypt(privateSection(), EEXEC_KEY);
            byte[] trailer = trailer().getBytes(StandardCharsets.ISO_8859_1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            segment(out, 1, clear);
            segment(out, 2, encrypted);
            segment(out, 1, trailer);
            out.write(0x80);
            out.write(3);
            return out.toByteArray();
        }

        /**
         * The font as a PFA file (the encrypted part in hexadecimal).
         */
        public static byte[] pfa() {
            byte[] encrypted = encrypt(privateSection(), EEXEC_KEY);
            StringBuilder sb = new StringBuilder(clearText());
            for (int i = 0; i < encrypted.length; i++) {
                sb.append(String.format("%02x", encrypted[i] & 0xFF));
                if (i % 32 == 31) {
                    sb.append('\n');
                }
            }
            sb.append('\n').append(trailer());
            return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        }

        private static void segment(ByteArrayOutputStream out, int type, byte[] data) {
            out.write(0x80);
            out.write(type);
            out.write(data.length & 0xFF);
            out.write((data.length >> 8) & 0xFF);
            out.write((data.length >> 16) & 0xFF);
            out.write((data.length >> 24) & 0xFF);
            out.writeBytes(data);
        }

        private static String clearText() {
            return "%!PS-AdobeFont-1.0: " + FONT_NAME + " 001.000\n"
                    + "%%Title: " + FONT_NAME + "\n"
                    + "%Generated by the quarkus-desktop showcase (io.quarkiverse.desktop.showcase.pages.text.FontFiles)\n"
                    + "11 dict begin\n"
                    + "/FontInfo 9 dict dup begin\n"
                    + "/version (001.000) readonly def\n"
                    + "/Notice (Generated test font, public domain) readonly def\n"
                    + "/FullName (" + FULL_NAME + ") readonly def\n"
                    + "/FamilyName (" + FAMILY_NAME + ") readonly def\n"
                    + "/Weight (Regular) readonly def\n"
                    + "/ItalicAngle 0 def\n"
                    + "/isFixedPitch false def\n"
                    + "/UnderlinePosition -100 def\n"
                    + "/UnderlineThickness 50 def\n"
                    + "end readonly def\n"
                    + "/FontName /" + FONT_NAME + " def\n"
                    + "/Encoding StandardEncoding def\n"
                    + "/PaintType 0 def\n"
                    + "/FontType 1 def\n"
                    + "/FontMatrix [0.001 0 0 0.001 0 0] readonly def\n"
                    + "/FontBBox {0 -10 700 710} readonly def\n"
                    + "currentdict end\n"
                    + "currentfile eexec\n";
        }

        private static String trailer() {
            StringBuilder sb = new StringBuilder();
            for (int line = 0; line < 8; line++) {
                sb.append("0".repeat(64)).append('\n');
            }
            return sb.append("cleartomark\n").toString();
        }

        private static byte[] privateSection() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 4 leading bytes (usually random ; fixed here : the file is deterministic)
            out.writeBytes(new byte[] { 0x0A, 0x0B, 0x0C, 0x0D });
            ascii(out, "dup /Private 9 dict dup begin\n"
                    + "/RD {string currentfile exch readstring pop} executeonly def\n"
                    + "/ND {noaccess def} executeonly def\n"
                    + "/NP {noaccess put} executeonly def\n"
                    + "/lenIV 4 def\n"
                    + "/MinFeature {16 16} def\n"
                    + "/password 5839 def\n"
                    + "/BlueValues [-10 0 700 710] def\n"
                    + "/StdVW [100] def\n");
            // the 4 standard subroutines (flex and hint replacement)
            List<byte[]> subrs = List.of(
                    new CharString().n(3, 0).esc(CALLOTHERSUBR).esc(POP).esc(POP).esc(SETCURRENTPOINT).op(RETURN).bytes(),
                    new CharString().n(0, 1).esc(CALLOTHERSUBR).op(RETURN).bytes(),
                    new CharString().n(0, 2).esc(CALLOTHERSUBR).op(RETURN).bytes(),
                    new CharString().op(RETURN).bytes());
            ascii(out, "/Subrs " + subrs.size() + " array\n");
            for (int i = 0; i < subrs.size(); i++) {
                byte[] encrypted = encrypt(subrs.get(i), CHARSTRING_KEY);
                ascii(out, "dup " + i + " " + encrypted.length + " RD ");
                out.writeBytes(encrypted);
                ascii(out, " NP\n");
            }
            ascii(out, "ND\n");
            Map<String, byte[]> glyphs = glyphs();
            ascii(out, "2 index /CharStrings " + glyphs.size() + " dict dup begin\n");
            for (Map.Entry<String, byte[]> glyph : glyphs.entrySet()) {
                byte[] encrypted = encrypt(glyph.getValue(), CHARSTRING_KEY);
                ascii(out, "/" + glyph.getKey() + " " + encrypted.length + " RD ");
                out.writeBytes(encrypted);
                ascii(out, " ND\n");
            }
            ascii(out, "end\nend\nreadonly put\nnoaccess put\ndup /FontName get exch definefont pop\n"
                    + "mark currentfile closefile\n");
            return out.toByteArray();
        }

        private static void ascii(ByteArrayOutputStream out, String s) {
            out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
        }

        /**
         * Glyph outlines : polygons in font units (y up), outer contours counterclockwise, counters clockwise.
         */
        private static Map<String, byte[]> glyphs() {
            Map<String, byte[]> glyphs = new LinkedHashMap<>();
            glyphs.put(".notdef", polygons(500));
            glyphs.put("space", polygons(300));
            glyphs.put("T", polygons(600, new int[] { 50, 700, 50, 600, 250, 600, 250, 0, 350, 0, 350, 600, 550, 600, 550,
                    700 }));
            glyphs.put("Y", polygons(600, new int[] { 250, 0, 350, 0, 350, 300, 560, 700, 450, 700, 300, 400, 150, 700, 40,
                    700, 250, 300 }));
            glyphs.put("P", polygons(580, new int[] { 80, 0, 180, 0, 180, 280, 520, 280, 520, 700, 80, 700 },
                    new int[] { 180, 380, 180, 600, 420, 600, 420, 380 }));
            glyphs.put("E", polygons(550, new int[] { 80, 0, 500, 0, 500, 100, 180, 100, 180, 300, 420, 300, 420, 400,
                    180, 400, 180, 600, 500, 600, 500, 700, 80, 700 }));
            glyphs.put("O", polygons(650, new int[] { 60, 0, 590, 0, 590, 700, 60, 700 },
                    new int[] { 160, 100, 160, 600, 490, 600, 490, 100 }));
            glyphs.put("N", polygons(650, new int[] { 70, 0, 170, 0, 170, 520, 480, 0, 580, 0, 580, 700, 480, 700, 480,
                    180, 170, 700, 70, 700 }));
            glyphs.put("one", polygons(500, new int[] { 200, 0, 300, 0, 300, 700, 220, 700, 100, 600, 130, 560, 200,
                    610 }));
            glyphs.put("o", ring(600, 300, 250, 250, 150));
            return glyphs;
        }

        private static byte[] polygons(int width, int[]... contours) {
            CharString cs = new CharString().n(0, width).op(HSBW);
            int x = 0;
            int y = 0;
            for (int[] contour : contours) {
                cs.n(contour[0] - x, contour[1] - y).op(RMOVETO);
                x = contour[0];
                y = contour[1];
                for (int i = 2; i < contour.length; i += 2) {
                    cs.n(contour[i] - x, contour[i + 1] - y).op(RLINETO);
                    x = contour[i];
                    y = contour[i + 1];
                }
                // closepath of a charstring does not move the current point (unlike PostScript's closepath) : it
                // stays at the last point of the contour
                cs.op(CLOSEPATH);
            }
            return cs.op(ENDCHAR).bytes();
        }

        /**
         * A ring (outer circle counterclockwise, inner circle clockwise), 4 cubic curves per circle.
         */
        private static byte[] ring(int width, int cx, int cy, int outer, int inner) {
            CharString cs = new CharString().n(0, width).op(HSBW);
            cs.n(cx + outer, cy).op(RMOVETO);
            circle(cs, outer, true);
            cs.op(CLOSEPATH);
            cs.n(inner - outer, 0).op(RMOVETO);
            circle(cs, inner, false);
            cs.op(CLOSEPATH);
            return cs.op(ENDCHAR).bytes();
        }

        private static void circle(CharString cs, int r, boolean counterclockwise) {
            int k = (int) Math.round(r * 0.5523);
            int s = counterclockwise ? 1 : -1;
            // from (r, 0) : quarter arcs, relative control points
            int[][] quarters = {
                    { 0, k, k - r, r - k, -k, 0 },
                    { -k, 0, k - r, k - r, 0, -k },
                    { 0, -k, r - k, k - r, k, 0 },
                    { k, 0, r - k, r - k, 0, k } };
            for (int[] q : quarters) {
                cs.n(q[0], s * q[1], q[2], s * q[3], q[4], s * q[5]).op(RRCURVETO);
            }
        }

        /**
         * Builds a charstring : numbers ({@link #n}) followed by their operator ({@link #op}, {@link #esc} for the
         * {@code 12 x} escaped operators).
         */
        private static final class CharString {

            private final ByteArrayOutputStream out = new ByteArrayOutputStream();

            CharString n(int... values) {
                for (int v : values) {
                    if (v >= -107 && v <= 107) {
                        out.write(v + 139);
                    } else if (v >= 108 && v <= 1131) {
                        out.write((v - 108) / 256 + 247);
                        out.write((v - 108) % 256);
                    } else if (v >= -1131 && v <= -108) {
                        out.write((-v - 108) / 256 + 251);
                        out.write((-v - 108) % 256);
                    } else {
                        out.write(255);
                        out.writeBytes(ByteBuffer.allocate(4).putInt(v).array());
                    }
                }
                return this;
            }

            CharString op(int op) {
                out.write(op);
                return this;
            }

            CharString esc(int op) {
                out.write(12);
                out.write(op);
                return this;
            }

            byte[] bytes() {
                return out.toByteArray();
            }
        }

        /**
         * Type 1 encryption (eexec or charstring key), with 4 fixed leading bytes for charstrings.
         */
        private static byte[] encrypt(byte[] plain, int key) {
            byte[] input = plain;
            if (key == CHARSTRING_KEY) {
                input = new byte[plain.length + 4];
                System.arraycopy(plain, 0, input, 4, plain.length);
            }
            byte[] out = new byte[input.length];
            int r = key;
            for (int i = 0; i < input.length; i++) {
                int c = (input[i] & 0xFF) ^ (r >> 8);
                out[i] = (byte) c;
                r = ((c + r) * 52845 + 22719) & 0xFFFF;
            }
            return out;
        }
    }
}
