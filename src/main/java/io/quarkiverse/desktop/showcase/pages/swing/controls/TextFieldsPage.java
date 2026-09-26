package io.quarkiverse.desktop.showcase.pages.swing.controls;

import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.caption;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.column;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.date;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.format;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.row;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.runAction;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.section;
import static io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.typed;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DateFormatter;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.DocumentFilter;
import javax.swing.text.InternationalFormatter;
import javax.swing.text.MaskFormatter;
import javax.swing.text.NumberFormatter;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Ui;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.Readiness;
import io.quarkiverse.desktop.showcase.pages.swing.controls.ControlsSupport.EventLog;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Text fields : {@code JTextField} (alignments, states), {@code JPasswordField} (echo characters),
 * {@code JFormattedTextField} with number, currency, percent, {@code BigDecimal}, {@code BigInteger}, date, mask, regular
 * expression and custom value class formatters, formatter factories, commit policies, {@code JSpinner} with number, date,
 * time, list and cyclic models, {@code InputVerifier} and {@code DocumentFilter}.
 * <p>
 * Native paths exercised on purpose : {@code DefaultFormatter} and {@code NumberFormatter} create values with the
 * {@code String} constructor of the value class, by reflection ({@code BigInteger}, {@code Integer}, {@code URI}, and the
 * application class {@link ProductCode}), spinner and text field actions loaded by reflection ({@code LazyActionMap}),
 * the date and number formats of the locale (CLDR data of the image).
 */
@Singleton
// java.net.URI is a value class of a JFormattedTextField of this page (DefaultFormatter calls its String constructor by
// reflection) : an application choice, so an application level registration. The JDK value classes that Swing uses by
// default (Integer, Long, Double, BigDecimal, BigInteger...) are left to quarkus-desktop.
@RegisterForReflection(targets = URI.class)
public class TextFieldsPage implements FeaturePage {

    private static final int LABEL_WIDTH = 250;
    private static final int FIELD_WIDTH = 230;

    private ChecksView results;
    private final Readiness readiness = new Readiness();
    private final List<JComponent> components = new ArrayList<>();

    /**
     * A value class for a {@code JFormattedTextField} : {@code DefaultFormatter} creates it from the text with its public
     * {@code String} constructor, by reflection (hence {@code @RegisterForReflection} for native executables).
     */
    @RegisterForReflection
    public static final class ProductCode {

        private final String code;

        public ProductCode(String code) {
            if (!code.matches("[A-Z]{2}-\\d{3}")) {
                throw new IllegalArgumentException("Not a product code: " + code);
            }
            this.code = code;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ProductCode other && other.code.equals(code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }

        @Override
        public String toString() {
            return code;
        }
    }

    /**
     * A formatter accepting the text matching a regular expression (the classic Swing tutorial formatter).
     */
    static final class RegexFormatter extends DefaultFormatter {

        private final Pattern pattern;

        RegexFormatter(String regex) {
            this.pattern = Pattern.compile(regex);
            setOverwriteMode(false);
        }

        @Override
        public Object stringToValue(String text) throws ParseException {
            if (text == null || !pattern.matcher(text).matches()) {
                throw new ParseException("Pattern did not match", 0);
            }
            return super.stringToValue(text);
        }
    }

    /**
     * Upper case, at most {@code max} characters.
     */
    static final class UpperCaseFilter extends DocumentFilter {

        private final int max;

        UpperCaseFilter(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String upper = text == null ? "" : text.toUpperCase(Locale.ROOT);
            int room = max - (fb.getDocument().getLength() - length);
            fb.replace(offset, length, upper.substring(0, Math.max(0, Math.min(room, upper.length()))), attrs);
        }
    }

    /**
     * Accepts even integers ; paints the field border red when the input is refused.
     */
    static final class EvenNumberVerifier extends InputVerifier {

