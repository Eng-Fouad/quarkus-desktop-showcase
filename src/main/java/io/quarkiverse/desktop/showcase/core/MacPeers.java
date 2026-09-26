package io.quarkiverse.desktop.showcase.core;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * macOS capture of AWT heavyweight components : on macOS every AWT component is drawn by a Swing delegate of its
 * {@code sun.lwawt} peer ({@code LWComponentPeer.paintPeer}), which {@code Component.printAll} does not draw
 * ({@code LWComponentPeer.print} only calls the {@code paint} method of the component, empty for a {@code Button}, a
 * {@code TextField}...). {@link #print} draws the delegate of each peer first, then the component (its own painting and
 * its lightweight children), then its heavyweight children, as {@code LWRepaintArea.paintComponent} does on screen.
 * <p>
 * Reflection on JDK internals : {@code java.awt.Component#peer} and the package private
 * {@code sun.lwawt.LWComponentPeer#getDelegate()}. The JVM needs
 * {@code --add-opens java.desktop/java.awt=ALL-UNNAMED --add-opens java.desktop/sun.lwawt=ALL-UNNAMED}
 * (tools/Snapshot.java adds them on macOS), the native build the same options (the {@code mac} profile of the pom) and
 * the reflection metadata of both members (reachability-metadata.json). Looked up lazily, not in a static initializer
 * (Quarkus initializes application classes at build time). AWT only, no Swing type.
 */
public final class MacPeers {

    private static volatile Lookup lookup;

    private record Lookup(Field peer, Method getDelegate, String error) {
    }

    private MacPeers() {
    }

    /**
     * {@code true} on macOS : {@link Snapshots#render} prints the AWT heavyweight components with {@link #print}.
     */
    public static boolean applies() {
        return Platforms.isMac();
    }

    /**
     * {@code null} when the reflection is available, otherwise why it is not.
     */
    public static String unavailable() {
        return lookup().error();
    }

    private static Lookup lookup() {
        Lookup l = lookup;
        if (l == null) {
            try {
                Field peer = Component.class.getDeclaredField("peer");
                peer.setAccessible(true);
                Method getDelegate = Class.forName("sun.lwawt.LWComponentPeer").getDeclaredMethod("getDelegate");
                getDelegate.setAccessible(true);
                l = new Lookup(peer, getDelegate, null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                l = new Lookup(null, null, Checks.describe(e));
            }
            lookup = l;
        }
        return l;
    }

    /**
     * Prints {@code component} and its descendants, with the delegates of the peers of the heavyweight ones : prints it
     * as usual ({@code printAll} : the lightweight components, and the heavyweight ones without their delegate), then
     * draws each heavyweight component over it (its delegate, then its own painting and lightweight children), parents
     * before children. Falls back to {@code printAll} alone when the reflection is not available. Call it on the EDT.
     */
    public static void print(Component component, Graphics g) {
        component.printAll(g);
        Lookup l = lookup();
        if (l.error() == null) {
            overlay(l, component, g);
        }
    }

    private static void overlay(Lookup l, Component c, Graphics g) {
        if (!c.isLightweight()) {
            try {
                Object peer = l.peer().get(c);
                if (peer != null && l.getDelegate().getDeclaringClass().isInstance(peer)
                        && l.getDelegate().invoke(peer) instanceof Component delegate) {
                    // the lock of LWComponentPeer.getDelegateLock()
                    synchronized (c.getTreeLock()) {
                        delegate.print(g);
                    }
                    // Component.paint and, for containers, the lightweight children
                    c.print(g);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // printed without its delegate
            }
        }
        if (c instanceof Container container) {
            Component[] children = container.getComponents();
            for (int i = children.length - 1; i >= 0; i--) {
                Component child = children[i];
                if (child.isVisible() && child.getWidth() > 0 && child.getHeight() > 0) {
                    Graphics cg = g.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());
                    try {
                        overlay(l, child, cg);
                    } finally {
                        cg.dispose();
                    }
                }
            }
        }
    }
}
