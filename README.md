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

- Windows x64 or Linux x64 (native executables), or macOS (JVM mode only: GraalVM native images do not support AWT on
  macOS)
- JDK 25 for the JVM mode and the tools, GraalVM for JDK 25 for native executables (`GRAALVM_HOME`)
- quarkus-desktop `999-SNAPSHOT` installed in the local Maven repository (`mvn install` in a quarkus-desktop checkout)
- Native builds: the [Quarkus native prerequisites](https://quarkus.io/guides/building-native-image) (Visual Studio
  Build Tools on Windows, gcc and zlib development packages on Linux)

Maven is provided by the wrapper (`./mvnw` on Linux and macOS, `mvnw.cmd` on Windows); a local `mvn` works too.

## Run

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Native executable (`...-runner.exe` on Windows):

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

`tools/Snapshot.java jvm|native [label] [--pages=ids] [--categories=names] [--awt-only] [--hidpi] [--screen] [--trace]
[-- options...]`:

- `-Duser.language=en -Duser.country=US` and `-Dsun.java2d.uiScale=1` are added unless given after `--` (a native
  executable defaults to the locale of the build machine; AWT heavyweight components render correctly with `printAll`
  at scale 1 only).
- `--hidpi` keeps the real UI scale. Forcing the scale to 1 hides a DPI unaware native executable: compare a `--hidpi`
  JVM run with a `--hidpi` native run (report keys `defaultTransform` and `screenResolution`).
- `--awt-only` runs the awt-only variant (`target/awt-only`). `--screen` also saves a Robot screen capture of the
  window per page (`<page>--screen.png`, reported as `SCREEN`, never a mismatch).
- `--trace` (JVM only) runs under the GraalVM tracing agent (`comparison/<label>/metadata`), see below.

`java tools/Cycle.java <label> [--trace] [--exact] [--awt-only] [--hidpi] [--pages=...] [--offline] [--skip-jvm]
[--skip-native-build] [--maven-args=a,b] [--native-args=a,b] [-- options]` runs a whole iteration: JVM build and
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
- never creates AWT or Swing objects in the static initializer of a class that is not itself an AWT/Swing subclass
  (Quarkus initializes application classes at build time, AWT and Swing classes at run time).

## Native image configuration tools

- `java tools/Cycle.java <label> --trace` (or `java tools/Snapshot.java jvm trace --trace`): JVM snapshots under the
  GraalVM tracing agent, then `tools/MetadataDiff.java` lists the JNI, reflection, resource, bundle, serialization and
  proxy accesses of the JDK desktop modules that quarkus-desktop does not register for the current platform:
  `java tools/MetadataDiff.java comparison/trace/metadata/reachability-metadata.json [windows|linux] [--awt-only]`.
  It reads the `static String[]` lists of `io.quarkiverse.desktop.awt.deployment.AwtClassesAndResources` and
  `io.quarkiverse.desktop.swing.deployment.SwingClassesAndResources` from the deployment jars installed in `~/.m2`,
  understands package entries and `fqcn#member` entries, and also lists stale entries (names that do not exist in the JDK).
- `java tools/ClinitAudit.java [windows|linux] [--awt-only] [class_initialization_report.csv]`: lists the JDK desktop
  classes left initialized at build time (not in the run time initialization lists of quarkus-desktop and quarkus-awt)
  whose static initializer reaches native code, library loading, threads, native memory, NIO channels, the toolkit,
  system properties or resource bundles (JDK class file API, no library needed).

## Linux in Docker

`docker/linux/Dockerfile` provides a Linux environment (GraalVM CE for JDK 25, a virtual X server, the X11, fontconfig,
CUPS, ALSA and GTK runtime libraries, fonts):

```bash
docker build -t quarkus-desktop-showcase-linux docker/linux
docker run --rm --init -v "$PWD":/showcase -v "$HOME/.m2":/root/.m2 quarkus-desktop-showcase-linux java tools/Cycle.java linux
```

Compare Linux runs with Linux runs only (another GraalVM release line, other fonts).

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
  showcase holds a machine-wide lock, after its window was brought to the front.
- **Safety** (the showcase runs on real desktops): never print to a real printer (only `StreamPrintService` PostScript
  into memory or files), close print and page dialogs programmatically, never call `Desktop.browse/open/mail/print/edit`
  or `TrayIcon.displayMessage` unless `-Dshowcase.sideEffects=true`, send Robot key presses only while one of the
  showcase windows is focused (`Edt.ownsFocus()`, otherwise record `skipped: not focused`), restore the mouse position
  after Robot moves, save and restore the user's clipboard text, no fullscreen or display mode change unless
  `-Dshowcase.fullscreen=true`, and dispose every window a page opens.

## Pages

Each page is a class of `src/main/java/io/quarkiverse/desktop/showcase/pages/<group>`. The catalogue follows the
feature surface of the JDK desktop modules: Overview, AWT, Java2D, Text & Fonts, Images & Color, Swing Components,
Look & Feel, Data Transfer & Desktop, Printing, Accessibility & Beans, Sound.