        @Override
        public boolean verify(JComponent input) {
            try {
                return Integer.parseInt(((JTextField) input).getText().trim()) % 2 == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public boolean shouldYieldFocus(JComponent source, JComponent target) {
            boolean ok = verify(source);
            source.setBorder(ok ? BorderFactory.createLineBorder(new Color(0x2E7D32), 2)
                    : BorderFactory.createLineBorder(new Color(0xC62828), 2));
            return ok;
        }
    }

    @Override
    public String id() {
        return "swing-text-fields";
    }

    @Override
    public String title() {
        return "Text fields, formatters and spinners";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Component build() throws Exception {
        components.clear();
        results = ChecksView.table("Checks", List.of(Check.info("state", "pending")));
        JPanel content = column(10,
                Ui.text("JTextField, JPasswordField, JFormattedTextField and JSpinner. The value column shows the value "
                        + "committed by the field (class and value) : number, date and mask formatters, and value classes "
                        + "created from the text with their String constructor by reflection.", 1000),
                section("JTextField and JPasswordField", textFields()),
                section("JFormattedTextField (display formatter : the fields are not focused)", formattedFields()),
                section("JSpinner", spinners()),
                results);
        content.setOpaque(true);
        content.setBackground(Color.WHITE);
        ControlsSupport.readyWhenShown(this, content);
        return content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        return readiness.get(content, this::prepare);
    }

    private CompletionStage<?> prepare(Component content) {
        ChecksView view = results;
        return Edt.rounds(2).thenAccept(v -> view.setChecks(checks()));
    }

    @Override
    public void dispose(Component content) {
        readiness.reset();
        results = null;
        components.clear();
    }

    // ----------------------------------------------------------------------------------------------------- gallery

    private <C extends JComponent> C track(C component) {
        components.add(component);
        return component;
    }

    private JComponent textFields() {
        JPanel alignments = new JPanel(new java.awt.GridLayout(2, 5, 8, 2));
        alignments.setOpaque(false);
        String[] names = { "LEFT", "CENTER", "RIGHT", "LEADING", "TRAILING" };
        int[] values = { SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.RIGHT, SwingConstants.LEADING,
                SwingConstants.TRAILING };
        for (String name : names) {
            alignments.add(caption(name));
        }
        for (int i = 0; i < values.length; i++) {
            JTextField field = track(new JTextField("Aligned", 12));
            field.setHorizontalAlignment(values[i]);
            alignments.add(field);
        }

        JTextField disabled = track(new JTextField("Disabled", 10));
        disabled.setEnabled(false);
        JTextField readOnly = track(new JTextField("Not editable", 10));
        readOnly.setEditable(false);
        JTextField styled = track(new JTextField("Monospaced 14", 15));
        styled.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        styled.setForeground(new Color(0x1565C0));
        styled.setBackground(new Color(0xFFF8E1));
        styled.setMargin(new Insets(4, 8, 4, 8));
        JTextField rtl = track(new JTextField("abc 123", 10));
        rtl.setComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);

        JPasswordField password = track(new JPasswordField("secret", 8));
        JPasswordField hash = track(new JPasswordField("secret", 8));
        hash.setEchoChar('#');
        JPasswordField revealed = track(new JPasswordField("secret", 8));
        revealed.setEchoChar((char) 0);

        JTextField filtered = track(new JTextField(12));
        ((AbstractDocument) filtered.getDocument()).setDocumentFilter(new UpperCaseFilter(10));
        filtered.setText("hello world swing");
        JTextField verified = track(new JTextField("7", 6));
        verified.setInputVerifier(new EvenNumberVerifier());
        verified.getInputVerifier().shouldYieldFocus(verified, null);
        JTextField verifiedOk = track(new JTextField("8", 6));
        verifiedOk.setInputVerifier(new EvenNumberVerifier());
        verifiedOk.getInputVerifier().shouldYieldFocus(verifiedOk, null);

        return column(6, alignments,
                row(10, labeled("disabled", disabled), labeled("setEditable(false)", readOnly),
                        labeled("font, colors, margin", styled), labeled("RIGHT_TO_LEFT", rtl)),
                row(10, labeled("JPasswordField (Metal echo)", password), labeled("echo '#'", hash),
                        labeled("echo 0 (revealed)", revealed),
                        labeled("DocumentFilter (upper, max 10)", filtered),
                        labeled("InputVerifier (even) : 7 / 8", row(4, verified, verifiedOk))));
    }

    private static JComponent labeled(String text, JComponent component) {
        return column(2, caption(text), component);
    }

    /**
     * The formatted field rows : description, field, committed value.
     */
    private final class Grid {

        final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        Grid() {
            panel.setOpaque(false);
        }

        void add(String description, JFormattedTextField field) {
            track(field);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(2, 0, 2, 12);
            JLabel label = new JLabel(description);
            label.setPreferredSize(new Dimension(LABEL_WIDTH, label.getPreferredSize().height));
            c.gridx = 0;
            panel.add(label, c);
            field.setPreferredSize(new Dimension(FIELD_WIDTH, field.getPreferredSize().height));
            c.gridx = 1;
            panel.add(field, c);
            c.gridx = 2;
            c.weightx = 1;
            JLabel value = new JLabel(typed(field.getValue()) + (field.isEditValid() ? "" : "   (edit not valid)"));
            value.setFont(value.getFont().deriveFont(Font.PLAIN));
            value.setForeground(field.isEditValid() ? new Color(0x333333) : new Color(0xC62828));
            panel.add(value, c);
        }
    }

    private JComponent formattedFields() throws ParseException {
        Grid grid = new Grid();
        grid.add("Integer (getIntegerInstance)", integerField(1234567));
        grid.add("Long (valueClass Long)", longField(9_876_543_210L));
        grid.add("Double (#,##0.000)", doubleField(3.14159));
        grid.add("BigDecimal (setParseBigDecimal)", bigDecimalField(new BigDecimal("12345.6789")));
        grid.add("BigInteger (String constructor)", defaultField(BigInteger.class, BigInteger.TWO.pow(70)));
        grid.add("Currency (en-US)", new JFormattedTextField(NumberFormat.getCurrencyInstance(Locale.US)) {
            {
                setValue(1234.5);
            }
        });
        grid.add("Percent (en-US)", new JFormattedTextField(NumberFormat.getPercentInstance(Locale.US)) {
            {
                setValue(0.25);
            }
        });
        grid.add("Date (yyyy-MM-dd HH:mm)", dateField(date(2024, 3, 15, 12, 30)));
        grid.add("Date (MEDIUM, SHORT, en-US)", mediumDateField(date(2024, 3, 15, 12, 30)));
        grid.add("MaskFormatter (###) ###-####", maskField("(###) ###-####", false, "5551234567"));
        grid.add("MaskFormatter UU-HHHH (literals kept)", maskField("UU-HHHH", true, "QD-2A3F"));
        grid.add("Custom value class (String ctor)", defaultField(ProductCode.class, new ProductCode("QD-001")));
        grid.add("URI (String constructor)", defaultField(URI.class, URI.create("urn:isbn:0451450523")));
        JFormattedTextField regex = new JFormattedTextField(new RegexFormatter("[A-Z]{3}-\\d{4}"));
        regex.setValue("ABC-1234");
        grid.add("RegexFormatter [A-Z]{3}-\\d{4}", regex);
        JFormattedTextField invalid = new JFormattedTextField(new RegexFormatter("[A-Z]{3}-\\d{4}"));
        invalid.setValue("XYZ-0001");
        invalid.setText("abc-12");
        grid.add("RegexFormatter, invalid edit (setText)", invalid);
        grid.add("DefaultFormatterFactory display/edit", factoryField(1234.5));
        grid.add("DefaultFormatterFactory null formatter", factoryField(null));
        grid.add("NumberFormatter 0..100 (min/max)", rangeField(42));
        grid.add("JFormattedTextField(Integer 42)", new JFormattedTextField(42));
        return grid.panel;
    }

    private JComponent spinners() {
        JSpinner number = track(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
        JSpinner decimal = track(new JSpinner(new SpinnerNumberModel(1.75, 0.0, 5.0, 0.25)));
        decimal.setEditor(new JSpinner.NumberEditor(decimal, "#0.00"));
        JSpinner dateSpinner = track(new JSpinner(new SpinnerDateModel(date(2024, 3, 15, 12, 30), null, null,
                Calendar.DAY_OF_MONTH)));
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
        JSpinner time = track(new JSpinner(new SpinnerDateModel(date(2024, 3, 15, 12, 30), null, null,
                Calendar.MINUTE)));
        time.setEditor(new JSpinner.DateEditor(time, "HH:mm"));
        JSpinner months = track(new JSpinner(new SpinnerListModel(months())));
        months.setValue("March");
        JSpinner days = track(new JSpinner(new CyclicListModel(weekdays())));
        days.setValue("Saturday");
        JSpinner disabled = track(new JSpinner(new SpinnerNumberModel(42, 0, 100, 1)));
        disabled.setEnabled(false);
        for (JSpinner spinner : List.of(number, decimal, dateSpinner, time, months, days, disabled)) {
            spinner.setPreferredSize(new Dimension(110, spinner.getPreferredSize().height));
        }
        return row(10, labeled("number 0..10", number), labeled("NumberEditor #0.00", decimal),
                labeled("DateEditor yyyy-MM-dd", dateSpinner), labeled("DateEditor HH:mm", time),
                labeled("SpinnerListModel", months), labeled("cyclic list", days), labeled("disabled", disabled));
    }

    // -------------------------------------------------------------------------------------------------- formatters

    private static JFormattedTextField integerField(int value) {
        JFormattedTextField field = new JFormattedTextField(
                new NumberFormatter(NumberFormat.getIntegerInstance(Locale.US)));
        field.setValue(value);
        return field;
    }

    private static JFormattedTextField longField(long value) {
        NumberFormatter formatter = new NumberFormatter(NumberFormat.getIntegerInstance(Locale.US));
        formatter.setValueClass(Long.class);
        JFormattedTextField field = new JFormattedTextField(formatter);
        field.setValue(value);
        return field;
    }

    private static JFormattedTextField doubleField(double value) {
        NumberFormatter formatter = new NumberFormatter(new DecimalFormat("#,##0.000", DecimalFormatSymbols.getInstance(
                Locale.US)));
        formatter.setValueClass(Double.class);
        JFormattedTextField field = new JFormattedTextField(formatter);
        field.setValue(value);
        return field;
    }

    private static JFormattedTextField bigDecimalField(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.0000", DecimalFormatSymbols.getInstance(Locale.US));
        format.setParseBigDecimal(true);
        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setValueClass(BigDecimal.class);
        JFormattedTextField field = new JFormattedTextField(formatter);
        field.setValue(value);
        return field;
    }

    /**
     * A {@code DefaultFormatter} for {@code valueClass} : values created with the {@code String} constructor.
     */
    private static JFormattedTextField defaultField(Class<?> valueClass, Object value) {
        DefaultFormatter formatter = new DefaultFormatter();
        formatter.setValueClass(valueClass);
        formatter.setOverwriteMode(false);
        JFormattedTextField field = new JFormattedTextField(formatter);
        field.setValue(value);
        return field;
    }

    private static SimpleDateFormat isoMinutes() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    }

    private static JFormattedTextField dateField(Date value) {
        JFormattedTextField field = new JFormattedTextField(new DateFormatter(isoMinutes()));
        field.setValue(value);
        return field;
    }

    private static JFormattedTextField mediumDateField(Date value) {
        JFormattedTextField field = new JFormattedTextField(
                new DateFormatter(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US)));
        field.setValue(value);
        return field;
    }

