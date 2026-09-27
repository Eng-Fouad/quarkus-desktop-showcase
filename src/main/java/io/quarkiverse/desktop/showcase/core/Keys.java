package io.quarkiverse.desktop.showcase.core;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The keys of the platform, for the pages that press keys with Robot or check key texts. macOS differs from Windows and
 * Linux in two ways :
 * <ul>
 * <li>it uses other modifier keys for some gestures : the menu shortcut key is Command ({@code Meta}), not Ctrl
 * ({@link Toolkit#getMenuShortcutKeyMaskEx()}) ; the mnemonics of Swing are Ctrl+Option, not Alt
 * ({@code LWCToolkit.getFocusAcceleratorKeyMask}) ; a copy drag and drop holds Option, not Ctrl (Control restricts a
 * Cocoa drag to a link) ;</li>
 * <li>it names the keys with symbols : {@code KeyEvent.getKeyText}, {@code InputEvent.getModifiersExText} and
 * {@code MenuShortcut.toString} read the {@code sun.awt.resources.awtosx} bundle there ({@code ⇧}, {@code ⌃},
 * {@code ⌥}, {@code ⌘}, {@code ⏎}...), the {@code sun.awt.resources.awt} bundle elsewhere ({@code Shift},
 * {@code Ctrl}...).</li>
 * </ul>
 * The expectations of the pages are written with the names of Windows and Linux ; {@link #text} gives the name of this
 * platform. AWT only (no Swing type).
 */
public final class Keys {

    /**
     * The names of {@code sun.awt.resources.awt} (Windows, Linux) that {@code sun.awt.resources.awtosx} (macOS)
     * replaces.
     */
    private static final Map<String, String> MAC_TEXTS = Map.ofEntries(
            Map.entry("Shift", "⇧"),
            Map.entry("Ctrl", "⌃"),
            Map.entry("Alt", "⌥"),
            Map.entry("Alt Graph", "⌥"),
            Map.entry("Meta", "⌘"),
            Map.entry("Enter", "⏎"),
            Map.entry("Space", "␣"),
            Map.entry("Escape", "⎋"),
            Map.entry("Cancel", "⎋"),
            Map.entry("Backspace", "⌫"),
            Map.entry("Delete", "⌦"),
            Map.entry("Tab", "⇥"),
            Map.entry("Caps Lock", "⇪"),
            Map.entry("Clear", "⌧"),
            Map.entry("Print Screen", "⎙"),
            Map.entry("Page Up", "⇞"),
            Map.entry("Page Down", "⇟"),
            Map.entry("Home", "↖"),
            Map.entry("End", "↘"),
            Map.entry("Left", "←"),
            Map.entry("Up", "↑"),
            Map.entry("Right", "→"),
            Map.entry("Down", "↓"),
            Map.entry("NumPad", "⌨"),
            Map.entry("Semicolon", ";"),
            Map.entry("Comma", ","),
            Map.entry("Period", "."),
            Map.entry("Slash", "/"),
            Map.entry("Back Slash", "\\"),
            Map.entry("Open Bracket", "["),
            Map.entry("Close Bracket", "]"),
            Map.entry("Equals", "="),
            Map.entry("Minus", "-"),
            Map.entry("Quote", "'"),
            Map.entry("Back Quote", "`"),
            Map.entry("Double Quote", "\""),
            Map.entry("Plus", "+"),
            Map.entry("Asterisk", "*"),
            Map.entry("Ampersand", "&"),
            Map.entry("At", "@"),
            Map.entry("Colon", ":"),
            Map.entry("Circumflex", "^"),
            Map.entry("Dollar", "$"),
            Map.entry("Euro", "€"),
            Map.entry("Number Sign", "#"),
            Map.entry("Underscore", "_"),
            Map.entry("Exclamation Mark", "!"),
            Map.entry("Inverted Exclamation Mark", "¡"),
            Map.entry("Left Parenthesis", "("),
            Map.entry("Right Parenthesis", ")"),
            // awtosx names both braces with a bracket
            Map.entry("Left Brace", "["),
            Map.entry("Right Brace", "]"),
            Map.entry("Less", "<"),
            Map.entry("Greater", ">"));

    private Keys() {
    }

    /**
     * The name of a key or a modifier on this platform, given its name on Windows and Linux : {@code "Shift"} is
     * {@code "⇧"} on macOS ; a numeric keypad key {@code "NumPad-5"} is {@code "⌨-5"}, a keypad operator
     * {@code "NumPad *"} is {@code "⌨ *"} ({@code AWT.multiply}, {@code AWT.add}, {@code AWT.subtract},
     * {@code AWT.divide}, {@code AWT.decimal}, {@code AWT.separator}). Other names are the same on every platform
     * ({@code "A"}, {@code "F10"}, {@code "Windows"}).
     */
    public static String text(String name) {
        if (!Platforms.isMac()) {
            return name;
        }
        if (name.startsWith("NumPad-") || name.startsWith("NumPad ")) {
            return MAC_TEXTS.get("NumPad") + name.substring("NumPad".length());
        }
        return MAC_TEXTS.getOrDefault(name, name);
    }

    /**
     * Key names joined as {@code InputEvent.getModifiersExText} and {@code MenuShortcut.toString} join them, with a
     * {@code +} ({@code "Ctrl", "Shift"} : {@code "Ctrl+Shift"} or {@code "⌃+⇧"}).
     */
    public static String join(String... names) {
        return Arrays.stream(names).map(Keys::text).collect(Collectors.joining("+"));
    }

    /**
     * The modifier of the menu shortcuts ({@code MenuShortcut}, the accelerators of menus) : {@code Meta} (Command) on
     * macOS, {@code Ctrl} elsewhere ({@link Toolkit#getMenuShortcutKeyMaskEx()}).
     */
    public static int menuShortcutMaskEx() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    /**
     * The Windows and Linux name of the menu shortcut modifier : {@code "Meta"} on macOS (shown {@code ⌘}),
     * {@code "Ctrl"} elsewhere.
     */
    public static String menuShortcutName() {
        return (menuShortcutMaskEx() & InputEvent.META_DOWN_MASK) != 0 ? "Meta" : "Ctrl";
    }

    /**
     * The key code of the menu shortcut modifier, to press it with Robot : {@code VK_META} (Command) on macOS,
     * {@code VK_CONTROL} elsewhere.
     */
    public static int menuShortcutKey() {
        return (menuShortcutMaskEx() & InputEvent.META_DOWN_MASK) != 0 ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
    }

    /**
     * The modifiers of the mnemonics of Swing (a button, a menu, a tab, a label) : Ctrl+Option on macOS, Alt elsewhere
     * ({@code SwingUtilities2.getSystemMnemonicKeyMask}, {@code LWCToolkit.getFocusAcceleratorKeyMask} on macOS).
     */
    public static int mnemonicMaskEx() {
        return Platforms.isMac() ? InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
    }

    /**
     * The key codes of {@link #mnemonicMaskEx()}, in the order to press them : {@code VK_CONTROL, VK_ALT} on macOS,
     * {@code VK_ALT} elsewhere.
     */
    public static int[] mnemonicKeys() {
        return Platforms.isMac() ? new int[] { KeyEvent.VK_CONTROL, KeyEvent.VK_ALT } : new int[] { KeyEvent.VK_ALT };
    }

    /**
     * The modifiers of {@link #mnemonicMaskEx()} as a key stroke text prefix ({@code KeyStroke.getKeyStroke} /
     * {@code AWTKeyStroke.getAWTKeyStroke(String)}) : {@code "ctrl alt "} on macOS, {@code "alt "} elsewhere.
     */
    public static String mnemonicStrokePrefix() {
        return Platforms.isMac() ? "ctrl alt " : "alt ";
    }

    /**
     * The key held during a drag and drop to copy instead of moving : Option ({@code VK_ALT}) on macOS, where Control
     * restricts the operations of a Cocoa drag to a link, {@code VK_CONTROL} elsewhere.
     */
    public static int copyDragKey() {
        return Platforms.isMac() ? KeyEvent.VK_ALT : KeyEvent.VK_CONTROL;
    }

    /**
     * The Windows and Linux name of {@link #copyDragKey()} : {@code "Alt"} (Option) on macOS, {@code "Ctrl"} elsewhere.
     */
    public static String copyDragKeyName() {
        return Platforms.isMac() ? "Alt" : "Ctrl";
    }
}
