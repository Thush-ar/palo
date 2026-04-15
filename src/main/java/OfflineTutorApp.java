import ai.djl.Model;
import java.awt.Frame;
import javax.swing.JDialog;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import com.google.gson.JsonElement;
import net.sourceforge.tess4j.Tesseract;
import javax.swing.table.DefaultTableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.RenderingHints;
import java.awt.GradientPaint;
import java.awt.BasicStroke;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OfflineTutorApp extends JFrame {

    // --- GUI Components ---
    private JComboBox<String> subjectDropdown;
    private JTextArea questionArea;
    private JLabel aiStatusLabel;
    private JLabel difficultyLabel;
    private QuizOptionButton btnA, btnB, btnC, btnD;
    private JLabel imageViewer;
    private Clip zenClip;
    private VoiceAssistant tts;
    private boolean isSpeechEnabled = true;
    private File currentChatFile = null;

    public void setSpeechEnabled(boolean enabled) { this.isSpeechEnabled = enabled; }
    public boolean isSpeechEnabled() { return isSpeechEnabled; }
    public VoiceAssistant getTTS() { return tts; }

    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton sendButton;

    // --- Integrated UI Components ---
    private JPanel mainCardPanel;
    private CardLayout cardLayout;
    private JPanel quizPanel;
    private JPanel chatPanel;
    private JPanel audioPanel;
    private JPanel focusPanel;
    private JProgressBar loadingBar;
    private JPanel loadingOverlay;

    // --- Logic & Data ---
    public String customUserName = "";
    private PaLOHomePage homePageInstance;
    private List<JsonObject> recentFilesList = new ArrayList<>();
    private Predictor<float[], Float> predictor;
    private JButton btnScan;
    private JLabel countLabel;
    private float currentProbability = 0.0f;
    private List<Float> studentHistory = new ArrayList<>();
    private List<QuizItem> reviewFlashcards = new ArrayList<>();
    public List<QuizItem> getReviewFlashcards() { return reviewFlashcards; }
    private String currentLevel = "EASY";
    private List<QuizItem> easyQuestions = new ArrayList<>();
    private List<QuizItem> easyMediumQuestions = new ArrayList<>();
    private List<QuizItem> mediumQuestions = new ArrayList<>();
    private List<QuizItem> mediumHardQuestions = new ArrayList<>();
    private List<QuizItem> hardQuestions = new ArrayList<>();
    private List<QuizItem> expertQuestions = new ArrayList<>();
    private Set<String> askedQuestionIDs = new HashSet<>();
    private List<QuizItem> sessionLog = new ArrayList<>();
    private QuizItem currentQuestion;
    private JButton btnConfigQuiz;
    private int targetTheoryCount = 10;
    private int targetNumericalCount = 0;
    private boolean numericalModeActive = false;
    private int questionCounter = 0;
    private javax.swing.Timer quizTimer;
    private int secondsRemaining;
    private int userSelectedTime = 0;
    private JLabel timerLabel;
    private CircleMasteryPanel homeCircleMastery;
    private QuizMasteryRing quizMasteryRing;
    private JPanel masteryContainer;
    private CircularTimer clockTimer;
    private JSpinner questionCountSpinner;
    private boolean isAudioMode = false;
    private boolean isChatMode = false;
    private LlamaConnection llamaConnection;
    private JPanel chatBox;
    private JPanel recentChatsPanel;
    private static final Map<String, ThemePreset> PRESETS = new java.util.LinkedHashMap<>();
    static {
        PRESETS.put("Emerald Forest", new ThemePreset(new Color(22, 68, 54), new Color(28, 28, 28), new Color(0, 150, 136), true, "Emerald Light"));
        PRESETS.put("Emerald Light", new ThemePreset(new Color(210, 230, 220), new Color(245, 247, 245), new Color(0, 150, 136), false, "Emerald Forest"));
        PRESETS.put("Midnight Coffee", new ThemePreset(new Color(45, 35, 30), new Color(15, 15, 15), new Color(180, 140, 100), true, "Latte"));
        PRESETS.put("Latte", new ThemePreset(new Color(235, 225, 215), new Color(250, 248, 245), new Color(140, 100, 60), false, "Midnight Coffee"));
        PRESETS.put("Deep Space", new ThemePreset(new Color(20, 25, 45), new Color(10, 10, 15), new Color(100, 120, 255), true, "Arctic Breeze"));
        PRESETS.put("Arctic Breeze", new ThemePreset(new Color(220, 230, 255), new Color(245, 250, 255), new Color(50, 100, 200), false, "Deep Space"));
        PRESETS.put("Purple Night", new ThemePreset(new Color(35, 25, 50), new Color(20, 15, 30), new Color(155, 89, 182), true, "Lavender Dream"));
        PRESETS.put("Lavender Dream", new ThemePreset(new Color(240, 230, 255), new Color(252, 250, 255), new Color(155, 89, 182), false, "Purple Night"));
    }

    private static class ThemePreset {
        final Color side, main, accent;
        final boolean isDark;
        final String counterpart;

        ThemePreset(Color s, Color m, Color a, boolean dark, String cp) {
            this.side = s; this.main = m; this.accent = a; this.isDark = dark; this.counterpart = cp;
        }
    }

    private String currentThemeName = "Emerald Forest";
    public Color sidebarColor = new Color(22, 68, 54);
    public Color windowColor = new Color(28, 28, 28);
    public Color currentAccentColor = new Color(0, 150, 136);
    private List<String> currentBannedTopics = new ArrayList<>();
    private String selectedSubject = "General";
    private static final Map<String, List<String>> SUBJECT_BAN_LISTS = new HashMap<>();

    static {
        SUBJECT_BAN_LISTS.put("Physics", Arrays.asList("figure", "table", "shown", "medium", "media", "value", "values", "constant", "constants", "diagram", "consider", "angle", "angles", "solution", "example", "direction", "magnitude", "ray", "rays", "index", "indices"));
        SUBJECT_BAN_LISTS.put("Computer Science", Arrays.asList("figure", "output", "input", "code", "program", "example", "shown", "value", "values", "variable", "variables", "line", "following", "statement"));
        SUBJECT_BAN_LISTS.put("Biology", Arrays.asList("figure", "diagram", "shown", "structure", "structures", "function", "process", "example", "type", "types", "part", "parts"));
        SUBJECT_BAN_LISTS.put("General", Arrays.asList("figure", "table", "shown", "example", "problem", "solution", "chapter"));
    }

    // --- THEME HELPER ---
    public boolean isDarkMode() {
        if (UIManager.getLookAndFeel() == null) return true;
        return UIManager.getLookAndFeel().getClass().getName().contains("Dark");
    }

    public OfflineTutorApp() {
        tts = new VoiceAssistant();
        SplashScreen splash = new SplashScreen();
        splash.setVisible(true);

        new Thread(() -> {
            try {
                initAI();
                loadProgress();
                loadRecentFiles();
                llamaConnection = new LlamaConnection();
                Thread.sleep(2500);

                SwingUtilities.invokeLater(() -> {
                    splash.dispose();
                    initializeIntegratedUI();
                    setupLoadingOverlay();
                    setGlassPane(loadingOverlay);
                    setAppIcon();
                    setSize(1400, 850);
                    this.setLocationRelativeTo(null);
                    homePageInstance = new PaLOHomePage(this);
                    homePageInstance.setVisible(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Lightweight wrapper around JButton to keep option-specific styling and feedback
    private class QuizOptionButton extends JButton {
        private final Color themeColor;
        private boolean isBlinking = false;
        private Color currentFeedbackColor = null;

        public QuizOptionButton(String text, Color themeColor) {
            super(text);
            this.themeColor = themeColor;
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(0, 110));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            updateTextAndFont(text);
            addActionListener(e -> checkAnswer(this));
        }

        private void updateTextAndFont(String text) {
            int fontSize = 22;
            if (text.length() > 80) fontSize = 14;
            else if (text.length() > 40) fontSize = 17;

            String htmlText = "<html><body style='width: 250px; text-align: center; " +
                    "font-family: Segoe UI Semibold; font-size: " + fontSize + "pt;'>" +
                    text + "</body></html>";
            super.setText(htmlText);
        }

        @Override
        public void setText(String text) { updateTextAndFont(text); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (currentFeedbackColor != null) {
                g2.setColor(isBlinking ? new Color(30, 30, 30, 180) : currentFeedbackColor);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 120));
            } else {
                g2.setColor(new Color(30, 30, 30, 150));
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

            super.paintComponent(g);
            g2.dispose();
        }

        public void setCorrectGlow() {
            this.currentFeedbackColor = new Color(46, 204, 113, 200);
            this.isBlinking = false;
            repaint();
        }

        public void startErrorBlink() {
            this.currentFeedbackColor = new Color(231, 76, 60, 200);
            javax.swing.Timer blinkTimer = new javax.swing.Timer(200, null);

            blinkTimer.addActionListener(e -> {
                isBlinking = !isBlinking;
                repaint();
            });
            blinkTimer.start();

            new javax.swing.Timer(1200, e -> {
                blinkTimer.stop();
                isBlinking = false;
                repaint();
            }).start();
        }

        public void resetVisual() {
            this.currentFeedbackColor = null;
            this.isBlinking = false;
            setOpaque(false);
            repaint();
        }
    }

    class CircleMasteryPanel extends JPanel {
        private int progress = 10;
        public CircleMasteryPanel() {
            setOpaque(false);
        }
        public void setProgress(int p) {
            this.progress = p;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean isDark = false;
            if (UIManager.getLookAndFeel() != null) {
                isDark = UIManager.getLookAndFeel().getClass().getName().contains("Dark");
            }

            int w = getWidth();
            int h = getHeight();

            int availableHeight = h - 40;
            int diameter = Math.min(w, availableHeight) - 20;

            if (diameter < 0) diameter = 0;

            int x = (w - diameter) / 2;
            int y = (availableHeight - diameter) / 2 + 5;

            g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(currentAccentColor.getRed(), currentAccentColor.getGreen(), currentAccentColor.getBlue(), 40));
            g2.drawArc(x, y, diameter, diameter, 225, -270);

            g2.setColor(currentAccentColor);
            int arcAngle = (int) ((progress / 100.0) * 270);
            g2.drawArc(x, y, diameter, diameter, 225, -arcAngle);

            g2.setColor(isDark ? Color.WHITE : Color.BLACK);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 36));
            FontMetrics fm = g2.getFontMetrics();
            String bigText = progress + "%";
            int textX = x + (diameter - fm.stringWidth(bigText)) / 2;
            int textY = y + (diameter / 2) + 10;
            g2.drawString(bigText, textX, textY);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g2.setColor(isDark ? new Color(200, 200, 200) : new Color(80, 80, 80));
            String subText = "Solved";
            fm = g2.getFontMetrics();
            g2.drawString(subText, x + (diameter - fm.stringWidth(subText)) / 2, textY + 30);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(isDark ? Color.GRAY : new Color(100, 100, 100));
            String bottomText = "0 Attempting";
            fm = g2.getFontMetrics();
            g2.drawString(bottomText, x + (diameter - fm.stringWidth(bottomText)) / 2, y + diameter + 30);
        }
    }
    // NEW: Dedicated Mastery Ring for the Quiz Sidebar (Transparent & Color-Adaptive)
    class QuizMasteryRing extends JPanel {
        private int progress = 10;
        private Color ringColor = new Color(46, 204, 113); // Default Green

        public QuizMasteryRing() {
            setOpaque(false); // Makes the background perfectly match the sidebar
            setPreferredSize(new Dimension(160, 160));
            setMaximumSize(new Dimension(160, 160));
        }

        public void setProgress(int p, Color c) {
            this.progress = p;
            this.ringColor = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean isDark = UIManager.getLookAndFeel() != null && UIManager.getLookAndFeel().getClass().getName().contains("Dark");

            int w = getWidth();
            int h = getHeight();
            int strokeWidth = 14; // Thickness of the ring
            int diameter = Math.min(w, h) - (strokeWidth * 2);
            int x = (w - diameter) / 2;
            int y = (h - diameter) / 2;

            // Draw Background Track
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(isDark ? new Color(60, 60, 60) : new Color(220, 220, 220));
            g2.drawArc(x, y, diameter, diameter, 0, 360);

            // Draw Dynamic Colored Foreground Arc
            g2.setColor(ringColor);
            int arcAngle = (int) ((progress / 100.0) * 360);
            g2.drawArc(x, y, diameter, diameter, 90, -arcAngle); // 90 starts at top dead center

            // Center Percentage Text
            g2.setColor(isDark ? Color.WHITE : Color.BLACK);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 36));
            String bigText = progress + "%";
            FontMetrics fm = g2.getFontMetrics();
            int textX = x + (diameter - fm.stringWidth(bigText)) / 2;
            int textY = y + (diameter / 2) + (fm.getAscent() / 3) + 2;
            g2.drawString(bigText, textX, textY);
        }
    }
    private static class QuizItem {
        String id;
        String questionText;
        String displaySentence;
        String correctAnswer;
        String userProvidedAnswer = "";
        List<String> options;
        String originalContext;

        public QuizItem(String q, String display, String a, List<String> opts, String context) {
            this.id = q.hashCode() + "" + a.hashCode();
            this.questionText = q;
            this.displaySentence = display;
            this.correctAnswer = a;
            this.options = opts;
            this.originalContext = context;
        }
    }

    private class BackgroundPanel extends JPanel {
        private ImageIcon gifIcon;
        public BackgroundPanel(String path) {
            File f = new File(path);
            if (f.exists()) this.gifIcon = new ImageIcon(path);
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            if (gifIcon != null) g2.drawImage(gifIcon.getImage(), 0, 0, getWidth(), getHeight(), this);
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    class CircularTimer extends JComponent {
        private int seconds = 0;
        private final int MAX_SECONDS = 3600;
        private final int INCREMENT = 300;
        private boolean isRunning = false;
        private boolean isEditable = true;

        public void setEditable(boolean editable) { this.isEditable = editable; } // <-- NEW SETTER

        public CircularTimer() {
            setPreferredSize(new Dimension(220, 220));
            setMaximumSize(new Dimension(220, 220));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEditable) return;
                    if (e.getPoint().distance(getWidth()/2.0, getHeight()/2.0) < 45) showCustomInputDialog();
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isRunning || !isEditable) return;
                    double dx = e.getX() - getWidth() / 2.0;
                    double dy = e.getY() - getHeight() / 2.0;
                    double angle = Math.atan2(dy, dx);
                    double normalized = (angle + Math.PI / 2.0) / (2.0 * Math.PI);
                    if (normalized < 0) normalized += 1.0;
                    int rawSeconds = (int) (normalized * MAX_SECONDS);
                    seconds = (rawSeconds / INCREMENT) * INCREMENT;
                    if (seconds == 0 && normalized > 0.1) seconds = MAX_SECONDS;
                    userSelectedTime = seconds;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void showCustomInputDialog() {
            String input = JOptionPane.showInputDialog(this, "Enter minutes (1-60):", "Custom Timer", JOptionPane.PLAIN_MESSAGE);
            try {
                int mins = Integer.parseInt(input);
                if (mins > 0 && mins <= 60) {
                    seconds = mins * 60;
                    userSelectedTime = seconds;
                    repaint();
                }
            } catch (Exception ex) { }
        }

        public void setSeconds(int s) { this.seconds = s; repaint(); }
        public int getSeconds() { return this.seconds; }
        public void setRunning(boolean r) { this.isRunning = r; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int radius = Math.min(getWidth(), getHeight()) / 2 - 25;
            Color themeColor = (isRunning && seconds > 0 && seconds <= 60) ? new Color(255, 45, 85) : currentAccentColor;

            for (int i = 0; i < 60; i++) {
                double angle = Math.toRadians(i * 6 - 90);
                int lineStart = (i % 5 == 0) ? radius - 12 : radius - 6;
                g2.setColor(i % 5 == 0 ? new Color(255, 255, 255, 180) : new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(i % 5 == 0 ? 2f : 1f));
                g2.drawLine((int)(centerX + lineStart * Math.cos(angle)), (int)(centerY + lineStart * Math.sin(angle)),
                        (int)(centerX + radius * Math.cos(angle)), (int)(centerY + radius * Math.sin(angle)));
            }

            int extent = (int) (((double) seconds / MAX_SECONDS) * 360);
            g2.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 40));
            g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, 90, -extent);

            g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(themeColor);
            g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, 90, -extent);

            if (isRunning) {
                double minAngle = Math.toRadians(((double) seconds / MAX_SECONDS * 360) - 90);
                int mHandLen = radius - 20;
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(centerX, centerY, (int)(centerX + mHandLen * Math.cos(minAngle)), (int)(centerY + mHandLen * Math.sin(minAngle)));

                int currentSecOfMin = seconds % 60;
                double secAngle = Math.toRadians((currentSecOfMin * 6) - 90);
                int sHandLen = radius - 10;
                g2.setColor(new Color(255, 45, 85));
                g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(centerX, centerY, (int)(centerX + sHandLen * Math.cos(secAngle)), (int)(centerY + sHandLen * Math.sin(secAngle)));
            }

            double angle = Math.toRadians(((double) seconds / MAX_SECONDS * 360) - 90);
            int knobX = (int) (centerX + radius * Math.cos(angle));
            int knobY = (int) (centerY + radius * Math.sin(angle));
            g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 80));
            g2.fillOval(knobX - 10, knobY - 10, 20, 20);
            g2.setColor(Color.WHITE);
            g2.fillOval(knobX - 6, knobY - 6, 12, 12);

            int innerR = 48;
            g2.setColor(new Color(30, 30, 30));
            g2.fillOval(centerX - innerR, centerY - innerR, innerR * 2, innerR * 2);
            g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 120));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawOval(centerX - innerR, centerY - innerR, innerR * 2, innerR * 2);

            g2.setFont(new Font("Segoe UI Black", Font.BOLD, 22));
            g2.setColor(Color.WHITE);
            String timeStr = (seconds < 60 && isRunning) ? seconds + "s" : (seconds / 60) + "m";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(timeStr, centerX - (fm.stringWidth(timeStr) / 2), centerY + (fm.getAscent() / 3));

            g2.dispose();
        }
    }

    private class TeacherQuizConfigDialog extends JDialog {
        private JSpinner easySpin, medSpin, hardSpin;
        private JTextField quizTitleField;

        public TeacherQuizConfigDialog(Frame owner) {
            super(owner, "Quiz Generator (Teacher Mode)", true);
            setSize(450, 500);
            setLocationRelativeTo(owner);
            setResizable(false);

            JPanel panel = new JPanel(new GridLayout(0, 1, 10, 15)) {
                @Override
                protected void paintComponent(Graphics g) {
                    setBackground(isDarkMode() ? new Color(30, 30, 30) : new Color(245, 245, 250));
                    super.paintComponent(g);
                }
            };
            panel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

            autoAddLabel(panel, "QUIZ TITLE");
            quizTitleField = new JTextField("Weekly Assessment - " + selectedSubject) {
                @Override
                protected void paintComponent(Graphics g) {
                    setBackground(isDarkMode() ? new Color(45, 45, 45) : Color.WHITE);
                    setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    setCaretColor(isDarkMode() ? Color.WHITE : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            panel.add(quizTitleField);

            autoAddLabel(panel, "NUMBER OF EASY QUESTIONS");
            easySpin = new JSpinner(new SpinnerNumberModel(5, 0, 50, 1));
            panel.add(easySpin);

            autoAddLabel(panel, "NUMBER OF MEDIUM QUESTIONS");
            medSpin = new JSpinner(new SpinnerNumberModel(5, 0, 50, 1));
            panel.add(medSpin);

            autoAddLabel(panel, "NUMBER OF HARD QUESTIONS");
            hardSpin = new JSpinner(new SpinnerNumberModel(2, 0, 50, 1));
            panel.add(hardSpin);

            JButton btnGenerate = new JButton("SCAN & EXPORT PDF");
            btnGenerate.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btnGenerate.setBackground(new Color(46, 204, 113));
            btnGenerate.setForeground(Color.WHITE);
            btnGenerate.addActionListener(e -> {
                dispose();
                startTeacherWorkflow(quizTitleField.getText(),
                        (int)easySpin.getValue(), (int)medSpin.getValue(), (int)hardSpin.getValue());
            });
            panel.add(btnGenerate);
            add(panel);
        }

        private void autoAddLabel(JPanel p, String txt) {
            JLabel l = new JLabel(txt) {
                @Override
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? Color.GRAY : Color.DARK_GRAY);
                    super.paintComponent(g);
                }
            };
            l.setFont(new Font("Segoe UI", Font.BOLD, 10));
            p.add(l);
        }
    }

    private class QuizConfigDialog extends JDialog {
        private JRadioButton theoryOpt, numericalOpt, mixedOpt;
        private JSpinner theorySpin, numericalSpin;
        private boolean confirmed = false;

        public QuizConfigDialog(Frame owner) {
            super(owner, "Quiz Configuration", true);
            setSize(400, 450);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());

            JPanel panel = new JPanel(new GridLayout(0, 1, 10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

            theoryOpt = new JRadioButton("Pure Theory");
            numericalOpt = new JRadioButton("Pure Numerical");
            mixedOpt = new JRadioButton("Mixed (Theory + Numerical)");

            ButtonGroup group = new ButtonGroup();
            group.add(theoryOpt); group.add(numericalOpt); group.add(mixedOpt);

            theorySpin = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
            numericalSpin = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));

            // Initial state: Disabled until a radio button is clicked
            theorySpin.setEnabled(false);
            numericalSpin.setEnabled(false);

            // Selection Logic: Enable only relevant spinners
            theoryOpt.addActionListener(e -> { theorySpin.setEnabled(true); numericalSpin.setEnabled(false); });
            numericalOpt.addActionListener(e -> { theorySpin.setEnabled(false); numericalSpin.setEnabled(true); });
            mixedOpt.addActionListener(e -> { theorySpin.setEnabled(true); numericalSpin.setEnabled(true); });

            panel.add(new JLabel("Study Mode:"));
            panel.add(theoryOpt);
            panel.add(new JLabel("Theory Question Count:"));
            panel.add(theorySpin);
            panel.add(new JLabel("----------------------------"));
            panel.add(numericalOpt);
            panel.add(mixedOpt);
            panel.add(new JLabel("Numerical Question Count:"));
            panel.add(numericalSpin);

            JButton btnStart = new JButton("CONFIRM SETTINGS");
            btnStart.addActionListener(e -> {
                if(!theoryOpt.isSelected() && !numericalOpt.isSelected() && !mixedOpt.isSelected()) {
                    JOptionPane.showMessageDialog(this, "Please select a mode.");
                    return;
                }
                confirmed = true;
                dispose();
            });

            add(panel, BorderLayout.CENTER);
            add(btnStart, BorderLayout.SOUTH);
        }

        public boolean isConfirmed() { return confirmed; }
        public boolean isTheory() { return theoryOpt.isSelected() || mixedOpt.isSelected(); }
        public boolean isNumerical() { return numericalOpt.isSelected() || mixedOpt.isSelected(); }
        public int getTheoryCount() { return theoryOpt.isSelected() || mixedOpt.isSelected() ? (int)theorySpin.getValue() : 0; }
        public int getNumericalCount() { return numericalOpt.isSelected() || mixedOpt.isSelected() ? (int)numericalSpin.getValue() : 0; }
    }

    private class SplashScreen extends JDialog {
        public SplashScreen() {
            setUndecorated(true);
            setSize(800, 480);
            setLocationRelativeTo(null);

            ImageIcon backgroundGif = new ImageIcon("background.gif");
            JLayeredPane layeredPane = new JLayeredPane();
            layeredPane.setPreferredSize(new Dimension(800, 480));

            JPanel contentPane = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (backgroundGif != null) g.drawImage(backgroundGif.getImage(), 0, 0, getWidth(), getHeight(), this);
                }
            };
            contentPane.setBounds(0, 0, 800, 480);

            JButton quitBtn = new JButton("<html><div style='text-shadow: 1px 1px 2px #000000;'> Quit X</div></html>");
            quitBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            quitBtn.setForeground(Color.WHITE);
            quitBtn.setContentAreaFilled(false);
            quitBtn.setBorderPainted(false);
            quitBtn.setFocusPainted(false);
            quitBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            quitBtn.setBounds(700, 10, 80, 30);
            quitBtn.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(this, "Are you sure you want to exit PaLO?", "Quit Application", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) System.exit(0);
            });

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new java.awt.Insets(40, 60, 40, 60);

            JLabel titleLabel = new JLabel("<html><div style='text-shadow: 3px 3px 6px #000000;'>PaLO</div></html>");
            titleLabel.setFont(new Font("Serif", Font.PLAIN, 110));
            titleLabel.setForeground(Color.WHITE);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 0.5;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            contentPane.add(titleLabel, gbc);

            String fullNameHtml = "<html><div style='text-shadow: 2px 2px 4px #000000;'>" + "Progressive and<br>Audio assisted<br>Learning<br>Orchestrator</div></html>";
            JLabel fullNameLabel = new JLabel(fullNameHtml);
            fullNameLabel.setFont(new Font("Serif", Font.PLAIN, 28));
            fullNameLabel.setForeground(Color.WHITE);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 1.0; gbc.weighty = 0.5;
            gbc.anchor = GridBagConstraints.SOUTHWEST;
            contentPane.add(fullNameLabel, gbc);

            JLabel initLabel = new JLabel("<html><div style='text-shadow: 1px 1px 3px #000000;'>" + "Initializing Orchestrators.....</div></html>");
            initLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            initLabel.setForeground(new Color(230, 230, 230));
            gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0; gbc.weighty = 0.5;
            gbc.anchor = GridBagConstraints.SOUTHEAST;
            contentPane.add(initLabel, gbc);

            layeredPane.add(contentPane, JLayeredPane.DEFAULT_LAYER);
            layeredPane.add(quitBtn, JLayeredPane.PALETTE_LAYER);
            add(layeredPane, BorderLayout.CENTER);
        }
    }

    private JPanel createAudioPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 0));
        mainPanel.setBackground(new Color(28, 28, 28));

        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(300, 800));
        sidebar.setBackground(new Color(35, 35, 35));
        sidebar.setBorder(BorderFactory.createEmptyBorder(25, 15, 25, 15));

        JLabel titleLabel = new JLabel("Audio Assisted Mode");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(titleLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

        JButton btnStartVoice = new JButton("Start Voice Recognition");
        btnStartVoice.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStartVoice.setMaximumSize(new Dimension(240, 50));
        btnStartVoice.setFont(new Font("Segoe UI Black", Font.BOLD, 14));
        btnStartVoice.setBackground(new Color(52, 152, 219));
        btnStartVoice.setForeground(Color.WHITE);
        btnStartVoice.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnStartVoice.addActionListener(e -> startVoiceRecognition());
        sidebar.add(btnStartVoice);

        JPanel audioContent = new JPanel(new BorderLayout());
        audioContent.setOpaque(false);
        JLabel status = new JLabel("<html><center>🎙<br>Audio Mode Ready</center></html>", SwingConstants.CENTER);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        status.setForeground(Color.WHITE);
        audioContent.add(status, BorderLayout.CENTER);

        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(audioContent, BorderLayout.CENTER);

        return mainPanel;
    }

    public void switchToFocusMode() {
        isChatMode = false;
        isAudioMode = false;
        focusPanel.removeAll();
        focusPanel.add(createFocusPanel(), BorderLayout.CENTER);
        cardLayout.show(mainCardPanel, "FOCUS");
        this.dispose();
        this.setUndecorated(true);
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.setVisible(true);
        focusPanel.revalidate();
        focusPanel.repaint();
    }

    private void initializeIntegratedUI() {
        initializeComponents();
        cardLayout = new CardLayout();
        mainCardPanel = new JPanel(cardLayout);
        mainCardPanel.setBackground(new Color(28, 28, 28));

        focusPanel = new JPanel(new BorderLayout());
        focusPanel.setBackground(new Color(18, 18, 18));
        mainCardPanel.add(focusPanel, "FOCUS");

        setTitle("PaLO - Adaptive Learning Orchestrator");

        // CRITICAL: Prevent the window from closing automatically so the save logic can finish
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Center the dialog on the frame
                int response = JOptionPane.showConfirmDialog(
                        OfflineTutorApp.this,
                        "Exit PaLO entirely? Your chat history and progress will be saved.",
                        "Confirm Exit",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (response == JOptionPane.YES_OPTION) {
                    // Ensure all orchestrations are backed up
                    saveChatHistory();
                    saveProgress();
                    System.exit(0);
                }
            }
        });

        JPanel blankPlaceholder = new JPanel();
        blankPlaceholder.setBackground(new Color(28, 28, 28));
        mainCardPanel.add(blankPlaceholder, "BLANK");

        // Initialize individual module panels
        quizPanel = createQuizPanel();
        mainCardPanel.add(quizPanel, "QUIZ");

        chatPanel = createChatPanel();
        mainCardPanel.add(chatPanel, "CHAT");

        audioPanel = createAudioPanel();
        mainCardPanel.add(audioPanel, "AUDIO");

        setLayout(new BorderLayout());
        add(mainCardPanel, BorderLayout.CENTER);

        // Default to a clean slate before the Dashboard takes over
        cardLayout.show(mainCardPanel, "BLANK");
        this.setVisible(false);
    }

    public void syncThemeBackgrounds() {
        boolean isDark = UIManager.getLookAndFeel().getClass().getName().contains("Dark");
        Color bg = isDark ? new Color(28, 28, 28) : new Color(245, 245, 250);

        if (mainCardPanel != null) mainCardPanel.setBackground(bg);
        if (quizPanel != null) quizPanel.setBackground(bg);
        if (chatPanel != null) chatPanel.setBackground(bg);
        if (focusPanel != null) focusPanel.setBackground(bg);

        SwingUtilities.updateComponentTreeUI(this);
    }

    private JPanel createFocusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        boolean isDark = isDarkMode();
        panel.setBackground(isDark ? new Color(18, 18, 18) : new Color(245, 245, 250));

        JLabel title = new JLabel("SELECT OPTIONS", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
        title.setBorder(BorderFactory.createEmptyBorder(100, 0, 40, 0));
        panel.add(title, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        JPanel grid = new JPanel(new GridLayout(2, 2, 25, 25));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(800, 350));

        JButton pomodoro = createTechniqueButton("POMODORO", "25m Work | 5m Rest", new Color(231, 76, 60));
        JButton flowState = createTechniqueButton("FLOW STATE", "90m Deep Work | 15m Rest", new Color(52, 152, 219));
        JButton rule5217 = createTechniqueButton("52 / 17 RULE", "Science-backed Productivity", new Color(46, 204, 113));
        JButton customZen = createTechniqueButton("CUSTOM ZEN", "Set your own intervals", new Color(155, 89, 182));

        pomodoro.addActionListener(e -> startFocusTimerInternal(25, 5));
        flowState.addActionListener(e -> startFocusTimerInternal(90, 15));
        rule5217.addActionListener(e -> startFocusTimerInternal(52, 17));
        customZen.addActionListener(e -> launchCustomFocus());

        grid.add(pomodoro); grid.add(flowState);
        grid.add(rule5217); grid.add(customZen);

        centerWrapper.add(grid);
        panel.add(centerWrapper, BorderLayout.CENTER);

        JButton backBtn = new JButton("BACK TO MENU");
        backBtn.setForeground(isDarkMode() ? Color.LIGHT_GRAY : new Color(80, 80, 80));
        backBtn.setContentAreaFilled(false);
        backBtn.setBorderPainted(false);
        backBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backBtn.addActionListener(e -> {
            this.dispose();
            this.setUndecorated(false);
            this.setExtendedState(JFrame.NORMAL);
            this.setSize(1400, 850);
            this.setLocationRelativeTo(null);
            SwingUtilities.updateComponentTreeUI(this);
            this.setVisible(true);
            new PaLOHomePage(this).setVisible(true);
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 80, 0));
        bottomPanel.add(backBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void launchCustomFocus() {
        String w = JOptionPane.showInputDialog(this, "Work minutes:", "Custom Zen", JOptionPane.PLAIN_MESSAGE);
        String r = JOptionPane.showInputDialog(this, "Rest minutes:", "Custom Zen", JOptionPane.PLAIN_MESSAGE);
        try {
            int wm = Integer.parseInt(w);
            int rm = Integer.parseInt(r);
            startFocusTimerInternal(wm, rm);
        } catch (Exception ex) { }
    }

    private void startFocusTimerInternal(int workMins, int restMins) {
        if (focusPanel == null) return;
        focusPanel.removeAll();

        BackgroundPanel sessionBG = new BackgroundPanel("assets/focus_bg.jpg");
        sessionBG.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel statusLabel = new JLabel("DEEP FOCUS ACTIVE");
        statusLabel.setFont(new Font("Segoe UI Black", Font.BOLD, 32));
        statusLabel.setForeground(currentAccentColor);

        CircularTimer timer = new CircularTimer();
        timer.setSeconds(workMins * 60);
        timer.setRunning(true);

        JButton btnAbort = new JButton("ABORT SESSION");
        btnAbort.setForeground(isDarkMode() ? Color.GRAY : Color.DARK_GRAY);
        btnAbort.setContentAreaFilled(false);
        btnAbort.addActionListener(e -> switchToFocusMode());

        javax.swing.Timer countdown = new javax.swing.Timer(1000, e -> {
            if (timer.getSeconds() > 0) {
                timer.setSeconds(timer.getSeconds() - 1);
            } else {
                ((javax.swing.Timer)e.getSource()).stop();
                playNotificationBell();
                triggerRestModeInternal(restMins);
            }
        });

        gbc.gridy = 0; gbc.insets = new Insets(0, 0, 20, 0);
        sessionBG.add(statusLabel, gbc);
        gbc.gridy = 1;
        sessionBG.add(timer, gbc);
        gbc.gridy = 2;
        sessionBG.add(btnAbort, gbc);

        focusPanel.add(sessionBG, BorderLayout.CENTER);
        focusPanel.revalidate();
        focusPanel.repaint();
        countdown.start();
    }

    private void initializeComponents() {
        homeCircleMastery = new CircleMasteryPanel();
        quizMasteryRing = new QuizMasteryRing();
        clockTimer = new CircularTimer();
        String[] subjects = {"Computer Science", "Mathematics", "AI Concepts", "Data Science"};
        subjectDropdown = new JComboBox<>(subjects);
        subjectDropdown.setBackground(new Color(50, 50, 50));
        subjectDropdown.setForeground(Color.WHITE);
        loadingOverlay = new JPanel(new GridBagLayout());
        loadingOverlay.setBackground(new Color(0, 0, 0, 150)); // Semi-transparent black
        JLabel loadingText = new JLabel("PaLO is Orchestrating Numericals...");
        loadingText.setForeground(Color.WHITE);
        loadingText.setFont(new Font("Segoe UI", Font.BOLD, 20));
        loadingOverlay.add(loadingText);
        loadingOverlay.setVisible(false);
        questionCountSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 30, 1));
        JComponent editor = questionCountSpinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
            textField.setBackground(new Color(50, 50, 50));
            textField.setForeground(Color.WHITE);
            textField.setCaretColor(Color.WHITE);
            textField.setEditable(true);
        }

        chatInput = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                setBackground(isDarkMode() ? Color.BLACK : Color.WHITE);
                setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                setCaretColor(isDarkMode() ? Color.WHITE : Color.BLACK);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isDarkMode() ? Color.BLACK : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                if (hasFocus() || getMousePosition() != null) {
                    float[] fractions = {0.0f, 0.5f, 1.0f};
                    Color[] colors = {new Color(0, 255, 255), new Color(255, 0, 255), new Color(255, 165, 0)};
                    g2.setPaint(new LinearGradientPaint(0, 0, getWidth(), getHeight(), fractions, colors));
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 25, 25);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };

        sendButton = new JButton();

        chatBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                setBackground(isDarkMode() ? new Color(28, 28, 28) : new Color(245, 245, 250));
                super.paintComponent(g);
            }
        };

        chatArea = new JTextArea();

        difficultyLabel = new JLabel("Question #1 | Current Level: EASY", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                super.paintComponent(g);
            }
        };

        questionArea = new JTextArea() {
            @Override
            protected void paintComponent(Graphics g) {
                setBackground(isDarkMode() ? new Color(28, 28, 28) : new Color(245, 245, 250));
                setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                super.paintComponent(g);
            }
        };
        questionArea.setEditable(false);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);

        btnA = createOptionButton("-", new Color(52, 152, 219));
        btnB = createOptionButton("-", new Color(155, 89, 182));
        btnC = createOptionButton("-", new Color(46, 204, 113));
        btnD = createOptionButton("-", new Color(241, 196, 15));
        attachVoice(btnA);
        attachVoice(btnB);
        attachVoice(btnC);
        attachVoice(btnD);

        questionArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentQuestion != null) {
                    tts.speak(currentQuestion.questionText);
                }
            }
        });
        aiStatusLabel = new JLabel("AI Status: Ready", SwingConstants.CENTER);
        aiStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        aiStatusLabel.setForeground(Color.LIGHT_GRAY);
    }

    public void switchToQuizMode() {
        isChatMode = false;
        isAudioMode = false;
        animatePanelTransition("QUIZ");
    }

    public void switchToChatMode() {
        isChatMode = true;
        isAudioMode = false;
        animatePanelTransition("CHAT");
    }

    public void switchToAudioMode() {
        isChatMode = false;
        isAudioMode = true;
        animatePanelTransition("AUDIO");
    }

    private void animatePanelTransition(String targetPanel) {
        cardLayout.show(mainCardPanel, targetPanel);
        revalidate();
        repaint();
    }

    private JPanel createQuizPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 0));

        JPanel sidebar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                setBackground(isDarkMode() ? new Color(35, 35, 35) : new Color(230, 230, 235));
                super.paintComponent(g);
            }
        };
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(300, 850));
        sidebar.setBorder(BorderFactory.createEmptyBorder(25, 20, 25, 20));

        // 1. MASTERY CONTAINER (Hidden until quiz starts)
        masteryContainer = new JPanel();
        masteryContainer.setLayout(new BoxLayout(masteryContainer, BoxLayout.Y_AXIS));
        masteryContainer.setOpaque(false);

        JLabel mTitle = new JLabel("OVERALL MASTERY");
        mTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        mTitle.setForeground(Color.GRAY);
        mTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        quizMasteryRing.setAlignmentX(Component.CENTER_ALIGNMENT);

        aiStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        masteryContainer.add(mTitle);
        masteryContainer.add(Box.createRigidArea(new Dimension(0, 15)));
        masteryContainer.add(quizMasteryRing);
        masteryContainer.add(Box.createRigidArea(new Dimension(0, 15))); // Balanced spacing
        masteryContainer.add(aiStatusLabel); // Status is placed cleanly below the circle

        masteryContainer.setVisible(false); // Hidden by default

        sidebar.add(masteryContainer);
        sidebar.add(Box.createRigidArea(new Dimension(0, 35))); // Pushes timer slightly down

        // 2. TIMER
        addSidebarSection(sidebar, "QUIZ DURATION", clockTimer);
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        timerLabel = new JLabel("Timer: Off", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        timerLabel.setForeground(Color.LIGHT_GRAY);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(timerLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 25)));

        // --- HIDDEN DURING QUIZ BLOCK ---
        countLabel = new JLabel("QUESTIONS TO GENERATE");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        countLabel.setForeground(Color.GRAY);
        countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(countLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));

        btnConfigQuiz = new JButton("CONFIGURE QUIZ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? new Color(155, 89, 182) : Color.DARK_GRAY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        styleSidebarButton(btnConfigQuiz);
        btnConfigQuiz.addActionListener(e -> {
            QuizConfigDialog dialog = new QuizConfigDialog(this);
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                targetTheoryCount = dialog.getTheoryCount();
                targetNumericalCount = dialog.getNumericalCount();
                updateStatus("Ready: " + (targetTheoryCount + targetNumericalCount) + " Questions");
            }
        });
        sidebar.add(btnConfigQuiz);

        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(loadingBar);

        questionCountSpinner.setMaximumSize(new Dimension(240, 40));
        questionCountSpinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(Box.createRigidArea(new Dimension(0, 25)));

        btnScan = new JButton("SCAN TEXTBOOK") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 229, 255)); // Electric Cyan
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        styleSidebarButton(btnScan);
        btnScan.addActionListener(e -> performScan());
        attachVoice(btnScan);
        sidebar.add(btnScan);
        sidebar.add(Box.createRigidArea(new Dimension(0, 15)));
        // --------------------------------

        // 3. VOICE SPEED & PITCH
        autoAddSidebarLabel(sidebar, "VOICE SPEED (RATE)");
        JSlider rateSlider = createModernSlider(100, 200, 130);
        rateSlider.addChangeListener(e -> tts.setRate(rateSlider.getValue()));
        rateSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                int percent = (int)((rateSlider.getValue() - 100) / (200.0 - 100.0) * 100);
                tts.speak("Speed set to " + percent + " percent");
            }
        });
        sidebar.add(rateSlider);
        sidebar.add(Box.createRigidArea(new Dimension(0, 15)));

        autoAddSidebarLabel(sidebar, "VOICE PITCH");
        JSlider pitchSlider = createModernSlider(50, 150, 110);
        pitchSlider.addChangeListener(e -> tts.setPitch(pitchSlider.getValue()));
        pitchSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                int percent = (int)((pitchSlider.getValue() - 50) / (150.0 - 50.0) * 100);
                tts.speak("Pitch set to " + percent + " percent");
            }
        });
        sidebar.add(pitchSlider);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));

        // 4. TEST VOICE
        JButton btnTestVoice = new JButton("TEST VOICE") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(100, 100, 100) : new Color(60, 60, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        styleSidebarButton(btnTestVoice);
        attachVoice(btnTestVoice);
        btnTestVoice.addActionListener(e -> {
            tts.speak("Testing voice speed and pitch. Is this comfortable for you?");
        });
        sidebar.add(btnTestVoice);
        sidebar.add(Box.createRigidArea(new Dimension(0, 25)));

        // 5. END QUIZ
        JButton btnEndQuiz = new JButton("END QUIZ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(255, 82, 82) : new Color(255, 82, 82, 180));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        styleSidebarButton(btnEndQuiz);
        btnEndQuiz.addActionListener(e -> {
            if (quizTimer != null) quizTimer.stop();
            if (clockTimer != null) {
                clockTimer.setRunning(false);
                clockTimer.setSeconds(0);
            }
            // Save current session's graph data to the most recent file
            if (!recentFilesList.isEmpty() && !studentHistory.isEmpty()) {
                JsonObject lastFile = recentFilesList.get(0);
                JsonArray histArray = new JsonArray();
                for (Float score : studentHistory) histArray.add(score);
                lastFile.add("history", histArray);
                saveRecentFiles();
            }
            stopZenMusic();
            if (!sessionLog.isEmpty()) showPerformanceReport();
            saveProgress();
            easyQuestions.clear(); easyMediumQuestions.clear(); mediumQuestions.clear();
            mediumHardQuestions.clear(); hardQuestions.clear(); expertQuestions.clear();
            askedQuestionIDs.clear(); sessionLog.clear();
            questionCounter = 0; currentQuestion = null; currentLevel = "EASY";
            questionArea.setText("");
            difficultyLabel.setText("Question #1 | Current Level: EASY");
            difficultyLabel.setForeground(Color.WHITE);
            resetButtonStyles();
            btnA.setEnabled(false); btnB.setEnabled(false); btnC.setEnabled(false); btnD.setEnabled(false);
            btnA.setText("-"); btnB.setText("-"); btnC.setText("-"); btnD.setText("-");

            // --- RESTORE UI COMPONENTS AFTER QUIZ ---
            btnConfigQuiz.setVisible(true);
            btnScan.setVisible(true);
            countLabel.setVisible(true);
            questionCountSpinner.setVisible(true);
            clockTimer.setEditable(true);
            masteryContainer.setVisible(false);

            this.setVisible(false);
            if (cardLayout != null) cardLayout.show(mainCardPanel, "BLANK");
            SwingUtilities.invokeLater(() -> {
                homePageInstance = new PaLOHomePage(this);
                homePageInstance.setVisible(true);
            });
        });
        sidebar.add(btnEndQuiz);
        attachVoice(btnEndQuiz);
        sidebar.add(Box.createVerticalGlue());

        JPanel quizContent = new JPanel(new BorderLayout(20, 20));
        quizContent.setOpaque(false);
        quizContent.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 30));
        difficultyLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 18));
        questionArea.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        questionArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(25, 30, 25, 30)
        ));

        JPanel btnGrid = new JPanel(new GridLayout(2, 2, 25, 25));
        btnGrid.setOpaque(false);
        btnGrid.add(btnA); btnGrid.add(btnB);
        btnGrid.add(btnC); btnGrid.add(btnD);

        quizContent.add(difficultyLabel, BorderLayout.NORTH);
        JScrollPane qScroll = new JScrollPane(questionArea);
        qScroll.getViewport().setOpaque(false);
        qScroll.setOpaque(false);
        qScroll.setBorder(null);
        quizContent.add(qScroll, BorderLayout.CENTER);
        quizContent.add(btnGrid, BorderLayout.SOUTH);

        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(quizContent, BorderLayout.CENTER);

        return mainPanel;
    }

    private void styleSidebarButton(JButton btn) {
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(240, 50));
        btn.setPreferredSize(new Dimension(240, 50));
        btn.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private JSlider createModernSlider(int min, int max, int value) {
        JSlider slider = new JSlider(min, max, value);
        slider.setOpaque(false);
        slider.setForeground(currentAccentColor);
        slider.setMaximumSize(new Dimension(240, 40));
        slider.setCursor(new Cursor(Cursor.HAND_CURSOR));
        slider.setMajorTickSpacing((max - min) / 2);
        slider.setPaintTicks(false);
        return slider;
    }

    private JPanel createChatPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 0));

        JPanel sidebar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                setBackground(isDarkMode() ? new Color(35, 35, 35) : new Color(230, 230, 235));
                super.paintComponent(g);
            }
        };
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(260, 850));
        sidebar.setBorder(BorderFactory.createEmptyBorder(25, 20, 25, 20));

        JLabel titleLabel = new JLabel("Talk to AI") {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                super.paintComponent(g);
            }
        };
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(titleLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 25)));

        JButton btnNewChat = createSidebarPillButton("NEW CHAT", new Color(60, 60, 60));
        btnNewChat.setMaximumSize(new Dimension(220, 45));
        btnNewChat.addActionListener(e -> startNewChat());
        sidebar.add(btnNewChat);
        sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

        this.recentChatsPanel = new JPanel();
        recentChatsPanel.setLayout(new BoxLayout(recentChatsPanel, BoxLayout.Y_AXIS));
        recentChatsPanel.setOpaque(false);

        JScrollPane recentScroll = new JScrollPane(recentChatsPanel);
        recentScroll.setOpaque(false);
        recentScroll.getViewport().setOpaque(false);
        recentScroll.setBorder(null);
        sidebar.add(recentScroll);
        sidebar.add(Box.createVerticalGlue());

        JButton btnBack = new JButton("EXIT TO DASHBOARD") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isDarkMode() ? Color.WHITE : new Color(50, 50, 50));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        btnBack.setMaximumSize(new Dimension(220, 50));
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.setForeground(Color.BLACK);
        btnBack.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnBack.setContentAreaFilled(false);
        btnBack.setBorderPainted(false);
        btnBack.setFocusPainted(false);
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> {
            saveChatHistory();
            this.setVisible(false);
            new PaLOHomePage(this).setVisible(true);
        });
        sidebar.add(btnBack);

        JPanel chatContent = new JPanel(new BorderLayout(0, 0));
        chatContent.setOpaque(false);
        chatContent.setBorder(BorderFactory.createEmptyBorder(15, 10, 10, 20));

        chatBox.setLayout(new BoxLayout(chatBox, BoxLayout.Y_AXIS));

        JScrollPane chatScroll = new JScrollPane(chatBox);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        chatScroll.setOpaque(false);
        chatScroll.getViewport().setOpaque(false);

        JPanel inputWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        inputWrapper.setOpaque(false);

        chatInput.setOpaque(false);
        chatInput.setPreferredSize(new Dimension(450, 50));
        chatInput.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        chatInput.addActionListener(e -> sendChatMessage());

        sendButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float[] fractions = {0.0f, 0.5f, 1.0f};
                Color[] colors = {new Color(0, 255, 255), new Color(255, 0, 255), new Color(255, 165, 0)};
                g2.setPaint(new LinearGradientPaint(0, 0, getWidth(), getHeight(), fractions, colors));
                g2.setStroke(new BasicStroke(getModel().isRollover() ? 3.5f : 2.0f));
                g2.drawRoundRect(2, 2, getWidth()-4, getHeight()-4, 20, 20);
                g2.setColor(isDarkMode() ? new Color(40, 40, 40) : Color.WHITE);
                g2.fillRoundRect(4, 4, getWidth()-8, getHeight()-8, 20, 20);
                g2.setColor(isDarkMode() ? Color.WHITE : Color.BLACK);
                int size = 8;
                Path2D.Double arrow = new Path2D.Double();
                arrow.moveTo(getWidth()/2.0 - size/2.0, getHeight()/2.0 - size);
                arrow.lineTo(getWidth()/2.0 + size, getHeight()/2.0);
                arrow.lineTo(getWidth()/2.0 - size/2.0, getHeight()/2.0 + size);
                arrow.closePath();
                g2.fill(arrow);
                g2.dispose();
            }
        };
        sendButton.setPreferredSize(new Dimension(85, 50));
        sendButton.setContentAreaFilled(false);
        sendButton.setBorderPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendChatMessage());

        inputWrapper.add(chatInput);
        inputWrapper.add(sendButton);

        chatContent.add(chatScroll, BorderLayout.CENTER);
        chatContent.add(inputWrapper, BorderLayout.SOUTH);

        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(chatContent, BorderLayout.CENTER);

        updateRecentChatsUI();
        return mainPanel;
    }

    private class ChatBubble extends JPanel {
        private JTextArea area;
        private float alpha = 0.0f;

        public ChatBubble(String text, boolean isUser) {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(1, 15, 1, 15));

            JLabel avatar = new JLabel(isUser ? "T" : "AI", SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isUser ? new Color(52, 152, 219) : new Color(155, 89, 182));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            avatar.setPreferredSize(new Dimension(32, 32));
            avatar.setMaximumSize(new Dimension(32, 32));
            avatar.setForeground(Color.WHITE);
            avatar.setFont(new Font("Segoe UI", Font.BOLD, 10));

            this.area = new JTextArea(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setEditable(false);
            area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            area.setOpaque(false);
            area.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

            JPanel pill = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2.setColor(isDarkMode() ? (isUser ? new Color(40, 40, 40) : new Color(30, 30, 30)) : (isUser ? new Color(220, 220, 225) : new Color(230, 230, 235)));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.dispose();
                }
            };
            pill.setOpaque(false);
            pill.add(area, BorderLayout.CENTER);

            int maxW = (int)(OfflineTutorApp.this.getWidth() * 0.50);
            pill.setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));

            if (isUser) {
                add(Box.createHorizontalGlue());
                add(pill);
                add(Box.createRigidArea(new Dimension(8, 0)));
                add(avatar);
            } else {
                add(avatar);
                add(Box.createRigidArea(new Dimension(8, 0)));
                add(pill);
                add(Box.createHorizontalGlue());
            }

            new javax.swing.Timer(15, e -> {
                alpha += 0.1f;
                if (alpha >= 1.0f) { alpha = 1.0f; ((javax.swing.Timer)e.getSource()).stop(); }
                repaint();
            }).start();
        }
        public JTextArea getTextArea() { return this.area; }
    }

    private JButton createSidebarPillButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) g2.setColor(accent.darker());
                else if (getModel().isRollover()) g2.setColor(accent.brighter());
                else g2.setColor(accent);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(240, 45));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void startVoiceRecognition() {
        JOptionPane.showMessageDialog(this, "Voice recognition feature coming soon!", "Audio Mode", JOptionPane.INFORMATION_MESSAGE);
    }

    private void initAI() {
        try {
            Path modelPath = Paths.get("tutor_brain.pt");
            if (!modelPath.toFile().exists()) {
                System.err.println("AI Engine: Model file 'tutor_brain.pt' not found. Running in rule-based mode.");
                return;
            }
            Model model = Model.newInstance("AdaptiveTutor");
            model.load(modelPath);
            this.predictor = model.newPredictor(new TutorTranslator());
            SwingUtilities.invokeLater(() -> {
                if (aiStatusLabel != null) aiStatusLabel.setText("AI Engine: Neural Network Loaded");
            });
        } catch (Exception e) {
            System.err.println("AI Initialization Error: " + e.getMessage());
        }
    }

    private void sendChatMessage() {
        String userMessage = chatInput.getText().trim();
        if (userMessage.isEmpty()) return;

        // 1. Add User Bubble immediately to UI
        chatBox.add(new ChatBubble(userMessage, true));
        chatBox.add(Box.createRigidArea(new Dimension(0, 5)));
        chatInput.setText("");

        // 2. Setup AI Response Bubble (Placeholder)
        ChatBubble aiResponseBubble = new ChatBubble("", false);
        chatBox.add(aiResponseBubble);
        chatBox.add(Box.createRigidArea(new Dimension(0, 5)));

        JTextArea aiTextArea = aiResponseBubble.getTextArea();
        aiTextArea.setText("Thinking...");

        // Initial UI Refresh
        chatBox.revalidate();
        chatBox.repaint();
        scrollToBottom();

        // Lock UI to prevent overlapping prompts during generation
        chatInput.setEnabled(false);
        sendButton.setEnabled(false);

        new Thread(() -> {
            try {
                llamaConnection.askStreaming(userMessage, new LlamaConnection.StreamHandler() {
                    boolean firstToken = true;

                    @Override
                    public void handleToken(String token) {
                        SwingUtilities.invokeLater(() -> {
                            if (firstToken) {
                                aiTextArea.setText("");
                                firstToken = false;
                            }
                            aiTextArea.append(token);
                            // Revalidate for long responses to ensure scrollbar moves
                            chatBox.revalidate();
                            scrollToBottom();
                        });
                    }

                    @Override
                    public void handleComplete() {
                        // CRITICAL: Final UI sync before triggering the save logic
                        SwingUtilities.invokeLater(() -> {
                            finishGeneration();

                            // Force a layout refresh so the Save Logic sees all components
                            chatBox.revalidate();
                            chatBox.repaint();

                            // Save the full conversation (User + AI)
                            saveChatHistory();

                            // Update the sidebar with the clean title and time
                            updateRecentChatsUI();
                        });
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    aiTextArea.setText("Error: " + ex.getMessage());
                    finishGeneration();
                });
            }
        }).start();
    }

    private void updateRecentChatsUI() {
        recentChatsPanel.removeAll();
        File folder = new File("chats/");
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        // Sort by last modified so newest chats are at the top
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        // Unified Formatter: Always includes Date then Time
        java.text.SimpleDateFormat dateTimeFormat = new java.text.SimpleDateFormat("dd MMM • hh:mm a");

        for (File f : files) {
            JPanel entryPanel = new JPanel(new BorderLayout(8, 0));
            entryPanel.setOpaque(false);
            entryPanel.setMaximumSize(new Dimension(280, 45));

            // 1. Extract and Clean the Title
            String rawName = f.getName().replace(".json", "");
            String cleanName = rawName.replaceAll("_\\d{8}_\\d{6}$", "").replace("_", " ").trim();

            if (cleanName.length() > 0) {
                cleanName = cleanName.substring(0, 1).toUpperCase() + cleanName.substring(1);
            }

            // 2. Format the Date and Time (Forced for all records)
            long lastModTime = f.lastModified();
            if (lastModTime == 0) lastModTime = System.currentTimeMillis();
            String dateTimeLabel = dateTimeFormat.format(new java.util.Date(lastModTime));

            // 3. Construct Final Label with Truncation
            String displayTitle = cleanName;
            // Tighten truncation to ensure the full date/time fits on the pill button
            if (displayTitle.length() > 9) {
                displayTitle = displayTitle.substring(0, 7) + "..";
            }

            // Result: "Hiii • 15 Apr • 05:51 PM"
            String finalPillLabel = displayTitle + " • " + dateTimeLabel;

            JButton snippet = createSidebarPillButton(finalPillLabel, new Color(50, 50, 50));
            snippet.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            snippet.addActionListener(e -> loadSpecificChat(f));

            // --- Delete Button (X) ---
            JButton btnDel = new JButton("X") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getModel().isRollover() ? new Color(231, 76, 60) : new Color(255, 255, 255, 80));
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, (getHeight()+fm.getAscent())/2 - 2);
                    g2.dispose();
                }
            };
            btnDel.setPreferredSize(new Dimension(30, 45));
            btnDel.setContentAreaFilled(false);
            btnDel.setBorderPainted(false);
            btnDel.setFocusPainted(false);
            btnDel.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btnDel.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Permanently delete this chat history?", "Confirm Delete",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    if (f.delete()) {
                        updateRecentChatsUI();
                        chatBox.removeAll();
                        chatBox.revalidate();
                        chatBox.repaint();
                    }
                }
            });

            entryPanel.add(snippet, BorderLayout.CENTER);
            entryPanel.add(btnDel, BorderLayout.EAST);
            recentChatsPanel.add(entryPanel);
            recentChatsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        // Explicitly refresh UI components
        recentChatsPanel.revalidate();
        recentChatsPanel.repaint();
    }

    private void loadSpecificChat(File file) {
        // 1. Reset UI and update the session pointer
        chatBox.removeAll();
        this.currentChatFile = file; // CRITICAL: Tells the app to save to THIS file for the rest of the session

        try (Scanner scanner = new Scanner(file)) {
            if (!scanner.hasNext()) return;

            String content = scanner.useDelimiter("\\Z").next();
            JsonArray history = JsonParser.parseString(content).getAsJsonArray();

            for (int i = 0; i < history.size(); i++) {
                JsonObject msg = history.get(i).getAsJsonObject();
                String text = msg.get("text").getAsString();
                boolean isUser = msg.get("isUser").getAsBoolean();

                // Re-render the bubbles into the UI
                chatBox.add(new ChatBubble(text, isUser));
                chatBox.add(Box.createRigidArea(new Dimension(0, 5)));
            }

            // 2. Finalize UI update
            chatBox.revalidate();
            chatBox.repaint();
            scrollToBottom();

        } catch (Exception e) {
            System.err.println("Error loading chat file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveChatHistory() {
        if (chatBox == null || chatBox.getComponentCount() == 0) return;

        try {
            File folder = new File("chats/");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            JsonArray history = new JsonArray();
            String firstMsgText = "New_Chat";
            boolean foundFirst = false;

            for (Component c : chatBox.getComponents()) {
                if (c instanceof ChatBubble) {
                    ChatBubble b = (ChatBubble) c;
                    String text = b.getTextArea().getText();
                    if (text == null || text.trim().isEmpty()) continue;

                    if (!foundFirst) {
                        firstMsgText = text.replaceAll("[^a-zA-Z0-9]", "_").trim();
                        if (firstMsgText.length() > 25) firstMsgText = firstMsgText.substring(0, 25);
                        foundFirst = true;
                    }

                    // Reliability Fix: Identify sender by Avatar Label
                    boolean isUser = false;
                    for (Component child : b.getComponents()) {
                        if (child instanceof JLabel) {
                            String labelText = ((JLabel) child).getText();
                            if ("T".equals(labelText)) {
                                isUser = true;
                                break;
                            }
                        }
                    }

                    JsonObject obj = new JsonObject();
                    obj.addProperty("text", text);
                    obj.addProperty("isUser", isUser);
                    history.add(obj);
                }
            }

            if (history.size() == 0) return;

            // SESSION PERSISTENCE LOGIC:
            // Only create a new filename if currentChatFile is null (new session).
            // Otherwise, keep using the same file to prevent sidebar clutter.
            if (currentChatFile == null) {
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                String fileName = firstMsgText + "_" + timestamp + ".json";
                currentChatFile = new File(folder, fileName);
            }

            try (FileWriter writer = new FileWriter(currentChatFile)) {
                writer.write(history.toString());
            }

        } catch (Exception e) {
            System.err.println("Failed to save session: " + e.getMessage());
        }
    }

    private void startNewChat() {
        // 1. Save the existing conversation before clearing the screen
        saveChatHistory();

        // 2. IMPORTANT: Reset the session file pointer.
        // This ensures the NEXT message triggers the creation of a brand new JSON file.
        currentChatFile = null;

        // 3. Clear the UI
        chatBox.removeAll();

        // 4. Add the initial AI greeting
        chatBox.add(new ChatBubble("Hello! How can I help you today?", false));

        // 5. Refresh the sidebar to show the chat we just saved
        updateRecentChatsUI();

        // 6. Force UI to reflect changes
        chatBox.revalidate();
        chatBox.repaint();

        // 7. Optional: Reset input focus for better UX
        chatInput.setText("");
        chatInput.requestFocusInWindow();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = ((JScrollPane)chatBox.getParent().getParent()).getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void finishGeneration() {
        SwingUtilities.invokeLater(() -> {
            chatInput.setEnabled(true);
            sendButton.setEnabled(true);
            sendButton.setText(">");
            chatInput.requestFocusInWindow();
            chatBox.revalidate();
            chatBox.repaint();
            scrollToBottom();
        });
    }

    private void addSidebarSection(JPanel p, String title, JComponent comp) {
        JLabel l = new JLabel(title);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(Color.GRAY);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(l);
        p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(comp);
    }

    private void autoAddSidebarLabel(JPanel panel, String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(new Color(120, 120, 120));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(l);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private void updateStatus(String text) {
        if (aiStatusLabel != null) aiStatusLabel.setText(text);
        else System.out.println("Status Update: " + text);
    }

    private void startNewTimer() {
        if (userSelectedTime <= 0) return;
        if (quizTimer != null) quizTimer.stop();

        secondsRemaining = userSelectedTime;
        clockTimer.setRunning(true);

        quizTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            clockTimer.setSeconds(secondsRemaining);
            if (secondsRemaining <= 0) {
                quizTimer.stop();
                clockTimer.setRunning(false);
                handleTimeout();
            }
        });
        quizTimer.start();
    }

    private void handleTimeout() {
        JOptionPane.showMessageDialog(this, "Time's up!");
        checkAnswer("TIMEOUT");
    }

    private void attachVoice(JButton btn) {
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                String cleanText = btn.getText().replaceAll("<[^>]*>", "").trim();
                if (!cleanText.equals("-")) tts.speak(cleanText);
            }
        });
    }

    private QuizOptionButton createOptionButton(String text, Color uniqueColor) {
        QuizOptionButton btn = new QuizOptionButton(text, uniqueColor);
        btn.setEnabled(false);
        return btn;
    }

    private void checkAnswer(QuizOptionButton selectedBtn) {
        handleAnswerSelection(selectedBtn.getText(), selectedBtn);
    }

    private void checkAnswer(String selectedText) {
        handleAnswerSelection(selectedText, null);
    }

    private void handleAnswerSelection(String selectedText, QuizOptionButton selectedBtn) {
        if (quizTimer != null) quizTimer.stop();
        if (currentQuestion == null) return;

        // --- BUG FIX: Clean HTML tags injected by the UI button formatting ---
        String cleanSelectedText = selectedText.replaceAll("<[^>]*>", "").trim();
        String cleanCorrectAnswer = currentQuestion.correctAnswer.replaceAll("<[^>]*>", "").trim();

        currentQuestion.userProvidedAnswer = cleanSelectedText;
        sessionLog.add(currentQuestion);

        // Compare the cleaned strings instead of the raw HTML
        boolean isCorrect = cleanSelectedText.equalsIgnoreCase(cleanCorrectAnswer);
        float score = isCorrect ? 1.0f : 0.0f;

        if (!isCorrect && !reviewFlashcards.contains(currentQuestion)) {
            reviewFlashcards.add(currentQuestion);
        }

        studentHistory.add(score);
        updateAI(score); // This will now accurately receive 1.0f or 0.0f
        removeQuestionFromPools(currentQuestion);

        btnA.setEnabled(false); btnB.setEnabled(false);
        btnC.setEnabled(false); btnD.setEnabled(false);

        if (isCorrect) {
            selectedBtn.setCorrectGlow();
        } else {
            if (selectedBtn != null) selectedBtn.startErrorBlink();
            revealCorrectAnswer();
        }

        javax.swing.Timer transitionTimer = new javax.swing.Timer(1500, e -> {
            SwingUtilities.invokeLater(() -> loadNextQuestion(this.currentLevel));
        });
        transitionTimer.setRepeats(false);
        transitionTimer.start();
    }

    private void revealCorrectAnswer() {
        String correct = currentQuestion.correctAnswer;
        if (btnA.getText().contains(correct)) btnA.setCorrectGlow();
        else if (btnB.getText().contains(correct)) btnB.setCorrectGlow();
        else if (btnC.getText().contains(correct)) btnC.setCorrectGlow();
        else if (btnD.getText().contains(correct)) btnD.setCorrectGlow();
    }

    private void showPerformanceReport() {
        if (sessionLog.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No data to generate report.");
            return;
        }

        long total = sessionLog.size();
        long correctCount = sessionLog.stream()
                .filter(q -> q.userProvidedAnswer.replaceAll("<[^>]*>", "").trim()
                        .equalsIgnoreCase(q.correctAnswer.replaceAll("<[^>]*>", "").trim()))
                .count();

        double accuracy = (double) correctCount / total * 100;
        String grade;
        Color gradeColor;
        if (accuracy >= 90) { grade = "A+"; gradeColor = new Color(46, 204, 113); }
        else if (accuracy >= 75) { grade = "B"; gradeColor = new Color(52, 152, 219); }
        else if (accuracy >= 50) { grade = "C"; gradeColor = new Color(230, 126, 34); }
        else { grade = "D"; gradeColor = new Color(231, 76, 60); }

        JDialog reportDialog = new JDialog(this, "Student Report Card", true);
        reportDialog.setSize(1000, 650);
        reportDialog.setLocationRelativeTo(this);
        reportDialog.setLayout(new BorderLayout());

        JPanel header = new JPanel(new GridLayout(1, 2));
        header.setBackground(new Color(30, 30, 30));
        header.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel gradeLabel = new JLabel(grade, SwingConstants.CENTER);
        gradeLabel.setFont(new Font("Serif", Font.BOLD, 100));
        gradeLabel.setForeground(gradeColor);
        gradeLabel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(gradeColor, 2), "FINAL GRADE", 0, 0, null, Color.GRAY));

        String statsHtml = "<html><body style='font-family:Segoe UI; color:white;'>" +
                "<h2 style='margin:0;'>PERFORMANCE SUMMARY</h2><br>" +
                "<b>Subject:</b> " + selectedSubject + "<br>" +
                "<b>Total Questions:</b> " + total + "<br>" +
                "<b>Correct Answers:</b> " + correctCount + "<br>" +
                "<b>Accuracy:</b> " + String.format("%.1f%%", accuracy) +
                "</body></html>";
        JLabel statsLabel = new JLabel(statsHtml);

        header.add(gradeLabel);
        header.add(statsLabel);

        String[] columns = {"Status", "Topic / Question", "Your Choice", "Correct Answer"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        for (QuizItem q : sessionLog) {
            String userAns = q.userProvidedAnswer.replaceAll("<[^>]*>", "").trim();
            String correctAns = q.correctAnswer.replaceAll("<[^>]*>", "").trim();
            boolean isCorrect = userAns.equalsIgnoreCase(correctAns);
            model.addRow(new Object[]{ isCorrect ? "✔ PASS" : "✘ FAIL", q.displaySentence, userAns, correctAns });
        }

        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (c instanceof JComponent) {
                    Object value = getValueAt(row, column);
                    if (value != null) {
                        ((JComponent) c).setToolTipText("<html><p style='width: 300px;'>" + value.toString() + "</p></html>");
                    }
                }
                return c;
            }
        };

        table.setRowHeight(35);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setBackground(new Color(45, 45, 45));
        table.setForeground(Color.WHITE);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        for (int column = 0; column < table.getColumnCount(); column++) {
            javax.swing.table.TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int preferredWidth = tableColumn.getMinWidth();
            int maxWidthThreshold = (column == 1) ? 500 : 250;

            javax.swing.table.TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, 0, column);
            preferredWidth = Math.max(preferredWidth, headerComp.getPreferredSize().width + 25);

            for (int row = 0; row < table.getRowCount(); row++) {
                javax.swing.table.TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
                Component cellComp = table.prepareRenderer(cellRenderer, row, column);
                preferredWidth = Math.max(preferredWidth, cellComp.getPreferredSize().width + 25);
                if (preferredWidth >= maxWidthThreshold) {
                    preferredWidth = maxWidthThreshold;
                    break;
                }
            }
            tableColumn.setPreferredWidth(preferredWidth);
        }

        table.getColumnModel().getColumn(0).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null && value.toString().contains("PASS")) {
                    c.setForeground(new Color(46, 204, 113));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    c.setForeground(new Color(231, 76, 60));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                setHorizontalAlignment(JLabel.CENTER);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(new Color(35, 35, 35));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JButton closeBtn = new JButton("CLOSE REPORT");
        closeBtn.addActionListener(e -> reportDialog.dispose());

        JPanel footer = new JPanel();
        footer.setBackground(new Color(25, 25, 25));
        footer.add(closeBtn);

        reportDialog.add(header, BorderLayout.NORTH);
        reportDialog.add(scrollPane, BorderLayout.CENTER);
        reportDialog.add(footer, BorderLayout.SOUTH);
        reportDialog.setVisible(true);
    }

    private void removeQuestionFromPools(QuizItem q) {
        askedQuestionIDs.add(q.id);
        easyQuestions.removeIf(item -> item.id.equals(q.id));
        easyMediumQuestions.removeIf(item -> item.id.equals(q.id));
        mediumQuestions.removeIf(item -> item.id.equals(q.id));
        mediumHardQuestions.removeIf(item -> item.id.equals(q.id));
        hardQuestions.removeIf(item -> item.id.equals(q.id));
        expertQuestions.removeIf(item -> item.id.equals(q.id));
    }

    private void updateAI(float score) {
        // --- RULE-BASED ADAPTIVE ENGINE ---
        if (studentHistory.isEmpty()) {
            currentProbability = 0.10f; // Start at 10% (EASY) for the first question
        } else {
            if (score >= 1.0f) {
                currentProbability += 0.15f; // Increase probability on correct answer
            } else {
                currentProbability -= 0.15f; // Decrease probability on wrong answer or timeout
            }
        }

        // Clamp probability between 0.0 (0%) and 1.0 (100%)
        currentProbability = Math.max(0.0f, Math.min(1.0f, currentProbability));

        int percent = (int) (currentProbability * 100);
        if (homeCircleMastery != null) homeCircleMastery.setProgress(percent);

        String targetLevel;
        Color statusColor;

        // --- DIFFICULTY THRESHOLDS ---
        if (percent <= 33) {
            targetLevel = "EASY";
            statusColor = new Color(46, 204, 113); // Green
        } else if (percent <= 66) {
            targetLevel = "MEDIUM";
            statusColor = new Color(241, 196, 15); // Yellow
        } else {
            targetLevel = "EXPERT";
            statusColor = new Color(155, 89, 182); // Purple
        }

        // Pass both percentage and color to the new Quiz Ring
        if (quizMasteryRing != null) quizMasteryRing.setProgress(percent, statusColor);

        if (aiStatusLabel != null) {
            aiStatusLabel.setForeground(statusColor);
            aiStatusLabel.setText("Status: " + targetLevel + " (" + percent + "%)");
        }
        this.currentLevel = targetLevel;
    }

    private void loadNextQuestion(String targetLevel) {
        // FIXED: Use the actual configured targets instead of the old spinner
        int userLimit = targetTheoryCount + targetNumericalCount;

        if (questionCounter >= userLimit) {
            finishQuiz();
            return;
        }

        List<QuizItem> pool = getPoolByLevel(targetLevel);
        if (pool == null || pool.isEmpty()) pool = findFirstAvailablePool(targetLevel);
        if (pool == null || pool.isEmpty()) {
            finishQuiz();
            return;
        }

        try {
            resetButtonStyles();
            questionCounter++;
            currentQuestion = pool.get(0);

            difficultyLabel.setText("Question #" + questionCounter + " of " + userLimit + " | Level: " + targetLevel);
            questionArea.setText(currentQuestion.questionText);

            // Safe TTS execution
            if (tts != null) {
                tts.speak("Question " + questionCounter + ". " + currentQuestion.questionText);
            }

            QuizOptionButton[] buttons = {btnA, btnB, btnC, btnD};
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setEnabled(true);
                if (currentQuestion.options.size() > i) {
                    buttons[i].setText(currentQuestion.options.get(i));
                } else {
                    buttons[i].setText("N/A");
                }
            }

            if (userSelectedTime > 0) startNewTimer();
            else timerLabel.setText("Timer: Off");

        } catch (Exception ex) {
            System.err.println("UI Loading Error: " + ex.getMessage());
            difficultyLabel.setText("UI Error loading question: " + ex.getMessage());
        }
    }

    private List<QuizItem> getPoolByLevel(String level) {
        switch (level) {
            case "EXPERT":      return expertQuestions;
            case "HARD":        return hardQuestions;
            case "MEDIUM-HARD": return mediumHardQuestions;
            case "MEDIUM":      return mediumQuestions;
            case "EASY-MEDIUM": return easyMediumQuestions;
            case "EASY":        return easyQuestions;
            default:            return easyQuestions;
        }
    }

    private List<QuizItem> findFirstAvailablePool(String preferredLevel) {
        String[] levels = {"EXPERT", "HARD", "MEDIUM-HARD", "MEDIUM", "EASY-MEDIUM", "EASY"};
        for (String lvl : levels) {
            List<QuizItem> p = getPoolByLevel(lvl);
            if (p != null && !p.isEmpty()) return p;
        }
        return null;
    }

    private void finishQuiz() {
        questionArea.setText("\n\n    QUESTIONS ARE OVER! \n\n   You have completed all valid questions from this page.\n   Please scan a new page or exit.");
        difficultyLabel.setText("Session Complete");
        btnA.setEnabled(false); btnB.setEnabled(false); btnC.setEnabled(false); btnD.setEnabled(false);
        btnA.setText("-"); btnB.setText("-"); btnC.setText("-"); btnD.setText("-");
        JOptionPane.showMessageDialog(this, "Great job! You've finished this section.");
    }

    private void performScan() {
        SwingUtilities.invokeLater(() -> {
            selectedSubject = (String) subjectDropdown.getSelectedItem();
            currentBannedTopics = SUBJECT_BAN_LISTS.get(selectedSubject);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Textbook (PDF or Images)");
            chooser.setFileFilter(new FileNameExtensionFilter("Documents (PDF, JPG, PNG)", "pdf", "jpg", "png", "jpeg"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                logRecentFile(selectedFile.getName(), selectedSubject, targetTheoryCount + targetNumericalCount);
                difficultyLabel.setText("Initializing Engine...");
                questionCounter = 0;
                currentQuestion = null;

                new Thread(() -> {
                    try {
                        SwingUtilities.invokeLater(() -> difficultyLabel.setText("Reading Document..."));
                        StringBuilder extractedText = new StringBuilder();
                        String fileName = selectedFile.getName().toLowerCase();

                        if (fileName.endsWith(".pdf")) {
                            extractedText.append(processPDF(selectedFile));
                        } else {
                            displayImage(selectedFile);
                            File cleanFile = cleanImage(selectedFile);
                            Tesseract tesseract = new Tesseract();
                            tesseract.setDatapath("tessdata");
                            extractedText.append(tesseract.doOCR(cleanFile));
                        }

                        SwingUtilities.invokeLater(() -> difficultyLabel.setText("AI is generating questions..."));

                        // CRITICAL FIX: Aggressively limit context to prevent LLM memory overflow
                        String documentContext = extractedText.toString();
                        if (documentContext.length() > 3500) {
                            documentContext = documentContext.substring(0, 3500);
                        }

                        // Block safely until the AI finishes generating
                        generateMCQ(documentContext);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        SwingUtilities.invokeLater(() -> difficultyLabel.setText("Scan Error: " + ex.getMessage()));
                    }
                }).start();
            }
        });
    }

    private void generateMCQ(String rawText) throws Exception {
        // 1. Clear all buffers to prevent cross-session contamination
        easyQuestions.clear(); easyMediumQuestions.clear();
        mediumQuestions.clear(); mediumHardQuestions.clear();
        hardQuestions.clear(); expertQuestions.clear();

        SwingUtilities.invokeLater(() -> getGlassPane().setVisible(true));

        try {
            // 2. Define the Weighted Distributions [EASY, MEDIUM, HARD, EXPERT]
            double[] theoryWeights = {0.40, 0.40, 0.15, 0.05}; // Theory bell curve
            double[] mathWeights = {0.20, 0.30, 0.40, 0.10};   // Math bell curve (leans harder)

            // --- TRUE BUFFER SYSTEM: Generate 2.5x more questions than needed ---
            int theoryBufferTotal = (int) (targetTheoryCount * 1.6);
            int numericalBufferTotal = (int) (targetNumericalCount * 1.6);

            // 3. Generate Theory Buffer
            if (targetTheoryCount > 0) {
                int[] dist = calculateWeightedBuffer(theoryBufferTotal, theoryWeights);
                fetchAndRouteQuestions(rawText, dist, false);
            }

            // 4. Generate Numerical Buffer
            if (targetNumericalCount > 0) {
                int[] dist = calculateWeightedBuffer(numericalBufferTotal, mathWeights);
                fetchAndRouteQuestions(rawText, dist, true);
            }

            // 5. Start the Quiz
            SwingUtilities.invokeLater(() -> {
                getGlassPane().setVisible(false);
                if (isAllPoolsEmpty()) {
                    difficultyLabel.setText("AI Error: Could not generate valid questions.");
                } else {
                    btnConfigQuiz.setVisible(false);
                    btnScan.setVisible(false);
                    countLabel.setVisible(false);
                    questionCountSpinner.setVisible(false);
                    clockTimer.setEditable(false);

                    masteryContainer.setVisible(true);
                    studentHistory.clear();
                    updateAI(0);

                    loadNextQuestion(currentLevel);
                }
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                getGlassPane().setVisible(false);

                // Catch the specific connection error
                if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                    difficultyLabel.setText("System Offline: Please start Ollama.");
                    JOptionPane.showMessageDialog(this,
                            "Cannot connect to the local AI engine.\n\nPlease ensure Ollama is running (port 11434) and try again.",
                            "Engine Offline",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    difficultyLabel.setText("AI Error: " + e.getMessage());
                }
            });
            throw e;
        }
    }

    private void startTeacherWorkflow(String title, int eCount, int mCount, int hCount) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Source Textbook");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File selectedFile = chooser.getSelectedFile();
        String sourceFileName = selectedFile.getName();
        logRecentFile(sourceFileName, "Teacher Export", eCount + mCount + hCount);

        JDialog progressDialog = new JDialog(this, "PaLO - Orchestrating Document", true);
        JLabel statusMsg = new JLabel("Analyzing textbook structure...", SwingConstants.CENTER);
        statusMsg.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        progressDialog.add(statusMsg);
        progressDialog.setSize(450, 150);
        progressDialog.setLocationRelativeTo(this);

        new Thread(() -> {
            try {
                // Grouped lists to ensure strict PDF sectioning
                List<QuizItem> easyList = new ArrayList<>();
                List<QuizItem> mediumList = new ArrayList<>();
                List<QuizItem> hardList = new ArrayList<>();

                try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(selectedFile)) {
                    int totalPages = document.getNumberOfPages();
                    int pagesPerBatch = 5;
                    int totalBatches = (int) Math.ceil((double) totalPages / pagesPerBatch);

                    // Distribute requested counts across batches
                    int ePerBatch = Math.max(0, (int) Math.ceil((double) eCount / totalBatches));
                    int mPerBatch = Math.max(0, (int) Math.ceil((double) mCount / totalBatches));
                    int hPerBatch = Math.max(0, (int) Math.ceil((double) hCount / totalBatches));

                    for (int i = 0; i < totalPages; i += pagesPerBatch) {
                        int start = i + 1;
                        int end = Math.min(i + pagesPerBatch, totalPages);
                        SwingUtilities.invokeLater(() -> statusMsg.setText("Processing Batch: Pages " + start + "-" + end));

                        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                        stripper.setStartPage(start); stripper.setEndPage(end);
                        String chunkText = stripper.getText(document);

                        // Fetch Easy, Medium, and Hard separately from each chunk
                        if (easyList.size() < eCount)
                            easyList.addAll(fetchQuestionsForExport(chunkText, "EASY", Math.min(ePerBatch, eCount - easyList.size()), false));
                        if (mediumList.size() < mCount)
                            mediumList.addAll(fetchQuestionsForExport(chunkText, "MEDIUM", Math.min(mPerBatch, mCount - mediumList.size()), false));
                        if (hardList.size() < hCount)
                            hardList.addAll(fetchQuestionsForExport(chunkText, "HARD", Math.min(hPerBatch, hCount - hardList.size()), false));
                    }
                }

                // Combine in order: EASY -> MEDIUM -> HARD
                List<QuizItem> finalExportList = new ArrayList<>();
                finalExportList.addAll(easyList);
                finalExportList.addAll(mediumList);
                finalExportList.addAll(hardList);

                if (finalExportList.isEmpty()) throw new Exception("AI failed to extract questions. Verify Ollama/Qwen is active.");

                SwingUtilities.invokeLater(() -> statusMsg.setText("Finalizing Watermarked PDF..."));
                String savePath = generatePDF(finalExportList, title, sourceFileName);

                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(this, "Success!\nSource: " + sourceFileName + "\nPath: " + savePath);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                });
            }
        }).start();
        progressDialog.setVisible(true);
    }

    private String generatePDF(List<QuizItem> items, String title, String sourceFileName) throws Exception {
        String userHome = System.getProperty("user.home");
        File dir = new File(userHome, "Documents/PaLO_Quizzes");
        if (!dir.exists()) dir.mkdirs();
        File pdfFile = new File(dir, title.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf");

        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);

            final float margin = 50;
            final float startY = 750;
            float y = startY;
            String currentDiff = "";
            int questionNumber = 1;

            applyBrandedWatermark(doc, page, title);

            org.apache.pdfbox.pdmodel.PDPageContentStream content = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true);

            // 1. Sanitize Header Title
            content.beginText();
            content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 18);
            content.newLineAtOffset(margin, y);
            content.showText(clean(title.toUpperCase()));
            content.endText();

            // 2. Sanitize Source Citation
            y -= 20;
            content.beginText();
            content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_OBLIQUE, 9);
            content.setNonStrokingColor(100, 100, 100);
            content.newLineAtOffset(margin, y);
            content.showText("Source Document: " + clean(sourceFileName));
            content.endText();
            content.setNonStrokingColor(0, 0, 0);

            y -= 40;

            for (QuizItem item : items) {
                if (!item.originalContext.equals(currentDiff)) {
                    currentDiff = item.originalContext;
                    questionNumber = 1;
                    y -= 20;
                    if (y < 150) {
                        content.close();
                        page = new org.apache.pdfbox.pdmodel.PDPage();
                        doc.addPage(page);
                        applyBrandedWatermark(doc, page, title);
                        content = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true);
                        y = startY;
                    }
                    content.beginText();
                    content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 14);
                    content.newLineAtOffset(margin, y);
                    content.showText("SECTION: " + clean(currentDiff) + " QUESTIONS");
                    content.endText();
                    y -= 25;
                }

                if (y < 120) {
                    content.close();
                    page = new org.apache.pdfbox.pdmodel.PDPage();
                    doc.addPage(page);
                    applyBrandedWatermark(doc, page, title);
                    content = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true);
                    y = startY;
                }

                // 3. Sanitize Question Text
                content.beginText();
                content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 11);
                content.newLineAtOffset(margin, y);
                String fullQ = clean(questionNumber + ". " + item.questionText);
                if (fullQ.length() > 95) {
                    content.showText(fullQ.substring(0, 90) + "...");
                } else {
                    content.showText(fullQ);
                }
                content.endText();
                y -= 18;

                // 4. Sanitize Options
                content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 10);
                for (String opt : item.options) {
                    content.beginText();
                    content.newLineAtOffset(margin + 20, y);
                    content.showText(clean(opt));
                    content.endText();
                    y -= 14;
                }
                y -= 15;
                questionNumber++;
            }
            content.close();
            doc.save(pdfFile);
        }
        return pdfFile.getAbsolutePath();
    }

    /**
     * Removes newlines and control characters that crash PDFBox font encoding.
     */
    private String clean(String text) {
        if (text == null) return "";
        return text.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
    }

    private void applyBrandedWatermark(org.apache.pdfbox.pdmodel.PDDocument doc, org.apache.pdfbox.pdmodel.PDPage page, String title) throws Exception {
        // PREPEND ensures the watermark remains on the background layer
        try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.PREPEND, true, true)) {

            org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState gs = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
            // Increased alpha to 0.18f for a more defined 'greyed out' look
            gs.setNonStrokingAlphaConstant(0.18f);
            cs.setGraphicsStateParameters(gs);

            // Darker grey (150) for that professional exam paper aesthetic
            cs.setNonStrokingColor(150, 150, 150);

            // Sanitize Watermark Text
            String watermarkText = clean("PaLO - " + title.toUpperCase());

            // Diagonal Tiling Logic
            for (int yCoord = -100; yCoord < 1000; yCoord += 180) {
                for (int xCoord = -100; xCoord < 800; xCoord += 250) {
                    cs.beginText();
                    cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 14);

                    // 45-degree rotation for the classic board-exam appearance
                    cs.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(45), xCoord, yCoord));

                    cs.showText(watermarkText);
                    cs.endText();
                }
            }
        }
    }

    private void stopZenMusic() {
        if (zenClip != null && zenClip.isRunning()) {
            zenClip.stop();
            zenClip.close();
        }
    }

    // --- SECOND PASS VALIDATOR ---
    //private boolean verifyQuestionWithAI(String context, String question, String correctAnswer) {
    //    try {
    //        String validationPrompt = "TEXT EXCERPT:\n" + context + "\n\n" +
    //                "QUESTION: " + question + "\n" +
    //                "PROVIDED ANSWER: " + correctAnswer + "\n\n" +
    //                "TASK: Verify if the PROVIDED ANSWER is 100% factually correct based ONLY on the TEXT EXCERPT. " +
    //                "If it is correct, reply with exactly 'VALID'. If it is incorrect, makes false assumptions, or is not in the text, reply with exactly 'INVALID'.";

    //        String validationResponse = llamaConnection.generateMCQs(context, validationPrompt, 1, false).toUpperCase();
    //       return validationResponse.contains("VALID") && !validationResponse.contains("INVALID");
    //    } catch (Exception e) {
    //        System.err.println("Validation check failed. Defaulting to false. " + e.getMessage());
    //        return false;
    //    }
    //}

    // --- 1. THE ADAPTIVE STUDENT ENGINE (Using weighted distribution) ---
    private void fetchAndRouteQuestions(String context, int[] distribution, boolean isNumerical) throws Exception {
        int totalCount = distribution[0] + distribution[1] + distribution[2] + distribution[3];
        if (totalCount == 0) return;

        String promptType = isNumerical ? "NUMERICAL calculation-based" : "THEORETICAL conceptual";
        String distString = String.format("- %d EASY\n- %d MEDIUM\n- %d HARD\n- %d EXPERT",
                distribution[0], distribution[1], distribution[2], distribution[3]);

        String prompt;
        if (isNumerical) {
            prompt = "TEXT EXCERPT:\n" + context + "\n\n" +
                    "TASK: Create exactly " + totalCount + " math problems based on the text.\n" +
                    "STRICT RULES:\n" +
                    "1. Provide a Question.\n" +
                    "2. Provide EXACTLY 4 comma-separated options.\n" +
                    "3. Provide the Correct Answer.\n" +
                    "FORMAT EXACTLY LIKE THIS:\n" +
                    "**Question**: What is 2 + 2?\n" +
                    "**Options**: 3, 4, 5, 6\n" +
                    "**Correct Answer**: 4\n";
        } else {
            prompt = "TEXT EXCERPT:\n" + context + "\n\n" +
                    "TASK: Create exactly " + totalCount + " " + promptType + " multiple-choice questions.\n" +
                    "REQUIRED DISTRIBUTION:\n" + distString + "\n\n" +
                    "STRICT RULES:\n" +
                    "1. YOU MUST USE THE EXACT XML TAGS PROVIDED IN THE EXAMPLE.\n" +
                    "2. YOU MUST PROVIDE EXACTLY 3 <distractor> TAGS PER QUESTION. DO NOT SKIP THEM.\n" +
                    "EXAMPLE FORMAT:\n" +
                    "<item>\n" +
                    "<difficulty>EASY</difficulty>\n" +
                    "<question>What law governs the reflection of light?</question>\n" +
                    "<correct_answer>The angle of incidence equals the angle of reflection</correct_answer>\n" +
                    "<distractor>Light bends around the object</distractor>\n" +
                    "<distractor>The speed of light decreases</distractor>\n" +
                    "<distractor>Light gets absorbed completely</distractor>\n" +
                    "</item>";
        }

        executeRegexExtraction(prompt, context, totalCount, isNumerical, true, "");
    }

    // --- 2. THE TEACHER EXPORT ENGINE (Fixes the 4-argument IDE error) ---
    private void fetchForExport(String context, String diff, int count, List<QuizItem> masterList) throws Exception {
        masterList.addAll(fetchQuestionsForExport(context, diff, count, false));
    }

    // --- 2. THE TEACHER EXPORT ENGINE ---
    private List<QuizItem> fetchQuestionsForExport(String context, String diff, int count, boolean isNumerical) throws Exception {
        String promptType = isNumerical ? "NUMERICAL calculation-based" : "THEORETICAL conceptual";

        String prompt;
        if (isNumerical) {
            prompt = "TEXT EXCERPT:\n" + context + "\n\n" +
                    "TASK: Create exactly " + count + " math problems based on the text.\n" +
                    "DIFFICULTY LEVEL: " + diff + "\n" +
                    "STRICT RULES:\n" +
                    "1. Provide a Question.\n" +
                    "2. Provide EXACTLY 4 comma-separated options.\n" +
                    "3. Provide the Correct Answer.\n" +
                    "FORMAT EXACTLY LIKE THIS:\n" +
                    "**Question**: What is 2 + 2?\n" +
                    "**Options**: 3, 4, 5, 6\n" +
                    "**Correct Answer**: 4\n";
        } else {
            prompt = "TEXT EXCERPT:\n" + context + "\n\n" +
                    "TASK: Create exactly " + count + " " + promptType + " multiple-choice questions.\n" +
                    "DIFFICULTY LEVEL: " + diff + "\n\n" +
                    "STRICT RULES:\n" +
                    "1. YOU MUST USE THE EXACT XML TAGS PROVIDED IN THE EXAMPLE.\n" +
                    "2. YOU MUST PROVIDE EXACTLY 3 <distractor> TAGS PER QUESTION. DO NOT SKIP THEM.\n" +
                    "EXAMPLE FORMAT:\n" +
                    "<item>\n" +
                    "<question>What law governs the reflection of light?</question>\n" +
                    "<correct_answer>The angle of incidence equals the angle of reflection</correct_answer>\n" +
                    "<distractor>Light bends around the object</distractor>\n" +
                    "<distractor>The speed of light decreases</distractor>\n" +
                    "<distractor>Light gets absorbed completely</distractor>\n" +
                    "</item>";
        }

        return executeRegexExtraction(prompt, context, count, isNumerical, false, diff);
    }

    // --- 3. THE CORE REGEX PARSER ---
    private List<QuizItem> executeRegexExtraction(String prompt, String context, int count, boolean isNumerical, boolean autoRoute, String forcedDiff) throws Exception {
        int maxRetries = 2;
        List<QuizItem> items = new ArrayList<>();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String currentPrompt = attempt > 1 ? "WARNING: PREVIOUS FORMAT FAILED. USE EXACT FORMAT REQUESTED.\n\n" + prompt : prompt;
                String rawResponse = llamaConnection.generateMCQs("", currentPrompt, count, isNumerical);

                System.out.println("--- AI RAW RESPONSE (ATTEMPT " + attempt + ") ---");
                System.out.println(rawResponse);

                if (isNumerical) {
                    // --- MATH MODEL PARSER (Markdown format) ---
                    // Looks for blocks formatted like:
                    // **Question**: ...
                    // **Options**: a, b, c, d
                    // **Correct Answer**: ...
                    Pattern qPattern = Pattern.compile("\\*\\*Question\\*\\*\\s*:(.*?)(?=\\*\\*Options|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                    Pattern optPattern = Pattern.compile("\\*\\*Options?\\*\\*\\s*:(.*?)(?=\\*\\*Correct Answer|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                    Pattern ansPattern = Pattern.compile("\\*\\*Correct Answer\\*\\*\\s*:(.*?)(?=\\*\\*Question|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

                    Matcher qMatcher = qPattern.matcher(rawResponse);
                    Matcher optMatcher = optPattern.matcher(rawResponse);
                    Matcher ansMatcher = ansPattern.matcher(rawResponse);

                    while (qMatcher.find() && optMatcher.find() && ansMatcher.find()) {
                        String question = qMatcher.group(1).trim();
                        String rawCorrect = ansMatcher.group(1).trim();
                        String optionsStr = optMatcher.group(1).trim();

                        // Clean up markdown bolding if AI added it to the answer
                        rawCorrect = rawCorrect.replace("**", "").trim();

                        // Split options by comma
                        String[] splitOptions = optionsStr.split(",");
                        List<String> rawOptions = new ArrayList<>();
                        for (String opt : splitOptions) {
                            rawOptions.add(opt.trim().replace("**", ""));
                        }

                        if (question.isEmpty() || rawCorrect.isEmpty() || rawOptions.isEmpty()) continue;

                        finalizeAndRouteItem(question, rawCorrect, rawOptions, items, autoRoute, forcedDiff);
                    }
                } else {
                    // --- THEORY MODEL PARSER (XML format) ---
                    Pattern itemPattern = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
                    Pattern diffPattern = Pattern.compile("<difficulty>(.*?)</difficulty>", Pattern.DOTALL);
                    Pattern qPattern = Pattern.compile("<question>(.*?)</question>", Pattern.DOTALL);
                    Pattern correctPattern = Pattern.compile("<correct_answer>(.*?)</correct_answer>", Pattern.DOTALL);
                    Pattern distractorPattern = Pattern.compile("<distractor>(.*?)</distractor>", Pattern.DOTALL);

                    Matcher itemMatcher = itemPattern.matcher(rawResponse);

                    while (itemMatcher.find()) {
                        String itemBlock = itemMatcher.group(1);

                        Matcher dMatcher = diffPattern.matcher(itemBlock);
                        String itemDiff = autoRoute ? (dMatcher.find() ? dMatcher.group(1).trim().toUpperCase() : "EASY") : forcedDiff;

                        Matcher qMatcher = qPattern.matcher(itemBlock);
                        String question = qMatcher.find() ? qMatcher.group(1).trim() : "";

                        Matcher cMatcher = correctPattern.matcher(itemBlock);
                        String rawCorrect = cMatcher.find() ? cMatcher.group(1).trim() : "";

                        List<String> rawOptions = new ArrayList<>();
                        if (!rawCorrect.isEmpty()) {
                            rawOptions.add(rawCorrect.replaceFirst("^[A-Da-d][\\.\\)]\\s*", ""));
                        }

                        Matcher distMatcher = distractorPattern.matcher(itemBlock);
                        while (distMatcher.find()) {
                            rawOptions.add(distMatcher.group(1).trim().replaceFirst("^[A-Da-d][\\.\\)]\\s*", ""));
                        }

                        if (question.isEmpty() || rawCorrect.isEmpty() || rawOptions.size() < 2) continue;
                        finalizeAndRouteItem(question, rawCorrect, rawOptions, items, autoRoute, itemDiff);
                    }
                }

                if (!items.isEmpty()) return items;

            } catch (Exception e) {
                System.err.println("Extraction Attempt " + attempt + " failed: " + e.getMessage());
            }
        }
        throw new Exception("AI Logic/Parsing Failed. Check IDE console.");
    }

    // Helper method to avoid duplicating the A/B/C/D formatting logic
    private void finalizeAndRouteItem(String question, String rawCorrect, List<String> rawOptions, List<QuizItem> items, boolean autoRoute, String itemDiff) {
        // SMART PADDING: Replaces missing options with realistic test options
        if (rawOptions.size() == 2) {
            rawOptions.add("Both A and B");
            rawOptions.add("None of the above");
        } else if (rawOptions.size() == 3) {
            rawOptions.add("None of the above");
        }
        if(rawOptions.size() > 4) rawOptions = rawOptions.subList(0, 4);

        // Ensure the correct answer is actually in the options list (safety net for math mode)
        if (!rawOptions.contains(rawCorrect)) {
            rawOptions.set(0, rawCorrect);
        }

        // Shuffle the options so the correct answer isn't always in the same spot
        Collections.shuffle(rawOptions);

        List<String> labeledOptions = new ArrayList<>();
        String finalLabeledAnswer = "";
        char label = 'A';

        for (String optText : rawOptions) {
            String fullOpt = label + ". " + optText;
            labeledOptions.add(fullOpt);
            // Match to find which labeled option is the correct one
            if (optText.equals(rawCorrect.replaceFirst("^[A-Da-d][\\.\\)]\\s*", ""))) {
                finalLabeledAnswer = fullOpt;
            }
            label++;
        }

        if (finalLabeledAnswer.isEmpty()) finalLabeledAnswer = labeledOptions.get(0);

        QuizItem newItem = new QuizItem(question, question, finalLabeledAnswer, labeledOptions, itemDiff);
        items.add(newItem);

        // Route to internal buffers if playing the adaptive quiz
        if (autoRoute) {
            if (itemDiff.contains("EXPERT")) expertQuestions.add(newItem);
            else if (itemDiff.contains("HARD")) hardQuestions.add(newItem);
            else if (itemDiff.contains("MEDIUM")) mediumQuestions.add(newItem);
            else easyQuestions.add(newItem);
        }
    }

    private String processPDF(File pdfFile) throws Exception {
        StringBuilder pdfText = new StringBuilder();
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(pdfFile)) {
            org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath("tessdata");

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                final int pageNum = i + 1;
                SwingUtilities.invokeLater(() -> difficultyLabel.setText("Scanning Page " + pageNum + "..."));
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String text = stripper.getText(document);

                if (text.trim().isEmpty()) {
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 300);
                    text = tesseract.doOCR(bim);
                }
                pdfText.append(text).append("\n");
            }
        }
        return pdfText.toString();
    }

    private void displayImage(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            int newWidth = 600;
            int newHeight = (int) ((double)img.getHeight() / img.getWidth() * newWidth);
            Image scaled = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            imageViewer.setIcon(new ImageIcon(scaled));
            imageViewer.setText("");
        } catch (Exception e) {}
    }

    private File cleanImage(File source) {
        try {
            BufferedImage img = ImageIO.read(source);
            BufferedImage gray = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics g = gray.getGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            File temp = File.createTempFile("ocr_clean", ".png");
            ImageIO.write(gray, "png", temp);
            return temp;
        } catch (Exception e) { return source; }
    }

    private String cleanGarbage(String text) {
        String cleaned = text.replaceAll("(?i)\\b\\d{1,2}-[a-z]{3}-\\d{2,4}\\b", "");
        cleaned = cleaned.replaceAll("(?i)\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM)?\\b", "");
        cleaned = cleaned.replaceAll("(?i)\\b(?:chapter|indd|reprint|page|edition|version)\\b", "");
        cleaned = cleaned.replaceAll("\\d{4}-\\d{2,4}", "");
        return cleaned.trim();
    }

    private void saveProgress() {
        try {
            FileWriter writer = new FileWriter("tutor_save.txt");
            // Save 1: Theme
            writer.write(currentThemeName + "\n");

            // Save 2: History
            StringBuilder sb = new StringBuilder();
            for (Float score : studentHistory) sb.append(score).append(",");
            writer.write(sb.toString() + "\n");

            // Save 3: Username
            writer.write((customUserName != null ? customUserName : "") + "\n");

            writer.close();
        } catch (Exception e) {}
    }

    private void loadProgress() {
        try {
            File saveFile = new File("tutor_save.txt");
            if (!saveFile.exists()) return;
            Scanner scanner = new Scanner(saveFile);

            // 1. Restore Theme
            if (scanner.hasNextLine()) {
                String savedTheme = scanner.nextLine().trim();
                if (PRESETS.containsKey(savedTheme)) {
                    currentThemeName = savedTheme;
                    ThemePreset tp = PRESETS.get(savedTheme);
                    sidebarColor = tp.side; windowColor = tp.main; currentAccentColor = tp.accent;
                }
            }
            // 2. Restore History
            if (scanner.hasNextLine()) {
                String[] scores = scanner.nextLine().split(",");
                studentHistory.clear();
                for (String s : scores) if (!s.isEmpty()) studentHistory.add(Float.parseFloat(s));
                updateAI(0);
            }
            // 3. Restore Username
            if (scanner.hasNextLine()) {
                customUserName = scanner.nextLine().trim();
            }
            scanner.close();
        } catch (Exception e) {}
    }

    // ================== FIXED DASHBOARD (Flexible Mastery + Safe Drawing) ==================
    private class ThemePill extends JButton {
        private String themeName;
        private ThemePreset preset;
        private JDialog parent;

        public ThemePill(String name, ThemePreset preset, JDialog parent) {
            this.themeName = name; this.preset = preset; this.parent = parent;
            setContentAreaFilled(false); setBorderPainted(false); setCursor(new Cursor(Cursor.HAND_CURSOR));
            addActionListener(e -> {
                applyThemePreset(themeName);
                parent.getContentPane().setBackground(windowColor);
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (currentThemeName.equals(themeName)) {
                g2.setColor(preset.accent);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 25, 25);
            }

            int splitWidth = (int)(getWidth() * 0.3);
            g2.setColor(preset.side); g2.fillRoundRect(5, 5, splitWidth, getHeight() - 10, 15, 15);
            g2.setColor(preset.main); g2.fillRoundRect(splitWidth + 2, 5, (getWidth() - splitWidth) - 10, getHeight() - 10, 15, 15);

            g2.setColor(preset.isDark ? Color.WHITE : Color.BLACK);
            g2.setFont(new Font("Segoe UI Bold", Font.PLAIN, 12));
            g2.drawString(themeName, splitWidth + 20, getHeight() / 2 + 5);
            g2.dispose();
        }
    }

    private class PaLOHomePage extends JDialog {
        private JLabel dynamicDisplayLabel;
        private int displayStep = 0;
        private java.util.List<SidebarItem> sidebarButtons = new java.util.ArrayList<>();
        private JLabel welcome;
        private JButton themeBtn;
        private JLabel dashboardTitle;

        private final JPanel mainLayout;
        private final JPanel sidebar;
        private final JPanel contentPanel;
        private final DailyLineGraphPanel graphPanel;
        private final ModernCalendar calendarPanel;
        private JTable table;
        private DefaultTableModel recentFilesModel;
        private List<JsonObject> currentlyDisplayedFiles = new ArrayList<>();

        public PaLOHomePage(Frame owner) {
            super(owner, false);
            setUndecorated(false);
            setTitle("PaLO - Student Dashboard");
            setSize(1280, 800);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            OfflineTutorApp mainAppRef = (OfflineTutorApp) owner;
            mainAppRef.setMostRecentDashboard(this);

            addWindowListener(new java.awt.event.WindowAdapter() {
                public void windowClosing(java.awt.event.WindowEvent e) {
                    if (getOwner() != null && questionCounter > 0) closeAndSwitch();
                    else {
                        if (JOptionPane.showConfirmDialog(null, "Exit PaLO entirely?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                            saveProgress(); System.exit(0);
                        }
                    }
                }
            });

            mainLayout = new JPanel(new BorderLayout());
            mainLayout.setBackground(OfflineTutorApp.this.windowColor);

            sidebar = new JPanel();
            sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
            sidebar.setBackground(OfflineTutorApp.this.sidebarColor);
            sidebar.setPreferredSize(new Dimension(260, 0));
            sidebar.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));

            dashboardTitle = new JLabel();
            updateDashboardTitleText();
            dashboardTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(dashboardTitle);
            sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

            addSidebarBtn(sidebar, "Home", true, e -> { });
            addSidebarBtn(sidebar, "Offline Scan Mode", false, e -> { isChatMode=false; isAudioMode=false; closeAndSwitch(); });
            addSidebarBtn(sidebar, "Deep Focus Mode", false, e -> {
                if (mainAppRef != null) {
                    mainAppRef.switchToFocusMode();
                    mainAppRef.setLocationRelativeTo(null);
                    mainAppRef.setVisible(true);
                }
                this.dispose();
            });
            addSidebarBtn(sidebar, "Talk to AI", false, e -> { isChatMode=true; isAudioMode=false; closeAndSwitch(); });
            addSidebarBtn(sidebar, "Quiz Generator", false, e -> {
                TeacherQuizConfigDialog dialog = new TeacherQuizConfigDialog((Frame)getOwner());
                dialog.setVisible(true);
            });
            addSidebarBtn(sidebar, "FlashCard Generator", false, e -> startFlashcardWorkflow());

            sidebar.add(Box.createVerticalGlue());

            JButton settingsBtn = createSimpleLinkBtn("Settings");
            settingsBtn.addActionListener(e -> openSettings());
            sidebar.add(settingsBtn);
            sidebar.add(Box.createRigidArea(new Dimension(0, 15)));

            JButton exitBtn = createSimpleLinkBtn("Exit Application");
            exitBtn.addActionListener(e -> { saveProgress(); System.exit(0); });
            sidebar.add(exitBtn);

            contentPanel = new JPanel(new BorderLayout(25, 25));
            contentPanel.setBackground(OfflineTutorApp.this.windowColor);
            contentPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

            JPanel topBar = new JPanel(new BorderLayout());
            topBar.setOpaque(false);

            java.util.Calendar c = java.util.Calendar.getInstance();
            int h = c.get(java.util.Calendar.HOUR_OF_DAY);
            final String greeting = (h < 12) ? "Good Morning" : ((h < 17) ? "Good Afternoon" : "Good Evening");

            welcome = new JLabel();
            updateWelcomeText(greeting);
            topBar.add(welcome, BorderLayout.WEST);

            JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
            topRight.setOpaque(false);

            JButton eyeBtn = new JButton("\uD83D\uDC41");
            eyeBtn.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 22));
            eyeBtn.setContentAreaFilled(false); eyeBtn.setBorderPainted(false); eyeBtn.setFocusPainted(false);
            eyeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            eyeBtn.setForeground(mainAppRef != null && mainAppRef.isSpeechEnabled() ? currentAccentColor : (isDarkMode() ? Color.WHITE : Color.BLACK));

            eyeBtn.addActionListener(e -> {
                if (mainAppRef != null) {
                    mainAppRef.setSpeechEnabled(!mainAppRef.isSpeechEnabled());
                    eyeBtn.setForeground(mainAppRef.isSpeechEnabled() ? currentAccentColor : (isDarkMode() ? Color.WHITE : Color.BLACK));
                    if (mainAppRef.isSpeechEnabled()) mainAppRef.getTTS().speak("Voice assisted mode enabled");
                }
            });
            topRight.add(eyeBtn);

            dynamicDisplayLabel = new JLabel() {
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            dynamicDisplayLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            startDisplayTimer();
            topRight.add(dynamicDisplayLabel);

            themeBtn = new JButton() {
                protected void paintComponent(Graphics g) {
                    setText(isDarkMode() ? "\u2600" : "\uD83C\uDF19");
                    setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            themeBtn.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 22));
            themeBtn.setContentAreaFilled(false); themeBtn.setBorderPainted(false); themeBtn.setFocusPainted(false);
            themeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            themeBtn.addActionListener(e -> mainAppRef.applyThemePreset(PRESETS.get(currentThemeName).counterpart));
            topRight.add(themeBtn);

            topBar.add(topRight, BorderLayout.EAST);
            contentPanel.add(topBar, BorderLayout.NORTH);

            JPanel centerGrid = new JPanel(new GridBagLayout());
            centerGrid.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();

            JPanel leftCol = new JPanel(new BorderLayout(0, 25));
            leftCol.setOpaque(false);

            GlassCardPanel graphCard = new GlassCardPanel();
            graphCard.setLayout(new BorderLayout(15, 15));
            graphCard.setPreferredSize(new Dimension(0, 320));
            JLabel gTitle = new JLabel("Today's Performance") {
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            gTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
            graphCard.add(gTitle, BorderLayout.NORTH);
            this.graphPanel = new DailyLineGraphPanel();
            graphCard.add(graphPanel, BorderLayout.CENTER);
            leftCol.add(graphCard, BorderLayout.CENTER);

            GlassCardPanel tableCard = new GlassCardPanel();
            tableCard.setLayout(new BorderLayout(10, 10));
            tableCard.setPreferredSize(new Dimension(0, 180));
            JLabel tTitle = new JLabel("Recently Used Files") {
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            tTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
            tableCard.add(tTitle, BorderLayout.NORTH);

            String[] cols = {"Time", "File Name", "Subject"}; // Changed Date to Time
            recentFilesModel = new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r, int c) { return false; }};
            table = new JTable(recentFilesModel) {
                @Override
                protected void paintComponent(Graphics g) {
                    setBackground(isDarkMode() ? new Color(35, 35, 35) : Color.WHITE);
                    setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    getTableHeader().setBackground(isDarkMode() ? new Color(45, 45, 45) : new Color(240, 240, 240));
                    getTableHeader().setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                    if (getParent() instanceof JViewport) getParent().setBackground(isDarkMode() ? new Color(35, 35, 35) : Color.WHITE);
                    super.paintComponent(g);
                }
            };

            // Allow row selection
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                    int row = table.getSelectedRow();
                    if (row < currentlyDisplayedFiles.size()) {
                        JsonObject selectedObj = currentlyDisplayedFiles.get(row);
                        List<Float> fileData = new ArrayList<>();
                        if (selectedObj.has("history")) {
                            JsonArray arr = selectedObj.getAsJsonArray("history");
                            for (JsonElement el : arr) fileData.add(el.getAsFloat());
                        }
                        graphPanel.setPlotData(fileData);
                    }
                }
            });

            table.setRowHeight(28); table.setShowGrid(false);
            // Add vertical scrollbar policy to ensure it is scrollable
            JScrollPane tsp = new JScrollPane(table);
            tsp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            tsp.setBorder(BorderFactory.createEmptyBorder());
            tableCard.add(tsp, BorderLayout.CENTER);
            leftCol.add(tableCard, BorderLayout.SOUTH);

            // Make the dashboard default to showing only today's files
            String todayStr = new java.text.SimpleDateFormat("dd MMM yyyy").format(new java.util.Date());
            refreshRecentFilesTable(todayStr);

            gbc.gridx=0; gbc.gridy=0; gbc.weightx=0.68; gbc.weighty=1.0;
            gbc.fill=GridBagConstraints.BOTH; gbc.insets=new Insets(0,0,0,25);
            centerGrid.add(leftCol, gbc);

            JPanel rightCol = new JPanel(new GridBagLayout());
            rightCol.setOpaque(false);
            GridBagConstraints rc = new GridBagConstraints();

            GlassCardPanel masteryCard = new GlassCardPanel();
            masteryCard.setLayout(new BorderLayout());
            JLabel mTitle = new JLabel("Overall Mastery", SwingConstants.CENTER) {
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? Color.GRAY : new Color(0, 0, 100));
                    super.paintComponent(g);
                }
            };
            masteryCard.add(mTitle, BorderLayout.NORTH);
            homeCircleMastery.setPreferredSize(null);
            masteryCard.add(homeCircleMastery, BorderLayout.CENTER);

            rc.gridx = 0; rc.gridy = 0; rc.weightx = 1.0; rc.weighty = 1.0;
            rc.fill = GridBagConstraints.BOTH; rc.insets = new Insets(0, 0, 25, 0);
            rightCol.add(masteryCard, rc);

            GlassCardPanel calCard = new GlassCardPanel();
            calCard.setLayout(new BorderLayout());
            calCard.setPreferredSize(new Dimension(0, 280));
            this.calendarPanel = new ModernCalendar();
            calCard.add(calendarPanel, BorderLayout.CENTER);

            rc.gridy = 1; rc.weighty = 0.0; rc.insets = new Insets(0, 0, 0, 0);
            rightCol.add(calCard, rc);

            gbc.gridx = 1; gbc.weightx = 0.32;
            centerGrid.add(rightCol, gbc);

            contentPanel.add(centerGrid, BorderLayout.CENTER);
            mainLayout.add(sidebar, BorderLayout.WEST);
            mainLayout.add(contentPanel, BorderLayout.CENTER);
            setContentPane(mainLayout);
        }

        public void refreshRecentFilesTable(String dateFilter) {
            if (this.recentFilesModel == null) return;
            this.recentFilesModel.setRowCount(0);
            this.currentlyDisplayedFiles.clear();

            for (JsonObject obj : OfflineTutorApp.this.recentFilesList) {
                if (dateFilter == null || obj.get("date").getAsString().equals(dateFilter)) {
                    currentlyDisplayedFiles.add(obj);
                }
            }

            if (currentlyDisplayedFiles.isEmpty()) {
                this.recentFilesModel.addRow(new Object[]{"-", "No activity", "-"});
                graphPanel.setPlotData(new ArrayList<>()); // Clear graph
            } else {
                for (JsonObject obj : currentlyDisplayedFiles) {
                    String time = obj.has("time") ? obj.get("time").getAsString() : "-";
                    this.recentFilesModel.addRow(new Object[]{time, obj.get("fileName").getAsString(), obj.get("subject").getAsString()});
                }
                // Auto-select the first file of the day
                table.setRowSelectionInterval(0, 0);
            }
        }

        public void refreshColorsDirectly() {
            sidebar.setBackground(OfflineTutorApp.this.sidebarColor);
            mainLayout.setBackground(OfflineTutorApp.this.windowColor);
            contentPanel.setBackground(OfflineTutorApp.this.windowColor);
            getContentPane().setBackground(OfflineTutorApp.this.windowColor);

            updateDashboardTitleText();
            updateWelcomeText(null);

            for(SidebarItem btn : sidebarButtons) btn.updateLook();

            if(graphPanel != null) graphPanel.repaint();
            if(homeCircleMastery != null) homeCircleMastery.repaint();
            if(calendarPanel != null) calendarPanel.repaint();
            repaint();
        }

        private void updateDashboardTitleText() {
            String color = isDarkMode() ? "white" : "black";
            dashboardTitle.setText("<html><div style='color:" + color + ";'>" +
                    "<span style='font-size:22px; font-weight:bold;'>PaLO</span><br>" +
                    "<span style='font-size:10px;'>Learning Orchestrator</span></div></html>");
        }

        private void updateWelcomeText(String greeting) {
            String colorStr = isDarkMode() ? "white" : "black";
            String currentGreeting = (greeting != null) ? greeting :
                    (welcome.getText().contains("Morning") ? "Good Morning" :
                            welcome.getText().contains("Afternoon") ? "Good Afternoon" : "Good Evening");

            // Decide which name to show
            String displayTarget = OfflineTutorApp.this.customUserName;
            if (displayTarget == null || displayTarget.isEmpty()) {
                String rawName = System.getProperty("user.name");
                displayTarget = (rawName != null && !rawName.isEmpty()) ?
                        rawName.substring(0, 1).toUpperCase() + rawName.substring(1) : "Student";
            }

            welcome.setText("<html><font color='#888888'>" + currentGreeting + ",</font><br><font size='6' color='" + colorStr + "'><b>" + displayTarget + "</b></font></html>");
        }

        private void openSettings() {
            JDialog settings = new JDialog(this, "Appearance Settings", true);
            settings.setSize(450, 750); // Made slightly taller for the new field
            settings.setLocationRelativeTo(this);
            settings.getContentPane().setBackground(OfflineTutorApp.this.windowColor);

            JPanel content = new JPanel(new BorderLayout());
            content.setOpaque(false);

            JPanel mainGrid = new JPanel(new GridLayout(0, 1, 10, 12));
            mainGrid.setOpaque(false);
            mainGrid.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

            // --- 1. USERNAME SECTION ---
            JLabel nameLabel = new JLabel("DISPLAY NAME", SwingConstants.CENTER) {
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? currentAccentColor : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            nameLabel.setFont(new Font("Segoe UI Bold", Font.PLAIN, 14));
            mainGrid.add(nameLabel);

            // Pre-fill with custom name, or OS name if empty
            String currentName = OfflineTutorApp.this.customUserName.isEmpty() ?
                    System.getProperty("user.name") : OfflineTutorApp.this.customUserName;

            JTextField nameField = new JTextField(currentName);
            nameField.setHorizontalAlignment(JTextField.CENTER);
            nameField.setFont(new Font("Segoe UI", Font.BOLD, 16));
            nameField.setBackground(isDarkMode() ? new Color(45, 45, 45) : Color.WHITE);
            nameField.setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
            nameField.setCaretColor(isDarkMode() ? Color.WHITE : Color.BLACK);
            nameField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(currentAccentColor, 1),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
            ));
            mainGrid.add(nameField);

            // Spacer
            mainGrid.add(Box.createRigidArea(new Dimension(0, 10)));

            // --- 2. THEME SECTION ---
            JLabel title = new JLabel("SELECT AESTHETIC PRESET", SwingConstants.CENTER) {
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? currentAccentColor : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            title.setFont(new Font("Segoe UI Bold", Font.PLAIN, 14));
            mainGrid.add(title);

            String initialTheme = currentThemeName;
            boolean appIsDark = isDarkMode();
            for (String name : PRESETS.keySet()) {
                if (PRESETS.get(name).isDark == appIsDark) mainGrid.add(new ThemePill(name, PRESETS.get(name), settings));
            }

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 15));
            footer.setOpaque(false);

            JButton btnApply = createPillActionBtn("APPLY CHANGES", new Color(46, 204, 113));
            JButton btnCancel = createPillActionBtn("CANCEL", new Color(231, 76, 60));

            btnApply.addActionListener(e -> {
                // Save the new username
                OfflineTutorApp.this.customUserName = nameField.getText().trim();
                saveProgress();
                updateWelcomeText(null); // Instantly update dashboard UI
                settings.dispose();
                repaint();
            });
            btnCancel.addActionListener(e -> {
                ((OfflineTutorApp)getOwner()).applyThemePreset(initialTheme);
                settings.dispose();
                repaint();
            });

            footer.add(btnApply); footer.add(btnCancel);
            content.add(mainGrid, BorderLayout.CENTER);
            content.add(footer, BorderLayout.SOUTH);
            settings.add(content);
            settings.setVisible(true);
        }

        private JButton createPillActionBtn(String text, Color baseColor) {
            JButton b = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getModel().isRollover() ? baseColor.brighter() : baseColor);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI Bold", Font.PLAIN, 12));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(text, (getWidth()-fm.stringWidth(text))/2, (getHeight()+fm.getAscent())/2 - 2);
                    g2.dispose();
                }
            };
            b.setPreferredSize(new Dimension(160, 45));
            b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return b;
        }

        private void addSidebarBtn(JPanel p, String text, boolean active, java.awt.event.ActionListener act) {
            SidebarItem btn = new SidebarItem(text, active);
            btn.addActionListener(act);
            btn.addActionListener(e -> { for(SidebarItem b: sidebarButtons) b.setActive(b==btn); });
            sidebarButtons.add(btn);
            p.add(btn); p.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        private JButton createSimpleLinkBtn(String text) {
            JButton b = new JButton(text) {
                protected void paintComponent(Graphics g) {
                    setForeground(isDarkMode() ? new Color(160, 200, 180) : Color.BLACK);
                    super.paintComponent(g);
                }
            };
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent e) { b.setForeground(currentAccentColor); }
                public void mouseExited(java.awt.event.MouseEvent e) { b.setForeground(isDarkMode() ? new Color(160, 200, 180) : Color.BLACK); }
            });
            return b;
        }

        private void closeAndSwitch() {
            OfflineTutorApp mainApp = (OfflineTutorApp) getOwner();
            if(mainApp != null) {
                mainApp.syncThemeBackgrounds();
                if(isChatMode) mainApp.switchToChatMode();
                else if(isAudioMode) mainApp.switchToAudioMode();
                else mainApp.switchToQuizMode();
                mainApp.setLocationRelativeTo(null); mainApp.setVisible(true);
            }
            this.dispose();
        }

        private void startDisplayTimer() {
            new javax.swing.Timer(2000, e -> {
                if (displayStep++ % 2 == 0) dynamicDisplayLabel.setText(new java.text.SimpleDateFormat("hh:mm a").format(new java.util.Date()));
                else dynamicDisplayLabel.setText(new java.text.SimpleDateFormat("dd MMM").format(new java.util.Date()));
            }).start();
        }

        private class SidebarItem extends JButton {
            private boolean isActive;
            private float hoverAlpha = 0.0f;
            private int shiftX = 0;
            private javax.swing.Timer animTimer;

            public SidebarItem(String text, boolean active) {
                super(text); this.isActive = active;
                setHorizontalAlignment(SwingConstants.LEFT);
                setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);
                setOpaque(false); setCursor(new Cursor(Cursor.HAND_CURSOR));
                setBorder(BorderFactory.createEmptyBorder(12, 25, 12, 25));
                setMaximumSize(new Dimension(240, 50));
                updateLook();

                addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseEntered(java.awt.event.MouseEvent e) { if (!isActive) startAnimation(true); }
                    public void mouseExited(java.awt.event.MouseEvent e) { if (!isActive) startAnimation(false); }
                });
            }

            private void startAnimation(boolean forward) {
                if (animTimer != null && animTimer.isRunning()) animTimer.stop();
                animTimer = new javax.swing.Timer(15, e -> {
                    boolean done = true;
                    if (forward && hoverAlpha < 1.0f) { hoverAlpha += 0.15f; if (hoverAlpha > 1.0f) hoverAlpha = 1.0f; done = false; }
                    else if (!forward && hoverAlpha > 0.0f) { hoverAlpha -= 0.15f; if (hoverAlpha < 0.0f) hoverAlpha = 0.0f; done = false; }
                    if (forward && shiftX < 6) { shiftX++; done = false; }
                    else if (!forward && shiftX > 0) { shiftX--; done = false; }
                    repaint();
                    if (done) ((javax.swing.Timer) e.getSource()).stop();
                });
                animTimer.start();
            }

            public void setActive(boolean a) { this.isActive = a; if (a) { hoverAlpha = 0; shiftX = 0; } updateLook(); repaint(); }

            public void updateLook() {
                boolean dark = isDarkMode();
                setForeground(isActive ? Color.WHITE : (dark ? new Color(180, 200, 190) : Color.BLACK));
                setFont(new Font("Segoe UI", isActive ? Font.BOLD : Font.PLAIN, 14));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isActive) {
                    g2.setColor(currentAccentColor);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                } else if (hoverAlpha > 0) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hoverAlpha * 0.3f));
                    g2.setColor(currentAccentColor);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                }
                g2.dispose();
                g.translate(shiftX, 0);
                super.paintComponent(g);
                g.translate(-shiftX, 0);
            }
        }

        private class GlassCardPanel extends JPanel {
            public GlassCardPanel() { setOpaque(false); setBorder(BorderFactory.createEmptyBorder(15,15,15,15)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean dark = isDarkMode();
                g2.setColor(dark ? new Color(35, 35, 35) : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(dark ? new Color(60, 60, 60) : new Color(230, 230, 235));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        }

        class ModernCalendar extends JPanel {
            private java.util.Calendar currentDisplayDate;
            private JLabel monthLabel;
            private JPanel gridPanel;
            private int selectedDay = -1;

            public ModernCalendar() {
                currentDisplayDate = java.util.Calendar.getInstance();
                selectedDay = currentDisplayDate.get(java.util.Calendar.DAY_OF_MONTH);
                setLayout(new BorderLayout(0, 10)); setOpaque(false);
                setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
                monthLabel = new JLabel("", SwingConstants.CENTER) {
                    protected void paintComponent(Graphics g) {
                        setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
                        super.paintComponent(g);
                    }
                };
                monthLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
                monthLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                monthLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseClicked(java.awt.event.MouseEvent e) { showDateSelectionDialog(); }
                });
                header.add(createNavButton("<", -1), BorderLayout.WEST);
                header.add(monthLabel, BorderLayout.CENTER);
                header.add(createNavButton(">", 1), BorderLayout.EAST);
                add(header, BorderLayout.NORTH);

                gridPanel = new JPanel(new GridLayout(0, 7, 3, 3)); gridPanel.setOpaque(false);
                add(gridPanel, BorderLayout.CENTER);
                refreshCalendar();
            }

            private JButton createNavButton(String text, int offset) {
                JButton b = new JButton(text); b.setContentAreaFilled(false); b.setBorderPainted(false);
                b.setForeground(Color.GRAY); b.setFont(new Font("Segoe UI", Font.BOLD, 16));
                b.addActionListener(e -> { currentDisplayDate.add(java.util.Calendar.MONTH, offset); refreshCalendar(); });
                return b;
            }

            private void showDateSelectionDialog() {
                JPanel p = new JPanel(new GridLayout(1, 2));
                String[] ms = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                JComboBox<String> mb = new JComboBox<>(ms);
                mb.setSelectedIndex(currentDisplayDate.get(java.util.Calendar.MONTH));
                JSpinner ys = new JSpinner(new SpinnerNumberModel(currentDisplayDate.get(java.util.Calendar.YEAR), 1900, 2100, 1));
                p.add(mb); p.add(ys);
                if (JOptionPane.showConfirmDialog(this, p, "Jump to", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                    currentDisplayDate.set(java.util.Calendar.MONTH, mb.getSelectedIndex());
                    currentDisplayDate.set(java.util.Calendar.YEAR, (Integer) ys.getValue());
                    refreshCalendar();
                }
            }

            private void refreshCalendar() {
                gridPanel.removeAll();
                java.util.Calendar calDate = (java.util.Calendar) currentDisplayDate.clone();
                calDate.set(java.util.Calendar.DAY_OF_MONTH, 1);
                monthLabel.setText(new java.text.SimpleDateFormat("MMM yyyy").format(calDate.getTime()));

                String[] days = {"S", "M", "T", "W", "T", "F", "S"};
                for (String d : days) {
                    JLabel l = new JLabel(d, SwingConstants.CENTER);
                    l.setForeground(Color.GRAY); l.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    gridPanel.add(l);
                }
                for (int i = 0; i < calDate.get(java.util.Calendar.DAY_OF_WEEK) - 1; i++) gridPanel.add(new JLabel(""));

                java.util.Calendar now = java.util.Calendar.getInstance();
                int tDay = -1;
                if (now.get(java.util.Calendar.MONTH) == calDate.get(java.util.Calendar.MONTH) && now.get(java.util.Calendar.YEAR) == calDate.get(java.util.Calendar.YEAR)) tDay = now.get(java.util.Calendar.DAY_OF_MONTH);
                final int today = tDay;

                for (int i = 1; i <= calDate.getActualMaximum(java.util.Calendar.DAY_OF_MONTH); i++) {
                    final int d = i;
                    JLabel cell = new JLabel(String.valueOf(i), SwingConstants.CENTER) {
                        protected void paintComponent(Graphics g) {
                            Color liveAccent = OfflineTutorApp.this.currentAccentColor;
                            if (d == selectedDay) { g.setColor(liveAccent); g.fillOval(getWidth()/2-10, getHeight()/2-10, 20, 20); setForeground(Color.BLACK); }
                            else if (d == today) { g.setColor(liveAccent); g.drawOval(getWidth()/2-10, getHeight()/2-10, 19, 19); setForeground(liveAccent); }
                            else { setForeground(isDarkMode() ? Color.WHITE : Color.BLACK); }
                            super.paintComponent(g);
                        }
                    };
                    cell.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    cell.addMouseListener(new java.awt.event.MouseAdapter() {
                        public void mouseClicked(java.awt.event.MouseEvent e) {
                            selectedDay = d;
                            refreshCalendar();

                            java.util.Calendar clickedCal = (java.util.Calendar) currentDisplayDate.clone();
                            clickedCal.set(java.util.Calendar.DAY_OF_MONTH, selectedDay);
                            String dateStr = new java.text.SimpleDateFormat("dd MMM yyyy").format(clickedCal.getTime());
                            PaLOHomePage.this.refreshRecentFilesTable(dateStr);
                        }
                    });
                    gridPanel.add(cell);
                }
                gridPanel.revalidate(); gridPanel.repaint();
            }
        }

        private class DailyLineGraphPanel extends JPanel {
            private List<Float> currentPlotData = new ArrayList<>();

            public DailyLineGraphPanel() {
                setOpaque(false);
                setPreferredSize(new Dimension(400, 220));
            }

            public void setPlotData(List<Float> fileHistory) {
                this.currentPlotData.clear();
                if (fileHistory == null || fileHistory.isEmpty()) {
                    currentPlotData.add(0.0f);
                    currentPlotData.add(0.0f);
                } else if (fileHistory.size() == 1) {
                    currentPlotData.add(fileHistory.get(0));
                    currentPlotData.add(fileHistory.get(0));
                } else {
                    float sum = 0;
                    for (int i = 0; i < fileHistory.size(); i++) {
                        sum += fileHistory.get(i);
                        currentPlotData.add(sum / (i + 1));
                    }
                }
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int mL = 50, mB = 35, mR = 20, mT = 20;
                int w = getWidth(), h = getHeight();
                int gW = w - mL - mR, gH = h - mT - mB;

                g2.setColor(isDarkMode() ? new Color(255, 255, 255, 80) : new Color(0,0,0,40));
                g2.drawLine(mL, mT, mL, mT + gH);
                g2.drawLine(mL, mT + gH, mL + gW, mT + gH);

                if (currentPlotData.isEmpty()) { g2.dispose(); return; }

                double xScale = (double) gW / (currentPlotData.size() - 1);
                java.util.List<Point> pts = new java.util.ArrayList<>();
                for (int i = 0; i < currentPlotData.size(); i++) {
                    int x = (int) (mL + i * xScale);
                    int y = (int) (mT + gH - currentPlotData.get(i) * gH);
                    pts.add(new Point(x, y));
                }

                Path2D path = new Path2D.Double();
                path.moveTo(pts.get(0).x, mT + gH);
                for (Point p : pts) path.lineTo(p.x, p.y);
                path.lineTo(pts.get(pts.size() - 1).x, mT + gH);
                path.closePath();

                Color liveAccent = OfflineTutorApp.this.currentAccentColor;
                g2.setPaint(new GradientPaint(0, mT, new Color(liveAccent.getRed(), liveAccent.getGreen(), liveAccent.getBlue(), 80), 0, mT + gH, new Color(liveAccent.getRed(), liveAccent.getGreen(), liveAccent.getBlue(), 0)));
                g2.fill(path);

                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(liveAccent);
                for (int i = 0; i < pts.size() - 1; i++) {
                    g2.drawLine(pts.get(i).x, pts.get(i).y, pts.get(i+1).x, pts.get(i+1).y);
                }

                for (Point p : pts) {
                    g2.setColor(isDarkMode() ? Color.WHITE : Color.DARK_GRAY);
                    g2.fillOval(p.x - 4, p.y - 4, 8, 8);
                    g2.setColor(liveAccent);
                    g2.drawOval(p.x - 4, p.y - 4, 8, 8);
                }
                g2.dispose();
            }
        }
    }

    private void playNotificationBell() {
        new Thread(() -> {
            try {
                File bellPath = new File("assets/notification_bell.wav");
                if (bellPath.exists()) {
                    AudioInputStream audioInput = AudioSystem.getAudioInputStream(bellPath);
                    Clip bellClip = AudioSystem.getClip();
                    bellClip.open(audioInput);
                    bellClip.start();
                }
            } catch (Exception ex) {
                System.out.println("Audio Error: " + ex.getMessage());
            }
        }).start();
    }

    private void triggerRestModeInternal(int restMins) {
        focusPanel.removeAll();
        BackgroundPanel restBG = new BackgroundPanel("assets/rest_bg.jpg");
        restBG.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel restLabel = new JLabel("TIME TO RECHARGE");
        restLabel.setFont(new Font("Segoe UI Black", Font.BOLD, 32));
        restLabel.setForeground(new Color(46, 204, 113));

        CircularTimer restTimer = new CircularTimer();
        restTimer.setSeconds(restMins * 60);
        restTimer.setRunning(true);

        javax.swing.Timer breakManager = new javax.swing.Timer(1000, e -> {
            if (restTimer.getSeconds() > 0) {
                restTimer.setSeconds(restTimer.getSeconds() - 1);
            } else {
                ((javax.swing.Timer)e.getSource()).stop();
                switchToFocusMode();
            }
        });

        gbc.gridy = 0; gbc.insets = new Insets(0, 0, 20, 0);
        restBG.add(restLabel, gbc);
        gbc.gridy = 1;
        restBG.add(restTimer, gbc);

        focusPanel.add(restBG, BorderLayout.CENTER);
        focusPanel.revalidate();
        focusPanel.repaint();
        breakManager.start();
    }


    private JButton createTechniqueButton(String title, String subtitle, Color theme) {
        String subColor = isDarkMode() ? "#BBBBBB" : "#444444";
        JButton btn = new JButton("<html><center><b>" + title + "</b><br>" +
                "<font size='3' color='" + subColor + "'>" + subtitle + "</font></center></html>"){
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isDark = isDarkMode();
                if (getModel().isRollover()) {
                    g2.setColor(theme);
                } else {
                    g2.setColor(isDark ? new Color(255, 255, 255, 20) : new Color(0, 0, 0, 20));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        btn.setForeground(isDarkMode() ? Color.WHITE : Color.BLACK);
        btn.setPreferredSize(new Dimension(280, 100));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }



    private static class TutorTranslator implements Translator<float[], Float> {
        private final int FIXED_SEQUENCE_LENGTH = 10;
        @Override
        public NDList processInput(TranslatorContext ctx, float[] input) {
            NDManager manager = ctx.getNDManager();
            float[] paddedInput = new float[FIXED_SEQUENCE_LENGTH];
            Arrays.fill(paddedInput, 0.5f);
            int startIdx = Math.max(0, input.length - FIXED_SEQUENCE_LENGTH);
            int fillCount = Math.min(input.length, FIXED_SEQUENCE_LENGTH);
            System.arraycopy(input, startIdx, paddedInput, FIXED_SEQUENCE_LENGTH - fillCount, fillCount);
            return new NDList(manager.create(paddedInput).reshape(1, FIXED_SEQUENCE_LENGTH, 1));
        }
        @Override
        public Float processOutput(TranslatorContext ctx, NDList list) {
            return list.singletonOrThrow().getFloat();
        }
    }

    private void setAppIcon() {
        try {
            File iconFile = new File("logo.png");
            if (iconFile.exists()) {
                BufferedImage img = ImageIO.read(iconFile);
                this.setIconImage(img);
            } else {
                System.out.println("Taskbar icon not found at: " + iconFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Could not load app icon: " + e.getMessage());
        }
    }

    private void resetButtonStyles() {
        QuizOptionButton[] buttons = {btnA, btnB, btnC, btnD};
        for (QuizOptionButton btn : buttons) {
            btn.resetVisual();
        }
    }

    public static void main(String[] args) {
        com.formdev.flatlaf.FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new OfflineTutorApp());
    }

    private class VoiceAssistant {
        private com.sun.speech.freetts.Voice voice;
        public VoiceAssistant() {
            System.setProperty("freetts.voices", "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");
            this.voice = com.sun.speech.freetts.VoiceManager.getInstance().getVoice("kevin16");
            if (this.voice != null) {
                this.voice.allocate();
                this.voice.setRate(130.0f);
                this.voice.setPitch(110.0f);
            } else {
                System.err.println("Error: Kevin16 voice not found in classpath.");
            }
        }
        public void setRate(float rate) {
            if (voice != null) voice.setRate(rate);
        }
        public void setPitch(float pitch) {
            if (voice != null) voice.setPitch(pitch);
        }
        public void speak(String text) {
            if (voice == null || text == null) return;
            if (!isSpeechEnabled) {
                voice.getAudioPlayer().cancel();
                return;
            }
            new Thread(() -> {
                try {
                    voice.getAudioPlayer().cancel();
                    voice.speak(text);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    class FlashcardDeckDialog extends JDialog {
        private int currentIndex = 0;
        private boolean isFlipped = false;

        public FlashcardDeckDialog(Frame owner, String[][] cards) {
            super(owner, "PaLO Flashcard Study", true);
            setSize(700, 450);
            setLocationRelativeTo(owner);
            boolean isDark = UIManager.getLookAndFeel().getClass().getName().contains("Dark");
            getContentPane().setBackground(isDark ? new Color(18, 18, 18) : Color.WHITE);
            setLayout(new BorderLayout());

            JPanel cardContainer = new JPanel(new CardLayout());
            cardContainer.setOpaque(false);
            cardContainer.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));

            JLabel contentLabel = new JLabel("", SwingConstants.CENTER);
            contentLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 28));

            JPanel mainCard = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    boolean isDarkScope = UIManager.getLookAndFeel().getClass().getName().contains("Dark");
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (isDarkScope) {
                        g2.setColor(isFlipped ? new Color(30, 40, 50) : new Color(45, 45, 45));
                    } else {
                        g2.setColor(isFlipped ? new Color(220, 240, 255) : new Color(240, 240, 240));
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                    g2.setColor(isFlipped ? new Color(0, 220, 255) : (isDarkScope ? new Color(100, 100, 100) : new Color(180, 180, 180)));
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 30, 30);
                    g2.dispose();
                }
            };
            mainCard.setOpaque(false);
            mainCard.add(contentLabel, BorderLayout.CENTER);
            mainCard.setCursor(new Cursor(Cursor.HAND_CURSOR));

            Runnable updateUI = () -> {
                boolean isDarkScope = UIManager.getLookAndFeel().getClass().getName().contains("Dark");
                isFlipped = false;
                contentLabel.setText("<html><center>" + cards[currentIndex][0] + "</center></html>");
                contentLabel.setForeground(isDarkScope ? Color.WHITE : Color.BLACK);
                mainCard.repaint();
            };

            mainCard.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    boolean isDarkScope = UIManager.getLookAndFeel().getClass().getName().contains("Dark");
                    isFlipped = !isFlipped;
                    if (isFlipped) {
                        contentLabel.setText("<html><center style='padding:20px;'>" + cards[currentIndex][1] + "</center></html>");
                        contentLabel.setForeground(isDarkScope ? new Color(0, 220, 255) : new Color(0, 150, 200));
                    } else {
                        contentLabel.setText("<html><center>" + cards[currentIndex][0] + "</center></html>");
                        contentLabel.setForeground(isDarkScope ? Color.WHITE : Color.BLACK);
                    }
                    mainCard.repaint();
                }
            });

            JButton btnPrev = createNavArrow("«");
            JButton btnNext = createNavArrow("»");

            btnPrev.addActionListener(e -> {
                if (currentIndex > 0) { currentIndex--; updateUI.run(); }
            });
            btnNext.addActionListener(e -> {
                if (currentIndex < cards.length - 1) { currentIndex++; updateUI.run(); }
            });

            JPanel centerPanel = new JPanel(new BorderLayout(20, 0));
            centerPanel.setOpaque(false);
            centerPanel.add(btnPrev, BorderLayout.WEST);
            centerPanel.add(mainCard, BorderLayout.CENTER);
            centerPanel.add(btnNext, BorderLayout.EAST);

            JLabel hint = new JLabel("Click the card to reveal the answer", SwingConstants.CENTER);
            hint.setForeground(Color.GRAY);
            hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

            add(centerPanel, BorderLayout.CENTER);
            add(hint, BorderLayout.SOUTH);
            updateUI.run();
        }

        private JButton createNavArrow(String arrow) {
            JButton b = new JButton(arrow);
            b.setFont(new Font("Segoe UI", Font.BOLD, 40));
            b.setForeground(new Color(100, 100, 100));
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    boolean isDarkScope = UIManager.getLookAndFeel().getClass().getName().contains("Dark");
                    b.setForeground(isDarkScope ? Color.WHITE : Color.BLACK);
                }
                public void mouseExited(MouseEvent e) {
                    b.setForeground(new Color(100, 100, 100));
                }
            });
            return b;
        }
    }

    public void startFlashcardWorkflow() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Textbook for Flashcards");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File selectedFile = chooser.getSelectedFile();
        logRecentFile(selectedFile.getName(), "Flashcards", 10);
        JDialog progress = new JDialog(this, "PaLO - Thinking", true);
        progress.add(new JLabel("AI is extracting concepts and creating cards...", SwingConstants.CENTER));
        progress.setSize(400, 100);
        progress.setLocationRelativeTo(this);

        new Thread(() -> {
            try {
                String text = processPDF(selectedFile);
                if (text.length() > 4000) text = text.substring(0, 4000);

                String prompt = "Extract 10 key concepts from this text and turn them into Flashcards.\n" +
                        "Format your response EXACTLY using these XML tags:\n" +
                        "<card>\n" +
                        "<front>Question or Term</front>\n" +
                        "<back>Concise 1-sentence explanation</back>\n" +
                        "</card>";

                String raw = llamaConnection.generateMCQs(text, prompt, 10);

                Pattern cardPattern = Pattern.compile("<card>(.*?)</card>", Pattern.DOTALL);
                Pattern fPattern = Pattern.compile("<front>(.*?)</front>", Pattern.DOTALL);
                Pattern bPattern = Pattern.compile("<back>(.*?)</back>", Pattern.DOTALL);

                Matcher cMatcher = cardPattern.matcher(raw);
                List<String[]> tempCards = new ArrayList<>();
                while(cMatcher.find()) {
                    String block = cMatcher.group(1);
                    Matcher fm = fPattern.matcher(block);
                    Matcher bm = bPattern.matcher(block);
                    if (fm.find() && bm.find()) {
                        tempCards.add(new String[]{fm.group(1).trim(), bm.group(1).trim()});
                    }
                }

                if (tempCards.isEmpty()) {
                    throw new Exception("Regex extracted 0 valid flashcards. Please try again.");
                }

                String[][] cards = tempCards.toArray(new String[0][0]);

                SwingUtilities.invokeLater(() -> {
                    progress.dispose();
                    new FlashcardDeckDialog(OfflineTutorApp.this, cards).setVisible(true); // <-- FIXED
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progress.dispose();
                    JOptionPane.showMessageDialog(this, "AI Error: " + ex.getMessage());
                });
            }
        }).start();
        progress.setVisible(true);
    }

    private void setupLoadingOverlay() {
        loadingOverlay = new JPanel(new GridBagLayout());
        // Semi-transparent black background
        loadingOverlay.setBackground(new Color(0, 0, 0, 160));

        JLabel label = new JLabel("PaLO is Orchestrating Questions...") {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(Color.WHITE);
                super.paintComponent(g);
            }
        };
        label.setFont(new Font("Segoe UI Semibold", Font.BOLD, 26));

        // Add a spinner or progress bar inside the overlay for movement
        JProgressBar miniBar = new JProgressBar();
        miniBar.setIndeterminate(true);
        miniBar.setPreferredSize(new Dimension(300, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(0,0,20,0);
        loadingOverlay.add(label, gbc);

        gbc.gridy = 1;
        loadingOverlay.add(miniBar, gbc);

        // Block mouse clicks from reaching the buttons underneath
        loadingOverlay.addMouseListener(new MouseAdapter() {});
    }

    // NEW METHOD: Extracts answers from step-by-step text when Regex fails entirely
    // NEW METHOD: Intelligently extracts the question and answer from step-by-step math output
    private QuizItem salvageQuestionFromText(String rawText, String diff) {
        try {
            String correctAnswer = "Solution";
            // Extract the boxed answer using Regex
            java.util.regex.Matcher mBox = java.util.regex.Pattern.compile("\\\\boxed\\{([^}]*)\\}").matcher(rawText);
            if (mBox.find()) {
                correctAnswer = mBox.group(1).replaceAll("\\\\text\\{.*?\\}", "").replaceAll("[\\\\(\\\\)]", "").trim();
            }

            // SMART FIX: Extract the actual question from the AI's introductory sentence
            String questionText = "What is the correct mathematical solution based on the text?";
            java.util.regex.Matcher mQ = java.util.regex.Pattern.compile("(?i)to find (.*?)[.,]").matcher(rawText);
            if (mQ.find()) {
                questionText = "Find " + mQ.group(1).trim() + ".";
                // Capitalize the first letter for proper formatting
                questionText = questionText.substring(0, 1).toUpperCase() + questionText.substring(1);
            }

            // Prevent insanely long strings from breaking the UI
            if (correctAnswer.length() > 50) correctAnswer = correctAnswer.substring(0, 50);

            List<String> rawOptions = new ArrayList<>();
            rawOptions.add(correctAnswer);

            // Create somewhat realistic fake multiple-choice options based on the correct answer
            String fake1 = correctAnswer.replace("2", "3").replace("4", "5").replace("1", "2");
            if (fake1.equals(correctAnswer)) fake1 = "None of the above";

            String fake2 = correctAnswer.replace("2", "4").replace("4", "8").replace("5", "10");
            if (fake2.equals(correctAnswer)) fake2 = "Data insufficient";

            String fake3 = "Cannot be determined";

            rawOptions.add(fake1);
            rawOptions.add(fake2);
            rawOptions.add(fake3);

            Collections.shuffle(rawOptions);

            List<String> labeledOptions = new ArrayList<>();
            String finalLabeledAnswer = "";
            char label = 'A';
            for (String optText : rawOptions) {
                String fullOpt = label + ". " + optText;
                labeledOptions.add(fullOpt);
                if (optText.equalsIgnoreCase(correctAnswer)) finalLabeledAnswer = fullOpt;
                label++;
            }
            if (finalLabeledAnswer.isEmpty()) finalLabeledAnswer = labeledOptions.get(0);

            return new QuizItem(questionText, questionText, finalLabeledAnswer, labeledOptions, diff);

        } catch (Exception e) {
            return null; // If fallback completely fails, return null to trigger standard error
        }
    }
    private int[] calculateWeightedBuffer(int totalCount, double[] weights) {
        int[] allocation = new int[weights.length];
        int sum = 0;

        // Distribute based on weights
        for (int i = 0; i < weights.length; i++) {
            allocation[i] = (int) Math.round(totalCount * weights[i]);
            sum += allocation[i];
        }

        // Correct any rounding discrepancies to strictly match the requested total
        while (sum < totalCount) { allocation[0]++; sum++; } // Push extras to EASY
        while (sum > totalCount) {
            for (int i = weights.length - 1; i >= 0; i--) {
                if (allocation[i] > 0) { allocation[i]--; sum--; break; }
            }
        }
        return allocation; // Returns array: [Easy, Medium, Hard, Expert]
    }

    private boolean isAllPoolsEmpty() {
        return easyQuestions.isEmpty() && easyMediumQuestions.isEmpty() &&
                mediumQuestions.isEmpty() && mediumHardQuestions.isEmpty() &&
                hardQuestions.isEmpty() && expertQuestions.isEmpty();
    }
    // --- RECENT FILES TRACKING ---
    private void loadRecentFiles() {
        try {
            File f = new File("recent_files.json");
            if (f.exists()) {
                Scanner scanner = new Scanner(f);
                String content = scanner.useDelimiter("\\Z").next();
                scanner.close();
                JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                recentFilesList.clear();
                for (JsonElement el : arr) {
                    recentFilesList.add(el.getAsJsonObject());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveRecentFiles() {
        try {
            FileWriter fw = new FileWriter("recent_files.json");
            JsonArray arr = new JsonArray();
            for (JsonObject obj : recentFilesList) arr.add(obj);
            fw.write(arr.toString());
            fw.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void logRecentFile(String fileName, String subject, int qs) {
        final String finalFileName = fileName.length() > 22 ? fileName.substring(0, 19) + "..." : fileName;

        JsonObject obj = new JsonObject();
        obj.addProperty("date", new java.text.SimpleDateFormat("dd MMM yyyy").format(new java.util.Date()));
        obj.addProperty("time", new java.text.SimpleDateFormat("hh:mm a").format(new java.util.Date()));
        obj.addProperty("fileName", finalFileName);
        obj.addProperty("subject", subject);
        obj.addProperty("qs", String.valueOf(qs));
        obj.add("history", new JsonArray());

        // Add to the top of the list without deleting older attempts of the same file
        recentFilesList.add(0, obj);

        // Increased to 500 so you never lose a day's history
        if (recentFilesList.size() > 500) recentFilesList.remove(recentFilesList.size() - 1);

        saveRecentFiles();

        if (homePageInstance != null) {
            // Auto-refresh the table to show today's updated list
            String today = new java.text.SimpleDateFormat("dd MMM yyyy").format(new java.util.Date());
            SwingUtilities.invokeLater(() -> homePageInstance.refreshRecentFilesTable(today));
        }
    }
    public void setMostRecentDashboard(PaLOHomePage dashboard) {
        this.homePageInstance = dashboard;
    }

    public void applyThemePreset(String name) {
        ThemePreset target = PRESETS.get(name);
        if (target == null) return;

        final Color startSide = this.sidebarColor;
        final Color startWindow = this.windowColor;
        final Color startAccent = this.currentAccentColor;
        final long duration = 400;
        final long startTime = System.currentTimeMillis();

        try {
            if (target.isDark) UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
            else UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        } catch (Exception ex) { ex.printStackTrace(); }

        javax.swing.Timer transitionTimer = new javax.swing.Timer(16, null);
        transitionTimer.addActionListener(e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float fraction = Math.min(1.0f, (float) elapsed / duration);

            if (fraction >= 1.0f) {
                ((javax.swing.Timer) e.getSource()).stop();
                SwingUtilities.updateComponentTreeUI(this);
                if (this.homePageInstance != null) this.homePageInstance.refreshColorsDirectly();
            }

            this.sidebarColor = lerpColor(startSide, target.side, fraction);
            this.windowColor = lerpColor(startWindow, target.main, fraction);
            this.currentAccentColor = lerpColor(startAccent, target.accent, fraction);

            syncThemeBackgrounds();
            if (this.homePageInstance != null && this.homePageInstance.isVisible()) {
                this.homePageInstance.refreshColorsDirectly();
            }
            this.getRootPane().paintImmediately(0, 0, this.getWidth(), this.getHeight());
        });

        currentThemeName = name;
        saveProgress();
        transitionTimer.start();
    }

    private Color lerpColor(Color s, Color e, float f) {
        return new Color(
                (int) (s.getRed() + (e.getRed() - s.getRed()) * f),
                (int) (s.getGreen() + (e.getGreen() - s.getGreen()) * f),
                (int) (s.getBlue() + (e.getBlue() - s.getBlue()) * f)
        );
    }
}