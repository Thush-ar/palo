package app.audio;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TTSService {

    private Voice voice;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public TTSService() {

        System.setProperty("freetts.voices",
                "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");

        VoiceManager vm = VoiceManager.getInstance();
        voice = vm.getVoice("kevin16");

        if (voice != null) {
            voice.allocate();
            startWorker();
        } else {
            System.err.println("FreeTTS voice not found!");
        }
    }

    private void startWorker() {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    String text = queue.take();
                    voice.speak(text);
                } catch (Exception e) {
                    System.err.println("TTS error ignored: " + e.getMessage());
                }
            }
        });

        worker.setDaemon(true);
        worker.start();
    }

    public void speak(String text) {

        if (voice == null || text == null) return;

        text = text.trim();
        if (text.isEmpty()) return;

        // Limit length
        if (text.length() > 400) {
            text = text.substring(0, 400);
        }

        // Remove problematic chars
        text = text.replaceAll("[^a-zA-Z0-9 .,?!()\\n]", " ");

        queue.offer(text);
    }
}
