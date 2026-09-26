package io.quarkiverse.desktop.showcase.pages.text;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.pages.text.FontFiles.Name;

/**
 * The fonts of the text pages : bundled font files (classpath resources under {@code /showcase/text-images/fonts/}, see
 * THIRD-PARTY-NOTICES.md) and fonts generated from them at run time by {@link FontFiles}.
 * <p>
 * Fonts are created once per process (never in a static initializer : Quarkus initializes application classes at build
 * time, AWT classes at run time) with {@code Font.createFont(TRUETYPE_FONT, InputStream)}, which copies the data to a
 * temporary file tracked by the JDK.
 */
public final class TextAssets {

    public static final String FONTS = "/showcase/text-images/fonts/";
    /** Roboto Light (Apache License 2.0) : Latin, Greek, Cyrillic, kerning and ligatures. */
    public static final String ROBOTO = FONTS + "Roboto-Light.ttf";
    /** Noto Sans Hebrew (OFL 1.1), OpenType with CFF outlines. */
    public static final String HEBREW = FONTS + "NotoSansHebrew-Regular.otf";
    /** Droid Arabic Kufi (Apache License 2.0). */
    public static final String KUFI = FONTS + "DroidKufi-Regular.ttf";
    /** Noto Sans Devanagari (OFL 1.1). */
    public static final String DEVANAGARI = FONTS + "NotoSansDevanagari-Regular.ttf";
    /** Noto Sans Thai (OFL 1.1). */
    public static final String THAI = FONTS + "NotoSansThai-Regular.ttf";
    /** Font Awesome 5 Free Solid (OFL 1.1) : icons in the Unicode private use area. */
    public static final String AWESOME = FONTS + "fa-solid-900.ttf";

    /** Family of the renamed Roboto Light registered with {@code GraphicsEnvironment.registerFont}. */
    public static final String SHOWCASE_SANS = "Showcase Sans";
    public static final String COLLECTION_A = "Showcase Collection A";
    public static final String COLLECTION_B = "Showcase Collection B";

    private static final Map<String, Font> FONTS_BY_RESOURCE = new HashMap<>();
    private static Font showcaseSans;
    private static Boolean showcaseSansRegistered;
    private static byte[] showcaseSansBytes;
    private static byte[] collectionBytes;

    private TextAssets() {
    }

    /**
     * A bundled font (size 1), created once.
     */
    public static synchronized Font font(String resource) {
        Font font = FONTS_BY_RESOURCE.get(resource);
        if (font == null) {
            try (InputStream in = Edt.resourceStream(resource)) {
                font = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (FontFormatException e) {
                throw new IllegalStateException(resource + ": " + e.getMessage(), e);
            }
            FONTS_BY_RESOURCE.put(resource, font);
        }
        return font;
    }

    public static Font font(String resource, float size) {
        return font(resource).deriveFont(size);
    }

    public static Font roboto(float size) {
        return font(ROBOTO, size);
    }

    /**
     * The name table of the renamed Roboto Light : English (Windows and Macintosh records) and localized family names
     * (French and Arabic in UTF-16, Japanese in Shift_JIS and Chinese in GBK : decoding them needs these charsets).
     */
    public static List<Name> showcaseSansNames() {
        return List.of(
                Name.windows(FontFiles.EN_US, FontFiles.FAMILY, SHOWCASE_SANS),
                Name.windows(FontFiles.EN_US, FontFiles.SUBFAMILY, "Regular"),
                Name.windows(FontFiles.EN_US, FontFiles.FULL_NAME, SHOWCASE_SANS + " Light"),
                Name.windows(FontFiles.EN_US, FontFiles.POSTSCRIPT_NAME, "ShowcaseSans-Light"),
                Name.mac(FontFiles.FAMILY, SHOWCASE_SANS),
                Name.mac(FontFiles.FULL_NAME, SHOWCASE_SANS + " Light"),
                Name.mac(FontFiles.POSTSCRIPT_NAME, "ShowcaseSans-Light"),
                Name.windows(FontFiles.FR_FR, FontFiles.FAMILY, "Vitrine Sans"),
                Name.windows(FontFiles.FR_FR, FontFiles.FULL_NAME, "Vitrine Sans Maigre"),
                Name.windows(FontFiles.AR_SA, FontFiles.FAMILY, "عرض سانس"),
                Name.windows(FontFiles.WIN_SHIFT_JIS, FontFiles.JA_JP, FontFiles.FAMILY, "ショーケース Sans"),
                Name.windows(FontFiles.WIN_PRC, FontFiles.ZH_CN, FontFiles.FAMILY, "展示 Sans"));
    }

    /**
     * Roboto Light renamed "Showcase Sans" (see {@link #showcaseSansNames()}).
     */
    public static synchronized byte[] showcaseSansBytes() {
        if (showcaseSansBytes == null) {
            showcaseSansBytes = FontFiles.rename(Edt.resourceBytes(ROBOTO), showcaseSansNames());
        }
        return showcaseSansBytes.clone();
    }

    /**
     * "Showcase Sans", created from {@link #showcaseSansBytes()} and registered with
     * {@code GraphicsEnvironment.registerFont} once per process : afterwards {@code new Font("Showcase Sans", ...)}
     * resolves to it.
     */
    public static synchronized Font showcaseSans() {
        if (showcaseSans == null) {
            try {
                showcaseSans = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(showcaseSansBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (FontFormatException e) {
                throw new IllegalStateException(e);
            }
            showcaseSansRegistered = GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(showcaseSans);
        }
        return showcaseSans;
    }

    /**
     * The result of the {@code registerFont} call of {@link #showcaseSans()} (the first call only : a second
     * registration of the same font name returns false).
     */
    public static synchronized boolean showcaseSansRegistered() {
        showcaseSans();
        return showcaseSansRegistered;
    }

    /**
     * A TrueType collection of two fonts ("Showcase Collection A" and "B") : Roboto Light with two name tables, every
     * other table shared.
     */
    public static synchronized byte[] collectionBytes() {
        if (collectionBytes == null) {
            byte[] roboto = Edt.resourceBytes(ROBOTO);
            collectionBytes = FontFiles.collection(List.of(
                    FontFiles.rename(roboto, collectionNames(COLLECTION_A, "ShowcaseCollection-A")),
                    FontFiles.rename(roboto, collectionNames(COLLECTION_B, "ShowcaseCollection-B"))));
        }
        return collectionBytes.clone();
    }

    private static List<Name> collectionNames(String family, String psName) {
        return List.of(
                Name.windows(FontFiles.EN_US, FontFiles.FAMILY, family),
                Name.windows(FontFiles.EN_US, FontFiles.SUBFAMILY, "Regular"),
                Name.windows(FontFiles.EN_US, FontFiles.FULL_NAME, family),
                Name.windows(FontFiles.EN_US, FontFiles.POSTSCRIPT_NAME, psName));
    }
}
