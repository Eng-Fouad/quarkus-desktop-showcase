package io.quarkiverse.desktop.showcase.pages.text;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.NumericShaper;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.text.TextSupport.Row;

/**
 * International text : Arabic (contextual shaping, lam-alef ligature, bidi, numeric shaping), Hebrew with points,
 * Devanagari (conjuncts, vowel sign reordering), Thai (stacked marks), CJK through the logical font fallback and
 * system fonts, emoji (system font, sequences), mixed run directions with {@link TextLayout}, {@link java.text.Bidi},
 * {@link NumericShaper} and grapheme / word / line boundaries.
 * <p>
 * Capture method C. The left column uses the bundled fonts (Droid Arabic Kufi, Noto Sans Hebrew / Devanagari / Thai :
 * the checks on their glyphs are platform independent), the right column system fonts and the logical Dialog font
 * (machine dependent, compared on the same machine). Native risks : HarfBuzz shaping through FFM upcalls and
 * downcalls (every complex script), the composite (logical) font slots of the font configuration, charsets, the
 * locale data of the break iterators ({@code quarkus.locales}).
 */
@Singleton
public class TextInternationalPage implements FeaturePage {

    static final String ARABIC = "مرحبا بالعالم";
    static final String ARABIC_2 = "القرآن الكريم ١٢٣";
    static final String LAM_ALEF = "لا";
    static final String MIXED = "النص العربي يحتوي على Quarkus 3.40 و AWT مع الرقم 2026.";
    static final String HEBREW = "בְּרֵאשִׁית בָּרָא אֱלֹהִים";
    static final String HEBREW_MIXED = "שלום Java 25 עולם";
    static final String DEVANAGARI = "नमस्ते दुनिया क्षत्रिय श्री कि";
    static final String THAI = "สวัสดีชาวโลก กิ่ง ป่า น้ำ";
    static final String CJK = "中文 日本語 한국어";
    static final String EMOJI = "😀🎉🚀❤\uFE0F👍🌍🍕";
    static final String EMOJI_SEQUENCES = "👍🏽 🇯🇵 👨\u200D👩\u200D👧";
    static final String DIGITS = "Digits 123, عربي 456, English 789";

    @Override
    public String id() {
        return "text-international";
    }

    @Override
    public String title() {
        return "International text";
    }

