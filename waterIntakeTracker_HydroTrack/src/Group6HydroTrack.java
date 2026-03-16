
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.awt.Desktop;

class WaterIntake {

    private LocalDate date;
    private double amountLiters;
    private String note;

    static final double CUPS_PER_LITER = 4.22675;

    private static final DecimalFormat DF
            = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));

    public WaterIntake(LocalDate date, double amountLiters, String note) {
        this.date = date;
        this.amountLiters = amountLiters;
        this.note = (note != null) ? note.trim() : "";
    }

    public LocalDate getDate() {
        return date;
    }

    public double getAmountLiters() {
        return amountLiters;
    }

    public String getNote() {
        return note;
    }

    public void setAmountLiters(double v) {
        this.amountLiters = v;
    }

    public void setNote(String n) {
        this.note = (n != null) ? n.trim() : "";
    }

    public String getAmountDisplay() {
        double cups = amountLiters * CUPS_PER_LITER;
        return DF.format(cups) + " Glasses  (" + DF.format(amountLiters * 1000) + " mL)";
    }

    public Object[] toRow() {
        return new Object[]{
            date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
            getAmountDisplay(),
            note.isEmpty() ? "\u2014" : note
        };
    }

    public String toCSV() {
        return date + "," + amountLiters + "," + note.replace(",", ";");
    }

    public static WaterIntake fromCSV(String line) throws Exception {
        String[] parts = line.split(",", 3);
        if (parts.length < 2) {
            throw new Exception("Malformed CSV line: " + line);
        }
        LocalDate d = LocalDate.parse(parts[0].trim());
        double l = Double.parseDouble(parts[1].trim());
        String n = (parts.length == 3) ? parts[2].trim() : "";
        return new WaterIntake(d, l, n);
    }
}

class PasswordUtil {

    public static String hash(String plainText) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(plainText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return plainText;
        }
    }
}

class BMIUtil {

    public static double calcBMI(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) {
            return 0;
        }
        double hm = heightCm / 100.0;
        return weightKg / (hm * hm);
    }

    public static double waterGoalFromBMI(double bmi) {
        if (bmi <= 0) {
            return 2.5;
        }
        if (bmi < 18.5) {
            return 2.0;
        }
        if (bmi < 25.0) {
            return 2.5;
        }
        if (bmi < 30.0) {
            return 3.0;
        }
        return 3.5;
    }

    public static String bmiCategory(double bmi) {
        if (bmi <= 0) {
            return "Unknown";
        }
        if (bmi < 18.5) {
            return "Underweight";
        }
        if (bmi < 25.0) {
            return "Normal weight";
        }
        if (bmi < 30.0) {
            return "Overweight";
        }
        return "Obese";
    }
}

class UserManager {

    static final String USER_FILE = "users.txt";
    static final int MAX_USERNAME = 30;
    static final int MAX_PASSWORD = 100;

    public static void ensureFileExists() {
        try {
            new File(USER_FILE).createNewFile();
        } catch (IOException ex) {
            System.err.println("[WARN] " + ex.getMessage());
        }
    }

    public static boolean validateLogin(String username, String password) {
        String hashed = PasswordUtil.hash(password);
        try (BufferedReader r = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", 7);
                if (p.length >= 2
                        && p[0].trim().equals(username)
                        && p[1].trim().equals(hashed)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    public static boolean saveUser(String username, String password,
            String email, double heightCm, double weightKg,
            String gender) {
        try (BufferedReader r = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith(username + ",")) {
                    return false;
                }
            }
        } catch (IOException ignored) {
        }

        try (BufferedWriter w = new BufferedWriter(new FileWriter(USER_FILE, true))) {
            w.write(username + "," + PasswordUtil.hash(password) + ","
                    + email + "," + heightCm + "," + weightKg + "," + gender + "\n");
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static double[] getHeightWeight(String username) {
        try (BufferedReader r = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", 7);
                if (p.length >= 5 && p[0].trim().equals(username)) {
                    try {
                        return new double[]{Double.parseDouble(p[3].trim()),
                            Double.parseDouble(p[4].trim())};
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return new double[]{0, 0};
    }

    public static double getUserGoal(String username) {
        double[] hw = getHeightWeight(username);
        double bmi = BMIUtil.calcBMI(hw[0], hw[1]);
        return BMIUtil.waterGoalFromBMI(bmi);
    }

    public static boolean resetPassword(String username, String newPassword) {
        File file = new File(USER_FILE);
        ArrayList<String> lines = new ArrayList<>();
        boolean found = false;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith(username + ",")) {
                    String[] p = line.split(",", 7);
                    String email = (p.length >= 3) ? p[2] : "";
                    String height = (p.length >= 4) ? p[3] : "0";
                    String weight = (p.length >= 5) ? p[4] : "0";
                    String gender = (p.length >= 6) ? p[5] : "";
                    lines.add(username + "," + PasswordUtil.hash(newPassword)
                            + "," + email + "," + height + "," + weight + "," + gender);
                    found = true;
                } else {
                    lines.add(line);
                }
            }
        } catch (IOException ex) {
            return false;
        }
        if (!found) {
            return false;
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, false))) {
            for (String l : lines) {
                w.write(l + "\n");
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static String getEmail(String username) {
        try (BufferedReader r = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", 7);
                if (p.length >= 3 && p[0].trim().equals(username)) {
                    return p[2].trim();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static String getGender(String username) {
        try (BufferedReader r = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", 7);
                if (p.length >= 6 && p[0].trim().equals(username)) {
                    return p[5].trim();
                }
            }
        } catch (IOException ignored) {
        }
        return "-";
    }

    public static ArrayList<String[]> getAllUsers() {
        ArrayList<String[]> users = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] p = line.split(",", 7);
                String uname = p[0].trim();
                String email = (p.length >= 3) ? p[2].trim() : "-";
                String height = (p.length >= 4) ? p[3].trim() : "-";
                String weight = (p.length >= 5) ? p[4].trim() : "-";
                String gender = (p.length >= 6) ? p[5].trim() : "-";
                if (email.isEmpty()) {
                    email = "-";
                }
                if (gender.isEmpty()) {
                    gender = "-";
                }
                users.add(new String[]{uname, email, gender, height, weight});
            }
        } catch (IOException ignored) {
        }
        return users;
    }

    public static boolean deleteUser(String username) {
        File file = new File(USER_FILE);
        ArrayList<String> remaining = new ArrayList<>();
        boolean found = false;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith(username + ",")) {
                    found = true;
                } else {
                    remaining.add(line);
                }
            }
        } catch (IOException ex) {
            return false;
        }
        if (!found) {
            return false;
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, false))) {
            for (String l : remaining) {
                w.write(l + "\n");
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}

class IntakeFileManager {

    public static String buildFileName(String username) {
        return "water_intake_" + username + ".csv";
    }

    public static String buildLastDateFileName(String username) {
        return "hydrotrack_lastdate_" + username + ".txt";
    }

    public static LocalDate loadLastDate(String username) {
        File f = new File(buildLastDateFileName(username));
        if (!f.exists()) {
            return null;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return LocalDate.parse(line.trim());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void saveLastDate(String username) {
        try (BufferedWriter w = new BufferedWriter(
                new FileWriter(buildLastDateFileName(username)))) {
            w.write(LocalDate.now().toString());
        } catch (IOException ex) {
            System.err.println("[WARN] " + ex.getMessage());
        }
    }

    public static String saveIntakes(String username, ArrayList<WaterIntake> intakes)
            throws IOException {
        String fname = buildFileName(username);
        String bakFname = fname + ".bak";
        try (BufferedWriter w = new BufferedWriter(new FileWriter(bakFname))) {
            writeCSV(w, username, intakes);
        }
        File target = new File(fname);
        if (target.exists() && !target.canWrite()) {
            throw new IOException("No write permission for: " + fname);
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(fname))) {
            writeCSV(w, username, intakes);
        }
        return fname;
    }

    private static void writeCSV(BufferedWriter w, String username,
            ArrayList<WaterIntake> intakes) throws IOException {
        w.write("Daily Water Intake for: " + username + "\n");
        w.write("Date,AmountLiters,Note\n");
        for (WaterIntake e : intakes) {
            w.write(e.toCSV() + "\n");
        }
    }

    public static int loadIntakes(String username, ArrayList<WaterIntake> intakes) {
        File file = new File(buildFileName(username));
        if (!file.exists()) {
            return 0;
        }
        int failures = 0;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first || line.startsWith("Date") || line.startsWith("Daily")) {
                    first = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    intakes.add(WaterIntake.fromCSV(line));
                } catch (Exception ex) {
                    failures++;
                }
            }
        } catch (IOException ex) {
            System.err.println("[ERROR] " + ex.getMessage());
        }
        return failures;
    }
}

class UITheme {

    static final Color BG_DARK = new Color(18, 28, 51);
    static final Color BG_CARD = new Color(28, 42, 74);
    static final Color BG_TABLE = new Color(22, 34, 60);
    static final Color ACCENT_BLUE = new Color(56, 189, 248);
    static final Color ACCENT_TEAL = new Color(45, 212, 191);
    static final Color ACCENT_PURPLE = new Color(167, 139, 250);
    static final Color ACCENT_YELLOW = new Color(250, 204, 21);
    static final Color ACCENT_GREEN = new Color(34, 197, 94);
    static final Color ACCENT_ORANGE = new Color(251, 146, 60);
    static final Color ACCENT_RED = new Color(239, 68, 68);
    static final Color TEXT_PRIMARY = new Color(226, 232, 240);
    static final Color TEXT_SECONDARY = new Color(148, 163, 184);
    static final Color ROW_EVEN = new Color(25, 38, 66);
    static final Color ROW_ODD = new Color(30, 45, 78);
    static final Color ROW_SELECT = new Color(56, 100, 160);
    static final Color BORDER_COLOR = new Color(51, 65, 85);