    private static MaskFormatter mask(String mask, boolean literals) throws ParseException {
        MaskFormatter formatter = new MaskFormatter(mask);
        formatter.setPlaceholderCharacter('_');
        formatter.setValueContainsLiteralCharacters(literals);
        return formatter;
    }

    private static JFormattedTextField maskField(String mask, boolean literals, String value) throws ParseException {
        JFormattedTextField field = new JFormattedTextField(mask(mask, literals));
        field.setValue(value);
        return field;
    }

    private static DefaultFormatterFactory currencyFactory() {
        NumberFormatter display = new NumberFormatter(NumberFormat.getCurrencyInstance(Locale.US));
        NumberFormatter edit = new NumberFormatter(new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US)));
        DefaultFormatter none = new DefaultFormatter() {
            @Override
            public String valueToString(Object value) throws ParseException {
                return value == null ? "(no value)" : super.valueToString(value);
            }
        };
        return new DefaultFormatterFactory(display, display, edit, none);
    }

    private static JFormattedTextField factoryField(Object value) {
        JFormattedTextField field = new JFormattedTextField(currencyFactory());
        field.setValue(value);
        return field;
    }

    private static NumberFormatter rangeFormatter() {
        NumberFormatter formatter = new NumberFormatter(NumberFormat.getIntegerInstance(Locale.US));
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(0);
        formatter.setMaximum(100);
        return formatter;
    }

