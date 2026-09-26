package io.quarkiverse.desktop.showcase.pages.images.plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Service provider of the "Showcase raw image" reader. Found by {@code IIORegistry} through
 * {@code META-INF/services/javax.imageio.spi.ImageReaderSpi} ({@code ServiceLoader} : the class is instantiated
 * reflectively, hence the registration for reflection in native executables).
 */
@RegisterForReflection
public class ShowcaseImageReaderSpi extends ImageReaderSpi {

    public ShowcaseImageReaderSpi() {
        super(ShowcaseFormat.VENDOR, ShowcaseFormat.VERSION,
                new String[] { ShowcaseFormat.NAME, "SHOWCASE" },
                new String[] { ShowcaseFormat.SUFFIX },
                new String[] { ShowcaseFormat.MIME_TYPE },
                ShowcaseImageReader.class.getName(),
                new Class<?>[] { ImageInputStream.class },
                new String[] { ShowcaseImageWriterSpi.class.getName() },
                false, null, null, null, null,
                true, ShowcaseFormat.NATIVE_METADATA_FORMAT, ShowcaseMetadataFormat.class.getName(), null, null);
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream stream)) {
            return false;
        }
        byte[] magic = ShowcaseFormat.magic();
        byte[] header = new byte[magic.length];
        stream.mark();
        try {
            int read = 0;
            while (read < header.length) {
                int n = stream.read(header, read, header.length - read);
                if (n < 0) {
                    return false;
                }
                read += n;
            }
            return Arrays.equals(magic, header);
        } finally {
            stream.reset();
        }
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new ShowcaseImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Quarkus Desktop Showcase raw image reader";
    }
}
