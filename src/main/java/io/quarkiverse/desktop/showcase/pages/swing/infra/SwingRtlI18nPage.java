package io.quarkiverse.desktop.showcase.pages.swing.infra;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.font.NumericShaper;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.text.Bidi;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.tree.DefaultMutableTreeNode;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Platforms;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * Right-to-left layout and localization : the same form (menu bar, tool bar, tabbed pane, grid bag form with text
 * fields, combo box, check box, radio buttons, slider, spinner, progress bar, split pane with a tree and a table, text
 * area, buttons) in English, left to right, and in Arabic after
 * {@code applyComponentOrientation(RIGHT_TO_LEFT)} ; bidirectional text in Swing text components, {@link Bidi},
 * {@link NumericShaper}, locale sensitive number and date formats, and the localized UI strings of
 * {@link JOptionPane}, {@link JFileChooser} and {@link JColorChooser} in German, Japanese and Simplified Chinese (the
 * resource bundles of the look and feel).
 * <p>
 * Native executables include the locale data and the localized resource bundles of the locales listed in
 * {@code quarkus.locales} only : this page needs {@code quarkus.locales=en-US,de-DE,ja-JP,zh-CN,ar-SA}.
 */
@Singleton
public class SwingRtlI18nPage implements FeaturePage {

    private static final int FORM_W = 500;
    /** 2026-09-26T00:00:00Z : dates are formatted in UTC, never "now". */
    private static final long DATE = 1790380800000L;

    private static final Locale AR = Locale.forLanguageTag("ar-SA");

    /** The texts of a form. */
    private record Texts(String file, String edit, String view, String help, String newText, String open, String save,
            String general, String advanced, String about, String name, String nameValue, String email, String city,
            String[] cities, String remember, String mail, String phone, String volume, String progress,
            String documents, String reports, String invoices, String pictures, String music, String product,
            String quantity, String price, String[] products, String sentence, String ok, String cancel) {
    }

    private static Texts english() {
        return new Texts("File", "Edit", "View", "Help", "New", "Open", "Save", "General", "Advanced", "About",
                "Name:", "Ahmad", "Email:", "City:", new String[] { "Riyadh", "Jeddah", "Dammam" }, "Remember me",
                "Mail", "Phone", "Volume:", "Progress:", "Documents", "Reports", "Invoices", "Pictures", "Music",
                "Product", "Quantity", "Price",
                new String[] { "Coffee", "Tea", "Dates", "Bread", "Milk", "Rice", "Honey", "Sugar" },
                "Welcome to Quarkus 3.40 with Java 25.", "OK", "Cancel");
    }

    private static Texts arabic() {
        return new Texts("ملف", "تحرير", "عرض", "مساعدة", "جديد", "فتح", "حفظ", "عام", "متقدم", "حول",
                "الاسم:", "أحمد", "البريد الإلكتروني:", "المدينة:", new String[] { "الرياض", "جدة", "الدمام" },
                "تذكرني", "بريد", "هاتف", "مستوى الصوت:", "التقدم:", "المستندات", "التقارير", "الفواتير", "الصور",
                "الموسيقى", "المنتج", "الكمية", "السعر",
                new String[] { "قهوة", "شاي", "تمر", "خبز", "حليب", "أرز", "عسل", "سكر" },
                "مرحبا بكم في Quarkus 3.40 مع Java 25.", "موافق", "إلغاء");
    }

    /** The components of one form, for the geometry checks. */
    private static final class Form {
        JPanel root;
        JMenuBar menuBar;
        JToolBar toolBar;
        JTabbedPane tabs;
        JLabel nameLabel;
        JTextField nameField;
        JTextField emailField;
        JCheckBox remember;
        JRadioButton mail;
        JSlider slider;
        JSpinner spinner;
        JProgressBar progress;
        JSplitPane split;
        JTree tree;
        JTable table;
        JScrollPane tableScroll;
        JTextArea area;
        JButton ok;
        JButton cancel;
    }

    /** UIManager keys shown for each locale. */
    private static final String[] KEYS = { "OptionPane.yesButtonText", "OptionPane.noButtonText",
            "OptionPane.cancelButtonText", "OptionPane.okButtonText", "OptionPane.titleText",
            "OptionPane.inputDialogTitle", "FileChooser.openButtonText", "FileChooser.saveButtonText",
            "FileChooser.lookInLabelText", "FileChooser.fileNameLabelText", "FileChooser.acceptAllFileFilterText",
            "FileChooser.upFolderToolTipText", "ColorChooser.okText", "ColorChooser.resetText",
            "ColorChooser.previewText", "ColorChooser.swatchesNameText", "ColorChooser.sampleText" };

