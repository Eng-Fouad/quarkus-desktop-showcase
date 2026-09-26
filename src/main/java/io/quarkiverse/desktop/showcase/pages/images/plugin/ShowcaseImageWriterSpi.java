package io.quarkiverse.desktop.showcase.pages.images.plugin;

import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.util.Locale;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Service provider of the "Showcase raw image" writer. Found by {@code IIORegistry} through
 * {@code META-INF/services/javax.imageio.spi.ImageWriterSpi}, and by {@code ImageIO.getImageWriter(ImageReader)}
 * through {@code Class.forName} of the reader's writer SPI name.
 */
@RegisterForReflection
public class ShowcaseImageWriterSpi extends ImageWriterSpi {

    public ShowcaseImageWriterSpi() {
        super(ShowcaseFormat.VENDOR, ShowcaseFormat.VERSION,
                new String[] { ShowcaseFormat.NAME, "SHOWCASE" },
                new String[] { ShowcaseFormat.SUFFIX },
                new String[] { ShowcaseFormat.MIME_TYPE },
                ShowcaseImageWriter.class.getName(),
                new Class<?>[] { ImageOutputStream.class },
                new String[] { ShowcaseImageReaderSpi.class.getName() },
                false, null, null, null, null,
                true, ShowcaseFormat.NATIVE_METADATA_FORMAT, ShowcaseMetadataFormat.class.getName(), null, null);
    }

    /**
     * Images with a direct or component color model of at most 4 bands of 8 bits (anything getRGB can read would do,
     * but a real plugin declares what it supports).
     */
    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        ColorModel model = type.getColorModel();
        return model instanceof DirectColorModel || model.getNumComponents() <= 4;
    }

    @Override
    public ImageWriter createWriterInstance(Object extension) {
        return new ShowcaseImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Quarkus Desktop Showcase raw image writer";
    }
}
