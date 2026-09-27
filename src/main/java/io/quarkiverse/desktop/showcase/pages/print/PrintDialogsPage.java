package io.quarkiverse.desktop.showcase.pages.print;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.JobAttributes;
import java.awt.KeyboardFocusManager;
import java.awt.PageAttributes;
import java.awt.PrintJob;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.ServiceUI;
import javax.print.StreamPrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.DialogOwner;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.a11y.AccessibleDump;

/**
 * The cross-platform print dialogs : {@link PrinterJob#printDialog(PrintRequestAttributeSet)},
 * {@link PrinterJob#pageDialog(PrintRequestAttributeSet)}, {@link ServiceUI#printDialog} and the AWT
 * {@link Toolkit#getPrintJob(Frame, String, JobAttributes, PageAttributes)} with a common dialog. They are all
 * {@code sun.print.ServiceDialog}, a Swing {@code JDialog} used by AWT : this page works without application Swing code
 * (awt-only variant), the JDK's Swing classes, look and feel resources, print icons and the {@code serviceui} resource
 * bundle must be in a native executable anyway.
 * <p>
 * Each dialog is opened by a helper thread (the call blocks until the modal dialog closes), found among the windows,
 * captured with {@code printAll} once stable (without focus owner : no caret, no focus ring), inspected through its
 * accessible contexts (tabs, buttons, mnemonics, icons : this AWT page cannot reference Swing classes), then cancelled
 * programmatically by the accessible action of its Cancel button : nothing is ever printed. The print dialog of the
 * {@code PrinterJob} lists the stream print services only (the job prints to a PostScript stream), the AWT
 * {@code PrintJob} dialog lists the printers of the machine ; on a machine without any printer (a Linux container
 * without CUPS) it is the "no print service" message of {@code ServiceDialog} instead. Outside Windows the print dialog
 * has an output tray combo box.
 * <p>
 * The native Windows print and page setup dialogs cannot be closed programmatically : they are only opened with
 * {@code -Dshowcase.interactive=true}.
 */
@Singleton
public class PrintDialogsPage implements FeaturePage {

    private static final double THUMBNAIL_SCALE = 0.45;
    private static final long APPEAR_MILLIS = 15_000;

    private ChecksView results;
    private Container thumbnails;
    private final Map<String, BufferedImage> captures = new LinkedHashMap<>();
    private final Set<Window> opened = Collections.newSetFromMap(new IdentityHashMap<>());

    private record DialogRun(String key, String title, Callable<String> action) {
    }

    @Override
    public String id() {
        return "print-dialogs";
    }

    @Override
    public String title() {
        return "Print dialogs";
    }

    @Override
    public String category() {
        return Categories.PRINTING;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Component build() {
        captures.clear();
        thumbnails = Ui.row(16);
        results = ChecksView.table("Dialogs (opened by a helper thread, captured, cancelled)",
                List.of(Check.info("dialogs", "pending")));
        return Ui.column(14,
                Ui.text("The cross-platform print dialogs (sun.print.ServiceDialog, a Swing JDialog also used by AWT "
                        + "applications) opened by PrinterJob, ServiceUI and Toolkit.getPrintJob, captured, inspected "
                        + "through their accessible contexts and cancelled programmatically : nothing is printed.", 1000),
                thumbnails,
                results);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView view = results;
        Container row = thumbnails;
        Frame owner = frameOf(content);
        List<Check> checks = Collections.synchronizedList(new ArrayList<>());
        List<DialogRun> runs = List.of(
                new DialogRun("printDialog", "PrinterJob.printDialog(attributes)", PrintDialogsPage::printDialog),
                new DialogRun("pageDialog", "PrinterJob.pageDialog(attributes)", PrintDialogsPage::pageDialog),
                new DialogRun("serviceUI", "ServiceUI.printDialog(...)", () -> serviceUi(owner)),
                new DialogRun("printJob", "Toolkit.getPrintJob(frame, title, COMMON dialog)", () -> toolkitPrintJob(owner)));
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (DialogRun run : runs) {
            chain = chain.thenComposeAsync(v -> open(run, checks), Edt.EDT);
        }
        return chain.thenComposeAsync(v -> interactive(checks), Edt.EDT).thenRun(() -> {
            captures.forEach((key, image) -> row.add(Ui.column(4, Ui.image(scale(image)), Ui.caption(key))));
            synchronized (checks) {
                view.setChecks(List.copyOf(checks));
            }
        });
    }

    @Override
    public CompletionStage<Map<String, BufferedImage>> extraSnapshots(Component content) {
        return CompletableFuture.completedFuture(Map.copyOf(captures));
    }

    @Override
    public void dispose(Component content) {
        disposeOpened();
        captures.clear();
        results = null;
        thumbnails = null;
    }

    // ------------------------------------------------------------------------------------------------- dialogs

    private static PrintRequestAttributeSet attributes() {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(2));
        attributes.add(MediaSizeName.ISO_A4);
        attributes.add(OrientationRequested.LANDSCAPE);
        attributes.add(new JobName("quarkus-desktop-showcase", null));
        return attributes;
    }

