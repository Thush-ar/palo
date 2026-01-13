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

    public OfflineTutorApp() {
        SplashScreen splash = new SplashScreen();
        splash.setVisible(true);

        new Thread(() -> {
            try {
                Thread.sleep(3000);
                initAI();
                loadProgress();

                SwingUtilities.invokeLater(() -> {
                    setupUI();

                    // 1. Force the specific resolution
                    setSize(1400, 800);

                    // 2. Center the window on the screen
                    // IMPORTANT: This must come AFTER setSize()
                    setLocationRelativeTo(null);

                    setVisible(true);
                    splash.dispose();
                });
            } catch (Exception e) {
                e.printStackTrace();
                splash.dispose();
            }
        }).start();
    }

    private void setupUI() {
        setTitle("Progressive and Audio assisted Learning Orchestrator (" + selectedSubject + " Mode)");
        setSize(1400, 800);

        // IMPORTANT: Change this to DO_NOTHING so we can handle it manually
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // Add the WindowListener to catch the "X" click
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleExitRequest();
            }
        });

        setLayout(new BorderLayout(10, 10));

        // --- 1. TOP PANEL ---
        // Changed GridLayout to 4 rows to accommodate the Timer Label
        JPanel topPanel = new JPanel(new GridLayout(4, 1));

        timerLabel = new JLabel("Timer: Off", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        aiStatusLabel = new JLabel("Status: Waiting for Scan...", SwingConstants.CENTER);
        aiStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));

        difficultyLabel = new JLabel("Difficulty Level: N/A", SwingConstants.CENTER);
        difficultyLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        difficultyLabel.setForeground(Color.GRAY);

        masteryBar = new JProgressBar(0, 100);
        masteryBar.setStringPainted(true);
        masteryBar.setString("Predicted Mastery: 0%");
        masteryBar.setForeground(new Color(46, 204, 113));

        topPanel.add(timerLabel);
        topPanel.add(aiStatusLabel);
        topPanel.add(difficultyLabel);
        topPanel.add(masteryBar);
        add(topPanel, BorderLayout.NORTH);

        // --- 2. CENTER SPLIT PANE ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // Left: Quiz Area
        JPanel quizPanel = new JPanel(new BorderLayout(10, 10));
        questionArea = new JTextArea("\n   [Instructions]\n   1. Select Subject and Timer below.\n   2. Click 'Scan Textbook Page'.\n   3. Answer questions to improve Mastery.");
        questionArea.setFont(new Font("Serif", Font.PLAIN, 22));
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setEditable(false);
        questionArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel optionsPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        btnA = createOptionButton("Option A");
        btnB = createOptionButton("Option B");
        btnC = createOptionButton("Option C");
        btnD = createOptionButton("Option D");
        optionsPanel.add(btnA); optionsPanel.add(btnB);
        optionsPanel.add(btnC); optionsPanel.add(btnD);

        quizPanel.add(new JScrollPane(questionArea), BorderLayout.CENTER);
        quizPanel.add(optionsPanel, BorderLayout.SOUTH);

        // Right: Image Viewer
        imageViewer = new JLabel("No Image Scanned", SwingConstants.CENTER);
        imageViewer.setFont(new Font("Segoe UI", Font.ITALIC, 18));
        imageViewer.setOpaque(true);
        imageViewer.setBackground(Color.LIGHT_GRAY);
        JScrollPane imageScroll = new JScrollPane(imageViewer);

        splitPane.setLeftComponent(quizPanel);
        splitPane.setRightComponent(imageScroll);
        add(splitPane, BorderLayout.CENTER);

        // --- 3. INTEGRATED BOTTOM PANEL ---
        JPanel bottomContainer = new JPanel(new BorderLayout());

        // Settings Bar
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Session Settings"));

        subjectDropdown = new JComboBox<>(new String[]{"Physics", "Computer Science", "Biology", "General"});
        subjectDropdown.setSelectedItem(selectedSubject);

        timerDropdown = new JComboBox<>(new String[]{"No Timer", "15 Seconds", "30 Seconds", "60 Seconds"});

        settingsPanel.add(new JLabel("Subject:"));
        settingsPanel.add(subjectDropdown);
        settingsPanel.add(new JLabel("Timer:"));
        settingsPanel.add(timerDropdown);

        // Main Control Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 15));
        JButton btnScan = new JButton("📷 Scan Textbook Page");
        btnScan.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnScan.addActionListener(e -> performScan());

        buttonPanel.add(btnScan);

        // Combine settings and buttons into the container
        bottomContainer.add(settingsPanel, BorderLayout.NORTH);
        bottomContainer.add(buttonPanel, BorderLayout.SOUTH);

        // Add the single container to the SOUTH position
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void startNewTimer() {
        if (userSelectedTime <= 0) return;

        if (quizTimer != null && quizTimer.isRunning()) {
            quizTimer.stop();
        }

        secondsRemaining = userSelectedTime;
        timerLabel.setText("Time Left: " + secondsRemaining + "s");
        timerLabel.setForeground(Color.BLACK); // Reset color

        quizTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;

            if (secondsRemaining <= 5) {
                timerLabel.setForeground(Color.RED); // Warning color
            }

            timerLabel.setText("Time Left: " + secondsRemaining + "s");

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

        // Save the user's choice into the question object
        currentQuestion.userProvidedAnswer = selectedText;
        sessionLog.add(currentQuestion); // Add to our permanent session log

        float score = selectedText.equals(currentQuestion.correctAnswer) ? 1.0f : 0.0f;

        if (selectedText.equals(currentQuestion.correctAnswer)) {
            score = 1.0f;
            JOptionPane.showMessageDialog(this, "Correct! ✅");
        } else {
            score = 0.0f;
            JOptionPane.showMessageDialog(this,
                    "Wrong! ❌\n\nCorrect Answer: " + currentQuestion.correctAnswer +
                            "\n\nContext:\n\"" + currentQuestion.originalContext + "\"");
        }

        studentHistory.add(score);
        updateAI(score);
        removeQuestionFromPools(currentQuestion);

        boolean nextIsEasy = (score == 0.0f);
        loadNextQuestion(nextIsEasy);
    }

    private void showPerformanceReport() {
        if (sessionLog.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No questions answered yet.");
            return;
        }

        // Calculate Accuracy
        long correctCount = sessionLog.stream()
                .filter(q -> q.userProvidedAnswer.equals(q.correctAnswer)).count();
        double accuracy = (double) correctCount / sessionLog.size() * 100;

        // Setup Table Model
        String[] columns = {"Status", "Question", "Your Answer", "Correct Answer", "Textbook Reference"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);

        for (QuizItem q : sessionLog) {
            boolean isCorrect = q.userProvidedAnswer.equals(q.correctAnswer);
            model.addRow(new Object[]{
                    isCorrect ? "✅" : "❌",
                    q.questionText.length() > 50 ? q.questionText.substring(0, 50) + "..." : q.questionText,
                    q.userProvidedAnswer,
                    q.correctAnswer,
                    q.originalContext // The "Reference Lines"
            });
        }

        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(4).setPreferredWidth(200); // Give space for reference lines

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JLabel summaryLabel = new JLabel(String.format(
                "<html><body style='padding:10px;'><h2>Session Summary</h2>" +
                        "<b>Total:</b> %d | <b>Correct:</b> %d | <b>Accuracy:</b> %.1f%%</body></html>",
                sessionLog.size(), correctCount, accuracy));

        panel.add(summaryLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(900, 500));

        JOptionPane.showMessageDialog(this, panel, "Final Performance Review", JOptionPane.PLAIN_MESSAGE);
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
        masteryBar.setValue(percent);
        masteryBar.setString("Mastery: " + percent + "%");

        if (probability < 0.4) {
            aiStatusLabel.setForeground(Color.RED);
            aiStatusLabel.setText("AI: DETECTED STRUGGLE -> Switching to Easier Questions");
        } else {
            aiStatusLabel.setForeground(new Color(39, 174, 96));
            aiStatusLabel.setText("AI: DETECTED MASTERY -> Switching to Harder Questions");
        }
    }

    private void handleExitRequest() {
        int response = JOptionPane.showConfirmDialog(
                this, "Quit Now?", "Exit Confirmation", JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
            showPerformanceReport(); // Show marks and wrong answers first
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