package io.quarkiverse.desktop.showcase.pages.images;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.AreaAveragingScaleFilter;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageFilter;
import java.awt.image.ColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.Kernel;
import java.awt.image.MemoryImageSource;
import java.awt.image.PixelGrabber;
import java.awt.image.RGBImageFilter;
import java.awt.image.ReplicateScaleFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
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
import io.quarkiverse.desktop.showcase.pages.images.ImagesSupport.Tile;

/**
 * Toolkit imaging (the pre-ImageIO image pipeline) : {@code Toolkit.createImage/getImage} from bytes, byte ranges,
 * classpath URLs ({@code resource:} URLs in a native executable) and files, with the Toolkit decoders (PNG, GIF, JPEG,
 * XBM ; BMP is not supported), interlaced and animated images, {@link MediaTracker}, {@code checkImage} flags, image
 * properties, {@link MemoryImageSource} (static, indexed, animated), image filters ({@link CropImageFilter},
 * {@link RGBImageFilter}, {@link ReplicateScaleFilter}, {@link AreaAveragingScaleFilter}, {@link BufferedImageFilter},
 * chains), {@link PixelGrabber}, {@code URL.getContent()} (content handlers) and multi-resolution images
 * ({@link BaseMultiResolutionImage}, a custom {@link AbstractMultiResolutionImage}, {@code KEY_RESOLUTION_VARIANT}).
 * <p>
 * Capture method C (the pixels are grabbed once loaded). Images are created in {@link #build}, loaded through a
 * {@link MediaTracker} in {@link #ready}. Native risks : the GIF and JPEG Toolkit decoders call back Java from native
 * code (JNI : {@code sun.awt.image.GifImageDecoder}, {@code sun.awt.image.JPEGImageDecoder}), {@code ImageRepresentation}
 * (JNI), the {@code resource:} URL connection, the {@code java.net.ContentHandlerFactory} service of java.desktop
 * ({@code sun.awt.www.content.MultimediaContentHandlers}).
 */
@Singleton
public class ToolkitImagingPage implements FeaturePage {

    private static final int W = ImageAssets.W;
    private static final int H = ImageAssets.H;

    // per build state (one content at a time)
    private Container holder;
    private MediaTracker tracker;
    private Map<String, Image> images;
    private MemoryImageSource animatedSource;
    private BufferedImage lastAnimatedFrame;
    private List<Check> buildChecks;

    @Override
    public String id() {
        return "images-toolkit";
    }

    @Override
    public String title() {
        return "Toolkit imaging";
    }

    @Override
    public String category() {
        return Categories.IMAGES;
    }

    @Override
    public int order() {
        return 40;
    }

    // ------------------------------------------------------------------------------------------------------ build

