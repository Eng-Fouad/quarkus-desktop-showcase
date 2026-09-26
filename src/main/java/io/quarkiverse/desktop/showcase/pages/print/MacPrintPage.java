package io.quarkiverse.desktop.showcase.pages.print;

import java.awt.Component;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.desktop.MacDesktop;

/**
 * Printing on macOS (macOS only) : {@code PrinterJob} is {@code sun.lwawt.macosx.CPrinterJob} (Cocoa printing,
 * {@code NSPrintInfo}), which quarkus-awt's macOS substitution of {@code PlatformPrinterJobProxy} disables and
 * quarkus-desktop removes ; the print services come from CUPS. The page only reads the model (default page, page
 * validation, services) : nothing is ever printed. The native print panel and page layout panel ({@code NSPrintPanel},
 * {@code NSPageLayout}) need the user : with {@code -Dshowcase.interactive=true} only (cancel them, or use "Save as
 * PDF"). On other operating systems the page only states that it is not available. AWT only.
 */
@Singleton
public class MacPrintPage implements FeaturePage {

    // per build state
    private ChecksView services;
    private ChecksView dialogs;

    @Override
    public String id() {
        return "print-mac";
    }

    @Override
    public String title() {
        return "macOS printing";
    }

    @Override
    public String category() {
        return Categories.PRINTING;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public Component build() {
        if (!Platforms.isMac()) {
            return MacDesktop.notAvailable("macOS printing", "Cocoa printing (CPrinterJob) only exists on macOS.",
                    List.of());
        }
        List<Check> job = new ArrayList<>();
        job.add(Checks.expect("PrinterJob.getPrinterJob() class", "sun.lwawt.macosx.CPrinterJob",
                () -> PrinterJob.getPrinterJob().getClass().getName()));
        job.add(Checks.info("defaultPage() paper", () -> paper(PrinterJob.getPrinterJob().defaultPage())));
        job.add(Checks.info("defaultPage() orientation", () -> PrinterJob.getPrinterJob().defaultPage().getOrientation()));
        job.add(Checks.info("validatePage(A4 with 0 margins) imageable area", () -> {
            PageFormat format = new PageFormat();
            Paper a4 = new Paper();
            a4.setSize(595.28, 841.89);
            a4.setImageableArea(0, 0, 595.28, 841.89);
            format.setPaper(a4);
            return paper(PrinterJob.getPrinterJob().validatePage(format));
        }));
        services = ChecksView.table("Print services (CUPS)", List.of(Check.info("lookup", "pending")), 330,
                ChecksView.WIDTH);
        dialogs = ChecksView.table("Native panels", List.of(Check.info("NSPrintPanel, NSPageLayout",
                MacDesktop.interactive() ? "pending" : "skipped (they need the user) : -Dshowcase.interactive=true")),
                330, ChecksView.WIDTH);
        return Ui.column(14, Ui.heading("macOS printing"),
                Ui.text("Cocoa printing : the printer job, its default page and page validation, the CUPS print services, "
                        + "and the native print and page layout panels (interactive only). Nothing is printed.", 1000),
                ChecksView.table("PrinterJob", job, 330, ChecksView.WIDTH), services, dialogs);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        if (services == null) {
            return CompletableFuture.completedFuture(null);
        }
        ChecksView servicesView = services;
        ChecksView dialogsView = dialogs;
        CompletionStage<Void> lookup = Edt.background(() -> {
            List<Check> checks = new ArrayList<>();
            PrintService[] all = PrintServiceLookup.lookupPrintServices(null, null);
            checks.add(Check.info("print services", all.length));
            checks.add(Check.info("default print service",
                    PrintServiceLookup.lookupDefaultPrintService() == null ? "none" : "present"));
            return checks;
        }).thenAccept(servicesView::setChecks);
        if (!MacDesktop.interactive()) {
            return lookup;
        }
        return lookup.thenCompose(v -> Edt.background(() -> {
            PrinterJob job = PrinterJob.getPrinterJob();
            boolean print = job.printDialog();
            PageFormat page = job.pageDialog(job.defaultPage());
            // the user may have chosen "Save as PDF" : the job is not printed by the page in any case
            return List.of(Check.info("printDialog()", print), Check.info("pageDialog()", paper(page)));
        })).thenAccept(dialogsView::setChecks);
    }

    @Override
    public void dispose(Component content) {
        services = null;
        dialogs = null;
    }

    private static String paper(PageFormat format) {
        return Checks.num(format.getWidth(), 1) + " x " + Checks.num(format.getHeight(), 1) + ", imageable "
                + Checks.num(format.getImageableX(), 1) + ", " + Checks.num(format.getImageableY(), 1) + " "
                + Checks.num(format.getImageableWidth(), 1) + " x " + Checks.num(format.getImageableHeight(), 1);
    }
}