    static final Color BTN_ADD = new Color(34, 197, 94);
    static final Color BTN_EDIT = new Color(59, 130, 246);
    static final Color BTN_DELETE = new Color(239, 68, 68);
    static final Color BTN_SAVE = new Color(168, 85, 247);
    static final Color BTN_VIEW = new Color(14, 165, 233);
    static final Color BTN_SEARCH = new Color(234, 179, 8);
    static final Color BTN_EXIT = new Color(100, 116, 139);
    static final Color BTN_RESET = new Color(249, 115, 22);
    static final Color BTN_SUMMARY = new Color(20, 184, 166);
    static final Color BTN_DEL_ACC = new Color(185, 28, 28);

    static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 30);
    static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 14);
    static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
    static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_CARD_TITLE = new Font("Segoe UI", Font.BOLD, 12);
    static final Font FONT_CARD_VALUE = new Font("Segoe UI", Font.BOLD, 26);
    static final Font FONT_LABEL = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_INPUT = new Font("Segoe UI", Font.PLAIN, 14);
    static final Font FONT_BTN = new Font("Segoe UI", Font.BOLD, 13);
    static final Font FONT_STATUS = new Font("Segoe UI", Font.PLAIN, 12);

    static final DecimalFormat DF
            = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));

    static JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = getModel().isPressed() ? bg.darker()
                        : getModel().isRollover() ? bg.brighter() : bg;
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }

            @Override
            protected void paintBorder(Graphics g) {
            }
        };
        btn.setFont(FONT_BTN);
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(145, 38));
        return btn;
    }

    static void styleField(JTextField f) {
        f.setBackground(new Color(38, 54, 90));
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_BLUE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        f.setFont(FONT_INPUT);
    }

    static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_SECONDARY);
        l.setFont(FONT_LABEL);
        return l;
    }

    static void setMaxLength(JTextField field, int maxChars) {
        ((javax.swing.text.AbstractDocument) field.getDocument())
                .setDocumentFilter(new javax.swing.text.DocumentFilter() {
                    @Override
                    public void insertString(FilterBypass fb, int off, String str,
                            javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        if (fb.getDocument().getLength() + str.length() <= maxChars) {
                            super.insertString(fb, off, str, a);
                        }
                    }

                    @Override
                    public void replace(FilterBypass fb, int off, int len, String str,
                            javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        int n = fb.getDocument().getLength() - len + (str == null ? 0 : str.length());
                        if (n <= maxChars) {
                            super.replace(fb, off, len, str, a);
                        }
                    }
                });
    }
}

class CalendarPicker extends JDialog {

    private LocalDate selected;
    private LocalDate viewing;
    private JPanel calPanel;
    private JLabel monthLbl;

    public CalendarPicker(Component parent, LocalDate initial) {
        super(SwingUtilities.getWindowAncestor(parent) instanceof JFrame
                ? (JFrame) SwingUtilities.getWindowAncestor(parent) : null,
                "Pick a Date", true);
        selected = (initial != null) ? initial : LocalDate.now();
        viewing = selected.withDayOfMonth(1);
        setUndecorated(false);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(UITheme.BG_CARD);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        setContentPane(root);

        JButton prev = makeNavBtn("\u2039");
        JButton next = makeNavBtn("\u203A");

        monthLbl = new JLabel("", SwingConstants.CENTER);
        monthLbl.setForeground(UITheme.ACCENT_BLUE);
        monthLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));

        JPanel nav = new JPanel(new BorderLayout(4, 0));
        nav.setOpaque(false);
        nav.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        nav.add(prev, BorderLayout.WEST);
        nav.add(monthLbl, BorderLayout.CENTER);
        nav.add(next, BorderLayout.EAST);
        root.add(nav, BorderLayout.NORTH);

        calPanel = new JPanel();
        calPanel.setBackground(UITheme.BG_CARD);
        root.add(calPanel, BorderLayout.CENTER);

        JButton todayBtn = UITheme.makeButton("Today", UITheme.BTN_EDIT);
        JButton clearBtn = UITheme.makeButton("Clear", UITheme.BTN_EXIT);
        todayBtn.setPreferredSize(new Dimension(100, 32));
        clearBtn.setPreferredSize(new Dimension(90, 32));

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        bot.setOpaque(false);
        bot.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER_COLOR));
        bot.add(todayBtn);
        bot.add(clearBtn);
        root.add(bot, BorderLayout.SOUTH);

        prev.addActionListener(e -> {
            viewing = viewing.minusMonths(1);
            rebuildCal();
        });
        next.addActionListener(e -> {
            viewing = viewing.plusMonths(1);
            rebuildCal();
        });
        todayBtn.addActionListener(e -> {
            selected = LocalDate.now();
            dispose();
        });
        clearBtn.addActionListener(e -> {
            selected = null;
            dispose();
        });

        rebuildCal();
    }

    private void rebuildCal() {
        monthLbl.setText(viewing.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        calPanel.removeAll();
        calPanel.setLayout(new GridLayout(0, 7, 4, 4));

        String[] dayNames = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        for (int i = 0; i < 7; i++) {
            JLabel l = new JLabel(dayNames[i], SwingConstants.CENTER);
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            l.setForeground(i == 0 || i == 6 ? UITheme.ACCENT_ORANGE : UITheme.ACCENT_TEAL);
            l.setPreferredSize(new Dimension(42, 24));
            calPanel.add(l);
        }

        LocalDate today = LocalDate.now();
        int startDow = viewing.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < startDow; i++) {
            JLabel blank = new JLabel("");
            blank.setPreferredSize(new Dimension(42, 36));
            calPanel.add(blank);
        }

        for (int day = 1; day <= viewing.lengthOfMonth(); day++) {
            LocalDate d = viewing.withDayOfMonth(day);
            boolean isSel = d.equals(selected);
            boolean isToday = d.equals(today);
            int col = (startDow + day - 1) % 7;
            boolean isWeekend = (col == 0 || col == 6);

            JButton btn = new JButton(String.valueOf(day)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    int arc = Math.min(w, h) - 2;

                    if (isSel) {
                        g2.setColor(UITheme.ACCENT_BLUE);
                        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);
                    } else if (isToday) {
                        g2.setColor(new Color(45, 212, 191, 60));
                        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);
                        g2.setColor(UITheme.ACCENT_TEAL);
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
                    } else {
                        g2.setColor(getModel().isRollover()
                                ? new Color(56, 189, 248, 40) : new Color(0, 0, 0, 0));
                        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);
                    }

                    Color textColor = isSel ? Color.WHITE
                            : isToday ? UITheme.ACCENT_TEAL
                                    : isWeekend ? UITheme.ACCENT_ORANGE
                                            : UITheme.TEXT_PRIMARY;
                    g2.setColor(textColor);
                    g2.setFont(isSel || isToday
                            ? new Font("Segoe UI", Font.BOLD, 13)
                            : new Font("Segoe UI", Font.PLAIN, 13));
                    FontMetrics fm = g2.getFontMetrics();
                    String txt = getText();
                    g2.drawString(txt,
                            (w - fm.stringWidth(txt)) / 2,
                            (h + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }

                @Override
                protected void paintBorder(Graphics g) {
                }
            };

            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setOpaque(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(42, 36));
            btn.setToolTipText(d.format(DateTimeFormatter.ofPattern("EEEE, MMM d yyyy")));
            btn.addActionListener(e -> {
                selected = d;
                dispose();
            });
            calPanel.add(btn);
        }

        calPanel.revalidate();
        calPanel.repaint();
        pack();
    }

    private JButton makeNavBtn(String label) {
        JButton b = new JButton(label);
        b.setFont(new Font("Segoe UI", Font.BOLD, 18));
        b.setForeground(UITheme.ACCENT_BLUE);
        b.setBackground(UITheme.BG_TABLE);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(38, 30));
        return b;
    }

    public static LocalDate show(Component parent, LocalDate current) {
        CalendarPicker cp = new CalendarPicker(parent, current);
        cp.setVisible(true);
        return cp.selected;
    }
}

class LoginScreen extends JFrame {

