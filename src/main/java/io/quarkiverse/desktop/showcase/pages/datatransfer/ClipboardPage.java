package io.quarkiverse.desktop.showcase.pages.datatransfer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.FlavorTable;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.FocusLock;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.desktop.DesktopSupport;

/**
 * The system clipboard : round trips of text, the three HTML flavors, RTF, an image, a file list, a URL, a custom
 * serializable flavor and a JVM-local object, the {@code FlavorListener} and {@code ClipboardOwner} notifications, a
 * private {@code Clipboard}, the data flavors and the {@code SystemFlavorMap}, and a native round trip through another
 * process (Windows PowerShell on Windows, xclip on Linux when installed) : the data this JVM writes never leaves the JVM
 * when it reads it back, the other process verifies the native formats and writes data the page reads natively.
 * <p>
 * AWT only. The user's clipboard contents (text, HTML, RTF, image, file list, URL) are saved first and restored when the
 * page is left ; on Windows, the data of the page is kept out of the clipboard history
 * ({@code CanIncludeInClipboardHistory} format through a custom {@code SystemFlavorMap} mapping). Every clipboard access
 * runs on a background thread ; a step is retried when another application changed the clipboard meanwhile.
 */
@Singleton
public class ClipboardPage implements FeaturePage {

    private static final Logger LOG = Logger.getLogger(ClipboardPage.class);

    // per build state (one content at a time)
    private ClipboardSession session;
    private ChecksView localView;
    private ChecksView foreignView;
    private ImageBox localImage;
    private ImageBox foreignImage;

    @Override
    public String id() {
        return "dt-clipboard";
    }

    @Override
    public String title() {
        return "Clipboard";
    }

