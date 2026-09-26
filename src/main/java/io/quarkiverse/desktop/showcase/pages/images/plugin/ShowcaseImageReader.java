package io.quarkiverse.desktop.showcase.pages.images.plugin;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 * Reader of the "Showcase raw image" format ({@link ShowcaseFormat}) : one image, source region, subsampling,
 * destination, progress listeners and abort are supported.
 */
public class ShowcaseImageReader extends ImageReader {

    private ShowcaseMetadata header;
    private long dataOffset;

    public ShowcaseImageReader(ImageReaderSpi spi) {
        super(spi);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        header = null;
    }

    private ImageInputStream stream() {
        if (!(input instanceof ImageInputStream stream)) {
            throw new IllegalStateException("No input stream");
        }
        return stream;
    }

    private void readHeader() throws IOException {
        if (header != null) {
            return;
        }
        ImageInputStream in = stream();
        in.seek(0);
        byte[] magic = new byte[ShowcaseFormat.magic().length];
        in.readFully(magic);
        if (!Arrays.equals(magic, ShowcaseFormat.magic())) {
            throw new IIOException("Not a showcase raw image");
        }
        int version = in.readInt();
        if (version != ShowcaseFormat.VERSION_1) {
            throw new IIOException("Unsupported version " + version);
        }
        ShowcaseMetadata metadata = new ShowcaseMetadata();
        metadata.width = in.readInt();
        metadata.height = in.readInt();
        metadata.alpha = in.readByte() != 0;
        byte[] comment = new byte[in.readUnsignedShort()];
        in.readFully(comment);
        metadata.comment = new String(comment, StandardCharsets.UTF_8);
        dataOffset = in.getStreamPosition();
        header = metadata;
    }

    private void checkIndex(int imageIndex) {
        if (imageIndex != 0) {
            throw new IndexOutOfBoundsException("imageIndex " + imageIndex);
        }
    }

    @Override
    public int getNumImages(boolean allowSearch) {
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        return header.width;
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        return header.height;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        return List.of(ImageTypeSpecifier.createFromBufferedImageType(header.alpha ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB)).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        ShowcaseMetadata copy = new ShowcaseMetadata();
        copy.width = header.width;
        copy.height = header.height;
        copy.alpha = header.alpha;
        copy.comment = header.comment;
        return copy;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        ImageInputStream in = stream();
        in.seek(dataOffset);
        int width = header.width;
        int height = header.height;
        clearAbortRequest();
        processImageStarted(imageIndex);

        Rectangle source = new Rectangle();
        Rectangle destinationRegion = new Rectangle();
        BufferedImage destination = getDestination(param, getImageTypes(imageIndex), width, height);
        computeRegions(param, width, height, destination, source, destinationRegion);
        int xStep = param == null ? 1 : param.getSourceXSubsampling();
        int yStep = param == null ? 1 : param.getSourceYSubsampling();

        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                int count = in.readUnsignedByte() + 1;
                int argb = in.readInt();
                if (!header.alpha) {
                    argb |= 0xFF000000;
                }
                Arrays.fill(row, x, Math.min(width, x + count), argb);
                x += count;
            }
            if (y >= source.y && y < source.y + source.height && (y - source.y) % yStep == 0) {
                int dy = destinationRegion.y + (y - source.y) / yStep;
                for (int sx = source.x, dx = destinationRegion.x; sx < source.x + source.width; sx += xStep, dx++) {
                    destination.setRGB(dx, dy, row[sx]);
                }
            }
            if (abortRequested()) {
                processReadAborted();
                return destination;
            }
            processImageProgress(100f * (y + 1) / height);
        }
        processImageComplete();
        return destination;
    }
}