    public LoginScreen() {
        super("HydroTrack \u2014 Login");
        UserManager.ensureFileExists();
        setSize(440, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, UITheme.BG_DARK,
                        getWidth(), getHeight(), new Color(30, 20, 70)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(20, 44, 20, 44));
        setContentPane(root);

        JLabel title = new JLabel("\ud83d\udca7 HydroTrack", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(UITheme.ACCENT_BLUE);
        JLabel sub = new JLabel("Daily Water Intake Tracker", SwingConstants.CENTER);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sub.setForeground(UITheme.TEXT_SECONDARY);
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        titlePanel.add(title);
        titlePanel.add(sub);
        root.add(titlePanel, BorderLayout.NORTH);

        JTextField userField = new JTextField(18);
        JPasswordField passField = new JPasswordField(18);
        UITheme.setMaxLength(userField, UserManager.MAX_USERNAME);
        UITheme.setMaxLength(passField, UserManager.MAX_PASSWORD);
        UITheme.styleField(userField);
        UITheme.styleField(passField);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        form.add(UITheme.makeLabel("Username:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(userField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        form.add(UITheme.makeLabel("Password:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(passField, gbc);
        root.add(form, BorderLayout.CENTER);

        JButton loginBtn = UITheme.makeButton("Login", UITheme.BTN_ADD);
        JButton regBtn = UITheme.makeButton("Register", UITheme.BTN_EDIT);
        JButton forgotBtn = UITheme.makeButton("Forgot Password", UITheme.BTN_RESET);
        forgotBtn.setPreferredSize(new Dimension(160, 36));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        btnRow.setOpaque(false);
        btnRow.add(loginBtn);
        btnRow.add(regBtn);
        JPanel forgotRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        forgotRow.setOpaque(false);
        forgotRow.add(forgotBtn);

        JLabel hint = new JLabel(
                "<html><center><i>New here? Click <b>Register</b> to create your account.</i></center></html>",
                SwingConstants.CENTER);
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(UITheme.ACCENT_TEAL);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        hint.setVisible(new File(UserManager.USER_FILE).length() == 0);

        JPanel southPanel = new JPanel(new BorderLayout(0, 2));
        southPanel.setOpaque(false);
        southPanel.add(btnRow, BorderLayout.NORTH);
        southPanel.add(forgotRow, BorderLayout.CENTER);
        southPanel.add(hint, BorderLayout.SOUTH);
        root.add(southPanel, BorderLayout.SOUTH);

        ActionListener doLogin = e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            if (user.isEmpty() || pass.isEmpty()) {
                showError(this, "Please enter both username and password.");
                return;
            }
            if (UserManager.validateLogin(user, pass)) {
                dispose();
                JOptionPane.showMessageDialog(null,
                        "Welcome back, " + user + "!  \ud83d\udca7",
                        "Login Successful", JOptionPane.INFORMATION_MESSAGE);
                new MainTrackerWindow(user);
            } else {
                showError(this, "Invalid username or password.");
                passField.setText("");
            }
        };

        loginBtn.addActionListener(doLogin);
        passField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    loginBtn.doClick();
                }
            }
        });
        regBtn.addActionListener(e -> new RegisterScreen(this).setVisible(true));
        forgotBtn.addActionListener(e -> new ForgotPasswordScreen(this).setVisible(true));
        setVisible(true);
    }

    static void showError(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}

class RegisterScreen extends JDialog {

    private String selectedGender = "";
    private JPanel maleCard, femaleCard;

    public RegisterScreen(JFrame parent) {
        super(parent, "Create Account", true);
        setSize(500, 560);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, UITheme.BG_DARK,
                        getWidth(), getHeight(), new Color(20, 40, 80)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(16, 40, 16, 40));
        setContentPane(root);

        JLabel title = new JLabel("Create Account", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(UITheme.ACCENT_TEAL);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        root.add(title, BorderLayout.NORTH);

        JTextField userField = new JTextField(18);
        JPasswordField passField = new JPasswordField(18);
        JPasswordField confirmField = new JPasswordField(18);

        JTextField heightField = makeNumericField();
        JTextField weightField = makeNumericField();

        UITheme.setMaxLength(userField, UserManager.MAX_USERNAME);
        UITheme.setMaxLength(passField, UserManager.MAX_PASSWORD);
        UITheme.setMaxLength(confirmField, UserManager.MAX_PASSWORD);

        UITheme.styleField(userField);
        UITheme.styleField(passField);
        UITheme.styleField(confirmField);

        heightField.setToolTipText("Height in cm, e.g. 165");
        weightField.setToolTipText("Weight in kg, e.g. 60.5");

        JLabel bmiHint = new JLabel(" ", SwingConstants.CENTER);
        bmiHint.setFont(UITheme.FONT_SMALL);
        bmiHint.setForeground(UITheme.ACCENT_TEAL);

        KeyAdapter bmiUpdater = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateBmiHint(bmiHint, heightField, weightField);
            }
        };
        heightField.addKeyListener(bmiUpdater);
        weightField.addKeyListener(bmiUpdater);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        String[] lbls = {"Username:", "Password:", "Confirm Password:"};
        Component[] comps = {userField, passField, confirmField};
        for (int i = 0; i < lbls.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            form.add(UITheme.makeLabel(lbls[i]), gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            form.add(comps[i], gbc);
        }

        JPanel hwPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        hwPanel.setOpaque(false);
        hwPanel.add(makeHWCell("Height (cm)", heightField));
        hwPanel.add(makeHWCell("Weight (kg)", weightField));

        gbc.gridx = 0;
        gbc.gridy = lbls.length;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.insets = new Insets(10, 4, 4, 4);
        form.add(hwPanel, gbc);

        maleCard = makeGenderCard("Male", false);
        femaleCard = makeGenderCard("Female", false);
        maleCard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectGender("Male");
            }
        });
        femaleCard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectGender("Female");
            }
        });
        JPanel genderRow = new JPanel(new GridLayout(1, 2, 10, 0));
        genderRow.setOpaque(false);
        genderRow.add(maleCard);
        genderRow.add(femaleCard);

        gbc.gridx = 0;
        gbc.gridy = lbls.length + 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.insets = new Insets(10, 4, 4, 4);
        form.add(genderRow, gbc);

        gbc.gridy = lbls.length + 2;
        gbc.insets = new Insets(4, 4, 4, 4);
        form.add(bmiHint, gbc);
        gbc.gridwidth = 1;

        root.add(form, BorderLayout.CENTER);

        JButton regBtn = UITheme.makeButton("Register", UITheme.BTN_EDIT);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        btnRow.setOpaque(false);
        btnRow.add(regBtn);
        root.add(btnRow, BorderLayout.SOUTH);

        regBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            String confirm = new String(confirmField.getPassword()).trim();

            if (user.isEmpty() || pass.isEmpty()) {
                LoginScreen.showError(this, "Username and password are required.");
                return;
            }
            if (user.contains(",") || pass.contains(",")) {
                LoginScreen.showError(this, "Username and password must not contain commas.");
                return;
            }
            if (!pass.equals(confirm)) {
                LoginScreen.showError(this, "Passwords do not match.");
                return;
            }
            if (selectedGender.isEmpty()) {
                LoginScreen.showError(this, "Please select Male or Female.");
                return;
            }

            double h = 0, w = 0;
            try {
                h = Double.parseDouble(heightField.getText().trim());
            } catch (NumberFormatException ex) {
                LoginScreen.showError(this, "Enter a valid height in cm (e.g. 165).");
                return;
            }
            try {
                w = Double.parseDouble(weightField.getText().trim());
            } catch (NumberFormatException ex) {
                LoginScreen.showError(this, "Enter a valid weight in kg (e.g. 60).");
                return;
            }
            if (h <= 0 || w <= 0) {
                LoginScreen.showError(this, "Height and weight must be greater than zero.");
                return;
            }

            if (UserManager.saveUser(user, pass, "", h, w, selectedGender)) {
                double bmi = BMIUtil.calcBMI(h, w);
                double goal = BMIUtil.waterGoalFromBMI(bmi);
                JOptionPane.showMessageDialog(this,
                        "Registration successful!\n\n"
                        + "Your BMI: " + UITheme.DF.format(bmi)
                        + " (" + BMIUtil.bmiCategory(bmi) + ")\n"
                        + "Recommended daily water intake: " + UITheme.DF.format(goal) + " L\n\n"
                        + "You can now log in.",
                        "Account Created", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                LoginScreen.showError(this, "Username already exists. Please choose another.");
            }
        });
    }

    private JPanel makeGenderCard(String label, boolean active) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(active ? UITheme.ACCENT_BLUE : UITheme.BG_TABLE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        active ? UITheme.ACCENT_BLUE : UITheme.BORDER_COLOR, 2, true),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel lbl = new JLabel(label, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(active ? Color.WHITE : UITheme.TEXT_PRIMARY);
        card.add(lbl, BorderLayout.CENTER);
        card.putClientProperty("label", lbl);
        return card;
    }

    private void selectGender(String gender) {
        selectedGender = gender;
        styleCard(maleCard, "Male".equals(gender));
        styleCard(femaleCard, "Female".equals(gender));
    }

    private void styleCard(JPanel card, boolean active) {
        card.setBackground(active ? UITheme.ACCENT_BLUE : UITheme.BG_TABLE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        active ? UITheme.ACCENT_BLUE : UITheme.BORDER_COLOR, 2, true),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));
        JLabel lbl = (JLabel) card.getClientProperty("label");
        if (lbl != null) {
            lbl.setForeground(active ? Color.WHITE : UITheme.TEXT_PRIMARY);
        }
        card.repaint();
    }

    private void updateBmiHint(JLabel hint, JTextField hf, JTextField wf) {
        try {
            double h = Double.parseDouble(hf.getText().trim());
            double w = Double.parseDouble(wf.getText().trim());
            if (h > 0 && w > 0) {
                double bmi = BMIUtil.calcBMI(h, w);
                double goal = BMIUtil.waterGoalFromBMI(bmi);
                hint.setText("BMI: " + UITheme.DF.format(bmi)
                        + " (" + BMIUtil.bmiCategory(bmi) + ")  \u2192  Goal: "
                        + UITheme.DF.format(goal) + " L / day");
            } else {
                hint.setText(" ");
            }
        } catch (NumberFormatException ex) {
            hint.setText(" ");
        }
    }

    private JPanel makeHWCell(String labelText, JTextField field) {
        JPanel cell = new JPanel(new BorderLayout(0, 6));
        cell.setBackground(UITheme.BG_TABLE);
        cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 2, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JLabel lbl = new JLabel(labelText, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(UITheme.TEXT_SECONDARY);

        JPanel fieldWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        fieldWrapper.setOpaque(false);
        fieldWrapper.add(field);

        cell.add(lbl, BorderLayout.NORTH);
        cell.add(fieldWrapper, BorderLayout.CENTER);
        return cell;
    }

    private JTextField makeNumericField() {
        JTextField f = new JTextField() {
            private final Dimension SZ = new Dimension(72, 30);

            @Override
            public Dimension getPreferredSize() {
                return SZ;
            }

            @Override
            public Dimension getMaximumSize() {
                return SZ;
            }

            @Override
            public Dimension getMinimumSize() {
                return SZ;
            }
        };

        f.setBackground(new Color(38, 54, 90));
        f.setForeground(UITheme.TEXT_PRIMARY);
        f.setCaretColor(UITheme.ACCENT_BLUE);
        f.setFont(new Font("Segoe UI", Font.BOLD, 15));
        f.setHorizontalAlignment(SwingConstants.CENTER);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        ((javax.swing.text.AbstractDocument) f.getDocument())
                .setDocumentFilter(new javax.swing.text.DocumentFilter() {
                    @Override
                    public void insertString(FilterBypass fb, int off, String str,
                            javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        if (str != null) {
                            String result = fb.getDocument()
                                    .getText(0, fb.getDocument().getLength()) + str;
                            if (valid(result)) {
                                super.insertString(fb, off, str, a);
                            }
                        }
                    }

                    @Override
                    public void replace(FilterBypass fb, int off, int len, String str,
                            javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
                        String next = cur.substring(0, off)
                                + (str == null ? "" : str)
                                + cur.substring(off + len);
                        if (next.isEmpty() || valid(next)) {
                            super.replace(fb, off, len, str, a);
                        }
                    }

                    private boolean valid(String s) {
                        return s.matches("\\d{0,5}(\\.\\d{0,2})?");
                    }
                });
        return f;
    }
}

