package io.quarkiverse.desktop.showcase.pages.datatransfer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.Platforms;

/**
 * Another application using the system clipboard : a child process that reads what the showcase wrote (so that the
 * native publication of every format is verified by a non-Java program) and writes its own data (so that the showcase
 * reads data through the native clipboard code of AWT : the data a JVM wrote itself never leaves the JVM).
 * <ul>
 * <li>Windows : Windows PowerShell with Windows Forms ({@code showcase/desktop/foreign-clipboard.ps1}), every format.</li>
 * <li>Linux : {@code xclip}, when installed (text only).</li>
 * </ul>
 */
final class ForeignClipboard {

    private static final long TIMEOUT_SECONDS = 25;

    /** The results of the child process (key : decoded value), or why it did not run. */
    record Result(Map<String, String> values, String skipped, String error) {

        String get(String key) {
            return values.get(key);
        }
    }

    private ForeignClipboard() {
    }

    /**
     * The tool used on this platform, or {@code null} if there is none.
     */
    static String tool() {
        if (Platforms.isWindows()) {
            Path ps = powerShell();
            return ps != null && Files.isRegularFile(ps) ? "Windows PowerShell" : null;
        }
        return Platforms.isLinux() && xclip() != null ? "xclip" : null;
    }

    /**
     * Windows : runs the script, which reads the clipboard written by the showcase, then writes the foreign data.
     *
     * @param serialFormat the native clipboard format of the serialized payload
     */
    static Result runWindows(String serialFormat) {
        Path ps = powerShell();
        if (ps == null || !Files.isRegularFile(ps)) {
            return new Result(Map.of(), "no Windows PowerShell", null);
        }
        try {
            Path foreignFile = Edt.tempDir().resolve(ClipboardData.FOREIGN_FILE);
            Files.writeString(foreignFile, "foreign file\n");
            String script = Edt.resourceText("/showcase/desktop/foreign-clipboard.ps1");
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder builder = new ProcessBuilder(ps.toString(), "-NoProfile", "-NonInteractive", "-Sta",
                    "-EncodedCommand", encoded);
            Map<String, String> env = builder.environment();
            env.put("SHOWCASE_SERIAL_FORMAT", serialFormat);
            env.put("SHOWCASE_FOREIGN_TEXT", ClipboardData.FOREIGN_TEXT);
            env.put("SHOWCASE_FOREIGN_HTML", ClipboardData.FOREIGN_HTML);
            env.put("SHOWCASE_FOREIGN_RTF", ClipboardData.FOREIGN_RTF);
            env.put("SHOWCASE_FOREIGN_URL", ClipboardData.FOREIGN_URL);
            env.put("SHOWCASE_FOREIGN_FILE", foreignFile.toString());
            builder.redirectErrorStream(true);
            Output out = run(builder, null);
            Map<String, String> values = new LinkedHashMap<>();
            List<String> other = new ArrayList<>();
            for (String line : out.text().split("\\R")) {
                int eq = line.indexOf('=');
                if (eq > 0 && line.substring(0, eq).matches("[a-z]+")) {
                    String key = line.substring(0, eq);
                    String value = line.substring(eq + 1).trim();
                    values.put(key, isText(key) && !value.equals("-") ? decode(value) : value);
                } else if (!line.isBlank()) {
                    other.add(line.trim());
                }
            }
            String error = out.exitCode() == 0 ? null
                    : "exit code " + out.exitCode() + (other.isEmpty() ? "" : ": " + other.getFirst());
            return new Result(values, null, error);
        } catch (IOException | RuntimeException e) {
            return new Result(Map.of(), null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean isText(String key) {
        return List.of("formats", "text", "html", "rtf", "files", "url").contains(key);
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    /**
     * Linux : reads the targets and the text of the CLIPBOARD selection with xclip.
     */
    static Result readLinux() {
        Path xclip = xclip();
        if (xclip == null) {
            return new Result(Map.of(), "xclip not installed", null);
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            Output targets = run(new ProcessBuilder(xclip.toString(), "-selection", "clipboard", "-o", "-t", "TARGETS"),
                    null);
            values.put("formats", String.join("\n", targets.text().lines().map(String::trim).filter(s -> !s.isEmpty())
                    .sorted().toList()));
            Output text = run(new ProcessBuilder(xclip.toString(), "-selection", "clipboard", "-o", "-t", "UTF8_STRING"),
                    null);
            values.put("text", text.text());
            return new Result(values, null, text.exitCode() == 0 ? null : "exit code " + text.exitCode());
        } catch (IOException | RuntimeException e) {
            return new Result(values, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Linux : xclip takes the CLIPBOARD selection with the foreign text (it serves it from a background process until
     * another application takes the selection, i.e. until the page restores the user's clipboard).
     */
    static Result writeLinux() {
        Path xclip = xclip();
        if (xclip == null) {
            return new Result(Map.of(), "xclip not installed", null);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(xclip.toString(), "-selection", "clipboard", "-t", "UTF8_STRING",
                    "-i");
            Output out = run(builder, ClipboardData.FOREIGN_TEXT.getBytes(StandardCharsets.UTF_8));
            return new Result(Map.of("written", String.valueOf(out.exitCode() == 0)), null,
                    out.exitCode() == 0 ? null : "exit code " + out.exitCode());
        } catch (IOException | RuntimeException e) {
            return new Result(Map.of(), null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private record Output(int exitCode, String text) {
    }

    private static Output run(ProcessBuilder builder, byte[] input) throws IOException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (OutputStream stdin = process.getOutputStream()) {
            if (input != null) {
                stdin.write(input);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(bytes);
            } catch (IOException e) {
                // the process ended
            }
        }, "showcase-foreign-clipboard");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return new Output(-1, "timeout");
            }
            // xclip forks a daemon serving the selection : its output stream stays open, do not wait for it
            reader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new Output(-1, "interrupted");
        }
        synchronized (bytes) {
            return new Output(process.exitValue(), bytes.toString(StandardCharsets.UTF_8));
        }
    }

    private static Path powerShell() {
        String root = System.getenv("SystemRoot");
        return root == null ? null : Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
    }

    private static Path xclip() {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isBlank()) {
                Path candidate = Path.of(dir, "xclip");
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
