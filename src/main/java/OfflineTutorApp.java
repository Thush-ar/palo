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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OfflineTutorApp extends JFrame {


    // --- GUI Components ---

    private JComboBox<String> subjectDropdown;
    private JComboBox<String> timerDropdown;
    private JTextArea questionArea;
    private JLabel aiStatusLabel;
    private JLabel difficultyLabel;
    private JProgressBar masteryBar;
    private QuizOptionButton btnA, btnB, btnC, btnD;
    private JLabel imageViewer;
    private Clip zenClip; // Stores the audio stream for lo-fi beats

    // Chat UI components
    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton sendButton;

    // --- Integrated UI Components ---
    private JPanel mainCardPanel;
    private CardLayout cardLayout;
    private JPanel quizPanel;
    private JPanel chatPanel;
    private JPanel audioPanel;
    private JPanel currentPanel;

    // --- Logic & Data ---
    private Predictor<float[], Float> predictor;
    private List<Float> studentHistory = new ArrayList<>();
    // Logic & Data
    private String currentLevel = "EASY";
    private List<QuizItem> easyQuestions = new ArrayList<>();
    private List<QuizItem> easyMediumQuestions = new ArrayList<>();
    private List<QuizItem> mediumQuestions = new ArrayList<>();
    private List<QuizItem> mediumHardQuestions = new ArrayList<>();
    private List<QuizItem> hardQuestions = new ArrayList<>();
    private List<QuizItem> expertQuestions = new ArrayList<>(); // "Really Hard"
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

    // Lightweight wrapper around JButton to keep option-specific styling and feedback
    private class QuizOptionButton extends JButton {
        private final Color themeColor;
        private boolean isBlinking = false;
        private Color currentFeedbackColor = null;

        public QuizOptionButton(String text, Color themeColor) {
            super(text);
            this.themeColor = themeColor;

            // Basic properties
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(0, 110));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            // Initial text formatting
            updateTextAndFont(text);

            addActionListener(e -> checkAnswer(this));
        }

        /**
         * Accommodates long text by using HTML for wrapping and
         * dynamic font sizing based on character count.
         */
        private void updateTextAndFont(String text) {
            int fontSize = 22; // Default
            if (text.length() > 80) fontSize = 14;
            else if (text.length() > 40) fontSize = 17;

            // Using HTML to force multi-line wrapping inside the button
            String htmlText = "<html><body style='width: 250px; text-align: center; " +
                    "font-family: Segoe UI Semibold; font-size: " + fontSize + "pt;'>" +
                    text + "</body></html>";
            super.setText(htmlText);
        }

        @Override
        public void setText(String text) {
            updateTextAndFont(text);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Determine Background Color
            if (currentFeedbackColor != null) {
                // Success Glow or Blink State
                g2.setColor(isBlinking ? new Color(30, 30, 30, 180) : currentFeedbackColor);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 120));
            } else {
                g2.setColor(new Color(30, 30, 30, 150));
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

            // Border
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

            super.paintComponent(g);
            g2.dispose();
        }

        /**
         * Turns the button Emerald Green to signify the correct answer.
         */
        public void setCorrectGlow() {
            this.currentFeedbackColor = new Color(46, 204, 113, 200);
            this.isBlinking = false;
            repaint();
        }

        /**
         * Makes the button blink Red to signify an incorrect choice.
         */
        public void startErrorBlink() {
            this.currentFeedbackColor = new Color(231, 76, 60, 200);
            javax.swing.Timer blinkTimer = new javax.swing.Timer(200, null);

            blinkTimer.addActionListener(e -> {
                isBlinking = !isBlinking;
                repaint();
            });

            blinkTimer.start();

            // Stop blinking after 1.2 seconds (approx 3 blinks)
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
            JButton btnTalk = createModernCard("Talk to AI", "🤖", "Chat with Llama about your subjects", new Color(155, 89, 182));

            btnScan.addActionListener(e -> { isAudioMode = false; isChatMode = false; dispose(); });
            btnAudio.addActionListener(e -> { isAudioMode = true; isChatMode = false; dispose(); });
            btnTalk.addActionListener(e -> { isAudioMode = false; isChatMode = true; dispose(); });

            cardContainer.add(btnScan);
            cardContainer.add(btnAudio);
            cardContainer.add(btnTalk);

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


            add(layeredPane, BorderLayout.CENTER);
        }
    }

    private JButton createQuitQuizButton() {
        JButton btn = new JButton("END QUIZ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Red themed glow for the termination button
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
            // 1. TERMINATE QUIZ LOGIC
            if (quizTimer != null) quizTimer.stop();
            stopZenMusic();
            saveProgress(); // Save results to the history file

            // 2. CLEAR DATA POOLS (Memory Termination)
            // This ensures the next "Offline Study" starts with a fresh scan
            easyQuestions.clear();
            hardQuestions.clear();
            expertQuestions.clear();
            sessionLog.clear();
            questionCounter = 0;
            currentQuestion = null;

            // 3. TERMINATE INTERFACE WINDOW
            // We set the main window to invisible and switch to a blank state
            this.setVisible(false);
            if (cardLayout != null) {
                cardLayout.show(mainCardPanel, "BLANK");
            }

            // 4. RELAUNCH DASHBOARD
            // This brings the user back to the main trend analysis/mode selection
            SwingUtilities.invokeLater(() -> {
                PaLOHomePage home = new PaLOHomePage(this);
                home.setVisible(true);
            });
        });
        return btn;
    }

    /**
     * Show homepage within the integrated interface
     */
    private void showHomepageInIntegratedMode() {
        // Create and show homepage dialog as before
        PaLOHomePage home = new PaLOHomePage(this);
        home.setVisible(true);
    }

    /**
     * Setup the main quiz UI (separate window approach)
     */
    private void setupUI() {
        JPanel mainPanel = createQuizPanel();
        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Setup the chat UI (separate window approach)
     */
    private void setupChatUI() {
        JPanel mainPanel = createChatPanel();
        add(mainPanel, BorderLayout.CENTER);
        
        // Center the window
        setLocationRelativeTo(null);
    }

    private class ChatBubble extends JPanel {
        public ChatBubble(String text, boolean isUser) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JTextArea area = new JTextArea(text);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setEditable(false);
            area.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            area.setForeground(Color.WHITE);
            area.setOpaque(false);
            area.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

            // Styling based on who is speaking
            Color bubbleColor = isUser ? new Color(52, 152, 219) : new Color(45, 45, 45);

            JPanel bubble = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(bubbleColor);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.dispose();
                }
            };
            bubble.setOpaque(false);
            bubble.add(area);

            // Align User to Right, AI to Left
            if (isUser) {
                add(Box.createHorizontalGlue(), BorderLayout.WEST);
                add(bubble, BorderLayout.CENTER);
            } else {
                add(bubble, BorderLayout.CENTER);
                add(Box.createHorizontalGlue(), BorderLayout.EAST);
            }
        }
    }

    // Add this field at the top of your class
    private boolean isAudioMode = false;
    private boolean isChatMode = false;
    private LlamaConnection llamaConnection;

    public OfflineTutorApp() {
        SplashScreen splash = new SplashScreen();
        splash.setVisible(true);

        new Thread(() -> {
            try {
                initAI();
                loadProgress();
                llamaConnection = new LlamaConnection();

                Thread.sleep(2500);

                SwingUtilities.invokeLater(() -> {
                    splash.dispose();

                    initializeIntegratedUI();

                    // --- APPLY CUSTOM LOGO ---
                    setAppIcon();

                    setSize(1400, 850);

                    // --- STABLE TASKBAR FIX ---
                    // Parked off-screen so the new P-Logo stays in the taskbar
                    this.setLocation(-4000, -4000);
                    this.setVisible(true);

                    // 4. Launch Dashboard
                    PaLOHomePage home = new PaLOHomePage(this);
                    home.setVisible(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private CircleMasteryPanel circleMastery;

    private CircularTimer clockTimer; // Add this as a field at the top of your class

    private JSpinner questionCountSpinner;

    /**
     * Initialize the integrated UI with CardLayout for seamless mode switching
     */
    private void initializeIntegratedUI() {
        // 1. Core logic and component initialization
        initializeComponents();

        setTitle("PaLO - Adaptive Learning Orchestrator");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int response = JOptionPane.showConfirmDialog(
                        null, "Exit PaLO entirely?", "Confirm Exit",
                        JOptionPane.YES_NO_OPTION);
                if (response == JOptionPane.YES_OPTION) {
                    saveProgress();
                    System.exit(0);
                }
            }
        });

        // 2. Initialize CardLayout for seamless mode switching
        cardLayout = new CardLayout();
        mainCardPanel = new JPanel(cardLayout);
        mainCardPanel.setBackground(new Color(28, 28, 28));

        // 3. Create a Blank Starter Card (The "Hidden" State)
        // This ensures the window looks like an empty dark container before a mode is chosen
        JPanel blankPlaceholder = new JPanel();
        blankPlaceholder.setBackground(new Color(28, 28, 28));
        mainCardPanel.add(blankPlaceholder, "BLANK");

        // 4. Build individual mode panels in the background
        quizPanel = createQuizPanel();
        chatPanel = createChatPanel();
        audioPanel = createAudioPanel();

        // 5. Add functional panels to the stack
        mainCardPanel.add(quizPanel, "QUIZ");
        mainCardPanel.add(chatPanel, "CHAT");
        mainCardPanel.add(audioPanel, "AUDIO");

        setLayout(new BorderLayout());
        add(mainCardPanel, BorderLayout.CENTER);

        // 6. CRITICAL: Default to the BLANK card
        // The main window will remain on this "Empty" card until
        // animateAndTransition() calls switchToQuizMode() or switchToChatMode()
        cardLayout.show(mainCardPanel, "BLANK");

        // Ensure the main window starts invisible
        this.setVisible(false);
    }

    /**
     * Initialize all UI components that are referenced in panels
     */
    private void initializeComponents() {
        // Initialize quiz components
        circleMastery = new CircleMasteryPanel();
        clockTimer = new CircularTimer();
        
        // Initialize quiz option buttons with theme colors
        btnA = createOptionButton("-", new Color(52, 152, 219));  // Blue
        btnB = createOptionButton("-", new Color(155, 89, 182));  // Purple  
        btnC = createOptionButton("-", new Color(46, 204, 113));  // Green
        btnD = createOptionButton("-", new Color(241, 196, 15));  // Yellow
        
        // Initialize other components
        aiStatusLabel = new JLabel("AI Status: Ready", SwingConstants.CENTER);
        aiStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        aiStatusLabel.setForeground(Color.LIGHT_GRAY);
    }

    /**
     * Switch to Quiz mode with slide animation
     */
    public void switchToQuizMode() {
        isChatMode = false;
        isAudioMode = false;
        animatePanelTransition("QUIZ");
    }

    /**
     * Switch to Chat mode with slide animation
     */
    public void switchToChatMode() {
        isChatMode = true;
        isAudioMode = false;
        animatePanelTransition("CHAT");
    }

    /**
     * Switch to Audio mode with slide animation
     */
    public void switchToAudioMode() {
        isChatMode = false;
        isAudioMode = true;
        animatePanelTransition("AUDIO");
    }

    /**
     * Animate panel transition with slide effect
     */
    private void animatePanelTransition(String targetPanel) {
        // Immediately switch to the target panel for better UX
        cardLayout.show(mainCardPanel, targetPanel);
        revalidate();
        repaint();
        
        // Optional: Add a simple fade-in effect if needed in the future
        // For now, immediate switching provides better responsiveness
    }

    /**
     * Create the Quiz mode panel
     */
    private JPanel createQuizPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 0));
        mainPanel.setBackground(new Color(28, 28, 28));

        // --- SIDEBAR (LEFT) ---
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(300, 800));
        sidebar.setBackground(new Color(35, 35, 35));
        sidebar.setBorder(BorderFactory.createEmptyBorder(25, 15, 25, 15));

        // 1. Mastery Circle
        addSidebarSection(sidebar, "OVERALL MASTERY", circleMastery);
        sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

        // 2. Timer Section
        addSidebarSection(sidebar, "QUIZ DURATION", clockTimer);
        sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
        timerLabel = new JLabel("Timer: Off", SwingConstants.CENTER);
        timerLabel.setForeground(Color.LIGHT_GRAY);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(timerLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 25)));

        // 3. Question Count Spinner - Always enabled
        JLabel countLabel = new JLabel("QUESTIONS TO GENERATE");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        countLabel.setForeground(Color.GRAY);
        countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(countLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));

        questionCountSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 30, 1));
        questionCountSpinner.setMaximumSize(new Dimension(240, 45));
        questionCountSpinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        questionCountSpinner.setEnabled(true); // Always enabled
        JComponent editor = questionCountSpinner.getEditor();
        JFormattedTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
        textField.setBackground(new Color(50, 50, 50));
        textField.setForeground(Color.WHITE);
        textField.setCaretColor(Color.WHITE);
        textField.setEditable(true); // Always editable
        sidebar.add(questionCountSpinner);
        sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

        // 4. Status & Scan
        sidebar.add(aiStatusLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 15)));

        JButton btnScan = new JButton("SCAN TEXTBOOK");
        btnScan.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnScan.setPreferredSize(new Dimension(240, 70));
        btnScan.setMaximumSize(new Dimension(240, 70));
        btnScan.setFont(new Font("Segoe UI Black", Font.BOLD, 16));
        btnScan.setBackground(new Color(52, 152, 219));
        btnScan.setForeground(Color.WHITE);
        btnScan.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        btnScan.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnScan.addActionListener(e -> performScan());
        sidebar.add(btnScan);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        // 5. END QUIZ Button - Bigger and under SCAN
        JButton btnEndQuiz = createQuitQuizButton();
        btnEndQuiz.setPreferredSize(new Dimension(240, 70)); // Same size as SCAN button
        btnEndQuiz.setMaximumSize(new Dimension(240, 70));
        sidebar.add(btnEndQuiz);
        sidebar.add(Box.createVerticalGlue());

        // --- QUIZ CONTENT (CENTER) ---
        JPanel quizContent = new JPanel(new BorderLayout(20, 20));
        quizContent.setOpaque(false);
        quizContent.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 30));

        difficultyLabel = new JLabel("Question #1 | Current Level: EASY", SwingConstants.CENTER);
        difficultyLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        difficultyLabel.setForeground(new Color(230, 126, 34));

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
        btnGrid.add(btnA); btnGrid.add(btnB);
        btnGrid.add(btnC); btnGrid.add(btnD);

        quizContent.add(difficultyLabel, BorderLayout.NORTH);
        quizContent.add(qScroll, BorderLayout.CENTER);
        quizContent.add(btnGrid, BorderLayout.SOUTH);

        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(quizContent, BorderLayout.CENTER);

        return mainPanel;
    }

    // Helper to create the mode-switching buttons in sidebar
    private JButton createSidebarModeButton(String text, Color accent) {
        JButton b = new JButton(text);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(240, 40));
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 50));
        b.setBorder(BorderFactory.createLineBorder(accent, 1));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        return b;
    }

    /**
     * Create the Chat mode panel
     */
    private JPanel createChatPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 0));
        mainPanel.setBackground(new Color(28, 28, 28));

        // --- SIDEBAR (LEFT) ---
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(280, 800));
        sidebar.setBackground(new Color(35, 35, 35));
        sidebar.setBorder(BorderFactory.createEmptyBorder(30, 15, 30, 15));

        JLabel titleLabel = new JLabel("Talk to AI");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(155, 89, 182));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(titleLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        JLabel infoLabel = new JLabel("<html><center>Chat with Llama about your subjects. All from inside PaLO—no browser or terminal.</center></html>");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLabel.setForeground(Color.LIGHT_GRAY);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(infoLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 25)));

        // --- Ollama Panel (Existing logic) ---
        JLabel ollamaStatusLabel = new JLabel("Checking...");
        ollamaStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ollamaStatusLabel.setForeground(Color.LIGHT_GRAY);
        ollamaStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(ollamaStatusLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));

        JButton btnStartOllama = new JButton("Start Ollama");
        btnStartOllama.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStartOllama.setMaximumSize(new Dimension(240, 36));
        btnStartOllama.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStartOllama.setBackground(new Color(46, 204, 113));
        btnStartOllama.setForeground(Color.WHITE);
        btnStartOllama.setFocusPainted(false);
        btnStartOllama.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnStartOllama.addActionListener(e -> {
            btnStartOllama.setEnabled(false);
            ollamaStatusLabel.setText("Starting Ollama...");
            new Thread(() -> {
                boolean ok = llamaConnection.startOllamaFromApp();
                SwingUtilities.invokeLater(() -> {
                    btnStartOllama.setEnabled(true);
                    updateOllamaStatusLabel(ollamaStatusLabel);
                });
            }).start();
        });

        JButton btnDownloadModel = new JButton("Download model (" + LlamaConnection.getDefaultPullModelName() + ")");
        btnDownloadModel.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnDownloadModel.setMaximumSize(new Dimension(240, 36));
        btnDownloadModel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnDownloadModel.setBackground(new Color(52, 152, 219));
        btnDownloadModel.setForeground(Color.WHITE);
        btnDownloadModel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDownloadModel.addActionListener(e -> {
            btnDownloadModel.setEnabled(false);
            ollamaStatusLabel.setText("Downloading...");
            new Thread(() -> {
                try {
                    llamaConnection.pullDefaultModel(() -> { });
                    SwingUtilities.invokeLater(() -> {
                        updateOllamaStatusLabel(ollamaStatusLabel);
                        btnDownloadModel.setEnabled(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        ollamaStatusLabel.setText("Download failed.");
                        btnDownloadModel.setEnabled(true);
                    });
                }
            }).start();
        });

        JButton btnCheckOllama = new JButton("Check again");
        btnCheckOllama.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnCheckOllama.setMaximumSize(new Dimension(240, 32));
        btnCheckOllama.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnCheckOllama.setForeground(Color.LIGHT_GRAY);
        btnCheckOllama.setContentAreaFilled(false);
        btnCheckOllama.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCheckOllama.addActionListener(e -> {
            llamaConnection.refreshModel();
            updateOllamaStatusLabel(ollamaStatusLabel);
        });

        sidebar.add(btnStartOllama);
        sidebar.add(Box.createRigidArea(new Dimension(0, 6)));
        sidebar.add(btnDownloadModel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 4)));
        sidebar.add(btnCheckOllama);
        sidebar.add(Box.createRigidArea(new Dimension(0, 25)));

        subjectDropdown = new JComboBox<>(new String[]{"Physics", "Computer Science", "Biology", "General"});
        subjectDropdown.setMaximumSize(new Dimension(240, 35));
        addSidebarSection(sidebar, "SUBJECT", subjectDropdown);

        sidebar.add(Box.createVerticalGlue());

        // --- NEW GO BACK BUTTON ---
        JButton btnBack = new JButton("BACK TO DASHBOARD");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.setPreferredSize(new Dimension(240, 50));
        btnBack.setMaximumSize(new Dimension(240, 50));
        btnBack.setFont(new Font("Segoe UI Black", Font.BOLD, 12));
        btnBack.setBackground(new Color(45, 45, 45));
        btnBack.setForeground(Color.WHITE);
        btnBack.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> {
            // Switch main window to blank and hide it
            cardLayout.show(mainCardPanel, "BLANK");
            this.setVisible(false);
            // Re-open Dashboard
            PaLOHomePage home = new PaLOHomePage(this);
            home.setVisible(true);
        });
        sidebar.add(btnBack);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        updateOllamaStatusLabel(ollamaStatusLabel);

        // --- CHAT CONTENT (CENTER) ---
        JPanel chatContent = new JPanel(new BorderLayout(20, 20));
        chatContent.setOpaque(false);
        chatContent.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 30));

        chatArea = new JTextArea();
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(40, 40, 40));
        chatArea.setForeground(Color.WHITE);
        chatArea.setEditable(false);
        chatArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        chatArea.setText("Welcome to PaLO Chat! Ask me anything.\n\n");

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.getViewport().setOpaque(false);
        chatScroll.setOpaque(false);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 2));

        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        chatInput = new JTextField();
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        chatInput.setBackground(new Color(50, 50, 50));
        chatInput.setForeground(Color.WHITE);
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100), 2),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        chatInput.addActionListener(e -> sendChatMessage());

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sendButton.setBackground(new Color(155, 89, 182));
        sendButton.setForeground(Color.WHITE);
        sendButton.setPreferredSize(new Dimension(100, 50));
        sendButton.setBorder(BorderFactory.createLineBorder(new Color(155, 89, 182), 2));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendChatMessage());

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatContent.add(chatScroll, BorderLayout.CENTER);
        chatContent.add(inputPanel, BorderLayout.SOUTH);

        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(chatContent, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * Create the Audio Assisted mode panel
     */
    private JPanel createAudioPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 0));
        mainPanel.setBackground(new Color(28, 28, 28));

        // --- SIDEBAR (LEFT) ---
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
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        JLabel infoLabel = new JLabel("<html><center>Interactive voice mode for the differently abled. Use voice commands and audio feedback.</center></html>");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLabel.setForeground(Color.LIGHT_GRAY);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(infoLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

        // Audio controls
        JButton btnStartVoice = new JButton("Start Voice Recognition");
        btnStartVoice.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStartVoice.setMaximumSize(new Dimension(240, 50));
        btnStartVoice.setFont(new Font("Segoe UI Black", Font.BOLD, 14));
        btnStartVoice.setBackground(new Color(52, 152, 219));
        btnStartVoice.setForeground(Color.WHITE);
        btnStartVoice.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        btnStartVoice.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnStartVoice.addActionListener(e -> startVoiceRecognition());
        sidebar.add(btnStartVoice);
        sidebar.add(Box.createRigidArea(new Dimension(0, 15)));

        JButton btnPlayAudio = new JButton("Play Question Audio");
        btnPlayAudio.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPlayAudio.setMaximumSize(new Dimension(240, 40));
        btnPlayAudio.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnPlayAudio.setBackground(new Color(46, 204, 113));
        btnPlayAudio.setForeground(Color.WHITE);
        btnPlayAudio.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnPlayAudio.addActionListener(e -> playQuestionAudio());
        sidebar.add(btnPlayAudio);
        sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

        // Mode switcher buttons
        JButton btnQuiz = createSidebarModeButton("QUIZ MODE", new Color(46, 204, 113));
        btnQuiz.addActionListener(e -> switchToQuizMode());
        sidebar.add(btnQuiz);
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));

        JButton btnChat = createSidebarModeButton("TALK TO AI", new Color(155, 89, 182));
        btnChat.addActionListener(e -> switchToChatMode());
        sidebar.add(btnChat);
        sidebar.add(Box.createVerticalGlue());

        // --- AUDIO CONTENT (CENTER) ---
        JPanel audioContent = new JPanel(new BorderLayout(20, 20));
        audioContent.setOpaque(false);
        audioContent.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 30));

        // Audio status display
        JLabel audioStatusLabel = new JLabel("<html><center><font size='6'>🎤</font><br><br>Audio Mode Ready<br><font color='#888888'>Click 'Start Voice Recognition' to begin</font></center></html>", SwingConstants.CENTER);
        audioStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        audioStatusLabel.setForeground(Color.WHITE);
        audioStatusLabel.setBackground(new Color(40, 40, 40));
        audioStatusLabel.setOpaque(true);
        audioStatusLabel.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 2));
        audioStatusLabel.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));

        audioContent.add(audioStatusLabel, BorderLayout.CENTER);

        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(audioContent, BorderLayout.CENTER);

        return mainPanel;
    }

    // Placeholder methods for audio functionality
    private void startVoiceRecognition() {
        JOptionPane.showMessageDialog(this, "Voice recognition feature coming soon!", "Audio Mode", JOptionPane.INFORMATION_MESSAGE);
    }

    private void playQuestionAudio() {
        JOptionPane.showMessageDialog(this, "Audio playback feature coming soon!", "Audio Mode", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Initializes the Deep Java Library (DJL) engine and loads the local
     * predictor model for adaptive learning difficulty estimation.
     */
    private void initAI() {
        try {
            // Paths to your local PyTorch model file
            Path modelPath = Paths.get("tutor_brain.pt");

            // Checking if model exists before attempting load to prevent crash
            if (!modelPath.toFile().exists()) {
                System.err.println("AI Engine: Model file 'tutor_brain.pt' not found. Running in rule-based mode.");
                return;
            }

            Model model = Model.newInstance("AdaptiveTutor");
            model.load(modelPath);

            // TutorTranslator is the inner class defined at the bottom of your file
            this.predictor = model.newPredictor(new TutorTranslator());

            SwingUtilities.invokeLater(() -> {
                if (aiStatusLabel != null) aiStatusLabel.setText("AI Engine: Neural Network Loaded");
            });
        } catch (Exception e) {
            System.err.println("AI Initialization Error: " + e.getMessage());
            // Fallback: predictor remains null, updateAI handles this gracefully
        }
    }

    private void updateOllamaStatusLabel(JLabel statusLabel) {
        if (statusLabel == null) return;
        if (llamaConnection.isAvailable()) {
            statusLabel.setForeground(new Color(46, 204, 113));
            statusLabel.setText("Connected · " + llamaConnection.getModelName());
        } else {
            statusLabel.setForeground(new Color(231, 76, 60));
            statusLabel.setText("Ollama not running");
        }
    }

    /**
     * Enable quiz controls after scanning
     */
    private void enableQuizControls() {
        SwingUtilities.invokeLater(() -> {
            if (questionCountSpinner != null) {
                questionCountSpinner.setEnabled(true);
                JComponent editor = questionCountSpinner.getEditor();
                if (editor instanceof JSpinner.DefaultEditor) {
                    JFormattedTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
                    textField.setEditable(true);
                }
            }
            if (clockTimer != null) {
                clockTimer.setEnabled(true);
            }
        });
    }

    /**
     * Disable quiz controls until scanning
     */
    private void disableQuizControls() {
        SwingUtilities.invokeLater(() -> {
            if (questionCountSpinner != null) {
                questionCountSpinner.setEnabled(false);
                JComponent editor = questionCountSpinner.getEditor();
                if (editor instanceof JSpinner.DefaultEditor) {
                    JFormattedTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
                    textField.setEditable(false);
                }
            }
            if (clockTimer != null) {
                clockTimer.setEnabled(false);
            }
        });
    }

    private void sendChatMessage() {
        String userMessage = chatInput.getText().trim();
        if (userMessage.isEmpty()) return;

        chatArea.append("You: " + userMessage + "\n\nAI: ");
        chatInput.setText("");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());

        chatInput.setEnabled(false);
        sendButton.setEnabled(false);
        sendButton.setText("Thinking...");

        new Thread(() -> {
            try {
                // MATCHING THE INTERFACE: handleToken and handleComplete
                llamaConnection.askStreaming(userMessage, new LlamaConnection.StreamHandler() {

                    @Override
                    public void handleToken(String token) {
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append(token);
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        });
                    }

                    @Override
                    public void handleComplete() {
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append("\n\n");
                            finishGeneration();
                        });
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("\n[Error: " + ex.getMessage() + "]\n\n");
                    finishGeneration();
                });
            }
        }).start();
    }

    // Helper to tidy up UI state
    private void finishGeneration() {
        chatInput.setEnabled(true);
        sendButton.setEnabled(true);
        sendButton.setText("Send");
        chatInput.requestFocus();
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
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

    private QuizOptionButton createOptionButton(String text, Color uniqueColor) {
        QuizOptionButton btn = new QuizOptionButton(text, uniqueColor);
        btn.setEnabled(false); // Enable this only after a scan
        return btn;
    }

    // Helper to determine if text should be black or white for best minimalist contrast
    private boolean isColorBright(Color c) {
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
        return luminance > 0.6;
    }

    // --- LOGIC: CHECK ANSWER ---
    private void checkAnswer(QuizOptionButton selectedBtn) {
        handleAnswerSelection(selectedBtn.getText(), selectedBtn);
    }

    private void checkAnswer(String selectedText) {
        handleAnswerSelection(selectedText, null);
    }

    private void handleAnswerSelection(String selectedText, QuizOptionButton selectedBtn) {
        if (quizTimer != null) quizTimer.stop();
        if (currentQuestion == null) return;

        // Log the user selection
        currentQuestion.userProvidedAnswer = selectedText;
        sessionLog.add(currentQuestion);

        boolean isCorrect = selectedText.equals(currentQuestion.correctAnswer);
        float score = isCorrect ? 1.0f : 0.0f;

        // 1. Logic Updates
        studentHistory.add(score);
        updateAI(score);
        removeQuestionFromPools(currentQuestion);

        // 2. Advanced Visual Feedback
        // Disable all buttons immediately to prevent multiple clicks
        btnA.setEnabled(false); btnB.setEnabled(false);
        btnC.setEnabled(false); btnD.setEnabled(false);

        if (isCorrect) {
            // Correct Choice: Solid Green Glow
            selectedBtn.setCorrectGlow();
        } else {
            // Incorrect Choice: User selection blinks Red
            if (selectedBtn != null) {
                selectedBtn.startErrorBlink();
            }

            // Reveal the correct answer with a Green Glow
            revealCorrectAnswer();
        }

        // 3. Smooth Transition
        // 1.5s pause allows the user to observe the blink and the reveal
        javax.swing.Timer transitionTimer = new javax.swing.Timer(1500, e -> {
            SwingUtilities.invokeLater(() -> loadNextQuestion(this.currentLevel));
        });
        transitionTimer.setRepeats(false);
        transitionTimer.start();
    }

    /**
     * Helper to find and highlight the correct button when the user fails.
     */
    private void revealCorrectAnswer() {
        String correct = currentQuestion.correctAnswer;
        // Use getText().contains because HTML tags surround the text
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
        easyMediumQuestions.removeIf(item -> item.id.equals(q.id));
        mediumQuestions.removeIf(item -> item.id.equals(q.id));
        mediumHardQuestions.removeIf(item -> item.id.equals(q.id));
        hardQuestions.removeIf(item -> item.id.equals(q.id));
        expertQuestions.removeIf(item -> item.id.equals(q.id));
    }

    private void updateAI(float score) {
        float probability = 0.5f;

        try {
            if (predictor != null && !studentHistory.isEmpty()) {
                // Convert the current history list to a primitive array
                // The TutorTranslator will handle the specific 10-step windowing
                float[] historyArray = new float[studentHistory.size()];
                for (int i = 0; i < studentHistory.size(); i++) {
                    historyArray[i] = studentHistory.get(i);
                }

                // Predict the mastery probability based on recent sequence
                probability = predictor.predict(historyArray);
            }
        } catch (Exception e) {
            System.err.println("AI Prediction Error: " + e.getMessage());
            probability = 0.5f; // Safe fallback
        }

        // Convert probability to 0-100 scale for UI components
        int percent = (int) (probability * 100);

        // Update the circular Mastery UI
        if (circleMastery != null) {
            circleMastery.setProgress(percent);
        }

        // --- 6-TIER DIFFICULTY MAPPING ---
        // Logic: Lower probability means the student is struggling -> EASY
        // Higher probability means mastery is high -> EXPERT
        String targetLevel;
        Color statusColor;

        if (percent < 15) {
            targetLevel = "EASY";
            statusColor = new Color(46, 204, 113); // Emerald Green
        } else if (percent < 30) {
            targetLevel = "EASY-MEDIUM";
            statusColor = new Color(171, 235, 198); // Light Green
        } else if (percent < 50) {
            targetLevel = "MEDIUM";
            statusColor = new Color(241, 196, 15); // Sunflower Yellow
        } else if (percent < 70) {
            targetLevel = "MEDIUM-HARD";
            statusColor = new Color(230, 126, 34); // Carrot Orange
        } else if (percent < 85) {
            targetLevel = "HARD";
            statusColor = new Color(231, 76, 60); // Alizarin Red
        } else {
            targetLevel = "EXPERT";
            statusColor = new Color(155, 89, 182); // Amethyst Purple
        }

        // Update Sidebar Status
        if (aiStatusLabel != null) {
            aiStatusLabel.setForeground(statusColor);
            aiStatusLabel.setText("Status: " + targetLevel + " (" + percent + "%)");
        }

        // Update global state for the loadNextQuestion method
        this.currentLevel = targetLevel;

        // System.out.println("LSTM Input Size: " + studentHistory.size() + " | Mastery: " + percent + "%");
    }


    private void loadNextQuestion(String targetLevel) {
        // 1. Determine which pool to use based on the AI's target level
        List<QuizItem> pool = getPoolByLevel(targetLevel);

        // 2. Fallback Logic: If the target pool is empty, find the next available pool
        if (pool == null || pool.isEmpty()) {
            pool = findFirstAvailablePool(targetLevel);
        }

        // 3. If all pools are empty, the quiz is over
        if (pool == null || pool.isEmpty()) {
            finishQuiz();
            return;
        }

        // 4. Reset UI: Clear the green/red colors from the previous turn
        resetButtonStyles();

        // 5. Setup the current question
        questionCounter++;
        currentQuestion = pool.get(0);

        // 6. Update the UI Labels
        difficultyLabel.setText("Question #" + questionCounter + " | Level: " + targetLevel);
        updateDifficultyLabelColor(targetLevel);

        // 7. Populate Question and Buttons
        questionArea.setText(currentQuestion.questionText);

        QuizOptionButton[] buttons = {btnA, btnB, btnC, btnD};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setEnabled(true);
            if (currentQuestion.options.size() > i) {
                buttons[i].setText(currentQuestion.options.get(i));
            }
        }

        // 8. Handle Timer
        if (userSelectedTime > 0) {
            startNewTimer();
        } else {
            timerLabel.setText("Timer: Off");
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
        // Priority order for searching if the preferred one is empty
        String[] levels = {"EXPERT", "HARD", "MEDIUM-HARD", "MEDIUM", "EASY-MEDIUM", "EASY"};
        for (String lvl : levels) {
            List<QuizItem> p = getPoolByLevel(lvl);
            if (p != null && !p.isEmpty()) return p;
        }
        return null;
    }

    private void updateDifficultyLabelColor(String level) {
        switch (level) {
            case "EXPERT":      difficultyLabel.setForeground(new Color(155, 89, 182)); break; // Purple
            case "HARD":        difficultyLabel.setForeground(new Color(231, 76, 60));  break; // Red
            case "MEDIUM-HARD": difficultyLabel.setForeground(new Color(230, 126, 34)); break; // Orange
            case "MEDIUM":      difficultyLabel.setForeground(new Color(241, 196, 15)); break; // Yellow
            case "EASY-MEDIUM": difficultyLabel.setForeground(new Color(171, 235, 198)); break; // Light Green
            case "EASY":        difficultyLabel.setForeground(new Color(46, 204, 113));  break; // Green
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
        // 1. Capture current settings
        selectedSubject = (String) subjectDropdown.getSelectedItem();
        currentBannedTopics = SUBJECT_BAN_LISTS.get(selectedSubject);

        if (userSelectedTime <= 0) {
            // Optional default: userSelectedTime = 600; // 10 mins
        }

        // 2. File Chooser
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Documents (PDF, JPG, PNG)", "pdf", "jpg", "png", "jpeg"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            difficultyLabel.setText("Initializing Engine...");
            questionCounter = 0;
            currentQuestion = null; // Important: Clear previous question state

            new Thread(() -> {
                try {
                    // --- STEP 1: OCR / PDF EXTRACTION ---
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

                    // --- STEP 2: PARALLEL AI GENERATION ---
                    // We update the UI to let the user know the AI is now "Thinking"
                    SwingUtilities.invokeLater(() -> difficultyLabel.setText("AI is generating first questions..."));

                    // This call now triggers multiple background threads (Parallel Batching)
                    generateMCQ(extractedText.toString());

                    // Note: We don't put the 'loadNextQuestion' here anymore because
                    // generateMCQ's internal threads will call it the moment they finish!

                    SwingUtilities.invokeLater(() -> {
                        // Sync the visual clock to user's choice
                        clockTimer.setSeconds(userSelectedTime);
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> difficultyLabel.setText("Scan Error: " + ex.getMessage()));
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
                SwingUtilities.invokeLater(() -> difficultyLabel.setText("Scanning Page " + pageNum + "..."));

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
    private void generateMCQ(String rawText) throws Exception {
        // 1. Clear previous session data
        easyQuestions.clear();
        easyMediumQuestions.clear();
        mediumQuestions.clear();
        mediumHardQuestions.clear();
        hardQuestions.clear();
        expertQuestions.clear();
        askedQuestionIDs.clear();

        // 2. Prepare text
        String text = cleanGarbage(rawText);
        if (text.length() > 2500) {
            text = text.substring(0, 2500) + "...";
        }
        final String contextText = text;

        // --- THREAD 1: FAST TRACK (EASY & MEDIUM) ---
        // Goal: Get the quiz started ASAP
        new Thread(() -> {
            try {
                String prompt = "Generate exactly 3 MCQs from this text. Difficulty: EASY or MEDIUM. " +
                        "Return ONLY a JSON array: [{\"question\":\"\", \"options\":[], \"correctAnswer\":\"\", \"difficulty\":\"\"}]";

                fetchAndPopulate(contextText, prompt);

                // If this is the first batch back, start the quiz immediately
                SwingUtilities.invokeLater(() -> {
                    if (currentQuestion == null && !easyQuestions.isEmpty()) {
                        loadNextQuestion("EASY");
                    }
                });
            } catch (Exception e) {
                System.err.println("Fast Track Gen Error: " + e.getMessage());
            }
        }).start();

        // --- THREAD 2: DEEP TRACK (HARD & EXPERT) ---
        // Goal: Populate the later stages of the quiz in the background
        new Thread(() -> {
            try {
                String prompt = "Generate exactly 3 deep-thinking MCQs. Difficulty: HARD or EXPERT. " +
                        "Return ONLY a JSON array with: question, options, correctAnswer, difficulty.";

                fetchAndPopulate(contextText, prompt);

                SwingUtilities.invokeLater(() -> {
                    updateStatus("AI Engine: All difficulty tiers loaded.");
                });
            } catch (Exception e) {
                System.err.println("Deep Track Gen Error: " + e.getMessage());
            }
        }).start();
    }

    private void fetchAndPopulate(String text, String customPrompt) throws Exception {
        // 1. Capture the exact count the user wants from the UI spinner
        int totalTarget = (int) questionCountSpinner.getValue();

        // 2. Parallel Load Balancing: Each thread generates half the total
        // We use a ceiling or Math.max to ensure we don't request 0 questions
        int batchSize = (totalTarget / 2) + (totalTarget % 2);
        if (batchSize < 1) batchSize = 1;

        // 3. Requesting JSON from local Ollama instance
        // Passing the batchSize ensures the AI knows when to stop
        String jsonResponse = llamaConnection.generateMCQs(text, customPrompt, batchSize);

        // 4. Robust Parsing
        JsonArray questionsArray = JsonParser.parseString(jsonResponse).getAsJsonArray();

        for (int i = 0; i < questionsArray.size(); i++) {
            JsonObject qObj = questionsArray.get(i).getAsJsonObject();

            String question = qObj.get("question").getAsString();
            String correctAnswer = qObj.get("correctAnswer").getAsString();
            String difficulty = qObj.get("difficulty").getAsString().toUpperCase();

            List<String> options = new ArrayList<>();
            JsonArray optsArray = qObj.get("options").getAsJsonArray();
            for (int j = 0; j < optsArray.size(); j++) {
                options.add(optsArray.get(j).getAsString());
            }

            // Create the QuizItem with the dynamic data
            QuizItem item = new QuizItem(question, question, correctAnswer, options, text);

            // 5. Thread-Safe Pool Management
            // Essential because FastGen and DeepGen threads finish at unpredictable times
            synchronized(askedQuestionIDs) {
                if (!askedQuestionIDs.contains(item.id)) {
                    if (difficulty.contains("EXPERT")) expertQuestions.add(item);
                    else if (difficulty.contains("HARD")) hardQuestions.add(item);
                    else if (difficulty.contains("MEDIUM-HARD")) mediumHardQuestions.add(item);
                    else if (difficulty.contains("MEDIUM")) mediumQuestions.add(item);
                    else if (difficulty.contains("EASY-MEDIUM")) easyMediumQuestions.add(item);
                    else easyQuestions.add(item);

                    askedQuestionIDs.add(item.id);
                }
            }
        }

        // UI Update: Notify the user that a batch is ready
        SwingUtilities.invokeLater(() -> {
            updateStatus("Batch sync complete. Pools updated.");
        });
    }

    // Fallback method using the old rule-based approach
    private void generateMCQFallback(String text) throws Exception {
        TokenizerModel tokenModel = new TokenizerModel(new FileInputStream("en-token.bin"));
        Tokenizer tokenizer = new TokenizerME(tokenModel);
        POSModel posModel = new POSModel(new FileInputStream("en-pos-maxent.bin"));
        POSTaggerME tagger = new POSTaggerME(posModel);

        String[] sentences = text.split("(?<=[.?!])\\s+");

        // PHASE 1: Identify Key Concepts (Nouns with frequency)
        Map<String, Integer> wordFrequency = new HashMap<>();
        for (String sentence : sentences) {
            if (!isSentenceLogical(sentence)) continue;

            String[] words = tokenizer.tokenize(sentence);
            String[] tags = tagger.tag(words);
            for (int i = 0; i < words.length; i++) {
                String w = words[i].toLowerCase();
                if (w.length() < 6 || currentBannedTopics.contains(w)) continue;

                if (tags[i].startsWith("NN")) {
                    wordFrequency.put(words[i], wordFrequency.getOrDefault(words[i], 0) + 1);
                }
            }
        }
        List<String> validTopics = new ArrayList<>(wordFrequency.keySet());

        // PHASE 2: Generate Questions based on Smart Strategies
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (!isSentenceLogical(sentence)) continue;

            boolean isMeta = false;
            for (String meta : META_PHRASES) if (sentence.toLowerCase().contains(meta)) isMeta = true;
            if (isMeta) continue;

            String qText = null;
            String answer = null;

            // STRATEGY A: Definition Detection
            if (sentence.contains(" is called ") || sentence.contains(" is known as ") || sentence.contains(" is defined as ")) {
                for (String topic : validTopics) {
                    if (sentence.contains(topic) && sentence.indexOf(topic) > sentence.indexOf(" is ")) {
                        answer = topic;
                        String definitionPart = sentence.substring(0, sentence.indexOf(" is "));
                        qText = "What concept is described by the following definition?\n\n\"" + definitionPart + "...\"";
                        break;
                    }
                }
            }

            // STRATEGY B: Contextual identification
            if (qText == null) {
                for (String topic : validTopics) {
                    if (sentence.contains(topic)) {
                        answer = topic;
                        qText = "Based on the text, what key concept is being discussed in this sentence?\n\n" +
                                "\"" + sentence + "\"";

                        if (sentence.matches("^(This|It|These|That).*") && i > 0) {
                            qText = "Context: " + sentences[i-1] + "\n\n" + qText;
                        }
                        break;
                    }
                }
            }

            // PHASE 3: Assign to one of 6 Difficulty Tiers
            if (qText != null && answer != null) {
                List<String> options = new ArrayList<>();
                options.add(answer);

                Collections.shuffle(validTopics);
                for (String dist : validTopics) {
                    if (options.size() < 4 && !dist.equalsIgnoreCase(answer)) {
                        options.add(dist);
                    }
                }
                while (options.size() < 4) options.add("General Concept");
                Collections.shuffle(options);

                QuizItem item = new QuizItem(qText, sentence, answer, options, sentence);
                if (!askedQuestionIDs.contains(item.id)) {
                    String lowerS = sentence.toLowerCase();
                    String lowerQ = qText.toLowerCase();

                    if (lowerQ.contains("described by") && lowerS.contains("defined as")) {
                        expertQuestions.add(item); // Really Hard
                    } else if (lowerQ.contains("described by")) {
                        hardQuestions.add(item);   // Hard
                    } else if (lowerS.contains(" because ") || lowerS.contains(" therefore ")) {
                        mediumHardQuestions.add(item);
                    } else if (sentence.length() > 120) {
                        mediumQuestions.add(item);
                    } else if (sentence.length() > 80) {
                        easyMediumQuestions.add(item);
                    } else {
                        easyQuestions.add(item);
                    }
                }
            }
        }
    }

    private String cleanGarbage(String text) {
        // Removes things like "18-Jun-21", "2:27:39 PM", "indd", "Reprint 2025-26"
        String cleaned = text.replaceAll("(?i)\\b\\d{1,2}-[a-z]{3}-\\d{2,4}\\b", ""); // Dates
        cleaned = cleaned.replaceAll("(?i)\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM)?\\b", ""); // Time
        cleaned = cleaned.replaceAll("(?i)\\b(?:chapter|indd|reprint|page|edition|version)\\b", ""); // Metadata words
        cleaned = cleaned.replaceAll("\\d{4}-\\d{2,4}", ""); // Year ranges
        return cleaned.trim();
    }

    private boolean isSentenceLogical(String sentence) {
        // A logical educational sentence should be long enough and not just numbers/symbols
        if (sentence.length() < 45) return false;

        long digitCount = sentence.chars().filter(Character::isDigit).count();
        // If more than 15% of the sentence is numbers, it's likely a page header/footer
        if ((double) digitCount / sentence.length() > 0.15) return false;

        // Must contain common 'teaching' verbs to be a good question candidate
        String lower = sentence.toLowerCase();
        return lower.contains(" is ") || lower.contains(" are ") ||
                lower.contains(" refers ") || lower.contains(" means ") || lower.contains(" used ");
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

    // --- UNIFIED & CORRECTED DASHBOARD CLASS ---
    private class PaLOHomePage extends JDialog {
        private JPanel topBar;
        private JPanel centerArea;
        private JPanel bottomBar;

        public PaLOHomePage(Frame owner) {
            // Set modality to false to prevent blocking initialization threads
            // that keep the taskbar icon alive.
            super(owner, false);
            setUndecorated(false);
            setTitle("PaLO - Student Dashboard");
            setSize(1200, 750);
            setLocationRelativeTo(null);

            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    if (getOwner() != null) {
                        if (questionCounter > 0) {
                            closeAndSwitch(); // Ensure we snap back if closed manually
                        } else {
                            int response = JOptionPane.showConfirmDialog(null,
                                    "Exit PaLO entirely?", "Confirm Exit", JOptionPane.YES_NO_OPTION);
                            if (response == JOptionPane.YES_OPTION) System.exit(0);
                        }
                    }
                }
            });

            BackgroundPanel bgPanel = new BackgroundPanel("homepage_bg.gif");
            bgPanel.setLayout(new BorderLayout());

            // --- TOPBAR ---
            topBar = new JPanel(new BorderLayout());
            topBar.setOpaque(false);
            topBar.setBorder(BorderFactory.createEmptyBorder(30, 60, 0, 60));

            JLabel welcome = new JLabel("<html><font color='#BBBBBB' size='5'>WELCOME BACK,</font><br>" +
                    "<font size='14' color='white'><b>THUSHAR</b></font></html>");

            JPanel topTrailingArea = new JPanel();
            topTrailingArea.setLayout(new BoxLayout(topTrailingArea, BoxLayout.Y_AXIS));
            topTrailingArea.setOpaque(false);
            topTrailingArea.add(createLiveClock());
            topTrailingArea.add(Box.createRigidArea(new Dimension(0, 25)));
            topTrailingArea.add(createBigQuitButton());
            topBar.add(welcome, BorderLayout.WEST);
            topBar.add(topTrailingArea, BorderLayout.EAST);

            // --- CENTER AREA (Analytics) ---
            centerArea = new JPanel(new GridBagLayout());
            centerArea.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();

            GlassCardPanel analyticalCard = new GlassCardPanel();
            analyticalCard.setLayout(new BoxLayout(analyticalCard, BoxLayout.Y_AXIS));

            ProgressGraphPanel graph = new ProgressGraphPanel();
            JLabel analysisLabel = new JLabel("<html><body style='width: 350px; text-align: center;'>" +
                    calculateTrend() + "</body></html>");
            analysisLabel.setFont(new Font("Segoe UI Light", Font.PLAIN, 15));
            analysisLabel.setForeground(Color.WHITE);
            analysisLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            analyticalCard.add(createLegend());
            analyticalCard.add(graph);
            analyticalCard.add(Box.createRigidArea(new Dimension(0, 15)));
            analyticalCard.add(analysisLabel);

            gbc.gridx = 1; gbc.weightx = 1.0;
            gbc.anchor = GridBagConstraints.CENTER;
            centerArea.add(analyticalCard, gbc);

            // --- BOTTOMBAR (Direct Navigation) ---
            bottomBar = new JPanel(new BorderLayout());
            bottomBar.setOpaque(false);
            bottomBar.setBorder(BorderFactory.createEmptyBorder(0, 60, 60, 60));

            JPanel utilityGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
            utilityGroup.setOpaque(false);
            utilityGroup.add(createGlowButton("SETTINGS", new Color(255, 200, 0), e -> openSettings()));
            utilityGroup.add(createGlowButton("DEEP FOCUS", new Color(155, 89, 182), e -> launchDeepFocusMode()));

            JPanel studyGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
            studyGroup.setOpaque(false);

            studyGroup.add(createGlowButton("TALK TO AI", new Color(155, 89, 182), e -> {
                isChatMode = true; isAudioMode = false;
                closeAndSwitch();
            }));

            studyGroup.add(createGlowButton("AUDIO MODE", new Color(52, 152, 219), e -> {
                isChatMode = false; isAudioMode = true;
                closeAndSwitch();
            }));

            studyGroup.add(createGlowButton("OFFLINE STUDY", new Color(46, 204, 113), e -> {
                isChatMode = false; isAudioMode = false;
                closeAndSwitch();
            }));

            bottomBar.add(utilityGroup, BorderLayout.WEST);
            bottomBar.add(studyGroup, BorderLayout.EAST);

            bgPanel.add(topBar, BorderLayout.NORTH);
            bgPanel.add(centerArea, BorderLayout.CENTER);
            bgPanel.add(bottomBar, BorderLayout.SOUTH);

            setContentPane(bgPanel);
            SwingUtilities.invokeLater(graph::startAnimation);
        }

        /**
         * Replaces animateAndTransition.
         * Handles instant mode switching and window restoration.
         */
        private void closeAndSwitch() {
            OfflineTutorApp mainApp = (OfflineTutorApp)getOwner();
            if (mainApp != null) {
                // 1. CONFIGURE THE CORRECT CARD
                // Switches the main view to Quiz, Chat, or Audio mode
                if (isChatMode) {
                    mainApp.switchToChatMode();
                } else if (isAudioMode) {
                    mainApp.switchToAudioMode();
                } else {
                    mainApp.switchToQuizMode();
                }

                // 2. SNAP BACK TO CENTER
                // IMPORTANT: We removed setOpacity(1.0f) here because it crashes decorated frames.
                // This method moves the window from its off-screen parking spot (-4000)
                // back to the center of your monitor.
                mainApp.setLocationRelativeTo(null);

                // 3. BRING TO FOCUS
                mainApp.setVisible(true);
                mainApp.toFront();
                mainApp.requestFocus();

                // 4. REFRESH UI
                mainApp.revalidate();
                mainApp.repaint();
            }

            // 5. CLOSE DASHBOARD
            this.dispose();
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

            JLabel title = new JLabel("SELECT YOUR FOCUS PATH");
            title.setFont(new Font("Segoe UI Black", Font.BOLD, 36));
            title.setForeground(Color.WHITE);
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
            focusBG.add(title, gbc);

            gbc.gridwidth = 1;

            JButton pBtn = createTechniqueButton("POMODORO", "25m Work • 5m Rest", new Color(255, 45, 85));
            pBtn.addActionListener(e -> startFocusSession(focusDialog, 25, 5));
            gbc.gridy = 1; gbc.gridx = 0; focusBG.add(pBtn, gbc);

            JButton fBtn = createTechniqueButton("FLOW STATE", "90m Deep Work • 15m Rest", new Color(0, 220, 255));
            fBtn.addActionListener(e -> startFocusSession(focusDialog, 90, 15));
            gbc.gridx = 1; focusBG.add(fBtn, gbc);

            JButton rBtn = createTechniqueButton("52 / 17 RULE", "Science-backed Productivity", new Color(46, 204, 113));
            rBtn.addActionListener(e -> startFocusSession(focusDialog, 52, 17));
            gbc.gridy = 2; gbc.gridx = 0; focusBG.add(rBtn, gbc);

            JButton cBtn = createTechniqueButton("CUSTOM ZEN", "Set your own intervals", new Color(255, 200, 0));
            cBtn.addActionListener(e -> {
                String work = JOptionPane.showInputDialog("Work Minutes:");
                String rest = JOptionPane.showInputDialog("Rest Minutes:");
                try {
                    startFocusSession(focusDialog, Integer.parseInt(work), Integer.parseInt(rest));
                } catch(Exception ex) {}
            });
            gbc.gridx = 1; focusBG.add(cBtn, gbc);

            JButton back = new JButton("BACK TO MENU");
            back.setForeground(Color.GRAY);
            back.setContentAreaFilled(false);
            back.addActionListener(e -> focusDialog.dispose());
            gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
            focusBG.add(back, gbc);

            focusDialog.add(focusBG);
            focusDialog.setVisible(true);
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

        private JLabel createLiveClock() {
            JLabel label = new JLabel();
            label.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 22));
            label.setForeground(Color.WHITE);
            label.setAlignmentX(Component.RIGHT_ALIGNMENT);
            new javax.swing.Timer(1000, e -> label.setText(new java.text.SimpleDateFormat("hh:mm:ss a").format(new java.util.Date()))).start();
            return label;
        }

        private JButton createBigQuitButton() {
            JButton btn = new JButton("EXIT APPLICATION");
            btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btn.setForeground(new Color(255, 255, 255, 150));
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 50)));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> System.exit(0));
            return btn;
        }

        private JPanel createLegend() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            p.setOpaque(false);
            JLabel dot = new JLabel("● ");
            dot.setForeground(currentAccentColor);
            JLabel txt = new JLabel("Mastery Progress  ");
            txt.setForeground(Color.WHITE);
            p.add(dot); p.add(txt);
            return p;
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
                        g2.setStroke(new BasicStroke(2.5f));
                        g2.setColor(glowColor);
                    } else {
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.setColor(new Color(255, 255, 255, 100));
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
    }// --- END OF PaLOHomePage ---


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
        private final int FIXED_SEQUENCE_LENGTH = 10;

        @Override
        public NDList processInput(TranslatorContext ctx, float[] input) {
            NDManager manager = ctx.getNDManager();

            // 1. Create a fixed-size buffer filled with neutral values (0.5)
            float[] paddedInput = new float[FIXED_SEQUENCE_LENGTH];
            Arrays.fill(paddedInput, 0.5f);

            // 2. Fill the buffer with the most recent scores from the end
            // This ensures the LSTM always receives exactly 10 time steps.
            int startIdx = Math.max(0, input.length - FIXED_SEQUENCE_LENGTH);
            int fillCount = Math.min(input.length, FIXED_SEQUENCE_LENGTH);

            System.arraycopy(input, startIdx, paddedInput, FIXED_SEQUENCE_LENGTH - fillCount, fillCount);

            // 3. Reshape for LSTM: [Batch Size (1), Time Steps (10), Features (1)]
            return new NDList(manager.create(paddedInput).reshape(1, FIXED_SEQUENCE_LENGTH, 1));
        }

        @Override
        public Float processOutput(TranslatorContext ctx, NDList list) {
            // DJL returns an NDArray; singletonOrThrow gets the single probability value
            return list.singletonOrThrow().getFloat();
        }
    }

    private void setAppIcon() {
        try {
            // Path to your logo file
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
        // Reset buttons to their default state (matching your createOptionButton styling)
        QuizOptionButton[] buttons = {btnA, btnB, btnC, btnD};
        for (QuizOptionButton btn : buttons) {
            btn.resetVisual();
        }
    }

    public static void main(String[] args) {
        com.formdev.flatlaf.FlatDarkLaf.setup();
        // Launch the app constructor on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new OfflineTutorApp());
    }
}