class ForgotPasswordScreen extends JDialog {

    public ForgotPasswordScreen(JFrame parent) {
        super(parent, "Reset Password", true);
        setSize(440, 320);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, UITheme.BG_DARK,
                        getWidth(), getHeight(), new Color(40, 20, 60)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(20, 44, 20, 44));
        setContentPane(root);

        JLabel title = new JLabel("Reset Password", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(UITheme.BTN_RESET);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        root.add(title, BorderLayout.NORTH);

        JTextField userField = new JTextField(18);
        JTextField emailField = new JTextField(18);
        JPasswordField newPass = new JPasswordField(18);
        JPasswordField confPass = new JPasswordField(18);
        UITheme.setMaxLength(userField, UserManager.MAX_USERNAME);
        UITheme.setMaxLength(newPass, UserManager.MAX_PASSWORD);
        UITheme.setMaxLength(confPass, UserManager.MAX_PASSWORD);
        UITheme.styleField(userField);
        UITheme.styleField(emailField);
        UITheme.styleField(newPass);
        UITheme.styleField(confPass);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        String[] lbls = {"Username:", "Registered Email:", "New Password:", "Confirm Password:"};
        JTextField[] flds = {userField, emailField, newPass, confPass};
        for (int i = 0; i < lbls.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            form.add(UITheme.makeLabel(lbls[i]), gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            form.add(flds[i], gbc);
        }
        root.add(form, BorderLayout.CENTER);

        JButton resetBtn = UITheme.makeButton("Reset Password", UITheme.BTN_RESET);
        resetBtn.setPreferredSize(new Dimension(160, 38));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        btnRow.setOpaque(false);
        btnRow.add(resetBtn);
        root.add(btnRow, BorderLayout.SOUTH);

        resetBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String email = emailField.getText().trim();
            String np = new String(newPass.getPassword()).trim();
            String cp = new String(confPass.getPassword()).trim();
            if (user.isEmpty() || email.isEmpty() || np.isEmpty()) {
                LoginScreen.showError(this, "All fields are required.");
                return;
            }
            if (!np.equals(cp)) {
                LoginScreen.showError(this, "New passwords do not match.");
                return;
            }
            String stored = UserManager.getEmail(user);
            if (stored == null) {
                LoginScreen.showError(this, "Username not found.");
                return;
            }
            if (stored.isEmpty()) {
                LoginScreen.showError(this,
                        "No email registered.\nPassword reset unavailable.");
                return;
            }
            if (!stored.equalsIgnoreCase(email)) {
                LoginScreen.showError(this, "Email does not match our records.");
                return;
            }
            if (UserManager.resetPassword(user, np)) {
                JOptionPane.showMessageDialog(this,
                        "Password reset successfully! You can now log in.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                LoginScreen.showError(this, "Reset failed. Please try again.");
            }
        });
    }
}

class StatCard extends JPanel {

    private final JLabel valueLabel;
    private final String unit;

    public StatCard(String icon, String cardTitle, String unit, Color accent) {
        this.unit = unit;
        setLayout(new BorderLayout(0, 6));
        setBackground(UITheme.BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent.darker(), 1, true),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        JLabel titleLbl = new JLabel(cardTitle, SwingConstants.CENTER);
        titleLbl.setFont(UITheme.FONT_CARD_TITLE);
        titleLbl.setForeground(UITheme.TEXT_SECONDARY);

        valueLabel = new JLabel("0.00" + (unit.isEmpty() ? "" : " " + unit), SwingConstants.CENTER);
        valueLabel.setFont(UITheme.FONT_CARD_VALUE);
        valueLabel.setForeground(accent);

        add(titleLbl, BorderLayout.NORTH);
        add(valueLabel, BorderLayout.CENTER);
    }

    public void setValue(double val) {
        valueLabel.setText(UITheme.DF.format(val) + (unit.isEmpty() ? "" : " " + unit));
    }
}

class GoalProgressBar extends JPanel {

    private double currentLiters = 0.0;
    private double goalLiters;

    public GoalProgressBar(double goalLiters) {
        this.goalLiters = goalLiters;
        setPreferredSize(new Dimension(0, 36));
        setOpaque(false);
        updateTooltip();
    }

    public void setGoalLiters(double g) {
        this.goalLiters = g;
        updateTooltip();
        repaint();
    }

    public double getGoalLiters() {
        return goalLiters;
    }

    public void setCurrentLiters(double l) {
        this.currentLiters = l;
        repaint();
    }

    private void updateTooltip() {
        setToolTipText("Daily goal: " + UITheme.DF.format(goalLiters)
                + " L  (" + UITheme.DF.format(goalLiters * WaterIntake.CUPS_PER_LITER) + " Glasses)");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth() - 2, h = 20, y = (getHeight() - h) / 2;
        g2.setColor(UITheme.BORDER_COLOR);
        g2.fillRoundRect(0, y, w, h, h, h);

        double pct = goalLiters > 0 ? Math.min(currentLiters / goalLiters, 1.0) : 0;
        int fillW = (int) (w * pct);
        Color fill = pct >= 1.0 ? UITheme.ACCENT_GREEN
                : pct >= 0.5 ? UITheme.ACCENT_BLUE
                        : pct > 0.0 ? UITheme.ACCENT_ORANGE : UITheme.ACCENT_RED;

        if (fillW > 0) {
            g2.setColor(fill);
            g2.fillRoundRect(0, y, fillW, h, h, h);
        } else {
            g2.setColor(new Color(239, 68, 68, 60));
            g2.fillRoundRect(0, y, w, h, h, h);
        }

        double cups = currentLiters * WaterIntake.CUPS_PER_LITER;
        double gcups = goalLiters * WaterIntake.CUPS_PER_LITER;
        String label = pct >= 1.0
                ? "Goal reached!  " + UITheme.DF.format(currentLiters) + " L  (" + UITheme.DF.format(cups) + " Glasses)"
                : UITheme.DF.format(currentLiters) + " L / " + UITheme.DF.format(goalLiters)
                + " L   (" + UITheme.DF.format(cups) + " / " + UITheme.DF.format(gcups)
                + " Glasses,  " + (int) (pct * 100) + "%)";

        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(UITheme.TEXT_PRIMARY);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, (w - fm.stringWidth(label)) / 2,
                y + (h + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }
}

class MonthlySummaryDialog extends JDialog {

