package io.quarkiverse.desktop.showcase.pages.images.plugin;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataFormat;
import javax.imageio.metadata.IIOMetadataFormatImpl;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The native metadata format of the "Showcase raw image" plugin. {@code IIOMetadata.getMetadataFormat} loads it with
 * {@code Class.forName} and invokes {@code getInstance()} reflectively (registered for reflection). Element and
 * attribute descriptions come from the resource bundle {@link ShowcaseFormat#RESOURCES} (a properties file under
 * {@code showcase/text-images/}).
 */
@RegisterForReflection
public class ShowcaseMetadataFormat extends IIOMetadataFormatImpl {

    private static ShowcaseMetadataFormat instance;

    private ShowcaseMetadataFormat() {
        super(ShowcaseFormat.NATIVE_METADATA_FORMAT, CHILD_POLICY_SOME);
        setResourceBaseName(ShowcaseFormat.RESOURCES);
        addElement("Header", ShowcaseFormat.NATIVE_METADATA_FORMAT, CHILD_POLICY_EMPTY);
        addAttribute("Header", "width", DATATYPE_INTEGER, true, null, "1", "65535", true, true);
        addAttribute("Header", "height", DATATYPE_INTEGER, true, null, "1", "65535", true, true);
        addBooleanAttribute("Header", "alpha", false, false);
        addElement("Comment", ShowcaseFormat.NATIVE_METADATA_FORMAT, CHILD_POLICY_EMPTY);
        addAttribute("Comment", "value", DATATYPE_STRING, false, "");
    }

    public static synchronized IIOMetadataFormat getInstance() {
        if (instance == null) {
            instance = new ShowcaseMetadataFormat();
        }
        return instance;
    }

    @Override
    public boolean canNodeAppear(String elementName, ImageTypeSpecifier imageType) {
        return true;
    }
}
