package io.quarkiverse.desktop.showcase.pages.images;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.color.ICC_ProfileGray;
import java.awt.color.ICC_ProfileRGB;
import java.awt.color.ProfileDataException;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Color management : the predefined color spaces (sRGB, linear RGB, CIEXYZ, PhotoYCC, gray) and their conversions,
 * the built-in ICC profiles (header, tags, matrices, tone curves), an ICC profile generated at run time (wide gamut RGB)
 * loaded from bytes, a stream and a file, written and modified, {@link ICC_ColorSpace} conversions,
 * {@link ColorConvertOp} between profiles (and profile chains), a custom (non ICC) CMYK {@link ColorSpace}, CMYK JPEG
 * rasters, and ICC profiles embedded in PNG ({@code iCCP}) and JPEG ({@code APP2}) files.
 * <p>
 * Capture method C, computed in the background. Native risks : Little CMS (JNI : {@code sun.java2d.cmm.lcms.LCMS},
 * native transforms), the built-in profile resources ({@code sun/java2d/cmm/profiles/*.pf}), the ImageIO profile
 * handling, float formatting of conversions (rounded to 3 or 4 decimals).
 */
@Singleton
public class ColorManagementPage implements FeaturePage {

    private static final int STRIP_W = 700;
    private static final int STRIP_H = 30;

    private Container holder;

    @Override
    public String id() {
        return "images-color-management";
    }

    @Override
    public String title() {
        return "Color management (ICC)";
    }

    @Override
    public String category() {
        return Categories.IMAGES;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Component build() {
        holder = Ui.column(12, Ui.text("Converting colors..."));
        return Ui.column(12,
                Ui.text("Color spaces, ICC profiles (built-in and generated at run time: a wide gamut RGB profile with "
                        + "the Adobe RGB primaries) and conversions through Little CMS. The strips show the same colors "
                        + "through different color spaces.", 1000),
                holder);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Container target = holder;
        return Edt.background(ColorManagementPage::compute).thenAccept(result -> ImagesSupport.show(target,
                Ui.image(result.strips()),
                ChecksView.table("Color spaces", result.spaces()),
                ChecksView.table("ICC profiles", result.profiles()),
                ChecksView.table("Conversions and embedded profiles", result.conversions())));
    }

    @Override
    public void dispose(Component content) {
        holder = null;
    }

    private record Result(BufferedImage strips, List<Check> spaces, List<Check> profiles, List<Check> conversions) {
    }

    private static Result compute() {
        List<Check> spaces = new ArrayList<>();
        ImagesSupport.section(spaces, "color spaces", ColorManagementPage::spaceChecks);
        List<Check> profiles = new ArrayList<>();
        ImagesSupport.section(profiles, "ICC profiles", ColorManagementPage::profileChecks);
        List<Check> conversions = new ArrayList<>();
        BufferedImage[] strips = new BufferedImage[1];
        ImagesSupport.section(conversions, "conversions", checks -> strips[0] = strips(checks));
        ImagesSupport.section(conversions, "embedded profiles", ColorManagementPage::embeddedChecks);
        if (strips[0] == null) {
            strips[0] = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        return new Result(strips[0], spaces, profiles, conversions);
    }

    static String nums(float[] values, int decimals) {
        List<String> list = new ArrayList<>();
        for (float v : values) {
            list.add(Checks.num(v, decimals));
        }
        return String.join(" ", list);
    }

    static ICC_Profile wideProfile() {
        return ICC_Profile.getInstance(IccProfiles.wideGamut());
    }

    // ------------------------------------------------------------------------------------------------ color spaces

    private static void spaceChecks(List<Check> checks) {
        String[] names = { "sRGB", "LINEAR_RGB", "CIEXYZ", "PYCC", "GRAY" };
        int[] ids = { ColorSpace.CS_sRGB, ColorSpace.CS_LINEAR_RGB, ColorSpace.CS_CIEXYZ, ColorSpace.CS_PYCC,
                ColorSpace.CS_GRAY };
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            checks.add(Checks.expect("CS_" + names[i] + " : class, type, components, names, min/max of component 0",
                    expectedSpace(names[i]), () -> {
                        ColorSpace cs = ColorSpace.getInstance(id);
                        List<String> componentNames = new ArrayList<>();
                        for (int c = 0; c < cs.getNumComponents(); c++) {
                            componentNames.add(cs.getName(c));
                        }
                        return cs.getClass().getSimpleName() + ", " + cs.getType() + ", " + cs.getNumComponents() + ", "
                                + String.join(" ", componentNames) + ", " + Checks.num(cs.getMinValue(0), 3) + "/"
                                + Checks.num(cs.getMaxValue(0), 3);
                    }));
        }
        checks.add(Checks.expect("getInstance is a singleton, isCS_sRGB", "true, true, false", () -> (ColorSpace.getInstance(
                ColorSpace.CS_sRGB) == ColorSpace.getInstance(ColorSpace.CS_sRGB)) + ", " + ColorSpace.getInstance(
                        ColorSpace.CS_sRGB).isCS_sRGB()
                + ", " + ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB).isCS_sRGB()));
        float[] orange = { 1f, 0.5f, 0f };
        checks.add(Checks.expect("sRGB (1, 0.5, 0) -> toCIEXYZ", "0.5183 0.3759 0.0347",
                () -> nums(ColorSpace.getInstance(ColorSpace.CS_sRGB)
                        .toCIEXYZ(orange), 4)));
        checks.add(Checks.expect("sRGB (1, 0.5, 0) -> LINEAR_RGB (fromRGB)", "0.9999 0.2141 0.0000",
                () -> nums(ColorSpace.getInstance(
                        ColorSpace.CS_LINEAR_RGB).fromRGB(orange), 4)));
        checks.add(
                Checks.expect("sRGB (1, 0.5, 0) -> PYCC (fromRGB)", "0.3967 0.3664 0.7691", () -> nums(ColorSpace.getInstance(
                        ColorSpace.CS_PYCC).fromRGB(orange), 4)));
        checks.add(Checks.expect("sRGB (1, 0.5, 0) -> GRAY (fromRGB)", "0.3759", () -> nums(ColorSpace.getInstance(
                ColorSpace.CS_GRAY).fromRGB(orange), 4)));
        checks.add(Checks.expect("CIEXYZ D50 white (0.9642, 1, 0.8249) -> sRGB (fromCIEXYZ)", "1.000 1.000 1.000", () -> nums(
                ColorSpace.getInstance(ColorSpace.CS_sRGB).fromCIEXYZ(new float[] { 0.9642f, 1f, 0.8249f }), 3)));
        checks.add(Checks.expect("LINEAR_RGB (0.5, 0.5, 0.5) -> sRGB (toRGB)", "0.7354 0.7353 0.7353",
                () -> nums(ColorSpace.getInstance(
                        ColorSpace.CS_LINEAR_RGB).toRGB(new float[] { 0.5f, 0.5f, 0.5f }), 4)));
        checks.add(Checks.expect("custom CMYK ColorSpace (0.2, 0.4, 0.6, 0.1) -> toRGB, type, names", "0.720 0.540 0.360, 9, "
                + "Cyan Magenta Yellow Black", () -> {
                    CmykSpace cmyk = new CmykSpace();
                    return nums(cmyk.toRGB(new float[] { 0.2f, 0.4f, 0.6f, 0.1f }), 3) + ", " + cmyk.getType() + ", "
                            + cmyk.getName(0) + " " + cmyk.getName(1) + " " + cmyk.getName(2) + " " + cmyk.getName(3);
                }));
        checks.add(Checks.expect("Color(ColorSpace, components, alpha) in LINEAR_RGB : sRGB value, components",
                "#7FBBBBBB, 0.500 0.500 0.500", () -> {
                    Color c = new Color(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), new float[] { 0.5f, 0.5f, 0.5f },
                            0.5f);
                    return Checks.argb(c.getRGB()) + ", " + nums(c.getColorComponents(null), 3);
                }));
        checks.add(Checks.expect("Color.getColorComponents(CS_CIEXYZ) of opaque red", "0.4359 0.2224 0.0139",
                () -> nums(new Color(0xFF0000)
                        .getColorComponents(ColorSpace.getInstance(ColorSpace.CS_CIEXYZ), null), 4)));
    }

    private static String expectedProfile(String name) {
        return switch (name) {
            case "sRGB" -> "ICC_ProfileRGB, class 1, space 5, PCS 0, v2.48, 3 components, \"sRGB built-in\", 6876 B";
            case "LINEAR_RGB" -> "ICC_ProfileRGB, class 1, space 5, PCS 0, v2.48, 3 components, \"linear sRGB\", 488 B";
            case "CIEXYZ" -> "ICC_Profile, class 5, space 0, PCS 0, v2.48, 3 components, \"lcms XYZ identity\", 784 B";
            case "PYCC" -> "ICC_Profile, class 4, space 13, PCS 0, v4.0, 3 components, \"PYCC from PCD 045\", "
                    + "java.awt.color.CMMException: LCMS error 13: LUT is not suitable to be saved as LutBToA";
            default -> "ICC_ProfileGray, class 1, space 6, PCS 0, v2.48, 1 components, \"lcms gray virtual profile\", 556 B";
        };
    }

    private static String expectedSpace(String name) {
        return switch (name) {
            case "sRGB" -> "ICC_ColorSpace, 5, 3, Red Green Blue, 0.000/1.000";
            case "LINEAR_RGB" -> "ICC_ColorSpace, 5, 3, Red Green Blue, 0.000/1.000";
            case "CIEXYZ" -> "ICC_ColorSpace, 0, 3, X Y Z, 0.000/2.000";
            case "PYCC" -> "ICC_ColorSpace, 13, 3, Unnamed color component(0) Unnamed color component(1) Unnamed color "
                    + "component(2), 0.000/1.000";
            default -> "ICC_ColorSpace, 6, 1, Gray, 0.000/1.000";
        };
    }

    /**
     * A naive, non ICC CMYK color space (no profile) : {@code R = (1 - C)(1 - K)}...
     */
    static final class CmykSpace extends ColorSpace {

        CmykSpace() {
            super(TYPE_CMYK, 4);
        }

        @Override
        public float[] toRGB(float[] v) {
            float k = 1 - v[3];
            return new float[] { (1 - v[0]) * k, (1 - v[1]) * k, (1 - v[2]) * k };
        }

        @Override
        public float[] fromRGB(float[] rgb) {
            float max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
            float k = 1 - max;
            if (max <= 0) {
                return new float[] { 0, 0, 0, 1 };
            }
            return new float[] { (max - rgb[0]) / max, (max - rgb[1]) / max, (max - rgb[2]) / max, k };
        }

        @Override
        public float[] toCIEXYZ(float[] v) {
            return ColorSpace.getInstance(CS_sRGB).toCIEXYZ(toRGB(v));
        }

        @Override
        public float[] fromCIEXYZ(float[] xyz) {
            return fromRGB(ColorSpace.getInstance(CS_sRGB).fromCIEXYZ(xyz));
        }

        @Override
        public String getName(int component) {
            return new String[] { "Cyan", "Magenta", "Yellow", "Black" }[component];
        }
    }

    // ------------------------------------------------------------------------------------------------- profiles

    private static String header(ICC_Profile p) {
        return p.getClass().getSimpleName() + ", class " + p.getProfileClass() + ", space " + p.getColorSpaceType()
                + ", PCS " + p.getPCSType() + ", v" + p.getMajorVersion() + "." + p.getMinorVersion() + ", "
                + p.getNumComponents() + " components";
    }

    private static void profileChecks(List<Check> checks) throws IOException {
        String[] names = { "sRGB", "LINEAR_RGB", "CIEXYZ", "PYCC", "GRAY" };
        int[] ids = { ColorSpace.CS_sRGB, ColorSpace.CS_LINEAR_RGB, ColorSpace.CS_CIEXYZ, ColorSpace.CS_PYCC,
                ColorSpace.CS_GRAY };
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            checks.add(Checks.expect("built-in " + names[i] + " : class, header, description, data size",
                    expectedProfile(names[i]), () -> {
                        ICC_Profile p = ICC_Profile.getInstance(id);
                        String size;
                        try {
                            size = p.getData().length + " B";
                        } catch (java.awt.color.CMMException e) {
                            // the PYCC profile cannot be serialized by LCMS
                            size = Checks.describe(e);
                        }
                        return header(p) + ", \"" + IccProfiles.describe(p.getData(ICC_Profile.icSigProfileDescriptionTag))
                                + "\", " + size;
                    }));
        }
        checks.add(Checks.expect("sRGB : media white point, matrix row 0, gamma(RED)",
                "0.9501 1.0000 1.0883, 0.4359 0.3853 0.1430, ProfileDataException, TRC of 1024 entries", () -> {
                    ICC_ProfileRGB p = (ICC_ProfileRGB) ICC_Profile.getInstance(ColorSpace.CS_sRGB);
                    float[][] m = p.getMatrix();
                    String gamma;
                    try {
                        gamma = Checks.num(p.getGamma(ICC_ProfileRGB.REDCOMPONENT), 3);
                    } catch (ProfileDataException e) {
                        gamma = "ProfileDataException, TRC of " + p.getTRC(ICC_ProfileRGB.REDCOMPONENT).length + " entries";
                    }
                    return nums(p.getMediaWhitePoint(), 4) + ", " + nums(m[0], 4) + ", " + gamma;
                }));
        checks.add(Checks.expect("GRAY : class, gamma or TRC", "ICC_ProfileGray, gamma 1.000", () -> {
            ICC_Profile p = ICC_Profile.getInstance(ColorSpace.CS_GRAY);
            if (p instanceof ICC_ProfileGray gray) {
                try {
                    return "ICC_ProfileGray, gamma " + Checks.num(gray.getGamma(), 3);
                } catch (ProfileDataException e) {
                    return "ICC_ProfileGray, TRC of " + gray.getTRC().length + " entries";
                }
            }
            return p.getClass().getSimpleName();
        }));
        checks.add(Checks.expect("header tag of sRGB : size, 'acsp' signature, rendering intent", "128, acsp, 0", () -> {
            byte[] head = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData(ICC_Profile.icSigHead);
            return head.length + ", " + new String(head, 36, 4, java.nio.charset.StandardCharsets.US_ASCII) + ", "
                    + head[67];
        }));

        byte[] wideBytes = IccProfiles.wideGamut();
        ICC_Profile wide = ICC_Profile.getInstance(wideBytes);
        checks.add(Checks.expect("generated profile : class, header, description", "ICC_ProfileRGB, class 1, space 5, PCS 0, "
                + "v2.16, 3 components, " + IccProfiles.WIDE_DESCRIPTION,
                () -> header(wide) + ", " + IccProfiles.describe(
                        wide.getData(ICC_Profile.icSigProfileDescriptionTag))));
        checks.add(Checks.expect("generated profile : matrix (Adobe RGB primaries, D50), gamma", "0.6097 0.2053 0.1492 | "
                + "0.3111 0.6257 0.0632 | 0.0195 0.0609 0.7446, 2.199", () -> {
                    ICC_ProfileRGB rgb = (ICC_ProfileRGB) wide;
                    float[][] m = rgb.getMatrix();
                    return nums(m[0], 4) + " | " + nums(m[1], 4) + " | " + nums(m[2], 4) + ", "
                            + Checks.num(rgb.getGamma(ICC_ProfileRGB.GREENCOMPONENT), 3);
                }));
        checks.add(Checks.expect("generated profile from a stream and from a file : same description", "true / true", () -> {
            ICC_Profile fromStream = ICC_Profile.getInstance(new ByteArrayInputStream(wideBytes));
            Path file = Edt.tempDir().resolve("showcase-wide.icc");
            Files.write(file, wideBytes);
            ICC_Profile fromFile = ICC_Profile.getInstance(file.toString());
            String expected = IccProfiles.describe(wide.getData(ICC_Profile.icSigProfileDescriptionTag));
            return expected.equals(IccProfiles.describe(fromStream.getData(ICC_Profile.icSigProfileDescriptionTag)))
                    + " / " + expected.equals(IccProfiles.describe(fromFile.getData(
                            ICC_Profile.icSigProfileDescriptionTag)));
        }));
        checks.add(Checks.info("generated profile : getData() (serialized by LCMS) size, SHA-256", () -> {
            byte[] data = wide.getData();
            return data.length + " B (" + wideBytes.length + " B generated), " + Checks.sha256(data);
        }));
        checks.add(Checks.expect("ICC_Profile.write(OutputStream) then getInstance : description", IccProfiles.WIDE_DESCRIPTION,
                () -> {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    wide.write(out);
                    return IccProfiles.describe(ICC_Profile.getInstance(out.toByteArray()).getData(
                            ICC_Profile.icSigProfileDescriptionTag));
                }));
        checks.add(Checks.expect("setData(desc) on a copy, then getData(desc)", "Renamed by setData", () -> {
            ICC_Profile copy = ICC_Profile.getInstance(IccProfiles.wideGamut());
            copy.setData(ICC_Profile.icSigProfileDescriptionTag, IccProfiles.textDescription("Renamed by setData"));
            return IccProfiles.describe(copy.getData(ICC_Profile.icSigProfileDescriptionTag));
        }));
        checks.add(Checks.expect("getInstance(invalid bytes)", "java.lang.IllegalArgumentException: Invalid ICC Profile "
                + "Data", () -> {
                    try {
                        ICC_Profile.getInstance(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
                        return "no exception";
                    } catch (IllegalArgumentException e) {
                        return Checks.describe(e);
                    }
                }));
        checks.add(Checks.expect("ICC_ColorSpace(generated) : type, components, sRGB orange -> fromRGB -> toRGB",
                "5, 3, 0.891 0.496 0.116 -> 1.000 0.500 0.000", () -> {
                    ICC_ColorSpace cs = new ICC_ColorSpace(wide);
                    float[] values = cs.fromRGB(new float[] { 1f, 0.5f, 0f });
                    return cs.getType() + ", " + cs.getNumComponents() + ", " + nums(values, 3) + " -> "
                            + nums(cs.toRGB(values),
                                    3);
                }));
    }

    // ---------------------------------------------------------------------------------------------------- strips

    /**
     * The hue and lightness strip (sRGB), computed per pixel.
     */
    static BufferedImage hueStrip() {
        BufferedImage image = new BufferedImage(STRIP_W, STRIP_H, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < STRIP_W; x++) {
            float hue = x / (float) STRIP_W;
            for (int y = 0; y < STRIP_H; y++) {
                float brightness = y < STRIP_H / 2 ? 1f : 1f - (y - STRIP_H / 2f) / STRIP_H;
                image.setRGB(x, y, Color.HSBtoRGB(hue, 0.85f, brightness));
            }
        }
        return image;
    }

    /**
     * {@code source} values reinterpreted in the color space of {@code profile} (same numbers, no conversion).
     */
    static BufferedImage reinterpret(BufferedImage source, ICC_Profile profile) {
        ComponentColorModel model = new ComponentColorModel(new ICC_ColorSpace(profile), false, false,
                ComponentColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster raster = model.createCompatibleWritableRaster(source.getWidth(), source.getHeight());
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                raster.setPixel(x, y, new int[] { (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF });
            }
        }
        return new BufferedImage(model, raster, false, null);
    }

    private static BufferedImage convert(BufferedImage source, ColorSpace target, RenderingHints hints) {
        ComponentColorModel model = new ComponentColorModel(target, false, false, ComponentColorModel.OPAQUE,
                DataBuffer.TYPE_BYTE);
        BufferedImage out = new BufferedImage(model, model.createCompatibleWritableRaster(source.getWidth(),
                source.getHeight()), false, null);
        return new ColorConvertOp(hints).filter(source, out);
    }

    static BufferedImage cmykPatches() {
        CmykSpace cmyk = new CmykSpace();
        ComponentColorModel model = new ComponentColorModel(cmyk, false, false, ComponentColorModel.OPAQUE,
                DataBuffer.TYPE_BYTE);
        WritableRaster raster = model.createCompatibleWritableRaster(STRIP_W, STRIP_H);
        int[][] patches = { { 255, 0, 0, 0 }, { 0, 255, 0, 0 }, { 0, 0, 255, 0 }, { 0, 0, 0, 255 }, { 255, 255, 0, 0 },
                { 0, 255, 255, 0 }, { 255, 0, 255, 0 }, { 128, 64, 0, 32 }, { 0, 128, 200, 60 }, { 60, 60, 60, 60 } };
        int w = STRIP_W / patches.length;
        for (int x = 0; x < STRIP_W; x++) {
            int[] patch = patches[Math.min(patches.length - 1, x / w)];
            for (int y = 0; y < STRIP_H; y++) {
                raster.setPixel(x, y, patch);
            }
        }
        return new BufferedImage(model, raster, false, null);
    }

    private record Strip(String label, BufferedImage image) {
    }

    private static BufferedImage strips(List<Check> checks) throws IOException {
        BufferedImage hue = hueStrip();
        ICC_Profile wide = wideProfile();
        ICC_ColorSpace wideSpace = new ICC_ColorSpace(wide);
        List<Strip> strips = new ArrayList<>();
        strips.add(new Strip("sRGB source (HSB hues, saturation 0.85)", hue));
        BufferedImage reinterpreted = reinterpret(hue, wide);
        strips.add(new Strip("same values in the wide gamut profile (drawn : converted to sRGB)", reinterpreted));
        BufferedImage toWide = convert(hue, wideSpace, null);
        strips.add(new Strip("ColorConvertOp sRGB -> wide gamut (drawn back to sRGB)", toWide));
        RenderingHints quality = new RenderingHints(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        quality.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        BufferedImage linear = convert(hue, ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), quality);
        strips.add(new Strip("ColorConvertOp -> LINEAR_RGB (quality hints)", linear));
        BufferedImage gray = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(hue, null);
        strips.add(new Strip("ColorConvertOp -> CS_GRAY", gray));
        BufferedImage chain = new ColorConvertOp(new ICC_Profile[] { ICC_Profile.getInstance(ColorSpace.CS_sRGB), wide,
                ICC_Profile.getInstance(ColorSpace.CS_GRAY) }, null).filter(hue, new BufferedImage(STRIP_W, STRIP_H,
                        BufferedImage.TYPE_BYTE_GRAY));
        strips.add(new Strip("ColorConvertOp(ICC_Profile[] sRGB, wide, GRAY)", chain));
        BufferedImage cmyk = cmykPatches();
        strips.add(new Strip("custom CMYK ColorSpace (C, M, Y, K, CM, MY, CY, mixes)", cmyk));
        BufferedImage cmykJpeg = cmykJpegRoundTrip(checks);
        strips.add(new Strip("CMYK raster written as a 4 component JPEG, read back as a raster", cmykJpeg));

        checks.add(Checks.expect("wide gamut : reinterpreted pure red -> sRGB (out of gamut, clipped)",
                "#FFFF2121 vs #FFFF2626", () -> {
                    return Checks.argb(reinterpreted.getRGB(0, 0)) + " vs " + Checks.argb(hue.getRGB(0, 0));
                }));
        checks.add(Checks.expect("sRGB -> wide gamut -> sRGB round trip (drawn)", "mean 0.24, max 5",
                () -> ImagesSupport.compare(hue,
                        ImagesSupport.argb(toWide))));
        checks.add(Checks.expect("wide gamut samples of sRGB pure red", "[220, 42, 42]", () -> {
            int[] pixel = toWide.getRaster().getPixel(0, 0, (int[]) null);
            return Arrays.toString(pixel);
        }));
        checks.add(Checks.expect("LINEAR_RGB samples of sRGB (x 350, y 5)", "[5, 255, 255] from #FF26FFFF",
                () -> Arrays.toString(linear.getRaster()
                        .getPixel(350, 5, (int[]) null)) + " from " + Checks.argb(hue.getRGB(350, 5))));
        checks.add(
                Checks.expect("CS_GRAY / profile chain gray at x 350", "199 / 199", () -> gray.getRaster().getSample(350, 5, 0)
                        + " / " + chain.getRaster().getSample(350, 5, 0)));
        checks.add(Checks.expect("custom CMYK patches drawn : C, M, Y, K", "#FF00FFFF #FFFF00FF #FFFFFF00 #FF000000",
                () -> String.join(" ", Checks.argb(cmyk.getRGB(10, 5)), Checks.argb(cmyk.getRGB(80, 5)),
                        Checks.argb(cmyk.getRGB(150, 5)), Checks.argb(cmyk.getRGB(220, 5)))));
        checks.add(Checks.info("strips SHA-256", () -> String.join(" ", strips.stream().map(s -> Checks.sha256(
                ImagesSupport.argb(s.image()))).toList())));

        int labelW = 290;
        int rowH = STRIP_H + 10;
        return Snapshots.offscreen(labelW + STRIP_W, strips.size() * rowH, g -> {
            ImagesSupport.labelHints(g);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
            for (int i = 0; i < strips.size(); i++) {
                int y = i * rowH;
                g.setColor(new Color(ImagesSupport.MUTED));
                drawWrapped(g, strips.get(i).label(), 2, y + 12, labelW - 10);
                g.drawImage(strips.get(i).image(), labelW, y + 4, null);
            }
        });
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int width) {
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && g.getFontMetrics().stringWidth(candidate) > width) {
                g.drawString(line.toString(), x, yy);
                yy += 13;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        g.drawString(line.toString(), x, yy);
    }

    private static BufferedImage cmykJpegRoundTrip(List<Check> checks) throws IOException {
        BufferedImage cmyk = cmykPatches();
        Raster raster = cmyk.getRaster();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);
            writer.write(null, new IIOImage(raster, null, null), param);
        } finally {
            writer.dispose();
        }
        byte[] data = bytes.toByteArray();
        ImageReader reader = ImageIO.getImageReadersByFormatName("jpeg").next();
        reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(data)));
        Raster back = reader.readRaster(0, null);
        checks.add(Checks.expect("CMYK JPEG : bands, raster round trip error (lossy)", "4, mean 0.00, max 1", () -> {
            double sum = 0;
            int max = 0;
            for (int y = 0; y < back.getHeight(); y++) {
                for (int x = 0; x < back.getWidth(); x++) {
                    for (int b = 0; b < 4; b++) {
                        int d = Math.abs(back.getSample(x, y, b) - raster.getSample(x, y, b));
                        sum += d;
                        max = Math.max(max, d);
                    }
                }
            }
            return back.getNumBands() + ", mean " + Checks.num(sum / (4.0 * back.getWidth() * back.getHeight()), 2)
                    + ", max " + max;
        }));
        checks.add(Checks.expect("CMYK JPEG read() : color space type, class", "9, SimpleCMYKColorSpace", () -> {
            BufferedImage image = reader.read(0);
            return image.getColorModel().getColorSpace().getType() + ", " + image.getColorModel().getColorSpace()
                    .getClass().getSimpleName();
        }));
        reader.dispose();
        WritableRaster copy = back.createCompatibleWritableRaster();
        copy.setRect(back);
        return new BufferedImage(cmyk.getColorModel(), copy, false, null);
    }

    // ------------------------------------------------------------------------------------------- embedded profiles

    private static byte[] write(BufferedImage image, String format, Consumer<ImageWriteParam> param) throws IOException {
        return ImageIoFormatsPage.write(image, format, param);
    }

    /**
     * The color spaces of the image types a reader offers (profile descriptions for ICC color spaces).
     */
    private static String imageTypes(byte[] data, String format) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName(format).next();
        try {
            reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(data)));
            List<String> types = new ArrayList<>();
            for (java.util.Iterator<ImageTypeSpecifier> it = reader.getImageTypes(0); it.hasNext();) {
                types.add(space(it.next().getColorModel().getColorSpace()));
            }
            return String.join(" | ", types) + " ; raw : " + space(reader.getRawImageType(0).getColorModel()
                    .getColorSpace());
        } finally {
            reader.dispose();
        }
    }

    private static String space(ColorSpace cs) {
        return cs instanceof ICC_ColorSpace icc ? IccProfiles.describe(icc.getProfile().getData(
                ICC_Profile.icSigProfileDescriptionTag)) : cs.getClass().getSimpleName();
    }

    private static void embeddedChecks(List<Check> checks) throws IOException {
        ICC_Profile wide = wideProfile();
        BufferedImage wideImage = reinterpret(hueStrip().getSubimage(0, 0, 96, 30), wide);
        // the PNG writer does not embed the profile of the image by itself : an iCCP node is merged
        ImageWriter pngWriter = ImageIO.getImageWritersByFormatName("png").next();
        IIOMetadata metadata = pngWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(wideImage),
                null);
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
        IIOMetadataNode iccp = new IIOMetadataNode("iCCP");
        iccp.setAttribute("profileName", "Showcase wide gamut");
        iccp.setAttribute("compressionMethod", "deflate");
        iccp.setUserObject(wide.getData());
        root.appendChild(iccp);
        metadata.mergeTree("javax_imageio_png_1.0", root);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            pngWriter.setOutput(out);
            pngWriter.write(new IIOImage(wideImage, null, metadata));
        } finally {
            pngWriter.dispose();
        }
        byte[] png = bytes.toByteArray();
        checks.add(Checks.expect("PNG with an iCCP node : chunk written", true, () -> pngHasIccp(png)));
        checks.add(Checks.expect("PNG with iCCP : image types of the reader",
                "sRGB built-in | sRGB built-in | sRGB built-in | sRGB built-in ; raw : sRGB built-in",
                () -> imageTypes(png, "png")));
        checks.add(
                Checks.expect("PNG with iCCP : ImageIO.read color space (the reader does not apply iCCP), pixels drawn in sRGB",
                        "sRGB built-in, mean 7.93, max 38", () -> {
                            BufferedImage read = ImageIO.read(new ByteArrayInputStream(png));
                            return space(read.getColorModel().getColorSpace()) + ", "
                                    + ImagesSupport.compare(ImagesSupport.argb(
                                            wideImage), ImagesSupport.argb(read));
                        }));
        byte[] jpeg = write(wideImage, "jpeg", p -> {
        });
        checks.add(Checks.expect("JPEG of an image in the generated profile : APP2 ICC_PROFILE marker", true,
                () -> contains(jpeg, "ICC_PROFILE".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        checks.add(Checks.expect("JPEG with an ICC profile : image types of the reader",
                "sRGB built-in | Showcase wide gamut RGB (gamma 2.2) | lcms gray virtual profile ; raw : sRGB built-in",
                () -> imageTypes(jpeg,
                        "jpeg")));
        checks.add(Checks.expect("JPEG with an ICC profile : ImageIO.read color space, mean error drawn in sRGB",
                "sRGB built-in, mean 0.77, max 12",
                () -> {
                    BufferedImage read = ImageIO.read(new ByteArrayInputStream(jpeg));
                    return space(read.getColorModel().getColorSpace()) + ", " + ImagesSupport.compare(ImagesSupport.argb(
                            wideImage), ImagesSupport.argb(read));
                }));
    }

    private static boolean pngHasIccp(byte[] png) {
        return contains(png, "iCCP".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static boolean contains(byte[] data, byte[] pattern) {
        outer: for (int i = 0; i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
