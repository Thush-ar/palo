import ai.djl.Model;
import java.util.regex.Pattern;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import net.sourceforge.tess4j.Tesseract;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import javax.swing.table.DefaultTableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.RenderingHints;
import java.awt.GradientPaint;
import java.awt.BasicStroke;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.*;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
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

public class OfflineTutorApp extends JFrame {


    // --- GUI Components ---
    private JComboBox<String> subjectDropdown;
    private JComboBox<String> timerDropdown;
    private JTextArea questionArea;
    private JLabel aiStatusLabel;
    private JLabel difficultyLabel;
    private JProgressBar masteryBar;
    private JButton btnA, btnB, btnC, btnD;
    private JLabel imageViewer;
    private Clip zenClip; // Stores the audio stream for lo-fi beats

    // --- Logic & Data ---
    private Predictor<float[], Float> predictor;
    private List<Float> studentHistory = new ArrayList<>();
    private List<QuizItem> easyQuestions = new ArrayList<>();
    private List<QuizItem> hardQuestions = new ArrayList<>();
    private Set<String> askedQuestionIDs = new HashSet<>();
    private List<QuizItem> sessionLog = new ArrayList<>();
    private QuizItem currentQuestion;


    // --- Logic & Data ---
    private int questionCounter = 0; // New variable to track question number

    // --- Timer Components ---
    private javax.swing.Timer quizTimer;
    private int secondsRemaining;
    private int userSelectedTime = 0; // 0 means no timer
    private JLabel timerLabel;