    /**
     * A {@link PrinterJob} printing to the PostScript stream : its dialog lists the stream print services only.
     */
    private static PrinterJob streamJob() throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(PrintSupport.postScriptService(new ByteArrayOutputStream()));
        job.setPageable(SampleBook.book(null));
        return job;
    }

    private static String printDialog() throws Exception {
        PrinterJob job = streamJob();
        PrintRequestAttributeSet attributes = attributes();
        boolean approved = job.printDialog(attributes);
        return approved + ", copies " + attributes.get(Copies.class) + ", " + attributes.get(OrientationRequested.class);
    }

    private static String pageDialog() throws Exception {
        PrinterJob job = streamJob();
        PageFormat format = job.pageDialog(attributes());
        return String.valueOf(format);
    }

    private static String serviceUi(Frame owner) {
        StreamPrintService service = PrintSupport.postScriptService(new ByteArrayOutputStream());
        PrintRequestAttributeSet attributes = attributes();
        if (owner != null) {
            attributes.add(new DialogOwner(owner));
        }
        PrintService selected = ServiceUI.printDialog(null, 60, 60, new PrintService[] { service }, service,
                DocFlavor.SERVICE_FORMATTED.PRINTABLE, attributes);
        return String.valueOf(selected);
    }

    private static String toolkitPrintJob(Frame owner) {
        if (owner == null) {
            return "no owner frame";
        }
        JobAttributes job = new JobAttributes();
        // the default dialog type is NATIVE
        job.setDialog(JobAttributes.DialogType.COMMON);
        PrintJob printJob = Toolkit.getDefaultToolkit().getPrintJob(owner, "quarkus-desktop-showcase", job,
                new PageAttributes());
        if (printJob != null) {
            // never reached when cancelled ; end() without any page prints nothing
            printJob.end();
            return "a PrintJob";
        }
        return "null";
    }

    /**
     * Opens the dialog of {@code run} (helper thread), captures and inspects it, then cancels it.
     */
    private CompletionStage<Void> open(DialogRun run, List<Check> checks) {
        Set<Window> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(List.of(Window.getWindows()));
        CompletableFuture<String> result = Edt.background(run.action()).toCompletableFuture();
        Dialog[] dialog = new Dialog[1];
        return Edt.until(() -> {
            dialog[0] = newDialog(before);
            return dialog[0] != null || result.isDone();
        }, APPEAR_MILLIS, run.title())
                .thenCompose(v -> dialog[0] == null ? CompletableFuture.completedFuture(null)
                        : Edt.stable(dialog[0], 3000))
                .thenCompose(v -> {
                    if (dialog[0] != null) {
                        opened.add(dialog[0]);
                        // no focus owner : no caret, no focus ring, whatever the window activation
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
                    }
                    return Edt.rounds(3);
                })
                .thenCompose(v -> dialog[0] == null ? CompletableFuture.completedFuture(null)
                        : Edt.stable(dialog[0], 2000))
                .thenCompose(v -> {
                    if (dialog[0] == null) {
                        checks.add(Check.fail(run.key() + " : dialog", "no dialog opened"));
                    } else {
                        // the client area only (the root pane) : the native title bar depends on the window activation
                        Component root = dialog[0].getComponentCount() > 0 ? dialog[0].getComponent(0) : dialog[0];
                        captures.put(run.key(), Snapshots.render(root));
                        checks.addAll(inspect(run.key(), dialog[0]));
                        checks.add(Check.info(run.key() + " : cancelled by", cancel(dialog[0])));
                    }
                    return Edt.timeout(result, 10_000, run.title() + " to return");
                })
                .handle((value, error) -> {
                    checks.add(error == null ? Check.pass(run.key() + " : returned", value)
                            : Check.fail(run.key() + " : returned", Checks.describe(unwrap(error))));
                    disposeOpened();
                    return null;
                });
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause()
                : error;
    }

    private static Dialog newDialog(Set<Window> before) {
        for (Window window : Window.getWindows()) {
            if (window instanceof Dialog d && window.isShowing() && !before.contains(window)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Presses the Cancel button through its accessible action (the Swing button's {@code doClick}), or sends a window
     * closing event (the dialog cancels), or disposes the dialog.
     */
    private static String cancel(Dialog dialog) {
        for (AccessibleContext button : AccessibleDump.byRole(dialog, "push button")) {
            AccessibleAction action = button.getAccessibleAction();
            if ("Cancel".equals(button.getAccessibleName()) && action != null && action.getAccessibleActionCount() > 0) {
                action.doAccessibleAction(0);
                return "accessible action \"" + action.getAccessibleActionDescription(0) + "\" of the Cancel button";
            }
        }
        dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
        if (!dialog.isShowing()) {
            return "window closing event";
        }
        dialog.dispose();
        return "dispose";
    }

    private void disposeOpened() {
        for (Window window : List.copyOf(opened)) {
            if (window.isDisplayable()) {
                window.dispose();
            }
        }
        opened.clear();
    }

    // ------------------------------------------------------------------------------------------------- inspection

    private static List<Check> inspect(String key, Dialog dialog) {
        List<Check> checks = new ArrayList<>();
        boolean pageSetup = key.equals("pageDialog");
        boolean printers = key.equals("printJob");
        if (printers && PrintServiceLookup.lookupPrintServices(null, null).length == 0) {
            return noPrintService(key, dialog);
        }
        checks.add(Checks.expect(key + " : dialog", "sun.print.ServiceDialog \"" + (pageSetup ? "Page Setup" : "Print")
                + "\" APPLICATION_MODAL", () -> dialog.getClass().getName() + " \"" + dialog.getTitle() + "\" "
                        + dialog.getModalityType()));
        checks.add(Checks.expect(key + " : tabs", pageSetup ? "" : "General, Page Setup, Appearance",
                () -> String.join(", ", AccessibleDump.names(dialog, "page tab"))));
        checks.add(Checks.expect(key + " : buttons", pageSetup ? "OK, Cancel" : "Properties..., Print, Cancel",
                () -> String.join(", ", AccessibleDump.names(dialog, "push button").stream()
                        .filter(name -> !name.isEmpty() && !name.equals("null")).toList())));
        checks.add(Checks.expect(key + " : radio buttons", pageSetup
                ? "Portrait, Landscape, Reverse Portrait, Reverse Landscape"
                : "All, Pages, Portrait, Landscape, Reverse Portrait, Reverse Landscape, Monochrome, Color, Draft, "
                        + "Normal, High, One Side, Tumble, Duplex",
                () -> String.join(", ", AccessibleDump.names(dialog, "radio button"))));
        checks.add(Checks.expect(key + " : check boxes", pageSetup ? "" : "Print To File, Collate, Banner Page",
                () -> String.join(", ", AccessibleDump.names(dialog, "check box"))));
        // outside Windows, the Appearance tab has an output tray combo box (ServiceDialog.OutputPanel)
        boolean outputTrays = !Platforms.isWindows();
        checks.add(Checks.expect(key + " : combo boxes", pageSetup ? 2 : outputTrays ? 4 : 3,
                () -> AccessibleDump.byRole(dialog, "combo box").size()));
        // the orientation icons are PNG resources of sun.print
        checks.add(Checks.expect(key + " : icons", pageSetup ? "4 x 41x24" : "7 x 41x24 41x41", () -> {
            List<AccessibleContext> icons = AccessibleDump.find(dialog,
                    c -> c.getAccessibleIcon() != null && c.getAccessibleIcon().length > 0);
            Set<String> sizes = new java.util.TreeSet<>();
            icons.forEach(c -> sizes.add(c.getAccessibleIcon()[0].getAccessibleIconWidth() + "x"
                    + c.getAccessibleIcon()[0].getAccessibleIconHeight()));
            return icons.size() + " x " + String.join(" ", sizes);
        }));
        // mnemonics from the serviceui resource bundle, as accessible key bindings (KeyStroke.toString())
        // margins in inches in the United States and Canada (and for an empty country), in millimetres elsewhere
        String country = java.util.Locale.getDefault().getCountry();
        String units = country.isEmpty() || country.equals("US") || country.equals("CA") ? "(in)" : "(mm)";
        String pageMnemonics = "Size: Z, Source: C, Portrait P, Landscape L, Reverse Portrait I, Reverse Landscape N, "
                + "left " + units + " F, right " + units + " R, left F, right R, top " + units + " T, bottom " + units
                + " B, top T, bottom B";
        checks.add(Checks.expect(key + " : mnemonics", pageSetup ? pageMnemonics
                : "Name: N, Properties... R, Print To File F, All L, Pages E, Number of copies: O, Collate C, "
                        + pageMnemonics + ", Monochrome M, Color C, Draft F, Normal N, High H, One Side O, Tumble T, "
                        + "Duplex D, Banner Page B, Priority: R, Job Name: J, User Name: U"
                        + (outputTrays ? ", Output trays: P" : ""),
                () -> mnemonics(dialog)));
        List<String> dump = AccessibleDump.dump(dialog);
        // the dialog of the AWT print job lists the printers of the machine
        String tree = dump.size() + " nodes, " + Checks.sha256(String.join("\n", dump));
        checks.add(printers ? Check.info(key + " : accessible tree", tree) : Check.pass(key + " : accessible tree", tree));
        checks.add(Check.info(key + " : size", dialog.getWidth() + "x" + dialog.getHeight()));
        return checks;
    }

    /**
     * The message dialog of {@code ServiceDialog.showNoPrintService} (a {@code JOptionPane}), shown by the print dialog of
     * {@code Toolkit.getPrintJob} when the machine has no printer.
     */
    private static List<Check> noPrintService(String key, Dialog dialog) {
        List<Check> checks = new ArrayList<>();
        checks.add(Check.info(key + " : print services", "none : the no print service message"));
        checks.add(Checks.expect(key + " : dialog", "javax.swing.JDialog \"Message\" APPLICATION_MODAL",
                () -> dialog.getClass().getName() + " \"" + dialog.getTitle() + "\" " + dialog.getModalityType()));
        checks.add(Checks.expect(key + " : buttons", "OK", () -> String.join(", ", AccessibleDump.names(dialog,
                "push button").stream().filter(name -> !name.isEmpty() && !name.equals("null")).toList())));
        checks.add(Checks.expect(key + " : message", true,
                () -> AccessibleDump.names(dialog, "label").contains("No print service found.")));
        List<String> dump = AccessibleDump.dump(dialog);
        checks.add(Check.pass(key + " : accessible tree",
                dump.size() + " nodes, " + Checks.sha256(String.join("\n", dump))));
        checks.add(Check.info(key + " : size", dialog.getWidth() + "x" + dialog.getHeight()));
        return checks;
    }

    private static String mnemonics(Dialog dialog) {
        // a label and the component it labels share the mnemonic : distinct entries, in tree order
        Set<String> keys = new java.util.LinkedHashSet<>();
        for (AccessibleContext context : AccessibleDump.find(dialog, c -> true)) {
            if (context.getAccessibleComponent() instanceof javax.accessibility.AccessibleExtendedComponent extended
                    && extended.getAccessibleKeyBinding() != null
                    && extended.getAccessibleKeyBinding().getAccessibleKeyBindingCount() > 0) {
                String binding = String.valueOf(extended.getAccessibleKeyBinding().getAccessibleKeyBinding(0));
                keys.add(context.getAccessibleName() + " " + binding.replace("pressed ", ""));
            }
        }
        return String.join(", ", keys);
    }

    private CompletionStage<Void> interactive(List<Check> checks) {
        if (!Boolean.getBoolean("showcase.interactive")) {
            checks.add(Check.info("native print and page setup dialogs",
                    "skipped (they cannot be closed programmatically) : -Dshowcase.interactive=true opens them"));
            return CompletableFuture.completedFuture(null);
        }
        return Edt.background(() -> {
            PrinterJob job = streamJob();
            boolean print = job.printDialog();
            PageFormat page = job.pageDialog(new PageFormat());
            return print + ", " + PrintSupport.describe(page);
        }).handle((value, error) -> {
            checks.add(Check.info("native printDialog(), pageDialog(PageFormat)",
                    error == null ? value : Checks.describe(error)));
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private static Frame frameOf(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof Frame frame) {
                return frame;
            }
        }
        return null;
    }

    private static BufferedImage scale(BufferedImage image) {
        int width = (int) Math.round(image.getWidth() * THUMBNAIL_SCALE);
        int height = (int) Math.round(image.getHeight() * THUMBNAIL_SCALE);
        return Snapshots.offscreen(width, height, g -> {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, null);
            g.setColor(new java.awt.Color(0xB0BEC5));
            g.drawRect(0, 0, width - 1, height - 1);
        });
    }
}
