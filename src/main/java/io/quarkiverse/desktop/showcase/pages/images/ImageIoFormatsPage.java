package io.quarkiverse.desktop.showcase.pages.images;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.bmp.BMPImageWriteParam;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.images.ImagesSupport.Tile;
import io.quarkiverse.desktop.showcase.pages.images.plugin.ShowcaseFormat;

/**
 * ImageIO formats : every standard writer and reader (PNG, JPEG, GIF, BMP, WBMP, TIFF) with their variants (color
 * types and bit depths, interlacing, compression types and levels, progressive JPEG, optimized Huffman tables, BMP
 * compressions including embedded PNG/JPEG, TIFF compressions, tiles, float samples and multi-page files), round
 * trips checked pixel by pixel, the plugin lookup API ({@code getImageReadersBy*}, {@code getImageWriter(reader)}),
 * streams (memory and file caches, files, random access files) and the reader / writer API (read params : region,
 * subsampling, destination type ; rasters, tiles, image types).
 * <p>
 * Capture method C : the decoded images are shown in a grid with their encoded size. Computed in the background
 * ({@link #ready}). Native risks : the JPEG codec (JNI, {@code javajpeg}), {@code Class.forName} of the plugin SPIs
 * and metadata formats, the resource bundles of the plugin messages, zlib.
 */
@Singleton
public class ImageIoFormatsPage implements FeaturePage {

    private static final int W = 96;
    private static final int H = 64;

    private Container holder;

    @Override
    public String id() {
        return "images-imageio-formats";
    }

    @Override
    public String title() {
        return "ImageIO formats";
    }

