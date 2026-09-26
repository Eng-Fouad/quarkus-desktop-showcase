package io.quarkiverse.desktop.showcase.pages.awt;

import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.quarkiverse.desktop.showcase.core.Edt;

/**
 * A {@code java.applet.Applet} (deprecated for removal, still in the {@code java.desktop} module of JDK 25) hosted in a
 * {@code Frame} by a minimal applet viewer : the {@link AppletStub} and {@link AppletContext} a browser plug-in used to
 * provide. Parameters come from {@code /showcase/awt/applet.properties} (the code base), the image is loaded with
 * {@code Applet.getImage(codeBase, name)} : a URL relative to a classpath resource URL ({@code jar:} on the JVM,
 * {@code resource:} in a native executable).
 */
@SuppressWarnings({ "removal", "deprecation" })
final class AppletDemo {

    static final String PROPERTIES = "/showcase/awt/applet.properties";

    private AppletDemo() {
    }

    /**
     * The applet : records its life cycle, shows a greeting label, a button and its image.
     */
    static final class ShowcaseApplet extends Applet {

        private final List<String> lifecycle = Collections.synchronizedList(new ArrayList<>());
        private transient Image image;

        List<String> lifecycle() {
            return lifecycle;
        }

        Image image() {
            return image;
        }

        @Override
        public void init() {
            lifecycle.add("init(active=" + isActive() + ")");
            setLayout(new BorderLayout());
            setBackground(Color.decode(getParameter("background")));
            Label greeting = new Label(getParameter("greeting"), Label.CENTER);
            greeting.setName("appletGreeting");
            greeting.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
            add(greeting, BorderLayout.NORTH);
            Panel buttons = new Panel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
            Button button = new Button("Applet button");
            button.setName("appletButton");
            buttons.add(button);
            add(buttons, BorderLayout.SOUTH);
            image = getImage(getCodeBase(), getParameter("image"));
        }

        @Override
        public void start() {
            lifecycle.add("start(active=" + isActive() + ")");
            showStatus("started");
        }

        @Override
        public void stop() {
            lifecycle.add("stop(active=" + isActive() + ")");
            showStatus("stopped");
        }

        @Override
        public void destroy() {
            lifecycle.add("destroy");
        }

        @Override
        public String getAppletInfo() {
            return "quarkus-desktop showcase applet";
        }

        @Override
        public String[][] getParameterInfo() {
            return new String[][] {
                    { "title", "String", "title of the viewer" },
                    { "image", "URL", "image relative to the code base" },
                    { "greeting", "String", "label text" },
                    { "background", "#RRGGBB", "background color" } };
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                AwtSupport.textHints(g2);
                if (image != null) {
                    g2.drawImage(image, 16, 40, this);
                }
                g2.setColor(new Color(0x5D4037));
                g2.setFont(new Font(Font.SERIF, Font.ITALIC, 16));
                g2.drawString(getAppletInfo(), 76, 70);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, 170);
        }
    }

    /**
     * The applet viewer side : parameters, code base, status line and image loading.
     */
    static final class Viewer implements AppletStub, AppletContext {

        private final Map<String, String> parameters = new HashMap<>();
        private final URL codeBase;
        private final List<Applet> applets = new ArrayList<>();
        private final List<String> statuses = Collections.synchronizedList(new ArrayList<>());
        private final Label statusLine;
        private volatile boolean active;

        Viewer(Label statusLine) {
            this.statusLine = statusLine;
            this.codeBase = Edt.resource(PROPERTIES);
            Properties properties = new Properties();
            try (InputStream in = Edt.resourceStream(PROPERTIES)) {
                properties.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            properties.forEach((k, v) -> parameters.put(String.valueOf(k), String.valueOf(v)));
        }

        void register(Applet applet) {
            applets.add(applet);
            applet.setStub(this);
        }

        void setActive(boolean active) {
            this.active = active;
        }

        List<String> statuses() {
            return statuses;
        }

        // ---------------------------------------------------------------------------------------------- AppletStub

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public URL getDocumentBase() {
            return codeBase;
        }

        @Override
        public URL getCodeBase() {
            return codeBase;
        }

        @Override
        public String getParameter(String name) {
            return parameters.get(name);
        }

        @Override
        public AppletContext getAppletContext() {
            return this;
        }

        @Override
        public void appletResize(int width, int height) {
            statuses.add("appletResize " + width + "x" + height);
        }

        // ------------------------------------------------------------------------------------------- AppletContext

        @Override
        public AudioClip getAudioClip(URL url) {
            // Applet.newAudioClip opens an audio line : out of scope here (see the sound pages)
            return null;
        }

        @Override
        public Image getImage(URL url) {
            return Toolkit.getDefaultToolkit().createImage(url);
        }

        @Override
        public Applet getApplet(String name) {
            for (Applet applet : applets) {
                if (name.equals(applet.getName())) {
                    return applet;
                }
            }
            return null;
        }

        @Override
        public Enumeration<Applet> getApplets() {
            return Collections.enumeration(applets);
        }

        @Override
        public void showDocument(URL url) {
            // never opens a browser (no side effect outside the showcase windows)
            statuses.add("showDocument ignored");
        }

        @Override
        public void showDocument(URL url, String target) {
            statuses.add("showDocument ignored (" + target + ")");
        }

        @Override
        public void showStatus(String status) {
            statuses.add(status);
            statusLine.setText("Status: " + status);
        }

        @Override
        public void setStream(String key, InputStream stream) {
        }

        @Override
        public InputStream getStream(String key) {
            return null;
        }

        @Override
        public Iterator<String> getStreamKeys() {
            return Collections.emptyIterator();
        }
    }
}
