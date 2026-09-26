package io.quarkiverse.desktop.showcase.pages.images.plugin;

import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.metadata.IIOMetadataNode;

import org.w3c.dom.Node;

/**
 * Image metadata of the "Showcase raw image" format : a native tree ({@code Header} and {@code Comment}) and the
 * standard tree ({@code Chroma}, {@code Transparency}, {@code Text}).
 */
public class ShowcaseMetadata extends IIOMetadata {

    int width;
    int height;
    boolean alpha;
    String comment = "";

    public ShowcaseMetadata() {
        super(true, ShowcaseFormat.NATIVE_METADATA_FORMAT, ShowcaseMetadataFormat.class.getName(), null, null);
    }

    public String comment() {
        return comment;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Node getAsTree(String formatName) {
        if (ShowcaseFormat.NATIVE_METADATA_FORMAT.equals(formatName)) {
            IIOMetadataNode root = new IIOMetadataNode(ShowcaseFormat.NATIVE_METADATA_FORMAT);
            IIOMetadataNode headerNode = new IIOMetadataNode("Header");
            headerNode.setAttribute("width", String.valueOf(width));
            headerNode.setAttribute("height", String.valueOf(height));
            headerNode.setAttribute("alpha", String.valueOf(alpha));
            root.appendChild(headerNode);
            IIOMetadataNode commentNode = new IIOMetadataNode("Comment");
            commentNode.setAttribute("value", comment);
            root.appendChild(commentNode);
            return root;
        }
        if (IIOMetadataFormatImpl.standardMetadataFormatName.equals(formatName)) {
            return getStandardTree();
        }
        throw new IllegalArgumentException("Unsupported format " + formatName);
    }

    @Override
    protected IIOMetadataNode getStandardChromaNode() {
        IIOMetadataNode chroma = new IIOMetadataNode("Chroma");
        IIOMetadataNode type = new IIOMetadataNode("ColorSpaceType");
        type.setAttribute("name", "RGB");
        chroma.appendChild(type);
        IIOMetadataNode channels = new IIOMetadataNode("NumChannels");
        channels.setAttribute("value", alpha ? "4" : "3");
        chroma.appendChild(channels);
        return chroma;
    }

    @Override
    protected IIOMetadataNode getStandardTransparencyNode() {
        IIOMetadataNode transparency = new IIOMetadataNode("Transparency");
        IIOMetadataNode alphaNode = new IIOMetadataNode("Alpha");
        alphaNode.setAttribute("value", alpha ? "nonpremultipled" : "none");
        transparency.appendChild(alphaNode);
        return transparency;
    }

    @Override
    protected IIOMetadataNode getStandardTextNode() {
        if (comment == null || comment.isEmpty()) {
            return null;
        }
        IIOMetadataNode text = new IIOMetadataNode("Text");
        IIOMetadataNode entry = new IIOMetadataNode("TextEntry");
        entry.setAttribute("keyword", "comment");
        entry.setAttribute("value", comment);
        entry.setAttribute("encoding", "UTF-8");
        text.appendChild(entry);
        return text;
    }

    @Override
    public void mergeTree(String formatName, Node root) throws IIOInvalidTreeException {
        if (ShowcaseFormat.NATIVE_METADATA_FORMAT.equals(formatName)) {
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof IIOMetadataNode node && node.getNodeName().equals("Comment")) {
                    comment = node.getAttribute("value");
                } else if (child instanceof IIOMetadataNode node && node.getNodeName().equals("Header")) {
                    if (node.hasAttribute("alpha")) {
                        alpha = Boolean.parseBoolean(node.getAttribute("alpha"));
                    }
                } else {
                    throw new IIOInvalidTreeException("Unexpected node " + child.getNodeName(), child);
                }
            }
        } else if (IIOMetadataFormatImpl.standardMetadataFormatName.equals(formatName)) {
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeName().equals("Text")) {
                    for (Node entry = child.getFirstChild(); entry != null; entry = entry.getNextSibling()) {
                        if (entry instanceof IIOMetadataNode node && node.hasAttribute("value")) {
                            comment = node.getAttribute("value");
                        }
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported format " + formatName);
        }
    }

    @Override
    public void reset() {
        comment = "";
    }
}
