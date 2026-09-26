package io.quarkiverse.desktop.showcase.pages.images;

import java.awt.Component;
import java.awt.Container;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BandCombineOp;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ByteLookupTable;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferShort;
import java.awt.image.Kernel;
import java.awt.image.LookupOp;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.PixelGrabber;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.RescaleOp;
import java.awt.image.ShortLookupTable;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ContextualRenderedImageFactory;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderContext;
import java.awt.image.renderable.RenderableImage;
import java.awt.image.renderable.RenderableImageOp;
import java.awt.image.renderable.RenderableImageProducer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.images.ImagesSupport.Tile;

/**
 * Image operations : {@link ConvolveOp} (blur, Gaussian, sharpen, edges, emboss, both edge conditions, gray and alpha
 * images), {@link AffineTransformOp} (nearest neighbor, bilinear, bicubic ; images and rasters), {@link RescaleOp},
 * {@link LookupOp} (byte and short tables), {@link BandCombineOp}, {@link ColorConvertOp} (gray, linear RGB, CIEXYZ,
 * PYCC), {@code Image.getScaledInstance} (5 hints), custom rasters (banded, float, double, short and 2 bit packed
 * sample models) and the renderable layer ({@link RenderableImageOp}, {@link RenderableImageProducer}).
 * <p>
 * Capture method C, computed in the background. Checks : exact results where the arithmetic is known (identity
 * kernel, edge conditions, rescale, lookups, band combination, nearest neighbor scaling) and SHA-256 of every result
 * (JVM and native must be equal : the {@code mlib_image} native library accelerates ConvolveOp, AffineTransformOp and
 * LookupOp, and {@code ImagingLib} loads the op classes by name ; LCMS converts colors).
 */
@Singleton
public class ImageOpsPage implements FeaturePage {

    private static final int W = 120;
    private static final int H = 80;

    private Container holder;

    @Override
    public String id() {
        return "images-ops";
    }

    @Override
    public String title() {
        return "Image operations";
    }