    private class GlassCardPanel extends JPanel {
        public GlassCardPanel() {
            setOpaque(false);
            // Added padding to ensure the graph isn't cramped against the 3D edges
            setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. DRAW DROP SHADOW (The 3D "Pop" effect)
            g2.setColor(new Color(0, 0, 0, 120)); // Darker shadow for more depth
            g2.fillRoundRect(10, 10, getWidth() - 20, getHeight() - 20, 35, 35);

            // 2. DRAW CARD BACKGROUND (Semi-transparent Dark Glass)
            g2.setColor(new Color(20, 20, 20, 210));
            g2.fillRoundRect(0, 0, getWidth() - 10, getHeight() - 10, 35, 35);

            // 3. DRAW INNER GLOW BORDER (Creates the glass edge effect)
            GradientPaint glassGlow = new GradientPaint(0, 0, new Color(255, 255, 255, 60),
                    getWidth(), getHeight(), new Color(255, 255, 255, 10));
            g2.setPaint(glassGlow);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawRoundRect(0, 0, getWidth() - 10, getHeight() - 10, 35, 35);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private class ModeSelectionScreen extends JDialog {
        public ModeSelectionScreen(Frame owner) {
            super(owner, "PaLO - Select Orchestration Mode", true);
            setUndecorated(true);
            setSize(850, 450);
            setLocationRelativeTo(owner);

            // Main Background Panel with a subtle gradient-like dark color
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(new Color(18, 18, 18));
            mainPanel.setBorder(BorderFactory.createLineBorder(new Color(45, 45, 45), 2));

            // Header Label
            JLabel header = new JLabel("Select Mode", SwingConstants.CENTER);
            header.setFont(new Font("Monospace", Font.BOLD, 22));
            header.setForeground(Color.WHITE);
            header.setBorder(BorderFactory.createEmptyBorder(30, 0, 10, 0));
            mainPanel.add(header, BorderLayout.NORTH);

            // Container for the 3 Cards
            JPanel cardContainer = new JPanel(new GridLayout(1, 3, 25, 0));
            cardContainer.setBackground(new Color(18, 18, 18));
            cardContainer.setBorder(BorderFactory.createEmptyBorder(30, 40, 50, 40));

            // Create the three options with specific accent colors
            JButton btnScan = createModernCard("Scan Pages", "📄", "Analyze textbook pages in a jiffy!", new Color(46, 204, 113));
            JButton btnAudio = createModernCard("Audio Assisted", "🔊", "Interactive voice mode for the differently abled", new Color(52, 152, 219));
            JButton btnOnline = createModernCard("Online Mode", "🌐", "For more extensive learning", new Color(149, 165, 166));

            btnOnline.setEnabled(false); // Locked as requested

            btnScan.addActionListener(e -> { isAudioMode = false; dispose(); });
            btnAudio.addActionListener(e -> { isAudioMode = true; dispose(); });

            cardContainer.add(btnScan);
            cardContainer.add(btnAudio);
            cardContainer.add(btnOnline);

            mainPanel.add(cardContainer, BorderLayout.CENTER);
            add(mainPanel);
        }

        private JButton createModernCard(String title, String icon, String subtitle, Color accentColor) {
            String content = "<html><center>" +
                    "<font size='7' color='" + toHex(accentColor) + "'>" + icon + "</font><br><br>" +
                    "<font size='5' color='white'><b>" + title + "</b></font><br>" +
                    "<font size='3' color='#888888'>" + subtitle + "</font>" +
                    "</center></html>";

            JButton b = new JButton(content);
            b.setFocusPainted(false);
            b.setContentAreaFilled(false); // We will draw our own background
            b.setOpaque(false);
            b.setForeground(Color.WHITE);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));

            // Custom Card Border and Background
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(40, 40, 40), 1, true),
                    BorderFactory.createEmptyBorder(20, 10, 20, 10)
            ));

            // Hover and Animation Effects
            b.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    if (b.isEnabled()) {
                        b.setBackground(new Color(35, 35, 35));
                        b.setBorder(BorderFactory.createLineBorder(accentColor, 1, true));
                        b.setOpaque(true);
                    }
                }
                public void mouseExited(java.awt.event.MouseEvent e) {
                    b.setOpaque(false);
                    b.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40), 1, true));
                }
            });

            return b;
        }

        private String toHex(Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }

    class CircleMasteryPanel extends JPanel {
        private int progress = 0;

        public void setProgress(int p) {
            this.progress = p;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 20;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            // Background Track
            g2.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(50, 50, 50));
            g2.drawOval(x, y, size, size);

            // Progress Arc
            g2.setColor(new Color(46, 204, 113));
            g2.drawArc(x, y, size, size, 90, -(int)(progress * 3.6));

            // Center Text
            String txt = progress + "%";
            g2.setFont(new Font("Segoe UI", Font.BOLD, 24));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(txt)) / 2;
            int ty = (getHeight() / 2) + (fm.getAscent() / 3);
            g2.drawString(txt, tx, ty);
        }
    }

    private static class QuizItem {
        String id;
        String questionText;
        String displaySentence; // Store the original, unedited sentence here
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

    // --- SUBJECT SPECIFIC STOP WORDS ---
    private List<String> currentBannedTopics = new ArrayList<>();
    private String selectedSubject = "General";
    // --- META-LANGUAGE FILTER (Bans "Classroom Talk") ---
    private static final List<String> META_PHRASES = Arrays.asList(
            "let us", "we will", "we shall", "in this chapter", "in this section",
            "study about", "discuss about", "consider the", "refer to", "look at",
            "as mentioned", "following is", "note that", "is given by", "shown in"
    );
    private static final Map<String, List<String>> SUBJECT_BAN_LISTS = new HashMap<>();
    static {
        // Physics: Added "media", "values", "constants", "rays"
        SUBJECT_BAN_LISTS.put("Physics", Arrays.asList(
                "figure", "table", "shown", "medium", "media", "value", "values",
                "constant", "constants", "diagram", "consider", "angle", "angles",
                "solution", "example", "direction", "magnitude", "ray", "rays", "index", "indices"
        ));

        // Computer Science
        SUBJECT_BAN_LISTS.put("Computer Science", Arrays.asList(
                "figure", "output", "input", "code", "program", "example", "shown",
                "value", "values", "variable", "variables", "line", "following", "statement"
        ));

        // Biology
        SUBJECT_BAN_LISTS.put("Biology", Arrays.asList(
                "figure", "diagram", "shown", "structure", "structures", "function",
                "process", "example", "type", "types", "part", "parts"
        ));

        // General
        SUBJECT_BAN_LISTS.put("General", Arrays.asList(
                "figure", "table", "shown", "example", "problem", "solution", "chapter"
        ));
    }

    private class BackgroundPanel extends JPanel {
        private ImageIcon gifIcon;

        // Use a String parameter to accept the path
        public BackgroundPanel(String path) {
            File f = new File(path);
            if (f.exists()) {
                this.gifIcon = new ImageIcon(path);
            }
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            if (gifIcon != null) {
                g2.drawImage(gifIcon.getImage(), 0, 0, getWidth(), getHeight(), this);
            }
            // Dark Tint Overlay for minimalist look
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    // Define this as a proper inner class so the casting works
    private class ModernToggle extends JButton {
        private boolean active = false;
        private String label;

        public ModernToggle(String label) {
            this.label = label;
            setText(label + ": OFF");
            setFont(new Font("Segoe UI Bold", Font.PLAIN, 12));
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(140, 40));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        public void toggle() {
            active = !active;
            setText(label + (active ? ": ON" : ": OFF"));
            repaint();
        }

        public boolean isActive() { return active; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background: If active, use theme color, else dark gray
            g2.setColor(active ? currentAccentColor : new Color(255, 255, 255, 30));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);

            // The Sliding White Knob
            g2.setColor(Color.WHITE);
            int knobSize = getHeight() - 10;
            int x = active ? (getWidth() - knobSize - 5) : 5;
            g2.fillOval(x, 5, knobSize, knobSize);

            super.paintComponent(g);
            g2.dispose();
        }
    }

    class CircularTimer extends JComponent {
        private int seconds = 0;
        private final int MAX_SECONDS = 3600; // 60 minutes max
        private final int INCREMENT = 300; // 5 minute steps
        private boolean isRunning = false;

        public CircularTimer() {
            setPreferredSize(new Dimension(220, 220));
            setMaximumSize(new Dimension(220, 220));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // Trigger custom input if clicking near the center circle
                    if (e.getPoint().distance(getWidth()/2.0, getHeight()/2.0) < 45) {
                        showCustomInputDialog();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isRunning) return;

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
        public int getSeconds() {
            return this.seconds;
        }
        public void setRunning(boolean r) { this.isRunning = r; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int radius = Math.min(getWidth(), getHeight()) / 2 - 20;

            // --- THEME-REACTIVE COLOR LOGIC ---
            // Priority 1: Red Warning if timer < 60s and running
            // Priority 2: Use the Global Accent Color selected in Settings
            Color themeColor;
            if (isRunning && seconds > 0 && seconds <= 60) {
                themeColor = new Color(255, 45, 85); // Critical Red
            } else {
                themeColor = currentAccentColor; // Reactive Global Theme
            }

            // 1. DRAW ANALOG TICK MARKS
            for (int i = 0; i < 60; i++) {
                double angle = Math.toRadians(i * 6 - 90);
                int lineStart = (i % 5 == 0) ? radius - 12 : radius - 6;
                g2.setColor(i % 5 == 0 ? new Color(255, 255, 255, 180) : new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(i % 5 == 0 ? 2f : 1f));

                int x1 = (int) (centerX + lineStart * Math.cos(angle));
                int y1 = (int) (centerY + lineStart * Math.sin(angle));
                int x2 = (int) (centerX + radius * Math.cos(angle));
                int y2 = (int) (centerY + radius * Math.sin(angle));
                g2.drawLine(x1, y1, x2, y2);
            }

            // 2. DRAW PROGRESS ARC & GLOW
            int extent = (int) (((double) seconds / MAX_SECONDS) * 360);
            // Neon Glow
            g2.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 40));
            g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, 90, -extent);
            // Sharp Arc
            g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(themeColor);
            g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, 90, -extent);

            // 3. DRAW MOVING HAND KNOB
            double handAngle = Math.toRadians(((double) seconds / MAX_SECONDS * 360) - 90);
            int handX = (int) (centerX + radius * Math.cos(handAngle));
            int handY = (int) (centerY + radius * Math.sin(handAngle));

            g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 80));
            g2.fillOval(handX - 10, handY - 10, 20, 20); // Glow
            g2.setColor(Color.WHITE);
            g2.fillOval(handX - 6, handY - 6, 12, 12); // Solid Center

            // 4. CENTER CONCENTRIC TEXT AREA
            int innerR = 48;
            g2.setColor(new Color(30, 30, 30));
            g2.fillOval(centerX - innerR, centerY - innerR, innerR * 2, innerR * 2);
            g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 120));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(centerX - innerR, centerY - innerR, innerR * 2, innerR * 2);

            // 5. CENTERED TEXT
            g2.setFont(new Font("Segoe UI Black", Font.BOLD, 22));
            g2.setColor(Color.WHITE);
            String timeStr = (seconds < 60 && isRunning) ? seconds + "s" : (seconds / 60) + "m";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(timeStr, centerX - (fm.stringWidth(timeStr) / 2), centerY + (fm.getAscent() / 3));

            g2.dispose();
        }
    }



    private class SplashScreen extends JDialog {
        public SplashScreen() {
            setUndecorated(true);
            setSize(800, 480);
            setLocationRelativeTo(null);

            ImageIcon backgroundGif = new ImageIcon("background.gif");

            // Use a LayeredPane so the Quit button can sit "on top" of everything easily
            JLayeredPane layeredPane = new JLayeredPane();
            layeredPane.setPreferredSize(new Dimension(800, 480));

            JPanel contentPane = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (backgroundGif != null) {
                        g.drawImage(backgroundGif.getImage(), 0, 0, getWidth(), getHeight(), this);
                    }
                }
            };
            contentPane.setBounds(0, 0, 800, 480);

            // --- QUIT BUTTON (Top Right) ---
            JButton quitBtn = new JButton("<html><div style='text-shadow: 1px 1px 2px #000000;'>✕ Quit</div></html>");
            quitBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            quitBtn.setForeground(Color.WHITE);
            quitBtn.setContentAreaFilled(false); // Transparent background
            quitBtn.setBorderPainted(false);
            quitBtn.setFocusPainted(false);
            quitBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            // Position it in the top right corner
            quitBtn.setBounds(700, 10, 80, 30);

            quitBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to exit PaLO?", "Quit Application",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            });

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new java.awt.Insets(40, 60, 40, 60);

            // 1. PaLO Title
            JLabel titleLabel = new JLabel("<html><div style='text-shadow: 3px 3px 6px #000000;'>PaLO</div></html>");
            titleLabel.setFont(new Font("Serif", Font.PLAIN, 110));
            titleLabel.setForeground(Color.WHITE);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 0.5;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            contentPane.add(titleLabel, gbc);

            // 2. Full Name
            String fullNameHtml = "<html><div style='text-shadow: 2px 2px 4px #000000;'>" +
                    "Progressive and<br>Audio assisted<br>Learning<br>Orchestrator</div></html>";
            JLabel fullNameLabel = new JLabel(fullNameHtml);
            fullNameLabel.setFont(new Font("Serif", Font.PLAIN, 28));
            fullNameLabel.setForeground(Color.WHITE);
            gbc.gridx = 0; gbc.gridy = 1;
            gbc.weightx = 1.0; gbc.weighty = 0.5;
            gbc.anchor = GridBagConstraints.SOUTHWEST;
            contentPane.add(fullNameLabel, gbc);

            // 3. Initializing Text
            JLabel initLabel = new JLabel("<html><div style='text-shadow: 1px 1px 3px #000000;'>" +
                    "Initializing Mathematical Engines...</div></html>");
            initLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            initLabel.setForeground(new Color(230, 230, 230));
            gbc.gridx = 1; gbc.gridy = 1;
            gbc.weightx = 0; gbc.weighty = 0.5;
            gbc.anchor = GridBagConstraints.SOUTHEAST;
            contentPane.add(initLabel, gbc);

            // Add components to LayeredPane
            layeredPane.add(contentPane, JLayeredPane.DEFAULT_LAYER);
            layeredPane.add(quitBtn, JLayeredPane.PALETTE_LAYER);

            // Progress Bar
            JProgressBar loading = new JProgressBar();
            loading.setIndeterminate(true);
            loading.setPreferredSize(new Dimension(800, 4));
            loading.setForeground(new Color(46, 204, 113));
            loading.setBorder(null);

            add(layeredPane, BorderLayout.CENTER);
            add(loading, BorderLayout.SOUTH);
        }
    }

    private JButton createQuitQuizButton() {
        JButton btn = new JButton("END QUIZ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(255, 45, 85) : new Color(255, 45, 85, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
                g2.dispose();
            }
        };

        btn.setFont(new Font("Segoe UI Black", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> {
            // 1. STOP TIMER & SHOW REPORT
            if (quizTimer != null) quizTimer.stop();
            if (!sessionLog.isEmpty()) showPerformanceReport();

            // 2. HARD RESET OF DATA POOLS (This "Ends" the Quiz)
            easyQuestions.clear();
            hardQuestions.clear();
            sessionLog.clear();
            questionCounter = 0;
            currentQuestion = null;

            // 3. RESET UI DISPLAY
            questionArea.setText("Session Ended. Please scan a new page to begin.");
            difficultyLabel.setText("No Active Session");
            btnA.setText("-"); btnB.setText("-"); btnC.setText("-"); btnD.setText("-");
            btnA.setEnabled(false); btnB.setEnabled(false); btnC.setEnabled(false); btnD.setEnabled(false);

            // 4. TRANSITION
            saveProgress();
            this.setVisible(false);

            SwingUtilities.invokeLater(() -> {
                PaLOHomePage home = new PaLOHomePage(this);
                home.setVisible(true);
            });
        });
        return btn;
    }

    // Add this field at the top of your class
    private boolean isAudioMode = false;

    public OfflineTutorApp() {
        SplashScreen splash = new SplashScreen();
        splash.setVisible(true);

        new Thread(() -> {
            try {
                initAI();
                loadProgress();
                Thread.sleep(3000);
                SwingUtilities.invokeLater(() -> {
                    splash.dispose();

                    // 1. Launch the Homepage (execution pauses here)
                    PaLOHomePage home = new PaLOHomePage(this);
                    home.setVisible(true);

                    // 2. Setup the Main Quiz Window
                    setupUI();

                    // 3. SET WINDOW SIZE (Ensures it is BIG as before)
                    setSize(1400, 850); // Set your desired large dimensions here
                    setLocationRelativeTo(null); // Centers the large window on screen
                    setVisible(true); // Makes the big window visible
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private CircleMasteryPanel circleMastery;


    private CircularTimer clockTimer; // Add this as a field at the top of your class

    private void setupUI() {
        setTitle("PaLO - Adaptive Learning Orchestrator (" + selectedSubject + " Mode)");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        // Inside setupUI()
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // This is the OS "X" button
                int response = JOptionPane.showConfirmDialog(
                        null, "Exit PaLO entirely?", "Confirm Exit",
                        JOptionPane.YES_NO_OPTION);

                if (response == JOptionPane.YES_OPTION) {
                    saveProgress();
                    System.exit(0); // This closes the app entirely
                }
            }
        });

        setLayout(new BorderLayout(20, 0));
        getContentPane().setBackground(new Color(28, 28, 28));

        // --- INITIALIZE SIDEBAR COMPONENTS ---
        aiStatusLabel = new JLabel("Ready", SwingConstants.CENTER);
        aiStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        aiStatusLabel.setForeground(Color.GRAY);

        difficultyLabel = new JLabel("Question #1 | Current Level: EASY", SwingConstants.CENTER);
        difficultyLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        difficultyLabel.setForeground(new Color(230, 126, 34));

        // Initialize New Circular Timer (Clock Button)
        clockTimer = new CircularTimer();
        clockTimer.setAlignmentX(Component.CENTER_ALIGNMENT);

        circleMastery = new CircleMasteryPanel();
        circleMastery.setBackground(new Color(35, 35, 35));
        circleMastery.setAlignmentX(Component.CENTER_ALIGNMENT);
        circleMastery.setPreferredSize(new Dimension(180, 180));
        circleMastery.setMaximumSize(new Dimension(180, 180));

        // --- SIDEBAR (LEFT) ---
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(280, 800));
        sidebar.setBackground(new Color(35, 35, 35));
        sidebar.setBorder(BorderFactory.createEmptyBorder(30, 15, 30, 15));

        addSidebarSection(sidebar, "OVERALL MASTERY", circleMastery);
        sidebar.add(Box.createRigidArea(new Dimension(0, 40)));

        // Replacing old timer text with Interactive Clock
        addSidebarSection(sidebar, "SET DURATION", clockTimer);
        sidebar.add(Box.createRigidArea(new Dimension(0, 15)));

        // New Minimalist Quit Quiz Button
        JButton btnQuit = createQuitQuizButton();
        btnQuit.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnQuit.setMaximumSize(new Dimension(240, 45));
        sidebar.add(btnQuit);

        sidebar.add(Box.createRigidArea(new Dimension(0, 40)));

        subjectDropdown = new JComboBox<>(new String[]{"Physics", "Computer Science", "Biology", "General"});
        subjectDropdown.setMaximumSize(new Dimension(240, 35));
        addSidebarSection(sidebar, "SUBJECT", subjectDropdown);

        sidebar.add(Box.createVerticalGlue());
        sidebar.add(aiStatusLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 15)));

        JButton btnScan = new JButton("SCAN TEXTBOOK");
        btnScan.setAlignmentX(Component.CENTER_ALIGNMENT);

// Increased dimensions for a "bigger" look
        btnScan.setPreferredSize(new Dimension(240, 70));
        btnScan.setMaximumSize(new Dimension(240, 70));

        btnScan.setFont(new Font("Segoe UI Black", Font.BOLD, 16));
        btnScan.setBackground(new Color(52, 152, 219));
        btnScan.setForeground(Color.WHITE);

// Added white border to match the minimalist dashboard style
        btnScan.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        btnScan.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnScan.addActionListener(e -> performScan());
        sidebar.add(btnScan);

        // --- QUIZ CONTENT (CENTER) ---
        JPanel quizContent = new JPanel(new BorderLayout(20, 20));
        quizContent.setOpaque(false);
        quizContent.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 30));

        questionArea = new JTextArea();
        questionArea.setFont(new Font("Segoe UI", Font.PLAIN, 26));
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setBackground(new Color(40, 40, 40));
        questionArea.setForeground(Color.WHITE);
        questionArea.setEditable(false);
        questionArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JScrollPane qScroll = new JScrollPane(questionArea);
        qScroll.getViewport().setOpaque(false);
        qScroll.setOpaque(false);
        qScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 2));

        JPanel btnGrid = new JPanel(new GridLayout(2, 2, 25, 25));
        btnGrid.setOpaque(false);

        // Pure, Highlighted minimalist colors as requested
        btnA = createOptionButton("A", new Color(0, 220, 255));   // Electric Cyan
        btnB = createOptionButton("B", new Color(255, 45, 85));   // Vibrant Rose
        btnC = createOptionButton("C", new Color(50, 255, 120));  // Spring Green
        btnD = createOptionButton("D", new Color(255, 200, 0));   // Cyber Yellow

        btnGrid.add(btnA); btnGrid.add(btnB);
        btnGrid.add(btnC); btnGrid.add(btnD);

        quizContent.add(difficultyLabel, BorderLayout.NORTH);
        quizContent.add(qScroll, BorderLayout.CENTER);
        quizContent.add(btnGrid, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.WEST);
        add(quizContent, BorderLayout.CENTER);
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

    // Helper to keep sidebar text consistent
    private void autoAddSidebarLabel(JPanel panel, String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(new Color(120, 120, 120));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(l);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private JButton createStyledOptionButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(0, 80));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        btn.addActionListener(e -> checkAnswer(btn.getText()));
        return btn;
    }

    private void updateStatus(String text) {
        if (aiStatusLabel != null) {
            aiStatusLabel.setText(text);
        } else {
            System.out.println("Status Update: " + text); // Fallback to console if UI isn't ready
        }
    }

    private void startNewTimer() {
        if (userSelectedTime <= 0) return;
        if (quizTimer != null) quizTimer.stop();

        secondsRemaining = userSelectedTime;
        clockTimer.setRunning(true); // Disable manual dragging during quiz

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

    private JButton createOptionButton(String text, Color uniqueColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // FIX: Use the specific 'uniqueColor' passed for this button, not the global theme
                if (getModel().isRollover()) {
                    g2.setColor(uniqueColor);
                } else {
                    g2.setColor(new Color(30, 30, 30, 150));
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                // ADDED: Draw a white border to match your requested aesthetic
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);

                super.paintComponent(g);
                g2.dispose();
            }
        };

        btn.setFont(new Font("Segoe UI Black", Font.PLAIN, 24));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(0, 100));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> checkAnswer(btn.getText()));
        btn.setEnabled(false); // Enable this only after a scan
        return btn;
    }

    // Helper to determine if text should be black or white for best minimalist contrast
    private boolean isColorBright(Color c) {
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
        return luminance > 0.6;
    }

    // --- LOGIC: CHECK ANSWER ---
    private void checkAnswer(String selectedText) {
        if (quizTimer != null) quizTimer.stop();
        if (currentQuestion == null) return;

        currentQuestion.userProvidedAnswer = selectedText;
        sessionLog.add(currentQuestion);

        boolean isCorrect = selectedText.equals(currentQuestion.correctAnswer);
        float score = isCorrect ? 1.0f : 0.0f;

        studentHistory.add(score);
        updateAI(score); // Mastery updates here
        removeQuestionFromPools(currentQuestion);

        if (isCorrect) {
            JOptionPane.showMessageDialog(this, "Correct! ✅");
        } else {
            JOptionPane.showMessageDialog(this, "Wrong! ❌\nCorrect Answer: " + currentQuestion.correctAnswer);
        }

        // This ensures the next question loads only AFTER the user clicks 'OK'
        boolean nextIsEasy = (score == 0.0f);
        SwingUtilities.invokeLater(() -> loadNextQuestion(nextIsEasy));
    }

    private void showPerformanceReport() {
        if (sessionLog.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No data to generate report.");
            return;
        }

        // --- 1. CALCULATE STATISTICS ---
        long total = sessionLog.size();
        long correctCount = sessionLog.stream()
                .filter(q -> q.userProvidedAnswer.equals(q.correctAnswer)).count();
        double accuracy = (double) correctCount / total * 100;

        // Determine Grade
        String grade;
        Color gradeColor;
        if (accuracy >= 90) { grade = "A+"; gradeColor = new Color(46, 204, 113); }
        else if (accuracy >= 75) { grade = "B"; gradeColor = new Color(52, 152, 219); }
        else if (accuracy >= 50) { grade = "C"; gradeColor = new Color(230, 126, 34); }
        else { grade = "D"; gradeColor = new Color(231, 76, 60); }

        // --- 2. CREATE THE DIALOG ---
        JDialog reportDialog = new JDialog(this, "Student Report Card", true);
        reportDialog.setSize(700, 600);
        reportDialog.setLocationRelativeTo(this);
        reportDialog.setLayout(new BorderLayout());

        // --- 3. HEADER PANEL (Grade & Summary) ---
        JPanel header = new JPanel(new GridLayout(1, 2));
        header.setBackground(new Color(35, 35, 35));
        header.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Left side: Large Grade
        JLabel gradeLabel = new JLabel(grade, SwingConstants.CENTER);
        gradeLabel.setFont(new Font("Serif", Font.BOLD, 80));
        gradeLabel.setForeground(gradeColor);
        gradeLabel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(gradeColor), "FINAL GRADE", 0, 0, null, Color.GRAY));

        // Right side: Statistics
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

        // --- 4. DATA TABLE (Detailed Breakdown) ---
        String[] columns = {"Status", "Topic / Question", "Your Choice", "Correct Answer"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        for (QuizItem q : sessionLog) {
            boolean isCorrect = q.userProvidedAnswer.equals(q.correctAnswer);
            model.addRow(new Object[]{
                    isCorrect ? "PASS" : "FAIL",
                    q.displaySentence.length() > 60 ? q.displaySentence.substring(0, 60) + "..." : q.displaySentence,
                    q.userProvidedAnswer,
                    q.correctAnswer
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setEnabled(false); // Make table read-only
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("DETAILED BREAKDOWN"));

        // --- 5. FOOTER ---
        JButton closeBtn = new JButton("Close Report");
        closeBtn.addActionListener(e -> reportDialog.dispose());
        JPanel footer = new JPanel();
        footer.add(closeBtn);

        reportDialog.add(header, BorderLayout.NORTH);
        reportDialog.add(scrollPane, BorderLayout.CENTER);
        reportDialog.add(footer, BorderLayout.SOUTH);

        reportDialog.setVisible(true);
    }

    private void removeQuestionFromPools(QuizItem q) {
        askedQuestionIDs.add(q.id);
        easyQuestions.removeIf(item -> item.id.equals(q.id));
        hardQuestions.removeIf(item -> item.id.equals(q.id));
    }

    private void updateAI(float score) {
        float probability = 0.5f;
        try {
            if (predictor != null) {
                float[] input = new float[studentHistory.size()];
                for(int i=0; i<studentHistory.size(); i++) input[i] = studentHistory.get(i);
                probability = predictor.predict(input);
            }
        } catch (Exception e) {}

        int percent = (int)(probability * 100);

        // Explicitly update the circle mastery component
        if (circleMastery != null) {
            circleMastery.setProgress(percent);
        }

        if (probability < 0.4) {
            aiStatusLabel.setForeground(new Color(231, 76, 60));
            aiStatusLabel.setText("Status: Review Needed");
        } else {
            aiStatusLabel.setForeground(new Color(46, 204, 113));
            aiStatusLabel.setText("Status: Mastering Topic");
        }
    }

    private void handleExitRequest() {
        int response = JOptionPane.showConfirmDialog(
                this, "Are you sure you want to exit the session?",
                "Exit Confirmation", JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
            showPerformanceReport(); // This now opens the Dialog
            saveProgress();
            System.exit(0);
        }
    }

    private void loadNextQuestion(boolean isEasy) {
        List<QuizItem> pool = isEasy ? easyQuestions : hardQuestions;
        if (pool.isEmpty()) pool = (isEasy) ? hardQuestions : easyQuestions;

        if (pool.isEmpty()) {
            finishQuiz();
            return;
        }


        // Increment the counter every time a new question is loaded
        questionCounter++;

        currentQuestion = pool.get(0);

        // Update the label to show both the Number and the Difficulty
        String levelText = isEasy ? "EASY" : "HARD";
        difficultyLabel.setText("Question #" + questionCounter + " | Current Level: " + levelText);

        if (isEasy) {
            difficultyLabel.setForeground(new Color(230, 126, 34));
        } else {
            difficultyLabel.setForeground(new Color(39, 174, 96));
        }

        questionArea.setText(currentQuestion.questionText);

        btnA.setEnabled(true); btnB.setEnabled(true); btnC.setEnabled(true); btnD.setEnabled(true);
        if (currentQuestion.options.size() >= 4) {
            btnA.setText(currentQuestion.options.get(0));
            btnB.setText(currentQuestion.options.get(1));
            btnC.setText(currentQuestion.options.get(2));
            btnD.setText(currentQuestion.options.get(3));
        }
        if (userSelectedTime > 0) {
            startNewTimer();
        } else {
            timerLabel.setText("Timer: Off");
        }
    }

    private void finishQuiz() {
        questionArea.setText("\n\n   🎉 QUESTIONS ARE OVER! 🎉\n\n   You have completed all valid questions from this page.\n   Please scan a new page or exit.");
        difficultyLabel.setText("Session Complete");
        btnA.setEnabled(false); btnB.setEnabled(false); btnC.setEnabled(false); btnD.setEnabled(false);
        btnA.setText("-"); btnB.setText("-"); btnC.setText("-"); btnD.setText("-");
        JOptionPane.showMessageDialog(this, "Great job! You've finished this section.");
    }

    // --- LOGIC: SCANNING ---
    private void performScan() {
        // 1. Capture current settings from the UI
        selectedSubject = (String) subjectDropdown.getSelectedItem();
        currentBannedTopics = SUBJECT_BAN_LISTS.get(selectedSubject);

        // NEW LOGIC: Time is now set via the CircularTimer interaction, not a dropdown.
        // userSelectedTime is updated automatically when you drag the clock.
        if (userSelectedTime <= 0) {
            // Optional: Default to a specific time if user didn't drag the clock
            // userSelectedTime = 60;
        }

        // 2. Open File Chooser
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Documents (PDF, JPG, PNG)", "pdf", "jpg", "png", "jpeg"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            aiStatusLabel.setText("Processing... Please Wait.");

            // Reset question counter for the new scan
            questionCounter = 0;

            new Thread(() -> {
                try {
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

                    // Generate the questions from extracted text
                    generateMCQ(extractedText.toString());

                    SwingUtilities.invokeLater(() -> {
                        if (easyQuestions.isEmpty() && hardQuestions.isEmpty()) {
                            aiStatusLabel.setText("No valid topics found on this page.");
                            JOptionPane.showMessageDialog(this, "The AI couldn't find enough text. Please try a clearer scan.");
                        } else {
                            aiStatusLabel.setText("Scan Complete!");

                            // SYNC THE CLOCK: Ensure the visual clock matches the selected time
                            clockTimer.setSeconds(userSelectedTime);

                            // START THE QUIZ & TIMER
                            loadNextQuestion(true);
                            startNewTimer();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> aiStatusLabel.setText("Error: " + ex.getMessage()));
                }
            }).start();
        }
    }

    private void playZenMusic() {
        new Thread(() -> {
            try {
                // Ensure you have a .wav file in your project folder
                File musicPath = new File("assets/lofi_beats.wav");
                if (!musicPath.exists()) {
                    System.out.println("Music file not found at: " + musicPath.getAbsolutePath());
                    return;
                }

                AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicPath);
                zenClip = AudioSystem.getClip();
                zenClip.open(audioInput);
                zenClip.loop(Clip.LOOP_CONTINUOUSLY); // Continuous play
                zenClip.start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private void stopZenMusic() {
        if (zenClip != null && zenClip.isRunning()) {
            zenClip.stop();
            zenClip.close();
        }
    }

    private void showSessionSummary() {
        int totalQuestions = studentHistory.size();
        if (totalQuestions == 0) {
            JOptionPane.showMessageDialog(this, "No questions answered this session.");
            return;
        }

        long correctCount = studentHistory.stream().filter(s -> s == 1.0f).count();
        double accuracy = (double) correctCount / totalQuestions * 100;

        // Create a Panel for the Report
        JPanel reportPanel = new JPanel(new BorderLayout(10, 10));
        reportPanel.setPreferredSize(new Dimension(600, 400));

        // Header: Score and Accuracy
        JLabel scoreLabel = new JLabel(String.format(
                "<html><div style='text-align: center;'><h2>Session Results</h2>" +
                        "<b>Score:</b> %d / %d<br>" +
                        "<b>Accuracy:</b> %.1f%%</div></html>",
                correctCount, totalQuestions, accuracy), SwingConstants.CENTER);
        reportPanel.add(scoreLabel, BorderLayout.NORTH);

        // Body: Detailed Table of Errors
        String[] columnNames = {"Question Snapshot", "Your Answer", "Correct Answer"};
        java.util.List<QuizItem> allQuestions = new ArrayList<>();
        allQuestions.addAll(easyQuestions); // This needs to be tracked in a 'master list' instead
        // Note: Since you remove items from pools, you should keep a 'sessionHistory' list of QuizItems

        // For this example, let's assume you've added answered items to a list called 'sessionLog'
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        // Loop through your logged questions and add rows where user was wrong

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        reportPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JOptionPane.showMessageDialog(this, reportPanel, "Performance Report", JOptionPane.PLAIN_MESSAGE);
    }

    private String processPDF(File pdfFile) throws Exception {
        StringBuilder pdfText = new StringBuilder();
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(pdfFile)) {
            org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath("tessdata");

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                final int pageNum = i + 1;
                SwingUtilities.invokeLater(() -> aiStatusLabel.setText("Scanning Page " + pageNum + "..."));

                // 1. Try to extract digital text
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String text = stripper.getText(document);

                // 2. If the page is a scan (empty text), use OCR
                if (text.trim().isEmpty()) {
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 300); // 300 DPI for OCR accuracy
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

    // --- GENERATION WITH PLURAL FILTER ---
    // --- SMART GENERATION: DEFINITION & CONCEPT DETECTION ---
    private void generateMCQ(String text) throws Exception {
        easyQuestions.clear();
        hardQuestions.clear();
        askedQuestionIDs.clear();

        TokenizerModel tokenModel = new TokenizerModel(new FileInputStream("en-token.bin"));
        Tokenizer tokenizer = new TokenizerME(tokenModel);
        POSModel posModel = new POSModel(new FileInputStream("en-pos-maxent.bin"));
        POSTaggerME tagger = new POSTaggerME(posModel);

        // Cleanup text
        text = text.replace("\n", " ").replace("  ", " ");
        String[] sentences = text.split("(?<=[.?!])\\s+");

        // PHASE 1: Find Important Topics (High Frequency Nouns)
        Map<String, Integer> wordFrequency = new HashMap<>();
        for (String sentence : sentences) {
            String[] words = tokenizer.tokenize(sentence);
            String[] tags = tagger.tag(words);
            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                if (w.length() < 4) continue;
                if (currentBannedTopics.contains(w.toLowerCase())) continue;
                if (tags[i].startsWith("NN")) wordFrequency.put(w, wordFrequency.getOrDefault(w, 0) + 1);
            }
        }
        List<String> validTopics = new ArrayList<>(wordFrequency.keySet());

        // PHASE 2: GENERATE SMART QUESTIONS
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];

            // Filter Meta-Talk
            boolean isMeta = false;
            for (String meta : META_PHRASES) if (sentence.toLowerCase().contains(meta)) isMeta = true;
            if (isMeta || sentence.length() < 25) continue;

            String qText = null;
            String answer = null;

            // --- STRATEGY A: "DEFINITION" DETECTION (High Value) ---
            // Detects: "X is called Y" or "X is defined as Y"
            if (sentence.contains(" is called ") || sentence.contains(" is known as ") || sentence.contains(" is defined as ")) {
                for (String topic : validTopics) {
                    // If the sentence talks about a topic appearing AFTER the definition phrase
                    if (sentence.contains(topic) && sentence.indexOf(topic) > sentence.indexOf(" is ")) {
                        answer = topic;
                        // Transform: "The bending of light is called refraction."
                        // To: "What is the term for 'The bending of light'?"
                        String prompt = sentence.substring(0, sentence.indexOf(" is "));
                        qText = "What concept is described by the following definition?\n\n\"" + prompt + "...\"";
                        break;
                    }
                }
            }

            // --- STRATEGY B: "KEYWORD" DETECTION (Medium Value) ---
            // Only if Strategy A failed, fallback to standard Cloze Deletion
            // --- STRATEGY B: CONCEPT IDENTIFICATION (Better Formatting) ---
            if (qText == null) {
                for (String topic : validTopics) {
                    if (sentence.contains(topic)) {
                        answer = topic;

                        // Instead of hiding the word inside the sentence,
                        // show the full sentence and ask what it refers to.
                        qText = "Based on the text, what key concept is being discussed in this sentence?\n\n" +
                                "\"" + sentence.trim() + "\"";

                        // Add Context if the sentence starts with a pronoun
                        if (sentence.matches("^(This|It|These|That).*") && i > 0) {
                            qText = "Context: " + sentences[i-1] + "\n\n" + qText;
                        }
                        break;
                    }
                }
            }

            // Create the Question Object
            if (qText != null && answer != null) {
                List<String> options = new ArrayList<>();
                options.add(answer);
                // Smart Distractors: Pick other topics from the SAME text
                for(int k=0; k<3; k++) {
                    if (!validTopics.isEmpty()) {
                        String dist = validTopics.get((int)(Math.random() * validTopics.size()));
                        if (!options.contains(dist) && !dist.equalsIgnoreCase(answer)) options.add(dist);
                    }
                }
                while(options.size() < 4) options.add("None of the above");
                Collections.shuffle(options);

                // This has 5 arguments: (qText, displaySentence, answer, options, originalContext)
                QuizItem item = new QuizItem(qText, sentence, answer, options, sentence);
                if (!askedQuestionIDs.contains(item.id)) {
                    if (qText.startsWith("What concept")) hardQuestions.add(item); // Definitions are harder
                    else easyQuestions.add(item);
                }
            }
        }
        Collections.shuffle(easyQuestions);
        Collections.shuffle(hardQuestions);
    }

    private void saveProgress() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Float score : studentHistory) sb.append(score).append(",");
            FileWriter writer = new FileWriter("tutor_save.txt");
            writer.write(sb.toString());
            writer.close();
        } catch (Exception e) {}
    }

    private void loadProgress() {
        try {
            File saveFile = new File("tutor_save.txt");
            if (!saveFile.exists()) return;
            Scanner scanner = new Scanner(saveFile);
            if (scanner.hasNext()) {
                String[] scores = scanner.next().split(",");
                studentHistory.clear();
                for (String s : scores) if (!s.isEmpty()) studentHistory.add(Float.parseFloat(s));
                updateAI(0);
            }
            scanner.close();
        } catch (Exception e) {}
    }

    private void customizeOptionButtons(JButton btnA, JButton btnB, JButton btnC, JButton btnD) {
        // 1. Define the specific colors for each option
        Color colorA = new Color(255, 45, 85);   // Red
        Color colorB = new Color(52, 152, 219);  // Blue
        Color colorC = new Color(46, 204, 113);  // Green
        Color colorD = new Color(255, 200, 0);   // Gold

        // 2. Apply styling only (Keyboard registration removed)
        applyOptionStyle(btnA, colorA, "A");
        applyOptionStyle(btnB, colorB, "B");
        applyOptionStyle(btnC, colorC, "C");
        applyOptionStyle(btnD, colorD, "D");
    }

    private void applyOptionStyle(JButton btn, Color themeColor, String label) {
        // Basic Properties
        btn.setText("<html><font size='5'><b>" + label + "</b></font></html>");
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false); // Ensure it's transparent by default
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Force the specific theme color border
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(themeColor, 2, true),
                BorderFactory.createEmptyBorder(12, 25, 12, 25)
        ));

        // Remove any existing MouseListeners to prevent "Blue" color leftovers
        for (java.awt.event.MouseListener ml : btn.getMouseListeners()) {
            btn.removeMouseListener(ml);
        }

        // Add New Hover Logic using the correct theme color
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                // Fill background with theme color (Low opacity)
                btn.setBackground(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 60));
                btn.setOpaque(true);
                btn.repaint();
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setOpaque(false);
                btn.repaint();
            }
        });
    }

    class ModernMenuButton extends JButton {
        private Color accent;
        private float alpha = 0.2f;

        public ModernMenuButton(String html, Color c) {
            super("<html><center>" + html + "</center></html>");
            this.accent = c;
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 40)));

            addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    alpha = 0.7f; // Glow effect on hover
                    repaint();
                }
                public void mouseExited(java.awt.event.MouseEvent e) {
                    alpha = 0.2f; // Fade out
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // CHANGE: Instead of using 'this.accent', we use 'currentAccentColor'
            // We only use the accent if it's NOT a themed button,
            // but for your minimalist look, use the global one:
            Color drawColor = getModel().isRollover() ? currentAccentColor : new Color(255, 255, 255, 40);

            g2.setColor(drawColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

            super.paintComponent(g);
            g2.dispose();
        }
    }

    class ProgressGraphPanel extends JPanel {
        private List<Float> scores;
        private double animationProgress = 0.0; // 0.0 to 1.0
        private Timer animTimer;

        public ProgressGraphPanel() {
            List<Float> history = studentHistory;
            if (history.size() > 10) {
                this.scores = history.subList(history.size() - 10, history.size());
            } else {
                this.scores = history;
            }
            setOpaque(false);
            setPreferredSize(new Dimension(450, 280));
        }

        public void startAnimation() {
            animationProgress = 0.0;
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();

            // 20ms delay + 0.01 increment = ~2 seconds to complete the draw
            animTimer = new Timer(20, e -> {
                animationProgress += 0.01;
                if (animationProgress >= 1.0) {
                    animationProgress = 1.0;
                    ((Timer)e.getSource()).stop();
                }
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int marginLeft = 60, marginBottom = 50, marginRight = 20, marginTop = 20;
            int graphWidth = getWidth() - marginLeft - marginRight;
            int graphHeight = getHeight() - marginBottom - marginTop;

            // 1. DRAW AXES
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawLine(marginLeft, marginTop, marginLeft, marginTop + graphHeight);
            g2.drawLine(marginLeft, marginTop + graphHeight, marginLeft + graphWidth, marginTop + graphHeight);

            // 2. DRAW Y-AXIS LABELS
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            for (int i = 0; i <= 4; i++) {
                int y = marginTop + graphHeight - (i * graphHeight / 4);
                g2.setColor(new Color(255, 255, 255, 30));
                g2.drawLine(marginLeft, y, marginLeft + graphWidth, y);
                g2.setColor(Color.WHITE);
                g2.drawString((i * 25) + "%", marginLeft - 45, y + 5);
            }

            if (scores == null || scores.size() < 2) {
                drawEmptyState(g2, getWidth(), getHeight());
                g2.dispose();
                return;
            }

            // 3. PLOT COORDINATES
            double xScale = (double) graphWidth / (scores.size() - 1);
            List<Point> graphPoints = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) {
                int x1 = (int) (i * xScale + marginLeft);
                int y1 = (int) (marginTop + graphHeight - (scores.get(i) * graphHeight));
                graphPoints.add(new Point(x1, y1));
            }

            // 4. ANIMATED LINE CLIPPING
            // We set a clip area that expands from left to right based on animationProgress
            Shape oldClip = g2.getClip();
            int clipWidth = (int) (getWidth() * animationProgress);
            g2.setClip(0, 0, clipWidth, getHeight());

            // Draw Gradient & Neon Line within clip
            drawGradientArea(g2, graphPoints, marginTop + graphHeight);
            g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(currentAccentColor);
            for (int i = 0; i < graphPoints.size() - 1; i++) {
                g2.drawLine(graphPoints.get(i).x, graphPoints.get(i).y,
                        graphPoints.get(i + 1).x, graphPoints.get(i + 1).y);
            }

            // 5. DATA NODES
            for (Point p : graphPoints) {
                // Dots only appear if the animation has passed their X position
                if (p.x <= clipWidth) {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(p.x - 4, p.y - 4, 8, 8);
                    g2.setColor(currentAccentColor);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(p.x - 4, p.y - 4, 8, 8);
                }
            }

            g2.setClip(oldClip); // Restore clip to draw labels

            // 6. X-AXIS LABELS
            g2.setColor(new Color(200, 200, 200));
            g2.setFont(new Font("Segoe UI Bold", Font.PLAIN, 10));
            g2.drawString("PREVIOUS", marginLeft, marginTop + graphHeight + 30);
            g2.drawString("LATEST", marginLeft + graphWidth - 40, marginTop + graphHeight + 30);

            g2.dispose();
        }

        private void drawGradientArea(Graphics2D g2, List<Point> points, int baselineY) {
            Path2D path = new Path2D.Double();
            path.moveTo(points.get(0).x, points.get(0).y);
            for (Point p : points) path.lineTo(p.x, p.y);
            path.lineTo(points.get(points.size() - 1).x, baselineY);
            path.lineTo(points.get(0).x, baselineY);
            path.closePath();

            GradientPaint gp = new GradientPaint(0, 0,
                    new Color(currentAccentColor.getRed(), currentAccentColor.getGreen(), currentAccentColor.getBlue(), 50),
                    0, baselineY, new Color(currentAccentColor.getRed(), currentAccentColor.getGreen(), currentAccentColor.getBlue(), 0));
            g2.setPaint(gp);
            g2.fill(path);
        }

        private void drawEmptyState(Graphics2D g2, int w, int h) {
            g2.setColor(new Color(255, 255, 255, 80));
            g2.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            String msg = "Collecting data points...";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2 + 30, h / 2);
        }
    }

    private class PaLOHomePage extends JDialog {
        public PaLOHomePage(Frame owner) {
            // Tie this dialog to the main OfflineTutorApp frame as its modal owner
            super(owner, true);

            // --- WINDOW CONFIGURATION ---
            setUndecorated(false);
            setTitle("PaLO - Student Dashboard");
            setSize(1200, 750);
            setLocationRelativeTo(owner);

            // FIX: Behavior for the Windows "X" button
            // Ensures that if the dashboard is closed, the main app frame is restored
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    if (getOwner() != null) {
                        // Only restore the main window if there is an active session state
                        if (questionCounter > 0) {
                            getOwner().setVisible(true);
                        } else {
                            // If no quiz is running, closing the dashboard prompts for full exit
                            int response = JOptionPane.showConfirmDialog(null,
                                    "Exit PaLO entirely?", "Confirm Exit", JOptionPane.YES_NO_OPTION);
                            if (response == JOptionPane.YES_OPTION) System.exit(0);
                        }
                    }
                }
            });

            // --- BACKGROUND WITH DARK TINT ---
            BackgroundPanel bgPanel = new BackgroundPanel("homepage_bg.gif");
            bgPanel.setLayout(new BorderLayout());

            // --- TOPBAR ---
            JPanel topBar = new JPanel(new BorderLayout());
            topBar.setOpaque(false);
            topBar.setBorder(BorderFactory.createEmptyBorder(30, 60, 0, 60));

            JLabel welcome = new JLabel("<html><font color='#BBBBBB' size='5'>WELCOME BACK,</font><br>" +
                    "<font size='14' color='white'><b>THUSHAR</b></font></html>");

            JPanel topTrailingArea = new JPanel();
            topTrailingArea.setLayout(new BoxLayout(topTrailingArea, BoxLayout.Y_AXIS));
            topTrailingArea.setOpaque(false);

            topTrailingArea.add(createLiveClock());
            topTrailingArea.add(Box.createRigidArea(new Dimension(0, 25))); // Moves exit button down
            topTrailingArea.add(createBigQuitButton());

            topBar.add(welcome, BorderLayout.WEST);
            topBar.add(topTrailingArea, BorderLayout.EAST);

            // --- CENTER AREA ---
            JPanel centerArea = new JPanel(new GridBagLayout());
            centerArea.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();

            JPanel leftSpacer = new JPanel();
            leftSpacer.setOpaque(false);
            gbc.gridx = 0; gbc.weightx = 0.45; gbc.anchor = GridBagConstraints.NORTHWEST;
            centerArea.add(leftSpacer, gbc);

            GlassCardPanel analyticalCard = new GlassCardPanel();
            analyticalCard.setLayout(new BoxLayout(analyticalCard, BoxLayout.Y_AXIS));

            ProgressGraphPanel graph = new ProgressGraphPanel();

            // Dynamic analysis label using student history logic
            JLabel analysisLabel = new JLabel("<html><body style='width: 350px; text-align: center;'>" +
                    calculateTrend() + "</body></html>");
            analysisLabel.setFont(new Font("Segoe UI Light", Font.PLAIN, 15));
            analysisLabel.setForeground(Color.WHITE);
            analysisLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            analyticalCard.add(createLegend()); // Legend Method Fixed
            analyticalCard.add(graph);
            analyticalCard.add(Box.createRigidArea(new Dimension(0, 15)));
            analyticalCard.add(analysisLabel);

            gbc.gridx = 1; gbc.weightx = 0.55;
            gbc.anchor = GridBagConstraints.NORTHEAST;
            gbc.insets = new Insets(20, 0, 0, 60);
            centerArea.add(analyticalCard, gbc);

            // --- BOTTOMBAR: Buttons with Specific Hover Colours ---
            JPanel bottomBar = new JPanel(new BorderLayout());
            bottomBar.setOpaque(false);
            bottomBar.setBorder(BorderFactory.createEmptyBorder(0, 60, 60, 60));

            JPanel utilityGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
            utilityGroup.setOpaque(false);
            utilityGroup.add(createGlowButton("SETTINGS", new Color(255, 200, 0), e -> openSettings()));
            utilityGroup.add(createGlowButton("DEEP FOCUS", new Color(155, 89, 182), e -> launchDeepFocusMode()));

            JPanel studyGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
            studyGroup.setOpaque(false);

            // FIX: Re-entry logic using getOwner() to restore the hidden frame
            studyGroup.add(createGlowButton("OFFLINE STUDY", new Color(46, 204, 113), e -> {
                isAudioMode = false;
                dispose(); // Close homepage
                if (getOwner() != null) {
                    getOwner().setVisible(true); // Show hidden Quiz Window
                    getOwner().toFront();
                    // If the session was ended, immediately trigger the scan dialog for the user
                    if (questionCounter == 0) {
                        ((OfflineTutorApp)getOwner()).performScan();
                    }
                }
            }));

            studyGroup.add(createGlowButton("AUDIO ASSISTED MODE", new Color(52, 152, 219), e -> {
                isAudioMode = true;
                dispose();
                if (getOwner() != null) {
                    getOwner().setVisible(true);
                    getOwner().toFront();
                }
            }));

            bottomBar.add(utilityGroup, BorderLayout.WEST);
            bottomBar.add(studyGroup, BorderLayout.EAST);

            bgPanel.add(topBar, BorderLayout.NORTH);
            bgPanel.add(centerArea, BorderLayout.CENTER);
            bgPanel.add(bottomBar, BorderLayout.SOUTH);

            setContentPane(bgPanel);
            SwingUtilities.invokeLater(graph::startAnimation);
        }

        // --- SUPPORTING METHODS ---

        // FIXED: Added missing createLegend method
        private JPanel createLegend() {
            JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            legend.setOpaque(false);
            JLabel dot = new JLabel("● ");
            dot.setForeground(currentAccentColor);
            JLabel text = new JLabel("Mastery Achievement (%)  ");
            text.setForeground(Color.WHITE);
            text.setFont(new Font("Segoe UI Bold", Font.PLAIN, 11));
            legend.add(dot); legend.add(text);
            return legend;
        }

        // FIXED: Included calculateTrend within the scope to prevent compilation error
        private String calculateTrend() {
            if (studentHistory.size() < 2) return "Awaiting more session data to map trends.";
            float last = studentHistory.get(studentHistory.size() - 1);
            float prev = studentHistory.get(studentHistory.size() - 2);
            float diff = (last - prev) * 100;
            if (diff > 0) return "Mastery increased by <font color='#2ecc71'>+" + String.format("%.1f", diff) + "%</font>. Excellent growth!";
            if (diff < 0) return "Performance dipped by <font color='#ff2d55'>" + String.format("%.1f", Math.abs(diff)) + "%</font>. Review weak areas.";
            return "Performance is stable. Your consistency is showing!";
        }

        private JButton createGlowButton(String text, Color glowColor, java.awt.event.ActionListener action) {
            JButton btn = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    if (getModel().isRollover()) {
                        g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 80));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                        g2.setStroke(new BasicStroke(3.0f));
                        g2.setColor(glowColor);
                    } else {
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.setColor(new Color(255, 255, 255, 120));
                    }

                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btn.setForeground(Color.WHITE);
            btn.setPreferredSize(new Dimension(190, 48));
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(action);
            return btn;
        }

        private JButton createBigQuitButton() {
            JButton btn = new JButton("EXIT APPLICATION") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    if (getModel().isRollover()) {
                        g2.setColor(new Color(255, 45, 85, 220));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                        g2.setStroke(new BasicStroke(2.5f));
                        g2.setColor(Color.WHITE);
                    } else {
                        g2.setColor(new Color(255, 255, 255, 30));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.setColor(new Color(255, 255, 255, 120));
                    }

                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btn.setForeground(Color.WHITE);
            btn.setPreferredSize(new Dimension(200, 50));
            btn.setMaximumSize(new Dimension(200, 50));
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setAlignmentX(Component.RIGHT_ALIGNMENT);
            btn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this, "Do you want to quit?", "Confirm Exit", 0);
                if (confirm == 0) System.exit(0);
            });
            return btn;
        }

        private JPanel createLiveClock() {
            JPanel clockPanel = new JPanel();
            clockPanel.setLayout(new BoxLayout(clockPanel, BoxLayout.Y_AXIS));
            clockPanel.setOpaque(false);

            JLabel timeLabel = new JLabel();
            timeLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 26));
            timeLabel.setForeground(Color.WHITE);
            timeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

            JLabel dateLabel = new JLabel();
            dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            dateLabel.setForeground(new Color(220, 220, 220));
            dateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

            java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("hh:mm:ss a");
            java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("EEEE, MMM dd, yyyy");

            new javax.swing.Timer(1000, e -> {
                java.util.Date now = new java.util.Date();
                timeLabel.setText(timeFmt.format(now));
                dateLabel.setText(dateFmt.format(now).toUpperCase());
            }).start();

            clockPanel.add(timeLabel);
            clockPanel.add(dateLabel);
            return clockPanel;
        }

        // --- INNER COMPONENTS ---

        private class GlassCardPanel extends JPanel {
            public GlassCardPanel() {
                setOpaque(false);
                setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 140));
                g2.fillRoundRect(10, 10, getWidth() - 20, getHeight() - 20, 35, 35);
                g2.setColor(new Color(15, 15, 15, 220));
                g2.fillRoundRect(0, 0, getWidth() - 10, getHeight() - 10, 35, 35);
                GradientPaint glow = new GradientPaint(0, 0, new Color(255, 255, 255, 50), getWidth(), getHeight(), new Color(255, 255, 255, 10));
                g2.setPaint(glow); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 10, getHeight() - 10, 35, 35);
                g2.dispose();
                super.paintComponent(g);
            }
        }

        private class BackgroundPanel extends JPanel {
            private ImageIcon gifIcon;
            public BackgroundPanel(String path) {
                java.io.File f = new java.io.File(path);
                if(f.exists()) this.gifIcon = new ImageIcon(path);
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
    }

    private String calculateTrend() {
        if (studentHistory.size() < 2) return "Start your first session to see performance insights.";

        float last = studentHistory.get(studentHistory.size() - 1);
        float prev = studentHistory.get(studentHistory.size() - 2);
        float diff = (last - prev) * 100;

        if (diff > 0) {
            return "Your score increased by <font color='#2ecc71'>" + String.format("%.1f", diff) + "%</font>. Excellent growth!";
        } else if (diff < 0) {
            return "Your score dipped by <font color='#e74c3c'>" + String.format("%.1f", Math.abs(diff)) + "%</font>. Review previous topics.";
        } else {
            return "Stability maintained. Aim for a 5% increase in your next session.";
        }
    }

    private JButton createAestheticButton(String text, Color accent) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(40, 40, 40, 180)); // Default semi-transparent dark
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 50)));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                // Animation: Light up with color
                btn.setBackground(accent);
                btn.setOpaque(true);
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                // Return to subtle glass look
                btn.setBackground(new Color(40, 40, 40, 180));
                btn.setOpaque(false);
            }
        });
        return btn;
    }

    private void initAI() {
        try {
            Path modelPath = Paths.get("tutor_brain.pt");
            Model model = Model.newInstance("AdaptiveTutor");
            model.load(modelPath);
            this.predictor = model.newPredictor(new TutorTranslator());
        } catch (Exception e) {}
    }

    private void launchDeepFocusMode() {
        JDialog focusDialog = new JDialog(this, true);
        focusDialog.setUndecorated(true);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        focusDialog.setBounds(0, 0, screen.width, screen.height);

        BackgroundPanel focusBG = new BackgroundPanel("assets/focus_bg.jpg");
        focusBG.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);

        // Title
        JLabel title = new JLabel("SELECT YOUR FOCUS PATH");
        title.setFont(new Font("Segoe UI Black", Font.BOLD, 36));
        title.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        focusBG.add(title, gbc);

        // --- TECHNIQUE BUTTONS ---
        gbc.gridwidth = 1;

        // 1. Pomodoro
        JButton pBtn = createTechniqueButton("POMODORO", "25m Work • 5m Rest", new Color(255, 45, 85));
        pBtn.addActionListener(e -> startFocusSession(focusDialog, 25, 5));
        gbc.gridy = 1; gbc.gridx = 0; focusBG.add(pBtn, gbc);

        // 2. Flow State
        JButton fBtn = createTechniqueButton("FLOW STATE", "90m Deep Work • 15m Rest", new Color(0, 220, 255));
        fBtn.addActionListener(e -> startFocusSession(focusDialog, 90, 15));
        gbc.gridx = 1; focusBG.add(fBtn, gbc);

        // 3. Rule of 52/17
        JButton rBtn = createTechniqueButton("52 / 17 RULE", "Science-backed Productivity", new Color(46, 204, 113));
        rBtn.addActionListener(e -> startFocusSession(focusDialog, 52, 17));
        gbc.gridy = 2; gbc.gridx = 0; focusBG.add(rBtn, gbc);

        // 4. Custom Zen
        JButton cBtn = createTechniqueButton("CUSTOM ZEN", "Set your own intervals", new Color(255, 200, 0));
        cBtn.addActionListener(e -> {
            // Show input dialogs for custom time
            String work = JOptionPane.showInputDialog("Work Minutes:");
            String rest = JOptionPane.showInputDialog("Rest Minutes:");
            try {
                startFocusSession(focusDialog, Integer.parseInt(work), Integer.parseInt(rest));
            } catch(Exception ex) {}
        });
        gbc.gridx = 1; focusBG.add(cBtn, gbc);

        // Exit Button
        JButton back = new JButton("BACK TO MENU");
        back.setForeground(Color.GRAY);
        back.setContentAreaFilled(false);
        back.addActionListener(e -> focusDialog.dispose());
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        focusBG.add(back, gbc);

        focusDialog.add(focusBG);
        focusDialog.setVisible(true);
    }

    private void startFocusSession(JDialog dialog, int workMins, int restMins) {
        dialog.getContentPane().removeAll();

        BackgroundPanel sessionBG = new BackgroundPanel("assets/focus_bg.jpg");
        sessionBG.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // 1. Status Label
        JLabel statusLabel = new JLabel("FOCUSING...");
        statusLabel.setFont(new Font("Segoe UI Black", Font.BOLD, 32));
        statusLabel.setForeground(currentAccentColor);
        gbc.gridx = 0; gbc.gridy = 0;
        sessionBG.add(statusLabel, gbc);

        // 2. The Clock
        CircularTimer timer = new CircularTimer();
        timer.setPreferredSize(new Dimension(450, 450));
        timer.setSeconds(workMins * 60);
        timer.setRunning(true);
        gbc.gridy = 1; gbc.insets = new Insets(30, 0, 30, 0);
        sessionBG.add(timer, gbc);

        // 3. Lo-Fi Toggle
        ModernToggle musicToggle = new ModernToggle("LO-FI MODE");
        musicToggle.addActionListener(e -> {
            musicToggle.toggle();
            if(musicToggle.isActive()) playZenMusic(); else stopZenMusic();
        });
        gbc.gridy = 2; gbc.insets = new Insets(0, 0, 0, 0);
        sessionBG.add(musicToggle, gbc);

        // 4. Session Manager Logic
        // This background timer updates every second to check if work is done
        javax.swing.Timer workManager = new javax.swing.Timer(1000, null);
        workManager.addActionListener(e -> {
            int currentSecs = timer.getSeconds();
            if (currentSecs > 0) {
                timer.setSeconds(currentSecs - 1);
            } else {
                workManager.stop();
                stopZenMusic();
                triggerRestMode(dialog, restMins);
            }
        });
        workManager.start();

        dialog.add(sessionBG);
        dialog.revalidate();
        dialog.repaint();
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

    private void triggerRestMode(JDialog dialog, int restMins) {
        dialog.getContentPane().removeAll();

        // Use a different, calmer background for rest if available
        BackgroundPanel restBG = new BackgroundPanel("assets/rest_bg.jpg");
        restBG.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // 1. Rest Title
        JLabel restLabel = new JLabel("TIME TO RELAX");
        restLabel.setFont(new Font("Segoe UI Black", Font.BOLD, 32));
        restLabel.setForeground(new Color(46, 204, 113)); // Minimalist Green
        gbc.gridx = 0; gbc.gridy = 0;
        restBG.add(restLabel, gbc);

        // 2. The Rest Clock
        CircularTimer restTimer = new CircularTimer();
        restTimer.setPreferredSize(new Dimension(400, 400));
        restTimer.setSeconds(restMins * 60);
        restTimer.setRunning(true);
        // Temporarily override the theme color for the rest clock
        // Note: You might need to add a setColor method to CircularTimer for this
        gbc.gridy = 1; gbc.insets = new Insets(30, 0, 30, 0);
        restBG.add(restTimer, gbc);

        // 3. Finish/Back Button
        JButton btnExit = new JButton("FINISH SESSION") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(46, 204, 113) : new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        btnExit.setFont(new Font("Segoe UI Bold", Font.PLAIN, 14));
        btnExit.setForeground(Color.WHITE);
        btnExit.setPreferredSize(new Dimension(200, 50));
        btnExit.setContentAreaFilled(false);
        btnExit.setBorderPainted(false);
        btnExit.addActionListener(e -> dialog.dispose());

        gbc.gridy = 2;
        restBG.add(btnExit, gbc);

        // 4. Break Manager Logic
        javax.swing.Timer breakManager = new javax.swing.Timer(1000, null);
        breakManager.addActionListener(e -> {
            int currentSecs = restTimer.getSeconds();
            if (currentSecs > 0) {
                restTimer.setSeconds(currentSecs - 1);
            } else {
                breakManager.stop();
                restLabel.setText("BREAK OVER!");
            }
        });
        breakManager.start();

        dialog.add(restBG);
        dialog.revalidate();
        dialog.repaint();
    }

    private JButton createModernToggle(String text) {
        JButton toggle = new JButton(text + ": OFF") {
            private boolean active = false;
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background of the switch
                g2.setColor(active ? currentAccentColor : new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);

                // The "Sliding" knob indicator
                g2.setColor(Color.WHITE);
                int knobSize = getHeight() - 10;
                int x = active ? (getWidth() - knobSize - 5) : 5;
                g2.fillOval(x, 5, knobSize, knobSize);

                super.paintComponent(g);
                g2.dispose();
            }

            public boolean isActive() { return active; }
            public void toggle() {
                active = !active;
                setText(text + (active ? ": ON" : ": OFF"));
                repaint();
            }
        };

        toggle.setFont(new Font("Segoe UI Bold", Font.PLAIN, 12));
        toggle.setForeground(Color.WHITE);
        toggle.setPreferredSize(new Dimension(140, 40));
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setFocusPainted(false);
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));

        toggle.addActionListener(e -> ((JButton)e.getSource()).getParent().repaint()); // Trigger redraw
        return toggle;
    }



    // Add this to your class fields
    private Color currentAccentColor = new Color(0, 220, 255); // Default Electric Cyan

    private Map<String, Color[]> getThemes() {
        Map<String, Color[]> themes = new HashMap<>();
        // Format: { Primary Accent, Hover/Secondary }
        themes.put("Spider-Verse Red", new Color[]{new Color(231, 76, 60), new Color(255, 45, 85)});
        themes.put("Deep Sea Blue", new Color[]{new Color(0, 220, 255), new Color(52, 152, 219)});
        themes.put("Emerald Forest", new Color[]{new Color(50, 255, 120), new Color(46, 204, 113)});
        return themes;
    }

    private JButton createTechniqueButton(String title, String subtitle, Color theme) {
        JButton btn = new JButton("<html><center><b>" + title + "</b><br>" +
                "<font size='3' color='#BBBBBB'>" + subtitle + "</font></center></html>") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Highlight with theme color on hover, otherwise dark glass
                g2.setColor(getModel().isRollover() ? theme : new Color(255, 255, 255, 20));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

                super.paintComponent(g);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(280, 100));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void openSettings() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 15, 15));
        panel.setBackground(new Color(40, 40, 40));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel("SELECT GLOBAL THEME");
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Segoe UI Bold", Font.PLAIN, 14));

        Map<String, Color[]> themes = getThemes();
        JComboBox<String> themeBox = new JComboBox<>(themes.keySet().toArray(new String[0]));

        themeBox.addActionListener(e -> {
            String selected = (String) themeBox.getSelectedItem();
            // Inside your themeBox ActionListener
            currentAccentColor = themes.get(selected)[0];
            SwingUtilities.updateComponentTreeUI(this); // Forces the entire window to refresh colors
            this.repaint();
        });

        panel.add(label);
        panel.add(themeBox);

        JOptionPane.showMessageDialog(this, panel, "Settings", JOptionPane.PLAIN_MESSAGE);
    }

    private static class TutorTranslator implements Translator<float[], Float> {
        public NDList processInput(TranslatorContext ctx, float[] input) {
            NDManager manager = ctx.getNDManager();
            return new NDList(manager.create(input).reshape(1, input.length, 1));
        }
        public Float processOutput(TranslatorContext ctx, NDList list) {
            return list.singletonOrThrow().getFloat();
        }
    }

    public static void main(String[] args) {
        com.formdev.flatlaf.FlatDarkLaf.setup();
        // Launch the app constructor on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new OfflineTutorApp());
    }
}