    private static JFormattedTextField rangeField(int value) {
        JFormattedTextField field = new JFormattedTextField(rangeFormatter());
        field.setValue(value);
        return field;
    }

    private static List<String> months() {
        return Arrays.stream(new DateFormatSymbols(Locale.US).getMonths()).filter(m -> !m.isEmpty()).toList();
    }

    private static List<String> weekdays() {
        return Arrays.stream(new DateFormatSymbols(Locale.US).getWeekdays()).filter(d -> !d.isEmpty()).toList();
    }

    /**
     * A list model whose next value after the last one is the first one (and conversely).
     */
    static final class CyclicListModel extends SpinnerListModel {

        CyclicListModel(List<?> values) {
            super(values);
        }

        @Override
        public Object getNextValue() {
            Object next = super.getNextValue();
            return next != null ? next : getList().get(0);
        }

        @Override
        public Object getPreviousValue() {
            Object previous = super.getPreviousValue();
            return previous != null ? previous : getList().get(getList().size() - 1);
        }
    }

    // ------------------------------------------------------------------------------------------------------ checks

    /**
     * Sets {@code text} and commits it : the committed value (class and value), or the exception.
     */
    private static String commit(JFormattedTextField field, String text) throws ParseException {
        field.setText(text);
        field.commitEdit();
        return typed(field.getValue());
    }

