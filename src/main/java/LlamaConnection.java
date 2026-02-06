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
 * LlamaConnection - Communicates with Ollama running locally.
 * All interaction is in-app via HTTP API; no browser or terminal required.
 */
public class LlamaConnection {
    private static final String OLLAMA_BASE_URL = "http://localhost:11434/api/generate";
    private static final String OLLAMA_TAGS_URL = "http://localhost:11434/api/tags";
    private static final String OLLAMA_PULL_URL = "http://localhost:11434/api/pull";
    private static final String[] PREFERRED_MODELS = {"llama3.2", "llama3", "llama2", "mistral", "phi"};
    private static final String DEFAULT_PULL_MODEL = "llama3.2";

    private String modelName = "llama2"; // fallback
    private Gson gson = new Gson();
    private Process ollamaProcess; // keep reference so it stays alive (optional)

    public LlamaConnection() {
        detectModel();
    }

    /** Returns the model name in use (for UI). */
    public String getModelName() {
        return modelName;
    }

    /** Re-checks Ollama and picks best model. Call after starting Ollama or pulling a model. */
    public void refreshModel() {
        detectModel();
    }

    /**
     * Starts Ollama inside the application (no browser/terminal needed).
     * Tries: "ollama serve" from PATH, or on Windows the installed app directory.
     */
    public boolean startOllamaFromApp() {
        if (isAvailable()) return true;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;

        if (os.contains("win")) {
            // Windows: try GUI app first (starts server), then "ollama serve" from PATH
            String localAppData = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("ProgramFiles");
            File exePath = null;
            if (localAppData != null) {
                exePath = new File(localAppData, "Programs\\Ollama\\ollama app.exe");
                if (!exePath.exists()) exePath = new File(localAppData, "Programs\\Ollama\\ollama.exe");
            }
            if ((exePath == null || !exePath.exists()) && programFiles != null) {
                exePath = new File(programFiles, "Ollama\\ollama app.exe");
                if (!exePath.exists()) exePath = new File(programFiles, "Ollama\\ollama.exe");
            }
            if (exePath != null && exePath.exists()) {
                try {
                    ProcessBuilder appPb = new ProcessBuilder(exePath.getAbsolutePath());
                    appPb.redirectError(ProcessBuilder.Redirect.DISCARD);
                    appPb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    ollamaProcess = appPb.start();
                    return waitUntilAvailable(30);
                } catch (IOException e) {
                    // fallback below
                }
            }
            pb = new ProcessBuilder("ollama", "serve");
        } else {
            pb = new ProcessBuilder("ollama", "serve");
        }

        try {
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            ollamaProcess = pb.start();
            return waitUntilAvailable(30);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean waitUntilAvailable(int maxSeconds) {
        for (int i = 0; i < maxSeconds; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (isAvailable()) {
                refreshModel();
                return true;
            }
        }
        return false;
    }

    /**
     * Pulls a model via Ollama API (in-app, no terminal).
     * progressCallback can be null; if non-null, called with status lines during download.
     */
    public void pullModel(String modelName, Runnable progressCallback) throws IOException {
        URL url = new URL(OLLAMA_PULL_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(0); // pull can take a long time

        JsonObject body = new JsonObject();
        body.addProperty("name", modelName);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Pull failed: " + conn.getResponseCode());
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null && progressCallback != null) {
                progressCallback.run();
            }
        }
        refreshModel();
    }

    /** Convenience: pull default model (e.g. llama3.2). */
    public void pullDefaultModel(Runnable progressCallback) throws IOException {
        pullModel(DEFAULT_PULL_MODEL, progressCallback);
    }

    public static String getDefaultPullModelName() {
        return DEFAULT_PULL_MODEL;
    }

    /** Picks the first available model from PREFERRED_MODELS, or keeps default. */
    private void detectModel() {
        try {
            URL url = new URL(OLLAMA_TAGS_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JsonObject root = JsonParser.parseString(sb.toString()).getAsJsonObject();
            if (!root.has("models")) return;
            JsonArray models = root.getAsJsonArray("models");
            for (JsonElement m : models) {
                String name = m.getAsJsonObject().get("name").getAsString();
                // handle "llama3.2:latest" style names
                String base = name.contains(":") ? name.substring(0, name.indexOf(":")) : name;
                for (String preferred : PREFERRED_MODELS) {
                    if (base.toLowerCase().startsWith(preferred.toLowerCase())) {
                        modelName = name;
                        return;
                    }
                }
            }
        } catch (Exception ignored) { }
    }
    
    /**
     * Sends a prompt to Ollama and returns the response
     * @param prompt The user's prompt/question
     * @return The LLM's response text
     */
    public String ask(String prompt) throws IOException {
        try {
            URL url = new URL(OLLAMA_BASE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // Build the JSON request
            JsonObject request = new JsonObject();
            request.addProperty("model", modelName);
            request.addProperty("prompt", prompt);
            request.addProperty("stream", false);
            
            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                }
                
                // Parse JSON response
                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                return jsonResponse.get("response").getAsString();
            } else {
                throw new IOException("Ollama API returned error code: " + responseCode);
            }
        } catch (Exception e) {
            throw new IOException("Failed to connect to Ollama. Make sure Ollama is running on localhost:11434", e);
        }
    }
    
    /**
     * Generates MCQs from text using LLM
     * @param text The educational text to generate questions from
     * @return JSON string containing array of MCQ objects
     */
    /**
     * Modified generateMCQs to support custom prompts for parallel batching.
     * It also optimizes AI parameters for speed.
     */
    /**
     * Optimized for speed and strict adherence to the requested question count.
     */
    /**
     * Main Method: Optimized for speed and strict adherence to the requested question count.
     */
    public String generateMCQs(String text, String customPrompt, int questionCount) throws IOException {
        // 1. Prepare the prompt with the specific count instruction
        String limitedText = text.substring(0, Math.min(text.length(), 2500));

        // Explicitly command the AI to generate the exact number of questions
        String fullPrompt = "You MUST generate exactly " + questionCount + " questions. " +
                customPrompt + "\n\nContext:\n" + limitedText;

        // 2. Build the JSON request
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
        request.addProperty("prompt", fullPrompt);
        request.addProperty("stream", false);

        // Performance Tweaks for compliant formatting and speed
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.1);
        options.addProperty("num_predict", questionCount * 250);
        request.add("options", options);

        // 3. Execute the request using your private helper
        return executeRequest(request);
    }



