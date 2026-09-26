package io.quarkiverse.desktop.showcase.pages.text;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.GraphicAttribute;
import java.awt.font.ImageGraphicAttribute;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.ShapeGraphicAttribute;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.font.TextMeasurer;
import java.awt.font.TransformAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.im.InputMethodHighlight;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
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
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.text.TextSupport.Row;

/**
 * Text attributes and layout : every {@link TextAttribute} on {@link AttributedString}s (drawn with {@link TextLayout}
 * and {@code Graphics2D.drawString(AttributedCharacterIterator)}), {@link GlyphVector} (outlines, positions,
 * transforms, logical/visual bounds, metrics, right-to-left layout, ligature to character mapping),
 * {@link TextLayout} carets, highlights and hit testing on bidirectional text, and {@link LineBreakMeasurer} /
 * {@link TextMeasurer} paragraphs, ragged and justified.
 * <p>
 * Capture method C. The text uses the bundled Roboto Light, registered as "Showcase Sans" (so that the FAMILY, WEIGHT,
 * WIDTH and POSTURE attributes resolve through the font manager), and Noto Sans Hebrew, with fractional metrics : the
 * geometry checks do not depend on the platform. Native risks : HarfBuzz shaping through FFM (kerning, ligatures,
 * right-to-left runs), {@code Toolkit.mapInputMethodHighlight}, bidi.
 */
@Singleton
public class TextAttributesLayoutPage implements FeaturePage {

    private static final String BIDI = "Quarkus שלום עולם AWT";
    private static final String PARAGRAPH = "Quarkus Desktop brings AWT and Swing to GraalVM native executables. "
            + "This paragraph is broken into lines by a LineBreakMeasurer, which measures the styled text with a "
            + "TextMeasurer and asks a BreakIterator for the break opportunities. The left column is ragged, the right "
            + "column justified with getJustifiedLayout, except for its last line. Styles change inside lines: bold, "
            + "larger, colored, underlined, and an inline \uFFFC graphic.";

    @Override
    public String id() {
        return "text-attributes-layout";
    }

    @Override
    public String title() {
        return "Attributes and layout";
    }

