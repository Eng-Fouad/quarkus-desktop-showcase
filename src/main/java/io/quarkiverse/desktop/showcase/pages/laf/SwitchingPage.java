package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.multi.MultiButtonUI;
import javax.swing.plaf.multi.MultiLabelUI;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Look and feel switching at run time : {@code UIManager.setLookAndFeel(className)} for every installed look and feel
 * (reflective instantiation, except Metal) and an application look and feel ({@link ShowcaseLookAndFeel}, installed
 * with {@code installLookAndFeel}), each time followed by {@code SwingUtilities.updateComponentTreeUI} of one component
 * tree (the gallery, in a packed window), back to the initial look and feel ; {@code createLookAndFeel(name)} ; the
 * system and cross platform look and feel names ; the {@code lookAndFeel} property change events ; and the
 * multiplexing look and feel with an auxiliary look and feel ({@link AuxiliaryLookAndFeel}, live panel below).
 */
@Singleton
public class SwitchingPage extends AbstractLafPage {

    private static final String METAL = "javax.swing.plaf.metal.MetalLookAndFeel";

    @Override
    public String id() {
        return "laf-switching";
    }

    @Override
    public String title() {
        return "Switching, custom and auxiliary look and feels";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    protected Component buildPage() throws Exception {
        LookAndFeel initial = UIManager.getLookAndFeel();
        List<Check> installedChecks = installedChecks();

        // an application look and feel, added to the installed list (restored in dispose)
        UIManager.installLookAndFeel("Showcase", ShowcaseLookAndFeel.class.getName());
        List<LookAndFeelInfo> steps = new ArrayList<>(Arrays.asList(UIManager.getInstalledLookAndFeels()));

        int[] events = { 0 };
        PropertyChangeListener listener = e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                events[0]++;
            }
        };
        UIManager.addPropertyChangeListener(listener);
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        List<Check> switchChecks = new ArrayList<>();
        JWindow stage = new JWindow();
        windows.add(stage);
        String firstHash = null;
        try {
            UIManager.setLookAndFeel(METAL);
            LafGallery tree = LafGallery.build();
            stage.setFocusableWindowState(false);
            stage.getContentPane().add(tree.panel());
            int step = 0;
            for (LookAndFeelInfo info : steps) {
                step++;
                String name = "step " + step + " " + info.getName();
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (UnsupportedLookAndFeelException e) {
                    switchChecks.add(Check.info(name, "unsupported here"));
                    continue;
                }
                BufferedImage image = switchAndRender(stage, tree);
                if (firstHash == null) {
                    firstHash = Checks.sha256(image);
                }
                images.put(step + ". " + info.getName(), image);
                extras.put("step-" + step + "-" + info.getName().toLowerCase(Locale.ROOT).replace("+", "plus")
                        .replaceAll("[^a-z0-9]+", "-"), image);
                switchChecks.add(stepCheck(name, info.getClassName(), tree));
            }
            // back to the initial look and feel instance
            UIManager.setLookAndFeel(initial);
            BufferedImage back = switchAndRender(stage, tree);
            step++;
            images.put(step + ". back to " + initial.getName(), back);
            extras.put("step-" + step + "-back", back);
            switchChecks.add(stepCheck("step " + step + " back to " + initial.getName(), initial.getClass().getName(),
                    tree));
            String backHash = Checks.sha256(back);
            String first = firstHash;
            // updateComponentTreeUI does not reset every property a look and feel installed (e.g. the table row
            // height of Nimbus) : informational
            switchChecks.add(Check.info("round trip renders the first step again", backHash.equals(first)));
        } finally {
            UIManager.removePropertyChangeListener(listener);
            stage.getContentPane().removeAll();
            stage.dispose();
        }
        switchChecks.add(Checks.expect("lookAndFeel property change events", steps.size() + 2, () -> events[0]));

        List<Check> customChecks = customChecks();

        // live : the initial look and feel with an auxiliary look and feel (removed in dispose)
        UIManager.setLookAndFeel(initial);
        UIManager.addAuxiliaryLookAndFeel(new AuxiliaryLookAndFeel());
        JPanel auxPanel = auxiliaryPanel();
        List<Check> auxChecks = auxiliaryChecks(auxPanel);