    /** Expected values of {@link #KEYS} in en (root), de, ja, zh_CN (JDK 25 resource bundles). */
    private static final String[][] EXPECTED = {
            { "Yes", "Ja", "はい(Y)", "是(Y)" },
            { "No", "Nein", "いいえ(N)", "否(N)" },
            { "Cancel", "Abbrechen", "取消", "取消" },
            { "OK", "OK", "OK", "确定" },
            { "Select an Option", "Option auswählen", "オプションの選択",
                    "选择一个选项" },
            { "Input", "Eingabe", "入力", "输入" },
            { "Open", "Öffnen", "開く", "打开" },
            { "Save", "Speichern", "保存", "保存" },
            { "Look In:", "Suchen in:", "ファイルの場所(I):", "查找(I):" },
            { "File Name:", "Dateiname:", "ファイル名(N):", "文件名(N):" },
            { "All Files", "Alle Dateien", "すべてのファイル",
                    "所有文件" },
            { "Up One Level", "Eine Ebene höher", "1レベル上へ", "向上一级" },
            { "OK", "OK", "OK", "确定" },
            { "Reset", "Zurücksetzen", "リセット(R)", "重置(R)" },
            { "Preview", "Vorschau", "プレビュー", "预览" },
            { "Swatches", "Swatches", "サンプル(S)", "样本(S)" },
            { "Sample Text  Sample Text", "Beispieltext  Beispieltext",
                    "サンプル・テキスト  "
                            + "サンプル・テキスト",
                    "示例文本  示例文本" },
    };

    // per build state
    private Form ltr;
    private Form rtl;
    private JPanel content;
    private ChecksView geometry;
    private ChecksView choosers;

    @Override
    public String id() {
        return "swing-rtl-i18n";
    }

    @Override
    public String title() {
        return "Right to left and localization";
    }

    @Override
    public String category() {
        return Categories.SWING;
    }

    @Override
    public int order() {
        return 950;
    }

    private static Locale[] uiLocales() {
        return new Locale[] { Locale.ROOT, Locale.GERMAN, Locale.JAPANESE, Locale.SIMPLIFIED_CHINESE };
    }

