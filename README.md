# Cypher — AI Mobile Agent for Android 16

An always-on, voice-activated AI assistant for Android that runs entirely on-device. No frontend, no cloud dependency — operates as a persistent background service with wake-word activation, local LLM inference via llama.cpp, and full agentic tool use (calls, SMS, camera, location, contacts, flashlight, clipboard, etc.).

## Architecture

```
┌─────────────────────────────────────┐
│         Android 16 Device           │
│  ┌───────────────────────────────┐  │
│  │   CypherBackgroundService     │  │
│  │  (Foreground Service)         │  │
│  │                               │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │    CypherDaemon          │  │  │
│  │  │  ┌───────────────────┐  │  │  │
│  │  │  │ WakeWordDetector   │  │  │  │
│  │  │  │ ("Zed One Eight")  │  │  │  │
│  │  │  └─────────┬─────────┘  │  │  │
│  │  │            ▼             │  │  │
│  │  │  ┌───────────────────┐  │  │  │
│  │  │  │    STTEngine       │  │  │  │
│  │  │  │ (Android Speech)   │  │  │  │
│  │  │  └─────────┬─────────┘  │  │  │
│  │  │            ▼             │  │  │
│  │  │  ┌───────────────────┐  │  │  │
│  │  │  │   CypherBrain      │  │  │  │
│  │  │  │ (llama.cpp JNI)    │  │  │  │
│  │  │  └─────────┬─────────┘  │  │  │
│  │  │            ▼             │  │  │
│  │  │  ┌───────────────────┐  │  │  │
│  │  │  │  AndroidCapabilities│  │  │
│  │  │  │ (Tool execution)   │  │  │  │
│  │  │  └───────────────────┘  │  │  │
│  │  └─────────────────────────┘  │  │
│  │  ┌──────────────────────┐     │  │
│  │  │  PermissionManager   │     │  │
│  │  │  (Access control)    │     │  │
│  │  └──────────────────────┘     │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

## Features

| Feature | Implementation |
|---|---|
| Wake word | Android's `SpeechRecognizer` (hotword: "Zed One Eight" / "Zee One Eight") |
| Speech-to-text | `android.speech.SpeechRecognizer` |
| LLM inference | `llama.cpp` via JNI (`llama_jni.cpp`), loads GGUF models |
| Text-to-speech | Android `TextToSpeech` engine |
| Tool calling | LLM outputs `<tool_call>{"name":"...","arguments":{...}}</tool_call>` — parsed and executed |
| Phone calls | `Intent.ACTION_CALL` |
| SMS | `SmsManager.sendTextMessage` |
| Contacts | `ContactsContract` content provider |
| GPS | `LocationManager.getLastKnownLocation` |
| Camera | `MediaStore.ACTION_IMAGE_CAPTURE` |
| Flashlight | Legacy `android.hardware.Camera` torch |
| Volume control | `AudioManager.setStreamVolume` |
| Clipboard | `ClipboardManager` read/write |
| URL open | `Intent.ACTION_VIEW` |
| App launch | `PackageManager.getLaunchIntentForPackage` |
| Notifications | `NotificationCompat` via foreground service |
| Boot auto-start | `BroadcastReceiver` for `BOOT_COMPLETED` |
| Permissions | Runtime permission requests + session/explicit tool gating |

## Project Structure

```
cypher_android_pack/
├── cypher_app/android/          # Android app (Kotlin + Compose)
│   ├── app/
│   │   ├── build.gradle.kts     # AGP 8.2.2, Compose, CMake/NDK
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/ai/cypher/assistant/
│   │       │   ├── MainActivity.kt       # Permission granting UI (Compose)
│   │       │   ├── CypherBackgroundService.kt  # Foreground service
│   │       │   ├── CypherBootReceiver.kt      # Auto-start on boot
│   │       │   ├── CypherDaemon.kt            # Main agent loop
│   │       │   ├── CypherBrain.kt             # LLM loader + inference
│   │       │   ├── WakeWordDetector.kt        # "Zed One Eight" listener
│   │       │   ├── STTEngine.kt              # Speech-to-text
│   │       │   ├── TTSManager.kt             # Text-to-speech
│   │       │   ├── AndroidCapabilities.kt    # All tool implementations
│   │       │   ├── PermissionManager.kt      # Tool-level access control
│   │       │   ├── TelephonyManager.kt       # Call answer/end
│   │       │   ├── NotificationHelper.kt     # System notifications
│   │       │   └── ...
│   │       ├── cpp/
│   │       │   ├── CMakeLists.txt            # Fetches llama.cpp @ b10043
│   │       │   └── llama_jni.cpp             # JNI bridge to llama.cpp
│   │       └── res/                          # Minimal theme
│   ├── gradle/
│   └── build.gradle.kts
├── cypher/                       # Python daemon (alternate runtime)
│   ├── agent.py, brain.py, memory.py, ...
│   └── cypher_mobile_daemon.py   # Main entry for Termux
├── .github/workflows/build-apk.yml  # CI: builds + releases APK
├── install_on_android.sh            # Termux installer script
└── cypher-1.5b-q4_0.gguf           # Model file (1.5B param Q4 GGUF)
```

## Model

Uses a 1.5B parameter Q4_0 GGUF model (~1GB). At startup the app looks for it in:
- `context.filesDir/models/`
- External files dir
- `/sdcard/Cypher/`

If absent, the user can say *"download model"* to fetch it from the GitHub release, or the CI bundles it into the APK at build time.

## Building

```bash
cd cypher_app/android
./gradlew assembleDebug
```

CI (GitHub Actions) does this automatically on push to `main` — the APK is uploaded to the [release](https://github.com/anayrajtiwari/cypher-assistant-apk/releases/tag/v1.0).

## Installing on-device (Termux)

```bash
# Install Python dependencies + Termux API
pkg update && pkg install python termux-api ffmpeg
pip install faster-whisper webrtcvad llama-cpp-python

# Clone and run
git clone https://github.com/anayrajtiwari/cypher-assistant-apk
cd cypher-assistant-apk
python3 -m cypher.cypher_mobile_daemon
```

## Wake Word

Say **"Zed One Eight"** (or "Zee One Eight") to activate. The Android app uses the built-in `SpeechRecognizer` for always-on listening.

## Agentic Tool Calling

The LLM is prompted to output structured tool calls:

```
<tool_call>{"name":"send_sms","arguments":{"number":"+1234567890","message":"Hello"}}</tool_call>
```

The daemon parses these, checks permissions, and executes them against the device's APIs.
