package io.quarkiverse.desktop.showcase.pages.swing.infra;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.InvocationEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.SwingPropertyChangeSupport;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Swing concurrency : {@link Timer} (repeating and one-shot, stopped after a fixed number of ticks : only counts are
 * shown), {@link SwingWorker} (publish / process, progress and state properties, done, get, cancellation, failure,
 * worker thread pool), {@code invokeLater} ordering, {@code invokeAndWait} from a worker and from the EDT (error),
 * {@link InvocationEvent} with a notifier and caught exceptions, {@link SwingPropertyChangeSupport} notifying on the
 * EDT, {@code repaint()} from a background thread, and {@link SecondaryLoop} (the nested event loop of modal dialogs),
 * entered on the EDT and on a background thread.
 * <p>
 * Everything runs once the page is shown ({@link #ready}) ; the page shows the final state (never a running timer or
 * a partial progress) and an ordered log whose lines do not depend on timing.
 */
@Singleton
public class SwingConcurrencyPage implements FeaturePage {

    private static final int TICKS = 5;
    private static final int PRIME_LIMIT = 2000;

    // per build state
    private State state;
    private ChecksView results;

    private static final class State {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        final List<Check> checks = Collections.synchronizedList(new ArrayList<>());
        JPanel content;
        JProgressBar progress;
        JLabel primes;
        JLabel timers;
        JLabel workers;
        JTextArea logArea;
        PaintCounter painter;
        final List<Timer> timersToStop = new ArrayList<>();
        final List<SwingWorker<?, ?>> workersToCancel = new ArrayList<>();
    }

    /** A component counting its paints and whether they ran on the EDT. */
    private static final class PaintCounter extends JComponent {

        final AtomicInteger paints = new AtomicInteger();
        final AtomicBoolean allOnEdt = new AtomicBoolean(true);

        PaintCounter() {
            setPreferredSize(new Dimension(160, 20));
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!SwingUtilities.isEventDispatchThread()) {
                allOnEdt.set(false);
            }
            paints.incrementAndGet();
            g.setColor(new java.awt.Color(0xE3F2FD));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new java.awt.Color(0x263238));
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
            g.drawString("repaint() target", 6, 14);
        }
    }

    @Override
    public String id() {
        return "swing-concurrency";
    }

    @Override
    public String title() {
        return "Timers, workers and event loops";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 940;
    }

    @Override
    public Component build() {
        State s = new State();
        state = s;
        s.progress = new JProgressBar(0, 100);
        s.progress.setStringPainted(true);
        s.progress.setPreferredSize(new Dimension(460, 22));
        s.primes = new JLabel("SwingWorker : pending");
        s.timers = new JLabel("Timer : pending");
        s.workers = new JLabel("Cancelled and failing workers : pending");
        s.painter = new PaintCounter();
        s.logArea = InfraSupport.log(List.of("pending"), 62, 20);
        results = ChecksView.table("Results", List.of(Check.info("state", "pending")));
        s.content = InfraSupport.column(14,
                Ui.text("Timers, SwingWorker, invokeLater / invokeAndWait, InvocationEvent, SwingPropertyChangeSupport "
                        + "and SecondaryLoop, run once the page is shown. The page shows the final state ; the log "
                        + "lists the steps in an order that does not depend on timing.", 1000),
                InfraSupport.row(14,
                        InfraSupport.titled("Final state", 480, InfraSupport.column(10,
                                Ui.caption("SwingWorker progress property (setProgress, coalesced)"), s.progress,
                                s.primes, s.timers, s.workers, s.painter)),
                        InfraSupport.titled("Ordered log", 534, s.logArea)),
                results);
        return s.content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        State s = state;
        ChecksView view = results;
        return Edt.rounds(2)
                .thenCompose(v -> timers(s))
                .thenCompose(v -> primesWorker(s))
                .thenCompose(v -> cancelledWorker(s))
                .thenCompose(v -> failingWorker(s))
                .thenCompose(v -> runnableWorker(s))
                .thenCompose(v -> invokeLaterOrder(s))
                .thenCompose(v -> invocationEvent(s))
                .thenCompose(v -> propertyChangeSupport(s))
                .thenCompose(v -> backgroundRepaint(s))
                .thenCompose(v -> secondaryLoopOnEdt(s))
                .thenCompose(v -> secondaryLoopInBackground(s))
                .handle((v, error) -> {
                    if (error != null) {
                        s.checks.add(Check.fail("sequence", Checks.describe(unwrap(error))));
                    }
                    threads(s);
                    view.setChecks(new ArrayList<>(s.checks));
                    s.logArea.setText(String.join("\n", s.log));
                    s.content.revalidate();
                    return null;
                });
    }

    @Override
    public void dispose(Component content) {
        State s = state;
        if (s != null) {
            s.timersToStop.forEach(Timer::stop);
            s.workersToCancel.forEach(w -> w.cancel(true));
        }
        state = null;
        results = null;
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof java.util.concurrent.CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static void add(State s, Check check) {
        s.checks.add(check);
    }

    // ------------------------------------------------------------------------------------------------ Timer

    private static CompletionStage<Void> timers(State s) {
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger oneShot = new AtomicInteger();
        AtomicBoolean onEdt = new AtomicBoolean(true);
        String[] command = new String[1];
        Timer repeating = new Timer(5, null);
        repeating.setInitialDelay(0);
        repeating.setActionCommand("tick");
        repeating.addActionListener(e -> {
            if (!SwingUtilities.isEventDispatchThread()) {
                onEdt.set(false);
            }
            command[0] = e.getActionCommand();
            if (ticks.incrementAndGet() >= TICKS) {
                repeating.stop();
            }
        });
        Timer once = new Timer(10, e -> oneShot.incrementAndGet());
        once.setRepeats(false);
        s.timersToStop.add(repeating);
        s.timersToStop.add(once);
        add(s, Checks.expect("Timer properties : delay, initial delay, repeats, coalesce", "5, 0, true, true",
                () -> repeating.getDelay() + ", " + repeating.getInitialDelay() + ", " + repeating.isRepeats() + ", "
                        + repeating.isCoalesce()));
        repeating.start();
        once.start();
        add(s, Checks.expect("Timer.isRunning() after start()", "true", repeating::isRunning));
        return Edt.until(() -> ticks.get() >= TICKS && oneShot.get() >= 1, 5000, "timers")
                .thenCompose(v -> Edt.delay(100))
                .handle((v, error) -> {
                    add(s, error == null ? Check.pass("timers completed", "ok")
                            : Check.fail("timers completed", Checks.describe(unwrap(error))));
                    add(s, Checks.expect("repeating Timer : ticks, stopped by its action", TICKS + ", running false",
                            () -> ticks.get() + ", running " + repeating.isRunning()));
                    add(s, Checks.expect("one-shot Timer (setRepeats(false)) : fired", 1, oneShot::get));
                    add(s, Checks.expect("Timer actions run on the EDT", "true", onEdt::get));
                    add(s, Checks.expect("Timer action command", "tick", () -> command[0]));
                    add(s, Checks.expect("TimerQueue thread (daemon)", "true", () -> Thread.getAllStackTraces()
                            .keySet().stream().anyMatch(t -> t.getName().equals("TimerQueue") && t.isDaemon())));
                    s.timers.setText("Timer : " + ticks.get() + " ticks then stopped, one-shot fired "
                            + oneShot.get() + " time");
                    s.log.add("Timer : " + TICKS + " ticks on the EDT, then stopped by its action");
                    s.log.add("Timer (setRepeats(false)) : fired once");
                    return null;
                });
    }

    // ------------------------------------------------------------------------------------------------ SwingWorker

    /** Counts the primes below {@link #PRIME_LIMIT}, publishing each prime. */
    private static final class PrimesWorker extends SwingWorker<Integer, Integer> {

        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger doneCalls = new AtomicInteger();
        final AtomicBoolean processOnEdt = new AtomicBoolean(true);
        final AtomicBoolean doneOnEdt = new AtomicBoolean(true);
        volatile long processedSum;
        volatile String threadName;
        volatile boolean daemon;
        volatile boolean workerOnEdt = true;
        volatile Boolean invokeAndWaitOnEdt;

        @Override
        protected Integer doInBackground() throws Exception {
            Thread current = Thread.currentThread();
            threadName = current.getName();
            daemon = current.isDaemon();
            workerOnEdt = EventQueue.isDispatchThread();
            AtomicBoolean edt = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> edt.set(SwingUtilities.isEventDispatchThread()));
            invokeAndWaitOnEdt = edt.get();
            int count = 0;
            for (int n = 2; n < PRIME_LIMIT; n++) {
                if (isPrime(n)) {
                    count++;
                    publish(n);
                }
                if (n % 200 == 0) {
                    setProgress(n * 100 / PRIME_LIMIT);
                }
            }
            setProgress(100);
            return count;
        }

        @Override
        protected void process(List<Integer> chunks) {
            if (!SwingUtilities.isEventDispatchThread()) {
                processOnEdt.set(false);
            }
            for (int prime : chunks) {
                processed.incrementAndGet();
                processedSum += prime;
            }
        }

        @Override
        protected void done() {
            if (!SwingUtilities.isEventDispatchThread()) {
                doneOnEdt.set(false);
            }
            doneCalls.incrementAndGet();
        }

        private static boolean isPrime(int n) {
            for (int d = 2; d * d <= n; d++) {
                if (n % d == 0) {
                    return false;
                }
            }
            return true;
        }
    }

    private static CompletionStage<Void> primesWorker(State s) {
        PrimesWorker worker = new PrimesWorker();
        s.workersToCancel.add(worker);
        List<String> states = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger lastProgress = new AtomicInteger(-1);
        AtomicBoolean listenerOnEdt = new AtomicBoolean(true);
        worker.addPropertyChangeListener((PropertyChangeEvent e) -> {
            if (!SwingUtilities.isEventDispatchThread()) {
                listenerOnEdt.set(false);
            }
            if ("state".equals(e.getPropertyName())) {
                states.add(String.valueOf(e.getNewValue()));
            } else if ("progress".equals(e.getPropertyName())) {
                lastProgress.set((Integer) e.getNewValue());
                s.progress.setValue((Integer) e.getNewValue());
            }
        });
        add(s, Checks.expect("SwingWorker state before execute()", "PENDING", () -> worker.getState()));
        worker.execute();
        return Edt.until(() -> worker.isDone() && worker.doneCalls.get() == 1 && states.contains("DONE")
                && lastProgress.get() == 100, 10_000, "primes worker")
                .handle((v, error) -> {
                    add(s, error == null ? Check.pass("primes worker completed", "ok")
                            : Check.fail("primes worker completed", Checks.describe(unwrap(error))));
                    add(s, Checks.expect("get() : primes below 2000", 303, worker::get));
                    add(s, Checks.expect("process() : published values received (count, sum)", "303, 277050",
                            () -> worker.processed.get() + ", " + worker.processedSum));
                    add(s, Checks.expect("state property changes", "STARTED DONE", () -> String.join(" ", states)));
                    add(s, Checks.expect("last progress property value", 100, lastProgress::get));
                    add(s, Checks.expect("process(), done(), property listeners on the EDT", "true, true, true",
                            () -> worker.processOnEdt.get() + ", " + worker.doneOnEdt.get() + ", "
                                    + listenerOnEdt.get()));
                    add(s, Checks.expect("done() calls", 1, worker.doneCalls::get));
                    add(s, Checks.expect("worker thread : name, daemon, EDT",
                            "SwingWorker-pool-#-thread-#, true, false",
                            () -> InfraSupport.digitsNormalized(worker.threadName) + ", " + worker.daemon + ", "
                                    + worker.workerOnEdt));
                    add(s, Checks.expect("invokeAndWait from the worker runs on the EDT", "true",
                            () -> worker.invokeAndWaitOnEdt));
                    add(s, Checks.expect("getState(), isCancelled()", "DONE, false",
                            () -> worker.getState() + ", " + worker.isCancelled()));
                    s.primes.setText("SwingWorker : " + worker.processed.get() + " primes below " + PRIME_LIMIT
                            + " published, sum " + worker.processedSum);
                    s.progress.setValue(lastProgress.get());
                    s.log.add("SwingWorker : execute() -> STARTED");
                    s.log.add("  doInBackground : invokeAndWait runs on the EDT");
                    s.log.add("  publish x 303 -> process() on the EDT ; setProgress ... 100");
                    s.log.add("  DONE, done() on the EDT, get() = 303");
                    return null;
                });
    }

    private static CompletionStage<Void> cancelledWorker(State s) {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean loopEnded = new AtomicBoolean();
        AtomicInteger doneCalls = new AtomicInteger();
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                started.countDown();
                try {
                    while (!isCancelled()) {
                        Thread.sleep(5);
                    }
                } catch (InterruptedException e) {
                    // cancel(true) interrupts the worker thread
                } finally {
                    loopEnded.set(true);
                }
                return "not cancelled";
            }

            @Override
            protected void done() {
                doneCalls.incrementAndGet();
            }
        };
        s.workersToCancel.add(worker);
        worker.execute();
        return Edt.until(() -> started.getCount() == 0, 5000, "cancellable worker started")
                .thenCompose(v -> {
                    add(s, Checks.expect("cancel(true) of a running worker", "true", () -> worker.cancel(true)));
                    return Edt.until(() -> worker.isDone() && loopEnded.get() && doneCalls.get() == 1, 5000,
                            "cancelled worker");
                })
                .handle((v, error) -> {
                    add(s, error == null ? Check.pass("cancelled worker completed", "ok")
                            : Check.fail("cancelled worker completed", Checks.describe(unwrap(error))));
                    add(s, Checks.expect("cancelled : isCancelled, isDone, done() calls", "true, true, 1",
                            () -> worker.isCancelled() + ", " + worker.isDone() + ", " + doneCalls.get()));
                    add(s, Checks.expect("cancelled : get() throws", CancellationException.class.getName(), () -> {
                        try {
                            return "returned " + worker.get();
                        } catch (CancellationException e) {
                            return e.getClass().getName();
                        }
                    }));
                    s.log.add("SwingWorker cancel(true) : the loop ends, done() is called,");
                    s.log.add("  get() throws CancellationException");
                    return null;
                });
    }

    private static CompletionStage<Void> failingWorker(State s) {
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                throw new IllegalStateException("expected failure");
            }
        };
        s.workersToCancel.add(worker);
        worker.execute();
        return Edt.until(worker::isDone, 5000, "failing worker").handle((v, error) -> {
            add(s, Checks.expect("failing worker : get() throws ExecutionException caused by",
                    "java.lang.IllegalStateException: expected failure", () -> {
                        try {
                            return "returned " + worker.get();
                        } catch (ExecutionException e) {
                            return Checks.describe(e.getCause());
                        }
                    }));
            s.workers.setText("cancel(true) : CancellationException ; failure : ExecutionException");
            s.log.add("SwingWorker failing : get() throws ExecutionException");
            return null;
        });
    }

    private static CompletionStage<Void> runnableWorker(State s) {
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                return 42;
            }
        };
        Thread thread = new Thread(worker, "showcase-runnable-worker");
        thread.setDaemon(true);
        thread.start();
        return Edt.until(worker::isDone, 5000, "runnable worker").handle((v, error) -> {
            add(s, Checks.expect("SwingWorker.run() on an application thread : get()", 42, worker::get));
            s.log.add("SwingWorker as a Runnable on an application thread : get() = 42");
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------------ event queue

    private static CompletionStage<Void> invokeLaterOrder(State s) {
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean allOnEdt = new AtomicBoolean(true);
        String[] currentEvent = new String[1];
        add(s, Checks.expect("invokeAndWait on the EDT throws", "java.lang.Error: Cannot call invokeAndWait from the "
                + "event dispatcher thread", () -> {
                    try {
                        EventQueue.invokeAndWait(() -> {
                        });
                        return "no error";
                    } catch (Error e) {
                        return Checks.describe(e);
                    }
                }));
        return Edt.background(() -> {
            for (int i = 1; i <= 6; i++) {
                int n = i;
                Runnable r = () -> {
                    if (!EventQueue.isDispatchThread()) {
                        allOnEdt.set(false);
                    }
                    if (n == 1) {
                        java.awt.AWTEvent event = EventQueue.getCurrentEvent();
                        currentEvent[0] = event == null ? "null" : event.getClass().getName();
                    }
                    order.add(n);
                };
                if (i % 2 == 0) {
                    SwingUtilities.invokeLater(r);
                } else {
                    EventQueue.invokeLater(r);
                }
            }
            return EventQueue.isDispatchThread();
        }).thenCompose(backgroundOnEdt -> {
            add(s, Checks.expect("EventQueue.isDispatchThread() on a background thread", "false",
                    () -> backgroundOnEdt));
            return Edt.until(() -> order.size() == 6, 5000, "invokeLater");
        }).handle((v, error) -> {
            add(s, Checks.expect("invokeLater order (EventQueue and SwingUtilities alternately)", "1 2 3 4 5 6",
                    () -> order.stream().map(String::valueOf).reduce((a, b) -> a + " " + b).orElse("")));
            add(s, Checks.expect("invokeLater runnables on the EDT", "true", allOnEdt::get));
            add(s, Checks.expect("EventQueue.getCurrentEvent() inside invokeLater", InvocationEvent.class.getName(),
                    () -> currentEvent[0]));
            s.log.add("invokeLater x 6 from a background thread : in order on the EDT");
            return null;
        });
    }

    private static CompletionStage<Void> invocationEvent(State s) {
        Object notifier = new Object();
        return Edt.background(() -> {
            InvocationEvent event = new InvocationEvent(Toolkit.getDefaultToolkit(), () -> {
                throw new IllegalArgumentException("caught by the InvocationEvent");
            }, notifier, true);
            synchronized (notifier) {
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(event);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!event.isDispatched() && System.nanoTime() < deadline) {
                    notifier.wait(100);
                }
            }
            return event;
        }).handle((event, error) -> {
            add(s, Checks.expect("InvocationEvent with notifier : dispatched, exception caught",
                    "true, java.lang.IllegalArgumentException: caught by the InvocationEvent", () -> {
                        if (error != null) {
                            throw new IllegalStateException(unwrap(error));
                        }
                        return event.isDispatched() + ", " + Checks.describe(event.getException());
                    }));
            s.log.add("InvocationEvent with a notifier : exception caught, notified");
            return null;
        });
    }

    private static CompletionStage<Void> propertyChangeSupport(State s) {
        SwingPropertyChangeSupport support = new SwingPropertyChangeSupport(s, true);
        AtomicBoolean onEdt = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        support.addPropertyChangeListener("value", e -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            calls.incrementAndGet();
        });
        add(s, Checks.expect("SwingPropertyChangeSupport.isNotifyOnEDT()", "true", support::isNotifyOnEDT));
        return Edt.background(() -> {
            support.firePropertyChange("value", 1, 2);
            return true;
        }).thenCompose(v -> Edt.until(() -> calls.get() == 1, 5000, "property change"))
                .handle((v, error) -> {
                    add(s, Checks.expect("fired on a background thread, listener called on the EDT", "1, true",
                            () -> calls.get() + ", " + onEdt.get()));
                    s.log.add("SwingPropertyChangeSupport(notifyOnEDT) : delivered on the EDT");
                    return null;
                });
    }

    private static CompletionStage<Void> backgroundRepaint(State s) {
        int before = s.painter.paints.get();
        return Edt.background(() -> {
            s.painter.repaint();
            return true;
        }).thenCompose(v -> Edt.until(() -> s.painter.paints.get() > before, 5000, "repaint"))
                .handle((v, error) -> {
                    add(s, Checks.expect("repaint() from a background thread : painted on the EDT", "true",
                            () -> {
                                if (error != null) {
                                    throw new IllegalStateException(unwrap(error));
                                }
                                return s.painter.allOnEdt.get();
                            }));
                    s.log.add("repaint() from a background thread : painted on the EDT");
                    return null;
                });
    }

    // ------------------------------------------------------------------------------------------------ SecondaryLoop

    private static CompletionStage<Void> secondaryLoopOnEdt(State s) {
        return Edt.supply(() -> {
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            List<String> inner = Collections.synchronizedList(new ArrayList<>());
            CompletableFuture<Boolean> exited = new CompletableFuture<>();
            for (int i = 1; i <= 3; i++) {
                int n = i;
                EventQueue.invokeLater(() -> {
                    inner.add("event " + n + " dispatched inside the loop");
                    if (n == 3) {
                        Thread exiter = new Thread(() -> exited.complete(loop.exit()), "showcase-loop-exit");
                        exiter.setDaemon(true);
                        exiter.start();
                    }
                });
            }
            // a watchdog, should the events never come
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(10_000);
                    loop.exit();
                } catch (InterruptedException e) {
                    // exited normally
                }
            }, "showcase-loop-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            // blocks this event handler, while the nested loop dispatches the events
            boolean entered = loop.enter();
            watchdog.interrupt();
            inner.add("enter() returned " + entered);
            // the loop is not active any more
            boolean again = loop.exit();
            return new LoopResult(inner, exited, again);
        }).thenCompose(result -> Edt.timeout(result.exited(), 5000, "exit()").handle((exit, error) -> {
            add(s, Checks.expect("SecondaryLoop on the EDT : events dispatched inside enter()",
                    "event 1 dispatched inside the loop ; event 2 dispatched inside the loop ; event 3 "
                            + "dispatched inside the loop ; enter() returned true",
                    () -> String.join(" ; ", result.inner())));
            add(s, Checks.expect("SecondaryLoop.exit() from a background thread, then exit() again",
                    "true, false", () -> (error == null ? exit : Checks.describe(unwrap(error))) + ", "
                            + result.exitAgain()));
            s.log.add("SecondaryLoop on the EDT : 3 events dispatched by the nested loop,");
            s.log.add("  exit() from a background thread");
            return null;
        }));
    }

    private record LoopResult(List<String> inner, CompletableFuture<Boolean> exited, boolean exitAgain) {
    }

    private static CompletionStage<Void> secondaryLoopInBackground(State s) {
        return Edt.background(() -> {
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            Thread exiter = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // exit anyway
                }
                loop.exit();
            }, "showcase-loop-exit-2");
            exiter.setDaemon(true);
            exiter.start();
            // on a thread that is not the EDT, enter() blocks until exit()
            boolean entered = loop.enter();
            return entered + ", after exit() " + loop.exit();
        }).handle((result, error) -> {
            add(s, Checks.expect("SecondaryLoop on a background thread : enter() blocks until exit()",
                    "true, after exit() false", () -> {
                        if (error != null) {
                            throw new IllegalStateException(unwrap(error));
                        }
                        return result;
                    }));
            s.log.add("SecondaryLoop on a background thread : enter() blocks until exit()");
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------------ threads

    private static void threads(State s) {
        add(s, Checks.expect("SwingUtilities.isEventDispatchThread() on the EDT", "true",
                SwingUtilities::isEventDispatchThread));
        add(s, Checks.info("EDT name", () -> Thread.currentThread().getName()));
        add(s, Checks.info("AWT and Swing threads (names normalized)", () -> {
            Set<String> names = new TreeSet<>();
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                String name = t.getName();
                // AWT-Shutdown and Image Fetcher threads come and go : not deterministic
                if ((name.startsWith("AWT-") && !name.equals("AWT-Shutdown")) || name.startsWith("Java2D")
                        || name.equals("TimerQueue") || name.startsWith("SwingWorker") || name.startsWith("D3D")
                        || name.startsWith("Swing-Shell")) {
                    names.add(InfraSupport.digitsNormalized(name) + (t.isDaemon() ? " (daemon)" : ""));
                }
            }
            return String.join(", ", names);
        }));
    }
}
