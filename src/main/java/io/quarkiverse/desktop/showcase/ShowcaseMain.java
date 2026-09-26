package io.quarkiverse.desktop.showcase;

import java.awt.EventQueue;

import jakarta.inject.Inject;

import io.quarkiverse.desktop.showcase.core.MacEnvironment;
import io.quarkiverse.desktop.showcase.core.ShowcaseMode;
import io.quarkus.runtime.ApplicationLifecycleManager;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Starts the showcase on the event dispatch thread, then keeps the main thread until {@code Quarkus.asyncExit()} :
 * closing the main window, the Quit menu item, or the end of a snapshot run.
 */
@QuarkusMain
public class ShowcaseMain implements QuarkusApplication {

    @Inject
    ShowcaseApp app;

    @Override
    public int run(String... args) {
        ShowcaseMode.mainThread(Thread.currentThread());
        // macOS, -Dshowcase.robot=true : the Robot permissions, before the user interface starts (off the EDT)
        MacEnvironment.probePermissions();
        EventQueue.invokeLater(app::start);
        Quarkus.waitForExit();
        // 1 when the showcase failed to start
        return Math.max(0, ApplicationLifecycleManager.getExitCode());
    }
}