    @Override
    public String category() {
        return Categories.DESKTOP;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() {
        session = new ClipboardSession();
        BufferedImage generated = ClipboardData.testImage();
        localImage = new ImageBox("read back from the system clipboard", null);
        foreignImage = new ImageBox("written by another process, read natively", null);
        localView = ChecksView.table("Round trips through the system clipboard (this process)",
                List.of(Check.info("state", "pending")));
        foreignView = ChecksView.table("Native round trip with another application",
                List.of(Check.info("state", "pending")));
        return Ui.column(14,
                Ui.text("System clipboard round trips on a background thread : text, HTML (all, selection and "
                        + "fragment flavors), RTF, image, file list, URL, a custom serializable flavor and a JVM-local "
                        + "object. Reading back data this JVM wrote never leaves the JVM : another process ("
                        + java.util.Objects.requireNonNullElse(ForeignClipboard.tool(), "none on this platform")
                        + ") reads the native formats and writes its own data, read through the native clipboard code. "
                        + "The user's clipboard is restored when the page is left.", 1000),
                Ui.row(16, new ImageBox("generated image (64 x 48)", generated), localImage, foreignImage),
                ChecksView.table("Data flavors and the flavor map", flavorChecks()),
                localView,
                foreignView);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ClipboardSession s = session;
        ChecksView local = localView;
        ChecksView foreign = foreignView;
        ImageBox localBox = localImage;
        ImageBox foreignBox = foreignImage;
        return Edt.background(s::run).thenAccept(result -> {
            local.setChecks(result.local());
            foreign.setChecks(result.foreign());
            localBox.setImage(result.localImage());
            foreignBox.setImage(result.foreignImage());
            Container parent = local.getParent();
            if (parent != null) {
                parent.invalidate();
                parent.validate();
            }
        });
    }

    @Override
    public void dispose(Component content) {
        if (session != null) {
            session.dispose();
        }
        session = null;
        localView = null;
        foreignView = null;
        localImage = null;
        foreignImage = null;
    }

    // ------------------------------------------------------------------------------------ flavors (no clipboard)

    private static List<Check> flavorChecks() {
        List<Check> checks = new ArrayList<>();
        // representation classes loaded by name from the MIME types
        checks.add(Checks.expect("DataFlavor text/plain charset=UTF-8 class=java.io.Reader", "java.io.Reader UTF-8 true",
                () -> {
                    DataFlavor f = ClipboardData.flavor("text/plain; charset=UTF-8; class=java.io.Reader");
                    return f.getRepresentationClass().getName() + " " + f.getParameter("charset") + " "
                            + f.isRepresentationClassReader();
                }));
        checks.add(Checks.expect("DataFlavor representation classes by name",
                "java.nio.CharBuffer java.nio.ByteBuffer [C [B java.io.InputStream java.net.URL java.util.List java.awt.Image",
                () -> String.join(" ", List.of("text/plain; class=java.nio.CharBuffer",
                        "application/octet-stream; class=java.nio.ByteBuffer", "text/plain; class=\"[C\"",
                        "text/plain; charset=utf-16le; class=\"[B\"", "text/plain; charset=UTF-8; class=java.io.InputStream",
                        "application/x-java-url; class=java.net.URL", "application/x-java-file-list; class=java.util.List",
                        "image/x-java-image; class=java.awt.Image").stream()
                        .map(m -> ClipboardData.flavor(m).getRepresentationClass().getName()).toList())));
        checks.add(Checks.expect("DataFlavor of an application class (serialized)",
                ClipboardPayload.class.getName() + " true", () -> {
                    DataFlavor f = ClipboardData.payloadFlavor();
                    return f.getRepresentationClass().getName() + " " + f.isFlavorSerializedObjectType();
                }));
        checks.add(Checks.expect("DataFlavor of an application class (JVM local)",
                ClipboardData.LocalReference.class.getName(),
                () -> ClipboardData.localFlavor().getRepresentationClass().getName()));
        checks.add(Checks.expect("imageFlavor / javaFileListFlavor from their MIME types", "true true true",
                () -> ClipboardData.flavor("image/x-java-image; class=java.awt.Image").equals(DataFlavor.imageFlavor)
                        + " " + ClipboardData.flavor("application/x-java-file-list; class=java.util.List")
                                .equals(DataFlavor.javaFileListFlavor)
                        + " " + DataFlavor.javaFileListFlavor.isFlavorJavaFileListType()));
        checks.add(Checks.expect("charset parameters are case insensitive", true,
                () -> ClipboardData.flavor("text/plain; charset=UTF-8; class=java.lang.String")
                        .equals(ClipboardData.flavor("text/plain; charset=utf-8; class=java.lang.String"))));
        checks.add(Checks.expect("HTML flavors (document parameter)", "all selection fragment",
                () -> DataFlavor.allHtmlFlavor.getParameter("document") + " "
                        + DataFlavor.selectionHtmlFlavor.getParameter("document") + " "
                        + DataFlavor.fragmentHtmlFlavor.getParameter("document")));
        checks.add(Checks.expect("stringFlavor", "application/x-java-serialized-object; class=java.lang.String",
                DataFlavor.stringFlavor::getMimeType));
        checks.add(Checks.expect("isMimeTypeEqual / isFlavorTextType", "true true false",
                () -> DataFlavor.allHtmlFlavor.isMimeTypeEqual("text/html") + " "
                        + DataFlavor.allHtmlFlavor.isFlavorTextType() + " "
                        + DataFlavor.imageFlavor.isFlavorTextType()));
        String unicode = Platforms.pick(null, "utf-16le", "iso-10646-ucs-2");
        Check unicodeCheck = unicode == null
                ? Checks.info("getTextPlainUnicodeFlavor() charset",
                        () -> DataFlavor.getTextPlainUnicodeFlavor().getParameter("charset"))
                // the default Unicode encoding comes from the platform data transfer service (a JDK service provider)
                : Checks.expect("getTextPlainUnicodeFlavor() charset", unicode,
                        () -> DataFlavor.getTextPlainUnicodeFlavor().getParameter("charset"));
        checks.add(unicodeCheck);
        checks.add(Checks.expect("stringFlavor.getReaderForText(StringSelection)", ClipboardData.TEXT, () -> {
            try (Reader reader = DataFlavor.stringFlavor.getReaderForText(new StringSelection(ClipboardData.TEXT))) {
                return ClipboardData.text(reader, StandardCharsets.UTF_8);
            }
        }));
        checks.add(Checks.expect("selectBestTextFlavor", "text/html; class=java.lang.String",
                () -> {
                    DataFlavor best = DataFlavor.selectBestTextFlavor(new DataFlavor[] {
                            ClipboardData.flavor("text/html; class=java.lang.String"),
                            ClipboardData.flavor("text/plain; charset=US-ASCII; class=java.io.InputStream"),
                            ClipboardData.flavor("text/plain; class=java.io.Reader") });
                    return best.getPrimaryType() + "/" + best.getSubType() + "; class="
                            + best.getRepresentationClass().getName();
                }));

        // the platform flavor map (flavormap.properties of java.datatransfer, and the desktop service provider)
        FlavorTable map = (FlavorTable) SystemFlavorMap.getDefaultFlavorMap();
        checks.add(Checks.info("natives of stringFlavor", () -> map.getNativesForFlavor(DataFlavor.stringFlavor)));
        checks.add(Checks.info("natives of allHtmlFlavor", () -> map.getNativesForFlavor(DataFlavor.allHtmlFlavor)));
        checks.add(Checks.info("natives of imageFlavor", () -> map.getNativesForFlavor(DataFlavor.imageFlavor)));
        checks.add(Checks.info("natives of javaFileListFlavor",
                () -> map.getNativesForFlavor(DataFlavor.javaFileListFlavor)));
        checks.add(Checks.info("natives of the URL flavor", () -> map.getNativesForFlavor(ClipboardData.urlFlavor())));
        String htmlNative = Platforms.pick("public.html", "HTML Format", "text/html");
        checks.add(Checks.info("flavors of native " + htmlNative,
                () -> ClipboardData.mimeTypes(map.getFlavorsForNative(htmlNative).toArray(DataFlavor[]::new))));
        checks.add(Checks.expect("encodeJavaMIMEType / isJavaMIMEType / decodeJavaMIMEType",
                "JAVA_DATAFLAVOR:text/plain true text/plain",
                () -> {
                    String encoded = SystemFlavorMap.encodeJavaMIMEType("text/plain");
                    return encoded + " " + SystemFlavorMap.isJavaMIMEType(encoded) + " "
                            + SystemFlavorMap.decodeJavaMIMEType(encoded);
                }));
        checks.add(Checks.expect("encodeDataFlavor(payload flavor)",
                "JAVA_DATAFLAVOR:application/x-java-serialized-object; class=" + ClipboardPayload.class.getName(),
                () -> SystemFlavorMap.encodeDataFlavor(ClipboardData.payloadFlavor())));
        checks.add(Checks.expect("custom native mapping (addUnencodedNativeForFlavor)", "[SHOWCASE_NATIVE] true", () -> {
            SystemFlavorMap m = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
            DataFlavor custom = ClipboardData.flavor("application/x-showcase-custom; class=java.io.InputStream");
            m.addUnencodedNativeForFlavor(custom, "SHOWCASE_NATIVE");
            m.addFlavorForUnencodedNative("SHOWCASE_NATIVE", custom);
            return m.getNativesForFlavor(custom) + " " + m.getFlavorsForNative("SHOWCASE_NATIVE").contains(custom);
        }));
        checks.add(Checks.expect("custom native mapping (setNativesForFlavor)", "[SHOWCASE_NATIVE_2] true", () -> {
            SystemFlavorMap m = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
            DataFlavor custom = ClipboardData.flavor("application/x-showcase-custom-2; class=java.io.InputStream");
            m.setNativesForFlavor(custom, new String[] { "SHOWCASE_NATIVE_2" });
            m.setFlavorsForNative("SHOWCASE_NATIVE_2", new DataFlavor[] { custom });
            return m.getNativesForFlavor(custom) + " " + m.getFlavorsForNative("SHOWCASE_NATIVE_2").contains(custom);
        }));
        return checks;
    }

    // ------------------------------------------------------------------------------------------ clipboard session

    record Result(List<Check> local, List<Check> foreign, BufferedImage localImage, BufferedImage foreignImage) {
    }

    /**
     * The clipboard work of one display of the page (background thread), and the restoration of the user's clipboard
     * when the page is left : by {@link #dispose()} if the work is over, else at the end of the work.
     */
    static final class ClipboardSession {

        private final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        private final List<String> lostOwnership = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger flavorEvents = new AtomicInteger();
        private final FlavorListener listener = e -> {
            if (e.getSource() == clipboard) {
                flavorEvents.incrementAndGet();
            }
        };
        private final Path tempDir = Edt.tempDir();
        private ClipboardData.SavedContents saved;
        private FocusLock lock;
        private boolean running;
        private boolean disposed;

        Result run() throws Exception {
            synchronized (this) {
                if (disposed) {
                    return new Result(List.of(Check.info("state", "cancelled")), List.of(), null, null);
                }
                running = true;
            }
            try {
                // the clipboard is shared by every showcase process : the machine-wide lock of the pages that need the
                // focus (keyboard pages copy and paste too) is held during the test (released before the next page of
                // this process asks for it : the user's clipboard is restored in dispose, a single write)
                lock = FocusLock.acquire(20_000).toCompletableFuture().get(25, TimeUnit.SECONDS);
                if (!lock.acquired()) {
                    LOG.warn("Clipboard page without the showcase lock : another showcase process may use the clipboard");
                }
                if (disposed) {
                    return new Result(List.of(Check.info("state", "cancelled")), List.of(), null, null);
                }
                ClipboardData.mapPrivateFlavors();
                saved = ClipboardData.SavedContents.save(clipboard);
                clipboard.addFlavorListener(listener);
                List<Check> local = new ArrayList<>();
                local.add(Check.info("user's clipboard saved", "restored when the page is left"));
                local.add(Checks.info("Toolkit.getSystemClipboard()", () -> clipboard.getClass().getName()));
                local.add(Checks.expect("Clipboard.getName()", "System", clipboard::getName));
                BufferedImage image = ClipboardData.testImage();
                roundTrips(local, image);
                privateClipboard(local);
                systemSelection(local);
                List<Check> foreign = new ArrayList<>();
                BufferedImage foreignImage = foreign(foreign, image);
                return new Result(local, foreign, image, foreignImage);
            } finally {
                clipboard.removeFlavorListener(listener);
                releaseLock();
                finish();
            }
        }

        private synchronized void finish() {
            running = false;
            if (disposed) {
                restore();
            }
        }

        synchronized void dispose() {
            disposed = true;
            if (!running) {
                restore();
            }
        }

        private synchronized void releaseLock() {
            if (lock != null) {
                lock.close();
                lock = null;
            }
        }

        private void restore() {
            ClipboardData.SavedContents contents = saved;
            saved = null;
            try {
                if (contents == null || !ownData()) {
                    return;
                }
                for (int attempt = 0; attempt < 5; attempt++) {
                    try {
                        clipboard.setContents(contents, null);
                        return;
                    } catch (IllegalStateException e) {
                        // another application has the clipboard open : retried
                    }
                }
                Thread retry = new Thread(() -> {
                    for (int attempt = 0; attempt < 20; attempt++) {
                        DesktopSupport.sleep(100);
                        try {
                            clipboard.setContents(contents, null);
                            return;
                        } catch (IllegalStateException e) {
                            // retried
                        }
                    }
                }, "showcase-clipboard-restore");
                retry.setDaemon(true);
                retry.start();
            } finally {
                releaseLock();
            }
        }

        /**
         * {@code true} when the clipboard still holds the data of the page (or of its foreign application) : data
         * another application wrote meanwhile is newer than the user's saved data, and is not overwritten.
         */
        private boolean ownData() {
            String current;
            try {
                current = clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
                        ? String.valueOf(clipboard.getData(DataFlavor.stringFlavor))
                        : null;
            } catch (Exception e) {
                return true;
            }
            boolean own = current == null || List.of(ClipboardData.TEXT, ClipboardData.FOREIGN_TEXT, ClipboardData.URL,
                    "Quarkus desktop clipboard").contains(current);
            if (!own) {
                LOG.warn("The clipboard was changed by another application during the clipboard page : not restored");
            }
            return own;
        }

        // clipboard reads, retried while another application has the clipboard open (IllegalStateException)

        private Object getData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            for (int attempt = 0;; attempt++) {
                try {
                    return clipboard.getData(flavor);
                } catch (IllegalStateException e) {
                    if (attempt >= 20) {
                        throw e;
                    }
                    DesktopSupport.sleep(50);
                }
            }
        }

        private boolean isAvailable(DataFlavor flavor) {
            return retried(() -> clipboard.isDataFlavorAvailable(flavor));
        }

        private DataFlavor[] availableFlavors() {
            return retried(clipboard::getAvailableDataFlavors);
        }

        private Transferable contents() {
            return retried(() -> clipboard.getContents(null));
        }

        private static <T> T retried(Supplier<T> read) {
            for (int attempt = 0;; attempt++) {
                try {
                    return read.get();
                } catch (IllegalStateException e) {
                    if (attempt >= 20) {
                        throw e;
                    }
                    DesktopSupport.sleep(50);
                }
            }
        }

        private ClipboardOwner owner(String name) {
            return (c, contents) -> {
                if (!lostOwnership.contains(name)) {
                    lostOwnership.add(name);
                }
            };
        }

        /**
         * Sets {@code contents} and runs {@code verify}, up to 3 times while a check fails (another application may
         * have changed the clipboard meanwhile).
         */
        private List<Check> step(String name, Transferable contents, Supplier<List<Check>> verify) {
            List<Check> checks = List.of();
            for (int attempt = 0; attempt < 3 && !disposed; attempt++) {
                try {
                    clipboard.setContents(contents, owner(name));
                } catch (IllegalStateException e) {
                    checks = List.of(Check.fail(name + ": setContents", Checks.describe(e)));
                    DesktopSupport.sleep(150);
                    continue;
                }
                checks = verify.get();
                if (checks.stream().noneMatch(c -> Boolean.FALSE.equals(c.ok()))) {
                    break;
                }
                DesktopSupport.sleep(200);
            }
            return checks;
        }

        private void roundTrips(List<Check> checks, BufferedImage image) throws IOException {
            String text = ClipboardData.TEXT;
            Transferable textContents = new ClipboardData.MapTransferable()
                    .with(DataFlavor.stringFlavor, () -> text).withPrivateFlavors();
            checks.addAll(step("text", textContents, () -> List.of(
                    Checks.expect("text: isDataFlavorAvailable(stringFlavor)", true,
                            () -> isAvailable(DataFlavor.stringFlavor)),
                    Checks.expect("text: getData(stringFlavor)", text, () -> getData(DataFlavor.stringFlavor)),
                    Checks.expect("text: getContents().getTransferData", text,
                            () -> contents().getTransferData(DataFlavor.stringFlavor)),
                    Checks.expect("text: getAvailableDataFlavors()",
                            "[application/x-java-serialized-object; class=java.lang.String]",
                            () -> ClipboardData.mimeTypes(availableFlavors())),
                    DesktopSupport.expectThrows("text: getData(unsupported flavor)", UnsupportedFlavorException.class,
                            () -> getData(ClipboardData.flavor("application/x-unknown; class=java.lang.String"))))));

            Transferable stringSelection = new StringSelection(text);
            checks.addAll(step("string selection", stringSelection, () -> List.of(
                    Checks.expect("StringSelection: getData(stringFlavor)", text,
                            () -> getData(DataFlavor.stringFlavor)),
                    Checks.expect("StringSelection: flavors",
                            "[application/x-java-serialized-object; class=java.lang.String, "
                                    + "text/plain; class=java.io.InputStream]",
                            () -> ClipboardData.mimeTypes(availableFlavors())))));

            Transferable html = new ClipboardData.MapTransferable()
                    .with(DataFlavor.allHtmlFlavor, () -> ClipboardData.HTML_ALL)
                    .with(DataFlavor.selectionHtmlFlavor, () -> ClipboardData.HTML_SELECTION)
                    .with(DataFlavor.fragmentHtmlFlavor, () -> ClipboardData.HTML_FRAGMENT)
                    .with(DataFlavor.stringFlavor, () -> "Quarkus desktop clipboard")
                    .withPrivateFlavors();
            checks.addAll(step("html", html, () -> List.of(
                    Checks.expect("HTML: getData(allHtmlFlavor)", ClipboardData.HTML_ALL,
                            () -> getData(DataFlavor.allHtmlFlavor)),
                    Checks.expect("HTML: getData(selectionHtmlFlavor)", ClipboardData.HTML_SELECTION,
                            () -> getData(DataFlavor.selectionHtmlFlavor)),
                    Checks.expect("HTML: getData(fragmentHtmlFlavor)", ClipboardData.HTML_FRAGMENT,
                            () -> getData(DataFlavor.fragmentHtmlFlavor)),
                    Checks.expect("HTML: getData(stringFlavor)", "Quarkus desktop clipboard",
                            () -> getData(DataFlavor.stringFlavor)))));

            DataFlavor rtf = ClipboardData.rtfFlavor();
            Transferable rtfContents = new ClipboardData.MapTransferable()
                    .with(rtf, () -> new java.io.ByteArrayInputStream(ClipboardData.RTF.getBytes(StandardCharsets.US_ASCII)))
                    .withPrivateFlavors();
            checks.addAll(step("rtf", rtfContents, () -> List.of(
                    Checks.expect("RTF: getData(text/rtf InputStream)", ClipboardData.RTF,
                            () -> ClipboardData.text(getData(rtf), StandardCharsets.US_ASCII)))));

            Transferable imageContents = new ClipboardData.MapTransferable()
                    .with(DataFlavor.imageFlavor, () -> image).withPrivateFlavors();
            checks.addAll(step("image", imageContents, () -> List.of(
                    Checks.expect("image: isDataFlavorAvailable(imageFlavor)", true,
                            () -> isAvailable(DataFlavor.imageFlavor)),
                    Checks.expect("image: getData(imageFlavor) is the same image (local transfer)", true,
                            () -> getData(DataFlavor.imageFlavor) == image),
                    Checks.expect("image: pixels", Checks.sha256(ClipboardData.testImage()),
                            () -> Checks.sha256((BufferedImage) getData(DataFlavor.imageFlavor))))));

            List<File> files = new ArrayList<>();
            for (String name : ClipboardData.FILE_NAMES) {
                Path file = tempDir.resolve(name);
                Files.writeString(file, name + "\n");
                files.add(file.toFile());
            }
            Transferable fileContents = new ClipboardData.MapTransferable()
                    .with(DataFlavor.javaFileListFlavor, () -> files).withPrivateFlavors();
            checks.addAll(step("files", fileContents, () -> List.of(
                    Checks.expect("file list: getData(javaFileListFlavor)", ClipboardData.FILE_NAMES,
                            () -> names(getData(DataFlavor.javaFileListFlavor))))));

            DataFlavor urlFlavor = ClipboardData.urlFlavor();
            URL url = URI.create(ClipboardData.URL).toURL();
            Transferable urlContents = new ClipboardData.MapTransferable()
                    .with(urlFlavor, () -> url).with(DataFlavor.stringFlavor, () -> ClipboardData.URL)
                    .withPrivateFlavors();
            checks.addAll(step("url", urlContents, () -> List.of(
                    Checks.expect("URL: getData(application/x-java-url)", ClipboardData.URL,
                            () -> String.valueOf(getData(urlFlavor))))));

            DataFlavor payloadFlavor = ClipboardData.payloadFlavor();
            ClipboardPayload payload = ClipboardData.payload();
            Transferable payloadContents = new ClipboardData.MapTransferable()
                    .with(payloadFlavor, () -> payload).withPrivateFlavors();
            checks.addAll(step("serializable", payloadContents, () -> List.of(
                    Checks.expect("serializable: getData(custom flavor)", payload.toString(),
                            () -> String.valueOf(getData(payloadFlavor))),
                    // a serialized flavor is copied (serialized and deserialized) even within the JVM
                    Checks.expect("serializable: equal copy, not the same instance", "true false", () -> {
                        Object copy = getData(payloadFlavor);
                        return payload.equals(copy) + " " + (copy == payload);
                    }))));

            DataFlavor localFlavor = ClipboardData.localFlavor();
            ClipboardData.LocalReference reference = new ClipboardData.LocalReference("jvm local reference");
            Transferable localContents = new ClipboardData.MapTransferable()
                    .with(localFlavor, () -> reference).withPrivateFlavors();
            checks.addAll(step("local", localContents, () -> List.of(
                    Checks.expect("JVM-local object: getData returns the same instance", true,
                            () -> getData(localFlavor) == reference))));

            checks.add(Checks.expect("FlavorListener notified", true, () -> DesktopSupport.await(
                    () -> flavorEvents.get() > 0, 3000)));
            checks.add(Checks.expect("ClipboardOwner.lostOwnership (replaced contents)",
                    "[text, string selection, html, rtf, image, files, url, serializable]",
                    () -> {
                        DesktopSupport.await(() -> lostOwnership.contains("serializable"), 3000);
                        return owners("text", "string selection", "html", "rtf", "image", "files", "url",
                                "serializable");
                    }));
        }

        private List<String> owners(String... expected) {
            List<String> lost;
            synchronized (lostOwnership) {
                lost = new ArrayList<>(lostOwnership);
            }
            List<String> names = Arrays.asList(expected);
            return lost.stream().filter(names::contains).toList();
        }

        /**
         * A private {@code Clipboard} (no native code) : its notifications run on the event dispatch thread through the
         * platform data transfer service (a JDK service provider), or synchronously when that provider is missing.
         */
        private void privateClipboard(List<Check> checks) {
            Clipboard clip = new Clipboard("showcase");
            List<String> events = Collections.synchronizedList(new ArrayList<>());
            clip.addFlavorListener(e -> events.add("flavorsChanged on EDT " + EventQueue.isDispatchThread()));
            clip.setContents(new StringSelection("first"), (c, t) -> events.add("lostOwnership on EDT "
                    + EventQueue.isDispatchThread()));
            // other flavors : the flavor listeners are notified again
            clip.setContents(new ClipboardData.MapTransferable().with(DataFlavor.allHtmlFlavor, () -> "second"), null);
            checks.add(Checks.expect("private Clipboard: getData", "second showcase",
                    () -> clip.getData(DataFlavor.allHtmlFlavor) + " " + clip.getName()));
            checks.add(Checks.expect("private Clipboard: notifications (sorted)", "[flavorsChanged on EDT true, "
                    + "flavorsChanged on EDT true, lostOwnership on EDT true]", () -> {
                        DesktopSupport.await(() -> events.size() >= 3, 3000);
                        synchronized (events) {
                            // the order of the two notifications of the second setContents is not specified
                            return events.stream().sorted().toList();
                        }
                    }));
        }

        /**
         * The X11 PRIMARY selection (Linux) : {@code null} on Windows and macOS.
         */
        private void systemSelection(List<Check> checks) {
            Clipboard selection = Toolkit.getDefaultToolkit().getSystemSelection();
            if (!Platforms.isLinux()) {
                checks.add(Checks.expect("Toolkit.getSystemSelection()", "null", () -> String.valueOf(selection)));
                return;
            }
            if (selection == null) {
                checks.add(Check.fail("Toolkit.getSystemSelection()", "null on Linux"));
                return;
            }
            // the selection is not restored : as any application selecting text, the page replaces it
            selection.setContents(new StringSelection("Quarkus primary selection"), null);
            checks.add(Checks.expect("system selection: round trip", "Quarkus primary selection",
                    () -> selection.getData(DataFlavor.stringFlavor)));
        }

        // ------------------------------------------------------------------------------------- foreign process

        private BufferedImage foreign(List<Check> checks, BufferedImage image) throws Exception {
            String tool = ForeignClipboard.tool();
            if (tool == null) {
                checks.add(Check.info("foreign application", "skipped: none on this platform"
                        + (Platforms.isLinux() ? " (xclip not installed)" : "")));
                return null;
            }
            checks.add(Check.info("foreign application", tool));
            return Platforms.isWindows() ? foreignWindows(checks, image) : foreignLinux(checks);
        }

        private Transferable combined(BufferedImage image) throws IOException {
            List<File> files = new ArrayList<>();
            for (String name : ClipboardData.FILE_NAMES) {
                files.add(tempDir.resolve(name).toFile());
            }
            URL url = URI.create(ClipboardData.URL).toURL();
            ClipboardPayload payload = ClipboardData.payload();
            return new ClipboardData.MapTransferable()
                    .with(DataFlavor.stringFlavor, () -> ClipboardData.TEXT)
                    // the fragment flavor : AWT adds the CF_HTML header (the "all" and "selection" flavors are published
                    // as they are)
                    .with(DataFlavor.fragmentHtmlFlavor, () -> ClipboardData.HTML_FRAGMENT)
                    .with(ClipboardData.rtfFlavor(),
                            () -> new java.io.ByteArrayInputStream(ClipboardData.RTF.getBytes(StandardCharsets.US_ASCII)))
                    .with(DataFlavor.imageFlavor, () -> image)
                    .with(DataFlavor.javaFileListFlavor, () -> files)
                    .with(ClipboardData.urlFlavor(), () -> url)
                    .with(ClipboardData.payloadFlavor(), () -> payload)
                    .withPrivateFlavors();
        }

        private BufferedImage foreignWindows(List<Check> checks, BufferedImage image) throws Exception {
            String serialFormat = ((FlavorTable) SystemFlavorMap.getDefaultFlavorMap())
                    .getNativesForFlavor(ClipboardData.payloadFlavor()).getFirst();
            List<Check> attemptChecks = List.of();
            BufferedImage foreignImage = null;
            for (int attempt = 0; attempt < 2 && !disposed; attempt++) {
                attemptChecks = new ArrayList<>();
                lostOwnership.remove("combined");
                int eventsBefore = flavorEvents.get();
                Transferable combined = combined(image);
                retried(() -> {
                    clipboard.setContents(combined, owner("combined"));
                    return null;
                });
                ForeignClipboard.Result read = ForeignClipboard.runWindows(serialFormat);
                foreignImage = verifyWindows(attemptChecks, read, eventsBefore);
                if (attemptChecks.stream().noneMatch(c -> Boolean.FALSE.equals(c.ok()))) {
                    break;
                }
                DesktopSupport.sleep(300);
            }
            checks.addAll(attemptChecks);
            return foreignImage;
        }

        private BufferedImage verifyWindows(List<Check> checks, ForeignClipboard.Result read, int eventsBefore) {
            if (read.error() != null) {
                checks.add(Check.fail("foreign application run", read.error()));
            }
            // 1. the native formats written by the showcase, as read by the other application
            checks.add(Check.info("native formats written by the showcase", read.get("formats")));
            checks.add(Checks.expect("native: HTML Format (header added by AWT, document)", "Version:1.0 true",
                    () -> String.valueOf(read.get("html")).lines().findFirst().orElse("-") + " "
                            + String.valueOf(read.get("html")).endsWith("\n<HTML><BODY>" + ClipboardData.HTML_FRAGMENT
                                    + "</BODY></HTML>")));
            // AWT publishes the bytes of the text/rtf stream as they are, without a terminating NUL : what follows them
            // in the global memory block is not part of the check
            checks.add(Checks.expect("native: Rich Text Format starts with the RTF written", true,
                    () -> String.valueOf(read.get("rtf")).startsWith(ClipboardData.RTF)));
            String expectedImage = ClipboardData.IMAGE_WIDTH + "x" + ClipboardData.IMAGE_HEIGHT + " "
                    + String.format(java.util.Locale.ROOT, "%08X %08X", 0xFF000000 | ClipboardData.QUADRANTS[0],
                            0xFF000000 | ClipboardData.QUADRANTS[3]);
            checks.add(Checks.expect("native: bitmap (size, 2 pixels)", expectedImage, () -> read.get("image")));
            checks.add(Checks.expect("native: file drop list", String.join("\n", ClipboardData.FILE_NAMES),
                    () -> read.get("files")));
            // the text flavor maps to UniformResourceLocator too (flavormap.properties), and AWT uses it rather than the
            // URL flavor for that format : the text, in the ANSI code page
            checks.add(Check.info("native: UniformResourceLocator (from the text flavor)", read.get("url")));
            checks.add(Checks.expect("native: serialized payload present", true,
                    () -> read.get("serialized") != null && !read.get("serialized").equals("-")));
            checks.add(Checks.expect("native: clipboard history and monitor exclusion formats", "true true",
                    () -> {
                        List<String> formats = List.of(String.valueOf(read.get("formats")).split("\n"));
                        return formats.contains(ClipboardData.NATIVE_NO_HISTORY) + " "
                                + formats.contains(ClipboardData.NATIVE_NO_MONITOR);
                    }));
            checks.add(Checks.expect("foreign data written", "true", () -> read.get("written")));

            // 2. the foreign data, read through the native clipboard code
            checks.add(Checks.expect("ClipboardOwner.lostOwnership (foreign write)", true,
                    () -> DesktopSupport.await(() -> lostOwnership.contains("combined"), 5000)));
            checks.add(Checks.expect("FlavorListener notified (foreign write)", true,
                    () -> DesktopSupport.await(() -> flavorEvents.get() > eventsBefore, 3000)));
            checks.add(Checks.info("foreign: getAvailableDataFlavors()",
                    () -> ClipboardData.mimeTypes(availableFlavors()).stream()
                            .map(s -> s.substring(0, s.indexOf(';'))).distinct().toList()));
            checks.add(Checks.expect("foreign: isDataFlavorAvailable (string, html, image, files)", "true true true true",
                    () -> isAvailable(DataFlavor.stringFlavor) + " "
                            + isAvailable(DataFlavor.allHtmlFlavor) + " "
                            + isAvailable(DataFlavor.imageFlavor) + " "
                            + isAvailable(DataFlavor.javaFileListFlavor)));
            checks.add(Checks.expect("foreign: getData(stringFlavor)", ClipboardData.FOREIGN_TEXT,
                    () -> getData(DataFlavor.stringFlavor)));
            textRepresentations(checks);
            // the String HTML flavors return the text of the native format (CF_HTML header included), the stream and
            // reader flavors go through the CF_HTML decoder (HTMLCodec), which honours the document parameter
            checks.add(Checks.expect("foreign: getData(fragmentHtmlFlavor) (String : raw CF_HTML)", "Version:0.9 true",
                    () -> {
                        String html = String.valueOf(getData(DataFlavor.fragmentHtmlFlavor));
                        return html.lines().findFirst().orElse("-") + " " + html.contains(ClipboardData.FOREIGN_HTML);
                    }));
            String[] documents = { "fragment", "selection", "all" };
            List<String> decoded = new ArrayList<>();
            for (String document : documents) {
                decoded.add(DesktopSupport.orElse(() -> ClipboardData.text(getData(ClipboardData.flavor(
                        "text/html; document=" + document + "; class=java.io.Reader")), StandardCharsets.UTF_8),
                        "failed"));
            }
            checks.add(Checks.expect("foreign: HTML fragment / selection (Reader, decoded)",
                    ClipboardData.FOREIGN_HTML + " | " + ClipboardData.FOREIGN_HTML,
                    () -> decoded.get(0) + " | " + decoded.get(1)));
            checks.add(Checks.expect("foreign: HTML all (Reader, decoded)",
                    "<html><body><!--StartFragment-->" + ClipboardData.FOREIGN_HTML + "<!--EndFragment--></body></html>",
                    () -> decoded.get(2)));
            checks.add(Checks.expect("foreign: getData(text/rtf)", ClipboardData.FOREIGN_RTF,
                    () -> ClipboardData.trimNul(ClipboardData.text(getData(ClipboardData.rtfFlavor()),
                            StandardCharsets.US_ASCII))));
            BufferedImage[] foreignImage = new BufferedImage[1];
            checks.add(Checks.expect("foreign: getData(imageFlavor) size and pixels",
                    "40x30 " + DesktopSupport.rgb(ClipboardData.FOREIGN_BACKGROUND) + " "
                            + DesktopSupport.rgb(ClipboardData.FOREIGN_SQUARE),
                    () -> {
                        BufferedImage img = toBufferedImage((Image) getData(DataFlavor.imageFlavor));
                        foreignImage[0] = img;
                        return img.getWidth() + "x" + img.getHeight() + " " + DesktopSupport.rgb(img.getRGB(30, 20))
                                + " " + DesktopSupport.rgb(img.getRGB(9, 9));
                    }));
            checks.add(Checks.expect("foreign: getData(javaFileListFlavor)", List.of(ClipboardData.FOREIGN_FILE),
                    () -> names(getData(DataFlavor.javaFileListFlavor))));
            checks.add(Checks.expect("foreign: getData(URL flavor)", ClipboardData.FOREIGN_URL,
                    () -> String.valueOf(getData(ClipboardData.urlFlavor()))));
            checks.add(Checks.expect("foreign: serialized payload deserialized natively", ClipboardData.payload().toString(),
                    () -> String.valueOf(getData(ClipboardData.payloadFlavor()))));
            return foreignImage[0];
        }

        /**
         * The foreign text read in every text representation class (the data transfer code converts the native bytes).
         */
        private void textRepresentations(List<Check> checks) {
            String expected = ClipboardData.FOREIGN_TEXT;
            Object[][] cases = {
                    { "text/plain; class=java.io.Reader", StandardCharsets.UTF_8 },
                    { "text/plain; charset=UTF-8; class=java.io.InputStream", StandardCharsets.UTF_8 },
                    { "text/plain; class=java.nio.CharBuffer", StandardCharsets.UTF_8 },
                    { "text/plain; charset=UTF-16LE; class=java.nio.ByteBuffer", StandardCharsets.UTF_16LE },
                    { "text/plain; class=\"[C\"", StandardCharsets.UTF_8 },
                    { "text/plain; charset=UTF-8; class=\"[B\"", StandardCharsets.UTF_8 },
            };
            List<String> results = new ArrayList<>();
            for (Object[] c : cases) {
                String mime = (String) c[0];
                try {
                    String value = ClipboardData.text(getData(ClipboardData.flavor(mime)), (Charset) c[1]);
                    results.add(expected.equals(value) ? "ok" : "differs: " + value);
                } catch (Exception e) {
                    results.add(Checks.describe(e));
                }
            }
            checks.add(Checks.expect("foreign: text as Reader, InputStream, CharBuffer, ByteBuffer, char[], byte[]",
                    "[ok, ok, ok, ok, ok, ok]", results::toString));
            checks.add(Checks.expect("foreign: getTextPlainUnicodeFlavor()", expected, () -> {
                DataFlavor unicode = DataFlavor.getTextPlainUnicodeFlavor();
                return ClipboardData.text(getData(unicode), Charset.forName(unicode.getParameter("charset")));
            }));
            checks.add(Checks.info("foreign: selectBestTextFlavor + getReaderForText", () -> {
                Transferable contents = contents();
                DataFlavor best = DataFlavor.selectBestTextFlavor(contents.getTransferDataFlavors());
                return best.getMimeType() + " -> "
                        + ClipboardData.text(best.getReaderForText(contents), StandardCharsets.UTF_8);
            }));
        }

        private BufferedImage foreignLinux(List<Check> checks) throws Exception {
            // Linux checks are informational (not verified yet on every desktop), except the text round trips
            Transferable combined = combined(ClipboardData.testImage());
            retried(() -> {
                clipboard.setContents(combined, owner("combined"));
                return null;
            });
            ForeignClipboard.Result read = ForeignClipboard.readLinux();
            checks.add(Check.info("native targets written by the showcase", read.get("formats")));
            checks.add(Checks.expect("native: UTF8_STRING", ClipboardData.TEXT, () -> read.get("text")));
            ForeignClipboard.Result write = ForeignClipboard.writeLinux();
            checks.add(Checks.expect("foreign data written", "true", () -> write.get("written")));
            checks.add(Checks.expect("ClipboardOwner.lostOwnership (foreign write)", true,
                    () -> DesktopSupport.await(() -> lostOwnership.contains("combined"), 5000)));
            checks.add(Checks.expect("foreign: getData(stringFlavor)", ClipboardData.FOREIGN_TEXT,
                    () -> getData(DataFlavor.stringFlavor)));
            return null;
        }

        private static List<String> names(Object files) {
            List<String> names = new ArrayList<>();
            for (Object f : (List<?>) files) {
                names.add(((File) f).getName());
            }
            return names;
        }

        private static BufferedImage toBufferedImage(Image image) {
            if (image instanceof BufferedImage b) {
                return b;
            }
            BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = copy.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            return copy;
        }
    }

    // ------------------------------------------------------------------------------------------------------- views

    /**
     * An image in a framed box with a caption (a placeholder while the image is not available).
     */
    static final class ImageBox extends Component {

        private static final int WIDTH = 240;
        private static final int HEIGHT = 110;
        private final String caption;
        private BufferedImage image;

        ImageBox(String caption, BufferedImage image) {
            this.caption = caption;
            this.image = image;
            setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(WIDTH, HEIGHT);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void paint(Graphics graphics) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0xF5F7FA));
                g.fillRect(0, 0, WIDTH, HEIGHT - 18);
                g.setColor(new Color(0xB0BEC5));
                g.drawRect(0, 0, WIDTH - 1, HEIGHT - 19);
                g.setFont(getFont());
                if (image != null) {
                    int x = (WIDTH - image.getWidth()) / 2;
                    int y = (HEIGHT - 18 - image.getHeight()) / 2;
                    g.drawImage(image, x, y, null);
                } else {
                    g.setColor(new Color(0x78909C));
                    g.drawString("not available", 10, 20);
                }
                g.setColor(new Color(0x455A64));
                g.drawString(caption, 0, HEIGHT - 4);
            } finally {
                g.dispose();
            }
        }
    }
}
