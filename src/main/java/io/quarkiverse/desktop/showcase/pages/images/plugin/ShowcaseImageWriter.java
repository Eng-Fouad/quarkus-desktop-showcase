package io.quarkiverse.desktop.showcase.pages.images.plugin;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

/**
 * Writer of the "Showcase raw image" format ({@link ShowcaseFormat}) : the comment comes from the image metadata
 * (native or standard {@code Text} tree), progress listeners are notified.
 */
public class ShowcaseImageWriter extends ImageWriter {

    public ShowcaseImageWriter(ImageWriterSpi spi) {
        super(spi);
    }

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
        ShowcaseMetadata metadata = new ShowcaseMetadata();
        metadata.alpha = imageType.getColorModel().hasAlpha();
        return metadata;
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType, ImageWriteParam param) {
        if (inData instanceof ShowcaseMetadata metadata) {
            return metadata;
        }
        if (inData != null && inData.isStandardMetadataFormatSupported()) {
            ShowcaseMetadata metadata = (ShowcaseMetadata) getDefaultImageMetadata(imageType, param);
            try {
                metadata.mergeTree(IIOMetadataFormatImpl.standardMetadataFormatName,
                        inData.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName));
                return metadata;
            } catch (IIOInvalidTreeException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) throws IOException {
        if (!(output instanceof ImageOutputStream out)) {
            throw new IllegalStateException("No output stream");
        }
        if (image.hasRaster()) {
            throw new UnsupportedOperationException("Rasters are not supported");
        }
        RenderedImage rendered = image.getRenderedImage();
        BufferedImage buffered = rendered instanceof BufferedImage b ? b : null;
        if (buffered == null) {
            throw new IIOException("Only BufferedImage sources are supported");
        }
        ShowcaseMetadata metadata = (ShowcaseMetadata) convertImageMetadata(image.getMetadata(),
                ImageTypeSpecifier.createFromRenderedImage(rendered), param);
        String comment = metadata == null || metadata.comment == null ? "" : metadata.comment;
        boolean alpha = buffered.getColorModel().hasAlpha();
        int width = buffered.getWidth();
        int height = buffered.getHeight();

        clearAbortRequest();
        processImageStarted(0);
        out.write(ShowcaseFormat.magic());
        out.writeInt(ShowcaseFormat.VERSION_1);
        out.writeInt(width);
        out.writeInt(height);
        out.writeByte(alpha ? 1 : 0);
        byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_8);
        out.writeShort(commentBytes.length);
        out.write(commentBytes);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            buffered.getRGB(0, y, width, 1, row, 0, width);
            int x = 0;
            while (x < width) {
                int argb = alpha ? row[x] : row[x] | 0xFF000000;
                int count = 1;
                while (x + count < width && count < 256 && (alpha ? row[x + count] : row[x + count] | 0xFF000000) == argb) {
                    count++;
                }
                out.writeByte(count - 1);
                out.writeInt(argb);
                x += count;
            }
            if (abortRequested()) {
                processWriteAborted();
                return;
            }
            processImageProgress(100f * (y + 1) / height);
        }
        out.flush();
        processImageComplete();
    }
}