    /**
     * Overload 1: Default call used for quick testing.
     */
    public String generateMCQs(String text) throws IOException {
        String defaultPrompt = "Act as an expert teacher. Generate 10 MCQs in JSON format. Return ONLY the JSON array.";
        return generateMCQs(text, defaultPrompt, 10);
    }

    /**
     * Overload 2: Used when you have a custom prompt but no specific count.
     */
    public String generateMCQs(String text, String customPrompt) throws IOException {
        return generateMCQs(text, customPrompt, 10);
    }

    /**
     * Private helper to handle HTTP communication and JSON cleaning.
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

                JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
                String cleanResponse = root.get("response").getAsString().trim();

                // Clean Markdown markers (```json ... ```) that Llama often adds
                return cleanResponse.replaceAll("(?s)```(?:json)?|```", "").trim();
            }
        }
        throw new IOException("Ollama API Error: " + conn.getResponseCode());
    }
    /**
     * Private helper to handle HTTP communication and JSON cleaning.
     */

    public interface StreamHandler {
        void handleToken(String token);
        void handleComplete();
    }



    public void askStreaming(String prompt, StreamHandler handler) throws Exception {
        URL url = new URL(OLLAMA_BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Build request with stream: true
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
        request.addProperty("prompt", prompt);
        request.addProperty("stream", true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(request).getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Ollama streaming failed: " + conn.getResponseCode());
        }

        // Read stream line-by-line
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                JsonObject jsonLine = JsonParser.parseString(line).getAsJsonObject();

                // Extract the word/token
                if (jsonLine.has("response")) {
                    handler.handleToken(jsonLine.get("response").getAsString());
                }

                // Check if the AI is finished
                if (jsonLine.has("done") && jsonLine.get("done").getAsBoolean()) {
                    break;
                }
            }
        }
        handler.handleComplete();
    }
    /**
     * Checks if Ollama is running and accessible
     * @return true if Ollama is reachable
     */
    public boolean isAvailable() {
        try {
            URL url = new URL(OLLAMA_TAGS_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            int responseCode = conn.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }
}
