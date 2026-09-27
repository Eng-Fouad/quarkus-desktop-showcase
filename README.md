# Quarkus Desktop Showcase

An AWT, Java2D and Swing application built with [Quarkus](https://quarkus.io) and the quarkus-desktop extensions
(`io.quarkiverse.desktop:quarkus-desktop-awt` and `quarkus-desktop-swing`), exercising as much of the JDK desktop
modules as possible: AWT components and windows, Java2D, text and fonts, images and color, Swing components, look and
feels, data transfer and desktop integration, printing, accessibility, beans and sound.

Its purpose is to verify that a GraalVM native executable renders **exactly** the same UI as the JVM, and to find the gaps
in the native image configuration of quarkus-desktop. It is the desktop counterpart of the quarkus-fx showcase.

Two variants are built from the same sources:

| Variant | Build | Main window | Extension | Pages |
|---|---|---|---|---|
| default | `mvn package` (into `target/`) | Swing: `JFrame`, `JTree`, `JSplitPane` | `quarkus-desktop-swing` (includes `quarkus-desktop-awt`) | all |
| awt-only | `mvn package -Dawt-only` (into `target/awt-only/`) | AWT: `Frame`, `java.awt.List`, `CardLayout` | `quarkus-desktop-awt` alone | AWT pages only |

The awt-only variant proves that quarkus-desktop-awt works without Swing application code: the classes of
`pages.swing.**`, `pages.laf.**` and `ui.swing.**` are neither compiled nor CDI beans there.

## Requirements

- Windows x64, Linux x64, or macOS on Apple silicon. Native executables on macOS need the quarkus-awt of the Quarkus
  pull request [Enable quarkus-awt on macOS](https://github.com/quarkusio/quarkus/pull/56979) (not in a Quarkus release
  yet: build Quarkus from it) and GraalVM 25.1 or later: see [Verifying on macOS](#verifying-on-macos)
- JDK 25 for the JVM mode and the tools, GraalVM for JDK 25 for native executables (`GRAALVM_HOME`)
- quarkus-desktop `999-SNAPSHOT` installed in the local Maven repository: clone
  [quarkus-desktop](https://github.com/Eng-Fouad/quarkus-desktop) and run `mvn install` in it
  (`git clone https://github.com/Eng-Fouad/quarkus-desktop && cd quarkus-desktop && mvn install -DskipTests`)
- Native builds: the [Quarkus native prerequisites](https://quarkus.io/guides/building-native-image) (Visual Studio
  Build Tools on Windows, gcc and zlib development packages on Linux)

Maven is provided by the wrapper (`./mvnw` on Linux and macOS, `mvnw.cmd` on Windows); a local `mvn` works too.

## Run

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Native executable (`...-runner.exe` on Windows, next to its `.dll`, `.so` or `.dylib` libraries):

```bash
./mvnw package -Dnative
./target/quarkus-desktop-showcase-1.0.0-SNAPSHOT-runner
```

AWT only variant: add `-Dawt-only` to the builds, and run `target/awt-only/...`.

The application has a `@QuarkusMain` (`ShowcaseMain`): it opens the main window on the event dispatch thread and waits
for `Quarkus.asyncExit()`, called when the main window is closed (or with Showcase > Quit).

Page filters (interactive and snapshot mode): `-Dshowcase.pages=overview-environment,j2d-` (page ids, an entry ending
with `-` or `*` is a prefix) and `-Dshowcase.categories=java2d,text` (category keys `overview, awt, java2d, text, images,
swing, laf, desktop, printing, a11y, sound` or names). `-Dshowcase.ui=awt` uses the AWT main window in the default
variant.

## Compare JVM and native rendering

In snapshot mode (`-Dshowcase.snapshot.dir=...`), the application renders every page to a PNG file, writes a
`report.json` with the environment, the non-visual checks and the errors of every page, then exits.

```bash
java tools/Snapshot.java jvm                  # comparison/jvm
java tools/Snapshot.java native               # comparison/native
java tools/Compare.java comparison/jvm comparison/native comparison/diff   # summary.txt and index.html
```

`tools/Snapshot.java jvm|native [label] [--pages=ids] [--categories=names] [--awt-only] [--hidpi]
[--pipeline=gdi|opengl|x11] [--screen] [--trace] [-- options...]`:

- `-Duser.language=en -Duser.country=US` and `-Dsun.java2d.uiScale=1` are added unless given after `--` (a native
  executable defaults to the locale of the build machine; AWT heavyweight components render correctly with `printAll`
  at scale 1 only).
- `--hidpi` keeps the real UI scale. Forcing the scale to 1 hides a DPI unaware native executable: compare a `--hidpi`
  JVM run with a `--hidpi` native run (report keys `defaultTransform` and `screenResolution`).
- `--pipeline` selects another Java2D pipeline than the default one of the platform: `gdi` (`-Dsun.java2d.d3d=false`,
  GDI instead of Direct3D on Windows), `opengl` (`-Dsun.java2d.opengl=true`, WGL on Windows, GLX on Linux), `x11`
  (`-Dsun.java2d.xrender=false` on Linux). Compare runs of the same pipeline (report key `pipeline`).
- `--awt-only` runs the awt-only variant (`target/awt-only`). `--screen` also saves a Robot screen capture of the
  window per page (`<page>--screen.png`, reported as `SCREEN`, never a mismatch).
- `--trace` (JVM only) runs under the GraalVM tracing agent (`comparison/<label>/metadata`), see below.
- `-- -Dshowcase.beans.dump-dir=<directory>` writes every XML text that the JavaBeans pages encode (and the decoding
  exceptions) to `<directory>/<sequence>-<sha256>.xml`: run it with one directory per runtime, then diff them when an
  `XML : lines, SHA-256` check differs.

`java tools/Cycle.java <label> [--trace] [--exact] [--awt-only] [--hidpi] [--pipeline=...] [--pages=...] [--offline]
[--skip-jvm] [--skip-native-build] [--maven-args=a,b] [--native-args=a,b] [-- options]` runs a whole iteration: JVM build and
snapshots, native build and snapshots, comparison (`comparison/{jvm,native,diff,logs}-<label>`). Run it with GraalVM's
java (`$GRAALVM_HOME/bin/java tools/Cycle.java win1`): Maven builds with the JDK running the tool, and both runs then use
the same JDK build. `--exact` builds with `--exact-reachability-metadata` and runs the executable with
`-XX:MissingRegistrationReportingMode=Warn`, so that every reflection, JNI or resource access missing from the metadata
is reported in the native `run.log`; the Snapshot tool prints how many `run.log` lines mention missing metadata
(`Missing*RegistrationError`, `NoSuchFieldError`, `UnsatisfiedLinkError`...).

Compare verdicts per image: `IDENTICAL`, `NOISE` (at most 2 levels per channel on less than 0.5 % of the pixels: the
same differences appear between two JVM runs using different execution modes, JIT vs `-Xint`), `DIFFERENT`, `SIZE`,
`ONLY_A`/`ONLY_B`, `EXPECTED` (a page whose `runtimeDependent()` is true shows where a native image legitimately differs),
`SCREEN`. Environment keys of the reports (Java2D pipeline, toolkit, screen, DPI, look and feel, font hints, desktop
features...) must be equal (`ENV DIFF` otherwise); raw values that legitimately differ between the runtimes
(`dpiaware`, `uiScale`, `javaHome`, `javaVendorVersion`) are only reported (`ENV NOTE`).

## Application-level native configuration

Everything AWT and Swing need in a native executable comes from quarkus-desktop. The application itself:

- does **not** set `quarkus.native.headless` (it only configures the image builder JVM: whether an executable is headless
  is decided at run time by the JDK) and has no `java.home` feature (AWT reads `java.home`: quarkus-desktop handles it);
- includes its resources: `quarkus.native.resources.includes=showcase/**`;
- includes the JDK resource bundles and locale data of the locales its i18n checks use (`quarkus.locales`; by default a
  native executable only has those of the build locale);
- registers what the JDK reaches by reflection in its own classes: `@RegisterForReflection` on JavaBeans, BeanInfos,
  property editors, look and feels and UI delegates created by name, formatter value classes, serializable clipboard
  payloads, and the service providers of its ImageIO plugin, whose `META-INF/services` files and metadata format bundle
  are listed in `META-INF/native-image/io.quarkiverse.desktop.showcase/quarkus-desktop-showcase/reachability-metadata.json`;
- registers the JDK methods that its JavaBeans pages call by name (`Expression(Integer.class, "parseInt")`,
  `(Math.class, "max")`, `<object class="java.lang.Integer" method="valueOf">` in `decoder-elements.xml`), in the same
  `reachability-metadata.json`, and one proxy class per listener interface of `EventHandler` (`@RegisterForProxy`);
- registers, in the same `reachability-metadata.json`, the Foreign Function and Memory downcalls of `core.Foreground`
  (the Windows foreground check; native access is enabled with the `Enable-Native-Access` manifest attribute of the run
  jar and `--enable-native-access=ALL-UNNAMED` for native builds, in `application.properties`, repeated in the `mac`
  profile of `pom.xml`), the JDK internals that the macOS capture of the AWT components reads by reflection
  (`java.awt.Component#peer`, `sun.lwawt.LWComponentPeer#getDelegate()`, with the `add-opens` of the `mac` profile),
  and the lookups that an `--exact-reachability-metadata` build needs for its own classes and resources: the types of
  its Synth painter and JavaBeans, the JavaBeans probes of its beans that do not exist (`BeanInfo`, `Customizer`,
  `PersistenceDelegate`, `Editor`, `java.beans.MetaData$..._PersistenceDelegate`) and the serialized forms that
  `Beans.instantiate` looks for (`.../pages/beans/*.ser`), the absent names its pages look up on purpose
  (`no.such.Bean`, `no.such.Type`, `no/such/*.ser`, missing images), the configuration files that Quarkus looks for,
  and the `provider()` method of the JDK locale data provider. These were taken from a Windows exact-mode trace: Linux
  and macOS exact-mode runs may need more;
- enables the JavaBeans registration of the JDK Swing classes (`quarkus.desktop.swing.java-beans.jdk-classes=true`; the
  AWT one, `quarkus.desktop.awt.java-beans.jdk-classes`, is enabled by default): the beans pages introspect, encode and
  decode AWT and Swing components;
- never creates AWT or Swing objects in the static initializer of a class that is not itself an AWT/Swing subclass
  (Quarkus initializes application classes at build time, AWT and Swing classes at run time).

## Native image configuration tools

- `java tools/Cycle.java <label> --trace` (or `java tools/Snapshot.java jvm trace --trace`): JVM snapshots under the
  GraalVM tracing agent, then `tools/MetadataDiff.java` lists the JNI, reflection, resource, bundle, serialization and
  proxy accesses of the JDK desktop modules that quarkus-desktop does not register for the current platform:
  `java tools/MetadataDiff.java comparison/trace/metadata/reachability-metadata.json [windows|linux|mac] [--awt-only]
  [--no-java-beans] [--repository=path] [--app-metadata=path]`, from the root of the repository.
  It reads the `static String[]` lists of `io.quarkiverse.desktop.awt.deployment.AwtClassesAndResources` and
  `io.quarkiverse.desktop.swing.deployment.SwingClassesAndResources` from the deployment jars installed in `~/.m2` (or
  in another local Maven repository given with `--repository`, e.g. one where a branch of quarkus-desktop was
  installed with `-Dmaven.repo.local`),
  understands package entries, `fqcn#member` entries and the classes registered with their public members
  (`REFLECTIVE_PUBLIC_MEMBERS`, and `JAVA_BEANS_CLASSES` unless `--no-java-beans`: the showcase enables the
  `java-beans.jdk-classes` properties), the lists of `--exact-reachability-metadata` builds (`REFLECTIVE_TYPES`,
  `NEGATIVE_CLASS_LOOKUPS`, `METHOD_LOOKUPS`), the constants of the extension code (`*SERIALIZABLE*` classes, and the
  `ABSENT_RESOURCE_BUNDLES` that the JDK looks up but does not have), and also lists stale entries (names that do not
  exist in the JDK) and the lookups of classes and resources that do not exist in the JDK (expected to fail, only an
  issue with `--exact-reachability-metadata`; the class lookups that quarkus-desktop registers for it, the
  `NEGATIVE_CLASS_LOOKUPS`, the JavaBeans probes of its classes and the lookups of the absent bundles, are only
  counted). What the showcase registers itself (its `reachability-metadata.json`, or the one given with
  `--app-metadata`) is subtracted and listed apart. Run it on the platform of the trace (the Linux cycle runs it in the
  container, where `~/.m2` is the Docker volume): the JDK running the tool is the universe, so on another platform a
  class or resource missing from that JDK may exist on the platform of the trace. The lookups that quarkus-desktop
  registers by name are recognized on any platform, but the other lookups of absent classes and resources are then
  listed as not registered or not included, and the stale entries of the platform lists are not checked.
- `java tools/ClinitAudit.java [windows|linux|mac] [--awt-only] [class_initialization_report.csv]`: lists the JDK desktop
  classes left initialized at build time (not in the run time initialization lists of quarkus-desktop and quarkus-awt)
  whose static initializer reaches native code, library loading, threads, native memory, NIO channels, the toolkit,
  system properties or resource bundles (JDK class file API, no library needed).

## Linux in Docker

`docker/linux/Dockerfile` provides a Linux environment: GraalVM CE for JDK 25 (the GraalVM of the Quarkus builder
image), a virtual X server (`Xvfb`, 3840x2160 at 96 DPI) with a window manager (`openbox`, without key and mouse
bindings: `docker/linux/openbox-rc.xml`) and a compositing manager (`xcompmgr`), the X11, fontconfig, CUPS (library only: no printer), ALSA (null device) and GTK runtime libraries, fixed
fonts (DejaVu, Liberation, Noto, Noto CJK and Noto Color Emoji), `xclip` (the foreign clipboard application of
`dt-clipboard`), and pins what changes the rendering: `GTK_THEME=Adwaita`, `LANG=C.UTF-8`, `TZ=UTC` (the snapshot tool
adds `-Duser.language=en -Duser.country=US`). The window manager keeps the always-on-top windows of the pages above the
others and activates the windows they bring to the front, as on a Linux desktop; the compositing manager renders the
translucent windows (AWT reports window opacity as supported whenever the window manager supports it).
`SHOWCASE_WM=none` runs a bare X server, `SHOWCASE_COMPOSITOR=none` leaves the compositing manager out. A system tray
(`stalonetray`, in the upper right corner) owns the `_NET_SYSTEM_TRAY` selection, so `SystemTray.isSupported()` is true:
the image sets `SHOWCASE_TRAY_ICON` to the point of its first icon, and `desktop-services` then clicks its tray icon with
Robot (a click, a double click, the popup button) and checks the events of the icon; the page needs the focus there, the
mouse events of the other pages are dropped in snapshot mode. `SHOWCASE_TRAY=none` leaves the tray out (no tray
support, as on a desktop without one).

The container has a Maven repository of its own (a Docker volume): quarkus-desktop is built and installed there first,
with its tests (the native integration tests run on the virtual display).

```bash
# in the showcase directory, with a quarkus-desktop clone next to it (../quarkus-desktop)
docker build -t quarkus-desktop-showcase-linux docker/linux
docker volume create quarkus-desktop-linux-m2
docker run --rm --init -v "$PWD/../quarkus-desktop":/quarkus-desktop -w /quarkus-desktop \
    -v quarkus-desktop-linux-m2:/root/.m2 quarkus-desktop-showcase-linux \
    ./mvnw -B install -Dnative -Dquarkus.native.native-image-xmx=8g
# default variant : JVM, JVM under the tracing agent (+ MetadataDiff), native build, native run, comparison
docker run --rm --init -v "$PWD":/showcase -v quarkus-desktop-linux-m2:/root/.m2 quarkus-desktop-showcase-linux \
    java tools/Cycle.java linux --trace
# a second JVM run (determinism : MATCH, every image identical)
docker run --rm --init -v "$PWD":/showcase -v quarkus-desktop-linux-m2:/root/.m2 quarkus-desktop-showcase-linux \
    sh -c 'java tools/Snapshot.java jvm jvm-linux-2 && java tools/Compare.java comparison/jvm-linux comparison/jvm-linux-2 comparison/diff-jvm-linux'
# awt-only variant
docker run --rm --init -v "$PWD":/showcase -v quarkus-desktop-linux-m2:/root/.m2 quarkus-desktop-showcase-linux \
    java tools/Cycle.java linux-awt --awt-only --trace
# exact reachability metadata : no "run.log lines about missing metadata" expected after the native snapshots
docker run --rm --init -v "$PWD":/showcase -v quarkus-desktop-linux-m2:/root/.m2 quarkus-desktop-showcase-linux \
    java tools/Cycle.java linux-exact --exact
```

The builds write `target/` and `comparison/` of the mounted directory (on Docker Desktop, a copy of the sources in a
Docker volume builds faster). Run the JVM and the native snapshots of a comparison in the same container (as
`Cycle.java` does): `sound` renders a MIDI file with the default soundbank of the JDK, and without one (Linux, macOS) the
JDK generates an emergency soundbank, different at each generation, that the first run caches in `~/.gervill` for the
next ones. Expected results: `MATCH`, every page identical except the `EXPECTED` differences of
`overview-native-limits`; the macOS and Windows pages only state that they are not available on this OS. Compare Linux
runs with Linux runs only (another GraalVM release line, other fonts). `--pipeline=x11` (no XRender) and
`--pipeline=opengl` (GLX, with the Mesa libraries of the image) run the cycle with another Java2D pipeline. The Robot
pages wait for what X11 does asynchronously (the focus back after FocusOut, a window placed, painted, raised above the
always-on-top windows, stable bounds): see "Focus" in "Writing a page". On Linux, the pages expect what X11 does:
the release after a drag has no click count and the wheel sends one event per notch (`awt-events`), F10 opens the first
menu on its first item (`awt-menus`), MIME types are native clipboard formats (`dt-clipboard`), no drag images
(`dt-dnd`), an output tray combo box in the print dialog and the "No print service found" message without printer
(`print-dialogs`), `PSPrinterJob` printing to a stream service itself (`print-java2d`), the file view icons of the look
and feel as system icons (`swing-choosers`).

## Writing a page

A page is a CDI bean implementing `io.quarkiverse.desktop.showcase.core.FeaturePage`:

```java
@Singleton
public class ShapesPage implements FeaturePage {
    public String id() { return "j2d-shapes"; }              // unique, file-name safe, <group>-<name>
    public String title() { return "Shapes and geometry"; }
    public String category() { return Categories.JAVA2D; }    // one of Categories.ORDER
    public int order() { return 10; }                         // order within the category
    public Component build() { ... }                          // fresh content each time, on the EDT
    // optional: ready(content), extraSnapshots(content), runtimeDependent(), needsFocus(), dispose(content)
}
```

- **Packaging**: a class referencing `javax.swing` lives under `pages.swing.**`, `pages.laf.**` or `ui.swing.**`.
  Everything else must compile and run without Swing (it is part of the awt-only variant).
- **Content**: AWT pages return lightweight containers (`core.Ui.column/row`, `java.awt.Container`) and AWT components;
  Swing pages return a `JPanel`. The page frame is white, at least 1060 x 760 with 16 px of padding, larger when the
  content prefers.
- **Checks**: non-visual results go into `Check`s (`Checks.run/expect/info`, `Check.info/pass/fail`) shown with
  `ChecksView.table(title, checks)` (a lightweight AWT component, usable in AWT and Swing pages) or attached to any
  component with `Checks.attach`. Values are deterministic text: no timing, address, hash code, file path or URL; format
  numbers with `Checks.num`, shapes with `Checks.bounds`, pixels with `Checks.argb`.
- **Determinism**: two runs must render the same pixels. No running animation or timer, no clock, no randomness, no
  caret, no hover, no focus decoration, fixed locale-independent formatting. Snapshot mode stops tooltips and caret
  blinking, drops real mouse events, and moves the focus to a sink before each capture. Java2D content is best drawn
  into a `BufferedImage` (`Snapshots.offscreen`) and shown with `Ui.image`. Verify with two JVM runs and a `-Xint` run:
  `Compare` must say `MATCH`.
- **Asynchronous work**: `ready(content)` returns a stage completing when the content is fully rendered; use `core.Edt`
  (`rounds`, `delay`, `until`, `timeout`, `stable`, `background`), whose stages complete on the EDT. Never block the EDT.
- **Resources**: under `src/main/resources/showcase/<group>/...`, read with `Edt.resource*` (never show a resource URL:
  `jar:` on the JVM, `resource:` in a native executable).
- **Focus**: a page needing the keyboard focus or real input (Robot) returns `needsFocus() = true`: it runs while the
  showcase holds a machine-wide lock, after its window was brought to the front. On a live desktop another application
  may take the foreground at any time, and Windows refuses the foreground to a background process that did not receive
  the last input, while it may still activate the window inside the process (Java then reports a focused window that
  gets no keyboard input). The core handles it:
  - `Edt.ownsFocus()` is true only when a showcase window is focused **and** no other process owns the foreground
    (`core.Foreground` asks Windows with `GetForegroundWindow`, through the Foreign Function and Memory API; the
    environment key `foregroundCheck` shows it);
  - `Edt.awaitFocus(millis)` waits a moment for the focus to come back (never on the EDT): X11 moves the focus with
    FocusOut then FocusIn (the application briefly has no focused window), Windows has no foreground window during an
    activation change;
  - `Focus.acquire(window)` brings a window to the front and waits until it really has the focus (at most 4 attempts);
    on Windows the later attempts click the middle of the title bar of a decorated showcase window, as a user would,
    only where `WindowFromPoint` says the window under the point belongs to the showcase, and move the pointer back. On
    Linux and macOS `core.Foreground` knows nothing (the Java focus state is trusted) and each attempt is `toFront` and
    `requestFocus` only: X11 activates windows through the window manager (`_NET_ACTIVE_WINDOW`; the Docker window
    manager has no mouse bindings, a click would activate nothing), and on macOS `Desktop.requestForeground` is the
    opt-in `dock` side effect;
  - `RobotSession` (from a background thread) sends keys and mouse buttons only when `Edt.ownsFocus()` (waiting up to
    500 ms with `Edt.awaitFocus`), waits for each key press and release to be dispatched before the next input (a
    modifier pressed too early or released too late changes the result), restores the pointer and releases everything
    when closed; `nativeKeys(true)` for keys that a native loop consumes (Windows menu loop, X11 menu grabs),
    `idleAfterInput(false)` during a drag and drop, `finishDrop` for a drag whose button release the drag loop missed
    (a small move, then a click on the drop target, only once the drag started), the modifier keys held until the drop
    is done;
  - windows are placed, painted and stacked asynchronously, above all on X11: `Focus.awaitPlaced` before Robot
    coordinates are computed from a window location (a bare X server confirms it after the focus, sometimes),
    `RobotSession.waitForPixel` until a new window is painted (X11 shows its unpainted native background until then),
    `RobotSession.raiseUntil` brings a focused but covered window back to the front while a probe pixel shows it covered
    (without a window manager always-on-top windows keep their mapping order; a restacking window manager may cover an
    override-redirect `POPUP` window), `Edt.untilStable` for bounds that a window manager configures in several steps;
  - on macOS Robot needs the Accessibility (input) and Screen Recording (pixels) permissions (TCC) of the application
    that started the showcase (environment keys `macos.tcc.input` and `macos.tcc.screenCapture`, probed with
    `-Dshowcase.robot=true`: two always-on-top windows read with a tolerance for 1.5 s, a pointer move tried three
    times). When a denial is known, `RobotSession` presses no key or button (skipped, as without the focus) and its
    pixel waits (`waitForPixel`, `raiseUntil`, `awaitVisible`) end at the first mismatch without raising windows;
    without the probe the input is silently dropped (the retries run to their limit, the checks tell). The menu bar of
    an AWT `Frame` is the screen menu bar on macOS: `awt-menus` does not drive it with the keyboard there (F10 does not
    open it), only its popup menu;
  - a Robot sequence whose effect is missing is done again (bounded, the window focused again first), and the number
    of attempts is recorded with `Check.attempts(action, n)`: an informational check in `report.json` only (not painted
    by `ChecksView`), whose differences `Compare` reports as `attempts:` notes, not as mismatches. What was missing is
    logged with `RobotSession.logRetry` (with the foreground owner on Windows), in the log only: it depends on the
    desktop, never put it in a check value.
- **Shared helpers**: `core.Grid` (captioned tiles painted offscreen, pixel probes) and `core.Slot` (an image shown once
  ready), `core.RobotSession`, `core.Focus`.
- **Safety** (the showcase runs on real desktops): never print to a real printer (only `StreamPrintService` PostScript
  into memory or files), close print and page dialogs programmatically, never call `Desktop.browse/open/mail/print/edit`
  or `TrayIcon.displayMessage` unless allowed (`-Dshowcase.sideEffects=true` allows every side effect, a comma
  separated list only these: `browse`, `tray-balloon`, `attention`, `taskbar`, `dock`; for instance
  `-Dshowcase.sideEffects=tray-balloon` shows one notification), send Robot key presses only while one of the showcase
  windows is focused (`Edt.ownsFocus()`, otherwise record `skipped: not focused`), restore the mouse position after
  Robot moves, save and restore the user's clipboard text, no fullscreen or display mode change unless
  `-Dshowcase.fullscreen=true`, and dispose every window a page opens.

## Pages

Each page is a class of `src/main/java/io/quarkiverse/desktop/showcase/pages/<group>`. The catalogue follows the
feature surface of the JDK desktop modules: Overview, AWT, Java2D, Text & Fonts, Images & Color, Swing Components,
Look & Feel, Data Transfer & Desktop, Printing, Accessibility & Beans, Sound.

75 pages with 3767 checks in the default variant, 40 pages in the awt-only variant (the 8 macOS pages only report
that they are not available on Windows and Linux). Classes are
relative to `io.quarkiverse.desktop.showcase`. *Checks*: number of checks of a Windows JVM run (some pages have
platform-specific checks). *Extras*: additional images (`<id>--<name>.png`). *focus*: the page needs the keyboard focus
or real input (Robot), see "Writing a page". *runtime dependent*: shows values that legitimately differ between the JVM
and a native executable (reported as `EXPECTED`).

| Category | Id | Title | Class | Checks | Extras | awt-only | Notes |
|---|---|---|---|---:|---:|---|---|
| Overview | `overview-environment` | Environment | `pages.overview.EnvironmentPage` | 82 | 0 | yes |  |
| Overview | `overview-native-limits` | Native limits | `pages.limits.NativeLimitsPage` | 28 | 0 | yes | runtime dependent |
| AWT | `awt-components` | AWT components | `pages.awt.AwtComponentsPage` | 50 | 0 | yes |  |
| AWT | `awt-menus` | AWT menus | `pages.awt.AwtMenusPage` | 25 | 4 | yes | focus |
| AWT | `awt-layouts` | AWT layouts | `pages.awt.AwtLayoutsPage` | 43 | 0 | yes |  |
| AWT | `awt-windows` | Windows and dialogs | `pages.awt.AwtWindowsPage` | 45 | 1 | yes | focus |
| AWT | `awt-events` | Event dispatch | `pages.awt.AwtEventsPage` | 44 | 1 | yes | focus |
| AWT | `awt-focus` | Focus traversal | `pages.awt.AwtFocusPage` | 24 | 0 | yes | focus |
| Java2D | `j2d-shapes` | Shapes and geometry | `pages.java2d.ShapesPage` | 36 | 0 | yes |  |
| Java2D | `j2d-strokes` | Strokes | `pages.java2d.StrokesPage` | 42 | 0 | yes |  |
| Java2D | `j2d-paints` | Paints and colors | `pages.java2d.PaintsPage` | 45 | 0 | yes |  |
| Java2D | `j2d-composites` | Composites and XOR mode | `pages.java2d.CompositesPage` | 60 | 0 | yes |  |
| Java2D | `j2d-transforms-clip` | Transforms, clipping and hints | `pages.java2d.TransformsClipPage` | 45 | 0 | yes |  |
| Java2D | `j2d-surfaces` | Surfaces and image types | `pages.java2d.SurfacesPage` | 104 | 0 | yes |  |
| Java2D | `j2d-onscreen-pipeline` | On-screen pipeline and BufferStrategy | `pages.java2d.OnscreenPipelinePage` | 34 | 0 | yes |  |
| Text & Fonts | `text-fonts` | Fonts and rendering modes | `pages.text.TextFontsPage` | 88 | 0 | yes |  |
| Text & Fonts | `text-attributes-layout` | Attributes and layout | `pages.text.TextAttributesLayoutPage` | 43 | 0 | yes |  |
| Text & Fonts | `text-international` | International text | `pages.text.TextInternationalPage` | 40 | 0 | yes |  |
| Images & Color | `images-imageio-formats` | ImageIO formats | `pages.images.ImageIoFormatsPage` | 85 | 0 | yes |  |
| Images & Color | `images-imageio-metadata` | ImageIO metadata and plugins | `pages.images.ImageIoMetadataPage` | 75 | 0 | yes |  |
| Images & Color | `images-ops` | Image operations | `pages.images.ImageOpsPage` | 59 | 0 | yes |  |
| Images & Color | `images-toolkit` | Toolkit imaging | `pages.images.ToolkitImagingPage` | 45 | 0 | yes |  |
| Images & Color | `images-color-management` | Color management (ICC) | `pages.images.ColorManagementPage` | 46 | 0 | yes |  |
| Images & Color | `desktop-mac-nsimage` | macOS images (NSImage, @2x) | `pages.images.MacImagesPage` | 1 | 1 | yes | macOS |
| Swing Components | `swing-buttons` | Buttons and range controls | `pages.swing.controls.ButtonsPage` | 37 | 0 |  |  |
| Swing Components | `swing-text-fields` | Text fields, formatters and spinners | `pages.swing.controls.TextFieldsPage` | 41 | 0 |  |  |
| Swing Components | `swing-text-documents` | Text documents, styles and undo | `pages.swing.controls.TextDocumentsPage` | 34 | 0 |  |  |
| Swing Components | `swing-html-rtf` | HTML and RTF | `pages.swing.controls.HtmlRtfPage` | 39 | 0 |  |  |
| Swing Components | `swing-lists-combos` | Lists and combo boxes | `pages.swing.controls.ListsCombosPage` | 21 | 2 |  | focus |
| Swing Components | `swing-table` | Tables | `pages.swing.controls.TablePage` | 29 | 1 |  |  |
| Swing Components | `swing-tree` | Trees | `pages.swing.controls.TreePage` | 20 | 0 |  |  |
| Swing Components | `swing-containers` | Containers | `pages.swing.containers.SwingContainersPage` | 102 | 1 |  |  |
| Swing Components | `swing-internal-frames` | Internal frames | `pages.swing.containers.InternalFramesPage` | 41 | 1 |  |  |
| Swing Components | `swing-menus-popups` | Menus and popups | `pages.swing.containers.MenusPopupsPage` | 53 | 7 |  |  |
| Swing Components | `swing-option-pane-dialogs` | Option panes and dialogs | `pages.swing.containers.OptionPaneDialogsPage` | 47 | 3 |  |  |
| Swing Components | `swing-choosers` | Color and file choosers | `pages.swing.containers.ChoosersPage` | 49 | 1 |  |  |
| Swing Components | `swing-decorations` | Borders, separators, tool tips, JLayer | `pages.swing.containers.DecorationsPage` | 31 | 3 |  |  |
| Swing Components | `swing-awt-mixing` | Heavyweight and lightweight mixing | `pages.swing.containers.AwtMixingPage` | 21 | 2 |  | focus |
| Swing Components | `swing-layouts` | Swing layouts | `pages.swing.infra.SwingLayoutsPage` | 56 | 0 |  |  |
| Swing Components | `swing-keybindings` | Key bindings | `pages.swing.infra.SwingKeyBindingsPage` | 137 | 0 |  | focus |
| Swing Components | `swing-painting` | Painting and RepaintManager | `pages.swing.infra.SwingPaintingPage` | 37 | 0 |  |  |
| Swing Components | `swing-concurrency` | Timers, workers and event loops | `pages.swing.infra.SwingConcurrencyPage` | 40 | 0 |  |  |
| Swing Components | `swing-rtl-i18n` | Right to left and localization | `pages.swing.infra.SwingRtlI18nPage` | 112 | 0 |  |  |
| Swing Components | `swing-printing` | Swing printing | `pages.swing.infra.SwingPrintingPage` | 49 | 7 |  |  |
| Look & Feel | `laf-metal` | Metal (Ocean, Steel, themes) | `pages.laf.MetalPage` | 176 | 7 |  |  |
| Look & Feel | `laf-nimbus` | Nimbus | `pages.laf.NimbusPage` | 175 | 1 |  |  |
| Look & Feel | `laf-synth-xml` | Synth from XML | `pages.laf.SynthXmlPage` | 178 | 0 |  |  |
| Look & Feel | `laf-motif` | CDE/Motif | `pages.laf.MotifPage` | 144 | 0 |  |  |
| Look & Feel | `laf-windows` | Windows and Windows Classic | `pages.laf.WindowsPage` | 176 | 1 |  |  |
| Look & Feel | `laf-gtk` | GTK+ | `pages.laf.GtkPage` | 2 | 0 |  |  |
| Look & Feel | `laf-aqua` | Aqua (macOS) | `pages.laf.AquaPage` | 2 | 1 |  | macOS |
| Look & Feel | `laf-aqua-client-properties` | Aqua variants (macOS) | `pages.laf.AquaVariantsPage` | 1 | 0 |  | macOS |
| Look & Feel | `laf-switching` | Switching, custom and auxiliary look and feels | `pages.laf.SwitchingPage` | 44 | 7 |  |  |
| Data Transfer & Desktop | `dt-clipboard` | Clipboard | `pages.datatransfer.ClipboardPage` | 76 | 0 | yes |  |
| Data Transfer & Desktop | `dt-dnd` | Drag and drop (AWT) | `pages.datatransfer.DragAndDropPage` | 31 | 0 | yes | focus |
| Data Transfer & Desktop | `dt-dnd-swing` | Drag and drop (Swing) | `pages.swing.desktop.SwingDragAndDropPage` | 36 | 0 |  | focus |
| Data Transfer & Desktop | `desktop-services` | Desktop, Taskbar, SystemTray and cursors | `pages.desktop.DesktopServicesPage` | 59 | 0 | yes | The events of the tray icon are logged in interactive mode (click the icon); in the Linux image, Robot clicks it |
| Data Transfer & Desktop | `desktop-robot` | Robot | `pages.desktop.RobotPage` | 30 | 1 | yes | focus |
| Data Transfer & Desktop | `desktop-screens-hidpi` | Screens and HiDPI | `pages.desktop.ScreensHiDpiPage` | 48 | 0 | yes |  |
| Data Transfer & Desktop | `desktop-input-methods` | Input methods (AWT) | `pages.desktop.InputMethodsPage` | 28 | 1 | yes |  |
| Data Transfer & Desktop | `desktop-input-methods-swing` | Input methods (Swing) | `pages.swing.desktop.SwingInputMethodsPage` | 22 | 0 |  |  |
| Data Transfer & Desktop | `desktop-mac-app-events` | macOS application events | `pages.desktop.MacAppEventsPage` | 2 | 0 | yes | macOS |
| Data Transfer & Desktop | `desktop-mac-windows` | macOS window properties | `pages.swing.desktop.MacWindowsPage` | 1 | 2 |  | macOS |
| Data Transfer & Desktop | `desktop-mac-dock-menubar` | macOS Dock and menu bar | `pages.swing.desktop.MacDockMenuBarPage` | 1 | 1 |  | macOS |
| Data Transfer & Desktop | `desktop-mac-file-dialog` | macOS file dialogs | `pages.desktop.MacFileDialogPage` | 1 | 0 | yes | macOS |
| Printing | `print-java2d` | Printable and Book | `pages.print.PrintJava2dPage` | 36 | 5 | yes |  |
| Printing | `print-javax-print` | javax.print services | `pages.print.JavaxPrintPage` | 69 | 0 | yes |  |
| Printing | `print-dialogs` | Print dialogs | `pages.print.PrintDialogsPage` | 49 | 4 | yes |  |
| Printing | `print-mac` | macOS printing | `pages.print.MacPrintPage` | 1 | 0 | yes | macOS |
| Accessibility & Beans | `a11y-contexts` | Accessibility API (AWT) | `pages.a11y.AccessibilityPage` | 32 | 0 | yes |  |
| Accessibility & Beans | `a11y-contexts-swing` | Accessibility API (Swing) | `pages.swing.a11y.SwingAccessibilityPage` | 25 | 0 |  |  |
| Accessibility & Beans | `beans-introspection` | JavaBeans introspection | `pages.beans.BeansIntrospectionPage` | 52 | 0 | yes |  |
| Accessibility & Beans | `beans-xml-persistence` | XMLEncoder and XMLDecoder | `pages.beans.XmlPersistencePage` | 29 | 0 | yes |  |
| Accessibility & Beans | `beans-xml-persistence-swing` | XMLEncoder and XMLDecoder (Swing form) | `pages.swing.beans.SwingXmlPersistencePage` | 11 | 0 |  |  |
| Sound | `sound` | Sampled audio and MIDI | `pages.sound.SoundPage` | 45 | 0 | yes |  |

*macOS*: pages that only apply on macOS (Aqua, the application events, the Dock and the menu bar, the window client
properties, the native file dialogs, NSImage and `@2x` images, Cocoa printing). Elsewhere they only show an
`availability` check (`not available on this OS`, like `laf-gtk` on Windows); on macOS their extras and checks are those
of the page description (extras: `laf-aqua--gallery-2x`, `desktop-mac-windows--plain` and `--styled`,
`desktop-mac-dock-menubar--screen-menu-bar-frame`, `desktop-mac-nsimage--2x`).

## Verifying on macOS

The macOS support of quarkus-desktop (native executables) can only run on a Mac: this is the check list to run on an
Apple silicon Mac, from a Terminal window of a graphical session (never over `ssh`: AWT is headless there), in a
directory outside `~/Desktop`, `~/Documents` and `~/Downloads` (macOS asks for permissions there). The macOS pages and
checks of the showcase are listed above; `overview-environment` also checks that `QuarkusApplication.run()` runs on the
thread `main` and that the `AppKit Thread` exists (AWT owns the application), and records the `macos.*` and
`property.*` keys of the environment.

### 1. Tools, GraalVM, Quarkus and quarkus-desktop

```bash
xcode-select --install                     # clang, otool, nm, codesign
mkdir -p ~/dev && cd ~/dev
curl -LO https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.4.4.1.1/graalvm-community-jdk-25i4-25.0.4.1.1_macos-aarch64_bin.tar.gz
tar xzf graalvm-community-jdk-25i4-25.0.4.1.1_macos-aarch64_bin.tar.gz
export JAVA_HOME=$(echo ~/dev/graalvm-community-*/Contents/Home) GRAALVM_HOME=$JAVA_HOME PATH=$JAVA_HOME/bin:$PATH
native-image --version                      # GraalVM CE 25.4 (25.1 or later is needed)
xattr -l $JAVA_HOME/lib/libawt.dylib        # nothing ; otherwise: xattr -dr com.apple.quarantine ~/dev/graalvm-community-*
# Quarkus built from the pull request "Enable quarkus-awt on macOS" (quarkus-awt with macOS support, not released yet)
git clone --filter=blob:none https://github.com/quarkusio/quarkus quarkus-pr-56979
cd quarkus-pr-56979
git fetch https://github.com/quarkusio/quarkus pull/56979/head:pr-56979 && git checkout pr-56979
./mvnw -Dquickly                            # installs Quarkus 999-SNAPSHOT (tens of minutes)
cd ~/dev
git clone https://github.com/Eng-Fouad/quarkus-desktop
(cd quarkus-desktop && ./mvnw -B install -DskipTests)
git clone https://github.com/Eng-Fouad/quarkus-desktop-showcase
```

The quarkus-desktop documentation page "Verifying macOS support" (`docs/modules/ROOT/pages/macos-verification.adoc`)
checks a small probe application first (window, threads, libraries, Metal, exit codes): run it before the showcase.

### 2. JVM and native cycles

```bash
cd ~/dev/quarkus-desktop-showcase
Q=--maven-args=-Dquarkus.platform.version=999-SNAPSHOT
java tools/Cycle.java mac --trace $Q                         # JVM, tracing agent, native, compare
java tools/Cycle.java mac-awt-only --awt-only $Q             # the AWT pages (drawn by Aqua delegates on macOS)
java tools/Cycle.java mac-hidpi --hidpi $Q                   # real Retina scale (2.0 in both runs)
java tools/Cycle.java mac-exact --exact $Q                   # accesses missing from the metadata, in native run.log
java tools/Cycle.java mac-opengl $Q -- -Dsun.java2d.opengl=true
```

What to look at:

- the first line of `comparison/logs-mac/compare.txt`: `MATCH` expected, except the runtime dependent pages;
- `ENV DIFF` lines: none expected. `pipeline` must be `MTLGraphicsConfig` in both runs (`CGLGraphicsConfig` in the
  native run means the Metal shaders are missing), `macos.appKitThread` `true` in both runs, `mainThread` `main`;
- no `InternalError` from `LWComponentPeer` (the reflection of `java.awt.Toolkit#eventListener`), no `Bad JNI lookup`
  in `comparison/native-mac/run.log`; `laf-aqua-client-properties`: `null borders` is 0 in both runs;
- `comparison/logs-mac/native-artifacts.txt`: the `.dylib` libraries next to the executable (`libawt`,
  `libawt_lwawt`, `libosxapp`, `libosxui`, `libfontmanager`, `libjava`, `libjvm`...), `CoreFoundation` in `otool -L`,
  `@loader_path` in `LC_RPATH`, `Signature=adhoc`;
- `comparison/trace-mac/metadata-diff.md`: the registrations that the `MAC_` lists of quarkus-desktop miss (the `## `
  headings with counts);
- a stuck run is killed by the watchdog after `sample` wrote `comparison/<label>/hang-sample.txt`: the main thread must
  be in `CFRunLoopRun` / `-[NSApplication run]`, not in `pthread_cond_wait`.

### 3. Robot and privacy permissions

Robot needs the Screen Recording permission (captures) and the Accessibility permission (input events) of the application
that starts the tools (Terminal, iTerm2 or IntelliJ IDEA): System Settings > Privacy & Security > Screen & System Audio
Recording, and > Accessibility; restart the terminal after granting them. Then:

```bash
java tools/Cycle.java mac-robot --skip-native-build -- -Dshowcase.robot=true
```

The environment keys `macos.tcc.screenCapture` and `macos.tcc.input` must be `true` in both runs. Reset the permissions
with `tccutil reset ScreenCapture com.apple.Terminal` and `tccutil reset Accessibility com.apple.Terminal`.

### 4. Manual checks (JVM, then native)

```bash
java --add-opens java.desktop/java.awt=ALL-UNNAMED --add-opens java.desktop/sun.lwawt=ALL-UNNAMED \
    -Dshowcase.interactive=true -Dshowcase.pages=desktop-mac-,laf-aqua,print-mac -jar target/quarkus-app/quarkus-run.jar
./target/*-runner -Dshowcase.interactive=true -Dshowcase.pages=desktop-mac-,laf-aqua,print-mac
```

With `-Dshowcase.sideEffects=true`, the Dock page also sets a badge, an icon, a menu and a progress value, and requests
attention (restored when the page is left). Check, and note what differs between the JVM and the native executable:

- `desktop-mac-app-events`: application menu > About (an event in the log, not the standard About panel), Settings,
  Quit (the page cancels it), hide (Cmd+H) and show, Dock icon click with no window (reopened);
- `desktop-mac-dock-menubar`: the `Screen menu bar` frame shows its `JMenuBar` in the macOS menu bar when active, the AWT
  frame its `MenuBar`; the Dock badge, icon, menu and progress with side effects;
- `desktop-mac-windows`: transparent small title bar without title, full size content, document modified dot and proxy
  icon, no zoom button, textured tool bar;
- `desktop-mac-file-dialog`: the button shows the open panel (multiple selection, `.txt` filter), the directory panel,
  the save panel (nothing is written);
- `print-mac`: the print panel and the page layout panel (cancel them, or "Save as PDF");
- `laf-aqua`: the Aqua gallery, and Aqua drawing the AWT components (`awt-components` in the awt-only variant);
- VoiceOver (Cmd+F5) and Accessibility Inspector over the Swing gallery and the AWT pages: names read, no crash;
- open files, URIs and print files need an application bundle: `sh tools/mac-app-bundle.sh native` (or `jvm`), then the
  commands at the top of that script;
- exit paths: close the main window (exit code 0), Cmd+Q (0), Ctrl-C in the terminal (the shutdown hooks run).

### 5. Report back

Send the results (or attach them to an issue of quarkus-desktop):

- `sw_vers`, `uname -m`, `native-image --version`, the Quarkus commit (`git -C ~/dev/quarkus-pr-56979 rev-parse HEAD`);
- the `comparison/` directory (at least `logs-*/`, `diff-*/summary.txt`, `trace-mac/metadata-diff.md`, the
  `report.json` and `run.log` of every run, `hang-sample.txt` if any) and `native-artifacts.txt`;
- the result of each manual check (passed, or what happened, with screenshots), for the JVM and the native executable.

A missing registration (`Bad JNI lookup`, `MissingReflectionRegistrationError`, `NoSuchMethodError`...) goes into the
`MAC_` lists of `AwtClassesAndResources` or `SwingClassesAndResources` of quarkus-desktop.

### Results (September 2026)

Apple silicon, macOS 27.0, one non-Retina display (scale 1 : the Retina scale is not verified), GraalVM CE 25.4.4.1.1,
Quarkus built from the pull request 56979, the Screen Recording and Accessibility permissions granted
(`-Dshowcase.robot=true`):

- default variant: MATCH, every image identical except `overview-native-limits` (EXPECTED) and the color noise of the
  raw Robot captures (see below); awt-only variant: MATCH ; `--exact` in both variants: no access missing from the
  metadata, MATCH.
- the pages need the showcase to be the active application: AWT never activates it, so a click or a typed key in
  another application during a run takes the focus away and the Robot input of the following pages is skipped
  (`not focused after 4 attempts` in run.log, then differences between the runs). Do not use the Mac while a cycle
  runs, and rerun a cycle whose run.log has such lines.
- the colors of Robot screen captures come through the color profile of the display, one to three levels apart
  from one run to the next: the Robot pages compare screen colors within `RobotSession.COLOR_TOLERANCE` and report the
  expected color then, and Compare reports the differences of the raw captures (`captures` of report.json) up to that
  tolerance as NOISE.
- the natural scrolling setting (System Settings, Mouse) changes the sign of the wheel rotations of `awt-events`: only
  the "off" case was observed (`-1 2`); with it on, the page checks the magnitudes and shows the signs as information.