    public MonthlySummaryDialog(JFrame parent, ArrayList<WaterIntake> intakes) {
        super(parent, "Monthly Intake Summary", true);
        setSize(480, 480);
        setMinimumSize(new Dimension(420, 360));
        setLocationRelativeTo(parent);
        setResizable(true);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(UITheme.BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        setContentPane(root);

        ArrayList<YearMonth> months = new ArrayList<>();
        for (WaterIntake e : intakes) {
            YearMonth ym = YearMonth.from(e.getDate());
            if (!months.contains(ym)) {
                months.add(ym);
            }
        }
        if (months.isEmpty()) {
            months.add(YearMonth.now());
        }
        months.sort(null);

        String[] labels = new String[months.size()];
        for (int i = 0; i < months.size(); i++) {
            labels[i] = months.get(i).format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        }

        JComboBox<String> monthBox = new JComboBox<>(labels);
        monthBox.setSelectedIndex(months.size() - 1);
        monthBox.setBackground(new Color(38, 54, 90));
        monthBox.setForeground(UITheme.TEXT_PRIMARY);
        monthBox.setFont(UITheme.FONT_BODY);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        topBar.setOpaque(false);
        topBar.add(UITheme.makeLabel("Month:"));
        topBar.add(monthBox);
        root.add(topBar, BorderLayout.NORTH);

        DefaultTableModel tModel = new DefaultTableModel(
                new String[]{"Date", "Total Glasses", "Total (L)"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        JTable tbl = new JTable(tModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(isRowSelected(row) ? UITheme.ROW_SELECT
                        : row % 2 == 0 ? UITheme.ROW_EVEN : UITheme.ROW_ODD);
                c.setForeground(UITheme.TEXT_PRIMARY);
                return c;
            }
        };
        tbl.setRowHeight(30);
        tbl.setFont(UITheme.FONT_BODY);
        tbl.setShowGrid(false);
        tbl.setBackground(UITheme.BG_TABLE);
        tbl.setForeground(UITheme.TEXT_PRIMARY);
        tbl.setSelectionBackground(UITheme.ROW_SELECT);
        tbl.setRowSorter(new TableRowSorter<>(tModel));

        JTableHeader th = tbl.getTableHeader();
        th.setFont(UITheme.FONT_HEADER);
        th.setBackground(new Color(40, 58, 100));
        th.setForeground(UITheme.ACCENT_BLUE);
        th.setReorderingAllowed(false);

        tbl.getColumnModel().getColumn(0).setPreferredWidth(150);
        tbl.getColumnModel().getColumn(1).setPreferredWidth(160);
        tbl.getColumnModel().getColumn(2).setPreferredWidth(110);

        JScrollPane scroll = new JScrollPane(tbl);
        scroll.setBackground(UITheme.BG_TABLE);
        scroll.getViewport().setBackground(UITheme.BG_TABLE);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));
        root.add(scroll, BorderLayout.CENTER);

        JLabel totalLbl = new JLabel(" ", SwingConstants.CENTER);
        totalLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        totalLbl.setForeground(UITheme.ACCENT_TEAL);

        JButton closeBtn = UITheme.makeButton("Close", UITheme.BTN_EXIT);
        closeBtn.setPreferredSize(new Dimension(100, 34));
        closeBtn.addActionListener(e -> dispose());

        JPanel bot = new JPanel(new BorderLayout(0, 4));
        bot.setOpaque(false);
        bot.add(totalLbl, BorderLayout.NORTH);
        bot.add(closeBtn, BorderLayout.SOUTH);
        root.add(bot, BorderLayout.SOUTH);

        Runnable populate = () -> {
            int idx = monthBox.getSelectedIndex();
            if (idx < 0) {
                return;
            }
            YearMonth ym = months.get(idx);

            LinkedHashMap<LocalDate, Double> map = new LinkedHashMap<>();
            for (WaterIntake e : intakes) {
                if (YearMonth.from(e.getDate()).equals(ym)) {
                    map.merge(e.getDate(), e.getAmountLiters(), Double::sum);
                }
            }

            tModel.setRowCount(0);
            double grand = 0;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");

            if (map.isEmpty()) {
                totalLbl.setText("No records found for " + labels[idx]);
                return;
            }

            for (LocalDate date : map.keySet()) {
                double liters = map.get(date);
                grand += liters;
                double cups = liters * WaterIntake.CUPS_PER_LITER;
                tModel.addRow(new Object[]{
                    date.format(fmt),
                    UITheme.DF.format(cups) + " Glasses",
                    UITheme.DF.format(liters) + " L"
                });
            }
            totalLbl.setText("Month total: " + UITheme.DF.format(grand * WaterIntake.CUPS_PER_LITER)
                    + " Glasses  (" + UITheme.DF.format(grand) + " L)");
        };

        monthBox.addActionListener(e -> populate.run());
        populate.run();
    }
}

class UserManagementDialog extends JDialog {

    private DefaultTableModel tModel;
    private JTable tbl;
    private JButton deleteBtn;

    public UserManagementDialog(JFrame parent) {
        super(parent, "User Management", true);
        setSize(700, 460);
        setMinimumSize(new Dimension(600, 380));
        setLocationRelativeTo(parent);
        setResizable(true);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(UITheme.BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        JLabel titleLbl = new JLabel("User Management", SwingConstants.LEFT);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLbl.setForeground(UITheme.ACCENT_BLUE);

        JLabel subLbl = new JLabel("Select a user and click Delete to remove their account and records.",
                SwingConstants.LEFT);
        subLbl.setFont(UITheme.FONT_SMALL);
        subLbl.setForeground(UITheme.TEXT_SECONDARY);

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 3));
        headerPanel.setOpaque(false);
        headerPanel.add(titleLbl);
        headerPanel.add(subLbl);
        root.add(headerPanel, BorderLayout.NORTH);

        tModel = new DefaultTableModel(
                new String[]{"Username", "Email", "Gender", "Height (cm)", "Weight (kg)"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        tbl = new JTable(tModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(isRowSelected(row) ? UITheme.ROW_SELECT
                        : row % 2 == 0 ? UITheme.ROW_EVEN : UITheme.ROW_ODD);
                c.setForeground(UITheme.TEXT_PRIMARY);
                ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return c;
            }
        };
        tbl.setRowHeight(34);
        tbl.setFont(UITheme.FONT_BODY);
        tbl.setShowGrid(false);
        tbl.setIntercellSpacing(new Dimension(0, 0));
        tbl.setBackground(UITheme.BG_TABLE);
        tbl.setForeground(UITheme.TEXT_PRIMARY);
        tbl.setSelectionBackground(UITheme.ROW_SELECT);
        tbl.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tbl.setRowSorter(new TableRowSorter<>(tModel));

        JTableHeader th = tbl.getTableHeader();
        th.setFont(UITheme.FONT_HEADER);
        th.setBackground(new Color(40, 58, 100));
        th.setForeground(UITheme.ACCENT_BLUE);
        th.setReorderingAllowed(false);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, UITheme.ACCENT_BLUE));

        tbl.getColumnModel().getColumn(0).setPreferredWidth(130);
        tbl.getColumnModel().getColumn(1).setPreferredWidth(160);
        tbl.getColumnModel().getColumn(2).setPreferredWidth(80);
        tbl.getColumnModel().getColumn(3).setPreferredWidth(110);
        tbl.getColumnModel().getColumn(4).setPreferredWidth(110);

