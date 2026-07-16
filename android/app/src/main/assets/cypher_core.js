/**
 * Cypher App Core Engine — Speech, Real Native LLM Integration & UI Receiver
 */

let synth = window.speechSynthesis;
let recognition = null;
let isListening = false;
let currentStreamingText = "";

window.addEventListener('load', () => {
  console.log("⚡ [Cy OS App] Native Bridge Initialized.");
  
  // Auto-Start Greeting on launch
  setTimeout(() => {
    speakAsCypher("Hello Boss! Neural core initializing. Standing by.");
  }, 800);
});

// Native Event Listener called from MainActivity.kt BroadcastReceiver
window.onNativeLLMEvent = function(type, data) {
  console.log("⚡ [Native Event]:", type, data);
  const statusEl = document.getElementById('statusText');
  const dialogueEl = document.getElementById('dialogueText');

  if (type === "STATUS_UPDATE") {
    if (statusEl) statusEl.innerText = data;
    if (data.includes("ONLINE") || data.includes("LOADED")) {
      speakAsCypher("Local GGUF Model loaded successfully, Boss! I am online.");
    } else if (data.includes("missing") || data.includes("missing")) {
      speakAsCypher("Model file missing. Please use the Check Updates button to download it.");
    }
  } else if (type === "STREAM_START") {
    currentStreamingText = "";
    if (statusEl) statusEl.innerText = "THINKING...";
    if (dialogueEl) dialogueEl.innerText = '"..."';
    if (window.setHologramSpeaking) window.setHologramSpeaking(true);
  } else if (type === "STREAM_TOKEN") {
    currentStreamingText += data;
    if (dialogueEl) dialogueEl.innerText = `"${currentStreamingText}"`;
  } else if (type === "STREAM_END") {
    if (statusEl) statusEl.innerText = "SYSTEM IDLE";
    if (window.setHologramSpeaking) window.setHologramSpeaking(false);
    if (currentStreamingText.trim().length > 0) {
      speakTextOnly(currentStreamingText);
    }
  }
};

function speakAsCypher(text) {
  const dialogueEl = document.getElementById('dialogueText');
  if (dialogueEl) dialogueEl.innerText = `"${text}"`;
  speakTextOnly(text);
}

function speakTextOnly(text) {
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
    speakAsCypher("Voice recognition not supported on this device, Boss.");
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
  const dialogueEl = document.getElementById('dialogueText');
  if (dialogueEl) dialogueEl.innerText = `"${cmd}"`;

  // Dispatch directly to Native GGUF Llama engine via AndroidInterface
  if (window.AndroidInterface && typeof window.AndroidInterface.sendPromptToCypher === 'function') {
    window.AndroidInterface.sendPromptToCypher(cmd);
  } else {
    speakAsCypher(`Received: "${cmd}". Android native interface unavailable.`);
  }
}

function triggerUpdateCheck() {
  const modal = document.getElementById('updateModal');
  if (modal) modal.classList.remove('hidden');
  speakAsCypher("Boss, do you want to download or re-sync the model weights?");
}

function acceptUpdate() {
  const input = document.getElementById('modelUrlInput');
  const downloadUrl = input ? input.value.trim() : "https://github.com/anayrajtiwari/cypher-assistant-apk/releases/download/v2.0/cypher-1.5b-q4_0.gguf";
  localStorage.setItem('cypher_model_url', downloadUrl);

  closeModal();
  speakAsCypher("Downloading model weights to public Download folder now, Boss.");
  if (window.AndroidInterface && typeof window.AndroidInterface.downloadModel === 'function') {
    window.AndroidInterface.downloadModel(downloadUrl);
  }
}

function closeModal() {
  const modal = document.getElementById('updateModal');
  if (modal) modal.classList.add('hidden');
}
