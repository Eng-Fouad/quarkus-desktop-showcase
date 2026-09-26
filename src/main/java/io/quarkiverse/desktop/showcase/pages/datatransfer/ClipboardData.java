package io.quarkiverse.desktop.showcase.pages.datatransfer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Data flavors, transferables and payloads of the data transfer pages (clipboard, drag and drop).
 * <p>
 * The flavors are created when used (never in static fields : {@code java.awt.datatransfer} is initialized at run time
 * in a native executable). Several are created from MIME type strings on purpose : {@code DataFlavor} then loads the
 * representation class by name ({@code class=} parameter), a reflective lookup a native executable must support.
 */
public final class ClipboardData {

    public static final String TEXT = "Quarkus desktop clipboard: äöü ß € 日本語 ✓ 🙂";
    public static final String HTML_ALL = "<html><body><p>Quarkus <b>desktop</b> clipboard</p></body></html>";
    public static final String HTML_SELECTION = "Quarkus <b>desktop</b> clipboard";
    public static final String HTML_FRAGMENT = "<b>desktop</b>";
    public static final String RTF = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}}\\f0\\fs20 Quarkus "
            + "{\\b desktop} clipboard\\par}";
    public static final String URL = "https://quarkus.io/extensions/";
    public static final List<String> FILE_NAMES = List.of("showcase-clipboard-1.txt", "showcase-clipboard-2.txt");

    public static final String FOREIGN_TEXT = "Foreign text from another process: ½ ± Ω €";
    public static final String FOREIGN_HTML = "<i>Foreign</i> HTML";
    public static final String FOREIGN_RTF = "{\\rtf1\\ansi Foreign {\\i RTF}\\par}";
    public static final String FOREIGN_URL = "https://quarkus.io/guides/";
    public static final String FOREIGN_FILE = "showcase-foreign.txt";
    public static final int FOREIGN_BACKGROUND = 0x2E7D32;
    public static final int FOREIGN_SQUARE = 0xFFB300;

    /** The quadrant colors of the generated test image (top left, top right, bottom left, bottom right). */
    public static final int[] QUADRANTS = { 0xD32F2F, 0x1976D2, 0x388E3C, 0xFBC02D };
    public static final int IMAGE_WIDTH = 64;
    public static final int IMAGE_HEIGHT = 48;

    /** Windows clipboard formats keeping the test data out of the clipboard history and of clipboard monitors. */
    public static final String NATIVE_NO_HISTORY = "CanIncludeInClipboardHistory";
    public static final String NATIVE_NO_MONITOR = "ExcludeClipboardContentFromMonitorProcessing";

    private ClipboardData() {
    }

    /**
     * An object transferred by reference within the JVM (never serialized, not serializable). Loaded by name by
     * {@code DataFlavor} : registered for reflection (application-level native configuration).
     */
    @RegisterForReflection
    public static final class LocalReference {

        private final String name;

        public LocalReference(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ------------------------------------------------------------------------------------------------------ flavors

    public static DataFlavor flavor(String mimeType) {
        try {
            return new DataFlavor(mimeType);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load the representation class of " + mimeType, e);
        }
    }

    public static DataFlavor rtfFlavor() {
        return flavor("text/rtf; class=java.io.InputStream");
    }

    public static DataFlavor urlFlavor() {
        return flavor("application/x-java-url; class=java.net.URL");
    }

    public static DataFlavor payloadFlavor() {
        return flavor(DataFlavor.javaSerializedObjectMimeType + "; class=" + ClipboardPayload.class.getName());
    }

    public static DataFlavor localFlavor() {
        return flavor(DataFlavor.javaJVMLocalObjectMimeType + "; class=" + LocalReference.class.getName());
    }

    public static DataFlavor noHistoryFlavor() {
        return flavor("application/x-showcase-no-clipboard-history; class=\"[B\"");
    }

    public static DataFlavor noMonitorFlavor() {
        return flavor("application/x-showcase-no-clipboard-monitor; class=\"[B\"");
    }

    public static ClipboardPayload payload() {
        return new ClipboardPayload("clipboard payload", 42);
    }

    /**
     * On Windows, maps the two private flavors to the clipboard formats that keep the data of the page out of the
     * clipboard history ({@code CanIncludeInClipboardHistory} = 0) and of clipboard monitors : exercises both ways to
     * customize {@link SystemFlavorMap} (unencoded natives added, and mappings replaced).
     */
    public static void mapPrivateFlavors() {
        if (!Platforms.isWindows()) {
            return;
        }
        SystemFlavorMap map = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
        DataFlavor noHistory = noHistoryFlavor();
        map.addUnencodedNativeForFlavor(noHistory, NATIVE_NO_HISTORY);
        map.addFlavorForUnencodedNative(NATIVE_NO_HISTORY, noHistory);
        DataFlavor noMonitor = noMonitorFlavor();
        map.setNativesForFlavor(noMonitor, new String[] { NATIVE_NO_MONITOR });
        map.setFlavorsForNative(NATIVE_NO_MONITOR, new DataFlavor[] { noMonitor });
    }

    /**
     * {@code true} for the flavors {@link #mapPrivateFlavors()} adds to every transferable of the page on Windows.
     */
    public static boolean isPrivate(DataFlavor flavor) {
        return flavor.getPrimaryType().equals("application")
                && flavor.getSubType().startsWith("x-showcase-no-clipboard-");
    }

    // ------------------------------------------------------------------------------------------------ transferables

    /**
     * A transferable backed by suppliers, one per flavor (a fresh stream on each request for stream flavors). On
     * Windows, the private flavors are added (see {@link #mapPrivateFlavors()}).
     */
    public static final class MapTransferable implements Transferable {

        private final Map<DataFlavor, Callable<Object>> data = new LinkedHashMap<>();

        public MapTransferable with(DataFlavor flavor, Callable<Object> value) {
            data.put(flavor, value);
            return this;
        }

        public MapTransferable withPrivateFlavors() {
            if (Platforms.isWindows()) {
                // a DWORD 0 (CanIncludeInClipboardHistory), any data (ExcludeClipboardContentFromMonitorProcessing)
                with(noHistoryFlavor(), () -> new byte[4]);
                with(noMonitorFlavor(), () -> new byte[4]);
            }
            return this;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return data.keySet().toArray(DataFlavor[]::new);
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return data.containsKey(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            Callable<Object> value = data.get(flavor);
            if (value == null) {
                throw new UnsupportedFlavorException(flavor);
            }
            try {
                return value.call();
            } catch (IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * The user's clipboard contents, saved before the page changes the clipboard and restored when the page is left :
     * the data of the common flavors (text, HTML, RTF, image, file list, URL) read at once.
     */
    public static final class SavedContents implements Transferable {

        private final Map<DataFlavor, Object> data = new LinkedHashMap<>();

        public static SavedContents save(Clipboard clipboard) {
            SavedContents saved = new SavedContents();
            Transferable contents = null;
            for (int attempt = 0; attempt < 10 && contents == null; attempt++) {
                try {
                    contents = clipboard.getContents(null);
                } catch (IllegalStateException e) {
                    // another application has the clipboard open
                    io.quarkiverse.desktop.showcase.pages.desktop.DesktopSupport.sleep(50);
                }
            }
            if (contents == null) {
                return saved;
            }
            for (DataFlavor flavor : List.of(DataFlavor.stringFlavor, DataFlavor.allHtmlFlavor, rtfFlavor(),
                    DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor, urlFlavor())) {
                try {
                    if (contents.isDataFlavorSupported(flavor)) {
                        Object value = contents.getTransferData(flavor);
                        if (value instanceof InputStream in) {
                            try (in) {
                                value = in.readAllBytes();
                            }
                        }
                        if (value != null) {
                            saved.data.put(flavor, value);
                        }
                    }
                } catch (Exception | LinkageError e) {
                    // that flavor is not restored
                }
            }
            if (Platforms.isWindows()) {
                saved.data.put(noHistoryFlavor(), new byte[4]);
            }
            return saved;
        }

        /** The number of flavors that will be restored (the user's data itself is never shown). */
        public int size() {
            return data.size();
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return data.keySet().toArray(DataFlavor[]::new);
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return data.containsKey(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            Object value = data.get(flavor);
            if (value == null) {
                throw new UnsupportedFlavorException(flavor);
            }
            return value instanceof byte[] bytes && flavor.isRepresentationClassInputStream()
                    ? new ByteArrayInputStream(bytes)
                    : value;
        }
    }

    // ---------------------------------------------------------------------------------------------------- payloads

    /**
     * The generated test image : four solid quadrants ({@link #QUADRANTS}), opaque (the JPEG clipboard format has no
     * alpha).
     */
    public static BufferedImage testImage() {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            int w = IMAGE_WIDTH / 2;
            int h = IMAGE_HEIGHT / 2;
            for (int i = 0; i < 4; i++) {
                g.setColor(new Color(QUADRANTS[i]));
                g.fillRect((i % 2) * w, (i / 2) * h, w, h);
            }
            g.setColor(Color.WHITE);
            g.fillOval(w - 8, h - 8, 16, 16);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * The text of a transfer data object of any text representation class.
     */
    public static String text(Object value, Charset charset) throws IOException {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Reader reader) {
            try (reader) {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[1024];
                for (int n; (n = reader.read(buffer)) > 0;) {
                    sb.append(buffer, 0, n);
                }
                return sb.toString();
            }
        }
        if (value instanceof InputStream in) {
            try (in) {
                return new String(in.readAllBytes(), charset);
            }
        }
        if (value instanceof CharBuffer buffer) {
            return buffer.toString();
        }
        if (value instanceof ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes, charset);
        }
        if (value instanceof char[] chars) {
            return new String(chars);
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, charset);
        }
        return value.getClass().getName();
    }

    /**
     * {@code text} without trailing NUL characters (native text formats are NUL terminated).
     */
    public static String trimNul(String text) {
        if (text == null) {
            return null;
        }
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\0') {
            end--;
        }
        return text.substring(0, end);
    }

    public static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * {@code primary/sub; class=...} of each flavor, sorted, private flavors excluded.
     */
    public static List<String> mimeTypes(DataFlavor[] flavors) {
        List<String> types = new ArrayList<>();
        for (DataFlavor f : flavors) {
            if (!isPrivate(f)) {
                types.add(f.getPrimaryType() + "/" + f.getSubType() + "; class=" + f.getRepresentationClass().getName());
            }
        }
        return types.stream().distinct().sorted().toList();
    }
}