    @Override
    public Component build() {
        ltr = form(english(), Locale.US, false);
        rtl = form(arabic(), AR, true);

        JPanel panes = InfraSupport.row(10);
        String[] messages = { "Save the changes?", "Änderungen speichern?",
                "変更を保存しますか?", "保存更改吗?" };
        Locale[] locales = uiLocales();
        for (int i = 0; i < locales.length; i++) {
            JOptionPane pane = new JOptionPane(messages[i], JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.YES_NO_CANCEL_OPTION);
            // the button texts come from the look and feel bundles, for the locale of the option pane
            pane.setLocale(locales[i]);
            pane.updateUI();
            pane.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0xB0BEC5)));
            pane.setPreferredSize(new Dimension(247, pane.getPreferredSize().height));
            panes.add(InfraSupport.column(4, pane, Ui.caption("JOptionPane, locale "
                    + (locales[i] == Locale.ROOT ? "root (en)" : locales[i].toString()))));
        }

        geometry = ChecksView.table("Right to left geometry (LTR form, RTL form)",
                List.of(Check.info("layout", "pending")));
        choosers = ChecksView.table("File and color choosers", List.of(Check.info("choosers", "pending")));
        content = InfraSupport.column(14,
                Ui.text("The same form in English (left to right) and in Arabic after applyComponentOrientation("
                        + "RIGHT_TO_LEFT) : menus, tool bar, tabs, labels, check boxes, sliders, spinners, progress "
                        + "bars, scroll bars, tables and trees are mirrored ; Arabic text is shaped and laid out right "
                        + "to left (bidi), numbers use Arabic-Indic digits (ar-SA). Below : option panes and look and "
                        + "feel strings in the root (English), German, Japanese and Simplified Chinese bundles.", 1000),
                InfraSupport.row(14, ltr.root, rtl.root),
                panes,
                stringsTable(),
                geometry,
                ChecksView.table("Bidi, numeric shaping, locales", textChecks()),
                choosers);
        return content;
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        Form l = ltr;
        Form r = rtl;
        ChecksView geometryView = geometry;
        ChecksView choosersView = choosers;
        JPanel root = this.content;
        return Edt.rounds(2).thenAccept(v -> {
            geometryView.setChecks(geometryChecks(l, r));
            choosersView.setChecks(chooserChecks());
            root.revalidate();
        });
    }

    @Override
    public void dispose(Component content) {
        ltr = null;
        rtl = null;
        this.content = null;
        geometry = null;
        choosers = null;
    }

    // ------------------------------------------------------------------------------------------------ the form

    private static Form form(Texts t, Locale locale, boolean rightToLeft) {
        Form f = new Form();
        f.menuBar = new JMenuBar();
        for (String menu : List.of(t.file(), t.edit(), t.view(), t.help())) {
            f.menuBar.add(new JMenu(menu));
        }
        f.toolBar = new JToolBar();
        f.toolBar.setFloatable(false);
        for (String button : List.of(t.newText(), t.open(), t.save())) {
            f.toolBar.add(new JButton(button));
        }
        f.toolBar.addSeparator();
        f.toolBar.add(new JLabel(utc(DateFormat.getDateInstance(DateFormat.LONG, locale)).format(new Date(DATE))));

        JPanel general = new JPanel(new GridBagLayout());
        f.nameLabel = new JLabel(t.name());
        f.nameField = new JTextField(t.nameValue(), 14);
        f.emailField = new JTextField("ahmad@example.org", 14);
        JComboBox<String> city = new JComboBox<>(t.cities());
        f.remember = new JCheckBox(t.remember(), true);
        f.mail = new JRadioButton(t.mail());
        JRadioButton phone = new JRadioButton(t.phone(), true);
        ButtonGroup group = new ButtonGroup();
        group.add(f.mail);
        group.add(phone);
        f.slider = new JSlider(0, 100, 25);
        f.slider.setMajorTickSpacing(25);
        f.slider.setPaintTicks(true);
        f.slider.setPreferredSize(new Dimension(170, 36));
        f.spinner = new JSpinner(new SpinnerNumberModel(7, 0, 10, 1));
        f.progress = new JProgressBar(0, 100);
        f.progress.setValue(30);
        f.progress.setStringPainted(true);
        f.progress.setString(NumberFormat.getPercentInstance(locale).format(0.3));
        row(general, 0, f.nameLabel, f.nameField);
        row(general, 1, new JLabel(t.email()), f.emailField);
        row(general, 2, new JLabel(t.city()), city);
        row(general, 3, new JLabel(""), InfraSupport.row(6, f.remember, f.mail, phone));
        row(general, 4, new JLabel(t.volume()), InfraSupport.row(6, f.slider, f.spinner));
        row(general, 5, new JLabel(t.progress()), f.progress);
        for (Component c : List.of(f.remember, f.mail, phone, f.slider)) {
            ((JComponent) c).setOpaque(false);
        }
        f.tabs = new JTabbedPane();
        f.tabs.addTab(t.general(), general);
        f.tabs.addTab(t.advanced(), new JPanel());
        f.tabs.addTab(t.about(), new JPanel());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(t.documents());
        DefaultMutableTreeNode reports = new DefaultMutableTreeNode(t.reports());
        reports.add(new DefaultMutableTreeNode("2026"));
        root.add(reports);
        root.add(new DefaultMutableTreeNode(t.invoices()));
        DefaultMutableTreeNode pictures = new DefaultMutableTreeNode(t.pictures());
        pictures.add(new DefaultMutableTreeNode("1"));
        root.add(pictures);
        root.add(new DefaultMutableTreeNode(t.music()));
        f.tree = new JTree(root);
        f.tree.setSelectionRow(2);
        JScrollPane treeScroll = new JScrollPane(f.tree);

        DefaultTableModel model = new DefaultTableModel(new Object[] { t.product(), t.quantity(), t.price() }, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? String.class : Number.class;
            }
        };
        for (int i = 0; i < t.products().length; i++) {
            model.addRow(new Object[] { t.products()[i], (i + 1) * 3, 4.5 + i * 2.25 });
        }
        f.table = new JTable(model);
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        NumberFormat integer = NumberFormat.getIntegerInstance(locale);
        f.table.setDefaultRenderer(Number.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setHorizontalAlignment(SwingConstants.TRAILING);
                setText(value instanceof Integer ? integer.format(value) : number.format(value));
            }
        });
        f.tableScroll = new JScrollPane(f.table);
        f.split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, f.tableScroll);
        f.split.setDividerLocation(160);
        f.split.setPreferredSize(new Dimension(FORM_W - 16, 130));

        f.area = new JTextArea(t.sentence(), 2, 30);
        f.area.setLineWrap(true);
        f.area.setWrapStyleWord(true);
        f.ok = new JButton(t.ok());
        f.cancel = new JButton(t.cancel());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        buttons.setOpaque(false);
        buttons.add(f.ok);
        buttons.add(f.cancel);

        JPanel north = new JPanel(new BorderLayout());
        north.add(f.menuBar, BorderLayout.NORTH);
        north.add(f.toolBar, BorderLayout.CENTER);
        JPanel center = InfraSupport.column(8, f.tabs, f.split, new JScrollPane(f.area), buttons);
        center.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 8));
        f.root = new JPanel(new BorderLayout(0, 6)) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(FORM_W, super.getPreferredSize().height);
            }
        };
        f.root.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(new Color(0x90A4AE)),
                javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0)));
        f.root.add(north, BorderLayout.NORTH);
        f.root.add(center, BorderLayout.CENTER);
        f.tabs.setPreferredSize(new Dimension(FORM_W - 16, 232));
        if (rightToLeft) {
            f.root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            f.root.setLocale(locale);
        }
        return f;
    }

    private static DateFormat utc(DateFormat format) {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

    private static void row(JPanel panel, int row, Component label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        panel.add(field, c);
    }

    // ------------------------------------------------------------------------------------------------ strings

    private static JComponent stringsTable() {
        Locale[] locales = uiLocales();
        Object[][] rows = new Object[KEYS.length][locales.length + 1];
        List<Check> checks = new ArrayList<>();
        int matching = 0;
        for (int k = 0; k < KEYS.length; k++) {
            rows[k][0] = KEYS[k];
            for (int l = 0; l < locales.length; l++) {
                String value = UIManager.getString(KEYS[k], locales[l]);
                rows[k][l + 1] = value;
                String name = KEYS[k] + " [" + (locales[l] == Locale.ROOT ? "root" : locales[l]) + "]";
                Check check = Checks.expect(name, EXPECTED[k][l], () -> value);
                checks.add(check);
                if (Boolean.TRUE.equals(check.ok())) {
                    matching++;
                }
            }
        }
        JTable table = new JTable(rows, new Object[] { "UIManager key", "root (en)", "de", "ja", "zh_CN" });
        table.getColumnModel().getColumn(0).setPreferredWidth(270);
        for (int c = 1; c < 5; c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(187);
        }
        table.setRowHeight(18);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(1020, table.getRowHeight() * KEYS.length + 24));
        // every value is checked (reported in report.json), the table shows them
        Checks.attach(scroll, checks);
        int total = KEYS.length * locales.length;
        int ok = matching;
        return InfraSupport.column(6, scroll, ChecksView.table(null, List.of(Checks.expect(
                "UIManager.getString(key, locale) matching the JDK bundles", total + "/" + total,
                () -> ok + "/" + total))));
    }

    // ------------------------------------------------------------------------------------------------ checks

    private static String orientation(ComponentOrientation o) {
        return o.isLeftToRight() ? "LTR" : "RTL";
    }

    private static List<Check> textChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("ComponentOrientation.getOrientation(ar fa he iw ur yi | en de ja zh)",
                "RTL RTL RTL RTL RTL RTL | LTR LTR LTR LTR", () -> {
                    String rtl = List.of("ar", "fa", "he", "iw", "ur", "yi").stream()
                            .map(l -> orientation(ComponentOrientation.getOrientation(Locale.forLanguageTag(l))))
                            .collect(Collectors.joining(" "));
                    String ltr = List.of("en", "de", "ja", "zh").stream()
                            .map(l -> orientation(ComponentOrientation.getOrientation(Locale.forLanguageTag(l))))
                            .collect(Collectors.joining(" "));
                    return rtl + " | " + ltr;
                }));
        String mixed = "abc ابت 123 def";
        checks.add(Checks.expect("Bidi(\"abc ابت 123 def\") : runs (start-limit level)",
                "0-4 0, 4-8 1, 8-11 2, 11-15 0",
                () -> {
                    Bidi bidi = new Bidi(mixed, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
                    List<String> runs = new ArrayList<>();
                    for (int i = 0; i < bidi.getRunCount(); i++) {
                        runs.add(bidi.getRunStart(i) + "-" + bidi.getRunLimit(i) + " " + bidi.getRunLevel(i));
                    }
                    return String.join(", ", runs);
                }));
        String arabicSentence = arabic().sentence();
        checks.add(Checks.expect("Bidi(Arabic sentence) : base level, runs", "1, 5", () -> {
            Bidi bidi = new Bidi(arabicSentence, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            return bidi.getBaseLevel() + ", " + bidi.getRunCount();
        }));
        checks.add(Checks.expect("NumericShaper.getShaper(ARABIC).shape(\"123.45\")",
                "١٢٣.٤٥", () -> {
                    char[] text = "123.45".toCharArray();
                    NumericShaper.getShaper(NumericShaper.ARABIC).shape(text, 0, text.length);
                    return new String(text);
                }));
        checks.add(Checks.expect("contextual NumericShaper (ARABIC | EUROPEAN)",
                "ا ١ b 2", () -> {
                    char[] text = "ا 1 b 2".toCharArray();
                    NumericShaper.getContextualShaper(NumericShaper.ARABIC | NumericShaper.EUROPEAN)
                            .shape(text, 0, text.length);
                    return new String(text);
                }));
        Object[][] formats = {
                { Locale.US, "1,234,567.891", "September 26, 2026" },
                { Locale.GERMANY, "1.234.567,891", "26. September 2026" },
                { Locale.JAPAN, "1,234,567.891", "2026年9月26日" },
                { Locale.SIMPLIFIED_CHINESE, "1,234,567.891", "2026年9月26日" },
                { AR, "١٬٢٣٤٬٥٦٧٫٨٩١",
                        "٢٦ سبتمبر ٢٠٢٦" },
        };
        for (Object[] f : formats) {
            Locale locale = (Locale) f[0];
            checks.add(Checks.expect("NumberFormat and long DateFormat " + locale.toLanguageTag(),
                    f[1] + " | " + f[2], () -> NumberFormat.getNumberInstance(locale).format(1234567.891) + " | "
                            + utc(DateFormat.getDateInstance(DateFormat.LONG, locale)).format(new Date(DATE))));
        }
        checks.add(Checks.expect("JComponent.getDefaultLocale()", "en_US", () -> JOptionPane.getDefaultLocale()));
        checks.add(Checks.info("Arabic font family (Platforms.Families.arabic)", Platforms.Families::arabic));
        checks.add(Checks.onlyOn(Platforms.Os.WINDOWS, Checks.expect(
                "Dialog font canDisplayUpTo(Arabic sentence)", -1,
                () -> new Font(Font.DIALOG, Font.PLAIN, 12).canDisplayUpTo(arabicSentence))));
        return checks;
    }

    private static List<Check> geometryChecks(Form l, Form r) {
        List<Check> checks = new ArrayList<>();
        // the column header viewport and the header renderer pane of the table are created when the table is added to
        // a window (addNotify), after applyComponentOrientation : they keep the default orientation
        checks.add(Checks.expect("components left to right in the Arabic form",
                "JViewport in JScrollPane, CellRendererPane in JTableHeader", () -> {
                    List<String> names = new ArrayList<>();
                    InfraSupport.walk(r.root, c -> {
                        if (c.getComponentOrientation().isLeftToRight()) {
                            names.add(c.getClass().getSimpleName() + " in " + c.getParent().getClass().getSimpleName());
                        }
                    });
                    return String.join(", ", names);
                }));
        checks.add(pair("menu bar : first menu right of the second", l, r,
                f -> f.menuBar.getComponent(0).getX() > f.menuBar.getComponent(1).getX()));
        checks.add(pair("tool bar : first button right of the second", l, r,
                f -> f.toolBar.getComponent(0).getX() > f.toolBar.getComponent(1).getX()));
        checks.add(pair("tabbed pane : first tab right of the second", l, r,
                f -> f.tabs.getBoundsAt(0).x > f.tabs.getBoundsAt(1).x));
        checks.add(pair("grid bag form : label right of its field", l, r,
                f -> f.nameLabel.getX() > f.nameField.getX()));
        checks.add(pair("check box : icon right of the text", l, r, f -> iconRightOfText(f.remember)));
        checks.add(pair("radio button : icon right of the text", l, r, f -> iconRightOfText(f.mail)));
        checks.add(Checks.expect("slider : value at the left end of the track (LTR, RTL)", "0, 100",
                () -> sliderValueAtLeft(l.slider) + ", " + sliderValueAtLeft(r.slider)));
        checks.add(pair("spinner : arrow buttons left of the editor", l, r, f -> {
            Component editor = f.spinner.getEditor();
            for (Component c : f.spinner.getComponents()) {
                if (c != editor && c.getX() >= editor.getX()) {
                    return false;
                }
            }
            return true;
        }));
        checks.add(pair("progress bar : filled from the right", l, r, f -> {
            BufferedImage image = Snapshots3.print(f.progress);
            int y = image.getHeight() / 2;
            // the unfilled part is the background : compare the colors near both inner ends
            return image.getRGB(image.getWidth() - 5, y) != image.getRGB(image.getWidth() / 2 + 20, y)
                    && image.getRGB(4, y) == image.getRGB(image.getWidth() / 2 + 20, y);
        }));
        checks.add(pair("scroll pane : vertical scroll bar left of the viewport", l, r,
                f -> f.tableScroll.getVerticalScrollBar().getX() < f.tableScroll.getViewport().getX()));
        checks.add(pair("table : column 0 right of column 1", l, r,
                f -> f.table.getCellRect(0, 0, true).x > f.table.getCellRect(0, 1, true).x));
        checks.add(Checks.expect("tree : row 1, RTL x > LTR x", "true",
                () -> r.tree.getRowBounds(1).x > l.tree.getRowBounds(1).x));
        // JSplitPane.setComponentOrientation swaps the left and right components : the table is shown on the left
        checks.add(pair("split pane : table left of the tree", l, r,
                f -> f.tableScroll.getX() < f.tree.getParent().getParent().getX()));
        // JDK 25 : setLeftComponent(right) removes the component from the right slot, then from the left slot
        checks.add(Checks.info("split pane : getLeftComponent(), getRightComponent() (LTR | RTL)",
                () -> splitNames(l) + " | " + splitNames(r)));
        checks.add(pair("FlowLayout.TRAILING buttons : OK right of Cancel", l, r, f -> f.ok.getX() > f.cancel.getX()));
        checks.add(pair("text field LEADING alignment : Arabic name right aligned", l, r,
                f -> f.nameField.modelToView2D(0) != null
                        && Math.max(f.nameField.modelToView2D(0).getX(),
                                f.nameField.modelToView2D(f.nameField.getText().length()).getX())
                                > f.nameField.getWidth() / 2.0));
        checks.add(Checks.expect("Arabic text laid out right to left : x(0) > x(end)", "true", () -> {
            JTextField field = r.nameField;
            return field.modelToView2D(0).getX() > field.modelToView2D(field.getText().length()).getX();
        }));
        checks.add(Checks.expect("document property i18n (bidi text) : English, Arabic", "false, true",
                () -> l.nameField.getDocument().getProperty("i18n") + ", "
                        + r.nameField.getDocument().getProperty("i18n")));
        checks.add(Checks.expect("text area bidi root element : runs (English, Arabic)", "1, 5",
                () -> ((AbstractDocument) l.area.getDocument()).getBidiRootElement().getElementCount() + ", "
                        + ((AbstractDocument) r.area.getDocument()).getBidiRootElement().getElementCount()));
        checks.add(Checks.expect("Ocean Tree.collapsedIcon : RTL variant differs (collapsed-rtl.gif)", "true", () -> {
            Icon icon = UIManager.getIcon("Tree.collapsedIcon");
            return !Checks.sha256(paintIcon(icon, l.tree)).equals(Checks.sha256(paintIcon(icon, r.tree)));
        }));
        checks.add(Checks.info("form bounds (LTR, RTL)",
                () -> InfraSupport.bounds(l.root) + " | " + InfraSupport.bounds(r.root)));
        return checks;
    }

    private interface Predicate {
        boolean test(Form f) throws Exception;
    }

    /** Expects the relation to be false in the left to right form and true in the right to left one. */
    private static Check pair(String name, Form l, Form r, Predicate relation) {
        return Checks.expect(name + " : LTR, RTL", "false, true", () -> relation.test(l) + ", " + relation.test(r));
    }

    private static String splitNames(Form f) {
        return name(f, f.split.getLeftComponent()) + ", " + name(f, f.split.getRightComponent());
    }

    private static String name(Form f, Component c) {
        return c == null ? "null" : c == f.tableScroll ? "table" : c == f.tree.getParent().getParent() ? "tree" : "?";
    }

    private static boolean iconRightOfText(AbstractButton b) {
        Rectangle view = new Rectangle(b.getWidth(), b.getHeight());
        Rectangle icon = new Rectangle();
        Rectangle text = new Rectangle();
        Icon defaultIcon = b.getIcon() != null ? b.getIcon() : UIManager.getIcon(b instanceof JCheckBox
                ? "CheckBox.icon" : "RadioButton.icon");
        SwingUtilities.layoutCompoundLabel(b, b.getFontMetrics(b.getFont()), b.getText(), defaultIcon,
                b.getVerticalAlignment(), b.getHorizontalAlignment(), b.getVerticalTextPosition(),
                b.getHorizontalTextPosition(), view, icon, text, b.getIconTextGap());
        return icon.x > text.x;
    }

    private static int sliderValueAtLeft(JSlider slider) {
        return ((BasicSliderUI) slider.getUI()).valueForXPosition(0);
    }

    private static BufferedImage paintIcon(Icon icon, Component c) {
        BufferedImage image = new BufferedImage(Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()),
                BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            icon.paintIcon(c, g, 0, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** printAll of a showing component into an image. */
    private static final class Snapshots3 {
        static BufferedImage print(JComponent c) {
            BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            try {
                c.printAll(g);
            } finally {
                g.dispose();
            }
            return image;
        }
    }

    // ------------------------------------------------------------------------------------------------ choosers

    private static List<Check> chooserChecks() {
        List<Check> checks = new ArrayList<>();
        Locale[] locales = uiLocales();
        File dir;
        try {
            dir = Files.createDirectories(Edt.tempDir().resolve("swing-rtl-i18n-chooser")).toFile();
        } catch (Exception e) {
            checks.add(Check.fail("temporary directory", Checks.describe(e)));
            return checks;
        }
        List<String> approve = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        checks.add(Checks.run("JFileChooser(dir) created, setLocale + updateUI per locale", () -> {
            JFileChooser chooser = new JFileChooser(dir);
            for (Locale locale : locales) {
                chooser.setLocale(locale);
                chooser.updateUI();
                approve.add(chooser.getUI().getApproveButtonText(chooser));
                titles.add(chooser.getUI().getDialogTitle(chooser));
            }
            return chooser.getUI().getClass().getSimpleName();
        }));
        checks.add(Checks.expect("JFileChooser approve button text (root de ja zh_CN)",
                "Open | Öffnen | 開く | 打开", () -> String.join(" | ", approve)));
        checks.add(Checks.expect("JFileChooser dialog title (root de ja zh_CN)",
                "Open | Öffnen | 開く | 打开", () -> String.join(" | ", titles)));
        List<String> panels = new ArrayList<>();
        // every JComponent gets the default locale when it is created : the chooser panels are created with the
        // default locale of the moment (restored right after)
        checks.add(Checks.run("JColorChooser created per locale (JComponent.setDefaultLocale)", () -> {
            Locale previous = JComponent.getDefaultLocale();
            int count = 0;
            try {
                for (Locale locale : locales) {
                    JComponent.setDefaultLocale(locale);
                    JColorChooser chooser = new JColorChooser(new Color(0x1E88E5));
                    panels.add(java.util.Arrays.stream(chooser.getChooserPanels())
                            .map(AbstractColorChooserPanel::getDisplayName).collect(Collectors.joining(", ")));
                    count = chooser.getChooserPanels().length;
                }
            } finally {
                JComponent.setDefaultLocale(previous);
            }
            return count + " panels";
        }));
        String[] expected = { "Swatches, HSV, HSL, RGB, CMYK", "Swatches, HSV, HSL, RGB, CMYK",
                "サンプル(S), HSV(H), HSL(L), RGB(G), CMYK", "样本(S), HSV(H), HSL(L), RGB(G), CMYK" };
        for (int i = 0; i < locales.length; i++) {
            int index = i;
            checks.add(Checks.expect("JColorChooser panel names " + (locales[i] == Locale.ROOT ? "root" : locales[i]),
                    expected[i], () -> index < panels.size() ? panels.get(index) : "none"));
        }
        return checks;
    }
}
