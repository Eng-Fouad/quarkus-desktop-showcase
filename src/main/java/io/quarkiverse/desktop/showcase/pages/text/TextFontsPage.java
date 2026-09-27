package io.quarkiverse.desktop.showcase.pages.text;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.text.TextSupport.Row;

/**
 * Fonts : the logical fonts, font files created from streams and files (TrueType, OpenType CFF, TrueType collection,
 * Type 1), {@code registerFont}, localized names, derived fonts ({@code deriveFont} with sizes, styles, transforms and
 * attributes, {@code Font.decode}, {@code Font.getFont(Map)}), metrics, and the text rendering modes (anti-aliasing
 * off/on/GASP, the four LCD sub-pixel orders, LCD contrast, fractional metrics, desktop hints).
 * <p>
 * Capture method C : everything is drawn into images. Bundled fonts (FreeType rasterizer) give platform independent
 * results, the logical fonts depend on the machine (compared on the same machine only). Native risks : font file
 * creation (temporary files, {@code java.home}), Type 1 fonts (quarkus-awt rejects {@code .pfa/.pfb}), charsets of the
 * localized names (Shift_JIS, GBK), the logical font configuration (fontconfig), Windows GDI LCD glyphs (JNI), the
 * desktop font hints ({@code WDesktopProperties} JNI callbacks).
 */
@Singleton
public class TextFontsPage implements FeaturePage {

    private static final String PANGRAM = "Sphinx of black quartz, judge my vow 0123 ΩЖ";
    private static final String RENDER_SAMPLE = "Quarkus AWT text 0123";
    private static final String HEBREW_SAMPLE = "שָׁלוֹם עוֹלָם";
    private static final String TYPE1_SAMPLE = "TYPE ONE 1 o";
    /** Font Awesome 5 : home, user, cog, heart, star, bolt, rocket, coffee, bell, camera, car, cloud, globe, music. */
    private static final String ICONS = "\uF015 \uF007 \uF013 \uF004 \uF005 \uF0E7 \uF135 \uF0F4 \uF0F3 \uF030 \uF1B9 "
            + "\uF0C2 \uF0AC \uF001";
    private static final String[] STYLE_NAMES = { "Plain", "Bold", "Italic", "Bold Italic" };

    @Override
    public String id() {
        return "text-fonts";
    }

    @Override
    public String title() {
        return "Fonts and rendering modes";
    }

    @Override
    public String category() {
        return Categories.TEXT;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() throws Exception {
        // the installed families before this page registers its fonts (Platforms caches the first result)
        Set<String> installed = Platforms.installedFamilies();
        Fonts fonts = loadFonts();

        List<Check> files = new ArrayList<>();
        List<Check> derived = new ArrayList<>();
        List<Check> metrics = new ArrayList<>();
        List<Check> rendering = new ArrayList<>();
        List<Check> environment = new ArrayList<>();
        fileChecks(fonts, files);
        derivedChecks(derived);
        metricsChecks(fonts, metrics);
        environmentChecks(installed, environment);

        BufferedImage logical = logicalFonts();
        BufferedImage fileSheet = fileFonts(fonts);
        BufferedImage derivedSheet = derivedFonts();
        BufferedImage renderSheet = renderingModes(rendering);

        return Ui.column(12,
                Ui.text("Logical fonts (5 families x 4 styles), font files created at run time from classpath resources "
                        + "and generated files, derived fonts, and the text rendering hints. Bundled fonts render the "
                        + "same everywhere, logical and system fonts depend on the machine.", 1000),
                Ui.title("Logical fonts"),
                Ui.image(logical),
                Ui.title("Font files"),
                Ui.image(fileSheet),
                ChecksView.table("Font files, names and errors", files),
                Ui.title("Derived fonts"),
                Ui.image(derivedSheet),
                ChecksView.table("Derivation and decoding", derived),
                ChecksView.table("Metrics (fractional metrics, anti-aliased render context)", metrics),
                Ui.title("Rendering modes (TYPE_INT_RGB destination, 3x zoom of the start of each sample)"),
                Ui.image(renderSheet),
                ChecksView.table("Rendering", rendering),
                ChecksView.table("Environment (machine dependent)", environment));
    }

    // ------------------------------------------------------------------------------------------------------- fonts

    /**
     * The fonts of the page, created for this display (the file based ones from temporary files).
     */
    private record Fonts(Font robotoStream, Font robotoFile, Font hebrewOtf, Font awesome, Font[] collectionFile,
            Font[] collectionStream, Font type1Pfb, Font type1Pfa, Font showcaseSans, Map<String, String> errors) {
    }

    private static Fonts loadFonts() {
        Map<String, String> errors = new HashMap<>();
        Font robotoStream = create("roboto stream", errors, () -> {
            try (InputStream in = Edt.resourceStream(TextAssets.ROBOTO)) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            }
        });
        Font robotoFile = create("roboto file", errors,
                () -> Font.createFont(Font.TRUETYPE_FONT, Edt.resourceToTempFile(TextAssets.ROBOTO).toFile()));
        Font hebrew = create("hebrew file", errors,
                () -> Font.createFont(Font.TRUETYPE_FONT, Edt.resourceToTempFile(TextAssets.HEBREW).toFile()));
        Font awesome = create("awesome", errors, () -> TextAssets.font(TextAssets.AWESOME));
        Font[] collectionFile = createAll("collection file", errors, () -> {
            Path file = Edt.tempDir().resolve("showcase-collection.ttc");
            Files.write(file, TextAssets.collectionBytes());
            return Font.createFonts(file.toFile());
        });
        Font[] collectionStream = createAll("collection stream", errors,
                () -> Font.createFonts(new ByteArrayInputStream(TextAssets.collectionBytes())));
        Font pfb = create("type1 pfb", errors,
                () -> Font.createFont(Font.TYPE1_FONT, new ByteArrayInputStream(FontFiles.Type1.pfb())));
        Font pfa = create("type1 pfa", errors, () -> {
            Path file = Edt.tempDir().resolve("showcase-type1.pfa");
            Files.write(file, FontFiles.Type1.pfa());
            return Font.createFont(Font.TYPE1_FONT, file.toFile());
        });
        Font sans = create("showcase sans", errors, TextAssets::showcaseSans);
        return new Fonts(robotoStream, robotoFile, hebrew, awesome, collectionFile, collectionStream, pfb, pfa, sans,
                errors);
    }