    @Override
    public String category() {
        return Categories.IMAGES;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() {
        holder = Ui.column(12, Ui.text("Encoding and decoding..."));
        return Ui.column(12,
                Ui.text("Every standard ImageIO writer and reader with its variants: the test pattern (96 x 64) is "
                        + "written, read back and compared pixel by pixel (lossy formats: mean channel error). Tiles show "
                        + "the decoded image, the variant and the encoded size.", 1000),
                holder);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Container target = holder;
        return Edt.background(ImageIoFormatsPage::compute).thenAccept(result -> ImagesSupport.show(target,
                Ui.title("Round trips"),
                Ui.image(ImagesSupport.grid(result.tiles(), 8, 125, 108)),
                ChecksView.table("Round trips (size, fidelity, SHA-256 of the encoded bytes)", result.roundTrips(), 330,
                        1000),
                ChecksView.table("Plugins and lookup", result.plugins()),
                ChecksView.table("Streams, readers and writers", result.api())));
    }

    @Override
    public void dispose(Component content) {
        holder = null;
    }

    private record Result(List<Tile> tiles, List<Check> roundTrips, List<Check> plugins, List<Check> api) {
    }

    // ---------------------------------------------------------------------------------------------------- variants

    private record Variant(String format, String label, Supplier<BufferedImage> source, Consumer<ImageWriteParam> param,
            double maxMeanError) {
    }

    private static Consumer<ImageWriteParam> compression(String type) {
        return p -> {
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionType(type);
        };
    }

    private static Consumer<ImageWriteParam> quality(float quality) {
        return p -> {
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
        };
    }

    private static final Consumer<ImageWriteParam> DEFAULT = p -> {
    };

    private static List<Variant> variants() {
        Supplier<BufferedImage> rgb = () -> ImagesSupport.pattern(W, H, BufferedImage.TYPE_INT_RGB);
        Supplier<BufferedImage> photo = () -> ImagesSupport.photo(W, H);
        Supplier<BufferedImage> binary = () -> ImagesSupport.binary(W, H);
        List<Variant> v = new ArrayList<>();
        // PNG
        v.add(new Variant("png", "RGB 8", rgb, DEFAULT, 0));
        v.add(new Variant("png", "RGBA 8", () -> ImagesSupport.patternArgb(W, H), DEFAULT, 0));
        v.add(new Variant("png", "gray 8", () -> ImagesSupport.gray(W, H, BufferedImage.TYPE_BYTE_GRAY), DEFAULT, 0));
        v.add(new Variant("png", "gray 16", () -> ImagesSupport.gray(W, H, BufferedImage.TYPE_USHORT_GRAY), DEFAULT, 0));
        v.add(new Variant("png", "RGB 16", ImageIoFormatsPage::rgb16, DEFAULT, 0));
        v.add(new Variant("png", "palette 8", () -> ImagesSupport.indexed(W, H, 8, -1), DEFAULT, 0));
        v.add(new Variant("png", "palette 4 + tRNS", () -> ImagesSupport.indexed(W, H, 4, 3), DEFAULT, 0));
        v.add(new Variant("png", "palette 2", () -> ImagesSupport.indexed(W, H, 2, -1), DEFAULT, 0));
        v.add(new Variant("png", "1 bit", binary, DEFAULT, 0));
        v.add(new Variant("png", "Adam7 interlaced", rgb, p -> p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT), 0));
        v.add(new Variant("png", "deflate quality 0", rgb, quality(0f), 0));
        v.add(new Variant("png", "deflate quality 1", rgb, quality(1f), 0));
        // JPEG
        v.add(new Variant("jpeg", "quality 0.9", photo, quality(0.9f), 3));
        v.add(new Variant("jpeg", "quality 0.25", photo, quality(0.25f), 6));
        v.add(new Variant("jpeg", "progressive", photo, p -> p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT), 4));
        v.add(new Variant("jpeg", "optimized Huffman", photo,
                p -> ((JPEGImageWriteParam) p).setOptimizeHuffmanTables(true), 4));
        v.add(new Variant("jpeg", "gray", () -> ImagesSupport.gray(W, H, BufferedImage.TYPE_BYTE_GRAY), quality(0.9f), 6));
        v.add(new Variant("jpeg", "pattern 0.75", rgb, DEFAULT, 20));
        // GIF
        v.add(new Variant("gif", "palette 8", () -> ImagesSupport.indexed(W, H, 8, -1), DEFAULT, 0));
        v.add(new Variant("gif", "RGB (PaletteBuilder)", rgb, DEFAULT, 1));
        v.add(new Variant("gif", "interlaced", () -> ImagesSupport.indexed(W, H, 8, -1),
                p -> p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT), 0));
        v.add(new Variant("gif", "transparent index", () -> ImagesSupport.indexed(W, H, 4, 3), DEFAULT, 0));
        // BMP
        v.add(new Variant("bmp", "BI_RGB 24", rgb, DEFAULT, 0));
        v.add(new Variant("bmp", "BI_RGB top-down", rgb, p -> ((BMPImageWriteParam) p).setTopDown(true), 0));
        v.add(new Variant("bmp", "BI_BITFIELDS 565", () -> ImagesSupport.pattern(W, H, BufferedImage.TYPE_USHORT_565_RGB),
                compression("BI_BITFIELDS"), 0));
        v.add(new Variant("bmp", "BI_RLE8", () -> ImagesSupport.indexed(W, H, 8, -1), compression("BI_RLE8"), 0));
        v.add(new Variant("bmp", "BI_RLE4", () -> ImagesSupport.indexed(W, H, 4, -1), compression("BI_RLE4"), 0));
        v.add(new Variant("bmp", "BI_RGB 1 bit", binary, DEFAULT, 0));
        v.add(new Variant("bmp", "BI_PNG", rgb, compression("BI_PNG"), 0));
        v.add(new Variant("bmp", "BI_JPEG", photo, compression("BI_JPEG"), 4));
        // WBMP
        v.add(new Variant("wbmp", "1 bit", binary, DEFAULT, 0));
        // TIFF
        v.add(new Variant("tiff", "uncompressed", rgb, DEFAULT, 0));
        for (String type : List.of("PackBits", "LZW", "Deflate", "ZLib")) {
            v.add(new Variant("tiff", type, rgb, compression(type), 0));
        }
        v.add(new Variant("tiff", "JPEG", photo, compression("JPEG"), 4));
        for (String type : List.of("CCITT RLE", "CCITT T.4", "CCITT T.6")) {
            v.add(new Variant("tiff", type, binary, compression(type), 0));
        }
        v.add(new Variant("tiff", "tiled 32 x 32", rgb, p -> {
            p.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
            p.setTiling(32, 32, 0, 0);
        }, 0));
        v.add(new Variant("tiff", "gray 16", () -> ImagesSupport.gray(W, H, BufferedImage.TYPE_USHORT_GRAY),
                compression("LZW"), 0));
        v.add(new Variant("tiff", "float gray", ImageIoFormatsPage::floatGray, compression("Deflate"), 0));
        v.add(new Variant("tiff", "RGBA", () -> ImagesSupport.patternArgb(W, H), compression("PackBits"), 0));
        v.add(new Variant("tiff", "Exif JPEG", photo, compression("Exif JPEG"), 4));
        return v;
    }

    /**
     * 16 bits per sample RGB (TYPE_CUSTOM : ComponentColorModel over a UShort raster).
     */
    static BufferedImage rgb16() {
        ComponentColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false,
                ComponentColorModel.OPAQUE, DataBuffer.TYPE_USHORT);
        WritableRaster raster = model.createCompatibleWritableRaster(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = ImagesSupport.patternRgb(x, y, W, H);
                raster.setSample(x, y, 0, ((rgb >> 16) & 0xFF) * 257);
                raster.setSample(x, y, 1, ((rgb >> 8) & 0xFF) * 257);
                raster.setSample(x, y, 2, (rgb & 0xFF) * 257);
            }
        }
        return new BufferedImage(model, raster, false, null);
    }

    /**
     * 32 bit float gray samples in [0, 1] (TYPE_CUSTOM : ComponentColorModel over a float raster).
     */
    static BufferedImage floatGray() {
        ComponentColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), false, false,
                ComponentColorModel.OPAQUE, DataBuffer.TYPE_FLOAT);
        WritableRaster raster = model.createCompatibleWritableRaster(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                raster.setSample(x, y, 0, (float) (0.5 + 0.5 * Math.sin(x / 7.0) * Math.cos(y / 5.0)));
            }
        }
        return new BufferedImage(model, raster, false, null);
    }

    static byte[] write(BufferedImage image, String format, Consumer<ImageWriteParam> configure) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            configure.accept(param);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    // -------------------------------------------------------------------------------------------------- compute

    private static Result compute() {
        List<Tile> tiles = new ArrayList<>();
        List<Check> roundTrips = new ArrayList<>();
        for (Variant variant : variants()) {
            String name = variant.format().toUpperCase(Locale.ROOT) + " " + variant.label();
            try {
                BufferedImage source = variant.source().get();
                byte[] bytes = write(source, variant.format(), variant.param());
                BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                if (decoded == null) {
                    roundTrips.add(Check.fail(name, "no reader for the written bytes"));
                    tiles.add(new Tile(null, name, "no reader"));
                    continue;
                }
                String fidelity;
                boolean ok;
                if (variant.label().equals("float gray")) {
                    // float samples are shown through the gray color space : compare the samples
                    fidelity = sameSamples(source.getRaster(), decoded.getRaster()) ? "exact samples" : "samples differ";
                    ok = fidelity.equals("exact samples");
                } else {
                    String compare = ImagesSupport.compare(ImagesSupport.argb(source), ImagesSupport.argb(decoded));
                    double error = ImagesSupport.meanError(ImagesSupport.argb(source), ImagesSupport.argb(decoded));
                    fidelity = compare;
                    ok = variant.maxMeanError() == 0 ? compare.equals("exact") : error <= variant.maxMeanError();
                }
                String value = bytes.length + " B, " + fidelity + ", " + Checks.sha256(bytes) + ", decoded "
                        + ImagesSupport.size(decoded) + " type " + decoded.getType();
                roundTrips.add(Check.of(name, ok, value));
                tiles.add(new Tile(decoded, name, bytes.length + " B, " + (fidelity.startsWith("mean") ? "lossy"
                        : fidelity)));
            } catch (Throwable t) {
                roundTrips.add(Check.fail(name, Checks.describe(t)));
                tiles.add(new Tile(null, name, "error"));
            }
        }
        // multi-page TIFF and animated GIF sequences
        ImagesSupport.section(roundTrips, "TIFF multi-page (writeToSequence)", checks -> sequence(checks, "tiff", tiles));
        ImagesSupport.section(roundTrips, "GIF sequence (writeToSequence)", checks -> sequence(checks, "gif", tiles));

        List<Check> plugins = new ArrayList<>();
        ImagesSupport.section(plugins, "plugins", ImageIoFormatsPage::pluginChecks);
        List<Check> api = new ArrayList<>();
        ImagesSupport.section(api, "streams", ImageIoFormatsPage::streamChecks);
        ImagesSupport.section(api, "reader", ImageIoFormatsPage::readerChecks);
        ImagesSupport.section(api, "writer", ImageIoFormatsPage::writerChecks);
        return new Result(tiles, roundTrips, plugins, api);
    }

    private static boolean sameSamples(Raster a, Raster b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight() || a.getNumBands() != b.getNumBands()) {
            return false;
        }
        double[] pa = a.getPixels(0, 0, a.getWidth(), a.getHeight(), (double[]) null);
        double[] pb = b.getPixels(0, 0, b.getWidth(), b.getHeight(), (double[]) null);
        return Arrays.equals(pa, pb);
    }

    private static void sequence(List<Check> checks, String format, List<Tile> tiles) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<BufferedImage> pages = List.of(ImagesSupport.pattern(W, H, BufferedImage.TYPE_INT_RGB),
                ImagesSupport.indexed(W, H, 8, -1), ImagesSupport.gray(W, H, BufferedImage.TYPE_BYTE_GRAY));
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (BufferedImage page : pages) {
                writer.writeToSequence(new IIOImage(format.equals("gif") ? ImagesSupport.indexed(W, H, 8, -1) : page,
                        null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        ImageReader reader = ImageIO.getImageReadersByFormatName(format).next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            reader.setInput(in);
            int count = reader.getNumImages(true);
            List<String> sizes = new ArrayList<>();
            BufferedImage last = null;
            for (int i = 0; i < count; i++) {
                last = reader.read(i);
                sizes.add(ImagesSupport.size(last));
            }
            String name = format.toUpperCase(Locale.ROOT) + " writeToSequence : images, sizes";
            checks.add(Checks.expect(name, "3, 96x64 96x64 96x64", () -> count + ", " + String.join(" ", sizes)));
            if (format.equals("tiff")) {
                BufferedImage page3 = last;
                checks.add(Checks.expect("TIFF page 3 equals its source", "exact", () -> ImagesSupport.compare(
                        ImagesSupport.argb(pages.get(2)), ImagesSupport.argb(page3))));
            }
            tiles.add(new Tile(last, format.toUpperCase(Locale.ROOT) + " sequence", count + " images, "
                    + bytes.size() + " B"));
        } finally {
            reader.dispose();
        }
    }

    // --------------------------------------------------------------------------------------------------- plugins

    /**
     * The names without those of the custom plugin of the metadata page (format, suffix, MIME type).
     */
    private static String standard(String[] names) {
        List<String> plugin = List.of(ShowcaseFormat.NAME, ShowcaseFormat.SUFFIX, ShowcaseFormat.MIME_TYPE);
        return ImagesSupport.lower(Arrays.stream(names).filter(n -> !plugin.contains(n.toLowerCase(Locale.ROOT)))
                .toArray(String[]::new));
    }

    private static void pluginChecks(List<Check> checks) {
        checks.add(Checks.expect("reader format names (standard)", "bmp gif jpeg jpg png tif tiff wbmp",
                () -> standard(ImageIO.getReaderFormatNames())));
        checks.add(Checks.expect("writer format names (standard)", "bmp gif jpeg jpg png tif tiff wbmp",
                () -> standard(ImageIO.getWriterFormatNames())));
        checks.add(Checks.expect("reader file suffixes (standard)", "bmp gif jpeg jpg png tif tiff wbmp",
                () -> standard(ImageIO.getReaderFileSuffixes())));
        checks.add(Checks.expect("reader MIME types (standard)", "image/bmp image/gif image/jpeg image/png image/tiff "
                + "image/vnd.wap.wbmp image/x-png",
                () -> standard(ImageIO.getReaderMIMETypes())));
        checks.add(Checks.expect("writer MIME types (standard)", "image/bmp image/gif image/jpeg image/png image/tiff "
                + "image/vnd.wap.wbmp image/x-png",
                () -> standard(ImageIO.getWriterMIMETypes())));
        List<String> formats = List.of("png", "jpeg", "gif", "bmp", "wbmp", "tiff");
        for (String format : formats) {
            checks.add(Checks.expect("reader / writer / getImageWriter(reader) / getImageReader(writer) : " + format,
                    expectedPlugins(format), () -> {
                        ImageReader reader = ImageIO.getImageReadersByFormatName(format).next();
                        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
                        ImageWriter writerOfReader = ImageIO.getImageWriter(reader);
                        ImageReader readerOfWriter = ImageIO.getImageReader(writer);
                        return simple(reader) + " / " + simple(writer) + " / " + simple(writerOfReader) + " / "
                                + simple(readerOfWriter);
                    }));
        }
        checks.add(Checks.expect("getImageReadersBySuffix(tif), ByMIMEType(image/vnd.wap.wbmp), ByFormatName(JPG)",
                "TIFFImageReader, WBMPImageReader, JPEGImageReader", () -> simple(ImageIO.getImageReadersBySuffix("tif")
                        .next()) + ", " + simple(ImageIO.getImageReadersByMIMEType("image/vnd.wap.wbmp").next()) + ", "
                        + simple(ImageIO.getImageReadersByFormatName("JPG").next())));
        checks.add(Checks.expect("PNG reader SPI : description, vendor, version, native metadata format",
                "Standard PNG image reader, Oracle Corporation, 1.0, javax_imageio_png_1.0", () -> {
                    ImageReaderSpi spi = ImageIO.getImageReadersByFormatName("png").next().getOriginatingProvider();
                    return spi.getDescription(Locale.US) + ", " + spi.getVendorName() + ", " + spi.getVersion() + ", "
                            + spi.getNativeImageMetadataFormatName();
                }));
        checks.add(Checks.expect("getImageTranscoders(png reader, jpeg writer)", false,
                () -> ImageIO.getImageTranscoders(ImageIO.getImageReadersByFormatName("png").next(),
                        ImageIO.getImageWritersByFormatName("jpeg").next()).hasNext()));
        checks.add(Checks.expect("ImageIO.write(TYPE_INT_ARGB, jpeg) (no writer for alpha)", false,
                () -> ImageIO.write(ImagesSupport.patternArgb(8, 8), "jpeg", new ByteArrayOutputStream())));
        checks.add(Checks.expect("getImageWriters(ARGB specifier, gif / bmp / png / jpeg / tiff)", "true false true false "
                + "true",
                () -> String.join(" ", formats.stream().filter(f -> !f.equals("wbmp")).sorted((a, b) -> order(a)
                        - order(b)).map(f -> String.valueOf(
                                ImageIO.getImageWriters(
                                        ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB), f)
                                        .hasNext()))
                        .toList())));
        checks.add(Checks.expect("ImageIO.read(unknown bytes)", "null",
                () -> String.valueOf(ImageIO.read(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 })))));
    }

    private static int order(String format) {
        return List.of("gif", "bmp", "png", "jpeg", "tiff").indexOf(format);
    }

    private static String simple(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    private static String expectedPlugins(String format) {
        String prefix = switch (format) {
            case "png" -> "PNG";
            case "jpeg" -> "JPEG";
            case "gif" -> "GIF";
            case "bmp" -> "BMP";
            case "wbmp" -> "WBMP";
            default -> "TIFF";
        };
        return prefix + "ImageReader / " + prefix + "ImageWriter / " + prefix + "ImageWriter / " + prefix + "ImageReader";
    }

    // --------------------------------------------------------------------------------------------------- streams

    private static void streamChecks(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.pattern(W, H, BufferedImage.TYPE_INT_RGB);
        byte[] png = write(image, "png", DEFAULT);
        boolean useCache = ImageIO.getUseCache();
        File cacheDirectory = ImageIO.getCacheDirectory();
        try {
            ImageIO.setCacheDirectory(Edt.tempDir().toFile());
            ImageIO.setUseCache(true);
            checks.add(Checks.expect("setUseCache(true) : output / input stream classes",
                    "FileCacheImageOutputStream / FileCacheImageInputStream", () -> {
                        try (ImageOutputStream out = ImageIO.createImageOutputStream(new ByteArrayOutputStream());
                                ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
                            return simple(out) + " / " + simple(in);
                        }
                    }));
            checks.add(Checks.expect("ImageIO.write / read with the file cache", "exact", () -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                return ImagesSupport.compare(image, ImageIO.read(new ByteArrayInputStream(out.toByteArray())));
            }));
            ImageIO.setUseCache(false);
            checks.add(Checks.expect("setUseCache(false) : output / input stream classes",
                    "MemoryCacheImageOutputStream / MemoryCacheImageInputStream", () -> {
                        try (ImageOutputStream out = ImageIO.createImageOutputStream(new ByteArrayOutputStream());
                                ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
                            return simple(out) + " / " + simple(in);
                        }
                    }));
        } finally {
            ImageIO.setUseCache(useCache);
            ImageIO.setCacheDirectory(cacheDirectory);
        }
        Path dir = Edt.tempDir();
        File file = dir.resolve("formats-roundtrip.png").toFile();
        checks.add(Checks.expect("ImageIO.write(File) / read(File) / read(URL)", "true / exact / exact", () -> {
            boolean written = ImageIO.write(image, "png", file);
            return written + " / " + ImagesSupport.compare(image, ImageIO.read(file)) + " / "
                    + ImagesSupport.compare(image, ImageIO.read(file.toURI().toURL()));
        }));
        checks.add(Checks.expect("FileImageOutputStream / FileImageInputStream (RandomAccessFile)", "exact", () -> {
            File tiff = dir.resolve("formats-stream.tif").toFile();
            try (FileImageOutputStream out = new FileImageOutputStream(tiff)) {
                ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
                writer.setOutput(out);
                writer.write(image);
                writer.dispose();
            }
            try (RandomAccessFile raf = new RandomAccessFile(tiff, "r");
                    FileImageInputStream in = new FileImageInputStream(
                            raf)) {
                ImageReader reader = ImageIO.getImageReaders(in).next();
                reader.setInput(in);
                BufferedImage read = reader.read(0);
                reader.dispose();
                return ImagesSupport.compare(image, read);
            }
        }));
        checks.add(Checks.expect("MemoryCacheImageInputStream : seek, readInt (PNG signature), length", "-1991225785, 13",
                () -> {
                    try (MemoryCacheImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
                        int signature = in.readInt();
                        in.seek(8);
                        return signature + ", " + in.readInt();
                    }
                }));
        checks.add(Checks.expect("ImageInputStream bit reading : readBits(3), readBit, getBitOffset", "4, 0, 4", () -> {
            try (MemoryCacheImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
                long bits = in.readBits(3);
                int bit = in.readBit();
                return bits + ", " + bit + ", " + in.getBitOffset();
            }
        }));
        Files.deleteIfExists(file.toPath());
    }

    // ------------------------------------------------------------------------------------------ readers, writers

    private static ImageReader reader(byte[] bytes, String format) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName(format).next();
        reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)));
        return reader;
    }

    private static void readerChecks(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.pattern(W, H, BufferedImage.TYPE_INT_RGB);
        byte[] png = write(image, "png", DEFAULT);
        byte[] tiled = write(image, "tiff", p -> {
            p.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
            p.setTiling(32, 32, 0, 0);
        });
        byte[] jpeg = write(ImagesSupport.photo(W, H), "jpeg", DEFAULT);
        checks.add(Checks.expect("PNG reader : numImages, width, height, aspect ratio, random access",
                "1, 96, 64, 1.500, false", () -> {
                    ImageReader r = reader(png, "png");
                    return r.getNumImages(true) + ", " + r.getWidth(0) + ", " + r.getHeight(0) + ", "
                            + Checks.num(r.getAspectRatio(0)) + ", " + r.isRandomAccessEasy(0);
                }));
        checks.add(Checks.expect("PNG reader : image types, raw image type", "4, TYPE_CUSTOM", () -> {
            ImageReader r = reader(png, "png");
            Iterator<ImageTypeSpecifier> types = r.getImageTypes(0);
            int n = 0;
            while (types.hasNext()) {
                types.next();
                n++;
            }
            return n + ", " + typeName(r.getRawImageType(0).getBufferedImageType());
        }));
        checks.add(Checks.expect("ImageReadParam : source region (10, 8, 40, 30) equals the sub image", "exact", () -> {
            ImageReader r = reader(png, "png");
            ImageReadParam param = r.getDefaultReadParam();
            param.setSourceRegion(new Rectangle(10, 8, 40, 30));
            return ImagesSupport.compare(image.getSubimage(10, 8, 40, 30), r.read(0, param));
        }));
        checks.add(Checks.expect("ImageReadParam : subsampling 3 x 2, offsets 1, 1", "exact, 32x32", () -> {
            ImageReader r = reader(png, "png");
            ImageReadParam param = r.getDefaultReadParam();
            param.setSourceSubsampling(3, 2, 1, 1);
            BufferedImage read = r.read(0, param);
            BufferedImage expected = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < read.getHeight(); y++) {
                for (int x = 0; x < read.getWidth(); x++) {
                    expected.setRGB(x, y, image.getRGB(1 + 3 * x, 1 + 2 * y));
                }
            }
            return ImagesSupport.compare(expected, read) + ", " + ImagesSupport.size(read);
        }));
        checks.add(Checks.expect("ImageReadParam : setDestinationType(TYPE_INT_BGR), destination offset", "4, exact",
                () -> {
                    ImageReader r = reader(png, "png");
                    ImageReadParam param = r.getDefaultReadParam();
                    param.setDestinationType(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_BGR));
                    BufferedImage read = r.read(0, param);
                    ImageReader r2 = reader(png, "png");
                    ImageReadParam offset = r2.getDefaultReadParam();
                    BufferedImage destination = new BufferedImage(W + 4, H + 4, BufferedImage.TYPE_INT_RGB);
                    offset.setDestination(destination);
                    offset.setDestinationOffset(new java.awt.Point(4, 4));
                    r2.read(0, offset);
                    return read.getType() + ", " + ImagesSupport.compare(image, destination.getSubimage(4, 4, W, H));
                }));
        checks.add(Checks.expect("TIFF tiled : isImageTiled, tile size, tiles, readTile(1, 1)", "true, 32x32, 3x2, exact",
                () -> {
                    ImageReader r = reader(tiled, "tiff");
                    BufferedImage tile = r.readTile(0, 1, 1);
                    return r.isImageTiled(0) + ", " + r.getTileWidth(0) + "x" + r.getTileHeight(0) + ", "
                            + (W + 31) / 32 + "x" + (H + 31) / 32 + ", " + ImagesSupport.compare(image.getSubimage(32, 32,
                                    32, 32), tile);
                }));
        checks.add(Checks.expect("JPEG reader : canReadRaster, readRaster bands, readAsRenderedImage", "true, 3, 96x64",
                () -> {
                    ImageReader r = reader(jpeg, "jpeg");
                    Raster raster = r.readRaster(0, null);
                    java.awt.image.RenderedImage rendered = r.readAsRenderedImage(0, null);
                    return r.canReadRaster() + ", " + raster.getNumBands() + ", " + rendered.getWidth() + "x"
                            + rendered.getHeight();
                }));
        checks.add(Checks.expect("reader of a truncated PNG", "javax.imageio.IIOException", () -> {
            try {
                ImageIO.read(new ByteArrayInputStream(Arrays.copyOf(png, png.length / 2)));
                return "no exception";
            } catch (IOException e) {
                return e.getClass().getName();
            }
        }));
    }

    private static String typeName(int type) {
        return switch (type) {
            case BufferedImage.TYPE_CUSTOM -> "TYPE_CUSTOM";
            case BufferedImage.TYPE_INT_RGB -> "TYPE_INT_RGB";
            case BufferedImage.TYPE_INT_ARGB -> "TYPE_INT_ARGB";
            case BufferedImage.TYPE_3BYTE_BGR -> "TYPE_3BYTE_BGR";
            case BufferedImage.TYPE_4BYTE_ABGR -> "TYPE_4BYTE_ABGR";
            case BufferedImage.TYPE_BYTE_GRAY -> "TYPE_BYTE_GRAY";
            case BufferedImage.TYPE_BYTE_INDEXED -> "TYPE_BYTE_INDEXED";
            default -> String.valueOf(type);
        };
    }

    private static void writerChecks(List<Check> checks) {
        checks.add(Checks.expect("compression types : png | jpeg | gif | bmp | tiff", "Deflate | JPEG | LZW | BI_RGB BI_RLE8 "
                + "BI_RLE4 BI_BITFIELDS BI_JPEG BI_PNG | CCITT RLE, CCITT T.4, CCITT T.6, LZW, JPEG, ZLib, PackBits, "
                + "Deflate, Exif JPEG",
                () -> String.join(" | ", types("png", " "), types("jpeg", " "), types("gif", " "),
                        types("bmp", " "), types("tiff", ", "))));
        checks.add(Checks.expect("canWriteSequence / canWriteRasters / canInsertImage(0) : gif, tiff, jpeg",
                "true false false, true false false, true true false", () -> String.join(", ",
                        writerCaps("gif"), writerCaps("tiff"), writerCaps("jpeg"))));
        checks.add(Checks.expect("write params : canWriteProgressive, canWriteTiles (png, jpeg, gif, tiff)",
                "true false, true false, true false, false true", () -> String.join(", ",
                        Arrays.stream(new String[] { "png", "jpeg", "gif", "tiff" }).map(f -> {
                            ImageWriteParam p = ImageIO.getImageWritersByFormatName(f).next().getDefaultWriteParam();
                            return p.canWriteProgressive() + " " + p.canWriteTiles();
                        }).toList())));
        checks.add(Checks.expect("JPEG write param : quality descriptions",
                "Low quality, Medium quality, Visually lossless at [0.0, 0.3, 0.75, 1.0]", () -> {
                    ImageWriteParam param = ImageIO.getImageWritersByFormatName("jpeg").next().getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    return String.join(", ", param.getCompressionQualityDescriptions()) + " at " + Arrays.toString(
                            param.getCompressionQualityValues());
                }));
        checks.add(Checks.expect("ImageTypeSpecifier : createGrayscale(16), createIndexed, createInterleaved bands",
                "16, 8, 3", () -> {
                    ImageTypeSpecifier gray = ImageTypeSpecifier.createGrayscale(16, DataBuffer.TYPE_USHORT, false);
                    BufferedImage indexed = ImagesSupport.indexed(4, 4, 8, -1);
                    java.awt.image.IndexColorModel icm = (java.awt.image.IndexColorModel) indexed.getColorModel();
                    byte[] r = new byte[256];
                    icm.getReds(r);
                    ImageTypeSpecifier idx = ImageTypeSpecifier.createIndexed(r, r, r, null, 8, DataBuffer.TYPE_BYTE);
                    ImageTypeSpecifier inter = ImageTypeSpecifier.createInterleaved(ColorSpace.getInstance(
                            ColorSpace.CS_sRGB), new int[] { 0, 1, 2 }, DataBuffer.TYPE_BYTE, false, false);
                    return gray.getBitsPerBand(0) + ", " + idx.getBitsPerBand(0) + ", " + inter.getNumBands();
                }));
    }

    private static String types(String format, String separator) {
        return String.join(separator, ImageIO.getImageWritersByFormatName(format).next().getDefaultWriteParam()
                .getCompressionTypes());
    }

    private static String writerCaps(String format) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        boolean insert;
        try {
            writer.setOutput(ImageIO.createImageOutputStream(new ByteArrayOutputStream()));
            insert = writer.canInsertImage(0);
        } catch (Exception e) {
            insert = false;
        }
        return writer.canWriteSequence() + " " + writer.canWriteRasters() + " " + insert;
    }
}
