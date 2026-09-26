package io.quarkiverse.desktop.showcase.pages.beans;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The explicit bean info of {@link Ticket} : a bean descriptor, two of its three properties with display names, a
 * property editor class, and 16 x 16 and 32 x 32 icons loaded with {@link SimpleBeanInfo#loadImage} from a classpath
 * resource (URL content handler of {@code image/png} : an image producer, then {@code Toolkit.createImage}).
 * <p>
 * Found by the {@code Introspector} with {@code Class.forName("...TicketBeanInfo")} : registered for reflection.
 */
@RegisterForReflection
public class TicketBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor descriptor = new BeanDescriptor(Ticket.class);
        descriptor.setDisplayName("Ticket (explicit bean info)");
        descriptor.setShortDescription("A ticket described by TicketBeanInfo");
        return descriptor;
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            PropertyDescriptor code = new PropertyDescriptor("code", Ticket.class);
            code.setDisplayName("Ticket code");
            code.setPropertyEditorClass(TicketEditor.class);
            PropertyDescriptor seat = new PropertyDescriptor("seat", Ticket.class, "getSeat", "setSeat");
            seat.setDisplayName("Seat number");
            seat.setPreferred(true);
            return new PropertyDescriptor[] { code, seat };
        } catch (IntrospectionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getDefaultPropertyIndex() {
        return 1;
    }

    @Override
    public Image getIcon(int kind) {
        return switch (kind) {
            case ICON_COLOR_16x16 -> loadImage("/showcase/print-misc/beans/ticket16.png");
            case ICON_COLOR_32x32 -> loadImage("/showcase/print-misc/beans/ticket32.png");
            default -> null;
        };
    }
}