    @Override
    public String category() {
        return Categories.TEXT;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Component build() {
        List<Check> shaping = new ArrayList<>();
        List<Check> bidi = new ArrayList<>();
        List<Check> system = new ArrayList<>();
        BufferedImage scripts = scripts();
        BufferedImage directions = directions();
        shapingChecks(shaping);
        bidiChecks(bidi);
        systemChecks(system);
        return Ui.column(12,
                Ui.text("Complex scripts shaped by HarfBuzz (through the foreign function API in JDK 25), bidirectional "
                        + "text and numeric shaping. Left: bundled fonts (platform independent). Right: system fonts and "
                        + "the logical Dialog font (machine dependent).", 1000),
                Ui.title("Scripts"),
                Ui.image(scripts),
                ChecksView.table("Shaping (bundled fonts)", shaping),
                Ui.title("Directions and numeric shaping (TextLayout)"),
                Ui.image(directions),
                ChecksView.table("Bidi, NumericShaper, boundaries", bidi),
                ChecksView.table("System and logical fonts (machine dependent)", system));
    }

    static FontRenderContext frc() {
        return new FontRenderContext(null, true, true);
    }

    // ---------------------------------------------------------------------------------------------------- drawings

    /**
     * Draws {@code text} with {@code font} in a {@code width} wide column : right aligned when the text starts with a
     * right-to-left character (TextLayout run direction from the text).
     */
    private static void line(Graphics2D g, Font font, String text, float x, float width, float baseline) {
        TextSupport.sampleHints(g);
        g.setColor(new Color(TextSupport.INK));
        TextLayout layout = new TextLayout(text, font, g.getFontRenderContext());
        float left = layout.isLeftToRight() ? x : x + width - layout.getAdvance();
        layout.draw(g, left, baseline);
    }

    private static Row script(String label, int height, Font bundled, String bundledCaption, Font system,
            String systemCaption, String... lines) {
        return new Row(label, height, g -> {
            float lineHeight = (height - 16f) / lines.length;
            for (int i = 0; i < lines.length; i++) {
                float baseline = (i + 0.78f) * lineHeight + 2;
                if (bundled != null) {
                    line(g, bundled, lines[i], 4, 350, baseline);
                }
                line(g, system, lines[i], 380, 350, baseline);
            }
            if (bundled != null) {
                TextSupport.caption(g, bundledCaption, 4, height - 4);
            }
            TextSupport.caption(g, systemCaption, 380, height - 4);
        });
    }

    private static BufferedImage scripts() {
        Font kufi = TextAssets.font(TextAssets.KUFI, 24);
        Font hebrew = TextAssets.font(TextAssets.HEBREW, 24);
        Font devanagari = TextAssets.font(TextAssets.DEVANAGARI, 22);
        Font thai = TextAssets.font(TextAssets.THAI, 24);
        Font dialog = new Font(Font.DIALOG, Font.PLAIN, 22);
        String arabicFamily = Platforms.Families.arabic();
        String hebrewFamily = Platforms.Families.hebrew();
        String devanagariFamily = Platforms.Families.devanagari();
        String thaiFamily = Platforms.Families.thai();
        String emojiFamily = Platforms.Families.emoji();
        List<Row> rows = new ArrayList<>();
        rows.add(script("Arabic : contextual forms, lam-alef, Arabic-Indic digits", 86, kufi, "Droid Arabic Kufi (bundled)",
                new Font(arabicFamily, Font.PLAIN, 22), arabicFamily, ARABIC, ARABIC_2));
        rows.add(script("Arabic : isolated letters (ZWNJ) and joined", 52, kufi, "Droid Arabic Kufi",
                dialog, "Dialog (logical)", "م\u200Cر\u200Cح\u200Cب\u200Cا  مرحبا  " + LAM_ALEF));
        rows.add(script("Hebrew with points (niqqud)", 54, hebrew, "Noto Sans Hebrew .otf (bundled)",
                new Font(hebrewFamily, Font.PLAIN, 22), hebrewFamily, HEBREW));
        rows.add(script("Devanagari : conjuncts, reordered vowel sign i", 56, devanagari, "Noto Sans Devanagari (bundled)",
                new Font(devanagariFamily, Font.PLAIN, 22), devanagariFamily, DEVANAGARI));
        rows.add(script("Thai : stacked tone marks and vowels", 56, thai, "Noto Sans Thai (bundled)",
                new Font(thaiFamily, Font.PLAIN, 22), thaiFamily, THAI));
        rows.add(new Row("CJK : logical font fallback and system fonts", 56, g -> {
            line(g, dialog, CJK, 4, 350, 30);
            TextSupport.caption(g, "Dialog (logical font, fallback slots)", 4, 52);
            String zh = Platforms.Families.chinese();
            String ja = Platforms.Families.japanese();
            String ko = Platforms.Families.korean();
            line(g, new Font(zh, Font.PLAIN, 22), "中文", 380, 100, 30);
            line(g, new Font(ja, Font.PLAIN, 22), "日本語", 480, 120, 30);
            line(g, new Font(ko, Font.PLAIN, 22), "한국어", 610, 120, 30);
            TextSupport.caption(g, zh + " / " + ja + " / " + ko, 380, 52);
        }));
        rows.add(new Row("Emoji : system font and logical font, sequences (skin tone, flag, ZWJ family)", 56, g -> {
            line(g, new Font(emojiFamily, Font.PLAIN, 22), EMOJI + " " + EMOJI_SEQUENCES, 4, 350, 30);
            TextSupport.caption(g, emojiFamily, 4, 52);
            line(g, dialog, EMOJI + " " + EMOJI_SEQUENCES, 380, 350, 30);
            TextSupport.caption(g, "Dialog (logical)", 380, 52);
        }));
        return TextSupport.sheet(240, rows);
    }

    private static TextLayout layout(String text, Map<TextAttribute, Object> attributes, FontRenderContext frc) {
        return new TextLayout(attributed(text, attributes).getIterator(), frc);
    }

    /**
     * {@code text} with the Kufi font for Arabic letters, Noto Sans Hebrew for Hebrew letters, Showcase Sans (Roboto)
     * for the rest, plus {@code attributes}.
     */
    static AttributedString attributed(String text, Map<TextAttribute, Object> attributes) {
        TextAssets.showcaseSans();
        Map<TextAttribute, Object> base = new HashMap<>();
        base.put(TextAttribute.FAMILY, TextAssets.SHOWCASE_SANS);
        base.put(TextAttribute.SIZE, 20f);
        base.put(TextAttribute.FOREGROUND, new Color(TextSupport.INK));
        base.putAll(attributes);
        AttributedString s = new AttributedString(text, base);
        Font kufi = TextAssets.font(TextAssets.KUFI, 20);
        Font hebrew = TextAssets.font(TextAssets.HEBREW, 20);
        // digits replaced by the numeric shaper are Arabic digits : the Kufi font has them, Roboto does not
        String shaped = attributes.get(TextAttribute.NUMERIC_SHAPING) instanceof NumericShaper shaper
                ? shape(shaper, text)
                : text;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.ARABIC || shaped.charAt(i) != text.charAt(i)) {
                s.addAttribute(TextAttribute.FONT, kufi, i, i + 1);
            } else if (script == Character.UnicodeScript.HEBREW) {
                s.addAttribute(TextAttribute.FONT, hebrew, i, i + 1);
            }
        }
        return s;
    }

    private static BufferedImage directions() {
        FontRenderContext frc = frc();
        List<Row> rows = new ArrayList<>();
        rows.add(directionRow("RUN_DIRECTION default (from the first strong character)", MIXED, Map.of(), frc));
        rows.add(directionRow("RUN_DIRECTION_LTR", MIXED, Map.of(TextAttribute.RUN_DIRECTION,
                TextAttribute.RUN_DIRECTION_LTR), frc));
        rows.add(directionRow("RUN_DIRECTION_RTL", MIXED, Map.of(TextAttribute.RUN_DIRECTION,
                TextAttribute.RUN_DIRECTION_RTL), frc));
        rows.add(directionRow("Hebrew and Latin, default direction", HEBREW_MIXED, Map.of(), frc));
        rows.add(directionRow("NUMERIC_SHAPING : none", DIGITS, Map.of(), frc));
        rows.add(directionRow("NUMERIC_SHAPING : getContextualShaper(ARABIC)", DIGITS, Map.of(TextAttribute.NUMERIC_SHAPING,
                NumericShaper.getContextualShaper(NumericShaper.ARABIC)), frc));
        rows.add(directionRow("NUMERIC_SHAPING : getContextualShaper(EnumSet.of(EASTERN_ARABIC), ...)",
                DIGITS, Map.of(TextAttribute.NUMERIC_SHAPING, NumericShaper.getContextualShaper(
                        EnumSet.of(NumericShaper.Range.EASTERN_ARABIC), NumericShaper.Range.EASTERN_ARABIC)),
                frc));
        rows.add(directionRow("NUMERIC_SHAPING : getShaper(ARABIC) (non contextual)", DIGITS,
                Map.of(TextAttribute.NUMERIC_SHAPING, NumericShaper.getShaper(NumericShaper.ARABIC)), frc));
        return TextSupport.sheet(300, rows);
    }

    private static Row directionRow(String label, String text, Map<TextAttribute, Object> attributes, FontRenderContext frc) {
        TextLayout layout = layout(text, attributes, frc);
        return new Row(label, 40, g -> {
            TextSupport.sampleHints(g);
            float x = layout.isLeftToRight() ? 6 : 660 - layout.getAdvance();
            layout.draw(g, x, 26);
            g.setColor(new Color(TextSupport.RULE_COLOR));
            g.drawLine(6, 32, 660, 32);
            TextSupport.caption(g, layout.isLeftToRight() ? "LTR" : "RTL", 670, 12);
        });
    }

    // ------------------------------------------------------------------------------------------------------ checks

    private static GlyphVector shaped(Font font, String text, int flags) {
        char[] chars = text.toCharArray();
        return font.layoutGlyphVector(frc(), chars, 0, chars.length, flags);
    }

    private static String codes(GlyphVector gv) {
        return Arrays.stream(gv.getGlyphCodes(0, gv.getNumGlyphs(), null)).mapToObj(String::valueOf)
                .collect(Collectors.joining(" "));
    }

    private static String indices(GlyphVector gv) {
        return Arrays.stream(gv.getGlyphCharIndices(0, gv.getNumGlyphs(), null)).mapToObj(String::valueOf)
                .collect(Collectors.joining(" "));
    }

    private static void shapingChecks(List<Check> checks) {
        Font kufi = TextAssets.font(TextAssets.KUFI, 24);
        Font hebrew = TextAssets.font(TextAssets.HEBREW, 24);
        Font devanagari = TextAssets.font(TextAssets.DEVANAGARI, 22);
        Font thai = TextAssets.font(TextAssets.THAI, 24);
        int rtl = Font.LAYOUT_RIGHT_TO_LEFT;
        int ltr = Font.LAYOUT_LEFT_TO_RIGHT;
        checks.add(Checks.expect("bundled fonts canDisplayUpTo (Arabic, Hebrew, Devanagari, Thai)", "-1 -1 -1 -1",
                () -> kufi.canDisplayUpTo(ARABIC + ARABIC_2) + " " + hebrew.canDisplayUpTo(HEBREW) + " "
                        + devanagari.canDisplayUpTo(DEVANAGARI) + " " + thai.canDisplayUpTo(THAI)));
        checks.add(Checks.expect("Arabic \"مرحبا\" : glyph codes (right to left)", "6 135 8 13 39",
                () -> codes(shaped(kufi, "مرحبا", rtl))));
        checks.add(Checks.expect("Arabic : isolated MEEM glyph differs from its initial form", true,
                () -> shaped(kufi, "م", rtl).getGlyphCode(0) != shaped(kufi, "مر", rtl).getGlyphCode(1)));
        checks.add(Checks.expect("Arabic lam-alef ligature : characters, glyphs", "2, 1",
                () -> LAM_ALEF.length() + ", " + shaped(kufi, LAM_ALEF, rtl).getNumGlyphs()));
        checks.add(Checks.expect("Arabic : advance of the first sample (TextLayout)", "128.39",
                () -> TextAttributesLayoutPage.num(new TextLayout(ARABIC, kufi, frc()).getAdvance())));
        checks.add(Checks.expect("Hebrew with points : characters, glyphs, layout flags",
                "27, 27, FLAG_HAS_POSITION_ADJUSTMENTS FLAG_COMPLEX_GLYPHS", () -> {
                    GlyphVector gv = shaped(hebrew, HEBREW, rtl);
                    return HEBREW.length() + ", " + gv.getNumGlyphs() + ", "
                            + TextAttributesLayoutPage.flags(gv.getLayoutFlags());
                }));
        checks.add(Checks.expect("Devanagari KA + VIRAMA + SSA (conjunct) : glyphs", 1,
                () -> shaped(devanagari, "क्ष", ltr).getNumGlyphs()));
        checks.add(Checks.expect("Devanagari KA + VOWEL SIGN I : glyphs, clusters, vowel sign glyph first", "2, 0 0, true",
                () -> {
                    GlyphVector gv = shaped(devanagari, "कि", ltr);
                    int ka = shaped(devanagari, "क", ltr).getGlyphCode(0);
                    return gv.getNumGlyphs() + ", " + indices(gv) + ", " + (gv.getGlyphCode(0) != ka
                            && gv.getGlyphCode(1) == ka);
                }));
        checks.add(Checks.expect("Devanagari sample : characters, glyphs, advance", "30, 22, 207.64", () -> {
            GlyphVector gv = shaped(devanagari, DEVANAGARI, ltr);
            return DEVANAGARI.length() + ", " + gv.getNumGlyphs() + ", "
                    + TextAttributesLayoutPage.num(new TextLayout(DEVANAGARI, devanagari, frc()).getAdvance());
        }));
        checks.add(Checks.expect("Thai NO NU + MAI THO + SARA AM : glyphs, char indices, positions x",
                "4, 0 1 1 1, 0.00 14.71 14.02 14.71", () -> {
                    GlyphVector gv = shaped(thai, "น้ำ", ltr);
                    float[] positions = gv.getGlyphPositions(0, gv.getNumGlyphs(), null);
                    return gv.getNumGlyphs() + ", " + indices(gv) + ", " + IntStream.range(0, gv.getNumGlyphs())
                            .mapToObj(i -> TextAttributesLayoutPage.num(positions[2 * i])).collect(Collectors.joining(" "));
                }));
        checks.add(Checks.expect("Thai sample : advance, visual bounds", "219.02, 1.17,-24.22 215.90x24.45", () -> {
            TextLayout layout = new TextLayout(THAI, thai, frc());
            return TextAttributesLayoutPage.num(layout.getAdvance()) + ", " + Checks.bounds(layout.getBounds());
        }));
        checks.add(Checks.expect("Arabic TextLayout : isLeftToRight, hitTestChar(10, 0), caret x of offset 0",
                "false, 12L, 128.39", () -> {
                    TextLayout layout = new TextLayout(ARABIC, kufi, frc());
                    TextHitInfo hit = layout.hitTestChar(10, 0);
                    return layout.isLeftToRight() + ", " + hit.getCharIndex() + (hit.isLeadingEdge() ? "L" : "T") + ", "
                            + TextAttributesLayoutPage.num(layout.getCaretShapes(0)[0].getBounds2D().getCenterX());
                }));
    }

    private static String runs(Bidi bidi) {
        return IntStream.range(0, bidi.getRunCount()).mapToObj(i -> bidi.getRunStart(i) + "-" + bidi.getRunLimit(i) + "@"
                + bidi.getRunLevel(i)).collect(Collectors.joining(" "));
    }

    private static String shape(NumericShaper shaper, String text) {
        char[] chars = text.toCharArray();
        shaper.shape(chars, 0, chars.length);
        return new String(chars);
    }

    private static void bidiChecks(List<Check> checks) {
        checks.add(Checks.expect("Bidi(mixed, DEFAULT_LEFT_TO_RIGHT) : base level, mixed, runs",
                "1, true, 0-22@1 22-34@2 34-37@1 37-40@2 40-50@1 50-54@2 54-55@1", () -> {
                    Bidi bidi = new Bidi(MIXED, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
                    return bidi.getBaseLevel() + ", " + bidi.isMixed() + ", " + runs(bidi);
                }));
        checks.add(Checks.expect("Bidi(mixed, LEFT_TO_RIGHT) : runs", "0-21@1 21-35@0 35-36@1 36-41@0 41-50@1 50-54@2 54-55@0",
                () -> runs(new Bidi(MIXED,
                        Bidi.DIRECTION_LEFT_TO_RIGHT))));
        checks.add(Checks.expect("Bidi(Hebrew mixed) : isRightToLeft, levels", "false, 11111222222211111", () -> {
            Bidi bidi = new Bidi(HEBREW_MIXED, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            return bidi.isRightToLeft() + ", " + IntStream.range(0, HEBREW_MIXED.length())
                    .mapToObj(i -> String.valueOf(bidi.getLevelAt(i))).collect(Collectors.joining());
        }));
        checks.add(Checks.expect("Bidi.requiresBidi (Latin / Hebrew / Arabic)", "false / true / true", () -> {
            return Bidi.requiresBidi("Latin".toCharArray(), 0, 5) + " / " + Bidi.requiresBidi(HEBREW.toCharArray(), 0,
                    HEBREW.length()) + " / " + Bidi.requiresBidi(ARABIC.toCharArray(), 0, ARABIC.length());
        }));
        checks.add(Checks.expect("Bidi.reorderVisually of the runs of the Hebrew mixed line", "עולם | Java 25 | שלום", () -> {
            Bidi bidi = new Bidi(HEBREW_MIXED, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            byte[] levels = new byte[bidi.getRunCount()];
            String[] objects = new String[bidi.getRunCount()];
            for (int i = 0; i < levels.length; i++) {
                levels[i] = (byte) bidi.getRunLevel(i);
                objects[i] = HEBREW_MIXED.substring(bidi.getRunStart(i), bidi.getRunLimit(i)).trim();
            }
            Bidi.reorderVisually(levels, 0, objects, 0, objects.length);
            return String.join(" | ", objects);
        }));
        checks.add(Checks.expect("Bidi.createLineBidi(0, 12) of the mixed text : runs", "0-12@1", () -> runs(new Bidi(MIXED,
                Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).createLineBidi(0, 12))));
        checks.add(Checks.expect("Bidi(AttributedCharacterIterator) with RUN_DIRECTION_RTL and BIDI_EMBEDDING",
                "1, 0-3@2 3-4@1 4-7@3 7-8@1 8-11@2", () -> {
                    AttributedString s = new AttributedString("abc def ghi");
                    s.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
                    s.addAttribute(TextAttribute.BIDI_EMBEDDING, -3, 4, 7);
                    Bidi bidi = new Bidi(s.getIterator());
                    return bidi.getBaseLevel() + ", " + runs(bidi);
                }));
        checks.add(Checks.expect("NumericShaper.getContextualShaper(ARABIC).shape", "Digits 123, عربي ٤٥٦, English 789",
                () -> shape(NumericShaper.getContextualShaper(NumericShaper.ARABIC), DIGITS)));
        checks.add(Checks.expect("NumericShaper contextual EASTERN_ARABIC (Range)", "Digits 123, عربي ۴۵۶, English 789",
                () -> shape(NumericShaper.getContextualShaper(EnumSet.of(NumericShaper.Range.EASTERN_ARABIC),
                        NumericShaper.Range.EASTERN_ARABIC), DIGITS)));
        checks.add(Checks.expect("NumericShaper.getShaper (DEVANAGARI, THAI, TIBETAN, Range.MONGOLIAN) of \"0123\" "
                + "(code points)", "0966-0969 0E50-0E53 0F20-0F23 1810-1813",
                () -> String.join(" ",
                        range(shape(NumericShaper.getShaper(NumericShaper.DEVANAGARI), "0123")),
                        range(shape(NumericShaper.getShaper(NumericShaper.THAI), "0123")),
                        range(shape(NumericShaper.getShaper(NumericShaper.TIBETAN), "0123")),
                        range(shape(NumericShaper.getShaper(NumericShaper.Range.MONGOLIAN), "0123")))));
        checks.add(Checks.expect("NumericShaper : isContextual, getRanges, getRangeSet, equals",
                "true, 10, [ARABIC, DEVANAGARI], true", () -> {
                    NumericShaper s = NumericShaper.getContextualShaper(NumericShaper.ARABIC | NumericShaper.DEVANAGARI);
                    return s.isContextual() + ", " + s.getRanges() + ", " + s.getRangeSet() + ", "
                            + s.equals(NumericShaper.getContextualShaper(NumericShaper.ARABIC | NumericShaper.DEVANAGARI));
                }));
        checks.add(Checks.expect("TextLayout advance without / with NUMERIC_SHAPING (contextual ARABIC)", "288.76 / 287.05",
                () -> {
                    TextLayout plain = layout(DIGITS, Map.of(), frc());
                    TextLayout shaped = layout(DIGITS, Map.of(TextAttribute.NUMERIC_SHAPING,
                            NumericShaper.getContextualShaper(NumericShaper.ARABIC)), frc());
                    return TextAttributesLayoutPage.num(plain.getAdvance()) + " / " + TextAttributesLayoutPage.num(shaped
                            .getAdvance());
                }));
        checks.add(Checks.expect("ComponentOrientation.getOrientation (ar, he, fa, en, ja) isLeftToRight",
                "false false false true true", () -> Arrays.stream(new String[] { "ar", "he", "fa", "en", "ja" })
                        .map(l -> String.valueOf(ComponentOrientation.getOrientation(Locale.forLanguageTag(l))
                                .isLeftToRight()))
                        .collect(Collectors.joining(" "))));
        checks.add(Checks.expect("BreakIterator.getCharacterInstance : grapheme clusters of the emoji sequences", "5",
                () -> count(BreakIterator.getCharacterInstance(Locale.ROOT), EMOJI_SEQUENCES)));
        checks.add(Checks.expect("BreakIterator.getCharacterInstance : grapheme clusters of the Devanagari sample", "15",
                () -> count(BreakIterator.getCharacterInstance(Locale.ROOT), DEVANAGARI)));
        checks.add(Checks.expect("BreakIterator.getWordInstance : boundaries of the mixed text",
                "0 4 5 11 12 17 18 21 22 29 30 34 35 36 37 40 41 43 44 49 50 54 55",
                () -> boundaries(BreakIterator.getWordInstance(Locale.ROOT), MIXED)));
        checks.add(Checks.info("BreakIterator.getLineInstance(th) : boundaries of the Thai sample (dictionary, locale "
                + "data)", () -> boundaries(BreakIterator.getLineInstance(Locale.forLanguageTag("th")), THAI)));
    }

    /**
     * {@code first-last} code points (hexadecimal) of a 4 character string.
     */
    private static String range(String s) {
        return String.format(Locale.ROOT, "%04X-%04X", (int) s.charAt(0), (int) s.charAt(s.length() - 1));
    }

    private static int count(BreakIterator it, String text) {
        it.setText(text);
        int n = 0;
        while (it.next() != BreakIterator.DONE) {
            n++;
        }
        return n;
    }

    private static String boundaries(BreakIterator it, String text) {
        it.setText(text);
        List<String> list = new ArrayList<>();
        for (int b = it.first(); b != BreakIterator.DONE; b = it.next()) {
            list.add(String.valueOf(b));
        }
        return String.join(" ", list);
    }

    private static void systemChecks(List<Check> checks) {
        Font dialog = new Font(Font.DIALOG, Font.PLAIN, 22);
        Map<String, String> samples = new java.util.LinkedHashMap<>();
        samples.put("Arabic", ARABIC);
        samples.put("Hebrew", HEBREW);
        samples.put("Devanagari", DEVANAGARI);
        samples.put("Thai", THAI);
        samples.put("CJK", CJK);
        samples.put("emoji", EMOJI);
        checks.add(Checks.info("Dialog canDisplayUpTo (Arabic, Hebrew, Devanagari, Thai, CJK, emoji)",
                () -> samples.values().stream().map(s -> String.valueOf(dialog.canDisplayUpTo(s)))
                        .collect(Collectors.joining(" "))));
        checks.add(Checks.info("Dialog advances (Arabic, Hebrew, Devanagari, Thai, CJK, emoji)",
                () -> samples.values().stream().map(s -> TextAttributesLayoutPage.num(new TextLayout(s, dialog, frc())
                        .getAdvance())).collect(Collectors.joining(" "))));
        List<String[]> families = List.of(
                new String[] { Platforms.Families.arabic(), ARABIC, "Arabic" },
                new String[] { Platforms.Families.hebrew(), HEBREW, "Hebrew" },
                new String[] { Platforms.Families.devanagari(), DEVANAGARI, "Devanagari" },
                new String[] { Platforms.Families.thai(), THAI, "Thai" },
                new String[] { Platforms.Families.chinese(), "中文", "Chinese" },
                new String[] { Platforms.Families.japanese(), "日本語", "Japanese" },
                new String[] { Platforms.Families.korean(), "한국어", "Korean" },
                new String[] { Platforms.Families.emoji(), EMOJI, "emoji" });
        for (String[] family : families) {
            checks.add(Checks.info(family[2] + ", " + family[0] + " : canDisplayUpTo, glyphs, advance", () -> {
                Font font = new Font(family[0], Font.PLAIN, 22);
                TextLayout layout = new TextLayout(family[1], font, frc());
                return font.canDisplayUpTo(family[1]) + ", " + shaped(font, family[1], layout.isLeftToRight()
                        ? Font.LAYOUT_LEFT_TO_RIGHT
                        : Font.LAYOUT_RIGHT_TO_LEFT).getNumGlyphs() + ", "
                        + TextAttributesLayoutPage.num(layout.getAdvance());
            }));
        }
        checks.add(Checks.info("emoji font renders colored glyphs", () -> {
            BufferedImage image = Snapshots.offscreen(60, 40, g -> {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 60, 40);
                TextSupport.sampleHints(g);
                g.setColor(Color.BLACK);
                g.setFont(new Font(Platforms.Families.emoji(), Font.PLAIN, 28));
                g.drawString("😀", 4, 32);
            });
            return TextSupport.pixelKind(image);
        }));
        checks.add(Checks.info("Dialog glyph codes of CJK (composite font slots)", () -> {
            GlyphVector gv = shaped(dialog, "中日한", Font.LAYOUT_LEFT_TO_RIGHT);
            return codes(gv);
        }));
    }
}
