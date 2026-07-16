/**
 * Cypher App Core Engine — Speech, Auto-Start Greeting & Model Update Receiver
 */

let synth = window.speechSynthesis;
let recognition = null;
let isListening = false;

window.addEventListener('load', () => {
  console.log("⚡ [Cy OS App] Initialized.");
  
  // Auto-Start Greeting on launch / Android OS boot
  setTimeout(() => {
    speakAsCypher("Hello Boss! All systems operational and standing by.");
  }, 800);

  // Check for model upgrades (e.g. new GGUF file copied)
  checkForModelUpdates();
});

function speakAsCypher(text) {
  const dialogueEl = document.getElementById('dialogueText');
  if (dialogueEl) dialogueEl.innerText = `"${text}"`;

  if ('speechSynthesis' in window) {
    synth.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 1.0;
    utterance.pitch = 0.95;

    utterance.onstart = () => {
      if (window.setHologramSpeaking) window.setHologramSpeaking(true);
    };

    utterance.onend = () => {
      if (window.setHologramSpeaking) window.setHologramSpeaking(false);
    };

    utterance.onerror = () => {
      if (window.setHologramSpeaking) window.setHologramSpeaking(false);
    };

    synth.speak(utterance);
  }
}

function toggleVoiceInput() {
  const micBtn = document.getElementById('micBtn');
  if (!isListening) {
    startListening();
    if (micBtn) micBtn.classList.add('active');
  } else {
    stopListening();
    if (micBtn) micBtn.classList.remove('active');
  }
}

function startListening() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    speakAsCypher("Voice recognition not supported on this browser version, Boss.");
    return;
  }

  recognition = new SpeechRecognition();
  recognition.lang = 'en-US';
  recognition.interimResults = false;

  recognition.onstart = () => {
    isListening = true;
    const statusEl = document.getElementById('statusText');
    if (statusEl) statusEl.innerText = 'LISTENING...';
  };

  recognition.onresult = (event) => {
    const transcript = event.results[0][0].transcript;
    console.log("Boss said:", transcript);
    processBossCommand(transcript);
  };

  recognition.onend = () => {
    isListening = false;
    const statusEl = document.getElementById('statusText');
    if (statusEl) statusEl.innerText = 'SYSTEM IDLE';
  };

  recognition.start();
}

function stopListening() {
  if (recognition) recognition.stop();
  isListening = false;
}

function processBossCommand(cmd) {
  const lower = cmd.toLowerCase();
  
  if (lower.includes("hello") || lower.includes("hey")) {
    speakAsCypher("Hello Boss! How may I assist you today?");
  } else if (lower.includes("upgrade") || lower.includes("update")) {
    triggerUpdateCheck();
  } else {
    speakAsCypher(`Received command: "${cmd}". Executing, Boss.`);
  }
}

function checkForModelUpdates() {
  // Simulate model file detection trigger
  const newModelFound = true; // Set to true to showcase modal prompt
  if (newModelFound) {
    setTimeout(() => {
      const modal = document.getElementById('updateModal');
      if (modal) modal.classList.remove('hidden');
      speakAsCypher("Boss, have you upgraded me? Do you want me to install my updates?");
    }, 2500);
  }
}

function triggerUpdateCheck() {
  const modal = document.getElementById('updateModal');
  if (modal) modal.classList.remove('hidden');
  speakAsCypher("Boss, have you upgraded me? Do you want me to install my updates?");
}

// Load saved model URL on startup
window.addEventListener('load', () => {
  const savedUrl = localStorage.getItem('cypher_model_url');
  if (savedUrl) {
    const input = document.getElementById('modelUrlInput');
    if (input) input.value = savedUrl;
  }
});

function acceptUpdate() {
  const input = document.getElementById('modelUrlInput');
  const downloadUrl = input ? input.value.trim() : "https://github.com/anayrajtiwari/cypher-assistant-apk/releases/download/v2.0/cypher-1.5b-q4_0.gguf";
  localStorage.setItem('cypher_model_url', downloadUrl);

  closeModal();
  speakAsCypher("Downloading new model weights now, Boss. Please check your notification drawer.");
  if (window.AndroidInterface && typeof window.AndroidInterface.downloadModel === 'function') {
    window.AndroidInterface.downloadModel(downloadUrl);
  }
}

function closeModal() {
  const modal = document.getElementById('updateModal');
  if (modal) modal.classList.add('hidden');
}
