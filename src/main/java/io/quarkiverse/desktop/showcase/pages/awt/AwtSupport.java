package io.quarkiverse.desktop.showcase.pages.awt;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Helpers shared by the AWT pages (no {@code javax.swing}) : secondary window placement, titled groups of heavyweight
 * components, deterministic event descriptions and event logs.
 * <p>
 * No AWT object is created in a static initializer (Quarkus initializes application classes at build time).
 */
final class AwtSupport {

    /** Background of the groups (a light gray, like a dialog). */
    static final int GROUP_BACKGROUND = 0xF3F4F6;
    /** Border of the groups. */
    static final int GROUP_BORDER = 0xC5CAD3;
    /** Monospaced log text size. */
    static final int LOG_SIZE = 11;

    private AwtSupport() {
    }

    /**
     * A rectangle of the default screen for the secondary windows of the AWT pages : the lower right part of the screen,
     * away from the showcase main window (40, 40, 1400 x 900) and from the (1500, 100) area other pages use.
     */
    static Rectangle secondaryArea(int width, int height) {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int right = screen.x + screen.width - insets.right - 40;
        int bottom = screen.y + screen.height - insets.bottom - 40;
        int x = Math.max(screen.x + insets.left, right - width);
        int y = Math.max(screen.y + insets.top, bottom - height);
        return new Rectangle(x, y, width, height);
    }