    @Override
    public String category() {
        return Categories.TEXT;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Component build() {
        // Roboto Light registered as "Showcase Sans" : the FAMILY attribute resolves to it
        TextAssets.showcaseSans();
        List<Check> attributes = new ArrayList<>();
        List<Check> glyphs = new ArrayList<>();
        List<Check> layout = new ArrayList<>();
        List<Check> paragraph = new ArrayList<>();
        BufferedImage attributeSheet = attributeSheet(attributes);
        BufferedImage glyphImage = glyphVectors(glyphs);
        BufferedImage layoutImage = textLayout(layout);
        BufferedImage paragraphImage = paragraphs(paragraph);
        return Ui.column(12,
                Ui.text("Every TextAttribute, GlyphVector geometry, TextLayout carets / highlights / hit testing on a "
                        + "bidirectional line, and LineBreakMeasurer paragraphs. Fonts: Showcase Sans (the bundled Roboto "
                        + "Light, registered) and Noto Sans Hebrew; fractional metrics.", 1000),
                Ui.title("TextAttribute"),
                Ui.image(attributeSheet),
                ChecksView.table("Attributes", attributes),
                Ui.title("GlyphVector"),
                Ui.image(glyphImage),
                ChecksView.table("GlyphVector", glyphs),
                Ui.title("TextLayout : carets (strong black, weak red), logical selection 3..13 (blue), hit tests (dots)"),
                Ui.image(layoutImage),
                ChecksView.table("TextLayout", layout),
                Ui.title("LineBreakMeasurer : ragged and justified (JUSTIFICATION_FULL)"),
                Ui.image(paragraphImage),
                ChecksView.table("Paragraph", paragraph));
    }

    static FontRenderContext frc() {
        return new FontRenderContext(null, true, true);
    }

    private static Map<TextAttribute, Object> base(float size) {
        Map<TextAttribute, Object> map = new HashMap<>();
        map.put(TextAttribute.FAMILY, TextAssets.SHOWCASE_SANS);
        map.put(TextAttribute.SIZE, size);
        map.put(TextAttribute.FOREGROUND, new Color(TextSupport.INK));
        return map;
    }

    /**
     * Styled text : consecutive runs, each with its own attributes on top of {@link #base}.
     */
    private static AttributedString styled(float size, Object... runs) {
        StringBuilder text = new StringBuilder();
        List<int[]> ranges = new ArrayList<>();
        List<Map<TextAttribute, ?>> maps = new ArrayList<>();
        for (int i = 0; i < runs.length; i += 2) {
            int start = text.length();
            text.append((String) runs[i]);
            ranges.add(new int[] { start, text.length() });
            @SuppressWarnings("unchecked")
            Map<TextAttribute, ?> map = (Map<TextAttribute, ?>) runs[i + 1];
            maps.add(map);
        }
        AttributedString s = new AttributedString(text.toString(), base(size));
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.get(i)[1] > ranges.get(i)[0]) {
                s.addAttributes(maps.get(i), ranges.get(i)[0], ranges.get(i)[1]);
            }
        }
        return s;
    }

    private static Map<TextAttribute, Object> attrs(Object... keyValues) {
        Map<TextAttribute, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((TextAttribute) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static final Map<TextAttribute, Object> NONE = Map.of();

    // ------------------------------------------------------------------------------------------------- attributes

    private static Row layoutRow(String label, int height, AttributedString text) {
        return new Row(label, height, g -> {
            TextSupport.sampleHints(g);
            TextLayout layout = new TextLayout(text.getIterator(), g.getFontRenderContext());
            layout.draw(g, 6, 6 + layout.getAscent());
        });
    }

    private static BufferedImage attributeSheet(List<Check> checks) {
        FontRenderContext frc = frc();
        List<Row> rows = new ArrayList<>();
        float[] weights = { 0.5f, 0.75f, 0.875f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f };
        Object[] weightRuns = new Object[weights.length * 2];
        for (int i = 0; i < weights.length; i++) {
            weightRuns[2 * i] = "W" + weights[i] + " ";
            weightRuns[2 * i + 1] = attrs(TextAttribute.WEIGHT, weights[i]);
        }
        rows.add(layoutRow("WEIGHT : EXTRA_LIGHT (0.5) ... ULTRABOLD (2.75)", 34, styled(18, weightRuns)));
        rows.add(layoutRow("WIDTH : CONDENSED, SEMI_CONDENSED, REGULAR, SEMI_EXTENDED, EXTENDED", 34, styled(18,
                "Condensed ", attrs(TextAttribute.WIDTH, TextAttribute.WIDTH_CONDENSED),
                "Semi condensed ", attrs(TextAttribute.WIDTH, TextAttribute.WIDTH_SEMI_CONDENSED),
                "Regular ", attrs(TextAttribute.WIDTH, TextAttribute.WIDTH_REGULAR),
                "Semi extended ", attrs(TextAttribute.WIDTH, TextAttribute.WIDTH_SEMI_EXTENDED),
                "Extended", attrs(TextAttribute.WIDTH, TextAttribute.WIDTH_EXTENDED))));
        rows.add(layoutRow("POSTURE, SIZE, TRANSFORM, FAMILY (logical Serif), FONT", 40, styled(18,
                "Oblique ", attrs(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE),
                "Size 26 ", attrs(TextAttribute.SIZE, 26f),
                "Transform ", attrs(TextAttribute.TRANSFORM,
                        new TransformAttribute(AffineTransform.getRotateInstance(Math.toRadians(-10)))),
                "Serif ", attrs(TextAttribute.FAMILY, Font.SERIF),
                "FONT (Type 1) ", NONE,
                "TYPE ONE", attrs(TextAttribute.FONT, type1(18)))));
        rows.add(layoutRow("SUPERSCRIPT : SUPER, SUB, nested (x² H₂O e^(iπ))", 40, styled(20,
                "x", NONE, "2", attrs(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER),
                " + H", NONE, "2", attrs(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB),
                "O  e", NONE, "iπ", attrs(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER),
                "  a", NONE, "n", attrs(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB),
                "k", attrs(TextAttribute.SUPERSCRIPT, 2),
                "  super-super", attrs(TextAttribute.SUPERSCRIPT, 2))));
        rows.add(layoutRow("UNDERLINE_ON, STRIKETHROUGH_ON, INPUT_METHOD_UNDERLINE (one pixel, two pixel, dotted, gray, "
                + "dashed)", 34,
                styled(18,
                        "Underline", attrs(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON), " ", NONE,
                        "Strike", attrs(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON), " ", NONE,
                        "One", attrs(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_ONE_PIXEL), " ", NONE,
                        "Two", attrs(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_TWO_PIXEL), " ", NONE,
                        "Dotted", attrs(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_DOTTED), " ", NONE,
                        "Gray", attrs(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_GRAY), " ", NONE,
                        "Dashed", attrs(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_DASHED))));
        rows.add(layoutRow("FOREGROUND (Color, GradientPaint), BACKGROUND, SWAP_COLORS", 34, styled(18,
                "Colored ", attrs(TextAttribute.FOREGROUND, new Color(0xC62828)),
                "Gradient paint ", attrs(TextAttribute.FOREGROUND,
                        new GradientPaint(0, 0, new Color(0x1565C0), 120, 0, new Color(0x2E7D32))),
                "Background", attrs(TextAttribute.BACKGROUND, new Color(0xFFF59D)), " ", NONE,
                "Swapped colors", attrs(TextAttribute.BACKGROUND, new Color(0xE3F2FD), TextAttribute.SWAP_COLORS,
                        TextAttribute.SWAP_COLORS_ON))));
        rows.add(layoutRow("KERNING off / on (\"AVATAR Taw WAVE\")", 34, styled(22,
                "AVATAR Taw WAVE", NONE, "   ", NONE,
                "AVATAR Taw WAVE", attrs(TextAttribute.KERNING, TextAttribute.KERNING_ON))));
        rows.add(layoutRow("LIGATURES off / on (\"office fluffy\")", 34, styled(22,
                "office fluffy", NONE, "   ", NONE,
                "office fluffy", attrs(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON))));
        rows.add(layoutRow("TRACKING : TIGHT (-0.04), 0, LOOSE (0.04), 0.15", 34, styled(18,
                "Tight tracking ", attrs(TextAttribute.TRACKING, TextAttribute.TRACKING_TIGHT),
                "No tracking ", NONE,
                "Loose tracking ", attrs(TextAttribute.TRACKING, TextAttribute.TRACKING_LOOSE),
                "Very loose", attrs(TextAttribute.TRACKING, 0.15f))));
        rows.add(layoutRow("CHAR_REPLACEMENT : ShapeGraphicAttribute (fill, stroke; ROMAN, CENTER, HANGING, TOP, BOTTOM "
                + "alignment), ImageGraphicAttribute", 44,
                styled(20,
                        "Roman", NONE, "\uFFFC", attrs(TextAttribute.CHAR_REPLACEMENT, star(GraphicAttribute.ROMAN_BASELINE, true)),
                        " Center", NONE, "\uFFFC",
                        attrs(TextAttribute.CHAR_REPLACEMENT, star(GraphicAttribute.CENTER_BASELINE, false)),
                        " Hanging", NONE, "\uFFFC",
                        attrs(TextAttribute.CHAR_REPLACEMENT, star(GraphicAttribute.HANGING_BASELINE, true)),
                        " Top", NONE, "\uFFFC", attrs(TextAttribute.CHAR_REPLACEMENT, star(GraphicAttribute.TOP_ALIGNMENT, true)),
                        " Bottom", NONE, "\uFFFC",
                        attrs(TextAttribute.CHAR_REPLACEMENT, star(GraphicAttribute.BOTTOM_ALIGNMENT, false)),
                        " Image", NONE, "\uFFFC", attrs(TextAttribute.CHAR_REPLACEMENT, imageGraphic()))));
        rows.add(layoutRow("INPUT_METHOD_HIGHLIGHT (Toolkit.mapInputMethodHighlight) : unselected / selected raw, "
                + "unselected / selected converted", 34,
                styled(18,
                        "raw", attrs(TextAttribute.INPUT_METHOD_HIGHLIGHT, InputMethodHighlight.UNSELECTED_RAW_TEXT_HIGHLIGHT),
                        " ", NONE,
                        "RAW", attrs(TextAttribute.INPUT_METHOD_HIGHLIGHT, InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT),
                        " ", NONE,
                        "converted", attrs(TextAttribute.INPUT_METHOD_HIGHLIGHT,
                                InputMethodHighlight.UNSELECTED_CONVERTED_TEXT_HIGHLIGHT),
                        " ", NONE,
                        "CONVERTED", attrs(TextAttribute.INPUT_METHOD_HIGHLIGHT,
                                InputMethodHighlight.SELECTED_CONVERTED_TEXT_HIGHLIGHT))));
        rows.add(layoutRow("RUN_DIRECTION RTL + BIDI_EMBEDDING (-1 : embedded right-to-left Latin)", 34, bidiRow()));
        rows.add(new Row("Graphics2D.drawString(AttributedCharacterIterator) of the same styled text", 34, g -> {
            TextSupport.sampleHints(g);
            g.drawString(styled(18, "drawString ", NONE, "bold ", attrs(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD),
                    "underlined ", attrs(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON),
                    "colored", attrs(TextAttribute.FOREGROUND, new Color(0x6A1B9A))).getIterator(), 6, 24);
        }));
        rows.add(new Row("TextLayout.getOutline : stroked outline filled with a gradient", 54, g -> {
            TextSupport.sampleHints(g);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            TextLayout layout = new TextLayout("Outline", TextAssets.roboto(44).deriveFont(Font.BOLD), frc);
            Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(6, 44));
            g.setPaint(new GradientPaint(0, 0, new Color(0xFFB74D), 0, 50, new Color(0xE53935)));
            g.fill(outline);
            g.setColor(new Color(TextSupport.INK));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(outline);
        }));

        checks.add(Checks.expect("advance : KERNING off / on", "194.94 / 188.35", () -> {
            float off = new TextLayout("AVATAR Taw WAVE", font(22, NONE), frc).getAdvance();
            float on = new TextLayout("AVATAR Taw WAVE", font(22, attrs(TextAttribute.KERNING, TextAttribute.KERNING_ON)),
                    frc).getAdvance();
            return num(off) + " / " + num(on);
        }));
        checks.add(Checks.expect("glyphs of \"office fluffy\" : LIGATURES off / on", "13 / 12", () -> glyphCount(
                font(22, NONE), "office fluffy") + " / "
                + glyphCount(font(22, attrs(TextAttribute.LIGATURES,
                        TextAttribute.LIGATURES_ON)), "office fluffy")));
        checks.add(Checks.expect("advance : TRACKING TIGHT / 0 / LOOSE (\"Tracking\" 18)", "63.09 / 68.85 / 74.61",
                () -> Arrays.stream(new Object[] { TextAttribute.TRACKING_TIGHT, 0f, TextAttribute.TRACKING_LOOSE })
                        .map(t -> num(new TextLayout("Tracking", font(18, attrs(TextAttribute.TRACKING, t)), frc)
                                .getAdvance()))
                        .collect(Collectors.joining(" / "))));
        checks.add(Checks.expect("advance : WIDTH CONDENSED / REGULAR / EXTENDED (\"Width\" 18)", "34.44 / 45.92 / 68.88",
                () -> Arrays.stream(new Object[] { TextAttribute.WIDTH_CONDENSED, TextAttribute.WIDTH_REGULAR,
                        TextAttribute.WIDTH_EXTENDED })
                        .map(w -> num(new TextLayout("Width", font(18, attrs(TextAttribute.WIDTH, w)), frc).getAdvance()))
                        .collect(Collectors.joining(" / "))));
        checks.add(Checks.expect("font of FAMILY \"Showcase Sans\" + WEIGHT_BOLD : name, style", "Showcase Sans Light, 1",
                () -> {
                    Font f = font(18, attrs(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD));
                    return f.getFontName(Locale.ROOT) + ", " + f.getStyle();
                }));
        checks.add(Checks.expect("SUPERSCRIPT_SUPER / SUB : black box of \"2\" after \"x\" (20 px)",
                "10.47,-17.11 6.09x9.61 / 10.47,-2.11 6.09x9.61", () -> {
                    TextLayout sup = new TextLayout(styled(20, "x", NONE, "2",
                            attrs(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER)).getIterator(), frc);
                    TextLayout sub = new TextLayout(styled(20, "x", NONE, "2",
                            attrs(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB)).getIterator(), frc);
                    return Checks.bounds(sup.getBlackBoxBounds(1, 2).getBounds2D()) + " / "
                            + Checks.bounds(sub.getBlackBoxBounds(1, 2).getBounds2D());
                }));
        checks.add(Checks.expect("CHAR_REPLACEMENT shape : ascent, descent, advance (ROMAN / TOP)",
                "16.00, 0.00, 16.61 / 16.00, 0.00, 16.61", () -> {
                    GraphicAttribute roman = star(GraphicAttribute.ROMAN_BASELINE, true);
                    GraphicAttribute top = star(GraphicAttribute.TOP_ALIGNMENT, true);
                    return num(roman.getAscent()) + ", " + num(roman.getDescent()) + ", " + num(roman.getAdvance()) + " / "
                            + num(top.getAscent()) + ", " + num(top.getDescent()) + ", " + num(top.getAdvance());
                }));
        checks.add(Checks.expect("layout with CHAR_REPLACEMENT : characters, advance", "4, 57.58", () -> {
            TextLayout layout = new TextLayout(styled(20, "A", NONE, "\uFFFC", attrs(TextAttribute.CHAR_REPLACEMENT,
                    star(GraphicAttribute.ROMAN_BASELINE, true)), "B", NONE, "\uFFFC",
                    attrs(TextAttribute.CHAR_REPLACEMENT, imageGraphic())).getIterator(), frc);
            return layout.getCharacterCount() + ", " + num(layout.getAdvance());
        }));
        checks.add(Checks.info("Toolkit.mapInputMethodHighlight(SELECTED_CONVERTED_TEXT_HIGHLIGHT)", () -> {
            Map<TextAttribute, ?> map = java.awt.Toolkit.getDefaultToolkit()
                    .mapInputMethodHighlight(InputMethodHighlight.SELECTED_CONVERTED_TEXT_HIGHLIGHT);
            return map == null ? "null"
                    : map.entrySet().stream().map(e -> attributeName(e.getKey()) + "="
                            + describeValue(e.getValue())).sorted().collect(Collectors.joining(", "));
        }));
        checks.add(Checks.expect("TextAttribute.readResolve (serialization round trip)", "true", () -> {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(TextAttribute.KERNING);
            }
            try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                return String.valueOf(in.readObject() == TextAttribute.KERNING);
            }
        }));
        return TextSupport.sheet(270, rows);
    }

    private static String attributeName(Object key) {
        String s = String.valueOf(key);
        return s.substring(s.indexOf('(') + 1, s.length() - 1);
    }

    private static String describeValue(Object value) {
        if (value instanceof Color c) {
            return Checks.argb(c.getRGB());
        }
        return String.valueOf(value);
    }

    private static AttributedString bidiRow() {
        String text = "שלום ABC עולם 123";
        Map<TextAttribute, Object> map = base(20);
        map.put(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        AttributedString s = new AttributedString(text, map);
        s.addAttribute(TextAttribute.FONT, TextAssets.font(TextAssets.HEBREW, 20), 0, 4);
        s.addAttribute(TextAttribute.FONT, TextAssets.font(TextAssets.HEBREW, 20), 9, 13);
        s.addAttribute(TextAttribute.BIDI_EMBEDDING, -1, 5, 8);
        s.addAttribute(TextAttribute.FOREGROUND, new Color(0x1565C0), 5, 8);
        return s;
    }

    static Font font(float size, Map<TextAttribute, ?> extra) {
        Map<TextAttribute, Object> map = base(size);
        map.remove(TextAttribute.FOREGROUND);
        map.putAll(extra);
        return Font.getFont(map);
    }

    private static Font type1(float size) {
        try {
            return Font.createFont(Font.TYPE1_FONT, new java.io.ByteArrayInputStream(FontFiles.Type1.pfb())).deriveFont(size);
        } catch (Exception e) {
            // Type 1 fonts may be unsupported (see the text-fonts page) : the text is drawn with Dialog then
            return new Font(Font.DIALOG, Font.PLAIN, (int) size);
        }
    }

    private static int glyphCount(Font font, String text) {
        char[] chars = text.toCharArray();
        return font.layoutGlyphVector(frc(), chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT).getNumGlyphs();
    }

    /**
     * A 5 pointed star above the origin (the baseline for the baseline alignments).
     */
    private static ShapeGraphicAttribute star(int alignment, boolean fill) {
        Path2D.Float star = new Path2D.Float();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double r = i % 2 == 0 ? 8 : 3.5;
            double x = 9 + r * Math.cos(angle);
            double y = -8 + r * Math.sin(angle);
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        return new ShapeGraphicAttribute(star, alignment, fill ? ShapeGraphicAttribute.FILL : ShapeGraphicAttribute.STROKE);
    }

    private static ImageGraphicAttribute imageGraphic() {
        BufferedImage image = Snapshots.offscreen(16, 16, g -> {
            g.setColor(new Color(0x43A047));
            g.fillRect(0, 0, 16, 16);
            g.setColor(Color.WHITE);
            g.fillRect(4, 4, 8, 8);
        });
        return new ImageGraphicAttribute(image, GraphicAttribute.ROMAN_BASELINE, 0, 14);
    }

    // ------------------------------------------------------------------------------------------------ GlyphVector

    private static BufferedImage glyphVectors(List<Check> checks) {
        FontRenderContext frc = frc();
        Font font = TextAssets.roboto(56);
        GlyphVector gv = font.createGlyphVector(frc, "Glyphs");
        GlyphVector wave = font.deriveFont(40f).createGlyphVector(frc, "wave of glyphs");
        for (int i = 0; i < wave.getNumGlyphs(); i++) {
            Point2D p = wave.getGlyphPosition(i);
            wave.setGlyphPosition(i, new Point2D.Double(p.getX(), p.getY() + 8 * Math.sin(i * 0.9)));
            wave.setGlyphTransform(i, AffineTransform.getRotateInstance(Math.toRadians(i % 2 == 0 ? -12 : 12)));
        }
        Font hebrew = TextAssets.font(TextAssets.HEBREW, 36);
        char[] hebrewChars = "שלום".toCharArray();
        GlyphVector rtl = hebrew.layoutGlyphVector(frc, hebrewChars, 0, hebrewChars.length, Font.LAYOUT_RIGHT_TO_LEFT);
        Font ligatures = TextAssets.roboto(40).deriveFont(Map.of(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON));
        char[] office = "office".toCharArray();
        GlyphVector lig = ligatures.layoutGlyphVector(frc, office, 0, office.length, Font.LAYOUT_LEFT_TO_RIGHT);

        BufferedImage image = Snapshots.offscreen(TextSupport.WIDTH, 170, g -> {
            TextSupport.sampleHints(g);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            // outline, logical (blue) and visual (red) bounds per glyph, baseline
            float x = 10;
            float y = 70;
            g.setColor(new Color(0xB0BEC5));
            g.drawLine((int) x, (int) y, (int) (x + gv.getLogicalBounds().getWidth()), (int) y);
            g.setPaint(new GradientPaint(x, y - 50, new Color(0x4FC3F7), x, y + 10, new Color(0x1565C0)));
            g.fill(gv.getOutline(x, y));
            for (int i = 0; i < gv.getNumGlyphs(); i++) {
                g.setColor(new Color(0x1565C0));
                g.draw(AffineTransform.getTranslateInstance(x, y).createTransformedShape(gv.getGlyphLogicalBounds(i)));
                g.setColor(new Color(0xE53935));
                g.draw(AffineTransform.getTranslateInstance(x, y).createTransformedShape(gv.getGlyphVisualBounds(i)));
            }
            TextSupport.caption(g, "outline, logical bounds (blue), visual bounds (red)", 10, 100);
            // positions and transforms
            g.setColor(new Color(TextSupport.INK));
            g.drawGlyphVector(wave, 250, 60);
            TextSupport.caption(g, "setGlyphPosition + setGlyphTransform", 250, 100);
            // right-to-left layout
            g.setColor(new Color(TextSupport.INK));
            g.drawGlyphVector(rtl, 590, 60);
            TextSupport.caption(g, "layoutGlyphVector RTL", 590, 100);
            // ligatures : glyphs to characters
            g.setColor(new Color(0x6A1B9A));
            g.drawGlyphVector(lig, 760, 60);
            g.setColor(new Color(0xE53935));
            for (int i = 0; i < lig.getNumGlyphs(); i++) {
                g.draw(AffineTransform.getTranslateInstance(760, 60).createTransformedShape(lig.getGlyphLogicalBounds(i)));
            }
            TextSupport.caption(g, "LIGATURES_ON : glyph boxes", 760, 100);
            // glyph metrics of 'W' and 'j'
            Font big = TextAssets.roboto(48);
            GlyphVector wj = big.createGlyphVector(frc, "Wj");
            g.setColor(new Color(TextSupport.INK));
            g.fill(wj.getOutline(10, 150));
            for (int i = 0; i < 2; i++) {
                GlyphMetrics m = wj.getGlyphMetrics(i);
                Point2D p = wj.getGlyphPosition(i);
                g.setColor(new Color(0x43A047));
                g.draw(new Rectangle2D.Double(10 + p.getX(), 150 - 40, m.getAdvance(), 50));
                g.setColor(new Color(0xE53935));
                Rectangle2D b = m.getBounds2D();
                g.draw(new Rectangle2D.Double(10 + p.getX() + b.getX(), 150 + b.getY(), b.getWidth(), b.getHeight()));
            }
            TextSupport.caption(g, "GlyphMetrics : advance (green), bounds (red)", 90, 150);
            // pixel bounds
            Rectangle2D pixels = gv.getPixelBounds(frc, 360, 150);
            g.setColor(new Color(0x90A4AE));
            g.draw(pixels);
            g.setColor(new Color(TextSupport.INK));
            g.drawGlyphVector(gv, 360, 150);
            TextSupport.caption(g, "getPixelBounds", 620, 150);
        });

        checks.add(Checks.expect("\"Glyphs\" : glyph codes", "41 78 91 82 74 85", () -> Arrays.stream(
                gv.getGlyphCodes(0, gv.getNumGlyphs(), null)).mapToObj(String::valueOf).collect(Collectors.joining(" "))));
        checks.add(Checks.expect("\"Glyphs\" : logical bounds, visual bounds",
                "0.00,-51.95 169.42x65.63 ; 4.11,-42.66 162.09x54.61", () -> Checks.bounds(gv.getLogicalBounds()) + " ; "
                        + Checks.bounds(gv.getVisualBounds())));
        checks.add(Checks.expect("\"Glyphs\" : glyph positions x", "0.00 38.45 51.16 78.56 109.73 140.90 169.42", () -> {
            float[] positions = gv.getGlyphPositions(0, gv.getNumGlyphs() + 1, null);
            return IntStream.range(0, gv.getNumGlyphs() + 1).mapToObj(i -> num(positions[2 * i]))
                    .collect(Collectors.joining(" "));
        }));
        checks.add(Checks.expect("\"Glyphs\" : pixel bounds at (360, 150)", "364,107 163x55", () -> {
            java.awt.Rectangle r = gv.getPixelBounds(frc, 360, 150);
            return r.x + "," + r.y + " " + r.width + "x" + r.height;
        }));
        checks.add(Checks.expect("GlyphMetrics of 'W' : advance, LSB, RSB, bounds",
                "42.89, 1.55, 1.61, 1.55,-34.13 39.73x34.13", () -> {
                    GlyphMetrics m = TextAssets.roboto(48).createGlyphVector(frc, "W").getGlyphMetrics(0);
                    return num(m.getAdvance()) + ", " + num(m.getLSB()) + ", " + num(m.getRSB()) + ", "
                            + Checks.bounds(m.getBounds2D());
                }));
        checks.add(Checks.expect("GlyphMetrics of 'j' : isStandard, isWhitespace, isCombining", "true, false, false", () -> {
            GlyphMetrics m = TextAssets.roboto(48).createGlyphVector(frc, "j").getGlyphMetrics(0);
            return m.isStandard() + ", " + m.isWhitespace() + ", " + m.isCombining();
        }));
        checks.add(Checks.expect("wave : transformed glyphs, outline bounds", "true, -3.16,-38.84 266.32x51.64", () -> {
            Rectangle2D b = wave.getOutline().getBounds2D();
            return (wave.getGlyphTransform(0) != null) + ", " + Checks.bounds(b);
        }));
        checks.add(Checks.expect("layoutGlyphVector RTL (Hebrew) : glyphs, char indices, layout flags",
                "4, 3 2 1 0, FLAG_HAS_POSITION_ADJUSTMENTS FLAG_RUN_RTL",
                () -> rtl.getNumGlyphs() + ", " + Arrays.stream(rtl.getGlyphCharIndices(0,
                        rtl.getNumGlyphs(), null)).mapToObj(String::valueOf).collect(Collectors.joining(" ")) + ", "
                        + flags(rtl.getLayoutFlags())));
        checks.add(Checks.expect("layoutGlyphVector LIGATURES_ON \"office\" : glyphs, char indices", "5, 0 1 2 4 5",
                () -> lig.getNumGlyphs() + ", " + Arrays.stream(lig.getGlyphCharIndices(0, lig.getNumGlyphs(), null))
                        .mapToObj(String::valueOf).collect(Collectors.joining(" "))));
        checks.add(Checks.expect("createGlyphVector(int[] glyph codes) : outline bounds equals the string's", true, () -> {
            int[] codes = gv.getGlyphCodes(0, gv.getNumGlyphs(), null);
            GlyphVector fromCodes = font.createGlyphVector(frc, codes);
            return Checks.bounds(fromCodes.getOutline().getBounds2D()).equals(Checks.bounds(gv.getOutline()
                    .getBounds2D()));
        }));
        checks.add(Checks.expect("GlyphVector.equals (same font and text) / performDefaultLayout after moves", "true / true",
                () -> {
                    GlyphVector a = font.createGlyphVector(frc, "Glyphs");
                    GlyphVector b = font.createGlyphVector(frc, "Glyphs");
                    boolean equal = a.equals(b);
                    b.setGlyphPosition(2, new Point2D.Float(0, 0));
                    b.performDefaultLayout();
                    return equal + " / " + a.equals(b);
                }));
        return image;
    }

    static String flags(int flags) {
        List<String> names = new ArrayList<>();
        if ((flags & GlyphVector.FLAG_HAS_TRANSFORMS) != 0) {
            names.add("FLAG_HAS_TRANSFORMS");
        }
        if ((flags & GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS) != 0) {
            names.add("FLAG_HAS_POSITION_ADJUSTMENTS");
        }
        if ((flags & GlyphVector.FLAG_RUN_RTL) != 0) {
            names.add("FLAG_RUN_RTL");
        }
        if ((flags & GlyphVector.FLAG_COMPLEX_GLYPHS) != 0) {
            names.add("FLAG_COMPLEX_GLYPHS");
        }
        return names.isEmpty() ? "none" : String.join(" ", names);
    }

    // ------------------------------------------------------------------------------------------------- TextLayout

    private static AttributedString bidiLine() {
        AttributedString s = new AttributedString(BIDI, base(28));
        Font hebrew = TextAssets.font(TextAssets.HEBREW, 28);
        s.addAttribute(TextAttribute.FONT, hebrew, 8, 17);
        return s;
    }

    private static BufferedImage textLayout(List<Check> checks) {
        FontRenderContext frc = frc();
        TextLayout layout = new TextLayout(bidiLine().getIterator(), frc);
        float x = 20;
        float y = 50;
        int[] carets = { 0, 4, 8, 12, 17, 21 };
        float[] probes = { 5, 60, 150, 175, 240, 300 };
        BufferedImage image = Snapshots.offscreen(TextSupport.WIDTH, 140, g -> {
            TextSupport.sampleHints(g);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            g.setColor(new Color(0xBBDEFB));
            g.fill(layout.getLogicalHighlightShape(3, 13));
            g.setColor(new Color(TextSupport.INK));
            layout.draw(g, 0, 0);
            for (int offset : carets) {
                Shape[] shapes = layout.getCaretShapes(offset);
                g.setColor(Color.BLACK);
                g.draw(shapes[0]);
                if (shapes[1] != null) {
                    g.setColor(new Color(0xE53935));
                    g.draw(shapes[1]);
                }
            }
            g.setColor(new Color(0x43A047));
            for (float probe : probes) {
                g.fill(new Ellipse2D.Float(probe - 3, layout.getDescent() + 6, 6, 6));
            }
            // visual highlight of the same selection, on a second line
            g.translate(0, 60);
            g.setColor(new Color(0xFFE082));
            g.fill(layout.getVisualHighlightShape(TextHitInfo.leading(3), TextHitInfo.trailing(12)));
            g.setColor(new Color(TextSupport.INK));
            layout.draw(g, 0, 0);
            g.setColor(new Color(0x90A4AE));
            g.draw(layout.getBlackBoxBounds(0, layout.getCharacterCount()));
            g.translate(-x, -y - 60);
            TextSupport.caption(g, "visual highlight (leading 3 .. trailing 12), black box bounds", 440, 116);
            TextSupport.caption(g, "logical highlight 3..13, carets at 0 4 8 12 17 21", 440, 50);
        });

        checks.add(Checks.expect("isLeftToRight, character count, levels", "true, 21, 000000001111111110000",
                () -> layout.isLeftToRight() + ", " + layout.getCharacterCount() + ", " + IntStream.range(0,
                        layout.getCharacterCount()).mapToObj(i -> String.valueOf(layout.getCharacterLevel(i)))
                        .collect(Collectors.joining())));
        checks.add(Checks.expect("advance, visible advance, ascent, descent, leading", "304.79, 304.79, 29.90, 8.18, 0.00",
                () -> num(layout.getAdvance()) + ", " + num(layout.getVisibleAdvance()) + ", "
                        + num(layout.getAscent()) + ", " + num(layout.getDescent()) + ", " + num(layout.getLeading())));
        checks.add(Checks.expect("hitTestChar at x = 5 60 150 175 240 300", "0L 3T 14L 12L 17L 20T", () -> Arrays.stream(
                toDoubles(probes)).mapToObj(p -> hit(layout.hitTestChar((float) p, 0))).collect(Collectors.joining(" "))));
        checks.add(Checks.expect("getNextRightHit from leading(0), 21 steps",
                "0L 1L 2L 3L 4L 5L 6L 7L 16T 15T 14T 13T 12T 11T 10T 9T 8T 17L 18L 19L 20L 21L", () -> {
                    List<String> hits = new ArrayList<>();
                    TextHitInfo hit = TextHitInfo.leading(0);
                    for (int i = 0; i < 22 && hit != null; i++) {
                        hits.add(hit(hit));
                        hit = layout.getNextRightHit(hit);
                    }
                    return String.join(" ", hits);
                }));
        checks.add(Checks.expect("caret x of offsets 0 4 8 12 17 21 (strong)", "0.00 59.23 109.63 176.00 238.63 304.79",
                () -> Arrays.stream(carets).mapToObj(o -> num(layout.getCaretShapes(o)[0].getBounds2D().getCenterX()))
                        .collect(Collectors.joining(" "))));
        checks.add(Checks.expect("weak caret at offset 8 (bidi boundary)", "present", () -> layout.getCaretShapes(8)[1] != null
                ? "present"
                : "absent"));
        checks.add(Checks.expect("getLogicalRangesForVisualSelection(leading 3, trailing 12)", "3-8 13-17", () -> {
            int[] ranges = layout.getLogicalRangesForVisualSelection(TextHitInfo.leading(3), TextHitInfo.trailing(12));
            List<String> list = new ArrayList<>();
            for (int i = 0; i < ranges.length; i += 2) {
                list.add(ranges[i] + "-" + ranges[i + 1]);
            }
            return String.join(" ", list);
        }));
        checks.add(Checks.expect("getVisualOtherHit(trailing 7) / getVisualOtherHit(leading 8)", "16T / 17L",
                () -> hit(layout.getVisualOtherHit(TextHitInfo.trailing(7))) + " / "
                        + hit(layout.getVisualOtherHit(TextHitInfo.leading(8)))));
        checks.add(Checks.expect("getLogicalHighlightShape(3, 13) bounds", "49.70,-29.90 188.93x38.08",
                () -> Checks.bounds(layout.getLogicalHighlightShape(3, 13).getBounds2D())));
        checks.add(Checks.expect("getBlackBoxBounds / getBounds / getOutline bounds",
                "1.80,-21.33 302.19x24.66 ; 1.80,-21.33 302.19x24.66 ; 1.80,-21.33 302.19x24.66",
                () -> Checks.bounds(layout.getBlackBoxBounds(0,
                        layout.getCharacterCount()).getBounds2D()) + " ; " + Checks.bounds(layout.getBounds()) + " ; "
                        + Checks.bounds(layout.getOutline(null).getBounds2D())));
        checks.add(Checks.expect("baseline, baseline offsets (roman, center, hanging)", "0, 0.00 -11.28 -25.98",
                () -> layout.getBaseline() + ", " + Arrays.stream(toDoubles(layout.getBaselineOffsets())).limit(3)
                        .mapToObj(TextAttributesLayoutPage::num).collect(Collectors.joining(" "))));
        checks.add(Checks.expect("TextHitInfo : trailing(4).getOtherHit / getOffsetHit(2) / isLeading", "5L / 6T / false",
                () -> hit(TextHitInfo.trailing(4).getOtherHit()) + " / " + hit(TextHitInfo.trailing(4).getOffsetHit(2))
                        + " / " + TextHitInfo.trailing(4).isLeadingEdge()));
        checks.add(Checks.expect("TextLayout(String, Map) equals TextLayout(String, Font)", true, () -> {
            TextLayout a = new TextLayout("Equal", font(20, NONE), frc);
            TextLayout b = new TextLayout("Equal", Map.of(TextAttribute.FONT, font(20, NONE)), frc);
            return num(a.getAdvance()).equals(num(b.getAdvance()));
        }));
        checks.add(Checks.expect("right-to-left layout (RUN_DIRECTION_RTL) : isLeftToRight, hitTestChar(2)", "false, 18L",
                () -> {
                    AttributedString s = bidiLine();
                    s.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
                    TextLayout rtl = new TextLayout(s.getIterator(), frc);
                    return rtl.isLeftToRight() + ", " + hit(rtl.hitTestChar(2, 0));
                }));
        return image;
    }

    private static double[] toDoubles(float[] values) {
        double[] d = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            d[i] = values[i];
        }
        return d;
    }

    private static String hit(TextHitInfo hit) {
        return hit.getCharIndex() + (hit.isLeadingEdge() ? "L" : "T");
    }

    static String num(double v) {
        return Checks.num(v, 2);
    }

    // ----------------------------------------------------------------------------------------------- paragraphs

    private static AttributedString paragraph() {
        AttributedString s = new AttributedString(PARAGRAPH, base(15));
        style(s, "Quarkus Desktop", TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        style(s, "bold", TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        style(s, "larger", TextAttribute.SIZE, 22f);
        style(s, "colored", TextAttribute.FOREGROUND, new Color(0xC62828));
        style(s, "underlined", TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        style(s, "LineBreakMeasurer", TextAttribute.FOREGROUND, new Color(0x1565C0));
        style(s, "\uFFFC", TextAttribute.CHAR_REPLACEMENT, star(GraphicAttribute.ROMAN_BASELINE, true));
        return s;
    }

    private static void style(AttributedString s, String word, TextAttribute key, Object value) {
        int start = PARAGRAPH.indexOf(word);
        s.addAttribute(key, value, start, start + word.length());
    }

    private record Line(int start, int limit, TextLayout layout) {
    }

    private static List<Line> lines(AttributedString text, float width) {
        AttributedCharacterIterator it = text.getIterator();
        LineBreakMeasurer measurer = new LineBreakMeasurer(it, BreakIterator.getLineInstance(Locale.US), frc());
        List<Line> lines = new ArrayList<>();
        while (measurer.getPosition() < it.getEndIndex()) {
            int start = measurer.getPosition();
            TextLayout layout = measurer.nextLayout(width);
            lines.add(new Line(start, measurer.getPosition(), layout));
        }
        return lines;
    }

    private static BufferedImage paragraphs(List<Check> checks) {
        float width = 440;
        AttributedString text = paragraph();
        List<Line> ragged = lines(text, width);
        AttributedString justifiedText = paragraph();
        justifiedText.addAttribute(TextAttribute.JUSTIFICATION, TextAttribute.JUSTIFICATION_FULL);
        List<Line> lines = lines(justifiedText, width);
        List<TextLayout> justified = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            TextLayout layout = lines.get(i).layout();
            justified.add(i < lines.size() - 1 ? layout.getJustifiedLayout(width) : layout);
        }
        float height = 0;
        for (Line line : ragged) {
            height += line.layout().getAscent() + line.layout().getDescent() + line.layout().getLeading();
        }
        int imageHeight = (int) Math.ceil(height) + 16;
        BufferedImage image = Snapshots.offscreen(TextSupport.WIDTH, imageHeight, g -> {
            TextSupport.sampleHints(g);
            g.setColor(new Color(TextSupport.RULE_COLOR));
            g.drawLine(20, 0, 20, imageHeight);
            g.drawLine(20 + (int) width, 0, 20 + (int) width, imageHeight);
            g.drawLine(520, 0, 520, imageHeight);
            g.drawLine(520 + (int) width, 0, 520 + (int) width, imageHeight);
            float y = 4;
            for (Line line : ragged) {
                y += line.layout().getAscent();
                line.layout().draw(g, 20, y);
                y += line.layout().getDescent() + line.layout().getLeading();
            }
            y = 4;
            for (TextLayout layout : justified) {
                y += layout.getAscent();
                layout.draw(g, 520, y);
                y += layout.getDescent() + layout.getLeading();
            }
        });
        checks.add(Checks.expect("lines (width 440)", 7, ragged::size));
        checks.add(Checks.expect("line break offsets", "0 55 109 166 233 292 359 425",
                () -> ragged.stream().map(l -> String.valueOf(l.start())).collect(Collectors.joining(" ")) + " "
                        + ragged.getLast().limit()));
        checks.add(Checks.expect("line advances (ragged)", "396.90 346.87 385.13 439.67 372.04 413.78 427.32",
                () -> ragged.stream().map(l -> num(l.layout().getAdvance())).collect(Collectors.joining(" "))));
        checks.add(Checks.expect("justified line visible advances (getJustifiedLayout(440) of all but the last line)",
                "437.25 440.00 436.10 440.00 "
                        + "440.00 440.00 427.32",
                () -> justified.stream().map(l -> num(l.getVisibleAdvance()))
                        .collect(Collectors.joining(" "))));
        checks.add(Checks.expect("TextMeasurer : getLineBreakIndex(0, 440), getAdvanceBetween(0, 58)", "60, 419.69", () -> {
            TextMeasurer measurer = new TextMeasurer(paragraph().getIterator(), frc());
            return measurer.getLineBreakIndex(0, 440) + ", " + num(measurer.getAdvanceBetween(0, 58));
        }));
        checks.add(Checks.expect("TextMeasurer.getLayout(0, 15) : advance of \"Quarkus Desktop\" (bold)", "119.47",
                () -> num(new TextMeasurer(paragraph().getIterator(), frc()).getLayout(0, 15).getAdvance())));
        checks.add(Checks.expect("LineBreakMeasurer.nextOffset(100) / nextLayout(440, 30, true)", "8 / 30", () -> {
            LineBreakMeasurer measurer = new LineBreakMeasurer(paragraph().getIterator(), frc());
            int next = measurer.nextOffset(100);
            measurer.nextLayout(440, 30, true);
            return next + " / " + measurer.getPosition();
        }));
        checks.add(Checks.expect("BreakIterator.getLineInstance(Locale.US) : first break opportunities", "8 16 23 27 31",
                () -> {
                    BreakIterator it = BreakIterator.getLineInstance(Locale.US);
                    it.setText(PARAGRAPH);
                    List<String> breaks = new ArrayList<>();
                    for (int i = 0, b = it.next(); i < 5 && b != BreakIterator.DONE; i++, b = it.next()) {
                        breaks.add(String.valueOf(b));
                    }
                    return String.join(" ", breaks);
                }));
        return image;
    }
}
