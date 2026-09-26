package io.quarkiverse.desktop.showcase.pages.images;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.event.IIOReadUpdateListener;
import javax.imageio.event.IIOWriteProgressListener;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormat;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.ExifParentTIFFTagSet;
import javax.imageio.plugins.tiff.ExifTIFFTagSet;
import javax.imageio.plugins.tiff.FaxTIFFTagSet;
import javax.imageio.plugins.tiff.GeoTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.plugins.tiff.TIFFImageReadParam;
import javax.imageio.plugins.tiff.TIFFTag;
import javax.imageio.plugins.tiff.TIFFTagSet;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import jakarta.inject.Singleton;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.images.ImagesSupport.Tile;
import io.quarkiverse.desktop.showcase.pages.images.plugin.ShowcaseFormat;
import io.quarkiverse.desktop.showcase.pages.images.plugin.ShowcaseImageReaderSpi;
import io.quarkiverse.desktop.showcase.pages.images.plugin.ShowcaseImageWriterSpi;
import io.quarkiverse.desktop.showcase.pages.images.plugin.ShowcaseMetadata;

/**
 * ImageIO metadata : native and standard metadata trees of PNG (text chunks, physical size, time, gamma, background),
 * JPEG (JFIF, comment and application markers, thumbnails), GIF (an animated GIF written frame by frame with graphic
 * control, application and comment extensions, read back and composited), TIFF (a directory with baseline, private and
 * Exif fields, tag sets), BMP and WBMP ; the metadata format descriptions (resource bundles), the read / write progress,
 * update and warning listeners, and a custom ImageIO plugin (reader, writer, metadata and metadata format) registered
 * through {@code META-INF/services}.
 * <p>
 * Capture method C (frames of the animated GIF, metadata tree dumps). Computed in the background. Native risks : the
 * metadata format classes loaded with {@code Class.forName} + {@code getInstance()}, the TIFF tag sets loaded the same
 * way, the plugin resource bundles (descriptions and warnings), JPEG warnings called back from native code, the
 * service loader lookup of the application plugin, the application resource bundle of the plugin's format.
 */
@Singleton
public class ImageIoMetadataPage implements FeaturePage {

    private static final int SCREEN_W = 120;
    private static final int SCREEN_H = 60;
    private static final int[] DELAYS = { 20, 30, 40, 50, 60 };
    private static final String[] DISPOSALS = { "none", "doNotDispose", "doNotDispose", "restoreToBackgroundColor",
            "restoreToPrevious" };

    /** How the plugin was found the first time in this process (the registry is global). */
    private static String pluginDiscovery;

    private Container holder;

    @Override
    public String id() {
        return "images-imageio-metadata";
    }

    @Override
    public String title() {
        return "ImageIO metadata and plugins";
    }

    @Override
    public String category() {
        return Categories.IMAGES;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Component build() {
        holder = Ui.column(12, Ui.text("Reading and writing metadata..."));
        return Ui.column(12,
                Ui.text("Native and standard metadata trees of every standard format, an animated GIF written frame by "
                        + "frame and composited back, TIFF directories with Exif and private fields, listeners, and a "
                        + "custom ImageIO plugin found through META-INF/services.", 1000),
                holder);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Container target = holder;
        return Edt.background(ImageIoMetadataPage::compute).thenAccept(result -> ImagesSupport.show(target,
                Ui.title("Animated GIF : composited frames (top), raw frames with their graphic control extension (bottom)"),
                Ui.image(result.animation()),
                ChecksView.table("GIF", result.gif()),
                Ui.title("Metadata trees"),
                Ui.text(result.pngTree(), new Font(Font.MONOSPACED, Font.PLAIN, 11), ImagesSupport.INK, 1000),
                Ui.text(result.jpegTree(), new Font(Font.MONOSPACED, Font.PLAIN, 11), ImagesSupport.INK, 1000),
                ChecksView.table("PNG", result.png()),
                ChecksView.table("JPEG", result.jpeg()),
                ChecksView.table("TIFF", result.tiff()),
                ChecksView.table("BMP, WBMP, format descriptions", result.formats()),
                ChecksView.table("Listeners and warnings", result.listeners()),
                Ui.title("Custom plugin (\"showcase\" format)"),
                Ui.image(ImagesSupport.grid(result.pluginTiles(), 8, 125, 108)),
                ChecksView.table("Custom plugin", result.plugin())));
    }

    @Override
    public void dispose(Component content) {
        holder = null;
    }

    private record Result(BufferedImage animation, String pngTree, String jpegTree, List<Check> gif, List<Check> png,
            List<Check> jpeg, List<Check> tiff, List<Check> formats, List<Check> listeners, List<Tile> pluginTiles,
            List<Check> plugin) {
    }

    private static Result compute() {
        List<Check> gif = new ArrayList<>();
        BufferedImage[] animation = new BufferedImage[1];
        ImagesSupport.section(gif, "animated GIF", checks -> animation[0] = gif(checks));
        if (animation[0] == null) {
            animation[0] = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        List<Check> png = new ArrayList<>();
        String[] pngTree = { "" };
        ImagesSupport.section(png, "PNG metadata", checks -> pngTree[0] = png(checks));
        List<Check> jpeg = new ArrayList<>();
        String[] jpegTree = { "" };
        ImagesSupport.section(jpeg, "JPEG metadata", checks -> jpegTree[0] = jpeg(checks));
        List<Check> tiff = new ArrayList<>();
        ImagesSupport.section(tiff, "TIFF metadata", ImageIoMetadataPage::tiff);
        List<Check> formats = new ArrayList<>();
        ImagesSupport.section(formats, "BMP and WBMP metadata", ImageIoMetadataPage::bmp);
        ImagesSupport.section(formats, "metadata formats", ImageIoMetadataPage::descriptions);
        List<Check> listeners = new ArrayList<>();
        ImagesSupport.section(listeners, "listeners", ImageIoMetadataPage::listeners);
        ImagesSupport.section(listeners, "warnings", ImageIoMetadataPage::warnings);
        List<Check> plugin = new ArrayList<>();
        List<Tile> pluginTiles = new ArrayList<>();
        ImagesSupport.section(plugin, "custom plugin", checks -> plugin(checks, pluginTiles));
        return new Result(animation[0], pngTree[0], jpegTree[0], gif, png, jpeg, tiff, formats, listeners, pluginTiles,
                plugin);
    }

    // ------------------------------------------------------------------------------------------------ tree dumps

    /**
     * An indented text dump of a metadata tree : element names, attributes sorted by name, user object sizes.
     */
    static String dump(Node node) {
        StringBuilder sb = new StringBuilder();
        dump(node, 0, sb);
        return sb.toString().stripTrailing();
    }

    private static void dump(Node node, int depth, StringBuilder sb) {
        sb.append("  ".repeat(depth)).append(node.getNodeName());
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            Map<String, String> sorted = new TreeMap<>();
            for (int i = 0; i < attributes.getLength(); i++) {
                sorted.put(attributes.item(i).getNodeName(), attributes.item(i).getNodeValue());
            }
            sorted.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"'));
        }
        if (node instanceof IIOMetadataNode n && n.getUserObject() instanceof byte[] bytes) {
            sb.append(" [").append(bytes.length).append(" bytes]");
        }
        sb.append('\n');
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            dump(child, depth + 1, sb);
        }
    }

