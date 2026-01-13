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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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

    // Add this field at the top of your class
    private boolean isAudioMode = false;

    public OfflineTutorApp() {
        SplashScreen splash = new SplashScreen();
        splash.setVisible(true);

        new Thread(() -> {
            try {
                // 1. Initialize logic/AI while splash is visible
                initAI();
                loadProgress();
                Thread.sleep(3000); // Allow splash to be seen

                SwingUtilities.invokeLater(() -> {
                    splash.dispose();

                    // 2. Show the New Header/Mode Selection Window
                    ModeSelectionScreen selector = new ModeSelectionScreen(this);
                    selector.setVisible(true); // Execution pauses here until a mode is picked

                    // 3. Build Main UI based on selection
                    setupUI();
                    setSize(1400, 800);
                    setLocationRelativeTo(null);
                    setVisible(true);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private CircleMasteryPanel circleMastery;


    private void setupUI() {
        setTitle("PaLO - Adaptive Learning Orchestrator (" + selectedSubject + " Mode)");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { handleExitRequest(); }
        });

        setLayout(new BorderLayout(20, 0));
        getContentPane().setBackground(new Color(28, 28, 28));

        // --- INITIALIZE COMPONENTS ---
        aiStatusLabel = new JLabel("Ready", SwingConstants.CENTER);
        aiStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        difficultyLabel = new JLabel("Question #1 | Current Level: EASY", SwingConstants.CENTER);
        difficultyLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        difficultyLabel.setForeground(new Color(230, 126, 34));

        timerLabel = new JLabel("00s", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Monospaced", Font.BOLD, 42));
        timerLabel.setForeground(new Color(231, 76, 60));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setPreferredSize(new Dimension(250, 60));
        timerLabel.setMaximumSize(new Dimension(250, 60));

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
        sidebar.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 10));

        addSidebarSection(sidebar, "OVERALL MASTERY", circleMastery);
        sidebar.add(Box.createRigidArea(new Dimension(0, 40)));
        addSidebarSection(sidebar, "TIME REMAINING", timerLabel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 40)));

        subjectDropdown = new JComboBox<>(new String[]{"Physics", "Computer Science", "Biology", "General"});
        subjectDropdown.setMaximumSize(new Dimension(220, 35));
        addSidebarSection(sidebar, "SUBJECT", subjectDropdown);

        timerDropdown = new JComboBox<>(new String[]{"No Timer", "15 Seconds", "30 Seconds", "60 Seconds"});
        timerDropdown.setMaximumSize(new Dimension(220, 35));
        addSidebarSection(sidebar, "TIME LIMIT", timerDropdown);

        sidebar.add(Box.createVerticalGlue());
        sidebar.add(aiStatusLabel);

        JButton btnScan = new JButton("SCAN TEXTBOOK");
        btnScan.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnScan.setMaximumSize(new Dimension(220, 45));
        btnScan.setBackground(new Color(52, 152, 219));
        btnScan.setForeground(Color.WHITE);
        btnScan.addActionListener(e -> performScan());
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(btnScan);

        // --- QUIZ CONTENT ---
        JPanel quizContent = new JPanel(new BorderLayout(20, 20));
        quizContent.setOpaque(false);
        quizContent.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 30));

        questionArea = new JTextArea();
        questionArea.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setBackground(new Color(40, 40, 40));
        questionArea.setForeground(Color.WHITE);
        JScrollPane qScroll = new JScrollPane(questionArea);
        qScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 2));

        JPanel btnGrid = new JPanel(new GridLayout(2, 2, 15, 15));
        btnGrid.setOpaque(false);
        btnA = createOptionButton("A"); btnB = createOptionButton("B");
        btnC = createOptionButton("C"); btnD = createOptionButton("D");
        btnGrid.add(btnA); btnGrid.add(btnB); btnGrid.add(btnC); btnGrid.add(btnGrid.add(btnD));

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
        if (userSelectedTime <= 0) {
            timerLabel.setText("Off");
            return;
        }

        if (quizTimer != null && quizTimer.isRunning()) {
            quizTimer.stop();
        }

        secondsRemaining = userSelectedTime;
        timerLabel.setText(secondsRemaining + "s"); // Shorter text to prevent "..."

        quizTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            timerLabel.setText(secondsRemaining + "s");

            if (secondsRemaining <= 5) {
                timerLabel.setForeground(Color.YELLOW);
            }

            if (secondsRemaining <= 0) {
                quizTimer.stop();
                handleTimeout();
            }
        });
        quizTimer.start();
    }

    private void handleTimeout() {
        JOptionPane.showMessageDialog(this, "Time's up!");
        checkAnswer("TIMEOUT");
    }

    private JButton createOptionButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btn.addActionListener(e -> checkAnswer(btn.getText()));
        btn.setEnabled(false);
        return btn;
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

        String timeChoice = (String) timerDropdown.getSelectedItem();
        if (timeChoice != null && !timeChoice.equals("No Timer")) {
            userSelectedTime = Integer.parseInt(timeChoice.split(" ")[0]);
        } else {
            userSelectedTime = 0;
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
                            JOptionPane.showMessageDialog(this, "The AI couldn't find enough clear text to generate questions. Please try a clearer scan.");
                        } else {
                            aiStatusLabel.setText("Scan Complete!");
                            loadNextQuestion(true); // Start the quiz
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> aiStatusLabel.setText("Error: " + ex.getMessage()));
                }
            }).start();
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

    private void initAI() {
        try {
            Path modelPath = Paths.get("tutor_brain.pt");
            Model model = Model.newInstance("AdaptiveTutor");
            model.load(modelPath);
            this.predictor = model.newPredictor(new TutorTranslator());
        } catch (Exception e) {}
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
        // Don't call setVisible(true) here, the constructor does it now
        SwingUtilities.invokeLater(() -> new OfflineTutorApp());
    }
}