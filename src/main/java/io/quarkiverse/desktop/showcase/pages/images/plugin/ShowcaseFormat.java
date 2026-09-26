package io.quarkiverse.desktop.showcase.pages.images.plugin;

import java.nio.charset.StandardCharsets;

/**
 * The "Showcase raw image" format of the custom ImageIO plugin (reader {@link ShowcaseImageReader}, writer
 * {@link ShowcaseImageWriter}, registered through {@code META-INF/services/javax.imageio.spi.ImageReaderSpi} and
 * {@code ImageWriterSpi}) : a run-length encoded ARGB image with a comment.
 * <p>
 * Layout (big endian) : magic {@code SHOWCASE}, int version (1), int width, int height, byte alpha (0 or 1), unsigned
 * short comment length + UTF-8 comment, then for every row runs of {@code (unsigned byte count - 1, int argb)}.
 */
public final class ShowcaseFormat {

    public static final String NAME = "showcase";
    public static final String SUFFIX = "sraw";
    public static final String MIME_TYPE = "image/x-showcase-raw";
    public static final String VENDOR = "Quarkus Desktop Showcase";
    public static final String VERSION = "1.0";
    public static final String NATIVE_METADATA_FORMAT = "io_quarkiverse_desktop_showcase_raw_1.0";
    /** Base name of the resource bundle of the metadata format descriptions (a properties file). */
    public static final String RESOURCES = "showcase.text-images.ShowcaseMetadataFormatResources";

    static final int VERSION_1 = 1;

    private ShowcaseFormat() {
    }

    static byte[] magic() {
        return "SHOWCASE".getBytes(StandardCharsets.US_ASCII);
    }
}
