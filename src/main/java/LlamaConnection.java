import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * LlamaConnection - Optimized for PaLO AI Tutoring.
 * Supports Model Switching between General Theory and Numerical Logic.
 */
public class LlamaConnection {
    private static final String OLLAMA_BASE_URL = "http://localhost:11434/api/generate";
    private static final String OLLAMA_TAGS_URL = "http://localhost:11434/api/tags";
    private static final String OLLAMA_PULL_URL = "http://localhost:11434/api/pull";

    // Define the two brains
    private static final String THEORY_MODEL = "qwen2.5:1.5b";
    private static final String MATH_MODEL = "qwen2-math:1.5b";

    private String activeModel = THEORY_MODEL; // Default
    private final Gson gson = new Gson();
    private Process ollamaProcess;

    public LlamaConnection() {
        detectModel();
    }

    /**
     * Generates MCQs. This is the main entry point for your Quiz logic.
     * @param isNumerical Set to true to switch to the Math Brain.
     */
    public String generateMCQs(String text, String customPrompt, int questionCount, boolean isNumerical) throws IOException {
        // Step 1: Switch the brain
        this.activeModel = isNumerical ? MATH_MODEL : THEORY_MODEL;

        // Step 2: Limit context window to prevent lag
        String limitedText = text.substring(0, Math.min(text.length(), 2800));

        // Step 3: Enhance the prompt based on mode
        StringBuilder systemInstruction = new StringBuilder();
        systemInstruction.append("You are an expert tutor for visually impaired students. ");
        systemInstruction.append("Generate exactly ").append(questionCount).append(" MCQs. ");

        if (isNumerical) {
            systemInstruction.append("MODE: NUMERICAL. Focus on calculations and logic. ")
                    .append("Use LaTeX for all mathematical expressions. ")
                    .append("Ensure options include common calculation errors as distractors.");
        } else {
            systemInstruction.append("MODE: THEORETICAL. Focus on definitions and concepts.");
        }

        String fullPrompt = systemInstruction.toString() + "\n\n" + customPrompt + "\n\nContext:\n" + limitedText;

        // Step 4: Build Request
        JsonObject request = new JsonObject();
        request.addProperty("model", activeModel);
        request.addProperty("prompt", fullPrompt);
        request.addProperty("stream", false);

        // Logic tweaks: Lower temperature for math (accuracy) vs theory (variety)
        JsonObject options = new JsonObject();
        options.addProperty("temperature", isNumerical ? 0 : 0.6);
        options.addProperty("num_predict", 1024); // Ensure enough space for the XML response.
        request.add("options", options);

        return executeRequest(request);
    }

    /**
     * Standard MCQ generation (Theory fallback)
     */
    public String generateMCQs(String text, String customPrompt, int count) throws IOException {
        return generateMCQs(text, customPrompt, count, false);
    }

    /**
     * Handles the HTTP communication and cleans the AI output.
     */
    /**
     * Handles the HTTP communication and cleans the AI output.
     */
    private String executeRequest(JsonObject requestJson) throws IOException {
        URL url = new URL(OLLAMA_BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(requestJson).getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);

                // This parses the Ollama API wrapper, NOT the AI's generated text
                JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
                String rawAiText = root.get("response").getAsString().trim();

                // UPDATED: Removes ANY markdown block (```xml, ```json, or ```)
                // so the XML regex parser in OfflineTutorApp gets clean text.
                return rawAiText.replaceAll("(?s)```[a-zA-Z]*\n?|```", "").trim();
            }
        }
        throw new IOException("Ollama Error: Code " + conn.getResponseCode());
    }

    /**
     * General Chat (Stays on Theory Model unless switched)
     */
    public void askStreaming(String prompt, StreamHandler handler) throws Exception {
        URL url = new URL(OLLAMA_BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JsonObject request = new JsonObject();
        request.addProperty("model", activeModel);
        request.addProperty("prompt", prompt);
        request.addProperty("stream", true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(request).getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                JsonObject jsonLine = JsonParser.parseString(line).getAsJsonObject();
                if (jsonLine.has("response")) {
                    handler.handleToken(jsonLine.get("response").getAsString());
                }
                if (jsonLine.has("done") && jsonLine.get("done").getAsBoolean()) break;
            }
        }
        handler.handleComplete();
    }

    // --- System / Infrastructure Methods ---

    public boolean isAvailable() {
        try {
            URL url = new URL(OLLAMA_TAGS_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception e) { return false; }
    }

    private void detectModel() {
        // Logic to check which models are already pulled in Ollama
        try {
            URL url = new URL(OLLAMA_TAGS_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return;

            InputStreamReader isr = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(new BufferedReader(isr).readLine()).getAsJsonObject();
            JsonArray models = root.getAsJsonArray("models");

            boolean hasMath = false;
            for (JsonElement m : models) {
                String name = m.getAsJsonObject().get("name").getAsString();
                if (name.contains("qwen2-math")) hasMath = true;
            }
            // If the user has Math model, keep it as an option
        } catch (Exception ignored) { }
    }

    public interface StreamHandler {
        void handleToken(String token);
        void handleComplete();
    }
}