    /**
     * A titled group : a lightweight container with a caption above {@code content} (a heavyweight {@link Panel} most of
     * the time), painted with a border.
     */
    static Container group(String title, Component content) {
        Container group = new Container() {
            @Override
            public void paint(Graphics g) {
                g.setColor(new Color(GROUP_BACKGROUND));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(GROUP_BORDER));
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                super.paint(g);
            }

            @Override
            public Insets getInsets() {
                return new Insets(8, 10, 10, 10);
            }
        };
        group.setLayout(new Ui.StackLayout(true, 6));
        group.add(Ui.title(title));
        group.add(content);
        return group;
    }

    /**
     * A heavyweight panel with a fixed preferred size (layout managers of AWT panels size their children from their own
     * preferred size).
     */
    static Panel panel(LayoutManager layout, int width, int height) {
        Panel panel = new Panel(layout);
        panel.setPreferredSize(new Dimension(width, height));
        panel.setBackground(new Color(GROUP_BACKGROUND));
        return panel;
    }

    /**
     * A monospaced text block showing {@code lines} (an event log), wrapped at {@code width}.
     */
    static Component log(List<String> lines, int width) {
        return Ui.text(lines.isEmpty() ? "(empty)" : String.join("\n", lines), new Font(Font.MONOSPACED, Font.PLAIN, LOG_SIZE),
                Ui.TEXT_COLOR, width);
    }

    /**
     * {@code value} as a string without the identity hash codes of {@code Object.toString()} : bounds, sizes, points.
     */
    static String bounds(Rectangle r) {
        return r.x + "," + r.y + " " + r.width + "x" + r.height;
    }

    static String size(Dimension d) {
        return d.width + "x" + d.height;
    }

    static String insets(Insets in) {
        return in.top + "," + in.left + "," + in.bottom + "," + in.right;
    }

    /**
     * The name of an event id ({@code MOUSE_PRESSED}, {@code KEY_TYPED}...), from its {@code paramString()} (every AWT
     * event starts it with the id name).
     */
    static String idName(AWTEvent e) {
        String param = e.paramString();
        int end = param.indexOf(',');
        String name = end < 0 ? param : param.substring(0, end);
        // ComponentEvent and HierarchyEvent add details in parentheses
        int details = name.indexOf(" (");
        return details < 0 ? name : name.substring(0, details);
    }

    /**
     * {@code true} when the current keyboard layout is Arabic or Hebrew : AWT on Windows then lays out native menus right
     * to left (it follows the input language), and the arrow keys move the other way in a menu bar.
     */
    static boolean rightToLeftInput() {
        java.util.Locale locale = java.awt.im.InputContext.getInstance().getLocale();
        String language = locale == null ? "" : locale.getLanguage();
        return language.equals("ar") || language.equals("he") || language.equals("iw");
    }

    /**
     * The current keyboard layout (input method locale), for the checks that depend on it.
     */
    static String inputLocale() {
        java.util.Locale locale = java.awt.im.InputContext.getInstance().getLocale();
        return locale == null ? "none" : locale.toLanguageTag();
    }

    /**
     * A deterministic description of {@code e} : id, coordinates relative to the source, button, click count, modifiers,
     * key code/char/location... never the time stamp, the screen coordinates or an identity hash code.
     */
    static String describe(AWTEvent e) {
        StringBuilder sb = new StringBuilder(idName(e));
        if (e instanceof MouseWheelEvent w) {
            sb.append(" (").append(w.getX()).append(',').append(w.getY()).append(") rotation=")
                    .append(w.getWheelRotation()).append(" precise=").append(num(w.getPreciseWheelRotation()))
                    .append(" type=").append(w.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? "UNIT" : "BLOCK");
            appendModifiers(sb, w);
        } else if (e instanceof MouseEvent m) {
            sb.append(" (").append(m.getX()).append(',').append(m.getY()).append(')');
            if (m.getButton() != MouseEvent.NOBUTTON) {
                sb.append(" button=").append(m.getButton());
            }
            if (m.getClickCount() > 0) {
                sb.append(" clicks=").append(m.getClickCount());
            }
            if (m.isPopupTrigger()) {
                sb.append(" popupTrigger");
            }
            appendModifiers(sb, m);
        } else if (e instanceof KeyEvent k) {
            sb.append(" code=").append(k.getKeyCode() == KeyEvent.VK_UNDEFINED ? "UNDEFINED" : KeyEvent.getKeyText(k.getKeyCode()))
                    .append(" char=").append(charText(k.getKeyChar()))
                    .append(" location=").append(location(k.getKeyLocation()));
            if (k.getExtendedKeyCode() != k.getKeyCode()) {
                sb.append(" extended=0x").append(Integer.toHexString(k.getExtendedKeyCode()).toUpperCase(Locale.ROOT));
            }
            appendModifiers(sb, k);
        } else if (e instanceof FocusEvent f) {
            sb.append(f.isTemporary() ? " temporary" : " permanent").append(" cause=").append(f.getCause());
            if (f.getOppositeComponent() != null) {
                sb.append(" opposite=").append(nameOf(f.getOppositeComponent()));
            }
        } else if (e instanceof WindowEvent w && e.getID() == WindowEvent.WINDOW_STATE_CHANGED) {
            sb.append(' ').append(frameState(w.getOldState())).append(" -> ").append(frameState(w.getNewState()));
        } else if (e instanceof ComponentEvent c && c.getComponent() != null && e.getID() == ComponentEvent.COMPONENT_RESIZED) {
            sb.append(' ').append(size(c.getComponent().getSize()));
        }
        return sb.toString();
    }

    private static void appendModifiers(StringBuilder sb, InputEvent e) {
        if (e.getModifiersEx() != 0) {
            sb.append(" mods=").append(InputEvent.getModifiersExText(e.getModifiersEx()));
        }
    }

    static String charText(char c) {
        if (c == KeyEvent.CHAR_UNDEFINED) {
            return "UNDEFINED";
        }
        if (c < 0x20 || c == 0x7F) {
            return String.format(Locale.ROOT, "\\u%04X", (int) c);
        }
        return "'" + c + "'";
    }

    static String location(int location) {
        return switch (location) {
            case KeyEvent.KEY_LOCATION_STANDARD -> "STANDARD";
            case KeyEvent.KEY_LOCATION_LEFT -> "LEFT";
            case KeyEvent.KEY_LOCATION_RIGHT -> "RIGHT";
            case KeyEvent.KEY_LOCATION_NUMPAD -> "NUMPAD";
            default -> "UNKNOWN";
        };
    }

    /**
     * {@code Frame} extended state as a readable text ({@code NORMAL}, {@code ICONIFIED}, {@code MAXIMIZED_BOTH}...).
     */
    static String frameState(int state) {
        if (state == java.awt.Frame.NORMAL) {
            return "NORMAL";
        }
        List<String> names = new ArrayList<>();
        if ((state & java.awt.Frame.ICONIFIED) != 0) {
            names.add("ICONIFIED");
        }
        if ((state & java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH) {
            names.add("MAXIMIZED_BOTH");
        } else if ((state & java.awt.Frame.MAXIMIZED_HORIZ) != 0) {
            names.add("MAXIMIZED_HORIZ");
        } else if ((state & java.awt.Frame.MAXIMIZED_VERT) != 0) {
            names.add("MAXIMIZED_VERT");
        }
        return String.join("|", names);
    }

    /**
     * The name of a component set with {@code setName}, or its class simple name : the default names of AWT
     * ({@code button3}, {@code canvas12}...) come from global counters and change from run to run.
     */
    static String nameOf(Component c) {
        String name = c.getName();
        return name != null && !name.matches("[a-z]+[0-9]+") ? name : c.getClass().getSimpleName();
    }

    static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.equals("-0.00") ? "0.00" : s;
    }

    /**
     * An opaque, always on top, non focusable window of a solid color : placed behind the windows a page captures with
     * Robot, so that the capture never shows what happens to be on the desktop (window corners, shadows).
     */
    static final class SolidWindow extends Window {

        private final int rgb;

        SolidWindow(Rectangle bounds, int rgb) {
            super((java.awt.Frame) null);
            this.rgb = rgb;
            setFocusableWindowState(false);
            setAutoRequestFocus(false);
            setAlwaysOnTop(true);
            setBounds(bounds);
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(new Color(rgb));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * Anti-aliased text hints for Java2D drawings of the pages.
     */
    static void textHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
}