    private List<Check> checks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("UI delegates",
                "BasicFormattedTextFieldUI BasicPasswordFieldUI BasicSpinnerUI MetalTextFieldUI", () -> {
                    TreeSet<String> names = new TreeSet<>();
                    components.forEach(c -> names.add(c.getUI().getClass().getSimpleName()));
                    return String.join(" ", names);
                }));
        checks.addAll(textFieldChecks());
        checks.addAll(formatterChecks());
        checks.addAll(spinnerChecks());
        return checks;
    }

    private static List<Check> textFieldChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("JPasswordField : getPassword, Metal echo char, echoCharIsSet after setEchoChar(0)",
                "secret, U+2022, false", () -> {
                    JPasswordField field = new JPasswordField("secret");
                    String password = new String(field.getPassword());
                    String echo = String.format(Locale.ROOT, "U+%04X", (int) field.getEchoChar());
                    field.setEchoChar((char) 0);
                    return password + ", " + echo + ", " + field.echoCharIsSet();
                }));
        checks.add(Checks.expect("DocumentFilter : insert, replace, then insert beyond the limit",
                "HELLO WORL | HELLO SWIN | HELLO SWIN", () -> {
                    JTextField field = new JTextField();
                    ((AbstractDocument) field.getDocument()).setDocumentFilter(new UpperCaseFilter(10));
                    field.setText("hello world swing");
                    String first = field.getText();
                    field.getDocument().remove(6, 4);
                    field.getDocument().insertString(6, "swing", null);
                    String second = field.getText();
                    field.getDocument().insertString(0, "more", null);
                    return first + " | " + second + " | " + field.getText();
                }));
        checks.add(Checks.expect("InputVerifier : verify 7 / 8, shouldYieldFocus 7", "false / true, false", () -> {
            EvenNumberVerifier verifier = new EvenNumberVerifier();
            JTextField field = new JTextField("7");
            String seven = String.valueOf(verifier.verify(field));
            field.setText("8");
            String eight = String.valueOf(verifier.verify(field));
            field.setText("7");
            return seven + " / " + eight + ", " + verifier.shouldYieldFocus(field, new JTextField());
        }));
        checks.add(Checks.expect("JTextField postActionEvent : command = text", "hello | 1 event", () -> {
            EventLog log = new EventLog();
            JTextField field = new JTextField("hello");
            field.addActionListener(e -> log.add(e.getActionCommand()));
            field.postActionEvent();
            return log + " | " + log.size() + " event";
        }));
        checks.add(Checks.expect("text actions (DefaultEditorKit) : select-all, caret-begin, delete-next, insert-content",
                "0-11 | ello world | Jello world", () -> {
                    JTextField field = new JTextField("hello world");
                    runAction(field, DefaultEditorKit.selectAllAction);
                    String selection = field.getSelectionStart() + "-" + field.getSelectionEnd();
                    runAction(field, DefaultEditorKit.beginAction);
                    runAction(field, DefaultEditorKit.deleteNextCharAction);
                    String deleted = field.getText();
                    runAction(field, DefaultEditorKit.insertContentAction, "J");
                    return selection + " | " + deleted + " | " + field.getText();
                }));
        checks.add(Checks.expect("JTextField actions count > 50, keymap bindings for ENTER",
                "true, notify-field-accept", () -> {
                    JTextField field = new JTextField();
                    return (field.getActions().length > 50) + ", "
                            + field.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("ENTER"));
                }));
        return checks;
    }

    private static List<Check> formatterChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("getIntegerInstance, no valueClass : commit \"98,765\" (Long)", "Long 98765",
                () -> commit(integerField(1), "98,765")));
        checks.add(Checks.expect("Long : commit \"9,876,543,210\"", "Long 9876543210",
                () -> commit(longField(1), "9,876,543,210")));
        checks.add(Checks.expect("Double #,##0.000 : commit \"2.71828\", then displayed text", "Double 2.71828, 2.718",
                () -> {
                    JFormattedTextField field = doubleField(1);
                    String value = commit(field, "2.71828");
                    field.setValue(field.getValue());
                    return value + ", " + field.getText();
                }));
        checks.add(Checks.expect("BigDecimal : commit \"0.1000\"", "BigDecimal 0.1000",
                () -> commit(bigDecimalField(BigDecimal.ONE), "0.1000")));
        checks.add(Checks.expect("BigInteger (DefaultFormatter, String constructor) : commit", "BigInteger 12345678901234567890",
                () -> commit(defaultField(BigInteger.class, BigInteger.ONE), "12345678901234567890")));
        checks.add(Checks.expect("Integer (DefaultFormatter, Integer(String) by reflection)", "Integer 321", () -> {
            DefaultFormatter formatter = new DefaultFormatter();
            formatter.setValueClass(Integer.class);
            return typed(formatter.stringToValue("321"));
        }));
        checks.add(Checks.expect("ProductCode (application value class, String constructor) : commit",
                "ProductCode QD-777", () -> commit(defaultField(ProductCode.class, new ProductCode("QD-001")), "QD-777")));
        checks.add(Checks.expect("ProductCode : invalid text -> ParseException", "ParseException: Error creating instance",
                () -> {
                    try {
                        return commit(defaultField(ProductCode.class, new ProductCode("QD-001")), "nope");
                    } catch (ParseException e) {
                        return "ParseException: " + e.getMessage();
                    }
                }));
        checks.add(Checks.expect("URI (String constructor) : commit", "URI urn:isbn:0306406152",
                () -> commit(defaultField(URI.class, URI.create("urn:x:y")), "urn:isbn:0306406152")));
        checks.add(Checks.expect("Currency : commit \"$99.95\" / \"$100.00\"", "Double 99.95 / Long 100", () -> {
            JFormattedTextField field = new JFormattedTextField(NumberFormat.getCurrencyInstance(Locale.US));
            field.setValue(1.5);
            return commit(field, "$99.95") + " / " + commit(field, "$100.00");
        }));
        checks.add(Checks.expect("Percent : commit \"12.5%\", display of 0.25", "Double 0.125, 25%", () -> {
            JFormattedTextField field = new JFormattedTextField(NumberFormat.getPercentInstance(Locale.US));
            field.setValue(0.5);
            String committed = commit(field, "12.5%");
            field.setValue(0.25);
            return committed + ", " + field.getText();
        }));
        checks.add(Checks.expect("DateFormatter : commit \"2025-12-31 23:59\"", "Date 2025-12-31 23:59", () -> {
            JFormattedTextField field = dateField(date(2024, 1, 1, 0, 0));
            field.setText("2025-12-31 23:59");
            field.commitEdit();
            return "Date " + format((Date) field.getValue());
        }));
        checks.add(Checks.expect("DateFormat MEDIUM/SHORT en-US text (CLDR, narrow no-break space before PM)",
                "Mar 15, 2024, 12:30<U+202F>PM", () -> mediumDateField(date(2024, 3, 15, 12, 30)).getText()
                        .replace("\u202F", "<U+202F>")));
        checks.add(Checks.expect("MaskFormatter (###) ###-#### : commit, value without literals", "String 5559876543",
                () -> commit(maskField("(###) ###-####", false, "5551234567"), "(555) 987-6543")));
        checks.add(Checks.expect("MaskFormatter : stringToValue with a letter", "ParseException: stringToValue passed invalid value",
                () -> {
                    try {
                        return mask("(###) ###-####", false).stringToValue("(55a) 987-6543");
                    } catch (ParseException e) {
                        return "ParseException: " + e.getMessage();
                    }
                }));
        checks.add(Checks.expect("MaskFormatter UU-HHHH : stringToValue(\"qd-2a3f\") / field setText + commit",
                "qd-2a3f / String QD-2A3F", () -> mask("UU-HHHH", true).stringToValue("qd-2a3f") + " / "
                        + commit(maskField("UU-HHHH", true, "AB-0000"), "qd-2a3f")));
        checks.add(Checks.expect("MaskFormatter : valueToString of a partial value", "(12_) ___-____",
                () -> mask("(###) ###-####", false).valueToString("12")));
        checks.add(Checks.expect("RegexFormatter : valid / invalid", "String ABC-1234 / ParseException", () -> {
            RegexFormatter formatter = new RegexFormatter("[A-Z]{3}-\\d{4}");
            String valid = typed(formatter.stringToValue("ABC-1234"));
            try {
                formatter.stringToValue("abc-12");
                return valid + " / accepted";
            } catch (ParseException e) {
                return valid + " / ParseException";
            }
        }));
        checks.add(Checks.expect("invalid edit : isEditValid, value kept", "false, String XYZ-0001", () -> {
            JFormattedTextField field = new JFormattedTextField(new RegexFormatter("[A-Z]{3}-\\d{4}"));
            field.setValue("XYZ-0001");
            field.setText("abc-12");
            return field.isEditValid() + ", " + typed(field.getValue());
        }));
        checks.add(Checks.expect("DefaultFormatterFactory : display / edit / null formatter texts",
                "$1,234.50 / 1234.50 / (no value)", () -> {
                    DefaultFormatterFactory factory = currencyFactory();
                    return factory.getDisplayFormatter().valueToString(1234.5) + " / "
                            + factory.getEditFormatter().valueToString(1234.5) + " / "
                            + factory.getNullFormatter().valueToString(null);
                }));
        checks.add(Checks.expect("NumberFormatter min/max : \"150\" / \"100\"", "ParseException / Integer 100", () -> {
            NumberFormatter formatter = rangeFormatter();
            String high;
            try {
                high = typed(formatter.stringToValue("150"));
            } catch (ParseException e) {
                high = "ParseException";
            }
            return high + " / " + typed(formatter.stringToValue("100"));
        }));
        checks.add(Checks.expect("setCommitsOnValidEdit(true) : value while typing, value events", "Integer 1234, 4 events",
                () -> {
                    NumberFormatter formatter = new NumberFormatter(NumberFormat.getIntegerInstance(Locale.US));
                    formatter.setValueClass(Integer.class);
                    formatter.setCommitsOnValidEdit(true);
                    JFormattedTextField field = new JFormattedTextField(formatter);
                    field.setValue(0);
                    EventLog log = new EventLog();
                    field.addPropertyChangeListener("value", e -> log.add(String.valueOf(e.getNewValue())));
                    field.setText("");
                    for (String digit : List.of("1", "2", "3", "4")) {
                        field.getDocument().insertString(field.getDocument().getLength(), digit, null);
                    }
                    return typed(field.getValue()) + ", " + log.size() + " events";
                }));
        checks.add(Checks.expect("focus lost behavior (default) / JFormattedTextField(42) formatter", "1 / NumberFormatter",
                () -> {
                    JFormattedTextField field = new JFormattedTextField(42);
                    return field.getFocusLostBehavior() + " / " + field.getFormatter().getClass().getSimpleName();
                }));
        checks.add(Checks.expect("JFormattedTextField(42) : commit \"43\"", "Integer 43",
                () -> commit(new JFormattedTextField(42), "43")));
        checks.add(Checks.expect("InternationalFormatter.getFields(0) of an integer", "integer", () -> {
            InternationalFormatter formatter = new InternationalFormatter(NumberFormat.getIntegerInstance(Locale.US));
            JFormattedTextField field = new JFormattedTextField(formatter);
            field.setValue(1234);
            return String.join(" ", Arrays.stream(formatter.getFields(0)).map(f -> f.toString()
                    .replaceAll(".*\\((.*)\\)", "$1")).toList());
        }));
        return checks;
    }

    private static List<Check> spinnerChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("SpinnerNumberModel : next / previous at the bounds", "null / 9, null / 1", () -> {
            SpinnerNumberModel model = new SpinnerNumberModel(10, 0, 10, 1);
            String top = model.getNextValue() + " / " + model.getPreviousValue();
            model.setValue(0);
            return top + ", " + model.getPreviousValue() + " / " + model.getNextValue();
        }));
        checks.add(Checks.expect("spinner actions increment, increment, decrement (BasicSpinnerUI.loadActionMap)",
                "6 7 6", () -> {
                    JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
                    List<String> values = new ArrayList<>();
                    for (String name : List.of("increment", "increment", "decrement")) {
                        runAction(spinner, name);
                        values.add(String.valueOf(spinner.getValue()));
                    }
                    return String.join(" ", values);
                }));
        checks.add(Checks.expect("spinner editor : commit \"8\" in the text field", "Integer 8", () -> {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
            JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            field.setText("8");
            field.commitEdit();
            return typed(spinner.getValue());
        }));
        checks.add(Checks.expect("NumberEditor #0.00 : next value, text", "Double 2.0, 2.00", () -> {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(1.75, 0.0, 5.0, 0.25));
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "#0.00"));
            spinner.setValue(spinner.getNextValue());
            return typed(spinner.getValue()) + ", "
                    + ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText();
        }));
        checks.add(Checks.expect("SpinnerDateModel DAY_OF_MONTH : next value, editor text", "2024-03-16 12:30, 2024-03-16",
                () -> {
                    JSpinner spinner = new JSpinner(new SpinnerDateModel(date(2024, 3, 15, 12, 30), null, null,
                            Calendar.DAY_OF_MONTH));
                    spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));
                    spinner.setValue(spinner.getNextValue());
                    return format((Date) spinner.getValue()) + ", "
                            + ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText();
                }));
        checks.add(Checks.expect("DateEditor HH:mm : increment action (field at the caret : hours)", "13:30", () -> {
            JSpinner spinner = new JSpinner(new SpinnerDateModel(date(2024, 3, 15, 12, 30), null, null,
                    Calendar.MINUTE));
            spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
            runAction(spinner, "increment");
            return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText();
        }));
        checks.add(Checks.expect("SpinnerListModel months : next of March, previous of January", "April, null", () -> {
            SpinnerListModel model = new SpinnerListModel(months());
            model.setValue("March");
            String next = String.valueOf(model.getNextValue());
            model.setValue("January");
            return next + ", " + model.getPreviousValue();
        }));
        checks.add(Checks.expect("cyclic list model : next of Saturday, previous of Sunday", "Sunday, Saturday", () -> {
            CyclicListModel model = new CyclicListModel(weekdays());
            model.setValue("Saturday");
            String next = String.valueOf(model.getNextValue());
            model.setValue("Sunday");
            return next + ", " + model.getPreviousValue();
        }));
        checks.add(Checks.expect("spinner editors", "NumberEditor DateEditor ListEditor", () -> {
            JSpinner number = new JSpinner(new SpinnerNumberModel());
            JSpinner dates = new JSpinner(new SpinnerDateModel());
            JSpinner list = new JSpinner(new SpinnerListModel(months()));
            return number.getEditor().getClass().getSimpleName() + " " + dates.getEditor().getClass().getSimpleName()
                    + " " + list.getEditor().getClass().getSimpleName();
        }));
        return checks;
    }
}