    @Override
    public Component build() throws Exception {
        holder = Ui.column(12, Ui.text("Loading images..."));
        Toolkit tk = Toolkit.getDefaultToolkit();
        images = new LinkedHashMap<>();
        buildChecks = new ArrayList<>();
        byte[] png = ImageAssets.png();
        images.put("PNG bytes", tk.createImage(png));
        images.put("PNG Adam7 bytes", tk.createImage(ImageIoFormatsPage.write(ImageAssets.pngImage(), "png",
                p -> p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT))));
        byte[] padded = new byte[png.length + 20];
        System.arraycopy(png, 0, padded, 7, png.length);
        images.put("PNG byte range", tk.createImage(padded, 7, png.length));
        images.put("GIF bytes + comment", tk.createImage(gifWithComment()));
        images.put("JPEG bytes", tk.createImage(ImageAssets.jpeg()));
        images.put("JPEG progressive bytes", tk.createImage(ImageIoFormatsPage.write(ImageAssets.jpegImage(), "jpeg",
                p -> p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT))));
        images.put("XBM bytes", tk.createImage(ImageAssets.xbm()));
        images.put("BMP bytes (unsupported)", tk.createImage(ImageIoFormatsPage.write(ImageAssets.pngImage(), "bmp",
                p -> {
                })));
        images.put("PNG getImage(URL)", tk.getImage(Edt.resource(ImageAssets.PNG)));
        images.put("GIF createImage(URL)", tk.createImage(Edt.resource(ImageAssets.GIF)));
        images.put("JPEG createImage(URL)", tk.createImage(Edt.resource(ImageAssets.JPEG)));
        images.put("XBM getImage(URL)", tk.getImage(Edt.resource(ImageAssets.XBM)));
        Path pngFile = Edt.tempDir().resolve("toolkit-sample.png");
        Files.write(pngFile, png);
        Path jpegFile = Edt.tempDir().resolve("toolkit-sample.jpg");
        Files.write(jpegFile, ImageAssets.jpeg());
        images.put("PNG createImage(file)", tk.createImage(pngFile.toString()));
        images.put("JPEG getImage(file)", tk.getImage(jpegFile.toString()));
        buildChecks.add(Checks.expect("getImage(file) is cached / createImage(file) is not", "true / true",
                () -> (tk.getImage(jpegFile.toString()) == tk.getImage(jpegFile.toString())) + " / "
                        + (tk.createImage(pngFile.toString()) != tk.createImage(pngFile.toString()))));
        images.put("animated GIF (frame 0)", tk.createImage(animatedGif()));

        int[] pixels = ImageAssets.pngImage().getRGB(0, 0, W, H, null, 0, W);
        images.put("MemoryImageSource int[]", tk.createImage(new MemoryImageSource(W, H, pixels, 0, W)));
        BufferedImage indexed = ImagesSupport.indexed(W, H, 8, -1);
        byte[] indices = new byte[W * H];
        indexed.getRaster().getDataElements(0, 0, W, H, indices);
        images.put("MemoryImageSource indexed", tk.createImage(new MemoryImageSource(W, H, indexed.getColorModel(),
                indices, 0, W)));
        animatedSource = new MemoryImageSource(W, H, new int[W * H], 0, W);
        animatedSource.setAnimated(true);
        animatedSource.setFullBufferUpdates(true);
        images.put("MemoryImageSource animated", tk.createImage(animatedSource));

        ImageProducer source = images.get("PNG bytes").getSource();
        images.put("CropImageFilter", tk.createImage(new FilteredImageSource(source, new CropImageFilter(10, 8, 48, 32))));
        images.put("RGBImageFilter gray", tk.createImage(new FilteredImageSource(source, new GrayFilter())));
        images.put("RGBImageFilter palette", tk.createImage(new FilteredImageSource(images.get("GIF bytes + comment")
                .getSource(), new InvertFilter())));
        images.put("ReplicateScaleFilter 1.5x", tk.createImage(new FilteredImageSource(source,
                new ReplicateScaleFilter(W * 3 / 2, H * 3 / 2))));
        images.put("AreaAveragingScaleFilter", tk.createImage(new FilteredImageSource(source,
                new AreaAveragingScaleFilter(W / 2, H / 2))));
        images.put("BufferedImageFilter(ConvolveOp)", tk.createImage(new FilteredImageSource(source,
                new BufferedImageFilter(blur()))));
        images.put("filter chain crop, gray, scale", tk.createImage(new FilteredImageSource(new FilteredImageSource(
                new FilteredImageSource(source, new CropImageFilter(0, 0, 48, 32)), new GrayFilter()),
                new ReplicateScaleFilter(96, 64))));
        images.put("createImage(BufferedImage.getSource())", tk.createImage(ImageAssets.pngImage().getSource()));

        // the MediaTracker tracks the decoded images ; animated images and images of an OffScreenImageSource
        // (BufferedImage.getSource()) end ABORTED in a MediaTracker (JDK behavior, checked separately) : they are
        // grabbed only
        tracker = new MediaTracker(holder);
        int id = 0;
        for (Map.Entry<String, Image> e : images.entrySet()) {
            if (!untracked(e.getKey())) {
                tracker.addImage(e.getValue(), id);
            }
            id++;
        }
        return Ui.column(12,
                Ui.text("Images created by the AWT Toolkit (its own decoders, not ImageIO), loaded asynchronously by the "
                        + "image fetcher threads and tracked by a MediaTracker, then grabbed with PixelGrabber. "
                        + "Multi-resolution images are drawn at scale 1 and 2.", 1000),
                holder);
    }

    private static boolean untracked(String name) {
        return name.contains("animated") || name.startsWith("createImage(BufferedImage");
    }

    private static ConvolveOp blur() {
        float n = 1f / 9f;
        return new ConvolveOp(new Kernel(3, 3, new float[] { n, n, n, n, n, n, n, n, n }), ConvolveOp.EDGE_NO_OP, null);
    }

    /**
     * Gray (luma) of every pixel.
     */
    static final class GrayFilter extends RGBImageFilter {

        GrayFilter() {
            canFilterIndexColorModel = true;
        }

        @Override
        public int filterRGB(int x, int y, int rgb) {
            return gray(rgb);
        }

        static int gray(int rgb) {
            int luma = (((rgb >> 16) & 0xFF) * 77 + ((rgb >> 8) & 0xFF) * 150 + (rgb & 0xFF) * 29) >> 8;
            return (rgb & 0xFF000000) | luma << 16 | luma << 8 | luma;
        }
    }

    /**
     * Inverts the colors ; with an IndexColorModel source only the palette is filtered.
     */
    static final class InvertFilter extends RGBImageFilter {

        int calls;

        InvertFilter() {
            canFilterIndexColorModel = true;
        }

        @Override
        public int filterRGB(int x, int y, int rgb) {
            calls++;
            return (rgb & 0xFF000000) | (~rgb & 0xFFFFFF);
        }
    }

    private static byte[] gifWithComment() throws IOException {
        BufferedImage image = ImageAssets.gifImage();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_gif_image_1.0");
        IIOMetadataNode comments = new IIOMetadataNode("CommentExtensions");
        IIOMetadataNode comment = new IIOMetadataNode("CommentExtension");
        comment.setAttribute("value", "Toolkit GIF comment");
        comments.appendChild(comment);
        root.appendChild(comments);
        metadata.mergeTree("javax_imageio_gif_image_1.0", root);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(new IIOImage(image, null, metadata));
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    static BufferedImage animatedFrame(int i) {
        BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_INDEXED);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean band = ((x + i * 12) / 12) % 2 == 0;
                frame.setRGB(x, y, band ? 0xFF1565C0 : i == 0 ? 0xFFFFB300 : i == 1 ? 0xFF43A047 : 0xFFE53935);
            }
        }
        return frame;
    }

    /**
     * An animated GIF of 3 frames (10/100 s, looping) : the Toolkit animates it on the image fetcher threads.
     */
    private static byte[] animatedGif() throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < 3; i++) {
                BufferedImage frame = animatedFrame(i);
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),
                        null);
                IIOMetadataNode root = new IIOMetadataNode("javax_imageio_gif_image_1.0");
                IIOMetadataNode gce = new IIOMetadataNode("GraphicControlExtension");
                gce.setAttribute("disposalMethod", "none");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", "10");
                gce.setAttribute("transparentColorIndex", "0");
                root.appendChild(gce);
                if (i == 0) {
                    IIOMetadataNode apps = new IIOMetadataNode("ApplicationExtensions");
                    IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] { 1, 0, 0 });
                    apps.appendChild(app);
                    root.appendChild(apps);
                }
                metadata.mergeTree("javax_imageio_gif_image_1.0", root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    // ------------------------------------------------------------------------------------------------------ ready

    @Override
    public CompletionStage<?> ready(Component content) {
        Container target = holder;
        MediaTracker mediaTracker = tracker;
        Map<String, Image> loaded = images;
        List<Check> early = buildChecks;
        // the animated memory image source : 3 frames sent synchronously, the last one stays
        for (int i = 0; i < 3; i++) {
            BufferedImage frame = animatedFrame(i);
            int[] pixels = frame.getRGB(0, 0, W, H, null, 0, W);
            animatedSource.newPixels(pixels, ColorModel.getRGBdefault(), 0, W);
            lastAnimatedFrame = frame;
        }
        BufferedImage expectedAnimated = lastAnimatedFrame;
        return Edt.until(() -> mediaTracker.checkAll(true), 20_000, "images loaded by the MediaTracker")
                .handle((v, error) -> error == null)
                .thenCompose(loadedInTime -> {
                    List<Check> tracking = new ArrayList<>(early);
                    tracking.add(Check.of("MediaTracker.checkAll(true) within 20 s", loadedInTime, loadedInTime));
                    trackingChecks(mediaTracker, loaded, tracking);
                    return Edt.background(() -> compute(loaded, expectedAnimated, tracking));
                })
                .thenAccept(result -> ImagesSupport.show(target,
                        Ui.image(ImagesSupport.grid(result.tiles(), 7, 142, 112)),
                        ChecksView.table("MediaTracker and Toolkit status", result.tracking(), 330, 1000),
                        ChecksView.table("Decoded pixels (compared with the images computed at run time)",
                                result.pixels(), 330, 1000),
                        Ui.title("Multi-resolution images : drawn at scale 1, scale 2, and scale 2 with "
                                + "KEY_RESOLUTION_VARIANT (BASE, SIZE_FIT, DPI_FIT)"),
                        Ui.image(result.multiResolution()),
                        ChecksView.table("Multi-resolution images", result.multi())));
    }

    @Override
    public void dispose(Component content) {
        if (images != null) {
            // stops the animations (image fetcher threads) and releases the decoded data
            images.values().forEach(Image::flush);
        }
        holder = null;
        tracker = null;
        images = null;
        animatedSource = null;
        lastAnimatedFrame = null;
        buildChecks = null;
    }

    private record Result(List<Tile> tiles, List<Check> tracking, List<Check> pixels, BufferedImage multiResolution,
            List<Check> multi) {
    }

    static String status(int status) {
        List<String> names = new ArrayList<>();
        if ((status & MediaTracker.LOADING) != 0) {
            names.add("LOADING");
        }
        if ((status & MediaTracker.ABORTED) != 0) {
            names.add("ABORTED");
        }
        if ((status & MediaTracker.ERRORED) != 0) {
            names.add("ERRORED");
        }
        if ((status & MediaTracker.COMPLETE) != 0) {
            names.add("COMPLETE");
        }
        return names.isEmpty() ? "0" : String.join("|", names);
    }

    static String flags(int flags) {
        List<String> names = new ArrayList<>();
        String[] all = { "WIDTH", "HEIGHT", "PROPERTIES", "SOMEBITS", "FRAMEBITS", "ALLBITS", "ERROR", "ABORT" };
        int[] bits = { ImageObserver.WIDTH, ImageObserver.HEIGHT, ImageObserver.PROPERTIES, ImageObserver.SOMEBITS,
                ImageObserver.FRAMEBITS, ImageObserver.ALLBITS, ImageObserver.ERROR, ImageObserver.ABORT };
        for (int i = 0; i < bits.length; i++) {
            if ((flags & bits[i]) != 0) {
                names.add(all[i]);
            }
        }
        return names.isEmpty() ? "0" : String.join("|", names);
    }

    private static void trackingChecks(MediaTracker tracker, Map<String, Image> images, List<Check> checks) {
        checks.add(Checks.expect("MediaTracker : statusAll, isErrorAny, errors", "ERRORED|COMPLETE, true, 1",
                () -> status(tracker.statusAll(false)) + ", " + tracker.isErrorAny() + ", " + tracker.getErrorsAny().length));
        List<String> statuses = new ArrayList<>();
        int id = 0;
        for (Map.Entry<String, Image> e : images.entrySet()) {
            if (!untracked(e.getKey())) {
                statuses.add(e.getKey() + "=" + status(tracker.statusID(id, false)));
            }
            id++;
        }
        checks.add(Checks.expect("MediaTracker.statusID : images not COMPLETE", "BMP bytes (unsupported)=ERRORED",
                () -> String.join(", ", statuses.stream().filter(s -> !s.endsWith("=COMPLETE")).toList())));
        Toolkit tk = Toolkit.getDefaultToolkit();
        checks.add(Checks.expect("checkImage flags : PNG / JPEG / XBM / BMP", "WIDTH|HEIGHT|PROPERTIES|ALLBITS / "
                + "WIDTH|HEIGHT|PROPERTIES|ALLBITS / WIDTH|HEIGHT|PROPERTIES|ALLBITS / ERROR",
                () -> String.join(" / ", flags(tk.checkImage(images.get("PNG bytes"), -1, -1, null)),
                        flags(tk.checkImage(images.get("JPEG bytes"), -1, -1, null)),
                        flags(tk.checkImage(images.get("XBM bytes"), -1, -1, null)),
                        flags(tk.checkImage(images.get("BMP bytes (unsupported)"), -1, -1, null)))));
        checks.add(Checks.expect("MediaTracker of createImage(BufferedImage.getSource()) : status, checkImage flags",
                "ABORTED, WIDTH|HEIGHT|PROPERTIES", () -> {
                    Image image = tk.createImage(ImageAssets.pngImage().getSource());
                    MediaTracker single = new MediaTracker(new Container());
                    single.addImage(image, 0);
                    single.checkAll(true);
                    return status(single.statusAll(false)) + ", " + flags(tk.checkImage(image, -1, -1, null));
                }));
        checks.add(Checks.expect("sizes : PNG, CropImageFilter, ReplicateScaleFilter, AreaAveragingScaleFilter",
                "96x64, 48x32, 144x96, 48x32", () -> String.join(", ", size(images.get("PNG bytes")),
                        size(images.get("CropImageFilter")), size(images.get("ReplicateScaleFilter 1.5x")),
                        size(images.get("AreaAveragingScaleFilter")))));
        checks.add(Checks.expect("BMP : width, height (unknown)", "-1x-1", () -> size(images.get("BMP bytes (unsupported)"))));
        checks.add(Checks.expect("getProperty(\"comment\") : GIF / PNG", "Toolkit GIF comment / UndefinedProperty", () -> {
            Object gif = images.get("GIF bytes + comment").getProperty("comment", null);
            Object png = images.get("PNG bytes").getProperty("comment", null);
            return gif + " / " + (png == Image.UndefinedProperty ? "UndefinedProperty" : String.valueOf(png));
        }));
    }

    private static String size(Image image) {
        return image.getWidth(null) + "x" + image.getHeight(null);
    }

    // ---------------------------------------------------------------------------------------------------- compute

    private static Result compute(Map<String, Image> images, BufferedImage expectedAnimated, List<Check> tracking)
            throws Exception {
        List<Tile> tiles = new ArrayList<>();
        Map<String, BufferedImage> grabbed = new LinkedHashMap<>();
        List<Check> pixels = new ArrayList<>();
        for (Map.Entry<String, Image> e : images.entrySet()) {
            if (e.getKey().startsWith("BMP")) {
                tiles.add(new Tile(null, e.getKey(), "ERROR (no decoder)"));
                continue;
            }
            try {
                BufferedImage image = ImageOpsPage.grab(e.getValue());
                grabbed.put(e.getKey(), image);
                tiles.add(new Tile(image, e.getKey(), ImagesSupport.size(image)));
            } catch (Throwable t) {
                pixels.add(Check.fail("grab " + e.getKey(), Checks.describe(t)));
                tiles.add(new Tile(null, e.getKey(), "error"));
            }
        }
        BufferedImage pattern = ImageAssets.pngImage();
        BufferedImage jpegImageIo = ImageIO.read(new java.io.ByteArrayInputStream(ImageAssets.jpeg()));
        expectEqual(pixels, grabbed, "PNG bytes", pattern);
        expectEqual(pixels, grabbed, "PNG Adam7 bytes", pattern);
        expectEqual(pixels, grabbed, "PNG byte range", pattern);
        expectEqual(pixels, grabbed, "PNG getImage(URL)", pattern);
        expectEqual(pixels, grabbed, "PNG createImage(file)", pattern);
        expectEqual(pixels, grabbed, "GIF bytes + comment", ImageAssets.gifImage());
        expectEqual(pixels, grabbed, "GIF createImage(URL)", ImageAssets.gifImage());
        expectEqual(pixels, grabbed, "JPEG bytes", jpegImageIo);
        expectEqual(pixels, grabbed, "JPEG createImage(URL)", jpegImageIo);
        expectEqual(pixels, grabbed, "JPEG getImage(file)", jpegImageIo);
        pixels.add(Checks.info("JPEG progressive bytes : difference with ImageIO's baseline decoding",
                () -> ImagesSupport.compare(jpegImageIo, grabbed.get("JPEG progressive bytes"))));
        expectEqual(pixels, grabbed, "XBM bytes", ImageAssets.xbmExpected());
        expectEqual(pixels, grabbed, "XBM getImage(URL)", ImageAssets.xbmExpected());
        expectEqual(pixels, grabbed, "animated GIF (frame 0)", animatedFrame(0));
        expectEqual(pixels, grabbed, "MemoryImageSource int[]", pattern);
        expectEqual(pixels, grabbed, "MemoryImageSource indexed", ImagesSupport.indexed(W, H, 8, -1));
        expectEqual(pixels, grabbed, "MemoryImageSource animated", expectedAnimated);
        expectEqual(pixels, grabbed, "CropImageFilter", pattern.getSubimage(10, 8, 48, 32));
        expectEqual(pixels, grabbed, "RGBImageFilter gray", map(pattern, GrayFilter::gray));
        expectEqual(pixels, grabbed, "RGBImageFilter palette", map(ImageAssets.gifImage(),
                rgb -> (rgb & 0xFF000000) | (~rgb & 0xFFFFFF)));
        expectEqual(pixels, grabbed, "ReplicateScaleFilter 1.5x", replicate(pattern, W * 3 / 2, H * 3 / 2));
        pixels.add(Checks.info("AreaAveragingScaleFilter : SHA-256", () -> Checks.sha256(grabbed.get(
                "AreaAveragingScaleFilter"))));
        expectEqual(pixels, grabbed, "BufferedImageFilter(ConvolveOp)", blur().filter(pattern, null));
        expectEqual(pixels, grabbed, "filter chain crop, gray, scale", replicate(map(pattern.getSubimage(0, 0, 48, 32),
                GrayFilter::gray), 96, 64));
        expectEqual(pixels, grabbed, "createImage(BufferedImage.getSource())", pattern);
        pixels.add(Checks.expect("PixelGrabber region of the GIF without forceRGB : color model, pixel type, status",
                "IndexColorModel, byte[], ALLBITS", () -> {
                    PixelGrabber grabber = new PixelGrabber(images.get("GIF bytes + comment"), 10, 10, 20, 10, false);
                    grabber.grabPixels(10_000);
                    return grabber.getColorModel().getClass().getSimpleName() + ", "
                            + grabber.getPixels().getClass().getSimpleName().replace("[]", "") + "[], "
                            + flags(grabber.getStatus() & ImageObserver.ALLBITS);
                }));
        pixels.add(Checks.expect("URL.getContent() of the PNG resource (MultimediaContentHandlers)", "ImageProducer, exact",
                () -> {
                    URL url = Edt.resource(ImageAssets.PNG);
                    Object content = url.getContent();
                    if (!(content instanceof ImageProducer producer)) {
                        return content == null ? "null" : "not an ImageProducer";
                    }
                    return "ImageProducer, " + ImagesSupport.compare(pattern, ImageOpsPage.grab(Toolkit.getDefaultToolkit()
                            .createImage(producer)));
                }));
        pixels.add(Checks.expect("resource bytes equal the generated images (PNG, GIF, JPEG, XBM)", "true true true true",
                () -> Arrays.equals(Edt.resourceBytes(ImageAssets.PNG), ImageAssets.png()) + " " + Arrays.equals(
                        Edt.resourceBytes(ImageAssets.GIF), ImageAssets.gif()) + " "
                        + Arrays.equals(Edt.resourceBytes(
                                ImageAssets.JPEG), ImageAssets.jpeg())
                        // the XBM is text : a checkout with CRLF line endings is equivalent
                        + " " + Edt.resourceText(ImageAssets.XBM).replace("\r", "").equals(
                                new String(ImageAssets.xbm(), java.nio.charset.StandardCharsets.US_ASCII))));

        List<Check> multi = new ArrayList<>();
        BufferedImage multiImage = multiResolution(multi);
        return new Result(tiles, tracking, pixels, multiImage, multi);
    }

    private static void expectEqual(List<Check> checks, Map<String, BufferedImage> grabbed, String name,
            BufferedImage expected) {
        checks.add(Checks.expect(name, "exact", () -> {
            BufferedImage image = grabbed.get(name);
            return image == null ? "not loaded" : ImagesSupport.compare(ImagesSupport.argb(expected), image);
        }));
    }

    private interface RgbFunction {
        int apply(int rgb);
    }

    private static BufferedImage map(BufferedImage image, RgbFunction f) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.setRGB(x, y, f.apply(image.getRGB(x, y)));
            }
        }
        return out;
    }

    /**
     * Nearest neighbor scaling as ReplicateScaleFilter does it (source pixel of the destination pixel center).
     */
    private static BufferedImage replicate(BufferedImage image, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int sw = image.getWidth();
        int sh = image.getHeight();
        for (int y = 0; y < h; y++) {
            int sy = (2 * y * sh + sh) / (2 * h);
            for (int x = 0; x < w; x++) {
                int sx = (2 * x * sw + sw) / (2 * w);
                out.setRGB(x, y, image.getRGB(sx, sy));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ multi-resolution

    private static BufferedImage variant(int size, int rgb, int markRgb) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean mark = x >= size / 4 && x < size / 4 + Math.max(2, size / 12) && y >= size / 6 && y < size * 5 / 6;
                image.setRGB(x, y, mark ? markRgb : rgb);
            }
        }
        return image;
    }

    /**
     * Generates its variants on demand and records the requested sizes.
     */
    static final class OnDemandImage extends AbstractMultiResolutionImage {

        final List<String> requests = new ArrayList<>();
        private final BufferedImage base = variant(48, 0xFF7E57C2, 0xFFFFFFFF);

        @Override
        public Image getResolutionVariant(double destWidth, double destHeight) {
            requests.add(Checks.num(destWidth, 0) + "x" + Checks.num(destHeight, 0));
            int size = (int) Math.round(Math.max(destWidth, destHeight));
            return size == 48 ? base : variant(size, 0xFF26A69A, 0xFFFFFFFF);
        }

        @Override
        public List<Image> getResolutionVariants() {
            return List.of(base);
        }

        @Override
        protected Image getBaseImage() {
            return base;
        }
    }

    private static BufferedImage multiResolution(List<Check> checks) {
        BaseMultiResolutionImage mri = new BaseMultiResolutionImage(variant(48, 0xFF1E88E5, 0xFFFFFFFF),
                variant(96, 0xFFFB8C00, 0xFF000000));
        OnDemandImage onDemand = new OnDemandImage();
        Object[] hints = { null, RenderingHints.VALUE_RESOLUTION_VARIANT_BASE, RenderingHints.VALUE_RESOLUTION_VARIANT_SIZE_FIT,
                RenderingHints.VALUE_RESOLUTION_VARIANT_DPI_FIT };
        String[] names = { "default", "BASE", "SIZE_FIT", "DPI_FIT" };
        BufferedImage out = Snapshots.offscreen(1000, 116, g -> {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1000, 116);
            g.drawImage(mri, 8, 8, null);
            g.drawImage(onDemand, 8, 60, null);
            for (int i = 0; i < hints.length; i++) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(80 + i * 110, 8);
                g2.scale(2, 2);
                if (hints[i] != null) {
                    g2.setRenderingHint(RenderingHints.KEY_RESOLUTION_VARIANT, hints[i]);
                }
                g2.drawImage(mri, 0, 0, null);
                g2.dispose();
            }
            Graphics2D g3 = (Graphics2D) g.create();
            g3.translate(540, 8);
            g3.scale(2, 2);
            g3.drawImage(onDemand, 0, 0, null);
            g3.dispose();
            Graphics2D g4 = (Graphics2D) g.create();
            g4.transform(AffineTransform.getScaleInstance(1.5, 1.5));
            g4.drawImage(mri, 440, 4, null);
            g4.dispose();
        });
        checks.add(Checks.expect("variants : count, getResolutionVariant(96, 96), (60, 60), (30, 30)", "2, 96, 96, 48",
                () -> mri.getResolutionVariants().size() + ", " + mri.getResolutionVariant(96, 96).getWidth(null) + ", "
                        + mri.getResolutionVariant(60, 60).getWidth(null) + ", "
                        + mri.getResolutionVariant(30, 30).getWidth(null)));
        checks.add(Checks.expect("drawn at scale 1 : 1x variant", Checks.argb(0xFF1E88E5), () -> Checks.argb(out.getRGB(
                8 + 40, 8 + 40))));
        String[] expected = { Checks.argb(0xFFFB8C00), Checks.argb(0xFF1E88E5), Checks.argb(0xFFFB8C00),
                Checks.argb(0xFF1E88E5) };
        for (int i = 0; i < hints.length; i++) {
            int x = 80 + i * 110 + 80;
            checks.add(Checks.expect("drawn at scale 2, KEY_RESOLUTION_VARIANT " + names[i], expected[i],
                    () -> Checks.argb(out.getRGB(x, 8 + 80))));
        }
        checks.add(Checks.expect("AbstractMultiResolutionImage : requested variants, drawn colors", "48x48 96x96, "
                + Checks.argb(0xFF7E57C2) + " " + Checks.argb(0xFF26A69A),
                () -> String.join(" ", onDemand.requests)
                        + ", " + Checks.argb(out.getRGB(8 + 40, 60 + 40)) + " " + Checks.argb(out.getRGB(540 + 80, 8 + 80))));
        checks.add(Checks.info("drawn at scale 1.5 (default hint) : color", () -> Checks.argb(out.getRGB(
                (int) (440 * 1.5) + 60, (int) (4 * 1.5) + 60))));
        return out;
    }
}
