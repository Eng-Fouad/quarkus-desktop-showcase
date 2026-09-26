package io.quarkiverse.desktop.showcase.pages.laf;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalButtonUI;
import javax.swing.plaf.metal.MetalLookAndFeel;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * An application look and feel (Metal with a custom button delegate and lazy defaults), installed the way third party
 * look and feels are : {@code UIManager.installLookAndFeel(name, className)} then
 * {@code UIManager.setLookAndFeel(className)} (class loaded by name through the context class loader, public no-arg
 * constructor invoked reflectively). Its defaults map {@code ButtonUI} to {@link ShowcaseButtonUI} by class name
 * ({@code UIDefaults.getUIClass} + reflective {@code createUI}) and use {@link UIDefaults.ProxyLazyValue} (reflective
 * constructor and static method), {@link UIDefaults.LazyInputMap} (key strokes parsed from strings),
 * {@link UIDefaults.LazyValue} and {@link UIDefaults.ActiveValue}.
 * <p>
 * {@code @RegisterForReflection} : instantiated by class name only.
 */
@RegisterForReflection
public class ShowcaseLookAndFeel extends MetalLookAndFeel {

    public ShowcaseLookAndFeel() {
    }

    @Override
    public String getName() {
        return "Showcase";
    }

    @Override
    public String getID() {
        return "Showcase";
    }

    @Override
    public String getDescription() {
        return "Metal with a custom button delegate (quarkus-desktop showcase)";
    }

    @Override
    protected void initClassDefaults(UIDefaults table) {
        super.initClassDefaults(table);
        table.put("ButtonUI", ShowcaseButtonUI.class.getName());
    }

    @Override
    protected void initComponentDefaults(UIDefaults table) {
        super.initComponentDefaults(table);
        table.put("Showcase.accent", new ColorUIResource(0xD81B60));
        // reflective constructor LineBorderUIResource(Color, int)
        table.put("Showcase.lineBorder", new UIDefaults.ProxyLazyValue(
                "javax.swing.plaf.BorderUIResource$LineBorderUIResource",
                new Object[] { new ColorUIResource(0xD81B60), 2 }));
        // reflective static method BorderFactory.createEtchedBorder()
        table.put("Showcase.etchedBorder", new UIDefaults.ProxyLazyValue("javax.swing.BorderFactory",
                "createEtchedBorder"));
        table.put("Showcase.inputMap", new UIDefaults.LazyInputMap(new Object[] {
                "ctrl shift X", "showcase-cut",
                "alt F4", "showcase-close",
                "shift F10", "showcase-menu",
                "meta BACK_QUOTE", "showcase-cycle" }));
        table.put("Showcase.lazy", (UIDefaults.LazyValue) t -> "created lazily");
        table.put("Showcase.active", (UIDefaults.ActiveValue) t -> "created on every lookup");
    }

    /**
     * Metal button with an accent bar at its bottom : an application UI delegate loaded by class name.
     */
    @RegisterForReflection
    public static class ShowcaseButtonUI extends MetalButtonUI {

        public ShowcaseButtonUI() {
        }

        public static ComponentUI createUI(JComponent c) {
            return new ShowcaseButtonUI();
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            super.paint(g, c);
            AbstractButton b = (AbstractButton) c;
            g.setColor(new Color(b.isEnabled() ? 0xD81B60 : 0xBDBDBD));
            g.fillRect(2, c.getHeight() - 5, c.getWidth() - 4, 3);
        }
    }
}
