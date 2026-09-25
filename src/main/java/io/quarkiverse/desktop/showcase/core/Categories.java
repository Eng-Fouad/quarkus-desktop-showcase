package io.quarkiverse.desktop.showcase.core;

import java.util.List;
import java.util.Locale;

/**
 * Page categories, in display order.
 */
public final class Categories {

    public static final String OVERVIEW = "Overview";
    public static final String AWT = "AWT";
    public static final String JAVA2D = "Java2D";
    public static final String TEXT = "Text & Fonts";
    public static final String IMAGES = "Images & Color";
    public static final String SWING = "Swing Components";
    public static final String LAF = "Look & Feel";
    public static final String DESKTOP = "Data Transfer & Desktop";
    public static final String PRINTING = "Printing";
    public static final String A11Y_BEANS = "Accessibility & Beans";
    public static final String SOUND = "Sound";

    public static final List<String> ORDER = List.of(OVERVIEW, AWT, JAVA2D, TEXT, IMAGES, SWING, LAF, DESKTOP, PRINTING,
            A11Y_BEANS, SOUND);

    /**
     * Short keys of the categories (same order as {@link #ORDER}), accepted by {@code -Dshowcase.categories=}.
     */
    public static final List<String> KEYS = List.of("overview", "awt", "java2d", "text", "images", "swing", "laf",
            "desktop", "printing", "a11y", "sound");

    private Categories() {
    }

    public static int rank(String category) {
        int index = ORDER.indexOf(category);
        return index < 0 ? ORDER.size() : index;
    }

    /**
     * {@code true} when {@code filter} designates {@code category} : its name or its key, ignoring case.
     */
    public static boolean matches(String filter, String category) {
        String f = filter.trim().toLowerCase(Locale.ROOT);
        int index = ORDER.indexOf(category);
        return f.equals(category.toLowerCase(Locale.ROOT)) || (index >= 0 && f.equals(KEYS.get(index)));
    }
}