    private interface FontSupplier<T> {
        T get() throws Exception;
    }

    private static Font create(String key, Map<String, String> errors, FontSupplier<Font> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            errors.put(key, Checks.describe(t));
            return null;
        }
    }

    private static Font[] createAll(String key, Map<String, String> errors, FontSupplier<Font[]> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            errors.put(key, Checks.describe(t));
            return null;
        }
    }

    private static String names(Font font) {
        return font.getFamily(Locale.ROOT) + " / " + font.getFontName(Locale.ROOT) + " / " + font.getPSName() + ", "
                + font.getNumGlyphs() + " glyphs";
    }

    private static Check fontCheck(String name, String expected, Font font, Map<String, String> errors, String key) {
        if (font == null) {
            return Check.fail(name, errors.getOrDefault(key, "not created"));
        }
        return Checks.expect(name, expected, () -> names(font));
    }

    private static void fileChecks(Fonts f, List<Check> checks) {
        Map<String, String> e = f.errors();
        checks.add(fontCheck("createFont(TRUETYPE_FONT, InputStream) .ttf", "Roboto / Roboto Light / Roboto-Light, 1052 glyphs",
                f.robotoStream(), e, "roboto stream"));
        checks.add(fontCheck("createFont(TRUETYPE_FONT, File) .ttf", "Roboto / Roboto Light / Roboto-Light, 1052 glyphs",
                f.robotoFile(), e, "roboto file"));
        checks.add(fontCheck("createFont(TRUETYPE_FONT, File) .otf (CFF outlines)",
                "Noto Sans Hebrew / Noto Sans Hebrew Regular / NotoSansHebrew-Regular, 151 glyphs", f.hebrewOtf(), e,
                "hebrew file"));
        checks.add(Checks.expect("sfnt version of the .otf / .ttf files", "OTTO / 0x00010000", () -> {
            int otf = FontFiles.sfntVersion(Edt.resourceBytes(TextAssets.HEBREW));
            int ttf = FontFiles.sfntVersion(Edt.resourceBytes(TextAssets.ROBOTO));
            return new String(new byte[] { (byte) (otf >> 24), (byte) (otf >> 16), (byte) (otf >> 8), (byte) otf },
                    java.nio.charset.StandardCharsets.ISO_8859_1) + " / " + String.format("0x%08X", ttf);
        }));
        checks.add(fontCheck("icon font (private use area)",
                "Font Awesome 5 Free Solid / Font Awesome 5 Free Solid / FontAwesome5Free-Solid, 1004 glyphs",
                f.awesome(), e, "awesome"));
        checks.add(f.awesome() == null ? Check.fail("icon font canDisplayUpTo(icons) / canDisplay('A')", "no font")
                : Checks.expect("icon font canDisplayUpTo(icons) / canDisplay('A')", "-1 / false",
                        () -> f.awesome().canDisplayUpTo(ICONS.replace(" ", "")) + " / " + f.awesome().canDisplay('A')));
        checks.add(collectionCheck("createFonts(File) .ttc", f.collectionFile(), e, "collection file"));
        checks.add(collectionCheck("createFonts(InputStream) .ttc", f.collectionStream(), e, "collection stream"));
        checks.add(Checks.expect("createFont(TRUETYPE_FONT, File) .ttc : first font", TextAssets.COLLECTION_A, () -> {
            Path file = Edt.tempDir().resolve("showcase-collection-first.ttc");
            Files.write(file, TextAssets.collectionBytes());
            return Font.createFont(Font.TRUETYPE_FONT, file.toFile()).getFontName(Locale.ROOT);
        }));
        checks.add(Checks.expect(".ttc shares the tables of its fonts", true,
                () -> TextAssets.collectionBytes().length < Edt.resourceBytes(TextAssets.ROBOTO).length * 3 / 2));
        String type1 = FontFiles.Type1.FAMILY_NAME + " / " + FontFiles.Type1.FULL_NAME + " / " + FontFiles.Type1.FONT_NAME
                + ", 10 glyphs";
        checks.add(fontCheck("createFont(TYPE1_FONT, InputStream) generated .pfb", type1, f.type1Pfb(), e, "type1 pfb"));
        checks.add(fontCheck("createFont(TYPE1_FONT, File) generated .pfa", type1, f.type1Pfa(), e, "type1 pfa"));
        checks.add(f.type1Pfb() == null ? Check.fail("Type 1 canDisplayUpTo(\"" + TYPE1_SAMPLE + "\") / canDisplay('A')",
                "no font")
                : Checks.expect("Type 1 canDisplayUpTo(\"" + TYPE1_SAMPLE + "\") / canDisplay('A')", "-1 / false",
                        () -> f.type1Pfb().canDisplayUpTo(TYPE1_SAMPLE) + " / " + f.type1Pfb().canDisplay('A')));
        checks.add(fontCheck("renamed .ttf (generated name table)",
                "Showcase Sans / Showcase Sans Light / ShowcaseSans-Light, 1052 glyphs", f.showcaseSans(), e,
                "showcase sans"));
        checks.add(Checks.expect("registerFont(Showcase Sans) (first call of the process)", true,
                TextAssets::showcaseSansRegistered));
        checks.add(Checks.expect("registered family listed by getAvailableFontFamilyNames()", true,
                () -> Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                        .contains(TextAssets.SHOWCASE_SANS)));
        checks.add(Checks.expect("new Font(\"Showcase Sans\", PLAIN, 20) resolves to the registered font",
                "Showcase Sans Light / ShowcaseSans-Light",
                () -> {
                    Font byName = new Font(TextAssets.SHOWCASE_SANS, Font.PLAIN, 20);
                    return byName.getFontName(Locale.ROOT) + " / " + byName.getPSName();
                }));
        checks.add(Checks.expect("localized family names (en, fr, ar, ja Shift_JIS, zh GBK, de)",
                "Showcase Sans | Vitrine Sans | عرض سانس | ショーケース Sans | 展示 Sans | Showcase Sans", () -> {
                    Font font = TextAssets.showcaseSans();
                    return String.join(" | ", font.getFamily(Locale.US), font.getFamily(Locale.FRANCE),
                            font.getFamily(Locale.forLanguageTag("ar-SA")), font.getFamily(Locale.JAPAN),
                            font.getFamily(Locale.CHINA), font.getFamily(Locale.GERMANY));
                }));
        checks.add(Checks.expect("localized full names (en, fr)", "Showcase Sans Light | Vitrine Sans Maigre",
                () -> TextAssets.showcaseSans().getFontName(Locale.US) + " | "
                        + TextAssets.showcaseSans().getFontName(Locale.FRANCE)));
        checks.add(Checks.expect("createFont(TRUETYPE_FONT, PNG bytes)", "java.awt.FontFormatException",
                () -> expectFailure(() -> Font.createFont(Font.TRUETYPE_FONT,
                        new ByteArrayInputStream(new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0 })))));
        checks.add(Checks.expect("createFont(TYPE1_FONT, .ttf bytes)", "java.awt.FontFormatException",
                () -> expectFailure(() -> Font.createFont(Font.TYPE1_FONT,
                        new ByteArrayInputStream(Edt.resourceBytes(TextAssets.HEBREW))))));
        checks.add(Checks.expect("createFont(42, stream)", "java.lang.IllegalArgumentException: font format not recognized",
                () -> expectFailure(() -> Font.createFont(42, new ByteArrayInputStream(new byte[16])))));
        checks.add(Checks.expect("createFont(TRUETYPE_FONT, missing file)", "java.io.IOException",
                () -> expectFailure(() -> Font.createFont(Font.TRUETYPE_FONT,
                        new File(Edt.tempDir().toFile(), "missing.ttf"))).replaceAll(":.*", "")));
    }

    private static Check collectionCheck(String name, Font[] fonts, Map<String, String> errors, String key) {
        if (fonts == null) {
            return Check.fail(name, errors.getOrDefault(key, "not created"));
        }
        return Checks.expect(name, "2 fonts : " + TextAssets.COLLECTION_A + ", " + TextAssets.COLLECTION_B + " ; 1052 glyphs",
                () -> fonts.length + " fonts : " + Arrays.stream(fonts).map(f -> f.getFontName(Locale.ROOT))
                        .collect(Collectors.joining(", ")) + " ; " + fonts[1].getNumGlyphs() + " glyphs");
    }

    private interface Failing {
        Object run() throws Exception;
    }

    /**
     * The exception thrown by {@code action} (class name, and message for runtime exceptions), or "no exception".
     */
    private static String expectFailure(Failing action) {
        try {
            action.run();
            return "no exception";
        } catch (RuntimeException e) {
            return e.getClass().getName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        } catch (FontFormatException | IOException e) {
            return e.getClass().getName();
        } catch (Exception e) {
            return e.getClass().getName();
        }
    }

    // -------------------------------------------------------------------------------------------- derived fonts

    private static void derivedChecks(List<Check> checks) {
        checks.add(Checks.expect("logical families : getFamily()", "Dialog DialogInput Serif SansSerif Monospaced",
                () -> Platforms.LOGICAL_FAMILIES.stream().map(f -> new Font(f, Font.PLAIN, 12).getFamily(Locale.ROOT))
                        .collect(Collectors.joining(" "))));
        // Windows, Linux : a logical font is a CompositeFont named <family>.<style>. macOS : CFontManager.setupLogicalFonts
        // clones the 4 styles of the real family (Serif : Times) with new CFont(realFont, "Serif"), whose full name is
        // the logical family name for every style
        checks.add(Checks.expect("logical font names (Serif, 4 styles)",
                Platforms.isMac() ? "Serif Serif Serif Serif" : "Serif.plain Serif.bold Serif.italic Serif.bolditalic",
                () -> Arrays.stream(new int[] { Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC })
                        .mapToObj(s -> new Font(Font.SERIF, s, 12).getFontName(Locale.ROOT))
                        .collect(Collectors.joining(" "))));
        checks.add(Checks.expect("Font.decode", "Serif 3 18 | Monospaced 0 14 | SansSerif 1 12 | DialogInput 2 15 | "
                + "Dialog 0 12 | Dialog 0 20",
                () -> String.join(" | ",
                        decoded("Serif-BOLDITALIC-18"), decoded("Monospaced 14"), decoded("SansSerif-bold"),
                        decoded("DialogInput-italic-15"), decoded(null), decoded("No Such Family-20"))));
        checks.add(Checks.expect("Font.decode(\"No Such Family-20\").getName()", "No Such Family",
                () -> Font.decode("No Such Family-20").getName()));
        checks.add(Checks.expect("Font.getFont(Map) : family, style, size", "Serif 3 20.5", () -> {
            Map<TextAttribute, Object> map = new HashMap<>();
            map.put(TextAttribute.FAMILY, Font.SERIF);
            map.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
            map.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
            map.put(TextAttribute.SIZE, 20.5f);
            Font font = Font.getFont(map);
            return font.getFamily(Locale.ROOT) + " " + font.getStyle() + " " + font.getSize2D();
        }));
        checks.add(Checks.expect("deriveFont(9.5f) : getSize2D / getSize", "9.5 / 10",
                () -> TextAssets.roboto(9.5f).getSize2D() + " / " + TextAssets.roboto(9.5f).getSize()));
        checks.add(Checks.expect("deriveFont(BOLD | ITALIC) of Roboto Light : style, font name", "3, Roboto Light", () -> {
            Font font = TextAssets.roboto(12).deriveFont(Font.BOLD | Font.ITALIC);
            return font.getStyle() + ", " + font.getFontName(Locale.ROOT);
        }));
        checks.add(Checks.expect("deriveFont(AffineTransform) : isTransformed, transform, italic angle",
                "true, [1.000 0.000 -0.300 1.000], 0.300", () -> {
                    Font font = TextAssets.roboto(20).deriveFont(AffineTransform.getShearInstance(-0.3, 0));
                    double[] m = new double[4];
                    font.getTransform().getMatrix(m);
                    return font.isTransformed() + ", [" + Arrays.stream(m).mapToObj(Checks::num)
                            .collect(Collectors.joining(" ")) + "], " + Checks.num(font.getItalicAngle());
                }));
        checks.add(Checks.expect("deriveFont(Map) : hasLayoutAttributes (KERNING, LIGATURES) / (WEIGHT)", "true / false",
                () -> TextAssets.roboto(12).deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON,
                        TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON)).hasLayoutAttributes() + " / "
                        + TextAssets.roboto(12).deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD))
                                .hasLayoutAttributes()));
        checks.add(Checks.expect("getAttributes() of a derived font : WEIGHT, WIDTH, TRACKING", "2.0, 1.5, 0.04", () -> {
            Font font = TextAssets.roboto(12).deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD,
                    TextAttribute.WIDTH, TextAttribute.WIDTH_EXTENDED, TextAttribute.TRACKING, TextAttribute.TRACKING_LOOSE));
            Map<TextAttribute, ?> attributes = font.getAttributes();
            return attributes.get(TextAttribute.WEIGHT) + ", " + attributes.get(TextAttribute.WIDTH) + ", "
                    + attributes.get(TextAttribute.TRACKING);
        }));
        checks.add(Checks.expect("Font.getAvailableAttributes() count", 22,
                () -> TextAssets.roboto(12).getAvailableAttributes().length));
        checks.add(Checks.expect("Font.textRequiresLayout (Latin / Hebrew / Devanagari / Thai)", "false / true / true / true",
                () -> String.join(" / ", String.valueOf(requiresLayout("Latin")), String.valueOf(requiresLayout("שלום")),
                        String.valueOf(requiresLayout("नमस्ते")), String.valueOf(requiresLayout("สวัสดี")))));
    }

    private static boolean requiresLayout(String s) {
        char[] chars = s.toCharArray();
        return Font.textRequiresLayout(chars, 0, chars.length);
    }

    private static String decoded(String spec) {
        Font font = Font.decode(spec);
        return font.getFamily(Locale.ROOT) + " " + font.getStyle() + " " + font.getSize();
    }

    // ----------------------------------------------------------------------------------------------------- metrics

    private static void metricsChecks(Fonts fonts, List<Check> checks) {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        checks.add(Checks.expect("Roboto Light 20 : ascent, descent, leading, height", "18.55, 4.88, 0.00, 23.44",
                () -> lineMetrics(TextAssets.roboto(20), frc)));
        checks.add(Checks.expect("Roboto Light 20 : underline, strikethrough (offset / thickness)",
                "1.46 / 0.98, -5.00 / 1.00", () -> {
                    LineMetrics lm = TextAssets.roboto(20).getLineMetrics("Ag", frc);
                    return num(lm.getUnderlineOffset()) + " / " + num(lm.getUnderlineThickness()) + ", "
                            + num(lm.getStrikethroughOffset()) + " / " + num(lm.getStrikethroughThickness());
                }));
        checks.add(Checks.expect("Roboto Light 20 : getStringBounds(pangram)", "0.00,-18.55 409.79x23.44",
                () -> Checks.bounds(TextAssets.roboto(20).getStringBounds(PANGRAM, frc))));
        checks.add(Checks.expect("Roboto Light 20 : getMaxCharBounds", "0.00,-18.55 23.00x23.44",
                () -> Checks.bounds(TextAssets.roboto(20).getMaxCharBounds(frc))));
        checks.add(Checks.expect("Roboto Light : baseline, missing glyph code, italic angle", "0, 0, 0.000", () -> {
            Font font = TextAssets.roboto(20);
            return font.getBaselineFor('A') + ", " + font.getMissingGlyphCode() + ", " + Checks.num(font.getItalicAngle());
        }));
        checks.add(fonts.hebrewOtf() == null ? Check.fail("Noto Sans Hebrew (CFF) 20 : metrics, width", "no font")
                : Checks.expect("Noto Sans Hebrew (CFF) 20 : metrics, width", "21.36, 5.84, 0.00, 27.20 ; 92.14", () -> {
                    Font font = fonts.hebrewOtf().deriveFont(20f);
                    return lineMetrics(font, frc) + " ; " + num(font.getStringBounds(HEBREW_SAMPLE, frc).getWidth());
                }));
        checks.add(fonts.type1Pfb() == null ? Check.fail("Type 1 20 : width of \"TYPE ONE\" (design advances)", "no font")
                : Checks.expect("Type 1 20 : width of \"TYPE ONE\" (design advances)", "89.60",
                        () -> num(fonts.type1Pfb().deriveFont(20f).getStringBounds("TYPE ONE", frc).getWidth())));
        checks.add(fonts.type1Pfb() == null ? Check.fail("Type 1 20 : glyph outline bounds of \"O\"", "no font")
                : Checks.expect("Type 1 20 : glyph outline bounds of \"O\"", "1.20,-14.00 10.59x14.00",
                        () -> Checks.bounds(fonts.type1Pfb().deriveFont(20f).createGlyphVector(frc, "O").getOutline()
                                .getBounds2D())));
        checks.add(Checks.expect("fractional metrics ON / OFF : width of \"" + RENDER_SAMPLE + "\" (Roboto 15)",
                "155.66 / 153.00", () -> num(TextAssets.roboto(15).getStringBounds(RENDER_SAMPLE, frc).getWidth())
                        + " / " + num(TextAssets.roboto(15).getStringBounds(RENDER_SAMPLE,
                                new FontRenderContext(null, true, false)).getWidth())));
        checks.add(Checks.info("Toolkit.getFontMetrics(Roboto 20) : height, stringWidth, charWidth('W')", () -> {
            @SuppressWarnings("deprecation")
            FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(TextAssets.roboto(20));
            return fm.getHeight() + ", " + fm.stringWidth(PANGRAM) + ", " + fm.charWidth('W');
        }));
        for (String family : List.of(Font.DIALOG, Font.SERIF, Font.MONOSPACED)) {
            checks.add(Checks.info(family + " 14 (logical) : ascent, descent, leading, height ; width",
                    () -> lineMetrics(new Font(family, Font.PLAIN, 14), frc) + " ; "
                            + num(new Font(family, Font.PLAIN, 14).getStringBounds(PANGRAM, frc).getWidth())));
        }
    }

    private static String lineMetrics(Font font, FontRenderContext frc) {
        LineMetrics lm = font.getLineMetrics("Ag", frc);
        return num(lm.getAscent()) + ", " + num(lm.getDescent()) + ", " + num(lm.getLeading()) + ", "
                + num(lm.getHeight());
    }

    private static String num(double v) {
        return Checks.num(v, 2);
    }

    // ------------------------------------------------------------------------------------------------- environment

    private static void environmentChecks(Set<String> installed, List<Check> checks) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        checks.add(Check.info("installed families (Locale.ROOT)", installed.size()));
        checks.add(Checks.expect("logical families listed", true,
                () -> installed.containsAll(Platforms.LOGICAL_FAMILIES)));
        checks.add(Checks.info("getAllFonts().length", () -> ge.getAllFonts().length));
        checks.add(Checks.info("getAvailableFontFamilyNames(Locale.JAPAN).length",
                () -> ge.getAvailableFontFamilyNames(Locale.JAPAN).length));
        checks.add(Checks.info("first families", () -> installed.stream().sorted().limit(12)
                .collect(Collectors.joining(", "))));
        String japanese = Platforms.Families.japanese();
        checks.add(Checks.info("localized family of " + japanese + " (ja / en)",
                () -> new Font(japanese, Font.PLAIN, 12).getFamily(Locale.JAPAN) + " / "
                        + new Font(japanese, Font.PLAIN, 12).getFamily(Locale.US)));
        checks.add(Checks.info("logical fonts : PostScript names", () -> Platforms.LOGICAL_FAMILIES.stream()
                .map(f -> new Font(f, Font.BOLD, 12).getPSName()).collect(Collectors.joining(", "))));
        checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("Dialog canDisplayUpTo(Latin Greek Cyrillic, CJK, "
                + "Hebrew, Arabic, Thai, Devanagari)", "-1 -1 -1 -1 0 0",
                () -> Arrays.stream(new String[] {
                        "AaΩжß€", "中文日本語한국어", "שלום", "مرحبا", "สวัสดี", "नमस्ते" })
                        .map(s -> String.valueOf(new Font(Font.DIALOG, Font.PLAIN, 12).canDisplayUpTo(s)))
                        .collect(Collectors.joining(" ")))));
    }

    // ---------------------------------------------------------------------------------------------------- drawings

    private static BufferedImage logicalFonts() {
        int columns = 4;
        int cellWidth = TextSupport.WIDTH / columns;
        int rowHeight = 28;
        List<String> families = Platforms.LOGICAL_FAMILIES;
        return Snapshots.offscreen(TextSupport.WIDTH, families.size() * rowHeight, g -> {
            TextSupport.sampleHints(g);
            g.setColor(new Color(TextSupport.INK));
            for (int row = 0; row < families.size(); row++) {
                for (int style = 0; style < columns; style++) {
                    Font font = new Font(families.get(row), style, 16);
                    g.setFont(font);
                    g.drawString(families.get(row) + " " + STYLE_NAMES[style] + " Ag", style * cellWidth + 2,
                            row * rowHeight + 20);
                }
            }
        });
    }

    private static BufferedImage fileFonts(Fonts f) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("createFont(TRUETYPE_FONT, InputStream) : Roboto Light .ttf", 34,
                g -> sample(g, f.robotoStream(), 22, PANGRAM)));
        rows.add(new Row("createFont(TRUETYPE_FONT, File) : Roboto Light .ttf", 34,
                g -> sample(g, f.robotoFile(), 22, "The quick brown fox jumps over the lazy dog")));
        rows.add(new Row("OpenType CFF .otf (File) : Noto Sans Hebrew", 38, g -> sample(g, f.hebrewOtf(), 24, HEBREW_SAMPLE)));
        rows.add(new Row("Icon font (private use area) : Font Awesome 5 Free Solid", 36,
                g -> sample(g, f.awesome(), 22, ICONS)));
        rows.add(new Row("createFonts(File) : TrueType collection .ttc, font 0 and 1", 34, g -> {
            if (f.collectionFile() != null && f.collectionFile().length == 2) {
                sample(g, f.collectionFile()[0], 20, TextAssets.COLLECTION_A);
                Graphics2D right = (Graphics2D) g.create(360, 0, 380, 34);
                sample(right, f.collectionFile()[1], 20, TextAssets.COLLECTION_B);
                right.dispose();
            } else {
                sample(g, null, 20, "");
            }
        }));
        rows.add(new Row("createFont(TYPE1_FONT) : generated .pfb (28 px) and .pfa (20 px)", 40, g -> {
            sample(g, f.type1Pfb(), 28, TYPE1_SAMPLE);
            Graphics2D right = (Graphics2D) g.create(360, 0, 380, 40);
            sample(right, f.type1Pfa(), 20, TYPE1_SAMPLE);
            right.dispose();
        }));
        rows.add(new Row("registerFont, then new Font(\"Showcase Sans\", PLAIN, 22)", 34,
                g -> sample(g, f.showcaseSans() == null ? null : new Font(TextAssets.SHOWCASE_SANS, Font.PLAIN, 1), 22,
                        "Showcase Sans : a registered font, by name")));
        String sans = Platforms.Families.sans();
        rows.add(new Row("System font : " + sans, 34, g -> sample(g, new Font(sans, Font.PLAIN, 1), 20, PANGRAM)));
        return TextSupport.sheet(260, rows);
    }

    private static void sample(Graphics2D g, Font font, float size, String text) {
        TextSupport.sampleHints(g);
        if (font == null) {
            g.setColor(new Color(Ui.ERROR_COLOR));
            g.setFont(TextSupport.labelFont());
            g.drawString("font not created (see the checks)", 4, 20);
            return;
        }
        g.setColor(new Color(TextSupport.INK));
        g.setFont(font.deriveFont(size));
        g.drawString(text, 4, size + 4);
    }

    private static BufferedImage derivedFonts() {
        Font roboto = TextAssets.roboto(1);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("deriveFont(float) : 8 10 12 14 18 24 30 36", 44, g -> {
            TextSupport.sampleHints(g);
            g.setColor(new Color(TextSupport.INK));
            float x = 4;
            for (float size : new float[] { 8, 10, 12, 14, 18, 24, 30, 36 }) {
                Font font = roboto.deriveFont(size);
                g.setFont(font);
                g.drawString("Ag" + (int) size, x, 38);
                x += (float) font.getStringBounds("Ag" + (int) size, g.getFontRenderContext()).getWidth() + 14;
            }
        }));
        rows.add(new Row("deriveFont(int style) : algorithmic bold and oblique of Roboto Light", 34, g -> {
            TextSupport.sampleHints(g);
            g.setColor(new Color(TextSupport.INK));
            for (int style = 0; style < 4; style++) {
                g.setFont(roboto.deriveFont(style, 20f));
                g.drawString(STYLE_NAMES[style], 4 + style * 150, 24);
            }
        }));
        rows.add(new Row("deriveFont(AffineTransform) : rotated 15°, sheared, 2x wide, 1.6x tall, mirrored", 70, g -> {
            TextSupport.sampleHints(g);
            g.setColor(new Color(TextSupport.INK));
            Font base = roboto.deriveFont(20f);
            g.setFont(base.deriveFont(AffineTransform.getRotateInstance(Math.toRadians(-15))));
            g.drawString("Rotated", 4, 50);
            g.setFont(base.deriveFont(AffineTransform.getShearInstance(-0.3, 0)));
            g.drawString("Sheared", 110, 50);
            g.setFont(base.deriveFont(AffineTransform.getScaleInstance(2, 1)));
            g.drawString("Wide", 220, 50);
            g.setFont(base.deriveFont(AffineTransform.getScaleInstance(1, 1.6)));
            g.drawString("Tall", 380, 50);
            g.setFont(base.deriveFont(AffineTransform.getScaleInstance(-1, 1)));
            g.drawString("Mirrored", 540, 50);
        }));
        rows.add(new Row("deriveFont(Map) : WEIGHT, WIDTH, POSTURE, TRACKING, SIZE", 34, g -> {
            TextSupport.sampleHints(g);
            g.setColor(new Color(TextSupport.INK));
            Font base = roboto.deriveFont(18f);
            List<Object[]> variants = List.of(
                    new Object[] { "Bold", Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD) },
                    new Object[] { "Condensed", Map.of(TextAttribute.WIDTH, TextAttribute.WIDTH_CONDENSED) },
                    new Object[] { "Extended", Map.of(TextAttribute.WIDTH, TextAttribute.WIDTH_EXTENDED) },
                    new Object[] { "Oblique", Map.of(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE) },
                    new Object[] { "Loose", Map.of(TextAttribute.TRACKING, TextAttribute.TRACKING_LOOSE) },
                    new Object[] { "Tight", Map.of(TextAttribute.TRACKING, TextAttribute.TRACKING_TIGHT) },
                    new Object[] { "Size 24", Map.of(TextAttribute.SIZE, 24f) });
            float x = 4;
            for (Object[] variant : variants) {
                @SuppressWarnings("unchecked")
                Font font = base.deriveFont((Map<TextAttribute, ?>) variant[1]);
                g.setFont(font);
                g.drawString((String) variant[0], x, 25);
                x += (float) font.getStringBounds((String) variant[0], g.getFontRenderContext()).getWidth() + 18;
            }
        }));
        rows.add(new Row("Font.decode(...)", 34, g -> {
            TextSupport.sampleHints(g);
            g.setColor(new Color(TextSupport.INK));
            float x = 4;
            for (String spec : List.of("Serif-BOLDITALIC-18", "Monospaced 14", "SansSerif-bold", "DialogInput-italic-15")) {
                Font font = Font.decode(spec);
                g.setFont(font);
                g.drawString(spec, x, 24);
                x += (float) font.getStringBounds(spec, g.getFontRenderContext()).getWidth() + 18;
            }
        }));
        return TextSupport.sheet(260, rows);
    }

    // ------------------------------------------------------------------------------------------- rendering modes

    private record Mode(String label, Map<RenderingHints.Key, Object> hints, boolean argb) {
    }

    private static List<Mode> modes() {
        List<Mode> modes = new ArrayList<>();
        modes.add(mode("TEXT_ANTIALIAS_OFF", RenderingHints.VALUE_TEXT_ANTIALIAS_OFF));
        modes.add(mode("TEXT_ANTIALIAS_ON", RenderingHints.VALUE_TEXT_ANTIALIAS_ON));
        modes.add(mode("TEXT_ANTIALIAS_GASP", RenderingHints.VALUE_TEXT_ANTIALIAS_GASP));
        modes.add(mode("TEXT_ANTIALIAS_DEFAULT", RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT));
        modes.add(mode("LCD_HRGB", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB));
        modes.add(mode("LCD_HBGR", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR));
        modes.add(mode("LCD_VRGB", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB));
        modes.add(mode("LCD_VBGR", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR));
        for (int contrast : new int[] { 100, 180, 250 }) {
            Map<RenderingHints.Key, Object> hints = new HashMap<>();
            hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            hints.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, contrast);
            modes.add(new Mode("LCD_HRGB, TEXT_LCD_CONTRAST " + contrast, hints, false));
        }
        Map<RenderingHints.Key, Object> fm = new HashMap<>();
        fm.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        fm.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        modes.add(new Mode("ANTIALIAS_ON, FRACTIONALMETRICS_ON", fm, false));
        Map<RenderingHints.Key, Object> lcdArgb = new HashMap<>();
        lcdArgb.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        modes.add(new Mode("LCD_HRGB on a TYPE_INT_ARGB image (grayscale fallback)", lcdArgb, true));
        modes.add(new Mode("desktop hints (awt.font.desktophints)", desktopHints(), false));
        return modes;
    }

    private static Mode mode(String label, Object antialiasing) {
        Map<RenderingHints.Key, Object> hints = new HashMap<>();
        hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, antialiasing);
        hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        return new Mode(label, hints, false);
    }

    private static Map<RenderingHints.Key, Object> desktopHints() {
        Map<RenderingHints.Key, Object> hints = new HashMap<>();
        if (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof RenderingHints.Key key) {
                    hints.put(key, v);
                }
            });
        }
        return hints;
    }

    private static String describeHints(Map<RenderingHints.Key, Object> hints) {
        Map<String, String> sorted = new TreeMap<>();
        hints.forEach((k, v) -> sorted.put(String.valueOf(k), String.valueOf(v)));
        return sorted.isEmpty() ? "none" : sorted.toString();
    }

    /**
     * The sample of {@code font} rendered with {@code mode} into an opaque (or translucent) white image.
     */
    private static BufferedImage render(Mode mode, Font font, int width) {
        BufferedImage image = new BufferedImage(width, 22,
                mode.argb() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, 22);
            g.setRenderingHints(mode.hints());
            g.setColor(Color.BLACK);
            g.setFont(font);
            g.drawString(RENDER_SAMPLE, 2, 16);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage renderingModes(List<Check> checks) {
        Font roboto = TextAssets.roboto(15);
        Font dialog = new Font(Font.DIALOG, Font.PLAIN, 13);
        List<Mode> modes = modes();
        List<Row> rows = new ArrayList<>();
        Map<String, BufferedImage> robotoImages = new HashMap<>();
        for (Mode mode : modes) {
            BufferedImage r = render(mode, roboto, 190);
            BufferedImage d = render(mode, dialog, 170);
            robotoImages.put(mode.label(), r);
            rows.add(new Row(mode.label(), 50, g -> {
                g.drawImage(r, 0, 4, null);
                zoom(g, r, 196, 2);
                g.drawImage(d, 330, 4, null);
                zoom(g, d, 506, 2);
                TextSupport.caption(g, "Roboto", 0, 44);
                TextSupport.caption(g, "Dialog", 330, 44);
            }));
            String name = mode.label().startsWith("desktop") ? "desktop hints" : mode.label();
            String expected = expectedKind(mode.label());
            if (expected != null) {
                checks.add(Checks.expect("pixels of Roboto : " + name, expected, () -> TextSupport.pixelKind(r)));
            } else {
                checks.add(Checks.info("pixels of Roboto : " + name, () -> TextSupport.pixelKind(r)));
            }
            checks.add(Checks.info("pixels of Dialog : " + name, () -> TextSupport.pixelKind(d) + ", "
                    + TextSupport.distinctColors(d) + " colors, " + Checks.sha256(d)));
        }
        checks.add(Checks.expect("LCD contrast 100 and 250 render differently (Roboto)", true, () -> !Checks.sha256(
                robotoImages.get("LCD_HRGB, TEXT_LCD_CONTRAST 100")).equals(
                        Checks.sha256(
                                robotoImages.get("LCD_HRGB, TEXT_LCD_CONTRAST 250")))));
        checks.add(Checks.expect("HRGB and HBGR render differently (Roboto)", true, () -> !Checks.sha256(
                robotoImages.get("LCD_HRGB")).equals(Checks.sha256(robotoImages.get("LCD_HBGR")))));
        checks.add(Checks.info("desktop hints", () -> describeHints(desktopHints())));
        checks.add(Checks.info("Roboto image hashes (OFF / ON / LCD_HRGB)", () -> Checks.sha256(robotoImages.get(
                "TEXT_ANTIALIAS_OFF")) + " / " + Checks.sha256(robotoImages.get("TEXT_ANTIALIAS_ON")) + " / "
                + Checks.sha256(robotoImages.get("LCD_HRGB"))));
        return TextSupport.sheet(250, rows);
    }

    private static String expectedKind(String label) {
        // Roboto's gasp table asks for no smoothing at 15 px. macOS : text is never rendered without anti-aliasing,
        // for every font (Roboto : FreeType), when FontUtilities.isMacOSX14 (macOS 10.14 and later). The strike :
        // SunGraphics2D.checkFontInfo resolves DEFAULT (no ANTIALIAS_ON hint) and GASP (Roboto at 15 px) to OFF, then
        // turns OFF into ON (FontStrikeDesc.getAAHintIntVal does the same for a FontRenderContext). The pipe : the
        // SurfaceData static initializer sets solidTextRenderer = aaTextRenderer, the pipe that getTextPipe returns for
        // OFF and DEFAULT (without it, the gray strike would still be drawn by the black and white pipe)
        if (label.equals("TEXT_ANTIALIAS_OFF") || label.equals("TEXT_ANTIALIAS_DEFAULT")
                || label.equals("TEXT_ANTIALIAS_GASP")) {
            return Platforms.isMac() ? "gray" : "bw";
        }
        if (label.startsWith("LCD_") && !label.contains("ARGB")) {
            return "color";
        }
        if (label.contains("ARGB") || label.equals("TEXT_ANTIALIAS_ON") || label.startsWith("ANTIALIAS_ON")) {
            return "gray";
        }
        return null;
    }

    /**
     * Draws the first 40 x 14 pixels of the sample 3 times larger (nearest neighbor : the sub-pixel colors show).
     */
    private static void zoom(Graphics2D g, BufferedImage image, int x, int y) {
        Graphics2D z = (Graphics2D) g.create();
        try {
            z.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            z.drawImage(image, x, y, x + 120, y + 42, 2, 4, 42, 18, null);
            z.setColor(new Color(TextSupport.RULE_COLOR));
            z.drawRect(x, y, 120, 42);
        } finally {
            z.dispose();
        }
    }
}