        List<Component> content = new ArrayList<>();
        content.add(Ui.heading("Switching look and feels"));
        content.add(Ui.text("One gallery (in a packed window) switched through every installed look and feel and an "
                + "application look and feel with UIManager.setLookAndFeel(className) + "
                + "SwingUtilities.updateComponentTreeUI, then back to the initial look and feel (full size images are "
                + "extra snapshots). Below : Metal with an auxiliary look and feel (MultiLookAndFeel) marking buttons, "
                + "labels, sliders and progress bars with an orange corner.", 1000));
        content.add(LafSupport.thumbnails(images, 0.32, 3));
        content.add(Ui.title("Auxiliary look and feel (live)"));
        content.add(auxPanel);
        content.add(LafSupport.twoColumns("Installed look and feels", installedChecks, 250));
        content.add(ChecksView.table("Switching (UI delegates of JButton, JTable, JTree, JSlider, JComboBox)",
                switchChecks, 300, ChecksView.WIDTH));
        content.add(LafSupport.twoColumns("Application look and feel (by class name) and lazy defaults", customChecks,
                250));
        content.add(LafSupport.twoColumns("Auxiliary look and feel", auxChecks, 250));
        return LafSupport.page(content.toArray(Component[]::new));
    }

    private static BufferedImage switchAndRender(JWindow stage, LafGallery tree) {
        SwingUtilities.updateComponentTreeUI(stage);
        stage.pack();
        stage.validate();
        return Snapshots.render(tree.panel());
    }

    private static Check stepCheck(String name, String className, LafGallery tree) {
        Map<String, JComponent> keyed = tree.keyed();
        List<JComponent> probes = List.of(keyed.get("JButton"), keyed.get("JTable"), keyed.get("JTree"),
                keyed.get("JSlider"), keyed.get("JComboBox"));
        List<Check> ui = LafSupport.uiChecks(probes);
        boolean ok = UIManager.getLookAndFeel().getClass().getName().equals(className)
                && ui.stream().allMatch(c -> Boolean.TRUE.equals(c.ok()));
        List<String> names = ui.stream().map(Check::value).toList();
        return Check.of(name, ok, String.join(" ", names));
    }

    private static List<Check> installedChecks() {
        List<Check> checks = new ArrayList<>();
        LookAndFeelInfo[] installed = UIManager.getInstalledLookAndFeels();
        List<String> names = Arrays.stream(installed).map(LookAndFeelInfo::getName).toList();
        checks.add(Check.info("installed look and feels", String.join(", ", names)));
        List<String> expected = new ArrayList<>(List.of("Metal", "Nimbus", "CDE/Motif"));
        if (Platforms.isWindows()) {
            expected.add("Windows");
        } else if (Platforms.isLinux()) {
            expected.add("GTK+");
        }
        checks.add(Checks.expect("installed : " + String.join(", ", expected), true, () -> names.containsAll(expected)));
        for (LookAndFeelInfo info : installed) {
            checks.add(Checks.expect("createLookAndFeel(\"" + info.getName() + "\")", info.getClassName(),
                    () -> UIManager.createLookAndFeel(info.getName()).getClass().getName()));
        }
        checks.add(Checks.expect("createLookAndFeel(\"Unknown\")", "UnsupportedLookAndFeelException", () -> {
            try {
                UIManager.createLookAndFeel("Unknown");
                return "created";
            } catch (UnsupportedLookAndFeelException e) {
                return "UnsupportedLookAndFeelException";
            }
        }));
        checks.add(Checks.expect("getCrossPlatformLookAndFeelClassName()", METAL,
                UIManager::getCrossPlatformLookAndFeelClassName));
        checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect("getSystemLookAndFeelClassName()",
                "com.sun.java.swing.plaf.windows.WindowsLookAndFeel", UIManager::getSystemLookAndFeelClassName)));
        for (String property : List.of("swing.defaultlaf", "swing.systemlaf", "swing.crossplatformlaf",
                "swing.auxiliarylaf", "swing.plaf.multiplexinglaf", "swing.installedlafs")) {
            checks.add(Check.info("-D" + property, System.getProperty(property)));
        }
        return checks;
    }

    /**
     * The application look and feel installed by class name, and its lazy defaults.
     */
    private List<Check> customChecks() throws Exception {
        List<Check> checks = new ArrayList<>();
        UIManager.setLookAndFeel(ShowcaseLookAndFeel.class.getName());
        checks.add(Checks.expect("setLookAndFeel(className) : getName()", "Showcase",
                () -> UIManager.getLookAndFeel().getName()));
        checks.add(Checks.expect("createLookAndFeel(\"Showcase\") (JDK look and feels only)",
                "UnsupportedLookAndFeelException", () -> {
                    try {
                        return UIManager.createLookAndFeel("Showcase").getName();
                    } catch (UnsupportedLookAndFeelException e) {
                        return "UnsupportedLookAndFeelException";
                    }
                }));
        JButton button = new JButton("Custom");
        checks.add(Checks.expect("JButton UI (class by name + createUI)", "ShowcaseLookAndFeel$ShowcaseButtonUI",
                () -> LafSupport.simple(button.getUI().getClass().getName())));
        checks.add(Checks.expect("custom delegate pixel (accent bar)", Checks.argb(0xFFD81B60), () -> {
            BufferedImage image = LafSupport.renderStaged(button);
            return Checks.argb(image.getRGB(image.getWidth() / 2, image.getHeight() - 4));
        }));
        checks.add(Checks.expect("ProxyLazyValue (constructor)", "BorderUIResource$LineBorderUIResource 2", () -> {
            Border border = UIManager.getBorder("Showcase.lineBorder");
            return LafSupport.simple(border.getClass().getName()) + " "
                    + ((javax.swing.border.LineBorder) border).getThickness();
        }));
        checks.add(Checks.expect("ProxyLazyValue (static method)", "EtchedBorder",
                () -> LafSupport.simple(UIManager.getBorder("Showcase.etchedBorder").getClass().getName())));
        checks.add(Checks.expect("LazyInputMap", "showcase-cut showcase-close showcase-menu showcase-cycle", () -> {
            InputMap map = (InputMap) UIManager.get("Showcase.inputMap");
            return map.get(KeyStroke.getKeyStroke("ctrl shift X")) + " " + map.get(KeyStroke.getKeyStroke("alt F4"))
                    + " " + map.get(KeyStroke.getKeyStroke("shift F10")) + " "
                    + map.get(KeyStroke.getKeyStroke("meta BACK_QUOTE"));
        }));
        checks.add(Checks.expect("LazyValue", "created lazily", () -> UIManager.get("Showcase.lazy")));
        checks.add(Checks.expect("ActiveValue", "created on every lookup", () -> UIManager.get("Showcase.active")));
        checks.add(Checks.expect("Showcase.accent", Checks.argb(0xFFD81B60),
                () -> LafSupport.color(UIManager.getColor("Showcase.accent"))));
        return checks;
    }

    private static JPanel auxiliaryPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        panel.add(new JButton("Button"));
        panel.add(new JToggleButton("Toggle", true));
        panel.add(new JCheckBox("Check", true));
        panel.add(new JLabel("Label"));
        JSlider slider = new JSlider(0, 100, 40);
        panel.add(slider);
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(60);
        panel.add(progress);
        panel.add(new JTextField("Text field (not auxiliary)", 16));
        panel.setPreferredSize(new java.awt.Dimension(LafGallery.GALLERY_WIDTH, panel.getPreferredSize().height));
        return panel;
    }

    private static List<Check> auxiliaryChecks(JPanel panel) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("getAuxiliaryLookAndFeels()", "Showcase auxiliary",
                () -> String.join(",", Arrays.stream(UIManager.getAuxiliaryLookAndFeels()).map(LookAndFeel::getName)
                        .toList())));
        JButton button = (JButton) panel.getComponent(0);
        checks.add(Checks.expect("JButton UI", "javax.swing.plaf.multi.MultiButtonUI",
                () -> button.getUI().getClass().getName()));
        checks.add(Checks.expect("JButton multiplexed UIs", "MetalButtonUI AuxiliaryLookAndFeel$MarkerUI",
                () -> uis(((MultiButtonUI) button.getUI()).getUIs())));
        JLabel label = (JLabel) panel.getComponent(3);
        checks.add(Checks.expect("JLabel multiplexed UIs", "MetalLabelUI AuxiliaryLookAndFeel$MarkerUI",
                () -> uis(((MultiLabelUI) label.getUI()).getUIs())));
        checks.add(Checks.expect("JSlider UI", "MultiSliderUI",
                () -> LafSupport.simple(((JComponent) panel.getComponent(4)).getUI().getClass().getName())));
        checks.add(Checks.expect("JTextField UI (no auxiliary delegate : not multiplexed)", "MetalTextFieldUI",
                () -> LafSupport.simple(((JComponent) panel.getComponent(6)).getUI().getClass().getName())));
        checks.add(Checks.expect("auxiliary installUI calls", 6, () -> {
            int count = 0;
            for (Component c : panel.getComponents()) {
                if (Boolean.TRUE.equals(((JComponent) c).getClientProperty(AuxiliaryLookAndFeel.INSTALLED))) {
                    count++;
                }
            }
            return count;
        }));
        JButton probe = new JButton("Probe");
        checks.add(Checks.expect("auxiliary marker pixel", Checks.argb(AuxiliaryLookAndFeel.MarkerUI.MARKER), () -> {
            BufferedImage image = LafSupport.renderStaged(probe);
            return Checks.argb(image.getRGB(image.getWidth() - 2, 1));
        }));
        checks.add(Checks.expect("preferred size from the default delegate", "true", () -> String.valueOf(
                probe.getPreferredSize().equals(new MultiProbe().metalPreferredSize("Probe")))));
        return checks;
    }

    private static String uis(ComponentUI[] uis) {
        List<String> names = new ArrayList<>();
        for (ComponentUI ui : uis) {
            names.add(LafSupport.simple(ui.getClass().getName()));
        }
        return String.join(" ", names);
    }

    /**
     * The preferred size of a button with the Metal delegate only (the auxiliary delegates do not size components).
     */
    private static final class MultiProbe {

        java.awt.Dimension metalPreferredSize(String text) {
            JButton button = new JButton(text);
            button.setUI(new javax.swing.plaf.metal.MetalButtonUI());
            return button.getPreferredSize();
        }
    }
}
