PaLO: Progressive and Audio Assisted Learning Orchestrator

### PROJECT OVERVIEW
PaLO is an application that helps people learn and study better. It is a computer program that you can use on your desktop without needing the internet connection at all. PaLO is helpful for students and teachers. It uses Large Language Models (LLMs) to help you learn, which works completely offline. This project combines ways of studying with new and intelligent ways of teaching. It uses language models like Llama3.2 and Qwen2.5 that are stored locally on your computer. These models can  scan papers and pictures and make quizzes for you. The quizzes get harder or easier depending on how well you do. This project also has an algorithm that will figure out how hard the quizzes should be. It looks at how well you are doing and makes the questions harder or easier. This way you are always learning something, and you are not getting bored.

### KEY FEATURES
These are the features we have implemented till now (not perfect but still). Suggestions are appreciated.
- Offline Scan & Adaptive Quizzes: Upload PDFs or images. The system uses Tesseract OCR + tess4j to extract text and local 
  LLMs to generate quizzes. Question difficulty (Easy, Medium, Hard) is adjusted in real-time based on your performance.
- Talk to AI (Offline chatbot): Chatbot powered by local models (Llama3.2 or Qwen2.5). Ask questions and receive responses locally.
- Deep Focus Mode: Productivity timers: Pomodoro (25m/5m), Flow State (90m/15m), 52/17 Rule, and Custom Zen.
- Flashcard generator: Automatically extract key concepts from uploaded documents and convert them flashcards for quick revision.
- Teacher Export Tools: Generates printable PDF assessments containing AI-generated questions from syllabus materials.
- Audio Accessibility & Personalization: Built-in Text-to-Speech (TTS) using FreeTTS providing feedback and reads questions aloud. The UI features customizable modern themes (Dark/Light) + coloured themes using FlatLaf.
- Performance Analytics: Dashboard that tracks learning progress, calculates accuracy percentages, assigns grades, and visualizes study trends over time.

### TOOLS & TECHNOLOGIES
- Frontend / UI: Java Swing enhanced with FlatLaf for modern theming.
- Core Logic: Java SE 8+
- Local AI Engine: Ollama (Models: Llama 3.2:3B, Qwen 2.5:2.5B)
- Neural Processing: Deep Java Library (DJL)
- OCR & NLP: Tesseract (Tess4J), Apache OpenNLP (en-token.bin, en-pos-maxent.bin)
- Audio/Accessibility: FreeTTS
- PDF Processing: Apache PDFBox
- Storage: Lightweight Flat-File JSON Storage (Local only for maximum privacy)

### PREREQUISITES & INSTALLATION (Developer Setup)
- Java Development Kit (JDK): Ensure Java 8 or higher is installed.
- Ollama:
   - Check out ollama GitHub repository : https://github.com/ollama/ollama
   - Install Ollama from: https://ollama.com/ (Installer is OS specific. Make sure you install the right one)
   - install AI models:
     
  **Pull the required models** (Works on macOS/Linux terminal or Windows PowerShell/CMD):

   ```bash
   ollama pull qwen2.5:1.5b
   ```

   **Run to check whether the LLM works**:

   ```bash
   ollama run qwen2.5:1,5b
   ```

(BELOW TOOLS ARE PREPACKAGED WITH THE APPLICATION/IDE, INSTALL THEM ONLY IF ANY ERROR OCCURS.)
- Tesseract OCR: Install Tesseract on your system and ensure the `eng.traineddata` model is available in your `tessdata` directory.
- Maven: The project uses Maven for dependency management (OpenNLP, Tess4J, FlatLaf). Ensure Maven is installed to fetch dependencies automatically.

### HOW TO RUN THE APPLICATION
*The setup is currently in 'Hard Mode',but if you can handle a little manual labor, you're good to go.Once we create the executable it would go wayyy smootherrr*

(ENSURE OLLAMA IS INSTALLED ON YOUR SYSTEM FOR THE APPLICATION TO FUNCTION CORRECTLY!)
1. Clone the repository: 
```bash
git clone https://github.com/Thush-ar/palo.git
```

or download the zip file.

2. Import the file into preferred IDE (with maven). Allow Maven to resolve and download all POM dependencies.
4. Click on src/main/java/OfflineTutorApp. Run the program by selecting "current file". (Ensure selected code to run should be "OfflineTutorApp", and not "PaLODashboardDemo".

### ARCHITECTURE
- AI & Core Services: `TutorTranslator`, `VoiceAssistant`, `LlamaConnection`
- User Interface: `OfflineTutorApp` (Main JFrame), `PaLOHomePage`, `QuizPanel`
- Data Models: `QuizItem`, `ThemePreset`
- Storage: JSON files (`recent_files.json`, `chats/`) stored locally.

### DEVELOPERS
- Harikrishna B 
- Muhammed Ansal M 
- Thushar K P 
- Devadathan M 
- Abhishek Anil 