    private static IIOMetadataNode child(Node parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals(name)) {
                return (IIOMetadataNode) child;
            }
        }
        return null;
    }

    private static IIOMetadataNode path(Node root, String... names) {
        Node node = root;
        for (String name : names) {
            node = node == null ? null : child(node, name);
        }
        return (IIOMetadataNode) node;
    }

    private static String attr(Node root, String attribute, String... path) {
        IIOMetadataNode node = path(root, path);
        return node == null ? "absent" : node.getAttribute(attribute);
    }

    /**
     * The node value of an element (BMP and WBMP trees store their values as node values).
     */
    private static String value(Node root, String... path) {
        IIOMetadataNode node = path(root, path);
        return node == null ? "absent" : node.getNodeValue();
    }

    private static IIOMetadataNode node(String name, String... attributes) {
        IIOMetadataNode node = new IIOMetadataNode(name);
        for (int i = 0; i < attributes.length; i += 2) {
            node.setAttribute(attributes[i], attributes[i + 1]);
        }
        return node;
    }

    private static IIOMetadataNode with(IIOMetadataNode parent, IIOMetadataNode... children) {
        for (IIOMetadataNode child : children) {
            parent.appendChild(child);
        }
        return parent;
    }

    private static byte[] write(ImageWriter writer, IIOImage image, ImageWriteParam param) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, image, param);
        }
        return bytes.toByteArray();
    }

    private static ImageReader reader(byte[] bytes, String format) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName(format).next();
        reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)));
        return reader;
    }

    // ------------------------------------------------------------------------------------------------------ GIF

    private static IndexColorModel spritePalette() {
        int size = 16;
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        int[] colors = { 0x000000, 0xFFFFFF, 0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x1E88E5, 0x8E24AA, 0x263238,
                0xB3E5FC, 0x81D4FA, 0x4FC3F7, 0x29B6F6, 0x03A9F4, 0x039BE5, 0x0288D1 };
        for (int i = 0; i < size; i++) {
            r[i] = (byte) (colors[i] >> 16);
            g[i] = (byte) (colors[i] >> 8);
            b[i] = (byte) colors[i];
        }
        // index 0 is transparent
        return new IndexColorModel(8, size, r, g, b, 0);
    }

    /**
     * Frame 0 : the sky background (indices 9-15 in bands). Frames 1-4 : a 24 x 24 sprite (a colored disc with a
     * transparent surrounding) at increasing x.
     */
    private static BufferedImage frame(int index) {
        IndexColorModel palette = spritePalette();
        if (index == 0) {
            BufferedImage image = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_BYTE_INDEXED, palette);
            for (int y = 0; y < SCREEN_H; y++) {
                for (int x = 0; x < SCREEN_W; x++) {
                    image.getRaster().setSample(x, y, 0, y > 48 ? 5 : 9 + y * 7 / 49);
                }
            }
            return image;
        }
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_BYTE_INDEXED, palette);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int dx = x - 12;
                int dy = y - 12;
                int d = dx * dx + dy * dy;
                image.getRaster().setSample(x, y, 0, d <= 36 ? 1 : d <= 110 ? 1 + index : 0);
            }
        }
        return image;
    }

    private static BufferedImage gif(List<Check> checks) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        IIOMetadata stream = writer.getDefaultStreamMetadata(param);
        IIOMetadataNode streamRoot = (IIOMetadataNode) stream.getAsTree("javax_imageio_gif_stream_1.0");
        IIOMetadataNode screen = path(streamRoot, "LogicalScreenDescriptor");
        screen.setAttribute("logicalScreenWidth", String.valueOf(SCREEN_W));
        screen.setAttribute("logicalScreenHeight", String.valueOf(SCREEN_H));
        // the default global color table is removed : the writer takes the palette of the first frame
        IIOMetadataNode global = path(streamRoot, "GlobalColorTable");
        if (global != null) {
            streamRoot.removeChild(global);
        }
        stream.setFromTree("javax_imageio_gif_stream_1.0", streamRoot);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(stream);
            for (int i = 0; i < 5; i++) {
                BufferedImage frame = frame(i);
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),
                        param);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = new IIOMetadataNode(format);
                root.appendChild(node("ImageDescriptor", "imageLeftPosition", String.valueOf(i == 0 ? 0 : 8 + (i - 1) * 26),
                        "imageTopPosition", String.valueOf(i == 0 ? 0 : 14 + (i % 2) * 8), "imageWidth",
                        String.valueOf(frame.getWidth()), "imageHeight", String.valueOf(frame.getHeight()),
                        "interlaceFlag", "FALSE"));
                root.appendChild(node("GraphicControlExtension", "disposalMethod", DISPOSALS[i], "userInputFlag", "FALSE",
                        "transparentColorFlag", i == 0 ? "FALSE" : "TRUE", "delayTime", String.valueOf(DELAYS[i]),
                        "transparentColorIndex", "0"));
                if (i == 0) {
                    IIOMetadataNode application = node("ApplicationExtension", "applicationID", "NETSCAPE",
                            "authenticationCode", "2.0");
                    application.setUserObject(new byte[] { 1, 3, 0 });
                    root.appendChild(with(new IIOMetadataNode("ApplicationExtensions"), application));
                    root.appendChild(with(new IIOMetadataNode("CommentExtensions"),
                            node("CommentExtension", "value", "Quarkus Desktop showcase animation")));
                }
                metadata.mergeTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        byte[] data = bytes.toByteArray();
        checks.add(Check.info("animated GIF : size, SHA-256", data.length + " B, " + Checks.sha256(data)));

        ImageReader reader = reader(data, "gif");
        int count = reader.getNumImages(true);
        checks.add(Checks.expect("frames", 5, () -> count));
        IIOMetadata streamRead = reader.getStreamMetadata();
        Node streamTree = streamRead.getAsTree("javax_imageio_gif_stream_1.0");
        checks.add(Checks.expect("stream : version, logical screen, global color table", "89a, 120x60, 16",
                () -> attr(streamTree, "value", "Version") + ", " + attr(streamTree, "logicalScreenWidth",
                        "LogicalScreenDescriptor") + "x" + attr(streamTree, "logicalScreenHeight", "LogicalScreenDescriptor")
                        + ", " + attr(streamTree, "sizeOfGlobalColorTable", "GlobalColorTable")));
        List<String> delays = new ArrayList<>();
        List<String> disposals = new ArrayList<>();
        List<String> positions = new ArrayList<>();
        List<BufferedImage> raw = new ArrayList<>();
        List<BufferedImage> composited = new ArrayList<>();
        BufferedImage canvas = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_INT_ARGB);
        String comment = "absent";
        String loops = "absent";
        for (int i = 0; i < count; i++) {
            BufferedImage frame = reader.read(i);
            raw.add(frame);
            Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
            String disposal = attr(tree, "disposalMethod", "GraphicControlExtension");
            int left = Integer.parseInt(attr(tree, "imageLeftPosition", "ImageDescriptor"));
            int top = Integer.parseInt(attr(tree, "imageTopPosition", "ImageDescriptor"));
            delays.add(attr(tree, "delayTime", "GraphicControlExtension"));
            disposals.add(disposal);
            positions.add(left + "," + top);
            if (i == 0) {
                comment = attr(tree, "value", "CommentExtensions", "CommentExtension");
                IIOMetadataNode app = path(tree, "ApplicationExtensions", "ApplicationExtension");
                if (app != null && app.getUserObject() instanceof byte[] b && b.length >= 3) {
                    loops = app.getAttribute("applicationID") + app.getAttribute("authenticationCode") + " loops "
                            + ((b[1] & 0xFF) | (b[2] & 0xFF) << 8);
                }
            }
            // compositing with the disposal methods
            BufferedImage previous = copy(canvas);
            Graphics2D g = canvas.createGraphics();
            g.drawImage(frame, left, top, null);
            g.dispose();
            composited.add(copy(canvas));
            if (disposal.equals("restoreToBackgroundColor")) {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(java.awt.AlphaComposite.Clear);
                clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
                clear.dispose();
            } else if (disposal.equals("restoreToPrevious")) {
                canvas = previous;
            }
        }
        reader.dispose();
        checks.add(Checks.expect("delay times (1/100 s)", "20 30 40 50 60", () -> String.join(" ", delays)));
        checks.add(Checks.expect("disposal methods", String.join(" ", DISPOSALS), () -> String.join(" ", disposals)));
        checks.add(Checks.expect("frame positions", "0,0 8,22 34,14 60,22 86,14", () -> String.join(" ", positions)));
        String c = comment;
        checks.add(Checks.expect("comment extension", "Quarkus Desktop showcase animation", () -> c));
        String l = loops;
        checks.add(Checks.expect("NETSCAPE2.0 application extension", "NETSCAPE2.0 loops 3", () -> l));
        checks.add(Checks.expect("frame 1 : size, transparent pixel at (0, 0), color model", "24x24, 0, IndexColorModel",
                () -> ImagesSupport.size(raw.get(1)) + ", " + (raw.get(1).getRGB(0, 0) >>> 24) + ", "
                        + raw.get(1).getColorModel().getClass().getSimpleName()));
        checks.add(Checks.expect("composited frames : sprite pixels at the sprite centers of frames 1-4",
                Checks.argb(0xFFFFFFFF) + " x4", () -> {
                    TreeSet<String> set = new TreeSet<>();
                    for (int i = 1; i < 5; i++) {
                        String[] p = positions.get(i).split(",");
                        set.add(Checks.argb(composited.get(i).getRGB(Integer.parseInt(p[0]) + 12, Integer.parseInt(p[1])
                                + 12)));
                    }
                    return String.join(" ", set) + " x4";
                }));
        checks.add(Checks.expect("after restoreToBackgroundColor (frame 3) : frame 4 shows the sky at frame 3's sprite",
                "transparent", () -> (composited.get(4).getRGB(60 + 12, 22 + 12) >>> 24) == 0 ? "transparent" : "painted"));
        checks.add(Checks.info("composited frames SHA-256", () -> String.join(" ", composited.stream().map(Checks::sha256)
                .toList())));
        checks.add(Checks.expect("ImageIO.read (first frame only)", "120x60", () -> ImagesSupport.size(ImageIO.read(
                new ByteArrayInputStream(data)))));

        int gap = 18;
        int stripW = 5 * (SCREEN_W + gap);
        return Snapshots.offscreen(Math.max(stripW, 1), 2 * SCREEN_H + 64, g -> {
            ImagesSupport.labelHints(g);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
            for (int i = 0; i < composited.size(); i++) {
                int x = i * (SCREEN_W + gap);
                ImagesSupport.checker(g, x, 0, SCREEN_W, SCREEN_H);
                g.drawImage(composited.get(i), x, 0, null);
                g.setColor(new Color(ImagesSupport.RULE));
                g.drawRect(x, 0, SCREEN_W - 1, SCREEN_H - 1);
                BufferedImage frame = raw.get(i);
                ImagesSupport.checker(g, x, SCREEN_H + 8, frame.getWidth(), frame.getHeight());
                g.drawImage(frame, x, SCREEN_H + 8, null);
                g.setColor(new Color(ImagesSupport.MUTED));
                g.drawString("#" + i + "  " + delays.get(i) + "/100 s  (" + positions.get(i) + ")", x, 2 * SCREEN_H + 24);
                g.drawString(disposals.get(i).replace("Color", ""), x, 2 * SCREEN_H + 38);
            }
        });
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    // ------------------------------------------------------------------------------------------------------ PNG

    private static String png(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.pattern(96, 64, BufferedImage.TYPE_INT_RGB);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
        String format = "javax_imageio_png_1.0";
        IIOMetadataNode root = new IIOMetadataNode(format);
        root.appendChild(with(new IIOMetadataNode("bKGD"), node("bKGD_RGB", "red", "255", "green", "240", "blue", "200")));
        root.appendChild(node("gAMA", "value", "45455"));
        root.appendChild(node("pHYs", "pixelsPerUnitXAxis", "2835", "pixelsPerUnitYAxis", "5670", "unitSpecifier", "meter"));
        root.appendChild(node("tIME", "year", "2026", "month", "1", "day", "2", "hour", "3", "minute", "4", "second", "5"));
        root.appendChild(with(new IIOMetadataNode("tEXt"), node("tEXtEntry", "keyword", "Title", "value",
                "Quarkus Desktop Showcase"), node("tEXtEntry", "keyword", "Author", "value", "ImageIO")));
        root.appendChild(with(new IIOMetadataNode("iTXt"), node("iTXtEntry", "keyword", "Description", "compressionFlag",
                "TRUE", "compressionMethod", "0", "languageTag", "ar", "translatedKeyword", "وصف", "text",
                "صورة اختبار للمعرض")));
        root.appendChild(with(new IIOMetadataNode("zTXt"), node("zTXtEntry", "keyword", "Comment", "compressionMethod",
                "deflate", "text", "A compressed comment, ".repeat(4).trim())));
        metadata.mergeTree(format, root);
        byte[] data = write(writer, new IIOImage(image, null, metadata), param);
        writer.dispose();

        ImageReader reader = reader(data, "png");
        IIOMetadata read = reader.getImageMetadata(0);
        Node tree = read.getAsTree(format);
        Node standard = read.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
        checks.add(Checks.expect("image pixels unchanged by the metadata", "exact",
                () -> ImagesSupport.compare(image, reader.read(0))));
        checks.add(Checks.expect("metadata formats : native, standard supported, extra", "javax_imageio_png_1.0, true, "
                + "none",
                () -> read.getNativeMetadataFormatName() + ", " + read.isStandardMetadataFormatSupported() + ", "
                        + (read.getExtraMetadataFormatNames() == null ? "none"
                                : String.join(" ",
                                        read.getExtraMetadataFormatNames()))));
        checks.add(Checks.expect("IHDR : width, height, bitDepth, colorType, interlace", "96 64 8 RGB none", () -> String.join(
                " ", attr(tree, "width", "IHDR"), attr(tree, "height", "IHDR"), attr(tree, "bitDepth", "IHDR"),
                attr(tree, "colorType", "IHDR"), attr(tree, "interlaceMethod", "IHDR"))));
        checks.add(Checks.expect("tEXt entries", "Title=Quarkus Desktop Showcase, Author=ImageIO", () -> {
            List<String> entries = new ArrayList<>();
            for (Node e = path(tree, "tEXt").getFirstChild(); e != null; e = e.getNextSibling()) {
                entries.add(((IIOMetadataNode) e).getAttribute("keyword") + "=" + ((IIOMetadataNode) e).getAttribute(
                        "value"));
            }
            return String.join(", ", entries);
        }));
        checks.add(Checks.expect("iTXt (compressed, UTF-8) : language, translated keyword, text", "ar, وصف, صورة "
                + "اختبار للمعرض",
                () -> attr(tree, "languageTag", "iTXt", "iTXtEntry") + ", " + attr(tree,
                        "translatedKeyword", "iTXt", "iTXtEntry") + ", " + attr(tree, "text", "iTXt", "iTXtEntry")));
        checks.add(Checks.expect("zTXt (deflate) text length", 87, () -> attr(tree, "text", "zTXt", "zTXtEntry").length()));
        checks.add(
                Checks.expect("pHYs, tIME, gAMA, bKGD", "2835x5670 meter, 2026-1-2 3:4:5, 45455, 255 240 200",
                        () -> attr(tree, "pixelsPerUnitXAxis", "pHYs") + "x" + attr(tree, "pixelsPerUnitYAxis", "pHYs") + " "
                                + attr(tree,
                                        "unitSpecifier", "pHYs")
                                + ", " + attr(tree, "year", "tIME") + "-" + attr(tree, "month", "tIME")
                                + "-" + attr(tree, "day", "tIME") + " " + attr(tree, "hour", "tIME") + ":"
                                + attr(tree, "minute",
                                        "tIME")
                                + ":" + attr(tree, "second", "tIME") + ", " + attr(tree, "value", "gAMA") + ", "
                                + attr(tree, "red", "bKGD", "bKGD_RGB") + " " + attr(tree, "green", "bKGD", "bKGD_RGB") + " "
                                + attr(tree, "blue", "bKGD", "bKGD_RGB")));
        checks.add(Checks.expect("standard tree : pixel size (mm), gamma, text entries, modification time",
                "0.35273367 0.17636684, 0.45455, 4, 2026 1 2", () -> attr(standard, "value", "Dimension",
                        "HorizontalPixelSize") + " " + attr(standard, "value", "Dimension", "VerticalPixelSize") + ", "
                        + attr(standard, "value", "Chroma", "Gamma") + ", " + path(standard, "Text").getLength() + ", "
                        + attr(standard, "year", "Document", "ImageModificationTime") + " " + attr(standard, "month",
                                "Document", "ImageModificationTime")
                        + " " + attr(standard, "day", "Document",
                                "ImageModificationTime")));
        checks.add(Checks.info("native tree SHA-256 / standard tree SHA-256", () -> Checks.sha256(dump(tree)) + " / "
                + Checks.sha256(dump(standard))));
        checks.add(Checks.expect("setFromTree(standard tree) : native tEXt entries, gAMA, pHYs", "2, 45455, 353", () -> {
            IIOMetadata copy = ImageIO.getImageWritersByFormatName("png").next().getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(image), null);
            copy.setFromTree(IIOMetadataFormatImpl.standardMetadataFormatName, standard);
            Node copied = copy.getAsTree(format);
            IIOMetadataNode text = path(copied, "tEXt");
            return (text == null ? 0 : text.getLength()) + ", " + attr(copied, "value", "gAMA") + ", "
                    + attr(copied, "pixelsPerUnitXAxis", "pHYs");
        }));
        reader.dispose();
        return "PNG native tree (read back)\n" + dump(tree);
    }

    // ----------------------------------------------------------------------------------------------------- JPEG

    private static String jpeg(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.photo(96, 64);
        BufferedImage thumbnail = ImagesSupport.photo(32, 21);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
        String format = "javax_imageio_jpeg_image_1.0";
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode jfif = path(root, "JPEGvariety", "app0JFIF");
        jfif.setAttribute("resUnits", "1");
        jfif.setAttribute("Xdensity", "150");
        jfif.setAttribute("Ydensity", "150");
        IIOMetadataNode markers = path(root, "markerSequence");
        markers.appendChild(node("com", "comment", "Quarkus Desktop showcase JPEG comment"));
        IIOMetadataNode app = node("unknown", "MarkerTag", "226");
        app.setUserObject("SHOWCASE application marker".getBytes(StandardCharsets.US_ASCII));
        markers.appendChild(app);
        metadata.setFromTree(format, root);
        byte[] data = write(writer, new IIOImage(image, List.of(thumbnail), metadata), param);
        writer.dispose();

        ImageReader reader = reader(data, "jpeg");
        IIOMetadata read = reader.getImageMetadata(0);
        Node tree = read.getAsTree(format);
        Node standard = read.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
        checks.add(Checks.expect("stream metadata (not an abbreviated stream)", "null",
                () -> String.valueOf(reader.getStreamMetadata())));
        checks.add(Checks.expect("app0JFIF : version, units, density", "1.2, 1, 150x150", () -> attr(tree, "majorVersion",
                "JPEGvariety", "app0JFIF") + "." + attr(tree, "minorVersion", "JPEGvariety", "app0JFIF") + ", "
                + attr(tree,
                        "resUnits", "JPEGvariety", "app0JFIF")
                + ", " + attr(tree, "Xdensity", "JPEGvariety", "app0JFIF")
                + "x" + attr(tree, "Ydensity", "JPEGvariety", "app0JFIF")));
        checks.add(Checks.expect("marker sequence", "com unknown dqt dqt sof dht dht dht dht sos", () -> {
            List<String> names = new ArrayList<>();
            for (Node m = path(tree, "markerSequence").getFirstChild(); m != null; m = m.getNextSibling()) {
                names.add(m.getNodeName());
            }
            return String.join(" ", names);
        }));
        checks.add(Checks.expect("comment marker, unknown APP2 marker", "Quarkus Desktop showcase JPEG comment, 226 : "
                + "SHOWCASE application marker",
                () -> attr(tree, "comment", "markerSequence", "com") + ", " + attr(tree,
                        "MarkerTag", "markerSequence", "unknown") + " : "
                        + new String((byte[]) path(tree, "markerSequence",
                                "unknown").getUserObject(), StandardCharsets.US_ASCII)));
        checks.add(Checks.expect("sof : process, components, sampling of Y", "0, 3, 2x2", () -> attr(tree, "process",
                "markerSequence", "sof") + ", " + attr(tree, "numFrameComponents", "markerSequence", "sof") + ", "
                + attr(tree, "HsamplingFactor", "markerSequence", "sof", "componentSpec") + "x" + attr(tree,
                        "VsamplingFactor", "markerSequence", "sof", "componentSpec")));
        checks.add(Checks.expect("thumbnails : count, size, readThumbnail", "1, 32x21, 32x21", () -> reader.getNumThumbnails(0)
                + ", " + reader.getThumbnailWidth(0, 0) + "x" + reader.getThumbnailHeight(0, 0) + ", "
                + ImagesSupport.size(reader.readThumbnail(0, 0))));
        checks.add(Checks.expect("thumbnail pixels (uncompressed JFIF RGB thumbnail)", "exact",
                () -> ImagesSupport.compare(thumbnail, reader.readThumbnail(0, 0))));
        checks.add(Checks.expect("standard tree : pixel size (mm), text entry", "0.16933332, Quarkus Desktop showcase JPEG "
                + "comment",
                () -> attr(standard, "value", "Dimension", "HorizontalPixelSize") + ", " + attr(standard,
                        "value", "Text", "TextEntry")));
        checks.add(Checks.info("native tree SHA-256", () -> Checks.sha256(dump(tree))));
        reader.dispose();
        IIOMetadataNode shown = (IIOMetadataNode) tree.cloneNode(true);
        // the quantization and Huffman tables are long : only their presence is shown
        for (Node m = path(shown, "markerSequence").getFirstChild(); m != null; m = m.getNextSibling()) {
            while (m.getFirstChild() != null && (m.getNodeName().equals("dqt") || m.getNodeName().equals("dht"))) {
                m.removeChild(m.getFirstChild());
            }
        }
        return "JPEG native tree (read back, tables elided)\n" + dump(shown);
    }

    // ----------------------------------------------------------------------------------------------------- TIFF

    private static void tiff(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.pattern(96, 64, BufferedImage.TYPE_INT_RGB);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
        TIFFDirectory dir = TIFFDirectory.createFromMetadata(metadata);
        BaselineTIFFTagSet base = BaselineTIFFTagSet.getInstance();
        dir.addTIFFField(new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_ARTIST), TIFFTag.TIFF_ASCII, 1,
                new String[] { "Quarkus Desktop Showcase" }));
        dir.addTIFFField(new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_SOFTWARE), TIFFTag.TIFF_ASCII, 1,
                new String[] { "javax.imageio TIFF" }));
        dir.addTIFFField(new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_DATE_TIME), TIFFTag.TIFF_ASCII, 1,
                new String[] { "2026:01:02 03:04:05" }));
        dir.addTIFFField(new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_IMAGE_DESCRIPTION), TIFFTag.TIFF_ASCII, 1,
                new String[] { "Test pattern with metadata" }));
        dir.addTIFFField(new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_X_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1,
                new long[][] { { 300, 1 } }));
        dir.addTIFFField(new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_Y_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1,
                new long[][] { { 600, 2 } }));
        dir.addTIFFField(new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT), TIFFTag.TIFF_SHORT, 1,
                new char[] { BaselineTIFFTagSet.RESOLUTION_UNIT_INCH }));
        TIFFTag privateTag = new TIFFTag("ShowcasePrivate", 65000, 1 << TIFFTag.TIFF_ASCII);
        dir.addTIFFField(new TIFFField(privateTag, TIFFTag.TIFF_ASCII, 1, new String[] { "private tag value" }));
        ExifTIFFTagSet exifTags = ExifTIFFTagSet.getInstance();
        TIFFTag exifPointer = ExifParentTIFFTagSet.getInstance().getTag(ExifParentTIFFTagSet.TAG_EXIF_IFD_POINTER);
        TIFFDirectory exif = new TIFFDirectory(new TIFFTagSet[] { exifTags }, exifPointer);
        exif.addTIFFField(new TIFFField(exifTags.getTag(ExifTIFFTagSet.TAG_EXIF_VERSION), TIFFTag.TIFF_UNDEFINED, 4,
                "0230".getBytes(StandardCharsets.US_ASCII)));
        exif.addTIFFField(new TIFFField(exifTags.getTag(ExifTIFFTagSet.TAG_DATE_TIME_ORIGINAL), TIFFTag.TIFF_ASCII, 1,
                new String[] { "2026:01:02 03:04:05" }));
        dir.addTagSet(ExifParentTIFFTagSet.getInstance());
        // the offset is computed by the writer, the constructor only requires a positive value
        dir.addTIFFField(new TIFFField(exifPointer, TIFFTag.TIFF_LONG, 1L, exif));
        byte[] data = write(writer, new IIOImage(image, null, dir.getAsMetadata()), param);
        writer.dispose();

        ImageReader reader = reader(data, "tiff");
        // unknown (private) tags are only kept with TIFFImageReadParam.setReadUnknownTags(true) : read with it first,
        // the metadata is read with the parameter of the first read
        TIFFImageReadParam readParam = new TIFFImageReadParam();
        readParam.setReadUnknownTags(true);
        BufferedImage pixels = reader.read(0, readParam);
        IIOMetadata read = reader.getImageMetadata(0);
        TIFFDirectory readDir = TIFFDirectory.createFromMetadata(read);
        checks.add(Checks.expect("image pixels", "exact", () -> ImagesSupport.compare(image, pixels)));
        checks.add(Checks.expect("TIFFImageReadParam : allowed tag sets", "BaselineTIFFTagSet ExifParentTIFFTagSet "
                + "FaxTIFFTagSet GeoTIFFTagSet",
                () -> String.join(" ", readParam.getAllowedTagSets().stream()
                        .map(t -> t.getClass().getSimpleName()).sorted().toList())));
        checks.add(Checks.expect("Artist, Software, DateTime, ImageDescription", "Quarkus Desktop Showcase | "
                + "javax.imageio TIFF | 2026:01:02 03:04:05 | Test pattern with metadata",
                () -> String.join(" | ",
                        readDir.getTIFFField(BaselineTIFFTagSet.TAG_ARTIST).getAsString(0),
                        readDir.getTIFFField(BaselineTIFFTagSet.TAG_SOFTWARE).getAsString(0),
                        readDir.getTIFFField(BaselineTIFFTagSet.TAG_DATE_TIME).getAsString(0),
                        readDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_DESCRIPTION).getAsString(0))));
        checks.add(Checks.expect("XResolution, YResolution (rationals), ResolutionUnit", "300/1, 300/1, 2", () -> {
            long[] x = readDir.getTIFFField(BaselineTIFFTagSet.TAG_X_RESOLUTION).getAsRational(0);
            long[] y = readDir.getTIFFField(BaselineTIFFTagSet.TAG_Y_RESOLUTION).getAsRational(0);
            return x[0] + "/" + x[1] + ", " + y[0] + "/" + y[1] + ", "
                    + readDir.getTIFFField(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT).getAsInt(0);
        }));
        checks.add(Checks.expect("private tag 65000 (unknown to the reader)", "private tag value, 1 value",
                () -> readDir.getTIFFField(65000).getAsString(0) + ", " + readDir.getTIFFField(65000).getCount() + " value"));
        checks.add(Checks.expect("Exif IFD : version, DateTimeOriginal", "0230, 2026:01:02 03:04:05", () -> {
            TIFFField pointer = readDir.getTIFFField(ExifParentTIFFTagSet.TAG_EXIF_IFD_POINTER);
            TIFFDirectory sub = (TIFFDirectory) pointer.getDirectory();
            return new String(sub.getTIFFField(ExifTIFFTagSet.TAG_EXIF_VERSION).getAsBytes(), StandardCharsets.US_ASCII)
                    + ", " + sub.getTIFFField(ExifTIFFTagSet.TAG_DATE_TIME_ORIGINAL).getAsString(0);
        }));
        checks.add(Checks.expect("fields of the directory, tag sets",
                "18, BaselineTIFFTagSet ExifParentTIFFTagSet FaxTIFFTagSet GeoTIFFTagSet",
                () -> readDir.getNumTIFFFields() + ", "
                        + String.join(" ", Arrays.stream(readDir.getTagSets()).map(s -> s.getClass().getSimpleName()).sorted()
                                .toList())));
        checks.add(Checks.expect("native tree -> mergeTree into new metadata (tag sets loaded by name) : Artist",
                "Quarkus Desktop Showcase", () -> {
                    String nativeFormat = read.getNativeMetadataFormatName();
                    ImageWriter tiffWriter = ImageIO.getImageWritersByFormatName("tiff").next();
                    IIOMetadata copy = tiffWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),
                            tiffWriter.getDefaultWriteParam());
                    copy.mergeTree(nativeFormat, read.getAsTree(nativeFormat));
                    return TIFFDirectory.createFromMetadata(copy).getTIFFField(BaselineTIFFTagSet.TAG_ARTIST).getAsString(0);
                }));
        checks.add(Checks.expect("tag set lookups : baseline 256, fax 327, GeoTIFF 33550, Exif 36867", "ImageWidth, "
                + "CleanFaxData, ModelPixelScaleTag, DateTimeOriginal",
                () -> BaselineTIFFTagSet.getInstance().getTag(256)
                        .getName() + ", " + FaxTIFFTagSet.getInstance().getTag(FaxTIFFTagSet.TAG_CLEAN_FAX_DATA).getName()
                        + ", "
                        + GeoTIFFTagSet.getInstance().getTag(33550).getName() + ", " + exifTags.getTag(36867).getName()));
        checks.add(Checks.expect("TIFFTag : type, value names of Compression", "Short, LZW", () -> TIFFField.getTypeName(
                TIFFTag.TIFF_SHORT) + ", "
                + base.getTag(BaselineTIFFTagSet.TAG_COMPRESSION).getValueName(
                        BaselineTIFFTagSet.COMPRESSION_LZW)));
        reader.dispose();
    }

    // ------------------------------------------------------------------------------------------------ BMP, WBMP

    private static void bmp(List<Check> checks) throws IOException {
        BufferedImage indexed = ImagesSupport.indexed(96, 64, 8, -1);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("bmp").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType("BI_RLE8");
        byte[] data = write(writer, new IIOImage(indexed, null, null), param);
        writer.dispose();
        Node tree = reader(data, "bmp").getImageMetadata(0).getAsTree("javax_imageio_bmp_1.0");
        checks.add(Checks.expect("BMP (BI_RLE8) : version, size, bits, compression, palette entries", "BMP v. 3.x, 96x64, 8, "
                + "1, 256",
                () -> value(tree, "BMPVersion") + ", " + value(tree, "Width") + "x" + value(tree, "Height")
                        + ", " + value(tree, "BitsPerPixel") + ", " + value(tree, "Compression") + ", "
                        + path(tree, "Palette").getLength()));
        byte[] wbmp = ImageIoFormatsPage.write(ImagesSupport.binary(96, 64), "wbmp", p -> {
        });
        Node wtree = reader(wbmp, "wbmp").getImageMetadata(0).getAsTree("javax_imageio_wbmp_1.0");
        checks.add(Checks.expect("WBMP : type, size", "0, 96x64", () -> value(wtree, "WBMPType") + ", "
                + value(wtree, "Width") + "x" + value(wtree, "Height")));
    }

    /**
     * The metadata format objects (loaded by class name) and their descriptions (resource bundles of the plugins).
     */
    private static void descriptions(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.pattern(8, 8, BufferedImage.TYPE_INT_RGB);
        // descriptions come from the resource bundles of the plugins (null : the bundle has no such key)
        String[][] formats = {
                { "png", "javax_imageio_png_1.0", "PNGMetadataFormat : IHDR = The IHDR chunk, containing the header ; "
                        + "width = The width of the image in pixels" },
                { "jpeg", "javax_imageio_jpeg_image_1.0", "JPEGImageMetadataFormat : app0JFIF = null ; majorVersion = The "
                        + "major JFIF version number" },
                { "gif", "javax_imageio_gif_image_1.0", "GIFImageMetadataFormat : ImageDescriptor = The image descriptor ; "
                        + "imageLeftPosition = The X offset of the image relative to the screen origin" },
                { "bmp", "javax_imageio_bmp_1.0", "BMPMetadataFormat : ImageDescriptor = null ; bmpVersion = null" },
                { "wbmp", "javax_imageio_wbmp_1.0", "WBMPMetadataFormat : ImageDescriptor = null ; WBMPType = null" },
                { "tiff", "javax_imageio_tiff_image_1.0", "TIFFImageMetadataFormat : TIFFIFD = null ; tagSets = null" } };
        for (String[] f : formats) {
            checks.add(Checks.expect(f[0] + " metadata format : class, first element with attributes, descriptions", f[2],
                    () -> {
                        // through the SPI (Class.forName of the format class + getInstance()) : the TIFF metadata
                        // object names a format class that does not exist (javax.imageio.plugins.tiff.
                        // TIFFImageMetadataFormat), so IIOMetadata.getMetadataFormat fails for TIFF on the JVM too
                        ImageReader formatReader = ImageIO.getImageReadersByFormatName(f[0]).next();
                        IIOMetadataFormat format = formatReader.getOriginatingProvider().getImageMetadataFormat(f[1]);
                        String element = firstElementWithAttributes(format, format.getRootName());
                        String attribute = element == null ? null : format.getAttributeNames(element)[0];
                        return format.getClass().getSimpleName() + " : " + element + " = "
                                + (element == null ? "-" : format.getElementDescription(element, Locale.US)) + " ; "
                                + attribute + " = " + (attribute == null ? "-"
                                        : format.getAttributeDescription(element, attribute, Locale.US));
                    }));
        }
        checks.add(Checks.expect("standard format : Chroma description, ColorSpaceType@name enumerations",
                "Chroma (color) information / XYZ Lab Luv YCbCr Yxy YCCK PhotoYCC RGB ", () -> {
                    IIOMetadataFormat standard = IIOMetadataFormatImpl.getStandardFormatInstance();
                    return standard.getElementDescription("Chroma", Locale.US) + " / " + String.join(" ",
                            standard.getAttributeEnumerations("ColorSpaceType", "name")).substring(0, 40);
                }));
        checks.add(Checks.expect("PNG IHDR@colorType enumerations, value type", "Grayscale RGB Palette GrayAlpha RGBAlpha, 16",
                () -> {
                    IIOMetadataFormat png = ImageIO.getImageWritersByFormatName("png").next().getDefaultImageMetadata(
                            ImageTypeSpecifier.createFromRenderedImage(image), null).getMetadataFormat(
                                    "javax_imageio_png_1.0");
                    return String.join(" ", png.getAttributeEnumerations("IHDR", "colorType")) + ", "
                            + png.getAttributeValueType("IHDR", "colorType");
                }));
        checks.add(Checks.expect("GIF stream format and JPEG stream format (writer SPI)",
                "GIFStreamMetadataFormat, JPEGStreamMetadataFormat", () -> {
                    ImageWriter gif = ImageIO.getImageWritersByFormatName("gif").next();
                    ImageWriter jpeg = ImageIO.getImageWritersByFormatName("jpeg").next();
                    return gif.getOriginatingProvider().getStreamMetadataFormat("javax_imageio_gif_stream_1.0").getClass()
                            .getSimpleName() + ", "
                            + jpeg.getOriginatingProvider().getStreamMetadataFormat(
                                    "javax_imageio_jpeg_stream_1.0").getClass().getSimpleName();
                }));
    }

    /**
     * The first element (depth first) of {@code format} having attributes.
     */
    private static String firstElementWithAttributes(IIOMetadataFormat format, String element) {
        String[] attributes = format.getAttributeNames(element);
        if (attributes != null && attributes.length > 0) {
            return element;
        }
        String[] children = format.getChildNames(element);
        if (children != null) {
            for (String child : children) {
                String found = firstElementWithAttributes(format, child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ listeners

    private static void listeners(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.pattern(96, 64, BufferedImage.TYPE_INT_RGB);
        byte[] interlaced = ImageIoFormatsPage.write(image, "png", p -> p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT));
        ImageReader reader = reader(interlaced, "png");
        List<String> events = new ArrayList<>();
        int[] progress = new int[1];
        int[] updates = new int[1];
        reader.addIIOReadProgressListener(new IIOReadProgressListener() {
            @Override
            public void sequenceStarted(ImageReader source, int minIndex) {
                events.add("sequenceStarted");
            }

            @Override
            public void sequenceComplete(ImageReader source) {
                events.add("sequenceComplete");
            }

            @Override
            public void imageStarted(ImageReader source, int imageIndex) {
                events.add("imageStarted(" + imageIndex + ")");
            }

            @Override
            public void imageProgress(ImageReader source, float percentageDone) {
                progress[0]++;
            }

            @Override
            public void imageComplete(ImageReader source) {
                events.add("imageComplete");
            }

            @Override
            public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
                events.add("thumbnailStarted");
            }

            @Override
            public void thumbnailProgress(ImageReader source, float percentageDone) {
            }

            @Override
            public void thumbnailComplete(ImageReader source) {
                events.add("thumbnailComplete");
            }

            @Override
            public void readAborted(ImageReader source) {
                events.add("readAborted");
            }
        });
        List<String> passes = new ArrayList<>();
        reader.addIIOReadUpdateListener(new IIOReadUpdateListener() {
            @Override
            public void passStarted(ImageReader source, BufferedImage theImage, int pass, int minPass, int maxPass,
                    int minX, int minY, int periodX, int periodY, int[] bands) {
                passes.add(pass + ":" + minX + "," + minY + "/" + periodX + "x" + periodY);
            }

            @Override
            public void imageUpdate(ImageReader source, BufferedImage theImage, int minX, int minY, int width, int height,
                    int periodX, int periodY, int[] bands) {
                updates[0]++;
            }

            @Override
            public void passComplete(ImageReader source, BufferedImage theImage) {
            }

            @Override
            public void thumbnailPassStarted(ImageReader source, BufferedImage theThumbnail, int pass, int minPass,
                    int maxPass, int minX, int minY, int periodX, int periodY, int[] bands) {
            }

            @Override
            public void thumbnailUpdate(ImageReader source, BufferedImage theThumbnail, int minX, int minY, int width,
                    int height, int periodX, int periodY, int[] bands) {
            }

            @Override
            public void thumbnailPassComplete(ImageReader source, BufferedImage theThumbnail) {
            }
        });
        BufferedImage read = reader.read(0);
        reader.dispose();
        checks.add(Checks.expect("PNG Adam7 read : progress listener events", "imageStarted(0) imageComplete",
                () -> String.join(" ", events)));
        checks.add(Check.info("PNG Adam7 read : imageProgress calls, imageUpdate calls", progress[0] + ", " + updates[0]));
        checks.add(Checks.expect("PNG Adam7 read : update listener passes (pass:minX,minY/period)", "0:0,0/8x8 1:4,0/8x8 "
                + "2:0,4/4x8 3:2,0/4x4 4:0,2/2x4 5:1,0/2x2 6:0,1/1x2", () -> String.join(" ", passes)));
        checks.add(Checks.expect("PNG Adam7 read : pixels", "exact", () -> ImagesSupport.compare(image, read)));

        ImageReader aborting = reader(ImageIoFormatsPage.write(image, "png", p -> {
        }), "png");
        List<String> abortEvents = new ArrayList<>();
        aborting.addIIOReadProgressListener(new IIOReadProgressListener() {
            @Override
            public void sequenceStarted(ImageReader source, int minIndex) {
            }

            @Override
            public void sequenceComplete(ImageReader source) {
            }

            @Override
            public void imageStarted(ImageReader source, int imageIndex) {
                abortEvents.add("imageStarted");
                source.abort();
            }

            @Override
            public void imageProgress(ImageReader source, float percentageDone) {
            }

            @Override
            public void imageComplete(ImageReader source) {
                abortEvents.add("imageComplete");
            }

            @Override
            public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
            }

            @Override
            public void thumbnailProgress(ImageReader source, float percentageDone) {
            }

            @Override
            public void thumbnailComplete(ImageReader source) {
            }

            @Override
            public void readAborted(ImageReader source) {
                abortEvents.add("readAborted");
            }
        });
        aborting.read(0);
        aborting.dispose();
        checks.add(Checks.expect("abort() from imageStarted", "imageStarted readAborted", () -> String.join(" ",
                abortEvents)));

        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        List<String> writeEvents = new ArrayList<>();
        int[] writeProgress = new int[1];
        writer.addIIOWriteProgressListener(new IIOWriteProgressListener() {
            @Override
            public void imageStarted(ImageWriter source, int imageIndex) {
                writeEvents.add("imageStarted(" + imageIndex + ")");
            }

            @Override
            public void imageProgress(ImageWriter source, float percentageDone) {
                writeProgress[0]++;
            }

            @Override
            public void imageComplete(ImageWriter source) {
                writeEvents.add("imageComplete");
            }

            @Override
            public void thumbnailStarted(ImageWriter source, int imageIndex, int thumbnailIndex) {
            }

            @Override
            public void thumbnailProgress(ImageWriter source, float percentageDone) {
            }

            @Override
            public void thumbnailComplete(ImageWriter source) {
            }

            @Override
            public void writeAborted(ImageWriter source) {
                writeEvents.add("writeAborted");
            }
        });
        write(writer, new IIOImage(image, null, null), writer.getDefaultWriteParam());
        writer.dispose();
        checks.add(Checks.expect("PNG write : progress listener events", "imageStarted(0) imageComplete",
                () -> String.join(" ", writeEvents)));
        checks.add(Check.info("PNG write : imageProgress calls", writeProgress[0]));
    }

    /**
     * Warnings : JPEG writer warnings (localized messages from the plugin resource bundle) and JPEG reader warnings
     * (from the native decoder, called back through JNI).
     */
    private static void warnings(List<Check> checks) throws IOException {
        BufferedImage image = ImagesSupport.photo(64, 48);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        List<String> writeWarnings = new ArrayList<>();
        writer.addIIOWriteWarningListener((source, imageIndex, warning) -> writeWarnings.add(warning));
        ImageWriteParam param = writer.getDefaultWriteParam();
        // a thumbnail wider than 255 pixels is clipped
        write(writer, new IIOImage(image, List.of(new BufferedImage(300, 20, BufferedImage.TYPE_INT_RGB)), null), param);
        // metadata without JFIF marker segment + thumbnail : the writer adds the node
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
        root.replaceChild(new IIOMetadataNode("JPEGvariety"), path(root, "JPEGvariety"));
        IIOMetadata noJfif = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
        noJfif.setFromTree("javax_imageio_jpeg_image_1.0", root);
        byte[] data = write(writer, new IIOImage(image, List.of(ImagesSupport.photo(16, 16)), noJfif), param);
        writer.dispose();
        checks.add(Checks.expect("JPEG writer warnings (JPEGImageWriterResources)", "Thumbnail clipped. | Thumbnails "
                + "require JFIF marker segment.  Missing node added to metadata.",
                () -> String.join(" | ",
                        writeWarnings)));

        List<String> readWarnings = new ArrayList<>();
        ImageReader reader = reader(Arrays.copyOf(data, data.length - 2), "jpeg");
        reader.addIIOReadWarningListener((source, warning) -> readWarnings.add(warning));
        BufferedImage read = reader.read(0);
        reader.dispose();
        checks.add(Checks.expect("JPEG reader warning, missing EOI (JPEGImageReaderResources)", "Truncated File - Missing "
                + "EOI marker ; 64x48",
                () -> String.join(" | ", new TreeSet<>(readWarnings)) + " ; "
                        + ImagesSupport.size(read)));

        List<String> corruptWarnings = new ArrayList<>();
        String outcome;
        ImageReader corrupt = reader(Arrays.copyOf(data, data.length * 2 / 3), "jpeg");
        corrupt.addIIOReadWarningListener((source, warning) -> corruptWarnings.add(warning));
        try {
            outcome = "read " + ImagesSupport.size(corrupt.read(0));
        } catch (IOException e) {
            outcome = Checks.describe(e);
        } finally {
            corrupt.dispose();
        }
        String o = outcome;
        checks.add(Checks.expect("JPEG reader, truncated to 2/3 : distinct warnings (libjpeg messages through JNI), "
                + "outcome",
                "Corrupt JPEG data: 1 extraneous bytes before marker 0xd9 | Truncated File - Missing EOI "
                        + "marker ; javax.imageio.IIOException: Invalid JPEG file structure: missing SOS marker",
                () -> String.join(" | ", new TreeSet<>(corruptWarnings)) + " ; " + o));
    }

    // ------------------------------------------------------------------------------------------- custom plugin

    private static synchronized String discovery() {
        if (pluginDiscovery == null) {
            if (ImageIO.getImageReadersByFormatName(ShowcaseFormat.NAME).hasNext()) {
                pluginDiscovery = "registry (META-INF/services)";
            } else {
                ImageIO.scanForPlugins();
                if (ImageIO.getImageReadersByFormatName(ShowcaseFormat.NAME).hasNext()) {
                    pluginDiscovery = "scanForPlugins (META-INF/services)";
                } else {
                    // not found : registered by hand, so that the rest of the page still exercises the plugin
                    IIORegistry.getDefaultInstance().registerServiceProvider(new ShowcaseImageReaderSpi());
                    IIORegistry.getDefaultInstance().registerServiceProvider(new ShowcaseImageWriterSpi());
                    pluginDiscovery = "not found, registered by hand";
                }
            }
        }
        return pluginDiscovery;
    }

    private static void plugin(List<Check> checks, List<Tile> tiles) throws IOException {
        checks.add(Checks.expect("plugin discovery", "registry (META-INF/services)", ImageIoMetadataPage::discovery));
        BufferedImage rgb = ImagesSupport.pattern(96, 64, BufferedImage.TYPE_INT_RGB);
        BufferedImage argb = ImagesSupport.patternArgb(96, 64);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        checks.add(Checks.expect("ImageIO.write(image, \"showcase\", stream)", true, () -> ImageIO.write(rgb,
                ShowcaseFormat.NAME, out)));
        byte[] data = out.toByteArray();
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(data));
        checks.add(Checks.expect("ImageIO.read (found by canDecodeInput) : pixels, size, SHA-256", "exact, 10208 B, "
                + Checks.sha256(data),
                () -> ImagesSupport.compare(rgb, read) + ", " + data.length + " B, "
                        + Checks.sha256(data)));
        tiles.add(new Tile(read, "showcase RGB", data.length + " B"));

        ImageWriter writer = ImageIO.getImageWritersBySuffix(ShowcaseFormat.SUFFIX).next();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(argb), null);
        IIOMetadataNode root = new IIOMetadataNode(ShowcaseFormat.NATIVE_METADATA_FORMAT);
        root.appendChild(node("Comment", "value", "ARGB with a comment : ümlaut, عربي, 日本"));
        metadata.mergeTree(ShowcaseFormat.NATIVE_METADATA_FORMAT, root);
        int[] progress = new int[1];
        writer.addIIOWriteProgressListener(new IIOWriteProgressListener() {
            @Override
            public void imageStarted(ImageWriter source, int imageIndex) {
            }

            @Override
            public void imageProgress(ImageWriter source, float percentageDone) {
                progress[0]++;
            }

            @Override
            public void imageComplete(ImageWriter source) {
            }

            @Override
            public void thumbnailStarted(ImageWriter source, int imageIndex, int thumbnailIndex) {
            }

            @Override
            public void thumbnailProgress(ImageWriter source, float percentageDone) {
            }

            @Override
            public void thumbnailComplete(ImageWriter source) {
            }

            @Override
            public void writeAborted(ImageWriter source) {
            }
        });
        byte[] argbData = write(writer, new IIOImage(argb, null, metadata), null);
        writer.dispose();
        checks.add(Checks.expect("writer by suffix, progress calls", "ShowcaseImageWriter, 64", () -> writer.getClass()
                .getSimpleName() + ", " + progress[0]));

        ImageReader reader = ImageIO.getImageReadersByMIMEType(ShowcaseFormat.MIME_TYPE).next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(argbData))) {
            reader.setInput(in);
            BufferedImage decoded = reader.read(0);
            tiles.add(new Tile(decoded, "showcase ARGB", argbData.length + " B"));
            IIOMetadata readMetadata = reader.getImageMetadata(0);
            checks.add(Checks.expect("ARGB round trip, comment", "exact, ARGB with a comment : ümlaut, عربي, 日本",
                    () -> ImagesSupport.compare(argb, decoded) + ", " + ((ShowcaseMetadata) readMetadata).comment()));
            checks.add(Checks.expect("native tree", ShowcaseFormat.NATIVE_METADATA_FORMAT + "\n  Header alpha=\"true\" "
                    + "height=\"64\" width=\"96\"\n  Comment value=\"ARGB with a comment : ümlaut, عربي, 日本\"",
                    () -> dump(readMetadata.getAsTree(ShowcaseFormat.NATIVE_METADATA_FORMAT))));
            checks.add(Checks.expect("standard tree : channels, alpha, text", "4, nonpremultipled, ARGB with a comment : "
                    + "ümlaut, عربي, 日本", () -> {
                        Node standard = readMetadata.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
                        return attr(standard, "value", "Chroma", "NumChannels") + ", " + attr(standard, "value",
                                "Transparency", "Alpha") + ", " + attr(standard, "value", "Text", "TextEntry");
                    }));
            checks.add(Checks.expect("getMetadataFormat (Class.forName + getInstance) : element and attribute counts",
                    "ShowcaseMetadataFormat, Header 3 attributes", () -> {
                        IIOMetadataFormat format = readMetadata.getMetadataFormat(ShowcaseFormat.NATIVE_METADATA_FORMAT);
                        return format.getClass().getSimpleName() + ", Header " + format.getAttributeNames("Header").length
                                + " attributes";
                    }));
            checks.add(Checks.expect("format descriptions (application resource bundle) : en, fr",
                    "The image header: size and transparency | L'en-tête de l'image : taille et transparence | The width "
                            + "of the image, in pixels",
                    () -> {
                        IIOMetadataFormat format = readMetadata.getMetadataFormat(
                                ShowcaseFormat.NATIVE_METADATA_FORMAT);
                        return format.getElementDescription("Header", Locale.US) + " | "
                                + format.getElementDescription("Header", Locale.FRENCH) + " | "
                                + format.getAttributeDescription("Header", "width", Locale.FRENCH);
                    }));
            checks.add(Checks.expect("source region + subsampling through the plugin", "exact, 20x10", () -> {
                javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(new java.awt.Rectangle(10, 20, 40, 20));
                param.setSourceSubsampling(2, 2, 0, 0);
                BufferedImage region = reader.read(0, param);
                BufferedImage expected = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 10; y++) {
                    for (int x = 0; x < 20; x++) {
                        expected.setRGB(x, y, argb.getRGB(10 + 2 * x, 20 + 2 * y));
                    }
                }
                return ImagesSupport.compare(expected, region) + ", " + ImagesSupport.size(region);
            }));
        } finally {
            reader.dispose();
        }
        // ImageIO.getImageWriter(showcase reader) is not checked : it loads the writer SPI class with the system class
        // loader, which does not see application classes in Quarkus JVM mode (null), unlike a native executable
        checks.add(Checks.expect("convertImageMetadata(PNG metadata with tEXt) : comment", "from a PNG text chunk", () -> {
            ImageWriter png = ImageIO.getImageWritersByFormatName("png").next();
            IIOMetadata pngMetadata = png.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(rgb), null);
            pngMetadata.mergeTree("javax_imageio_png_1.0", with(new IIOMetadataNode("javax_imageio_png_1.0"), with(
                    new IIOMetadataNode("tEXt"), node("tEXtEntry", "keyword", "Comment", "value",
                            "from a PNG text chunk"))));
            ImageWriter showcase = ImageIO.getImageWritersByFormatName(ShowcaseFormat.NAME).next();
            return ((ShowcaseMetadata) showcase.convertImageMetadata(pngMetadata,
                    ImageTypeSpecifier.createFromRenderedImage(rgb), null)).comment();
        }));
        checks.add(Checks.expect("plugin listed : format names, suffixes, MIME types", "true true true", () -> Arrays.asList(
                ImageIO.getReaderFormatNames()).contains(ShowcaseFormat.NAME) + " " + Arrays
                        .asList(ImageIO
                                .getReaderFileSuffixes())
                        .contains(ShowcaseFormat.SUFFIX)
                + " " + Arrays.asList(ImageIO
                        .getWriterMIMETypes()).contains(ShowcaseFormat.MIME_TYPE)));
    }
}