        JScrollPane scroll = new JScrollPane(tbl);
        scroll.setBackground(UITheme.BG_TABLE);
        scroll.getViewport().setBackground(UITheme.BG_TABLE);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));
        root.add(scroll, BorderLayout.CENTER);

        deleteBtn = UITheme.makeButton("Delete Selected User", UITheme.BTN_DEL_ACC);
        deleteBtn.setPreferredSize(new Dimension(200, 38));
        deleteBtn.setToolTipText("Permanently delete the selected user and all their records");

        JButton closeBtn = UITheme.makeButton("Close", UITheme.BTN_EXIT);
        closeBtn.setPreferredSize(new Dimension(110, 38));

        JLabel countLbl = new JLabel("", SwingConstants.LEFT);
        countLbl.setFont(UITheme.FONT_SMALL);
        countLbl.setForeground(UITheme.TEXT_SECONDARY);

        JPanel btnRow = new JPanel(new BorderLayout(10, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);
        rightBtns.add(deleteBtn);
        rightBtns.add(closeBtn);

        btnRow.add(countLbl, BorderLayout.WEST);
        btnRow.add(rightBtns, BorderLayout.EAST);
        root.add(btnRow, BorderLayout.SOUTH);

        closeBtn.addActionListener(e -> dispose());

        deleteBtn.addActionListener(e -> {
            int row = tbl.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this,
                        "Please select a user from the table first.",
                        "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tbl.convertRowIndexToModel(row);
            String target = (String) tModel.getValueAt(modelRow, 0);

            int confirm = JOptionPane.showConfirmDialog(this,
                    "<html><b>Delete user \"" + target + "\"?</b><br><br>"
                    + "This will permanently remove:<br>"
                    + "- Their account from users.txt<br>"
                    + "- All their water intake records<br><br>"
                    + "<i>This cannot be undone.</i></html>",
                    "Confirm Delete User",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            new File(IntakeFileManager.buildFileName(target)).delete();
            new File(IntakeFileManager.buildFileName(target) + ".bak").delete();
            new File(IntakeFileManager.buildLastDateFileName(target)).delete();

            if (UserManager.deleteUser(target)) {
                JOptionPane.showMessageDialog(this,
                        "User \"" + target + "\" has been deleted successfully.",
                        "User Deleted", JOptionPane.INFORMATION_MESSAGE);
                loadUsers(countLbl);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Could not delete user. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        loadUsers(countLbl);
    }

    private void loadUsers(JLabel countLbl) {
        tModel.setRowCount(0);
        ArrayList<String[]> users = UserManager.getAllUsers();
        for (String[] u : users) {
            tModel.addRow(new Object[]{
                u[0],
                u[1],
                u[2],
                u[3] + " cm",
                u[4] + " kg"
            });
        }
        int n = users.size();
        countLbl.setText(n + " user" + (n == 1 ? "" : "s") + " registered");
    }
}

class MainTrackerWindow extends JFrame {

    private final String currentUser;
    private double dailyGoalLiters;
    private LocalDate lastActiveDate;
    private final ArrayList<WaterIntake> intakes = new ArrayList<>();
    private final ArrayList<WaterIntake> filteredIntakes = new ArrayList<>();

    private DefaultTableModel model;
    private JTable table;
    private JTextField searchField;
    private JLabel statusBar;
    private JLabel bmiGoalLabel;
    private GoalProgressBar goalBar;

    private StatCard todayCard;
    private StatCard goalCard;
    private StatCard countCard;

    private boolean goalResetActive = false;
    private boolean milestoneFired = false;

    private LocalDate fromDate = null;
    private LocalDate toDate = null;
    private JButton fromBtn, toBtn;

    public MainTrackerWindow(String username) {
        super("HydroTrack - " + username + "'s HydroTrack");
        this.currentUser = username;
        this.dailyGoalLiters = UserManager.getUserGoal(username);
        this.lastActiveDate = IntakeFileManager.loadLastDate(username);

        setSize(1100, 740);
        setMinimumSize(new Dimension(860, 600));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });

        buildUI();
        registerKeyboardShortcuts();

        int failures = IntakeFileManager.loadIntakes(currentUser, intakes);
        checkNewDay(false);
        refreshTable();

        if (failures > 0) {
            JOptionPane.showMessageDialog(this,
                    failures + " record(s) could not be loaded due to corrupted data.",
                    "Load Warning", JOptionPane.WARNING_MESSAGE);
        }

        if (intakes.isEmpty()) {
            setStatus("Welcome to HydroTrack! Click \u2795 Add to log your first intake.");
        } else {
            setStatus("Showing today\u2019s records. Use Search to view past dates.",
                    UITheme.TEXT_SECONDARY);
        }

        javax.swing.Timer dayTimer = new javax.swing.Timer(60_000, e -> checkNewDay(true));
        dayTimer.setRepeats(true);
        dayTimer.start();

        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(UITheme.BG_DARK);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        setContentPane(root);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildSouthPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.BG_CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        JLabel titleLbl = new JLabel("HydroTrack  —  Daily Water Intake");
        titleLbl.setFont(UITheme.FONT_TITLE);
        titleLbl.setForeground(UITheme.ACCENT_BLUE);

        JPanel rightPanel = new JPanel(new GridLayout(3, 1, 0, 6));
        rightPanel.setOpaque(false);

        JLabel userLbl = new JLabel("Logged in: " + currentUser, SwingConstants.RIGHT);
        userLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userLbl.setForeground(UITheme.TEXT_SECONDARY);

        double[] hw = UserManager.getHeightWeight(currentUser);
        double bmi = BMIUtil.calcBMI(hw[0], hw[1]);
        JLabel bmiLbl = new JLabel(
                "BMI: " + UITheme.DF.format(bmi) + "  \u2014  " + BMIUtil.bmiCategory(bmi),
                SwingConstants.RIGHT);
        bmiLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        bmiLbl.setForeground(UITheme.TEXT_PRIMARY);

        bmiGoalLabel = new JLabel(
                "Goal: " + UITheme.DF.format(dailyGoalLiters)
                + " L  (" + UITheme.DF.format(dailyGoalLiters * WaterIntake.CUPS_PER_LITER) + " Glasses)",
                SwingConstants.RIGHT);
        bmiGoalLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        bmiGoalLabel.setForeground(UITheme.ACCENT_TEAL);

        rightPanel.add(userLbl);
        rightPanel.add(bmiLbl);
        rightPanel.add(bmiGoalLabel);
        header.add(titleLbl, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(10, 14, 4, 14));

        JPanel statsRow = new JPanel(new GridLayout(1, 3, 10, 0));
        statsRow.setOpaque(false);
        todayCard = new StatCard("\uD83D\uDCA7", "Today's Intake", "Glasses", UITheme.ACCENT_BLUE);
        goalCard = new StatCard("\uD83C\uDFAF", "Daily Goal Intake", "L", UITheme.ACCENT_PURPLE);
        countCard = new StatCard("\uD83D\uDCCB", "Today's Entries", "", UITheme.ACCENT_YELLOW);
        statsRow.add(todayCard);
        statsRow.add(goalCard);
        statsRow.add(countCard);

        goalBar = new GoalProgressBar(dailyGoalLiters);
        JPanel topSection = new JPanel(new BorderLayout(0, 6));
        topSection.setOpaque(false);
        topSection.add(statsRow, BorderLayout.NORTH);
        topSection.add(goalBar, BorderLayout.CENTER);
        center.add(topSection, BorderLayout.NORTH);

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchBar.setOpaque(false);
        searchField = new JTextField(16);
        UITheme.styleField(searchField);
        UITheme.setMaxLength(searchField, 100);

        fromBtn = makeDatePickerBtn("From: Any");
        toBtn = makeDatePickerBtn("To: Any");
        fromBtn.addActionListener(e -> {
            LocalDate p = CalendarPicker.show(this, fromDate);
            fromDate = p;
            fromBtn.setText(fromDate != null
                    ? "From: " + fromDate.format(DateTimeFormatter.ofPattern("MMM d"))
                    : "From: Any");
        });
        toBtn.addActionListener(e -> {
            LocalDate p = CalendarPicker.show(this, toDate);
            toDate = p;
            toBtn.setText(toDate != null
                    ? "To: " + toDate.format(DateTimeFormatter.ofPattern("MMM d"))
                    : "To: Any");
        });

        JButton searchBtn = UITheme.makeButton("Search", UITheme.BTN_SEARCH);
        JButton clearBtn = UITheme.makeButton("Clear", UITheme.BTN_EXIT);
        searchBtn.setPreferredSize(new Dimension(115, 30));
        clearBtn.setPreferredSize(new Dimension(80, 30));
        searchBtn.setToolTipText("Search all records including past dates  (Enter / Ctrl+F)");
        clearBtn.setToolTipText("Clear filters and return to today's records");

        searchBar.add(UITheme.makeLabel("Search:"));
        searchBar.add(searchField);
        searchBar.add(fromBtn);
        searchBar.add(toBtn);
        searchBar.add(searchBtn);
        searchBar.add(clearBtn);

        searchBtn.addActionListener(e
                -> performSearch(searchField.getText().trim(), fromDate, toDate));
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            fromDate = null;
            toDate = null;
            fromBtn.setText("From: Any");
            toBtn.setText("To: Any");
            refreshTable();
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    searchBtn.doClick();
                }
            }
        });

        model = new DefaultTableModel(new String[]{"Date", "Amount", "Note"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(isRowSelected(row) ? UITheme.ROW_SELECT
                        : row % 2 == 0 ? UITheme.ROW_EVEN : UITheme.ROW_ODD);
                c.setForeground(UITheme.TEXT_PRIMARY);
                return c;
            }
        };
        table.setRowHeight(32);
        table.setFont(UITheme.FONT_BODY);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(UITheme.BG_TABLE);
        table.setForeground(UITheme.TEXT_PRIMARY);
        table.setSelectionBackground(UITheme.ROW_SELECT);
        table.setRowSorter(new TableRowSorter<>(model));
        JTableHeader th = table.getTableHeader();
        th.setFont(UITheme.FONT_HEADER);
        th.setBackground(new Color(40, 58, 100));
        th.setForeground(UITheme.ACCENT_BLUE);
        th.setReorderingAllowed(false);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, UITheme.ACCENT_BLUE));
        th.setToolTipText("Click column header to sort");
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(460);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(UITheme.BG_TABLE);
        scroll.getViewport().setBackground(UITheme.BG_TABLE);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));

        JPanel tableWrapper = new JPanel(new BorderLayout(0, 4));
        tableWrapper.setOpaque(false);
        tableWrapper.add(searchBar, BorderLayout.NORTH);
        tableWrapper.add(scroll, BorderLayout.CENTER);
        center.add(tableWrapper, BorderLayout.CENTER);
        return center;
    }

    private JButton makeDatePickerBtn(String label) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover()
                        ? UITheme.ACCENT_BLUE.darker() : new Color(38, 54, 90));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(UITheme.BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(UITheme.TEXT_PRIMARY);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }

            @Override
            protected void paintBorder(Graphics g) {
            }
        };
        btn.setFont(UITheme.FONT_BTN);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(110, 30));
        return btn;
    }

    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(UITheme.BG_CARD);
        south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER_COLOR));

        JButton addBtn = UITheme.makeButton("Add", UITheme.BTN_ADD);
        JButton editBtn = UITheme.makeButton("Edit", UITheme.BTN_EDIT);
        JButton delBtn = UITheme.makeButton("Delete", UITheme.BTN_DELETE);
        JButton saveBtn = UITheme.makeButton("Save", UITheme.BTN_SAVE);
        JButton goalBtn = UITheme.makeButton("Reset Goal", UITheme.BTN_RESET);
        JButton summaryBtn = UITheme.makeButton("Summary", UITheme.BTN_SUMMARY);
        JButton usersBtn = UITheme.makeButton("Manage Users", UITheme.BTN_VIEW);
        JButton viewBtn = UITheme.makeButton("Open Dir", UITheme.BTN_VIEW);
        JButton exitBtn = UITheme.makeButton("Exit", UITheme.BTN_EXIT);

        addBtn.setToolTipText("Add a new water intake record  (Alt+N)");
        editBtn.setToolTipText("Edit the selected record  (Alt+E)");
        delBtn.setToolTipText("Delete the selected record  (Delete)");
        saveBtn.setToolTipText("Save all records to file  (Ctrl+S)");
        goalBtn.setToolTipText("Reset today\u2019s progress display (data is kept)");
        summaryBtn.setToolTipText("View monthly intake summary");
        usersBtn.setToolTipText("View and manage all registered user accounts");
        viewBtn.setToolTipText("Open the folder containing your CSV files");
        exitBtn.setToolTipText("Exit and return to the login screen");

        addBtn.setMnemonic(KeyEvent.VK_N);
        editBtn.setMnemonic(KeyEvent.VK_E);
        saveBtn.setMnemonic(KeyEvent.VK_S);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 8));
        btnRow.setOpaque(false);
        for (JButton b : new JButton[]{addBtn, editBtn, delBtn, saveBtn, goalBtn, summaryBtn, usersBtn, viewBtn, exitBtn}) {
            btnRow.add(b);
        }

        addBtn.addActionListener(e -> addIntake());
        editBtn.addActionListener(e -> editSelected());
        delBtn.addActionListener(e -> deleteSelected());
        saveBtn.addActionListener(e -> saveData());
        goalBtn.addActionListener(e -> resetDailyGoal());
        summaryBtn.addActionListener(e -> showMonthlySummary());
        usersBtn.addActionListener(e -> showUserManagement());
        viewBtn.addActionListener(e -> openSaveDirectory());
        exitBtn.addActionListener(e -> confirmExit());

        south.add(btnRow, BorderLayout.CENTER);

        statusBar = new JLabel("  Ready", SwingConstants.LEFT);
        statusBar.setFont(UITheme.FONT_STATUS);
        statusBar.setForeground(UITheme.TEXT_SECONDARY);
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 10, 5, 10));
        south.add(statusBar, BorderLayout.SOUTH);
        return south;
    }

    private void registerKeyboardShortcuts() {
        JPanel root = (JPanel) getContentPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        am.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveData();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        am.put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelected();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focusSearch");
        am.put("focusSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.requestFocusInWindow();
            }
        });
    }

    private void addIntake() {
        JTextField dateField = new JTextField(LocalDate.now().toString(), 14);
        JTextField amountField = new JTextField("", 10);
        JTextField noteField = new JTextField(30);
        UITheme.setMaxLength(noteField, 200);
        UITheme.styleField(dateField);
        UITheme.styleField(amountField);
        UITheme.styleField(noteField);
        noteField.setToolTipText("e.g. Morning, Afternoon, Evening — max 200 characters");

        JPanel p = buildFormPanel(
                new String[]{"Date (yyyy-MM-dd):", "Amount (Liters):", "Note (optional):"},
                new JTextField[]{dateField, amountField, noteField});

        int opt = JOptionPane.showConfirmDialog(this, p,
                "Add New Water Intake", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opt != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            LocalDate d = LocalDate.parse(dateField.getText().trim());
            String rawAmt = amountField.getText().trim();
            if (rawAmt.isEmpty()) {
                throw new NumberFormatException("empty");
            }
            double l = Double.parseDouble(rawAmt);
            if (l <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }
            if (l > 20) {
                throw new IllegalArgumentException("Amount is too large (max 20 L).");
            }
            String n = noteField.getText().trim();

            goalResetActive = false;

            LocalDate today = LocalDate.now();
            double todayBefore = 0;
            for (WaterIntake e : intakes) {
                if (e.getDate().equals(today)) {
                    todayBefore += e.getAmountLiters();
                }
            }

            intakes.add(new WaterIntake(d, l, n));
            refreshTable();
            autoSave();

            double todayAfter = todayBefore + (d.equals(today) ? l : 0);

            if (d.equals(today)) {
                if (todayAfter > dailyGoalLiters && todayBefore <= dailyGoalLiters) {

                    if (!milestoneFired) {
                        checkMilestone();
                        milestoneFired = true;
                    }
                    JOptionPane.showMessageDialog(this,
                            "Warning: You have exceeded your recommended daily water intake!\n\n"
                            + "You have consumed " + UITheme.DF.format(todayAfter * WaterIntake.CUPS_PER_LITER)
                            + " Glasses today, which is above your goal of "
                            + UITheme.DF.format(dailyGoalLiters * WaterIntake.CUPS_PER_LITER) + " Glasses.",
                            "Daily Goal Exceeded", JOptionPane.WARNING_MESSAGE);
                } else if (todayAfter > dailyGoalLiters && todayBefore > dailyGoalLiters) {

                    JOptionPane.showMessageDialog(this,
                            "Warning: You have exceeded your recommended daily water intake!\n\n"
                            + "You have consumed " + UITheme.DF.format(todayAfter * WaterIntake.CUPS_PER_LITER)
                            + " Glasses today, which is above your goal of "
                            + UITheme.DF.format(dailyGoalLiters * WaterIntake.CUPS_PER_LITER) + " Glasses.",
                            "Daily Goal Exceeded", JOptionPane.WARNING_MESSAGE);
                } else if (!milestoneFired && todayBefore < dailyGoalLiters && todayAfter >= dailyGoalLiters) {
                    checkMilestone();
                    milestoneFired = true;
                }
            }

        } catch (DateTimeParseException ex) {
            LoginScreen.showError(this, "Invalid date format.\nUse: yyyy-MM-dd  (e.g. 2026-03-13)");
        } catch (NumberFormatException ex) {
            LoginScreen.showError(this, "Invalid amount.\nEnter a positive number (e.g. 1.5).");
        } catch (IllegalArgumentException ex) {
            LoginScreen.showError(this, ex.getMessage());
        }
    }

    private void editSelected() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row to edit.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(r);
        WaterIntake entry = getVisibleEntry(modelRow);
        if (entry == null) {
            return;
        }

        JTextField amountField = new JTextField(String.valueOf(entry.getAmountLiters()), 10);
        JTextField noteField = new JTextField(entry.getNote(), 30);
        UITheme.setMaxLength(noteField, 200);
        UITheme.styleField(amountField);
        UITheme.styleField(noteField);

        JPanel p = buildFormPanel(
                new String[]{"Amount (Liters):", "Note:"},
                new JTextField[]{amountField, noteField});
        int opt = JOptionPane.showConfirmDialog(this, p,
                "Edit Intake  [" + entry.getDate() + "]",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opt != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            double l = Double.parseDouble(amountField.getText().trim());
            if (l <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }
            if (l > 20) {
                throw new IllegalArgumentException("Amount is too large (max 20 L).");
            }
            entry.setAmountLiters(l);
            entry.setNote(noteField.getText().trim());
            refreshTable();
            autoSave();
            setStatus("Record updated: " + entry.getDate()
                    + "  \u2022  " + UITheme.DF.format(l) + " L");
        } catch (NumberFormatException ex) {
            LoginScreen.showError(this, "Invalid amount.\nEnter a positive number (e.g. 1.5).");
        } catch (IllegalArgumentException ex) {
            LoginScreen.showError(this, ex.getMessage());
        }
    }

    private void deleteSelected() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row to delete.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(r);
        WaterIntake entry = getVisibleEntry(modelRow);
        if (entry == null) {
            return;
        }
        int opt = JOptionPane.showConfirmDialog(this,
                "Delete the entry for " + entry.getDate()
                + " (" + entry.getAmountDisplay() + ")?\nThis cannot be undone.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opt == JOptionPane.YES_OPTION) {
            intakes.remove(entry);
            refreshTable();
            autoSave();
            setStatus("Record deleted: " + entry.getDate());
        }
    }

    private void performSearch(String keyword, LocalDate from, LocalDate to) {
        if (keyword.isEmpty() && from == null && to == null) {
            refreshTable();
            return;
        }
        model.setRowCount(0);
        filteredIntakes.clear();
        String lower = keyword.toLowerCase();
        for (WaterIntake e : intakes) {
            if (from != null && e.getDate().isBefore(from)) {
                continue;
            }
            if (to != null && e.getDate().isAfter(to)) {
                continue;
            }
            if (!keyword.isEmpty()) {
                boolean dm = e.getDate()
                        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        .toLowerCase().contains(lower);
                boolean nm = e.getNote().toLowerCase().contains(lower);
                boolean am = e.getAmountDisplay().toLowerCase().contains(lower);
                if (!dm && !nm && !am) {
                    continue;
                }
            }
            model.addRow(e.toRow());
            filteredIntakes.add(e);
        }
        if (filteredIntakes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No records match your search criteria.",
                    "No Results", JOptionPane.INFORMATION_MESSAGE);
        } else {
            setStatus("Search: " + filteredIntakes.size() + " record(s) found.");
        }
    }

    private void saveData() {
        try {
            String fname = IntakeFileManager.saveIntakes(currentUser, intakes);
            JOptionPane.showMessageDialog(this,
                    "Data successfully saved.\n\nFile: " + fname,
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
            setStatus("Saved  -  " + time);
        } catch (IOException ex) {
            LoginScreen.showError(this, "Could not save file:\n" + ex.getMessage());
        }
    }

    private void autoSave() {
        try {
            IntakeFileManager.saveIntakes(currentUser, intakes);
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
            statusBar.setText("  Auto-saved at " + time
                    + "   \u2022   " + intakes.size() + " record(s)");
        } catch (IOException ex) {
            System.err.println("[WARN] " + ex.getMessage());
        }
    }

    private void openSaveDirectory() {
        try {
            Desktop.getDesktop().open(new File("."));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Cannot open folder automatically.\nSaved files are in your project directory.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshTable() {
        model.setRowCount(0);
        filteredIntakes.clear();
        LocalDate today = LocalDate.now();
        for (WaterIntake e : intakes) {
            if (e.getDate().equals(today)) {
                model.addRow(e.toRow());
                filteredIntakes.add(e);
            }
        }
        updateStats();
    }

    private void updateStats() {
        double todayTotal = 0;
        int todayCount = 0;
        LocalDate now = LocalDate.now();
        for (WaterIntake e : intakes) {
            if (e.getDate().equals(now)) {
                todayTotal += e.getAmountLiters();
                todayCount++;
            }
        }
        double displayed = goalResetActive ? 0.0 : todayTotal;
        double cups = displayed * WaterIntake.CUPS_PER_LITER;

        todayCard.setValue(cups);
        goalCard.setValue(dailyGoalLiters);
        countCard.setValue(todayCount);
        goalBar.setGoalLiters(dailyGoalLiters);
        goalBar.setCurrentLiters(displayed);
        checkGoalStatus(displayed);
    }

    private void checkGoalStatus(double todayLiters) {
        double goalCups = dailyGoalLiters * WaterIntake.CUPS_PER_LITER;
        double todayCups = todayLiters * WaterIntake.CUPS_PER_LITER;
        double remaining = goalCups - todayCups;

        if (todayLiters >= dailyGoalLiters) {
            statusBar.setForeground(UITheme.ACCENT_GREEN);
            setStatus("Goal reached! Great job today!  "
                    + UITheme.DF.format(todayCups) + " / " + UITheme.DF.format(goalCups) + " Glasses");
        } else if (todayLiters > 0) {
            statusBar.setForeground(UITheme.ACCENT_ORANGE);
            setStatus("\u26A0 " + UITheme.DF.format(todayCups) + " / " + UITheme.DF.format(goalCups)
                    + " Glasses today  \u2014  " + UITheme.DF.format(remaining) + " Glasses remaining.");
        } else {
            statusBar.setForeground(UITheme.ACCENT_RED);
            setStatus("No intake logged yet. Stay hydrated!");
        }
    }

    private void checkMilestone() {
        double total = 0;
        for (WaterIntake e : intakes) {
            if (e.getDate().equals(LocalDate.now())) {
                total += e.getAmountLiters();
            }
        }
        double cups = total * WaterIntake.CUPS_PER_LITER;
        double gcups = dailyGoalLiters * WaterIntake.CUPS_PER_LITER;
        JOptionPane.showMessageDialog(this,
                "Goal reached!\n\nGreat job staying hydrated today!\n"
                + "You drank " + UITheme.DF.format(cups) + " out of "
                + UITheme.DF.format(gcups) + " Glasses.",
                "Daily Goal Achieved!", JOptionPane.INFORMATION_MESSAGE);
        statusBar.setForeground(UITheme.ACCENT_GREEN);
        setStatus("Goal reached!  "
                + UITheme.DF.format(cups) + " / " + UITheme.DF.format(gcups) + " Glasses");
    }

    private void checkNewDay(boolean triggered) {
        LocalDate today = LocalDate.now();
        if (lastActiveDate == null) {
            lastActiveDate = today;
            IntakeFileManager.saveLastDate(currentUser);
            return;
        }
        if (!today.isAfter(lastActiveDate)) {
            return;
        }

        final LocalDate yesterday = lastActiveDate;
        double yTotal = 0;
        for (WaterIntake e : intakes) {
            if (e.getDate().equals(yesterday)) {
                yTotal += e.getAmountLiters();
            }
        }

        double yCups = yTotal * WaterIntake.CUPS_PER_LITER;
        double gCups = dailyGoalLiters * WaterIntake.CUPS_PER_LITER;
        boolean goalMet = yTotal >= dailyGoalLiters;

        JOptionPane.showMessageDialog(this,
                (goalMet ? "Great job! You reached your daily goal yesterday."
                        : "You did not reach your goal yesterday. Try more today.")
                + "\n\nYesterday (" + yesterday + "):\n"
                + UITheme.DF.format(yCups) + " Glasses out of " + UITheme.DF.format(gCups) + " Glasses.\n\n"
                + "Starting a fresh day!",
                "New Day - Daily Reset",
                goalMet ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);

        intakes.removeIf(e -> !e.getDate().equals(today));
        goalResetActive = false;
        milestoneFired = false;
        lastActiveDate = today;
        IntakeFileManager.saveLastDate(currentUser);
        try {
            IntakeFileManager.saveIntakes(currentUser, intakes);
        } catch (IOException ex) {
            System.err.println("[WARN] " + ex.getMessage());
        }
        if (triggered) {
            refreshTable();
        }
        setStatus(goalMet
                ? "New day! Great work yesterday. Keep it up!"
                : "New day! Let's reach the goal today!",
                goalMet ? UITheme.ACCENT_GREEN : UITheme.ACCENT_ORANGE);
    }

    private void resetDailyGoal() {
        double gGoal = UserManager.getUserGoal(currentUser);
        double gCups = gGoal * WaterIntake.CUPS_PER_LITER;

        int confirm = JOptionPane.showConfirmDialog(this,
                "<html><b>Are you sure you want to reset today\u2019s goal?</b><br><br>"
                + "This will:<br>"
                + "- Reset <b>Today's Intake</b> display to <b>0 glasses</b><br>"
                + "- Restore your daily goal to <b>"
                + UITheme.DF.format(gGoal) + " L  (" + UITheme.DF.format(gCups) + " Glasses)</b><br>"
                + "- Reset the <b>progress bar</b> to 0%<br><br>"
                + "<i>Your previously recorded intake data is kept safe.<br>"
                + "You can still search all past records at any time.</i></html>",
                "Confirm Reset Daily Goal",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        dailyGoalLiters = gGoal;
        goalResetActive = true;
        milestoneFired = false;
        refreshTable();
        bmiGoalLabel.setText("Goal: " + UITheme.DF.format(dailyGoalLiters)
                + " L  (" + UITheme.DF.format(dailyGoalLiters * WaterIntake.CUPS_PER_LITER) + " Glasses)");
        setStatus("Goal reset to " + UITheme.DF.format(dailyGoalLiters)
                + " L. Progress reset to 0. Your intake history is preserved.",
                UITheme.ACCENT_BLUE);
    }

    private void showMonthlySummary() {
        new MonthlySummaryDialog(this, intakes).setVisible(true);
    }

    private void showUserManagement() {
        new UserManagementDialog(this).setVisible(true);
    }

    private WaterIntake getVisibleEntry(int modelRow) {
        if (modelRow < 0 || modelRow >= filteredIntakes.size()) {
            return null;
        }
        return filteredIntakes.get(modelRow);
    }

    private JPanel buildFormPanel(String[] labels, JTextField[] fields) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UITheme.BG_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            p.add(UITheme.makeLabel(labels[i]), gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            p.add(fields[i], gbc);
        }
        return p;
    }

    private void setStatus(String message) {
        statusBar.setText("  " + message);
    }

    private void setStatus(String message, Color colour) {
        statusBar.setForeground(colour);
        statusBar.setText("  " + message);
    }

    private void deleteAccount() {

        int first = JOptionPane.showConfirmDialog(this,
                "<html><b>\u26A0\uFE0F Permanently Delete Your Account?</b><br><br>"
                + "This action <b>cannot be undone</b>. It will:<br><br>"
                + "- Permanently remove your profile (<b>" + currentUser + "</b>)<br>"
                + "- Delete all your water intake records<br>"
                + "- Remove all saved files for this account<br><br>"
                + "<i>Are you sure you want to continue?</i></html>",
                "Delete Account \u2014 Step 1 of 2",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (first != JOptionPane.YES_OPTION) {
            return;
        }

        JPasswordField pwField = new JPasswordField(20);
        UITheme.styleField(pwField);
        JPanel pwPanel = new JPanel(new GridBagLayout());
        pwPanel.setBackground(UITheme.BG_CARD);
        pwPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel pwLbl = UITheme.makeLabel("Confirm your password:");
        pwLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        pwLbl.setForeground(UITheme.ACCENT_RED);
        pwPanel.add(pwLbl, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        pwPanel.add(pwField, gbc);

        int second = JOptionPane.showConfirmDialog(this, pwPanel,
                "Delete Account \u2014 Step 2 of 2: Confirm Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (second != JOptionPane.OK_OPTION) {
            return;
        }

        String entered = new String(pwField.getPassword()).trim();
        if (!UserManager.validateLogin(currentUser, entered)) {
            LoginScreen.showError(this, "Incorrect password. Account deletion cancelled.");
            return;
        }

        String csvFile = IntakeFileManager.buildFileName(currentUser);
        String bakFile = csvFile + ".bak";
        String dateFile = IntakeFileManager.buildLastDateFileName(currentUser);
        new File(csvFile).delete();
        new File(bakFile).delete();
        new File(dateFile).delete();

        boolean removed = UserManager.deleteUser(currentUser);

        if (removed) {
            JOptionPane.showMessageDialog(this,
                    "Your account has been permanently deleted.\n\n"
                    + "All records and data for \"" + currentUser + "\" have been removed.\n"
                    + "You will now be returned to the login screen.",
                    "Account Deleted", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            new LoginScreen();
        } else {
            LoginScreen.showError(this, "Could not remove account. Please try again.");
        }
    }

    private void confirmExit() {
        int opt = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?\nYou will be returned to the login screen.",
                "Confirm Exit", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            dispose();
            new LoginScreen();
        }
    }
}

public class Group6HydroTrack {

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        SwingUtilities.invokeLater(LoginScreen::new);
    }
}