    @Override
    public String category() {
        return Categories.IMAGES;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Component build() {
        holder = Ui.column(12, Ui.text("Filtering..."));
        return Ui.column(12,
                Ui.text("BufferedImageOp and RasterOp implementations applied to the 120 x 80 test pattern, scaled "
                        + "instances, custom sample models and the renderable image layer. Every result is hashed "
                        + "(JVM and native must match: native acceleration by mlib_image, color conversion by LCMS).",
                        1000),
                holder);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Container target = holder;
        return Edt.background(ImageOpsPage::compute).thenAccept(result -> ImagesSupport.show(target,
                Ui.image(ImagesSupport.grid(result.tiles(), 7, 142, 112)),
                ChecksView.table("Exact results", result.exact()),
                ChecksView.table("Results (size, type, SHA-256 of the ARGB pixels)", result.hashes(), 330, 1000)));
    }

    @Override
    public void dispose(Component content) {
        holder = null;
    }

    private record Result(List<Tile> tiles, List<Check> exact, List<Check> hashes) {
    }

    private record Op(String name, Callable<Image> result) {
    }

    static BufferedImage source() {
        return ImagesSupport.pattern(W, H, BufferedImage.TYPE_INT_RGB);
    }

    private static BufferedImage convert(BufferedImage image, int type) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), type);
        out.createGraphics().drawImage(image, 0, 0, null);
        return out;
    }

    private static Kernel kernel(int size, float... values) {
        return new Kernel(size, size, values);
    }

    private static Kernel gaussian(int size, double sigma) {
        float[] values = new float[size * size];
        double sum = 0;
        int c = size / 2;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double v = Math.exp(-((x - c) * (x - c) + (y - c) * (y - c)) / (2 * sigma * sigma));
                values[y * size + x] = (float) v;
                sum += v;
            }
        }
        for (int i = 0; i < values.length; i++) {
            values[i] /= (float) sum;
        }
        return new Kernel(size, size, values);
    }

    private static List<Op> ops() {
        BufferedImage src = source();
        float ninth = 1f / 9f;
        List<Op> ops = new ArrayList<>();
        ops.add(new Op("source", () -> src));
        ops.add(new Op("ConvolveOp box 3x3 NO_OP", () -> new ConvolveOp(kernel(3, ninth, ninth, ninth, ninth, ninth, ninth,
                ninth, ninth, ninth), ConvolveOp.EDGE_NO_OP, null).filter(src, null)));
        ops.add(new Op("ConvolveOp Gaussian 5x5 ZERO_FILL", () -> new ConvolveOp(gaussian(5, 1.2),
                ConvolveOp.EDGE_ZERO_FILL, null).filter(src, null)));
        ops.add(new Op("ConvolveOp sharpen", () -> new ConvolveOp(kernel(3, 0, -1, 0, -1, 5, -1, 0, -1, 0))
                .filter(src, null)));
        ops.add(new Op("ConvolveOp edges (Laplacian)", () -> new ConvolveOp(kernel(3, -1, -1, -1, -1, 8, -1, -1, -1, -1))
                .filter(src, null)));
        ops.add(new Op("ConvolveOp emboss", () -> new ConvolveOp(kernel(3, -2, -1, 0, -1, 1, 1, 0, 1, 2))
                .filter(src, null)));
        ops.add(new Op("ConvolveOp blur BYTE_GRAY", () -> new ConvolveOp(gaussian(5, 1.5), ConvolveOp.EDGE_NO_OP, null)
                .filter(ImagesSupport.gray(W, H, BufferedImage.TYPE_BYTE_GRAY), null)));
        ops.add(new Op("ConvolveOp blur INT_ARGB_PRE", () -> new ConvolveOp(gaussian(5, 1.5), ConvolveOp.EDGE_NO_OP, null)
                .filter(convert(ImagesSupport.patternArgb(W, H), BufferedImage.TYPE_INT_ARGB_PRE), null)));
        ops.add(new Op("ConvolveOp blur 4BYTE_ABGR", () -> new ConvolveOp(gaussian(5, 1.5), ConvolveOp.EDGE_NO_OP, null)
                .filter(convert(ImagesSupport.patternArgb(W, H), BufferedImage.TYPE_4BYTE_ABGR), null)));
        ops.add(new Op("AffineTransformOp NEAREST x1.6", () -> new AffineTransformOp(AffineTransform.getScaleInstance(1.6,
                1.6), AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(src, null)));
        ops.add(new Op("AffineTransformOp BILINEAR 20°", () -> new AffineTransformOp(rotation(),
                AffineTransformOp.TYPE_BILINEAR).filter(src, null)));
        ops.add(new Op("AffineTransformOp BICUBIC 20°", () -> new AffineTransformOp(rotation(),
                AffineTransformOp.TYPE_BICUBIC).filter(src, null)));
        ops.add(new Op("AffineTransformOp shear, BYTE_GRAY", () -> new AffineTransformOp(AffineTransform.getShearInstance(
                0.4, 0), AffineTransformOp.TYPE_BILINEAR).filter(ImagesSupport.gray(W, H, BufferedImage.TYPE_BYTE_GRAY),
                        null)));
        ops.add(new Op("AffineTransformOp on a Raster", () -> {
            AffineTransformOp op = new AffineTransformOp(AffineTransform.getScaleInstance(0.75, 1.25),
                    AffineTransformOp.TYPE_BILINEAR);
            // mlib only (no Java fallback) : integer RGB rasters are supported, 3 byte BGR rasters are not
            WritableRaster raster = op.filter(src.getRaster(), null);
            return new BufferedImage(src.getColorModel(), raster, false, null);
        }));
        ops.add(new Op("RescaleOp x1.3 +20", () -> new RescaleOp(1.3f, 20f, null).filter(src, null)));
        ops.add(new Op("RescaleOp per band", () -> new RescaleOp(new float[] { 1f, 0.5f, 0.2f }, new float[] { 0, 30, 60 },
                null).filter(src, null)));
        ops.add(new Op("RescaleOp with alpha", () -> new RescaleOp(new float[] { 0.8f, 0.8f, 1.2f, 0.5f },
                new float[] { 0, 0, 0, 0 }, null).filter(ImagesSupport.patternArgb(W, H), null)));
        ops.add(new Op("LookupOp invert (byte)", () -> new LookupOp(new ByteLookupTable(0, invert()), null)
                .filter(src, null)));
        // on a 3BYTE_BGR source, the compatible destination is TYPE_CUSTOM and its bands come out in reverse order
        ops.add(new Op("LookupOp invert 3BYTE_BGR", () -> new LookupOp(new ByteLookupTable(0, invert()), null)
                .filter(convert(src, BufferedImage.TYPE_3BYTE_BGR), null)));
        ops.add(new Op("LookupOp posterize (byte)", () -> new LookupOp(new ByteLookupTable(0, posterize()), null)
                .filter(src, null)));
        ops.add(new Op("LookupOp per band (byte)", () -> new LookupOp(new ByteLookupTable(0, new byte[][] { identity(),
                invert(), posterize() }), null).filter(convert(src, BufferedImage.TYPE_3BYTE_BGR), null)));
        ops.add(new Op("LookupOp gamma (short, USHORT_GRAY)", () -> new LookupOp(new ShortLookupTable(0, gammaShort()),
                null).filter(ImagesSupport.gray(W, H, BufferedImage.TYPE_USHORT_GRAY), null)));
        ops.add(new Op("BandCombineOp R<->B", () -> bandCombine(src, new float[][] { { 0, 0, 1 }, { 0, 1, 0 },
                { 1, 0, 0 } })));
        ops.add(new Op("BandCombineOp sepia", () -> bandCombine(src, new float[][] { { 0.393f, 0.769f, 0.189f },
                { 0.349f, 0.686f, 0.168f }, { 0.272f, 0.534f, 0.131f } })));
        ops.add(new Op("ColorConvertOp -> CS_GRAY", () -> new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY),
                null).filter(src, null)));
        ops.add(new Op("ColorConvertOp LINEAR_RGB round trip", () -> roundTrip(src, ColorSpace.CS_LINEAR_RGB)));
        ops.add(new Op("ColorConvertOp CIEXYZ round trip", () -> roundTrip(src, ColorSpace.CS_CIEXYZ)));
        ops.add(new Op("ColorConvertOp PYCC round trip", () -> roundTrip(src, ColorSpace.CS_PYCC)));
        String[] hints = { "DEFAULT", "FAST", "SMOOTH", "REPLICATE", "AREA_AVERAGING" };
        int[] values = { Image.SCALE_DEFAULT, Image.SCALE_FAST, Image.SCALE_SMOOTH, Image.SCALE_REPLICATE,
                Image.SCALE_AREA_AVERAGING };
        for (int i = 0; i < hints.length; i++) {
            int hint = values[i];
            ops.add(new Op("getScaledInstance " + hints[i], () -> grab(src.getScaledInstance(W * 3 / 4, H * 3 / 4, hint))));
        }
        ops.add(new Op("BandedSampleModel + RescaleOp", () -> new RescaleOp(0.8f, 40f, null).filter(banded(src), null)));
        ops.add(new Op("float samples", () -> floating(src, false)));
        ops.add(new Op("double samples", () -> floating(src, true)));
        ops.add(new Op("short samples (signed)", () -> shortGray(src)));
        ops.add(new Op("2 bit packed + LookupOp", () -> twoBit()));
        ops.add(new Op("RenderableImageOp 90x60", () -> (Image) renderable().createScaledRendering(90, 60, null)));
        ops.add(new Op("RenderableImageProducer", () -> grab(java.awt.Toolkit.getDefaultToolkit().createImage(
                new RenderableImageProducer(renderable(), new RenderContext(AffineTransform.getScaleInstance(90, 60)))))));
        return ops;
    }

    private static AffineTransform rotation() {
        AffineTransform tx = new AffineTransform();
        tx.translate(20, 0);
        tx.rotate(Math.toRadians(20));
        return tx;
    }

    private static byte[] identity() {
        byte[] table = new byte[256];
        for (int i = 0; i < 256; i++) {
            table[i] = (byte) i;
        }
        return table;
    }

    private static byte[] invert() {
        byte[] table = new byte[256];
        for (int i = 0; i < 256; i++) {
            table[i] = (byte) (255 - i);
        }
        return table;
    }

    private static byte[] posterize() {
        byte[] table = new byte[256];
        for (int i = 0; i < 256; i++) {
            table[i] = (byte) ((i / 64) * 85);
        }
        return table;
    }

    private static short[] gammaShort() {
        short[] table = new short[65536];
        for (int i = 0; i < table.length; i++) {
            table[i] = (short) Math.round(65535 * Math.pow(i / 65535.0, 0.5));
        }
        return table;
    }

    private static BufferedImage bandCombine(BufferedImage src, float[][] matrix) {
        BufferedImage threeByte = convert(src, BufferedImage.TYPE_3BYTE_BGR);
        WritableRaster out = new BandCombineOp(matrix, null).filter(threeByte.getRaster(), null);
        return new BufferedImage(threeByte.getColorModel(), out, false, null);
    }

    private static BufferedImage roundTrip(BufferedImage src, int space) {
        ColorSpace cs = ColorSpace.getInstance(space);
        ComponentColorModel model = new ComponentColorModel(cs, false, false, ComponentColorModel.OPAQUE,
                DataBuffer.TYPE_USHORT);
        BufferedImage converted = new BufferedImage(model, model.createCompatibleWritableRaster(W, H), false, null);
        new ColorConvertOp(null).filter(src, converted);
        return new ColorConvertOp(null).filter(converted, new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB));
    }

    /**
     * The pixels of a Toolkit image (e.g. a scaled instance) grabbed synchronously (this runs in the background).
     */
    static BufferedImage grab(Image image) throws InterruptedException {
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, -1, -1, true);
        if (!grabber.grabPixels(10_000)) {
            throw new IllegalStateException("grabPixels failed, status " + grabber.getStatus());
        }
        int w = grabber.getWidth();
        int h = grabber.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, (int[]) grabber.getPixels(), 0, w);
        return out;
    }

    private static BufferedImage banded(BufferedImage src) {
        ComponentColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false,
                ComponentColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster raster = Raster.createWritableRaster(new BandedSampleModel(DataBuffer.TYPE_BYTE, W, H, 3),
                new Point(0, 0));
        BufferedImage image = new BufferedImage(model, raster, false, null);
        image.setRGB(0, 0, W, H, src.getRGB(0, 0, W, H, null, 0, W), 0, W);
        return image;
    }

    private static BufferedImage floating(BufferedImage src, boolean doubles) {
        ComponentColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false,
                ComponentColorModel.OPAQUE, doubles ? DataBuffer.TYPE_DOUBLE : DataBuffer.TYPE_FLOAT);
        WritableRaster raster = model.createCompatibleWritableRaster(W, H);
        if (!(raster.getDataBuffer() instanceof DataBufferFloat || raster.getDataBuffer() instanceof DataBufferDouble)) {
            throw new IllegalStateException("unexpected data buffer");
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = src.getRGB(x, y);
                for (int b = 0; b < 3; b++) {
                    raster.setSample(x, y, b, ((rgb >> (16 - 8 * b)) & 0xFF) / 255.0);
                }
            }
        }
        return new BufferedImage(model, raster, false, null);
    }

    private static BufferedImage shortGray(BufferedImage src) {
        ComponentColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), false, false,
                ComponentColorModel.OPAQUE, DataBuffer.TYPE_SHORT);
        WritableRaster raster = model.createCompatibleWritableRaster(W, H);
        if (!(raster.getDataBuffer() instanceof DataBufferShort)) {
            throw new IllegalStateException("unexpected data buffer");
        }
        BufferedImage gray = ImagesSupport.gray(W, H, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                raster.setSample(x, y, 0, gray.getRaster().getSample(x, y, 0) * 128);
            }
        }
        return new BufferedImage(model, raster, false, null);
    }

    private static BufferedImage twoBit() {
        BufferedImage indexed = ImagesSupport.indexed(W, H, 2, -1);
        if (!(indexed.getSampleModel() instanceof MultiPixelPackedSampleModel)) {
            throw new IllegalStateException("unexpected sample model");
        }
        byte[] rotate = { 1, 2, 3, 0 };
        WritableRaster out = new LookupOp(new ByteLookupTable(0, rotate), null).filter(indexed.getRaster(), null);
        return new BufferedImage(indexed.getColorModel(), out, false, null);
    }

    // -------------------------------------------------------------------------------------------- renderable layer

    /**
     * A resolution independent "sun" : a radial gradient on a unit square, rendered at the size of the render context.
     */
    static final class SunFactory implements ContextualRenderedImageFactory {

        @Override
        public RenderContext mapRenderContext(int i, RenderContext renderContext, ParameterBlock paramBlock,
                RenderableImage image) {
            return renderContext;
        }

        @Override
        public RenderedImage create(ParameterBlock paramBlock, RenderingHints hints) {
            return create(new RenderContext(new AffineTransform(), hints), paramBlock);
        }

        @Override
        public RenderedImage create(RenderContext context, ParameterBlock paramBlock) {
            AffineTransform tx = context.getTransform();
            int w = (int) Math.round(tx.getScaleX());
            int h = (int) Math.round(tx.getScaleY());
            int color = paramBlock.getIntParameter(0);
            BufferedImage image = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double dx = (x + 0.5) / w - 0.5;
                    double dy = (y + 0.5) / h - 0.5;
                    double d = Math.sqrt(dx * dx + dy * dy) * 2;
                    int alpha = ImagesSupport.clamp(255 * (1 - d));
                    image.setRGB(x, y, alpha << 24 | (color & 0xFFFFFF));
                }
            }
            return image;
        }

        @Override
        public Rectangle2D getBounds2D(ParameterBlock paramBlock) {
            return new Rectangle2D.Float(0, 0, 1, 1);
        }

        @Override
        public Object getProperty(ParameterBlock paramBlock, String name) {
            return "sun".equals(name) ? "radial" : java.awt.Image.UndefinedProperty;
        }

        @Override
        public String[] getPropertyNames() {
            return new String[] { "sun" };
        }

        @Override
        public boolean isDynamic() {
            return false;
        }
    }

    static RenderableImageOp renderable() {
        ParameterBlock block = new ParameterBlock();
        block.add(0xFF8F00);
        return new RenderableImageOp(new SunFactory(), block);
    }

    // ---------------------------------------------------------------------------------------------------- compute

    private static Result compute() {
        List<Tile> tiles = new ArrayList<>();
        List<Check> hashes = new ArrayList<>();
        for (Op op : ops()) {
            try {
                Image result = op.result().call();
                BufferedImage argb = ImagesSupport.argb(result);
                String type = result instanceof BufferedImage b ? String.valueOf(b.getType()) : "toolkit";
                hashes.add(Check.info(op.name(), ImagesSupport.size(argb) + ", type " + type + ", " + Checks.sha256(argb)));
                tiles.add(new Tile(argb, op.name(), ImagesSupport.size(argb)));
            } catch (Throwable t) {
                hashes.add(Check.fail(op.name(), Checks.describe(t)));
                tiles.add(new Tile(null, op.name(), "error"));
            }
        }
        List<Check> exact = new ArrayList<>();
        ImagesSupport.section(exact, "exact results", ImageOpsPage::exactChecks);
        return new Result(tiles, exact, hashes);
    }

    private static String rgb(int argb) {
        return ((argb >> 16) & 0xFF) + "," + ((argb >> 8) & 0xFF) + "," + (argb & 0xFF);
    }

    private static void exactChecks(List<Check> checks) {
        BufferedImage src = source();
        checks.add(Checks.expect("ConvolveOp identity kernel (EDGE_NO_OP)", "exact", () -> ImagesSupport.compare(src,
                new ConvolveOp(kernel(3, 0, 0, 0, 0, 1, 0, 0, 0, 0), ConvolveOp.EDGE_NO_OP, null).filter(src, null))));
        checks.add(Checks.expect("ConvolveOp on a banded image (mlib only, no Java implementation)",
                "java.awt.image.ImagingOpException: Unable to convolve src image", () -> {
                    try {
                        new ConvolveOp(gaussian(3, 1)).filter(banded(src), null);
                        return "no exception";
                    } catch (java.awt.image.ImagingOpException e) {
                        return Checks.describe(e);
                    }
                }));
        checks.add(Checks.expect("ConvolveOp 3x3 : corner pixel with EDGE_ZERO_FILL / EDGE_NO_OP", "#FF000000 / #FFFFFFFF",
                () -> {
                    float n = 1f / 9f;
                    Kernel k = kernel(3, n, n, n, n, n, n, n, n, n);
                    return Checks.argb(new ConvolveOp(k, ConvolveOp.EDGE_ZERO_FILL, null).filter(src, null).getRGB(0, 0))
                            + " / " + Checks.argb(new ConvolveOp(k, ConvolveOp.EDGE_NO_OP, null).filter(src, null)
                                    .getRGB(0, 0));
                }));
        checks.add(Checks.expect("ConvolveOp box blur of a uniform area keeps its color", "#FFFFFF00", () -> {
            float n = 1f / 9f;
            // inside the yellow bar (x 15..29, y 0..19)
            return Checks.argb(new ConvolveOp(kernel(3, n, n, n, n, n, n, n, n, n)).filter(src, null).getRGB(22, 8));
        }));
        checks.add(Checks.expect("ConvolveOp kernel : origin, size ; getRenderingHints", "1,1 3x3 ; null", () -> {
            ConvolveOp op = new ConvolveOp(kernel(3, 0, 0, 0, 0, 1, 0, 0, 0, 0));
            return op.getKernel().getXOrigin() + "," + op.getKernel().getYOrigin() + " " + op.getKernel().getWidth() + "x"
                    + op.getKernel().getHeight() + " ; " + op.getRenderingHints();
        }));
        checks.add(Checks.expect("AffineTransformOp NEAREST x2 replicates pixels", "exact, 240x160", () -> {
            BufferedImage scaled = new AffineTransformOp(AffineTransform.getScaleInstance(2, 2),
                    AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(src, null);
            BufferedImage sampled = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    sampled.setRGB(x, y, scaled.getRGB(2 * x + 1, 2 * y + 1));
                }
            }
            return ImagesSupport.compare(src, sampled) + ", " + ImagesSupport.size(scaled);
        }));
        checks.add(Checks.expect("AffineTransformOp : getBounds2D, getPoint2D, interpolation type",
                "-7.36,0.00 140.12x116.22, 20.00,0.00 -> 20.00,0.00, 2", () -> {
                    AffineTransformOp op = new AffineTransformOp(rotation(), AffineTransformOp.TYPE_BILINEAR);
                    Point2D p = op.getPoint2D(new Point2D.Double(0, 0), null);
                    return Checks.bounds(op.getBounds2D(src)) + ", 20.00,0.00 -> " + Checks.num(p.getX(), 2) + ","
                            + Checks.num(p.getY(), 2)
                            + ", " + op.getInterpolationType();
                }));
        checks.add(Checks.expect("RescaleOp x1.3 +20 of (0, 0, 255) / gray ramp pixel", "20,20,255 / 52,52,52", () -> {
            BufferedImage out = new RescaleOp(1.3f, 20f, null).filter(src, null);
            // (x 97, y 2) : blue bar ; (x 12, y 35) : gray ramp value 12 * 255 / 119 = 25 -> 25 * 1.3 + 20 = 52.5
            return rgb(out.getRGB(97, 2)) + " / " + rgb(out.getRGB(12, 35));
        }));
        checks.add(Checks.expect("RescaleOp : scale factors, offsets, numFactors", "[1.0, 0.5, 0.2], [0.0, 30.0, 60.0], 3",
                () -> {
                    RescaleOp op = new RescaleOp(new float[] { 1f, 0.5f, 0.2f }, new float[] { 0, 30, 60 }, null);
                    return Arrays.toString(op.getScaleFactors(null)) + ", " + Arrays.toString(op.getOffsets(null)) + ", "
                            + op.getNumFactors();
                }));
        checks.add(Checks.expect("LookupOp invert : every pixel is 255 - source", "exact", () -> {
            BufferedImage out = new LookupOp(new ByteLookupTable(0, invert()), null).filter(src, null);
            BufferedImage expected = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    expected.setRGB(x, y, ~src.getRGB(x, y) & 0xFFFFFF);
                }
            }
            return ImagesSupport.compare(expected, out);
        }));
        checks.add(Checks.expect("LookupOp posterize : distinct channel values", "0 85 170 255", () -> {
            BufferedImage out = new LookupOp(new ByteLookupTable(0, posterize()), null).filter(src, null);
            java.util.TreeSet<Integer> values = new java.util.TreeSet<>();
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int p = out.getRGB(x, y);
                    values.add((p >> 16) & 0xFF);
                    values.add((p >> 8) & 0xFF);
                    values.add(p & 0xFF);
                }
            }
            return String.join(" ", values.stream().map(String::valueOf).toList());
        }));
        checks.add(Checks.expect("BandCombineOp R<->B : swapped channels", "exact", () -> {
            BufferedImage out = bandCombine(src, new float[][] { { 0, 0, 1 }, { 0, 1, 0 }, { 1, 0, 0 } });
            BufferedImage expected = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int p = src.getRGB(x, y);
                    expected.setRGB(x, y, (p & 0xFF) << 16 | (p & 0xFF00) | ((p >> 16) & 0xFF));
                }
            }
            return ImagesSupport.compare(expected, out);
        }));
        checks.add(Checks.expect("ColorConvertOp -> CS_GRAY : white, black, red bars", "255, 0, 57", () -> {
            BufferedImage gray = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(src, null);
            return gray.getRaster().getSample(5, 5, 0) + ", " + gray.getRaster().getSample(115, 5, 0) + ", "
                    + gray.getRaster().getSample(80, 5, 0);
        }));
        checks.add(Checks.expect("ColorConvertOp LINEAR_RGB / CIEXYZ / PYCC round trips", "exact / exact / mean 5.91, "
                + "max 56",
                () -> String.join(
                        " / ", ImagesSupport.compare(src, roundTrip(src, ColorSpace.CS_LINEAR_RGB)),
                        ImagesSupport.compare(src, roundTrip(src, ColorSpace.CS_CIEXYZ)),
                        ImagesSupport.compare(src, roundTrip(src, ColorSpace.CS_PYCC)))));
        checks.add(Checks.expect("getScaledInstance REPLICATE x0.5 : pixels sampled", "exact", () -> {
            BufferedImage scaled = grab(src.getScaledInstance(W / 2, H / 2, Image.SCALE_REPLICATE));
            BufferedImage expected = new BufferedImage(W / 2, H / 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < H / 2; y++) {
                for (int x = 0; x < W / 2; x++) {
                    // ReplicateScaleFilter samples the center of each destination pixel
                    expected.setRGB(x, y, src.getRGB(2 * x + 1, 2 * y + 1));
                }
            }
            return ImagesSupport.compare(expected, scaled);
        }));
        checks.add(Checks.expect("float samples : DataBuffer type, sample of the red bar", "4, 1.0 0.0 0.0", () -> {
            BufferedImage image = floating(src, false);
            return image.getRaster().getDataBuffer().getDataType() + ", " + image.getRaster().getSampleFloat(80, 5, 0)
                    + " " + image.getRaster().getSampleFloat(80, 5, 1) + " " + image.getRaster().getSampleFloat(80, 5, 2);
        }));
        checks.add(Checks.expect("Raster : createChild, getDataElements, getPixels", "40x20, 3 elements, 0, 17", () -> {
            Raster child = src.getRaster().createChild(10, 10, 40, 20, 0, 0, null);
            Object elements = convert(src, BufferedImage.TYPE_3BYTE_BGR).getRaster().getDataElements(0, 0, null);
            int[] pixels = src.getRaster().getPixels(15, 2, 1, 1, (int[]) null);
            return child.getWidth() + "x" + child.getHeight() + ", " + ((byte[]) elements).length + " elements, "
                    + child.getMinX() + ", " + pixels[0] / 15;
        }));
        checks.add(Checks.expect("RenderableImageOp : properties, bounds, rendering size", "radial, 0.00,0.00 1.00x1.00, "
                + "90x60", () -> {
                    RenderableImageOp op = renderable();
                    RenderedImage rendered = op.createScaledRendering(90, 60, null);
                    return op.getProperty("sun") + ", " + Checks.bounds(new Rectangle2D.Float(op.getMinX(), op.getMinY(),
                            op.getWidth(), op.getHeight())) + ", " + rendered.getWidth() + "x" + rendered.getHeight();
                }));
        checks.add(Checks.expect("RenderableImageProducer pixels equal createRendering", "exact", () -> {
            RenderContext context = new RenderContext(AffineTransform.getScaleInstance(90, 60));
            BufferedImage rendered = (BufferedImage) renderable().createRendering(context);
            BufferedImage produced = grab(java.awt.Toolkit.getDefaultToolkit().createImage(new RenderableImageProducer(
                    renderable(), context)));
            return ImagesSupport.compare(rendered, produced);
        }));
    }
}
